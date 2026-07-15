package com.flowfuel.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetActiveSharedVehiclesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

data class MainContainerUiState(
    val isGuestMode: Boolean = false,
    val guestVehicle: VehicleShare? = null,
)

@HiltViewModel
class MainContainerViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val getActiveSharedVehicles: GetActiveSharedVehiclesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MainContainerUiState())
    val state: StateFlow<MainContainerUiState> = _state.asStateFlow()

    init {
        sessionStore.activeVehicleIsGuestFlow
            .combine(sessionStore.activeVehicleIdFlow) { isGuest, vehicleId -> isGuest to vehicleId }
            .onEach { (isGuest, vehicleId) ->
                if (!isGuest || vehicleId == null) {
                    _state.value = MainContainerUiState(isGuestMode = false, guestVehicle = null)
                    return@onEach
                }
                val share = (getActiveSharedVehicles() as? AppResult.Success)
                    ?.value?.firstOrNull { it.vehicleId == vehicleId }
                _state.value = MainContainerUiState(isGuestMode = true, guestVehicle = share)
            }
            .launchIn(viewModelScope)
    }
}
