package com.flowfuel.app.feature.auth.presentation.changepassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.common.Validators
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auth.domain.usecase.ChangePasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val currentError: Boolean = false,
    val newError: Boolean = false,
    val confirmError: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
) {
    val canSubmit: Boolean
        get() = currentPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank() && !isSubmitting
}

sealed interface ChangePasswordEffect {
    data object Success : ChangePasswordEffect
}

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val changePassword: ChangePasswordUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    private val _effects = Channel<ChangePasswordEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onCurrentPasswordChange(v: String) =
        _state.update { it.copy(currentPassword = v, currentError = false, error = null) }

    fun onNewPasswordChange(v: String) =
        _state.update { it.copy(newPassword = v, newError = false, error = null) }

    fun onConfirmChange(v: String) =
        _state.update { it.copy(confirmPassword = v, confirmError = false, error = null) }

    fun submit() {
        val s = _state.value
        if (s.currentPassword.isBlank()) {
            _state.update { it.copy(currentError = true) }
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
            when (val result = changePassword(s.currentPassword, s.newPassword)) {
                is AppResult.Success -> {
                    _state.update { it.copy(isSubmitting = false) }
                    _effects.send(ChangePasswordEffect.Success)
                }
                is AppResult.Failure -> when (result.error) {
                    AppError.Unauthorized -> _state.update {
                        it.copy(
                            isSubmitting = false,
                            newPassword = "",
                            confirmPassword = "",
                            error = AppError.Api("AUTH_BAD_CREDENTIALS"),
                        )
                    }
                    else -> _state.update {
                        it.copy(
                            isSubmitting = false,
                            currentPassword = "",
                            newPassword = "",
                            confirmPassword = "",
                            error = result.error,
                        )
                    }
                }
            }
        }
    }
}
