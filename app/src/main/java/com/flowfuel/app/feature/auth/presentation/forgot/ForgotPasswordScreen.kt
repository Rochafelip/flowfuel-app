package com.flowfuel.app.feature.auth.presentation.forgot

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.R
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.components.FFTextField
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onGoToResetPassword: (String) -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar apenas para erros — sucesso tem estado visual próprio
    val errorMessage = state.error?.userMessage()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(FFSnackbarVisuals(errorMessage, FFSnackbarKind.Error))
        }
    }

    Scaffold(
        topBar = { FFTopBar(title = "", onBack = onBack) },
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
    ) { padding ->
        // Transição suave entre formulário e estado de sucesso
        AnimatedContent(
            targetState = state.sent,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "forgot_content",
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) { sent ->
            if (sent) {
                ForgotPasswordSuccessContent(
                    onBackToLogin = onBack,
                    onHaveToken = { onGoToResetPassword(state.email) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ForgotPasswordFormContent(
                    state = state,
                    onEmailChange = viewModel::onEmailChange,
                    onSubmit = viewModel::submit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ─── Formulário ───────────────────────────────────────────────────────────────

@Composable
private fun ForgotPasswordFormContent(
    state: ForgotUiState,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .imePadding()
            .padding(horizontal = FFTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
    ) {
        Text(
            text = stringResource(R.string.auth_forgot_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.auth_forgot_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FFTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.auth_email),
            leadingIcon = Icons.Outlined.Email,
            errorText = if (state.emailError) stringResource(R.string.error_email_invalid) else null,
            enabled = !state.isSubmitting,
        )
        FFButton(
            text = if (state.rateLimitCooldown > 0)
                stringResource(R.string.auth_submit_cooldown, state.rateLimitCooldown)
            else
                stringResource(R.string.auth_forgot_cta),
            onClick = onSubmit,
            enabled = state.email.isNotBlank() && !state.isSubmitting && state.rateLimitCooldown == 0,
            loading = state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─── Estado de sucesso ────────────────────────────────────────────────────────

@Composable
private fun ForgotPasswordSuccessContent(
    onBackToLogin: () -> Unit,
    onHaveToken: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = FFTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Ícone principal
        Icon(
            imageVector = Icons.Outlined.MarkEmailRead,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(FFTheme.spacing.lg))

        // Título
        Text(
            text = stringResource(R.string.auth_forgot_success_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )

        Spacer(Modifier.height(FFTheme.spacing.sm))

        // Descrição
        Text(
            text = stringResource(R.string.auth_forgot_sent),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(FFTheme.spacing.xl))

        // CTA
        FFButton(
            text = stringResource(R.string.auth_forgot_success_cta),
            onClick = onBackToLogin,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(FFTheme.spacing.sm))

        FFButton(
            text = stringResource(R.string.auth_forgot_have_token_cta),
            variant = FFButtonVariant.Text,
            onClick = onHaveToken,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
