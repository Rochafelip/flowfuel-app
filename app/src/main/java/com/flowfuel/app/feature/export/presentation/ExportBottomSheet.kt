package com.flowfuel.app.feature.export.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.R
import com.flowfuel.app.core.designsystem.components.FFBottomSheet
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFChip
import com.flowfuel.app.core.designsystem.components.FFChipKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.feature.export.domain.ExportFormat
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val displayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy")

enum class ExportTarget { REFUELS, EVENTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    target: ExportTarget,
    onDismiss: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val errorMessage = state.error?.userMessage()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.clearError()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ExportEffect.FileReady -> {
                    if (!openFile(context, effect.uri)) {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.export_no_app)
                        )
                    }
                }
            }
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = dateRangePickerState.selectedStartDateMillis != null &&
                              dateRangePickerState.selectedEndDateMillis != null,
                    onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            viewModel.onDateRangeChange(
                                start = Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate(),
                                end = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate(),
                            )
                        }
                        showDatePicker = false
                    },
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            },
        ) {
            DateRangePicker(state = dateRangePickerState, modifier = Modifier.weight(1f))
        }
    }

    FFBottomSheet(onDismiss = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = stringResource(R.string.export_title),
                    style = MaterialTheme.typography.titleLarge,
                )

                Spacer(Modifier.height(FFTheme.spacing.md))

                Text(
                    text = stringResource(R.string.export_format_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(FFTheme.spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                    FFChip(
                        label = stringResource(R.string.export_format_csv),
                        selected = state.selectedFormat == ExportFormat.CSV,
                        onClick = { viewModel.onFormatChange(ExportFormat.CSV) },
                    )
                    FFChip(
                        label = stringResource(R.string.export_format_pdf),
                        selected = state.selectedFormat == ExportFormat.PDF,
                        onClick = { viewModel.onFormatChange(ExportFormat.PDF) },
                    )
                }
                Spacer(Modifier.height(FFTheme.spacing.md))

                // Date range
                Text(
                    text = stringResource(R.string.export_period_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(FFTheme.spacing.xs))
                if (state.hasDateRange) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs),
                    ) {
                        FFChip(
                            label = stringResource(
                                R.string.export_period_selected,
                                state.startDate!!.format(displayFormatter),
                                state.endDate!!.format(displayFormatter),
                            ),
                            kind = FFChipKind.Input,
                            selected = true,
                            onClick = { showDatePicker = true },
                            onTrailingClick = { viewModel.onDateRangeChange(null, null) },
                        )
                    }
                } else {
                    FFChip(
                        label = stringResource(R.string.export_period_hint),
                        kind = FFChipKind.Assist,
                        leadingIcon = Icons.Outlined.CalendarMonth,
                        onClick = { showDatePicker = true },
                    )
                }

                // Event type filter (only for events export)
                if (target == ExportTarget.EVENTS) {
                    Spacer(Modifier.height(FFTheme.spacing.md))
                    Text(
                        text = stringResource(R.string.export_event_type_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(FFTheme.spacing.xs))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                    ) {
                        FFChip(
                            label = stringResource(R.string.export_event_type_all),
                            selected = state.selectedEventType == null,
                            onClick = { viewModel.onEventTypeChange(null) },
                        )
                        EventCategory.entries.forEach { cat ->
                            FFChip(
                                label = cat.label,
                                selected = state.selectedEventType == cat.apiValue,
                                onClick = { viewModel.onEventTypeChange(cat.apiValue) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(FFTheme.spacing.lg))

                FFButton(
                    text = if (state.isLoading) stringResource(R.string.export_loading)
                           else stringResource(R.string.export_cta),
                    onClick = {
                        when (target) {
                            ExportTarget.REFUELS -> viewModel.exportRefuels()
                            ExportTarget.EVENTS  -> viewModel.exportEvents()
                        }
                    },
                    enabled = !state.isLoading,
                    loading = state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(FFTheme.spacing.md))
            }

            FFSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private fun openFile(context: Context, uri: Uri): Boolean {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, context.contentResolver.getType(uri))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent) }.isSuccess
}
