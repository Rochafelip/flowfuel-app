package com.flowfuel.app.feature.station.domain

import com.flowfuel.app.feature.station.domain.model.LocationResult

interface LocationProvider {
    suspend fun getCurrentLocation(): LocationResult
}
