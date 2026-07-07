package com.flowfuel.app.feature.home.presentation

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.CreateRefuelRequest
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class QuickRefuelViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val getActiveVehicle: GetActiveVehicleUseCase = mockk()
    private val createRefuel: CreateRefuelUseCase = mockk()

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

    private lateinit var viewModel: QuickRefuelViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { getActiveVehicle() } returns AppResult.Success(testVehicle)
        viewModel = QuickRefuelViewModel(getActiveVehicle, createRefuel)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── openSheet ──────────────────────────────────────────────────────────────

    @Test
    fun `openSheet() shows the sheet and fetches the active vehicle`() = runTest {
        viewModel.openSheet()

        assertTrue(viewModel.state.value.showSheet)
        assertEquals(testVehicle, viewModel.state.value.vehicle)
    }

    @Test
    fun `closeSheet() hides the sheet and resets the form`() = runTest {
        viewModel.openSheet()
        viewModel.onLitersChange("21,29")

        viewModel.closeSheet()

        assertFalse(viewModel.state.value.showSheet)
        assertEquals("", viewModel.state.value.form.liters)
    }

    // ── onOdometerInputModeChange ─────────────────────────────────────────────

    @Test
    fun `onOdometerInputModeChange to ODOMETER sets mode`() {
        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)
        assertEquals(OdometerInputMode.ODOMETER, viewModel.state.value.form.odometerInputMode)
    }

    @Test
    fun `onOdometerInputModeChange clears odometer and tripKm fields`() {
        viewModel.onOdometerChange("672700")
        viewModel.onTripKmChange("310,6")
        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)

        val form = viewModel.state.value.form
        assertEquals("", form.odometer)
        assertEquals("", form.tripKm)
    }

    @Test
    fun `onOdometerInputModeChange clears errors`() {
        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)

        val form = viewModel.state.value.form
        assertFalse(form.odometerError)
        assertFalse(form.tripKmError)
    }

    // ── onTripKmChange ────────────────────────────────────────────────────────

    @Test
    fun `onTripKmChange filters to digits comma and dot only`() {
        viewModel.onTripKmChange("310,6abc!@#")
        assertEquals("310,6", viewModel.state.value.form.tripKm)
    }

    @Test
    fun `onTripKmChange accepts dot as decimal separator`() {
        viewModel.onTripKmChange("310.6")
        assertEquals("310.6", viewModel.state.value.form.tripKm)
    }

    @Test
    fun `onTripKmChange clears tripKmError`() = runTest {
        // Dispara o erro de validação primeiro
        viewModel.openSheet()
        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")
        viewModel.submitRefuel()
        assertTrue(viewModel.state.value.form.tripKmError)

        viewModel.onTripKmChange("310,6")
        assertFalse(viewModel.state.value.form.tripKmError)
    }

    // ── submitRefuel em modo TRIP ─────────────────────────────────────────────

    @Test
    fun `submitRefuel TRIP mode sends currentKm plus tripKm as odometer`() = runTest {
        var capturedRequest: CreateRefuelRequest? = null
        coEvery { createRefuel(any()) } answers {
            capturedRequest = firstArg()
            AppResult.Success(Unit)
        }

        viewModel.openSheet()
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

        viewModel.openSheet()
        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onTripKmChange("310.6")
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        assertEquals(67580.6, capturedRequest!!.odometer, 0.001)
    }

    @Test
    fun `submitRefuel TRIP mode blank tripKm sets tripKmError and skips api`() = runTest {
        viewModel.openSheet()
        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        assertTrue(viewModel.state.value.form.tripKmError)
        coVerify(exactly = 0) { createRefuel(any()) }
    }

    @Test
    fun `submitRefuel TRIP mode zero tripKm sets tripKmError and skips api`() = runTest {
        viewModel.openSheet()
        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onTripKmChange("0")
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        assertTrue(viewModel.state.value.form.tripKmError)
        coVerify(exactly = 0) { createRefuel(any()) }
    }

    @Test
    fun `submitRefuel ODOMETER mode uses odometerDouble as before`() = runTest {
        var capturedRequest: CreateRefuelRequest? = null
        coEvery { createRefuel(any()) } answers {
            capturedRequest = firstArg()
            AppResult.Success(Unit)
        }

        viewModel.openSheet()
        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)
        viewModel.onOdometerChange("675800")   // 67580.0 km
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        assertEquals(67580.0, capturedRequest!!.odometer, 0.001)
    }

    // ── submitRefuel: sucesso e erro ────────────────────────────────────────────

    @Test
    fun `submitRefuel on success closes the sheet, resets the form and emits RefuelRegistered`() = runTest {
        coEvery { createRefuel(any()) } returns AppResult.Success(Unit)
        viewModel.openSheet()
        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)
        viewModel.onOdometerChange("675800")
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        var effect: QuickRefuelEffect? = null
        val job = launch { effect = viewModel.effects.first() }

        viewModel.submitRefuel()
        job.join()

        assertFalse(viewModel.state.value.showSheet)
        assertEquals("", viewModel.state.value.form.liters)
        assertEquals(QuickRefuelEffect.RefuelRegistered, effect)
    }

    @Test
    fun `submitRefuel on failure keeps the sheet open and exposes submitError`() = runTest {
        coEvery { createRefuel(any()) } returns AppResult.Failure(AppError.Network)
        viewModel.openSheet()
        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)
        viewModel.onOdometerChange("675800")
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        assertTrue(viewModel.state.value.showSheet)
        assertEquals(AppError.Network, viewModel.state.value.submitError)
    }
}
