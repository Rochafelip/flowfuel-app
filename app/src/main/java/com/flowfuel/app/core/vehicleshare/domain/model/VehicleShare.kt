package com.flowfuel.app.core.vehicleshare.domain.model

enum class VehicleShareStatus {
    PENDING, ACTIVE, REJECTED, REVOKED, EXPIRED;

    companion object {
        fun fromApi(raw: String): VehicleShareStatus =
            entries.firstOrNull { it.name == raw } ?: EXPIRED
    }
}

data class VehicleShare(
    val id: Int,
    val vehicleId: Int,
    val vehicleBrand: String,
    val vehicleModel: String,
    val ownerId: Int,
    val ownerName: String,
    val guestId: Int,
    val guestName: String,
    val status: VehicleShareStatus,
    val createdAt: String?,
    val respondedAt: String?,
    val expiresAt: String?,
)
