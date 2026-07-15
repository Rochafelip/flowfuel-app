package com.flowfuel.app.core.vehicleshare.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class VehicleShareRequestDto(
    val vehicleId: Int,
    val inviteeEmail: String,
    val durationDays: Int,
)

@Serializable
data class VehicleShareResponseDto(
    val id: Int,
    val vehicleId: Int,
    val vehicleBrand: String?,
    val vehicleModel: String?,
    val ownerId: Int,
    val ownerName: String?,
    val guestId: Int,
    val guestName: String?,
    val status: String,
    val createdAt: String?,
    val respondedAt: String?,
    val expiresAt: String?,
)

interface VehicleShareApi {
    @POST("vehicle-shares")
    suspend fun createShare(@Body body: VehicleShareRequestDto): VehicleShareResponseDto

    @POST("vehicle-shares/{id}/accept")
    suspend fun acceptShare(@Path("id") id: Int): VehicleShareResponseDto

    @POST("vehicle-shares/{id}/reject")
    suspend fun rejectShare(@Path("id") id: Int): VehicleShareResponseDto

    @DELETE("vehicle-shares/{id}")
    suspend fun revokeShare(@Path("id") id: Int)

    @GET("vehicle-shares/vehicle/{vehicleId}")
    suspend fun getShareForVehicle(@Path("vehicleId") vehicleId: Int): VehicleShareResponseDto?

    @GET("vehicle-shares/pending")
    suspend fun getPending(): List<VehicleShareResponseDto>

    @GET("vehicle-shares/active-for-me")
    suspend fun getActiveForMe(): List<VehicleShareResponseDto>
}
