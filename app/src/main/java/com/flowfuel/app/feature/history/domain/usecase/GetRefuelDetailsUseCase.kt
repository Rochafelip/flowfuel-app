package com.flowfuel.app.feature.history.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.HistoryRepository
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import javax.inject.Inject

class GetRefuelDetailsUseCase @Inject constructor(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(id: Int): AppResult<RefuelItem> =
        repository.getRefuelDetails(id)
}
