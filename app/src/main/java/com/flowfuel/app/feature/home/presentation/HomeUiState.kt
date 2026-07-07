package com.flowfuel.app.feature.home.presentation

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.FieldError
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.home.domain.model.FinancialSummary
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

// ─── Modo de entrada do odômetro ──────────────────────────────────────────────

enum class OdometerInputMode { TRIP, ODOMETER }

// ─── Formulário de abastecimento ─────────────────────────────────────────────

/**
 * Estado do formulário de registro rápido.
 *
 * [odometer] armazena apenas dígitos representando décimos de km
 * (ex: "1000005" = 100.000,5 km). A [OdometerVisualTransformation] formata
 * em tempo real no campo de texto.
 *
 * [totalPriceRaw] armazena apenas dígitos (ex: "24944" = R$ 249,44).
 * A transformação visual formata em tempo real no campo de texto.
 *
 * [refuelType] é `null` para veículos de combustão/elétrico puro (inferido
 * automaticamente). Para híbridos o usuário deve escolher "FUEL" ou "ELECTRIC".
 */
data class RefuelFormState(
    val odometer: String = "",   // somente dígitos, representa décimos de km
    val liters: String = "",
    val totalPriceRaw: String = "",   // somente dígitos, representa centavos
    val fullTank: Boolean = true,
    val refuelType: String? = null,
    val odometerInputMode: OdometerInputMode = OdometerInputMode.TRIP,
    val tripKm: String = "",
    val odometerError: Boolean = false,
    val litersError: Boolean = false,
    val totalPriceError: Boolean = false,
    val refuelTypeError: Boolean = false,
    val tripKmError: Boolean = false,
    val serverErrors: List<FieldError>? = null,
) {
    /** Odômetro em km como Double (décimos → km). Ex: "1000005" → 100000.5 */
    val odometerDouble: Double get() = (odometer.toLongOrNull() ?: 0L) / 10.0

    /** Valor em centavos para cálculos internos. */
    val totalPriceCents: Long get() = totalPriceRaw.toLongOrNull() ?: 0L

    /** Valor como Double para enviar à API. */
    val totalPriceDouble: Double get() = totalPriceCents / 100.0

    fun canSubmit(isHybrid: Boolean): Boolean {
        val inputValid = when (odometerInputMode) {
            OdometerInputMode.TRIP ->
                tripKm.isNotBlank() &&
                tripKm.replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } == true
            OdometerInputMode.ODOMETER ->
                odometer.isNotBlank()
        }
        return inputValid
            && liters.isNotBlank()
            && totalPriceCents > 0
            && (!isHybrid || refuelType != null)
    }
}

// ─── Estado global da tela ────────────────────────────────────────────────────

data class HomeUiState(
    val screenState: HomeScreenState = HomeScreenState.Loading,
    val showRefuelSheet: Boolean = false,
    val refuelForm: RefuelFormState = RefuelFormState(),
    val isSubmittingRefuel: Boolean = false,
    val submitError: AppError? = null,
    // ── Pull-to-refresh ────────────────────────────────────────────────────
    /** true somente durante um pull-to-refresh; não afeta o screenState. */
    val isRefreshing: Boolean = false,
    // ── Seletor de veículo ─────────────────────────────────────────────────
    val showVehicleSwitcher: Boolean = false,
    val vehicleSwitcherState: VehicleSwitcherState = VehicleSwitcherState.Idle,
    // ── Confirmação de logout ──────────────────────────────────────────────
    val showLogoutDialog: Boolean = false,
)

// ─── Efeitos de navegação ─────────────────────────────────────────────────────

sealed interface HomeEffect {
    data object NavigateToLogin : HomeEffect
    data object RefuelRegistered : HomeEffect  // feedback de sucesso
}