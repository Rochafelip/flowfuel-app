package com.flowfuel.app.core.vehicleshare.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.VehicleShareRepository
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import javax.inject.Inject

class InviteVehicleShareUseCase @Inject constructor(
    private val repository: VehicleShareRepository,
) {
    suspend operator fun invoke(vehicleId: Int, inviteeEmail: String, durationDays: Int): AppResult<VehicleShare> =
        repository.invite(vehicleId, inviteeEmail, durationDays)
}
