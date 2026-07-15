package com.flowfuel.app.feature.vehicle.presentation.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.usecase.AcceptVehicleShareUseCase
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetPendingVehicleSharesUseCase
import com.flowfuel.app.core.vehicleshare.domain.usecase.RejectVehicleShareUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ShareInviteUiState {
    data object Loading : ShareInviteUiState
    data class Content(
        val share: VehicleShare,
        val isSubmitting: Boolean = false,
        val error: String? = null,
    ) : ShareInviteUiState
    data class NotFound(val message: String = "Convite não encontrado ou já respondido") : ShareInviteUiState
}

sealed interface ShareInviteEffect {
    data object NavigateBack : ShareInviteEffect
}

/**
 * Tela do convidado para aceitar ou recusar um convite de compartilhamento de veículo.
 *
 * O backend não expõe `GET /vehicle-shares/{id}` — só `GET /vehicle-shares/pending` (lista).
 * A tela busca a lista de pendentes e filtra pelo `shareId` da rota (que vem do deep link
 * do push ou da lista do Perfil).
 */
@HiltViewModel
class ShareInviteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val acceptShare: AcceptVehicleShareUseCase,
    private val rejectShare: RejectVehicleShareUseCase,
    private val getPending: GetPendingVehicleSharesUseCase,
) : ViewModel() {

    private val shareId: Int = checkNotNull(savedStateHandle["shareId"])

    private val _state = MutableStateFlow<ShareInviteUiState>(ShareInviteUiState.Loading)
    val state: StateFlow<ShareInviteUiState> = _state.asStateFlow()

    private val _effects = Channel<ShareInviteEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            when (val result = getPending()) {
                is AppResult.Success -> {
                    val found = result.value.firstOrNull { it.id == shareId }
                    _state.value = if (found != null) ShareInviteUiState.Content(found) else ShareInviteUiState.NotFound()
                }
                is AppResult.Failure -> _state.value = ShareInviteUiState.NotFound("Erro ao carregar o convite")
            }
        }
    }

    fun accept() = respond { acceptShare(shareId) }

    fun reject() = respond { rejectShare(shareId) }

    private fun respond(action: suspend () -> AppResult<VehicleShare>) {
        val current = _state.value as? ShareInviteUiState.Content ?: return
        if (current.isSubmitting) return
        _state.value = current.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            when (val result = action()) {
                is AppResult.Success -> _effects.send(ShareInviteEffect.NavigateBack)
                is AppResult.Failure ->
                    _state.value = current.copy(isSubmitting = false, error = mapErrorMessage(result.error))
            }
        }
    }

    private fun mapErrorMessage(error: AppError): String = when {
        error is AppError.Api && error.code == "RESOURCE_NOT_FOUND" -> "Esse convite não existe mais"
        error is AppError.Api && error.code == "CONFLICT" -> "Esse convite já foi respondido"
        error is AppError.Api && error.code == "BUSINESS_RULE_VIOLATED" -> error.message ?: "Não foi possível responder ao convite"
        error is AppError.Network -> "Sem conexão. Verifique sua internet."
        else -> "Erro inesperado. Tente novamente."
    }
}
