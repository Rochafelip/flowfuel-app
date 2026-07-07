package com.flowfuel.app.feature.auth.presentation.resetpassword

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.R
import com.flowfuel.app.core.designsystem.components.FFButton
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
fun ResetPasswordScreen(
    email: String,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: ResetPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                ResetPasswordEffect.Success -> onSuccess()
            }
        }
    }

    val errorMessage = state.error?.userMessage()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(FFSnackbarVisuals(errorMessage, FFSnackbarKind.Error))
        }
    }

    Scaffold(
        topBar = { FFTopBar(title = stringResource(R.string.reset_password_title), onBack = onBack) },
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = FFTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
        ) {
            Text(
                text = stringResource(R.string.reset_password_subtitle, email),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FFInfoBanner(text = stringResource(R.string.spam_folder_notice))
            FFTextField(
                value = state.token,
                onValueChange = viewModel::onTokenChange,
                label = stringResource(R.string.reset_password_token),
                errorText = if (state.tokenError) stringResource(R.string.error_required) else null,
                enabled = !state.isSubmitting,
                imeAction = ImeAction.Next,
            )
            FFTextField(
                value = state.newPassword,
                onValueChange = viewModel::onNewPasswordChange,
                label = stringResource(R.string.reset_password_new),
                isPassword = true,
                errorText = if (state.newError) stringResource(R.string.error_password_short) else null,
                enabled = !state.isSubmitting,
                imeAction = ImeAction.Next,
            )
            FFTextField(
                value = state.confirmPassword,
                onValueChange = viewModel::onConfirmChange,
                label = stringResource(R.string.reset_password_confirm),
                isPassword = true,
                errorText = if (state.confirmError) stringResource(R.string.error_password_mismatch) else null,
                enabled = !state.isSubmitting,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
            )
            FFButton(
                text = stringResource(R.string.reset_password_cta),
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                loading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
