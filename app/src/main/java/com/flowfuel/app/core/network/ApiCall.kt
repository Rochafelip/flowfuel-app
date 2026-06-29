package com.flowfuel.app.core.network

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException

private val problemJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Extrai o ProblemDetail (RFC 7807) do corpo de erro de uma [HttpException], se houver. */
fun problemDetailsOf(e: HttpException): ProblemDetails? {
    val body = e.response()?.errorBody()?.string()
    return body?.let {
        runCatching { problemJson.decodeFromString(ProblemDetails.serializer(), it) }.getOrNull()
    }
}

suspend fun <T> apiCall(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: HttpException) {
    val problem = problemDetailsOf(e)
    val code = problem?.code ?: "HTTP_${e.code()}"
    if (e.code() == 401) AppResult.Failure(AppError.Unauthorized)
    else if (e.code() == 429) {
        val retryAfter = e.response()?.headers()?.get("Retry-After")?.toIntOrNull()
        AppResult.Failure(AppError.RateLimited(retryAfter))
    }
    else AppResult.Failure(AppError.Api(code, problem?.detail ?: problem?.title ?: e.message(), problem?.errors))
} catch (e: IOException) {
    Timber.w(e, "network failure")
    AppResult.Failure(AppError.Network)
} catch (e: SerializationException) {
    Timber.e(e, "serialization failure — response body não corresponde ao DTO esperado")
    AppResult.Failure(AppError.Unknown(e))
} catch (e: Throwable) {
    Timber.e(e, "unexpected failure: ${e::class.simpleName}")
    AppResult.Failure(AppError.Unknown(e))
}
