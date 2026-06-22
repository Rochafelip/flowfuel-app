package com.flowfuel.app.feature.vehicle.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import com.flowfuel.app.feature.vehicle.domain.model.PagedVehicles
import javax.inject.Inject

class GetVehiclesPageUseCase @Inject constructor(
    private val repository: VehicleRepository,
) {
    suspend operator fun invoke(page: Int): AppResult<PagedVehicles> =
        repository.getVehiclesPage(page)
}
