package com.flowfuel.app.feature.station.presentation.list

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flowfuel.app.core.designsystem.components.FFEmptyState
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFSkeletonList
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.feature.station.domain.model.StationType
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsScreen(
    onNavigateToLogin: () -> Unit = {},
    viewModel: StationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val radiusMeters by viewModel.radiusMeters.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var hasPermanentlyDeniedPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.load()
        } else {
            val activity = context as? Activity
            hasPermanentlyDeniedPermission = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.ACCESS_FINE_LOCATION,
                )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                when (state) {
                    StationsUiState.PermissionRequired, StationsUiState.LocationUnavailable -> viewModel.load()
                    else -> Unit
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is StationsEffect.OpenNavigation -> try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effect.uri)))
                } catch (_: ActivityNotFoundException) {
                    snackbarHostState.showSnackbar(
                        FFSnackbarVisuals("Nenhum app de navegação instalado", FFSnackbarKind.Error)
                    )
                }
                StationsEffect.NavigateToLogin -> onNavigateToLogin()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { FFTopBar(title = "Postos próximos") },
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state != StationsUiState.PermissionRequired) {
                StationTypeFilterRow(
                    selectedType = selectedType,
                    onSelect = viewModel::onTypeSelected,
                    modifier = Modifier.padding(top = FFTheme.spacing.sm),
                )
                StationDistanceFilterRow(
                    selectedRadiusMeters = radiusMeters,
                    onSelect = viewModel::onRadiusSelected,
                    modifier = Modifier.padding(vertical = FFTheme.spacing.sm),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (val s = state) {
                    StationsUiState.Loading -> FFSkeletonList(modifier = Modifier.fillMaxSize(), itemCount = 4)

                    is StationsUiState.Success -> {
                        val filteredStations = remember(s.stations, selectedType) {
                            s.stations.filter { it.type == selectedType }
                        }
                        if (filteredStations.isEmpty()) {
                            FFEmptyState(
                                title = when (selectedType) {
                                    StationType.Fuel -> "Nenhum posto de combustível encontrado por perto"
                                    StationType.Electric -> "Nenhum posto elétrico encontrado por perto"
                                },
                                description = "Tente aumentar o raio de busca ou trocar o filtro de tipo.",
                                actionText = "Tentar novamente",
                                onAction = viewModel::load,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        } else {
                            PullToRefreshBox(
                                isRefreshing = false,
                                onRefresh = viewModel::load,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(FFTheme.spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                                ) {
                                    items(filteredStations, key = { it.placeId }) { station ->
                                        StationCard(station = station, onRouteClick = { viewModel.onRouteClick(station) })
                                    }
                                }
                            }
                        }
                    }

                    StationsUiState.Empty -> FFEmptyState(
                        title = "Nenhum posto encontrado por perto",
                        description = "Tente novamente em uma área com mais cobertura.",
                        actionText = "Tentar novamente",
                        onAction = viewModel::load,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    is StationsUiState.Error -> FFErrorState(
                        message = s.error.userMessage(),
                        onRetry = viewModel::load,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    StationsUiState.LocationUnavailable -> FFErrorState(
                        title = "Localização indisponível",
                        message = "Não foi possível obter sua localização. Verifique se o GPS está ativado.",
                        onRetry = viewModel::load,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    StationsUiState.PermissionRequired -> FFEmptyState(
                        title = "Precisamos da sua localização",
                        description = "Para mostrar postos e estações próximos, permita o acesso à localização.",
                        icon = Icons.Outlined.LocationOn,
                        actionText = if (hasPermanentlyDeniedPermission) "Abrir configurações" else "Permitir acesso à localização",
                        onAction = {
                            if (hasPermanentlyDeniedPermission) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    )
                                )
                            } else {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}
