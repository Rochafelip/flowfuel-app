package com.flowfuel.app.feature.auth.domain

import com.flowfuel.app.core.domain.AppResult

interface AuthRepository {
    suspend fun login(email: String, password: String): AppResult<Unit>
    suspend fun register(name: String, email: String, password: String, phone: String): AppResult<String>
    suspend fun resendActivation(email: String): AppResult<Unit>
    suspend fun activate(token: String): AppResult<Unit>
    suspend fun forgotPassword(email: String): AppResult<Unit>
    suspend fun resetPassword(token: String, newPassword: String): AppResult<Unit>
    suspend fun logout(): AppResult<Unit>
    suspend fun changePassword(userId: String, current: String, new: String): AppResult<Unit>
    suspend fun deleteAccount(): AppResult<Unit>
}
