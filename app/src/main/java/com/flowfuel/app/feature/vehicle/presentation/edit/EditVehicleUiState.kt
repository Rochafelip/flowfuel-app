package com.flowfuel.app.feature.vehicle.presentation.edit

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.FieldError
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType

// ─── Estado da tela ───────────────────────────────────────────────────────────

sealed interface EditVehicleScreenState {
    data object Loading : EditVehicleScreenState
    data object Editing : EditVehicleScreenState
    data class Error(val error: AppError) : EditVehicleScreenState
}

// ─── Estado global do formulário ──────────────────────────────────────────────

data class EditVehicleUiState(
    val screenState: EditVehicleScreenState = EditVehicleScreenState.Loading,
    // — Seção 1: Informações principais
    val brand: String = "",
    val model: String = "",
    val manufactureYear: String = "",
    val modelYear: String = "",
    val licensePlate: String = "",
    val color: String = "",
    // — Seção 2: Tipo do veículo
    val vehicleType: VehicleType = VehicleType.Car,
    // — Seção 3: Tipo de energia
    val energyType: EnergyType = EnergyType.Combustion,
    // — Seção 4: Subtipo de combustível
    val fuelType: FuelType = FuelType.Flex,
    // — Seção 5: Dados técnicos
    val odometer: String = "",
    val tankCapacity: String = "",
    val batteryCapacity: String = "",
    // — Erros de validação por campo
    val brandError: Boolean = false,
    val modelError: Boolean = false,
    val manufactureYearError: Boolean = false,
    val modelYearError: Boolean = false,
    val licensePlateError: Boolean = false,
    val odometerError: Boolean = false,
    // — Estado global
    val isSubmitting: Boolean = false,
    val formError: AppError? = null,
    val serverErrors: List<FieldError>? = null,
    val submitAttempt: Int = 0,
    val isDirty: Boolean = false,
    val showDiscardDialog: Boolean = false,
) {
    val showFuelType: Boolean
        get() = energyType == EnergyType.Combustion || energyType == EnergyType.Hybrid

    val showTankCapacity: Boolean
        get() = energyType == EnergyType.Combustion || energyType == EnergyType.Hybrid

    val showBatteryCapacity: Boolean
        get() = energyType == EnergyType.Electric || energyType == EnergyType.Hybrid

    val canSubmit: Boolean
        get() = brand.isNotBlank() && model.isNotBlank()
            && manufactureYear.isNotBlank() && modelYear.isNotBlank()
            && licensePlate.isNotBlank() && odometer.isNotBlank()
            && !isSubmitting
            && screenState is EditVehicleScreenState.Editing
}

// ─── Efeitos pontuais ─────────────────────────────────────────────────────────

sealed interface EditVehicleEffect {
    /** Voltar sem sinalizar atualização (descarte ou back sem alterações). */
    data object NavigateBack : EditVehicleEffect
    /** Voltar após salvar com sucesso — sinaliza a lista para recarregar. */
    data object NavigateBackAfterSave : EditVehicleEffect
    data object NavigateToLogin : EditVehicleEffect
}
