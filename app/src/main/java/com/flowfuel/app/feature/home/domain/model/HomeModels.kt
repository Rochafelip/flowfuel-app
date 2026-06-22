package com.flowfuel.app.feature.home.domain.model

/**
 * Representação simplificada do veículo ativo para a tela principal.
 * Evita acoplar o domínio de Home ao domínio de Vehicle.
 */
data class ActiveVehicleData(
    val id: Int,
    val brand: String,
    val model: String,
    val fuelSubType: String?,
    val capacity: Double?,
    val licensePlate: String?,
    val energyType: String,
    val currentKm: Int,
)

/** Dados do painel de controle para um veículo. */
data class DashboardData(
    val averageConsumption: Double?,
    val lastOdometer: Double,
    val totalSpent: Double,
    val totalRefuels: Int,
    val lastRefuelDate: String?,
    val lastRefuelLiters: Double?,
    val lastRefuelAmount: Double?,
) {
    val hasRefuels: Boolean get() = totalRefuels > 0
}

/** Parâmetros para registrar um novo abastecimento. */
data class CreateRefuelRequest(
    val vehicleId: Int,
    val odometer: Double,
    val liters: Double,
    val totalPrice: Double,
    val fullTank: Boolean,
    /** null para combustão/elétrico puro; "FUEL" ou "ELECTRIC" para híbridos. */
    val refuelType: String? = null,
)