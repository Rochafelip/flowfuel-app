package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.history.domain.model.RefuelPage
import com.flowfuel.app.feature.history.domain.usecase.GetRefuelHistoryUseCase
import com.flowfuel.app.feature.home.domain.model.SpendSlice
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
import org.junit.Assert.assertTrue
import org.junit.Test

private val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE

class GetMonthlySpendBreakdownUseCaseTest {

    private val getRefuelHistory: GetRefuelHistoryUseCase = mockk()
    private val getVehicleEventsPage: GetVehicleEventsPageUseCase = mockk()
    private val useCase = GetMonthlySpendBreakdownUseCase(getRefuelHistory, getVehicleEventsPage)

    private fun refuel(totalPrice: Double, energyAmount: Double = 40.0) = RefuelItem(
        id = 1, date = "2026-08-05", energyAmount = energyAmount, pricePerUnit = totalPrice / energyAmount,
        totalPrice = totalPrice, fullTank = true, refuelType = null, odometer = null, trip = null, consumption = null,
    )

    private fun event(category: EventCategory, amount: Double) = VehicleEvent(
        id = 1, vehicleId = 1, category = category, title = category.label, description = null,
        amount = amount, eventDate = "2026-08-05", odometerKm = null, notes = null, receiptUrl = null,
        createdAt = null, updatedAt = null,
    )

    private fun refuelPage(items: List<RefuelItem>) =
        RefuelPage(items = items, hasMore = false, currentPage = 0, totalElements = items.size)

    private fun eventPage(items: List<VehicleEvent>) =
        PagedVehicleEvents(items = items, currentPage = 0, totalPages = 1, totalElements = items.size)

    @Test
    fun `sums current month refuels into the Combustível slice and groups events by category`() = runTest {
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returnsMany listOf(
            AppResult.Success(refuelPage(listOf(refuel(200.0)))),
            AppResult.Success(refuelPage(emptyList())),
        )
        coEvery { getVehicleEventsPage(1, 0, null, any(), any()) } returnsMany listOf(
            AppResult.Success(eventPage(listOf(event(EventCategory.MAINTENANCE, 40.0)))),
            AppResult.Success(eventPage(emptyList())),
        )

        val summary = (useCase(1) as AppResult.Success).value

        assertEquals(240.0, summary.breakdown.totalSpent, 0.001)
        assertEquals(
            listOf(SpendSlice("Combustível", 200.0), SpendSlice("Manutenção", 40.0)),
            summary.breakdown.slices,
        )
    }

    @Test
    fun `requests the current and previous month date windows`() = runTest {
        val refuelFromCalls = mutableListOf<LocalDate>()
        val refuelToCalls = mutableListOf<LocalDate>()
        val eventFromCalls = mutableListOf<String>()
        val eventToCalls = mutableListOf<String>()

        coEvery {
            getRefuelHistory(1, 0, 50, capture(refuelFromCalls), capture(refuelToCalls))
        } returns AppResult.Success(refuelPage(emptyList()))
        coEvery {
            getVehicleEventsPage(1, 0, null, capture(eventFromCalls), capture(eventToCalls))
        } returns AppResult.Success(eventPage(emptyList()))

        useCase(1)

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
    fun `computes previousMonthTotal from previous month refuels and events`() = runTest {
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returnsMany listOf(
            AppResult.Success(refuelPage(emptyList())),
            AppResult.Success(refuelPage(listOf(refuel(150.0)))),
        )
        coEvery { getVehicleEventsPage(1, 0, null, any(), any()) } returnsMany listOf(
            AppResult.Success(eventPage(emptyList())),
            AppResult.Success(eventPage(listOf(event(EventCategory.MAINTENANCE, 30.0)))),
        )

        val summary = (useCase(1) as AppResult.Success).value

        assertEquals(180.0, summary.previousMonthTotal, 0.001)
    }

    @Test
    fun `percentDelta compares current month total to previous month total`() = runTest {
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returnsMany listOf(
            AppResult.Success(refuelPage(listOf(refuel(120.0)))),
            AppResult.Success(refuelPage(listOf(refuel(100.0)))),
        )
        coEvery { getVehicleEventsPage(1, 0, null, any(), any()) } returns AppResult.Success(eventPage(emptyList()))

        val summary = (useCase(1) as AppResult.Success).value

        assertEquals(20.0, summary.percentDelta!!, 0.001)
    }

    @Test
    fun `percentDelta is null when previous month had no spending`() = runTest {
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returnsMany listOf(
            AppResult.Success(refuelPage(listOf(refuel(200.0)))),
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

        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.Network, (result as AppResult.Failure).error)
    }

    @Test
    fun `propagates failure from vehicle events`() = runTest {
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returns AppResult.Success(refuelPage(emptyList()))
        coEvery { getVehicleEventsPage(any(), any(), any(), any(), any()) } returns AppResult.Failure(AppError.Network)

        val result = useCase(1)

        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.Network, (result as AppResult.Failure).error)
    }
}
