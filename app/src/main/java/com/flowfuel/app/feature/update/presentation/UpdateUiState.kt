package com.flowfuel.app.feature.update.presentation

import android.net.Uri
import com.flowfuel.app.feature.update.domain.model.UpdateInfo

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class RequestingInstallPermission(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo) : UpdateUiState
    data class ReadyToInstall(val installUri: Uri) : UpdateUiState
}
