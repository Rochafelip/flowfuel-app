package com.flowfuel.app.feature.auth.domain.model

data class UserProfile(
    val id: Long,
    val email: String,
    val name: String?,
    val phone: String?,
    val profilePictureUrl: String?,
    val createdAt: String?,
)
