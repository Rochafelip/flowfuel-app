package com.flowfuel.app.core.vehicleshare.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.VehicleShareRepository
import javax.inject.Inject

class RevokeVehicleShareUseCase @Inject constructor(
    private val repository: VehicleShareRepository,
) {
    suspend operator fun invoke(shareId: Int): AppResult<Unit> = repository.revoke(shareId)
}
