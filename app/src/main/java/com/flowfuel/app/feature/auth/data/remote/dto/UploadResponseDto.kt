package com.flowfuel.app.feature.auth.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class UploadResponseDto(
    val internalUrl: String,
    val signedUrl: String? = null,
)
