package com.flowfuel.app.feature.history.presentation.edit

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.feature.home.presentation.RefuelFormState

sealed interface EditRefuelScreenState {
    data object Loading : EditRefuelScreenState
    data class Error(val error: AppError) : EditRefuelScreenState
    data object Ready : EditRefuelScreenState
}

data class EditRefuelUiState(
    val screenState: EditRefuelScreenState = EditRefuelScreenState.Loading,
    val form: RefuelFormState = RefuelFormState(),
    val isSubmitting: Boolean = false,
    val submitError: AppError? = null,
    val vehicleEnergyType: String = "FUEL",
    val vehicleId: Int = 0,
)

sealed interface EditRefuelEffect {
    data object Saved : EditRefuelEffect
}
