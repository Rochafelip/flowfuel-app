package com.flowfuel.app.feature.auth.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChangePasswordRequestDto(
    val currentPassword: String,
    val newPassword: String,
)
