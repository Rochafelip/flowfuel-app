package com.flowfuel.app.feature.history.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.HistoryRepository
import com.flowfuel.app.feature.history.domain.model.RefuelPage
import java.time.LocalDate
import javax.inject.Inject

class GetRefuelHistoryUseCase @Inject constructor(
    private val repo: HistoryRepository,
) {
    suspend operator fun invoke(
        vehicleId: Int,
        page: Int,
        size: Int,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): AppResult<RefuelPage> = repo.getRefuelHistory(vehicleId, page, size, startDate, endDate)
}
