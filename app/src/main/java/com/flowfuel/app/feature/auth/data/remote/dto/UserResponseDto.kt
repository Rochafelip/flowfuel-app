package com.flowfuel.app.feature.auth.data.remote.dto

import com.flowfuel.app.feature.auth.domain.model.UserProfile
import kotlinx.serialization.Serializable

@Serializable
data class UserResponseDto(
    val id: Long,
    val email: String,
    val name: String? = null,
    val phone: String? = null,
    val profilePicture: String? = null,
    val profilePictureUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    fun toDomain() = UserProfile(
        id                = id,
        email             = email,
        name              = name,
        phone             = phone,
        profilePictureUrl = profilePictureUrl,
        createdAt         = createdAt,
    )
}
