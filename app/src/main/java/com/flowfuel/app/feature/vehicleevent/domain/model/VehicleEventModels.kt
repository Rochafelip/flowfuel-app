package com.flowfuel.app.feature.vehicleevent.domain.model

enum class EventCategory(val apiValue: String, val label: String) {
    FUEL("COMBUSTIVEL", "Combustível"),
    MAINTENANCE("MANUTENCAO", "Manutenção"),
    OIL_CHANGE("TROCA_OLEO", "Troca de Óleo"),
    WASH("LAVAGEM", "Lavagem"),
    TIRES("PNEUS", "Pneus"),
    INSURANCE("SEGURO", "Seguro"),
    TAX("IMPOSTO", "Imposto"),
    DOCUMENTS("DOCUMENTOS", "Documentos"),
    OTHER("OUTROS", "Outros"),
}

data class VehicleEvent(
    val id: Int,
    val vehicleId: Int,
    val category: EventCategory,
    val title: String,
    val description: String?,
    val amount: Double?,
    val eventDate: String,
    val odometerKm: Int?,
    val notes: String?,
    val receiptUrl: String?,
    val createdAt: String?,
    val updatedAt: String?,
)

data class CreateVehicleEventRequest(
    val vehicleId: Int,
    val category: EventCategory,
    val title: String,
    val description: String? = null,
    val amount: Double? = null,
    val eventDate: String,
    val odometerKm: Int? = null,
    val notes: String? = null,
    val receiptUrl: String? = null,
)

data class UpdateVehicleEventRequest(
    val category: EventCategory? = null,
    val title: String? = null,
    val description: String? = null,
    val amount: Double? = null,
    val eventDate: String? = null,
    val odometerKm: Int? = null,
    val notes: String? = null,
)
