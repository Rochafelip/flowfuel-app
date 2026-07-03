package com.flowfuel.app.feature.vehicle.presentation.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehicleByIdUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.UpdateVehicleUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.UploadVehiclePhotoUseCase
import io.mockk.coEvery
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EditVehicleViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val getVehicleById: GetVehicleByIdUseCase = mockk()
    private val updateVehicle: UpdateVehicleUseCase = mockk()
    private val uploadVehiclePhoto: UploadVehiclePhotoUseCase = mockk()
    private val sessionStore: SessionStore = mockk(relaxed = true)

    private val savedStateHandle = SavedStateHandle(mapOf("vehicleId" to 42))
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
        photoUrl = "https://cdn.example.com/old-photo.jpg",
    )

    private fun createViewModel(): EditVehicleViewModel {
        coEvery { getVehicleById(42) } returns AppResult.Success(fixtureVehicle)
        return EditVehicleViewModel(savedStateHandle, getVehicleById, updateVehicle, sessionStore, uploadVehiclePhoto)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load populates photoUrl from loaded vehicle`() {
        val viewModel = createViewModel()

        assertEquals("https://cdn.example.com/old-photo.jpg", viewModel.state.value.photoUrl)
    }

    @Test
    fun `onPhotoPicked success updates photoUrl and resets isUploadingPhoto`() {
        val viewModel = createViewModel()
        coEvery { uploadVehiclePhoto(42, photoUri) } returns AppResult.Success("https://cdn.example.com/new-photo.jpg")

        viewModel.onPhotoPicked(photoUri)

        assertEquals("https://cdn.example.com/new-photo.jpg", viewModel.state.value.photoUrl)
        assertFalse(viewModel.state.value.isUploadingPhoto)
        assertNull(viewModel.state.value.photoUploadError)
    }

    @Test
    fun `onPhotoPicked failure sets photoUploadError, resets isUploadingPhoto and keeps previous photoUrl`() {
        val viewModel = createViewModel()
        coEvery { uploadVehiclePhoto(42, photoUri) } returns AppResult.Failure(AppError.Network)

        viewModel.onPhotoPicked(photoUri)

        assertEquals(AppError.Network, viewModel.state.value.photoUploadError)
        assertFalse(viewModel.state.value.isUploadingPhoto)
        assertEquals("https://cdn.example.com/old-photo.jpg", viewModel.state.value.photoUrl)
    }

    @Test
    fun `clearPhotoUploadError resets error to null`() {
        val viewModel = createViewModel()
        coEvery { uploadVehiclePhoto(42, photoUri) } returns AppResult.Failure(AppError.Network)
        viewModel.onPhotoPicked(photoUri)

        viewModel.clearPhotoUploadError()

        assertNull(viewModel.state.value.photoUploadError)
    }
}
