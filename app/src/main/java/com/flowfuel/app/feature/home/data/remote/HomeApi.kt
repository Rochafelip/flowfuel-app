package com.flowfuel.app.feature.home.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// ─── Dashboard ────────────────────────────────────────────────────────────────

@Serializable
data class DashboardResponseDto(
    val averageConsumption: Double? = null,
    val lastOdometer: Double? = null,
    val totalSpent: Double? = null,
    val totalRefuels: Int? = null,
    val lastRefuelDate: String? = null,
    val lastRefuelLiters: Double? = null,
    val lastRefuelAmount: Double? = null,
)

// ─── Abastecimento ────────────────────────────────────────────────────────────

@Serializable
data class CreateRefuelRequestDto(
    val vehicleId: Int,
    val odometer: Double,
    val energyAmount: Double,
    val pricePerUnit: Double,
    val fullTank: Boolean,
    val refuelType: String? = null,
)

@Serializable
data class RefuelResponseDto(
    val id: Int,
    val vehicleId: Int,
    val odometer: Double,
    val energyAmount: Double,
    val pricePerUnit: Double,
    val fullTank: Boolean,
    val createdAt: String? = null,
)

// ─── Interfaces ───────────────────────────────────────────────────────────────

interface DashboardApi {
    @GET("dashboard/vehicle/{vehicleId}")
    suspend fun getDashboard(@Path("vehicleId") vehicleId: Int): DashboardResponseDto
}

interface RefuelApi {
    @POST("refuels")
    suspend fun createRefuel(@Body body: CreateRefuelRequestDto): RefuelResponseDto
}