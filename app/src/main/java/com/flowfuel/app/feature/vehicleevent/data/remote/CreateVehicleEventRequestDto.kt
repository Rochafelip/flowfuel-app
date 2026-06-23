package com.flowfuel.app.feature.vehicleevent.data.remote

import kotlinx.serialization.Serializable

// Espelha VehicleEventRequestDTO do backend (.claude/docs_api/openapi.yaml):
// campos reais são vehicleId/type/amount/eventDate/odometer/description — não
// existe title/notes/receiptUrl no backend. Esses 3 conceitos da UI são
// combinados em [description] por VehicleEventRepositoryImpl antes de chegar
// aqui (ver combineDescription/splitDescription).
@Serializable
data class CreateVehicleEventRequestDto(
    val vehicleId: Int,
    val type: String,
    val description: String? = null,
    val amount: Double? = null,
    val eventDate: String,
    val odometer: Int? = null,
)
