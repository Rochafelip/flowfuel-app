package com.flowfuel.app.feature.vehicleevent.domain.model

data class PagedVehicleEvents(
    val items: List<VehicleEvent>,
    val currentPage: Int,
    val totalPages: Int,
    val totalElements: Int,
) {
    val hasMore: Boolean get() = currentPage < totalPages - 1
}
