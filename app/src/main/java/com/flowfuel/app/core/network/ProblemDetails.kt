package com.flowfuel.app.core.network

import com.flowfuel.app.core.domain.FieldError
import kotlinx.serialization.Serializable

@Serializable
data class ProblemDetails(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val code: String? = null,
    val detail: String? = null,
    val instance: String? = null,
    val requestId: String? = null,
    val timestamp: String? = null,
    val errors: List<FieldError>? = null,
)
