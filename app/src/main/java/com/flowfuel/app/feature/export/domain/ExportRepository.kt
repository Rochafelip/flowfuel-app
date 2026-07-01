package com.flowfuel.app.feature.export.domain

import android.net.Uri
import com.flowfuel.app.core.domain.AppResult

enum class ExportFormat(val value: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    PDF("pdf", "application/pdf"),
}

interface ExportRepository {
    suspend fun exportRefuels(
        vehicleId: Int,
        format: ExportFormat,
        startDate: String? = null,
        endDate: String? = null,
    ): AppResult<Uri>

    suspend fun exportEvents(
        vehicleId: Int,
        format: ExportFormat,
        type: String? = null,
        startDate: String? = null,
        endDate: String? = null,
    ): AppResult<Uri>
}
