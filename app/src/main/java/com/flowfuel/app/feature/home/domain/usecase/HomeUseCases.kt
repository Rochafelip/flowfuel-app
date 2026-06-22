package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.home.domain.HomeRepository
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.CreateRefuelRequest
import com.flowfuel.app.feature.home.domain.model.DashboardData
import javax.inject.Inject

class GetActiveVehicleUseCase @Inject constructor(private val repo: HomeRepository) {
    suspend operator fun invoke(): AppResult<ActiveVehicleData> = repo.getActiveVehicle()
}

class GetDashboardUseCase @Inject constructor(private val repo: HomeRepository) {
    suspend operator fun invoke(vehicleId: Int): AppResult<DashboardData> =
        repo.getDashboard(vehicleId)
}

class CreateRefuelUseCase @Inject constructor(private val repo: HomeRepository) {
    suspend operator fun invoke(request: CreateRefuelRequest): AppResult<Unit> =
        repo.createRefuel(request)
}