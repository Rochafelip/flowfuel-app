package com.flowfuel.app.feature.vehicle.presentation.add

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.R
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFChip
import com.flowfuel.app.core.designsystem.components.FFNumberField
import com.flowfuel.app.core.designsystem.components.FFNumberKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.components.FFTextField
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.preview.FFPreviewBox
import com.flowfuel.app.core.designsystem.preview.FFThemePreviews
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import kotlinx.coroutines.flow.collectLatest

// ─── Transformação visual da placa ───────────────────────────────────────────

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

// ─── Tela principal (wizard) ─────────────────────────────────────────────────

@Composable
fun AddVehicleScreen(
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddVehicleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = state.error?.userMessage()

    BackHandler(enabled = state.currentStep > 1) {
        viewModel.onPreviousStep()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                AddVehicleEffect.NavigateBack -> onSuccess()
            }
        }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(FFSnackbarVisuals(errorMessage, FFSnackbarKind.Error))
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            FFTopBar(
                title = stringResource(R.string.vehicle_add_title),
                onBack = if (state.currentStep > 1) viewModel::onPreviousStep else onBack,
            )
        },
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FFButton(
                        text = if (state.currentStep < 3) stringResource(R.string.vehicle_add_continue)
                               else stringResource(R.string.vehicle_add_cta),
                        onClick = if (state.currentStep < 3) viewModel::onNextStep
                                  else ({ viewModel.submit() }),
                        enabled = if (state.currentStep == 3) state.canSubmit else !state.isSubmitting,
                        loading = state.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.currentStep == 3) {
                        FFButton(
                            text = stringResource(R.string.vehicle_add_fill_later),
                            onClick = { viewModel.submit(skipOptional = true) },
                            enabled = !state.isSubmitting,
                            variant = FFButtonVariant.Text,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            WizardStepper(
                currentStep = state.currentStep,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            AnimatedContent(
                targetState = state.currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                },
                modifier = Modifier.weight(1f),
                label = "wizard_step",
            ) { step ->
                when (step) {
                    1 -> Step1Content(state = state, viewModel = viewModel)
                    2 -> Step2Content(state = state, viewModel = viewModel)
                    else -> Step3Content(state = state, viewModel = viewModel)
                }
            }
        }
    }
}

// ─── Etapas do wizard ────────────────────────────────────────────────────────

@Composable
private fun Step1Content(
    state: AddVehicleUiState,
    viewModel: AddVehicleViewModel,
) {
    val focusBrand           = remember { FocusRequester() }
    val focusModel           = remember { FocusRequester() }
    val focusManufactureYear = remember { FocusRequester() }
    val focusModelYear       = remember { FocusRequester() }
    val focusManager         = LocalFocusManager.current

    LaunchedEffect(Unit) { focusBrand.requestFocus() }

    LaunchedEffect(state.stepAttempt) {
        if (state.stepAttempt == 0) return@LaunchedEffect
        when {
            state.brandError           -> focusBrand.requestFocus()
            state.modelError           -> focusModel.requestFocus()
            state.manufactureYearError -> focusManufactureYear.requestFocus()
            state.modelYearError       -> focusModelYear.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FFTextField(
            value = state.brand,
            onValueChange = viewModel::onBrandChange,
            label = stringResource(R.string.vehicle_brand),
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(onNext = { focusModel.requestFocus() }),
            errorText = if (state.brandError) stringResource(R.string.error_required)
                        else state.serverErrors?.firstOrNull { it.field == "brand" }?.message,
            enabled = !state.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusBrand),
        )

        FFTextField(
            value = state.model,
            onValueChange = viewModel::onModelChange,
            label = stringResource(R.string.vehicle_model),
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(onNext = { focusManufactureYear.requestFocus() }),
            errorText = if (state.modelError) stringResource(R.string.error_required)
                        else state.serverErrors?.firstOrNull { it.field == "model" }?.message,
            enabled = !state.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusModel),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FFTextField(
                value = state.manufactureYear,
                onValueChange = viewModel::onManufactureYearChange,
                label = stringResource(R.string.vehicle_manufacture_year),
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
                keyboardActions = KeyboardActions(onNext = { focusModelYear.requestFocus() }),
                errorText = if (state.manufactureYearError) stringResource(R.string.error_year_invalid)
                            else state.serverErrors?.firstOrNull { it.field == "manufactureYear" }?.message,
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusManufactureYear),
            )

            FFTextField(
                value = state.modelYear,
                onValueChange = viewModel::onModelYearChange,
                label = stringResource(R.string.vehicle_model_year),
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                errorText = if (state.modelYearError) stringResource(R.string.error_model_year_invalid)
                            else state.serverErrors?.firstOrNull { it.field == "modelYear" }?.message,
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusModelYear),
            )
        }
    }
}

@Composable
private fun Step2Content(
    state: AddVehicleUiState,
    viewModel: AddVehicleViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        FormSection(title = stringResource(R.string.vehicle_section_type)) {
            VehicleTypeSelector(
                selected = state.vehicleType,
                onSelect = viewModel::onVehicleTypeChange,
                enabled = !state.isSubmitting,
            )
        }

        SectionDivider()

        FormSection(title = stringResource(R.string.vehicle_section_energy)) {
            EnergyTypeSelector(
                selected = state.energyType,
                onSelect = viewModel::onEnergyTypeChange,
                enabled = !state.isSubmitting,
            )
        }

        AnimatedVisibility(
            visible = state.showFuelType,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                SectionDivider()
                FormSection(title = stringResource(R.string.vehicle_section_fuel)) {
                    FuelTypeSelector(
                        selected = state.fuelType,
                        onSelect = viewModel::onFuelTypeChange,
                        enabled = !state.isSubmitting,
                    )
                }
            }
        }
    }
}

@Composable
private fun Step3Content(
    state: AddVehicleUiState,
    viewModel: AddVehicleViewModel,
) {
    val focusPlate   = remember { FocusRequester() }
    val focusColor   = remember { FocusRequester() }
    val focusOdometer = remember { FocusRequester() }
    val focusTank    = remember { FocusRequester() }
    val focusBattery = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { focusPlate.requestFocus() }

    LaunchedEffect(state.stepAttempt) {
        if (state.stepAttempt == 0 || !state.licensePlateError) return@LaunchedEffect
        focusPlate.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FFTextField(
                value = state.licensePlate,
                onValueChange = viewModel::onLicensePlateChange,
                label = stringResource(R.string.vehicle_plate),
                placeholder = "ABC1D23",
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
                keyboardActions = KeyboardActions(onNext = { focusColor.requestFocus() }),
                visualTransformation = PlateVisualTransformation,
                errorText = if (state.licensePlateError) stringResource(R.string.error_plate_invalid)
                            else state.serverErrors?.firstOrNull { it.field == "licensePlate" }?.message,
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusPlate),
            )

            FFTextField(
                value = state.color,
                onValueChange = viewModel::onColorChange,
                label = stringResource(R.string.vehicle_color),
                imeAction = ImeAction.Next,
                keyboardActions = KeyboardActions(onNext = { focusOdometer.requestFocus() }),
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusColor),
            )
        }

        FFNumberField(
            value = state.odometer,
            onValueChange = viewModel::onOdometerChange,
            label = stringResource(R.string.vehicle_odometer),
            kind = FFNumberKind.WholeNumber,
            leadingIcon = Icons.Outlined.Speed,
            errorText = state.serverErrors?.firstOrNull { it.field == "odometerKm" }?.message,
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(onNext = {
                if (state.showTankCapacity) focusTank.requestFocus()
                else if (state.showBatteryCapacity) focusBattery.requestFocus()
                else focusManager.clearFocus()
            }),
            enabled = !state.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusOdometer),
        )

        AnimatedVisibility(
            visible = state.showTankCapacity,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            FFNumberField(
                value = state.tankCapacity,
                onValueChange = viewModel::onTankCapacityChange,
                label = stringResource(R.string.vehicle_tank_capacity),
                kind = FFNumberKind.Decimal,
                leadingIcon = Icons.Outlined.LocalGasStation,
                imeAction = if (state.showBatteryCapacity) ImeAction.Next else ImeAction.Done,
                keyboardActions = KeyboardActions(
                    onNext = { focusBattery.requestFocus() },
                    onDone = { focusManager.clearFocus() },
                ),
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusTank),
            )
        }

        AnimatedVisibility(
            visible = state.showBatteryCapacity,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            FFNumberField(
                value = state.batteryCapacity,
                onValueChange = viewModel::onBatteryCapacityChange,
                label = stringResource(R.string.vehicle_battery_capacity),
                kind = FFNumberKind.Decimal,
                leadingIcon = Icons.Outlined.EvStation,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusBattery),
            )
        }
    }
}

// ─── Stepper visual ──────────────────────────────────────────────────────────

@Composable
private fun WizardStepper(
    currentStep: Int,
    modifier: Modifier = Modifier,
) {
    val stepLabels = listOf(
        stringResource(R.string.vehicle_wizard_step1),
        stringResource(R.string.vehicle_wizard_step2),
        stringResource(R.string.vehicle_wizard_step3),
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
    ) {
        stepLabels.forEachIndexed { index, label ->
            val step        = index + 1
            val isCompleted = step < currentStep
            val isActive    = step == currentStep

            // Connector before each step (except first) — Box height = circle height so
            // the divider aligns with the circle center when verticalAlignment = Top.
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .weight(0.5f)
                        .height(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    HorizontalDivider(
                        color = if (step <= currentStep) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.5.dp,
                    )
                }
            }

            // Step cell: circle + label stacked, centered within weight column
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            color = if (isCompleted || isActive) MaterialTheme.colorScheme.primary
                                    else Color.Transparent,
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (isCompleted || isActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        ),
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    } else {
                        Text(
                            text = step.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCompleted || isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─── Componentes reutilizáveis ───────────────────────────────────────────────

@Composable
private fun FormSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun VehicleTypeSelector(
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
                onClick = { if (enabled) onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = { SegmentedButtonDefaults.Icon(active = selected == type) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun EnergyTypeSelector(
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { option ->
            EnergyTypeCard(
                label = option.label,
                description = option.description,
                icon = option.icon,
                selected = selected == option.type,
                onClick = { if (enabled) onSelect(option.type) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EnergyTypeCard(
    label: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor     = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth     = if (selected) 2.dp else 1.dp
    val containerColor  = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                          else MaterialTheme.colorScheme.surface
    val iconTint        = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor      = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Card(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        border = BorderStroke(borderWidth, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = labelColor, textAlign = TextAlign.Center)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FuelTypeSelector(
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (fuel, label) ->
            FFChip(
                label = label,
                selected = selected == fuel,
                onClick = { if (enabled) onSelect(fuel) },
            )
        }
    }
}

// ─── Previews ────────────────────────────────────────────────────────────────

@FFThemePreviews
@Composable
private fun AddVehicleScreenPreview() {
    FFPreviewBox {
        Text("AddVehicleScreen preview — requer Hilt VM")
    }
}
