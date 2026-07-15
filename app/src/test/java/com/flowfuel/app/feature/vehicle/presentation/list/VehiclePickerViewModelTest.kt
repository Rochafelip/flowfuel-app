package com.flowfuel.app.feature.vehicle.presentation.list

import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShareStatus
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetActiveSharedVehiclesUseCase
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.PagedVehicles
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehiclesPageUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.SetActiveGuestVehicleUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.SetActiveVehicleUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VehiclePickerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val getVehiclesPage: GetVehiclesPageUseCase = mockk()
    private val setActiveVehicle: SetActiveVehicleUseCase = mockk(relaxed = true)
    private val sessionStore: SessionStore = mockk(relaxed = true)
    private val getActiveSharedVehicles: GetActiveSharedVehiclesUseCase = mockk()
    private val setActiveGuestVehicle: SetActiveGuestVehicleUseCase = mockk(relaxed = true)

    private val fixtureVehicle = Vehicle(
        id = 1,
        brand = "Toyota",
        model = "Corolla",
        manufactureYear = 2020,
        modelYear = 2020,
        licensePlate = "ABC1234",
        color = "Prata",
        type = VehicleType.Car,
        energyType = EnergyType.Combustion,
        fuelType = null,
        odometerKm = 10000,
        tankCapacityL = 50.0,
        batteryCapacityKwh = null,
        isActive = true,
    )

    private fun createViewModel(): VehiclePickerViewModel {
        // Cada teste deve stubar getActiveSharedVehicles() antes de chamar createViewModel();
        // não há default aqui porque coEvery aplicado depois sobrescreveria o que o teste configurou.
        return VehiclePickerViewModel(
            getVehiclesPage,
            setActiveVehicle,
            sessionStore,
            getActiveSharedVehicles,
            setActiveGuestVehicle,
        )
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
    fun load_comVeiculoEmprestadoAtivo_incluiItemBorrowed() = runTest {
        coEvery { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        coEvery { getVehiclesPage(0) } returns AppResult.Success(
            PagedVehicles(items = listOf(fixtureVehicle), currentPage = 0, totalPages = 1, totalElements = 1),
        )
        val share = VehicleShare(
            id = 100, vehicleId = 99, vehicleBrand = "Fiat", vehicleModel = "Uno",
            ownerId = 5, ownerName = "Outro Dono", guestId = 1, guestName = "Eu",
            status = VehicleShareStatus.ACTIVE, createdAt = null, respondedAt = null,
            expiresAt = "2026-07-20T00:00:00",
        )
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(listOf(share))

        val viewModel = createViewModel()

        val state = viewModel.state.value as VehiclePickerUiState.Success
        assertEquals(2, state.items.size)
        assertTrue(state.items.any { it is VehiclePickerItem.Borrowed && (it as VehiclePickerItem.Borrowed).share.id == 100 })
    }

    @Test
    fun load_semVeiculosCompartilhados_apenasItensOwned() = runTest {
        coEvery { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        coEvery { getVehiclesPage(0) } returns AppResult.Success(
            PagedVehicles(items = listOf(fixtureVehicle), currentPage = 0, totalPages = 1, totalElements = 1),
        )
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(emptyList())

        val viewModel = createViewModel()

        val state = viewModel.state.value as VehiclePickerUiState.Success
        assertEquals(1, state.items.size)
        assertTrue(state.items.single() is VehiclePickerItem.Owned)
    }

    @Test
    fun load_falhaAoBuscarCompartilhados_trataComoListaVazia() = runTest {
        coEvery { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        coEvery { getVehiclesPage(0) } returns AppResult.Success(
            PagedVehicles(items = listOf(fixtureVehicle), currentPage = 0, totalPages = 1, totalElements = 1),
        )
        coEvery { getActiveSharedVehicles() } returns AppResult.Failure(AppError.Network)

        val viewModel = createViewModel()

        val state = viewModel.state.value as VehiclePickerUiState.Success
        assertEquals(1, state.items.size)
        assertTrue(state.items.single() is VehiclePickerItem.Owned)
    }

    @Test
    fun onItemSelected_itemOwned_chamaSetActiveVehicleENaoChamaSetActiveGuestVehicle() = runTest {
        coEvery { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        coEvery { getVehiclesPage(0) } returns AppResult.Success(
            PagedVehicles(items = listOf(fixtureVehicle), currentPage = 0, totalPages = 1, totalElements = 1),
        )
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(emptyList())

        val viewModel = createViewModel()
        viewModel.onItemSelected(VehiclePickerItem.Owned(fixtureVehicle))

        coVerify { setActiveVehicle(fixtureVehicle.id) }
        coVerify(exactly = 0) { setActiveGuestVehicle(any()) }
    }

    @Test
    fun onVehicleSelected_itemBorrowed_naoChamaSetActiveVehicleEUsaSetActiveGuestVehicle() = runTest {
        coEvery { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        coEvery { getVehiclesPage(0) } returns AppResult.Success(PagedVehicles(items = emptyList(), currentPage = 0, totalPages = 1, totalElements = 0))
        val share = VehicleShare(
            id = 100, vehicleId = 99, vehicleBrand = "Fiat", vehicleModel = "Uno",
            ownerId = 5, ownerName = "Outro Dono", guestId = 1, guestName = "Eu",
            status = VehicleShareStatus.ACTIVE, createdAt = null, respondedAt = null,
            expiresAt = "2026-07-20T00:00:00",
        )
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(listOf(share))
        coEvery { setActiveGuestVehicle(99) } returns Unit

        val viewModel = createViewModel()
        viewModel.onItemSelected(VehiclePickerItem.Borrowed(share))

        coVerify { setActiveGuestVehicle(99) }
        coVerify(exactly = 0) { setActiveVehicle(any()) }
    }
}
