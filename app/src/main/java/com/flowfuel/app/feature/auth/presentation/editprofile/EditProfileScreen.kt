package com.flowfuel.app.feature.auth.presentation.editprofile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.R
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFDialog
import com.flowfuel.app.core.designsystem.components.FFDialogKind
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.components.FFTextField
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.components.FFTopBarVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import kotlinx.coroutines.flow.collectLatest

// ─── Tela de edição de perfil ─────────────────────────────────────────────────

@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMsg = stringResource(R.string.profile_updated_success)

    BackHandler(enabled = state.screenState is EditProfileScreenState.Editing) {
        viewModel.onBackPressed()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                EditProfileEffect.NavigateBack -> onBack()
                EditProfileEffect.NavigateBackAfterSave -> {
                    snackbarHostState.showSnackbar(
                        FFSnackbarVisuals(savedMsg, FFSnackbarKind.Success)
                    )
                    onSaved()
                }
                EditProfileEffect.NavigateToLogin -> onNavigateToLogin()
            }
        }
    }

    val formErrorMsg = state.formError?.userMessage()
    LaunchedEffect(formErrorMsg) {
        if (formErrorMsg != null) {
            snackbarHostState.showSnackbar(FFSnackbarVisuals(formErrorMsg, FFSnackbarKind.Error))
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            FFTopBar(
                title   = stringResource(R.string.profile_edit_title),
                variant = FFTopBarVariant.Large,
                onBack  = viewModel::onBackPressed,
            )
        },
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = state.screenState is EditProfileScreenState.Editing,
                enter   = fadeIn(tween(200)),
                exit    = fadeOut(tween(200)),
            ) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    FFButton(
                        text    = stringResource(R.string.profile_edit_cta),
                        onClick = viewModel::submit,
                        enabled = state.canSubmit,
                        loading = state.isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FFTheme.spacing.md, vertical = FFTheme.spacing.sm)
                            .navigationBarsPadding(),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val s = state.screenState) {
                EditProfileScreenState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
                is EditProfileScreenState.Error -> FFErrorState(
                    message = s.error.userMessage(),
                    onRetry = viewModel::load,
                    modifier = Modifier.align(Alignment.Center),
                )
                EditProfileScreenState.Editing -> EditProfileForm(
                    state         = state,
                    onNameChange  = viewModel::onNameChange,
                    onPhoneChange = viewModel::onPhoneChange,
                )
            }
        }
    }

    if (state.showDiscardDialog) {
        FFDialog(
            title       = stringResource(R.string.profile_discard_title),
            message     = stringResource(R.string.profile_discard_message),
            confirmText = stringResource(R.string.profile_discard_confirm),
            onConfirm   = viewModel::onDiscardConfirm,
            onDismiss   = viewModel::onDiscardDismiss,
            kind        = FFDialogKind.Destructive,
        )
    }
}

// ─── Formulário ───────────────────────────────────────────────────────────────

@Composable
private fun EditProfileForm(
    state: EditProfileUiState,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(FFTheme.spacing.md)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
    ) {
        FFTextField(
            value         = state.name,
            onValueChange = onNameChange,
            label         = stringResource(R.string.profile_field_name),
            errorText     = if (state.nameError) stringResource(R.string.profile_name_error)
                            else state.serverErrors?.firstOrNull { it.field == "name" }?.message,
            imeAction     = ImeAction.Next,
        )
        FFTextField(
            value         = state.phone,
            onValueChange = onPhoneChange,
            label         = stringResource(R.string.profile_field_phone),
            keyboardType  = KeyboardType.Phone,
            imeAction     = ImeAction.Done,
            errorText     = state.serverErrors?.firstOrNull { it.field == "phone" }?.message,
        )
    }
}
