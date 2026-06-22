package com.flowfuel.app.feature.history.presentation

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.pagination.PaginationState
import com.flowfuel.app.feature.history.domain.model.HistoryFilter
import com.flowfuel.app.feature.history.domain.model.RefuelItem

// ─── Estado da tela ───────────────────────────────────────────────────────────

sealed interface HistoryScreenState {
    data object Loading : HistoryScreenState
    data object Empty : HistoryScreenState
    data class Error(val error: AppError) : HistoryScreenState
    data class Success(val items: List<RefuelItem>) : HistoryScreenState
}

// ─── Estado global ────────────────────────────────────────────────────────────

data class HistoryUiState(
    val screenState: HistoryScreenState = HistoryScreenState.Loading,
    val isRefreshing: Boolean = false,
    val paginationState: PaginationState = PaginationState(),
    val filter: HistoryFilter = HistoryFilter(),
    val pendingDeleteItem: RefuelItem? = null,
    val isDeletingId: Int? = null,
    val deleteError: AppError? = null,
    val activeVehicleLabel: String? = null,
)

// ─── Efeitos pontuais ─────────────────────────────────────────────────────────

sealed interface HistoryEffect {
    data object NavigateToLogin : HistoryEffect
}
