package com.flowfuel.app.feature.auth.presentation.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.common.Validators
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.FieldError
import com.flowfuel.app.feature.auth.domain.usecase.RegisterUseCase
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

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val phone: String = "",
    val nameError: Boolean = false,
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
    val confirmError: Boolean = false,
    val phoneError: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    val serverErrors: List<FieldError>? = null,
    val rateLimitCooldown: Int = 0,
) {
    val canSubmit: Boolean
        get() = name.isNotBlank() && email.isNotBlank() && password.isNotBlank() &&
                confirmPassword.isNotBlank() && phone.isNotBlank() && !isSubmitting &&
                rateLimitCooldown == 0
}

sealed interface RegisterEffect {
    data class NavigateToCheckEmail(val email: String) : RegisterEffect
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val register: RegisterUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    private val _effects = Channel<RegisterEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onNameChange(v: String)    = _state.update { it.copy(name = v, nameError = false, error = null, serverErrors = null) }
    fun onEmailChange(v: String)   = _state.update { it.copy(email = v, emailError = false, error = null, serverErrors = null) }
    fun onPasswordChange(v: String)= _state.update { it.copy(password = v, passwordError = false, confirmError = false, error = null, serverErrors = null) }
    fun onConfirmChange(v: String) = _state.update { it.copy(confirmPassword = v, confirmError = false, error = null, serverErrors = null) }
    fun onPhoneChange(v: String)   = _state.update { it.copy(phone = v, phoneError = false, error = null, serverErrors = null) }

    fun submit() {
        val s = _state.value
        val nameInvalid     = s.name.isBlank()
        val emailInvalid    = !Validators.isEmail(s.email)
        val passwordInvalid = !Validators.isStrongEnoughPassword(s.password)
        val confirmInvalid  = s.password != s.confirmPassword
        val phoneInvalid    = !Validators.isPhone(s.phone)

        if (nameInvalid || emailInvalid || passwordInvalid || confirmInvalid || phoneInvalid) {
            _state.update {
                it.copy(
                    nameError     = nameInvalid,
                    emailError    = emailInvalid,
                    passwordError = passwordInvalid,
                    confirmError  = confirmInvalid,
                    phoneError    = phoneInvalid,
                )
            }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val result = register(s.name, s.email, s.password, s.phone)) {
                is AppResult.Success -> {
                    _state.update { it.copy(isSubmitting = false) }
                    _effects.send(RegisterEffect.NavigateToCheckEmail(result.value))
                }
                is AppResult.Failure -> {
                    val err = result.error
                    if (err is AppError.RateLimited) {
                        _state.update { it.copy(isSubmitting = false, error = err) }
                        startRateLimitCooldown(err.retryAfterSeconds)
                        return@launch
                    }
                    val apiErr = err as? AppError.Api
                    val fieldErrors = apiErr?.takeIf { it.code == "VALIDATION_FAILED" }?.fieldErrors
                    if (!fieldErrors.isNullOrEmpty()) {
                        _state.update { it.copy(isSubmitting = false, serverErrors = fieldErrors) }
                    } else {
                        _state.update { it.copy(isSubmitting = false, error = err) }
                    }
                }
            }
        }
    }

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
}
