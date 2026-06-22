package com.flowfuel.app.feature.history.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.HistoryRepository
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.history.domain.model.UpdateRefuelRequest
import javax.inject.Inject

class UpdateRefuelUseCase @Inject constructor(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(request: UpdateRefuelRequest): AppResult<RefuelItem> =
        repository.updateRefuel(request.id, request)
}
