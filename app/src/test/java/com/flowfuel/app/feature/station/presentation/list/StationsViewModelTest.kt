package com.flowfuel.app.feature.station.presentation.list

import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.NearbyStationsPrefetcher
import com.flowfuel.app.feature.station.domain.model.DEFAULT_STATION_RADIUS_METERS
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.LocationResult
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.model.StationType
import com.flowfuel.app.feature.station.domain.model.stationDistanceBand
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehicleByIdUseCase
import app.cash.turbine.test
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
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
    private val stationsPrefetcher: NearbyStationsPrefetcher = mockk()

    private val location = GeoLocation(latitude = -8.05, longitude = -34.90)

    // Cada preset é uma faixa exclusiva de distância — a API é sempre chamada com o teto da
    // faixa (próximo preset - 1), não com o valor do preset em si.
    private val defaultBandMaxMeters = stationDistanceBand(DEFAULT_STATION_RADIUS_METERS).maxMeters
    private val farthestBandMaxMeters = stationDistanceBand(10_000).maxMeters
    private val defaultBandDistance = DEFAULT_STATION_RADIUS_METERS + 500

    private fun station(id: String, distance: Int = defaultBandDistance) = Station(
        placeId = id, name = "Posto $id", type = StationType.Fuel,
        distanceMeters = distance, rating = null, latitude = -8.05, longitude = -34.90,
    )

    private fun vehicle(energyType: EnergyType) = Vehicle(
        id = 7, brand = "Toyota", model = "Corolla", manufactureYear = 2020, modelYear = 2020,
        licensePlate = "ABC1234", color = "Prata", type = VehicleType.Car, energyType = energyType,
        fuelType = null, odometerKm = 1000, tankCapacityL = null, batteryCapacityKwh = null, isActive = true,
    )

    private fun buildViewModel() =
        StationsViewModel(getNearbyStations, locationProvider, sessionStore, getVehicleById, stationsPrefetcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        every { stationsPrefetcher.freshCachedStations() } returns null
        every { stationsPrefetcher.updateCache(any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Success state when stations are found`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))

        val vm = buildViewModel()

        val state = vm.state.value
        assertTrue(state is StationsUiState.Success)
        assertEquals(1, (state as StationsUiState.Success).stations.size)
    }

    @Test
    fun `Empty state when repository returns no stations`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(emptyList())

        val vm = buildViewModel()

        assertEquals(StationsUiState.Empty, vm.state.value)
    }

    @Test
    fun `Error state when repository fails`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Failure(AppError.Network)

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
    fun `onRouteClick emits OpenNavigation with a generic geo uri`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))
        val vm = buildViewModel()

        vm.effects.test {
            vm.onRouteClick(station("a"))
            val effect = awaitItem()
            assertTrue(effect is StationsEffect.OpenNavigation)
            assertEquals("geo:-8.05,-34.9?q=-8.05,-34.9", (effect as StationsEffect.OpenNavigation).uri)
        }
    }

    @Test
    fun `radiusMeters starts at the default preset`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))

        val vm = buildViewModel()

        assertEquals(DEFAULT_STATION_RADIUS_METERS, vm.radiusMeters.value)
    }

    @Test
    fun `onRadiusSelected updates radiusMeters and reloads stations with the new band's query radius`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))
        coEvery { getNearbyStations(location, farthestBandMaxMeters) } returns AppResult.Success(
            listOf(station("a", 11_000), station("b", 19_000))
        )
        val vm = buildViewModel()

        vm.onRadiusSelected(10_000)

        assertEquals(10_000, vm.radiusMeters.value)
        val state = vm.state.value
        assertTrue(state is StationsUiState.Success)
        assertEquals(2, (state as StationsUiState.Success).stations.size)
        coVerify { getNearbyStations(location, farthestBandMaxMeters) }
    }

    @Test
    fun `load() excludes stations below the selected band's lower bound`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(
            listOf(station("below", DEFAULT_STATION_RADIUS_METERS - 1), station("inBand", defaultBandDistance))
        )

        val vm = buildViewModel()

        val state = vm.state.value
        assertTrue(state is StationsUiState.Success)
        assertEquals(listOf(station("inBand", defaultBandDistance)), (state as StationsUiState.Success).stations)
    }

    @Test
    fun `load() at the farthest preset shows only stations at or beyond its lower bound`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))
        coEvery { getNearbyStations(location, farthestBandMaxMeters) } returns AppResult.Success(
            listOf(station("tooClose", 9_999), station("farEnough", 15_000))
        )
        val vm = buildViewModel()

        vm.onRadiusSelected(10_000)

        val state = vm.state.value
        assertTrue(state is StationsUiState.Success)
        assertEquals(listOf(station("farEnough", 15_000)), (state as StationsUiState.Success).stations)
    }

    @Test
    fun `selectedType starts at Fuel by default`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))

        val vm = buildViewModel()

        assertEquals(StationType.Fuel, vm.selectedType.value)
    }

    @Test
    fun `onTypeSelected updates selectedType without triggering a new load`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))
        val vm = buildViewModel()

        vm.onTypeSelected(StationType.Electric)

        assertEquals(StationType.Electric, vm.selectedType.value)
        coVerify(exactly = 1) { getNearbyStations(location, defaultBandMaxMeters) }
    }

    @Test
    fun `selectedType defaults to Fuel when there is no active vehicle`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))

        val vm = buildViewModel()

        assertEquals(StationType.Fuel, vm.selectedType.value)
        coVerify(inverse = true) { getVehicleById(any()) }
    }

    @Test
    fun `selectedType syncs to Electric when the active vehicle is Electric`() = runTest {
        every { sessionStore.activeVehicleIdFlow } returns flowOf(7)
        coEvery { getVehicleById(7) } returns AppResult.Success(vehicle(EnergyType.Electric))
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))

        val vm = buildViewModel()

        assertEquals(StationType.Electric, vm.selectedType.value)
    }

    @Test
    fun `selectedType syncs to Fuel when the active vehicle is Combustion or Hybrid`() = runTest {
        every { sessionStore.activeVehicleIdFlow } returns flowOf(7)
        coEvery { getVehicleById(7) } returns AppResult.Success(vehicle(EnergyType.Hybrid))
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))

        val vm = buildViewModel()

        assertEquals(StationType.Fuel, vm.selectedType.value)
    }

    @Test
    fun `selectedType falls back to Fuel and load still runs when getVehicleById fails`() = runTest {
        every { sessionStore.activeVehicleIdFlow } returns flowOf(7)
        coEvery { getVehicleById(7) } returns AppResult.Failure(AppError.Network)
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))

        val vm = buildViewModel()

        assertEquals(StationType.Fuel, vm.selectedType.value)
        assertTrue(vm.state.value is StationsUiState.Success)
    }

    @Test
    fun `init populates Success directly from a fresh prefetch cache, without calling locationProvider or getNearbyStations`() = runTest {
        every { stationsPrefetcher.freshCachedStations() } returns listOf(station("a"))

        val vm = buildViewModel()

        val state = vm.state.value
        assertTrue(state is StationsUiState.Success)
        assertEquals(1, (state as StationsUiState.Success).stations.size)
        coVerify(inverse = true) { locationProvider.getCurrentLocation() }
        coVerify(inverse = true) { getNearbyStations(any(), any()) }
    }

    @Test
    fun `init populates Empty when the fresh prefetch cache is an empty list`() = runTest {
        every { stationsPrefetcher.freshCachedStations() } returns emptyList()

        val vm = buildViewModel()

        assertEquals(StationsUiState.Empty, vm.state.value)
        coVerify(inverse = true) { locationProvider.getCurrentLocation() }
    }

    @Test
    fun `init falls back to load() when there is no prefetch cache`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))

        val vm = buildViewModel()

        assertTrue(vm.state.value is StationsUiState.Success)
        coVerify { locationProvider.getCurrentLocation() }
    }

    @Test
    fun `load() at the default radius updates the prefetch cache on success`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))

        buildViewModel()

        verify { stationsPrefetcher.updateCache(listOf(station("a"))) }
    }

    @Test
    fun `load() at a non-default radius does not update the prefetch cache again`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))
        coEvery { getNearbyStations(location, farthestBandMaxMeters) } returns AppResult.Success(
            listOf(station("a", 11_000), station("b", 19_000))
        )
        val vm = buildViewModel()

        vm.onRadiusSelected(10_000)

        verify(exactly = 1) { stationsPrefetcher.updateCache(any()) }
    }
}
