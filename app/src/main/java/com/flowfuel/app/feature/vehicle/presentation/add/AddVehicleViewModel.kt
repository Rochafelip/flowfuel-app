package com.flowfuel.app.feature.vehicle.presentation.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.FieldError
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import com.flowfuel.app.feature.vehicle.domain.usecase.CreateVehicleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddVehicleUiState(
    // — Etapa 1: Identificação
    val brand: String = "",
    val model: String = "",
    val manufactureYear: String = "",
    val modelYear: String = "",
    // — Etapa 2: Classificação
    val vehicleType: VehicleType = VehicleType.Car,
    val energyType: EnergyType = EnergyType.Combustion,
    val fuelType: FuelType = FuelType.Flex,
    // — Etapa 3: Detalhes
    val licensePlate: String = "",
    val color: String = "",
    val odometer: String = "",
    val tankCapacity: String = "",
    val batteryCapacity: String = "",
    // — Wizard
    val currentStep: Int = 1,
    /**
     * Incrementado a cada falha de validação para que a UI possa reagir via
     * [LaunchedEffect] mesmo quando os campos inválidos são os mesmos da tentativa anterior.
     */
    val stepAttempt: Int = 0,
    // — Erros de validação por campo
    val brandError: Boolean = false,
    val modelError: Boolean = false,
    val manufactureYearError: Boolean = false,
    val modelYearError: Boolean = false,
    val licensePlateError: Boolean = false,
    // — Estado global
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    val serverErrors: List<FieldError>? = null,
) {
    val showFuelType: Boolean
        get() = energyType == EnergyType.Combustion || energyType == EnergyType.Hybrid

    val showTankCapacity: Boolean
        get() = energyType == EnergyType.Combustion || energyType == EnergyType.Hybrid

    val showBatteryCapacity: Boolean
        get() = energyType == EnergyType.Electric || energyType == EnergyType.Hybrid

    /** Placa obrigatória; cor e odômetro são opcionais (default null/0). */
    val canSubmit: Boolean
        get() = brand.isNotBlank() && model.isNotBlank()
            && manufactureYear.isNotBlank() && modelYear.isNotBlank()
            && licensePlate.length >= 7
            && !isSubmitting
}

sealed interface AddVehicleEffect {
    data object NavigateBack : AddVehicleEffect
}

@HiltViewModel
class AddVehicleViewModel @Inject constructor(
    private val createVehicle: CreateVehicleUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AddVehicleUiState())
    val state: StateFlow<AddVehicleUiState> = _state.asStateFlow()

    private val _effects = Channel<AddVehicleEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    // — Etapa 1
    fun onBrandChange(v: String) =
        _state.update { it.copy(brand = v, brandError = false, error = null, serverErrors = null) }

    fun onModelChange(v: String) =
        _state.update { it.copy(model = v, modelError = false, error = null, serverErrors = null) }

    fun onManufactureYearChange(v: String) =
        _state.update {
            it.copy(
                manufactureYear      = v.filter(Char::isDigit).take(4),
                manufactureYearError = false,
                error                = null,
                serverErrors         = null,
            )
        }

    fun onModelYearChange(v: String) =
        _state.update {
            it.copy(
                modelYear      = v.filter(Char::isDigit).take(4),
                modelYearError = false,
                error          = null,
                serverErrors   = null,
            )
        }

    // — Etapa 2
    fun onVehicleTypeChange(v: VehicleType) = _state.update { it.copy(vehicleType = v) }
    fun onEnergyTypeChange(v: EnergyType)   = _state.update { it.copy(energyType = v) }
    fun onFuelTypeChange(v: FuelType)       = _state.update { it.copy(fuelType = v) }

    // — Etapa 3
    fun onLicensePlateChange(v: String) =
        _state.update { it.copy(licensePlate = v.uppercase().take(7), licensePlateError = false, error = null, serverErrors = null) }

    fun onColorChange(v: String) = _state.update { it.copy(color = v) }

    fun onOdometerChange(v: String) =
        _state.update { it.copy(odometer = v, error = null, serverErrors = null) }

    fun onTankCapacityChange(v: String)    = _state.update { it.copy(tankCapacity = v) }
    fun onBatteryCapacityChange(v: String) = _state.update { it.copy(batteryCapacity = v) }

    fun clearError() = _state.update { it.copy(error = null) }

    /** Avança para a próxima etapa validando os campos da etapa atual. */
    fun onNextStep() {
        val s = _state.value
        when (s.currentStep) {
            1 -> {
                val brandInvalid  = s.brand.isBlank()
                val modelInvalid  = s.model.isBlank()
                val mfYearInvalid = s.manufactureYear.length < 4 || s.manufactureYear.toIntOrNull() == null
                val mdYearInvalid = s.modelYear.length < 4 || s.modelYear.toIntOrNull() == null
                if (brandInvalid || modelInvalid || mfYearInvalid || mdYearInvalid) {
                    _state.update {
                        it.copy(
                            brandError           = brandInvalid,
                            modelError           = modelInvalid,
                            manufactureYearError = mfYearInvalid,
                            modelYearError       = mdYearInvalid,
                            stepAttempt          = it.stepAttempt + 1,
                        )
                    }
                    return
                }
                _state.update { it.copy(currentStep = 2) }
            }
            2 -> _state.update { it.copy(currentStep = 3) }
        }
    }

    fun onPreviousStep() {
        _state.update { it.copy(currentStep = (it.currentStep - 1).coerceAtLeast(1)) }
    }

    /**
     * Submete o formulário.
     * @param skipOptional Se true, ignora a validação da placa e envia com campos
     *   opcionais vazios ("Preencher depois").
     */
    fun submit(skipOptional: Boolean = false) {
        val s = _state.value

        if (!skipOptional) {
            val licensePlateInvalid = s.licensePlate.length < 7
            if (licensePlateInvalid) {
                _state.update {
                    it.copy(
                        licensePlateError = true,
                        stepAttempt       = it.stepAttempt + 1,
                    )
                }
                return
            }
        }

        _state.update { it.copy(isSubmitting = true, error = null, serverErrors = null) }

        viewModelScope.launch {
            val result = createVehicle(
                brand              = s.brand.trim(),
                model              = s.model.trim(),
                manufactureYear    = s.manufactureYear.toInt(),
                modelYear          = s.modelYear.toInt(),
                licensePlate       = s.licensePlate,
                color              = s.color.trim().takeIf { it.isNotBlank() },
                type               = s.vehicleType,
                energyType         = s.energyType,
                fuelType           = if (s.showFuelType) s.fuelType else null,
                odometerKm         = s.odometer.toIntOrNull() ?: 0,
                tankCapacityL      = if (s.showTankCapacity) {
                    s.tankCapacity.replace(",", ".").toDoubleOrNull()
                } else null,
                batteryCapacityKwh = if (s.showBatteryCapacity) {
                    s.batteryCapacity.replace(",", ".").toDoubleOrNull()
                } else null,
            )
            when (result) {
                is AppResult.Success -> {
                    _state.update { it.copy(isSubmitting = false) }
                    _effects.send(AddVehicleEffect.NavigateBack)
                }
                is AppResult.Failure -> {
                    val apiErr      = result.error as? AppError.Api
                    val fieldErrors = apiErr?.takeIf { it.code == "VALIDATION_FAILED" }?.fieldErrors
                    if (!fieldErrors.isNullOrEmpty()) {
                        _state.update { it.copy(isSubmitting = false, serverErrors = fieldErrors) }
                    } else {
                        _state.update { it.copy(isSubmitting = false, error = result.error) }
                    }
                }
            }
        }
    }
}
