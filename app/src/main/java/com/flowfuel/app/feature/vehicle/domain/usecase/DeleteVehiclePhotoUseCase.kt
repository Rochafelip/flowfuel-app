package com.flowfuel.app.feature.vehicle.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import javax.inject.Inject

class DeleteVehiclePhotoUseCase @Inject constructor(
    private val repository: VehicleRepository,
) {
    suspend operator fun invoke(vehicleId: Int): AppResult<Unit> =
        repository.deletePhoto(vehicleId)
}
