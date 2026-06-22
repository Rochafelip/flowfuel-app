package com.flowfuel.app.feature.vehicle.presentation.odometer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.usecase.UpdateOdometerUseCase
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
class UpdateOdometerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val updateOdometer: UpdateOdometerUseCase,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val vehicleId: Int = checkNotNull(savedStateHandle["vehicleId"])
    private val currentKm: Int = checkNotNull(savedStateHandle["currentKm"])

    private val _state = MutableStateFlow(UpdateOdometerUiState(currentKm = currentKm))
    val state: StateFlow<UpdateOdometerUiState> = _state.asStateFlow()

    private val _effects = Channel<UpdateOdometerEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onNewKmChange(value: String) {
        val parsed = value.toIntOrNull()
        val regression = parsed != null && parsed < currentKm
        _state.update { it.copy(newKm = value, regressionError = regression, formError = null) }
    }

    fun clearError() = _state.update { it.copy(formError = null) }

    fun confirm() {
        val s = _state.value
        if (!s.canConfirm) return
        val newKm = s.newKm.toIntOrNull() ?: return

        _state.update { it.copy(isSaving = true, formError = null) }
        viewModelScope.launch {
            when (val result = updateOdometer(vehicleId, currentKm, newKm)) {
                is AppResult.Success -> {
                    _effects.send(UpdateOdometerEffect.NavigateBackWithResult(newKm))
                }
                is AppResult.Failure -> {
                    _state.update { it.copy(isSaving = false) }
                    val error = result.error
                    if (error == AppError.Unauthorized) {
                        sessionStore.clear()
                        _effects.send(UpdateOdometerEffect.NavigateToLogin)
                    } else {
                        _state.update { it.copy(formError = error) }
                    }
                }
            }
        }
    }
}
