package com.flowfuel.app.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.flowfuel.app.core.designsystem.components.FFBottomBar
import com.flowfuel.app.core.designsystem.components.FFBottomItem
import com.flowfuel.app.core.designsystem.components.FFBottomSheet
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFDialog
import com.flowfuel.app.core.designsystem.components.FFDialogKind
import com.flowfuel.app.core.designsystem.components.FFFab
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.notification.presentation.NotificationPermissionUiState
import com.flowfuel.app.core.notification.presentation.NotificationPermissionViewModel
import com.flowfuel.app.feature.auth.presentation.profile.ProfileScreen
import com.flowfuel.app.feature.history.presentation.HistoryScreen
import com.flowfuel.app.feature.home.presentation.HomeScreen
import com.flowfuel.app.feature.home.presentation.QuickRefuelBottomSheet
import com.flowfuel.app.feature.home.presentation.QuickRefuelEffect
import com.flowfuel.app.feature.home.presentation.QuickRefuelViewModel
import com.flowfuel.app.feature.station.presentation.list.StationsScreen
import com.flowfuel.app.feature.update.presentation.UpdateAvailableDialog
import com.flowfuel.app.feature.update.presentation.UpdateUiState
import com.flowfuel.app.feature.update.presentation.UpdateViewModel
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.presentation.list.VehicleEventsScreen
import kotlinx.coroutines.flow.collectLatest

/**
 * Container principal autenticado.
 *
 * Responsável por:
 * - Exibir a [FFBottomBar] com as 5 abas do app (3 em modo convidado: Home/
 *   Postos/Perfil) e o FAB global docado (contextual: "Registrar
 *   abastecimento" em Home/Histórico/Postos/Perfil, "Novo evento" em Eventos;
 *   oculto em modo convidado, ver [MainContainerViewModel]).
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
    onNavigateToGuestEventCreate: (vehicleId: Int) -> Unit = {},
    onNavigateToMaintenanceEventCreate: (vehicleId: Int, category: EventCategory) -> Unit = { _, _ -> },
    onNavigateToEventDetails: (eventId: Int) -> Unit = {},
    onNavigateToRefuelDetails: (refuelId: Int) -> Unit = {},
    onNavigateToVehicles: () -> Unit = {},
    onNavigateToVehiclePicker: () -> Unit = {},
    onNavigateToShareInvite: (shareId: Int) -> Unit = {},
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
    updateViewModel: UpdateViewModel = hiltViewModel(),
    notificationPermissionViewModel: NotificationPermissionViewModel = hiltViewModel(),
    mainContainerViewModel: MainContainerViewModel = hiltViewModel(),
) {
    val innerNavController = rememberNavController()
    val backStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val containerState by mainContainerViewModel.state.collectAsState()
    val quickRefuelState by quickRefuelViewModel.state.collectAsState()
    val updateState by updateViewModel.state.collectAsState()
    val context = LocalContext.current
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

    val tabs = remember(containerState.isGuestMode) {
        val base = listOf(
            FFBottomItem(
                route        = MainDestinations.HOME,
                label        = "Home",
                icon         = Icons.Outlined.Home,
                selectedIcon = Icons.Filled.Home,
            ),
        )
        val ownerOnlyBeforeStations = if (containerState.isGuestMode) emptyList() else listOf(
            FFBottomItem(
                route        = MainDestinations.HISTORY,
                label        = "Histórico",
                icon         = Icons.Outlined.History,
                selectedIcon = Icons.Filled.History,
            ),
        )
        val stationsAndBeyond = listOf(
            FFBottomItem(
                route        = MainDestinations.STATIONS,
                label        = "Postos",
                icon         = Icons.Outlined.LocalGasStation,
                selectedIcon = Icons.Filled.LocalGasStation,
            ),
        ) + (if (containerState.isGuestMode) emptyList() else listOf(
            FFBottomItem(
                route        = MainDestinations.EVENTS,
                label        = "Eventos",
                icon         = Icons.AutoMirrored.Outlined.Assignment,
                selectedIcon = Icons.AutoMirrored.Filled.Assignment,
            ),
        )) + listOf(
            FFBottomItem(
                route        = MainDestinations.PROFILE,
                label        = "Perfil",
                icon         = Icons.Outlined.Person,
                selectedIcon = Icons.Filled.Person,
            ),
        )
        base + ownerOnlyBeforeStations + stationsAndBeyond
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
                    if (containerState.isGuestMode) {
                        // sem FAB — GuestVehicleScreen tem os próprios botões de ação
                    } else if (currentRoute == MainDestinations.EVENTS) {
                        FFFab(
                            icon               = Icons.Default.Add,
                            contentDescription = "Novo evento",
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
                val guestVehicle = containerState.guestVehicle
                if (containerState.isGuestMode && guestVehicle != null) {
                    com.flowfuel.app.feature.vehicle.presentation.guest.GuestVehicleScreen(
                        guestVehicle = guestVehicle,
                        onNavigateToCreateEvent = { vehicleId -> onNavigateToGuestEventCreate(vehicleId) },
                        onNavigateToPicker = { onNavigateToVehiclePicker() },
                        onSwitchVehicleClicked = { onNavigateToVehiclePicker() },
                    )
                } else if (containerState.isGuestMode && containerState.guestVehicleLoadFailed) {
                    // Busca do VehicleShare falhou ou não achou correspondência
                    // (GetActiveSharedVehiclesUseCase) — sem isso o convidado
                    // ficaria preso num spinner infinito, já que o ViewModel só
                    // reage a mudanças no SessionStore, não a falhas de rede.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(FFTheme.spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Não foi possível carregar o veículo emprestado.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(FFTheme.spacing.md))
                        FFButton(
                            text = "Tentar novamente",
                            onClick = mainContainerViewModel::retryLoadGuestVehicle,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(FFTheme.spacing.sm))
                        FFButton(
                            text = "Voltar",
                            onClick = onNavigateToVehiclePicker,
                            variant = FFButtonVariant.Text,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else if (containerState.isGuestMode) {
                    // Modo convidado confirmado pela sessão, VehicleShare ainda
                    // não chegou (primeira busca em andamento). Nunca cair para a
                    // HomeScreen do dono aqui — ela é a tela errada para o
                    // convidado e pode tentar carregar dados que ele não tem acesso.
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
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
                    onNavigateToShareInvite    = onNavigateToShareInvite,
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updateViewModel.onInstallPermissionResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (val update = updateState) {
        is UpdateUiState.Available, is UpdateUiState.Downloading -> UpdateAvailableDialog(
            state                   = update,
            onUpdateClick           = updateViewModel::onUpdateClick,
            onDismiss               = updateViewModel::onDismiss,
            onHideDownload          = updateViewModel::onHideDownloadProgress,
            onInstallClick          = {},
            onDismissReadyToInstall = {},
        )

        is UpdateUiState.RequestingInstallPermission -> LaunchedEffect(Unit) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                )
            )
        }

        is UpdateUiState.ReadyToInstall -> {
            // Best case: the app is foregrounded and this launches the installer
            // immediately. On Android 10+, starting an activity from the
            // background can be silently blocked (e.g. the download finished
            // while the app wasn't foregrounded) — the dialog below is the
            // visible fallback so the user always has a way to trigger install.
            LaunchedEffect(update.installUri) {
                launchInstallIntent(context, update.installUri)
            }
            UpdateAvailableDialog(
                state                   = update,
                onUpdateClick           = updateViewModel::onUpdateClick,
                onDismiss               = updateViewModel::onDismiss,
                onHideDownload          = updateViewModel::onHideDownloadProgress,
                onInstallClick          = { launchInstallIntent(context, update.installUri) },
                onDismissReadyToInstall = updateViewModel::onDismissReadyToInstall,
            )
        }

        UpdateUiState.Idle -> Unit
    }

    val notificationPermissionState by notificationPermissionViewModel.state.collectAsState()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* sem-op — o status é reavaliado via NotificationManagerCompat quando o Perfil volta ao foreground (Task 4) */ }

    if (notificationPermissionState is NotificationPermissionUiState.ShowRationale) {
        FFDialog(
            title       = "Ativar notificações?",
            message     = "Avisamos sobre coisas importantes, como um convite de compartilhamento de veículo. Você pode mudar isso depois em Perfil > Notificações.",
            confirmText = "Ativar",
            dismissText = "Agora não",
            kind        = FFDialogKind.Info,
            onConfirm   = {
                notificationPermissionViewModel.onRationaleShown()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss   = notificationPermissionViewModel::onRationaleShown,
        )
    }
}

private fun launchInstallIntent(context: Context, installUri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(installUri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
