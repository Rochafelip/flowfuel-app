package com.flowfuel.app.feature.auth.presentation.editprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auth.domain.usecase.GetProfileUseCase
import com.flowfuel.app.feature.auth.domain.usecase.UpdateProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val getProfile: GetProfileUseCase,
    private val updateProfile: UpdateProfileUseCase,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private var initialState: EditProfileUiState? = null

    private val _state = MutableStateFlow(EditProfileUiState())
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()

    private val _effects = Channel<EditProfileEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init { load() }

    // ─── Carregamento ─────────────────────────────────────────────────────────

    fun load() {
        _state.update { it.copy(screenState = EditProfileScreenState.Loading) }
        viewModelScope.launch {
            when (val result = getProfile()) {
                is AppResult.Success -> {
                    val profile = result.value
                    val loaded = EditProfileUiState(
                        screenState = EditProfileScreenState.Editing,
                        name = profile.name ?: "",
                        phone = profile.phone ?: "",
                    )
                    initialState = loaded
                    _state.value = loaded
                }
                is AppResult.Failure -> handleLoadError(result.error)
            }
        }
    }

    // ─── Handlers do formulário ───────────────────────────────────────────────

    fun onNameChange(v: String) =
        updateWithDirtyCheck { it.copy(name = v, nameError = false, formError = null, serverErrors = null) }

    fun onPhoneChange(v: String) =
        updateWithDirtyCheck { it.copy(phone = v, formError = null, serverErrors = null) }

    fun clearError() = _state.update { it.copy(formError = null) }

    // ─── Navegação / descarte ─────────────────────────────────────────────────

    fun onBackPressed() {
        if (_state.value.isDirty) {
            _state.update { it.copy(showDiscardDialog = true) }
        } else {
            viewModelScope.launch { _effects.send(EditProfileEffect.NavigateBack) }
        }
    }

    fun onDiscardConfirm() {
        _state.update { it.copy(showDiscardDialog = false) }
        viewModelScope.launch { _effects.send(EditProfileEffect.NavigateBack) }
    }

    fun onDiscardDismiss() {
        _state.update { it.copy(showDiscardDialog = false) }
    }

    // ─── Submissão ────────────────────────────────────────────────────────────

    fun submit() {
        val s = _state.value
        if (s.screenState !is EditProfileScreenState.Editing || s.isSubmitting) return

        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = true) }
            return
        }

        _state.update { it.copy(isSubmitting = true, formError = null) }

        viewModelScope.launch {
            when (val result = updateProfile(
                name = s.name.trim(),
                phone = s.phone.trim().takeIf { it.isNotBlank() },
            )) {
                is AppResult.Success -> _effects.send(EditProfileEffect.NavigateBackAfterSave)
                is AppResult.Failure -> {
                    if (result.error == AppError.Unauthorized) {
                        sessionStore.clear()
                        _effects.send(EditProfileEffect.NavigateToLogin)
                    } else {
                        val apiErr = result.error as? AppError.Api
                        val fieldErrors = apiErr?.takeIf { it.code == "VALIDATION_FAILED" }?.fieldErrors
                        if (!fieldErrors.isNullOrEmpty()) {
                            _state.update { it.copy(isSubmitting = false, serverErrors = fieldErrors) }
                        } else {
                            _state.update { it.copy(isSubmitting = false, formError = result.error) }
                        }
                    }
                }
            }
        }
    }

    // ─── Helpers privados ─────────────────────────────────────────────────────

    private fun updateWithDirtyCheck(transform: (EditProfileUiState) -> EditProfileUiState) {
        _state.update { current ->
            val updated = transform(current)
            val init = initialState ?: return@update updated
            updated.copy(isDirty = updated.name != init.name || updated.phone != init.phone)
        }
    }

    private suspend fun handleLoadError(error: AppError) {
        if (error == AppError.Unauthorized) {
            sessionStore.clear()
            _effects.send(EditProfileEffect.NavigateToLogin)
        } else {
            _state.update { it.copy(screenState = EditProfileScreenState.Error(error)) }
        }
    }
}
