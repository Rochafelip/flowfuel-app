package com.flowfuel.app.feature.auth.presentation.editprofile

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.FieldError

// ─── Estado da tela ───────────────────────────────────────────────────────────

sealed interface EditProfileScreenState {
    data object Loading : EditProfileScreenState
    data object Editing : EditProfileScreenState
    data class Error(val error: AppError) : EditProfileScreenState
}

// ─── Estado global do formulário ──────────────────────────────────────────────

data class EditProfileUiState(
    val screenState: EditProfileScreenState = EditProfileScreenState.Loading,
    val name: String = "",
    val phone: String = "",
    val nameError: Boolean = false,
    val isSubmitting: Boolean = false,
    val formError: AppError? = null,
    val serverErrors: List<FieldError>? = null,
    val isDirty: Boolean = false,
    val showDiscardDialog: Boolean = false,
) {
    val canSubmit: Boolean
        get() = name.isNotBlank() && !isSubmitting && screenState is EditProfileScreenState.Editing
}

// ─── Efeitos pontuais ─────────────────────────────────────────────────────────

sealed interface EditProfileEffect {
    data object NavigateBack : EditProfileEffect
    data object NavigateBackAfterSave : EditProfileEffect
    data object NavigateToLogin : EditProfileEffect
}
