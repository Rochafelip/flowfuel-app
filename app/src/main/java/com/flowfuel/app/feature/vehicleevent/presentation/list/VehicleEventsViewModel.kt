package com.flowfuel.app.feature.vehicleevent.presentation.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.pagination.PaginationState
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.EventDateFilter
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.model.toDateRange
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehicleByIdUseCase
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class VehicleEventsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getEventsPage: GetVehicleEventsPageUseCase,
    private val getVehicleById: GetVehicleByIdUseCase,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private var vehicleId: Int = savedStateHandle["vehicleId"] ?: -1

    private val _state = MutableStateFlow(VehicleEventsUiState())
    val state: StateFlow<VehicleEventsUiState> = _state.asStateFlow()

    private val _effects = Channel<VehicleEventsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var accumulatedEvents: MutableList<VehicleEvent> = mutableListOf()
    private var loadJob: Job? = null
    private var isLoadingMore = false

    init {
        if (vehicleId != -1) {
            viewModelScope.launch { fetchVehicleLabel(vehicleId) }
            load()
        } else {
            viewModelScope.launch {
                sessionStore.activeVehicleIdFlow.collectLatest { id ->
                    if (id != null && id != vehicleId) {
                        vehicleId = id
                        fetchVehicleLabel(id)
                        load()
                    }
                }
            }
        }
    }

    private suspend fun fetchVehicleLabel(id: Int) {
        when (val result = getVehicleById(id)) {
            is AppResult.Success -> {
                val v = result.value
                val year = v.modelYear ?: v.manufactureYear
                val label = buildString {
                    append(v.brand)
                    append(" ")
                    append(v.model)
                    if (year != null) append(" $year")
                }
                _state.update { it.copy(activeVehicleLabel = label) }
            }
            is AppResult.Failure -> { /* label permanece null — não crítico */ }
        }
    }

    fun load() {
        loadJob?.cancel()
        accumulatedEvents = mutableListOf()
        isLoadingMore = false
        _state.update {
            it.copy(
                screenState = VehicleEventsScreenState.Loading,
                isRefreshing = false,
                pagination = PaginationState(),
            )
        }
        loadJob = viewModelScope.launch { fetchPage(0) }
    }

    fun refresh() {
        loadJob?.cancel()
        accumulatedEvents = mutableListOf()
        isLoadingMore = false
        _state.update { it.copy(isRefreshing = true, pagination = PaginationState()) }
        loadJob = viewModelScope.launch { fetchPage(0, isRefresh = true) }
    }

    fun loadNextPage() {
        val pagination = _state.value.pagination
        if (isLoadingMore || !pagination.hasMore) return
        isLoadingMore = true
        _state.update { it.copy(pagination = it.pagination.copy(isLoadingMore = true, pageError = null)) }
        loadJob = viewModelScope.launch {
            val nextPage = pagination.currentPage + 1
            val (dateFrom, dateTo) = _state.value.selectedDateFilter.toDateRange()
            when (val result = getEventsPage(vehicleId, nextPage, _state.value.selectedCategory, dateFrom, dateTo)) {
                is AppResult.Success -> {
                    val existingIds = accumulatedEvents.map { it.id }.toSet()
                    val deduped = result.value.items.filter { it.id !in existingIds }
                    accumulatedEvents.addAll(deduped)
                    _state.update { s ->
                        s.copy(
                            screenState = VehicleEventsScreenState.Success(accumulatedEvents.toList()),
                            pagination = s.pagination.copy(
                                currentPage = nextPage,
                                isLoadingMore = false,
                                hasMore = result.value.hasMore,
                            ),
                        )
                    }
                }
                is AppResult.Failure -> {
                    _state.update {
                        it.copy(pagination = it.pagination.copy(isLoadingMore = false, pageError = result.error))
                    }
                }
            }
            isLoadingMore = false
        }
    }

    fun onCategorySelected(category: EventCategory?) {
        if (_state.value.selectedCategory == category) return
        loadJob?.cancel()
        accumulatedEvents = mutableListOf()
        isLoadingMore = false
        _state.update {
            it.copy(
                selectedCategory = category,
                pagination = PaginationState(),
                screenState = VehicleEventsScreenState.Loading,
            )
        }
        loadJob = viewModelScope.launch { fetchPage(0) }
    }

    fun onDateFilterSelected(filter: EventDateFilter) {
        if (_state.value.selectedDateFilter == filter) return
        loadJob?.cancel()
        accumulatedEvents = mutableListOf()
        isLoadingMore = false
        _state.update {
            it.copy(
                selectedDateFilter = filter,
                pagination = PaginationState(),
                screenState = VehicleEventsScreenState.Loading,
            )
        }
        loadJob = viewModelScope.launch { fetchPage(0) }
    }

    fun onEventClick(eventId: Int) {
        viewModelScope.launch { _effects.send(VehicleEventsEffect.NavigateToDetails(eventId)) }
    }

    fun onCreateClick() {
        viewModelScope.launch { _effects.send(VehicleEventsEffect.NavigateToCreate(vehicleId)) }
    }

    fun onRetryPage() {
        _state.update { it.copy(pagination = it.pagination.copy(pageError = null)) }
        isLoadingMore = false
        loadNextPage()
    }

    fun removeEvent(eventId: Int) {
        accumulatedEvents.removeIf { it.id == eventId }
        val updated = accumulatedEvents.toList()
        _state.update {
            it.copy(
                screenState = if (updated.isEmpty()) VehicleEventsScreenState.Empty
                              else VehicleEventsScreenState.Success(updated),
            )
        }
    }

    private suspend fun fetchPage(page: Int, isRefresh: Boolean = false) {
        val category = _state.value.selectedCategory
        val (dateFrom, dateTo) = _state.value.selectedDateFilter.toDateRange()
        when (val result = getEventsPage(vehicleId, page, category, dateFrom, dateTo)) {
            is AppResult.Success -> {
                val items = result.value.items
                if (items.isEmpty() && page == 0) {
                    _state.update {
                        it.copy(
                            screenState = VehicleEventsScreenState.Empty,
                            isRefreshing = false,
                            pagination = PaginationState(currentPage = 0, hasMore = false),
                        )
                    }
                } else {
                    accumulatedEvents.addAll(items)
                    _state.update {
                        it.copy(
                            screenState = VehicleEventsScreenState.Success(accumulatedEvents.toList()),
                            isRefreshing = false,
                            pagination = PaginationState(currentPage = page, hasMore = result.value.hasMore),
                        )
                    }
                }
            }
            is AppResult.Failure -> {
                Timber.e("VehicleEvents: falha → ${result.error}")
                if (result.error == AppError.Unauthorized) {
                    sessionStore.clear()
                    _effects.send(VehicleEventsEffect.NavigateToLogin)
                } else if (isRefresh) {
                    _state.update { it.copy(isRefreshing = false) }
                } else {
                    _state.update {
                        it.copy(
                            screenState = VehicleEventsScreenState.Error(result.error),
                            isRefreshing = false,
                        )
                    }
                }
            }
        }
    }
}
