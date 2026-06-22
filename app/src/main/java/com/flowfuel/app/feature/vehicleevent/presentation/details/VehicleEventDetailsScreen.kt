package com.flowfuel.app.feature.vehicleevent.presentation.details

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFSkeletonBlock
import com.flowfuel.app.core.designsystem.components.FFSkeletonLine
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.components.FFTopBarVariant
import com.flowfuel.app.core.designsystem.components.FFFab
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.presentation.components.DeleteEventDialog
import com.flowfuel.app.feature.vehicleevent.presentation.components.EventCategoryChip
import kotlinx.coroutines.flow.collectLatest
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun VehicleEventDetailsScreen(
    onBack: () -> Unit,
    onNavigateToEdit: (eventId: Int) -> Unit,
    onDeletedBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    eventUpdated: Boolean = false,
    onEventUpdatedConsumed: () -> Unit = {},
    viewModel: VehicleEventDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(eventUpdated) {
        if (eventUpdated) {
            viewModel.refresh()
            onEventUpdatedConsumed()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is VehicleEventDetailsEffect.NavigateToEdit -> onNavigateToEdit(effect.eventId)
                VehicleEventDetailsEffect.NavigateBack -> onDeletedBack()
                is VehicleEventDetailsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(
                    FFSnackbarVisuals(effect.message, FFSnackbarKind.Error),
                )
                VehicleEventDetailsEffect.NavigateToLogin -> onNavigateToLogin()
            }
        }
    }

    if (state.showDeleteDialog) {
        DeleteEventDialog(
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::onDeleteDismiss,
            isDeleting = state.isDeleting,
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
        topBar = {
            FFTopBar(
                title = "Detalhes do Evento",
                variant = FFTopBarVariant.Small,
                onBack = onBack,
                actions = {
                    if (state.screenState is VehicleEventDetailsScreenState.Success) {
                        OverflowMenu(onDeleteClick = viewModel::onDeleteRequest)
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.screenState is VehicleEventDetailsScreenState.Success) {
                FFFab(
                    icon = Icons.Default.Edit,
                    contentDescription = "Editar evento",
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
                VehicleEventDetailsScreenState.Loading -> VehicleEventDetailsShimmer(
                    modifier = Modifier.fillMaxSize(),
                )

                is VehicleEventDetailsScreenState.Success -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    VehicleEventDetailsContent(event = s.event)
                }

                is VehicleEventDetailsScreenState.Error -> FFErrorState(
                    message = s.error.userMessage(),
                    onRetry = viewModel::load,
                    modifier = Modifier.align(Alignment.Center),
                )

                VehicleEventDetailsScreenState.NotFound -> FFErrorState(
                    title = "Evento não encontrado",
                    message = "Este evento não existe mais.",
                    actionText = "Voltar",
                    onRetry = onBack,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

// ─── Menu overflow ────────────────────────────────────────────────────────────

@Composable
private fun OverflowMenu(onDeleteClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Excluir", color = MaterialTheme.colorScheme.error) },
            onClick = {
                expanded = false
                onDeleteClick()
            },
        )
    }
}

// ─── Conteúdo do estado de sucesso ────────────────────────────────────────────

@Composable
private fun VehicleEventDetailsContent(event: VehicleEvent) {
    // Extract nullable fields to local vals so smart-casts work inside lazy item lambdas
    val description = event.description
    val odometerKm = event.odometerKm
    val amount = event.amount
    val notes = event.notes

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
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                EventCategoryChip(category = event.category)
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "info") {
            FFCard(title = "Informações") {
                Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                    EventInfoRow(
                        icon = Icons.Default.CalendarToday,
                        label = "Data",
                        value = formatEventDate(event.eventDate),
                    )
                    if (odometerKm != null) {
                        EventInfoRow(
                            icon = Icons.Default.Speed,
                            label = "Quilometragem",
                            value = formatOdometer(odometerKm),
                        )
                    }
                }
            }
        }

        if (amount != null) {
            item(key = "financial") {
                FFCard(title = "Financeiro") {
                    EventInfoRow(
                        icon = Icons.Default.Payments,
                        label = "Valor",
                        value = formatAmount(amount),
                    )
                }
            }
        }

        if (!notes.isNullOrBlank()) {
            item(key = "notes") {
                FFCard(title = "Observações") {
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        item(key = "spacer") { Spacer(modifier = Modifier.height(FFTheme.spacing.xxl)) }
    }
}

// ─── Linha de informação: ícone + label + valor ───────────────────────────────

@Composable
private fun EventInfoRow(icon: ImageVector, label: String, value: String) {
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

// ─── Shimmer ──────────────────────────────────────────────────────────────────

@Composable
private fun VehicleEventDetailsShimmer(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(FFTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
    ) {
        FFSkeletonLine(widthFraction = 0.3f, height = 22.dp)
        FFSkeletonLine(widthFraction = 0.7f, height = 28.dp)
        FFSkeletonLine(widthFraction = 0.9f, height = 18.dp)
        Spacer(Modifier.height(FFTheme.spacing.xs))
        FFSkeletonBlock(height = 104.dp)
        FFSkeletonBlock(height = 72.dp)
        FFSkeletonBlock(height = 88.dp)
    }
}

// ─── Formatadores ─────────────────────────────────────────────────────────────

private val currencyFormat: NumberFormat
    get() = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

private val odometerFormat: NumberFormat
    get() = NumberFormat.getIntegerInstance(Locale("pt", "BR"))

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("pt", "BR"))

private fun formatAmount(amount: Double): String = currencyFormat.format(amount)

private fun formatOdometer(km: Int): String = "${odometerFormat.format(km)} km"

private fun formatEventDate(isoDate: String): String =
    runCatching { LocalDate.parse(isoDate).format(dateFormatter) }.getOrDefault(isoDate)
