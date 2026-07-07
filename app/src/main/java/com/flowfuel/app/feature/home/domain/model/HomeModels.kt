package com.flowfuel.app.feature.home.domain.model

import com.flowfuel.app.feature.vehicle.domain.model.VehicleType

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
    val photoUrl: String? = null,
    val vehicleType: VehicleType = VehicleType.Car,
)

/** Consumo separado por modal para veículos HYBRID. */
data class HybridConsumptionBreakdown(
    val fuelConsumption: Double?,
    val fuelConsumptionUnit: String?,
    val electricConsumption: Double?,
    val electricConsumptionUnit: String?,
)

/** Dados do painel de controle para um veículo. */
data class DashboardData(
    val averageConsumption: Double?,
    /** Unidade de consumo informada pelo backend (ex: "km/L", "km/kWh"); null para HYBRID. */
    val consumptionUnit: String?,
    val totalSpent: Double,
    val totalRefuels: Int,
    val lastRefuelDate: String?,
    /** Litros ou kWh do último abastecimento — vem de /refuels, não do endpoint de dashboard. */
    val lastRefuelEnergyAmount: Double?,
    val lastRefuelAmount: Double?,
    /** Unidade do último abastecimento (ex: "L", "kWh"), inferida do refuelType/energyType. */
    val lastRefuelEnergyUnit: String?,
    /** Detalhamento por combustão/elétrico; preenchido apenas para HYBRID. */
    val hybridBreakdown: HybridConsumptionBreakdown? = null,
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