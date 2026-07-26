package com.flowfuel.app.feature.vehicle.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.FipeRepository
import com.flowfuel.app.feature.vehicle.domain.model.FipeOption
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import javax.inject.Inject

class GetFipeModelsUseCase @Inject constructor(
    private val repository: FipeRepository,
) {
    suspend operator fun invoke(vehicleType: VehicleType, brandCode: String): AppResult<List<FipeOption>> =
        repository.getModels(vehicleType, brandCode)
}
