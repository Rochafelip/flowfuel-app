package com.flowfuel.app.feature.station.domain.model

enum class StationType { Fuel, Electric }

data class Station(
    val placeId: String,
    val name: String,
    val type: StationType,
    val distanceMeters: Int,
    val rating: Double?,
    val latitude: Double,
    val longitude: Double,
)
