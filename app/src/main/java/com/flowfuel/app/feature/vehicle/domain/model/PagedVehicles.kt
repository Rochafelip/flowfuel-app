package com.flowfuel.app.feature.vehicle.domain.model

data class PagedVehicles(
    val items: List<Vehicle>,
    val currentPage: Int,
    val totalPages: Int,
    val totalElements: Int,
) {
    val hasMore: Boolean get() = currentPage < totalPages - 1
}
