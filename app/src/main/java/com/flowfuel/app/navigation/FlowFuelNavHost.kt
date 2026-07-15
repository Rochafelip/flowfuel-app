package com.flowfuel.app.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.flowfuel.app.feature.auth.presentation.changepassword.ChangePasswordScreen
import com.flowfuel.app.feature.auth.presentation.editprofile.EditProfileScreen
import com.flowfuel.app.feature.auth.presentation.checkemail.CheckEmailScreen
import com.flowfuel.app.feature.auth.presentation.forgot.ForgotPasswordScreen
import com.flowfuel.app.feature.auth.presentation.login.LoginScreen
import com.flowfuel.app.feature.auth.presentation.resetpassword.ResetPasswordScreen
import com.flowfuel.app.feature.auth.presentation.register.RegisterScreen
import com.flowfuel.app.feature.history.presentation.details.RefuelDetailsScreen
import com.flowfuel.app.feature.history.presentation.edit.EditRefuelScreen
import com.flowfuel.app.feature.onboarding.OnboardingScreen
import com.flowfuel.app.feature.vehicle.presentation.add.AddVehicleScreen
import com.flowfuel.app.feature.vehicle.presentation.details.VehicleDetailsScreen
import com.flowfuel.app.feature.vehicle.presentation.edit.EditVehicleScreen
import com.flowfuel.app.feature.vehicle.presentation.list.VehiclePickerScreen
import com.flowfuel.app.feature.vehicle.presentation.manage.VehiclesScreen
import com.flowfuel.app.feature.vehicle.presentation.odometer.UpdateOdometerScreen
import com.flowfuel.app.feature.vehicleevent.presentation.create.CreateVehicleEventScreen
import com.flowfuel.app.feature.vehicleevent.presentation.details.VehicleEventDetailsScreen
import com.flowfuel.app.feature.vehicleevent.presentation.edit.EditVehicleEventScreen
import com.flowfuel.app.feature.vehicleevent.presentation.list.VehicleEventsScreen
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun FlowFuelNavHost(
    onSplashReady: () -> Unit,
    deepLinkUri: Uri? = null,
    notificationTitle: String? = null,
    notificationBody: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val splashVm: SplashViewModel = hiltViewModel()
    val start by splashVm.start.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Trata deep links flowfuel://<rota> (ex.: flowfuel://vehicle/details/1).
    // Caso especial: flowfuel://activate?token=... (magic link de ativação de
    // conta) não exige sessão — pode chegar com o usuário deslogado, que é o
    // caso normal logo após o registro. Os demais caminhos internos exigem
    // sessão com veículo ativo (StartDestination.Home), pois não há contexto
    // de navegação válido para eles em outros estados (login/onboarding/picker).
    LaunchedEffect(deepLinkUri, start) {
        val uri = deepLinkUri ?: return@LaunchedEffect
        if (start == StartDestination.Loading) return@LaunchedEffect

        if (uri.host == "activate") {
            val token = uri.getQueryParameter("token")
            if (!token.isNullOrBlank()) {
                val email = uri.getQueryParameter("email") ?: ""
                navController.currentBackStackEntryFlow.first { it.destination.route != Destinations.SPLASH }
                runCatching { navController.navigate(Destinations.checkEmail(email, token)) }
            }
            onDeepLinkConsumed()
            return@LaunchedEffect
        }

        if (start != StartDestination.Home) return@LaunchedEffect
        // Uri.path não inclui o host (ex.: flowfuel://vehicle/details/1 → host="vehicle",
        // path="/details/1") — é preciso recombinar os dois para bater com as rotas internas.
        val path = listOfNotNull(uri.host, uri.path?.removePrefix("/"))
            .filter { it.isNotBlank() }
            .joinToString("/")
        if (path.isNotBlank()) {
            navController.currentBackStackEntryFlow.first { it.destination.route == Destinations.MAIN_CONTAINER }
            val navigated = runCatching { navController.navigate(path) }.isSuccess
            if (navigated && !notificationTitle.isNullOrBlank()) {
                snackbarHostState.showSnackbar(
                    FFSnackbarVisuals(
                        message = listOfNotNull(notificationTitle, notificationBody).joinToString(" — "),
                        kind = FFSnackbarKind.Info,
                    )
                )
            }
        }
        onDeepLinkConsumed()
    }

    LaunchedEffect(start) {
        when (start) {
            StartDestination.Loading -> Unit

            StartDestination.Onboarding -> {
                onSplashReady()
                navController.navigate(Destinations.ONBOARDING) {
                    popUpTo(Destinations.SPLASH) { inclusive = true }
                }
            }

            StartDestination.Login -> {
                onSplashReady()
                navController.navigate(Destinations.LOGIN) {
                    popUpTo(Destinations.SPLASH) { inclusive = true }
                }
            }

            // Sessão ativa, sem veículo escolhido: mostra o picker
            StartDestination.VehiclePicker -> {
                onSplashReady()
                navController.navigate(Destinations.VEHICLE_PICKER) {
                    popUpTo(Destinations.SPLASH) { inclusive = true }
                }
            }

            // Sessão ativa + veículo ativo já definido: pula o picker
            StartDestination.Home -> {
                onSplashReady()
                navController.navigate(Destinations.MAIN_CONTAINER) {
                    popUpTo(Destinations.SPLASH) { inclusive = true }
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
    NavHost(
        navController = navController,
        startDestination = Destinations.SPLASH,
        modifier = Modifier.padding(innerPadding),
        enterTransition = { defaultEnter() },
        exitTransition = { defaultExit() },
        popEnterTransition = { defaultEnter() },
        popExitTransition = { defaultExit() },
    ) {
        // ── Splash ─────────────────────────────────────────────────────────
        composable(Destinations.SPLASH) { /* janela splash fica visível */ }

        // ── Onboarding ─────────────────────────────────────────────────────
        composable(Destinations.ONBOARDING) {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            val store = remember(context) {
                EntryPointAccessors.fromApplication(context, NavEntryPoint::class.java).sessionStore()
            }
            fun markAndGoToLogin() {
                scope.launch { store.markOnboarded() }
                navController.navigate(Destinations.LOGIN) {
                    popUpTo(Destinations.ONBOARDING) { inclusive = true }
                }
            }
            OnboardingScreen(
                onNavigateToLogin = { markAndGoToLogin() },
                onNavigateToRegister = {
                    markAndGoToLogin()
                    navController.navigate(Destinations.REGISTER)
                },
            )
        }

        // ── Login ──────────────────────────────────────────────────────────
        composable(Destinations.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Destinations.VEHICLE_PICKER) {
                        popUpTo(Destinations.LOGIN) { inclusive = true }
                    }
                },
                onGoToRegister = { navController.navigate(Destinations.REGISTER) },
                onGoToForgot = { navController.navigate(Destinations.FORGOT) },
                onAccountNotActivated = { email ->
                    navController.navigate(Destinations.checkEmail(email))
                },
            )
        }

        // ── Registro ───────────────────────────────────────────────────────
        composable(Destinations.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = { email ->
                    navController.navigate(Destinations.checkEmail(email)) {
                        popUpTo(Destinations.LOGIN) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Confirme seu e-mail (A5 / ADR-014) ────────────────────────────
        composable(
            route = Destinations.CHECK_EMAIL,
            arguments = listOf(
                navArgument("email") { type = NavType.StringType },
                navArgument("token") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val email = entry.arguments?.getString("email")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?: ""
            val token = entry.arguments?.getString("token")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?: ""
            CheckEmailScreen(
                email = email,
                initialToken = token,
                onBack = { navController.popBackStack() },
                onNavigateToLogin = {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(Destinations.LOGIN) { inclusive = true }
                    }
                },
                onNavigateHome = {
                    navController.navigate(Destinations.VEHICLE_PICKER) {
                        popUpTo(Destinations.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        // ── Esqueci a senha ────────────────────────────────────────────────
        composable(Destinations.FORGOT) {
            ForgotPasswordScreen(
                onBack = { navController.popBackStack() },
                onGoToResetPassword = { email ->
                    navController.navigate(Destinations.resetPassword(email))
                },
            )
        }

        // ── Conclusão de reset de senha (código manual, e-mail desligado em prod) ──
        composable(
            route = Destinations.RESET_PASSWORD,
            arguments = listOf(navArgument("email") { type = NavType.StringType }),
        ) { entry ->
            val email = entry.arguments?.getString("email")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?: ""
            ResetPasswordScreen(
                email = email,
                onSuccess = {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(Destinations.LOGIN) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Seleção de veículo ─────────────────────────────────────────────
        // Ponto de entrada pós-login. Redireciona automaticamente para
        // VEHICLE_ADD se o usuário não tiver nenhum veículo cadastrado,
        // ou para LOGIN se o token estiver expirado/inválido.
        composable(Destinations.VEHICLE_PICKER) {
            // Quando o picker é aberto a partir da Home (troca de veículo), existe
            // uma entrada anterior na back stack e oferecemos o botão de voltar.
            // No fluxo pós-login o picker é a raiz, então não há volta.
            val canGoBack = navController.previousBackStackEntry != null
            VehiclePickerScreen(
                onNavigateToAddVehicle = {
                    // Sem veículos: substitui o picker pelo cadastro na stack
                    navController.navigate(Destinations.VEHICLE_ADD) {
                        popUpTo(Destinations.VEHICLE_PICKER) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    // Veículo selecionado: vai para o container principal limpando
                    // toda a back stack. Garante uma única instância do container
                    // tanto no fluxo pós-login quanto na troca de veículo.
                    navController.navigate(Destinations.MAIN_CONTAINER) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    // Token expirado: limpa toda a stack e volta para o login
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = if (canGoBack) {
                    { navController.popBackStack() }
                } else null,
            )
        }

        // ── Cadastro de veículo ────────────────────────────────────────────
        composable(Destinations.VEHICLE_ADD) {
            // Sem back stack anterior quando chega aqui pelo auto-redirect do
            // picker (0 veículos, pós-login) — nesse caso não há nada
            // significativo para voltar, então a flecha de voltar é ocultada
            // (mesmo padrão de VehiclePickerScreen). Quando aberto a partir de
            // "Novo veículo" na aba Veículos, há stack anterior e o voltar funciona normal.
            val canGoBack = navController.previousBackStackEntry != null
            AddVehicleScreen(
                onSuccess = {
                    // Após cadastrar, vai para o container principal limpando toda a back stack
                    navController.navigate(Destinations.MAIN_CONTAINER) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = if (canGoBack) { { navController.popBackStack() } } else null,
            )
        }

        // ── Detalhes do veículo ────────────────────────────────────────────
        composable(
            route = Destinations.VEHICLE_DETAILS,
            arguments = listOf(navArgument("vehicleId") { type = NavType.IntType }),
        ) { entry ->
            val odometerUpdated by entry.savedStateHandle
                .getStateFlow("odometer_updated", false)
                .collectAsStateWithLifecycle()

            VehicleDetailsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLogin = {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToEdit = { vehicleId ->
                    navController.navigate(Destinations.vehicleEdit(vehicleId))
                },
                onNavigateToUpdateOdometer = { vehicleId, currentKm ->
                    navController.navigate(Destinations.vehicleOdometer(vehicleId, currentKm))
                },
                onNavigateToEvents = { vehicleId ->
                    navController.navigate(Destinations.vehicleEvents(vehicleId))
                },
                odometerUpdated = odometerUpdated,
                onOdometerUpdatedConsumed = {
                    entry.savedStateHandle["odometer_updated"] = false
                },
            )
        }

        // ── Edição de veículo ──────────────────────────────────────────────
        composable(
            route = Destinations.VEHICLE_EDIT,
            arguments = listOf(navArgument("vehicleId") { type = NavType.IntType }),
        ) {
            EditVehicleScreen(
                onBack = { navController.popBackStack() },
                onSaved = {
                    // Sinaliza a tela de gestão de veículos para recarregar ao retornar
                    runCatching {
                        navController.getBackStackEntry(Destinations.VEHICLE_MANAGE)
                            .savedStateHandle["vehicle_updated"] = true
                    }
                    navController.popBackStack()
                },
                onNavigateToLogin = {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // ── Gestão de veículos (acessível pelo Perfil) ─────────────────────
        composable(Destinations.VEHICLE_MANAGE) { entry ->
            val vehicleUpdated by entry.savedStateHandle
                .getStateFlow("vehicle_updated", false)
                .collectAsStateWithLifecycle()

            VehiclesScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLogin = {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToAddVehicle = { navController.navigate(Destinations.VEHICLE_ADD) },
                onNavigateToVehicleDetails = { vehicleId ->
                    navController.navigate(Destinations.vehicleDetails(vehicleId))
                },
                onNavigateToEditVehicle = { vehicleId ->
                    navController.navigate(Destinations.vehicleEdit(vehicleId))
                },
                onNavigateToVehicleEvents = { vehicleId ->
                    navController.navigate(Destinations.vehicleEvents(vehicleId))
                },
                vehicleUpdated = vehicleUpdated,
                onVehicleUpdatedConsumed = { entry.savedStateHandle["vehicle_updated"] = false },
            )
        }

        // ── Atualizar odômetro ─────────────────────────────────────────────
        composable(
            route = Destinations.VEHICLE_ODOMETER,
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.IntType },
                navArgument("currentKm") { type = NavType.IntType },
            ),
        ) {
            UpdateOdometerScreen(
                onBack = { navController.popBackStack() },
                onSaved = {
                    runCatching {
                        navController.getBackStackEntry(Destinations.VEHICLE_DETAILS)
                            .savedStateHandle["odometer_updated"] = true
                    }
                    navController.popBackStack()
                },
                onNavigateToLogin = {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // ── Detalhes do abastecimento ──────────────────────────────────────
        composable(
            route = Destinations.REFUEL_DETAILS,
            arguments = listOf(navArgument("refuelId") { type = NavType.IntType }),
        ) { entry ->
            val refuelId = entry.arguments?.getInt("refuelId") ?: return@composable
            val refuelUpdated by entry.savedStateHandle
                .getStateFlow("refuel_updated", false)
                .collectAsStateWithLifecycle()

            RefuelDetailsScreen(
                onBack           = { navController.popBackStack() },
                onNavigateToEdit = { navController.navigate(Destinations.refuelEdit(refuelId)) },
                onDeletedNavigateBack = {
                    runCatching {
                        navController.getBackStackEntry(Destinations.MAIN_CONTAINER)
                            .savedStateHandle["history_needs_refresh"] = true
                    }
                    navController.popBackStack()
                },
                refuelUpdated           = refuelUpdated,
                onRefuelUpdatedConsumed = { entry.savedStateHandle["refuel_updated"] = false },
            )
        }

        // ── Edição de abastecimento ────────────────────────────────────────
        composable(
            route = Destinations.REFUEL_EDIT,
            arguments = listOf(navArgument("refuelId") { type = NavType.IntType }),
        ) {
            EditRefuelScreen(
                onBack  = { navController.popBackStack() },
                onSaved = {
                    runCatching {
                        navController.getBackStackEntry(Destinations.REFUEL_DETAILS)
                            .savedStateHandle["refuel_updated"] = true
                    }
                    runCatching {
                        navController.getBackStackEntry(Destinations.MAIN_CONTAINER)
                            .savedStateHandle["history_needs_refresh"] = true
                    }
                    navController.popBackStack()
                },
            )
        }

        // ── Lista de eventos do veículo ────────────────────────────────────
        composable(
            route = Destinations.VEHICLE_EVENTS,
            arguments = listOf(navArgument("vehicleId") { type = NavType.IntType }),
        ) { entry ->
            val eventCreated by entry.savedStateHandle
                .getStateFlow("event_created", false)
                .collectAsStateWithLifecycle()
            val eventDeleted by entry.savedStateHandle
                .getStateFlow("event_deleted", -1)
                .collectAsStateWithLifecycle()
            val eventUpdated by entry.savedStateHandle
                .getStateFlow("event_updated", false)
                .collectAsStateWithLifecycle()

            VehicleEventsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToCreate = { vehicleId ->
                    navController.navigate(Destinations.vehicleEventCreate(vehicleId))
                },
                onNavigateToDetails = { eventId ->
                    navController.navigate(Destinations.vehicleEventDetails(eventId))
                },
                onNavigateToRefuelDetails = { refuelId ->
                    navController.navigate(Destinations.refuelDetails(refuelId))
                },
                onNavigateToLogin = {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                eventCreated = eventCreated,
                onEventCreatedConsumed = { entry.savedStateHandle["event_created"] = false },
                eventDeleted = eventDeleted,
                onEventDeletedConsumed = { entry.savedStateHandle["event_deleted"] = -1 },
                eventUpdated = eventUpdated,
                onEventUpdatedConsumed = { entry.savedStateHandle["event_updated"] = false },
            )
        }

        // ── Criar evento do veículo ────────────────────────────────────────
        composable(
            route = Destinations.VEHICLE_EVENT_CREATE,
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.IntType },
                navArgument("category") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("guestMode") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            CreateVehicleEventScreen(
                onBack = { navController.popBackStack() },
                onSaved = {
                    val signaled = runCatching {
                        navController.getBackStackEntry(Destinations.VEHICLE_EVENTS)
                            .savedStateHandle["event_created"] = true
                        true
                    }.getOrDefault(false)
                    if (!signaled) {
                        runCatching {
                            navController.getBackStackEntry(Destinations.MAIN_CONTAINER)
                                .savedStateHandle["tab_event_created"] = true
                        }
                    }
                    // Um evento novo afeta o gasto total exibido no dashboard da Home,
                    // então sinaliza independentemente de qual aba recebeu o evento acima.
                    runCatching {
                        navController.getBackStackEntry(Destinations.MAIN_CONTAINER)
                            .savedStateHandle["home_needs_refresh"] = true
                    }
                    navController.popBackStack()
                },
                onNavigateToLogin = {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // ── Detalhes do evento ─────────────────────────────────────────────
        composable(
            route = Destinations.VEHICLE_EVENT_DETAILS,
            arguments = listOf(navArgument("eventId") { type = NavType.IntType }),
        ) { entry ->
            val eventId = entry.arguments?.getInt("eventId") ?: return@composable
            val eventUpdated by entry.savedStateHandle
                .getStateFlow("event_updated", false)
                .collectAsStateWithLifecycle()

            VehicleEventDetailsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToEdit = { id ->
                    navController.navigate(Destinations.vehicleEventEdit(id))
                },
                onDeletedBack = {
                    val signaled = runCatching {
                        navController.getBackStackEntry(Destinations.VEHICLE_EVENTS)
                            .savedStateHandle["event_deleted"] = eventId
                        true
                    }.getOrDefault(false)
                    if (!signaled) {
                        runCatching {
                            navController.getBackStackEntry(Destinations.MAIN_CONTAINER)
                                .savedStateHandle["tab_event_deleted"] = eventId
                        }
                    }
                    runCatching {
                        navController.getBackStackEntry(Destinations.MAIN_CONTAINER)
                            .savedStateHandle["home_needs_refresh"] = true
                    }
                    navController.popBackStack()
                },
                onNavigateToLogin = {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                eventUpdated = eventUpdated,
                onEventUpdatedConsumed = { entry.savedStateHandle["event_updated"] = false },
            )
        }

        // ── Editar evento do veículo ───────────────────────────────────────
        composable(
            route = Destinations.VEHICLE_EVENT_EDIT,
            arguments = listOf(navArgument("eventId") { type = NavType.IntType }),
        ) {
            EditVehicleEventScreen(
                onBack = { navController.popBackStack() },
                onSaved = {
                    runCatching {
                        navController.getBackStackEntry(Destinations.VEHICLE_EVENT_DETAILS)
                            .savedStateHandle["event_updated"] = true
                    }
                    val signaled = runCatching {
                        navController.getBackStackEntry(Destinations.VEHICLE_EVENTS)
                            .savedStateHandle["event_updated"] = true
                        true
                    }.getOrDefault(false)
                    if (!signaled) {
                        runCatching {
                            navController.getBackStackEntry(Destinations.MAIN_CONTAINER)
                                .savedStateHandle["tab_event_updated"] = true
                        }
                    }
                    runCatching {
                        navController.getBackStackEntry(Destinations.MAIN_CONTAINER)
                            .savedStateHandle["home_needs_refresh"] = true
                    }
                    navController.popBackStack()
                },
                onNavigateToLogin = {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // ── Container principal (Home + abas) ──────────────────────────────
        composable(Destinations.MAIN_CONTAINER) { entry ->
            val passwordChanged by entry.savedStateHandle
                .getStateFlow("password_changed", false)
                .collectAsStateWithLifecycle()
            val historyNeedsRefresh by entry.savedStateHandle
                .getStateFlow("history_needs_refresh", false)
                .collectAsStateWithLifecycle()
            val homeNeedsRefresh by entry.savedStateHandle
                .getStateFlow("home_needs_refresh", false)
                .collectAsStateWithLifecycle()
            val tabEventCreated by entry.savedStateHandle
                .getStateFlow("tab_event_created", false)
                .collectAsStateWithLifecycle()
            val tabEventDeleted by entry.savedStateHandle
                .getStateFlow("tab_event_deleted", -1)
                .collectAsStateWithLifecycle()
            val tabEventUpdated by entry.savedStateHandle
                .getStateFlow("tab_event_updated", false)
                .collectAsStateWithLifecycle()
            val profileUpdated by entry.savedStateHandle
                .getStateFlow("profile_updated", false)
                .collectAsStateWithLifecycle()

            MainContainerScreen(
                onNavigateToLogin = {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToAddVehicle = {
                    navController.navigate(Destinations.VEHICLE_ADD)
                },
                onNavigateToEventCreate = { vehicleId ->
                    navController.navigate(Destinations.vehicleEventCreate(vehicleId))
                },
                onNavigateToMaintenanceEventCreate = { vehicleId, category ->
                    navController.navigate(Destinations.vehicleEventCreate(vehicleId, category.name))
                },
                onNavigateToEventDetails = { eventId ->
                    navController.navigate(Destinations.vehicleEventDetails(eventId))
                },
                onNavigateToRefuelDetails = { refuelId ->
                    navController.navigate(Destinations.refuelDetails(refuelId))
                },
                onNavigateToVehicles = {
                    navController.navigate(Destinations.VEHICLE_MANAGE)
                },
                onNavigateToEditProfile = {
                    navController.navigate(Destinations.EDIT_PROFILE)
                },
                onNavigateToChangePassword = {
                    navController.navigate(Destinations.CHANGE_PASSWORD)
                },
                passwordChanged = passwordChanged,
                onPasswordChangedConsumed = {
                    entry.savedStateHandle["password_changed"] = false
                },
                historyNeedsRefresh = historyNeedsRefresh,
                onHistoryRefreshConsumed = {
                    entry.savedStateHandle["history_needs_refresh"] = false
                },
                homeNeedsRefresh = homeNeedsRefresh,
                onHomeRefreshConsumed = {
                    entry.savedStateHandle["home_needs_refresh"] = false
                },
                tabEventCreated = tabEventCreated,
                onTabEventCreatedConsumed = {
                    entry.savedStateHandle["tab_event_created"] = false
                },
                tabEventDeleted = tabEventDeleted,
                onTabEventDeletedConsumed = {
                    entry.savedStateHandle["tab_event_deleted"] = -1
                },
                tabEventUpdated = tabEventUpdated,
                onTabEventUpdatedConsumed = {
                    entry.savedStateHandle["tab_event_updated"] = false
                },
                profileUpdated = profileUpdated,
                onProfileUpdatedConsumed = {
                    entry.savedStateHandle["profile_updated"] = false
                },
            )
        }

        // ── Edição de perfil ───────────────────────────────────────────────
        composable(Destinations.EDIT_PROFILE) {
            EditProfileScreen(
                onBack = { navController.popBackStack() },
                onSaved = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("profile_updated", true)
                    navController.popBackStack()
                },
                onNavigateToLogin = {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // ── Troca de senha ─────────────────────────────────────────────────
        composable(Destinations.CHANGE_PASSWORD) {
            ChangePasswordScreen(
                onSuccess = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("password_changed", true)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
    }
}

private fun AnimatedContentTransitionScope<*>.defaultEnter() =
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(250),
    )

private fun AnimatedContentTransitionScope<*>.defaultExit() =
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(250),
    )

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface NavEntryPoint {
    fun sessionStore(): SessionStore
}