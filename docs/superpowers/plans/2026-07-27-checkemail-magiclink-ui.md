# CheckEmailScreen Magic-Link UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify `CheckEmailScreen` so the primary path is "open the magic-link email, tap once, done" — the always-visible manual code field goes behind a secondary fallback, the spam warning becomes the main instruction, and opening the `flowfuel://activate` deep link activates the account without any extra tap.

**Architecture:** Pure UI/Composable change to `CheckEmailScreen.kt` plus string resource updates. `CheckEmailViewModel` keeps its existing public surface (`state`, `effects`, `resend()`, `onActivationTokenChange()`, `activateWithToken()`, `onAlreadyConfirmed()`) — nothing in it changes. The screen orchestrates two extra things: it calls `activateWithToken()` right after pre-filling the token from a deep link (instead of only pre-filling), and it surfaces `state.activationError` as a snackbar in addition to the existing inline field error, since the field can now be hidden.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Hilt (`hiltViewModel()`), existing FlowFuel design system (`FFButton`, `FFTextField`, `FFInfoBanner`, `FFBottomSheet`, `FFSnackbarHost`).

## Global Constraints

- No changes to `CheckEmailViewModel.kt`, `AuthApi.kt`, `AuthRepositoryImpl.kt`, `ActivateAccountUseCase.kt`, `ResendActivationUseCase.kt`, `FlowFuelNavHost.kt`, or any backend/deep-link routing.
- No changes to `CheckEmailViewModelTest.kt` — it must keep passing unmodified since the ViewModel's behavior doesn't change.
- All user-facing copy is in Brazilian Portuguese, matching the existing strings in `app/src/main/res/values/strings.xml`.
- Follow the existing FlowFuel design-system components (`FFButton`, `FFTextField`, `FFInfoBanner`, `FFBottomSheet`) rather than raw Material3 widgets, matching how the rest of the screen (and `QuickRefuelBottomSheet`/`MainContainerScreen`) already uses them.

---

### Task 1: Update activation-screen strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml:62-74`

**Interfaces:**
- Produces: `R.string.spam_folder_notice` (content changes, key unchanged, still consumed by `FFInfoBanner` in `CheckEmailScreen`), `R.string.check_email_manual_entry_link` (new key, consumed by Task 2 as the label of the "fallback" text button). `R.string.check_email_instruction` is removed — Task 2 must not reference it.

- [ ] **Step 1: Remove `check_email_instruction` and update `spam_folder_notice`**

In `app/src/main/res/values/strings.xml`, delete this line (it's redundant with the new spam-focused banner text):

```xml
    <string name="check_email_instruction">Clique no link no e-mail para ativar sua conta e volte aqui para entrar.</string>
```

Replace this line:

```xml
    <string name="spam_folder_notice">Não encontrou o e-mail? Verifique também a caixa de spam ou lixo eletrônico.</string>
```

with:

```xml
    <string name="spam_folder_notice">Abra o e-mail e toque no botão de ativação. Não encontrou? Verifique a caixa de spam ou lixo eletrônico.</string>
```

- [ ] **Step 2: Add the new fallback-link string**

Immediately after the `check_email_manual_token_cta` line, add:

```xml
    <string name="check_email_manual_entry_link">Problemas para ativar?</string>
```

- [ ] **Step 3: Confirm no other file still references the removed string**

Run: `grep -rn "check_email_instruction" app/src`
Expected: no output (Task 2 hasn't been applied yet, so `CheckEmailScreen.kt` will still show one match — that's expected and gets removed in Task 2. If this grep returns matches anywhere else, stop and investigate before continuing).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "$(cat <<'EOF'
feat(auth): update check-email strings for spam-focused magic-link copy

Folds the old instruction string into spam_folder_notice and adds a
label for the new manual-entry fallback link.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Restructure `CheckEmailScreen` — magic link primary, manual entry as fallback sheet

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailScreen.kt` (full rewrite of the file)

**Interfaces:**
- Consumes: `CheckEmailViewModel.state: StateFlow<CheckEmailUiState>` (fields: `isResending`, `cooldownSeconds`, `resendError`, `activationToken`, `isActivating`, `activationError`, `canResend`), `CheckEmailViewModel.effects: Flow<CheckEmailEffect>` (`NavigateToLogin`, `ResendConfirmed`, `ActivatedAndLoggedIn`), `CheckEmailViewModel.resend(email: String)`, `CheckEmailViewModel.onActivationTokenChange(v: String)`, `CheckEmailViewModel.activateWithToken()`, `CheckEmailViewModel.onAlreadyConfirmed()` — all unchanged from the current `CheckEmailViewModel.kt`. Consumes `R.string.spam_folder_notice` and `R.string.check_email_manual_entry_link` from Task 1.
- Produces: `CheckEmailScreen(email, onBack, onNavigateToLogin, onNavigateHome, initialToken, viewModel)` — same public signature as today, so `FlowFuelNavHost.kt` requires no changes.

There is no dedicated Compose UI test file for this screen today (confirmed: no `CheckEmailScreenTest` exists), so this task's verification is a compile check plus the manual emulator check in Task 3. `CheckEmailViewModelTest.kt` is untouched and must still pass since the ViewModel doesn't change.

- [ ] **Step 1: Replace the full contents of `CheckEmailScreen.kt`**

```kotlin
package com.flowfuel.app.feature.auth.presentation.checkemail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.R
import com.flowfuel.app.core.designsystem.components.FFBottomSheet
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFInfoBanner
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.components.FFTextField
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CheckEmailScreen(
    email: String,
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateHome: () -> Unit,
    initialToken: String = "",
    viewModel: CheckEmailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showManualEntry by remember { mutableStateOf(false) }
    val resendSentMessage = stringResource(R.string.check_email_resend_sent)
    val activationConfirmedMessage = stringResource(R.string.check_email_activation_confirmed)
    val resendErrorMessage = state.resendError?.userMessage()
    val activationErrorMessage = state.activationError?.userMessage()

    // Token vindo do magic link de ativação (flowfuel://activate?token=...) ativa
    // a conta automaticamente, sem exigir que o usuário abra o fallback manual.
    LaunchedEffect(initialToken) {
        if (initialToken.isNotBlank()) {
            viewModel.onActivationTokenChange(initialToken)
            viewModel.activateWithToken()
        }
    }
    LaunchedEffect(resendErrorMessage) {
        if (resendErrorMessage != null) {
            snackbarHostState.showSnackbar(FFSnackbarVisuals(resendErrorMessage, FFSnackbarKind.Error))
        }
    }
    LaunchedEffect(activationErrorMessage) {
        // Cobre o caso de ativação automática via deep link falhar (token
        // inválido/expirado) sem o bottom sheet estar aberto para mostrar o
        // erro inline. Se o sheet estiver aberto, o campo também mostra o
        // erro — redundante nesse caso, mas inofensivo.
        if (activationErrorMessage != null) {
            snackbarHostState.showSnackbar(FFSnackbarVisuals(activationErrorMessage, FFSnackbarKind.Error))
        }
    }

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

    Scaffold(
        topBar = { FFTopBar(title = "", onBack = onBack) },
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = FFTheme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Email,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(FFTheme.spacing.lg))

            Text(
                text = stringResource(R.string.check_email_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(FFTheme.spacing.sm))

            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.check_email_subtitle))
                    append(" ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(email) }
                    append(".")
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(FFTheme.spacing.md))

            FFInfoBanner(text = stringResource(R.string.spam_folder_notice))

            Spacer(Modifier.height(FFTheme.spacing.xl))

            val resendLabel = when {
                state.isResending -> stringResource(R.string.check_email_resend_loading)
                state.cooldownSeconds > 0 ->
                    stringResource(R.string.check_email_resend_cooldown, state.cooldownSeconds)
                else -> stringResource(R.string.check_email_resend)
            }

            FFButton(
                text = resendLabel,
                onClick = { viewModel.resend(email) },
                enabled = state.canResend,
                loading = state.isResending,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(FFTheme.spacing.sm))

            FFButton(
                text = stringResource(R.string.check_email_already_confirmed),
                variant = FFButtonVariant.Text,
                onClick = viewModel::onAlreadyConfirmed,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(FFTheme.spacing.lg))

            FFButton(
                text = stringResource(R.string.check_email_manual_entry_link),
                variant = FFButtonVariant.Text,
                onClick = { showManualEntry = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showManualEntry) {
        FFBottomSheet(onDismiss = { showManualEntry = false }) {
            Text(
                text = stringResource(R.string.check_email_manual_token_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(FFTheme.spacing.sm))

            FFTextField(
                value = state.activationToken,
                onValueChange = viewModel::onActivationTokenChange,
                label = stringResource(R.string.check_email_manual_token_field),
                errorText = state.activationError?.userMessage(),
                enabled = !state.isActivating,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(FFTheme.spacing.sm))

            FFButton(
                text = stringResource(R.string.check_email_manual_token_cta),
                onClick = viewModel::activateWithToken,
                enabled = state.activationToken.isNotBlank() && !state.isActivating,
                loading = state.isActivating,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
```

- [ ] **Step 2: Compile to catch type/import errors**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run the existing ViewModel test suite to confirm no regression**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.auth.presentation.checkemail.CheckEmailViewModelTest"`
Expected: `BUILD SUCCESSFUL`, 2 tests passed, 0 failures (this file is unmodified — this just confirms the ViewModel contract the screen relies on hasn't drifted).

- [ ] **Step 4: Confirm the removed string has no remaining references**

Run: `grep -rn "check_email_instruction" app/src`
Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailScreen.kt
git commit -m "$(cat <<'EOF'
feat(auth): make CheckEmailScreen magic-link-first with manual entry as fallback

Primary screen now leads with the spam-folder notice instead of manual
code entry. Opening the flowfuel://activate deep link activates the
account automatically (no extra tap). The code-paste field moves
behind a "Problemas para ativar?" bottom sheet for the case where the
email client doesn't linkify the flowfuel:// button.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Manual verification on the emulator

**Files:** none (verification only).

- [ ] **Step 1: Launch the app on the emulator**

Use the `run-android-emulator` skill (or `./gradlew :app:installDebug` followed by launching `MainActivity` manually) to get a debug build running.

- [ ] **Step 2: Verify the normal post-registration flow**

Register a new account (or trigger "esqueci minha senha"/resend from an existing pending activation, using the QA test account if one is already pending). Confirm the `CheckEmailScreen` shows, in order: email icon, title, subtitle with the email address, the spam-focused banner text ("Abra o e-mail e toque no botão de ativação. Não encontrou? Verifique a caixa de spam ou lixo eletrônico."), the "Reenviar e-mail" button, "Já confirmei → Entrar", and "Problemas para ativar?" — with **no** code field visible by default.

- [ ] **Step 3: Verify the manual fallback**

Tap "Problemas para ativar?". Confirm a bottom sheet opens with the label, the code text field, and the "Ativar com código" button — same behavior as the old always-visible block. Dismiss it (tap outside or swipe down) and confirm it closes without side effects.

- [ ] **Step 4: Verify deep-link auto-activation**

With a valid pending activation token (from the backend logs or a real email), trigger the deep link via adb:

```bash
adb shell am start -a android.intent.action.VIEW -d "flowfuel://activate?token=<TOKEN>&email=<EMAIL_URL_ENCODED>" com.flowfuel.app
```

Confirm the app opens directly on `CheckEmailScreen` and, without any additional tap, shows the "Conta ativada com sucesso. Faça login." snackbar and navigates to the vehicle picker/home — matching `CheckEmailEffect.ActivatedAndLoggedIn`.

- [ ] **Step 5: Verify deep-link failure path**

Repeat step 4 with an already-used or malformed token. Confirm the app stays on `CheckEmailScreen` and shows an error snackbar (not a silent failure, not a crash).

- [ ] **Step 6: Report results**

Note any deviations from steps 2-5 before considering this plan done. If something doesn't match, treat it as a bug to fix before moving on — do not silently accept a mismatch.
