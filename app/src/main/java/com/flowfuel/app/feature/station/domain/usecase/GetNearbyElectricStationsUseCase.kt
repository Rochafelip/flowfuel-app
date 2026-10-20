package com.flowfuel.app.feature.station.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.StationRepository
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.Station
import javax.inject.Inject

class GetNearbyElectricStationsUseCase @Inject constructor(
    private val repository: StationRepository,
) {
    suspend operator fun invoke(location: GeoLocation, radiusMeters: Int): AppResult<List<Station>> =
        repository.getNearbyElectricStations(location, radiusMeters)
}
