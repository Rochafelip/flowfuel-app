package com.flowfuel.app.feature.auth.domain

import android.net.Uri
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auth.domain.model.UserProfile

interface ProfileRepository {
    suspend fun getProfile(userId: String): AppResult<UserProfile>
    suspend fun updateProfile(userId: String, name: String?, phone: String?): AppResult<UserProfile>
    suspend fun uploadProfilePicture(userId: String, uri: Uri): AppResult<String>
    suspend fun deleteProfilePicture(userId: String): AppResult<Unit>
}
