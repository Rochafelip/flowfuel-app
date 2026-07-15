package com.flowfuel.app.feature.vehicle.presentation.share

import androidx.lifecycle.SavedStateHandle
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShareStatus
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetVehicleShareForVehicleUseCase
import com.flowfuel.app.core.vehicleshare.domain.usecase.InviteVehicleShareUseCase
import com.flowfuel.app.core.vehicleshare.domain.usecase.RevokeVehicleShareUseCase
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
class ShareVehicleViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val invite: InviteVehicleShareUseCase = mockk()
    private val revoke: RevokeVehicleShareUseCase = mockk()
    private val getForVehicle: GetVehicleShareForVehicleUseCase = mockk()

    private fun share(status: VehicleShareStatus) = VehicleShare(
        id = 100, vehicleId = 10, vehicleBrand = "Toyota", vehicleModel = "Corolla",
        ownerId = 1, ownerName = "Dono", guestId = 2, guestName = "Convidado",
        status = status, createdAt = null, respondedAt = null, expiresAt = "2026-07-20T00:00:00",
    )

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun init_semShare_estadoNoShare() = runTest {
        coEvery { getForVehicle(10) } returns AppResult.Success(null)
        val viewModel = ShareVehicleViewModel(SavedStateHandle(mapOf("vehicleId" to 10)), invite, revoke, getForVehicle)

        assertTrue(viewModel.state.value is ShareVehicleUiState.NoShare)
    }

    @Test
    fun init_sharePending_estadoPending() = runTest {
        coEvery { getForVehicle(10) } returns AppResult.Success(share(VehicleShareStatus.PENDING))
        val viewModel = ShareVehicleViewModel(SavedStateHandle(mapOf("vehicleId" to 10)), invite, revoke, getForVehicle)

        assertTrue(viewModel.state.value is ShareVehicleUiState.Pending)
    }

    @Test
    fun sendInvite_sucesso_chamaUseCaseComEmailEDuracao() = runTest {
        coEvery { getForVehicle(10) } returns AppResult.Success(null)
        coEvery { invite(10, "guest@test.com", 3) } returns AppResult.Success(share(VehicleShareStatus.PENDING))
        val viewModel = ShareVehicleViewModel(SavedStateHandle(mapOf("vehicleId" to 10)), invite, revoke, getForVehicle)

        viewModel.onEmailChange("guest@test.com")
        viewModel.onDurationChange(3)
        viewModel.sendInvite()

        coVerify { invite(10, "guest@test.com", 3) }
    }

    @Test
    fun revokeShare_sucesso_voltaParaNoShare() = runTest {
        coEvery { getForVehicle(10) } returns AppResult.Success(share(VehicleShareStatus.PENDING))
        coEvery { revoke(100) } returns AppResult.Success(Unit)
        val viewModel = ShareVehicleViewModel(SavedStateHandle(mapOf("vehicleId" to 10)), invite, revoke, getForVehicle)

        viewModel.revokeShare()

        coVerify { revoke(100) }
        assertTrue(viewModel.state.value is ShareVehicleUiState.NoShare)
    }

    @Test
    fun revokeShare_falha_mantemPendingComErroEResetaIsRevoking() = runTest {
        coEvery { getForVehicle(10) } returns AppResult.Success(share(VehicleShareStatus.PENDING))
        coEvery { revoke(100) } returns AppResult.Failure(AppError.Network)
        val viewModel = ShareVehicleViewModel(SavedStateHandle(mapOf("vehicleId" to 10)), invite, revoke, getForVehicle)

        viewModel.revokeShare()

        val state = viewModel.state.value
        assertTrue(state is ShareVehicleUiState.Pending)
        state as ShareVehicleUiState.Pending
        assertFalse(state.isRevoking)
        assertEquals("Sem conexão. Verifique sua internet.", state.error)
    }
}
