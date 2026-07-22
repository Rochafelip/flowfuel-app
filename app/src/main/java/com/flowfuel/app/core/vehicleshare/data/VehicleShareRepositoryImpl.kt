package com.flowfuel.app.core.vehicleshare.data

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.network.apiCall
import com.flowfuel.app.core.vehicleshare.data.remote.VehicleShareApi
import com.flowfuel.app.core.vehicleshare.data.remote.VehicleShareRequestDto
import com.flowfuel.app.core.vehicleshare.data.remote.VehicleShareResponseDto
import com.flowfuel.app.core.vehicleshare.domain.VehicleShareRepository
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShareStatus
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

private fun VehicleShareResponseDto.toDomain() = VehicleShare(
    id = id,
    vehicleId = vehicleId,
    vehicleBrand = vehicleBrand.orEmpty(),
    vehicleModel = vehicleModel.orEmpty(),
    ownerId = ownerId,
    ownerName = ownerName.orEmpty(),
    guestId = guestId,
    guestName = guestName.orEmpty(),
    status = VehicleShareStatus.fromApi(status),
    createdAt = createdAt,
    respondedAt = respondedAt,
    expiresAt = expiresAt,
)

@Singleton
class VehicleShareRepositoryImpl @Inject constructor(
    private val api: VehicleShareApi,
) : VehicleShareRepository {

    override suspend fun invite(vehicleId: Int, inviteeEmail: String, durationDays: Int): AppResult<VehicleShare> =
        apiCall { api.createShare(VehicleShareRequestDto(vehicleId, inviteeEmail, durationDays)).toDomain() }

    override suspend fun accept(shareId: Int): AppResult<VehicleShare> =
        apiCall { api.acceptShare(shareId).toDomain() }

    override suspend fun reject(shareId: Int): AppResult<VehicleShare> =
        apiCall { api.rejectShare(shareId).toDomain() }

    override suspend fun revoke(shareId: Int): AppResult<Unit> =
        apiCall { api.revokeShare(shareId) }

    override suspend fun getForVehicle(vehicleId: Int): AppResult<VehicleShare?> =
        apiCall {
            val response = api.getShareForVehicle(vehicleId)
            if (!response.isSuccessful) throw HttpException(response)
            response.body()?.toDomain()
        }

    override suspend fun getPending(): AppResult<List<VehicleShare>> =
        apiCall { api.getPending().map { it.toDomain() } }

    override suspend fun getActiveForMe(): AppResult<List<VehicleShare>> =
        apiCall { api.getActiveForMe().map { it.toDomain() } }
}
