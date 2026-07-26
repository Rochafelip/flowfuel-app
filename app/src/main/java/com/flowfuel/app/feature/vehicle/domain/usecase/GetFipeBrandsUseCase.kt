package com.flowfuel.app.feature.vehicle.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.FipeRepository
import com.flowfuel.app.feature.vehicle.domain.model.FipeOption
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import javax.inject.Inject

class GetFipeBrandsUseCase @Inject constructor(
    private val repository: FipeRepository,
) {
    suspend operator fun invoke(vehicleType: VehicleType): AppResult<List<FipeOption>> =
        repository.getBrands(vehicleType)
}
