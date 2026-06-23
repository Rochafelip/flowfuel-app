package com.flowfuel.app.feature.vehicleevent.data.remote

import kotlinx.serialization.Serializable

// Ver nota em CreateVehicleEventRequestDto.kt sobre os campos reais do backend.
@Serializable
data class UpdateVehicleEventRequestDto(
    val vehicleId: Int,
    val type: String? = null,
    val description: String? = null,
    val amount: Double? = null,
    val eventDate: String? = null,
    val odometer: Int? = null,
)
