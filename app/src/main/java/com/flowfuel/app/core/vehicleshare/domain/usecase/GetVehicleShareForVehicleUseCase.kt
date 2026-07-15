package com.flowfuel.app.core.vehicleshare.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.VehicleShareRepository
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import javax.inject.Inject

class GetVehicleShareForVehicleUseCase @Inject constructor(
    private val repository: VehicleShareRepository,
) {
    suspend operator fun invoke(vehicleId: Int): AppResult<VehicleShare?> = repository.getForVehicle(vehicleId)
}
