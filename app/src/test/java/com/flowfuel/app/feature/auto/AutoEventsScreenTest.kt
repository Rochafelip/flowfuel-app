package com.flowfuel.app.feature.auto

import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auto.events.AutoEventsScreen
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsUseCase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoEventsScreenTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val carContext: TestCarContext
        get() = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun event(
        id: Int, title: String, date: String, amount: Double? = null,
        category: EventCategory = EventCategory.MAINTENANCE,
    ) = VehicleEvent(
        id = id, vehicleId = 1, category = category, title = title, description = null,
        amount = amount, eventDate = date, odometerKm = null, notes = null,
        receiptUrl = null, createdAt = null, updatedAt = null,
    )

    @Test
    fun `estado inicial retorna MessageTemplate loading`() {
        val screen = AutoEventsScreen(carContext, vehicleId = 1, getVehicleEvents = mockk())
        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `lista vazia retorna mensagem informativa`() = runTest {
        val getVehicleEvents: GetVehicleEventsUseCase = mockk()
        coEvery { getVehicleEvents(1) } returns AppResult.Success(emptyList())

        val screen = AutoEventsScreen(carContext, vehicleId = 1, getVehicleEvents)
        screen.loadData()

        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `erro de rede retorna mensagem com acao de tentar novamente`() = runTest {
        val getVehicleEvents: GetVehicleEventsUseCase = mockk()
        coEvery { getVehicleEvents(1) } returns AppResult.Failure(AppError.Network)

        val screen = AutoEventsScreen(carContext, vehicleId = 1, getVehicleEvents)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isNotEmpty())
    }

    @Test
    fun `erro 401 retorna mensagem sem acao de retry`() = runTest {
        val getVehicleEvents: GetVehicleEventsUseCase = mockk()
        coEvery { getVehicleEvents(1) } returns AppResult.Failure(AppError.Unauthorized)

        val screen = AutoEventsScreen(carContext, vehicleId = 1, getVehicleEvents)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isEmpty())
    }

    @Test
    fun `sucesso ordena por data desc e limita a 10, sem onClick nas linhas`() = runTest {
        val getVehicleEvents: GetVehicleEventsUseCase = mockk()
        val events = (1..12).map { event(it, "Evento $it", date = "2026-01-%02d".format(it)) }
        coEvery { getVehicleEvents(1) } returns AppResult.Success(events)

        val screen = AutoEventsScreen(carContext, vehicleId = 1, getVehicleEvents)
        screen.loadData()

        val template = screen.onGetTemplate() as ListTemplate
        val items = template.singleList!!.items
        assertTrue("deve limitar a 10 eventos", items.size == 10)
        val first = items.first() as Row
        assertEquals("Evento 12", first.title.toString())
        assertNull("Eventos sao somente leitura, sem onClick", first.onClickDelegate)
    }

    @Test
    fun `titulo vazio usa o label da categoria`() = runTest {
        val getVehicleEvents: GetVehicleEventsUseCase = mockk()
        val events = listOf(event(1, title = "", date = "2026-06-15", category = EventCategory.OIL_CHANGE))
        coEvery { getVehicleEvents(1) } returns AppResult.Success(events)

        val screen = AutoEventsScreen(carContext, vehicleId = 1, getVehicleEvents)
        screen.loadData()

        val template = screen.onGetTemplate() as ListTemplate
        val row = template.singleList!!.items.first() as Row
        assertEquals("Troca de Óleo", row.title.toString())
    }

    @Test
    fun `linha mostra data e valor formatados`() = runTest {
        val getVehicleEvents: GetVehicleEventsUseCase = mockk()
        val events = listOf(event(1, title = "Troca de óleo", date = "2026-06-15", amount = 289.90))
        coEvery { getVehicleEvents(1) } returns AppResult.Success(events)

        val screen = AutoEventsScreen(carContext, vehicleId = 1, getVehicleEvents)
        screen.loadData()

        val template = screen.onGetTemplate() as ListTemplate
        val row = template.singleList!!.items.first() as Row
        val text = row.texts.first().toString()
        assertTrue("deve conter a data", text.contains("15/06"))
        assertTrue("deve conter o valor", text.contains("289"))
    }
}
