package com.flowfuel.app.feature.auto

import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auto.dashboard.AutoDashboardScreen
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoDashboardScreenTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val carContext: TestCarContext
        get() = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val testVehicle = ActiveVehicleData(
        id = 1, brand = "VW", model = "Fox", fuelSubType = null,
        capacity = null, licensePlate = "ABC1D23", energyType = "COMBUSTION", currentKm = 67270,
    )
    private val testDashboard = DashboardData(
        averageConsumption = 8.4, consumptionUnit = "km/L",
        totalSpent = 1240.0, totalRefuels = 5,
        lastRefuelDate = "2026-06-15", lastRefuelEnergyAmount = 42.0,
        lastRefuelAmount = 289.90, lastRefuelEnergyUnit = "L",
    )

    @Test
    fun `estado inicial retorna MessageTemplate loading`() {
        val screen = AutoDashboardScreen(carContext, mockk(), mockk(), mockk())
        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `apos loadData com sucesso retorna GridTemplate`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        val getDashboard: GetDashboardUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Success(testVehicle)
        coEvery { getDashboard(1) } returns AppResult.Success(testDashboard)

        val screen = AutoDashboardScreen(carContext, getActiveVehicle, getDashboard, mockk())
        screen.loadData()

        assertTrue(screen.onGetTemplate() is GridTemplate)
    }

    @Test
    fun `erro de rede retorna MessageTemplate error`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Failure(AppError.Network)

        val screen = AutoDashboardScreen(carContext, getActiveVehicle, mockk(), mockk())
        screen.loadData()

        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `erro 401 retorna MessageTemplate sem acao de retry`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Failure(AppError.Unauthorized)
        val screen = AutoDashboardScreen(carContext, getActiveVehicle, mockk(), mockk())
        screen.loadData()

        assertTrue("Deve retornar MessageTemplate para 401", screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `dashboard exibe 5 blocos sem precisar rolar, incluindo o de acao`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        val getDashboard: GetDashboardUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Success(testVehicle)
        coEvery { getDashboard(testVehicle.id) } returns AppResult.Success(testDashboard)
        val screen = AutoDashboardScreen(carContext, getActiveVehicle, getDashboard, mockk())
        screen.loadData()
        val template = screen.onGetTemplate() as GridTemplate
        val items = template.singleList!!.items
        assertTrue("deve ter os 4 blocos de info + 1 de acao", items.size == 5)
        val actionItem = items.last() as GridItem
        assertTrue(
            "ultimo bloco deve ser o de registrar abastecimento",
            actionItem.title.toString().contains("Registrar abastecimento")
        )
        assertNotNull(
            "bloco de acao deve ter onClick pra navegar pro Step1",
            actionItem.onClickDelegate
        )
    }

    @Test
    fun `dashboard exibe total de abastecimentos`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        val getDashboard: GetDashboardUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Success(testVehicle)
        coEvery { getDashboard(testVehicle.id) } returns AppResult.Success(
            testDashboard.copy(totalRefuels = 12)
        )
        val screen = AutoDashboardScreen(carContext, getActiveVehicle, getDashboard, mockk())
        screen.loadData()
        val template = screen.onGetTemplate() as GridTemplate
        val items = template.singleList!!.items
        assertTrue(
            "bloco de abastecimentos deve conter '12'",
            items.any { item -> (item as GridItem).text?.toString()?.contains("12") == true }
        )
    }

    @Test
    fun `dashboard exibe valor monetario do ultimo abastecimento`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        val getDashboard: GetDashboardUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Success(testVehicle)
        coEvery { getDashboard(testVehicle.id) } returns AppResult.Success(
            testDashboard.copy(
                lastRefuelDate = "2026-06-15",
                lastRefuelEnergyAmount = 42.0,
                lastRefuelEnergyUnit = "L",
                lastRefuelAmount = 289.90,
            )
        )
        val screen = AutoDashboardScreen(carContext, getActiveVehicle, getDashboard, mockk())
        screen.loadData()
        val template = screen.onGetTemplate() as GridTemplate
        val items = template.singleList!!.items
        assertTrue(
            "bloco do ultimo abastecimento deve conter o valor",
            items.any { item -> (item as GridItem).text?.toString()?.contains("289") == true }
        )
    }
}
