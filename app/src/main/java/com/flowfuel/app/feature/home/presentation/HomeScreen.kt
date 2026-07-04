package com.flowfuel.app.feature.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.Card
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.flowfuel.app.core.designsystem.components.FFAutoSizeText
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFSkeletonBlock
import com.flowfuel.app.core.designsystem.components.FFSkeletonLine
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.components.FFStatTile
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.components.FFTopBarVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.home.domain.model.HybridConsumptionBreakdown
import kotlinx.coroutines.flow.collectLatest
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

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

    // Coleta efeitos de navegação e feedback
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

    // Título da TopBar varia conforme o estado
    val topBarTitle = (state.screenState as? HomeScreenState.Success)
        ?.let { "${it.vehicle.brand} ${it.vehicle.model}" }
        ?: "FlowFuel"

    // Título só fica clicável quando há veículo ativo carregado com sucesso
    val onTitleClick = if (state.screenState is HomeScreenState.Success)
        viewModel::openVehicleSwitcher
    else
        null

    Scaffold(
        // Zera os insets do sistema: eles já foram consumidos pelo Scaffold
        // externo (MainContainerScreen), evitando padding duplicado.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            FFTopBar(
                title = topBarTitle,
                variant = FFTopBarVariant.Small,
                onTitleClick = onTitleClick,
            )
        },
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
        bottomBar = {
            // CTA fixo na parte inferior: visível apenas quando carregado com sucesso
            Surface(shadowElevation = 8.dp) {
                Box(
                    modifier = Modifier.padding(
                        horizontal = FFTheme.spacing.md,
                        vertical = FFTheme.spacing.sm,
                    ),
                ) {
                    FFButton(
                        text = "Registrar abastecimento",
                        onClick = viewModel::openRefuelSheet,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = Icons.Default.LocalGasStation,
                        enabled = state.screenState is HomeScreenState.Success,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val s = state.screenState) {
                // ── Carregando ────────────────────────────────────────────────
                HomeScreenState.Loading -> HomeLoadingSkeleton(
                    modifier = Modifier.fillMaxSize(),
                )

                // ── Erro global ───────────────────────────────────────────────
                is HomeScreenState.Error -> FFErrorState(
                    message = s.error.userMessage(),
                    onRetry = viewModel::load,
                    modifier = Modifier.align(Alignment.Center),
                )

                // ── Conteúdo ──────────────────────────────────────────────────
                is HomeScreenState.Success -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    HomeContent(
                        vehicle = s.vehicle,
                        dashboard = s.dashboard,
                        onRegisterRefuel = viewModel::openRefuelSheet,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    // Bottom sheet de abastecimento (renderizado fora do Scaffold para sobrepor tudo)
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

    // Bottom sheet do seletor de veículo
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
    onRegisterRefuel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFirstUse = dashboard.totalRefuels == 0

    // Prioriza a unidade informada pelo backend; cai para inferência client-side
    // só se o servidor não a enviar (ex: resposta antiga/incompleta).
    val consumptionUnit = dashboard.consumptionUnit
        ?: if (vehicle.energyType.equals("ELECTRIC", ignoreCase = true)) "km/kWh" else "km/L"
    val consumptionValue = dashboard.averageConsumption
        ?.let { "%.1f".format(it) }
        ?: "—"

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = FFTheme.spacing.md,
            vertical = FFTheme.spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap),
    ) {
        // ── 0. Saudação contextual ─────────────────────────────────────────
        item {
            GreetingBanner(
                vehicle = vehicle,
                lastRefuelDate = if (isFirstUse) null else dashboard.lastRefuelDate,
            )
        }

        // ── 1. Hero: boas-vindas (primeiro uso) ou consumo médio ──────────
        item {
            when {
                isFirstUse -> WelcomeHeroCard(
                    vehicleName = "${vehicle.brand} ${vehicle.model}",
                    onRegisterRefuel = onRegisterRefuel,
                )
                vehicle.energyType.equals("HYBRID", ignoreCase = true)
                        && dashboard.hybridBreakdown != null -> HybridConsumptionHeroCard(
                    breakdown = dashboard.hybridBreakdown,
                    totalRefuels = dashboard.totalRefuels,
                    fuelLabel = vehicle.fuelSubType ?: "Híbrido",
                )
                else -> ConsumptionHeroCard(
                    value = consumptionValue,
                    unit = consumptionUnit,
                    totalRefuels = dashboard.totalRefuels,
                    fuelLabel = vehicle.fuelSubType ?: vehicle.energyType,
                )
            }
        }

        // ── 2. Stats: odômetro (+ gasto total quando há dados) ────────────
        item {
            val displayedOdometer = vehicle.currentKm.toDouble()

            if (isFirstUse) {
                FFStatTile(
                    label = "Odômetro atual",
                    value = formatKm(displayedOdometer),
                    unit = "km",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap)) {
                    FFStatTile(
                        label = "Odômetro atual",
                        value = formatKm(displayedOdometer),
                        unit = "km",
                        modifier = Modifier.weight(1f),
                    )
                    FFStatTile(
                        label = "Gasto total",
                        value = formatBrl(dashboard.totalSpent),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ── 3. Último abastecimento (oculto no primeiro uso) ───────────────
        if (!isFirstUse) {
            item {
                LastRefuelCard(dashboard = dashboard)
            }
        }

        // Espaçamento extra para o botão fixo não sobrepor o conteúdo
        item { Spacer(Modifier.height(FFTheme.spacing.sm)) }
    }
}

// ─── Card hero: boas-vindas (primeiro uso) ───────────────────────────────────

@Composable
private fun WelcomeHeroCard(
    vehicleName: String,
    onRegisterRefuel: () -> Unit,
) {
    val brandGreen = FFTheme.semanticColors.brandGreen
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FFTheme.extraShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = FFTheme.elevation.level1),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(brandGreen),
            )
            Column(
                modifier = Modifier
                    .padding(FFTheme.spacing.lg)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Default.LocalGasStation,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = brandGreen,
                )

                Spacer(Modifier.height(FFTheme.spacing.xs))

                Text(
                    text = "$vehicleName pronto para rodar!",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "Registre seu primeiro abastecimento para começar a acompanhar o consumo e os gastos do veículo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(FFTheme.spacing.xs))

                FFButton(
                    text = "Registrar primeiro abastecimento",
                    onClick = onRegisterRefuel,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Default.LocalGasStation,
                )
            }
        }
    }
}

// ─── Card hero: consumo médio ──────────────────────────────────────────────────

@Composable
private fun ConsumptionHeroCard(
    value: String,
    unit: String,
    totalRefuels: Int,
    fuelLabel: String,
) {
    val brandGreen = FFTheme.semanticColors.brandGreen
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FFTheme.extraShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = FFTheme.elevation.level1),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(brandGreen),
            )
            Column(
                modifier = Modifier.padding(FFTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs),
            ) {
                Text(
                    text = fuelLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(FFTheme.spacing.xs))

                Text(
                    text = "Consumo médio",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                ) {
                    FFAutoSizeText(
                        text = value,
                        style = FFTheme.numericTypography.numericLarge,
                        color = brandGreen,
                    )
                    if (value != "—") {
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.height(FFTheme.spacing.xs))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Spacer(Modifier.height(FFTheme.spacing.xs))

                Text(
                    text = when {
                        totalRefuels == 0 -> "Registre o primeiro abastecimento para calcular o consumo"
                        totalRefuels == 1 -> "Baseado em 1 abastecimento • mínimo 2 para calcular"
                        else -> "Baseado em $totalRefuels abastecimentos"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Card hero: consumo híbrido (combustão + elétrico separados) ─────────────

@Composable
private fun HybridConsumptionHeroCard(
    breakdown: HybridConsumptionBreakdown,
    totalRefuels: Int,
    fuelLabel: String,
) {
    val brandGreen = FFTheme.semanticColors.brandGreen
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FFTheme.extraShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = FFTheme.elevation.level1),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(brandGreen),
            )
            Column(
                modifier = Modifier.padding(FFTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs),
            ) {
                Text(
                    text = fuelLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(FFTheme.spacing.xs))

                Text(
                    text = "Consumo médio",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(FFTheme.spacing.xs))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
                ) {
                    HybridMetricColumn(
                        icon = Icons.Default.LocalGasStation,
                        label = "Combustão",
                        value = breakdown.fuelConsumption?.let { "%.1f".format(it) } ?: "—",
                        unit = breakdown.fuelConsumptionUnit ?: "km/L",
                        accentColor = brandGreen,
                        modifier = Modifier.weight(1f),
                    )
                    HybridMetricColumn(
                        icon = Icons.Default.Bolt,
                        label = "Elétrico",
                        value = breakdown.electricConsumption?.let { "%.1f".format(it) } ?: "—",
                        unit = breakdown.electricConsumptionUnit ?: "km/kWh",
                        accentColor = brandGreen,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(FFTheme.spacing.xs))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Spacer(Modifier.height(FFTheme.spacing.xs))

                Text(
                    text = when {
                        totalRefuels == 0 -> "Registre o primeiro abastecimento para calcular o consumo"
                        totalRefuels == 1 -> "Baseado em 1 abastecimento • mínimo 2 para calcular"
                        else -> "Baseado em $totalRefuels abastecimentos"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HybridMetricColumn(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = FFTheme.numericTypography.numericMedium,
                color = accentColor,
            )
            if (value != "—") {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

// ─── Card: último abastecimento ───────────────────────────────────────────────

@Composable
private fun LastRefuelCard(dashboard: DashboardData) {
    FFCard(
        title = "Último abastecimento",
        variant = FFCardVariant.Flat,
    ) {
        if (dashboard.totalRefuels == 0 || dashboard.lastRefuelDate == null) {
            Text(
                text = "Nenhum abastecimento registrado ainda.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                // Data
                LastRefuelRow(
                    label = "Data",
                    value = formatDate(dashboard.lastRefuelDate),
                )
                // Quantidade (litros ou kWh, conforme o tipo de energia)
                if (dashboard.lastRefuelEnergyAmount != null) {
                    val unit = dashboard.lastRefuelEnergyUnit ?: "L"
                    LastRefuelRow(
                        label = if (unit == "kWh") "Energia" else "Litros",
                        value = "%.2f %s".format(dashboard.lastRefuelEnergyAmount, unit)
                            .replace('.', ','),
                    )
                }
                // Valor
                if (dashboard.lastRefuelAmount != null) {
                    LastRefuelRow(
                        label = "Valor pago",
                        value = formatBrl(dashboard.lastRefuelAmount),
                    )
                }
                // Preço por unidade calculado
                if (dashboard.lastRefuelEnergyAmount != null && dashboard.lastRefuelAmount != null
                    && dashboard.lastRefuelEnergyAmount > 0.0
                ) {
                    val pricePerUnit = dashboard.lastRefuelAmount / dashboard.lastRefuelEnergyAmount
                    LastRefuelRow(
                        label = if (dashboard.lastRefuelEnergyUnit == "kWh") "Preço/kWh" else "Preço/litro",
                        value = formatBrl(pricePerUnit),
                        highlight = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun LastRefuelRow(
    label: String,
    value: String,
    highlight: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ─── Saudação contextual ─────────────────────────────────────────────────────

@Composable
private fun GreetingBanner(
    vehicle: ActiveVehicleData,
    lastRefuelDate: String?,
) {
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Bom dia"
            hour < 18 -> "Boa tarde"
            else -> "Boa noite"
        }
    }
    val daysSince: Int? = remember(lastRefuelDate) {
        lastRefuelDate ?: return@remember null
        runCatching {
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
    val vehicleName = "${vehicle.brand} ${vehicle.model}"
    val message = when {
        daysSince == null -> "$greeting! Seu $vehicleName está pronto para rodar."
        daysSince == 0 -> "$greeting! Você abasteceu o $vehicleName hoje."
        daysSince == 1 -> "$greeting! Último abastecimento foi ontem."
        daysSince in 2..7 -> "$greeting! Último abastecimento há $daysSince dias."
        else -> "$greeting! Seu $vehicleName está há $daysSince dias sem abastecer."
    }
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ─── Skeleton de carregamento ─────────────────────────────────────────────────

@Composable
private fun HomeLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(FFTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap),
    ) {
        // Hero card skeleton
        FFSkeletonBlock(height = 148.dp)
        // Stats row skeleton
        Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap)) {
            FFSkeletonBlock(modifier = Modifier.weight(1f), height = 88.dp)
            FFSkeletonBlock(modifier = Modifier.weight(1f), height = 88.dp)
        }
        // Last refuel card skeleton
        FFSkeletonBlock(height = 112.dp)
        // Sub-lines
        FFSkeletonLine(widthFraction = 0.6f)
        FFSkeletonLine(widthFraction = 0.4f)
    }
}

// ─── Helpers de formatação ────────────────────────────────────────────────────

private val brlFormat: NumberFormat
    get() = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

private val kmFormat: NumberFormat
    get() = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }

private fun formatBrl(amount: Double): String = brlFormat.format(amount)

private fun formatKm(km: Double): String = kmFormat.format(km)

/**
 * Converte uma data ISO-8601 (ex: "2024-01-15T10:30:00") para "15/01/2024".
 * Se a string não estiver no formato esperado, retorna os primeiros 10 caracteres.
 */
private fun formatDate(iso: String): String {
    val datePart = iso.take(10)
    val parts = datePart.split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else datePart
}