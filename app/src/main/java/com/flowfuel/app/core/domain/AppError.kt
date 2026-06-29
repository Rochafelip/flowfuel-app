package com.flowfuel.app.core.domain

sealed class AppError(open val message: String? = null) {
    data class Api(
        val code: String,
        override val message: String? = null,
        val fieldErrors: List<FieldError>? = null,
    ) : AppError(message)
    data object Network : AppError("network")
    data object Unauthorized : AppError("unauthorized")
    data class RateLimited(val retryAfterSeconds: Int? = null) : AppError("rate_limited")
    data class Unknown(val throwable: Throwable? = null) : AppError(throwable?.message)
}

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}
