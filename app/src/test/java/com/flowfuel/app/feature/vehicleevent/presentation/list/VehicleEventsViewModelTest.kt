package com.flowfuel.app.feature.vehicleevent.presentation.list

import androidx.lifecycle.SavedStateHandle
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.HistoryRepository
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.history.domain.model.RefuelPage
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehicleByIdUseCase
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.EventDateFilter
import com.flowfuel.app.feature.vehicleevent.domain.model.PagedVehicleEvents
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleTimelineItem
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VehicleEventsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val getEventsPage: GetVehicleEventsPageUseCase = mockk()
    private val getVehicleById: GetVehicleByIdUseCase = mockk(relaxed = true)
    private val sessionStore: SessionStore = mockk(relaxed = true)
    private val historyRepository: HistoryRepository = mockk()

    private val savedStateHandle = SavedStateHandle(mapOf("vehicleId" to 1))

    private val emptyEventsPage = PagedVehicleEvents(items = emptyList(), currentPage = 0, totalPages = 1, totalElements = 0)
    private val emptyRefuelPage = RefuelPage(items = emptyList(), hasMore = false, currentPage = 0)

    private fun makeRefuel(id: Int, date: String, refuelType: String? = null) = RefuelItem(
        id = id,
        date = date,
        energyAmount = 40.0,
        pricePerUnit = 5.89,
        totalPrice = 235.6,
        fullTank = true,
        refuelType = refuelType,
        odometer = 50000.0,
        trip = 400.0,
        consumption = 10.0,
    )

    private fun makeEvent(id: Int, date: String, category: EventCategory = EventCategory.FUEL) = VehicleEvent(
        id = id,
        vehicleId = 1,
        category = category,
        title = "Evento $id",
        description = null,
        amount = 100.0,
        eventDate = date,
        odometerKm = null,
        notes = null,
        receiptUrl = null,
        createdAt = null,
        updatedAt = null,
    )

    private fun buildViewModel(): VehicleEventsViewModel =
        VehicleEventsViewModel(savedStateHandle, getEventsPage, getVehicleById, sessionStore, historyRepository)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── shouldIncludeRefuels ──────────────────────────────────────────────────

    @Test
    fun `refuels included when category is null (Todas)`() = runTest {
        coEvery { getEventsPage(any(), any(), isNull(), any(), any()) } returns AppResult.Success(emptyEventsPage)
        coEvery { historyRepository.getRefuelHistory(any(), any(), any(), any(), any()) } returns AppResult.Success(emptyRefuelPage)
        buildViewModel()
        // null category = Todas → deve ter chamado historyRepository
        io.mockk.coVerify(atLeast = 1) { historyRepository.getRefuelHistory(1, 0, 200, null, null) }
    }

    @Test
    fun `refuels included when category is FUEL`() = runTest {
        coEvery { getEventsPage(any(), any(), any(), any(), any()) } returns AppResult.Success(emptyEventsPage)
        coEvery { historyRepository.getRefuelHistory(any(), any(), any(), any(), any()) } returns AppResult.Success(emptyRefuelPage)
        val vm = buildViewModel()

        coEvery { historyRepository.getRefuelHistory(any(), any(), any(), any(), any()) } returns AppResult.Success(emptyRefuelPage)
        vm.onCategorySelected(EventCategory.FUEL)

        io.mockk.coVerify(atLeast = 1) { historyRepository.getRefuelHistory(1, 0, 200, null, null) }
    }

    @Test
    fun `refuels NOT loaded when category is MAINTENANCE`() = runTest {
        coEvery { getEventsPage(any(), any(), any(), any(), any()) } returns AppResult.Success(emptyEventsPage)
        coEvery { historyRepository.getRefuelHistory(any(), any(), any(), any(), any()) } returns AppResult.Success(emptyRefuelPage)
        val vm = buildViewModel()
        io.mockk.clearMocks(historyRepository)

        vm.onCategorySelected(EventCategory.MAINTENANCE)

        io.mockk.coVerify(exactly = 0) { historyRepository.getRefuelHistory(any(), any(), any(), any(), any()) }
    }

    // ── Timeline merge & sort ─────────────────────────────────────────────────

    @Test
    fun `timeline merges events and refuels sorted by date descending`() = runTest {
        val event = makeEvent(1, "2026-06-15")
        val refuel = makeRefuel(10, "2026-06-20")
        coEvery { getEventsPage(any(), any(), isNull(), any(), any()) } returns AppResult.Success(
            PagedVehicleEvents(items = listOf(event), currentPage = 0, totalPages = 1, totalElements = 1)
        )
        coEvery { historyRepository.getRefuelHistory(any(), any(), any(), any(), any()) } returns AppResult.Success(
            RefuelPage(items = listOf(refuel), hasMore = false, currentPage = 0)
        )

        val vm = buildViewModel()
        val state = vm.state.value.screenState as VehicleEventsScreenState.Success

        assertEquals(2, state.items.size)
        // refuel date 2026-06-20 > event date 2026-06-15 → refuel first
        assertTrue(state.items[0] is VehicleTimelineItem.RefuelEntry)
        assertTrue(state.items[1] is VehicleTimelineItem.EventEntry)
    }

    // ── onRefuelClick ─────────────────────────────────────────────────────────

    @Test
    fun `onRefuelClick emits NavigateToRefuelDetails`() = runTest {
        coEvery { getEventsPage(any(), any(), isNull(), any(), any()) } returns AppResult.Success(emptyEventsPage)
        coEvery { historyRepository.getRefuelHistory(any(), any(), any(), any(), any()) } returns AppResult.Success(emptyRefuelPage)
        val vm = buildViewModel()

        val effects = mutableListOf<VehicleEventsEffect>()
        val job = launch(testDispatcher) { vm.effects.collect { effects.add(it) } }

        vm.onRefuelClick(42)
        job.cancel()

        assertEquals(1, effects.size)
        assertEquals(VehicleEventsEffect.NavigateToRefuelDetails(42), effects[0])
    }

    // ── Silent refuel error ───────────────────────────────────────────────────

    @Test
    fun `refuel load failure shows only events without error state`() = runTest {
        val event = makeEvent(1, "2026-06-15")
        coEvery { getEventsPage(any(), any(), isNull(), any(), any()) } returns AppResult.Success(
            PagedVehicleEvents(items = listOf(event), currentPage = 0, totalPages = 1, totalElements = 1)
        )
        coEvery { historyRepository.getRefuelHistory(any(), any(), any(), any(), any()) } returns
            AppResult.Failure(com.flowfuel.app.core.domain.AppError.Unknown())

        val vm = buildViewModel()
        val state = vm.state.value.screenState as VehicleEventsScreenState.Success

        assertEquals(1, state.items.size)
        assertTrue(state.items[0] is VehicleTimelineItem.EventEntry)
    }

    // ── Filtro de data client-side ────────────────────────────────────────────

    @Test
    fun `onDateFilterSelected prunes refuels outside date range`() = runTest {
        val recentRefuel = makeRefuel(20, LocalDate.now().minusDays(5).toString())
        val oldRefuel    = makeRefuel(21, LocalDate.now().minusDays(60).toString())

        coEvery { getEventsPage(any(), any(), isNull(), any(), any()) } returns AppResult.Success(emptyEventsPage)
        coEvery { historyRepository.getRefuelHistory(any(), any(), any(), any(), any()) } returns AppResult.Success(
            RefuelPage(items = listOf(recentRefuel, oldRefuel), hasMore = false, currentPage = 0)
        )

        val vm = buildViewModel()

        // trigger date filter change — events mock returns empty
        coEvery { getEventsPage(any(), any(), isNull(), any(), any()) } returns AppResult.Success(emptyEventsPage)
        vm.onDateFilterSelected(EventDateFilter.Last30Days)

        val state = vm.state.value.screenState as VehicleEventsScreenState.Success
        assertEquals(1, state.items.size)
        val refuelEntry = state.items[0] as VehicleTimelineItem.RefuelEntry
        assertEquals(20, refuelEntry.refuel.id)
    }
}
