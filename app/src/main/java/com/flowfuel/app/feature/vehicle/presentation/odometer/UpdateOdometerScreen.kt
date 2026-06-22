package com.flowfuel.app.feature.vehicle.presentation.odometer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.R
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFNumberField
import com.flowfuel.app.core.designsystem.components.FFNumberKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import kotlinx.coroutines.flow.collectLatest

@Composable
fun UpdateOdometerScreen(
    onBack: () -> Unit,
    onSaved: (newKm: Int) -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: UpdateOdometerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is UpdateOdometerEffect.NavigateBackWithResult -> {
                    snackbarHostState.showSnackbar(
                        FFSnackbarVisuals(
                            message = "Odômetro atualizado para ${"%,d".format(effect.updatedKm)} km",
                            kind = FFSnackbarKind.Success,
                        )
                    )
                    onSaved(effect.updatedKm)
                }
                UpdateOdometerEffect.NavigateToLogin -> onNavigateToLogin()
            }
        }
    }

    val errorMsg = state.formError?.userMessage()
    LaunchedEffect(errorMsg) {
        if (errorMsg != null) {
            snackbarHostState.showSnackbar(FFSnackbarVisuals(errorMsg, FFSnackbarKind.Error))
            viewModel.clearError()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            FFTopBar(
                title = stringResource(R.string.vehicle_odometer_update_title),
                onBack = onBack,
            )
        },
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val currentKmLabel = "%,d km".format(state.currentKm)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = FFTheme.spacing.md, vertical = FFTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.lg),
        ) {
            Text(
                text = "KM atual: $currentKmLabel",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics {
                    contentDescription = "Quilometragem atual: $currentKmLabel"
                },
            )

            FFNumberField(
                value = state.newKm,
                onValueChange = viewModel::onNewKmChange,
                label = stringResource(R.string.vehicle_odometer_new_km_label),
                kind = FFNumberKind.Odometer,
                errorText = if (state.regressionError) {
                    stringResource(R.string.vehicle_odometer_regression_error)
                } else null,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { viewModel.confirm() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )

            Spacer(Modifier.height(FFTheme.spacing.xs))

            FFButton(
                text = stringResource(R.string.vehicle_odometer_confirm_cta),
                onClick = viewModel::confirm,
                enabled = state.canConfirm,
                loading = state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
