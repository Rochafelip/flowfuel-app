package com.flowfuel.app.feature.auto

import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.TestScreenManager
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.feature.auto.refuel.AutoRefuelStep1Screen
import com.flowfuel.app.feature.auto.refuel.AutoRefuelStep2Screen
import com.flowfuel.app.feature.auto.refuel.AutoRefuelStep3Screen
import com.flowfuel.app.feature.auto.refuel.OdometerInput
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoRefuelStepScreensTest {

    private val carContext = TestCarContext.createCarContext(
        ApplicationProvider.getApplicationContext()
    )
    private val createRefuel: CreateRefuelUseCase = mockk()
    private val vehicle = ActiveVehicleData(
        id = 1, brand = "Toyota", model = "Corolla", fuelSubType = "GASOLINE",
        capacity = 50.0, licensePlate = "ABC1234", energyType = "COMBUSTION", currentKm = 50000,
    )

    private fun lastPushed() =
        carContext.getCarService(TestScreenManager::class.java).screensPushed.last()

    // ─── Step 1 ───────────────────────────────────────────────────────────────

    @Test
    fun `Step1 retorna GridTemplate com 13 itens (12 do teclado + alternar modo)`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        val template = screen.onGetTemplate() as GridTemplate
        assertEquals(13, template.singleList!!.items.size)
    }

    @Test
    fun `Step1 comeca no modo Percurso, titulo com valor zerado`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        val template = screen.onGetTemplate() as GridTemplate
        assertEquals("Percurso: 0,0 km", template.title.toString())
    }

    @Test
    fun `Step1 digitar 1,5,0 mostra 15,0 km no titulo`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        screen.testDigit('1')
        screen.testDigit('5')
        screen.testDigit('0')
        val template = screen.onGetTemplate() as GridTemplate
        assertEquals("Percurso: 15,0 km", template.title.toString())
    }

    @Test
    fun `Step1 apagar remove o ultimo digito`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        screen.testDigit('1')
        screen.testDigit('5')
        screen.testDigit('0')
        screen.testBackspace()
        val template = screen.onGetTemplate() as GridTemplate
        assertEquals("Percurso: 1,5 km", template.title.toString())
    }

    @Test
    fun `Step1 alternar pro modo Odometro muda o titulo e zera o valor`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        screen.testDigit('1')
        screen.testDigit('5')
        screen.testToggleMode()

        val template = screen.onGetTemplate() as GridTemplate
        assertEquals("Odômetro: 0,0 km", template.title.toString())
    }

    @Test
    fun `Step1 alternar duas vezes volta pro modo Percurso`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        screen.testToggleMode()
        screen.testToggleMode()

        val template = screen.onGetTemplate() as GridTemplate
        assertEquals("Percurso: 0,0 km", template.title.toString())
    }

    @Test
    fun `Step1 confirmar com valor zero nao avanca de tela`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        screen.testConfirm()
        assertTrue(carContext.getCarService(TestScreenManager::class.java).screensPushed.isEmpty())
    }

    @Test
    fun `Step1 no modo Percurso, confirmar constroi OdometerInput_Trip e avanca`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        screen.testDigit('1')
        screen.testDigit('5')
        screen.testDigit('0')
        screen.testDigit('0')
        screen.testConfirm()

        val pushed = lastPushed()
        assertTrue(pushed is AutoRefuelStep2Screen)
        val odometerInput = (pushed as AutoRefuelStep2Screen).testOdometerInput()
        assertTrue(odometerInput is OdometerInput.Trip)
        assertEquals(150.0, (odometerInput as OdometerInput.Trip).km, 0.0)
    }

    @Test
    fun `Step1 no modo Odometro, confirmar constroi OdometerInput_Odometer e avanca`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        screen.testToggleMode()
        "675200".forEach { screen.testDigit(it) }
        screen.testConfirm()

        val pushed = lastPushed()
        assertTrue(pushed is AutoRefuelStep2Screen)
        val odometerInput = (pushed as AutoRefuelStep2Screen).testOdometerInput()
        assertTrue(odometerInput is OdometerInput.Odometer)
        assertEquals(67520.0, (odometerInput as OdometerInput.Odometer).value, 0.0)
    }

    // ─── Step 2 ───────────────────────────────────────────────────────────────

    @Test
    fun `Step2 retorna GridTemplate com 12 itens`() {
        val screen = AutoRefuelStep2Screen(carContext, vehicle, OdometerInput.Trip(100.0), createRefuel)
        val template = screen.onGetTemplate() as GridTemplate
        assertEquals(12, template.singleList!!.items.size)
    }

    @Test
    fun `Step2 confirmar com valor zero nao avanca`() {
        val screen = AutoRefuelStep2Screen(carContext, vehicle, OdometerInput.Trip(100.0), createRefuel)
        screen.testConfirm()
        assertTrue(carContext.getCarService(TestScreenManager::class.java).screensPushed.isEmpty())
    }

    @Test
    fun `Step2 digitar 4,5,5 e confirmar avanca com 45,5 litros`() {
        val screen = AutoRefuelStep2Screen(carContext, vehicle, OdometerInput.Trip(100.0), createRefuel)
        "455".forEach { screen.testDigit(it) }
        screen.testConfirm()

        val pushed = lastPushed()
        assertTrue(pushed is AutoRefuelStep3Screen)
        assertEquals(45.5, (pushed as AutoRefuelStep3Screen).testLiters(), 0.0)
    }

    // ─── Step 3 ───────────────────────────────────────────────────────────────

    @Test
    fun `Step3 retorna GridTemplate com 12 itens`() {
        val screen = AutoRefuelStep3Screen(
            carContext, vehicle, OdometerInput.Trip(100.0), liters = 45.5, createRefuel,
        )
        val template = screen.onGetTemplate() as GridTemplate
        assertEquals(12, template.singleList!!.items.size)
    }

    @Test
    fun `Step3 confirmar com valor zero nao avanca`() {
        val screen = AutoRefuelStep3Screen(
            carContext, vehicle, OdometerInput.Trip(100.0), liters = 45.5, createRefuel,
        )
        screen.testConfirm()
        assertTrue(carContext.getCarService(TestScreenManager::class.java).screensPushed.isEmpty())
    }

    @Test
    fun `Step3 digitar 28990 mostra R$ 289,90 no titulo e confirmar avanca`() {
        val screen = AutoRefuelStep3Screen(
            carContext, vehicle, OdometerInput.Trip(100.0), liters = 45.5, createRefuel,
        )
        "28990".forEach { screen.testDigit(it) }
        val template = screen.onGetTemplate() as GridTemplate
        assertTrue(template.title.toString().contains("289,90"))

        screen.testConfirm()
        val pushed = lastPushed()
        assertTrue(pushed is com.flowfuel.app.feature.auto.refuel.AutoRefuelConfirmScreen)
    }

    @Test
    fun `todos os botoes do teclado tem onClick configurado`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        val template = screen.onGetTemplate() as GridTemplate
        template.singleList!!.items.forEach { item ->
            assertTrue((item as GridItem).onClickDelegate != null)
        }
    }
}
