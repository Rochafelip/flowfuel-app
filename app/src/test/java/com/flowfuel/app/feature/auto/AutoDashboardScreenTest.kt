package com.flowfuel.app.feature.auto

import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.PaneTemplate
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
    fun `apos loadData com sucesso retorna PaneTemplate`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        val getDashboard: GetDashboardUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Success(testVehicle)
        coEvery { getDashboard(1) } returns AppResult.Success(testDashboard)

        val screen = AutoDashboardScreen(carContext, getActiveVehicle, getDashboard, mockk())
        screen.loadData()

        assertTrue(screen.onGetTemplate() is PaneTemplate)
    }

    @Test
    fun `erro de rede retorna MessageTemplate error`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Failure(AppError.Network)

        val screen = AutoDashboardScreen(carContext, getActiveVehicle, mockk(), mockk())
        screen.loadData()

        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }
}
