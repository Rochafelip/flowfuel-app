package com.flowfuel.app.core.notification.domain

import com.flowfuel.app.core.domain.AppResult

interface DeviceTokenRepository {
    suspend fun registerToken(token: String): AppResult<Unit>
    suspend fun unregisterToken(token: String): AppResult<Unit>
}
