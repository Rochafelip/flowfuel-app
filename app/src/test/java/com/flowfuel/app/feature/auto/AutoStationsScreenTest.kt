package com.flowfuel.app.feature.auto

import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auto.stations.AutoStationsScreen
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.LocationResult
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.model.StationType
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoStationsScreenTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val carContext: TestCarContext
        get() = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val fuelVehicle = ActiveVehicleData(
        id = 1, brand = "VW", model = "Fox", fuelSubType = null,
        capacity = null, licensePlate = "ABC1D23", energyType = "COMBUSTION", currentKm = 67270,
    )
    private val location = GeoLocation(-23.5, -46.6)

    private fun station(
        name: String, type: StationType, distance: Int,
        lat: Double = -23.55, lng: Double = -46.63,
    ) = Station(
        placeId = name, name = name, type = type, distanceMeters = distance,
        rating = null, latitude = lat, longitude = lng,
    )

    @Test
    fun `estado inicial retorna MessageTemplate loading`() {
        val screen = AutoStationsScreen(carContext, fuelVehicle, mockk(), mockk())
        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `permissao negada retorna mensagem sem acao de retry`() = runTest {
        val locationProvider: LocationProvider = mockk()
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.PermissionDenied

        val screen = AutoStationsScreen(carContext, fuelVehicle, mockk(), locationProvider)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isEmpty())
    }

    @Test
    fun `sem fix de gps retorna mensagem informativa`() = runTest {
        val locationProvider: LocationProvider = mockk()
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Unavailable

        val screen = AutoStationsScreen(carContext, fuelVehicle, mockk(), locationProvider)
        screen.loadData()

        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `veiculo a combustao filtra so postos de combustivel, ordena por distancia e limita a 6`() = runTest {
        val locationProvider: LocationProvider = mockk()
        val getNearbyStations: GetNearbyStationsUseCase = mockk()
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        val stations = (1..8).map { station("Posto $it", StationType.Fuel, distance = it * 100) } +
            station("Eletroposto", StationType.Electric, distance = 50)
        coEvery { getNearbyStations(location, 3_000) } returns AppResult.Success(stations)

        val screen = AutoStationsScreen(carContext, fuelVehicle, getNearbyStations, locationProvider)
        screen.loadData()

        val template = screen.onGetTemplate() as ListTemplate
        val items = template.singleList!!.items
        assertTrue("deve limitar a 6 postos", items.size == 6)
        assertEquals("Posto 1", (items.first() as Row).title.toString())
    }

    @Test
    fun `veiculo eletrico filtra so postos eletricos`() = runTest {
        val electricVehicle = fuelVehicle.copy(energyType = "ELECTRIC")
        val locationProvider: LocationProvider = mockk()
        val getNearbyStations: GetNearbyStationsUseCase = mockk()
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        val stations = listOf(
            station("Posto combustível", StationType.Fuel, distance = 10),
            station("Eletroposto", StationType.Electric, distance = 500),
        )
        coEvery { getNearbyStations(location, 3_000) } returns AppResult.Success(stations)

        val screen = AutoStationsScreen(carContext, electricVehicle, getNearbyStations, locationProvider)
        screen.loadData()

        val template = screen.onGetTemplate() as ListTemplate
        val items = template.singleList!!.items
        assertTrue(items.size == 1)
        assertEquals("Eletroposto", (items.first() as Row).title.toString())
    }

    @Test
    fun `lista vazia apos filtro retorna mensagem informativa`() = runTest {
        val locationProvider: LocationProvider = mockk()
        val getNearbyStations: GetNearbyStationsUseCase = mockk()
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, 3_000) } returns AppResult.Success(emptyList())

        val screen = AutoStationsScreen(carContext, fuelVehicle, getNearbyStations, locationProvider)
        screen.loadData()

        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `erro de rede retorna mensagem com acao de tentar novamente`() = runTest {
        val locationProvider: LocationProvider = mockk()
        val getNearbyStations: GetNearbyStationsUseCase = mockk()
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, 3_000) } returns AppResult.Failure(AppError.Network)

        val screen = AutoStationsScreen(carContext, fuelVehicle, getNearbyStations, locationProvider)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isNotEmpty())
    }

    @Test
    fun `erro 401 retorna mensagem sem acao de retry`() = runTest {
        val locationProvider: LocationProvider = mockk()
        val getNearbyStations: GetNearbyStationsUseCase = mockk()
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, 3_000) } returns AppResult.Failure(AppError.Unauthorized)

        val screen = AutoStationsScreen(carContext, fuelVehicle, getNearbyStations, locationProvider)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isEmpty())
    }

    @Test
    fun `testNavigateTo dispara ACTION_NAVIGATE com geo do posto`() {
        val testContext = carContext
        val screen = AutoStationsScreen(testContext, fuelVehicle, mockk(), mockk())
        val target = station("Posto Ipiranga", StationType.Fuel, distance = 200, lat = -23.55, lng = -46.63)

        screen.testNavigateTo(target)

        val intent = testContext.startCarAppIntents.single()
        assertEquals(CarContext.ACTION_NAVIGATE, intent.action)
        assertEquals(Uri.parse("geo:-23.55,-46.63"), intent.data)
    }
}
