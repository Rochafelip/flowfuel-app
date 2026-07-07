package com.flowfuel.app.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.datastore.VehicleMaintenancePrefsStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auth.domain.usecase.LogoutUseCase
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetDashboardUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetFinancialSummaryUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetRecentActivityUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetUpcomingMaintenanceUseCase
import com.flowfuel.app.feature.station.domain.NearbyStationsPrefetcher
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehiclesUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.SetActiveVehicleUseCase
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsTotalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getActiveVehicle: GetActiveVehicleUseCase,
    private val getDashboard: GetDashboardUseCase,
    private val logout: LogoutUseCase,
    private val sessionStore: SessionStore,
    private val getVehicles: GetVehiclesUseCase,
    private val setActiveVehicle: SetActiveVehicleUseCase,
    private val stationsPrefetcher: NearbyStationsPrefetcher,
    private val getVehicleEventsTotal: GetVehicleEventsTotalUseCase,
    private val getFinancialSummary: GetFinancialSummaryUseCase,
    private val getRecentActivity: GetRecentActivityUseCase,
    private val getUpcomingMaintenance: GetUpcomingMaintenanceUseCase,
    private val maintenancePrefsStore: VehicleMaintenancePrefsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** ID do veículo carregado por último — usado pelos retries de seção. */
    private var loadedVehicleId: Int? = null

    init { load() }

    // ─── Carregamento ─────────────────────────────────────────────────────────

    fun load() {
        stationsPrefetcher.prefetch()
        _state.update { it.copy(screenState = HomeScreenState.Loading) }
        viewModelScope.launch {
            val storedVehicleId = sessionStore.activeVehicleIdFlow.first()
            val vehicleResult = getActiveVehicle()

            if (vehicleResult is AppResult.Failure) {
                handleGlobalError(vehicleResult.error)
                return@launch
            }

            val vehicle = (vehicleResult as AppResult.Success).value
            val vehicleId = storedVehicleId ?: vehicle.id

            if (storedVehicleId == null) {
                Timber.d("Home › vehicleId não estava no SessionStore, persistindo id=${vehicle.id}")
                sessionStore.saveActiveVehicleId(vehicle.id)
            }
            loadedVehicleId = vehicleId

            when (val dashboardResult = fetchDashboardWithEventsTotal(vehicleId)) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            screenState = HomeScreenState.Success(
                                vehicle = vehicle,
                                dashboard = dashboardResult.value,
                            ),
                        )
                    }
                    launch { loadFinancialSummary(vehicleId) }
                    launch { loadRecentActivity(vehicleId) }
                    launch { loadUpcomingMaintenance(vehicleId, vehicle.currentKm) }
                }
                is AppResult.Failure -> handleGlobalError(dashboardResult.error)
            }
        }
    }

    private suspend fun loadFinancialSummary(vehicleId: Int) {
        val sectionState = when (val result = getFinancialSummary(vehicleId)) {
            is AppResult.Success -> SectionState.Success(result.value)
            is AppResult.Failure -> SectionState.Error(result.error)
        }
        _state.update { state ->
            val success = state.screenState as? HomeScreenState.Success ?: return@update state
            // Descarta o resultado se, enquanto o fetch estava em andamento, o usuário
            // trocou de veículo — o Success atual já não pertence a este vehicleId.
            if (success.vehicle.id != vehicleId) return@update state
            state.copy(screenState = success.copy(financialSummary = sectionState))
        }
    }

    private suspend fun loadRecentActivity(vehicleId: Int) {
        val sectionState = when (val result = getRecentActivity(vehicleId)) {
            is AppResult.Success -> SectionState.Success(result.value)
            is AppResult.Failure -> SectionState.Error(result.error)
        }
        _state.update { state ->
            val success = state.screenState as? HomeScreenState.Success ?: return@update state
            // Descarta o resultado se, enquanto o fetch estava em andamento, o usuário
            // trocou de veículo — o Success atual já não pertence a este vehicleId.
            if (success.vehicle.id != vehicleId) return@update state
            state.copy(screenState = success.copy(recentActivity = sectionState))
        }
    }

    /** Reexecuta só o resumo financeiro, sem recarregar o resto da tela. */
    fun retryFinancialSummary() {
        val vehicleId = loadedVehicleId ?: return
        _state.update { state ->
            val success = state.screenState as? HomeScreenState.Success ?: return@update state
            state.copy(screenState = success.copy(financialSummary = SectionState.Loading))
        }
        viewModelScope.launch { loadFinancialSummary(vehicleId) }
    }

    /** Reexecuta só a atividade recente, sem recarregar o resto da tela. */
    fun retryRecentActivity() {
        val vehicleId = loadedVehicleId ?: return
        _state.update { state ->
            val success = state.screenState as? HomeScreenState.Success ?: return@update state
            state.copy(screenState = success.copy(recentActivity = SectionState.Loading))
        }
        viewModelScope.launch { loadRecentActivity(vehicleId) }
    }

    private suspend fun loadUpcomingMaintenance(vehicleId: Int, currentKm: Int) {
        val sectionState = when (val result = getUpcomingMaintenance(vehicleId, currentKm)) {
            is AppResult.Success -> SectionState.Success(result.value)
            is AppResult.Failure -> SectionState.Error(result.error)
        }
        _state.update { state ->
            val success = state.screenState as? HomeScreenState.Success ?: return@update state
            if (success.vehicle.id != vehicleId) return@update state
            state.copy(screenState = success.copy(upcomingMaintenance = sectionState))
        }
    }

    /** Reexecuta só os lembretes de manutenção, sem recarregar o resto da tela. */
    fun retryUpcomingMaintenance() {
        val success = _state.value.screenState as? HomeScreenState.Success ?: return
        _state.update { it.copy(screenState = success.copy(upcomingMaintenance = SectionState.Loading)) }
        viewModelScope.launch { loadUpcomingMaintenance(success.vehicle.id, success.vehicle.currentKm) }
    }

    // ─── Data de licenciamento ────────────────────────────────────────────────

    fun openLicensingDueDatePicker() = _state.update { it.copy(showLicensingDueDatePicker = true) }

    fun closeLicensingDueDatePicker() = _state.update { it.copy(showLicensingDueDatePicker = false) }

    /** Salva a data escolhida localmente e recarrega só a seção de lembretes. */
    fun onLicensingDueDateSelected(isoDate: String) {
        val vehicleId = loadedVehicleId ?: return
        _state.update { it.copy(showLicensingDueDatePicker = false) }
        viewModelScope.launch {
            maintenancePrefsStore.saveLicensingDueDate(vehicleId, isoDate)
            retryUpcomingMaintenance()
        }
    }

    // ─── Diálogo "Sobre" ──────────────────────────────────────────────────────

    fun openAboutDialog() = _state.update { it.copy(showAboutDialog = true) }

    fun closeAboutDialog() = _state.update { it.copy(showAboutDialog = false) }

    // ─── Pull-to-refresh ──────────────────────────────────────────────────────

    /**
     * Atualiza o dashboard sem substituir o screenState por Loading.
     * O indicador de PTR é controlado por [HomeUiState.isRefreshing].
     * Só executa se já há dados exibidos (Success) e não há refresh em andamento.
     */
    fun refresh() {
        if (_state.value.screenState !is HomeScreenState.Success) return
        if (_state.value.isRefreshing) return

        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val storedVehicleId = sessionStore.activeVehicleIdFlow.first()
            val vehicleResult = getActiveVehicle()
            if (vehicleResult is AppResult.Failure) {
                _state.update { it.copy(isRefreshing = false) }
                return@launch
            }

            val vehicle = (vehicleResult as AppResult.Success).value
            val vehicleId = storedVehicleId ?: vehicle.id
            loadedVehicleId = vehicleId

            when (val dashboardResult = fetchDashboardWithEventsTotal(vehicleId)) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            isRefreshing = false,
                            screenState = HomeScreenState.Success(
                                vehicle = vehicle,
                                dashboard = dashboardResult.value,
                            ),
                        )
                    }
                    launch { loadFinancialSummary(vehicleId) }
                    launch { loadRecentActivity(vehicleId) }
                    launch { loadUpcomingMaintenance(vehicleId, vehicle.currentKm) }
                }
                is AppResult.Failure ->
                    _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    // ─── Seletor de veículo ───────────────────────────────────────────────────

    /** Abre o BottomSheet e carrega a lista de veículos. */
    fun openVehicleSwitcher() {
        _state.update {
            it.copy(
                showVehicleSwitcher = true,
                vehicleSwitcherState = VehicleSwitcherState.Loading,
            )
        }
        viewModelScope.launch {
            val activeId = sessionStore.activeVehicleIdFlow.first()
            when (val result = getVehicles()) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        vehicleSwitcherState = VehicleSwitcherState.Success(
                            vehicles = result.value,
                            activeId = activeId,
                        ),
                    )
                }
                is AppResult.Failure -> _state.update {
                    it.copy(vehicleSwitcherState = VehicleSwitcherState.Error(result.error))
                }
            }
        }
    }

    /** Fecha o BottomSheet e descarta o estado da lista. */
    fun closeVehicleSwitcher() = _state.update {
        it.copy(showVehicleSwitcher = false, vehicleSwitcherState = VehicleSwitcherState.Idle)
    }

    /**
     * Troca o veículo ativo:
     * 1. Fecha o sheet imediatamente (otimista).
     * 2. Persiste o novo ID e chama a API.
     * 3. Recarrega o dashboard com o novo veículo.
     */
    fun onVehicleSwitch(vehicleId: Int) {
        _state.update { it.copy(showVehicleSwitcher = false, vehicleSwitcherState = VehicleSwitcherState.Idle) }
        viewModelScope.launch {
            setActiveVehicle(vehicleId)
            stationsPrefetcher.prefetch()
            load()
        }
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    /** Abre o dialog de confirmação sem executar o logout ainda. */
    fun openLogoutDialog() = _state.update { it.copy(showLogoutDialog = true) }

    /** Fecha o dialog sem fazer nada. */
    fun closeLogoutDialog() = _state.update { it.copy(showLogoutDialog = false) }

    /** Executa o logout — chamado apenas após confirmação do usuário. */
    fun logout() {
        _state.update { it.copy(showLogoutDialog = false) }
        viewModelScope.launch {
            logout.invoke()
            _effects.send(HomeEffect.NavigateToLogin)
        }
    }

    // ─── Helpers privados ─────────────────────────────────────────────────────

    /**
     * O dashboard remoto soma apenas abastecimentos. Eventos do veículo
     * (manutenção, troca de óleo etc.) vivem em outra coleção e não entram
     * nesse total, então somamos aqui para que "Gasto total" reflita o veículo
     * como um todo.
     */
    private suspend fun fetchDashboardWithEventsTotal(vehicleId: Int): AppResult<DashboardData> {
        val dashboardResult = getDashboard(vehicleId)
        if (dashboardResult is AppResult.Failure) return dashboardResult
        val dashboard = (dashboardResult as AppResult.Success).value

        val eventsTotal = when (val eventsResult = getVehicleEventsTotal(vehicleId)) {
            is AppResult.Success -> eventsResult.value
            is AppResult.Failure -> {
                Timber.w("Home: falha ao somar eventos do veículo, exibindo apenas abastecimentos")
                0.0
            }
        }
        return AppResult.Success(dashboard.copy(totalSpent = dashboard.totalSpent + eventsTotal))
    }

    private suspend fun handleGlobalError(error: AppError) {
        Timber.e("Home: error → $error")
        if (error == AppError.Unauthorized) {
            sessionStore.clear()
            _effects.send(HomeEffect.NavigateToLogin)
        } else {
            _state.update { it.copy(screenState = HomeScreenState.Error(error)) }
        }
    }
}