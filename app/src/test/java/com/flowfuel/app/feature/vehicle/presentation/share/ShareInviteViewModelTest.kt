package com.flowfuel.app.feature.vehicle.presentation.share

import androidx.lifecycle.SavedStateHandle
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShareStatus
import com.flowfuel.app.core.vehicleshare.domain.usecase.AcceptVehicleShareUseCase
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetPendingVehicleSharesUseCase
import com.flowfuel.app.core.vehicleshare.domain.usecase.RejectVehicleShareUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShareInviteViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val accept: AcceptVehicleShareUseCase = mockk()
    private val reject: RejectVehicleShareUseCase = mockk()
    private val getPending: GetPendingVehicleSharesUseCase = mockk()

    private fun share() = VehicleShare(
        id = 100, vehicleId = 10, vehicleBrand = "Toyota", vehicleModel = "Corolla",
        ownerId = 1, ownerName = "Dono", guestId = 2, guestName = "Eu",
        status = VehicleShareStatus.PENDING, createdAt = null, respondedAt = null, expiresAt = null,
    )

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun init_buscaConvitesPendentesEEncontraOShareId() = runTest {
        coEvery { getPending() } returns AppResult.Success(listOf(share()))
        val viewModel = ShareInviteViewModel(SavedStateHandle(mapOf("shareId" to 100)), accept, reject, getPending)

        val state = viewModel.state.first { it is ShareInviteUiState.Content }
        assertTrue(state is ShareInviteUiState.Content)
    }

    @Test
    fun accept_sucesso_chamaUseCaseComShareId() = runTest {
        coEvery { getPending() } returns AppResult.Success(listOf(share()))
        coEvery { accept(100) } returns AppResult.Success(share())
        val viewModel = ShareInviteViewModel(SavedStateHandle(mapOf("shareId" to 100)), accept, reject, getPending)

        viewModel.accept()

        coVerify { accept(100) }
    }

    @Test
    fun reject_sucesso_chamaUseCaseComShareId() = runTest {
        coEvery { getPending() } returns AppResult.Success(listOf(share()))
        coEvery { reject(100) } returns AppResult.Success(share())
        val viewModel = ShareInviteViewModel(SavedStateHandle(mapOf("shareId" to 100)), accept, reject, getPending)

        viewModel.reject()

        coVerify { reject(100) }
    }
}
