package com.flowfuel.app.feature.auth.presentation.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.R
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.components.FFTextField
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import kotlinx.coroutines.flow.collectLatest

@Composable
fun RegisterScreen(
    onRegisterSuccess: (email: String) -> Unit,
    onBack: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = state.error?.userMessage()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest {
            when (it) {
                is RegisterEffect.NavigateToCheckEmail -> onRegisterSuccess(it.email)
            }
        }
    }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(FFSnackbarVisuals(errorMessage, FFSnackbarKind.Error))
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
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = FFTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
        ) {
            Text(stringResource(R.string.auth_register_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.auth_register_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FFTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = stringResource(R.string.auth_name),
                leadingIcon = Icons.Default.Person,
                errorText = if (state.nameError) stringResource(R.string.error_required)
                            else state.serverErrors?.firstOrNull { it.field == "name" }?.message,
                enabled = !state.isSubmitting,
            )
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
                value = state.phone,
                onValueChange = viewModel::onPhoneChange,
                label = stringResource(R.string.auth_phone),
                leadingIcon = Icons.Default.Phone,
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next,
                errorText = if (state.phoneError) stringResource(R.string.error_phone_invalid)
                            else state.serverErrors?.firstOrNull { it.field == "phone" }?.message,
                helper = "Ex: +5511999999999",
                enabled = !state.isSubmitting,
            )
            FFTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(R.string.auth_password),
                leadingIcon = Icons.Default.Lock,
                isPassword = true,
                imeAction = ImeAction.Next,
                errorText = if (state.passwordError) stringResource(R.string.error_password_short)
                            else state.serverErrors?.firstOrNull { it.field == "password" }?.message,
                enabled = !state.isSubmitting,
            )
            FFTextField(
                value = state.confirmPassword,
                onValueChange = viewModel::onConfirmChange,
                label = stringResource(R.string.auth_password_confirm),
                leadingIcon = Icons.Default.Lock,
                isPassword = true,
                imeAction = ImeAction.Done,
                errorText = if (state.confirmError) stringResource(R.string.error_password_mismatch) else null,
                enabled = !state.isSubmitting,
            )

            FFButton(
                text = stringResource(R.string.auth_register_cta),
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                loading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth().padding(top = FFTheme.spacing.sm),
            )
        }
    }
}
