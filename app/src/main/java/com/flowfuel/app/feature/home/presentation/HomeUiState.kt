package com.flowfuel.app.feature.home.presentation

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.home.domain.model.FinancialSummary
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceItem
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleTimelineItem

// ─── Estado da tela ───────────────────────────────────────────────────────────

sealed interface HomeScreenState {
    data object Loading : HomeScreenState
    data class Success(
        val vehicle: ActiveVehicleData,
        val dashboard: DashboardData,
        val financialSummary: SectionState<FinancialSummary> = SectionState.Loading,
        val recentActivity: SectionState<List<VehicleTimelineItem>> = SectionState.Loading,
        val upcomingMaintenance: SectionState<List<UpcomingMaintenanceItem>> = SectionState.Loading,
    ) : HomeScreenState
    data class Error(val error: AppError) : HomeScreenState
}

/** Estado independente de uma seção carregada em paralelo ao restante da tela. */
sealed interface SectionState<out T> {
    data object Loading : SectionState<Nothing>
    data class Success<T>(val value: T) : SectionState<T>
    data class Error(val error: AppError) : SectionState<Nothing>
}

// ─── Estado do seletor de veículo ─────────────────────────────────────────────

sealed interface VehicleSwitcherState {
    /** Sheet fechada / não inicializada. */
    data object Idle : VehicleSwitcherState
    /** Carregando lista de veículos. */
    data object Loading : VehicleSwitcherState
    /** Lista disponível; [activeId] pode ser null se nenhum ainda foi escolhido. */
    data class Success(
        val vehicles: List<Vehicle>,
        val activeId: Int?,
    ) : VehicleSwitcherState
    /** Falha ao buscar a lista. */
    data class Error(val error: AppError) : VehicleSwitcherState
}

// ─── Estado global da tela ────────────────────────────────────────────────────

data class HomeUiState(
    val screenState: HomeScreenState = HomeScreenState.Loading,
    // ── Pull-to-refresh ────────────────────────────────────────────────────
    /** true somente durante um pull-to-refresh; não afeta o screenState. */
    val isRefreshing: Boolean = false,
    // ── Seletor de veículo ─────────────────────────────────────────────────
    val showVehicleSwitcher: Boolean = false,
    val vehicleSwitcherState: VehicleSwitcherState = VehicleSwitcherState.Idle,
    // ── Confirmação de logout ──────────────────────────────────────────────
    val showLogoutDialog: Boolean = false,
    // ── Data de licenciamento (lembrete de manutenção) ─────────────────────
    val showLicensingDueDatePicker: Boolean = false,
    // ── Diálogo "Sobre" ─────────────────────────────────────────────────────
    val showAboutDialog: Boolean = false,
)

// ─── Efeitos de navegação ─────────────────────────────────────────────────────

sealed interface HomeEffect {
    data object NavigateToLogin : HomeEffect
}