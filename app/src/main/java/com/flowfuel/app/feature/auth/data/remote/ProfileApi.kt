package com.flowfuel.app.feature.auth.data.remote

import com.flowfuel.app.feature.auth.data.remote.dto.UpdateProfileRequestDto
import com.flowfuel.app.feature.auth.data.remote.dto.UploadResponseDto
import com.flowfuel.app.feature.auth.data.remote.dto.UserResponseDto
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

interface ProfileApi {
    @GET("auth/{userId}/profile")
    suspend fun getProfile(@Path("userId") userId: String): UserResponseDto

    @PUT("auth/{userId}/profile")
    suspend fun updateProfile(
        @Path("userId") userId: String,
        @Body body: UpdateProfileRequestDto,
    ): UserResponseDto

    @Multipart
    @POST("auth/{userId}/upload-profile-picture")
    suspend fun uploadProfilePicture(
        @Path("userId") userId: String,
        @Part file: MultipartBody.Part,
    ): UploadResponseDto

    @DELETE("auth/{userId}/profile-picture")
    suspend fun deleteProfilePicture(
        @Path("userId") userId: String,
    )
}
