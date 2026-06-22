package com.flowfuel.app.feature.home.domain

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.CreateRefuelRequest
import com.flowfuel.app.feature.home.domain.model.DashboardData

interface HomeRepository {
    suspend fun getActiveVehicle(): AppResult<ActiveVehicleData>
    suspend fun getDashboard(vehicleId: Int): AppResult<DashboardData>
    suspend fun createRefuel(request: CreateRefuelRequest): AppResult<Unit>
}