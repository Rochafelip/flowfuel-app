package com.flowfuel.app.core.pagination

import com.flowfuel.app.core.domain.AppError

data class PaginationState(
    val currentPage: Int = 0,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val pageError: AppError? = null,
)
