package com.flowfuel.app.feature.vehicleevent.presentation.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.pagination.PaginationState
import com.flowfuel.app.feature.history.domain.HistoryRepository
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehicleByIdUseCase
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.EventDateFilter
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleTimelineItem
import com.flowfuel.app.feature.vehicleevent.domain.model.toDateRange
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
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class VehicleEventsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getEventsPage: GetVehicleEventsPageUseCase,
    private val getVehicleById: GetVehicleByIdUseCase,
    private val sessionStore: SessionStore,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private var vehicleId: Int = savedStateHandle["vehicleId"] ?: -1

    private val _state = MutableStateFlow(VehicleEventsUiState())
    val state: StateFlow<VehicleEventsUiState> = _state.asStateFlow()

    private val _effects = Channel<VehicleEventsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var accumulatedEvents: MutableList<VehicleEvent> = mutableListOf()
    private var accumulatedRefuels: List<RefuelItem> = emptyList()
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

    private fun shouldIncludeRefuels(): Boolean {
        val cat = _state.value.selectedCategory
        return cat == null || cat == EventCategory.FUEL
    }

    private suspend fun loadRefuels() {
        if (!shouldIncludeRefuels()) {
            accumulatedRefuels = emptyList()
            return
        }
        // TODO: paginar se volume de abastecimentos superar 200 por veículo
        when (val result = historyRepository.getRefuelHistory(vehicleId, page = 0, size = 200)) {
            is AppResult.Success -> accumulatedRefuels = result.value.items
            is AppResult.Failure -> accumulatedRefuels = emptyList()
        }
    }

    private fun filterRefuelsByDate(filter: EventDateFilter): List<RefuelItem> {
        val (fromStr, toStr) = filter.toDateRange()
        if (fromStr == null) return accumulatedRefuels
        val fromDate = LocalDate.parse(fromStr)
        val toDate = toStr?.let { LocalDate.parse(it) }
        return accumulatedRefuels.filter { item ->
            runCatching {
                val date = LocalDate.parse(item.date)
                !date.isBefore(fromDate) && (toDate == null || !date.isAfter(toDate))
            }.getOrDefault(true)
        }
    }

    private fun buildTimeline(): List<VehicleTimelineItem> {
        val events = accumulatedEvents.map { VehicleTimelineItem.EventEntry(it) }
        val refuels = filterRefuelsByDate(_state.value.selectedDateFilter)
            .map { VehicleTimelineItem.RefuelEntry(it) }
        return (events + refuels).sortedByDescending { it.sortDate }
    }

    fun load() {
        loadJob?.cancel()
        accumulatedEvents = mutableListOf()
        accumulatedRefuels = emptyList()
        isLoadingMore = false
        _state.update {
            it.copy(
                screenState = VehicleEventsScreenState.Loading,
                isRefreshing = false,
                pagination = PaginationState(),
            )
        }
        loadJob = viewModelScope.launch {
            loadRefuels()
            fetchPage(0)
        }
    }

    fun refresh() {
        loadJob?.cancel()
        accumulatedEvents = mutableListOf()
        accumulatedRefuels = emptyList()
        isLoadingMore = false
        _state.update { it.copy(isRefreshing = true, pagination = PaginationState()) }
        loadJob = viewModelScope.launch {
            loadRefuels()
            fetchPage(0, isRefresh = true)
        }
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
                    val timeline = buildTimeline()
                    _state.update { s ->
                        s.copy(
                            screenState = VehicleEventsScreenState.Success(timeline),
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
        accumulatedRefuels = emptyList()
        isLoadingMore = false
        _state.update {
            it.copy(
                selectedCategory = category,
                pagination = PaginationState(),
                screenState = VehicleEventsScreenState.Loading,
            )
        }
        loadJob = viewModelScope.launch {
            loadRefuels()
            fetchPage(0)
        }
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
        // refuels não são resetados aqui — filtragem de data é client-side em buildTimeline()
        loadJob = viewModelScope.launch { fetchPage(0) }
    }

    fun onEventClick(eventId: Int) {
        viewModelScope.launch { _effects.send(VehicleEventsEffect.NavigateToDetails(eventId)) }
    }

    fun onRefuelClick(refuelId: Int) {
        viewModelScope.launch { _effects.send(VehicleEventsEffect.NavigateToRefuelDetails(refuelId)) }
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
        val timeline = buildTimeline()
        _state.update {
            it.copy(
                screenState = if (timeline.isEmpty()) VehicleEventsScreenState.Empty
                              else VehicleEventsScreenState.Success(timeline),
            )
        }
    }

    private suspend fun fetchPage(page: Int, isRefresh: Boolean = false) {
        val category = _state.value.selectedCategory
        val (dateFrom, dateTo) = _state.value.selectedDateFilter.toDateRange()
        when (val result = getEventsPage(vehicleId, page, category, dateFrom, dateTo)) {
            is AppResult.Success -> {
                val items = result.value.items
                accumulatedEvents.addAll(items)
                val timeline = buildTimeline()
                if (timeline.isEmpty() && page == 0) {
                    _state.update {
                        it.copy(
                            screenState = VehicleEventsScreenState.Empty,
                            isRefreshing = false,
                            pagination = PaginationState(currentPage = 0, hasMore = false),
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            screenState = VehicleEventsScreenState.Success(timeline),
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
