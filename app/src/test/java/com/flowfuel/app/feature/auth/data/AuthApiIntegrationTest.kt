package com.flowfuel.app.feature.auth.data

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.network.apiCall
import com.flowfuel.app.feature.auth.data.remote.AuthApi
import com.flowfuel.app.feature.auth.data.remote.LoginRequestDto
import com.flowfuel.app.feature.auth.data.remote.RefreshRequestDto
import com.flowfuel.app.feature.auth.data.remote.RegisterRequestDto
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AuthApiIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AuthApi

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .client(OkHttpClient())
            .build()
        api = retrofit.create(AuthApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(status: Int, body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(status)
                .addHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    // ── Register — ADR-014: retorna UserResponseDto (sem tokens) ─────────────

    @Test
    fun `register deserializes UserResponseDto with id email and name`() = runTest {
        enqueue(201, """
            {
              "id": 1,
              "email": "user@example.com",
              "name": "Felipe"
            }
        """.trimIndent())

        val dto = api.register(RegisterRequestDto("Felipe", "user@example.com", "Senha@123", "+5511999999999"))

        assertEquals(1L, dto.id)
        assertEquals("user@example.com", dto.email)
        assertEquals("Felipe", dto.name)
    }

    @Test
    fun `register id as json number is deserialized as Long`() = runTest {
        enqueue(201, """
            {
              "id": 42,
              "email": "user@example.com"
            }
        """.trimIndent())

        val dto = api.register(RegisterRequestDto("Felipe", "user@example.com", "Senha@123", "+5511999999999"))

        assertEquals(42L, dto.id)
    }

    @Test
    fun `register optional fields default to null when absent`() = runTest {
        enqueue(201, """{ "id": 1, "email": "user@example.com" }""")

        val dto = api.register(RegisterRequestDto("", "user@example.com", "pass", ""))

        assertNull(dto.name)
        assertNull(dto.phone)
        assertNull(dto.profilePictureUrl)
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    fun `login deserializes new format with nested user object`() = runTest {
        enqueue(200, """
            {
              "user": { "id": 7, "email": "user@example.com", "name": "Felipe" },
              "accessToken": "access_login",
              "refreshToken": "refresh_login",
              "expiresIn": 900
            }
        """.trimIndent())

        val dto = api.login(LoginRequestDto("user@example.com", "Senha@123"))

        assertEquals(7L, dto.user?.id)
        assertEquals("access_login", dto.accessToken)
    }

    // ── Refresh sem user object ───────────────────────────────────────────────

    @Test
    fun `refresh without user object deserializes correctly with user null`() = runTest {
        enqueue(200, """
            {
              "accessToken": "new_access",
              "refreshToken": "new_refresh",
              "expiresIn": 900
            }
        """.trimIndent())

        val dto = api.refresh(RefreshRequestDto("old_refresh"))

        assertNull(dto.user)
        assertEquals("new_access", dto.accessToken)
        assertEquals("new_refresh", dto.refreshToken)
    }

    @Test
    fun `refresh with user object deserializes name and email`() = runTest {
        enqueue(200, """
            {
              "user": { "id": 5, "email": "user@example.com", "name": "Updated Name" },
              "accessToken": "new_access",
              "refreshToken": "new_refresh"
            }
        """.trimIndent())

        val dto = api.refresh(RefreshRequestDto("old_refresh"))

        assertEquals("Updated Name", dto.user?.name)
        assertEquals(5L, dto.user?.id)
    }

    // ── Campos extras ignorados ───────────────────────────────────────────────

    @Test
    fun `unknown fields in response are ignored`() = runTest {
        enqueue(200, """
            {
              "user": { "id": 1, "email": "user@example.com", "roles": ["admin"], "createdAt": "2024-01-01" },
              "accessToken": "tok",
              "refreshToken": "ref",
              "expiresIn": 900,
              "tokenType": "Bearer"
            }
        """.trimIndent())

        val dto = api.login(LoginRequestDto("user@example.com", "pass"))

        assertEquals(1L, dto.user?.id)
        assertEquals("tok", dto.accessToken)
    }

    // ── Erros HTTP via apiCall ────────────────────────────────────────────────

    @Test
    fun `409 conflict returns AppError Api`() = runTest {
        enqueue(409, """
            {
              "code": "EMAIL_ALREADY_EXISTS",
              "title": "Email já cadastrado"
            }
        """.trimIndent())

        val result = apiCall { api.register(RegisterRequestDto("Felipe", "dup@example.com", "Senha@123", "+5511999999999")) }

        assertTrue(result is AppResult.Failure)
        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.Api)
        assertEquals("EMAIL_ALREADY_EXISTS", (error as AppError.Api).code)
    }

    @Test
    fun `422 validation error returns AppError Api with fieldErrors`() = runTest {
        enqueue(422, """
            {
              "code": "VALIDATION_FAILED",
              "title": "Validação falhou",
              "errors": [
                { "field": "email", "message": "Email inválido" },
                { "field": "phone", "message": "Telefone inválido" }
              ]
            }
        """.trimIndent())

        val result = apiCall { api.register(RegisterRequestDto("F", "bad", "123", "0")) }

        assertTrue(result is AppResult.Failure)
        val error = (result as AppResult.Failure).error as AppError.Api
        assertEquals("VALIDATION_FAILED", error.code)
        assertNotNull(error.fieldErrors)
        assertEquals(2, error.fieldErrors!!.size)
        assertEquals("email", error.fieldErrors!![0].field)
    }

    @Test
    fun `401 returns AppError Unauthorized`() = runTest {
        enqueue(401, """{ "code": "UNAUTHORIZED" }""")

        val result = apiCall { api.login(LoginRequestDto("user@example.com", "wrong")) }

        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.Unauthorized, (result as AppResult.Failure).error)
    }
}
