package com.flowfuel.app.feature.vehicle.presentation.manage

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
import com.flowfuel.app.feature.vehicle.domain.usecase.DeleteVehicleUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehiclesPageUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.SetActiveGuestVehicleUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.SetActiveVehicleUseCase
import app.cash.turbine.test
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
class VehiclesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val getVehiclesPage: GetVehiclesPageUseCase = mockk()
    private val setActiveVehicle: SetActiveVehicleUseCase = mockk(relaxed = true)
    private val deleteVehicle: DeleteVehicleUseCase = mockk(relaxed = true)
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

    private val fixtureShare = VehicleShare(
        id = 100, vehicleId = 99, vehicleBrand = "Fiat", vehicleModel = "Uno",
        ownerId = 5, ownerName = "Outro Dono", guestId = 1, guestName = "Eu",
        status = VehicleShareStatus.ACTIVE, createdAt = null, respondedAt = null,
        expiresAt = "2026-07-20T00:00:00",
    )

    private fun createViewModel(): VehiclesViewModel {
        // Cada teste deve stubar getVehiclesPage(0) e getActiveSharedVehicles()
        // antes de chamar createViewModel() — o init{} já dispara load().
        return VehiclesViewModel(
            getVehiclesPage,
            setActiveVehicle,
            deleteVehicle,
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
    fun load_comVeiculosPropriosECompartilhados_separaEmDoisGrupos() = runTest {
        coEvery { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        coEvery { getVehiclesPage(0) } returns AppResult.Success(
            PagedVehicles(items = listOf(fixtureVehicle), currentPage = 0, totalPages = 1, totalElements = 1),
        )
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(listOf(fixtureShare))

        val viewModel = createViewModel()

        val screenState = viewModel.state.value.screenState as VehiclesScreenState.Success
        assertEquals(listOf(fixtureVehicle), screenState.ownedItems)
        assertEquals(listOf(fixtureShare), screenState.borrowedItems)
    }

    @Test
    fun load_semVeiculosPropriosMasComCompartilhados_naoDisparaEmpty() = runTest {
        coEvery { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        coEvery { getVehiclesPage(0) } returns AppResult.Success(
            PagedVehicles(items = emptyList(), currentPage = 0, totalPages = 1, totalElements = 0),
        )
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(listOf(fixtureShare))

        val viewModel = createViewModel()

        val screenState = viewModel.state.value.screenState as VehiclesScreenState.Success
        assertTrue(screenState.ownedItems.isEmpty())
        assertEquals(listOf(fixtureShare), screenState.borrowedItems)
    }

    @Test
    fun load_semVeiculosEmAmbos_disparaEmpty() = runTest {
        coEvery { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        coEvery { getVehiclesPage(0) } returns AppResult.Success(
            PagedVehicles(items = emptyList(), currentPage = 0, totalPages = 1, totalElements = 0),
        )
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(emptyList())

        val viewModel = createViewModel()

        assertEquals(VehiclesScreenState.Empty, viewModel.state.value.screenState)
    }

    @Test
    fun load_falhaAoBuscarCompartilhados_trataComoListaVaziaSemErroGlobal() = runTest {
        coEvery { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        coEvery { getVehiclesPage(0) } returns AppResult.Success(
            PagedVehicles(items = listOf(fixtureVehicle), currentPage = 0, totalPages = 1, totalElements = 1),
        )
        coEvery { getActiveSharedVehicles() } returns AppResult.Failure(AppError.Network)

        val viewModel = createViewModel()

        val screenState = viewModel.state.value.screenState as VehiclesScreenState.Success
        assertEquals(listOf(fixtureVehicle), screenState.ownedItems)
        assertTrue(screenState.borrowedItems.isEmpty())
    }

    @Test
    fun onBorrowedSelected_chamaSetActiveGuestVehicleEEmiteEfeito() = runTest {
        coEvery { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        coEvery { getVehiclesPage(0) } returns AppResult.Success(
            PagedVehicles(items = listOf(fixtureVehicle), currentPage = 0, totalPages = 1, totalElements = 1),
        )
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(listOf(fixtureShare))
        coEvery { setActiveGuestVehicle(99) } returns Unit

        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.onBorrowedSelected(fixtureShare)
            assertEquals(VehiclesEffect.NavigateToGuestVehicle(fixtureShare), awaitItem())
        }
        coVerify { setActiveGuestVehicle(99) }
    }

    @Test
    fun loadNextPage_preservesBorrowedItemsWhilePaginatingOwnedItems() = runTest {
        coEvery { sessionStore.activeVehicleIdFlow } returns flowOf(null)

        // Page 0: owned vehicle + borrowed items with hasMore=true
        val vehicle1 = fixtureVehicle.copy(id = 1)
        coEvery { getVehiclesPage(0) } returns AppResult.Success(
            PagedVehicles(items = listOf(vehicle1), currentPage = 0, totalPages = 2, totalElements = 2),
        )
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(listOf(fixtureShare))

        val viewModel = createViewModel()

        // Verify initial state has both owned and borrowed items
        val initialState = viewModel.state.value.screenState as VehiclesScreenState.Success
        assertEquals(listOf(vehicle1), initialState.ownedItems)
        assertEquals(listOf(fixtureShare), initialState.borrowedItems)

        // Page 1: additional owned vehicle
        val vehicle2 = fixtureVehicle.copy(id = 2, brand = "Honda", model = "Civic")
        coEvery { getVehiclesPage(1) } returns AppResult.Success(
            PagedVehicles(items = listOf(vehicle2), currentPage = 1, totalPages = 2, totalElements = 2),
        )

        // Load next page
        viewModel.loadNextPage()

        // Verify borrowed items preserved and owned items accumulated
        val finalState = viewModel.state.value.screenState as VehiclesScreenState.Success
        assertEquals(listOf(vehicle1, vehicle2), finalState.ownedItems)
        assertEquals(listOf(fixtureShare), finalState.borrowedItems)
    }
}
