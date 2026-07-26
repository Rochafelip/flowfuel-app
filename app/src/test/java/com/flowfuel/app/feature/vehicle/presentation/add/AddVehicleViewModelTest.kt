package com.flowfuel.app.feature.vehicle.presentation.add

import android.net.Uri
import app.cash.turbine.test
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.FieldError
import com.flowfuel.app.core.media.ImagePickerHelper
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FipeOption
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import com.flowfuel.app.feature.vehicle.domain.usecase.CreateVehicleUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.GetFipeBrandsUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.GetFipeModelsUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.GetFipeYearsUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.UploadVehiclePhotoUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
    private val imagePickerHelper: ImagePickerHelper = mockk()
    private val getFipeBrands: GetFipeBrandsUseCase = mockk()
    private val getFipeModels: GetFipeModelsUseCase = mockk()
    private val getFipeYears: GetFipeYearsUseCase = mockk()
    private lateinit var viewModel: AddVehicleViewModel

    private val photoUri: Uri = Uri.parse("content://media/test/photo.jpg")
    private val templateUri: Uri = Uri.parse("file:///cache/photo_templates/template_1.jpg")

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
        coEvery { getFipeBrands(any()) } returns AppResult.Success(emptyList())
        coEvery { getFipeModels(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { getFipeYears(any(), any(), any()) } returns AppResult.Success(emptyList())
        viewModel = AddVehicleViewModel(
            createVehicle, uploadVehiclePhoto, imagePickerHelper,
            getFipeBrands, getFipeModels, getFipeYears,
        )
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

    // ── onSkipPhoto ───────────────────────────────────────────────────────────

    @Test
    fun `onSkipPhoto sets photoUri to a generated template and enables canSubmit`() {
        every { imagePickerHelper.createTemplatePhoto(VehicleType.Car) } returns templateUri

        viewModel.onSkipPhoto()

        assertEquals(templateUri, viewModel.state.value.photoUri)
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun `onSkipPhoto uses the vehicleType currently selected in step 2`() {
        every { imagePickerHelper.createTemplatePhoto(VehicleType.Motorcycle) } returns templateUri
        viewModel.onVehicleTypeChange(VehicleType.Motorcycle)

        viewModel.onSkipPhoto()

        verify(exactly = 1) { imagePickerHelper.createTemplatePhoto(VehicleType.Motorcycle) }
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

    @Test
    fun `submit create failure with field errors sets serverErrors and does not call uploadVehiclePhoto`() {
        val fieldErrors = listOf(FieldError("capacity", "must not be null"))
        coEvery { createVehicle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            AppResult.Failure(AppError.Api("VALIDATION_FAILED", null, fieldErrors))
        fillStep1()
        viewModel.onPhotoPicked(photoUri)

        viewModel.submit()

        assertNotNull(viewModel.state.value.serverErrors)
        assertEquals("capacity", viewModel.state.value.serverErrors!!.first().field)
        coVerify(exactly = 0) { uploadVehiclePhoto(any(), any()) }
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

    // ── Cascata FIPE ──────────────────────────────────────────────────────────

    @Test
    fun `loadFipeBrandsIfNeeded loads brands once and does not reload if already loaded`() = runTest {
        coEvery { getFipeBrands(VehicleType.Car) } returns AppResult.Success(listOf(FipeOption("21", "Fiat")))

        viewModel.loadFipeBrandsIfNeeded()
        viewModel.loadFipeBrandsIfNeeded()

        assertEquals(listOf(FipeOption("21", "Fiat")), viewModel.state.value.fipeBrands)
        coVerify(exactly = 1) { getFipeBrands(VehicleType.Car) }
    }

    @Test
    fun `loadFipeBrandsIfNeeded sets fipeBrandsError on failure`() = runTest {
        coEvery { getFipeBrands(VehicleType.Car) } returns AppResult.Failure(AppError.Network)

        viewModel.loadFipeBrandsIfNeeded()

        assertTrue(viewModel.state.value.fipeBrandsError)
        assertFalse(viewModel.state.value.fipeBrandsLoading)
    }

    @Test
    fun `onRetryLoadFipeBrands retries and clears fipeBrandsError on success`() = runTest {
        coEvery { getFipeBrands(VehicleType.Car) } returns AppResult.Failure(AppError.Network)
        viewModel.loadFipeBrandsIfNeeded()
        assertTrue(viewModel.state.value.fipeBrandsError)

        coEvery { getFipeBrands(VehicleType.Car) } returns AppResult.Success(listOf(FipeOption("21", "Fiat")))
        viewModel.onRetryLoadFipeBrands()

        assertFalse(viewModel.state.value.fipeBrandsError)
        assertEquals(listOf(FipeOption("21", "Fiat")), viewModel.state.value.fipeBrands)
    }

    @Test
    fun `onFipeBrandSelected sets brand fields and triggers loadFipeModels`() = runTest {
        coEvery { getFipeModels(VehicleType.Car, "21") } returns AppResult.Success(listOf(FipeOption("1004", "Uno")))

        viewModel.onFipeBrandSelected(FipeOption("21", "Fiat"))

        val state = viewModel.state.value
        assertEquals("Fiat", state.brand)
        assertEquals("21", state.selectedFipeBrandCode)
        assertEquals(listOf(FipeOption("1004", "Uno")), state.fipeModels)
        coVerify(exactly = 1) { getFipeModels(VehicleType.Car, "21") }
    }

    @Test
    fun `onFipeModelSelected sets model fields and triggers loadFipeYears`() = runTest {
        viewModel.onFipeBrandSelected(FipeOption("21", "Fiat"))
        coEvery { getFipeYears(VehicleType.Car, "21", "1004") } returns
            AppResult.Success(listOf(FipeOption("2016-1", "2016 Gasolina")))

        viewModel.onFipeModelSelected(FipeOption("1004", "Uno"))

        val state = viewModel.state.value
        assertEquals("Uno", state.model)
        assertEquals("1004", state.selectedFipeModelCode)
        assertEquals(listOf(FipeOption("2016-1", "2016 Gasolina")), state.fipeYears)
    }

    @Test
    fun `onFipeYearSelected fills manufactureYear and modelYear with the same parsed year`() {
        viewModel.onFipeYearSelected(FipeOption("2016-1", "2016 Gasolina"))

        val state = viewModel.state.value
        assertEquals("2016", state.modelYear)
        assertEquals("2016", state.manufactureYear)
    }

    @Test
    fun `onFipeYearSelected does not change years when the fipe code is unparseable`() {
        viewModel.onManufactureYearChange("2020")
        viewModel.onModelYearChange("2021")

        viewModel.onFipeYearSelected(FipeOption("", ""))

        val state = viewModel.state.value
        assertEquals("2020", state.manufactureYear)
        assertEquals("2021", state.modelYear)
    }

    @Test
    fun `onVehicleTypeChange to Motorcycle clears previous selection and reloads brands for motos`() = runTest {
        coEvery { getFipeModels(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { getFipeBrands(VehicleType.Motorcycle) } returns AppResult.Success(listOf(FipeOption("80", "Honda")))
        viewModel.onFipeBrandSelected(FipeOption("21", "Fiat"))

        viewModel.onVehicleTypeChange(VehicleType.Motorcycle)

        val state = viewModel.state.value
        assertEquals(VehicleType.Motorcycle, state.vehicleType)
        assertEquals("", state.brand)
        assertNull(state.selectedFipeBrandCode)
        assertEquals(listOf(FipeOption("80", "Honda")), state.fipeBrands)
    }

    @Test
    fun `onVehicleTypeChange to the same type does not reload brands`() = runTest {
        viewModel.loadFipeBrandsIfNeeded() // Car (default)

        viewModel.onVehicleTypeChange(VehicleType.Car)

        coVerify(exactly = 1) { getFipeBrands(VehicleType.Car) }
    }

    @Test
    fun `onToggleManualEntry flips useFipeSearch without touching other fields`() {
        assertTrue(viewModel.state.value.useFipeSearch)
        viewModel.onFipeBrandSelected(FipeOption("21", "Fiat"))

        viewModel.onToggleManualEntry()

        val state = viewModel.state.value
        assertFalse(state.useFipeSearch)
        assertEquals("Fiat", state.brand)

        viewModel.onToggleManualEntry()
        assertTrue(viewModel.state.value.useFipeSearch)
    }
}
