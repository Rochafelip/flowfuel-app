package com.flowfuel.app.feature.auto

import androidx.car.app.model.signin.SignInTemplate
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.feature.auto.refuel.AutoRefuelStep1Screen
import com.flowfuel.app.feature.auto.refuel.AutoRefuelStep2Screen
import com.flowfuel.app.feature.auto.refuel.AutoRefuelStep3Screen
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import io.mockk.mockk
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

    // ─── Step 1 ───────────────────────────────────────────────────────────────

    @Test
    fun `Step1 retorna SignInTemplate`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        assertTrue(screen.onGetTemplate() is SignInTemplate)
    }

    @Test
    fun `Step1 input zero mantem template sem excecao`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        screen.testAdvance("0")
        assertTrue(screen.onGetTemplate() is SignInTemplate)
    }

    @Test
    fun `Step1 input texto invalido mantem template sem excecao`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        screen.testAdvance("abc")
        assertTrue(screen.onGetTemplate() is SignInTemplate)
    }

    @Test
    fun `Step1 input valido nao lanca excecao`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        screen.testAdvance("150")
    }

    @Test
    fun `Step1 aceita virgula como separador decimal`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        screen.testAdvance("150,5")
    }

    // ─── Step 2 ───────────────────────────────────────────────────────────────

    @Test
    fun `Step2 retorna SignInTemplate`() {
        val screen = AutoRefuelStep2Screen(carContext, vehicle, tripKm = 100.0, createRefuel)
        assertTrue(screen.onGetTemplate() is SignInTemplate)
    }

    @Test
    fun `Step2 input invalido mantem template sem excecao`() {
        val screen = AutoRefuelStep2Screen(carContext, vehicle, tripKm = 100.0, createRefuel)
        screen.testAdvance("0")
        assertTrue(screen.onGetTemplate() is SignInTemplate)
    }

    @Test
    fun `Step2 input valido nao lanca excecao`() {
        val screen = AutoRefuelStep2Screen(carContext, vehicle, tripKm = 100.0, createRefuel)
        screen.testAdvance("45,5")
    }

    // ─── Step 3 ───────────────────────────────────────────────────────────────

    @Test
    fun `Step3 retorna SignInTemplate`() {
        val screen = AutoRefuelStep3Screen(carContext, vehicle, tripKm = 100.0, liters = 45.5, createRefuel)
        assertTrue(screen.onGetTemplate() is SignInTemplate)
    }

    @Test
    fun `Step3 input invalido mantem template sem excecao`() {
        val screen = AutoRefuelStep3Screen(carContext, vehicle, tripKm = 100.0, liters = 45.5, createRefuel)
        screen.testAdvance("0")
        assertTrue(screen.onGetTemplate() is SignInTemplate)
    }

    @Test
    fun `Step3 input valido nao lanca excecao`() {
        val screen = AutoRefuelStep3Screen(carContext, vehicle, tripKm = 100.0, liters = 45.5, createRefuel)
        screen.testAdvance("289,90")
    }
}
