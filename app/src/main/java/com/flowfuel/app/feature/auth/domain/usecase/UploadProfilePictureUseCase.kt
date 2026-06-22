package com.flowfuel.app.feature.auth.domain.usecase

import android.net.Uri
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auth.domain.ProfileRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class UploadProfilePictureUseCase @Inject constructor(
    private val repo: ProfileRepository,
    private val sessionStore: SessionStore,
) {
    suspend operator fun invoke(uri: Uri): AppResult<String> {
        val userId = sessionStore.sessionFlow.first().userId
            ?: return AppResult.Failure(AppError.Unknown())
        return repo.uploadProfilePicture(userId, uri)
    }
}
