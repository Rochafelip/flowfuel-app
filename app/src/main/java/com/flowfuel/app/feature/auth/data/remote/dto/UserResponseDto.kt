package com.flowfuel.app.feature.auth.data.remote.dto

import com.flowfuel.app.BuildConfig
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
    /**
     * O backend retorna [profilePictureUrl] (URL presignada do S3) só quando
     * disponível; [profilePicture] é sempre uma URL interna relativa
     * (ex.: "/auth/1/profile-picture") que requer o cliente autenticado.
     */
    fun toDomain() = UserProfile(
        id                = id,
        email             = email,
        name              = name,
        phone             = phone,
        profilePictureUrl = profilePictureUrl
            ?: profilePicture?.let { BuildConfig.API_BASE_URL.trimEnd('/') + it },
        createdAt         = createdAt,
    )
}
