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
    val street: String? = null,
    val houseNumber: String? = null,
)

@Serializable
data class GeocodeResultDto(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
)

interface StationApi {
    /** Backend próprio, que proxeia Google Places (gas_station). */
    @GET("stations/nearby-google")
    suspend fun getNearbyFuelStations(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radiusMeters: Int = 5000,
    ): List<StationResponseDto>

    /** Backend próprio, que proxeia Open Charge Map (electric_vehicle_charging_station). */
    @GET("stations/nearby")
    suspend fun getNearbyElectricStations(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radiusMeters: Int = 5000,
    ): List<StationResponseDto>

    @GET("stations/geocode")
    suspend fun getGeocode(@Query("query") query: String): List<GeocodeResultDto>
}
