package com.flowfuel.app.feature.auth.data

import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.map
import com.flowfuel.app.core.network.apiCall
import com.flowfuel.app.feature.auth.data.remote.ActivateAccountRequestDto
import com.flowfuel.app.feature.auth.data.remote.AuthApi
import com.flowfuel.app.feature.auth.data.remote.ForgotPasswordRequestDto
import com.flowfuel.app.feature.auth.data.remote.LoginRequestDto
import com.flowfuel.app.feature.auth.data.remote.RegisterRequestDto
import com.flowfuel.app.feature.auth.data.remote.ResendActivationRequestDto
import com.flowfuel.app.feature.auth.data.remote.ResetPasswordRequestDto
import com.flowfuel.app.feature.auth.data.remote.dto.ChangePasswordRequestDto
import com.flowfuel.app.feature.auth.domain.AuthRepository
import kotlinx.coroutines.flow.firstOrNull
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val sessionStore: SessionStore,
) : AuthRepository {

    override suspend fun login(email: String, password: String): AppResult<Unit> {
        val result = apiCall { api.login(LoginRequestDto(email, password)) }
        return when (result) {
            is AppResult.Success -> {
                val dto = result.value
                val userId = dto.user?.id?.toString() ?: userIdFromJwt(dto.accessToken)
                if (userId.isBlank()) return AppResult.Failure(AppError.Unknown())
                sessionStore.save(
                    accessToken  = dto.accessToken,
                    refreshToken = dto.refreshToken,
                    userId       = userId,
                    userName     = dto.user?.name,
                    userEmail    = dto.user?.email,
                )
                AppResult.Success(Unit)
            }
            is AppResult.Failure -> result
        }
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
        phone: String,
    ): AppResult<String> =
        apiCall { api.register(RegisterRequestDto(name, email, password, phone)) }
            .map { dto -> dto.email }

    override suspend fun resendActivation(email: String): AppResult<Unit> =
        apiCall { api.resendActivation(ResendActivationRequestDto(email)) }

    override suspend fun activate(token: String): AppResult<Unit> =
        apiCall { api.activate(ActivateAccountRequestDto(token)) }

    override suspend fun forgotPassword(email: String): AppResult<Unit> =
        apiCall { api.forgotPassword(ForgotPasswordRequestDto(email)) }

    override suspend fun resetPassword(token: String, newPassword: String): AppResult<Unit> =
        apiCall { api.resetPassword(ResetPasswordRequestDto(token, newPassword)) }

    override suspend fun logout(): AppResult<Unit> {
        val result = apiCall { api.logout() }
        sessionStore.clear()
        return result
    }

    override suspend fun changePassword(userId: String, current: String, new: String): AppResult<Unit> =
        when (val r = apiCall { api.changePassword(userId, ChangePasswordRequestDto(current, new)) }) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> r
        }

    override suspend fun deleteAccount(): AppResult<Unit> {
        val userId = sessionStore.sessionFlow.firstOrNull()?.userId
            ?: return AppResult.Failure(AppError.Unknown())
        return try {
            api.deleteAccount(userId)?.close()
            AppResult.Success(Unit)
        } catch (e: HttpException) {
            AppResult.Failure(AppError.Api("HTTP_${e.code()}", e.message()))
        } catch (e: IOException) {
            AppResult.Failure(AppError.Network)
        }
    }
}
