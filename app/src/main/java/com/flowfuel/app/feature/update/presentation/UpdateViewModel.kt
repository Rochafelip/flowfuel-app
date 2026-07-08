package com.flowfuel.app.feature.update.presentation

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.feature.update.domain.UpdateRepository
import com.flowfuel.app.feature.update.domain.model.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    @ApplicationContext private val context: Context,
    @Named("isDebugBuild") private val isDebugBuild: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var downloadReceiver: BroadcastReceiver? = null

    init {
        if (!isDebugBuild) {
            viewModelScope.launch {
                updateRepository.checkForUpdate()?.let { info ->
                    _state.value = UpdateUiState.Available(info)
                }
            }
        }
    }

    fun onUpdateClick() {
        val info = currentInfo() ?: return
        if (updateRepository.canRequestPackageInstalls()) {
            startDownload(info)
        } else {
            _state.value = UpdateUiState.RequestingInstallPermission(info)
        }
    }

    fun onInstallPermissionResumed() {
        val info = (_state.value as? UpdateUiState.RequestingInstallPermission)?.info ?: return
        if (updateRepository.canRequestPackageInstalls()) {
            startDownload(info)
        } else {
            _state.value = UpdateUiState.Available(info)
        }
    }

    fun onDismiss() {
        val info = currentInfo() ?: return
        viewModelScope.launch {
            updateRepository.dismiss(info.tag)
            _state.value = UpdateUiState.Idle
        }
    }

    private fun currentInfo(): UpdateInfo? = when (val s = _state.value) {
        is UpdateUiState.Available -> s.info
        is UpdateUiState.RequestingInstallPermission -> s.info
        else -> null
    }

    private fun startDownload(info: UpdateInfo) {
        _state.value = UpdateUiState.Downloading(info)
        val downloadId = updateRepository.enqueueDownload(info)
        registerDownloadReceiver(downloadId)
    }

    private fun registerDownloadReceiver(downloadId: Long) {
        unregisterDownloadReceiver()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (completedId != downloadId) return
                onDownloadFinished(downloadId)
            }
        }
        downloadReceiver = receiver
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun onDownloadFinished(downloadId: Long) {
        val info = (_state.value as? UpdateUiState.Downloading)?.info
        unregisterDownloadReceiver()
        _state.value = if (updateRepository.isDownloadComplete(downloadId)) {
            updateRepository.installUri(downloadId)?.let { UpdateUiState.ReadyToInstall(it) }
                ?: info?.let { UpdateUiState.Available(it) } ?: UpdateUiState.Idle
        } else {
            info?.let { UpdateUiState.Available(it) } ?: UpdateUiState.Idle
        }
    }

    private fun unregisterDownloadReceiver() {
        downloadReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        downloadReceiver = null
    }

    override fun onCleared() {
        unregisterDownloadReceiver()
    }
}
