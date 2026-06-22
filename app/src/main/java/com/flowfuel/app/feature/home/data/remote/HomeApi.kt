package com.flowfuel.app.feature.home.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// ─── Dashboard ────────────────────────────────────────────────────────────────
// Espelha DashboardDTO do backend (.claude/docs_api/openapi.yaml). Para veículos
// HYBRID, totalEnergy/averagePrice/averageConsumption/energyUnit/priceUnit/
// consumptionUnit vêm null e o detalhamento por tipo vem em [breakdown].

@Serializable
data class FuelMetricsDto(
    val totalEnergy: Double? = null,
    val totalSpent: Double? = null,
    val averagePrice: Double? = null,
    val averageConsumption: Double? = null,
    val energyUnit: String? = null,
    val priceUnit: String? = null,
    val consumptionUnit: String? = null,
)

@Serializable
data class HybridBreakdownDto(
    val fuel: FuelMetricsDto? = null,
    val electric: FuelMetricsDto? = null,
)

@Serializable
data class DashboardResponseDto(
    val vehicleId: Int? = null,
    val energyType: String? = null,
    val totalRefuels: Int? = null,
    val totalSpent: Double? = null,
    val totalEnergy: Double? = null,
    val averagePrice: Double? = null,
    val averageConsumption: Double? = null,
    val energyUnit: String? = null,
    val priceUnit: String? = null,
    val consumptionUnit: String? = null,
    val breakdown: HybridBreakdownDto? = null,
    val lastRefuelDate: String? = null,
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