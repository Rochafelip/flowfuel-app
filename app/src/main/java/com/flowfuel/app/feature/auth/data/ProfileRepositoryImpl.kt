package com.flowfuel.app.feature.auth.data

import android.net.Uri
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.map
import com.flowfuel.app.core.media.ImagePickerHelper
import com.flowfuel.app.core.network.apiCall
import com.flowfuel.app.feature.auth.data.remote.ProfileApi
import com.flowfuel.app.feature.auth.data.remote.dto.UpdateProfileRequestDto
import com.flowfuel.app.feature.auth.domain.ProfileRepository
import com.flowfuel.app.feature.auth.domain.model.UserProfile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val api: ProfileApi,
    private val imagePickerHelper: ImagePickerHelper,
) : ProfileRepository {

    override suspend fun getProfile(userId: String): AppResult<UserProfile> {
        Timber.d("ProfileRepo › GET auth/$userId/profile")
        return apiCall { api.getProfile(userId) }.map { it.toDomain() }
    }

    override suspend fun updateProfile(userId: String, name: String?, phone: String?): AppResult<UserProfile> {
        Timber.d("ProfileRepo › PUT auth/$userId/profile")
        return apiCall { api.updateProfile(userId, UpdateProfileRequestDto(name, phone)) }.map { it.toDomain() }
    }

    override suspend fun uploadProfilePicture(userId: String, uri: Uri): AppResult<String> {
        Timber.d("ProfileRepo › POST auth/$userId/upload-profile-picture")
        return try {
            val compressed = imagePickerHelper.compressToJpeg(uri)
            val requestBody = compressed.toRequestBody("image/jpeg".toMediaType())
            val part = MultipartBody.Part.createFormData("file", "profile.jpg", requestBody)
            apiCall { api.uploadProfilePicture(userId, part) }.map { it.signedUrl ?: it.internalUrl }
        } catch (e: Throwable) {
            Timber.e(e, "ProfileRepo › erro ao comprimir imagem")
            AppResult.Failure(AppError.Unknown(e))
        }
    }

    override suspend fun deleteProfilePicture(userId: String): AppResult<Unit> {
        Timber.d("ProfileRepo › DELETE auth/$userId/profile-picture")
        return try {
            api.deleteProfilePicture(userId)?.close()
            AppResult.Success(Unit)
        } catch (e: HttpException) {
            Timber.w("deleteProfilePicture: HTTP ${e.code()}")
            if (e.code() == 401) AppResult.Failure(AppError.Unauthorized)
            else AppResult.Failure(AppError.Api("HTTP_${e.code()}", e.message()))
        } catch (e: IOException) {
            Timber.w(e, "deleteProfilePicture: network error")
            AppResult.Failure(AppError.Network)
        } catch (e: Throwable) {
            Timber.e(e, "deleteProfilePicture: unexpected error")
            AppResult.Failure(AppError.Unknown(e))
        }
    }
}
