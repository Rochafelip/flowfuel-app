package com.flowfuel.app.feature.vehicleevent.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicleevent.domain.VehicleEventRepository
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import javax.inject.Inject

class GetVehicleEventByIdUseCase @Inject constructor(
    private val repository: VehicleEventRepository,
) {
    suspend operator fun invoke(id: Int): AppResult<VehicleEvent> =
        repository.getEventById(id)
}
