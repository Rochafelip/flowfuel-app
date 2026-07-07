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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE

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
        val refuelFromCalls = mutableListOf<LocalDate>()
        val refuelToCalls = mutableListOf<LocalDate>()
        val eventFromCalls = mutableListOf<String>()
        val eventToCalls = mutableListOf<String>()

        coEvery {
            getRefuelHistory(1, 0, 50, capture(refuelFromCalls), capture(refuelToCalls))
        } returnsMany listOf(
            AppResult.Success(refuelPage(listOf(refuel(200.0, 40.0)))),
            AppResult.Success(refuelPage(listOf(refuel(150.0, 30.0)))),
        )
        coEvery {
            getVehicleEventsPage(1, 0, null, capture(eventFromCalls), capture(eventToCalls))
        } returnsMany listOf(
            AppResult.Success(eventPage(listOf(event(50.0)))),
            AppResult.Success(eventPage(listOf(event(30.0)))),
        )

        val summary = (useCase(1) as AppResult.Success).value

        assertEquals(250.0, summary.currentMonthTotal, 0.001)
        assertEquals(180.0, summary.previousMonthTotal, 0.001)

        // Verify the actual date windows requested, not just call order: a bug that swaps
        // current/previous windows or miscalculates month boundaries must fail these asserts.
        val today = LocalDate.now()
        val expectedCurrentStart = today.withDayOfMonth(1)
        val previousMonth = today.minusMonths(1)
        val expectedPreviousStart = previousMonth.withDayOfMonth(1)
        val expectedPreviousEnd = previousMonth.withDayOfMonth(previousMonth.lengthOfMonth())

        assertEquals(2, refuelFromCalls.size)
        assertEquals(expectedCurrentStart, refuelFromCalls[0])
        assertEquals(today, refuelToCalls[0])
        assertEquals(expectedPreviousStart, refuelFromCalls[1])
        assertEquals(expectedPreviousEnd, refuelToCalls[1])

        assertEquals(2, eventFromCalls.size)
        assertEquals(expectedCurrentStart.format(isoFmt), eventFromCalls[0])
        assertEquals(today.format(isoFmt), eventToCalls[0])
        assertEquals(expectedPreviousStart.format(isoFmt), eventFromCalls[1])
        assertEquals(expectedPreviousEnd.format(isoFmt), eventToCalls[1])
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
