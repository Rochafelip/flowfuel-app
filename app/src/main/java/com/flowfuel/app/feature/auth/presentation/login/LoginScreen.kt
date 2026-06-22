package com.flowfuel.app.feature.auth.presentation.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.R
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.components.FFTextField
import com.flowfuel.app.core.designsystem.preview.FFPreviewBox
import com.flowfuel.app.core.designsystem.preview.FFThemePreviews
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onGoToRegister: () -> Unit,
    onGoToForgot: () -> Unit,
    onAccountNotActivated: (email: String) -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = state.error?.userMessage()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                LoginEffect.NavigateHome -> onLoginSuccess()
                is LoginEffect.NavigateToCheckEmail -> onAccountNotActivated(effect.email)
            }
        }
    }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(FFSnackbarVisuals(errorMessage, FFSnackbarKind.Error))
        }
    }

    Scaffold(snackbarHost = { FFSnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = FFTheme.spacing.lg, vertical = FFTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_splash),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp),
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.auth_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(FFTheme.spacing.sm))
            Text(stringResource(R.string.auth_login_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.auth_login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(FFTheme.spacing.xs))

            FFTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = stringResource(R.string.auth_email),
                leadingIcon = Icons.Default.Email,
                errorText = if (state.emailError) stringResource(R.string.error_email_invalid)
                            else state.serverErrors?.firstOrNull { it.field == "email" }?.message,
                enabled = !state.isSubmitting,
            )
            FFTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(R.string.auth_password),
                leadingIcon = Icons.Default.Lock,
                isPassword = true,
                errorText = if (state.passwordError) stringResource(R.string.error_required)
                            else state.serverErrors?.firstOrNull { it.field == "password" }?.message,
                enabled = !state.isSubmitting,
            )

            FFButton(
                text = stringResource(R.string.auth_forgot),
                variant = FFButtonVariant.Text,
                onClick = onGoToForgot,
            )

            FFButton(
                text = stringResource(R.string.auth_login_cta),
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                loading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            FFButton(
                text = stringResource(R.string.auth_register_cta),
                variant = FFButtonVariant.Text,
                onClick = onGoToRegister,
            )
        }
    }
}

@FFThemePreviews
@Composable
private fun LoginScreenPreview() {
    FFPreviewBox {
        Text("LoginScreen preview — interativo requer Hilt VM")
    }
}
