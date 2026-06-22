package com.flowfuel.app.feature.vehicleevent.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicleevent.domain.VehicleEventRepository
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.PagedVehicleEvents
import javax.inject.Inject

class GetVehicleEventsPageUseCase @Inject constructor(
    private val repository: VehicleEventRepository,
) {
    suspend operator fun invoke(
        vehicleId: Int,
        page: Int,
        category: EventCategory?,
        dateFrom: String? = null,
        dateTo: String? = null,
    ): AppResult<PagedVehicleEvents> =
        repository.getEventsByVehicle(vehicleId, page, category, dateFrom, dateTo)
}
