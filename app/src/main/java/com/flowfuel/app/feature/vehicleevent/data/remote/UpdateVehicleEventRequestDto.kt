package com.flowfuel.app.feature.vehicleevent.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class UpdateVehicleEventRequestDto(
    val category: String? = null,
    val title: String? = null,
    val description: String? = null,
    val amount: Double? = null,
    val eventDate: String? = null,
    val odometerKm: Int? = null,
    val notes: String? = null,
)
