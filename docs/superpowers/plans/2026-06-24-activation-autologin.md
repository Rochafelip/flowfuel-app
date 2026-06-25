# Activation Auto-Login Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Execution constraint:** if you are running from a Linux/WSL session (no Android SDK, no ADB), you can do Tasks 1-6 (code edits) directly, but **cannot** run `./gradlew test`, build the APK, or do on-device verification (Tasks 7-9). Hand those off explicitly to the user / an Android Studio (Windows) session — don't claim they passed without actually running them.

**Goal:** tapping the account-activation email link signs the user in automatically (no separate login step), reusing the deep link infrastructure that already exists.

**Architecture:** the backend already returns JWT tokens from `POST /auth/activate`; the Android app's `AuthApi`/`AuthRepositoryImpl`/`CheckEmailViewModel` currently discard that response and just show a confirmation message. Wire the response through to `SessionStore`, mirroring exactly how `login()` already does it, then change the success navigation target from "Login" to "Home" (same destination login itself navigates to).

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Retrofit/kotlinx.serialization, JUnit + MockK + kotlinx-coroutines-test (existing conventions in `AuthRepositoryImplTest`).

**Reference spec:** `docs/superpowers/specs/2026-06-24-activation-autologin-design.md`

---

## File Structure

```
Modify (backend, separate repo /home/rocha/Projetos/flowfuel):
  src/main/java/com/devappmobile/flowfuel/user/SmtpAccountActivationNotifier.java

Modify (this repo):
  app/src/main/java/com/flowfuel/app/feature/auth/data/remote/AuthApi.kt
  app/src/main/java/com/flowfuel/app/feature/auth/data/AuthRepositoryImpl.kt
  app/src/main/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailViewModel.kt
  app/src/main/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailScreen.kt
  app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt
  app/src/test/java/com/flowfuel/app/feature/auth/data/AuthRepositoryImplTest.kt

Create (this repo):
  app/src/test/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailViewModelTest.kt

Config only (no file): Fly secret ACCOUNT_ACTIVATION_LINK_BASE_URL on flowfuel-api
```

---

### Task 1: Backend — include `email` in the activation link

**Repo:** `/home/rocha/Projetos/flowfuel` (separate git repo from the Android app)

**Files:**
- Modify: `src/main/java/com/devappmobile/flowfuel/user/SmtpAccountActivationNotifier.java:49`

- [ ] **Step 1: Add the email query param**

Current code (line 49):

```java
        String link = linkBaseUrl + "?token=" + activationToken;
```

Replace with:

```java
        String link = linkBaseUrl + "?token=" + activationToken
                + "&email=" + java.net.URLEncoder.encode(user.getEmail(), java.nio.charset.StandardCharsets.UTF_8);
```

- [ ] **Step 2: Run the backend test suite**

```bash
cd /home/rocha/Projetos/flowfuel
./mvnw -o test -Dtest=AccountActivationServiceTest,UserControllerIntegrationTest
```

Expected: all tests pass (the link's exact content isn't asserted by existing tests, so this should be unaffected — confirms no regression).

- [ ] **Step 3: Commit**

```bash
cd /home/rocha/Projetos/flowfuel
git add src/main/java/com/devappmobile/flowfuel/user/SmtpAccountActivationNotifier.java
git commit -m "feat: include email in account activation link for mobile deep link"
```

---

### Task 2: Backend — point the activation link at the app's deep link scheme

**Repo:** `/home/rocha/Projetos/flowfuel` (Fly secret, no file change)

- [ ] **Step 1: Update the secret**

```bash
flyctl secrets set ACCOUNT_ACTIVATION_LINK_BASE_URL="flowfuel://activate" -a flowfuel-api
```

Expected: rolling deploy, ending in `update succeeded`.

- [ ] **Step 2: Verify**

```bash
flyctl secrets list -a flowfuel-api
```

Expected: `ACCOUNT_ACTIVATION_LINK_BASE_URL` has a new `DIGEST` value.

No commit (deployed secret, not a file).

---

### Task 3: Android — `AuthApi.activate()` returns the token pair

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/auth/data/remote/AuthApi.kt:71-73`

- [ ] **Step 1: Change the return type**

Current (lines 71-73):

```kotlin
    @Headers("No-Auth: true")
    @POST("auth/activate")
    suspend fun activate(@Body body: ActivateAccountRequestDto)
```

Replace with:

```kotlin
    @Headers("No-Auth: true")
    @POST("auth/activate")
    suspend fun activate(@Body body: ActivateAccountRequestDto): AuthResponseDto
```

(`AuthResponseDto` is already defined in this same file at lines 46-52 — no new import needed.)

- [ ] **Step 2: Compile to confirm callers need updating**

```bash
cd "/mnt/c/Users/rocha/AndroidStudioProjects/flowfuel-app"
./gradlew compileDebugKotlin --console=plain
```

Expected: **FAILS** — `AuthRepositoryImpl.activate()` (Task 4) still calls `api.activate(...)` expecting `Unit`/discarding a value that now has a return type; this is fine and expected at this point, since `apiCall { api.activate(...) }` wraps any return type generically (check: does it actually fail, or does `apiCall<T> { block: suspend () -> T }` just infer `T = AuthResponseDto` and compile fine, with `AuthRepository.activate()`'s declared return type `AppResult<Unit>` being the actual mismatch)? Run the command and read the actual compiler error before assuming which line breaks — don't guess.

- [ ] **Step 3: Commit only after Task 4 is also done (this task and Task 4 land in one commit — see Task 4 Step 4)**

Skip committing here; continue directly to Task 4.

---

### Task 4: Android — `AuthRepositoryImpl.activate()` saves the session

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/auth/data/AuthRepositoryImpl.kt:59-60`

- [ ] **Step 1: Write the failing tests first**

Add to `app/src/test/java/com/flowfuel/app/feature/auth/data/AuthRepositoryImplTest.kt`, after the existing `// ── login` test block (after line 152, before the closing `}` of the class):

```kotlin
    // ── activate (autologin via deep link) ─────────────────────────────────────

    @Test
    fun `activate success saves session from response`() = runTest {
        coEvery { api.activate(any()) } returns authResponse(42L)

        val result = repository.activate("plain-token")

        assertEquals(AppResult.Success(Unit), result)
        coVerify { sessionStore.save("access_token", "refresh_token", "42", "Felipe", "user@example.com") }
    }

    @Test
    fun `activate success saves tokens exactly once`() = runTest {
        coEvery { api.activate(any()) } returns authResponse()

        repository.activate("plain-token")

        coVerify(exactly = 1) { sessionStore.save(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `activate sends trimmed token to api`() = runTest {
        coEvery { api.activate(any()) } returns authResponse()

        repository.activate("plain-token")

        coVerify { api.activate(ActivateAccountRequestDto("plain-token")) }
    }

    @Test
    fun `activate io exception returns network failure`() = runTest {
        coEvery { api.activate(any()) } throws IOException("timeout")

        val result = repository.activate("plain-token")

        assertEquals(AppResult.Failure(AppError.Network), result)
    }

    @Test
    fun `activate failure does not save session`() = runTest {
        coEvery { api.activate(any()) } throws IOException("no connection")

        repository.activate("plain-token")

        coVerify(exactly = 0) { sessionStore.save(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `activate null user and blank jwt userId returns failure`() = runTest {
        val dtoWithoutUser = AuthResponseDto(
            user = null,
            accessToken = "invalid.jwt.token",
            refreshToken = "refresh",
        )
        coEvery { api.activate(any()) } returns dtoWithoutUser

        val result = repository.activate("plain-token")

        assertTrue(result is AppResult.Failure)
        coVerify(exactly = 0) { sessionStore.save(any(), any(), any(), any(), any()) }
    }
```

Add the missing import at the top of the test file (alongside the existing `ActivateAccountRequestDto` usage — check if it's already imported; if not, add):

```kotlin
import com.flowfuel.app.feature.auth.data.remote.ActivateAccountRequestDto
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd "/mnt/c/Users/rocha/AndroidStudioProjects/flowfuel-app"
./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.auth.data.AuthRepositoryImplTest" --console=plain
```

Expected: FAIL — `activate` currently returns `AppResult<Unit>` by directly wrapping `api.activate(...)` without touching `sessionStore`, so `coVerify { sessionStore.save(...) }` assertions fail (0 invocations).

- [ ] **Step 3: Implement**

Current code (`AuthRepositoryImpl.kt` lines 59-60):

```kotlin
    override suspend fun activate(token: String): AppResult<Unit> =
        apiCall { api.activate(ActivateAccountRequestDto(token)) }
```

Replace with (mirroring `login()`'s exact pattern at lines 27-45):

```kotlin
    override suspend fun activate(token: String): AppResult<Unit> {
        val result = apiCall { api.activate(ActivateAccountRequestDto(token)) }
        return when (result) {
            is AppResult.Success -> {
                val dto = result.value
                val userId = dto.user?.id?.toString() ?: userIdFromJwt(dto.accessToken)
                if (userId.isBlank()) return AppResult.Failure(AppError.Unknown())
                sessionStore.save(
                    accessToken  = dto.accessToken,
                    refreshToken = dto.refreshToken,
                    userId       = userId,
                    userName     = dto.user?.name,
                    userEmail    = dto.user?.email,
                )
                AppResult.Success(Unit)
            }
            is AppResult.Failure -> result
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd "/mnt/c/Users/rocha/AndroidStudioProjects/flowfuel-app"
./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.auth.data.AuthRepositoryImplTest" --console=plain
```

Expected: PASS (all tests in this file, including the 6 new ones).

- [ ] **Step 5: Commit (Tasks 3 + 4 together)**

```bash
cd "/mnt/c/Users/rocha/AndroidStudioProjects/flowfuel-app"
git add app/src/main/java/com/flowfuel/app/feature/auth/data/remote/AuthApi.kt app/src/main/java/com/flowfuel/app/feature/auth/data/AuthRepositoryImpl.kt app/src/test/java/com/flowfuel/app/feature/auth/data/AuthRepositoryImplTest.kt
git commit -m "feat: activate() saves session from response, same as login()"
```

---

### Task 5: Android — `CheckEmailViewModel` emits a logged-in effect

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailViewModel.kt`
- Create: `app/src/test/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailViewModelTest.kt`:

```kotlin
package com.flowfuel.app.feature.auth.presentation.checkemail

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auth.domain.usecase.ActivateAccountUseCase
import com.flowfuel.app.feature.auth.domain.usecase.ResendActivationUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CheckEmailViewModelTest {

    private val resendActivation: ResendActivationUseCase = mockk(relaxed = true)
    private val activateAccount: ActivateAccountUseCase = mockk()
    private lateinit var viewModel: CheckEmailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = CheckEmailViewModel(resendActivation, activateAccount)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `activateWithToken success emits ActivatedAndLoggedIn`() = runTest {
        coEvery { activateAccount(any()) } returns AppResult.Success(Unit)
        viewModel.onActivationTokenChange("plain-token")

        viewModel.activateWithToken()

        val effect = viewModel.effects.firstEffect()
        assertEquals(CheckEmailEffect.ActivatedAndLoggedIn, effect)
    }

    @Test
    fun `activateWithToken failure keeps showing error, no navigation effect`() = runTest {
        coEvery { activateAccount(any()) } returns AppResult.Failure(AppError.Unauthorized)
        viewModel.onActivationTokenChange("plain-token")

        viewModel.activateWithToken()

        assertEquals(AppError.Api("AUTH_ACTIVATION_INVALID"), viewModel.state.value.activationError)
    }
}

private suspend fun kotlinx.coroutines.flow.Flow<CheckEmailEffect>.firstEffect(): CheckEmailEffect {
    var result: CheckEmailEffect? = null
    val job = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined).launch {
        result = first()
    }
    job.join()
    return result ?: error("No effect emitted")
}
```

Note for the implementer: if this `firstEffect()` helper doesn't compile cleanly against the project's existing coroutine-test setup, check how other ViewModel tests in this codebase (if any exist outside `auth`) consume `Channel`-backed effect flows in tests — search with `grep -rn "effects.receiveAsFlow\|Turbine\|effects.first()" app/src/test` first. If a `turbine` test library is already a dependency (check `app/build.gradle.kts` for `app.cash.turbine`), prefer rewriting both tests using `viewModel.effects.test { assertEquals(...) }` instead of the manual `firstEffect()` helper above — it's cleaner and is probably already the project's convention if present. Don't add a new test library dependency without checking first.

- [ ] **Step 2: Run test to verify it fails**

```bash
cd "/mnt/c/Users/rocha/AndroidStudioProjects/flowfuel-app"
./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.auth.presentation.checkemail.CheckEmailViewModelTest" --console=plain
```

Expected: FAIL — compilation error, `CheckEmailEffect.ActivatedAndLoggedIn` doesn't exist yet.

- [ ] **Step 3: Implement**

In `app/src/main/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailViewModel.kt`:

Replace the `CheckEmailEffect` sealed interface (lines 30-34):

```kotlin
sealed interface CheckEmailEffect {
    data object NavigateToLogin : CheckEmailEffect
    data object ResendConfirmed : CheckEmailEffect
    data object ActivationConfirmed : CheckEmailEffect
}
```

with:

```kotlin
sealed interface CheckEmailEffect {
    data object NavigateToLogin : CheckEmailEffect
    data object ResendConfirmed : CheckEmailEffect
    data object ActivatedAndLoggedIn : CheckEmailEffect
}
```

Replace the success branch inside `activateWithToken()` (lines 67-71):

```kotlin
                is AppResult.Success -> {
                    _state.update { it.copy(isActivating = false) }
                    _effects.send(CheckEmailEffect.ActivationConfirmed)
                }
```

with:

```kotlin
                is AppResult.Success -> {
                    _state.update { it.copy(isActivating = false) }
                    _effects.send(CheckEmailEffect.ActivatedAndLoggedIn)
                }
```

(`ActivationConfirmed` is fully removed — there is no other use of it in this file. `CheckEmailScreen.kt` still references it; that's fixed in Task 6, which must land in the same commit or the project won't compile.)

- [ ] **Step 4: Run test to verify it passes**

This will still fail to compile until Task 6 updates `CheckEmailScreen.kt` (same file tree, same module). Proceed to Task 6 before running this verification step again.

---

### Task 6: Android — wire `onNavigateHome` through the screen and nav host

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailScreen.kt`
- Modify: `app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt:199-209`

- [ ] **Step 1: Add the new callback parameter to `CheckEmailScreen`**

Current signature (`CheckEmailScreen.kt` lines 46-52):

```kotlin
@Composable
fun CheckEmailScreen(
    email: String,
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    initialToken: String = "",
    viewModel: CheckEmailViewModel = hiltViewModel(),
) {
```

Replace with:

```kotlin
@Composable
fun CheckEmailScreen(
    email: String,
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateHome: () -> Unit,
    initialToken: String = "",
    viewModel: CheckEmailViewModel = hiltViewModel(),
) {
```

- [ ] **Step 2: Handle the new effect**

Current effect handling (lines 64-80):

```kotlin
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                CheckEmailEffect.NavigateToLogin -> onNavigateToLogin()
                CheckEmailEffect.ResendConfirmed ->
                    snackbarHostState.showSnackbar(
                        FFSnackbarVisuals(resendSentMessage, FFSnackbarKind.Info)
                    )
                CheckEmailEffect.ActivationConfirmed -> {
                    snackbarHostState.showSnackbar(
                        FFSnackbarVisuals(activationConfirmedMessage, FFSnackbarKind.Success)
                    )
                    onNavigateToLogin()
                }
            }
        }
    }
```

Replace with:

```kotlin
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                CheckEmailEffect.NavigateToLogin -> onNavigateToLogin()
                CheckEmailEffect.ResendConfirmed ->
                    snackbarHostState.showSnackbar(
                        FFSnackbarVisuals(resendSentMessage, FFSnackbarKind.Info)
                    )
                CheckEmailEffect.ActivatedAndLoggedIn -> {
                    snackbarHostState.showSnackbar(
                        FFSnackbarVisuals(activationConfirmedMessage, FFSnackbarKind.Success)
                    )
                    onNavigateHome()
                }
            }
        }
    }
```

(`activationConfirmedMessage` — the `stringResource` variable name — is unchanged; only which navigation callback fires after showing it changes.)

- [ ] **Step 3: Update the nav host wiring**

Current (`FlowFuelNavHost.kt` lines 199-209):

```kotlin
            CheckEmailScreen(
                email = email,
                initialToken = token,
                onBack = { navController.popBackStack() },
                onNavigateToLogin = {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(Destinations.LOGIN) { inclusive = true }
                    }
                },
            )
```

Replace with:

```kotlin
            CheckEmailScreen(
                email = email,
                initialToken = token,
                onBack = { navController.popBackStack() },
                onNavigateToLogin = {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(Destinations.LOGIN) { inclusive = true }
                    }
                },
                onNavigateHome = {
                    navController.navigate(Destinations.VEHICLE_PICKER) {
                        popUpTo(Destinations.LOGIN) { inclusive = true }
                    }
                },
            )
```

(This is the exact same navigation call used by `LoginScreen`'s `onLoginSuccess` at lines 160-164 — same destination, same `popUpTo` behavior, so the post-activation experience is identical to a normal successful login.)

- [ ] **Step 4: Run all the auth-related unit tests**

```bash
cd "/mnt/c/Users/rocha/AndroidStudioProjects/flowfuel-app"
./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.auth.*" --console=plain
```

Expected: PASS (all of `AuthRepositoryImplTest` and the new `CheckEmailViewModelTest`).

- [ ] **Step 5: Compile the full app to catch any other reference to the removed `ActivationConfirmed`**

```bash
cd "/mnt/c/Users/rocha/AndroidStudioProjects/flowfuel-app"
./gradlew compileDebugKotlin --console=plain
```

Expected: BUILD SUCCESSFUL. If it fails referencing `ActivationConfirmed` somewhere else, search with `grep -rn "ActivationConfirmed" app/src` and update that reference too — there should be none left after Tasks 5-6, but verify rather than assume.

- [ ] **Step 6: Commit (Tasks 5 + 6 together)**

```bash
cd "/mnt/c/Users/rocha/AndroidStudioProjects/flowfuel-app"
git add app/src/main/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailViewModel.kt app/src/main/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailScreen.kt app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt app/src/test/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailViewModelTest.kt
git commit -m "feat: activation success signs the user in instead of returning to login"
```

---

### Task 7: Full unit test suite + release build

**Execution constraint:** requires Windows-side Gradle/Android SDK — run from Android Studio's terminal or a Windows shell, not from a Linux/WSL session without SDK access.

- [ ] **Step 1: Run the full unit test suite**

```bash
cd "/mnt/c/Users/rocha/AndroidStudioProjects/flowfuel-app"
./gradlew test --console=plain
```

Expected: BUILD SUCCESSFUL, no failing tests anywhere in the module (not just the auth feature).

- [ ] **Step 2: Build the release APK**

```bash
./gradlew assembleRelease --console=plain
```

Expected: BUILD SUCCESSFUL, produces `app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 3: Install on the test device/emulator**

```bash
export ANDROID_HOME=/c/Users/rocha/AppData/Local/Android/Sdk
export PATH="$ANDROID_HOME/platform-tools:$PATH"
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

### Task 8: Manual end-to-end verification

**Execution constraint:** requires a real device or emulator with the release build installed (Task 7) and a real, checkable email inbox. Cannot be done from this session.

- [ ] **Step 1: Register a fresh test account in the app**

Confirm it lands on `CheckEmailScreen` (unchanged behavior).

- [ ] **Step 2: Open the activation email and tap the link/button**

Confirm:
- The link is now `flowfuel://activate?token=...&email=...` (from Tasks 1-2) — check the email body if curious, or just confirm the app opens.
- The app opens directly into `CheckEmailScreen` with the token pre-filled (already working per the prior session's deep-link work).
- Tap "Ativar minha conta" (the manual-token-confirm button) if it doesn't auto-submit — confirm: **the app navigates straight to the vehicle picker / home, not back to login.**

- [ ] **Step 3: Test the reuse case**

Activate the same token a second time (re-tap the email link, or re-enter the same token manually). Confirm the existing error UI still shows (`AUTH_ACTIVATION_INVALID`), unchanged from before this plan.

- [ ] **Step 4: Confirm normal login still works**

Log out, log back in with an existing active account's email/password. Confirm it still lands on the vehicle picker/home as before (this plan didn't touch `LoginViewModel`, so this is a regression check, not a new behavior).

- [ ] **Step 5: Report back**

Note whether tapping the link from the actual email client used opens the app directly (validates/refutes the known `flowfuel://` custom-scheme-in-email risk noted in the design spec — same category of risk as the SMTP sender domain issue, deferred until a verified domain exists for Android App Links).

---

## Self-Review Notes (for the implementer)

- `AuthApi.activate()`'s new return type (`AuthResponseDto`) reuses the existing DTO — no new DTO class needed, since the backend's `/auth/activate` response shape is identical to `/auth/login`'s (`TokenPairResponse` on the backend side, both `{accessToken, refreshToken, expiresIn}` — the backend doesn't include a `user` object in either response today, so `dto.user` will be `null` and `userIdFromJwt()` is the actual path taken in practice, not just a fallback for an edge case).
- Task 5's test file introduces a manual `firstEffect()` helper as a fallback — check for `turbine` first, as noted inline, to avoid reinventing an existing pattern.
- Tasks 3+4 must land together (one doesn't compile without the other). Same for Tasks 5+6.
- Task 1 (backend) is a separate git repo (`/home/rocha/Projetos/flowfuel`) from Tasks 3-6 (`flowfuel-app`, this repo) — don't try to commit them together.
