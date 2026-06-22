package com.flowfuel.app.feature.history.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.HistoryRepository
import javax.inject.Inject

class DeleteRefuelUseCase @Inject constructor(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(id: Int): AppResult<Unit> =
        repository.deleteRefuel(id)
}
