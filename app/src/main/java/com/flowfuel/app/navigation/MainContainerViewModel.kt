package com.flowfuel.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetActiveSharedVehiclesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainContainerUiState(
    val isGuestMode: Boolean = false,
    val guestVehicle: VehicleShare? = null,
    /** True quando isGuestMode e a busca do share ativo falhou/não achou correspondência — distingue do estado "ainda carregando". */
    val guestVehicleLoadFailed: Boolean = false,
)

@HiltViewModel
class MainContainerViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val getActiveSharedVehicles: GetActiveSharedVehiclesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MainContainerUiState())
    val state: StateFlow<MainContainerUiState> = _state.asStateFlow()

    init {
        sessionStore.activeVehicleIsGuestFlow
            .combine(sessionStore.activeVehicleIdFlow) { isGuest, vehicleId -> isGuest to vehicleId }
            .onEach { (isGuest, vehicleId) -> resolveGuestState(isGuest, vehicleId) }
            .launchIn(viewModelScope)
    }

    /**
     * Reexecuta a busca do share ativo. Necessário porque o `combine` do
     * `init` só reage a mudanças no [SessionStore] — se a busca falhar (rede,
     * share não encontrado), nada nele muda de novo sozinho, e sem isso o
     * convidado ficaria preso na tela de carregamento indefinidamente.
     * Chamado pela UI a partir do estado de erro (ver [MainContainerUiState.guestVehicleLoadFailed]).
     */
    fun retryLoadGuestVehicle() {
        if (!_state.value.isGuestMode) return
        viewModelScope.launch {
            val vehicleId = sessionStore.activeVehicleIdFlow.first()
            resolveGuestState(isGuest = true, vehicleId = vehicleId)
        }
    }

    private suspend fun resolveGuestState(isGuest: Boolean, vehicleId: Int?) {
        if (!isGuest || vehicleId == null) {
            _state.value = MainContainerUiState(isGuestMode = false, guestVehicle = null)
            return
        }
        val share = (getActiveSharedVehicles() as? AppResult.Success)
            ?.value?.firstOrNull { it.vehicleId == vehicleId }
        _state.value = MainContainerUiState(
            isGuestMode = true,
            guestVehicle = share,
            guestVehicleLoadFailed = share == null,
        )
    }
}
