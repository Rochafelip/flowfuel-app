package com.flowfuel.app.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class FieldError(
    val field: String,
    val message: String,
)
