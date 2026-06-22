package com.flowfuel.app.feature.home.data

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.map
import com.flowfuel.app.core.network.apiCall
import com.flowfuel.app.feature.home.data.remote.CreateRefuelRequestDto
import com.flowfuel.app.feature.home.data.remote.DashboardApi
import com.flowfuel.app.feature.home.data.remote.RefuelApi
import com.flowfuel.app.feature.home.domain.HomeRepository
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.CreateRefuelRequest
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.vehicle.data.remote.VehicleApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepositoryImpl @Inject constructor(
    private val vehicleApi: VehicleApi,
    private val dashboardApi: DashboardApi,
    private val refuelApi: RefuelApi,
) : HomeRepository {

    override suspend fun getActiveVehicle(): AppResult<ActiveVehicleData> =
        apiCall { vehicleApi.getActiveVehicle() }.map { dto ->
            ActiveVehicleData(
                id          = dto.id,
                brand       = dto.brand,
                model       = dto.model,
                fuelSubType = dto.fuelSubType,
                capacity    = dto.capacity,
                licensePlate = dto.licensePlate,
                energyType  = dto.energyType,
                currentKm   = dto.currentKm,
            )
        }

    override suspend fun getDashboard(vehicleId: Int): AppResult<DashboardData> =
        apiCall { dashboardApi.getDashboard(vehicleId) }.map { dto ->
            DashboardData(
                averageConsumption = dto.averageConsumption,
                lastOdometer       = dto.lastOdometer ?: 0.0,
                totalSpent         = dto.totalSpent ?: 0.0,
                totalRefuels       = dto.totalRefuels ?: 0,
                lastRefuelDate     = dto.lastRefuelDate,
                lastRefuelLiters   = dto.lastRefuelLiters,
                lastRefuelAmount   = dto.lastRefuelAmount,
            )
        }

    override suspend fun createRefuel(request: CreateRefuelRequest): AppResult<Unit> =
        apiCall {
            refuelApi.createRefuel(
                CreateRefuelRequestDto(
                    vehicleId    = request.vehicleId,
                    odometer     = request.odometer,
                    energyAmount = request.liters,
                    pricePerUnit = if (request.liters > 0.0) request.totalPrice / request.liters else 0.0,
                    fullTank     = request.fullTank,
                    refuelType   = request.refuelType,
                )
            )
        }.map { Unit }
}