package com.flowfuel.app.feature.vehicleevent.data.remote

import kotlinx.serialization.Serializable

// Ver nota em CreateVehicleEventRequestDto.kt sobre os campos reais do backend.
@Serializable
data class VehicleEventResponseDto(
    val id: Int,
    val vehicleId: Int,
    val type: String,
    val description: String? = null,
    val amount: Double? = null,
    val eventDate: String,
    val odometer: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
