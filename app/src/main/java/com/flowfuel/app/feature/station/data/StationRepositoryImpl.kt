package com.flowfuel.app.feature.station.data

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.map
import com.flowfuel.app.core.network.apiCall
import com.flowfuel.app.feature.station.data.remote.StationApi
import com.flowfuel.app.feature.station.data.remote.StationResponseDto
import com.flowfuel.app.feature.station.domain.StationRepository
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.model.StationType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StationRepositoryImpl @Inject constructor(
    private val api: StationApi,
) : StationRepository {

    override suspend fun getNearbyStations(location: GeoLocation, radiusMeters: Int): AppResult<List<Station>> =
        apiCall {
            api.getNearbyStations(
                lat = location.latitude,
                lng = location.longitude,
                radiusMeters = radiusMeters,
            )
        }.map { list -> list.map { it.toDomain() }.sortedBy { it.distanceMeters } }

    private fun StationResponseDto.toDomain(): Station = Station(
        placeId = placeId,
        name = name,
        type = if (type.equals("ELECTRIC", ignoreCase = true)) StationType.Electric else StationType.Fuel,
        distanceMeters = distanceMeters,
        rating = rating,
        latitude = latitude,
        longitude = longitude,
        street = street,
        houseNumber = houseNumber,
    )
}
