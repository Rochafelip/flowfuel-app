package com.flowfuel.app.feature.history.domain

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.history.domain.model.RefuelPage
import com.flowfuel.app.feature.history.domain.model.UpdateRefuelRequest
import java.time.LocalDate

interface HistoryRepository {
    suspend fun getRefuelHistory(
        vehicleId: Int,
        page: Int,
        size: Int,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): AppResult<RefuelPage>

    suspend fun getRefuelDetails(id: Int): AppResult<RefuelItem>

    suspend fun updateRefuel(id: Int, request: UpdateRefuelRequest): AppResult<RefuelItem>

    suspend fun deleteRefuel(id: Int): AppResult<Unit>
}
