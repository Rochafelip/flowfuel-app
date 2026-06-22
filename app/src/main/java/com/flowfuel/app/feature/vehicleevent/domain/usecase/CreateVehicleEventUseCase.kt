package com.flowfuel.app.feature.vehicleevent.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicleevent.domain.VehicleEventRepository
import com.flowfuel.app.feature.vehicleevent.domain.model.CreateVehicleEventRequest
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import javax.inject.Inject

class CreateVehicleEventUseCase @Inject constructor(
    private val repository: VehicleEventRepository,
) {
    suspend operator fun invoke(request: CreateVehicleEventRequest): AppResult<VehicleEvent> =
        repository.createEvent(request)
}
