package com.flowfuel.app.feature.vehicleevent.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicleevent.domain.VehicleEventRepository
import javax.inject.Inject

class DeleteVehicleEventUseCase @Inject constructor(
    private val repository: VehicleEventRepository,
) {
    suspend operator fun invoke(id: Int): AppResult<Unit> =
        repository.deleteEvent(id)
}
