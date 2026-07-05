package com.flowfuel.app.feature.history.presentation.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.components.FFTopBarVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import kotlinx.coroutines.flow.collectLatest
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─── Tela de detalhes do abastecimento ───────────────────────────────────────

@Composable
fun RefuelDetailsScreen(
    onBack: () -> Unit,
    onNavigateToEdit: (() -> Unit)? = null,
    onDeletedNavigateBack: (() -> Unit)? = null,
    refuelUpdated: Boolean = false,
    onRefuelUpdatedConsumed: () -> Unit = {},
    viewModel: RefuelDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                RefuelDetailsEffect.Deleted -> onDeletedNavigateBack?.invoke()
            }
        }
    }

    LaunchedEffect(refuelUpdated) {
        if (refuelUpdated) {
            viewModel.load()
            snackbarHostState.showSnackbar("Abastecimento atualizado")
            onRefuelUpdatedConsumed()
        }
    }

    val deleteErrorMessage = state.deleteError?.userMessage()
    LaunchedEffect(deleteErrorMessage) {
        if (deleteErrorMessage != null) {
            snackbarHostState.showSnackbar(deleteErrorMessage)
        }
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title   = { Text("Remover abastecimento?") },
            text    = { Text("Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Remover", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Cancelar") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FFTopBar(
                title   = "Abastecimento",
                variant = FFTopBarVariant.Small,
                onBack  = onBack,
                actions = {
                    IconButton(onClick = { onNavigateToEdit?.invoke() }) {
                        Icon(
                            imageVector        = Icons.Default.Edit,
                            contentDescription = "Editar abastecimento",
                        )
                    }
                    IconButton(
                        onClick  = viewModel::requestDelete,
                        enabled  = !state.isDeleting,
                    ) {
                        if (state.isDeleting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(
                                imageVector        = Icons.Default.Delete,
                                contentDescription = "Remover abastecimento",
                                tint               = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val s = state.screenState) {
                RefuelDetailsScreenState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                is RefuelDetailsScreenState.Error -> FFErrorState(
                    message  = s.error.userMessage(),
                    onRetry  = viewModel::load,
                    modifier = Modifier.align(Alignment.Center),
                )

                is RefuelDetailsScreenState.Success -> RefuelDetailsContent(item = s.item)
            }
        }
    }
}

// ─── Conteúdo do estado de sucesso ────────────────────────────────────────────

@Composable
private fun RefuelDetailsContent(item: RefuelItem) {
    val isElectric = item.refuelType?.uppercase() == "ELECTRIC"

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start  = FFTheme.spacing.md,
            end    = FFTheme.spacing.md,
            top    = FFTheme.spacing.md,
            bottom = FFTheme.spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
    ) {
        // ── Resumo ────────────────────────────────────────────────────────────
        item(key = "resumo") {
            FFCard(title = "Resumo") {
                Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                    RefuelInfoRow(
                        icon  = Icons.Default.CalendarToday,
                        label = "Data",
                        value = formatDetailDate(item.date),
                    )
                    RefuelInfoRow(
                        icon  = Icons.Default.LocalGasStation,
                        label = "Tipo",
                        value = refuelTypeLabel(item.refuelType),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                    ) {
                        Icon(
                            imageVector        = Icons.Default.WaterDrop,
                            contentDescription = null,
                            modifier           = Modifier.size(20.dp),
                            tint               = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text     = "Tanque cheio",
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.fullTank) {
                            FullTankBadge()
                        } else {
                            Text(
                                text  = "Não",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        // ── Abastecimento ─────────────────────────────────────────────────────
        item(key = "abastecimento") {
            val quantityUnit = if (isElectric) "kWh" else "L"
            val priceUnit    = if (isElectric) "kWh" else "L"

            FFCard(title = "Abastecimento") {
                Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                    RefuelInfoRow(
                        icon  = Icons.Default.LocalGasStation,
                        label = "Quantidade",
                        value = "${"%.2f".format(item.energyAmount).replace('.', ',')} $quantityUnit",
                    )
                    RefuelInfoRow(
                        icon  = Icons.Default.Payments,
                        label = "Preço/$priceUnit",
                        value = "${formatBrl(item.pricePerUnit)}/$priceUnit",
                    )
                    RefuelInfoRow(
                        icon  = Icons.Default.Payments,
                        label = "Total",
                        value = formatBrl(item.totalPrice),
                    )
                }
            }
        }

        // ── Desempenho ────────────────────────────────────────────────────────
        item(key = "desempenho") {
            val consumptionUnit = if (isElectric) "km/kWh" else "km/L"

            FFCard(title = "Desempenho") {
                Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                    RefuelInfoRow(
                        icon  = Icons.Default.Speed,
                        label = "Odômetro",
                        value = item.odometer?.let { formatOdometer(it) } ?: "–",
                    )
                    RefuelInfoRow(
                        icon  = Icons.Default.Speed,
                        label = "Km percorridos",
                        value = item.trip?.let { "${"%.0f".format(it).replace('.', ',')} km" } ?: "–",
                    )
                    val consumptionValue = item.consumption
                        ?: item.trip?.let { t ->
                            if (item.energyAmount > 0.0) t / item.energyAmount else null
                        }
                    RefuelInfoRow(
                        icon  = Icons.Default.Speed,
                        label = "Consumo",
                        value = consumptionValue?.let { "${"%.1f".format(it).replace('.', ',')} $consumptionUnit" } ?: "–",
                    )
                }
            }
        }
    }
}

// ─── Linha de informação ──────────────────────────────────────────────────────

@Composable
private fun RefuelInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            modifier           = Modifier.size(20.dp),
            tint               = MaterialTheme.colorScheme.primary,
        )
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text  = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ─── Badge "Tanque Cheio" ─────────────────────────────────────────────────────

@Composable
private fun FullTankBadge() {
    Surface(
        color        = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape        = FFTheme.extraShapes.pill,
    ) {
        Text(
            text     = "Tanque Cheio",
            style    = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = FFTheme.spacing.sm, vertical = 4.dp),
        )
    }
}

// ─── Helpers de formatação ────────────────────────────────────────────────────

private fun refuelTypeLabel(refuelType: String?): String = when (refuelType?.uppercase()) {
    "ELECTRIC" -> "Elétrico"
    "FUEL"     -> "Combustível"
    else       -> "–"
}

private val brlFormat: NumberFormat
    get() = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

private fun formatBrl(amount: Double): String = brlFormat.format(amount)

private fun formatOdometer(km: Double): String {
    val formatted = NumberFormat.getNumberInstance(Locale("pt", "BR")).format(km.toLong())
    return "$formatted km"
}

private val monthNameFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("pt", "BR"))

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH'h'mm")

private fun formatDetailDate(iso: String): String {
    if (iso.isBlank()) return "–"
    return runCatching {
        val dt       = LocalDateTime.parse(iso.take(19))
        val datePart = dt.format(monthNameFmt)
        val timePart = dt.format(timeFmt)
        "$datePart · $timePart"
    }.getOrElse {
        runCatching {
            LocalDate.parse(iso.take(10)).format(monthNameFmt)
        }.getOrDefault(iso.take(10))
    }
}
