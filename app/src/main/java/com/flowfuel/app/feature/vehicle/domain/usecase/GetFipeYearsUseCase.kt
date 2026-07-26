package com.flowfuel.app.feature.vehicle.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.FipeRepository
import com.flowfuel.app.feature.vehicle.domain.model.FipeOption
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import javax.inject.Inject

class GetFipeYearsUseCase @Inject constructor(
    private val repository: FipeRepository,
) {
    suspend operator fun invoke(vehicleType: VehicleType, brandCode: String, modelCode: String): AppResult<List<FipeOption>> =
        repository.getYears(vehicleType, brandCode, modelCode)
}
