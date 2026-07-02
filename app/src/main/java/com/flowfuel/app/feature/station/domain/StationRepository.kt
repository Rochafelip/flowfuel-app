package com.flowfuel.app.feature.station.domain

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.model.DEFAULT_STATION_RADIUS_METERS
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.Station

interface StationRepository {
    suspend fun getNearbyStations(location: GeoLocation, radiusMeters: Int = DEFAULT_STATION_RADIUS_METERS): AppResult<List<Station>>
}
