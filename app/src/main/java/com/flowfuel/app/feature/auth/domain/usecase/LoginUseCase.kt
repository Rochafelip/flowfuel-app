package com.flowfuel.app.feature.auth.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auth.domain.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): AppResult<Unit> =
        repo.login(email.trim().lowercase(), password)
}

class RegisterUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(
        name: String,
        email: String,
        password: String,
        phone: String,
    ): AppResult<String> = repo.register(name.trim(), email.trim().lowercase(), password, phone.trim())
}

class ResendActivationUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(email: String) {
        runCatching { repo.resendActivation(email.trim().lowercase()) }
    }
}

class ActivateAccountUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(token: String): AppResult<Unit> = repo.activate(token.trim())
}

class ForgotPasswordUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(email: String): AppResult<Unit> =
        repo.forgotPassword(email.trim().lowercase())
}

class ResetPasswordUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(token: String, newPassword: String): AppResult<Unit> =
        repo.resetPassword(token.trim(), newPassword)
}

class LogoutUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repo.logout()
}
