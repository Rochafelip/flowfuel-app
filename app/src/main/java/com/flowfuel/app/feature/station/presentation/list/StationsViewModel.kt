package com.flowfuel.app.feature.station.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.NearbyStationsPrefetcher
import com.flowfuel.app.feature.station.domain.model.DEFAULT_STATION_RADIUS_METERS
import com.flowfuel.app.feature.station.domain.model.GeocodeResult
import com.flowfuel.app.feature.station.domain.model.LocationResult
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.model.StationType
import com.flowfuel.app.feature.station.domain.model.stationDistanceBand
import com.flowfuel.app.feature.station.domain.usecase.GeocodeLocationsUseCase
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehicleByIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StationsViewModel @Inject constructor(
    private val getNearbyStations: GetNearbyStationsUseCase,
    private val locationProvider: LocationProvider,
    private val sessionStore: SessionStore,
    private val getVehicleById: GetVehicleByIdUseCase,
    private val stationsPrefetcher: NearbyStationsPrefetcher,
    private val geocodeLocations: GeocodeLocationsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<StationsUiState>(StationsUiState.Loading)
    val state: StateFlow<StationsUiState> = _state.asStateFlow()

    private val _radiusMeters = MutableStateFlow(DEFAULT_STATION_RADIUS_METERS)
    val radiusMeters: StateFlow<Int> = _radiusMeters.asStateFlow()

    private val _selectedType = MutableStateFlow(StationType.Fuel)
    val selectedType: StateFlow<StationType> = _selectedType.asStateFlow()

    private val _showLocationSearch = MutableStateFlow(false)
    val showLocationSearch: StateFlow<Boolean> = _showLocationSearch.asStateFlow()

    private val _locationSearchState = MutableStateFlow<LocationSearchState>(LocationSearchState.Idle)
    val locationSearchState: StateFlow<LocationSearchState> = _locationSearchState.asStateFlow()

    /** null = usa a localização atual (GPS); preenchido = busca ativa por localidade. */
    private val _selectedLocation = MutableStateFlow<GeocodeResult?>(null)
    val selectedLocation: StateFlow<GeocodeResult?> = _selectedLocation.asStateFlow()

    private val _effects = Channel<StationsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            sessionStore.activeVehicleIdFlow.firstOrNull()?.let { vehicleId ->
                val result = getVehicleById(vehicleId)
                if (result is AppResult.Success) {
                    _selectedType.value = when (result.value.energyType) {
                        EnergyType.Electric -> StationType.Electric
                        EnergyType.Combustion, EnergyType.Hybrid -> StationType.Fuel
                    }
                }
            }
            val cached = stationsPrefetcher.freshCachedStations()
            if (cached != null) {
                _state.value = if (cached.isEmpty()) StationsUiState.Empty else StationsUiState.Success(cached)
            } else {
                load()
            }
        }
    }

    fun load() {
        _state.value = StationsUiState.Loading
        val requestRadius = _radiusMeters.value
        val band = stationDistanceBand(requestRadius)
        val selected = _selectedLocation.value
        viewModelScope.launch {
            val locationResult = selected?.let { LocationResult.Available(it.location) }
                ?: locationProvider.getCurrentLocation()
            when (locationResult) {
                LocationResult.PermissionDenied -> _state.value = StationsUiState.PermissionRequired
                LocationResult.Unavailable -> _state.value = StationsUiState.LocationUnavailable
                is LocationResult.Available -> {
                    when (val result = getNearbyStations(locationResult.location, band.maxMeters)) {
                        is AppResult.Success -> {
                            val stations = result.value.filter { it.distanceMeters >= band.minMeters }
                            _state.value = if (stations.isEmpty()) {
                                StationsUiState.Empty
                            } else {
                                StationsUiState.Success(stations)
                            }
                            // Só atualiza o cache de "perto de mim" quando a busca é por GPS —
                            // salvar resultados de uma localidade pesquisada corromperia o
                            // prefetch que a Home usa pra mostrar postos rapidamente.
                            if (requestRadius == DEFAULT_STATION_RADIUS_METERS && selected == null) {
                                stationsPrefetcher.updateCache(stations)
                            }
                        }
                        is AppResult.Failure -> handleFailure(result.error)
                    }
                }
            }
        }
    }

    fun onRadiusSelected(radiusMeters: Int) {
        _radiusMeters.value = radiusMeters
        load()
    }

    fun onTypeSelected(type: StationType) {
        _selectedType.value = type
    }

    fun openLocationSearch() {
        _showLocationSearch.value = true
        _locationSearchState.value = LocationSearchState.Idle
    }

    fun closeLocationSearch() {
        _showLocationSearch.value = false
    }

    fun searchLocation(query: String) {
        if (query.isBlank()) return
        _locationSearchState.value = LocationSearchState.Loading
        viewModelScope.launch {
            when (val result = geocodeLocations(query)) {
                is AppResult.Success -> _locationSearchState.value =
                    if (result.value.isEmpty()) LocationSearchState.Empty else LocationSearchState.Success(result.value)
                is AppResult.Failure -> _locationSearchState.value = LocationSearchState.Error(result.error)
            }
        }
    }

    fun onLocationSelected(result: GeocodeResult) {
        _selectedLocation.value = result
        _showLocationSearch.value = false
        load()
    }

    fun clearSelectedLocation() {
        _selectedLocation.value = null
        load()
    }

    fun onRouteClick(station: Station) {
        val uri = "geo:${station.latitude},${station.longitude}?q=${station.latitude},${station.longitude}"
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
