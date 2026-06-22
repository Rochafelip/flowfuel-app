package com.flowfuel.app.feature.vehicleevent.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class CreateVehicleEventRequestDto(
    val vehicleId: Int,
    val category: String,
    val title: String,
    val description: String? = null,
    val amount: Double? = null,
    val eventDate: String,
    val odometerKm: Int? = null,
    val notes: String? = null,
    val receiptUrl: String? = null,
)
