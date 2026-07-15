package com.flowfuel.app.feature.vehicle.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.pagination.PaginationState
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetActiveSharedVehiclesUseCase
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehiclesPageUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.SetActiveGuestVehicleUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.SetActiveVehicleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface VehiclePickerItem {
    data class Owned(val vehicle: Vehicle) : VehiclePickerItem
    data class Borrowed(val share: VehicleShare) : VehiclePickerItem
}

sealed interface VehiclePickerUiState {
    data object Loading : VehiclePickerUiState
    data class Success(
        val items: List<VehiclePickerItem>,
        /** ID do veículo atualmente ativo (último usado); null se nenhum ainda. */
        val activeVehicleId: Int?,
    ) : VehiclePickerUiState
    data class Error(val error: AppError) : VehiclePickerUiState
}

sealed interface VehiclePickerEffect {
    /** Lista vazia — redirecionar para cadastro automaticamente */
    data object NavigateToAddVehicle : VehiclePickerEffect
    /** Usuário selecionou um veículo */
    data class NavigateToHome(val vehicle: Vehicle) : VehiclePickerEffect
    /** Usuário selecionou um veículo emprestado (compartilhado com ele) */
    data class NavigateToGuestVehicle(val share: VehicleShare) : VehiclePickerEffect
    /** Token inválido/expirado — redirecionar para login */
    data object NavigateToLogin : VehiclePickerEffect
    /** Mensagem de saída forçada do modo convidado (ex: dono revogou o compartilhamento) */
    data class ShowMessage(val message: String) : VehiclePickerEffect
}

@HiltViewModel
class VehiclePickerViewModel @Inject constructor(
    private val getVehiclesPage: GetVehiclesPageUseCase,
    private val setActiveVehicle: SetActiveVehicleUseCase,
    private val sessionStore: SessionStore,
    private val getActiveSharedVehicles: GetActiveSharedVehiclesUseCase,
    private val setActiveGuestVehicle: SetActiveGuestVehicleUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<VehiclePickerUiState>(VehiclePickerUiState.Loading)
    val state: StateFlow<VehiclePickerUiState> = _state.asStateFlow()

    private val _effects = Channel<VehiclePickerEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val _pagination = MutableStateFlow(PaginationState())
    val paginationState: StateFlow<PaginationState> = _pagination.asStateFlow()

    private var accumulatedVehicles: List<Vehicle> = emptyList()
    private var isLoadingMore = false

    // Preserved across navigation (ViewModel survives; composable recreates on back-press)
    var savedScrollIndex: Int = 0
        private set
    var savedScrollOffset: Int = 0
        private set

    init {
        load()
        // Mensagem de saída forçada do modo convidado (ex: dono revogou o
        // compartilhamento) — setada no SessionStore por GuestVehicleViewModel
        // antes de navegar de volta pra cá, pois o popUpTo(0) da navegação
        // destrói o NavBackStackEntry que o padrão savedStateHandle usaria.
        sessionStore.consumeGuestAccessEndedMessage()?.let { message ->
            viewModelScope.launch { _effects.send(VehiclePickerEffect.ShowMessage(message)) }
        }
    }

    fun load() {
        _state.value = VehiclePickerUiState.Loading
        accumulatedVehicles = emptyList()
        _pagination.value = PaginationState()
        isLoadingMore = false
        viewModelScope.launch {
            val activeVehicleId = sessionStore.activeVehicleIdFlow.first()
            when (val result = getVehiclesPage(0)) {
                is AppResult.Success -> {
                    val paged = result.value
                    Timber.d("VehiclePicker: ${paged.items.size} veículo(s), ativo=$activeVehicleId")
                    val sharedResult = getActiveSharedVehicles()
                    val borrowedItems = (sharedResult as? AppResult.Success)?.value
                        ?.map { VehiclePickerItem.Borrowed(it) } ?: emptyList()
                    if (paged.items.isEmpty() && borrowedItems.isEmpty()) {
                        _effects.send(VehiclePickerEffect.NavigateToAddVehicle)
                    } else {
                        accumulatedVehicles = paged.items
                        val ownedItems = accumulatedVehicles.map { VehiclePickerItem.Owned(it) }
                        _state.value = VehiclePickerUiState.Success(ownedItems + borrowedItems, activeVehicleId)
                        _pagination.value = PaginationState(currentPage = 0, hasMore = paged.hasMore)
                    }
                }
                is AppResult.Failure -> {
                    Timber.e("VehiclePicker: falha → ${result.error}")
                    if (result.error == AppError.Unauthorized) {
                        sessionStore.clear()
                        _effects.send(VehiclePickerEffect.NavigateToLogin)
                    } else {
                        _state.value = VehiclePickerUiState.Error(result.error)
                    }
                }
            }
        }
    }

    fun loadNextPage() {
        val paginationSnapshot = _pagination.value
        if (isLoadingMore || !paginationSnapshot.hasMore) return

        isLoadingMore = true
        _pagination.update { it.copy(isLoadingMore = true, pageError = null) }

        viewModelScope.launch {
            val nextPage = paginationSnapshot.currentPage + 1
            when (val result = getVehiclesPage(nextPage)) {
                is AppResult.Success -> {
                    val existingIds = accumulatedVehicles.map { it.id }.toSet()
                    val deduped = result.value.items.filter { it.id !in existingIds }
                    accumulatedVehicles = accumulatedVehicles + deduped

                    val current = _state.value
                    if (current is VehiclePickerUiState.Success) {
                        val borrowedItems = current.items.filterIsInstance<VehiclePickerItem.Borrowed>()
                        _state.value = current.copy(
                            items = accumulatedVehicles.map { VehiclePickerItem.Owned(it) } + borrowedItems,
                        )
                    }
                    _pagination.update {
                        it.copy(
                            currentPage = nextPage,
                            isLoadingMore = false,
                            hasMore = result.value.hasMore,
                        )
                    }
                }
                is AppResult.Failure -> {
                    _pagination.update { it.copy(isLoadingMore = false, pageError = result.error) }
                }
            }
            isLoadingMore = false
        }
    }

    fun onItemSelected(item: VehiclePickerItem) {
        viewModelScope.launch {
            when (item) {
                is VehiclePickerItem.Owned -> {
                    // 1. Salva localmente e chama API (SetActiveVehicleUseCase faz as duas coisas)
                    setActiveVehicle(item.vehicle.id)
                    // 2. Navega imediatamente (otimista)
                    _effects.send(VehiclePickerEffect.NavigateToHome(item.vehicle))
                }
                is VehiclePickerItem.Borrowed -> {
                    setActiveGuestVehicle(item.share.vehicleId)
                    _effects.send(VehiclePickerEffect.NavigateToGuestVehicle(item.share))
                }
            }
        }
    }

    fun onAddVehicleClicked() {
        viewModelScope.launch { _effects.send(VehiclePickerEffect.NavigateToAddVehicle) }
    }

    fun saveScrollState(index: Int, offset: Int) {
        savedScrollIndex = index
        savedScrollOffset = offset
    }
}
