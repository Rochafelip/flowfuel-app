package com.flowfuel.app.feature.vehicle.presentation.add

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.FieldError
import com.flowfuel.app.core.media.ImagePickerHelper
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FipeOption
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import com.flowfuel.app.feature.vehicle.domain.usecase.CreateVehicleUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.GetFipeBrandsUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.GetFipeModelsUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.GetFipeYearsUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.UploadVehiclePhotoUseCase
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
    // — Etapa 1: Busca FIPE
    val useFipeSearch: Boolean = true,
    val fipeBrands: List<FipeOption> = emptyList(),
    val fipeModels: List<FipeOption> = emptyList(),
    val fipeYears: List<FipeOption> = emptyList(),
    val selectedFipeBrandCode: String? = null,
    val selectedFipeModelCode: String? = null,
    val fipeBrandsLoading: Boolean = false,
    val fipeModelsLoading: Boolean = false,
    val fipeYearsLoading: Boolean = false,
    val fipeBrandsError: Boolean = false,
    val fipeModelsError: Boolean = false,
    val fipeYearsError: Boolean = false,
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
    // — Etapa 4: Foto
    val photoUri: Uri? = null,
    val photoUploadError: AppError? = null,
    /** Preenchido após a criação bem-sucedida do veículo; usado para retry do upload de foto. */
    val createdVehicleId: Int? = null,
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

    /**
     * Placa é validada (ou pulada via "Preencher depois") ao sair do Step 3;
     * a foto é sempre obrigatória para concluir o cadastro (Step 4).
     */
    val canSubmit: Boolean
        get() = photoUri != null && !isSubmitting
}

sealed interface AddVehicleEffect {
    data object NavigateBack : AddVehicleEffect
}

@HiltViewModel
class AddVehicleViewModel @Inject constructor(
    private val createVehicle: CreateVehicleUseCase,
    private val uploadVehiclePhoto: UploadVehiclePhotoUseCase,
    private val imagePickerHelper: ImagePickerHelper,
    private val getFipeBrands: GetFipeBrandsUseCase,
    private val getFipeModels: GetFipeModelsUseCase,
    private val getFipeYears: GetFipeYearsUseCase,
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

    // — Etapa 1: Busca FIPE
    fun loadFipeBrandsIfNeeded() {
        val s = _state.value
        if (s.fipeBrands.isNotEmpty() || s.fipeBrandsLoading) return
        loadFipeBrands()
    }

    fun onRetryLoadFipeBrands() = loadFipeBrands()
    fun onRetryLoadFipeModels() = loadFipeModels()
    fun onRetryLoadFipeYears() = loadFipeYears()

    fun onFipeBrandSelected(option: FipeOption) {
        _state.update {
            it.copy(
                brand = option.name,
                brandError = false,
                selectedFipeBrandCode = option.code,
                model = "",
                modelError = false,
                selectedFipeModelCode = null,
                fipeModels = emptyList(),
                fipeModelsError = false,
                modelYear = "",
                modelYearError = false,
                fipeYears = emptyList(),
                fipeYearsError = false,
                error = null,
                serverErrors = null,
            )
        }
        loadFipeModels()
    }

    fun onFipeModelSelected(option: FipeOption) {
        _state.update {
            it.copy(
                model = option.name,
                modelError = false,
                selectedFipeModelCode = option.code,
                modelYear = "",
                modelYearError = false,
                fipeYears = emptyList(),
                fipeYearsError = false,
                error = null,
                serverErrors = null,
            )
        }
        loadFipeYears()
    }

    fun onFipeYearSelected(option: FipeOption) {
        val year = parseFipeYear(option) ?: return
        _state.update {
            it.copy(
                modelYear = year.toString(),
                modelYearError = false,
                manufactureYear = year.toString(),
                manufactureYearError = false,
                error = null,
                serverErrors = null,
            )
        }
    }

    fun onToggleManualEntry() = _state.update { it.copy(useFipeSearch = !it.useFipeSearch) }

    private fun loadFipeBrands() {
        _state.update { it.copy(fipeBrandsLoading = true, fipeBrandsError = false) }
        viewModelScope.launch {
            when (val result = getFipeBrands(_state.value.vehicleType)) {
                is AppResult.Success -> _state.update { it.copy(fipeBrands = result.value, fipeBrandsLoading = false) }
                is AppResult.Failure -> _state.update { it.copy(fipeBrandsLoading = false, fipeBrandsError = true) }
            }
        }
    }

    private fun loadFipeModels() {
        val brandCode = _state.value.selectedFipeBrandCode ?: return
        _state.update { it.copy(fipeModelsLoading = true, fipeModelsError = false) }
        viewModelScope.launch {
            when (val result = getFipeModels(_state.value.vehicleType, brandCode)) {
                is AppResult.Success -> _state.update { it.copy(fipeModels = result.value, fipeModelsLoading = false) }
                is AppResult.Failure -> _state.update { it.copy(fipeModelsLoading = false, fipeModelsError = true) }
            }
        }
    }

    private fun loadFipeYears() {
        val s = _state.value
        val brandCode = s.selectedFipeBrandCode ?: return
        val modelCode = s.selectedFipeModelCode ?: return
        _state.update { it.copy(fipeYearsLoading = true, fipeYearsError = false) }
        viewModelScope.launch {
            when (val result = getFipeYears(s.vehicleType, brandCode, modelCode)) {
                is AppResult.Success -> _state.update { it.copy(fipeYears = result.value, fipeYearsLoading = false) }
                is AppResult.Failure -> _state.update { it.copy(fipeYearsLoading = false, fipeYearsError = true) }
            }
        }
    }

    // — Etapa 2
    fun onVehicleTypeChange(v: VehicleType) {
        if (v == _state.value.vehicleType) return
        _state.update {
            it.copy(
                vehicleType = v,
                brand = "", brandError = false, selectedFipeBrandCode = null, fipeBrands = emptyList(), fipeBrandsError = false,
                model = "", modelError = false, selectedFipeModelCode = null, fipeModels = emptyList(), fipeModelsError = false,
                manufactureYear = "", manufactureYearError = false,
                modelYear = "", modelYearError = false, fipeYears = emptyList(), fipeYearsError = false,
            )
        }
        loadFipeBrands()
    }
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

    // — Etapa 4
    fun onPhotoPicked(uri: Uri) =
        _state.update { it.copy(photoUri = uri, photoUploadError = null) }

    /** Usa uma foto padrão gerada localmente em vez de exigir que o usuário escolha uma — ver [ImagePickerHelper.createTemplatePhoto]. */
    fun onSkipPhoto() {
        val templateUri = imagePickerHelper.createTemplatePhoto(_state.value.vehicleType)
        _state.update { it.copy(photoUri = templateUri, photoUploadError = null) }
    }

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
            3 -> {
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
                _state.update { it.copy(currentStep = 4) }
            }
        }
    }

    /** Avança da Etapa 3 para a Etapa 4 sem validar a placa ("Preencher depois"). */
    fun onSkipToPhotoStep() {
        _state.update { it.copy(currentStep = 4, licensePlateError = false) }
    }

    fun onPreviousStep() {
        _state.update { it.copy(currentStep = (it.currentStep - 1).coerceAtLeast(1)) }
    }

    /**
     * Cria o veículo (se ainda não criado) e envia a foto.
     * Se uma tentativa anterior já criou o veículo mas o upload da foto falhou
     * ([AddVehicleUiState.createdVehicleId] não nulo), reenvia só a foto, sem
     * recriar o veículo.
     */
    fun submit() {
        val s = _state.value
        val photoUri = s.photoUri
        if (photoUri == null || s.isSubmitting) return

        _state.update { it.copy(isSubmitting = true, error = null, serverErrors = null, photoUploadError = null) }

        viewModelScope.launch {
            val vehicleId: Int = s.createdVehicleId ?: run {
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
                    is AppResult.Success -> result.value.id
                    is AppResult.Failure -> {
                        val apiErr      = result.error as? AppError.Api
                        val fieldErrors = apiErr?.takeIf { it.code == "VALIDATION_FAILED" }?.fieldErrors
                        if (!fieldErrors.isNullOrEmpty()) {
                            _state.update { it.copy(isSubmitting = false, serverErrors = fieldErrors) }
                        } else {
                            _state.update { it.copy(isSubmitting = false, error = result.error) }
                        }
                        return@launch
                    }
                }
            }

            _state.update { it.copy(createdVehicleId = vehicleId) }

            // Backend endpoint ainda não existe (404 esperado até o deploy) — ver docs/superpowers/specs/2026-07-03-vehicle-photo-required-design.md
            when (val uploadResult = uploadVehiclePhoto(vehicleId, photoUri)) {
                is AppResult.Success -> {
                    _state.update { it.copy(isSubmitting = false) }
                    _effects.send(AddVehicleEffect.NavigateBack)
                }
                is AppResult.Failure -> {
                    _state.update { it.copy(isSubmitting = false, photoUploadError = uploadResult.error) }
                }
            }
        }
    }
}

/** Extrai o ano de um item de "Ano do modelo" da FIPE (código "2016-1" ou nome "2016 Gasolina"). */
private fun parseFipeYear(option: FipeOption): Int? =
    option.code.substringBefore("-").toIntOrNull()
        ?: option.name.take(4).toIntOrNull()
