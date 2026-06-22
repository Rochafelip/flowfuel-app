package com.flowfuel.app.feature.auth.domain.usecase

import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auth.domain.AuthRepository
import javax.inject.Inject

class DeleteAccountUseCase @Inject constructor(
    private val repo: AuthRepository,
    private val sessionStore: SessionStore,
) {
    suspend operator fun invoke(): AppResult<Unit> {
        val result = repo.deleteAccount()
        sessionStore.clear()
        return result
    }
}
