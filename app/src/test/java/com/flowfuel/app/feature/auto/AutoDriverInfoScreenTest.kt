package com.flowfuel.app.feature.auto

import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auto.driverinfo.AutoDriverInfoScreen
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceItem
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceType
import com.flowfuel.app.feature.home.domain.usecase.GetUpcomingMaintenanceUseCase
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
class AutoDriverInfoScreenTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val carContext: TestCarContext
        get() = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val vehicle = ActiveVehicleData(
        id = 1, brand = "VW", model = "Fox", fuelSubType = null,
        capacity = null, licensePlate = "ABC1D23", energyType = "COMBUSTION", currentKm = 67270,
    )

    @Test
    fun `estado inicial retorna MessageTemplate loading`() {
        val screen = AutoDriverInfoScreen(carContext, vehicle, mockk())
        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `erro de rede retorna mensagem com acao de tentar novamente`() = runTest {
        val getUpcomingMaintenance: GetUpcomingMaintenanceUseCase = mockk()
        coEvery { getUpcomingMaintenance(1, 67270) } returns AppResult.Failure(AppError.Network)

        val screen = AutoDriverInfoScreen(carContext, vehicle, getUpcomingMaintenance)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isNotEmpty())
    }

    @Test
    fun `erro 401 retorna mensagem sem acao de retry`() = runTest {
        val getUpcomingMaintenance: GetUpcomingMaintenanceUseCase = mockk()
        coEvery { getUpcomingMaintenance(1, 67270) } returns AppResult.Failure(AppError.Unauthorized)

        val screen = AutoDriverInfoScreen(carContext, vehicle, getUpcomingMaintenance)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isEmpty())
    }

    @Test
    fun `sucesso mostra as 3 linhas fixas com titulo e status`() = runTest {
        val getUpcomingMaintenance: GetUpcomingMaintenanceUseCase = mockk()
        val items = listOf(
            UpcomingMaintenanceItem(type = UpcomingMaintenanceType.OIL_CHANGE, remainingKm = 320),
            UpcomingMaintenanceItem(type = UpcomingMaintenanceType.TIRE_ROTATION, remainingKm = -150, isOverdue = true),
            UpcomingMaintenanceItem(type = UpcomingMaintenanceType.LICENSING, needsSetup = true),
        )
        coEvery { getUpcomingMaintenance(1, 67270) } returns AppResult.Success(items)

        val screen = AutoDriverInfoScreen(carContext, vehicle, getUpcomingMaintenance)
        screen.loadData()

        val template = screen.onGetTemplate() as ListTemplate
        val rows = template.singleList!!.items.map { it as Row }
        assertTrue("deve ter 3 linhas fixas", rows.size == 3)
        assertEquals("Troca de óleo", rows[0].title.toString())
        assertEquals("Em 320 km", rows[0].texts.first().toString())
        assertEquals("Rodízio de pneus", rows[1].title.toString())
        assertEquals("Atrasado 150 km", rows[1].texts.first().toString())
        assertEquals("Licenciamento", rows[2].title.toString())
        assertEquals("Defina a data de licenciamento", rows[2].texts.first().toString())
    }
}
