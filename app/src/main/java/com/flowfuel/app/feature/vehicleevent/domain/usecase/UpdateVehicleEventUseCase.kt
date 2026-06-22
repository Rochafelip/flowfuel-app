package com.flowfuel.app.feature.vehicleevent.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicleevent.domain.VehicleEventRepository
import com.flowfuel.app.feature.vehicleevent.domain.model.UpdateVehicleEventRequest
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import javax.inject.Inject

class UpdateVehicleEventUseCase @Inject constructor(
    private val repository: VehicleEventRepository,
) {
    suspend operator fun invoke(id: Int, request: UpdateVehicleEventRequest): AppResult<VehicleEvent> =
        repository.updateEvent(id, request)
}
