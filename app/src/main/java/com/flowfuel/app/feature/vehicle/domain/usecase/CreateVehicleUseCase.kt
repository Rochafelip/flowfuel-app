package com.flowfuel.app.feature.vehicle.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import javax.inject.Inject

class CreateVehicleUseCase @Inject constructor(
    private val repository: VehicleRepository,
) {
    suspend operator fun invoke(
        brand: String,
        model: String,
        manufactureYear: Int,
        modelYear: Int,
        licensePlate: String,
        color: String?,
        type: VehicleType,
        energyType: EnergyType,
        fuelType: FuelType?,
        odometerKm: Int,
        tankCapacityL: Double?,
        batteryCapacityKwh: Double?,
    ): AppResult<Vehicle> = repository.createVehicle(
        brand = brand,
        model = model,
        manufactureYear = manufactureYear,
        modelYear = modelYear,
        licensePlate = licensePlate,
        color = color,
        type = type,
        energyType = energyType,
        fuelType = fuelType,
        odometerKm = odometerKm,
        tankCapacityL = tankCapacityL,
        batteryCapacityKwh = batteryCapacityKwh,
    )
}