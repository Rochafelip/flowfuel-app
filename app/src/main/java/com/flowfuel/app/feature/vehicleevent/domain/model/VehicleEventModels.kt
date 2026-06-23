package com.flowfuel.app.feature.vehicleevent.domain.model

// apiValue precisa bater exatamente com o enum VehicleEventType do backend
// (.claude/docs_api/openapi.yaml): [FUEL, MAINTENANCE, OIL_CHANGE, CAR_WASH,
// TIRES, INSURANCE, TAX, DOCUMENTS, OTHER]. Valores em português aqui faziam
// toda criação/edição de evento falhar silenciosamente.
enum class EventCategory(val apiValue: String, val label: String) {
    FUEL("FUEL", "Combustível"),
    MAINTENANCE("MAINTENANCE", "Manutenção"),
    OIL_CHANGE("OIL_CHANGE", "Troca de Óleo"),
    WASH("CAR_WASH", "Lavagem"),
    TIRES("TIRES", "Pneus"),
    INSURANCE("INSURANCE", "Seguro"),
    TAX("TAX", "Imposto"),
    DOCUMENTS("DOCUMENTS", "Documentos"),
    OTHER("OTHER", "Outros"),
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
    val vehicleId: Int,
    val category: EventCategory? = null,
    val title: String? = null,
    val description: String? = null,
    val amount: Double? = null,
    val eventDate: String? = null,
    val odometerKm: Int? = null,
    val notes: String? = null,
)
