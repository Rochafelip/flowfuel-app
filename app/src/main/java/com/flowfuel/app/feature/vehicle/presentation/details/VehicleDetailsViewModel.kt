package com.flowfuel.app.feature.vehicle.presentation.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehicleByIdUseCase
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
class VehicleDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getVehicleById: GetVehicleByIdUseCase,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val vehicleId: Int = checkNotNull(savedStateHandle["vehicleId"])

    private val _state = MutableStateFlow(VehicleDetailsUiState())
    val state: StateFlow<VehicleDetailsUiState> = _state.asStateFlow()

    private val _effects = Channel<VehicleDetailsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init { load() }

    // ─── Carregamento ─────────────────────────────────────────────────────────

    fun load() {
        _state.update { it.copy(screenState = VehicleDetailsScreenState.Loading) }
        fetchVehicle()
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        fetchVehicle()
    }

    // ─── Ações da tela ────────────────────────────────────────────────────────

    fun onEditClick() {
        viewModelScope.launch {
            _effects.send(VehicleDetailsEffect.NavigateToEdit(vehicleId))
        }
    }

    fun onUpdateOdometerClick() {
        val vehicle = (_state.value.screenState as? VehicleDetailsScreenState.Success)?.vehicle ?: return
        viewModelScope.launch {
            _effects.send(VehicleDetailsEffect.NavigateToUpdateOdometer(vehicleId, vehicle.odometerKm))
        }
    }

    fun onViewEventsClick() {
        viewModelScope.launch {
            _effects.send(VehicleDetailsEffect.NavigateToEvents(vehicleId))
        }
    }

    // ─── Helpers privados ─────────────────────────────────────────────────────

    private fun fetchVehicle() {
        viewModelScope.launch {
            when (val result = getVehicleById(vehicleId)) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        screenState = VehicleDetailsScreenState.Success(result.value),
                        isRefreshing = false,
                    )
                }
                is AppResult.Failure -> {
                    _state.update { it.copy(isRefreshing = false) }
                    handleError(result.error)
                }
            }
        }
    }

    private suspend fun handleError(error: AppError) {
        if (error == AppError.Unauthorized) {
            sessionStore.clear()
            _effects.send(VehicleDetailsEffect.NavigateToLogin)
        } else {
            _state.update { it.copy(screenState = VehicleDetailsScreenState.Error(error)) }
        }
    }
}
