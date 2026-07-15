package com.flowfuel.app.feature.vehicleevent.presentation.create

import androidx.lifecycle.SavedStateHandle
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.usecase.CreateVehicleEventUseCase
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CreateVehicleEventViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val createVehicleEvent: CreateVehicleEventUseCase = mockk()
    private val sessionStore: SessionStore = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_guestModeTrue_availableCategoriesRestritoAsQuatroPermitidas() {
        val savedStateHandle = SavedStateHandle(mapOf("vehicleId" to 10, "guestMode" to "true"))
        val viewModel = CreateVehicleEventViewModel(savedStateHandle, createVehicleEvent, sessionStore)

        val expected = setOf(EventCategory.FUEL, EventCategory.WASH, EventCategory.TIRES, EventCategory.OTHER)
        assertEquals(expected, viewModel.state.value.availableCategories.toSet())
    }

    @Test
    fun init_guestModeAusente_availableCategoriesTemTodas() {
        val savedStateHandle = SavedStateHandle(mapOf("vehicleId" to 10))
        val viewModel = CreateVehicleEventViewModel(savedStateHandle, createVehicleEvent, sessionStore)

        assertEquals(EventCategory.entries.toSet(), viewModel.state.value.availableCategories.toSet())
    }
}
