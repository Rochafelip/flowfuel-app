package com.flowfuel.app.feature.station.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class StationResponseDto(
    val placeId: String,
    val name: String,
    /** "FUEL" ou "ELECTRIC" — ver StationRepositoryImpl.toDomain(). */
    val type: String,
    val distanceMeters: Int,
    val rating: Double? = null,
    val latitude: Double,
    val longitude: Double,
)

interface StationApi {
    /** Backend próprio, que proxeia OSM Overpass (postos de combustível) e Open Charge Map (recarga elétrica). */
    @GET("stations/nearby")
    suspend fun getNearbyStations(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radiusMeters: Int = 5000,
    ): List<StationResponseDto>
}
