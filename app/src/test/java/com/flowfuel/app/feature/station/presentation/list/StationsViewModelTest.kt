package com.flowfuel.app.feature.station.presentation.list

import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.model.DEFAULT_STATION_RADIUS_METERS
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.LocationResult
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.model.StationType
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehicleByIdUseCase
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
class StationsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val getNearbyStations: GetNearbyStationsUseCase = mockk()
    private val locationProvider: LocationProvider = mockk()
    private val sessionStore: SessionStore = mockk(relaxed = true)
    private val getVehicleById: GetVehicleByIdUseCase = mockk()

    private val location = GeoLocation(latitude = -8.05, longitude = -34.90)

    private fun station(id: String, distance: Int) = Station(
        placeId = id, name = "Posto $id", type = StationType.Fuel,
        distanceMeters = distance, rating = null, latitude = -8.05, longitude = -34.90,
    )

    private fun vehicle(energyType: EnergyType) = Vehicle(
        id = 7, brand = "Toyota", model = "Corolla", manufactureYear = 2020, modelYear = 2020,
        licensePlate = "ABC1234", color = "Prata", type = VehicleType.Car, energyType = energyType,
        fuelType = null, odometerKm = 1000, tankCapacityL = null, batteryCapacityKwh = null, isActive = true,
    )

    private fun buildViewModel() = StationsViewModel(getNearbyStations, locationProvider, sessionStore, getVehicleById)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { sessionStore.activeVehicleIdFlow } returns flowOf(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Success state when stations are found`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))

        val vm = buildViewModel()

        val state = vm.state.value
        assertTrue(state is StationsUiState.Success)
        assertEquals(1, (state as StationsUiState.Success).stations.size)
    }

    @Test
    fun `Empty state when repository returns no stations`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(emptyList())

        val vm = buildViewModel()

        assertEquals(StationsUiState.Empty, vm.state.value)
    }

    @Test
    fun `Error state when repository fails`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Failure(AppError.Network)

        val vm = buildViewModel()

        assertEquals(StationsUiState.Error(AppError.Network), vm.state.value)
    }

    @Test
    fun `PermissionRequired state when location permission denied`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.PermissionDenied

        val vm = buildViewModel()

        assertEquals(StationsUiState.PermissionRequired, vm.state.value)
        coVerify(inverse = true) { getNearbyStations(any(), any()) }
    }

    @Test
    fun `LocationUnavailable state when GPS has no fix`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Unavailable

        val vm = buildViewModel()

        assertEquals(StationsUiState.LocationUnavailable, vm.state.value)
    }

    @Test
    fun `onRouteClick emits OpenNavigation with lat,lng uri`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))
        val vm = buildViewModel()

        vm.effects.test {
            vm.onRouteClick(station("a", 100))
            val effect = awaitItem()
            assertTrue(effect is StationsEffect.OpenNavigation)
            assertEquals("google.navigation:q=-8.05,-34.9", (effect as StationsEffect.OpenNavigation).uri)
        }
    }

    @Test
    fun `radiusMeters starts at the default preset`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))

        val vm = buildViewModel()

        assertEquals(DEFAULT_STATION_RADIUS_METERS, vm.radiusMeters.value)
    }

    @Test
    fun `onRadiusSelected updates radiusMeters and reloads stations with the new radius`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))
        coEvery { getNearbyStations(location, 10_000) } returns AppResult.Success(listOf(station("a", 100), station("b", 9000)))
        val vm = buildViewModel()

        vm.onRadiusSelected(10_000)

        assertEquals(10_000, vm.radiusMeters.value)
        val state = vm.state.value
        assertTrue(state is StationsUiState.Success)
        assertEquals(2, (state as StationsUiState.Success).stations.size)
        coVerify { getNearbyStations(location, 10_000) }
    }

    @Test
    fun `selectedType starts at Fuel by default`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))

        val vm = buildViewModel()

        assertEquals(StationType.Fuel, vm.selectedType.value)
    }

    @Test
    fun `onTypeSelected updates selectedType without triggering a new load`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))
        val vm = buildViewModel()

        vm.onTypeSelected(StationType.Electric)

        assertEquals(StationType.Electric, vm.selectedType.value)
        coVerify(exactly = 1) { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) }
    }

    @Test
    fun `selectedType defaults to Fuel when there is no active vehicle`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))

        val vm = buildViewModel()

        assertEquals(StationType.Fuel, vm.selectedType.value)
        coVerify(inverse = true) { getVehicleById(any()) }
    }

    @Test
    fun `selectedType syncs to Electric when the active vehicle is Electric`() = runTest {
        every { sessionStore.activeVehicleIdFlow } returns flowOf(7)
        coEvery { getVehicleById(7) } returns AppResult.Success(vehicle(EnergyType.Electric))
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))

        val vm = buildViewModel()

        assertEquals(StationType.Electric, vm.selectedType.value)
    }

    @Test
    fun `selectedType syncs to Fuel when the active vehicle is Combustion or Hybrid`() = runTest {
        every { sessionStore.activeVehicleIdFlow } returns flowOf(7)
        coEvery { getVehicleById(7) } returns AppResult.Success(vehicle(EnergyType.Hybrid))
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))

        val vm = buildViewModel()

        assertEquals(StationType.Fuel, vm.selectedType.value)
    }

    @Test
    fun `selectedType falls back to Fuel and load still runs when getVehicleById fails`() = runTest {
        every { sessionStore.activeVehicleIdFlow } returns flowOf(7)
        coEvery { getVehicleById(7) } returns AppResult.Failure(AppError.Network)
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))

        val vm = buildViewModel()

        assertEquals(StationType.Fuel, vm.selectedType.value)
        assertTrue(vm.state.value is StationsUiState.Success)
    }
}
