package com.flowfuel.app.feature.auth.presentation.checkemail

import app.cash.turbine.test
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auth.domain.usecase.ActivateAccountUseCase
import com.flowfuel.app.feature.auth.domain.usecase.ResendActivationUseCase
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CheckEmailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val resendActivation: ResendActivationUseCase = mockk(relaxed = true)
    private val activateAccount: ActivateAccountUseCase = mockk()
    private lateinit var viewModel: CheckEmailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CheckEmailViewModel(resendActivation, activateAccount)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `activateWithToken success emits ActivatedAndLoggedIn`() = runTest {
        coEvery { activateAccount(any()) } returns AppResult.Success(Unit)
        viewModel.onActivationTokenChange("plain-token")

        viewModel.effects.test {
            viewModel.activateWithToken()
            assertEquals(CheckEmailEffect.ActivatedAndLoggedIn, awaitItem())
        }
    }

    @Test
    fun `activateWithToken failure keeps showing error, no navigation effect`() = runTest {
        coEvery { activateAccount(any()) } returns AppResult.Failure(AppError.Unauthorized)
        viewModel.onActivationTokenChange("plain-token")

        viewModel.activateWithToken()

        assertEquals(AppError.Api("AUTH_ACTIVATION_INVALID"), viewModel.state.value.activationError)
    }
}
