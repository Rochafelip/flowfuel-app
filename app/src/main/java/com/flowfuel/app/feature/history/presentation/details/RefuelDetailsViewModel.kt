package com.flowfuel.app.feature.history.presentation.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.usecase.DeleteRefuelUseCase
import com.flowfuel.app.feature.history.domain.usecase.GetRefuelDetailsUseCase
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
class RefuelDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getRefuelDetails: GetRefuelDetailsUseCase,
    private val deleteRefuel: DeleteRefuelUseCase,
) : ViewModel() {

    private val refuelId: Int = checkNotNull(savedStateHandle["refuelId"])

    private val _state = MutableStateFlow(RefuelDetailsUiState())
    val state: StateFlow<RefuelDetailsUiState> = _state.asStateFlow()

    private val _effects = Channel<RefuelDetailsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(screenState = RefuelDetailsScreenState.Loading) }
        viewModelScope.launch {
            when (val result = getRefuelDetails(refuelId)) {
                is AppResult.Success -> _state.update {
                    it.copy(screenState = RefuelDetailsScreenState.Success(result.value))
                }
                is AppResult.Failure -> _state.update {
                    it.copy(screenState = RefuelDetailsScreenState.Error(result.error))
                }
            }
        }
    }

    fun requestDelete() {
        _state.update { it.copy(showDeleteConfirm = true, deleteError = null) }
    }

    fun cancelDelete() {
        _state.update { it.copy(showDeleteConfirm = false, deleteError = null) }
    }

    fun confirmDelete() {
        if (_state.value.isDeleting) return
        _state.update { it.copy(showDeleteConfirm = false, isDeleting = true, deleteError = null) }
        viewModelScope.launch {
            when (val result = deleteRefuel(refuelId)) {
                is AppResult.Success -> _effects.send(RefuelDetailsEffect.Deleted)
                is AppResult.Failure -> _state.update {
                    it.copy(isDeleting = false, deleteError = result.error)
                }
            }
        }
    }
}
