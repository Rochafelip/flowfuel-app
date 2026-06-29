# Vehicle Events — Abastecimentos na Timeline — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Exibir abastecimentos (`RefuelItem`) mesclados com eventos manuais (`VehicleEvent`) na tela de Eventos do veículo, nas categorias "Todas" e "Combustível", com navegação para `RefuelDetailsScreen` ao tocar.

**Architecture:** Novo `VehicleTimelineItem` sealed interface unifica as duas fontes; o ViewModel carrega abastecimentos em batch único via `HistoryRepository` e os mescla com eventos paginados, ordenados por data descendente. A Screen e o NavHost recebem um novo callback de navegação para abastecimentos.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Coroutines/Flow, MockK, Robolectric, JUnit4.

## Global Constraints

- Pacote raiz: `com.flowfuel.app`
- Abastecimentos carregados com `page=0, size=200` — sem paginação adicional.
- Filtro de data aplicado **client-side** nos abastecimentos (após carregamento).
- Erros no carregamento de abastecimentos são silenciosos — timeline exibe apenas eventos.
- `HistoryRepository` já está vinculado via Hilt em `HistoryModule` — não requer DI adicional.
- Testes usam `UnconfinedTestDispatcher`, `Dispatchers.setMain/resetMain`, `@RunWith(RobolectricTestRunner::class) @Config(sdk = [33])`.

---

## File Map

| Ação | Arquivo |
|---|---|
| **Criar** | `feature/vehicleevent/domain/model/VehicleTimelineItem.kt` |
| **Criar** | `feature/vehicleevent/presentation/components/RefuelTimelineCard.kt` |
| **Criar** | `test/.../vehicleevent/presentation/list/VehicleEventsViewModelTest.kt` |
| **Modificar** | `feature/vehicleevent/presentation/list/VehicleEventsUiState.kt` |
| **Modificar** | `feature/vehicleevent/presentation/list/VehicleEventsViewModel.kt` |
| **Modificar** | `feature/vehicleevent/presentation/list/VehicleEventsScreen.kt` |
| **Modificar** | `navigation/FlowFuelNavHost.kt` |

---

### Task 1: Modelo de domínio `VehicleTimelineItem`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicleevent/domain/model/VehicleTimelineItem.kt`

**Interfaces:**
- Consumes: `VehicleEvent` de `VehicleEventModels.kt`, `RefuelItem` de `feature/history/domain/model/HistoryModels.kt`
- Produces: `VehicleTimelineItem` sealed interface com `EventEntry` e `RefuelEntry`, cada um com `val sortDate: String`

---

- [ ] **Step 1: Criar o arquivo**

```kotlin
// app/src/main/java/com/flowfuel/app/feature/vehicleevent/domain/model/VehicleTimelineItem.kt
package com.flowfuel.app.feature.vehicleevent.domain.model

import com.flowfuel.app.feature.history.domain.model.RefuelItem

sealed interface VehicleTimelineItem {
    val sortDate: String

    data class EventEntry(val event: VehicleEvent) : VehicleTimelineItem {
        override val sortDate get() = event.eventDate
    }

    data class RefuelEntry(val refuel: RefuelItem) : VehicleTimelineItem {
        override val sortDate get() = refuel.date
    }
}
```

- [ ] **Step 2: Verificar compilação**

```
./gradlew :app:compileDebugKotlin
```
Esperado: BUILD SUCCESSFUL (zero erros de compilação).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicleevent/domain/model/VehicleTimelineItem.kt
git commit -m "feat(vehicleevent): add VehicleTimelineItem sealed interface"
```

---

### Task 2: ViewModel + UiState — merge de abastecimentos

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/list/VehicleEventsUiState.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/list/VehicleEventsViewModel.kt`
- Create: `app/src/test/java/com/flowfuel/app/feature/vehicleevent/presentation/list/VehicleEventsViewModelTest.kt`

**Interfaces:**
- Consumes: `VehicleTimelineItem` (Task 1), `HistoryRepository.getRefuelHistory(vehicleId, page, size)`, `EventDateFilter.toDateRange()`
- Produces:
  - `VehicleEventsScreenState.Success(items: List<VehicleTimelineItem>)`
  - `VehicleEventsEffect.NavigateToRefuelDetails(refuelId: Int)`
  - `VehicleEventsViewModel.onRefuelClick(refuelId: Int)`

---

- [ ] **Step 1: Atualizar `VehicleEventsUiState.kt`**

Substituir o conteúdo completo do arquivo:

```kotlin
package com.flowfuel.app.feature.vehicleevent.presentation.list

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.pagination.PaginationState
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.EventDateFilter
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleTimelineItem

sealed interface VehicleEventsScreenState {
    data object Loading : VehicleEventsScreenState
    data class Success(val items: List<VehicleTimelineItem>) : VehicleEventsScreenState
    data class Error(val error: AppError) : VehicleEventsScreenState
    data object Empty : VehicleEventsScreenState
}

data class VehicleEventsUiState(
    val screenState: VehicleEventsScreenState = VehicleEventsScreenState.Loading,
    val isRefreshing: Boolean = false,
    val pagination: PaginationState = PaginationState(),
    val selectedCategory: EventCategory? = null,
    val selectedDateFilter: EventDateFilter = EventDateFilter.All,
    val activeVehicleLabel: String? = null,
)

sealed interface VehicleEventsEffect {
    data class NavigateToCreate(val vehicleId: Int) : VehicleEventsEffect
    data class NavigateToDetails(val eventId: Int) : VehicleEventsEffect
    data class NavigateToRefuelDetails(val refuelId: Int) : VehicleEventsEffect
    data object NavigateToLogin : VehicleEventsEffect
}
```

- [ ] **Step 2: Escrever o teste (falha esperada antes do Step 3)**

Criar `app/src/test/java/com/flowfuel/app/feature/vehicleevent/presentation/list/VehicleEventsViewModelTest.kt`:

```kotlin
package com.flowfuel.app.feature.vehicleevent.presentation.list

import androidx.lifecycle.SavedStateHandle
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.HistoryRepository
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.history.domain.model.RefuelPage
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehicleByIdUseCase
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.EventDateFilter
import com.flowfuel.app.feature.vehicleevent.domain.model.PagedVehicleEvents
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleTimelineItem
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VehicleEventsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val getEventsPage: GetVehicleEventsPageUseCase = mockk()
    private val getVehicleById: GetVehicleByIdUseCase = mockk(relaxed = true)
    private val sessionStore: SessionStore = mockk(relaxed = true)
    private val historyRepository: HistoryRepository = mockk()

    private val savedStateHandle = SavedStateHandle(mapOf("vehicleId" to 1))

    private val emptyEventsPage = PagedVehicleEvents(items = emptyList(), currentPage = 0, totalPages = 1, totalElements = 0)
    private val emptyRefuelPage = RefuelPage(items = emptyList(), hasMore = false, currentPage = 0)

    private fun makeRefuel(id: Int, date: String, refuelType: String? = null) = RefuelItem(
        id = id,
        date = date,
        energyAmount = 40.0,
        pricePerUnit = 5.89,
        totalPrice = 235.6,
        fullTank = true,
        refuelType = refuelType,
        odometer = 50000.0,
        trip = 400.0,
        consumption = 10.0,
    )

    private fun makeEvent(id: Int, date: String, category: EventCategory = EventCategory.FUEL) = VehicleEvent(
        id = id,
        vehicleId = 1,
        category = category,
        title = "Evento $id",
        description = null,
        amount = 100.0,
        eventDate = date,
        odometerKm = null,
        notes = null,
        receiptUrl = null,
        createdAt = null,
        updatedAt = null,
    )

    private fun buildViewModel(): VehicleEventsViewModel =
        VehicleEventsViewModel(savedStateHandle, getEventsPage, getVehicleById, sessionStore, historyRepository)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── shouldIncludeRefuels ──────────────────────────────────────────────────

    @Test
    fun `refuels included when category is null (Todas)`() = runTest {
        coEvery { getEventsPage(any(), any(), isNull(), any(), any()) } returns AppResult.Success(emptyEventsPage)
        coEvery { historyRepository.getRefuelHistory(any(), any(), any(), any(), any()) } returns AppResult.Success(emptyRefuelPage)
        buildViewModel()
        // null category = Todas → deve ter chamado historyRepository
        io.mockk.coVerify(atLeast = 1) { historyRepository.getRefuelHistory(1, 0, 200, null, null) }
    }

    @Test
    fun `refuels included when category is FUEL`() = runTest {
        coEvery { getEventsPage(any(), any(), any(), any(), any()) } returns AppResult.Success(emptyEventsPage)
        coEvery { historyRepository.getRefuelHistory(any(), any(), any(), any(), any()) } returns AppResult.Success(emptyRefuelPage)
        val vm = buildViewModel()

        coEvery { historyRepository.getRefuelHistory(any(), any(), any(), any(), any()) } returns AppResult.Success(emptyRefuelPage)
        vm.onCategorySelected(EventCategory.FUEL)

        io.mockk.coVerify(atLeast = 1) { historyRepository.getRefuelHistory(1, 0, 200, null, null) }
    }

    @Test
    fun `refuels NOT loaded when category is MAINTENANCE`() = runTest {
        coEvery { getEventsPage(any(), any(), any(), any(), any()) } returns AppResult.Success(emptyEventsPage)
        coEvery { historyRepository.getRefuelHistory(any(), any(), any(), any(), any()) } returns AppResult.Success(emptyRefuelPage)
        val vm = buildViewModel()
        io.mockk.clearMocks(historyRepository)

        vm.onCategorySelected(EventCategory.MAINTENANCE)

        io.mockk.coVerify(exactly = 0) { historyRepository.getRefuelHistory(any(), any(), any(), any(), any()) }
    }

    // ── Timeline merge & sort ─────────────────────────────────────────────────

    @Test
    fun `timeline merges events and refuels sorted by date descending`() = runTest {
        val event = makeEvent(1, "2026-06-15")
        val refuel = makeRefuel(10, "2026-06-20")
        coEvery { getEventsPage(any(), any(), isNull(), any(), any()) } returns AppResult.Success(
            PagedVehicleEvents(items = listOf(event), currentPage = 0, totalPages = 1, totalElements = 1)
        )
        coEvery { historyRepository.getRefuelHistory(any(), any(), any(), any(), any()) } returns AppResult.Success(
            RefuelPage(items = listOf(refuel), hasMore = false, currentPage = 0)
        )

        val vm = buildViewModel()
        val state = vm.state.value.screenState as VehicleEventsScreenState.Success

        assertEquals(2, state.items.size)
        // refuel date 2026-06-20 > event date 2026-06-15 → refuel first
        assertTrue(state.items[0] is VehicleTimelineItem.RefuelEntry)
        assertTrue(state.items[1] is VehicleTimelineItem.EventEntry)
    }

    // ── onRefuelClick ─────────────────────────────────────────────────────────

    @Test
    fun `onRefuelClick emits NavigateToRefuelDetails`() = runTest {
        coEvery { getEventsPage(any(), any(), isNull(), any(), any()) } returns AppResult.Success(emptyEventsPage)
        coEvery { historyRepository.getRefuelHistory(any(), any(), any(), any(), any()) } returns AppResult.Success(emptyRefuelPage)
        val vm = buildViewModel()

        val effects = mutableListOf<VehicleEventsEffect>()
        val job = launch(testDispatcher) { vm.effects.collect { effects.add(it) } }

        vm.onRefuelClick(42)
        job.cancel()

        assertEquals(1, effects.size)
        assertEquals(VehicleEventsEffect.NavigateToRefuelDetails(42), effects[0])
    }

    // ── Silent refuel error ───────────────────────────────────────────────────

    @Test
    fun `refuel load failure shows only events without error state`() = runTest {
        val event = makeEvent(1, "2026-06-15")
        coEvery { getEventsPage(any(), any(), isNull(), any(), any()) } returns AppResult.Success(
            PagedVehicleEvents(items = listOf(event), currentPage = 0, totalPages = 1, totalElements = 1)
        )
        coEvery { historyRepository.getRefuelHistory(any(), any(), any(), any(), any()) } returns
            AppResult.Failure(com.flowfuel.app.core.domain.AppError.Unknown)

        val vm = buildViewModel()
        val state = vm.state.value.screenState as VehicleEventsScreenState.Success

        assertEquals(1, state.items.size)
        assertTrue(state.items[0] is VehicleTimelineItem.EventEntry)
    }
}
```

- [ ] **Step 3: Rodar o teste — confirmar que falha**

```
./gradlew :app:testDebugUnitTest --tests "*.VehicleEventsViewModelTest" 2>&1 | tail -20
```
Esperado: falha com `unresolved reference` ou `constructor not found` para `historyRepository`.

- [ ] **Step 4: Atualizar `VehicleEventsViewModel.kt`**

Substituir o conteúdo completo do arquivo:

```kotlin
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
```

- [ ] **Step 5: Rodar os testes**

```
./gradlew :app:testDebugUnitTest --tests "*.VehicleEventsViewModelTest" 2>&1 | tail -30
```
Esperado: todos os testes passam (PASSED).

- [ ] **Step 6: Verificar compilação geral**

```
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|warning:" | head -20
```
Esperado: zero erros (warnings sobre Screen.kt por `s.events` serão fixados na Task 4).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/list/VehicleEventsUiState.kt
git add app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/list/VehicleEventsViewModel.kt
git add app/src/test/java/com/flowfuel/app/feature/vehicleevent/presentation/list/VehicleEventsViewModelTest.kt
git commit -m "feat(vehicleevent): merge refuels into events timeline via HistoryRepository"
```

---

### Task 3: Componente `RefuelTimelineCard`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/components/RefuelTimelineCard.kt`

**Interfaces:**
- Consumes: `RefuelItem` de `feature/history/domain/model/HistoryModels.kt`, `FFCard`, `FFTheme`, padrão visual de `EventCategoryChip` (`Surface` + `FFTheme.extraShapes.pill`)
- Produces: `RefuelTimelineCard(refuel: RefuelItem, onClick: () -> Unit)` — composable público

---

- [ ] **Step 1: Criar o arquivo**

```kotlin
// app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/components/RefuelTimelineCard.kt
package com.flowfuel.app.feature.vehicleevent.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RefuelTimelineCard(refuel: RefuelItem, onClick: () -> Unit) {
    FFCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RefuelTypeChip(refuelType = refuel.refuelType)
                val unit = if (refuel.refuelType == "ELECTRIC") "kWh" else "L"
                Text(
                    text = "${refuelEnergyFormat.format(refuel.energyAmount)} $unit · ${refuelCurrencyFormat.format(refuel.pricePerUnit)}/$unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = refuelCurrencyFormat.format(refuel.totalPrice),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = refuelFormatDate(refuel.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (refuel.odometer != null) {
                    Text(
                        text = "${refuelOdometerFormat.format(refuel.odometer.toLong())} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RefuelTypeChip(refuelType: String?) {
    val label = if (refuelType == "ELECTRIC") "Carga" else "Abastecimento"
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = FFTheme.extraShapes.pill,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FFTheme.spacing.sm, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalGasStation,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private val refuelCurrencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
private val refuelEnergyFormat = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
    maximumFractionDigits = 2
    minimumFractionDigits = 2
}
private val refuelOdometerFormat = NumberFormat.getIntegerInstance(Locale("pt", "BR"))
private val refuelDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("pt", "BR"))

private fun refuelFormatDate(isoDate: String): String =
    runCatching { LocalDate.parse(isoDate).format(refuelDateFormatter) }.getOrDefault(isoDate)
```

- [ ] **Step 2: Verificar compilação**

```
./gradlew :app:compileDebugKotlin 2>&1 | grep "error:" | head -10
```
Esperado: zero erros.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/components/RefuelTimelineCard.kt
git commit -m "feat(vehicleevent): add RefuelTimelineCard composable"
```

---

### Task 4: Atualizar `VehicleEventsScreen`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/list/VehicleEventsScreen.kt`

**Interfaces:**
- Consumes: `VehicleTimelineItem` (Task 1), `RefuelTimelineCard` (Task 3), `VehicleEventsEffect.NavigateToRefuelDetails` (Task 2)
- Produces: `VehicleEventsScreen(..., onNavigateToRefuelDetails: (Int) -> Unit, ...)` — parâmetro adicional

---

- [ ] **Step 1: Adicionar parâmetro `onNavigateToRefuelDetails` e imports necessários**

Localizar a assinatura da função `VehicleEventsScreen` e adicionar o novo parâmetro após `onNavigateToDetails`:

```kotlin
// Antes
onNavigateToDetails: (eventId: Int) -> Unit,

// Depois
onNavigateToDetails: (eventId: Int) -> Unit,
onNavigateToRefuelDetails: (refuelId: Int) -> Unit,
```

- [ ] **Step 2: Adicionar o handler do novo effect no `LaunchedEffect(viewModel)`**

Localizar o bloco:
```kotlin
when (effect) {
    is VehicleEventsEffect.NavigateToCreate -> onNavigateToCreate(effect.vehicleId)
    is VehicleEventsEffect.NavigateToDetails -> onNavigateToDetails(effect.eventId)
    VehicleEventsEffect.NavigateToLogin -> onNavigateToLogin()
}
```

Substituir por:
```kotlin
when (effect) {
    is VehicleEventsEffect.NavigateToCreate -> onNavigateToCreate(effect.vehicleId)
    is VehicleEventsEffect.NavigateToDetails -> onNavigateToDetails(effect.eventId)
    is VehicleEventsEffect.NavigateToRefuelDetails -> onNavigateToRefuelDetails(effect.refuelId)
    VehicleEventsEffect.NavigateToLogin -> onNavigateToLogin()
}
```

- [ ] **Step 3: Atualizar o bloco `LazyColumn` para renderizar ambos os tipos**

Localizar o bloco:
```kotlin
items(s.events, key = { it.id }) { event ->
    VehicleEventCard(
        event = event,
        onClick = { viewModel.onEventClick(event.id) },
    )
}
```

Substituir por (adicionar import de `VehicleTimelineItem` e `RefuelTimelineCard` no topo do arquivo):
```kotlin
items(s.items, key = { item ->
    when (item) {
        is VehicleTimelineItem.EventEntry  -> "event-${item.event.id}"
        is VehicleTimelineItem.RefuelEntry -> "refuel-${item.refuel.id}"
    }
}) { item ->
    when (item) {
        is VehicleTimelineItem.EventEntry ->
            VehicleEventCard(
                event = item.event,
                onClick = { viewModel.onEventClick(item.event.id) },
            )
        is VehicleTimelineItem.RefuelEntry ->
            RefuelTimelineCard(
                refuel = item.refuel,
                onClick = { viewModel.onRefuelClick(item.refuel.id) },
            )
    }
}
```

- [ ] **Step 4: Adicionar imports no topo do arquivo** (se o IDE não adicionou automaticamente)

```kotlin
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleTimelineItem
import com.flowfuel.app.feature.vehicleevent.presentation.components.RefuelTimelineCard
```

- [ ] **Step 5: Verificar compilação**

```
./gradlew :app:compileDebugKotlin 2>&1 | grep "error:" | head -10
```
Esperado: zero erros.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/list/VehicleEventsScreen.kt
git commit -m "feat(vehicleevent): render refuel and event cards in timeline"
```

---

### Task 5: Wiring de navegação em `FlowFuelNavHost`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt`

**Interfaces:**
- Consumes: `VehicleEventsScreen(onNavigateToRefuelDetails = ...)` (Task 4), `Destinations.refuelDetails(id)` (já existe)
- Produces: tela compilando e navegação funcional

---

- [ ] **Step 1: Adicionar `onNavigateToRefuelDetails` no composable `VEHICLE_EVENTS`**

Localizar em `FlowFuelNavHost.kt` o bloco do composable `VEHICLE_EVENTS` que chama `VehicleEventsScreen(...)`. Adicionar o parâmetro após `onNavigateToDetails`:

```kotlin
// Antes
onNavigateToDetails = { eventId ->
    navController.navigate(Destinations.vehicleEventDetails(eventId))
},

// Depois
onNavigateToDetails = { eventId ->
    navController.navigate(Destinations.vehicleEventDetails(eventId))
},
onNavigateToRefuelDetails = { refuelId ->
    navController.navigate(Destinations.refuelDetails(refuelId))
},
```

- [ ] **Step 2: Verificar compilação completa do app**

```
./gradlew :app:compileDebugKotlin 2>&1 | grep "error:" | head -10
```
Esperado: zero erros.

- [ ] **Step 3: Rodar todos os testes**

```
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```
Esperado: BUILD SUCCESSFUL, todos os testes passam.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt
git commit -m "feat(nav): wire refuel details navigation from vehicle events screen"
```

---

## Self-Review

### Cobertura do spec

| Requisito do spec | Task |
|---|---|
| `VehicleTimelineItem` sealed interface com `sortDate` | Task 1 |
| `RefuelEntry` referência cross-feature aceita | Task 1 |
| `Success(items: List<VehicleTimelineItem>)` | Task 2 |
| `shouldIncludeRefuels()` — null e FUEL | Task 2 |
| Batch load `size=200`, erro silencioso | Task 2 |
| `accumulatedRefuels` limpo em `onCategorySelected` | Task 2 |
| `buildTimeline()` — merge + filtro data + sort desc | Task 2 |
| `NavigateToRefuelDetails` effect | Task 2 |
| `onRefuelClick()` | Task 2 |
| `RefuelTimelineCard` — chip, litros, preço, data, odômetro | Task 3 |
| Chip "Abastecimento" / "Carga" por `refuelType` | Task 3 |
| `LazyColumn` com key composta e dois tipos de card | Task 4 |
| `onNavigateToRefuelDetails` em Screen | Task 4 |
| Wiring `Destinations.refuelDetails` no NavHost | Task 5 |

### Checklist de placeholders

Nenhum "TBD", "TODO" ou referência sem implementação encontrada.

### Consistência de tipos

- `RefuelItem.id: Int` ✓ — `Destinations.refuelDetails(id: Int)` ✓
- `VehicleTimelineItem.RefuelEntry.sortDate` → `refuel.date: String` ✓
- `VehicleTimelineItem.EventEntry.sortDate` → `event.eventDate: String` ✓
- `VehicleEventsEffect.NavigateToRefuelDetails(refuelId: Int)` ✓ — usado em Screen e NavHost ✓
