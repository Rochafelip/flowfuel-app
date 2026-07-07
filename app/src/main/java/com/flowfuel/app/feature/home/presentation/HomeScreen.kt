package com.flowfuel.app.feature.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.components.FFEmptyState
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFFab
import com.flowfuel.app.core.designsystem.components.FFSkeletonBlock
import com.flowfuel.app.core.designsystem.components.FFSkeletonLine
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.home.domain.model.FinancialSummary
import com.flowfuel.app.feature.home.presentation.components.FinancialSummaryCard
import com.flowfuel.app.feature.home.presentation.components.IndicatorItem
import com.flowfuel.app.feature.home.presentation.components.IndicatorsGrid
import com.flowfuel.app.feature.home.presentation.components.InsightCard
import com.flowfuel.app.feature.home.presentation.components.LastRefuelCard
import com.flowfuel.app.feature.home.presentation.components.RecentActivityCard
import com.flowfuel.app.feature.home.presentation.components.VehicleHeader
import com.flowfuel.app.feature.home.presentation.components.formatBrl
import com.flowfuel.app.feature.home.presentation.components.formatKm
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleTimelineItem
import kotlinx.coroutines.flow.collectLatest
import java.util.Calendar

// ─── Tela principal ────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToAddVehicle: () -> Unit,
    openRefuelSheet: Boolean = false,
    onRefuelSheetOpened: () -> Unit = {},
    refreshTrigger: Boolean = false,
    onRefreshConsumed: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(openRefuelSheet) {
        if (openRefuelSheet) {
            viewModel.openRefuelSheet()
            onRefuelSheetOpened()
        }
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger) {
            viewModel.refresh()
            onRefreshConsumed()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                HomeEffect.NavigateToLogin -> onNavigateToLogin()
                HomeEffect.RefuelRegistered -> snackbarHostState.showSnackbar(
                    FFSnackbarVisuals(
                        message = "Abastecimento registrado com sucesso!",
                        kind = FFSnackbarKind.Success,
                        duration = SnackbarDuration.Short,
                    ),
                )
            }
        }
    }

    Scaffold(
        // Zera os insets do sistema: eles já foram consumidos pelo Scaffold
        // externo (MainContainerScreen), evitando padding duplicado.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.screenState is HomeScreenState.Success) {
                FFFab(
                    icon = Icons.Default.LocalGasStation,
                    contentDescription = "Registrar abastecimento",
                    onClick = viewModel::openRefuelSheet,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val s = state.screenState) {
                HomeScreenState.Loading -> HomeLoadingSkeleton(modifier = Modifier.fillMaxSize())

                is HomeScreenState.Error -> FFErrorState(
                    message = s.error.userMessage(),
                    onRetry = viewModel::load,
                    modifier = Modifier.align(Alignment.Center),
                )

                is HomeScreenState.Success -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    HomeContent(
                        vehicle = s.vehicle,
                        dashboard = s.dashboard,
                        financialSummary = s.financialSummary,
                        recentActivity = s.recentActivity,
                        onRegisterRefuel = viewModel::openRefuelSheet,
                        onVehicleClick = viewModel::openVehicleSwitcher,
                        onRetryFinancialSummary = viewModel::retryFinancialSummary,
                        onRetryRecentActivity = viewModel::retryRecentActivity,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (state.showRefuelSheet) {
        val energyType = (state.screenState as? HomeScreenState.Success)
            ?.vehicle?.energyType ?: ""
        QuickRefuelBottomSheet(
            form                      = state.refuelForm,
            isSubmitting              = state.isSubmittingRefuel,
            submitError               = state.submitError,
            energyType                = energyType,
            onOdometerInputModeChange = viewModel::onOdometerInputModeChange,
            onTripKmChange            = viewModel::onTripKmChange,
            onOdometerChange          = viewModel::onOdometerChange,
            onLitersChange = viewModel::onLitersChange,
            onTotalPriceInput = viewModel::onTotalPriceInput,
            onFullTankToggle = viewModel::onFullTankToggle,
            onRefuelTypeChange = viewModel::onRefuelTypeChange,
            onSubmit = viewModel::submitRefuel,
            onDismiss = viewModel::closeRefuelSheet,
        )
    }

    if (state.showVehicleSwitcher) {
        VehicleSwitcherBottomSheet(
            state = state.vehicleSwitcherState,
            onVehicleSelect = viewModel::onVehicleSwitch,
            onAddVehicle = {
                viewModel.closeVehicleSwitcher()
                onNavigateToAddVehicle()
            },
            onRetry = viewModel::openVehicleSwitcher,
            onDismiss = viewModel::closeVehicleSwitcher,
        )
    }
}

// ─── Conteúdo principal (estado Success) ──────────────────────────────────────

@Composable
private fun HomeContent(
    vehicle: ActiveVehicleData,
    dashboard: DashboardData,
    financialSummary: SectionState<FinancialSummary>,
    recentActivity: SectionState<List<VehicleTimelineItem>>,
    onRegisterRefuel: () -> Unit,
    onVehicleClick: () -> Unit,
    onRetryFinancialSummary: () -> Unit,
    onRetryRecentActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFirstUse = dashboard.totalRefuels == 0
    val daysSince = remember(dashboard.lastRefuelDate) { daysSinceRefuel(dashboard.lastRefuelDate) }
    val consumptionUnit = dashboard.consumptionUnit
        ?: if (vehicle.energyType.equals("ELECTRIC", ignoreCase = true)) "km/kWh" else "km/L"
    val consumptionValue = dashboard.averageConsumption?.let { "%.1f".format(it) } ?: "—"

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = FFTheme.spacing.md,
            vertical = FFTheme.spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap),
    ) {
        item {
            VehicleHeader(
                vehicle = vehicle,
                daysSinceLastRefuel = if (isFirstUse) null else daysSince,
                onVehicleClick = onVehicleClick,
            )
        }

        if (isFirstUse) {
            item {
                FFEmptyState(
                    title = "Pronto para começar",
                    description = "Registre seu primeiro abastecimento para ver seus indicadores e resumo financeiro.",
                    actionText = "Registrar abastecimento",
                    onAction = onRegisterRefuel,
                )
            }
        } else {
            item {
                when (financialSummary) {
                    is SectionState.Success -> FinancialSummaryCard(
                        currentMonthTotalLabel = formatBrl(financialSummary.value.currentMonthTotal),
                        percentDelta = financialSummary.value.percentDelta,
                    )
                    SectionState.Loading -> FFSkeletonBlock(height = 96.dp)
                    is SectionState.Error -> SectionErrorCard(onRetry = onRetryFinancialSummary)
                }
            }

            item {
                val averagePrice = (financialSummary as? SectionState.Success)?.value?.averagePricePerUnit
                IndicatorsGrid(
                    consumption = IndicatorItem("Consumo médio", consumptionValue, consumptionUnit),
                    averagePrice = IndicatorItem("Preço médio", averagePrice?.let(::formatBrl) ?: "—"),
                    odometer = IndicatorItem("Odômetro", formatKm(vehicle.currentKm.toDouble()), "km"),
                    lastRefuel = IndicatorItem("Último abastecimento", shortDaysSinceLabel(daysSince)),
                )
            }
        }

        item { InsightCard() }

        if (!isFirstUse) {
            item { LastRefuelCard(dashboard = dashboard) }

            item {
                when (recentActivity) {
                    is SectionState.Success -> RecentActivityCard(items = recentActivity.value)
                    SectionState.Loading -> FFSkeletonBlock(height = 160.dp)
                    is SectionState.Error -> SectionErrorCard(onRetry = onRetryRecentActivity)
                }
            }
        }

        // Espaço para o FAB não sobrepor o último item da lista.
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ─── Erro isolado por seção ────────────────────────────────────────────────────

@Composable
private fun SectionErrorCard(onRetry: () -> Unit) {
    FFCard(variant = FFCardVariant.Flat) {
        Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
            Text(
                text = "Não foi possível carregar esta seção.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRetry) {
                Text("Tentar novamente")
            }
        }
    }
}

// ─── Skeleton de carregamento ─────────────────────────────────────────────────

@Composable
private fun HomeLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(FFTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap),
    ) {
        FFSkeletonBlock(height = 56.dp)
        FFSkeletonBlock(height = 96.dp)
        FFSkeletonBlock(height = 176.dp)
        FFSkeletonBlock(height = 96.dp)
        FFSkeletonBlock(height = 160.dp)
        FFSkeletonLine(widthFraction = 0.6f)
        FFSkeletonLine(widthFraction = 0.4f)
    }
}

// ─── Helpers de data ──────────────────────────────────────────────────────────

private fun daysSinceRefuel(lastRefuelDate: String?): Int? {
    lastRefuelDate ?: return null
    return runCatching {
        val datePart = lastRefuelDate.take(10)
        val parts = datePart.split("-")
        val refuel = Calendar.getInstance().apply {
            set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        ((today.timeInMillis - refuel.timeInMillis) / 86_400_000L).toInt()
    }.getOrNull()
}

private fun shortDaysSinceLabel(days: Int?): String = when {
    days == null -> "—"
    days == 0 -> "Hoje"
    days == 1 -> "Ontem"
    else -> "Há $days dias"
}
