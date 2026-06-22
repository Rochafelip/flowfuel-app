package com.flowfuel.app.feature.auth.presentation.checkemail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auth.domain.usecase.ActivateAccountUseCase
import com.flowfuel.app.feature.auth.domain.usecase.ResendActivationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CheckEmailUiState(
    val isResending: Boolean = false,
    val cooldownSeconds: Int = 0,
    val activationToken: String = "",
    val isActivating: Boolean = false,
    val activationError: AppError? = null,
) {
    val canResend: Boolean get() = !isResending && cooldownSeconds == 0
}

sealed interface CheckEmailEffect {
    data object NavigateToLogin : CheckEmailEffect
    data object ResendConfirmed : CheckEmailEffect
    data object ActivationConfirmed : CheckEmailEffect
}

@HiltViewModel
class CheckEmailViewModel @Inject constructor(
    private val resendActivation: ResendActivationUseCase,
    private val activateAccount: ActivateAccountUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CheckEmailUiState())
    val state: StateFlow<CheckEmailUiState> = _state.asStateFlow()

    private val _effects = Channel<CheckEmailEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun resend(email: String) {
        if (!_state.value.canResend) return
        _state.update { it.copy(isResending = true) }
        viewModelScope.launch {
            resendActivation(email)
            _state.update { it.copy(isResending = false) }
            _effects.send(CheckEmailEffect.ResendConfirmed)
            startCooldown()
        }
    }

    fun onActivationTokenChange(v: String) =
        _state.update { it.copy(activationToken = v, activationError = null) }

    fun activateWithToken() {
        val token = _state.value.activationToken
        if (token.isBlank() || _state.value.isActivating) return
        _state.update { it.copy(isActivating = true, activationError = null) }
        viewModelScope.launch {
            when (val result = activateAccount(token)) {
                is AppResult.Success -> {
                    _state.update { it.copy(isActivating = false) }
                    _effects.send(CheckEmailEffect.ActivationConfirmed)
                }
                is AppResult.Failure -> {
                    // 401 genérico de auth não distingue o motivo — neste contexto
                    // (POST /auth/activate) só pode ser token de ativação inválido.
                    val error = if (result.error == AppError.Unauthorized) {
                        AppError.Api("AUTH_ACTIVATION_INVALID")
                    } else {
                        result.error
                    }
                    _state.update { it.copy(isActivating = false, activationError = error) }
                }
            }
        }
    }

    fun onAlreadyConfirmed() {
        viewModelScope.launch { _effects.send(CheckEmailEffect.NavigateToLogin) }
    }

    private fun startCooldown() {
        viewModelScope.launch {
            for (remaining in 30 downTo 1) {
                _state.update { it.copy(cooldownSeconds = remaining) }
                delay(1_000L)
            }
            _state.update { it.copy(cooldownSeconds = 0) }
        }
    }
}
