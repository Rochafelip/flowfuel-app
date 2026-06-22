package com.flowfuel.app.feature.vehicleevent.presentation.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicleevent.domain.usecase.DeleteVehicleEventUseCase
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventByIdUseCase
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
class VehicleEventDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getEventById: GetVehicleEventByIdUseCase,
    private val deleteEvent: DeleteVehicleEventUseCase,
    private val sessionStore: SessionStore,
) : ViewModel() {

    val eventId: Int = checkNotNull(savedStateHandle["eventId"])

    private val _state = MutableStateFlow(VehicleEventDetailsUiState())
    val state: StateFlow<VehicleEventDetailsUiState> = _state.asStateFlow()

    private val _effects = Channel<VehicleEventDetailsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(screenState = VehicleEventDetailsScreenState.Loading, isRefreshing = false) }
        viewModelScope.launch { fetch() }
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch { fetch() }
    }

    fun onEditClick() {
        viewModelScope.launch { _effects.send(VehicleEventDetailsEffect.NavigateToEdit(eventId)) }
    }

    fun onDeleteRequest() {
        _state.update { it.copy(showDeleteDialog = true) }
    }

    fun onDeleteDismiss() {
        if (_state.value.isDeleting) return
        _state.update { it.copy(showDeleteDialog = false) }
    }

    fun confirmDelete() {
        if (_state.value.isDeleting) return
        _state.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            when (val result = deleteEvent(eventId)) {
                is AppResult.Success -> {
                    _state.update { it.copy(isDeleting = false, showDeleteDialog = false) }
                    _effects.send(VehicleEventDetailsEffect.NavigateBack)
                }
                is AppResult.Failure -> {
                    _state.update { it.copy(isDeleting = false, showDeleteDialog = false) }
                    val error = result.error
                    if (error == AppError.Unauthorized) {
                        sessionStore.clear()
                        _effects.send(VehicleEventDetailsEffect.NavigateToLogin)
                    } else {
                        _effects.send(VehicleEventDetailsEffect.ShowSnackbar("Não foi possível excluir o evento. Tente novamente."))
                    }
                }
            }
        }
    }

    private suspend fun fetch() {
        when (val result = getEventById(eventId)) {
            is AppResult.Success -> _state.update {
                it.copy(
                    screenState = VehicleEventDetailsScreenState.Success(result.value),
                    isRefreshing = false,
                )
            }
            is AppResult.Failure -> {
                _state.update { it.copy(isRefreshing = false) }
                val error = result.error
                when {
                    error == AppError.Unauthorized -> {
                        sessionStore.clear()
                        _effects.send(VehicleEventDetailsEffect.NavigateToLogin)
                    }
                    error is AppError.Api && (error.code == "RESOURCE_NOT_FOUND" || error.code == "HTTP_404") -> {
                        _state.update { it.copy(screenState = VehicleEventDetailsScreenState.NotFound) }
                    }
                    else -> {
                        _state.update { it.copy(screenState = VehicleEventDetailsScreenState.Error(error)) }
                    }
                }
            }
        }
    }
}
