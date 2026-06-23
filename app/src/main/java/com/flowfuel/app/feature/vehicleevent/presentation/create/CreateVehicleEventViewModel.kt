package com.flowfuel.app.feature.vehicleevent.presentation.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicleevent.domain.model.CreateVehicleEventRequest
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.usecase.CreateVehicleEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CreateVehicleEventViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val createVehicleEvent: CreateVehicleEventUseCase,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val vehicleId: Int = checkNotNull(savedStateHandle["vehicleId"])

    private val _state = MutableStateFlow(
        CreateVehicleEventUiState(eventDate = LocalDate.now().toString()),
    )
    val state: StateFlow<CreateVehicleEventUiState> = _state.asStateFlow()

    private val _effects = Channel<CreateVehicleEventEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onCategoryChange(v: EventCategory) =
        _state.update { it.copy(category = v).recalcDirty() }

    fun onTitleChange(v: String) =
        _state.update { it.copy(title = v, titleError = null).recalcDirty() }

    fun onDescriptionChange(v: String) =
        _state.update { it.copy(description = v).recalcDirty() }

    fun onAmountChange(v: String) =
        _state.update { it.copy(amount = v, amountError = null).recalcDirty() }

    fun onEventDateChange(v: String) =
        _state.update { it.copy(eventDate = v, eventDateError = null).recalcDirty() }

    fun onOdometerKmChange(v: String) =
        _state.update { it.copy(odometerKm = v, odometerError = null).recalcDirty() }

    fun onNotesChange(v: String) =
        _state.update { it.copy(notes = v).recalcDirty() }

    fun clearError() = _state.update { it.copy(formError = null) }

    fun onBackPressed() {
        if (_state.value.isDirty) {
            _state.update { it.copy(showDiscardDialog = true) }
        } else {
            viewModelScope.launch { _effects.send(CreateVehicleEventEffect.NavigateBack) }
        }
    }

    fun onDiscardConfirm() {
        _state.update { it.copy(showDiscardDialog = false) }
        viewModelScope.launch { _effects.send(CreateVehicleEventEffect.NavigateBack) }
    }

    fun onDiscardDismiss() {
        _state.update { it.copy(showDiscardDialog = false) }
    }

    fun submit() {
        val s = _state.value
        if (s.isSubmitting) return

        val titleError = when {
            s.title.isBlank() -> "Título obrigatório"
            s.title.trim().length < 2 -> "Mínimo 2 caracteres"
            s.title.trim().length > 100 -> "Máximo 100 caracteres"
            else -> null
        }
        val eventDateError = if (s.eventDate.isBlank()) "Data obrigatória" else null
        val odometerError = s.odometerKm.takeIf { it.isNotBlank() }?.toIntOrNull()
            ?.let { if (it <= 0) "Deve ser maior que zero" else null }
        // amount é obrigatório no backend (VehicleEventRequestDTO.amount, min 0.01) — sem
        // essa validação local, o erro só aparecia como a mensagem crua do Bean Validation
        // ("must not be null") vinda da API, sem tradução.
        val amountError = s.amount.takeIf { it.isNotBlank() }?.toLongOrNull()?.let { cents ->
            if (cents <= 0L) "Valor deve ser maior que zero" else null
        } ?: if (s.amount.isBlank()) "Valor obrigatório" else null

        if (titleError != null || eventDateError != null || odometerError != null || amountError != null) {
            _state.update {
                it.copy(
                    titleError = titleError,
                    eventDateError = eventDateError,
                    odometerError = odometerError,
                    amountError = amountError,
                )
            }
            return
        }

        _state.update { it.copy(isSubmitting = true, formError = null) }

        viewModelScope.launch {
            // Digits-only string → Double: "15000" = R$ 150,00
            val amountDouble: Double? = s.amount.takeIf { it.isNotBlank() }
                ?.toLongOrNull()
                ?.let { it.toDouble() / 100.0 }
                ?.takeIf { it > 0.0 }

            val request = CreateVehicleEventRequest(
                vehicleId = vehicleId,
                category = s.category,
                title = s.title.trim(),
                description = s.description.trim().takeIf { it.isNotBlank() },
                amount = amountDouble,
                eventDate = s.eventDate,
                odometerKm = s.odometerKm.toIntOrNull()?.takeIf { it > 0 },
                notes = s.notes.trim().takeIf { it.isNotBlank() },
            )

            when (val result = createVehicleEvent(request)) {
                is AppResult.Success -> {
                    _effects.send(CreateVehicleEventEffect.ShowSnackbar("Evento registrado"))
                    _effects.send(CreateVehicleEventEffect.NavigateBack)
                }
                is AppResult.Failure -> {
                    val error = result.error
                    if (error == AppError.Unauthorized) {
                        sessionStore.clear()
                        _effects.send(CreateVehicleEventEffect.NavigateToLogin)
                    } else {
                        val apiErr = error as? AppError.Api
                        val fieldErrors = apiErr?.takeIf { it.code == "VALIDATION_FAILED" }?.fieldErrors
                        if (!fieldErrors.isNullOrEmpty()) {
                            _state.update { state ->
                                state.copy(
                                    isSubmitting = false,
                                    titleError = fieldErrors.firstOrNull { fe -> fe.field == "title" }?.message ?: state.titleError,
                                    eventDateError = fieldErrors.firstOrNull { fe -> fe.field == "eventDate" }?.message ?: state.eventDateError,
                                    amountError = fieldErrors.firstOrNull { fe -> fe.field == "amount" }?.message,
                                    odometerError = fieldErrors.firstOrNull { fe -> fe.field == "odometerKm" }?.message,
                                    formError = null,
                                )
                            }
                        } else {
                            _state.update { it.copy(isSubmitting = false, formError = error) }
                        }
                    }
                }
            }
        }
    }

    private fun CreateVehicleEventUiState.recalcDirty() = copy(
        isDirty = category != EventCategory.OTHER
            || title.isNotBlank()
            || description.isNotBlank()
            || amount.isNotBlank()
            || odometerKm.isNotBlank()
            || notes.isNotBlank(),
    )
}
