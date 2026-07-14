# Push Notification Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the three missing pieces of UI for push notifications on Android: a permission-rationale dialog shown once after login, a "Notificações" status row in the Profile screen, and a generic Snackbar shown when the app opens via a notification's deep link.

**Architecture:** A new `NotificationPermissionViewModel` (Hilt) decides, once per app install, whether to show a rationale `FFDialog` before the system permission prompt — mounted once in `MainContainerScreen` (the post-login "Home" container), replacing the unconditional permission request currently in `MainActivity.onCreate`. `ProfileScreen` gets a self-contained status row that reads `NotificationManagerCompat.areNotificationsEnabled()` directly (no ViewModel involvement, matching the existing local-permission-state pattern in `StationsScreen`). `FlowFuelNavHost` gains a `Scaffold` + `SnackbarHostState` wrapping its `NavHost`, fed by two new extras (`notification_title`/`notification_body`) that `MainActivity` reads off the launching `Intent` alongside the existing deep-link `Uri`.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, `androidx.datastore-preferences` (existing `preferencesDataStore` convention), JUnit4 + Robolectric + MockK (existing test convention for anything touching `Context` — see `UpdateViewModelTest.kt`, `VehicleMaintenancePrefsStoreTest.kt`).

## Global Constraints

- Android only, `minSdk = 26`, Kotlin `2.0.21`, Hilt `2.53` — same as the rest of the app; no new Gradle dependencies needed for this plan.
- **Depends on the push-notification-foundation plan** (`docs/superpowers/plans/2026-07-14-push-notification-foundation.md`) for Task 5 only, specifically its Task 7 (`FlowFuelFcmService.kt`). Tasks 1–4 of this plan have no dependency on that plan and can be implemented independently, in either order relative to it.
- The backend (`flowfuel`, separate Spring Boot repo) already has the full push-notification contract implemented (`POST /devices`, `DELETE /devices/{token}`, `sendPushToUser`) — no backend changes in this plan.
- Rationale dialog and permission request only happen on `Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU` (Android 13+). Below that, `POST_NOTIFICATIONS` doesn't exist as a runtime permission.
- `POST_NOTIFICATIONS` is already declared in `app/src/main/AndroidManifest.xml:8` — no manifest change needed in this plan.
- Testing convention for anything needing `Context` (ViewModels, DataStore-backed stores): `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [33])`, `ApplicationProvider.getApplicationContext()`, direct constructor injection in the test — established by `UpdateViewModelTest.kt` and `VehicleMaintenancePrefsStoreTest.kt`. Robolectric is already a test dependency (`libs.robolectric`) — do not assume it's unavailable.
- No new `ProfileActionRow` composable — extend the existing one (`ProfileScreen.kt:527`) with an optional `trailingText` parameter, default `null`, so the three existing callers are unaffected.

---

### Task 1: `NotificationPrefsStore`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/core/notification/data/NotificationPrefsStore.kt`
- Test: `app/src/test/java/com/flowfuel/app/core/notification/data/NotificationPrefsStoreTest.kt`

**Interfaces:**
- Produces: `NotificationPrefsStore.hasShownRationale(): Boolean` (suspend), `NotificationPrefsStore.markRationaleShown()` (suspend) — consumed by Task 2 (`NotificationPermissionViewModel`).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/flowfuel/app/core/notification/data/NotificationPrefsStoreTest.kt`:

```kotlin
package com.flowfuel.app.core.notification.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationPrefsStoreTest {

    // Um único teste cobre o round-trip completo (não dois testes separados):
    // o DataStore por trás deste store é um singleton de nível de classloader
    // no Robolectric (mesma instância viva durante toda a execução da classe,
    // ver comentário em VehicleMaintenancePrefsStoreTest), e como só existe uma
    // chave booleana aqui (sem parâmetro tipo vehicleId para isolar por teste),
    // dois métodos de teste se contaminariam dependendo da ordem de execução.
    @Test
    fun `hasShownRationale defaults to false and becomes true after markRationaleShown`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val store = NotificationPrefsStore(context)

        assertFalse(store.hasShownRationale())

        store.markRationaleShown()

        assertTrue(store.hasShownRationale())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.core.notification.data.NotificationPrefsStoreTest"`
Expected: FAIL — `NotificationPrefsStore` is unresolved.

- [ ] **Step 3: Implement `NotificationPrefsStore`**

Create `app/src/main/java/com/flowfuel/app/core/notification/data/NotificationPrefsStore.kt`:

```kotlin
package com.flowfuel.app.core.notification.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notificationDataStore by preferencesDataStore(name = "flowfuel_notification")

@Singleton
class NotificationPrefsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val RATIONALE_SHOWN = booleanPreferencesKey("rationale_shown")
    }

    suspend fun hasShownRationale(): Boolean =
        context.notificationDataStore.data.firstOrNull()?.get(Keys.RATIONALE_SHOWN) ?: false

    suspend fun markRationaleShown() {
        context.notificationDataStore.edit { it[Keys.RATIONALE_SHOWN] = true }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.core.notification.data.NotificationPrefsStoreTest"`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/notification/data/NotificationPrefsStore.kt app/src/test/java/com/flowfuel/app/core/notification/data/NotificationPrefsStoreTest.kt
git commit -m "feat: add NotificationPrefsStore for the permission-rationale shown flag"
```

---

### Task 2: `NotificationPermissionViewModel`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/core/notification/presentation/NotificationPermissionViewModel.kt`
- Test: `app/src/test/java/com/flowfuel/app/core/notification/presentation/NotificationPermissionViewModelTest.kt`

**Interfaces:**
- Consumes: `NotificationPrefsStore.hasShownRationale()`, `NotificationPrefsStore.markRationaleShown()` (Task 1).
- Produces: `NotificationPermissionUiState` (sealed interface: `Idle`, `ShowRationale`), `NotificationPermissionViewModel.state: StateFlow<NotificationPermissionUiState>`, `NotificationPermissionViewModel.onRationaleShown()` — consumed by Task 3 (`MainContainerScreen`).

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/flowfuel/app/core/notification/presentation/NotificationPermissionViewModelTest.kt`:

```kotlin
package com.flowfuel.app.core.notification.presentation

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.notification.data.NotificationPrefsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationPermissionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefsStore = NotificationPrefsStore(context)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setNotificationsEnabled(enabled: Boolean) {
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationsEnabled(enabled)
    }

    @Test
    fun `shows rationale when notifications are disabled and never shown before`() = runTest {
        setNotificationsEnabled(false)

        val vm = NotificationPermissionViewModel(context, prefsStore)

        assertEquals(NotificationPermissionUiState.ShowRationale, vm.state.value)
    }

    @Test
    fun `stays idle when notifications are already enabled`() = runTest {
        setNotificationsEnabled(true)

        val vm = NotificationPermissionViewModel(context, prefsStore)

        assertEquals(NotificationPermissionUiState.Idle, vm.state.value)
    }

    @Test
    fun `stays idle when the rationale was already shown before`() = runTest {
        setNotificationsEnabled(false)
        prefsStore.markRationaleShown()

        val vm = NotificationPermissionViewModel(context, prefsStore)

        assertEquals(NotificationPermissionUiState.Idle, vm.state.value)
    }

    @Test
    @Config(sdk = [30])
    fun `stays idle below Android 13 regardless of notification state`() = runTest {
        setNotificationsEnabled(false)

        val vm = NotificationPermissionViewModel(context, prefsStore)

        assertEquals(NotificationPermissionUiState.Idle, vm.state.value)
    }

    @Test
    fun `onRationaleShown transitions to Idle and persists the flag`() = runTest {
        setNotificationsEnabled(false)
        val vm = NotificationPermissionViewModel(context, prefsStore)

        vm.onRationaleShown()

        assertEquals(NotificationPermissionUiState.Idle, vm.state.value)
        assertTrue(prefsStore.hasShownRationale())
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.core.notification.presentation.NotificationPermissionViewModelTest"`
Expected: FAIL — `NotificationPermissionViewModel`/`NotificationPermissionUiState` unresolved.

- [ ] **Step 3: Implement the ViewModel**

Create `app/src/main/java/com/flowfuel/app/core/notification/presentation/NotificationPermissionViewModel.kt`:

```kotlin
package com.flowfuel.app.core.notification.presentation

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.notification.data.NotificationPrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NotificationPermissionUiState {
    data object Idle : NotificationPermissionUiState
    data object ShowRationale : NotificationPermissionUiState
}

@HiltViewModel
class NotificationPermissionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsStore: NotificationPrefsStore,
) : ViewModel() {

    private val _state = MutableStateFlow<NotificationPermissionUiState>(NotificationPermissionUiState.Idle)
    val state: StateFlow<NotificationPermissionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (shouldShowRationale()) {
                _state.value = NotificationPermissionUiState.ShowRationale
            }
        }
    }

    fun onRationaleShown() {
        _state.value = NotificationPermissionUiState.Idle
        viewModelScope.launch { prefsStore.markRationaleShown() }
    }

    private suspend fun shouldShowRationale(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return !prefsStore.hasShownRationale()
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.core.notification.presentation.NotificationPermissionViewModelTest"`
Expected: `BUILD SUCCESSFUL`, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/notification/presentation/NotificationPermissionViewModel.kt app/src/test/java/com/flowfuel/app/core/notification/presentation/NotificationPermissionViewModelTest.kt
git commit -m "feat: add NotificationPermissionViewModel to gate the rationale dialog"
```

---

### Task 3: Move the permission request behind the rationale dialog

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/MainActivity.kt`
- Modify: `app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt`

**Interfaces:**
- Consumes: `NotificationPermissionViewModel`, `NotificationPermissionUiState` (Task 2).

No new automated test — this task rewires existing, already-tested pieces (the ViewModel's logic is covered by Task 2; the dialog trigger itself is a manual/compile-time check, matching the codebase's convention of not writing Compose UI tests for dialog wiring, e.g. `UpdateAvailableDialog`'s wiring in this same file has no dedicated test either).

- [ ] **Step 1: Remove the unconditional permission request from `MainActivity`**

Replace the full contents of `app/src/main/java/com/flowfuel/app/MainActivity.kt` with:

```kotlin
package com.flowfuel.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.flowfuel.app.core.designsystem.theme.FlowFuelTheme
import com.flowfuel.app.navigation.FlowFuelNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var deepLinkUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkUri = intent.data
        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }
        setContent {
            FlowFuelTheme {
                FlowFuelNavHost(
                    onSplashReady = { keepSplash = false },
                    deepLinkUri = deepLinkUri,
                    onDeepLinkConsumed = { deepLinkUri = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkUri = intent.data
    }
}
```

This removes the `notificationPermissionLauncher` property and its unconditional `.launch(...)` call in `onCreate`, along with the now-unused `Manifest`, `Build`, and `ActivityResultContracts` imports. (Task 5 will add two more fields back to this file for the notification title/body extras — this step intentionally leaves the file at its simplest form first.)

- [ ] **Step 2: Add the permission-rationale gate to `MainContainerScreen`**

In `app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt`, add these imports (alongside the existing `android.content.Context`, `android.content.Intent`, `android.provider.Settings` imports at the top):

```kotlin
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.flowfuel.app.core.designsystem.components.FFDialog
import com.flowfuel.app.core.designsystem.components.FFDialogKind
import com.flowfuel.app.core.notification.presentation.NotificationPermissionUiState
import com.flowfuel.app.core.notification.presentation.NotificationPermissionViewModel
```

Add a new parameter to the `MainContainerScreen` function signature (`MainContainerScreen.kt:114-115`), right after `updateViewModel`:

```kotlin
    quickRefuelViewModel: QuickRefuelViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
    notificationPermissionViewModel: NotificationPermissionViewModel = hiltViewModel(),
) {
```

At the very end of the function, insert the dialog block right before the closing brace of the `when (val update = updateState) { ... }` block (`MainContainerScreen.kt:389-391`):

Before:
```kotlin
        UpdateUiState.Idle -> Unit
    }
}
```

After:
```kotlin
        UpdateUiState.Idle -> Unit
    }

    val notificationPermissionState by notificationPermissionViewModel.state.collectAsState()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* sem-op — o status é reavaliado via NotificationManagerCompat quando o Perfil volta ao foreground (Task 4) */ }

    if (notificationPermissionState is NotificationPermissionUiState.ShowRationale) {
        FFDialog(
            title       = "Ativar notificações?",
            message     = "Avisamos sobre coisas importantes, como um convite de compartilhamento de veículo. Você pode mudar isso depois em Perfil > Notificações.",
            confirmText = "Ativar",
            dismissText = "Agora não",
            kind        = FFDialogKind.Info,
            onConfirm   = {
                notificationPermissionViewModel.onRationaleShown()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss   = notificationPermissionViewModel::onRationaleShown,
        )
    }
}
```

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, no regressions (this task didn't touch any tested logic, but confirms nothing else silently broke).

- [ ] **Step 5: Manual verification**

Install a debug build on an emulator/device running API 33+ (`./gradlew :app:installDebug`). Log in with a test account. Confirm:
- No permission prompt appears before login.
- The rationale dialog appears once, the first time the Home screen (`MAIN_CONTAINER`) is reached.
- Tapping "Ativar" shows the system permission prompt.
- Logging out and back in does **not** show the dialog again.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/MainActivity.kt app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt
git commit -m "feat: show a rationale dialog before requesting POST_NOTIFICATIONS"
```

---

### Task 4: Notification status row in the Profile screen

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileScreen.kt`

**Interfaces:** none new — self-contained UI, no ViewModel changes.

No automated test — matches the existing precedent in `StationsScreen.kt`, where the analogous local permission-status handling (`hasPermanentlyDeniedPermission`, opening system settings) also has no dedicated Compose UI test in this codebase.

- [ ] **Step 1: Add a `trailingText` parameter to `ProfileActionRow`**

In `app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileScreen.kt`, replace the `ProfileActionRow` composable (`ProfileScreen.kt:526-553`):

Before:
```kotlin
@Composable
private fun ProfileActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = FFTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(
                text     = label,
                style    = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector        = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

After:
```kotlin
@Composable
private fun ProfileActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    trailingText: String? = null,
) {
    Surface(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = FFTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(
                text     = label,
                style    = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (trailingText != null) {
                Text(
                    text  = trailingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector        = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 2: Add the `NotificationStatusRow` composable**

In the same file, add this new private composable right after `ProfileActionRow` (i.e. after the block just edited in Step 1):

```kotlin
@Composable
private fun NotificationStatusRow() {
    val context = LocalContext.current
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ProfileActionRow(
        icon         = Icons.Outlined.Notifications,
        label        = "Notificações",
        trailingText = if (notificationsEnabled) "Ativadas" else "Desativadas",
        onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            )
        },
    )
}
```

- [ ] **Step 3: Wire it into the "Ações" section**

In the same file, replace the "Ações" block (`ProfileScreen.kt:306-324`):

Before:
```kotlin
        ProfileActionRow(
            icon    = Icons.Outlined.DirectionsCar,
            label   = "Meus veículos",
            onClick = onManageVehicles,
        )
        HorizontalDivider()
        ProfileActionRow(
            icon    = Icons.Outlined.Edit,
            label   = "Editar perfil",
            onClick = onEditProfile,
        )
        HorizontalDivider()
        ProfileActionRow(
            icon    = Icons.Outlined.Lock,
            label   = "Trocar senha",
            onClick = onChangePassword,
        )
        HorizontalDivider()
```

After:
```kotlin
        ProfileActionRow(
            icon    = Icons.Outlined.DirectionsCar,
            label   = "Meus veículos",
            onClick = onManageVehicles,
        )
        HorizontalDivider()
        ProfileActionRow(
            icon    = Icons.Outlined.Edit,
            label   = "Editar perfil",
            onClick = onEditProfile,
        )
        HorizontalDivider()
        ProfileActionRow(
            icon    = Icons.Outlined.Lock,
            label   = "Trocar senha",
            onClick = onChangePassword,
        )
        HorizontalDivider()
        NotificationStatusRow()
        HorizontalDivider()
```

- [ ] **Step 4: Add the missing imports**

At the top of `app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileScreen.kt`, add:

```kotlin
import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
```

- [ ] **Step 5: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, no regressions.

- [ ] **Step 7: Manual verification**

Install a debug build, open Perfil. Confirm:
- The "Notificações" row shows "Ativadas" or "Desativadas" matching the real system state (Android Settings → Apps → FlowFuel → Notifications).
- Tapping the row opens the app's notification settings screen directly (not the generic app-info screen).
- Toggling notifications off in system settings, then returning to the Perfil tab (or backgrounding/foregrounding the app), updates the row's text without needing to leave and re-enter the screen manually first — it updates on `ON_RESUME`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileScreen.kt
git commit -m "feat: show notification permission status in the Profile screen"
```

---

### Task 5: Snackbar feedback after opening the app via a notification

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/core/notification/FlowFuelFcmService.kt` (created by Task 7 of `docs/superpowers/plans/2026-07-14-push-notification-foundation.md` — implement that task first if this file doesn't exist yet)
- Modify: `app/src/main/java/com/flowfuel/app/MainActivity.kt`
- Modify: `app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt`

**Interfaces:**
- Produces: `MainActivity.EXTRA_NOTIFICATION_TITLE`, `MainActivity.EXTRA_NOTIFICATION_BODY` (String constants) — consumed by `FlowFuelFcmService` (this task) and read back by `MainActivity` itself.

No automated test — this is Activity/Intent/NavHost wiring with no unit-testable logic of its own (matches the foundation plan's own precedent of no test for `FlowFuelFcmService`'s Intent-building code).

- [ ] **Step 1: Add the notification extras to `FlowFuelFcmService`**

In `app/src/main/java/com/flowfuel/app/core/notification/FlowFuelFcmService.kt`, add the import `com.flowfuel.app.MainActivity` if not already present, then modify `showNotification`:

Before:
```kotlin
    private fun showNotification(payload: PushPayload) {
        val intent = Intent(this, MainActivity::class.java).apply {
            if (!payload.deepLink.isNullOrBlank()) {
                action = Intent.ACTION_VIEW
                data = Uri.parse(payload.deepLink)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
```

After:
```kotlin
    private fun showNotification(payload: PushPayload) {
        val intent = Intent(this, MainActivity::class.java).apply {
            if (!payload.deepLink.isNullOrBlank()) {
                action = Intent.ACTION_VIEW
                data = Uri.parse(payload.deepLink)
            }
            putExtra(MainActivity.EXTRA_NOTIFICATION_TITLE, payload.title)
            putExtra(MainActivity.EXTRA_NOTIFICATION_BODY, payload.body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
```

- [ ] **Step 2: Read the extras in `MainActivity` and expose them alongside `deepLinkUri`**

Replace the full contents of `app/src/main/java/com/flowfuel/app/MainActivity.kt` (as left by Task 3, Step 1) with:

```kotlin
package com.flowfuel.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.flowfuel.app.core.designsystem.theme.FlowFuelTheme
import com.flowfuel.app.navigation.FlowFuelNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var deepLinkUri by mutableStateOf<Uri?>(null)
    private var notificationTitle by mutableStateOf<String?>(null)
    private var notificationBody by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyIntentExtras(intent)
        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }
        setContent {
            FlowFuelTheme {
                FlowFuelNavHost(
                    onSplashReady = { keepSplash = false },
                    deepLinkUri = deepLinkUri,
                    notificationTitle = notificationTitle,
                    notificationBody = notificationBody,
                    onDeepLinkConsumed = {
                        deepLinkUri = null
                        notificationTitle = null
                        notificationBody = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntentExtras(intent)
    }

    private fun applyIntentExtras(intent: Intent) {
        deepLinkUri = intent.data
        notificationTitle = intent.getStringExtra(EXTRA_NOTIFICATION_TITLE)
        notificationBody = intent.getStringExtra(EXTRA_NOTIFICATION_BODY)
    }

    companion object {
        const val EXTRA_NOTIFICATION_TITLE = "notification_title"
        const val EXTRA_NOTIFICATION_BODY = "notification_body"
    }
}
```

- [ ] **Step 3: Accept the new params and add a `SnackbarHostState` in `FlowFuelNavHost`**

In `app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt`, add these imports alongside the existing ones:

```kotlin
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
```

Replace the function signature and the first three lines of the body (`FlowFuelNavHost.kt:45-53`):

Before:
```kotlin
@Composable
fun FlowFuelNavHost(
    onSplashReady: () -> Unit,
    deepLinkUri: Uri? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val splashVm: SplashViewModel = hiltViewModel()
    val start by splashVm.start.collectAsState()
```

After:
```kotlin
@Composable
fun FlowFuelNavHost(
    onSplashReady: () -> Unit,
    deepLinkUri: Uri? = null,
    notificationTitle: String? = null,
    notificationBody: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val splashVm: SplashViewModel = hiltViewModel()
    val start by splashVm.start.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
```

- [ ] **Step 4: Trigger the Snackbar after a successful deep-link navigation**

In the same file, replace the generic-path branch of the deep-link `LaunchedEffect` (`FlowFuelNavHost.kt:76-86`):

Before:
```kotlin
        if (start != StartDestination.Home) return@LaunchedEffect
        // Uri.path não inclui o host (ex.: flowfuel://vehicle/details/1 → host="vehicle",
        // path="/details/1") — é preciso recombinar os dois para bater com as rotas internas.
        val path = listOfNotNull(uri.host, uri.path?.removePrefix("/"))
            .filter { it.isNotBlank() }
            .joinToString("/")
        if (path.isNotBlank()) {
            navController.currentBackStackEntryFlow.first { it.destination.route == Destinations.MAIN_CONTAINER }
            runCatching { navController.navigate(path) }
        }
        onDeepLinkConsumed()
```

After:
```kotlin
        if (start != StartDestination.Home) return@LaunchedEffect
        // Uri.path não inclui o host (ex.: flowfuel://vehicle/details/1 → host="vehicle",
        // path="/details/1") — é preciso recombinar os dois para bater com as rotas internas.
        val path = listOfNotNull(uri.host, uri.path?.removePrefix("/"))
            .filter { it.isNotBlank() }
            .joinToString("/")
        if (path.isNotBlank()) {
            navController.currentBackStackEntryFlow.first { it.destination.route == Destinations.MAIN_CONTAINER }
            val navigated = runCatching { navController.navigate(path) }.isSuccess
            if (navigated && !notificationTitle.isNullOrBlank()) {
                snackbarHostState.showSnackbar(
                    FFSnackbarVisuals(
                        message = listOfNotNull(notificationTitle, notificationBody).joinToString(" — "),
                        kind = FFSnackbarKind.Info,
                    )
                )
            }
        }
        onDeepLinkConsumed()
```

- [ ] **Step 5: Wrap the `NavHost` in a `Scaffold` hosting the Snackbar**

In the same file, replace the `NavHost` opening call (`FlowFuelNavHost.kt:125-132`):

Before:
```kotlin
    NavHost(
        navController = navController,
        startDestination = Destinations.SPLASH,
        enterTransition = { defaultEnter() },
        exitTransition = { defaultExit() },
        popEnterTransition = { defaultEnter() },
        popExitTransition = { defaultExit() },
    ) {
```

After:
```kotlin
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
    NavHost(
        navController = navController,
        startDestination = Destinations.SPLASH,
        modifier = Modifier.padding(innerPadding),
        enterTransition = { defaultEnter() },
        exitTransition = { defaultExit() },
        popEnterTransition = { defaultEnter() },
        popExitTransition = { defaultExit() },
    ) {
```

Do not touch anything between this point and the end of the `NavHost` trailing lambda — all the `composable(...)` blocks stay exactly as they are.

- [ ] **Step 6: Close the new `Scaffold` block**

At the very end of the file, replace the closing braces of the `CHANGE_PASSWORD` composable through the end of the function (`FlowFuelNavHost.kt:716-729`):

Before:
```kotlin
        // ── Troca de senha ─────────────────────────────────────────────────
        composable(Destinations.CHANGE_PASSWORD) {
            ChangePasswordScreen(
                onSuccess = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("password_changed", true)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
```

After:
```kotlin
        // ── Troca de senha ─────────────────────────────────────────────────
        composable(Destinations.CHANGE_PASSWORD) {
            ChangePasswordScreen(
                onSuccess = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("password_changed", true)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
    }
}
```

- [ ] **Step 7: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. If it fails with an unresolved reference to `MainActivity.EXTRA_NOTIFICATION_TITLE` from `FlowFuelFcmService.kt`, confirm Task 7 of the foundation plan has been implemented first (see the Files note at the top of this task).

- [ ] **Step 8: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, no regressions.

- [ ] **Step 9: Manual verification**

Requires the push-notification-foundation plan fully implemented and the backend (`flowfuel`) reachable with `flowfuel.push.enabled=true`. Send a test push targeting a known route (e.g. `deepLink = "flowfuel://vehicle/details/1"`, per Task 9 of the foundation plan). Verify:
- Tapping the notification opens the app on that route.
- A Snackbar appears with the notification's title and body.
- The Snackbar does **not** appear when opening the app normally (not via a notification) or via the `flowfuel://activate` deep link.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/notification/FlowFuelFcmService.kt app/src/main/java/com/flowfuel/app/MainActivity.kt app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt
git commit -m "feat: show a snackbar with the notification's title/body after deep-link navigation"
```
