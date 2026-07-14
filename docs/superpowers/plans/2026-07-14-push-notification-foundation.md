# Push Notification Foundation (FCM) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a generic Firebase Cloud Messaging (FCM) foundation to the Android client — device token registration/renewal/removal, receiving and displaying a push notification, and navigating to a route via the existing `flowfuel://` deep link mechanism when the notification is tapped.

**Architecture:** A new `core/notification` module owns a `FirebaseMessagingService` subclass, a `PushPayload` parser, a notification channel setup, and a `DeviceTokenRepository` that talks to a new `DeviceTokenApi`. Registration hooks into the existing auth success/logout chokepoints (`AuthRepositoryImpl.handleAuthSuccess`, `ProfileViewModel.logout`). Notification taps reuse `MainActivity`'s existing `intent.data` → `FlowFuelNavHost` deep-link flow — no new navigation code.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Retrofit + kotlinx.serialization, Firebase Cloud Messaging (`firebase-messaging-ktx` via `firebase-bom`), `kotlinx-coroutines-play-services` (for `Task<T>.await()`), JUnit4 + MockK (existing test convention in this codebase — despite `junit5` being present in the version catalog, all existing repository/viewmodel tests use `org.junit.Test`/`Assert`, not JUnit5 annotations; this plan follows that established convention, not the catalog aspiration).

## Global Constraints

- Android only — no iOS work.
- `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`, Kotlin `2.0.21`, Hilt `2.53` (from `app/build.gradle.kts` / `gradle/libs.versions.toml` — do not bump unrelated versions).
- Single notification channel, id `"general"` — no per-type channels or templates.
- FCM messages are **data-only** (no `notification` key in the payload) so the client fully controls display. Payload shape: `{ "title": "...", "body": "...", "deepLink": "flowfuel://...", "type": "generic" }`. The `type` field is parsed and stored but not branched on yet.
- Reuse the existing `flowfuel://<host>/<path>` deep link mechanism in `FlowFuelNavHost.kt` (lines 61-86) — it already accepts arbitrary routes beyond the `activate` special case. Do not add new navigation routes or a new deep-link parsing path.
- Backend endpoints (`POST /devices`, `DELETE /devices/:token`, and the internal `sendPushToUser`) live in the separate `flowfuel` (Spring Boot/Java, not Node/TypeScript as originally assumed) repository, **not present in this workspace**, and are **out of scope for this plan** — that contract is already implemented and live there (see `docs/superpowers/specs/2026-07-14-fcm-push-notification-foundation-design.md` in that repo). One contract detail matters for Task 5 below: the backend's `platform` field is a Java enum with a single value `ANDROID` (uppercase, case-sensitive Jackson deserialization) — sending `"android"` (lowercase) fails with a 400. `RegisterDeviceRequestDto` must default to `"ANDROID"`.
- Firebase project + `google-services.json` do not exist yet in this repo and must be set up manually (Task 1) before any other task's Gradle changes will build.
- `POST_NOTIFICATIONS` runtime permission is **already requested** unconditionally in `MainActivity.onCreate` (`MainActivity.kt:32-34`, no-op callback) — this supersedes the spec's phrasing ("solicitada no fluxo de login/onboarding"); no new permission-request code is needed anywhere in this plan.

---

### Task 1: Firebase project setup (manual prerequisite, not code)

**Files:**
- Create: `app/google-services.json` (downloaded from Firebase Console, not written by hand)

This task has no automatable steps — it requires a human with access to (or creating) a Firebase project and the Google Play Console/app signing config.

- [ ] **Step 1: Create or reuse a Firebase project**

Go to https://console.firebase.google.com, create a project (or reuse an existing one for FlowFuel), and add an Android app with package name `com.flowfuel.app` (the release `applicationId` — see `app/build.gradle.kts:18`). Register the debug variant too if using `com.flowfuel.app.debug` (the `applicationIdSuffix` for debug builds, `app/build.gradle.kts:49`) as a second Android app in the same Firebase project, or the debug build's FCM registration will silently fail package-name matching.

- [ ] **Step 2: Download `google-services.json`**

Download the config file from the Firebase Console (Project Settings → your Android app) and place it at `app/google-services.json` (same directory as `app/build.gradle.kts`).

- [ ] **Step 3: Verify it's git-ignored appropriately or not, per your preference**

Check `.gitignore` — this file is not a secret in the traditional sense (it's bundled into the APK anyway) but confirm whether you want it committed or kept local. If keeping it out of git, add `app/google-services.json` to `.gitignore` and document in `README.md`'s "Configurações locais" section that it must be obtained from Firebase Console.

- [ ] **Step 4: Confirm the file is in place before continuing**

Run: `ls "app/google-services.json"` (or `Test-Path` in PowerShell) — must exist before Task 2, otherwise the `google-services` Gradle plugin (added in Task 2) will fail the build with `File google-services.json is missing`.

---

### Task 2: Gradle dependencies and plugin wiring

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `libs.firebase.bom`, `libs.firebase.messaging`, `libs.kotlinx.coroutines.play.services`, `libs.plugins.google.services` — used by Task 6 (repository) and Task 7 (FCM service).

- [ ] **Step 1: Add version catalog entries**

In `gradle/libs.versions.toml`, add to `[versions]` (after line 35, `playServicesLocation = "21.3.0"`):

```toml
firebaseBom = "33.7.0"
googleServices = "4.4.2"
```

Add to `[libraries]` (after line 106, `play-services-location = ...`):

```toml
firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebaseBom" }
firebase-messaging = { module = "com.google.firebase:firebase-messaging-ktx" }
kotlinx-coroutines-play-services = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-play-services", version.ref = "coroutines" }
```

Add to `[plugins]` (after line 114, `hilt = ...`):

```toml
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

- [ ] **Step 2: Apply the plugin at the root**

In `build.gradle.kts` (root), add to the `plugins` block:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
}
```

- [ ] **Step 3: Apply the plugin and add dependencies in the app module**

In `app/build.gradle.kts`, add to the `plugins` block:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}
```

Add to the `dependencies` block (after line 116, `implementation(libs.play.services.location)`):

```kotlin
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)
```

- [ ] **Step 4: Sync and build to verify the plugin wiring works**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. If it fails with `File google-services.json is missing`, go back to Task 1.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts
git commit -m "build: add Firebase Cloud Messaging dependencies and plugin"
```

(Do not commit `app/google-services.json` in this step if you decided to keep it untracked in Task 1, Step 3.)

---

### Task 3: `PushPayload` parsing

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/core/notification/PushPayload.kt`
- Test: `app/src/test/java/com/flowfuel/app/core/notification/PushPayloadTest.kt`

**Interfaces:**
- Produces: `PushPayload(title: String, body: String, deepLink: String?, type: String)` and `PushPayload.fromData(data: Map<String, String>): PushPayload?` — consumed by Task 7 (`FlowFuelFcmService`).

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/flowfuel/app/core/notification/PushPayloadTest.kt`:

```kotlin
package com.flowfuel.app.core.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushPayloadTest {

    @Test
    fun `fromData parses all fields`() {
        val data = mapOf(
            "title" to "Convite recebido",
            "body" to "Maria compartilhou um veículo com você",
            "deepLink" to "flowfuel://vehicle/details/1",
            "type" to "vehicle_share_invite",
        )

        val payload = PushPayload.fromData(data)

        assertEquals(
            PushPayload(
                title = "Convite recebido",
                body = "Maria compartilhou um veículo com você",
                deepLink = "flowfuel://vehicle/details/1",
                type = "vehicle_share_invite",
            ),
            payload,
        )
    }

    @Test
    fun `fromData defaults type to generic when absent`() {
        val data = mapOf("title" to "Título", "body" to "Corpo")

        val payload = PushPayload.fromData(data)

        assertEquals("generic", payload?.type)
    }

    @Test
    fun `fromData allows missing deepLink`() {
        val data = mapOf("title" to "Título", "body" to "Corpo")

        val payload = PushPayload.fromData(data)

        assertNull(payload?.deepLink)
    }

    @Test
    fun `fromData returns null when title is missing`() {
        val data = mapOf("body" to "Corpo")

        assertNull(PushPayload.fromData(data))
    }

    @Test
    fun `fromData returns null when body is missing`() {
        val data = mapOf("title" to "Título")

        assertNull(PushPayload.fromData(data))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.core.notification.PushPayloadTest"`
Expected: FAIL — `PushPayload` is unresolved (class doesn't exist yet).

- [ ] **Step 3: Implement `PushPayload`**

Create `app/src/main/java/com/flowfuel/app/core/notification/PushPayload.kt`:

```kotlin
package com.flowfuel.app.core.notification

data class PushPayload(
    val title: String,
    val body: String,
    val deepLink: String?,
    val type: String,
) {
    companion object {
        fun fromData(data: Map<String, String>): PushPayload? {
            val title = data["title"] ?: return null
            val body = data["body"] ?: return null
            return PushPayload(
                title = title,
                body = body,
                deepLink = data["deepLink"],
                type = data["type"] ?: "generic",
            )
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.core.notification.PushPayloadTest"`
Expected: `BUILD SUCCESSFUL`, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/notification/PushPayload.kt app/src/test/java/com/flowfuel/app/core/notification/PushPayloadTest.kt
git commit -m "feat: add PushPayload parsing for FCM data messages"
```

---

### Task 4: Notification channel setup

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/core/notification/NotificationChannelSetup.kt`
- Modify: `app/src/main/java/com/flowfuel/app/FlowFuelApplication.kt`

**Interfaces:**
- Produces: `NotificationChannelSetup.CHANNEL_ID` (const String), `NotificationChannelSetup.create(context: Context)` — consumed by Task 7 (`FlowFuelFcmService`, for the channel id) and by `FlowFuelApplication.onCreate`.

No automated test for this task — creating a system notification channel is a side effect verified visually in Task 9's manual end-to-end check (channel appears under Android Settings → Apps → FlowFuel → Notifications).

- [ ] **Step 1: Implement `NotificationChannelSetup`**

Create `app/src/main/java/com/flowfuel/app/core/notification/NotificationChannelSetup.kt`:

```kotlin
package com.flowfuel.app.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

object NotificationChannelSetup {
    const val CHANNEL_ID = "general"

    fun create(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Geral",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }
}
```

- [ ] **Step 2: Call it from `FlowFuelApplication.onCreate`**

In `app/src/main/java/com/flowfuel/app/FlowFuelApplication.kt`, add the import `com.flowfuel.app.core.notification.NotificationChannelSetup` and call it right after the Coil setup (after line 39, `Coil.setImageLoader(imageLoader)`):

```kotlin
        // Initialize Coil with the authenticated OkHttp client
        Coil.setImageLoader(imageLoader)

        NotificationChannelSetup.create(this)

        if (BuildConfig.DEBUG) {
```

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/notification/NotificationChannelSetup.kt app/src/main/java/com/flowfuel/app/FlowFuelApplication.kt
git commit -m "feat: create general notification channel on app startup"
```

---

### Task 5: `DeviceTokenApi`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/core/notification/data/remote/DeviceTokenApi.kt`

**Interfaces:**
- Produces: `DeviceTokenApi.registerDevice(body: RegisterDeviceRequestDto)`, `DeviceTokenApi.unregisterDevice(token: String)`, `RegisterDeviceRequestDto(token: String, platform: String = "ANDROID")` — consumed by Task 6 (`DeviceTokenRepositoryImpl`). The backend's `platform` field is a Java enum with a single value `ANDROID` (uppercase) — sending `"android"` fails deserialization with a 400.

No standalone test for this file — it's a plain Retrofit interface declaration, matching the existing convention where `AuthApi.kt` has no dedicated test file either (verified via `AuthRepositoryImplTest.kt`, which tests the repository that calls the API, not the API interface itself). Task 6's repository test covers this indirectly via MockK.

- [ ] **Step 1: Implement the API interface**

Create `app/src/main/java/com/flowfuel/app/core/notification/data/remote/DeviceTokenApi.kt`:

```kotlin
package com.flowfuel.app.core.notification.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class RegisterDeviceRequestDto(
    val token: String,
    val platform: String = "ANDROID",
)

interface DeviceTokenApi {
    @POST("devices")
    suspend fun registerDevice(@Body body: RegisterDeviceRequestDto)

    @DELETE("devices/{token}")
    suspend fun unregisterDevice(@Path("token") token: String)
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/notification/data/remote/DeviceTokenApi.kt
git commit -m "feat: add DeviceTokenApi Retrofit interface"
```

---

### Task 6: `DeviceTokenRepository`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/core/notification/domain/DeviceTokenRepository.kt`
- Create: `app/src/main/java/com/flowfuel/app/core/notification/data/DeviceTokenRepositoryImpl.kt`
- Create: `app/src/main/java/com/flowfuel/app/core/notification/di/DeviceTokenModule.kt`
- Test: `app/src/test/java/com/flowfuel/app/core/notification/data/DeviceTokenRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `DeviceTokenApi` (Task 5), `apiCall` (`com.flowfuel.app.core.network.apiCall`, existing), `AppResult`/`AppError` (`com.flowfuel.app.core.domain`, existing).
- Produces: `DeviceTokenRepository.registerToken(token: String): AppResult<Unit>`, `DeviceTokenRepository.unregisterToken(token: String): AppResult<Unit>` — consumed by Task 7 (`FlowFuelFcmService.onNewToken`), Task 8 (`AuthRepositoryImpl`, `ProfileViewModel`).

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/flowfuel/app/core/notification/data/DeviceTokenRepositoryImplTest.kt`:

```kotlin
package com.flowfuel.app.core.notification.data

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.notification.data.remote.DeviceTokenApi
import com.flowfuel.app.core.notification.data.remote.RegisterDeviceRequestDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class DeviceTokenRepositoryImplTest {

    private val api: DeviceTokenApi = mockk()
    private lateinit var repository: DeviceTokenRepositoryImpl

    @Before
    fun setUp() {
        repository = DeviceTokenRepositoryImpl(api)
    }

    @Test
    fun `registerToken success sends token with ANDROID platform`() = runTest {
        coEvery { api.registerDevice(any()) } returns Unit

        val result = repository.registerToken("fcm-token-123")

        assertEquals(AppResult.Success(Unit), result)
        coVerify { api.registerDevice(RegisterDeviceRequestDto("fcm-token-123", "ANDROID")) }
    }

    @Test
    fun `registerToken io exception returns network failure`() = runTest {
        coEvery { api.registerDevice(any()) } throws IOException("timeout")

        val result = repository.registerToken("fcm-token-123")

        assertEquals(AppResult.Failure(AppError.Network), result)
    }

    @Test
    fun `unregisterToken success calls delete with the given token`() = runTest {
        coEvery { api.unregisterDevice(any()) } returns Unit

        val result = repository.unregisterToken("fcm-token-123")

        assertEquals(AppResult.Success(Unit), result)
        coVerify { api.unregisterDevice("fcm-token-123") }
    }

    @Test
    fun `unregisterToken io exception returns network failure`() = runTest {
        coEvery { api.unregisterDevice(any()) } throws IOException("timeout")

        val result = repository.unregisterToken("fcm-token-123")

        assertEquals(AppResult.Failure(AppError.Network), result)
    }
}
```

Note: this test drives the repository with an explicit token string for both directions (`registerToken`/`unregisterToken`), rather than reaching into `FirebaseMessaging.getInstance()` — that call happens one layer up, in `FlowFuelFcmService` (which already has the token from `onNewToken`) and in `ProfileViewModel.logout()` (Task 8, which fetches the current token before calling `unregisterToken`). Keeping `FirebaseMessaging` static calls out of the repository is what makes it unit-testable without Robolectric.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.core.notification.data.DeviceTokenRepositoryImplTest"`
Expected: FAIL — `DeviceTokenRepositoryImpl` is unresolved.

- [ ] **Step 3: Implement the domain interface**

Create `app/src/main/java/com/flowfuel/app/core/notification/domain/DeviceTokenRepository.kt`:

```kotlin
package com.flowfuel.app.core.notification.domain

import com.flowfuel.app.core.domain.AppResult

interface DeviceTokenRepository {
    suspend fun registerToken(token: String): AppResult<Unit>
    suspend fun unregisterToken(token: String): AppResult<Unit>
}
```

- [ ] **Step 4: Implement the repository**

Create `app/src/main/java/com/flowfuel/app/core/notification/data/DeviceTokenRepositoryImpl.kt`:

```kotlin
package com.flowfuel.app.core.notification.data

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.network.apiCall
import com.flowfuel.app.core.notification.data.remote.DeviceTokenApi
import com.flowfuel.app.core.notification.data.remote.RegisterDeviceRequestDto
import com.flowfuel.app.core.notification.domain.DeviceTokenRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceTokenRepositoryImpl @Inject constructor(
    private val api: DeviceTokenApi,
) : DeviceTokenRepository {

    override suspend fun registerToken(token: String): AppResult<Unit> =
        apiCall { api.registerDevice(RegisterDeviceRequestDto(token)) }

    override suspend fun unregisterToken(token: String): AppResult<Unit> =
        apiCall { api.unregisterDevice(token) }
}
```

- [ ] **Step 5: Wire up Hilt DI**

Create `app/src/main/java/com/flowfuel/app/core/notification/di/DeviceTokenModule.kt`, mirroring `feature/auth/di/AuthModule.kt`:

```kotlin
package com.flowfuel.app.core.notification.di

import com.flowfuel.app.core.notification.data.DeviceTokenRepositoryImpl
import com.flowfuel.app.core.notification.data.remote.DeviceTokenApi
import com.flowfuel.app.core.notification.domain.DeviceTokenRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceTokenBindModule {
    @Binds @Singleton
    abstract fun bindDeviceTokenRepository(impl: DeviceTokenRepositoryImpl): DeviceTokenRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DeviceTokenApiModule {
    @Provides @Singleton
    fun provideDeviceTokenApi(retrofit: Retrofit): DeviceTokenApi = retrofit.create(DeviceTokenApi::class.java)
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.core.notification.data.DeviceTokenRepositoryImplTest"`
Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/notification/domain/DeviceTokenRepository.kt app/src/main/java/com/flowfuel/app/core/notification/data/DeviceTokenRepositoryImpl.kt app/src/main/java/com/flowfuel/app/core/notification/di/DeviceTokenModule.kt app/src/test/java/com/flowfuel/app/core/notification/data/DeviceTokenRepositoryImplTest.kt
git commit -m "feat: add DeviceTokenRepository with register/unregister"
```

---

### Task 7: `FlowFuelFcmService`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/core/notification/FlowFuelFcmService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `DeviceTokenRepository.registerToken(token: String)` (Task 6), `PushPayload.fromData(data)` (Task 3), `NotificationChannelSetup.CHANNEL_ID` (Task 4), `MainActivity` (existing, `com.flowfuel.app.MainActivity`).

No automated test for this class — `FirebaseMessagingService` lifecycle methods (`onNewToken`, `onMessageReceived`) are framework callbacks that need a running Android service context to exercise meaningfully; per the approved spec, this is covered by the manual end-to-end check in Task 9, not a Robolectric test.

- [ ] **Step 1: Implement the service**

Create `app/src/main/java/com/flowfuel/app/core/notification/FlowFuelFcmService.kt`:

```kotlin
package com.flowfuel.app.core.notification

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.flowfuel.app.MainActivity
import com.flowfuel.app.R
import com.flowfuel.app.core.notification.domain.DeviceTokenRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FlowFuelFcmService : FirebaseMessagingService() {

    @Inject lateinit var deviceTokenRepository: DeviceTokenRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch { deviceTokenRepository.registerToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val payload = PushPayload.fromData(message.data) ?: return
        showNotification(payload)
    }

    private fun showNotification(payload: PushPayload) {
        val intent = Intent(this, MainActivity::class.java).apply {
            if (!payload.deepLink.isNullOrBlank()) {
                action = Intent.ACTION_VIEW
                data = Uri.parse(payload.deepLink)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, NotificationChannelSetup.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
    }
}
```

Note: `R.drawable.ic_launcher_foreground` (`app/src/main/res/drawable/ic_launcher_foreground.xml`) is reused as the small icon — it's already a single-color white vector path (a fuel-drop silhouette) on a transparent background, exactly the shape the Android status bar expects for a monochrome notification icon. No new drawable resource is needed.

`NotificationManagerCompat.notify()` will trigger an Android Studio lint warning (`MissingPermission`) because it's annotated `@RequiresPermission(POST_NOTIFICATIONS)` — the manifest already declares `POST_NOTIFICATIONS` (`AndroidManifest.xml:8`) and `MainActivity` already requests it at runtime (`MainActivity.kt:32-34`), so this is a known, accepted lint warning, not a functional gap: if the user denies the permission, the system silently drops the notification (no crash) rather than throwing.

- [ ] **Step 2: Register the service in the manifest**

In `app/src/main/AndroidManifest.xml`, add a new `<service>` block inside `<application>`, right after the `AutoCarAppService` block (after line 77):

```xml
        <service
            android:name=".core.notification.FlowFuelFcmService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
```

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/notification/FlowFuelFcmService.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add FlowFuelFcmService to receive and display push notifications"
```

---

### Task 8: Wire registration into login/activation and logout

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/auth/data/AuthRepositoryImpl.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/auth/data/AuthRepositoryImplTest.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileViewModel.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileViewModelTest.kt`

**Interfaces:**
- Consumes: `DeviceTokenRepository.registerToken(token: String)`, `DeviceTokenRepository.unregisterToken(token: String)` (Task 6).

This task deliberately does **not** call `FirebaseMessaging.getInstance().token` from a unit-testable class — see the note in Task 6, Step 1. Instead:
- `AuthRepositoryImpl.handleAuthSuccess` fetches the current FCM token (via `kotlinx-coroutines-play-services`'s `Task<T>.await()`) and calls `deviceTokenRepository.registerToken(it)`, matching the pattern already used for `sessionStore.save(...)` right below it.
- `ProfileViewModel.logout()` does the same before wiping the session, so the `DELETE /devices/:token` call still carries a valid `Authorization` header — the existing code clears `sessionStore` **before** calling `logoutUseCase()` (`ProfileViewModel.kt:112-116`), so the new device-unregister call must run even earlier, as the very first action in `logout()`, or the `AuthInterceptor` will have no access token left to attach.

- [ ] **Step 1: Write the failing test for login registering the device token**

In `app/src/test/java/com/flowfuel/app/feature/auth/data/AuthRepositoryImplTest.kt`, add the import `com.flowfuel.app.core.notification.domain.DeviceTokenRepository` and a new mock, update the `setUp()` constructor call, and add a test:

```kotlin
    private val deviceTokenRepository: DeviceTokenRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        repository = AuthRepositoryImpl(api, sessionStore, deviceTokenRepository)
    }
```

(This replaces the existing 2-arg `AuthRepositoryImpl(api, sessionStore)` call and the `@Before` block above it.)

```kotlin
    @Test
    fun `login success registers current device token`() = runTest {
        coEvery { api.login(any()) } returns authResponse()
        coEvery { deviceTokenRepository.registerToken(any()) } returns AppResult.Success(Unit)

        repository.login("user@example.com", "Senha@123")

        coVerify { deviceTokenRepository.registerToken(any()) }
    }
```

Add this test in the `// ── login ──` section, and an analogous one in the `// ── activate ──` section:

```kotlin
    @Test
    fun `activate success registers current device token`() = runTest {
        coEvery { api.activate(any()) } returns authResponse()
        coEvery { deviceTokenRepository.registerToken(any()) } returns AppResult.Success(Unit)

        repository.activate("plain-token")

        coVerify { deviceTokenRepository.registerToken(any()) }
    }
```

Note: with `deviceTokenRepository` mocked via MockK (not a real `FirebaseMessaging` call), these tests exercise the repository call directly — the real implementation obtains the token from Firebase internally (Step 3 below), which MockK bypasses entirely in these unit tests.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.auth.data.AuthRepositoryImplTest"`
Expected: FAIL — compile error, `AuthRepositoryImpl` doesn't yet accept a 3rd constructor argument.

- [ ] **Step 3: Update `AuthRepositoryImpl`**

In `app/src/main/java/com/flowfuel/app/feature/auth/data/AuthRepositoryImpl.kt`, add imports and the new constructor parameter, then call it inside `handleAuthSuccess`:

```kotlin
import com.flowfuel.app.core.notification.domain.DeviceTokenRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
```

```kotlin
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val sessionStore: SessionStore,
    private val deviceTokenRepository: DeviceTokenRepository,
) : AuthRepository {

    private suspend fun handleAuthSuccess(dto: AuthResponseDto): AppResult<Unit> {
        val userId = dto.user?.id?.toString() ?: userIdFromJwt(dto.accessToken)
        if (userId.isBlank()) return AppResult.Failure(AppError.Unknown())
        sessionStore.save(
            accessToken  = dto.accessToken,
            refreshToken = dto.refreshToken,
            userId       = userId,
            userName     = dto.user?.name,
            userEmail    = dto.user?.email,
        )
        runCatching { FirebaseMessaging.getInstance().token.await() }
            .getOrNull()
            ?.let { deviceTokenRepository.registerToken(it) }
        return AppResult.Success(Unit)
    }
```

The `runCatching` wraps the Firebase call because a missing/outdated Google Play Services (already called out in the spec as an accepted failure mode) must not block login — `handleAuthSuccess` still returns `AppResult.Success(Unit)` either way.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.auth.data.AuthRepositoryImplTest"`
Expected: `BUILD SUCCESSFUL`, all tests passed (existing + 2 new).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/auth/data/AuthRepositoryImpl.kt app/src/test/java/com/flowfuel/app/feature/auth/data/AuthRepositoryImplTest.kt
git commit -m "feat: register FCM device token on login and account activation"
```

- [ ] **Step 6: Write the failing test for logout unregistering the device token**

In `app/src/test/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileViewModelTest.kt`, add the import `com.flowfuel.app.core.notification.domain.DeviceTokenRepository` and a new mock, update `createViewModel()`, and add a test:

```kotlin
    private val deviceTokenRepository: DeviceTokenRepository = mockk(relaxed = true)

    private fun createViewModel(): ProfileViewModel {
        coEvery { getProfile() } returns AppResult.Success(fixtureProfile)
        coEvery { getProfileStats() } returns ProfileStats(vehiclesCount = 0, refuelsCount = 0, eventsCount = 0)
        return ProfileViewModel(
            getProfile,
            getProfileStats,
            logoutUseCase,
            sessionStore,
            uploadProfilePicture,
            deleteProfilePicture,
            deleteAccount,
            deviceTokenRepository,
        )
    }
```

```kotlin
    @Test
    fun `logout unregisters device token before clearing the session`() {
        val viewModel = createViewModel()

        viewModel.logout()

        coVerifyOrder {
            deviceTokenRepository.unregisterToken(any())
            sessionStore.clear()
        }
    }
```

Add the import `io.mockk.coVerifyOrder` alongside the existing `io.mockk.coEvery`/`io.mockk.mockk` imports.

- [ ] **Step 7: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.auth.presentation.profile.ProfileViewModelTest"`
Expected: FAIL — compile error, `ProfileViewModel` doesn't yet accept an 8th constructor argument.

- [ ] **Step 8: Update `ProfileViewModel`**

In `app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileViewModel.kt`, add the import `com.flowfuel.app.core.notification.domain.DeviceTokenRepository` and `com.google.firebase.messaging.FirebaseMessaging`, plus `kotlinx.coroutines.tasks.await`, then:

```kotlin
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getProfile: GetProfileUseCase,
    private val getProfileStats: GetProfileStatsUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val sessionStore: SessionStore,
    private val uploadProfilePicture: UploadProfilePictureUseCase,
    private val deleteProfilePicture: DeleteProfilePictureUseCase,
    private val deleteAccount: DeleteAccountUseCase,
    private val deviceTokenRepository: DeviceTokenRepository,
) : ViewModel() {
```

```kotlin
    fun logout() {
        val current = _state.value
        if (current is ProfileUiState.Content) {
            _state.update { current.copy(isLoggingOut = true) }
        }
        viewModelScope.launch {
            runCatching { FirebaseMessaging.getInstance().token.await() }
                .getOrNull()
                ?.let { deviceTokenRepository.unregisterToken(it) }
            sessionStore.clear()
            logoutUseCase()
            _effects.send(ProfileEffect.NavigateToLogin)
        }
    }
```

- [ ] **Step 9: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.auth.presentation.profile.ProfileViewModelTest"`
Expected: `BUILD SUCCESSFUL`, both tests passed (existing + new).

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileViewModel.kt app/src/test/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileViewModelTest.kt
git commit -m "feat: unregister FCM device token on logout before session clear"
```

---

### Task 9: Manual end-to-end verification

**Files:** none (verification only)

This is the "verificação manual ponta a ponta" the approved spec calls for — automating a real push round-trip isn't worth the setup cost at this scope. The backend's `POST /devices`/`DELETE /devices/{token}` endpoints and `sendPushToUser` are already implemented in the `flowfuel` (Spring Boot) repository — it just needs to be running and reachable from the debug build's `API_BASE_URL` (`app/build.gradle.kts:51`, default `http://10.0.2.2:8090/api/v1/` for the emulator) with `flowfuel.push.enabled=true` for a real push round-trip in Step 3.

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`, all tests passing (existing suite + all tests added in Tasks 3, 6, 8).

- [ ] **Step 2: Install a debug build and log in**

Build and install on an emulator or device with Google Play Services (`./gradlew :app:installDebug`), log in with a test account. Confirm in Logcat (filter `FlowFuel` or check the Chucker in-app HTTP inspector, already wired per `NetworkModule.kt`) that a `POST /devices` request fires after login.

- [ ] **Step 3: Send a test push and verify display + navigation**

Trigger `sendPushToUser` from the backend (or a manual Firebase Console "Send test message" to the registered token, using a data-only payload matching `{title, body, deepLink, type}`), targeting a known route, e.g. `deepLink = "flowfuel://vehicle/details/1"`. Verify:
- The notification appears in the shade with the correct channel ("Geral").
- Tapping it opens the app directly on that vehicle's details screen (not just the app's default start screen).
- With the app already in the foreground, the same push still shows in the shade without auto-navigating.

- [ ] **Step 4: Verify logout unregisters the token**

Log out from the Profile screen, confirm (via Chucker/Logcat) that `DELETE /devices/{token}` fires before the app navigates back to the login screen.

No commit for this task — it's verification, not a code change.
