package com.flowfuel.app.core.network

import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.feature.auth.data.remote.AuthApi
import com.flowfuel.app.feature.auth.data.remote.RefreshRequestDto
import com.flowfuel.app.feature.auth.data.userIdFromJwt
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * OkHttp [Authenticator] que intercepta respostas 401 e tenta renovar o
 * access token via refresh token antes de retentar a requisição original.
 *
 * Proteções implementadas:
 * - **Anti-loop**: header `X-Refresh-Attempted` impede segunda tentativa.
 * - **Concorrência**: `synchronized(lock)` garante que apenas uma coroutine
 *   faz o refresh; as demais reutilizam o novo token já salvo.
 * - **Falha no refresh**: sessão limpa localmente → app trata o 401 resultante
 *   e navega para login.
 */
@Singleton
class TokenRefreshAuthenticator @Inject constructor(
    private val sessionStore: SessionStore,
    @Named("refresh") retrofit: Retrofit,
) : Authenticator {

    private val refreshApi: AuthApi = retrofit.create(AuthApi::class.java)
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // ── Anti-loop: já tentamos refresh nesta requisição ───────────────────
        if (response.request.header("X-Refresh-Attempted") != null) {
            Timber.w("TokenRefresh: refresh já tentado — limpando sessão")
            runBlocking { sessionStore.clear() }
            return null
        }

        synchronized(lock) {
            // ── Outro thread já fez o refresh ─────────────────────────────────
            val storedToken = runBlocking { sessionStore.accessToken() }
            val requestToken = response.request.header("Authorization")
                ?.removePrefix("Bearer ")?.trim()

            if (storedToken != null && storedToken != requestToken) {
                Timber.d("TokenRefresh: token já renovado por outra thread — reusando")
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $storedToken")
                    .build()
            }

            // ── Tenta renovar ─────────────────────────────────────────────────
            val refreshToken = runBlocking { sessionStore.refreshToken() }
            if (refreshToken.isNullOrBlank()) {
                Timber.w("TokenRefresh: refresh token ausente — limpando sessão")
                runBlocking { sessionStore.clear() }
                return null
            }

            return try {
                val tokens = runBlocking {
                    refreshApi.refresh(RefreshRequestDto(refreshToken))
                }
                val userId = tokens.user?.id?.toString() ?: userIdFromJwt(tokens.accessToken)
                if (userId.isBlank()) {
                    Timber.w("TokenRefresh: userId não resolvido após refresh — limpando sessão")
                    runBlocking { sessionStore.clear() }
                    return null
                }
                runBlocking {
                    sessionStore.save(
                        accessToken  = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                        userId       = userId,
                        userName     = tokens.user?.name,
                        userEmail    = tokens.user?.email,
                    )
                }
                Timber.d("TokenRefresh: tokens renovados com sucesso")

                response.request.newBuilder()
                    .header("Authorization", "Bearer ${tokens.accessToken}")
                    .header("X-Refresh-Attempted", "true")
                    .build()
            } catch (e: Exception) {
                Timber.w(e, "TokenRefresh: falha ao renovar token — limpando sessão")
                runBlocking { sessionStore.clear() }
                null
            }
        }
    }
}
