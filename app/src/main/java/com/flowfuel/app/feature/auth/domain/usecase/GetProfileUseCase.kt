package com.flowfuel.app.feature.auth.domain.usecase

import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auth.domain.ProfileRepository
import com.flowfuel.app.feature.auth.domain.model.UserProfile
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetProfileUseCase @Inject constructor(
    private val repo: ProfileRepository,
    private val sessionStore: SessionStore,
) {
    suspend operator fun invoke(): AppResult<UserProfile> {
        val userId = sessionStore.sessionFlow.first().userId ?: ""
        return repo.getProfile(userId)
    }
}
