package com.flowfuel.app.feature.history.presentation.details

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.feature.history.domain.model.RefuelItem

sealed interface RefuelDetailsScreenState {
    data object Loading : RefuelDetailsScreenState
    data class Error(val error: AppError) : RefuelDetailsScreenState
    data class Success(val item: RefuelItem) : RefuelDetailsScreenState
}

data class RefuelDetailsUiState(
    val screenState: RefuelDetailsScreenState = RefuelDetailsScreenState.Loading,
    val showDeleteConfirm: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteError: AppError? = null,
)

sealed interface RefuelDetailsEffect {
    data object Deleted : RefuelDetailsEffect
}
