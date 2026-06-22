package com.flowfuel.app.feature.auth.domain.usecase

import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auth.domain.AuthRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ChangePasswordUseCase @Inject constructor(
    private val repo: AuthRepository,
    private val sessionStore: SessionStore,
) {
    suspend operator fun invoke(current: String, new: String): AppResult<Unit> {
        val userId = sessionStore.sessionFlow.first().userId
            ?: return AppResult.Failure(AppError.Unknown())
        return repo.changePassword(userId, current, new)
    }
}
