package com.flowfuel.app.feature.auth.presentation.register

import app.cash.turbine.test
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.FieldError
import com.flowfuel.app.feature.auth.domain.usecase.RegisterUseCase
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
import org.junit.Assert.assertNotNull
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
class RegisterViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val registerUseCase: RegisterUseCase = mockk()
    private lateinit var viewModel: RegisterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = RegisterViewModel(registerUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fillValidForm() {
        viewModel.onNameChange("Felipe")
        viewModel.onEmailChange("user@example.com")
        viewModel.onPasswordChange("Senha1234")
        viewModel.onConfirmChange("Senha1234")
        viewModel.onPhoneChange("+5511999999999")
    }

    // ── Success flow ──────────────────────────────────────────────────────────

    @Test
    fun `submit success emits NavigateToCheckEmail with registered email`() = runTest {
        coEvery { registerUseCase(any(), any(), any(), any()) } returns AppResult.Success("user@example.com")
        fillValidForm()

        viewModel.effects.test {
            viewModel.submit()
            assertEquals(RegisterEffect.NavigateToCheckEmail("user@example.com"), awaitItem())
        }
    }

    @Test
    fun `submit success emits NavigateToCheckEmail not NavigateHome`() = runTest {
        coEvery { registerUseCase(any(), any(), any(), any()) } returns AppResult.Success("user@example.com")
        fillValidForm()

        viewModel.effects.test {
            viewModel.submit()
            val effect = awaitItem()
            assertTrue(effect is RegisterEffect.NavigateToCheckEmail)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit success resets isSubmitting to false`() = runTest {
        coEvery { registerUseCase(any(), any(), any(), any()) } returns AppResult.Success("user@example.com")
        fillValidForm()
        viewModel.submit()

        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun `submit success clears error state`() = runTest {
        coEvery { registerUseCase(any(), any(), any(), any()) } returns AppResult.Success("user@example.com")
        fillValidForm()
        viewModel.submit()

        assertNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.serverErrors)
    }

    // ── Validation — form not submitted to server ─────────────────────────────

    @Test
    fun `submit with blank name sets nameError and skips use case`() = runTest {
        fillValidForm()
        viewModel.onNameChange("")
        viewModel.submit()

        assertTrue(viewModel.state.value.nameError)
        coVerify(exactly = 0) { registerUseCase(any(), any(), any(), any()) }
    }

    @Test
    fun `submit with invalid email sets emailError and skips use case`() = runTest {
        fillValidForm()
        viewModel.onEmailChange("not-an-email")
        viewModel.submit()

        assertTrue(viewModel.state.value.emailError)
        coVerify(exactly = 0) { registerUseCase(any(), any(), any(), any()) }
    }

    @Test
    fun `submit with short password sets passwordError and skips use case`() = runTest {
        fillValidForm()
        viewModel.onPasswordChange("123")
        viewModel.onConfirmChange("123")
        viewModel.submit()

        assertTrue(viewModel.state.value.passwordError)
        coVerify(exactly = 0) { registerUseCase(any(), any(), any(), any()) }
    }

    @Test
    fun `submit with mismatched passwords sets confirmError and skips use case`() = runTest {
        fillValidForm()
        viewModel.onConfirmChange("DifferentPass123")
        viewModel.submit()

        assertTrue(viewModel.state.value.confirmError)
        coVerify(exactly = 0) { registerUseCase(any(), any(), any(), any()) }
    }

    @Test
    fun `submit with invalid phone sets phoneError and skips use case`() = runTest {
        fillValidForm()
        viewModel.onPhoneChange("123")
        viewModel.submit()

        assertTrue(viewModel.state.value.phoneError)
        coVerify(exactly = 0) { registerUseCase(any(), any(), any(), any()) }
    }

    // ── Server errors ─────────────────────────────────────────────────────────

    @Test
    fun `submit email already exists populates serverErrors`() = runTest {
        val fieldErrors = listOf(FieldError("email", "Email já cadastrado"))
        coEvery { registerUseCase(any(), any(), any(), any()) } returns
            AppResult.Failure(AppError.Api("VALIDATION_FAILED", null, fieldErrors))
        fillValidForm()
        viewModel.submit()

        assertNotNull(viewModel.state.value.serverErrors)
        assertEquals("email", viewModel.state.value.serverErrors!!.first().field)
        assertEquals("Email já cadastrado", viewModel.state.value.serverErrors!!.first().message)
    }

    @Test
    fun `submit server validation error does not emit navigation effect`() = runTest {
        val fieldErrors = listOf(FieldError("email", "Email já cadastrado"))
        coEvery { registerUseCase(any(), any(), any(), any()) } returns
            AppResult.Failure(AppError.Api("VALIDATION_FAILED", null, fieldErrors))
        fillValidForm()

        viewModel.effects.test {
            viewModel.submit()
            expectNoEvents()
        }
    }

    @Test
    fun `submit network error sets error in state`() = runTest {
        coEvery { registerUseCase(any(), any(), any(), any()) } returns
            AppResult.Failure(AppError.Network)
        fillValidForm()
        viewModel.submit()

        assertEquals(AppError.Network, viewModel.state.value.error)
    }

    @Test
    fun `submit api error sets error in state`() = runTest {
        coEvery { registerUseCase(any(), any(), any(), any()) } returns
            AppResult.Failure(AppError.Api("INTERNAL_ERROR", "Erro interno"))
        fillValidForm()
        viewModel.submit()

        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun `submit failure resets isSubmitting to false`() = runTest {
        coEvery { registerUseCase(any(), any(), any(), any()) } returns
            AppResult.Failure(AppError.Network)
        fillValidForm()
        viewModel.submit()

        assertFalse(viewModel.state.value.isSubmitting)
    }

    // ── Input field error clearing ─────────────────────────────────────────────

    @Test
    fun `changing email clears email error`() = runTest {
        fillValidForm()
        viewModel.onEmailChange("invalid")
        viewModel.submit()
        assertTrue(viewModel.state.value.emailError)

        viewModel.onEmailChange("user@example.com")
        assertFalse(viewModel.state.value.emailError)
    }

    @Test
    fun `changing name clears name error`() = runTest {
        fillValidForm()
        viewModel.onNameChange("")
        viewModel.submit()
        assertTrue(viewModel.state.value.nameError)

        viewModel.onNameChange("Felipe")
        assertFalse(viewModel.state.value.nameError)
    }
}
