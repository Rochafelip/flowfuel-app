package com.flowfuel.app.feature.vehicle.domain.usecase

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import javax.inject.Inject

class UpdateOdometerUseCase @Inject constructor(
    private val repository: VehicleRepository,
) {
    suspend operator fun invoke(
        vehicleId: Int,
        currentKm: Int,
        newKm: Int,
    ): AppResult<Unit> {
        if (newKm < currentKm) {
            return AppResult.Failure(
                AppError.Api(
                    code = "ODOMETER_REGRESSION",
                    message = "O novo valor deve ser maior ou igual ao odômetro atual",
                )
            )
        }
        return repository.updateOdometer(vehicleId, newKm)
    }
}
