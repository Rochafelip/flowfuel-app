package com.flowfuel.app.feature.vehicle.presentation.edit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EnergySavingsLeaf
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.R
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFChip
import com.flowfuel.app.core.designsystem.components.FFDialog
import com.flowfuel.app.core.designsystem.components.FFDialogKind
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFNumberField
import com.flowfuel.app.core.designsystem.components.FFNumberKind
import com.flowfuel.app.core.designsystem.components.FFSkeletonBlock
import com.flowfuel.app.core.designsystem.components.FFSkeletonLine
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.components.FFTextField
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.components.FFTopBarVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import kotlinx.coroutines.flow.collectLatest

// ─── Transformação visual da placa (mesma lógica do AddVehicleScreen) ─────────

private object PlateVisualTransformation : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val raw = text.text
        val isOldFormat = raw.length == 7
            && raw.take(3).all(Char::isLetter)
            && raw.drop(3).all(Char::isDigit)

        if (!isOldFormat) return TransformedText(text, OffsetMapping.Identity)

        val formatted = "${raw.take(3)}-${raw.drop(3)}"
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                if (offset <= 3) offset else offset + 1
            override fun transformedToOriginal(offset: Int): Int =
                if (offset <= 3) offset else (offset - 1).coerceAtLeast(3)
        }
        return TransformedText(androidx.compose.ui.text.AnnotatedString(formatted), mapping)
    }
}

// ─── Tela principal ──────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditVehicleScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: EditVehicleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val focusManager = LocalFocusManager.current
    val savedMsg = stringResource(R.string.vehicle_updated_success)

    BackHandler(enabled = state.screenState is EditVehicleScreenState.Editing) {
        viewModel.onBackPressed()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                EditVehicleEffect.NavigateBack         -> onBack()
                EditVehicleEffect.NavigateBackAfterSave -> {
                    snackbarHostState.showSnackbar(FFSnackbarVisuals(savedMsg, FFSnackbarKind.Success))
                    onSaved()
                }
                EditVehicleEffect.NavigateToLogin      -> onNavigateToLogin()
            }
        }
    }

    // Erro de formulário (rede/servidor)
    val formErrorMsg = state.formError?.userMessage()
    LaunchedEffect(formErrorMsg) {
        if (formErrorMsg != null) {
            snackbarHostState.showSnackbar(FFSnackbarVisuals(formErrorMsg, FFSnackbarKind.Error))
            viewModel.clearError()
        }
    }

    // ── Gerenciamento de foco ────────────────────────────────────────────────
    val focusBrand           = remember { FocusRequester() }
    val focusModel           = remember { FocusRequester() }
    val focusManufactureYear = remember { FocusRequester() }
    val focusModelYear       = remember { FocusRequester() }
    val focusColor           = remember { FocusRequester() }
    val focusPlate           = remember { FocusRequester() }
    val focusOdometer        = remember { FocusRequester() }
    val focusTank            = remember { FocusRequester() }
    val focusBattery         = remember { FocusRequester() }

    val bringerBrand           = remember { BringIntoViewRequester() }
    val bringerModel           = remember { BringIntoViewRequester() }
    val bringerManufactureYear = remember { BringIntoViewRequester() }
    val bringerModelYear       = remember { BringIntoViewRequester() }
    val bringerPlate           = remember { BringIntoViewRequester() }
    val bringerOdometer        = remember { BringIntoViewRequester() }

    LaunchedEffect(state.submitAttempt) {
        if (state.submitAttempt == 0) return@LaunchedEffect
        when {
            state.brandError           -> { bringerBrand.bringIntoView();           focusBrand.requestFocus() }
            state.modelError           -> { bringerModel.bringIntoView();            focusModel.requestFocus() }
            state.manufactureYearError -> { bringerManufactureYear.bringIntoView(); focusManufactureYear.requestFocus() }
            state.modelYearError       -> { bringerModelYear.bringIntoView();       focusModelYear.requestFocus() }
            state.licensePlateError    -> { bringerPlate.bringIntoView();           focusPlate.requestFocus() }
            state.odometerError        -> { bringerOdometer.bringIntoView();        focusOdometer.requestFocus() }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FFTopBar(
                title         = stringResource(R.string.vehicle_edit_title),
                variant       = FFTopBarVariant.Large,
                onBack        = viewModel::onBackPressed,
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = state.screenState is EditVehicleScreenState.Editing,
                enter   = fadeIn(tween(200)),
                exit    = fadeOut(tween(200)),
            ) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    FFButton(
                        text    = stringResource(R.string.vehicle_edit_cta),
                        onClick = viewModel::submit,
                        enabled = state.canSubmit,
                        loading = state.isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = FFTheme.spacing.md, vertical = FFTheme.spacing.sm),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = state.screenState) {

                // ── Carregando ─────────────────────────────────────────────────
                EditVehicleScreenState.Loading -> EditVehicleFormShimmer(
                    modifier = Modifier.fillMaxSize(),
                )

                // ── Erro ───────────────────────────────────────────────────────
                is EditVehicleScreenState.Error -> {
                    val apiError = s.error as? AppError.Api
                    val isNotFound = apiError?.code == "RESOURCE_NOT_FOUND"
                        || apiError?.code == "HTTP_404"
                    if (isNotFound) {
                        FFErrorState(
                            title      = "Veículo não encontrado",
                            message    = "Este veículo não existe ou foi removido.",
                            actionText = "Voltar",
                            onRetry    = onBack,
                            modifier   = Modifier.align(Alignment.Center),
                        )
                    } else {
                        FFErrorState(
                            message  = s.error.userMessage(),
                            onRetry  = viewModel::load,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                // ── Formulário ─────────────────────────────────────────────────
                EditVehicleScreenState.Editing -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = FFTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xl),
                ) {
                    Spacer(Modifier.height(FFTheme.spacing.xs))

                    // ── Seção 1: Informações principais ──────────────────────
                    EditFormSection(title = stringResource(R.string.vehicle_section_info)) {

                        FFTextField(
                            value         = state.brand,
                            onValueChange = viewModel::onBrandChange,
                            label         = stringResource(R.string.vehicle_brand),
                            imeAction     = ImeAction.Next,
                            keyboardActions = KeyboardActions(onNext = { focusModel.requestFocus() }),
                            errorText     = if (state.brandError) stringResource(R.string.error_required)
                                            else state.serverErrors?.firstOrNull { it.field == "brand" }?.message,
                            enabled       = !state.isSubmitting,
                            modifier      = Modifier
                                .bringIntoViewRequester(bringerBrand)
                                .focusRequester(focusBrand),
                        )

                        FFTextField(
                            value         = state.model,
                            onValueChange = viewModel::onModelChange,
                            label         = stringResource(R.string.vehicle_model),
                            imeAction     = ImeAction.Next,
                            keyboardActions = KeyboardActions(onNext = { focusManufactureYear.requestFocus() }),
                            errorText     = if (state.modelError) stringResource(R.string.error_required)
                                            else state.serverErrors?.firstOrNull { it.field == "model" }?.message,
                            enabled       = !state.isSubmitting,
                            modifier      = Modifier
                                .bringIntoViewRequester(bringerModel)
                                .focusRequester(focusModel),
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                            FFTextField(
                                value         = state.manufactureYear,
                                onValueChange = viewModel::onManufactureYearChange,
                                label         = stringResource(R.string.vehicle_manufacture_year),
                                keyboardType  = KeyboardType.Number,
                                imeAction     = ImeAction.Next,
                                keyboardActions = KeyboardActions(onNext = { focusModelYear.requestFocus() }),
                                errorText     = if (state.manufactureYearError) stringResource(R.string.error_year_invalid)
                                                else state.serverErrors?.firstOrNull { it.field == "manufactureYear" }?.message,
                                enabled       = !state.isSubmitting,
                                modifier      = Modifier
                                    .weight(1f)
                                    .bringIntoViewRequester(bringerManufactureYear)
                                    .focusRequester(focusManufactureYear),
                            )
                            FFTextField(
                                value         = state.modelYear,
                                onValueChange = viewModel::onModelYearChange,
                                label         = stringResource(R.string.vehicle_model_year),
                                keyboardType  = KeyboardType.Number,
                                imeAction     = ImeAction.Next,
                                keyboardActions = KeyboardActions(onNext = { focusColor.requestFocus() }),
                                errorText     = if (state.modelYearError) stringResource(R.string.error_model_year_invalid)
                                                else state.serverErrors?.firstOrNull { it.field == "modelYear" }?.message,
                                enabled       = !state.isSubmitting,
                                modifier      = Modifier
                                    .weight(1f)
                                    .bringIntoViewRequester(bringerModelYear)
                                    .focusRequester(focusModelYear),
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                            FFTextField(
                                value         = state.color,
                                onValueChange = viewModel::onColorChange,
                                label         = stringResource(R.string.vehicle_color),
                                imeAction     = ImeAction.Next,
                                keyboardActions = KeyboardActions(onNext = { focusPlate.requestFocus() }),
                                enabled       = !state.isSubmitting,
                                modifier      = Modifier
                                    .weight(1f)
                                    .focusRequester(focusColor),
                            )
                            FFTextField(
                                value         = state.licensePlate,
                                onValueChange = viewModel::onLicensePlateChange,
                                label         = stringResource(R.string.vehicle_plate),
                                placeholder   = "ABC1D23",
                                keyboardType  = KeyboardType.Text,
                                imeAction     = ImeAction.Done,
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                visualTransformation = PlateVisualTransformation,
                                errorText     = if (state.licensePlateError) stringResource(R.string.error_plate_invalid)
                                                else state.serverErrors?.firstOrNull { it.field == "licensePlate" }?.message,
                                enabled       = !state.isSubmitting,
                                modifier      = Modifier
                                    .weight(1f)
                                    .bringIntoViewRequester(bringerPlate)
                                    .focusRequester(focusPlate),
                            )
                        }
                    }

                    EditSectionDivider()

                    // ── Seção 2: Tipo do veículo ──────────────────────────────
                    EditFormSection(title = stringResource(R.string.vehicle_section_type)) {
                        EditVehicleTypeSelector(
                            selected = state.vehicleType,
                            onSelect = viewModel::onVehicleTypeChange,
                            enabled  = !state.isSubmitting,
                        )
                    }

                    EditSectionDivider()

                    // ── Seção 3: Tipo de energia ──────────────────────────────
                    EditFormSection(title = stringResource(R.string.vehicle_section_energy)) {
                        EditEnergyTypeSelector(
                            selected = state.energyType,
                            onSelect = viewModel::onEnergyTypeChange,
                            enabled  = !state.isSubmitting,
                        )
                    }

                    // ── Seção 4: Combustível (condicional) ────────────────────
                    AnimatedVisibility(
                        visible = state.showFuelType,
                        enter   = fadeIn() + expandVertically(),
                        exit    = fadeOut() + shrinkVertically(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xl)) {
                            EditSectionDivider()
                            EditFormSection(title = stringResource(R.string.vehicle_section_fuel)) {
                                EditFuelTypeSelector(
                                    selected = state.fuelType,
                                    onSelect = viewModel::onFuelTypeChange,
                                    enabled  = !state.isSubmitting,
                                )
                            }
                        }
                    }

                    EditSectionDivider()

                    // ── Seção 5: Dados técnicos ───────────────────────────────
                    EditFormSection(title = stringResource(R.string.vehicle_section_tech)) {

                        FFNumberField(
                            value         = state.odometer,
                            onValueChange = viewModel::onOdometerChange,
                            label         = stringResource(R.string.vehicle_odometer),
                            kind          = FFNumberKind.WholeNumber,
                            leadingIcon   = Icons.Outlined.Speed,
                            errorText     = if (state.odometerError) stringResource(R.string.error_required)
                                            else state.serverErrors?.firstOrNull { it.field == "odometerKm" }?.message,
                            imeAction     = ImeAction.Next,
                            keyboardActions = KeyboardActions(onNext = {
                                if (state.showTankCapacity) focusTank.requestFocus()
                                else if (state.showBatteryCapacity) focusBattery.requestFocus()
                                else focusManager.clearFocus()
                            }),
                            enabled       = !state.isSubmitting,
                            modifier      = Modifier
                                .bringIntoViewRequester(bringerOdometer)
                                .focusRequester(focusOdometer),
                        )

                        AnimatedVisibility(
                            visible = state.showTankCapacity,
                            enter   = fadeIn() + expandVertically(),
                            exit    = fadeOut() + shrinkVertically(),
                        ) {
                            FFNumberField(
                                value         = state.tankCapacity,
                                onValueChange = viewModel::onTankCapacityChange,
                                label         = stringResource(R.string.vehicle_tank_capacity),
                                kind          = FFNumberKind.Decimal,
                                leadingIcon   = Icons.Outlined.LocalGasStation,
                                imeAction     = if (state.showBatteryCapacity) ImeAction.Next else ImeAction.Done,
                                keyboardActions = KeyboardActions(
                                    onNext = { focusBattery.requestFocus() },
                                    onDone = { focusManager.clearFocus() },
                                ),
                                enabled       = !state.isSubmitting,
                                modifier      = Modifier.focusRequester(focusTank),
                            )
                        }

                        AnimatedVisibility(
                            visible = state.showBatteryCapacity,
                            enter   = fadeIn() + expandVertically(),
                            exit    = fadeOut() + shrinkVertically(),
                        ) {
                            FFNumberField(
                                value         = state.batteryCapacity,
                                onValueChange = viewModel::onBatteryCapacityChange,
                                label         = stringResource(R.string.vehicle_battery_capacity),
                                kind          = FFNumberKind.Decimal,
                                leadingIcon   = Icons.Outlined.EvStation,
                                imeAction     = ImeAction.Done,
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                enabled       = !state.isSubmitting,
                                modifier      = Modifier.focusRequester(focusBattery),
                            )
                        }
                    }

                    Spacer(Modifier.height(FFTheme.spacing.xl))
                }
            }
        }
    }

    // ── Dialog de descarte de alterações ──────────────────────────────────────
    if (state.showDiscardDialog) {
        FFDialog(
            title       = stringResource(R.string.vehicle_discard_title),
            message     = stringResource(R.string.vehicle_discard_message),
            confirmText = stringResource(R.string.vehicle_discard_confirm),
            onConfirm   = viewModel::onDiscardConfirm,
            onDismiss   = viewModel::onDiscardDismiss,
            kind        = FFDialogKind.Destructive,
            dismissText = stringResource(R.string.action_cancel),
        )
    }
}

// ─── Shimmer do formulário ────────────────────────────────────────────────────

@Composable
private fun EditVehicleFormShimmer(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FFTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xl),
    ) {
        Spacer(Modifier.height(FFTheme.spacing.xs))
        // Seção info
        FFSkeletonLine(widthFraction = 0.4f, height = 14.dp)
        FFSkeletonBlock(height = 56.dp)
        FFSkeletonBlock(height = 56.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
            FFSkeletonBlock(height = 56.dp, modifier = Modifier.weight(1f))
            FFSkeletonBlock(height = 56.dp, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
            FFSkeletonBlock(height = 56.dp, modifier = Modifier.weight(1f))
            FFSkeletonBlock(height = 56.dp, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Seção tipo
        FFSkeletonLine(widthFraction = 0.35f, height = 14.dp)
        FFSkeletonBlock(height = 48.dp)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Seção energia
        FFSkeletonLine(widthFraction = 0.4f, height = 14.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
            FFSkeletonBlock(height = 88.dp, modifier = Modifier.weight(1f))
            FFSkeletonBlock(height = 88.dp, modifier = Modifier.weight(1f))
            FFSkeletonBlock(height = 88.dp, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Seção dados técnicos
        FFSkeletonLine(widthFraction = 0.4f, height = 14.dp)
        FFSkeletonBlock(height = 56.dp)
        FFSkeletonBlock(height = 56.dp)
        Spacer(Modifier.height(FFTheme.spacing.xxl))
    }
}

// ─── Componentes internos (equivalentes aos do AddVehicleScreen) ──────────────

@Composable
private fun EditFormSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md)) {
        Text(
            text  = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun EditSectionDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun EditVehicleTypeSelector(
    selected: VehicleType,
    onSelect: (VehicleType) -> Unit,
    enabled: Boolean,
) {
    val options = listOf(
        VehicleType.Car        to (stringResource(R.string.vehicle_type_car)        to Icons.Outlined.DirectionsCar),
        VehicleType.Motorcycle to (stringResource(R.string.vehicle_type_motorcycle) to Icons.Outlined.TwoWheeler),
    )

    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Tipo do veículo" },
    ) {
        options.forEachIndexed { index, (type, pair) ->
            val (label, icon) = pair
            SegmentedButton(
                selected = selected == type,
                onClick  = { if (enabled) onSelect(type) },
                shape    = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon     = { SegmentedButtonDefaults.Icon(active = selected == type) },
            ) {
                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs),
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun EditEnergyTypeSelector(
    selected: EnergyType,
    onSelect: (EnergyType) -> Unit,
    enabled: Boolean,
) {
    data class EnergyOption(
        val type: EnergyType,
        val label: String,
        val description: String,
        val icon: ImageVector,
    )

    val options = listOf(
        EnergyOption(EnergyType.Combustion, stringResource(R.string.vehicle_energy_combustion), stringResource(R.string.vehicle_energy_combustion_desc), Icons.Outlined.LocalGasStation),
        EnergyOption(EnergyType.Electric,   stringResource(R.string.vehicle_energy_electric),   stringResource(R.string.vehicle_energy_electric_desc),   Icons.Outlined.EvStation),
        EnergyOption(EnergyType.Hybrid,     stringResource(R.string.vehicle_energy_hybrid),     stringResource(R.string.vehicle_energy_hybrid_desc),     Icons.Outlined.EnergySavingsLeaf),
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { option ->
            EditEnergyTypeCard(
                label       = option.label,
                description = option.description,
                icon        = option.icon,
                selected    = selected == option.type,
                onClick     = { if (enabled) onSelect(option.type) },
                modifier    = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EditEnergyTypeCard(
    label: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor    = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth    = if (selected) 2.dp else 1.dp
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                         else MaterialTheme.colorScheme.surface
    val iconTint   = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Card(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        border   = BorderStroke(borderWidth, borderColor),
        colors   = CardDefaults.cardColors(containerColor = containerColor),
        shape    = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = FFTheme.spacing.md, horizontal = FFTheme.spacing.sm),
            horizontalAlignment  = Alignment.CenterHorizontally,
            verticalArrangement  = Arrangement.spacedBy(FFTheme.spacing.xs),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = labelColor, textAlign = TextAlign.Center)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditFuelTypeSelector(
    selected: FuelType,
    onSelect: (FuelType) -> Unit,
    enabled: Boolean,
) {
    val options = listOf(
        FuelType.Gasoline to stringResource(R.string.vehicle_fuel_gasoline),
        FuelType.Ethanol  to stringResource(R.string.vehicle_fuel_ethanol),
        FuelType.Diesel   to stringResource(R.string.vehicle_fuel_diesel),
        FuelType.Flex     to stringResource(R.string.vehicle_fuel_flex),
        FuelType.GNV      to stringResource(R.string.vehicle_fuel_gnv),
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        verticalArrangement   = Arrangement.spacedBy(FFTheme.spacing.xs),
    ) {
        options.forEach { (fuel, label) ->
            FFChip(
                label    = label,
                selected = selected == fuel,
                onClick  = { if (enabled) onSelect(fuel) },
            )
        }
    }
}
