package com.flowfuel.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.flowfuel.app.core.designsystem.components.FFBottomBar
import com.flowfuel.app.core.designsystem.components.FFBottomItem
import com.flowfuel.app.core.designsystem.components.FFBottomSheet
import com.flowfuel.app.core.designsystem.components.FFFab
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.auth.presentation.profile.ProfileScreen
import com.flowfuel.app.feature.history.presentation.HistoryScreen
import com.flowfuel.app.feature.home.presentation.HomeScreen
import com.flowfuel.app.feature.home.presentation.QuickRefuelBottomSheet
import com.flowfuel.app.feature.home.presentation.QuickRefuelEffect
import com.flowfuel.app.feature.home.presentation.QuickRefuelViewModel
import com.flowfuel.app.feature.station.presentation.list.StationsScreen
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.presentation.list.VehicleEventsScreen
import kotlinx.coroutines.flow.collectLatest

/**
 * Container principal autenticado.
 *
 * Responsável por:
 * - Exibir a [FFBottomBar] com as 5 abas do app e o FAB global docado
 *   (contextual: "Registrar abastecimento" em Home/Histórico/Postos/Perfil,
 *   "Novo evento" em Eventos).
 * - Hospedar o [NavHost] aninhado das abas.
 * - Hospedar o [QuickRefuelBottomSheet] (via [QuickRefuelViewModel]), para que
 *   o FAB abra o sheet a partir de qualquer aba, sem trocar de aba.
 * - Repassar callbacks de navegação "para fora" (login, add vehicle)
 *   ao NavHost raiz via lambdas, sem acoplamento direto.
 *
 * As abas preservam estado entre si via [launchSingleTop] + [saveState] /
 * [restoreState] + [popUpTo(findStartDestination)].
 */
@Composable
fun MainContainerScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToAddVehicle: () -> Unit,
    onNavigateToEventCreate: (vehicleId: Int) -> Unit = {},
    onNavigateToMaintenanceEventCreate: (vehicleId: Int, category: EventCategory) -> Unit = { _, _ -> },
    onNavigateToEventDetails: (eventId: Int) -> Unit = {},
    onNavigateToRefuelDetails: (refuelId: Int) -> Unit = {},
    onNavigateToVehicles: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToChangePassword: () -> Unit = {},
    passwordChanged: Boolean = false,
    onPasswordChangedConsumed: () -> Unit = {},
    profileUpdated: Boolean = false,
    onProfileUpdatedConsumed: () -> Unit = {},
    historyNeedsRefresh: Boolean = false,
    onHistoryRefreshConsumed: () -> Unit = {},
    homeNeedsRefresh: Boolean = false,
    onHomeRefreshConsumed: () -> Unit = {},
    tabEventCreated: Boolean = false,
    onTabEventCreatedConsumed: () -> Unit = {},
    tabEventDeleted: Int = -1,
    onTabEventDeletedConsumed: () -> Unit = {},
    tabEventUpdated: Boolean = false,
    onTabEventUpdatedConsumed: () -> Unit = {},
    quickRefuelViewModel: QuickRefuelViewModel = hiltViewModel(),
) {
    val innerNavController = rememberNavController()
    val backStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val quickRefuelState by quickRefuelViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var triggerEventCreate by remember { mutableStateOf(false) }
    // Sinalizadores locais de "houve um abastecimento registrado agora", um por
    // aba consumidora — cada um só é resetado quando a própria aba (se e quando
    // estiver ativa) processa o refresh, mesmo padrão de homeNeedsRefresh/
    // historyNeedsRefresh vindos de fora (ver FlowFuelNavHost.kt).
    var homeRefuelPending by remember { mutableStateOf(false) }
    var historyRefuelPending by remember { mutableStateOf(false) }

    LaunchedEffect(quickRefuelViewModel) {
        quickRefuelViewModel.effects.collectLatest { effect ->
            when (effect) {
                QuickRefuelEffect.RefuelRegistered -> {
                    homeRefuelPending = true
                    historyRefuelPending = true
                    snackbarHostState.showSnackbar(
                        FFSnackbarVisuals(
                            message = "Abastecimento registrado com sucesso!",
                            kind = FFSnackbarKind.Success,
                            duration = SnackbarDuration.Short,
                        ),
                    )
                }
            }
        }
    }

    val tabs = remember {
        listOf(
            FFBottomItem(
                route        = MainDestinations.HOME,
                label        = "Home",
                icon         = Icons.Outlined.Home,
                selectedIcon = Icons.Filled.Home,
            ),
            FFBottomItem(
                route        = MainDestinations.HISTORY,
                label        = "Histórico",
                icon         = Icons.Outlined.History,
                selectedIcon = Icons.Filled.History,
            ),
            FFBottomItem(
                route        = MainDestinations.STATIONS,
                label        = "Postos",
                icon         = Icons.Outlined.LocalGasStation,
                selectedIcon = Icons.Filled.LocalGasStation,
            ),
            FFBottomItem(
                route        = MainDestinations.EVENTS,
                label        = "Eventos",
                icon         = Icons.AutoMirrored.Outlined.Assignment,
                selectedIcon = Icons.AutoMirrored.Filled.Assignment,
            ),
            FFBottomItem(
                route        = MainDestinations.PROFILE,
                label        = "Perfil",
                icon         = Icons.Outlined.Person,
                selectedIcon = Icons.Filled.Person,
            ),
        )
    }

    Scaffold(
        // O Scaffold raiz consome os insets do sistema (nav bar, status bar).
        // As telas internas recebem contentWindowInsets = WindowInsets(0) via
        // consumeWindowInsets no NavHost para não duplicar o padding.
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
        bottomBar = {
            FFBottomBar(
                items        = tabs,
                currentRoute = currentRoute,
                onSelect     = { item ->
                    innerNavController.navigate(item.route) {
                        // Mantém o start destination na back stack e salva estado
                        // de cada aba para restaurar quando voltar a ela.
                        popUpTo(innerNavController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Evita múltiplas cópias da mesma aba na back stack
                        launchSingleTop = true
                        // Restaura o estado salvo ao re-selecionar a aba
                        restoreState = true
                    }
                },
                floatingActionButton = {
                    if (currentRoute == MainDestinations.EVENTS) {
                        FFFab(
                            icon               = Icons.Default.Add,
                            contentDescription = "Novo evento",
                            text               = "Novo Evento",
                            onClick            = { triggerEventCreate = true },
                        )
                    } else {
                        FFFab(
                            icon               = Icons.Default.LocalGasStation,
                            contentDescription = "Registrar abastecimento",
                            onClick            = quickRefuelViewModel::openSheet,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController   = innerNavController,
            startDestination = MainDestinations.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Informa ao Compose que os insets já foram consumidos pelo Scaffold
                // externo, evitando padding duplo em Scaffolds filhos.
                .consumeWindowInsets(innerPadding),
            enterTransition   = { fadeIn(animationSpec = tween(200)) },
            exitTransition    = { fadeOut(animationSpec = tween(150)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition  = { fadeOut(animationSpec = tween(150)) },
        ) {
            // ── Home ──────────────────────────────────────────────────────────
            composable(MainDestinations.HOME) {
                HomeScreen(
                    onNavigateToLogin      = onNavigateToLogin,
                    onNavigateToAddVehicle = onNavigateToAddVehicle,
                    onNavigateToMaintenanceEventCreate = onNavigateToMaintenanceEventCreate,
                    onOpenRefuelSheet      = quickRefuelViewModel::openSheet,
                    refreshTrigger         = homeNeedsRefresh || homeRefuelPending,
                    onRefreshConsumed      = {
                        onHomeRefreshConsumed()
                        homeRefuelPending = false
                    },
                )
            }

            // ── Histórico ─────────────────────────────────────────────────────
            composable(MainDestinations.HISTORY) {
                HistoryScreen(
                    onNavigateToLogin        = onNavigateToLogin,
                    onNavigateToDetails      = onNavigateToRefuelDetails,
                    historyNeedsRefresh      = historyNeedsRefresh || historyRefuelPending,
                    onHistoryRefreshConsumed = {
                        onHistoryRefreshConsumed()
                        historyRefuelPending = false
                    },
                )
            }

            // ── Postos ────────────────────────────────────────────────────────
            composable(MainDestinations.STATIONS) {
                StationsScreen(onNavigateToLogin = onNavigateToLogin)
            }

            // ── Eventos ───────────────────────────────────────────────────────
            composable(MainDestinations.EVENTS) {
                VehicleEventsScreen(
                    onBack = null,
                    onNavigateToCreate = onNavigateToEventCreate,
                    onNavigateToDetails = onNavigateToEventDetails,
                    onNavigateToRefuelDetails = onNavigateToRefuelDetails,
                    onNavigateToLogin = onNavigateToLogin,
                    eventCreated = tabEventCreated,
                    onEventCreatedConsumed = onTabEventCreatedConsumed,
                    eventDeleted = tabEventDeleted,
                    onEventDeletedConsumed = onTabEventDeletedConsumed,
                    eventUpdated = tabEventUpdated,
                    onEventUpdatedConsumed = onTabEventUpdatedConsumed,
                    triggerCreate = triggerEventCreate,
                    onCreateTriggerConsumed = { triggerEventCreate = false },
                )
            }

            // ── Perfil ────────────────────────────────────────────────────────
            composable(MainDestinations.PROFILE) {
                ProfileScreen(
                    onNavigateToLogin          = onNavigateToLogin,
                    onNavigateToEditProfile    = onNavigateToEditProfile,
                    onNavigateToChangePassword = onNavigateToChangePassword,
                    onNavigateToVehicles       = onNavigateToVehicles,
                    passwordChanged            = passwordChanged,
                    onPasswordChangedConsumed  = onPasswordChangedConsumed,
                    profileUpdated             = profileUpdated,
                    onProfileUpdatedConsumed   = onProfileUpdatedConsumed,
                )
            }
        }
    }

    if (quickRefuelState.showSheet) {
        val vehicle = quickRefuelState.vehicle
        if (vehicle == null) {
            FFBottomSheet(onDismiss = quickRefuelViewModel::closeSheet) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(FFTheme.spacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            QuickRefuelBottomSheet(
                form                      = quickRefuelState.form,
                isSubmitting              = quickRefuelState.isSubmitting,
                submitError               = quickRefuelState.submitError,
                energyType                = vehicle.energyType,
                onOdometerInputModeChange = quickRefuelViewModel::onOdometerInputModeChange,
                onTripKmChange            = quickRefuelViewModel::onTripKmChange,
                onOdometerChange          = quickRefuelViewModel::onOdometerChange,
                onLitersChange            = quickRefuelViewModel::onLitersChange,
                onTotalPriceInput         = quickRefuelViewModel::onTotalPriceInput,
                onFullTankToggle          = quickRefuelViewModel::onFullTankToggle,
                onRefuelTypeChange        = quickRefuelViewModel::onRefuelTypeChange,
                onSubmit                  = quickRefuelViewModel::submitRefuel,
                onDismiss                 = quickRefuelViewModel::closeSheet,
            )
        }
    }
}
