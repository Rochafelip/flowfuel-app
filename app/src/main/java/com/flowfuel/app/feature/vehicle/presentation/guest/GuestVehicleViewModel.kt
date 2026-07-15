package com.flowfuel.app.feature.vehicle.presentation.guest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GuestVehicleUiState(
    val vehicleId: Int,
    val vehicleBrand: String,
    val vehicleModel: String,
    val expiresAt: String?,
    val odometerInput: String = "",
    val isSavingOdometer: Boolean = false,
    val odometerError: String? = null,
)

sealed interface GuestVehicleEffect {
    data object OdometerUpdated : GuestVehicleEffect
    data class NavigateToCreateEvent(val vehicleId: Int) : GuestVehicleEffect
    data class NavigateToPicker(val message: String?) : GuestVehicleEffect
}

/**
 * Home mínima do convidado (usuário com veículo emprestado). Reaproveita
 * [VehicleRepository.updateOdometer] diretamente — a assinatura já é
 * `(vehicleId: Int, newKm: Int): AppResult<Unit>`, exatamente o que o
 * convidado precisa. O `UpdateOdometerUseCase` existente não serve aqui pois
 * exige `currentKm` (fluxo do dono, com validação de regressão), estado que
 * esta tela não carrega.
 *
 * A identidade do veículo emprestado não vem de argumentos de navegação —
 * [GuestVehicleScreen] é embutida diretamente na aba HOME de
 * [com.flowfuel.app.navigation.MainContainerScreen] (rota sem argumentos), e
 * já recebe o [VehicleShare] completo de
 * [com.flowfuel.app.navigation.MainContainerViewModel]. Por isso a
 * inicialização é feita via [initialize], chamada diretamente do corpo do
 * composable (idempotente, ver kdoc do método).
 */
@HiltViewModel
class GuestVehicleViewModel @Inject constructor(
    private val repository: VehicleRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        GuestVehicleUiState(
            vehicleId = UNINITIALIZED_VEHICLE_ID,
            vehicleBrand = "",
            vehicleModel = "",
            expiresAt = null,
        ),
    )
    val state: StateFlow<GuestVehicleUiState> = _state.asStateFlow()

    private val _effects = Channel<GuestVehicleEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /**
     * Popula o estado a partir do [VehicleShare] recebido como parâmetro do
     * composable. Idempotente: recompor com o mesmo `vehicleId` é um no-op,
     * preservando `odometerInput`/etc. em progresso. Seguro para chamar
     * diretamente no corpo do composable (fora de `LaunchedEffect`) — evita um
     * frame com estado vazio antes do primeiro efeito rodar.
     */
    fun initialize(guestVehicle: VehicleShare) {
        if (_state.value.vehicleId == guestVehicle.vehicleId) return
        _state.value = GuestVehicleUiState(
            vehicleId = guestVehicle.vehicleId,
            vehicleBrand = guestVehicle.vehicleBrand,
            vehicleModel = guestVehicle.vehicleModel,
            expiresAt = guestVehicle.expiresAt,
        )
    }

    fun onOdometerChange(value: String) =
        _state.update { it.copy(odometerInput = value, odometerError = null) }

    fun confirmOdometer() {
        val km = _state.value.odometerInput.toIntOrNull()
        if (km == null || km <= 0) {
            _state.update { it.copy(odometerError = "Informe um valor válido") }
            return
        }
        _state.update { it.copy(isSavingOdometer = true) }
        viewModelScope.launch {
            when (val result = repository.updateOdometer(_state.value.vehicleId, km)) {
                is AppResult.Success -> {
                    _state.update { it.copy(isSavingOdometer = false, odometerInput = "") }
                    _effects.send(GuestVehicleEffect.OdometerUpdated)
                }
                is AppResult.Failure -> handleFailure(result.error)
            }
        }
    }

    fun onCreateEventClicked() {
        viewModelScope.launch {
            _effects.send(GuestVehicleEffect.NavigateToCreateEvent(_state.value.vehicleId))
        }
    }

    private suspend fun handleFailure(error: AppError) {
        _state.update { it.copy(isSavingOdometer = false) }
        val isForbidden = (error as? AppError.Api)?.code == "FORBIDDEN_OPERATION"
        if (isForbidden) {
            sessionStore.clearActiveVehicleId()
            _effects.send(GuestVehicleEffect.NavigateToPicker("Esse veículo não está mais compartilhado com você"))
        } else {
            _state.update { it.copy(odometerError = error.message ?: "Erro ao atualizar odômetro") }
        }
    }

    private companion object {
        const val UNINITIALIZED_VEHICLE_ID = -1
    }
}
