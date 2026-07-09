# Update Dialog Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop showing GitHub's raw markdown changelog in the update dialog and give it a FlowFuel-branded visual with real download progress.

**Architecture:** Additive-then-subtractive sequencing across the existing `feature/update` module (data/domain/presentation, unchanged package layout): first add the new `DownloadProgress` model and repository polling support, then wire polling into `UpdateViewModel`, then swap `UpdateAvailableDialog`'s visuals to stop reading the changelog field, and only then remove the now-unused `releaseNotes`/`body` fields. This order keeps the project compiling and all tests green after every task — nothing is ever removed before its last consumer stops using it.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, JUnit4 + Robolectric + MockK (existing test stack, no new dependencies).

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-08-update-dialog-redesign-design.md` — read it if anything here is ambiguous.
- No link to the GitHub changelog page in the dialog (explicit user decision — do not add one).
- Button labels/actions stay exactly as they are today ("Depois"/"Atualizar", "Ocultar", "Depois"/"Instalar") — only the visual shell and the Available-state message text change.
- No new automated tests for `UpdateAvailableDialog.kt` itself — the project has no Compose UI test pattern for dialogs (`FFDialog` doesn't have one either); don't introduce one here.
- **Windows note:** on this machine, invoke Gradle via PowerShell (`.\gradlew.bat ...`), not the Bash tool's `./gradlew` — the latter fails to resolve `JAVA_HOME` when the path contains spaces.

---

## Task 1: `DownloadProgress` domain model + repository `downloadProgress()`

Purely additive — nothing existing is touched except adding a new interface method (with a real implementation, so nothing is left unimplemented).

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/update/domain/model/DownloadProgress.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/update/domain/model/DownloadProgressTest.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/update/domain/UpdateRepository.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/update/data/UpdateRepositoryImpl.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/update/data/UpdateRepositoryImplTest.kt`

**Interfaces:**
- Produces: `DownloadProgress(bytesDownloaded: Long, totalBytes: Long)` with a `val fraction: Float?` property (null when `totalBytes <= 0`). Produces `UpdateRepository.downloadProgress(downloadId: Long): DownloadProgress?`, implemented in `UpdateRepositoryImpl`, returning `null` when the `downloadId` isn't found by `DownloadManager`.

- [ ] **Step 1: Write the failing test for `DownloadProgress.fraction`**

Create `app/src/test/java/com/flowfuel/app/feature/update/domain/model/DownloadProgressTest.kt`:

```kotlin
package com.flowfuel.app.feature.update.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadProgressTest {

    @Test
    fun `fraction is null when totalBytes is zero`() {
        val progress = DownloadProgress(bytesDownloaded = 0, totalBytes = 0)

        assertNull(progress.fraction)
    }

    @Test
    fun `fraction is the ratio of bytesDownloaded to totalBytes`() {
        val progress = DownloadProgress(bytesDownloaded = 50, totalBytes = 200)

        assertEquals(0.25f, progress.fraction)
    }
}
```

- [ ] **Step 2: Run it, confirm it fails to compile (the class doesn't exist yet)**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.update.domain.model.DownloadProgressTest"
```

Expected: build fails with `unresolved reference: DownloadProgress`.

- [ ] **Step 3: Create `DownloadProgress.kt`**

Create `app/src/main/java/com/flowfuel/app/feature/update/domain/model/DownloadProgress.kt`:

```kotlin
package com.flowfuel.app.feature.update.domain.model

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
) {
    /** null enquanto o DownloadManager ainda não sabe o tamanho total (bem no início do download). */
    val fraction: Float?
        get() = totalBytes.takeIf { it > 0 }?.let { bytesDownloaded.toFloat() / it }
}
```

- [ ] **Step 4: Run the test again, confirm it passes**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.update.domain.model.DownloadProgressTest"
```

Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 5: Add `downloadProgress` to the `UpdateRepository` interface**

Modify `app/src/main/java/com/flowfuel/app/feature/update/domain/UpdateRepository.kt` — full file becomes:

```kotlin
package com.flowfuel.app.feature.update.domain

import android.net.Uri
import com.flowfuel.app.feature.update.domain.model.DownloadProgress
import com.flowfuel.app.feature.update.domain.model.UpdateInfo

interface UpdateRepository {
    /** Busca a release mais recente no GitHub; retorna null se não há update, se o asset .apk não existe, ou em caso de erro. */
    suspend fun checkForUpdate(): UpdateInfo?

    /** Enfileira o download do APK via DownloadManager; retorna o downloadId. */
    fun enqueueDownload(info: UpdateInfo): Long

    /** true se o download terminou com sucesso (STATUS_SUCCESSFUL); false se falhou ou não foi encontrado. */
    fun isDownloadComplete(downloadId: Long): Boolean

    /** Progresso do download em andamento; null se o downloadId não for encontrado pelo DownloadManager. */
    fun downloadProgress(downloadId: Long): DownloadProgress?

    /** Uri (via FileProvider) do APK baixado, pronta para abrir o instalador; null se o arquivo não existe. */
    fun installUri(downloadId: Long): Uri?

    fun canRequestPackageInstalls(): Boolean

    /** Marca [tag] como dispensada — não volta a avisar sobre essa versão. */
    suspend fun dismiss(tag: String)
}
```

- [ ] **Step 6: Implement it in `UpdateRepositoryImpl`**

Modify `app/src/main/java/com/flowfuel/app/feature/update/data/UpdateRepositoryImpl.kt`:

Add this import alongside the existing `com.flowfuel.app.feature.update.domain.model.UpdateInfo` import:

```kotlin
import com.flowfuel.app.feature.update.domain.model.DownloadProgress
```

Add this method right after `isDownloadComplete` (before `installUri`):

```kotlin
    override fun downloadProgress(downloadId: Long): DownloadProgress? {
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        return cursor.use {
            if (!it.moveToFirst()) {
                null
            } else {
                val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                DownloadProgress(bytesDownloaded = downloaded, totalBytes = total)
            }
        }
    }
```

- [ ] **Step 7: Add the failing repository test**

Modify `app/src/test/java/com/flowfuel/app/feature/update/data/UpdateRepositoryImplTest.kt` — add this test at the end of the class, right before the closing `}`:

```kotlin
    @Test
    fun `downloadProgress returns null when the downloadId does not exist`() {
        assertNull(repository.downloadProgress(downloadId = 424242L))
    }
```

(`assertNull` is already imported at the top of this file.)

- [ ] **Step 8: Run the full `UpdateRepositoryImplTest` suite, confirm everything passes**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.update.data.UpdateRepositoryImplTest"
```

Expected: `BUILD SUCCESSFUL`, all tests (the 6 pre-existing ones plus the new one) pass. This confirms the new method didn't break `checkForUpdate`/`dismiss`.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/java/com/flowfuel/app/feature/update/domain/model/DownloadProgress.kt app/src/test/java/com/flowfuel/app/feature/update/domain/model/DownloadProgressTest.kt app/src/main/java/com/flowfuel/app/feature/update/domain/UpdateRepository.kt app/src/main/java/com/flowfuel/app/feature/update/data/UpdateRepositoryImpl.kt app/src/test/java/com/flowfuel/app/feature/update/data/UpdateRepositoryImplTest.kt
git commit -m "feat(update): add DownloadProgress model and repository polling support"
```

---

## Task 2: Progress polling in `UpdateViewModel`

Purely additive on top of Task 1 — `UpdateUiState.Downloading` gains an optional field with a default value, so every existing call site and test keeps compiling unchanged.

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/update/presentation/UpdateUiState.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/update/presentation/UpdateViewModel.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/update/presentation/UpdateViewModelTest.kt`

**Interfaces:**
- Consumes: `UpdateRepository.downloadProgress(downloadId: Long): DownloadProgress?` (Task 1), `DownloadProgress` (Task 1).
- Produces: `UpdateUiState.Downloading(info: UpdateInfo, progress: DownloadProgress? = null)`. This `progress` field is what Task 3 (the dialog) reads to render the progress bar.

- [ ] **Step 1: Add `progress` to `UpdateUiState.Downloading`**

Modify `app/src/main/java/com/flowfuel/app/feature/update/presentation/UpdateUiState.kt` — full file becomes:

```kotlin
package com.flowfuel.app.feature.update.presentation

import android.net.Uri
import com.flowfuel.app.feature.update.domain.model.DownloadProgress
import com.flowfuel.app.feature.update.domain.model.UpdateInfo

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class RequestingInstallPermission(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val progress: DownloadProgress? = null) : UpdateUiState
    data class ReadyToInstall(val installUri: Uri) : UpdateUiState
}
```

- [ ] **Step 2: Run the existing ViewModel tests, confirm they still pass unchanged**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.update.presentation.UpdateViewModelTest"
```

Expected: `BUILD SUCCESSFUL`, all 15 pre-existing tests pass — the `progress` field's default value (`null`) means every `UpdateUiState.Downloading(updateInfo())` call site in the test file still means the same thing as before.

- [ ] **Step 3: Write the two failing tests for polling**

Modify `app/src/test/java/com/flowfuel/app/feature/update/presentation/UpdateViewModelTest.kt`:

Add this import alongside the existing `com.flowfuel.app.feature.update.domain.model.UpdateInfo` import:

```kotlin
import com.flowfuel.app.feature.update.domain.model.DownloadProgress
```

Add these two tests at the end of the class, right before the closing `}`:

```kotlin
    @Test
    fun `polls download progress every 300ms while Downloading`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()
        every { updateRepository.canRequestPackageInstalls() } returns true
        every { updateRepository.enqueueDownload(updateInfo()) } returns 42L
        val firstTick = DownloadProgress(bytesDownloaded = 50, totalBytes = 200)
        val secondTick = DownloadProgress(bytesDownloaded = 150, totalBytes = 200)
        every { updateRepository.downloadProgress(42L) } returnsMany listOf(firstTick, secondTick)
        val vm = buildViewModel()

        vm.onUpdateClick()
        assertEquals(UpdateUiState.Downloading(updateInfo(), progress = null), vm.state.value)

        testDispatcher.scheduler.advanceTimeBy(300)
        testDispatcher.scheduler.runCurrent()
        assertEquals(UpdateUiState.Downloading(updateInfo(), progress = firstTick), vm.state.value)

        testDispatcher.scheduler.advanceTimeBy(300)
        testDispatcher.scheduler.runCurrent()
        assertEquals(UpdateUiState.Downloading(updateInfo(), progress = secondTick), vm.state.value)
    }

    @Test
    fun `onHideDownloadProgress stops polling without unregistering the download receiver`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()
        every { updateRepository.canRequestPackageInstalls() } returns true
        every { updateRepository.enqueueDownload(updateInfo()) } returns 42L
        every { updateRepository.downloadProgress(42L) } returns DownloadProgress(50, 200)
        val vm = buildViewModel()
        vm.onUpdateClick()

        vm.onHideDownloadProgress()
        testDispatcher.scheduler.advanceTimeBy(300)
        testDispatcher.scheduler.runCurrent()

        coVerify(inverse = true) { updateRepository.downloadProgress(42L) }
    }
```

- [ ] **Step 4: Run the two new tests, confirm they fail**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.update.presentation.UpdateViewModelTest"
```

Expected: the two new tests FAIL (state stays `Downloading(updateInfo(), progress = null)` forever — nothing polls yet). The 15 pre-existing tests still pass.

- [ ] **Step 5: Implement polling in `UpdateViewModel`**

Modify `app/src/main/java/com/flowfuel/app/feature/update/presentation/UpdateViewModel.kt` — full file becomes:

```kotlin
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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

    /** Polls [UpdateRepository.downloadProgress] while [UpdateUiState.Downloading] is visible. */
    private var progressJob: Job? = null

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
        progressJob?.cancel()
        progressJob = null
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
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(PROGRESS_POLL_INTERVAL_MS)
                val progress = updateRepository.downloadProgress(downloadId)
                _state.update { s ->
                    (s as? UpdateUiState.Downloading)?.copy(progress = progress) ?: s
                }
            }
        }
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
        progressJob?.cancel()
        progressJob = null
    }

    override fun onCleared() {
        unregisterDownloadReceiver()
    }

    private companion object {
        const val PROGRESS_POLL_INTERVAL_MS = 300L
    }
}
```

Key points for whoever implements this:
- The poll loop `delay`s **before** each `downloadProgress` call (not after) — this is what keeps the pre-existing tests green: they call `onUpdateClick()` and immediately assert on `vm.state.value` without advancing the test dispatcher's virtual clock, so the loop is still parked at its first `delay` and never calls `downloadProgress` during those tests.
- `progressJob` is cancelled in two places: `unregisterDownloadReceiver()` (covers normal completion/failure via `onDownloadFinished`, and `onCleared()`), and separately in `onHideDownloadProgress()` (which must NOT call `unregisterDownloadReceiver()`, since the existing behavior of the download receiver surviving a "hide" is covered by the pre-existing test `a completion broadcast that arrives after hiding the download still reaches ReadyToInstall` — don't break that).

- [ ] **Step 6: Run the two new tests again, confirm they pass**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.update.presentation.UpdateViewModelTest"
```

Expected: `BUILD SUCCESSFUL`, all 17 tests (15 pre-existing + 2 new) pass.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/flowfuel/app/feature/update/presentation/UpdateUiState.kt app/src/main/java/com/flowfuel/app/feature/update/presentation/UpdateViewModel.kt app/src/test/java/com/flowfuel/app/feature/update/presentation/UpdateViewModelTest.kt
git commit -m "feat(update): poll real download progress while Downloading"
```

---

## Task 3: Redesign `UpdateAvailableDialog`

Rewrites the dialog's visuals: FlowFuel branding, a fixed friendly message (stops reading `releaseNotes` — that field still exists on `UpdateInfo` after this task, just unused from here on), and a real progress bar fed by Task 2's `state.progress`.

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/update/presentation/UpdateAvailableDialog.kt`

**Interfaces:**
- Consumes: `UpdateUiState` (unchanged public shape from the caller's perspective — `MainContainerScreen.kt` needs zero changes), `DownloadProgress` (Task 1) via `UpdateUiState.Downloading.progress` (Task 2).
- Produces: same public function signature as before — `UpdateAvailableDialog(state, onUpdateClick, onDismiss, onHideDownload, onInstallClick, onDismissReadyToInstall)`. No caller changes needed.

- [ ] **Step 1: Rewrite the file**

Replace the full contents of `app/src/main/java/com/flowfuel/app/feature/update/presentation/UpdateAvailableDialog.kt`:

```kotlin
package com.flowfuel.app.feature.update.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.flowfuel.app.R
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.update.domain.model.DownloadProgress

@Composable
fun UpdateAvailableDialog(
    state: UpdateUiState,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit,
    onHideDownload: () -> Unit,
    onInstallClick: () -> Unit,
    onDismissReadyToInstall: () -> Unit,
) {
    when (state) {
        is UpdateUiState.Available -> UpdateDialogShell(
            title = "Nova versão disponível",
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onUpdateClick) { Text("Atualizar") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Depois") } },
        ) {
            Text("A versão ${state.info.versionLabel} do FlowFuel já está disponível, com melhorias e correções.")
        }

        is UpdateUiState.Downloading -> UpdateDialogShell(
            title = "Baixando atualização",
            onDismissRequest = onHideDownload,
            confirmButton = {},
            dismissButton = { TextButton(onClick = onHideDownload) { Text("Ocultar") } },
        ) {
            DownloadProgressContent(versionLabel = state.info.versionLabel, progress = state.progress)
        }

        is UpdateUiState.ReadyToInstall -> UpdateDialogShell(
            title = "Atualização pronta",
            onDismissRequest = onDismissReadyToInstall,
            confirmButton = { TextButton(onClick = onInstallClick) { Text("Instalar") } },
            dismissButton = { TextButton(onClick = onDismissReadyToInstall) { Text("Depois") } },
        ) {
            Text("A nova versão do FlowFuel já foi baixada e está pronta para ser instalada.")
        }

        else -> Unit
    }
}

/** Shell comum aos 3 estados: ícone da marca + título + corpo + botões, mesmo padrão visual em todos. */
@Composable
private fun UpdateDialogShell(
    title: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            // ic_splash is a 108dp splash-screen asset — must be sized down explicitly,
            // AlertDialog's icon slot does not scale its content automatically.
            Icon(
                painter = painterResource(R.drawable.ic_splash),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = { Text(title) },
        text = content,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
    )
}

@Composable
private fun DownloadProgressContent(versionLabel: String, progress: DownloadProgress?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val fraction = progress?.fraction
        if (progress != null && fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(FFTheme.spacing.sm))
            Text("${(fraction * 100).toInt()}% · ${formatBytes(progress.bytesDownloaded)} de ${formatBytes(progress.totalBytes)}")
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(FFTheme.spacing.sm))
            Text("Baixando FlowFuel $versionLabel...")
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return "%.1f MB".format(mb).replace('.', ',')
}
```

- [ ] **Step 2: Compile the project to confirm the rewrite is valid Kotlin/Compose**

```powershell
.\gradlew.bat compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. (There's no automated test for this file — see Global Constraints — so a successful compile plus the manual check in Step 3 is the verification for this task.)

- [ ] **Step 3: Manual smoke check**

Run the project's `run-android-emulator` skill (or reuse an already-booted emulator/connected device) to build+install a debug APK and confirm the app still launches normally — the dialog itself only renders in **release** builds when an update is detected (see `UpdateViewModel.init`, `if (!isDebugBuild)`), so this step is just confirming the rewrite didn't break app startup/compilation in practice, not a full visual check. A full visual check of the 3 dialog states requires a release build pointed at a GitHub release older than the one installed, same process already used earlier in this project's history (install an older release APK, open the app, observe the dialog) — do this once after Task 4 is also done, not here, to avoid re-doing it twice.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/flowfuel/app/feature/update/presentation/UpdateAvailableDialog.kt
git commit -m "feat(update): redesign the update dialog with branding and real download progress"
```

---

## Task 4: Remove `releaseNotes`/`body` (cleanup)

Now safe: after Task 3, nothing in `app/src/main` reads `UpdateInfo.releaseNotes` anymore. This task removes the field and its only producer (`GithubReleaseDto.body` → `UpdateRepositoryImpl.checkForUpdate()`), and fixes the two tests that still reference it.

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/update/domain/model/UpdateInfo.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/update/data/remote/dto/GithubReleaseDto.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/update/data/UpdateRepositoryImpl.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/update/data/UpdateRepositoryImplTest.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/update/presentation/UpdateViewModelTest.kt`

**Interfaces:**
- Produces: `UpdateInfo(tag: String, versionLabel: String, downloadUrl: String)` — `releaseNotes` removed. Nothing later in this plan consumes this (this is the last task).

- [ ] **Step 1: Remove `releaseNotes` from `UpdateInfo`**

Modify `app/src/main/java/com/flowfuel/app/feature/update/domain/model/UpdateInfo.kt` — full file becomes:

```kotlin
package com.flowfuel.app.feature.update.domain.model

data class UpdateInfo(
    val tag: String,
    val versionLabel: String,
    val downloadUrl: String,
)
```

- [ ] **Step 2: Remove `body` from `GithubReleaseDto`**

Modify `app/src/main/java/com/flowfuel/app/feature/update/data/remote/dto/GithubReleaseDto.kt` — full file becomes:

```kotlin
package com.flowfuel.app.feature.update.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("assets") val assets: List<GithubReleaseAssetDto> = emptyList(),
)

@Serializable
data class GithubReleaseAssetDto(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
)
```

- [ ] **Step 3: Remove the `releaseNotes` mapping in `UpdateRepositoryImpl`**

Modify `app/src/main/java/com/flowfuel/app/feature/update/data/UpdateRepositoryImpl.kt` — inside `checkForUpdate()`, change:

```kotlin
            else -> UpdateInfo(
                tag = release.tagName,
                versionLabel = release.tagName.removePrefix("v"),
                releaseNotes = release.body,
                downloadUrl = asset.downloadUrl,
            )
```

to:

```kotlin
            else -> UpdateInfo(
                tag = release.tagName,
                versionLabel = release.tagName.removePrefix("v"),
                downloadUrl = asset.downloadUrl,
            )
```

- [ ] **Step 4: Fix the two tests that reference the removed field**

Modify `app/src/test/java/com/flowfuel/app/feature/update/data/UpdateRepositoryImplTest.kt` — change:

```kotlin
    private fun releaseDto(tagName: String, assetName: String = "flowfuel-app.apk") = GithubReleaseDto(
        tagName = tagName,
        body = "Notas da versão $tagName",
        assets = listOf(GithubReleaseAssetDto(name = assetName, downloadUrl = "https://example.com/$assetName")),
    )
```

to:

```kotlin
    private fun releaseDto(tagName: String, assetName: String = "flowfuel-app.apk") = GithubReleaseDto(
        tagName = tagName,
        assets = listOf(GithubReleaseAssetDto(name = assetName, downloadUrl = "https://example.com/$assetName")),
    )
```

And remove this line from `checkForUpdate returns UpdateInfo when remote tag is newer than the installed version`:

```kotlin
        assertEquals("Notas da versão v999.0.0", result?.releaseNotes)
```

Modify `app/src/test/java/com/flowfuel/app/feature/update/presentation/UpdateViewModelTest.kt` — change:

```kotlin
    private fun updateInfo(tag: String = "v9.9.9") = UpdateInfo(
        tag = tag,
        versionLabel = tag.removePrefix("v"),
        releaseNotes = "notas",
        downloadUrl = "https://example.com/a.apk",
    )
```

to:

```kotlin
    private fun updateInfo(tag: String = "v9.9.9") = UpdateInfo(
        tag = tag,
        versionLabel = tag.removePrefix("v"),
        downloadUrl = "https://example.com/a.apk",
    )
```

- [ ] **Step 5: Run the full `feature.update` suite**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.update.*"
```

Expected: `BUILD SUCCESSFUL` — `DownloadProgressTest` (2), `UpdateRepositoryImplTest` (7), `UpdateViewModelTest` (17), `VersionComparatorTest` (however many it already had) all pass.

- [ ] **Step 6: Run the entire project's unit test suite (regression check)**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, no failures anywhere else in the project (nothing outside `feature/update` references `UpdateInfo.releaseNotes` or `GithubReleaseDto.body` — confirmed during spec research).

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/flowfuel/app/feature/update/domain/model/UpdateInfo.kt app/src/main/java/com/flowfuel/app/feature/update/data/remote/dto/GithubReleaseDto.kt app/src/main/java/com/flowfuel/app/feature/update/data/UpdateRepositoryImpl.kt app/src/test/java/com/flowfuel/app/feature/update/data/UpdateRepositoryImplTest.kt app/src/test/java/com/flowfuel/app/feature/update/presentation/UpdateViewModelTest.kt
git commit -m "refactor(update): drop unused releaseNotes/body now that the dialog no longer shows them"
```

- [ ] **Step 8: Full manual verification**

Follow the same manual process already used earlier for this feature: install an older release APK (e.g. the current latest tag at planning time, v1.3.1) on an emulator or physical device, bump `app/build.gradle.kts` `versionCode`/`versionName` and cut a new release (or otherwise get a newer GitHub release published), open the app, log in, and confirm:
- The "Nova versão disponível" dialog shows the new branded look (icon, no raw markdown/asterisks/URL) with the fixed friendly message.
- Tapping "Atualizar" shows the "Baixando atualização" dialog with a real progress bar and a `"NN% · X,X MB de Y,Y MB"` label that updates over time (not stuck at an indeterminate spinner the whole time).
- The install flow completes exactly as before (install prompt → "App installed" → new version running).
