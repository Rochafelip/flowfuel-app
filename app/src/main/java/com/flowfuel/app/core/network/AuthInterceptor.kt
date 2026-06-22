package com.flowfuel.app.core.network

import com.flowfuel.app.core.datastore.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val skipAuth = request.header("No-Auth") != null
        if (skipAuth) {
            return chain.proceed(request.newBuilder().removeHeader("No-Auth").build())
        }

        // Dispatchers.IO evita conflito com o thread pool do OkHttp
        val token = runBlocking(Dispatchers.IO) { sessionStore.accessToken() }

        if (token == null) {
            Timber.w("AuthInterceptor: token ausente — requisição enviada sem Authorization")
        }

        val newRequest = if (token != null) {
            request.newBuilder().addHeader("Authorization", "Bearer $token").build()
        } else request

        return chain.proceed(newRequest)
    }
}
