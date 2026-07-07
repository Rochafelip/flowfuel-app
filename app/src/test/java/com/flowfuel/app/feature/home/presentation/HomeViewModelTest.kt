package com.flowfuel.app.feature.home.presentation

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.datastore.VehicleMaintenancePrefsStore
import com.flowfuel.app.feature.auth.domain.usecase.LogoutUseCase
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.CreateRefuelRequest
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.home.domain.model.FinancialSummary
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceItem
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceType
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetDashboardUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetFinancialSummaryUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetRecentActivityUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetUpcomingMaintenanceUseCase
import com.flowfuel.app.feature.station.domain.NearbyStationsPrefetcher
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehiclesUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.SetActiveVehicleUseCase
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleTimelineItem
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsTotalUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val getActiveVehicle: GetActiveVehicleUseCase = mockk()
    private val getDashboard: GetDashboardUseCase = mockk()
    private val createRefuel: CreateRefuelUseCase = mockk()
    private val logout: LogoutUseCase = mockk(relaxed = true)
    private val sessionStore: SessionStore = mockk(relaxed = true)
    private val getVehicles: GetVehiclesUseCase = mockk(relaxed = true)
    private val setActiveVehicle: SetActiveVehicleUseCase = mockk(relaxed = true)
    private val stationsPrefetcher: NearbyStationsPrefetcher = mockk(relaxed = true)
    private val getVehicleEventsTotal: GetVehicleEventsTotalUseCase = mockk(relaxed = true)
    private val getFinancialSummary: GetFinancialSummaryUseCase = mockk()
    private val getRecentActivity: GetRecentActivityUseCase = mockk()
    private val getUpcomingMaintenance: GetUpcomingMaintenanceUseCase = mockk()
    private val maintenancePrefsStore: VehicleMaintenancePrefsStore = mockk(relaxed = true)

    private val testFinancialSummary = FinancialSummary(
        currentMonthTotal = 300.0,
        previousMonthTotal = 250.0,
        averagePricePerUnit = 5.5,
    )

    private val testUpcomingMaintenance = listOf(
        UpcomingMaintenanceItem(type = UpcomingMaintenanceType.OIL_CHANGE, remainingKm = 320),
        UpcomingMaintenanceItem(type = UpcomingMaintenanceType.TIRE_ROTATION, remainingKm = 900),
        UpcomingMaintenanceItem(type = UpcomingMaintenanceType.LICENSING, remainingDays = 18),
    )

    private val testVehicle = ActiveVehicleData(
        id = 1,
        brand = "Volkswagen",
        model = "Fox",
        fuelSubType = null,
        capacity = null,
        licensePlate = "ABC1D23",
        energyType = "COMBUSTION",
        currentKm = 67270,
    )
    private val testDashboard = DashboardData(
        averageConsumption = null,
        consumptionUnit = null,
        totalSpent = 0.0,
        totalRefuels = 1,
        lastRefuelDate = null,
        lastRefuelEnergyAmount = null,
        lastRefuelAmount = null,
        lastRefuelEnergyUnit = null,
    )

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { sessionStore.activeVehicleIdFlow } returns flowOf(1)
        coEvery { getActiveVehicle() } returns AppResult.Success(testVehicle)
        coEvery { getDashboard(any()) } returns AppResult.Success(testDashboard)
        coEvery { getFinancialSummary(any()) } returns AppResult.Success(testFinancialSummary)
        coEvery { getRecentActivity(any()) } returns AppResult.Success(emptyList())
        coEvery { getUpcomingMaintenance(any(), any()) } returns AppResult.Success(testUpcomingMaintenance)
        viewModel = HomeViewModel(
            getActiveVehicle, getDashboard, createRefuel, logout,
            sessionStore, getVehicles, setActiveVehicle, stationsPrefetcher, getVehicleEventsTotal,
            getFinancialSummary, getRecentActivity, getUpcomingMaintenance, maintenancePrefsStore,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── onOdometerInputModeChange ─────────────────────────────────────────────

    @Test
    fun `onOdometerInputModeChange to ODOMETER sets mode`() {
        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)
        assertEquals(OdometerInputMode.ODOMETER, viewModel.state.value.refuelForm.odometerInputMode)
    }

    @Test
    fun `onOdometerInputModeChange clears odometer and tripKm fields`() {
        viewModel.onOdometerChange("672700")
        viewModel.onTripKmChange("310,6")
        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)

        val form = viewModel.state.value.refuelForm
        assertEquals("", form.odometer)
        assertEquals("", form.tripKm)
    }

    @Test
    fun `onOdometerInputModeChange clears errors`() {
        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)

        val form = viewModel.state.value.refuelForm
        assertFalse(form.odometerError)
        assertFalse(form.tripKmError)
    }

    // ── onTripKmChange ────────────────────────────────────────────────────────

    @Test
    fun `onTripKmChange filters to digits comma and dot only`() {
        viewModel.onTripKmChange("310,6abc!@#")
        assertEquals("310,6", viewModel.state.value.refuelForm.tripKm)
    }

    @Test
    fun `onTripKmChange accepts dot as decimal separator`() {
        viewModel.onTripKmChange("310.6")
        assertEquals("310.6", viewModel.state.value.refuelForm.tripKm)
    }

    @Test
    fun `onTripKmChange clears tripKmError`() = runTest {
        // Trigger validation error first
        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")
        viewModel.submitRefuel()
        assertTrue(viewModel.state.value.refuelForm.tripKmError)

        viewModel.onTripKmChange("310,6")
        assertFalse(viewModel.state.value.refuelForm.tripKmError)
    }

    // ── submitRefuel em modo TRIP ─────────────────────────────────────────────

    @Test
    fun `submitRefuel TRIP mode sends currentKm plus tripKm as odometer`() = runTest {
        var capturedRequest: CreateRefuelRequest? = null
        coEvery { createRefuel(any()) } answers {
            capturedRequest = firstArg()
            AppResult.Success(Unit)
        }

        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onTripKmChange("310,6")
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        // 67270.0 + 310.6 = 67580.6
        assertEquals(67580.6, capturedRequest!!.odometer, 0.001)
    }

    @Test
    fun `submitRefuel TRIP mode with comma separator parses correctly`() = runTest {
        var capturedRequest: CreateRefuelRequest? = null
        coEvery { createRefuel(any()) } answers {
            capturedRequest = firstArg()
            AppResult.Success(Unit)
        }

        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onTripKmChange("310.6")
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        assertEquals(67580.6, capturedRequest!!.odometer, 0.001)
    }

    @Test
    fun `submitRefuel TRIP mode blank tripKm sets tripKmError and skips api`() = runTest {
        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        assertTrue(viewModel.state.value.refuelForm.tripKmError)
        coVerify(exactly = 0) { createRefuel(any()) }
    }

    @Test
    fun `submitRefuel TRIP mode zero tripKm sets tripKmError and skips api`() = runTest {
        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onTripKmChange("0")
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        assertTrue(viewModel.state.value.refuelForm.tripKmError)
        coVerify(exactly = 0) { createRefuel(any()) }
    }

    @Test
    fun `submitRefuel ODOMETER mode uses odometerDouble as before`() = runTest {
        var capturedRequest: CreateRefuelRequest? = null
        coEvery { createRefuel(any()) } answers {
            capturedRequest = firstArg()
            AppResult.Success(Unit)
        }

        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)
        viewModel.onOdometerChange("675800")   // 67580.0 km
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        assertEquals(67580.0, capturedRequest!!.odometer, 0.001)
    }

    // ── Estações (prefetch) ────────────────────────────────────────────────────

    @Test
    fun `load() triggers a stations prefetch`() {
        viewModel.load()

        // 1 call from init's load() in setUp(), +1 from this explicit call
        verify(exactly = 2) { stationsPrefetcher.prefetch() }
    }

    @Test
    fun `onVehicleSwitch() triggers a stations prefetch, both directly and via the load() it triggers`() {
        viewModel.onVehicleSwitch(2)

        // 1 call from init's load() in setUp(), +1 explicit call in onVehicleSwitch(),
        // +1 from the load() that onVehicleSwitch() calls internally
        verify(exactly = 3) { stationsPrefetcher.prefetch() }
    }

    // ── Seções independentes (financialSummary / recentActivity) ──────────────

    @Test
    fun `load() populates financialSummary and recentActivity sections on success`() = runTest {
        viewModel.load()

        val success = viewModel.state.value.screenState as HomeScreenState.Success
        assertEquals(SectionState.Success(testFinancialSummary), success.financialSummary)
        assertEquals(SectionState.Success(emptyList<VehicleTimelineItem>()), success.recentActivity)
    }

    @Test
    fun `load() isolates financialSummary failure without breaking the rest of the screen`() = runTest {
        coEvery { getFinancialSummary(any()) } returns AppResult.Failure(AppError.Network)

        viewModel.load()

        val success = viewModel.state.value.screenState as HomeScreenState.Success
        assertEquals(SectionState.Error(AppError.Network), success.financialSummary)
        assertEquals(SectionState.Success(emptyList<VehicleTimelineItem>()), success.recentActivity)
    }

    @Test
    fun `retryFinancialSummary() re-fetches only the financial summary section`() = runTest {
        coEvery { getFinancialSummary(any()) } returns AppResult.Failure(AppError.Network)
        viewModel.load()
        coEvery { getFinancialSummary(any()) } returns AppResult.Success(testFinancialSummary)

        viewModel.retryFinancialSummary()

        val success = viewModel.state.value.screenState as HomeScreenState.Success
        assertEquals(SectionState.Success(testFinancialSummary), success.financialSummary)
    }

    @Test
    fun `stale financialSummary fetch for a previous vehicle does not overwrite the new vehicle's state after a switch`() = runTest {
        val vehicle2 = testVehicle.copy(id = 2, brand = "Toyota", model = "Corolla")
        val financialSummaryForVehicle2 = testFinancialSummary.copy(currentMonthTotal = 999.0)
        val staleFetchGate = CompletableDeferred<Unit>()

        // getFinancialSummary(1) parks on staleFetchGate until we explicitly release it,
        // simulating a slow in-flight fetch for the vehicle the user is navigating away from.
        coEvery { getFinancialSummary(1) } coAnswers {
            staleFetchGate.await()
            AppResult.Success(testFinancialSummary)
        }
        coEvery { getFinancialSummary(2) } returns AppResult.Success(financialSummaryForVehicle2)

        // Kick off a fresh load for vehicle 1; its loadFinancialSummary(1) call suspends on the gate,
        // leaving the coroutine in flight when we switch vehicles below.
        viewModel.load()
        val afterInitialLoad = viewModel.state.value.screenState as HomeScreenState.Success
        assertEquals(1, afterInitialLoad.vehicle.id)
        assertEquals(SectionState.Loading, afterInitialLoad.financialSummary)

        // Now switch to vehicle 2 while vehicle 1's financial summary fetch is still pending.
        every { sessionStore.activeVehicleIdFlow } returns flowOf(2)
        coEvery { getActiveVehicle() } returns AppResult.Success(vehicle2)
        viewModel.onVehicleSwitch(2)

        val afterSwitch = viewModel.state.value.screenState as HomeScreenState.Success
        assertEquals(2, afterSwitch.vehicle.id)
        assertEquals(SectionState.Success(financialSummaryForVehicle2), afterSwitch.financialSummary)

        // Release the stale vehicle-1 fetch now that vehicle 2's Success state is in place.
        staleFetchGate.complete(Unit)

        val finalState = viewModel.state.value.screenState as HomeScreenState.Success
        assertEquals(2, finalState.vehicle.id)
        assertEquals(
            "Stale vehicle-1 financial summary must not overwrite vehicle 2's state",
            SectionState.Success(financialSummaryForVehicle2),
            finalState.financialSummary,
        )
    }

    // ── Seção independente (upcomingMaintenance) ──────────────────────────────

    @Test
    fun `load() populates upcomingMaintenance section on success`() = runTest {
        viewModel.load()

        val success = viewModel.state.value.screenState as HomeScreenState.Success
        assertEquals(SectionState.Success(testUpcomingMaintenance), success.upcomingMaintenance)
    }

    @Test
    fun `load() isolates upcomingMaintenance failure without breaking the rest of the screen`() = runTest {
        coEvery { getUpcomingMaintenance(any(), any()) } returns AppResult.Failure(AppError.Network)

        viewModel.load()

        val success = viewModel.state.value.screenState as HomeScreenState.Success
        assertEquals(SectionState.Error(AppError.Network), success.upcomingMaintenance)
        assertEquals(SectionState.Success(testFinancialSummary), success.financialSummary)
    }

    @Test
    fun `retryUpcomingMaintenance() re-fetches only that section, using the current vehicle's km`() = runTest {
        coEvery { getUpcomingMaintenance(any(), any()) } returns AppResult.Failure(AppError.Network)
        viewModel.load()
        coEvery { getUpcomingMaintenance(any(), any()) } returns AppResult.Success(testUpcomingMaintenance)

        viewModel.retryUpcomingMaintenance()

        val success = viewModel.state.value.screenState as HomeScreenState.Success
        assertEquals(SectionState.Success(testUpcomingMaintenance), success.upcomingMaintenance)
        coVerify { getUpcomingMaintenance(1, 67270) }
    }

    // ── Diálogo de data de licenciamento ───────────────────────────────────────

    @Test
    fun `openLicensingDueDatePicker() shows the dialog`() {
        viewModel.openLicensingDueDatePicker()
        assertTrue(viewModel.state.value.showLicensingDueDatePicker)
    }

    @Test
    fun `closeLicensingDueDatePicker() hides the dialog`() {
        viewModel.openLicensingDueDatePicker()
        viewModel.closeLicensingDueDatePicker()
        assertFalse(viewModel.state.value.showLicensingDueDatePicker)
    }

    @Test
    fun `onLicensingDueDateSelected() saves the date, closes the dialog, and reloads the section`() = runTest {
        viewModel.load()
        viewModel.openLicensingDueDatePicker()

        viewModel.onLicensingDueDateSelected("2026-08-15")

        assertFalse(viewModel.state.value.showLicensingDueDatePicker)
        coVerify { maintenancePrefsStore.saveLicensingDueDate(1, "2026-08-15") }
        coVerify(atLeast = 2) { getUpcomingMaintenance(1, 67270) } // 1x do load() + 1x do retry pós-seleção
    }

    // ── Diálogo "Sobre" ─────────────────────────────────────────────────────

    @Test
    fun `openAboutDialog() shows the dialog`() {
        viewModel.openAboutDialog()
        assertTrue(viewModel.state.value.showAboutDialog)
    }

    @Test
    fun `closeAboutDialog() hides the dialog`() {
        viewModel.openAboutDialog()
        viewModel.closeAboutDialog()
        assertFalse(viewModel.state.value.showAboutDialog)
    }
}
