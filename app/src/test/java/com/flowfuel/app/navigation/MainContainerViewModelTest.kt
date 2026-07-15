package com.flowfuel.app.navigation

import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShareStatus
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetActiveSharedVehiclesUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainContainerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val sessionStore: SessionStore = mockk()
    private val getActiveSharedVehicles: GetActiveSharedVehiclesUseCase = mockk()

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun state_veiculoAtivoProprio_isGuestModeFalse() = runTest {
        every { sessionStore.activeVehicleIsGuestFlow } returns flowOf(false)
        every { sessionStore.activeVehicleIdFlow } returns flowOf(1)
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(emptyList())

        val viewModel = MainContainerViewModel(sessionStore, getActiveSharedVehicles)

        assertFalse(viewModel.state.value.isGuestMode)
    }

    @Test
    fun state_veiculoAtivoEmprestado_isGuestModeTrueEResolveShare() = runTest {
        val share = VehicleShare(
            id = 100, vehicleId = 99, vehicleBrand = "Fiat", vehicleModel = "Uno",
            ownerId = 5, ownerName = "Dono", guestId = 1, guestName = "Eu",
            status = VehicleShareStatus.ACTIVE, createdAt = null, respondedAt = null,
            expiresAt = "2026-07-20T00:00:00",
        )
        every { sessionStore.activeVehicleIsGuestFlow } returns flowOf(true)
        every { sessionStore.activeVehicleIdFlow } returns flowOf(99)
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(listOf(share))

        val viewModel = MainContainerViewModel(sessionStore, getActiveSharedVehicles)

        assertTrue(viewModel.state.value.isGuestMode)
    }
}
