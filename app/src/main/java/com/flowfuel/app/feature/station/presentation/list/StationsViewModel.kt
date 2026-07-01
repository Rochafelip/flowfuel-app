package com.flowfuel.app.feature.station.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.model.LocationResult
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StationsViewModel @Inject constructor(
    private val getNearbyStations: GetNearbyStationsUseCase,
    private val locationProvider: LocationProvider,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow<StationsUiState>(StationsUiState.Loading)
    val state: StateFlow<StationsUiState> = _state.asStateFlow()

    private val _effects = Channel<StationsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _state.value = StationsUiState.Loading
        viewModelScope.launch {
            when (val locationResult = locationProvider.getCurrentLocation()) {
                LocationResult.PermissionDenied -> _state.value = StationsUiState.PermissionRequired
                LocationResult.Unavailable -> _state.value = StationsUiState.LocationUnavailable
                is LocationResult.Available -> {
                    when (val result = getNearbyStations(locationResult.location)) {
                        is AppResult.Success -> _state.value = if (result.value.isEmpty()) {
                            StationsUiState.Empty
                        } else {
                            StationsUiState.Success(result.value)
                        }
                        is AppResult.Failure -> handleFailure(result.error)
                    }
                }
            }
        }
    }

    fun onRouteClick(station: Station) {
        val uri = "google.navigation:q=${station.latitude},${station.longitude}"
        viewModelScope.launch { _effects.send(StationsEffect.OpenNavigation(uri)) }
    }

    private suspend fun handleFailure(error: AppError) {
        if (error == AppError.Unauthorized) {
            sessionStore.clear()
            _effects.send(StationsEffect.NavigateToLogin)
        } else {
            _state.value = StationsUiState.Error(error)
        }
    }
}
