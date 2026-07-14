package com.flowfuel.app.feature.auth.data

import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.notification.domain.DeviceTokenRepository
import com.flowfuel.app.feature.auth.data.remote.ActivateAccountRequestDto
import com.flowfuel.app.feature.auth.data.remote.AuthApi
import com.flowfuel.app.feature.auth.data.remote.AuthResponseDto
import com.flowfuel.app.feature.auth.data.remote.UserDto
import com.flowfuel.app.feature.auth.data.remote.dto.UserResponseDto
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class AuthRepositoryImplTest {

    private val api: AuthApi = mockk()
    private val sessionStore: SessionStore = mockk(relaxed = true)
    private val deviceTokenRepository: DeviceTokenRepository = mockk(relaxed = true)
    private val firebaseMessaging: FirebaseMessaging = mockk()
    private val fcmTokenTask: Task<String> = mockk()
    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setUp() {
        // FirebaseMessaging.getInstance().token.await() is a static/extension call that
        // AuthRepositoryImpl invokes internally (not injected) — see Task 8 brief. It's not
        // mockable via a constructor-injected mock, so we stub the statics directly; this is
        // scaffolding not specified verbatim in the brief, added because the plain call throws
        // in this non-Robolectric unit test and gets swallowed by runCatching, silently
        // preventing deviceTokenRepository.registerToken from ever being invoked.
        mockkStatic(FirebaseMessaging::class)
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        every { FirebaseMessaging.getInstance() } returns firebaseMessaging
        every { firebaseMessaging.token } returns fcmTokenTask
        coEvery { fcmTokenTask.await() } returns "fcm-token-abc"
        repository = AuthRepositoryImpl(api, sessionStore, deviceTokenRepository)
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseMessaging::class)
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    private fun authResponse(userId: Long = 1L) = AuthResponseDto(
        user = UserDto(id = userId, email = "user@example.com", name = "Felipe"),
        accessToken = "access_token",
        refreshToken = "refresh_token",
        expiresIn = 900L,
    )

    private fun userResponse(email: String = "user@example.com") = UserResponseDto(
        id = 1L,
        email = email,
        name = "Felipe",
    )

    // ── register (ADR-014: não salva sessão, retorna email) ───────────────────

    @Test
    fun `register success returns email from response`() = runTest {
        coEvery { api.register(any()) } returns userResponse("user@example.com")

        val result = repository.register("Felipe", "user@example.com", "Senha@123", "+5511999999999")

        assertEquals(AppResult.Success("user@example.com"), result)
    }

    @Test
    fun `register success does not save session`() = runTest {
        coEvery { api.register(any()) } returns userResponse()

        repository.register("Felipe", "user@example.com", "Senha@123", "+5511999999999")

        coVerify(exactly = 0) { sessionStore.save(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `register success does not make secondary login call`() = runTest {
        coEvery { api.register(any()) } returns userResponse()

        repository.register("Felipe", "user@example.com", "Senha@123", "+5511999999999")

        coVerify(exactly = 0) { api.login(any()) }
    }

    @Test
    fun `register io exception returns network failure`() = runTest {
        coEvery { api.register(any()) } throws IOException("timeout")

        val result = repository.register("Felipe", "user@example.com", "Senha@123", "+5511999999999")

        assertEquals(AppResult.Failure(AppError.Network), result)
    }

    @Test
    fun `register failure does not save session`() = runTest {
        coEvery { api.register(any()) } throws IOException("no connection")

        repository.register("Felipe", "user@example.com", "Senha@123", "+5511999999999")

        coVerify(exactly = 0) { sessionStore.save(any(), any(), any(), any(), any()) }
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    fun `login success saves userId from nested user object`() = runTest {
        coEvery { api.login(any()) } returns authResponse(99L)

        val result = repository.login("user@example.com", "Senha@123")

        assertEquals(AppResult.Success(Unit), result)
        coVerify { sessionStore.save("access_token", "refresh_token", "99", "Felipe", "user@example.com") }
    }

    @Test
    fun `login success saves name and email from user object`() = runTest {
        coEvery { api.login(any()) } returns authResponse()

        repository.login("user@example.com", "Senha@123")

        coVerify { sessionStore.save(any(), any(), any(), "Felipe", "user@example.com") }
    }

    @Test
    fun `login success saves tokens exactly once`() = runTest {
        coEvery { api.login(any()) } returns authResponse()

        repository.login("user@example.com", "Senha@123")

        coVerify(exactly = 1) { sessionStore.save(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `login io exception returns network failure`() = runTest {
        coEvery { api.login(any()) } throws IOException("timeout")

        val result = repository.login("user@example.com", "Senha@123")

        assertEquals(AppResult.Failure(AppError.Network), result)
    }

    @Test
    fun `login failure does not save tokens`() = runTest {
        coEvery { api.login(any()) } throws IOException("no connection")

        repository.login("user@example.com", "Senha@123")

        coVerify(exactly = 0) { sessionStore.save(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `login null user and blank jwt userId returns failure`() = runTest {
        val dtoWithoutUser = AuthResponseDto(
            user = null,
            accessToken = "invalid.jwt.token",
            refreshToken = "refresh",
        )
        coEvery { api.login(any()) } returns dtoWithoutUser

        val result = repository.login("user@example.com", "Senha@123")

        assertTrue(result is AppResult.Failure)
        coVerify(exactly = 0) { sessionStore.save(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `login success registers current device token`() = runTest {
        coEvery { api.login(any()) } returns authResponse()
        coEvery { deviceTokenRepository.registerToken(any()) } returns AppResult.Success(Unit)

        repository.login("user@example.com", "Senha@123")

        coVerify { deviceTokenRepository.registerToken(any()) }
    }

    // ── activate (autologin via deep link) ─────────────────────────────────────

    @Test
    fun `activate success saves session from response`() = runTest {
        coEvery { api.activate(any()) } returns authResponse(42L)

        val result = repository.activate("plain-token")

        assertEquals(AppResult.Success(Unit), result)
        coVerify { sessionStore.save("access_token", "refresh_token", "42", "Felipe", "user@example.com") }
    }

    @Test
    fun `activate success saves tokens exactly once`() = runTest {
        coEvery { api.activate(any()) } returns authResponse()

        repository.activate("plain-token")

        coVerify(exactly = 1) { sessionStore.save(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `activate sends token to api unmodified`() = runTest {
        coEvery { api.activate(any()) } returns authResponse()

        repository.activate("plain-token")

        coVerify { api.activate(ActivateAccountRequestDto("plain-token")) }
    }

    @Test
    fun `activate io exception returns network failure`() = runTest {
        coEvery { api.activate(any()) } throws IOException("timeout")

        val result = repository.activate("plain-token")

        assertEquals(AppResult.Failure(AppError.Network), result)
    }

    @Test
    fun `activate failure does not save session`() = runTest {
        coEvery { api.activate(any()) } throws IOException("no connection")

        repository.activate("plain-token")

        coVerify(exactly = 0) { sessionStore.save(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `activate null user and blank jwt userId returns failure`() = runTest {
        val dtoWithoutUser = AuthResponseDto(
            user = null,
            accessToken = "invalid.jwt.token",
            refreshToken = "refresh",
        )
        coEvery { api.activate(any()) } returns dtoWithoutUser

        val result = repository.activate("plain-token")

        assertTrue(result is AppResult.Failure)
        coVerify(exactly = 0) { sessionStore.save(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `activate success registers current device token`() = runTest {
        coEvery { api.activate(any()) } returns authResponse()
        coEvery { deviceTokenRepository.registerToken(any()) } returns AppResult.Success(Unit)

        repository.activate("plain-token")

        coVerify { deviceTokenRepository.registerToken(any()) }
    }
}
