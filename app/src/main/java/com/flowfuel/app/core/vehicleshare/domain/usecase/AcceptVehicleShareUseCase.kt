package com.flowfuel.app.core.vehicleshare.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.VehicleShareRepository
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import javax.inject.Inject

class AcceptVehicleShareUseCase @Inject constructor(
    private val repository: VehicleShareRepository,
) {
    suspend operator fun invoke(shareId: Int): AppResult<VehicleShare> = repository.accept(shareId)
}
