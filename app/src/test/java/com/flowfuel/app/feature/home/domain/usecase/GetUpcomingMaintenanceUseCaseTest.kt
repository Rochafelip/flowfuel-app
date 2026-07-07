package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.datastore.VehicleMaintenancePrefsStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceType
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.PagedVehicleEvents
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetUpcomingMaintenanceUseCaseTest {

    private val getVehicleEventsPage: GetVehicleEventsPageUseCase = mockk()
    private val prefsStore: VehicleMaintenancePrefsStore = mockk()
    private val useCase = GetUpcomingMaintenanceUseCase(getVehicleEventsPage, prefsStore)

    private fun event(category: EventCategory, odometerKm: Int?, eventDate: String) = VehicleEvent(
        id = 1, vehicleId = 1, category = category, title = "Evento", description = null,
        amount = null, eventDate = eventDate, odometerKm = odometerKm, notes = null,
        receiptUrl = null, createdAt = null, updatedAt = null,
    )

    private fun eventPage(items: List<VehicleEvent>) =
        PagedVehicleEvents(items = items, currentPage = 0, totalPages = 1, totalElements = items.size)

    private fun stubNoEvents() {
        coEvery { getVehicleEventsPage(1, 0, EventCategory.OIL_CHANGE) } returns AppResult.Success(eventPage(emptyList()))
        coEvery { getVehicleEventsPage(1, 0, EventCategory.TIRES) } returns AppResult.Success(eventPage(emptyList()))
    }

    private fun stubNoAnchors() {
        every { prefsStore.anchorKmFlow(1, EventCategory.OIL_CHANGE) } returns flowOf(null)
        every { prefsStore.anchorKmFlow(1, EventCategory.TIRES) } returns flowOf(null)
        coEvery { prefsStore.saveAnchorKm(any(), any(), any()) } returns Unit
    }

    private fun stubNoLicensing() {
        every { prefsStore.licensingDueDateFlow(1) } returns flowOf(null)
    }

    @Test
    fun `oil change uses last event odometer plus interval, ignoring any anchor`() = runTest {
        coEvery { getVehicleEventsPage(1, 0, EventCategory.OIL_CHANGE) } returns AppResult.Success(
            eventPage(listOf(event(EventCategory.OIL_CHANGE, odometerKm = 60_000, eventDate = "2026-01-10"))),
        )
        coEvery { getVehicleEventsPage(1, 0, EventCategory.TIRES) } returns AppResult.Success(eventPage(emptyList()))
        stubNoAnchors()
        stubNoLicensing()

        val items = (useCase(1, currentKm = 65_000) as AppResult.Success).value
        val oil = items.first { it.type == UpcomingMaintenanceType.OIL_CHANGE }

        // due = 60_000 + 10_000 = 70_000; remaining = 70_000 - 65_000
        assertEquals(5_000, oil.remainingKm)
        assertFalse(oil.isOverdue)
        coVerify(exactly = 0) { prefsStore.saveAnchorKm(1, EventCategory.OIL_CHANGE, any()) }
    }

    @Test
    fun `oil change with no event and no anchor saves current km as anchor and returns full interval`() = runTest {
        stubNoEvents()
        stubNoAnchors()
        stubNoLicensing()

        val items = (useCase(1, currentKm = 50_000) as AppResult.Success).value
        val oil = items.first { it.type == UpcomingMaintenanceType.OIL_CHANGE }

        assertEquals(10_000, oil.remainingKm)
        coVerify(exactly = 1) { prefsStore.saveAnchorKm(1, EventCategory.OIL_CHANGE, 50_000) }
    }

    @Test
    fun `oil change with no event but an existing anchor reuses it without saving again`() = runTest {
        stubNoEvents()
        every { prefsStore.anchorKmFlow(1, EventCategory.OIL_CHANGE) } returns flowOf(50_000)
        every { prefsStore.anchorKmFlow(1, EventCategory.TIRES) } returns flowOf(null)
        coEvery { prefsStore.saveAnchorKm(any(), any(), any()) } returns Unit
        stubNoLicensing()

        // O odômetro avançou desde que a âncora foi gravada; due continua fixo em 50_000 + 10_000.
        val items = (useCase(1, currentKm = 58_000) as AppResult.Success).value
        val oil = items.first { it.type == UpcomingMaintenanceType.OIL_CHANGE }

        assertEquals(2_000, oil.remainingKm)
        coVerify(exactly = 0) { prefsStore.saveAnchorKm(1, EventCategory.OIL_CHANGE, any()) }
    }

    @Test
    fun `negative remainingKm marks the item overdue`() = runTest {
        coEvery { getVehicleEventsPage(1, 0, EventCategory.OIL_CHANGE) } returns AppResult.Success(
            eventPage(listOf(event(EventCategory.OIL_CHANGE, odometerKm = 60_000, eventDate = "2026-01-10"))),
        )
        coEvery { getVehicleEventsPage(1, 0, EventCategory.TIRES) } returns AppResult.Success(eventPage(emptyList()))
        stubNoAnchors()
        stubNoLicensing()

        // due = 70_000; currentKm já passou disso.
        val items = (useCase(1, currentKm = 71_500) as AppResult.Success).value
        val oil = items.first { it.type == UpcomingMaintenanceType.OIL_CHANGE }

        assertEquals(-1_500, oil.remainingKm)
        assertTrue(oil.isOverdue)
    }

    @Test
    fun `licensing without a due date returns needsSetup`() = runTest {
        stubNoEvents()
        stubNoAnchors()
        every { prefsStore.licensingDueDateFlow(1) } returns flowOf(null)

        val items = (useCase(1, currentKm = 50_000) as AppResult.Success).value
        val licensing = items.first { it.type == UpcomingMaintenanceType.LICENSING }

        assertTrue(licensing.needsSetup)
        assertNull(licensing.remainingDays)
    }

    @Test
    fun `licensing with a future due date computes remainingDays and is not overdue`() = runTest {
        stubNoEvents()
        stubNoAnchors()
        val futureDate = LocalDate.now().plusDays(18)
        every { prefsStore.licensingDueDateFlow(1) } returns flowOf(futureDate.toString())

        val items = (useCase(1, currentKm = 50_000) as AppResult.Success).value
        val licensing = items.first { it.type == UpcomingMaintenanceType.LICENSING }

        assertEquals(18, licensing.remainingDays)
        assertFalse(licensing.isOverdue)
    }

    @Test
    fun `licensing with a past due date is overdue`() = runTest {
        stubNoEvents()
        stubNoAnchors()
        val pastDate = LocalDate.now().minusDays(3)
        every { prefsStore.licensingDueDateFlow(1) } returns flowOf(pastDate.toString())

        val items = (useCase(1, currentKm = 50_000) as AppResult.Success).value
        val licensing = items.first { it.type == UpcomingMaintenanceType.LICENSING }

        assertEquals(-3, licensing.remainingDays)
        assertTrue(licensing.isOverdue)
    }

    @Test
    fun `propagates failure from the oil change lookup without querying tires`() = runTest {
        coEvery { getVehicleEventsPage(1, 0, EventCategory.OIL_CHANGE) } returns AppResult.Failure(AppError.Network)

        val result = useCase(1, currentKm = 50_000)

        assertEquals(AppError.Network, (result as AppResult.Failure).error)
        coVerify(exactly = 0) { getVehicleEventsPage(1, 0, EventCategory.TIRES) }
    }
}
