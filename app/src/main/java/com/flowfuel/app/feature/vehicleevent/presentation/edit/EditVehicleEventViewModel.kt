package com.flowfuel.app.feature.vehicleevent.presentation.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.UpdateVehicleEventRequest
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventByIdUseCase
import com.flowfuel.app.feature.vehicleevent.domain.usecase.UpdateVehicleEventUseCase
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
class EditVehicleEventViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getEventById: GetVehicleEventByIdUseCase,
    private val updateEvent: UpdateVehicleEventUseCase,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val eventId: Int = checkNotNull(savedStateHandle["eventId"])

    private val _state = MutableStateFlow(EditVehicleEventUiState())
    val state: StateFlow<EditVehicleEventUiState> = _state.asStateFlow()

    private val _effects = Channel<EditVehicleEventEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    // Snapshot of values at load time — used to compute isDirty
    private var original: VehicleEvent? = null

    init { load() }

    fun load() {
        _state.update { it.copy(screenState = EditVehicleEventScreenState.Loading) }
        viewModelScope.launch { fetch() }
    }

    private suspend fun fetch() {
        when (val result = getEventById(eventId)) {
            is AppResult.Success -> {
                val event = result.value
                original = event
                _state.update {
                    it.copy(screenState = event.toEditingState())
                }
            }
            is AppResult.Failure -> {
                val error = result.error
                when {
                    error == AppError.Unauthorized -> {
                        sessionStore.clear()
                        _effects.send(EditVehicleEventEffect.NavigateToLogin)
                    }
                    error is AppError.Api && (error.code == "RESOURCE_NOT_FOUND" || error.code == "HTTP_404") -> {
                        _state.update { it.copy(screenState = EditVehicleEventScreenState.Error(error)) }
                    }
                    else -> {
                        _state.update { it.copy(screenState = EditVehicleEventScreenState.Error(error)) }
                    }
                }
            }
        }
    }

    fun onCategoryChange(v: EventCategory) = updateEditing { it.copy(category = v).recalcDirty() }
    fun onTitleChange(v: String) = updateEditing { it.copy(title = v, titleError = null).recalcDirty() }
    fun onDescriptionChange(v: String) = updateEditing { it.copy(description = v).recalcDirty() }
    fun onAmountChange(v: String) = updateEditing { it.copy(amount = v, amountError = null).recalcDirty() }
    fun onEventDateChange(v: String) = updateEditing { it.copy(eventDate = v, eventDateError = null).recalcDirty() }
    fun onOdometerKmChange(v: String) = updateEditing { it.copy(odometerKm = v, odometerError = null).recalcDirty() }
    fun onNotesChange(v: String) = updateEditing { it.copy(notes = v).recalcDirty() }

    fun clearFormError() = updateEditing { it.copy(formError = null) }

    fun onBackPressed() {
        val editing = currentEditing() ?: return
        if (editing.isDirty) {
            updateEditing { it.copy(showDiscardDialog = true) }
        } else {
            viewModelScope.launch { _effects.send(EditVehicleEventEffect.NavigateBack) }
        }
    }

    fun onDiscardConfirm() {
        updateEditing { it.copy(showDiscardDialog = false) }
        viewModelScope.launch { _effects.send(EditVehicleEventEffect.NavigateBack) }
    }

    fun onDiscardDismiss() = updateEditing { it.copy(showDiscardDialog = false) }

    fun submit() {
        val editing = currentEditing() ?: return
        if (editing.isSubmitting) return

        val titleError = when {
            editing.title.isBlank() -> "Título obrigatório"
            editing.title.trim().length < 2 -> "Mínimo 2 caracteres"
            editing.title.trim().length > 100 -> "Máximo 100 caracteres"
            else -> null
        }
        val eventDateError = if (editing.eventDate.isBlank()) "Data obrigatória" else null
        val odometerError = editing.odometerKm.takeIf { it.isNotBlank() }?.toIntOrNull()
            ?.let { if (it <= 0) "Deve ser maior que zero" else null }
        val amountError = editing.amount.takeIf { it.isNotBlank() }?.toLongOrNull()?.let { cents ->
            if (cents <= 0L) "Valor deve ser maior que zero" else null
        } ?: if (editing.amount.isBlank()) "Valor obrigatório" else null

        if (titleError != null || eventDateError != null || odometerError != null || amountError != null) {
            updateEditing {
                it.copy(
                    titleError = titleError,
                    eventDateError = eventDateError,
                    odometerError = odometerError,
                    amountError = amountError,
                )
            }
            return
        }

        updateEditing { it.copy(isSubmitting = true, formError = null) }

        viewModelScope.launch {
            val amountDouble: Double? = editing.amount.takeIf { it.isNotBlank() }
                ?.toLongOrNull()
                ?.let { it.toDouble() / 100.0 }
                ?.takeIf { it > 0.0 }

            val request = UpdateVehicleEventRequest(
                category = editing.category,
                title = editing.title.trim(),
                description = editing.description.trim().takeIf { it.isNotBlank() },
                amount = amountDouble,
                eventDate = editing.eventDate,
                odometerKm = editing.odometerKm.toIntOrNull()?.takeIf { it > 0 },
                notes = editing.notes.trim().takeIf { it.isNotBlank() },
            )

            when (val result = updateEvent(eventId, request)) {
                is AppResult.Success -> {
                    _effects.send(EditVehicleEventEffect.ShowSnackbar("Evento atualizado"))
                    _effects.send(EditVehicleEventEffect.NavigateBack)
                }
                is AppResult.Failure -> {
                    val error = result.error
                    if (error == AppError.Unauthorized) {
                        sessionStore.clear()
                        _effects.send(EditVehicleEventEffect.NavigateToLogin)
                    } else {
                        val apiErr = error as? AppError.Api
                        val fieldErrors = apiErr?.takeIf { it.code == "VALIDATION_FAILED" }?.fieldErrors
                        if (!fieldErrors.isNullOrEmpty()) {
                            updateEditing { editing ->
                                editing.copy(
                                    isSubmitting = false,
                                    titleError = fieldErrors.firstOrNull { fe -> fe.field == "title" }?.message ?: editing.titleError,
                                    eventDateError = fieldErrors.firstOrNull { fe -> fe.field == "eventDate" }?.message ?: editing.eventDateError,
                                    amountError = fieldErrors.firstOrNull { fe -> fe.field == "amount" }?.message,
                                    odometerError = fieldErrors.firstOrNull { fe -> fe.field == "odometerKm" }?.message,
                                    formError = null,
                                )
                            }
                        } else {
                            updateEditing { it.copy(isSubmitting = false, formError = error) }
                        }
                    }
                }
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private fun currentEditing(): EditVehicleEventScreenState.Editing? =
        _state.value.screenState as? EditVehicleEventScreenState.Editing

    private fun updateEditing(transform: (EditVehicleEventScreenState.Editing) -> EditVehicleEventScreenState.Editing) {
        _state.update { uiState ->
            val editing = uiState.screenState as? EditVehicleEventScreenState.Editing ?: return@update uiState
            uiState.copy(screenState = transform(editing))
        }
    }

    private fun EditVehicleEventScreenState.Editing.recalcDirty(): EditVehicleEventScreenState.Editing {
        val orig = original ?: return this
        val origAmountStr = orig.amount
            ?.let { (it * 100).toLong().toString() }
            ?: ""
        val origOdometerStr = orig.odometerKm?.toString() ?: ""
        return copy(
            isDirty = category != orig.category
                || title.trim() != orig.title
                || description.trim() != (orig.description ?: "")
                || amount != origAmountStr
                || eventDate != orig.eventDate
                || odometerKm != origOdometerStr
                || notes.trim() != (orig.notes ?: ""),
        )
    }

    private fun VehicleEvent.toEditingState(): EditVehicleEventScreenState.Editing {
        val amountStr = amount?.let { (it * 100).toLong().toString() } ?: ""
        val odometerStr = odometerKm?.toString() ?: ""
        return EditVehicleEventScreenState.Editing(
            category = category,
            title = title,
            description = description ?: "",
            amount = amountStr,
            eventDate = eventDate,
            odometerKm = odometerStr,
            notes = notes ?: "",
        )
    }
}
