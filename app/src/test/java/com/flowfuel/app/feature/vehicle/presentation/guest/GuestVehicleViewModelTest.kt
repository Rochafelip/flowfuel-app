package com.flowfuel.app.feature.vehicle.presentation.guest

import app.cash.turbine.test
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShareStatus
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GuestVehicleViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: VehicleRepository = mockk()
    private val sessionStore: SessionStore = mockk(relaxed = true)

    private fun testShare(vehicleId: Int = 99, brand: String = "Fiat", model: String = "Uno", expiresAt: String? = "2026-07-20T00:00:00") =
        VehicleShare(
            id = 1,
            vehicleId = vehicleId,
            vehicleBrand = brand,
            vehicleModel = model,
            ownerId = 1,
            ownerName = "Dono",
            guestId = 2,
            guestName = "Convidado",
            status = VehicleShareStatus.ACTIVE,
            createdAt = "2026-06-01T00:00:00",
            respondedAt = "2026-06-01T00:00:00",
            expiresAt = expiresAt,
        )

    private fun createViewModel(vehicleId: Int = 99, brand: String = "Fiat", model: String = "Uno", expiresAt: String? = "2026-07-20T00:00:00") =
        GuestVehicleViewModel(repository, sessionStore).apply {
            initialize(testShare(vehicleId = vehicleId, brand = brand, model = model, expiresAt = expiresAt))
        }

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun initialize_populaEstadoComDadosDoVehicleShare() = runTest {
        val viewModel = GuestVehicleViewModel(repository, sessionStore)
        viewModel.initialize(testShare(vehicleId = 42, brand = "Toyota", model = "Corolla", expiresAt = "2026-08-01T00:00:00"))

        val state = viewModel.state.value
        assertEquals(42, state.vehicleId)
        assertEquals("Toyota", state.vehicleBrand)
        assertEquals("Corolla", state.vehicleModel)
        assertEquals("2026-08-01T00:00:00", state.expiresAt)
    }

    @Test
    fun initialize_mesmoVehicleId_naoReinicializaEPreservaInputEmProgresso() = runTest {
        val viewModel = GuestVehicleViewModel(repository, sessionStore)
        viewModel.initialize(testShare(vehicleId = 42))
        viewModel.onOdometerChange("1234")

        viewModel.initialize(testShare(vehicleId = 42, brand = "Outra marca"))

        val state = viewModel.state.value
        assertEquals("1234", state.odometerInput)
        assertEquals("Fiat", state.vehicleBrand)
    }

    @Test
    fun confirmOdometer_sucesso_mostraSnackbarDeSucesso() = runTest {
        coEvery { repository.updateOdometer(99, 1050) } returns AppResult.Success(Unit)
        val viewModel = createViewModel()
        viewModel.onOdometerChange("1050")

        viewModel.effects.test {
            viewModel.confirmOdometer()
            assertEquals(GuestVehicleEffect.OdometerUpdated, awaitItem())
        }

        coVerify { repository.updateOdometer(99, 1050) }
    }

    @Test
    fun confirmOdometer_erro403_limpaSessaoConvidadoEEnviaEfeitoDeVoltarPraPicker() = runTest {
        coEvery { repository.updateOdometer(99, 1050) } returns AppResult.Failure(AppError.Api("FORBIDDEN_OPERATION", "sem acesso"))
        val viewModel = createViewModel()
        viewModel.onOdometerChange("1050")

        viewModel.effects.test {
            viewModel.confirmOdometer()
            val effect = awaitItem()
            assertEquals(GuestVehicleEffect.NavigateToPicker::class, effect::class)
            assertNotNull((effect as GuestVehicleEffect.NavigateToPicker).message)
        }

        coVerify { sessionStore.clearActiveVehicleId() }
    }

    @Test
    fun confirmOdometer_inputEmBranco_naoChamaRepositorioEMostraErro() = runTest {
        val viewModel = createViewModel()
        viewModel.onOdometerChange("")

        viewModel.confirmOdometer()

        assertNotNull(viewModel.state.value.odometerError)
        coVerify(exactly = 0) { repository.updateOdometer(any(), any()) }
    }

    @Test
    fun confirmOdometer_zero_naoChamaRepositorioEMostraErro() = runTest {
        val viewModel = createViewModel()
        viewModel.onOdometerChange("0")

        viewModel.confirmOdometer()

        assertNotNull(viewModel.state.value.odometerError)
        coVerify(exactly = 0) { repository.updateOdometer(any(), any()) }
    }

    @Test
    fun confirmOdometer_negativo_naoChamaRepositorioEMostraErro() = runTest {
        val viewModel = createViewModel()
        viewModel.onOdometerChange("-10")

        viewModel.confirmOdometer()

        assertNotNull(viewModel.state.value.odometerError)
        coVerify(exactly = 0) { repository.updateOdometer(any(), any()) }
    }
}
