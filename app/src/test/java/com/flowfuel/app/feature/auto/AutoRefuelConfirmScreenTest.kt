package com.flowfuel.app.feature.auto

import androidx.car.app.model.MessageTemplate
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auto.refuel.AutoRefuelConfirmScreen
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.CreateRefuelRequest
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class AutoRefuelConfirmScreenTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val carContext = TestCarContext.createCarContext(
        ApplicationProvider.getApplicationContext()
    )
    private val createRefuel: CreateRefuelUseCase = mockk()

    private val combustionVehicle = ActiveVehicleData(
        id = 7, brand = "Honda", model = "Civic", fuelSubType = "GASOLINE",
        capacity = 47.0, licensePlate = "XYZ9876", energyType = "COMBUSTION", currentKm = 80000,
    )
    private val hybridVehicle = combustionVehicle.copy(id = 8, energyType = "HYBRID")
    private val electricVehicle = combustionVehicle.copy(id = 9, energyType = "ELECTRIC")

    @Test
    fun `submit calcula odometro corretamente para veiculo combustao`() = runTest {
        coEvery { createRefuel(any()) } returns AppResult.Success(Unit)
        val screen = AutoRefuelConfirmScreen(
            carContext = carContext,
            vehicle = combustionVehicle,
            tripKm = 150.0,
            liters = 45.5,
            totalPrice = 289.90,
            createRefuel = createRefuel,
        )
        screen.testSubmit()
        advanceUntilIdle()
        coVerify {
            createRefuel(
                CreateRefuelRequest(
                    vehicleId = 7,
                    odometer = 80150.0,
                    liters = 45.5,
                    totalPrice = 289.90,
                    fullTank = false,
                    refuelType = "FUEL",
                )
            )
        }
    }

    @Test
    fun `submit usa refuelType FUEL para veiculo hibrido`() = runTest {
        coEvery { createRefuel(any()) } returns AppResult.Success(Unit)
        val screen = AutoRefuelConfirmScreen(
            carContext = carContext,
            vehicle = hybridVehicle,
            tripKm = 100.0,
            liters = 30.0,
            totalPrice = 200.0,
            createRefuel = createRefuel,
        )
        screen.testSubmit()
        advanceUntilIdle()
        coVerify {
            createRefuel(match { it.refuelType == "FUEL" })
        }
    }

    @Test
    fun `submit usa refuelType ELECTRIC para veiculo eletrico`() = runTest {
        coEvery { createRefuel(any()) } returns AppResult.Success(Unit)
        val screen = AutoRefuelConfirmScreen(
            carContext = carContext,
            vehicle = electricVehicle,
            tripKm = 80.0,
            liters = 30.0,
            totalPrice = 60.0,
            createRefuel = createRefuel,
        )
        screen.testSubmit()
        advanceUntilIdle()
        coVerify {
            createRefuel(match { it.refuelType == "ELECTRIC" })
        }
    }

    @Test
    fun `erro 401 durante submit exibe MessageTemplate de sessao expirada`() = runTest {
        coEvery { createRefuel(any()) } returns AppResult.Failure(AppError.Unauthorized)
        val screen = AutoRefuelConfirmScreen(
            carContext = carContext,
            vehicle = combustionVehicle,
            tripKm = 100.0,
            liters = 40.0,
            totalPrice = 250.0,
            createRefuel = createRefuel,
        )
        screen.testSubmit()
        advanceUntilIdle()
        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `erro de rede exibe MessageTemplate com botao tentar novamente`() = runTest {
        coEvery { createRefuel(any()) } returns AppResult.Failure(AppError.Network)
        val screen = AutoRefuelConfirmScreen(
            carContext = carContext,
            vehicle = combustionVehicle,
            tripKm = 100.0,
            liters = 40.0,
            totalPrice = 250.0,
            createRefuel = createRefuel,
        )
        screen.testSubmit()
        advanceUntilIdle()
        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }
}
