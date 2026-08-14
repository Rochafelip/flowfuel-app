package com.flowfuel.app.feature.station.domain

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.GeocodeResult
import com.flowfuel.app.feature.station.domain.model.Station

interface StationRepository {
    suspend fun getNearbyStations(location: GeoLocation, radiusMeters: Int): AppResult<List<Station>>
    suspend fun geocode(query: String): AppResult<List<GeocodeResult>>
}
