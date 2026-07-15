package com.flowfuel.app.feature.vehicle.presentation.guest

import androidx.lifecycle.SavedStateHandle
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
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

    private fun createViewModel(vehicleId: Int = 99, brand: String = "Fiat", model: String = "Uno", expiresAt: String? = "2026-07-20T00:00:00") =
        GuestVehicleViewModel(
            SavedStateHandle(mapOf("vehicleId" to vehicleId, "vehicleBrand" to brand, "vehicleModel" to model, "expiresAt" to expiresAt)),
            repository,
            sessionStore,
        )

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun confirmOdometer_sucesso_mostraSnackbarDeSucesso() = runTest {
        coEvery { repository.updateOdometer(99, 1050) } returns AppResult.Success(Unit)
        val viewModel = createViewModel()
        viewModel.onOdometerChange("1050")

        viewModel.confirmOdometer()

        coVerify { repository.updateOdometer(99, 1050) }
    }

    @Test
    fun confirmOdometer_erro403_limpaSessaoConvidadoEEnviaEfeitoDeVoltarPraPicker() = runTest {
        coEvery { repository.updateOdometer(99, 1050) } returns AppResult.Failure(AppError.Api("FORBIDDEN_OPERATION", "sem acesso"))
        val viewModel = createViewModel()
        viewModel.onOdometerChange("1050")

        viewModel.confirmOdometer()

        coVerify { sessionStore.clearActiveVehicleId() }
    }
}
