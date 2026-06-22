package com.flowfuel.app.feature.vehicle.presentation.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.pagination.PaginationState
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.usecase.DeleteVehicleUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehiclesPageUseCase
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

@HiltViewModel
class VehiclesViewModel @Inject constructor(
    private val getVehiclesPage: GetVehiclesPageUseCase,
    private val setActiveVehicle: SetActiveVehicleUseCase,
    private val deleteVehicle: DeleteVehicleUseCase,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(VehiclesUiState())
    val state: StateFlow<VehiclesUiState> = _state.asStateFlow()

    private val _effects = Channel<VehiclesEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val _pagination = MutableStateFlow(PaginationState())
    val paginationState: StateFlow<PaginationState> = _pagination.asStateFlow()

    private var accumulatedVehicles: List<Vehicle> = emptyList()
    private var isLoadingMore = false

    // Preserved across navigation
    var savedScrollIndex: Int = 0
        private set
    var savedScrollOffset: Int = 0
        private set

    init { load() }

    // ─── Carregamento ─────────────────────────────────────────────────────────

    fun load() {
        _state.update { it.copy(screenState = VehiclesScreenState.Loading, isDeleting = false) }
        accumulatedVehicles = emptyList()
        _pagination.value = PaginationState()
        isLoadingMore = false
        viewModelScope.launch {
            val activeId = sessionStore.activeVehicleIdFlow.first()
            when (val result = getVehiclesPage(0)) {
                is AppResult.Success -> {
                    val paged = result.value
                    accumulatedVehicles = paged.items
                    _state.update {
                        it.copy(
                            activeVehicleId = activeId,
                            screenState = if (paged.items.isEmpty()) VehiclesScreenState.Empty
                                          else VehiclesScreenState.Success(accumulatedVehicles),
                        )
                    }
                    _pagination.value = PaginationState(currentPage = 0, hasMore = paged.hasMore)
                }
                is AppResult.Failure -> {
                    Timber.e("Vehicles: error → ${result.error}")
                    handleGlobalError(result.error)
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

                    _state.update {
                        it.copy(screenState = VehiclesScreenState.Success(accumulatedVehicles))
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

    // ─── Troca de veículo ativo ───────────────────────────────────────────────

    /**
     * Troca o veículo ativo de forma otimista:
     * 1. Atualiza [activeVehicleId] imediatamente (sem esperar a API).
     * 2. Chama [SetActiveVehicleUseCase] que persiste no DataStore e chama a API.
     */
    fun onSetActive(vehicle: Vehicle) {
        _state.update { it.copy(activeVehicleId = vehicle.id) }
        viewModelScope.launch {
            setActiveVehicle(vehicle.id)
        }
    }

    // ─── Exclusão de veículo ──────────────────────────────────────────────────

    /** Abre o dialog de confirmação de exclusão. */
    fun onDeleteRequest(vehicle: Vehicle) {
        _state.update { it.copy(vehiclePendingDelete = vehicle) }
    }

    /** Fecha o dialog sem excluir. */
    fun onDeleteDismiss() {
        _state.update { it.copy(vehiclePendingDelete = null) }
    }

    /** Executa a exclusão após confirmação do usuário. */
    fun onDeleteConfirm() {
        val vehicle = _state.value.vehiclePendingDelete ?: return
        _state.update { it.copy(vehiclePendingDelete = null, isDeleting = true) }
        viewModelScope.launch {
            when (deleteVehicle(vehicle.id)) {
                is AppResult.Success -> load()   // recarrega lista atualizada do page 0
                is AppResult.Failure -> {
                    Timber.e("Vehicles: delete error para id=${vehicle.id}")
                    _state.update { it.copy(isDeleting = false) }
                }
            }
        }
    }

    fun saveScrollState(index: Int, offset: Int) {
        savedScrollIndex = index
        savedScrollOffset = offset
    }

    // ─── Helpers privados ─────────────────────────────────────────────────────

    private suspend fun handleGlobalError(error: AppError) {
        if (error == AppError.Unauthorized) {
            sessionStore.clear()
            _effects.send(VehiclesEffect.NavigateToLogin)
        } else {
            _state.update { it.copy(screenState = VehiclesScreenState.Error(error)) }
        }
    }
}
