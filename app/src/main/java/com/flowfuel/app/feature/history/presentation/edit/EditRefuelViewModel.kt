package com.flowfuel.app.feature.history.presentation.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.model.UpdateRefuelRequest
import com.flowfuel.app.feature.history.domain.usecase.GetRefuelDetailsUseCase
import com.flowfuel.app.feature.history.domain.usecase.UpdateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.presentation.RefuelFormState
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

@HiltViewModel
class EditRefuelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getRefuelDetails: GetRefuelDetailsUseCase,
    private val updateRefuel: UpdateRefuelUseCase,
    private val getActiveVehicle: GetActiveVehicleUseCase,
) : ViewModel() {

    private val refuelId: Int = checkNotNull(savedStateHandle["refuelId"])

    private val _state = MutableStateFlow(EditRefuelUiState())
    val state: StateFlow<EditRefuelUiState> = _state.asStateFlow()

    private val _effects = Channel<EditRefuelEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(screenState = EditRefuelScreenState.Loading) }
        viewModelScope.launch {
            val vehicleResult = getActiveVehicle()
            val energyType = (vehicleResult as? AppResult.Success)?.value?.energyType ?: "FUEL"

            when (val refuelResult = getRefuelDetails(refuelId)) {
                is AppResult.Success -> {
                    val item = refuelResult.value
                    val form = RefuelFormState(
                        odometer      = item.odometer?.let { (it * 10).toLong().toString() } ?: "",
                        liters        = "%.2f".format(item.energyAmount).replace('.', ','),
                        totalPriceRaw = ((item.pricePerUnit * item.energyAmount) * 100).toLong().toString(),
                        fullTank      = item.fullTank,
                        refuelType    = item.refuelType,
                    )
                    _state.update {
                        it.copy(
                            screenState       = EditRefuelScreenState.Ready,
                            form              = form,
                            vehicleEnergyType = energyType,
                        )
                    }
                }
                is AppResult.Failure -> _state.update {
                    it.copy(screenState = EditRefuelScreenState.Error(refuelResult.error))
                }
            }
        }
    }

    // ─── Handlers do formulário ───────────────────────────────────────────────

    fun onOdometerChange(v: String) = _state.update {
        it.copy(form = it.form.copy(odometer = v.filter(Char::isDigit), odometerError = false))
    }

    fun onLitersChange(v: String) = _state.update {
        it.copy(form = it.form.copy(
            liters = v.filter { c -> c.isDigit() || c == ',' || c == '.' },
            litersError = false,
        ))
    }

    fun onTotalPriceInput(digits: String) {
        val cleaned = digits.filter(Char::isDigit).trimStart('0').ifEmpty { "" }
        _state.update {
            it.copy(form = it.form.copy(
                totalPriceRaw = cleaned.take(7),
                totalPriceError = false,
            ))
        }
    }

    fun onFullTankToggle(checked: Boolean) = _state.update {
        it.copy(form = it.form.copy(fullTank = checked))
    }

    fun onRefuelTypeChange(type: String) = _state.update {
        it.copy(form = it.form.copy(refuelType = type, refuelTypeError = false))
    }

    // ─── Submissão ────────────────────────────────────────────────────────────

    fun submit() {
        val s = _state.value
        val form = s.form
        val isHybrid = s.vehicleEnergyType.equals("HYBRID", ignoreCase = true)

        val odometerInvalid   = form.odometer.isBlank()
        val litersInvalid     = form.liters.isBlank()
            || form.liters.replace(',', '.').toDoubleOrNull() == null
        val priceInvalid      = form.totalPriceCents == 0L
        val refuelTypeInvalid = isHybrid && form.refuelType == null

        if (odometerInvalid || litersInvalid || priceInvalid || refuelTypeInvalid) {
            _state.update {
                it.copy(form = it.form.copy(
                    odometerError   = odometerInvalid,
                    litersError     = litersInvalid,
                    totalPriceError = priceInvalid,
                    refuelTypeError = refuelTypeInvalid,
                ))
            }
            return
        }

        val resolvedRefuelType = when {
            isHybrid -> form.refuelType
            s.vehicleEnergyType.equals("ELECTRIC", ignoreCase = true) -> "ELECTRIC"
            else -> "FUEL"
        }

        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            when (val result = updateRefuel(
                UpdateRefuelRequest(
                    id         = refuelId,
                    odometer   = form.odometerDouble,
                    liters     = form.liters.replace(',', '.').toDouble(),
                    totalPrice = form.totalPriceDouble,
                    fullTank   = form.fullTank,
                    refuelType = resolvedRefuelType,
                )
            )) {
                is AppResult.Success -> {
                    _state.update { it.copy(isSubmitting = false) }
                    _effects.send(EditRefuelEffect.Saved)
                }
                is AppResult.Failure -> {
                    Timber.e("EditRefuelVM: ${result.error}")
                    _state.update { it.copy(isSubmitting = false, submitError = result.error) }
                }
            }
        }
    }
}
