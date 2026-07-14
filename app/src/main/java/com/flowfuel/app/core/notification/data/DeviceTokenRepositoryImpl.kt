package com.flowfuel.app.core.notification.data

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.network.apiCall
import com.flowfuel.app.core.notification.data.remote.DeviceTokenApi
import com.flowfuel.app.core.notification.data.remote.RegisterDeviceRequestDto
import com.flowfuel.app.core.notification.domain.DeviceTokenRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceTokenRepositoryImpl @Inject constructor(
    private val api: DeviceTokenApi,
) : DeviceTokenRepository {

    override suspend fun registerToken(token: String): AppResult<Unit> =
        apiCall { api.registerDevice(RegisterDeviceRequestDto(token)) }

    override suspend fun unregisterToken(token: String): AppResult<Unit> =
        apiCall { api.unregisterDevice(token) }
}
