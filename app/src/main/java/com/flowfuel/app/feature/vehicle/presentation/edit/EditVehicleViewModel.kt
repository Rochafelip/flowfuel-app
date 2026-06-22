package com.flowfuel.app.feature.vehicle.presentation.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.UpdateVehicleRequest
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehicleByIdUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.UpdateVehicleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditVehicleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getVehicleById: GetVehicleByIdUseCase,
    private val updateVehicle: UpdateVehicleUseCase,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val vehicleId: Int = checkNotNull(savedStateHandle["vehicleId"])

    /** Estado inicial carregado do servidor — usado para calcular isDirty. */
    private var initialState: EditVehicleUiState? = null

    private val _state = MutableStateFlow(EditVehicleUiState())
    val state: StateFlow<EditVehicleUiState> = _state.asStateFlow()

    private val _effects = Channel<EditVehicleEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init { load() }

    // ─── Carregamento ─────────────────────────────────────────────────────────

    fun load() {
        _state.update { it.copy(screenState = EditVehicleScreenState.Loading) }
        viewModelScope.launch {
            when (val result = getVehicleById(vehicleId)) {
                is AppResult.Success -> {
                    val vehicle = result.value
                    val loaded = EditVehicleUiState(
                        screenState      = EditVehicleScreenState.Editing,
                        brand            = vehicle.brand,
                        model            = vehicle.model,
                        manufactureYear  = vehicle.manufactureYear?.toString() ?: "",
                        modelYear        = vehicle.modelYear?.toString() ?: "",
                        licensePlate     = vehicle.licensePlate ?: "",
                        color            = vehicle.color ?: "",
                        vehicleType      = vehicle.type,
                        energyType       = vehicle.energyType,
                        fuelType         = vehicle.fuelType ?: FuelType.Flex,
                        odometer         = vehicle.odometerKm.toString(),
                        tankCapacity     = vehicle.tankCapacityL?.toFormString() ?: "",
                        batteryCapacity  = vehicle.batteryCapacityKwh?.toFormString() ?: "",
                    )
                    initialState = loaded
                    _state.value = loaded
                }
                is AppResult.Failure -> handleLoadError(result.error)
            }
        }
    }

    // ─── Handlers do formulário ───────────────────────────────────────────────

    fun onBrandChange(v: String) =
        updateWithDirtyCheck { it.copy(brand = v, brandError = false, formError = null, serverErrors = null) }

    fun onModelChange(v: String) =
        updateWithDirtyCheck { it.copy(model = v, modelError = false, formError = null, serverErrors = null) }

    fun onManufactureYearChange(v: String) =
        updateWithDirtyCheck {
            it.copy(
                manufactureYear      = v.filter(Char::isDigit).take(4),
                manufactureYearError = false,
                formError            = null,
                serverErrors         = null,
            )
        }

    fun onModelYearChange(v: String) =
        updateWithDirtyCheck {
            it.copy(
                modelYear      = v.filter(Char::isDigit).take(4),
                modelYearError = false,
                formError      = null,
                serverErrors   = null,
            )
        }

    fun onLicensePlateChange(v: String) =
        updateWithDirtyCheck {
            it.copy(licensePlate = v.uppercase().take(7), licensePlateError = false, formError = null, serverErrors = null)
        }

    fun onColorChange(v: String) =
        updateWithDirtyCheck { it.copy(color = v) }

    fun onVehicleTypeChange(v: VehicleType) =
        updateWithDirtyCheck { it.copy(vehicleType = v) }

    fun onEnergyTypeChange(v: EnergyType) =
        updateWithDirtyCheck { it.copy(energyType = v) }

    fun onFuelTypeChange(v: FuelType) =
        updateWithDirtyCheck { it.copy(fuelType = v) }

    fun onOdometerChange(v: String) =
        updateWithDirtyCheck { it.copy(odometer = v, odometerError = false, formError = null, serverErrors = null) }

    fun onTankCapacityChange(v: String) =
        updateWithDirtyCheck { it.copy(tankCapacity = v) }

    fun onBatteryCapacityChange(v: String) =
        updateWithDirtyCheck { it.copy(batteryCapacity = v) }

    fun clearError() = _state.update { it.copy(formError = null) }

    // ─── Navegação / descarte ─────────────────────────────────────────────────

    fun onBackPressed() {
        if (_state.value.isDirty) {
            _state.update { it.copy(showDiscardDialog = true) }
        } else {
            viewModelScope.launch { _effects.send(EditVehicleEffect.NavigateBack) }
        }
    }

    fun onDiscardConfirm() {
        _state.update { it.copy(showDiscardDialog = false) }
        viewModelScope.launch { _effects.send(EditVehicleEffect.NavigateBack) }
    }

    fun onDiscardDismiss() {
        _state.update { it.copy(showDiscardDialog = false) }
    }

    // ─── Submissão ────────────────────────────────────────────────────────────

    fun submit() {
        val s = _state.value
        if (s.screenState !is EditVehicleScreenState.Editing || s.isSubmitting) return

        val brandInvalid            = s.brand.isBlank()
        val modelInvalid            = s.model.isBlank()
        val manufactureYearInvalid  = s.manufactureYear.length < 4 || s.manufactureYear.toIntOrNull() == null
        val modelYearInvalid        = s.modelYear.length < 4 || s.modelYear.toIntOrNull() == null
        val licensePlateInvalid     = s.licensePlate.length < 7
        val odometerInvalid         = s.odometer.isBlank()

        if (brandInvalid || modelInvalid || manufactureYearInvalid || modelYearInvalid
            || licensePlateInvalid || odometerInvalid
        ) {
            _state.update {
                it.copy(
                    brandError           = brandInvalid,
                    modelError           = modelInvalid,
                    manufactureYearError = manufactureYearInvalid,
                    modelYearError       = modelYearInvalid,
                    licensePlateError    = licensePlateInvalid,
                    odometerError        = odometerInvalid,
                    submitAttempt        = it.submitAttempt + 1,
                )
            }
            return
        }

        _state.update { it.copy(isSubmitting = true, formError = null) }

        viewModelScope.launch {
            val request = UpdateVehicleRequest(
                brand            = s.brand.trim(),
                model            = s.model.trim(),
                manufactureYear  = s.manufactureYear.toIntOrNull(),
                modelYear        = s.modelYear.toIntOrNull(),
                licensePlate     = s.licensePlate,
                color            = s.color.trim().takeIf { it.isNotBlank() },
                type             = s.vehicleType,
                energyType       = s.energyType,
                fuelType         = if (s.showFuelType) s.fuelType else null,
                odometerKm       = s.odometer.toIntOrNull() ?: 0,
                tankCapacityL    = if (s.showTankCapacity) {
                    s.tankCapacity.replace(",", ".").toDoubleOrNull()
                } else null,
                batteryCapacityKwh = if (s.showBatteryCapacity) {
                    s.batteryCapacity.replace(",", ".").toDoubleOrNull()
                } else null,
            )

            when (val result = updateVehicle(vehicleId, request)) {
                is AppResult.Success -> {
                    // Mantém isSubmitting=true até a navegação para que o botão fique
                    // desabilitado durante o snackbar de sucesso na tela.
                    _effects.send(EditVehicleEffect.NavigateBackAfterSave)
                }
                is AppResult.Failure -> {
                    val error = result.error
                    if (error == AppError.Unauthorized) {
                        sessionStore.clear()
                        _effects.send(EditVehicleEffect.NavigateToLogin)
                    } else {
                        val apiErr = error as? AppError.Api
                        val fieldErrors = apiErr?.takeIf { it.code == "VALIDATION_FAILED" }?.fieldErrors
                        if (!fieldErrors.isNullOrEmpty()) {
                            _state.update { it.copy(isSubmitting = false, serverErrors = fieldErrors) }
                        } else {
                            _state.update { it.copy(isSubmitting = false, formError = error) }
                        }
                    }
                }
            }
        }
    }

    // ─── Helpers privados ─────────────────────────────────────────────────────

    private fun updateWithDirtyCheck(transform: (EditVehicleUiState) -> EditVehicleUiState) {
        _state.update { current ->
            val updated = transform(current)
            val init = initialState ?: return@update updated
            val dirty = updated.brand != init.brand
                || updated.model != init.model
                || updated.manufactureYear != init.manufactureYear
                || updated.modelYear != init.modelYear
                || updated.licensePlate != init.licensePlate
                || updated.color != init.color
                || updated.vehicleType != init.vehicleType
                || updated.energyType != init.energyType
                || updated.fuelType != init.fuelType
                || updated.odometer != init.odometer
                || updated.tankCapacity != init.tankCapacity
                || updated.batteryCapacity != init.batteryCapacity
            updated.copy(isDirty = dirty)
        }
    }

    private suspend fun handleLoadError(error: AppError) {
        if (error == AppError.Unauthorized) {
            sessionStore.clear()
            _effects.send(EditVehicleEffect.NavigateToLogin)
        } else {
            _state.update { it.copy(screenState = EditVehicleScreenState.Error(error)) }
        }
    }

    private companion object {
        /** Formata um Double sem casas decimais desnecessárias: 50.0 → "50", 45.5 → "45.5". */
        fun Double.toFormString(): String =
            if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()
    }
}
