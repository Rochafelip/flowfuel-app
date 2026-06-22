package com.flowfuel.app.feature.vehicle.domain.model

data class UpdateVehicleRequest(
    val brand: String,
    val model: String,
    val manufactureYear: Int?,
    val modelYear: Int?,
    val licensePlate: String?,
    val color: String?,
    val type: VehicleType,
    val energyType: EnergyType,
    val fuelType: FuelType?,
    val odometerKm: Int,
    val tankCapacityL: Double?,
    val batteryCapacityKwh: Double?,
)
