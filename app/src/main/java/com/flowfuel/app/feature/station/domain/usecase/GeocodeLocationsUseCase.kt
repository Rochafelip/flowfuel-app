package com.flowfuel.app.feature.station.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.StationRepository
import com.flowfuel.app.feature.station.domain.model.GeocodeResult
import javax.inject.Inject

class GeocodeLocationsUseCase @Inject constructor(
    private val repository: StationRepository,
) {
    suspend operator fun invoke(query: String): AppResult<List<GeocodeResult>> =
        repository.geocode(query)
}
