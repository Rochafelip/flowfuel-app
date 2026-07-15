package com.flowfuel.app.core.vehicleshare.domain

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare

interface VehicleShareRepository {
    suspend fun invite(vehicleId: Int, inviteeEmail: String, durationDays: Int): AppResult<VehicleShare>
    suspend fun accept(shareId: Int): AppResult<VehicleShare>
    suspend fun reject(shareId: Int): AppResult<VehicleShare>
    suspend fun revoke(shareId: Int): AppResult<Unit>
    suspend fun getForVehicle(vehicleId: Int): AppResult<VehicleShare?>
    suspend fun getPending(): AppResult<List<VehicleShare>>
    suspend fun getActiveForMe(): AppResult<List<VehicleShare>>
}
