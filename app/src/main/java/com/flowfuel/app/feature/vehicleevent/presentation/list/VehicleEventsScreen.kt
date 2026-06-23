package com.flowfuel.app.feature.vehicleevent.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFEmptyState
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFSkeletonBlock
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.components.FFTopBarVariant
import com.flowfuel.app.core.designsystem.components.FFFab
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.EventDateFilter
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.presentation.components.EventCategoryChip
import com.flowfuel.app.feature.vehicleevent.presentation.components.EventCategoryFilterRow
import com.flowfuel.app.feature.vehicleevent.presentation.components.EventDateFilterRow
import kotlinx.coroutines.flow.collectLatest
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun VehicleEventsScreen(
    onBack: (() -> Unit)? = null,
    onNavigateToCreate: (vehicleId: Int) -> Unit,
    onNavigateToDetails: (eventId: Int) -> Unit,
    onNavigateToLogin: () -> Unit,
    eventCreated: Boolean = false,
    onEventCreatedConsumed: () -> Unit = {},
    eventDeleted: Int = -1,
    onEventDeletedConsumed: () -> Unit = {},
    eventUpdated: Boolean = false,
    onEventUpdatedConsumed: () -> Unit = {},
    viewModel: VehicleEventsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    LaunchedEffect(eventCreated) {
        if (eventCreated) {
            viewModel.refresh()
            onEventCreatedConsumed()
        }
    }

    LaunchedEffect(eventDeleted) {
        if (eventDeleted > 0) {
            viewModel.removeEvent(eventDeleted)
            snackbarHostState.showSnackbar(FFSnackbarVisuals("Evento excluído", FFSnackbarKind.Success))
            onEventDeletedConsumed()
        }
    }

    LaunchedEffect(eventUpdated) {
        if (eventUpdated) {
            viewModel.refresh()
            onEventUpdatedConsumed()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is VehicleEventsEffect.NavigateToCreate -> onNavigateToCreate(effect.vehicleId)
                is VehicleEventsEffect.NavigateToDetails -> onNavigateToDetails(effect.eventId)
                VehicleEventsEffect.NavigateToLogin -> onNavigateToLogin()
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMs = dateRangePickerState.selectedStartDateMillis
                        val endMs = dateRangePickerState.selectedEndDateMillis
                        if (startMs != null && endMs != null) {
                            val from = Instant.ofEpochMilli(startMs).atZone(ZoneOffset.UTC).toLocalDate()
                            val to = Instant.ofEpochMilli(endMs).atZone(ZoneOffset.UTC).toLocalDate()
                            viewModel.onDateFilterSelected(EventDateFilter.Custom(from, to))
                        }
                        showDatePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            },
        ) {
            DateRangePicker(state = dateRangePickerState)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
        topBar = {
            val title = buildString {
                append("Eventos")
                val label = state.activeVehicleLabel
                if (label != null) append(" • $label")
            }
            FFTopBar(
                title = title,
                variant = FFTopBarVariant.Small,
                onBack = onBack,
            )
        },
        floatingActionButton = {
            FFFab(
                icon = Icons.Default.Add,
                contentDescription = "Novo evento",
                onClick = viewModel::onCreateClick,
                text = "Novo Evento",
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            EventCategoryFilterRow(
                selected = state.selectedCategory,
                onSelect = viewModel::onCategorySelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = FFTheme.spacing.sm),
            )

            EventDateFilterRow(
                selected = state.selectedDateFilter,
                onSelect = viewModel::onDateFilterSelected,
                onCustomClick = { showDatePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = FFTheme.spacing.sm),
            )

            HorizontalDivider()

            Box(modifier = Modifier.weight(1f)) {
                when (val s = state.screenState) {
                    VehicleEventsScreenState.Loading -> VehicleEventsShimmer(
                        modifier = Modifier.fillMaxSize(),
                    )

                    is VehicleEventsScreenState.Success -> {
                        val listState = rememberLazyListState()
                        val shouldLoadMore by remember {
                            derivedStateOf {
                                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                                val total = listState.layoutInfo.totalItemsCount
                                lastVisible != null && total > 0 && lastVisible.index >= total - 3
                            }
                        }

                        LaunchedEffect(shouldLoadMore) {
                            if (shouldLoadMore) viewModel.loadNextPage()
                        }

                        PullToRefreshBox(
                            isRefreshing = state.isRefreshing,
                            onRefresh = viewModel::refresh,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    horizontal = FFTheme.spacing.md,
                                    vertical = FFTheme.spacing.md,
                                ),
                                verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap),
                            ) {
                                items(s.events, key = { it.id }) { event ->
                                    VehicleEventCard(
                                        event = event,
                                        onClick = { viewModel.onEventClick(event.id) },
                                    )
                                }

                                item {
                                    val pagination = state.pagination
                                    when {
                                        pagination.isLoadingMore -> Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = FFTheme.spacing.md),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                        }

                                        pagination.pageError != null -> Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = FFTheme.spacing.sm),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            FFButton(
                                                text = "Tentar novamente",
                                                onClick = viewModel::onRetryPage,
                                                variant = FFButtonVariant.Tonal,
                                            )
                                        }

                                        else -> Spacer(modifier = Modifier.height(FFTheme.spacing.xxl))
                                    }
                                }
                            }
                        }
                    }

                    VehicleEventsScreenState.Empty -> PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val hasActiveFilters = state.selectedCategory != null ||
                                state.selectedDateFilter !is EventDateFilter.All
                            if (hasActiveFilters) {
                                FFEmptyState(
                                    icon = Icons.AutoMirrored.Outlined.Assignment,
                                    title = "Nenhum evento encontrado",
                                    description = "Nenhum evento para os filtros selecionados. Tente outro filtro.",
                                )
                            } else {
                                FFEmptyState(
                                    icon = Icons.AutoMirrored.Outlined.Assignment,
                                    title = "Nenhum evento registrado",
                                    description = "Toque em + para adicionar o primeiro evento.",
                                )
                            }
                        }
                    }

                    is VehicleEventsScreenState.Error -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        FFErrorState(
                            message = s.error.userMessage(),
                            onRetry = viewModel::load,
                        )
                    }
                }
            }
        }
    }
}

// ─── Card de evento ────────────────────────────────────────────────────────────

@Composable
private fun VehicleEventCard(event: VehicleEvent, onClick: () -> Unit) {
    FFCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EventCategoryChip(category = event.category)
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (event.description != null) {
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = formatAmount(event.amount),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (event.amount != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatEventDate(event.eventDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (event.odometerKm != null) {
                    Text(
                        text = formatOdometer(event.odometerKm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}


// ─── Shimmer ────────────────────────────────────────────────────────────────────

@Composable
private fun VehicleEventsShimmer(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(FFTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap),
    ) {
        repeat(3) { FFSkeletonBlock(height = 88.dp) }
    }
}

// ─── Formatadores ────────────────────────────────────────────────────────────

private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("pt", "BR"))
private val odometerFormat = NumberFormat.getIntegerInstance(Locale("pt", "BR"))

private fun formatAmount(amount: Double?): String =
    if (amount == null) "—" else currencyFormat.format(amount)

private fun formatEventDate(isoDate: String): String =
    runCatching { LocalDate.parse(isoDate).format(dateFormatter) }.getOrDefault(isoDate)

private fun formatOdometer(km: Int): String =
    "${odometerFormat.format(km)} km"
