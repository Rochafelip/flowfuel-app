package com.flowfuel.app.core.notification.data

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.notification.data.remote.DeviceTokenApi
import com.flowfuel.app.core.notification.data.remote.RegisterDeviceRequestDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class DeviceTokenRepositoryImplTest {

    private val api: DeviceTokenApi = mockk()
    private lateinit var repository: DeviceTokenRepositoryImpl

    @Before
    fun setUp() {
        repository = DeviceTokenRepositoryImpl(api)
    }

    @Test
    fun `registerToken success sends token with ANDROID platform`() = runTest {
        coEvery { api.registerDevice(any()) } returns Unit

        val result = repository.registerToken("fcm-token-123")

        assertEquals(AppResult.Success(Unit), result)
        coVerify { api.registerDevice(RegisterDeviceRequestDto("fcm-token-123", "ANDROID")) }
    }

    @Test
    fun `registerToken io exception returns network failure`() = runTest {
        coEvery { api.registerDevice(any()) } throws IOException("timeout")

        val result = repository.registerToken("fcm-token-123")

        assertEquals(AppResult.Failure(AppError.Network), result)
    }

    @Test
    fun `unregisterToken success calls delete with the given token`() = runTest {
        coEvery { api.unregisterDevice(any()) } returns Unit

        val result = repository.unregisterToken("fcm-token-123")

        assertEquals(AppResult.Success(Unit), result)
        coVerify { api.unregisterDevice("fcm-token-123") }
    }

    @Test
    fun `unregisterToken io exception returns network failure`() = runTest {
        coEvery { api.unregisterDevice(any()) } throws IOException("timeout")

        val result = repository.unregisterToken("fcm-token-123")

        assertEquals(AppResult.Failure(AppError.Network), result)
    }
}
