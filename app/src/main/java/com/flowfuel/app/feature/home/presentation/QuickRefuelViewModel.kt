package com.flowfuel.app.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.FieldError
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.CreateRefuelRequest
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

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

// ─── Estado e efeitos ─────────────────────────────────────────────────────────

data class QuickRefuelUiState(
    val showSheet: Boolean = false,
    /** Veículo ativo, buscado ao abrir o sheet; null enquanto carrega. */
    val vehicle: ActiveVehicleData? = null,
    val form: RefuelFormState = RefuelFormState(),
    val isSubmitting: Boolean = false,
    val submitError: AppError? = null,
)

sealed interface QuickRefuelEffect {
    data object RefuelRegistered : QuickRefuelEffect
}

/**
 * Dono do sheet de registro rápido de abastecimento. Vive no escopo do
 * [com.flowfuel.app.navigation.MainContainerScreen] (não em [HomeViewModel]),
 * para que o FAB global consiga abri-lo a partir de qualquer aba, não só da Home.
 */
@HiltViewModel
class QuickRefuelViewModel @Inject constructor(
    private val getActiveVehicle: GetActiveVehicleUseCase,
    private val createRefuel: CreateRefuelUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickRefuelUiState())
    val state: StateFlow<QuickRefuelUiState> = _state.asStateFlow()

    private val _effects = Channel<QuickRefuelEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun openSheet() {
        _state.update { it.copy(showSheet = true) }
        viewModelScope.launch {
            when (val result = getActiveVehicle()) {
                is AppResult.Success -> _state.update { it.copy(vehicle = result.value) }
                is AppResult.Failure ->
                    Timber.e("QuickRefuel: falha ao buscar veículo ativo → ${result.error}")
            }
        }
    }

    fun closeSheet() = _state.update {
        it.copy(showSheet = false, form = RefuelFormState(), submitError = null)
    }

    // ─── Handlers do formulário ───────────────────────────────────────────────

    fun onOdometerChange(v: String) = _state.update {
        it.copy(form = it.form.copy(
            odometer = v.filter(Char::isDigit),
            odometerError = false,
            serverErrors = null,
        ))
    }

    fun onLitersChange(v: String) = _state.update {
        it.copy(form = it.form.copy(
            liters = v.filter { c -> c.isDigit() || c == ',' || c == '.' },
            litersError = false,
            serverErrors = null,
        ))
    }

    /** Recebe apenas dígitos; armazena em centavos. Ex: "24944" = R$ 249,44 */
    fun onTotalPriceInput(digits: String) {
        val cleaned = digits.filter(Char::isDigit).trimStart('0').ifEmpty { "" }
        _state.update {
            it.copy(form = it.form.copy(
                totalPriceRaw = cleaned.take(7),   // max R$ 99.999,99
                totalPriceError = false,
                serverErrors = null,
            ))
        }
    }

    fun onFullTankToggle(checked: Boolean) = _state.update {
        it.copy(form = it.form.copy(fullTank = checked))
    }

    fun onRefuelTypeChange(type: String) = _state.update {
        it.copy(form = it.form.copy(refuelType = type, refuelTypeError = false, serverErrors = null))
    }

    fun onOdometerInputModeChange(mode: OdometerInputMode) = _state.update {
        it.copy(form = it.form.copy(
            odometerInputMode = mode,
            odometer          = "",
            tripKm            = "",
            odometerError     = false,
            tripKmError       = false,
            serverErrors      = null,
        ))
    }

    fun onTripKmChange(v: String) = _state.update {
        it.copy(form = it.form.copy(
            tripKm       = v.filter { c -> c.isDigit() || c == ',' || c == '.' },
            tripKmError  = false,
            serverErrors = null,
        ))
    }

    // ─── Submissão do formulário ──────────────────────────────────────────────

    fun submitRefuel() {
        val s = _state.value
        val form = s.form
        val vehicle = s.vehicle ?: return
        val isHybrid = vehicle.energyType.equals("HYBRID", ignoreCase = true)

        val isTripMode = form.odometerInputMode == OdometerInputMode.TRIP

        val odometerInvalid = !isTripMode && form.odometer.isBlank()
        val tripInvalid = isTripMode && (
            form.tripKm.isBlank() ||
            form.tripKm.replace(',', '.').toDoubleOrNull()?.let { it <= 0.0 } != false
        )
        val litersInvalid = form.liters.isBlank()
            || form.liters.replace(',', '.').toDoubleOrNull() == null
        val priceInvalid      = form.totalPriceCents == 0L
        val refuelTypeInvalid = isHybrid && form.refuelType == null

        if (odometerInvalid || tripInvalid || litersInvalid || priceInvalid || refuelTypeInvalid) {
            _state.update {
                it.copy(form = it.form.copy(
                    odometerError   = odometerInvalid,
                    tripKmError     = tripInvalid,
                    litersError     = litersInvalid,
                    totalPriceError = priceInvalid,
                    refuelTypeError = refuelTypeInvalid,
                ))
            }
            return
        }

        val resolvedRefuelType = when {
            isHybrid -> form.refuelType
            vehicle.energyType.equals("ELECTRIC", ignoreCase = true) -> "ELECTRIC"
            else -> "FUEL"
        }

        val odometer = if (isTripMode)
            vehicle.currentKm.toDouble() + form.tripKm.replace(',', '.').toDouble()
        else
            form.odometerDouble

        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            val result = createRefuel(
                CreateRefuelRequest(
                    vehicleId  = vehicle.id,
                    odometer   = odometer,
                    liters     = form.liters.replace(',', '.').toDouble(),
                    totalPrice = form.totalPriceDouble,
                    fullTank   = form.fullTank,
                    refuelType = resolvedRefuelType,
                )
            )
            when (result) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            showSheet    = false,
                            form         = RefuelFormState(),
                        )
                    }
                    _effects.send(QuickRefuelEffect.RefuelRegistered)
                }
                is AppResult.Failure -> {
                    Timber.e("submitRefuel: ${result.error}")
                    val apiErr     = result.error as? AppError.Api
                    val fieldErrors = apiErr?.takeIf { it.code == "VALIDATION_FAILED" }?.fieldErrors
                    if (!fieldErrors.isNullOrEmpty()) {
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                form         = it.form.copy(serverErrors = fieldErrors),
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(isSubmitting = false, submitError = result.error)
                        }
                    }
                }
            }
        }
    }
}
