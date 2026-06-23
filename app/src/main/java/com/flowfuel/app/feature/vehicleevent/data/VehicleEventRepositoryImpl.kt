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
            type = request.category.apiValue,
            description = combineDescription(request.title, request.description, request.notes),
            amount = request.amount,
            eventDate = request.eventDate,
            odometer = request.odometerKm,
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
        apiCall { api.getEventsByVehicle(vehicleId, page = page, size = 20, type = category?.apiValue, startDate = dateFrom, endDate = dateTo) }
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
            type = request.category?.apiValue,
            description = combineDescription(request.title, request.description, request.notes),
            amount = request.amount,
            eventDate = request.eventDate,
            odometer = request.odometerKm,
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

    private fun VehicleEventResponseDto.toDomain(): VehicleEvent {
        val (title, desc) = splitDescription(description, category = type)
        return VehicleEvent(
            id = id,
            vehicleId = vehicleId,
            category = EventCategory.entries.firstOrNull { it.apiValue == type } ?: EventCategory.OTHER,
            title = title,
            description = desc,
            amount = amount,
            eventDate = eventDate,
            odometerKm = odometer,
            notes = null,
            receiptUrl = null,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private companion object {
        /**
         * O backend não tem campos title/notes — só [description] (ver
         * VehicleEventRequestDTO no openapi.yaml). Título, descrição e
         * observações da UI são combinados em uma única string, separados por
         * linha em branco, e desfeitos em [splitDescription] na leitura.
         */
        fun combineDescription(title: String?, description: String?, notes: String?): String =
            listOfNotNull(
                title?.trim()?.takeIf { it.isNotBlank() },
                description?.trim()?.takeIf { it.isNotBlank() },
                notes?.trim()?.takeIf { it.isNotBlank() },
            ).joinToString("\n\n")

        /** Inverso de [combineDescription]: primeira linha = título, resto = descrição. */
        fun splitDescription(raw: String?, category: String): Pair<String, String?> {
            val text = raw.orEmpty()
            val separatorIndex = text.indexOf("\n\n")
            return if (separatorIndex >= 0) {
                text.substring(0, separatorIndex) to text.substring(separatorIndex + 2).takeIf { it.isNotBlank() }
            } else {
                text.ifBlank {
                    EventCategory.entries.firstOrNull { it.apiValue == category }?.label ?: category
                } to null
            }
        }
    }
}
