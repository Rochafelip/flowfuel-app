package com.flowfuel.app.feature.vehicleevent.data

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.map
import com.flowfuel.app.core.network.apiCall
import com.flowfuel.app.feature.vehicleevent.data.remote.CreateVehicleEventRequestDto
import com.flowfuel.app.feature.vehicleevent.data.remote.UpdateVehicleEventRequestDto
import com.flowfuel.app.feature.vehicleevent.data.remote.VehicleEventApi
import com.flowfuel.app.feature.vehicleevent.data.remote.VehicleEventResponseDto
import com.flowfuel.app.feature.vehicleevent.domain.VehicleEventRepository
import com.flowfuel.app.feature.vehicleevent.domain.model.CreateVehicleEventRequest
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.PagedVehicleEvents
import com.flowfuel.app.feature.vehicleevent.domain.model.UpdateVehicleEventRequest
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleEventRepositoryImpl @Inject constructor(
    private val api: VehicleEventApi,
) : VehicleEventRepository {

    override suspend fun createEvent(request: CreateVehicleEventRequest): AppResult<VehicleEvent> {
        val body = CreateVehicleEventRequestDto(
            vehicleId = request.vehicleId,
            category = request.category.apiValue,
            title = request.title,
            description = request.description,
            amount = request.amount,
            eventDate = request.eventDate,
            odometerKm = request.odometerKm,
            notes = request.notes,
            receiptUrl = request.receiptUrl,
        )
        return apiCall { api.createEvent(body) }.map { it.toDomain() }
    }

    override suspend fun getEventsByVehicle(
        vehicleId: Int,
        page: Int,
        category: EventCategory?,
        dateFrom: String?,
        dateTo: String?,
    ): AppResult<PagedVehicleEvents> =
        apiCall { api.getEventsByVehicle(vehicleId, page = page, size = 20, category = category?.apiValue, startDate = dateFrom, endDate = dateTo) }
            .map { paged ->
                PagedVehicleEvents(
                    items = paged.content.map { it.toDomain() },
                    currentPage = paged.page,
                    totalPages = paged.totalPages,
                    totalElements = paged.totalElements,
                )
            }

    override suspend fun getEventById(id: Int): AppResult<VehicleEvent> =
        apiCall { api.getEventById(id) }.map { it.toDomain() }

    override suspend fun updateEvent(id: Int, request: UpdateVehicleEventRequest): AppResult<VehicleEvent> {
        val body = UpdateVehicleEventRequestDto(
            category = request.category?.apiValue,
            title = request.title,
            description = request.description,
            amount = request.amount,
            eventDate = request.eventDate,
            odometerKm = request.odometerKm,
            notes = request.notes,
        )
        return apiCall { api.updateEvent(id, body) }.map { it.toDomain() }
    }

    override suspend fun deleteEvent(id: Int): AppResult<Unit> = try {
        api.deleteEvent(id)?.close()
        AppResult.Success(Unit)
    } catch (e: HttpException) {
        Timber.w("deleteEvent: HTTP ${e.code()}")
        if (e.code() == 401) AppResult.Failure(AppError.Unauthorized)
        else AppResult.Failure(AppError.Api("HTTP_${e.code()}", e.message()))
    } catch (e: IOException) {
        Timber.w(e, "deleteEvent: network error")
        AppResult.Failure(AppError.Network)
    } catch (e: Throwable) {
        Timber.e(e, "deleteEvent: unexpected error")
        AppResult.Failure(AppError.Unknown(e))
    }

    private fun VehicleEventResponseDto.toDomain() = VehicleEvent(
        id = id,
        vehicleId = vehicleId,
        category = EventCategory.entries.firstOrNull { it.apiValue == category } ?: EventCategory.OTHER,
        title = title,
        description = description,
        amount = amount,
        eventDate = eventDate,
        odometerKm = odometerKm,
        notes = notes,
        receiptUrl = receiptUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
