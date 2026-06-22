package com.flowfuel.app.feature.auth.presentation.resetpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.common.Validators
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auth.domain.usecase.ResetPasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResetPasswordUiState(
    val token: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val tokenError: Boolean = false,
    val newError: Boolean = false,
    val confirmError: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
) {
    val canSubmit: Boolean
        get() = token.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank() && !isSubmitting
}

sealed interface ResetPasswordEffect {
    data object Success : ResetPasswordEffect
}

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val resetPassword: ResetPasswordUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ResetPasswordUiState())
    val state: StateFlow<ResetPasswordUiState> = _state.asStateFlow()

    private val _effects = Channel<ResetPasswordEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onTokenChange(v: String) = _state.update { it.copy(token = v, tokenError = false, error = null) }

    fun onNewPasswordChange(v: String) =
        _state.update { it.copy(newPassword = v, newError = false, error = null) }

    fun onConfirmChange(v: String) =
        _state.update { it.copy(confirmPassword = v, confirmError = false, error = null) }

    fun submit() {
        val s = _state.value
        if (s.token.isBlank()) {
            _state.update { it.copy(tokenError = true) }
            return
        }
        if (!Validators.isStrongEnoughPassword(s.newPassword)) {
            _state.update { it.copy(newError = true) }
            return
        }
        if (s.confirmPassword != s.newPassword) {
            _state.update { it.copy(confirmError = true) }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val result = resetPassword(s.token, s.newPassword)) {
                is AppResult.Success -> {
                    _state.update { it.copy(isSubmitting = false) }
                    _effects.send(ResetPasswordEffect.Success)
                }
                is AppResult.Failure -> {
                    // 401 genérico de auth não distingue o motivo — neste contexto
                    // (POST /auth/reset-password) só pode ser token de reset inválido.
                    val error = if (result.error == AppError.Unauthorized) {
                        AppError.Api("AUTH_RESET_INVALID")
                    } else {
                        result.error
                    }
                    _state.update {
                        it.copy(isSubmitting = false, newPassword = "", confirmPassword = "", error = error)
                    }
                }
            }
        }
    }
}
