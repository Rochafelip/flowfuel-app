package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.history.domain.model.RefuelPage
import com.flowfuel.app.feature.history.domain.usecase.GetRefuelHistoryUseCase
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.PagedVehicleEvents
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetFinancialSummaryUseCaseTest {

    private val getRefuelHistory: GetRefuelHistoryUseCase = mockk()
    private val getVehicleEventsPage: GetVehicleEventsPageUseCase = mockk()
    private val useCase = GetFinancialSummaryUseCase(getRefuelHistory, getVehicleEventsPage)

    private fun refuel(totalPrice: Double, energyAmount: Double) = RefuelItem(
        id = 1, date = "2026-07-05", energyAmount = energyAmount, pricePerUnit = totalPrice / energyAmount,
        totalPrice = totalPrice, fullTank = true, refuelType = null, odometer = null, trip = null, consumption = null,
    )

    private fun event(amount: Double) = VehicleEvent(
        id = 1, vehicleId = 1, category = EventCategory.MAINTENANCE, title = "Revisão", description = null,
        amount = amount, eventDate = "2026-07-05", odometerKm = null, notes = null, receiptUrl = null,
        createdAt = null, updatedAt = null,
    )

    private fun refuelPage(items: List<RefuelItem>) =
        RefuelPage(items = items, hasMore = false, currentPage = 0, totalElements = items.size)

    private fun eventPage(items: List<VehicleEvent>) =
        PagedVehicleEvents(items = items, currentPage = 0, totalPages = 1, totalElements = items.size)

    @Test
    fun `sums refuels and events for current and previous month`() = runTest {
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returnsMany listOf(
            AppResult.Success(refuelPage(listOf(refuel(200.0, 40.0)))),
            AppResult.Success(refuelPage(listOf(refuel(150.0, 30.0)))),
        )
        coEvery { getVehicleEventsPage(1, 0, null, any(), any()) } returnsMany listOf(
            AppResult.Success(eventPage(listOf(event(50.0)))),
            AppResult.Success(eventPage(listOf(event(30.0)))),
        )

        val summary = (useCase(1) as AppResult.Success).value

        assertEquals(250.0, summary.currentMonthTotal, 0.001)
        assertEquals(180.0, summary.previousMonthTotal, 0.001)
    }

    @Test
    fun `computes average price per unit from current month refuels`() = runTest {
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returnsMany listOf(
            AppResult.Success(refuelPage(listOf(refuel(200.0, 40.0)))),
            AppResult.Success(refuelPage(emptyList())),
        )
        coEvery { getVehicleEventsPage(1, 0, null, any(), any()) } returns AppResult.Success(eventPage(emptyList()))

        val summary = (useCase(1) as AppResult.Success).value

        assertEquals(5.0, summary.averagePricePerUnit!!, 0.001)
    }

    @Test
    fun `averagePricePerUnit is null when there are no refuels this month`() = runTest {
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returns AppResult.Success(refuelPage(emptyList()))
        coEvery { getVehicleEventsPage(1, 0, null, any(), any()) } returns AppResult.Success(eventPage(emptyList()))

        val summary = (useCase(1) as AppResult.Success).value

        assertNull(summary.averagePricePerUnit)
    }

    @Test
    fun `percentDelta is null when previous month had no spending`() = runTest {
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returnsMany listOf(
            AppResult.Success(refuelPage(listOf(refuel(200.0, 40.0)))),
            AppResult.Success(refuelPage(emptyList())),
        )
        coEvery { getVehicleEventsPage(1, 0, null, any(), any()) } returns AppResult.Success(eventPage(emptyList()))

        val summary = (useCase(1) as AppResult.Success).value

        assertNull(summary.percentDelta)
    }

    @Test
    fun `propagates failure from refuel history`() = runTest {
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returns AppResult.Failure(AppError.Network)
        coEvery { getVehicleEventsPage(any(), any(), any(), any(), any()) } returns AppResult.Success(eventPage(emptyList()))

        val result = useCase(1)

        assertEquals(AppError.Network, (result as AppResult.Failure).error)
    }
}
