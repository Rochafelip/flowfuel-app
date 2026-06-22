package com.flowfuel.app.feature.auth.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequestDto(
    val name: String?,
    val phone: String?,
)
