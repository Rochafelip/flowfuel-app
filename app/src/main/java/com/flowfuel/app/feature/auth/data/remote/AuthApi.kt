package com.flowfuel.app.feature.auth.data.remote

import com.flowfuel.app.feature.auth.data.remote.dto.ChangePasswordRequestDto
import com.flowfuel.app.feature.auth.data.remote.dto.UserResponseDto
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class RegisterRequestDto(
    val name: String,
    val email: String,
    val password: String,
    val phone: String,
)

@Serializable
data class ForgotPasswordRequestDto(val email: String)

@Serializable
data class ResendActivationRequestDto(val email: String)

@Serializable
data class ActivateAccountRequestDto(val token: String)

@Serializable
data class ResetPasswordRequestDto(val token: String, val newPassword: String)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class UserDto(
    val id: Long,
    val email: String,
    val name: String? = null,
)

@Serializable
data class AuthResponseDto(
    val user: UserDto? = null,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long? = null,
)

interface AuthApi {
    @Headers("No-Auth: true")
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): AuthResponseDto

    @Headers("No-Auth: true")
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): UserResponseDto

    @Headers("No-Auth: true")
    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequestDto)

    @Headers("No-Auth: true")
    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequestDto)

    @Headers("No-Auth: true")
    @POST("auth/activate")
    suspend fun activate(@Body body: ActivateAccountRequestDto): AuthResponseDto

    @Headers("No-Auth: true")
    @POST("auth/resend-activation")
    suspend fun resendActivation(@Body body: ResendActivationRequestDto)

    @Headers("No-Auth: true")
    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequestDto): AuthResponseDto

    @POST("auth/logout")
    suspend fun logout()

    @PUT("auth/{userId}/password")
    suspend fun changePassword(
        @Path("userId") userId: String,
        @Body body: ChangePasswordRequestDto,
    )

    @DELETE("auth/{userId}")
    suspend fun deleteAccount(
        @Path("userId") userId: String,
    )
}
