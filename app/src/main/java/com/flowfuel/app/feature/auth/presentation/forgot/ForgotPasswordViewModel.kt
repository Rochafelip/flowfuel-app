package com.flowfuel.app.feature.auth.presentation.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.common.Validators
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auth.domain.usecase.ForgotPasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ForgotUiState(
    val email: String = "",
    val emailError: Boolean = false,
    val isSubmitting: Boolean = false,
    val sent: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val forgot: ForgotPasswordUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotUiState())
    val state: StateFlow<ForgotUiState> = _state.asStateFlow()

    fun onEmailChange(v: String) = _state.update { it.copy(email = v, emailError = false, error = null) }

    fun submit() {
        val s = _state.value
        if (!Validators.isEmail(s.email)) {
            _state.update { it.copy(emailError = true) }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val result = forgot(s.email)) {
                is AppResult.Success -> _state.update { it.copy(isSubmitting = false, sent = true) }
                is AppResult.Failure -> _state.update { it.copy(isSubmitting = false, error = result.error) }
            }
        }
    }
}
