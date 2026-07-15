package com.flowfuel.app.feature.vehicle.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.R
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFFab
import com.flowfuel.app.core.designsystem.components.FFSkeletonBlock
import com.flowfuel.app.core.designsystem.components.FFSkeletonList
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.components.FFVehicleCard
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun VehiclePickerScreen(
    onNavigateToAddVehicle: () -> Unit,
    onNavigateToHome: (Vehicle) -> Unit,
    onNavigateToLogin: () -> Unit,
    onBack: (() -> Unit)? = null,
    onNavigateToGuestVehicle: (VehicleShare) -> Unit = {},
    viewModel: VehiclePickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val paginationState by viewModel.paginationState.collectAsState()

    // Reage a efeitos de navegação emitidos pelo ViewModel
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                VehiclePickerEffect.NavigateToAddVehicle    -> onNavigateToAddVehicle()
                is VehiclePickerEffect.NavigateToHome       -> onNavigateToHome(effect.vehicle)
                is VehiclePickerEffect.NavigateToGuestVehicle -> onNavigateToGuestVehicle(effect.share)
                VehiclePickerEffect.NavigateToLogin         -> onNavigateToLogin()
            }
        }
    }

    // Hoisted so it survives state transitions (Loading → Success) without resetting
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

    // Preserve scroll position when composable leaves composition (navigation or back-press)
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveScrollState(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
    }

    Scaffold(
        topBar = {
            FFTopBar(
                title = stringResource(R.string.vehicle_picker_title),
                onBack = onBack,
            )
        },
        floatingActionButton = {
            FFFab(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.vehicle_picker_fab),
                text = stringResource(R.string.vehicle_picker_fab),
                onClick = viewModel::onAddVehicleClicked,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (val s = state) {

                // ── Carregando ──────────────────────────────────────────────
                VehiclePickerUiState.Loading -> {
                    FFSkeletonList(
                        itemCount = 3,
                        modifier = Modifier.padding(horizontal = FFTheme.spacing.md),
                    )
                }

                // ── Erro ────────────────────────────────────────────────────
                is VehiclePickerUiState.Error -> {
                    FFErrorState(
                        message = s.error.userMessage(),
                        onRetry = viewModel::load,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                // ── Lista de veículos ────────────────────────────────────────
                is VehiclePickerUiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = stringResource(R.string.vehicle_picker_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = FFTheme.spacing.md,
                                vertical = FFTheme.spacing.sm,
                            ),
                        )

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(
                                horizontal = FFTheme.spacing.md,
                                vertical = FFTheme.spacing.sm,
                            ),
                            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap),
                        ) {
                            items(
                                items = s.items,
                                key = { item ->
                                    when (item) {
                                        is VehiclePickerItem.Owned -> "owned_${item.vehicle.id}"
                                        is VehiclePickerItem.Borrowed -> "borrowed_${item.share.id}"
                                    }
                                },
                            ) { item ->
                                when (item) {
                                    is VehiclePickerItem.Owned -> {
                                        val vehicle = item.vehicle
                                        val isCurrentlyActive = vehicle.id == s.activeVehicleId
                                        FFVehicleCard(
                                            nickname = "${vehicle.brand} ${vehicle.model}",
                                            plate = vehicle.licensePlate ?: "—",
                                            odometerKm = String.format(Locale("pt", "BR"), "%,d", vehicle.odometerKm),
                                            isActive = isCurrentlyActive,
                                            modifier = Modifier.fillMaxWidth(),
                                            photoUrl = vehicle.photoUrl,
                                            vehicleType = vehicle.type,
                                            onClick = { viewModel.onItemSelected(item) },
                                        )
                                    }
                                    is VehiclePickerItem.Borrowed -> {
                                        BorrowedVehicleCard(
                                            share = item.share,
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = { viewModel.onItemSelected(item) },
                                        )
                                    }
                                }
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
                                    PaginationErrorRetry(
                                        message = error.userMessage(),
                                        onRetry = viewModel::loadNextPage,
                                        modifier = Modifier.padding(top = FFTheme.spacing.sm),
                                    )
                                }
                            }

                            item { Spacer(Modifier.height(FFTheme.spacing.xl)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BorrowedVehicleCard(
    share: VehicleShare,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        ) {
            Text(
                text = "${share.vehicleBrand} ${share.vehicleModel}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Surface(
                color = FFTheme.semanticColors.info,
                contentColor = FFTheme.semanticColors.onInfo,
                shape = FFTheme.extraShapes.pill,
            ) {
                Text(
                    text = "Emprestado",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(
                        start = FFTheme.spacing.sm,
                        end = FFTheme.spacing.sm,
                        top = 2.dp,
                        bottom = 2.dp,
                    ),
                )
            }
        }
        Text(
            text = "até ${share.expiresAt?.formatShareExpiry() ?: "—"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val shareExpiryFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("pt", "BR"))

private fun String.formatShareExpiry(): String =
    runCatching { LocalDate.parse(take(10)).format(shareExpiryFormatter) }.getOrDefault(this)

@Composable
private fun PaginationErrorRetry(
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
