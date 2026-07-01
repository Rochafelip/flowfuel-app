package com.flowfuel.app.feature.station.domain.model

data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
)

sealed interface LocationResult {
    data class Available(val location: GeoLocation) : LocationResult
    data object PermissionDenied : LocationResult
    data object Unavailable : LocationResult
}
