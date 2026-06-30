package com.flowfuel.app.feature.history.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.IconButton
import com.flowfuel.app.core.designsystem.components.FFFab
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.core.designsystem.components.FFDialog
import com.flowfuel.app.core.designsystem.components.FFDialogKind
import com.flowfuel.app.core.common.DateFormatter
import com.flowfuel.app.core.designsystem.components.FFEmptyState
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFRefuelListItem
import com.flowfuel.app.core.designsystem.components.FFSkeletonList
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.components.FFTopBarVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.feature.export.presentation.ExportBottomSheet
import com.flowfuel.app.feature.export.presentation.ExportTarget
import com.flowfuel.app.feature.history.domain.model.FilterPreset
import com.flowfuel.app.feature.history.domain.model.HistoryFilter
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import kotlinx.coroutines.flow.collectLatest
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

// ─── Lista de itens agrupados ──────────────────────────────────────────────────

private sealed interface HistoryListItem {
    data class Header(
        val yearMonth: String,
        val label: String,
        val count: Int,
        val subtotal: Double,
    ) : HistoryListItem

    data class Entry(val refuel: RefuelItem) : HistoryListItem
}

// ─── Tela de histórico ─────────────────────────────────────────────────────────

@Composable
fun HistoryScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToDetails: (id: Int) -> Unit = {},
    onAddRefuel: () -> Unit = {},
    historyNeedsRefresh: Boolean = false,
    onHistoryRefreshConsumed: () -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                HistoryEffect.NavigateToLogin -> onNavigateToLogin()
            }
        }
    }

    LaunchedEffect(historyNeedsRefresh) {
        if (historyNeedsRefresh) {
            viewModel.load()
            onHistoryRefreshConsumed()
        }
    }

    val deleteError = state.deleteError
    LaunchedEffect(deleteError) {
        if (deleteError != null) {
            snackbarHostState.showSnackbar(FFSnackbarVisuals("Não foi possível remover. Tente novamente.", FFSnackbarKind.Error))
            viewModel.clearDeleteError()
        }
    }

    if (state.pendingDeleteItem != null) {
        FFDialog(
            title        = "Remover abastecimento?",
            message      = "Esta ação não pode ser desfeita.",
            confirmText  = "Remover",
            onConfirm    = viewModel::confirmDelete,
            onDismiss    = viewModel::cancelDelete,
            kind         = FFDialogKind.Destructive,
        )
    }

    var showExportSheet by remember { mutableStateOf(false) }

    // Estado do DateRangePicker para filtro personalizado
    val dateRangePickerState = rememberDateRangePickerState()
    var showDateRangePicker by remember { mutableStateOf(false) }

    // Detecção de fim da lista para infinite scroll
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            lastVisible >= total - 3 && total > 0
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadNextPage()
    }

    if (showDateRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    enabled = dateRangePickerState.selectedStartDateMillis != null &&
                              dateRangePickerState.selectedEndDateMillis != null,
                    onClick = {
                        val startMillis = dateRangePickerState.selectedStartDateMillis
                        val endMillis   = dateRangePickerState.selectedEndDateMillis
                        if (startMillis != null && endMillis != null) {
                            val startDate = Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC).toLocalDate()
                            val endDate   = Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate()
                            viewModel.applyFilter(HistoryFilter(FilterPreset.CUSTOM, startDate, endDate))
                        }
                        showDateRangePicker = false
                    },
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) { Text("Cancelar") }
            },
        ) {
            DateRangePicker(
                state    = dateRangePickerState,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showExportSheet) {
        ExportBottomSheet(
            target = ExportTarget.REFUELS,
            onDismiss = { showExportSheet = false },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
        topBar = {
            FFTopBar(
                title   = "Histórico",
                variant = FFTopBarVariant.Small,
                actions = {
                    IconButton(onClick = { showExportSheet = true }) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "Exportar")
                    }
                },
            )
        },
        floatingActionButton = {
            FFFab(
                icon               = Icons.Default.LocalGasStation,
                contentDescription = "Registrar abastecimento",
                text               = "Registrar",
                onClick            = onAddRefuel,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            state.activeVehicleLabel?.let { label ->
                ActiveVehicleChip(label = label)
            }

            // Filtros sempre visíveis em Success ou quando filtro está ativo
            val showFilterArea = state.screenState is HistoryScreenState.Success || state.filter.isActive
            if (showFilterArea) {
                HistoryFilterPanel(
                    currentPreset    = state.filter.preset,
                    onPresetSelected = viewModel::applyPreset,
                    onCustomSelected = { showDateRangePicker = true },
                )
            }

            // Conteúdo principal
            Box(modifier = Modifier.weight(1f)) {
                when (val s = state.screenState) {

                    // ── Carregando ────────────────────────────────────────────
                    HistoryScreenState.Loading -> FFSkeletonList(
                        modifier  = Modifier.fillMaxSize(),
                        itemCount = 6,
                    )

                    // ── Vazio ─────────────────────────────────────────────────
                    HistoryScreenState.Empty -> FFEmptyState(
                        title       = if (state.filter.isActive) "Nenhum abastecimento no período"
                                      else "Nenhum abastecimento",
                        description = if (state.filter.isActive) "Tente selecionar outro período ou limpe o filtro."
                                      else "Registre seu primeiro abastecimento na aba Home.",
                        modifier    = Modifier.align(Alignment.Center),
                    )

                    // ── Erro ──────────────────────────────────────────────────
                    is HistoryScreenState.Error -> FFErrorState(
                        message  = s.error.userMessage(),
                        onRetry  = viewModel::load,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    // ── Conteúdo com infinite scroll ──────────────────────────
                    is HistoryScreenState.Success -> PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh    = viewModel::refresh,
                        modifier     = Modifier.fillMaxSize(),
                    ) {
                        val groupedItems = remember(s.items) { groupRefuelsByMonth(s.items) }
                        LazyColumn(
                            state          = listState,
                            modifier       = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = FFTheme.spacing.md),
                        ) {
                            itemsIndexed(
                                items       = groupedItems,
                                key         = { _, listItem ->
                                    when (listItem) {
                                        is HistoryListItem.Header -> "header_${listItem.yearMonth}"
                                        is HistoryListItem.Entry  -> listItem.refuel.id
                                    }
                                },
                                contentType = { _, listItem ->
                                    when (listItem) {
                                        is HistoryListItem.Header -> 0
                                        is HistoryListItem.Entry  -> 1
                                    }
                                },
                            ) { index, listItem ->
                                when (listItem) {
                                    is HistoryListItem.Header -> MonthGroupHeader(
                                        label    = listItem.label,
                                        count    = listItem.count,
                                        subtotal = listItem.subtotal,
                                    )
                                    is HistoryListItem.Entry -> Column {
                                        RefuelHistoryItem(
                                            item    = listItem.refuel,
                                            onClick = { onNavigateToDetails(listItem.refuel.id) },
                                        )
                                        if (groupedItems.getOrNull(index + 1) is HistoryListItem.Entry) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = FFTheme.spacing.md),
                                            )
                                        }
                                    }
                                }
                            }

                            // Footer: carregando próxima página
                            if (state.paginationState.isLoadingMore) {
                                item(key = "loading_more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(FFTheme.spacing.md),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            }

                            // Footer: erro na paginação com retry
                            state.paginationState.pageError?.let { error ->
                                item(key = "page_error") {
                                    HistoryPaginationError(
                                        message = error.userMessage(),
                                        onRetry = viewModel::retryNextPage,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Chip de veículo ativo ─────────────────────────────────────────────────────

@Composable
private fun ActiveVehicleChip(label: String) {
    AssistChip(
        onClick    = {},
        modifier   = Modifier.padding(start = FFTheme.spacing.md, top = FFTheme.spacing.xs),
        label      = { Text("Veículo Ativo: $label") },
        leadingIcon = {
            Icon(
                imageVector        = Icons.Default.DirectionsCar,
                contentDescription = null,
                modifier           = Modifier.size(18.dp),
            )
        },
    )
}

// ─── Painel de seleção de período ──────────────────────────────────────────────

@Composable
private fun HistoryFilterPanel(
    currentPreset: FilterPreset?,
    onPresetSelected: (FilterPreset?) -> Unit,
    onCustomSelected: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = FFTheme.spacing.md, end = FFTheme.spacing.md, bottom = FFTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs),
    ) {
        FilterChip(
            selected = currentPreset == null,
            onClick  = { onPresetSelected(null) },
            label    = { Text("Tudo") },
        )
        FilterChip(
            selected = currentPreset == FilterPreset.LAST_30_DAYS,
            onClick  = { onPresetSelected(FilterPreset.LAST_30_DAYS) },
            label    = { Text("30 dias") },
        )
        FilterChip(
            selected = currentPreset == FilterPreset.LAST_3_MONTHS,
            onClick  = { onPresetSelected(FilterPreset.LAST_3_MONTHS) },
            label    = { Text("3 meses") },
        )
        FilterChip(
            selected = currentPreset == FilterPreset.THIS_YEAR,
            onClick  = { onPresetSelected(FilterPreset.THIS_YEAR) },
            label    = { Text("Este ano") },
        )
        FilterChip(
            selected = currentPreset == FilterPreset.CUSTOM,
            onClick  = onCustomSelected,
            label    = { Text("Personalizado") },
        )
    }
}

// ─── Footer de erro de paginação ───────────────────────────────────────────────

@Composable
private fun HistoryPaginationError(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(FFTheme.spacing.md),
    ) {
        Text(
            text  = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) {
            Text("Tentar novamente")
        }
    }
}

// ─── Cabeçalho de mês ──────────────────────────────────────────────────────────

@Composable
private fun MonthGroupHeader(label: String, count: Int, subtotal: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start  = FFTheme.spacing.md,
                end    = FFTheme.spacing.md,
                top    = FFTheme.spacing.lg,
                bottom = FFTheme.spacing.xs,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Bottom,
    ) {
        Column {
            Text(
                text  = label,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text  = if (count == 1) "1 abastecimento" else "$count abastecimentos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text  = formatBrl(subtotal),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ─── Item da lista ─────────────────────────────────────────────────────────────

@Composable
private fun RefuelHistoryItem(item: RefuelItem, onClick: () -> Unit) {
    FFRefuelListItem(
        date        = DateFormatter.formatBr(item.date),
        stationName = refuelTypeLabel(item.refuelType),
        liters      = formatQuantity(item.energyAmount, item.refuelType),
        totalCost   = formatBrl(item.totalPrice),
        fuelType    = item.consumption?.let { formatConsumption(it, item.refuelType) },
        onClick     = onClick,
    )
}

// ─── Helpers de formatação e labels ───────────────────────────────────────────

private fun refuelTypeLabel(refuelType: String?): String =
    if (refuelType?.uppercase() == "ELECTRIC") "Elétrico" else "Combustível"

private fun formatQuantity(amount: Double, refuelType: String?): String {
    val unit      = if (refuelType?.uppercase() == "ELECTRIC") "kWh" else "L"
    val formatted = "%.2f".format(amount).replace('.', ',')
    return "$formatted $unit"
}

private fun formatConsumption(consumption: Double, refuelType: String?): String {
    val unit      = if (refuelType?.uppercase() == "ELECTRIC") "km/kWh" else "km/L"
    val formatted = "%.1f".format(consumption).replace('.', ',')
    return "$formatted $unit"
}

private val brlFormat: NumberFormat
    get() = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

private fun formatBrl(amount: Double): String = brlFormat.format(amount)

// ─── Agrupamento mensal ────────────────────────────────────────────────────────

private fun groupRefuelsByMonth(items: List<RefuelItem>): List<HistoryListItem> =
    items
        .groupBy { it.date.take(7) }       // "YYYY-MM"
        .entries
        .sortedByDescending { it.key }
        .flatMap { (yearMonth, monthItems) ->
            listOf(
                HistoryListItem.Header(
                    yearMonth = yearMonth,
                    label     = formatMonthLabel(yearMonth),
                    count     = monthItems.size,
                    subtotal  = monthItems.sumOf { it.totalPrice },
                ),
            ) + monthItems.map { HistoryListItem.Entry(it) }
        }

private fun formatMonthLabel(yearMonth: String): String {
    val parts = yearMonth.split("-")
    if (parts.size != 2) return yearMonth
    val month = parts[1].toIntOrNull() ?: return yearMonth
    val monthName = when (month) {
        1  -> "Janeiro";  2  -> "Fevereiro"; 3  -> "Março"
        4  -> "Abril";    5  -> "Maio";       6  -> "Junho"
        7  -> "Julho";    8  -> "Agosto";     9  -> "Setembro"
        10 -> "Outubro";  11 -> "Novembro";  12 -> "Dezembro"
        else -> return yearMonth
    }
    return "$monthName ${parts[0]}"
}
