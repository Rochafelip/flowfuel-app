package com.flowfuel.app.feature.vehicle.presentation.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.core.designsystem.components.FFDialog
import com.flowfuel.app.core.designsystem.components.FFDialogKind
import com.flowfuel.app.core.designsystem.components.FFEmptyState
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFSkeletonBlock
import com.flowfuel.app.core.designsystem.components.FFSkeletonList
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.components.FFTopBarVariant
import com.flowfuel.app.core.designsystem.components.FFVehicleCard
import com.flowfuel.app.core.designsystem.components.FFFab
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import kotlinx.coroutines.flow.collectLatest

// ─── Tela de gerenciamento de veículos ────────────────────────────────────────

@Composable
fun VehiclesScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToAddVehicle: () -> Unit,
    onNavigateToVehicleDetails: (vehicleId: Int) -> Unit = {},
    onNavigateToEditVehicle: (vehicleId: Int) -> Unit = {},
    onNavigateToVehicleEvents: (vehicleId: Int) -> Unit = {},
    vehicleUpdated: Boolean = false,
    onVehicleUpdatedConsumed: () -> Unit = {},
    viewModel: VehiclesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val paginationState by viewModel.paginationState.collectAsState()

    LaunchedEffect(vehicleUpdated) {
        if (vehicleUpdated) {
            viewModel.load()
            onVehicleUpdatedConsumed()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                VehiclesEffect.NavigateToLogin -> onNavigateToLogin()
            }
        }
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.savedScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.savedScrollOffset,
    )

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

    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveScrollState(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            FFTopBar(
                title   = "Veículos",
                variant = FFTopBarVariant.Small,
            )
        },
        floatingActionButton = {
            // FAB visível apenas quando há conteúdo (não durante loading/error)
            if (state.screenState !is VehiclesScreenState.Loading) {
                FFFab(
                    icon               = Icons.Default.Add,
                    contentDescription = "Adicionar veículo",
                    text               = "Novo veículo",
                    onClick            = onNavigateToAddVehicle,
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

                // ── Carregando ────────────────────────────────────────────────
                VehiclesScreenState.Loading -> FFSkeletonList(
                    modifier  = Modifier.fillMaxSize(),
                    itemCount = 4,
                )

                // ── Vazio ─────────────────────────────────────────────────────
                VehiclesScreenState.Empty -> FFEmptyState(
                    title       = "Nenhum veículo cadastrado",
                    description = "Adicione seu primeiro veículo para começar a registrar abastecimentos.",
                    icon        = Icons.Outlined.DirectionsCar,
                    actionText  = "Adicionar veículo",
                    onAction    = onNavigateToAddVehicle,
                    modifier    = Modifier.align(Alignment.Center),
                )

                // ── Erro ──────────────────────────────────────────────────────
                is VehiclesScreenState.Error -> FFErrorState(
                    message  = s.error.userMessage(),
                    onRetry  = viewModel::load,
                    modifier = Modifier.align(Alignment.Center),
                )

                // ── Lista ─────────────────────────────────────────────────────
                is VehiclesScreenState.Success -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start  = FFTheme.spacing.md,
                        end    = FFTheme.spacing.md,
                        top    = FFTheme.spacing.md,
                        // Espaço extra para o FAB não cobrir o último item
                        bottom = FFTheme.spacing.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap),
                ) {
                    itemsIndexed(
                        items = s.vehicles,
                        key   = { _, v -> v.id },
                    ) { _, vehicle ->
                        VehicleManageItem(
                            vehicle                    = vehicle,
                            activeVehicleId            = state.activeVehicleId,
                            onNavigateToVehicleDetails = { onNavigateToVehicleDetails(vehicle.id) },
                            onSetActive                = { viewModel.onSetActive(vehicle) },
                            onEdit                     = { onNavigateToEditVehicle(vehicle.id) },
                            onDeleteRequest            = { viewModel.onDeleteRequest(vehicle) },
                            onNavigateToEvents         = { onNavigateToVehicleEvents(vehicle.id) },
                        )
                    }

                    if (paginationState.isLoadingMore) {
                        item(key = "loading_more") {
                            FFSkeletonBlock(
                                modifier = Modifier.padding(top = FFTheme.spacing.cardGap),
                            )
                        }
                    }

                    paginationState.pageError?.let { error ->
                        item(key = "error_retry") {
                            VehiclesPaginationErrorRetry(
                                message = error.userMessage(),
                                onRetry = viewModel::loadNextPage,
                                modifier = Modifier.padding(top = FFTheme.spacing.sm),
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Dialog de confirmação de exclusão ─────────────────────────────────────
    val pending = state.vehiclePendingDelete
    if (pending != null) {
        FFDialog(
            title       = "Excluir veículo?",
            message     = "\"${pending.brand} ${pending.model}\" e todo o seu histórico serão removidos permanentemente.",
            confirmText = if (state.isDeleting) "Excluindo…" else "Excluir",
            onConfirm   = viewModel::onDeleteConfirm,
            onDismiss   = viewModel::onDeleteDismiss,
            kind        = FFDialogKind.Destructive,
            dismissText = "Cancelar",
        )
    }
}

// ─── Item da lista com menu de opções ─────────────────────────────────────────

@Composable
private fun VehicleManageItem(
    vehicle: Vehicle,
    activeVehicleId: Int?,
    onNavigateToVehicleDetails: () -> Unit,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit,
    onNavigateToEvents: () -> Unit,
) {
    val isActive = vehicle.id == activeVehicleId
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Card principal — tap navega para detalhes do veículo
        FFVehicleCard(
            nickname   = "${vehicle.brand} ${vehicle.model}",
            plate      = vehicle.licensePlate ?: "—",
            odometerKm = "%,d".format(vehicle.odometerKm),
            isActive   = isActive,
            modifier   = Modifier.fillMaxWidth(),
            onClick    = onNavigateToVehicleDetails,
        )

        // Botão de opções (3 pontos) — canto superior direito sobre o card
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = FFTheme.spacing.xs, end = FFTheme.spacing.xs),
        ) {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector        = Icons.Default.MoreVert,
                    contentDescription = "Opções de ${vehicle.brand} ${vehicle.model}",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DropdownMenu(
                expanded          = showMenu,
                onDismissRequest  = { showMenu = false },
            ) {
                // "Definir como ativo" só aparece se o veículo não é o ativo atual
                if (!isActive) {
                    DropdownMenuItem(
                        text     = { Text("Definir como ativo") },
                        onClick  = { showMenu = false; onSetActive() },
                        leadingIcon = {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                        },
                    )
                }

                DropdownMenuItem(
                    text     = { Text("Eventos") },
                    onClick  = { showMenu = false; onNavigateToEvents() },
                    leadingIcon = {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    },
                )

                DropdownMenuItem(
                    text     = { Text("Editar") },
                    onClick  = { showMenu = false; onEdit() },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    },
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            text  = "Excluir",
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = { showMenu = false; onDeleteRequest() },
                    leadingIcon = {
                        Icon(
                            imageVector        = Icons.Default.Delete,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.error,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun VehiclesPaginationErrorRetry(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) {
            Text("Tentar novamente")
        }
    }
}
