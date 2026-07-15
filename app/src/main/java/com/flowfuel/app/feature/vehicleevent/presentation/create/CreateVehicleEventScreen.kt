package com.flowfuel.app.feature.vehicleevent.presentation.create

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.core.designsystem.components.CurrencyBrlVisualTransformation
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFDialog
import com.flowfuel.app.core.designsystem.components.FFDialogKind
import com.flowfuel.app.core.designsystem.components.FFNumberField
import com.flowfuel.app.core.designsystem.components.FFNumberKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.components.FFTopBarVariant
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.components.FFTextField
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.feature.vehicleevent.presentation.components.EventCategorySelector
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateVehicleEventScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: CreateVehicleEventViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showDatePicker by remember { mutableStateOf(false) }

    BackHandler { viewModel.onBackPressed() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is CreateVehicleEventEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        FFSnackbarVisuals(effect.message, FFSnackbarKind.Success),
                    )
                }
                CreateVehicleEventEffect.NavigateBack -> onSaved()
                CreateVehicleEventEffect.NavigateToLogin -> onNavigateToLogin()
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FFTopBar(
                title = "Novo Evento",
                variant = FFTopBarVariant.Small,
                onBack = viewModel::onBackPressed,
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                FFButton(
                    text = "Salvar",
                    onClick = viewModel::submit,
                    enabled = !state.isSubmitting,
                    loading = state.isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = FFTheme.spacing.md, vertical = FFTheme.spacing.sm),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Spacer(Modifier.height(FFTheme.spacing.sm))

            Text(
                text = "Categoria",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = FFTheme.spacing.md),
            )
            Spacer(Modifier.height(FFTheme.spacing.sm))
            EventCategorySelector(
                selected = state.category,
                onSelect = viewModel::onCategoryChange,
                enabled = !state.isSubmitting,
                categories = state.availableCategories,
            )

            Spacer(Modifier.height(FFTheme.spacing.lg))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = FFTheme.spacing.md),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(Modifier.height(FFTheme.spacing.lg))

            Column(
                modifier = Modifier.padding(horizontal = FFTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
            ) {
                FFTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitleChange,
                    label = "Título *",
                    errorText = state.titleError,
                    enabled = !state.isSubmitting,
                    imeAction = ImeAction.Next,
                )

                FFTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = "Descrição",
                    enabled = !state.isSubmitting,
                    singleLine = false,
                    imeAction = ImeAction.Next,
                )

                DateField(
                    value = state.eventDate,
                    errorText = state.eventDateError,
                    enabled = !state.isSubmitting,
                    onClick = { showDatePicker = true },
                )

                FFTextField(
                    value = state.amount,
                    onValueChange = { viewModel.onAmountChange(it.filter(Char::isDigit)) },
                    label = "Valor (R$) *",
                    errorText = state.amountError,
                    enabled = !state.isSubmitting,
                    keyboardType = KeyboardType.Number,
                    visualTransformation = CurrencyBrlVisualTransformation(),
                    imeAction = ImeAction.Next,
                )

                FFNumberField(
                    value = state.odometerKm,
                    onValueChange = viewModel::onOdometerKmChange,
                    label = "Quilometragem",
                    kind = FFNumberKind.WholeNumber,
                    errorText = state.odometerError,
                    enabled = !state.isSubmitting,
                    imeAction = ImeAction.Next,
                )

                FFTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = "Observações",
                    enabled = !state.isSubmitting,
                    singleLine = false,
                    imeAction = ImeAction.Done,
                )

                Spacer(Modifier.height(FFTheme.spacing.md))
            }
        }
    }

    if (showDatePicker) {
        EventDatePickerDialog(
            initialDateIso = state.eventDate,
            onConfirm = { iso ->
                viewModel.onEventDateChange(iso)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (state.showDiscardDialog) {
        FFDialog(
            title = "Descartar alterações?",
            message = "As informações preenchidas serão perdidas.",
            confirmText = "Descartar",
            onConfirm = viewModel::onDiscardConfirm,
            onDismiss = viewModel::onDiscardDismiss,
            kind = FFDialogKind.Destructive,
            dismissText = "Continuar editando",
        )
    }
}

// ─── Campo de data (read-only, abre DatePicker) ───────────────────────────────

@Composable
private fun DateField(
    value: String,
    errorText: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val displayValue = value.formatDateDisplay()
    OutlinedTextField(
        value = displayValue,
        onValueChange = {},
        label = { Text("Data do evento *") },
        readOnly = true,
        enabled = false,
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it) } },
        trailingIcon = {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = if (errorText != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.outline,
            disabledLabelColor = if (errorText != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledSupportingTextColor = MaterialTheme.colorScheme.error,
            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}

// ─── DatePicker dialog ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDatePickerDialog(
    initialDateIso: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = initialDateIso.takeIf { it.isNotBlank() }?.let {
        runCatching {
            LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                    onConfirm(date.toString())
                }
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

// ─── Formatação de data ───────────────────────────────────────────────────────

private val ptBrFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("pt", "BR"))

private fun String.formatDateDisplay(): String {
    if (isBlank()) return ""
    return runCatching { LocalDate.parse(this).format(ptBrFormatter) }.getOrDefault(this)
}
