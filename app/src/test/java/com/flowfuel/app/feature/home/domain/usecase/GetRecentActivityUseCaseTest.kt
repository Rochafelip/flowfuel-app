package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.history.domain.model.RefuelPage
import com.flowfuel.app.feature.history.domain.usecase.GetRefuelHistoryUseCase
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.PagedVehicleEvents
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleTimelineItem
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetRecentActivityUseCaseTest {

    private val getRefuelHistory: GetRefuelHistoryUseCase = mockk()
    private val getVehicleEventsPage: GetVehicleEventsPageUseCase = mockk()
    private val useCase = GetRecentActivityUseCase(getRefuelHistory, getVehicleEventsPage)

    private fun refuel(id: Int, date: String) = RefuelItem(
        id = id, date = date, energyAmount = 40.0, pricePerUnit = 5.0, totalPrice = 200.0,
        fullTank = true, refuelType = null, odometer = null, trip = null, consumption = null,
    )

    private fun event(id: Int, date: String) = VehicleEvent(
        id = id, vehicleId = 1, category = EventCategory.MAINTENANCE, title = "Revisão", description = null,
        amount = 100.0, eventDate = date, odometerKm = null, notes = null, receiptUrl = null,
        createdAt = null, updatedAt = null,
    )

    @Test
    fun `merges refuels and events sorted by date descending, limited to 4`() = runTest {
        coEvery { getRefuelHistory(1, 0, 4) } returns AppResult.Success(
            RefuelPage(items = listOf(refuel(1, "2026-07-01"), refuel(2, "2026-06-15")), hasMore = false, currentPage = 0, totalElements = 2)
        )
        coEvery { getVehicleEventsPage(1, 0, null) } returns AppResult.Success(
            PagedVehicleEvents(
                items = listOf(event(1, "2026-07-05"), event(2, "2026-06-01"), event(3, "2026-05-01")),
                currentPage = 0, totalPages = 1, totalElements = 3,
            )
        )

        val timeline = (useCase(1) as AppResult.Success).value

        assertEquals(4, timeline.size)
        assertEquals("2026-07-05", timeline[0].sortDate)
        assertEquals("2026-07-01", timeline[1].sortDate)
        assertEquals("2026-06-15", timeline[2].sortDate)
        assertEquals("2026-06-01", timeline[3].sortDate)
        assertEquals(VehicleTimelineItem.EventEntry::class, timeline[0]::class)
        assertEquals(VehicleTimelineItem.RefuelEntry::class, timeline[1]::class)
        assertEquals(VehicleTimelineItem.RefuelEntry::class, timeline[2]::class)
        assertEquals(VehicleTimelineItem.EventEntry::class, timeline[3]::class)
    }

    @Test
    fun `propagates failure from events page`() = runTest {
        coEvery { getRefuelHistory(1, 0, 4) } returns AppResult.Success(
            RefuelPage(items = emptyList(), hasMore = false, currentPage = 0, totalElements = 0)
        )
        coEvery { getVehicleEventsPage(1, 0, null) } returns AppResult.Failure(AppError.Network)

        val result = useCase(1)

        assertEquals(AppError.Network, (result as AppResult.Failure).error)
    }
}
