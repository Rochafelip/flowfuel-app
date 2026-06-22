package com.flowfuel.app.feature.vehicleevent.presentation.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFNumberField
import com.flowfuel.app.core.designsystem.components.FFNumberKind
import com.flowfuel.app.core.designsystem.components.FFSkeletonBlock
import com.flowfuel.app.core.designsystem.components.FFSkeletonLine
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
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
fun EditVehicleEventScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: EditVehicleEventViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showDatePicker by remember { mutableStateOf(false) }

    BackHandler { viewModel.onBackPressed() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is EditVehicleEventEffect.ShowSnackbar -> snackbarHostState.showSnackbar(
                    FFSnackbarVisuals(effect.message, FFSnackbarKind.Success),
                )
                EditVehicleEventEffect.NavigateBack -> onSaved()
                EditVehicleEventEffect.NavigateToLogin -> onNavigateToLogin()
            }
        }
    }

    val editing = uiState.screenState as? EditVehicleEventScreenState.Editing
    val formErrorMsg = editing?.formError?.userMessage()
    LaunchedEffect(formErrorMsg) {
        if (formErrorMsg != null) {
            snackbarHostState.showSnackbar(FFSnackbarVisuals(formErrorMsg, FFSnackbarKind.Error))
            viewModel.clearFormError()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Editar Evento") },
                navigationIcon = {
                    IconButton(onClick = viewModel::onBackPressed) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
        bottomBar = {
            if (editing != null) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    FFButton(
                        text = "Salvar",
                        onClick = viewModel::submit,
                        enabled = editing.isDirty && !editing.isSubmitting,
                        loading = editing.isSubmitting,
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
            when (val screenState = uiState.screenState) {
                EditVehicleEventScreenState.Loading -> EditEventShimmer(
                    modifier = Modifier.fillMaxSize(),
                )

                is EditVehicleEventScreenState.Editing -> EditEventForm(
                    state = screenState,
                    viewModel = viewModel,
                    onDateFieldClick = { showDatePicker = true },
                )

                is EditVehicleEventScreenState.Error -> FFErrorState(
                    message = screenState.error.userMessage(),
                    onRetry = viewModel::load,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }

    if (showDatePicker && editing != null) {
        EditEventDatePickerDialog(
            initialDateIso = editing.eventDate,
            onConfirm = { iso ->
                viewModel.onEventDateChange(iso)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (editing?.showDiscardDialog == true) {
        FFDialog(
            title = "Descartar alterações?",
            message = "As informações editadas serão perdidas.",
            confirmText = "Descartar",
            onConfirm = viewModel::onDiscardConfirm,
            onDismiss = viewModel::onDiscardDismiss,
            kind = FFDialogKind.Destructive,
            dismissText = "Continuar editando",
        )
    }
}

// ─── Formulário ───────────────────────────────────────────────────────────────

@Composable
private fun EditEventForm(
    state: EditVehicleEventScreenState.Editing,
    viewModel: EditVehicleEventViewModel,
    onDateFieldClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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

            EditDateField(
                value = state.eventDate,
                errorText = state.eventDateError,
                enabled = !state.isSubmitting,
                onClick = onDateFieldClick,
            )

            FFTextField(
                value = state.amount,
                onValueChange = { viewModel.onAmountChange(it.filter(Char::isDigit)) },
                label = "Valor (R$)",
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
                kind = FFNumberKind.Odometer,
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

// ─── Campo de data (read-only, abre DatePicker) ───────────────────────────────

@Composable
private fun EditDateField(
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
private fun EditEventDatePickerDialog(
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

// ─── Shimmer ──────────────────────────────────────────────────────────────────

@Composable
private fun EditEventShimmer(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(FFTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
    ) {
        FFSkeletonLine(widthFraction = 0.4f, height = 16.dp)
        FFSkeletonBlock(height = 48.dp)
        Spacer(Modifier.height(FFTheme.spacing.xs))
        FFSkeletonBlock(height = 56.dp)
        FFSkeletonBlock(height = 56.dp)
        FFSkeletonBlock(height = 56.dp)
        FFSkeletonBlock(height = 56.dp)
        FFSkeletonBlock(height = 56.dp)
        FFSkeletonBlock(height = 80.dp)
    }
}

// ─── Formatação de data ───────────────────────────────────────────────────────

private val ptBrFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("pt", "BR"))

private fun String.formatDateDisplay(): String {
    if (isBlank()) return ""
    return runCatching { LocalDate.parse(this).format(ptBrFormatter) }.getOrDefault(this)
}
