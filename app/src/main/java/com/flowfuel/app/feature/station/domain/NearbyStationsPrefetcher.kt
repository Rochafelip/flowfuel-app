package com.flowfuel.app.feature.station.domain

import com.flowfuel.app.core.common.Clock
import com.flowfuel.app.core.common.IoDispatcher
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.model.DEFAULT_STATION_RADIUS_METERS
import com.flowfuel.app.feature.station.domain.model.LocationResult
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NearbyStationsPrefetcher @Inject constructor(
    private val getNearbyStations: GetNearbyStationsUseCase,
    private val locationProvider: LocationProvider,
    private val clock: Clock,
    @IoDispatcher dispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var activeJob: Job? = null

    private data class CachedStations(val stations: List<Station>, val fetchedAtMillis: Long)
    private val cache = MutableStateFlow<CachedStations?>(null)

    fun prefetch() {
        activeJob?.cancel()
        activeJob = scope.launch {
            when (val locationResult = locationProvider.getCurrentLocation()) {
                is LocationResult.Available -> {
                    val result = getNearbyStations(locationResult.location, DEFAULT_STATION_RADIUS_METERS)
                    if (result is AppResult.Success) {
                        cache.value = CachedStations(result.value, clock.nowMillis())
                    }
                    // Failure: no-op, mantém cache anterior.
                }
                // PermissionDenied/Unavailable: no-op, mantém cache anterior.
                else -> Unit
            }
        }
    }

    fun freshCachedStations(): List<Station>? {
        val entry = cache.value ?: return null
        return entry.stations.takeIf { clock.nowMillis() - entry.fetchedAtMillis <= CACHE_TTL_MILLIS }
    }

    fun updateCache(stations: List<Station>) {
        cache.value = CachedStations(stations, clock.nowMillis())
    }

    private companion object {
        const val CACHE_TTL_MILLIS = 5 * 60 * 1000L
    }
}
