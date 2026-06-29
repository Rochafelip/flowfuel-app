package com.flowfuel.app.feature.export.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.export.domain.ExportFormat
import com.flowfuel.app.feature.export.domain.ExportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ExportUiState(
    val isLoading: Boolean = false,
    val selectedFormat: ExportFormat = ExportFormat.CSV,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val selectedEventType: String? = null,
    val error: AppError? = null,
) {
    val hasDateRange: Boolean get() = startDate != null && endDate != null
}

sealed interface ExportEffect {
    data class FileReady(val uri: Uri) : ExportEffect
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val exportRepository: ExportRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    private val _effects = Channel<ExportEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onFormatChange(format: ExportFormat) =
        _state.update { it.copy(selectedFormat = format, error = null) }

    fun onDateRangeChange(start: LocalDate?, end: LocalDate?) =
        _state.update { it.copy(startDate = start, endDate = end, error = null) }

    fun onEventTypeChange(type: String?) =
        _state.update { it.copy(selectedEventType = type, error = null) }

    fun clearError() = _state.update { it.copy(error = null) }

    fun exportRefuels() {
        val s = _state.value
        if (s.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val vehicleId = sessionStore.activeVehicleIdFlow.first() ?: run {
                _state.update { it.copy(isLoading = false, error = AppError.Unknown()) }
                return@launch
            }
            val result = exportRepository.exportRefuels(
                vehicleId = vehicleId,
                format = s.selectedFormat,
                startDate = s.startDate?.toString(),
                endDate = s.endDate?.toString(),
            )
            handleResult(result)
        }
    }

    fun exportEvents() {
        val s = _state.value
        if (s.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val vehicleId = sessionStore.activeVehicleIdFlow.first() ?: run {
                _state.update { it.copy(isLoading = false, error = AppError.Unknown()) }
                return@launch
            }
            val result = exportRepository.exportEvents(
                vehicleId = vehicleId,
                format = s.selectedFormat,
                type = s.selectedEventType,
                startDate = s.startDate?.toString(),
                endDate = s.endDate?.toString(),
            )
            handleResult(result)
        }
    }

    private suspend fun handleResult(result: AppResult<Uri>) {
        when (result) {
            is AppResult.Success -> {
                _state.update { it.copy(isLoading = false) }
                _effects.send(ExportEffect.FileReady(result.value))
            }
            is AppResult.Failure -> {
                _state.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }
}
