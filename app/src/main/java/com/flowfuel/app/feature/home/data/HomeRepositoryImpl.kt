package com.flowfuel.app.feature.home.data

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.map
import com.flowfuel.app.core.network.apiCall
import com.flowfuel.app.feature.history.data.remote.HistoryApi
import com.flowfuel.app.feature.history.data.remote.RefuelItemDto
import com.flowfuel.app.feature.home.data.remote.CreateRefuelRequestDto
import com.flowfuel.app.feature.home.data.remote.DashboardApi
import com.flowfuel.app.feature.home.data.remote.DashboardResponseDto
import com.flowfuel.app.feature.home.data.remote.RefuelApi
import com.flowfuel.app.feature.home.domain.HomeRepository
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.CreateRefuelRequest
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.home.domain.model.HybridConsumptionBreakdown
import com.flowfuel.app.feature.vehicle.data.remote.VehicleApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepositoryImpl @Inject constructor(
    private val vehicleApi: VehicleApi,
    private val dashboardApi: DashboardApi,
    private val refuelApi: RefuelApi,
    private val historyApi: HistoryApi,
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
            // O endpoint de dashboard não traz litros/valor do último abastecimento
            // (não existem no DashboardDTO real) — busca-se à parte em /refuels,
            // que já é ordenado por data desc (mais recente primeiro).
            val lastRefuel = (apiCall { historyApi.getRefuelHistory(vehicleId, page = 0, size = 1) }
                as? AppResult.Success)?.value?.content?.firstOrNull()
            buildDashboardData(dto, lastRefuel)
        }

    private fun buildDashboardData(dto: DashboardResponseDto, lastRefuel: RefuelItemDto?) = DashboardData(
        averageConsumption     = dto.averageConsumption,
        consumptionUnit        = dto.consumptionUnit,
        totalSpent              = dto.totalSpent ?: 0.0,
        totalRefuels            = dto.totalRefuels ?: 0,
        lastRefuelDate          = dto.lastRefuelDate,
        lastRefuelEnergyAmount  = lastRefuel?.energyAmount,
        lastRefuelAmount        = lastRefuel?.totalAmount,
        lastRefuelEnergyUnit    = lastRefuelEnergyUnit(dto.energyType, lastRefuel?.refuelType),
        hybridBreakdown         = dto.breakdown?.let { b ->
            HybridConsumptionBreakdown(
                fuelConsumption         = b.fuel?.averageConsumption,
                fuelConsumptionUnit     = b.fuel?.consumptionUnit ?: "km/L",
                electricConsumption     = b.electric?.averageConsumption,
                electricConsumptionUnit = b.electric?.consumptionUnit ?: "km/kWh",
            )
        },
    )

    private fun lastRefuelEnergyUnit(vehicleEnergyType: String?, refuelType: String?): String? =
        when {
            vehicleEnergyType == "HYBRID" -> when (refuelType) {
                "ELECTRIC" -> "kWh"
                "FUEL" -> "L"
                else -> null
            }
            vehicleEnergyType == "ELECTRIC" -> "kWh"
            vehicleEnergyType == "COMBUSTION" -> "L"
            else -> null
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