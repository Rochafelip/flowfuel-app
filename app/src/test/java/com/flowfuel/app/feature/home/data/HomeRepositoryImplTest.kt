package com.flowfuel.app.feature.home.data

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.data.remote.HistoryApi
import com.flowfuel.app.feature.history.data.remote.RefuelHistoryPageDto
import com.flowfuel.app.feature.history.data.remote.RefuelItemDto
import com.flowfuel.app.feature.home.data.remote.DashboardApi
import com.flowfuel.app.feature.home.data.remote.DashboardResponseDto
import com.flowfuel.app.feature.home.data.remote.FuelMetricsDto
import com.flowfuel.app.feature.home.data.remote.HybridBreakdownDto
import com.flowfuel.app.feature.home.data.remote.RefuelApi
import com.flowfuel.app.feature.vehicle.data.remote.VehicleApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRepositoryImplTest {

    private val vehicleApi: VehicleApi = mockk()
    private val dashboardApi: DashboardApi = mockk()
    private val refuelApi: RefuelApi = mockk()
    private val historyApi: HistoryApi = mockk()

    private val repository = HomeRepositoryImpl(vehicleApi, dashboardApi, refuelApi, historyApi)

    @Test
    fun `getDashboard mapeia priceUnit, preco do ultimo abastecimento e breakdown hibrido completo`() = runTest {
        coEvery { dashboardApi.getDashboard(1) } returns DashboardResponseDto(
            energyType = "HYBRID",
            totalSpent = 500.0,
            totalRefuels = 3,
            priceUnit = "R$/L",
            lastRefuelDate = "2026-08-10",
            lastOdometer = 50_000,
            breakdown = HybridBreakdownDto(
                fuel = FuelMetricsDto(
                    averageConsumption = 12.5,
                    consumptionUnit = "km/L",
                    averagePrice = 5.89,
                    priceUnit = "R$/L",
                    totalSpent = 300.0,
                ),
                electric = FuelMetricsDto(
                    averageConsumption = 6.2,
                    consumptionUnit = "km/kWh",
                    averagePrice = 0.85,
                    priceUnit = "R$/kWh",
                    totalSpent = 200.0,
                ),
            ),
        )
        coEvery { historyApi.getRefuelHistory(1, page = 0, size = 1) } returns RefuelHistoryPageDto(
            content = listOf(
                RefuelItemDto(
                    id = 1,
                    refuelDate = "2026-08-10",
                    energyAmount = 42.3,
                    pricePerUnit = 6.97,
                    totalAmount = 294.83,
                    refuelType = "FUEL",
                ),
            ),
        )

        val result = repository.getDashboard(1) as AppResult.Success

        assertEquals("R$/L", result.value.priceUnit)
        assertEquals(6.97, result.value.lastRefuelPricePerUnit)
        assertEquals(5.89, result.value.hybridBreakdown?.fuelAveragePrice)
        assertEquals("R$/L", result.value.hybridBreakdown?.fuelPriceUnit)
        assertEquals(300.0, result.value.hybridBreakdown?.fuelTotalSpent)
        assertEquals(0.85, result.value.hybridBreakdown?.electricAveragePrice)
        assertEquals("R$/kWh", result.value.hybridBreakdown?.electricPriceUnit)
        assertEquals(200.0, result.value.hybridBreakdown?.electricTotalSpent)
    }
}
