package com.flowfuel.app.feature.auto

import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auto.menu.AutoMenuScreen
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
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
class AutoMenuScreenTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val carContext: TestCarContext
        get() = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val testVehicle = ActiveVehicleData(
        id = 1, brand = "VW", model = "Fox", fuelSubType = null,
        capacity = null, licensePlate = "ABC1D23", energyType = "COMBUSTION", currentKm = 67270,
    )

    private fun makeScreen(getActiveVehicle: GetActiveVehicleUseCase) = AutoMenuScreen(
        carContext, getActiveVehicle, mockk(), mockk(), mockk(), mockk(), mockk(),
    )

    @Test
    fun `estado inicial retorna MessageTemplate loading`() {
        val screen = makeScreen(mockk())
        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `apos loadData com sucesso retorna GridTemplate com os 4 itens do menu`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Success(testVehicle)

        val screen = makeScreen(getActiveVehicle)
        screen.loadData()

        val template = screen.onGetTemplate() as GridTemplate
        val items = template.singleList!!.items
        assertTrue("deve ter os 4 itens do menu, cabendo sem rolagem (limite é 6)", items.size == 4)
        val titles = items.map { (it as GridItem).title.toString() }
        assertEquals(
            listOf("Registrar abastecimento", "Postos próximos", "Eventos", "Informações importantes"),
            titles,
        )
        items.forEach { assertNotNull((it as GridItem).onClickDelegate) }
    }

    @Test
    fun `erro de rede retorna MessageTemplate com acao de tentar novamente`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Failure(AppError.Network)

        val screen = makeScreen(getActiveVehicle)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isNotEmpty())
    }

    @Test
    fun `erro 401 retorna MessageTemplate sem acao de retry`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Failure(AppError.Unauthorized)

        val screen = makeScreen(getActiveVehicle)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isEmpty())
    }
}
