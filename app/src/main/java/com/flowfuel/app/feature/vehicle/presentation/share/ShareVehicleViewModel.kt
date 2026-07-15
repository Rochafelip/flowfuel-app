package com.flowfuel.app.feature.vehicle.presentation.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShareStatus
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetVehicleShareForVehicleUseCase
import com.flowfuel.app.core.vehicleshare.domain.usecase.InviteVehicleShareUseCase
import com.flowfuel.app.core.vehicleshare.domain.usecase.RevokeVehicleShareUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ShareVehicleUiState {
    data object Loading : ShareVehicleUiState
    data class NoShare(
        val email: String = "",
        val durationDays: Int = 3,
        val isSubmitting: Boolean = false,
        val error: String? = null,
    ) : ShareVehicleUiState
    data class Pending(val share: VehicleShare, val isRevoking: Boolean = false) : ShareVehicleUiState
    data class Active(val share: VehicleShare, val isRevoking: Boolean = false) : ShareVehicleUiState
    data class Error(val message: String) : ShareVehicleUiState
}

/**
 * Tela do dono para convidar um usuário (por email) a compartilhar o veículo,
 * e gerenciar (ver/cancelar) um compartilhamento pendente ou ativo existente.
 */
@HiltViewModel
class ShareVehicleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val invite: InviteVehicleShareUseCase,
    private val revoke: RevokeVehicleShareUseCase,
    private val getForVehicle: GetVehicleShareForVehicleUseCase,
) : ViewModel() {

    private val vehicleId: Int = checkNotNull(savedStateHandle["vehicleId"])

    private val _state = MutableStateFlow<ShareVehicleUiState>(ShareVehicleUiState.Loading)
    val state: StateFlow<ShareVehicleUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            when (val result = getForVehicle(vehicleId)) {
                is AppResult.Success -> _state.value = toState(result.value)
                is AppResult.Failure -> _state.value = ShareVehicleUiState.Error(mapErrorMessage(result.error))
            }
        }
    }

    private fun toState(share: VehicleShare?): ShareVehicleUiState = when {
        share == null -> ShareVehicleUiState.NoShare()
        share.status == VehicleShareStatus.ACTIVE -> ShareVehicleUiState.Active(share)
        share.status == VehicleShareStatus.PENDING -> ShareVehicleUiState.Pending(share)
        else -> ShareVehicleUiState.NoShare()
    }

    fun onEmailChange(value: String) {
        val current = _state.value as? ShareVehicleUiState.NoShare ?: return
        _state.value = current.copy(email = value, error = null)
    }

    fun onDurationChange(days: Int) {
        val current = _state.value as? ShareVehicleUiState.NoShare ?: return
        _state.value = current.copy(durationDays = days)
    }

    fun sendInvite() {
        val current = _state.value as? ShareVehicleUiState.NoShare ?: return
        if (current.email.isBlank()) {
            _state.value = current.copy(error = "Informe o email do convidado")
            return
        }
        _state.value = current.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            when (val result = invite(vehicleId, current.email.trim(), current.durationDays)) {
                is AppResult.Success -> _state.value = toState(result.value)
                is AppResult.Failure ->
                    _state.value = current.copy(isSubmitting = false, error = mapErrorMessage(result.error))
            }
        }
    }

    fun revokeShare() {
        val shareId = when (val s = _state.value) {
            is ShareVehicleUiState.Pending -> s.share.id
            is ShareVehicleUiState.Active -> s.share.id
            else -> return
        }
        viewModelScope.launch {
            when (revoke(shareId)) {
                is AppResult.Success -> _state.value = ShareVehicleUiState.NoShare()
                is AppResult.Failure -> load()
            }
        }
    }

    private fun mapErrorMessage(error: AppError): String = when {
        error is AppError.Api && error.code == "RESOURCE_NOT_FOUND" -> "Esse email não tem cadastro no FlowFuel"
        error is AppError.Api && error.code == "CONFLICT" ->
            "Já existe um compartilhamento pendente ou ativo pra esse veículo"
        error is AppError.Api && error.code == "BUSINESS_RULE_VIOLATED" -> error.message ?: "Não foi possível compartilhar"
        error is AppError.Network -> "Sem conexão. Verifique sua internet."
        else -> "Erro inesperado. Tente novamente."
    }
}
