package com.flowfuel.app.feature.vehicle.presentation.add

import android.net.Uri
import app.cash.turbine.test
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import com.flowfuel.app.feature.vehicle.domain.usecase.CreateVehicleUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.UploadVehiclePhotoUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AddVehicleViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val createVehicle: CreateVehicleUseCase = mockk()
    private val uploadVehiclePhoto: UploadVehiclePhotoUseCase = mockk()
    private lateinit var viewModel: AddVehicleViewModel

    private val photoUri: Uri = Uri.parse("content://media/test/photo.jpg")

    private val fixtureVehicle = Vehicle(
        id = 42,
        brand = "Toyota",
        model = "Corolla",
        manufactureYear = 2022,
        modelYear = 2023,
        licensePlate = "ABC1234",
        color = "Prata",
        type = VehicleType.Car,
        energyType = EnergyType.Combustion,
        fuelType = FuelType.Flex,
        odometerKm = 15000,
        tankCapacityL = 50.0,
        batteryCapacityKwh = null,
        isActive = true,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AddVehicleViewModel(createVehicle, uploadVehiclePhoto)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fillStep1() {
        viewModel.onBrandChange("Toyota")
        viewModel.onModelChange("Corolla")
        viewModel.onManufactureYearChange("2022")
        viewModel.onModelYearChange("2023")
    }

    private fun advanceToStep3() {
        fillStep1()
        viewModel.onNextStep() // 1 -> 2
        viewModel.onNextStep() // 2 -> 3
    }

    // ── Navegação do wizard ───────────────────────────────────────────────────

    @Test
    fun `onNextStep from step 3 with invalid plate sets licensePlateError and stays on step 3`() {
        advanceToStep3()
        viewModel.onLicensePlateChange("ABC")

        viewModel.onNextStep()

        assertEquals(3, viewModel.state.value.currentStep)
        assertTrue(viewModel.state.value.licensePlateError)
    }

    @Test
    fun `onNextStep from step 3 with valid plate advances to step 4`() {
        advanceToStep3()
        viewModel.onLicensePlateChange("ABC1234")

        viewModel.onNextStep()

        assertEquals(4, viewModel.state.value.currentStep)
        assertFalse(viewModel.state.value.licensePlateError)
    }

    @Test
    fun `onSkipToPhotoStep advances to step 4 without validating plate`() {
        advanceToStep3()

        viewModel.onSkipToPhotoStep()

        assertEquals(4, viewModel.state.value.currentStep)
        assertFalse(viewModel.state.value.licensePlateError)
    }

    // ── canSubmit ──────────────────────────────────────────────────────────────

    @Test
    fun `canSubmit false without photo`() {
        assertFalse(viewModel.state.value.canSubmit)
    }

    @Test
    fun `canSubmit true after photo picked`() {
        viewModel.onPhotoPicked(photoUri)

        assertTrue(viewModel.state.value.canSubmit)
    }

    // ── onPhotoPicked ──────────────────────────────────────────────────────────

    @Test
    fun `onPhotoPicked sets photoUri and clears previous upload error`() {
        coEvery { createVehicle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            AppResult.Success(fixtureVehicle)
        coEvery { uploadVehiclePhoto(any(), any()) } returns AppResult.Failure(AppError.Network)
        fillStep1()
        viewModel.onPhotoPicked(photoUri)
        viewModel.submit()
        assertNotNull(viewModel.state.value.photoUploadError)

        viewModel.onPhotoPicked(photoUri)

        assertEquals(photoUri, viewModel.state.value.photoUri)
        assertNull(viewModel.state.value.photoUploadError)
    }

    // ── submit — guard ────────────────────────────────────────────────────────

    @Test
    fun `submit without photo does not call createVehicle`() {
        fillStep1()

        viewModel.submit()

        coVerify(exactly = 0) { createVehicle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    // ── submit — sucesso total ───────────────────────────────────────────────

    @Test
    fun `submit success creates vehicle then uploads photo and emits NavigateBack`() = runTest {
        coEvery { createVehicle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            AppResult.Success(fixtureVehicle)
        coEvery { uploadVehiclePhoto(any(), any()) } returns AppResult.Success("https://cdn.example.com/photo.jpg")
        fillStep1()
        viewModel.onPhotoPicked(photoUri)

        viewModel.effects.test {
            viewModel.submit()
            assertEquals(AddVehicleEffect.NavigateBack, awaitItem())
        }
        coVerify(exactly = 1) { uploadVehiclePhoto(42, photoUri) }
    }

    @Test
    fun `submit success resets isSubmitting to false`() {
        coEvery { createVehicle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            AppResult.Success(fixtureVehicle)
        coEvery { uploadVehiclePhoto(any(), any()) } returns AppResult.Success("https://cdn.example.com/photo.jpg")
        fillStep1()
        viewModel.onPhotoPicked(photoUri)

        viewModel.submit()

        assertFalse(viewModel.state.value.isSubmitting)
    }

    // ── submit — falha na criação ────────────────────────────────────────────

    @Test
    fun `submit create failure does not call uploadVehiclePhoto`() {
        coEvery { createVehicle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            AppResult.Failure(AppError.Network)
        fillStep1()
        viewModel.onPhotoPicked(photoUri)

        viewModel.submit()

        coVerify(exactly = 0) { uploadVehiclePhoto(any(), any()) }
        assertEquals(AppError.Network, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    // ── submit — falha só no upload (retry) ──────────────────────────────────

    @Test
    fun `submit upload failure keeps vehicle id and sets photoUploadError without navigating`() = runTest {
        coEvery { createVehicle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            AppResult.Success(fixtureVehicle)
        coEvery { uploadVehiclePhoto(any(), any()) } returns AppResult.Failure(AppError.Network)
        fillStep1()
        viewModel.onPhotoPicked(photoUri)

        viewModel.effects.test {
            viewModel.submit()
            expectNoEvents()
        }
        assertEquals(42, viewModel.state.value.createdVehicleId)
        assertEquals(AppError.Network, viewModel.state.value.photoUploadError)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun `submit retry after upload failure does not call createVehicle again`() = runTest {
        coEvery { createVehicle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            AppResult.Success(fixtureVehicle)
        coEvery { uploadVehiclePhoto(any(), any()) } returns AppResult.Failure(AppError.Network)
        fillStep1()
        viewModel.onPhotoPicked(photoUri)
        viewModel.submit() // primeira tentativa: cria veículo, upload falha

        coEvery { uploadVehiclePhoto(any(), any()) } returns AppResult.Success("https://cdn.example.com/photo.jpg")
        viewModel.effects.test {
            viewModel.submit() // retry
            assertEquals(AddVehicleEffect.NavigateBack, awaitItem())
        }

        coVerify(exactly = 1) { createVehicle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 2) { uploadVehiclePhoto(42, photoUri) }
    }
}
