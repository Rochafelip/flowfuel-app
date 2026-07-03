package com.flowfuel.app.feature.vehicle.presentation.details

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFSkeletonBlock
import com.flowfuel.app.core.designsystem.components.FFSkeletonLine
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.components.FFTopBarVariant
import com.flowfuel.app.core.designsystem.components.FFFab
import com.flowfuel.app.core.designsystem.components.VehiclePhotoAvatar
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale

// ─── Tela de detalhes do veículo ──────────────────────────────────────────────

@Composable
fun VehicleDetailsScreen(
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToEdit: (vehicleId: Int) -> Unit,
    onNavigateToUpdateOdometer: (vehicleId: Int, currentKm: Int) -> Unit,
    onNavigateToEvents: (vehicleId: Int) -> Unit,
    odometerUpdated: Boolean = false,
    onOdometerUpdatedConsumed: () -> Unit = {},
    viewModel: VehicleDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(odometerUpdated) {
        if (odometerUpdated) {
            viewModel.refresh()
            onOdometerUpdatedConsumed()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is VehicleDetailsEffect.NavigateToEdit -> onNavigateToEdit(effect.vehicleId)
                is VehicleDetailsEffect.NavigateToUpdateOdometer ->
                    onNavigateToUpdateOdometer(effect.vehicleId, effect.currentKm)
                is VehicleDetailsEffect.NavigateToEvents -> onNavigateToEvents(effect.vehicleId)
                VehicleDetailsEffect.NavigateBack -> onBack()
                VehicleDetailsEffect.NavigateToLogin -> onNavigateToLogin()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            FFTopBar(
                title = "Detalhes do Veículo",
                variant = FFTopBarVariant.Small,
                onBack = onBack,
            )
        },
        floatingActionButton = {
            if (state.screenState is VehicleDetailsScreenState.Success) {
                FFFab(
                    icon = Icons.Default.Edit,
                    contentDescription = "Editar veículo",
                    onClick = viewModel::onEditClick,
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
                VehicleDetailsScreenState.Loading -> VehicleDetailsShimmer(
                    modifier = Modifier.fillMaxSize(),
                )

                is VehicleDetailsScreenState.Success -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    VehicleDetailsContent(
                        vehicle = s.vehicle,
                        onUpdateOdometerClick = viewModel::onUpdateOdometerClick,
                        onViewEventsClick = viewModel::onViewEventsClick,
                    )
                }

                is VehicleDetailsScreenState.Error -> {
                    val isNotFound = s.error is AppError.Api &&
                        (s.error.code == "RESOURCE_NOT_FOUND" || s.error.code == "HTTP_404")
                    if (isNotFound) {
                        FFErrorState(
                            title = "Veículo não encontrado",
                            message = "Este veículo não existe ou foi removido.",
                            actionText = "Voltar",
                            onRetry = onBack,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        FFErrorState(
                            message = s.error.userMessage(),
                            onRetry = viewModel::load,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }
    }
}

// ─── Conteúdo do estado de sucesso ────────────────────────────────────────────

@Composable
private fun VehicleDetailsContent(
    vehicle: Vehicle,
    onUpdateOdometerClick: () -> Unit,
    onViewEventsClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = FFTheme.spacing.md,
            end = FFTheme.spacing.md,
            top = FFTheme.spacing.md,
            bottom = FFTheme.spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
    ) {
        item { VehicleHeader(vehicle = vehicle) }

        item { HorizontalDivider() }

        item {
            FFCard(title = "Identificação") {
                Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                    VehicleInfoRow(Icons.Default.Tag, "Placa", vehicle.licensePlate ?: "—")
                    VehicleInfoRow(Icons.Default.Palette, "Cor", vehicle.color ?: "—")
                    VehicleInfoRow(
                        Icons.Default.CalendarMonth,
                        "Fabricação",
                        vehicle.manufactureYear?.toString() ?: "—",
                    )
                    VehicleInfoRow(
                        Icons.Default.CalendarToday,
                        "Modelo",
                        vehicle.modelYear?.toString() ?: "—",
                    )
                }
            }
        }

        item {
            FFCard(title = "Tipo & Energia") {
                Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                    VehicleInfoRow(
                        icon = if (vehicle.type == VehicleType.Motorcycle) Icons.Default.TwoWheeler
                               else Icons.Default.DirectionsCar,
                        label = "Tipo",
                        value = when (vehicle.type) {
                            VehicleType.Car -> "Carro"
                            VehicleType.Motorcycle -> "Moto"
                        },
                    )
                    VehicleInfoRow(
                        icon = Icons.Default.Bolt,
                        label = "Energia",
                        value = when (vehicle.energyType) {
                            EnergyType.Combustion -> "Combustão"
                            EnergyType.Electric -> "Elétrico"
                            EnergyType.Hybrid -> "Híbrido"
                        },
                    )
                    val fuelType = vehicle.fuelType
                    if (vehicle.energyType != EnergyType.Electric && fuelType != null) {
                        VehicleInfoRow(
                            icon = Icons.Default.LocalGasStation,
                            label = "Combustível",
                            value = when (fuelType) {
                                FuelType.Gasoline -> "Gasolina"
                                FuelType.Ethanol -> "Etanol"
                                FuelType.Diesel -> "Diesel"
                                FuelType.Flex -> "Flex"
                                FuelType.GNV -> "GNV"
                            },
                        )
                    }
                }
            }
        }

        item {
            FFCard(title = "Telemetria") {
                Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                    VehicleInfoRow(
                        Icons.Default.Speed,
                        "Odômetro",
                        String.format(Locale("pt", "BR"), "%,d km", vehicle.odometerKm),
                    )
                    if (vehicle.energyType == EnergyType.Combustion || vehicle.energyType == EnergyType.Hybrid) {
                        VehicleInfoRow(
                            Icons.Default.LocalGasStation,
                            "Cap. tanque",
                            vehicle.tankCapacityL?.let { "%.0f L".format(it) } ?: "—",
                        )
                    }
                    if (vehicle.energyType == EnergyType.Electric || vehicle.energyType == EnergyType.Hybrid) {
                        VehicleInfoRow(
                            Icons.Default.BatteryFull,
                            "Cap. bateria",
                            vehicle.batteryCapacityKwh?.let { "%.0f kWh".format(it) } ?: "—",
                        )
                    }
                }
            }
        }

        item {
            FFCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    VehicleStatusBadge(isActive = vehicle.isActive)
                }
            }
        }

        item {
            FFButton(
                text = "Atualizar odômetro",
                onClick = onUpdateOdometerClick,
                variant = FFButtonVariant.Secondary,
                leadingIcon = Icons.Default.Speed,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            FFButton(
                text = "Histórico de Eventos",
                onClick = onViewEventsClick,
                variant = FFButtonVariant.Secondary,
                leadingIcon = Icons.Default.CalendarMonth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─── Header com avatar, título e chips de tipo/energia ────────────────────────

@Composable
private fun VehicleHeader(vehicle: Vehicle) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
    ) {
        VehiclePhotoAvatar(
            photoUrl = vehicle.photoUrl,
            vehicleType = vehicle.type,
            size = 64.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs),
        ) {
            Text(
                text = "${vehicle.brand} ${vehicle.model}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VehicleTypeBadge(type = vehicle.type)
                EnergyTypeBadge(energyType = vehicle.energyType)
            }
        }
    }
}

// ─── Linha de informação: ícone + label + valor ───────────────────────────────

@Composable
private fun VehicleInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ─── Badge de tipo do veículo (Carro / Moto) ─────────────────────────────────

@Composable
private fun VehicleTypeBadge(type: VehicleType) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = FFTheme.extraShapes.pill,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FFTheme.spacing.sm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (type == VehicleType.Motorcycle) Icons.Default.TwoWheeler
                              else Icons.Default.DirectionsCar,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = when (type) {
                    VehicleType.Car -> "Carro"
                    VehicleType.Motorcycle -> "Moto"
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

// ─── Badge de tipo de energia ─────────────────────────────────────────────────

@Composable
private fun EnergyTypeBadge(energyType: EnergyType) {
    val (containerColor, contentColor, label) = when (energyType) {
        EnergyType.Combustion -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "Combustão",
        )
        EnergyType.Electric -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Elétrico",
        )
        EnergyType.Hybrid -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Híbrido",
        )
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = FFTheme.extraShapes.pill,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = FFTheme.spacing.sm, vertical = 4.dp),
        )
    }
}

// ─── Badge de status ativo/inativo ────────────────────────────────────────────

@Composable
private fun VehicleStatusBadge(isActive: Boolean) {
    val containerColor = if (isActive) FFTheme.semanticColors.success
                         else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isActive) FFTheme.semanticColors.onSuccess
                       else MaterialTheme.colorScheme.onSurfaceVariant
    val label = if (isActive) "Ativo" else "Inativo"
    val icon = if (isActive) Icons.Default.CheckCircle else Icons.Default.Cancel

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = FFTheme.extraShapes.pill,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FFTheme.spacing.sm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(14.dp),
            )
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ─── Shimmer do layout de detalhes ───────────────────────────────────────────

@Composable
private fun VehicleDetailsShimmer(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(FFTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(64.dp).clip(CircleShape)) {
                FFSkeletonBlock(height = 64.dp)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs),
            ) {
                FFSkeletonLine(widthFraction = 0.65f, height = 22.dp)
                FFSkeletonLine(widthFraction = 0.45f, height = 14.dp)
            }
        }
        HorizontalDivider()
        FFSkeletonBlock(height = 172.dp)
        FFSkeletonBlock(height = 132.dp)
        FFSkeletonBlock(height = 100.dp)
        FFSkeletonBlock(height = 56.dp)
        FFSkeletonBlock(height = 48.dp)
        Spacer(modifier = Modifier.height(FFTheme.spacing.xxl))
    }
}
