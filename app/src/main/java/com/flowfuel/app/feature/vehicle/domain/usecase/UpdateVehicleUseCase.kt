package com.flowfuel.app.feature.vehicle.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import com.flowfuel.app.feature.vehicle.domain.model.UpdateVehicleRequest
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import javax.inject.Inject

class UpdateVehicleUseCase @Inject constructor(
    private val repository: VehicleRepository,
) {
    suspend operator fun invoke(id: Int, request: UpdateVehicleRequest): AppResult<Vehicle> =
        repository.updateVehicle(id, request)
}
