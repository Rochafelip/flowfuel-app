package com.flowfuel.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.flowfuel.app.core.designsystem.components.FFBottomBar
import com.flowfuel.app.core.designsystem.components.FFBottomItem
import com.flowfuel.app.feature.auth.presentation.profile.ProfileScreen
import com.flowfuel.app.feature.history.presentation.HistoryScreen
import com.flowfuel.app.feature.home.presentation.HomeScreen
import com.flowfuel.app.feature.vehicle.presentation.manage.VehiclesScreen
import com.flowfuel.app.feature.vehicleevent.presentation.list.VehicleEventsScreen

/**
 * Container principal autenticado.
 *
 * Responsável por:
 * - Exibir a [FFBottomBar] com as 4 abas do app.
 * - Hospedar o [NavHost] aninhado das abas.
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
    onNavigateToVehicleDetails: (vehicleId: Int) -> Unit = {},
    onNavigateToEditVehicle: (vehicleId: Int) -> Unit = {},
    onNavigateToVehicleEvents: (vehicleId: Int) -> Unit = {},
    onNavigateToEventCreate: (vehicleId: Int) -> Unit = {},
    onNavigateToEventDetails: (eventId: Int) -> Unit = {},
    onNavigateToRefuelDetails: (refuelId: Int) -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToChangePassword: () -> Unit = {},
    passwordChanged: Boolean = false,
    onPasswordChangedConsumed: () -> Unit = {},
    profileUpdated: Boolean = false,
    onProfileUpdatedConsumed: () -> Unit = {},
    vehicleUpdated: Boolean = false,
    onVehicleUpdatedConsumed: () -> Unit = {},
    historyNeedsRefresh: Boolean = false,
    onHistoryRefreshConsumed: () -> Unit = {},
    tabEventCreated: Boolean = false,
    onTabEventCreatedConsumed: () -> Unit = {},
    tabEventDeleted: Int = -1,
    onTabEventDeletedConsumed: () -> Unit = {},
    tabEventUpdated: Boolean = false,
    onTabEventUpdatedConsumed: () -> Unit = {},
) {
    val innerNavController = rememberNavController()
    val backStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var openRefuelSheet by remember { mutableStateOf(false) }

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
                route        = MainDestinations.VEHICLES,
                label        = "Veículos",
                icon         = Icons.Outlined.DirectionsCar,
                selectedIcon = Icons.Filled.DirectionsCar,
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
                    openRefuelSheet        = openRefuelSheet,
                    onRefuelSheetOpened    = { openRefuelSheet = false },
                )
            }

            // ── Histórico ─────────────────────────────────────────────────────
            composable(MainDestinations.HISTORY) {
                HistoryScreen(
                    onNavigateToLogin        = onNavigateToLogin,
                    onNavigateToDetails      = onNavigateToRefuelDetails,
                    onAddRefuel              = {
                        openRefuelSheet = true
                        innerNavController.navigate(MainDestinations.HOME) {
                            popUpTo(innerNavController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    },
                    historyNeedsRefresh      = historyNeedsRefresh,
                    onHistoryRefreshConsumed = onHistoryRefreshConsumed,
                )
            }

            // ── Veículos ──────────────────────────────────────────────────────
            composable(MainDestinations.VEHICLES) {
                VehiclesScreen(
                    onNavigateToLogin           = onNavigateToLogin,
                    onNavigateToAddVehicle      = onNavigateToAddVehicle,
                    onNavigateToVehicleDetails  = onNavigateToVehicleDetails,
                    onNavigateToEditVehicle     = onNavigateToEditVehicle,
                    onNavigateToVehicleEvents   = onNavigateToVehicleEvents,
                    vehicleUpdated              = vehicleUpdated,
                    onVehicleUpdatedConsumed    = onVehicleUpdatedConsumed,
                )
            }

            // ── Eventos ───────────────────────────────────────────────────────
            composable(MainDestinations.EVENTS) {
                VehicleEventsScreen(
                    onBack = null,
                    onNavigateToCreate = onNavigateToEventCreate,
                    onNavigateToDetails = onNavigateToEventDetails,
                    onNavigateToLogin = onNavigateToLogin,
                    eventCreated = tabEventCreated,
                    onEventCreatedConsumed = onTabEventCreatedConsumed,
                    eventDeleted = tabEventDeleted,
                    onEventDeletedConsumed = onTabEventDeletedConsumed,
                    eventUpdated = tabEventUpdated,
                    onEventUpdatedConsumed = onTabEventUpdatedConsumed,
                )
            }

            // ── Perfil ────────────────────────────────────────────────────────
            composable(MainDestinations.PROFILE) {
                ProfileScreen(
                    onNavigateToLogin          = onNavigateToLogin,
                    onNavigateToEditProfile    = onNavigateToEditProfile,
                    onNavigateToChangePassword = onNavigateToChangePassword,
                    passwordChanged            = passwordChanged,
                    onPasswordChangedConsumed  = onPasswordChangedConsumed,
                    profileUpdated             = profileUpdated,
                    onProfileUpdatedConsumed   = onProfileUpdatedConsumed,
                )
            }
        }
    }
}
