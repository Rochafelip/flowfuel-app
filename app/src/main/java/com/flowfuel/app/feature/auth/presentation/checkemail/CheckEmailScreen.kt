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
import androidx.compose.runtime.remember
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
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
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
    val resendSentMessage = stringResource(R.string.check_email_resend_sent)
    val activationConfirmedMessage = stringResource(R.string.check_email_activation_confirmed)

    // Token vindo do magic link de ativação (flowfuel://activate?token=...) já
    // chega pré-preenchido no campo manual, evitando copiar do e-mail/log.
    LaunchedEffect(initialToken) {
        if (initialToken.isNotBlank()) viewModel.onActivationTokenChange(initialToken)
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

            Spacer(Modifier.height(FFTheme.spacing.sm))

            Text(
                text = stringResource(R.string.check_email_instruction),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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

            Spacer(Modifier.height(FFTheme.spacing.xl))

            Text(
                text = stringResource(R.string.check_email_manual_token_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
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
                variant = FFButtonVariant.Text,
                onClick = viewModel::activateWithToken,
                enabled = state.activationToken.isNotBlank() && !state.isActivating,
                loading = state.isActivating,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
