package com.flowfuel.app.feature.vehicle.domain.model

enum class VehicleType(val apiValue: String) {
    Car("Carro"),
    Motorcycle("Moto"),
}

enum class EnergyType(val apiValue: String) {
    Combustion("COMBUSTION"),
    Electric("ELECTRIC"),
    Hybrid("HYBRID"),
}
enum class FuelType(val apiValue: String) {
    Gasoline("Gasolina comum"),
    Ethanol("Etanol"),
    Diesel("Diesel"),
    Flex("Flex"),
    GNV("GNV"),
}

data class Vehicle(
    val id: Int,
    val brand: String,
    val model: String,
    /** Pode ser null em veículos cadastrados antes da validação obrigatória. */
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
    val isActive: Boolean,
)