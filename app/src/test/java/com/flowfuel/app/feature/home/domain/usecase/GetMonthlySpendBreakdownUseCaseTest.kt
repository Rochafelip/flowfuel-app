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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetMonthlySpendBreakdownUseCaseTest {

    private val getRefuelHistory: GetRefuelHistoryUseCase = mockk()
    private val getVehicleEventsPage: GetVehicleEventsPageUseCase = mockk()
    private val useCase = GetMonthlySpendBreakdownUseCase(getRefuelHistory, getVehicleEventsPage)

    private fun refuel(totalPrice: Double) = RefuelItem(
        id = 1, date = "2026-08-05", energyAmount = 40.0, pricePerUnit = totalPrice / 40.0,
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
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returns AppResult.Success(refuelPage(listOf(refuel(200.0))))
        coEvery { getVehicleEventsPage(1, 0, null, any(), any()) } returns AppResult.Success(
            eventPage(listOf(event(EventCategory.MAINTENANCE, 40.0)))
        )

        val breakdown = (useCase(1) as AppResult.Success).value

        assertEquals(240.0, breakdown.totalSpent, 0.001)
        assertEquals(
            listOf(SpendSlice("Combustível", 200.0), SpendSlice("Manutenção", 40.0)),
            breakdown.slices,
        )
    }

    @Test
    fun `requests the current month date window`() = runTest {
        val refuelFromCalls = mutableListOf<LocalDate>()
        val refuelToCalls = mutableListOf<LocalDate>()
        coEvery {
            getRefuelHistory(1, 0, 50, capture(refuelFromCalls), capture(refuelToCalls))
        } returns AppResult.Success(refuelPage(emptyList()))
        coEvery { getVehicleEventsPage(1, 0, null, any(), any()) } returns AppResult.Success(eventPage(emptyList()))

        useCase(1)

        val today = LocalDate.now()
        assertEquals(today.withDayOfMonth(1), refuelFromCalls.single())
        assertEquals(today, refuelToCalls.single())
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
