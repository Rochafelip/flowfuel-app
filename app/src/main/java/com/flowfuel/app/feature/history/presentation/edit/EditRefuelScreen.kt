package com.flowfuel.app.feature.history.presentation.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.core.designsystem.components.CurrencyBrlVisualTransformation
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFNumberField
import com.flowfuel.app.core.designsystem.components.FFNumberKind
import com.flowfuel.app.core.designsystem.components.FFTextField
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.components.FFTopBarVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.feature.home.presentation.RefuelFormState
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EditRefuelScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditRefuelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                EditRefuelEffect.Saved -> onSaved()
            }
        }
    }

    val submitErrorMessage = state.submitError?.userMessage()
    LaunchedEffect(submitErrorMessage) {
        if (submitErrorMessage != null) {
            snackbarHostState.showSnackbar(submitErrorMessage)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FFTopBar(
                title   = "Editar Abastecimento",
                variant = FFTopBarVariant.Small,
                onBack  = onBack,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val s = state.screenState) {
                EditRefuelScreenState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                is EditRefuelScreenState.Error -> FFErrorState(
                    message  = s.error.userMessage(),
                    onRetry  = viewModel::load,
                    modifier = Modifier.align(Alignment.Center),
                )

                EditRefuelScreenState.Ready -> EditRefuelContent(
                    state              = state,
                    onOdometerChange   = viewModel::onOdometerChange,
                    onLitersChange     = viewModel::onLitersChange,
                    onTotalPriceInput  = viewModel::onTotalPriceInput,
                    onFullTankToggle   = viewModel::onFullTankToggle,
                    onRefuelTypeChange = viewModel::onRefuelTypeChange,
                    onSubmit           = viewModel::submit,
                )
            }
        }
    }
}

@Composable
private fun EditRefuelContent(
    state: EditRefuelUiState,
    onOdometerChange: (String) -> Unit,
    onLitersChange: (String) -> Unit,
    onTotalPriceInput: (String) -> Unit,
    onFullTankToggle: (Boolean) -> Unit,
    onRefuelTypeChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val form = state.form
    val isHybrid   = state.vehicleEnergyType.equals("HYBRID", ignoreCase = true)
    val isElectric = state.vehicleEnergyType.equals("ELECTRIC", ignoreCase = true)
    val effectiveElectric = isElectric || (isHybrid && form.refuelType == "ELECTRIC")

    val quantityLabel = if (effectiveElectric) "kWh carregados" else "Litros abastecidos"
    val quantityError = if (effectiveElectric) "Informe a quantidade de kWh" else "Informe a quantidade de litros"
    // form.totalPriceRaw é o valor TOTAL pago (não o preço por unidade) — ver
    // mesmo bug já corrigido em QuickRefuelBottomSheet.kt.
    val priceLabel    = "Valor total pago"

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(FFTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        ) {
            FFNumberField(
                value        = form.odometer,
                onValueChange = onOdometerChange,
                label        = "Odômetro (km)",
                kind         = FFNumberKind.Odometer,
                errorText    = if (form.odometerError) "Informe a leitura do odômetro" else null,
                imeAction    = ImeAction.Next,
            )

            FFNumberField(
                value         = form.liters,
                onValueChange = onLitersChange,
                label         = quantityLabel,
                kind          = FFNumberKind.Decimal,
                errorText     = if (form.litersError) quantityError else null,
                helper        = "Use vírgula ou ponto como separador decimal",
                imeAction     = ImeAction.Next,
            )

            FFTextField(
                value               = form.totalPriceRaw,
                onValueChange       = onTotalPriceInput,
                label               = priceLabel,
                keyboardType        = KeyboardType.Number,
                visualTransformation = CurrencyBrlVisualTransformation(),
                errorText           = if (form.totalPriceError) "Informe o valor pago" else null,
                imeAction           = ImeAction.Done,
            )

            if (isHybrid) {
                HorizontalDivider(modifier = Modifier.padding(vertical = FFTheme.spacing.xs))
                Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
                    Text(
                        text  = "Tipo de abastecimento",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                        FilterChip(
                            selected = form.refuelType == "FUEL",
                            onClick  = { onRefuelTypeChange("FUEL") },
                            label    = { Text("Combustível") },
                        )
                        FilterChip(
                            selected = form.refuelType == "ELECTRIC",
                            onClick  = { onRefuelTypeChange("ELECTRIC") },
                            label    = { Text("Elétrico") },
                        )
                    }
                    if (form.refuelTypeError) {
                        Text(
                            text  = "Selecione o tipo de abastecimento",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = FFTheme.spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Tanque cheio", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text  = "Abastecimento completo até o limite",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = form.fullTank, onCheckedChange = onFullTankToggle)
            }

            if (state.submitError != null) {
                Surface(
                    color    = MaterialTheme.colorScheme.errorContainer,
                    shape    = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier  = Modifier.padding(FFTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                    ) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text  = state.submitError.userMessage(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            Spacer(Modifier.height(FFTheme.spacing.xs))
        }

        FFButton(
            text     = if (state.isSubmitting) "Salvando…" else "Salvar alterações",
            onClick  = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(FFTheme.spacing.md)
                .imePadding(),
            loading  = state.isSubmitting,
            enabled  = form.canSubmit(isHybrid),
            variant  = FFButtonVariant.Primary,
        )
    }
}
