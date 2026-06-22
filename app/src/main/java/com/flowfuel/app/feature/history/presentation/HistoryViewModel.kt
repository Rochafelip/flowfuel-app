package com.flowfuel.app.feature.history.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.pagination.PaginationState
import com.flowfuel.app.feature.history.domain.model.FilterPreset
import com.flowfuel.app.feature.history.domain.model.HistoryFilter
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.history.domain.usecase.DeleteRefuelUseCase
import com.flowfuel.app.feature.history.domain.usecase.GetRefuelHistoryUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehicleByIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getRefuelHistory: GetRefuelHistoryUseCase,
    private val sessionStore: SessionStore,
    private val deleteRefuel: DeleteRefuelUseCase,
    private val getVehicleById: GetVehicleByIdUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private val _effects = Channel<HistoryEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var preDeleteSnapshot: List<RefuelItem>? = null

    companion object {
        private const val PAGE_SIZE = 20
    }

    init {
        viewModelScope.launch {
            sessionStore.activeVehicleIdFlow
                .distinctUntilChanged()
                .collectLatest { vehicleId ->
                    Timber.d("History › activeVehicleId mudou → $vehicleId")
                    loadActiveVehicleLabel(vehicleId)
                    resetAndLoad(vehicleId, filter = HistoryFilter())
                }
        }
    }

    private fun loadActiveVehicleLabel(vehicleId: Int?) {
        if (vehicleId == null) {
            _state.update { it.copy(activeVehicleLabel = null) }
            return
        }
        viewModelScope.launch {
            when (val result = getVehicleById(vehicleId)) {
                is AppResult.Success -> {
                    val v = result.value
                    val year = v.modelYear ?: v.manufactureYear
                    val label = listOfNotNull(v.brand, v.model, year?.toString()).joinToString(" ")
                    _state.update { it.copy(activeVehicleLabel = label) }
                }
                is AppResult.Failure -> Timber.w("History › falha ao carregar label do veículo: ${result.error}")
            }
        }
    }

    // ─── Retry manual ─────────────────────────────────────────────────────────

    fun load() {
        viewModelScope.launch {
            val vehicleId = sessionStore.activeVehicleIdFlow.first()
            Timber.d("History › load() → vehicleId=$vehicleId")
            resetAndLoad(vehicleId, filter = _state.value.filter)
        }
    }

    // ─── Pull-to-refresh ──────────────────────────────────────────────────────

    fun refresh() {
        if (_state.value.screenState !is HistoryScreenState.Success) return
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val vehicleId = sessionStore.activeVehicleIdFlow.first()
            Timber.d("History › refresh() → vehicleId=$vehicleId")
            fetchPage(vehicleId, page = 0, filter = _state.value.filter, append = false, isRefresh = true)
        }
    }

    // ─── Infinite scroll ──────────────────────────────────────────────────────

    fun loadNextPage() {
        val pg = _state.value.paginationState
        if (pg.isLoadingMore || !pg.hasMore || _state.value.isRefreshing) return
        val nextPage = pg.currentPage + 1
        _state.update { it.copy(paginationState = it.paginationState.copy(isLoadingMore = true, pageError = null)) }
        viewModelScope.launch {
            val vehicleId = sessionStore.activeVehicleIdFlow.first() ?: return@launch
            Timber.d("History › loadNextPage() → page=$nextPage")
            fetchPage(vehicleId, page = nextPage, filter = _state.value.filter, append = true, isRefresh = false)
        }
    }

    fun retryNextPage() {
        _state.update { it.copy(paginationState = it.paginationState.copy(pageError = null)) }
        loadNextPage()
    }

    // ─── Filtros ──────────────────────────────────────────────────────────────

    fun applyFilter(filter: HistoryFilter) {
        viewModelScope.launch {
            val vehicleId = sessionStore.activeVehicleIdFlow.first()
            resetAndLoad(vehicleId, filter = filter)
        }
    }

    fun applyPreset(preset: FilterPreset?) {
        val today = LocalDate.now()
        val filter = when (preset) {
            FilterPreset.LAST_30_DAYS  -> HistoryFilter(preset, today.minusDays(30), today)
            FilterPreset.LAST_3_MONTHS -> HistoryFilter(preset, today.minusMonths(3), today)
            FilterPreset.THIS_YEAR     -> HistoryFilter(preset, LocalDate.of(today.year, 1, 1), today)
            FilterPreset.CUSTOM        -> return  // handled separately via applyFilter
            null                       -> HistoryFilter()
        }
        applyFilter(filter)
    }

    fun clearFilter() = applyFilter(HistoryFilter())

    // ─── Deleção ──────────────────────────────────────────────────────────────

    fun requestDelete(item: RefuelItem) {
        _state.update { it.copy(pendingDeleteItem = item) }
    }

    fun cancelDelete() {
        _state.update { it.copy(pendingDeleteItem = null) }
    }

    fun confirmDelete() {
        val item = _state.value.pendingDeleteItem ?: return
        if (_state.value.isDeletingId != null) return

        val current = (_state.value.screenState as? HistoryScreenState.Success)?.items ?: return
        preDeleteSnapshot = current

        val filtered = current.filter { it.id != item.id }
        _state.update {
            it.copy(
                pendingDeleteItem = null,
                isDeletingId      = item.id,
                deleteError       = null,
                screenState       = if (filtered.isEmpty()) HistoryScreenState.Empty
                                    else HistoryScreenState.Success(filtered),
            )
        }

        viewModelScope.launch {
            when (val result = deleteRefuel(item.id)) {
                is AppResult.Success -> {
                    preDeleteSnapshot = null
                    _state.update { it.copy(isDeletingId = null) }
                    Timber.d("History › item ${item.id} deletado")
                }
                is AppResult.Failure -> {
                    Timber.e("History › erro ao deletar ${item.id}: ${result.error}")
                    preDeleteSnapshot?.let { snapshot ->
                        _state.update {
                            it.copy(
                                isDeletingId = null,
                                deleteError  = result.error,
                                screenState  = HistoryScreenState.Success(snapshot),
                            )
                        }
                    }
                    preDeleteSnapshot = null
                }
            }
        }
    }

    fun clearDeleteError() {
        _state.update { it.copy(deleteError = null) }
    }

    // ─── Lógica interna ───────────────────────────────────────────────────────

    private suspend fun resetAndLoad(vehicleId: Int?, filter: HistoryFilter) {
        _state.update {
            it.copy(
                screenState     = HistoryScreenState.Loading,
                isRefreshing    = false,
                paginationState = PaginationState(),
                filter          = filter,
            )
        }
        fetchPage(vehicleId, page = 0, filter = filter, append = false, isRefresh = false)
    }

    private suspend fun fetchPage(
        vehicleId: Int?,
        page: Int,
        filter: HistoryFilter,
        append: Boolean,
        isRefresh: Boolean,
    ) {
        if (vehicleId == null) {
            Timber.w("History › vehicleId é null — sem veículo ativo")
            _state.update {
                it.copy(
                    isRefreshing    = false,
                    paginationState = it.paginationState.copy(isLoadingMore = false),
                    screenState     = if (!append && !isRefresh) HistoryScreenState.Empty else it.screenState,
                )
            }
            return
        }

        Timber.d("History › fetchPage vehicleId=$vehicleId page=$page append=$append")
        when (val result = getRefuelHistory(vehicleId, page, PAGE_SIZE, filter.startDate, filter.endDate)) {
            is AppResult.Success -> {
                val refuelPage = result.value
                Timber.d("History › ${refuelPage.items.size} item(s) página=$page hasMore=${refuelPage.hasMore}")

                if (append) {
                    val existing   = (_state.value.screenState as? HistoryScreenState.Success)?.items ?: emptyList()
                    val existingIds = existing.map { it.id }.toSet()
                    val merged     = existing + refuelPage.items.filter { it.id !in existingIds }
                    _state.update {
                        it.copy(
                            screenState     = HistoryScreenState.Success(merged),
                            paginationState = it.paginationState.copy(
                                isLoadingMore = false,
                                hasMore       = refuelPage.hasMore,
                                currentPage   = page,
                                pageError     = null,
                            ),
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isRefreshing    = false,
                            screenState     = if (refuelPage.items.isEmpty()) HistoryScreenState.Empty
                                             else HistoryScreenState.Success(refuelPage.items),
                            paginationState = PaginationState(
                                currentPage = 0,
                                hasMore     = refuelPage.hasMore,
                            ),
                        )
                    }
                }
            }
            is AppResult.Failure -> {
                Timber.e("History › erro na página $page → ${result.error}")
                when {
                    append    -> _state.update {
                        it.copy(paginationState = it.paginationState.copy(isLoadingMore = false, pageError = result.error))
                    }
                    isRefresh -> _state.update { it.copy(isRefreshing = false) }
                    else      -> handleGlobalError(result.error)
                }
            }
        }
    }

    private suspend fun handleGlobalError(error: AppError) {
        if (error == AppError.Unauthorized) {
            sessionStore.clear()
            _effects.send(HistoryEffect.NavigateToLogin)
        } else {
            _state.update { it.copy(screenState = HistoryScreenState.Error(error)) }
        }
    }
}
