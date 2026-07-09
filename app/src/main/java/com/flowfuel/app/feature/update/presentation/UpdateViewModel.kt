package com.flowfuel.app.feature.update.presentation

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
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
import kotlinx.coroutines.flow.update
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

    /**
     * [UpdateInfo] for the download currently in flight. Tracked separately from
     * [_state] because the user can hide the [UpdateUiState.Downloading] progress
     * UI (via [onHideDownloadProgress]) while the download keeps running in the
     * background; when the completion broadcast eventually arrives, we still need
     * the original [UpdateInfo] to build the failure-fallback [UpdateUiState.Available]
     * state, even though [_state] is no longer [UpdateUiState.Downloading] by then.
     */
    private var downloadingInfo: UpdateInfo? = null

    /**
     * Drives [pollRunnable] on the main thread's message queue rather than via a
     * coroutine `delay()` loop on [viewModelScope]: the latter shares its scheduler
     * with `Dispatchers.Main` in tests (via `Dispatchers.setMain`), and an
     * indefinitely-recurring `delay()`-based loop that's still pending when a test
     * ends gets force-drained by `runTest`'s automatic `advanceUntilIdle()` cleanup —
     * which never terminates for a self-rescheduling loop and crashes/OOMs even tests
     * that never touch progress. Posting through the main [Looper] instead means the
     * poll only ever advances when a test explicitly idles the looper (as this file
     * already does for the download-completion broadcast), matching real Android
     * behavior without fighting the coroutine test scheduler.
     */
    private val handler = Handler(Looper.getMainLooper())

    /** Polls [UpdateRepository.downloadProgress] while [UpdateUiState.Downloading] is visible. */
    private var pollRunnable: Runnable? = null

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

    /**
     * Hides the non-dismissible [UpdateUiState.Downloading] progress UI without
     * rejecting the update or losing track of the in-flight download: the
     * broadcast receiver stays registered, so if the download completes or fails
     * later, [onDownloadFinished] still transitions the state correctly. Progress
     * polling is stopped since there's no UI left to show it to.
     */
    fun onHideDownloadProgress() {
        val info = (_state.value as? UpdateUiState.Downloading)?.info ?: return
        stopPolling()
        _state.value = UpdateUiState.Available(info)
    }

    /**
     * User deferred installing a ready-to-install update. Intentionally does NOT
     * call [UpdateRepository.dismiss] — [UpdateUiState.ReadyToInstall] carries no
     * `tag` to persist as dismissed, and deferring installation isn't the same as
     * rejecting the update: the next app launch will offer it again.
     */
    fun onDismissReadyToInstall() {
        if (_state.value !is UpdateUiState.ReadyToInstall) return
        _state.value = UpdateUiState.Idle
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
        downloadingInfo = info
        startPolling(downloadId)
    }

    private fun startPolling(downloadId: Long) {
        lateinit var runnable: Runnable
        runnable = Runnable {
            val progress = updateRepository.downloadProgress(downloadId)
            _state.update { s ->
                (s as? UpdateUiState.Downloading)?.copy(progress = progress) ?: s
            }
            handler.postDelayed(runnable, PROGRESS_POLL_INTERVAL_MS)
        }
        pollRunnable = runnable
        handler.postDelayed(runnable, PROGRESS_POLL_INTERVAL_MS)
    }

    private fun stopPolling() {
        pollRunnable?.let(handler::removeCallbacks)
        pollRunnable = null
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
        // DownloadManager's completion broadcast is sent as an explicit intent by
        // com.android.providers.downloads (a different app/UID), not by the system
        // itself — RECEIVER_NOT_EXPORTED silently drops it (verified on a real
        // device/emulator; Robolectric's sendBroadcast() doesn't enforce this).
        // onReceive() filters by the exact downloadId, so exporting is safe here.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun onDownloadFinished(downloadId: Long) {
        val info = downloadingInfo
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
        downloadingInfo = null
        stopPolling()
    }

    override fun onCleared() {
        unregisterDownloadReceiver()
    }

    private companion object {
        const val PROGRESS_POLL_INTERVAL_MS = 300L
    }
}
