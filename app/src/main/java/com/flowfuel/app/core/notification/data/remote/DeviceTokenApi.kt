package com.flowfuel.app.core.notification.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class RegisterDeviceRequestDto(
    val token: String,
    val platform: String = "ANDROID",
)

interface DeviceTokenApi {
    @POST("devices")
    suspend fun registerDevice(@Body body: RegisterDeviceRequestDto)

    @DELETE("devices/{token}")
    suspend fun unregisterDevice(@Path("token") token: String)
}
