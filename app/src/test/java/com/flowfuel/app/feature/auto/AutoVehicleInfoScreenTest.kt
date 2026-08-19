package com.flowfuel.app.feature.auto

import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auto.vehicleinfo.AutoVehicleInfoScreen
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.home.domain.usecase.GetDashboardUseCase
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
class AutoVehicleInfoScreenTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val carContext: TestCarContext
        get() = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val vehicle = ActiveVehicleData(
        id = 1, brand = "VW", model = "Fox", fuelSubType = null,
        capacity = null, licensePlate = "ABC1D23", energyType = "COMBUSTION", currentKm = 67270,
        manufactureYear = 2020, modelYear = 2021,
    )
    private val dashboard = DashboardData(
        averageConsumption = 12.4, consumptionUnit = "km/L",
        totalSpent = 1240.0, fuelSpent = 1240.0, totalRefuels = 5,
        lastRefuelDate = "2026-06-15", lastRefuelEnergyAmount = 42.0,
        lastRefuelAmount = 289.90, lastRefuelEnergyUnit = "L",
        averagePricePerUnit = 5.79, priceUnit = "R$/L",
    )

    @Test
    fun `estado inicial retorna MessageTemplate loading`() {
        val screen = AutoVehicleInfoScreen(carContext, vehicle, mockk())
        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `erro de rede retorna mensagem com acao de tentar novamente`() = runTest {
        val getDashboard: GetDashboardUseCase = mockk()
        coEvery { getDashboard(1) } returns AppResult.Failure(AppError.Network)

        val screen = AutoVehicleInfoScreen(carContext, vehicle, getDashboard)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isNotEmpty())
    }

    @Test
    fun `erro 401 retorna mensagem sem acao de retry`() = runTest {
        val getDashboard: GetDashboardUseCase = mockk()
        coEvery { getDashboard(1) } returns AppResult.Failure(AppError.Unauthorized)

        val screen = AutoVehicleInfoScreen(carContext, vehicle, getDashboard)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isEmpty())
    }

    @Test
    fun `sucesso mostra as 4 linhas da ficha do veiculo`() = runTest {
        val getDashboard: GetDashboardUseCase = mockk()
        coEvery { getDashboard(1) } returns AppResult.Success(dashboard)

        val screen = AutoVehicleInfoScreen(carContext, vehicle, getDashboard)
        screen.loadData()

        val template = screen.onGetTemplate() as ListTemplate
        val rows = template.singleList!!.items.map { it as Row }
        assertEquals(4, rows.size)

        assertEquals("VW Fox", rows[0].title.toString())
        assertEquals("2021 • ABC1D23", rows[0].texts.first().toString())

        assertTrue(rows[1].title.toString().contains("67270"))
        assertEquals("Odômetro", rows[1].texts.first().toString())

        assertTrue(rows[2].title.toString().contains("12,4"))
        assertEquals("Consumo médio", rows[2].texts.first().toString())

        assertTrue(rows[3].title.toString().contains("5,79"))
        assertEquals("Preço médio", rows[3].texts.first().toString())
    }

    @Test
    fun `sem placa mostra so o ano na segunda linha`() = runTest {
        val vehicleNoPlate = vehicle.copy(licensePlate = null)
        val getDashboard: GetDashboardUseCase = mockk()
        coEvery { getDashboard(1) } returns AppResult.Success(dashboard)

        val screen = AutoVehicleInfoScreen(carContext, vehicleNoPlate, getDashboard)
        screen.loadData()

        val template = screen.onGetTemplate() as ListTemplate
        val firstRow = template.singleList!!.items.first() as Row
        assertEquals("VW Fox", firstRow.title.toString())
        assertEquals("2021", firstRow.texts.first().toString())
    }

    @Test
    fun `sem placa e sem ano nao mostra segunda linha de texto vazia`() = runTest {
        val vehicleBare = vehicle.copy(licensePlate = null, manufactureYear = null, modelYear = null)
        val getDashboard: GetDashboardUseCase = mockk()
        coEvery { getDashboard(1) } returns AppResult.Success(dashboard)

        val screen = AutoVehicleInfoScreen(carContext, vehicleBare, getDashboard)
        screen.loadData()

        val template = screen.onGetTemplate() as ListTemplate
        val firstRow = template.singleList!!.items.first() as Row
        assertEquals("VW Fox", firstRow.title.toString())
        assertTrue(firstRow.texts.isEmpty())
    }

    @Test
    fun `sem ano de modelo usa ano de fabricacao`() = runTest {
        val vehicleManufactureOnly = vehicle.copy(modelYear = null)
        val getDashboard: GetDashboardUseCase = mockk()
        coEvery { getDashboard(1) } returns AppResult.Success(dashboard)

        val screen = AutoVehicleInfoScreen(carContext, vehicleManufactureOnly, getDashboard)
        screen.loadData()

        val template = screen.onGetTemplate() as ListTemplate
        val firstRow = template.singleList!!.items.first() as Row
        assertEquals("2020 • ABC1D23", firstRow.texts.first().toString())
    }

    @Test
    fun `consumo e preco ausentes mostram traco`() = runTest {
        val getDashboard: GetDashboardUseCase = mockk()
        coEvery { getDashboard(1) } returns AppResult.Success(
            dashboard.copy(averageConsumption = null, consumptionUnit = null, averagePricePerUnit = null)
        )

        val screen = AutoVehicleInfoScreen(carContext, vehicle, getDashboard)
        screen.loadData()

        val template = screen.onGetTemplate() as ListTemplate
        val rows = template.singleList!!.items.map { it as Row }
        assertEquals("—", rows[2].title.toString())
        assertEquals("—", rows[3].title.toString())
    }
}
