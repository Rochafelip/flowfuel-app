package com.flowfuel.app.feature.vehicleevent.domain

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicleevent.domain.model.CreateVehicleEventRequest
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.PagedVehicleEvents
import com.flowfuel.app.feature.vehicleevent.domain.model.UpdateVehicleEventRequest
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent

interface VehicleEventRepository {
    suspend fun createEvent(request: CreateVehicleEventRequest): AppResult<VehicleEvent>
    suspend fun getEventsByVehicle(
        vehicleId: Int,
        page: Int,
        category: EventCategory?,
        dateFrom: String? = null,
        dateTo: String? = null,
    ): AppResult<PagedVehicleEvents>
    suspend fun getEventById(id: Int): AppResult<VehicleEvent>
    suspend fun updateEvent(id: Int, request: UpdateVehicleEventRequest): AppResult<VehicleEvent>
    suspend fun deleteEvent(id: Int): AppResult<Unit>
}
