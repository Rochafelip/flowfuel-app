package com.flowfuel.app.feature.home.presentation

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.feature.auth.domain.usecase.LogoutUseCase
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.CreateRefuelRequest
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetDashboardUseCase
import com.flowfuel.app.feature.station.domain.NearbyStationsPrefetcher
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehiclesUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.SetActiveVehicleUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
        viewModel = HomeViewModel(
            getActiveVehicle, getDashboard, createRefuel, logout,
            sessionStore, getVehicles, setActiveVehicle, stationsPrefetcher,
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
}
