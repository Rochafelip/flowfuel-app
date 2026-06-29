package com.flowfuel.app.feature.auth.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.common.Validators
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.FieldError
import com.flowfuel.app.feature.auth.domain.usecase.LoginUseCase
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

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    val serverErrors: List<FieldError>? = null,
    val rateLimitCooldown: Int = 0,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isSubmitting && rateLimitCooldown == 0
}

sealed interface LoginEffect {
    data object NavigateHome : LoginEffect
    data class NavigateToCheckEmail(val email: String) : LoginEffect
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val login: LoginUseCase,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _effects = Channel<LoginEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        // Motivo de um logout forçado (ex: refresh token revogado/expirado em
        // outra tela) — consumido uma única vez para explicar ao usuário por
        // que ele caiu de volta no login.
        sessionStore.consumeForcedLogoutReason()?.let { code ->
            _state.update { it.copy(error = AppError.Api(code)) }
        }
    }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = false, error = null, serverErrors = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = false, error = null, serverErrors = null) }

    private fun startRateLimitCooldown(seconds: Int?) {
        val duration = seconds ?: 60
        viewModelScope.launch {
            for (remaining in duration downTo 1) {
                _state.update { it.copy(rateLimitCooldown = remaining) }
                delay(1_000L)
            }
            _state.update { it.copy(rateLimitCooldown = 0) }
        }
    }

    fun submit() {
        val current = _state.value
        val emailInvalid = !Validators.isEmail(current.email)
        val passwordInvalid = current.password.isBlank()
        if (emailInvalid || passwordInvalid) {
            _state.update { it.copy(emailError = emailInvalid, passwordError = passwordInvalid) }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val result = login(current.email, current.password)) {
                is AppResult.Success -> {
                    _state.update { it.copy(isSubmitting = false) }
                    _effects.send(LoginEffect.NavigateHome)
                }
                is AppResult.Failure -> {
                    val err = result.error
                    if (err is AppError.RateLimited) {
                        _state.update { it.copy(isSubmitting = false, error = err) }
                        startRateLimitCooldown(err.retryAfterSeconds)
                        return@launch
                    }
                    val apiErr = err as? AppError.Api
                    if (apiErr?.code == "ACCOUNT_NOT_ACTIVATED") {
                        _state.update { it.copy(isSubmitting = false) }
                        _effects.send(LoginEffect.NavigateToCheckEmail(current.email))
                        return@launch
                    }
                    val fieldErrors = apiErr?.takeIf { it.code == "VALIDATION_FAILED" }?.fieldErrors
                    if (!fieldErrors.isNullOrEmpty()) {
                        _state.update { it.copy(isSubmitting = false, serverErrors = fieldErrors) }
                    } else {
                        // E-mail/senha errados chegam como 401 AUTH_BAD_CREDENTIALS, mas
                        // apiCall() colapsa todo 401 em AppError.Unauthorized antes do
                        // code ser lido — sem isso a tela mostraria "sessão expirada"
                        // para uma simples senha errada. Mesmo padrão de reinterpretação
                        // local já usado em ChangePasswordViewModel.
                        val error = if (err == AppError.Unauthorized) {
                            AppError.Api("INVALID_CREDENTIALS")
                        } else {
                            err
                        }
                        _state.update { it.copy(isSubmitting = false, error = error) }
                    }
                }
            }
        }
    }
}
