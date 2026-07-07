# Home Dashboard (reformulação) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reformular a `HomeScreen` do FlowFuel para o novo layout de dashboard (header com veículo, resumo financeiro mensal, grid de indicadores, insight, último abastecimento, atividade recente e FAB), calculando tudo no cliente a partir de dados já existentes, sem endpoints novos no backend.

**Architecture:** Evolução in-place do `feature/home` existente (Kotlin + Jetpack Compose + MVVM + Hilt). Dois use cases novos (`GetFinancialSummaryUseCase`, `GetRecentActivityUseCase`) compõem dados de `feature/history` (abastecimentos) e `feature/vehicleevent` (eventos), ambos já com filtro de data server-side. `HomeViewModel` carrega vehicle+dashboard primeiro (bloqueante, já existente) e depois financialSummary+recentActivity em paralelo e independente (cada seção com seu próprio estado de loading/erro, sem travar a tela). A UI é quebrada em composables por seção sob `presentation/components/`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Coroutines/StateFlow, Retrofit, MockK + JUnit4 (+ Robolectric só para o ViewModel, que já usa hoje).

## Global Constraints

- minSdk 26, compileSdk 35; BOM Compose `2024.12.01` — não alterar versões fora do BOM.
- Nenhum endpoint novo no backend — todo cálculo (resumo mensal, preço médio, atividade recente) é client-side sobre endpoints que já suportam filtro de data (`GET refuels/vehicle/{vehicleId}` e `GET vehicle-events/vehicle/{vehicleId}`, ambos com `startDate`/`endDate`).
- Sem mudança de navegação: `HomeScreen` continua o mesmo destino (`MainDestinations.HOME`), mesma posição na bottom bar.
- Reaproveitar componentes de design system existentes (`FFCard`, `FFStatTile`, `FFTrendBadge`, `FFFab`, `FFEmptyState`, `FFErrorState`, `FFSkeletonBlock`, `VehiclePhotoAvatar`) — não recriar variantes.
- Testes de lógica (use cases, ViewModel) em JUnit4 + MockK, seguindo o padrão exato já usado no projeto (`org.junit.Test`, sem Robolectric para classes de domínio puras; Robolectric só onde o arquivo já o exige, como `HomeViewModelTest`). Sem testes de UI/Compose instrumentados.
- `HomeModule.kt` (DI) **não precisa mudar**: os 2 use cases novos são classes concretas com `@Inject constructor` cujas dependências (`GetRefuelHistoryUseCase`, `GetVehicleEventsPageUseCase`) já são resolvidas pelos módulos Hilt de `feature/history`/`feature/vehicleevent`.

---

### Task 1: `ActiveVehicleData` ganha foto e tipo do veículo

O header novo precisa de `VehiclePhotoAvatar` (foto + ícone de fallback por tipo), mas `ActiveVehicleData` hoje não carrega `photoUrl`/tipo — embora o DTO da API (`VehicleResponseDto`) já traga `photo` e `type`. Esta task só liga esses dois campos que já existem na API ao domínio de Home; não é um dado novo.

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/data/HomeRepositoryImpl.kt`

**Interfaces:**
- Produces: `ActiveVehicleData.photoUrl: String?` (default `null`), `ActiveVehicleData.vehicleType: VehicleType` (default `VehicleType.Car`) — consumidos pela `VehicleHeader` (Task 7).

- [ ] **Step 1: Adicionar os campos ao domain model**

Em `HomeModels.kt`, adicionar o import e os dois campos (com default, para não quebrar nenhum call site existente — `ActiveVehicleData` também é construída em testes de `feature/auto`, fora do escopo desta task):

```kotlin
package com.flowfuel.app.feature.home.domain.model

import com.flowfuel.app.feature.vehicle.domain.model.VehicleType

/**
 * Representação simplificada do veículo ativo para a tela principal.
 * Evita acoplar o domínio de Home ao domínio de Vehicle.
 */
data class ActiveVehicleData(
    val id: Int,
    val brand: String,
    val model: String,
    val fuelSubType: String?,
    val capacity: Double?,
    val licensePlate: String?,
    val energyType: String,
    val currentKm: Int,
    val photoUrl: String? = null,
    val vehicleType: VehicleType = VehicleType.Car,
)
```

(As demais declarações do arquivo — `HybridConsumptionBreakdown`, `DashboardData`, `CreateRefuelRequest` — continuam inalteradas.)

- [ ] **Step 2: Mapear os campos no repositório**

Em `HomeRepositoryImpl.kt`, adicionar o import de `VehicleType` e popular os dois campos a partir do DTO, usando o mesmo padrão de parsing de `type` já usado em `VehicleRepositoryImpl.toDomain()`:

```kotlin
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType

// ...

override suspend fun getActiveVehicle(): AppResult<ActiveVehicleData> =
    apiCall { vehicleApi.getActiveVehicle() }.map { dto ->
        ActiveVehicleData(
            id           = dto.id,
            brand        = dto.brand,
            model        = dto.model,
            fuelSubType  = dto.fuelSubType,
            capacity     = dto.capacity,
            licensePlate = dto.licensePlate,
            energyType   = dto.energyType,
            currentKm    = dto.currentKm,
            photoUrl     = dto.photo,
            vehicleType  = VehicleType.entries.firstOrNull { it.apiValue == dto.type } ?: VehicleType.Car,
        )
    }
```

- [ ] **Step 3: Verificar compilação**

Não há teste de unidade para `HomeRepositoryImpl` hoje (nenhum repositório do projeto tem — verificado, é o padrão atual), então a verificação desta task é compilar:

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL, sem erros em `HomeModels.kt`/`HomeRepositoryImpl.kt`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt app/src/main/java/com/flowfuel/app/feature/home/data/HomeRepositoryImpl.kt
git commit -m "feat(home): adiciona foto e tipo do veículo a ActiveVehicleData"
```

---

### Task 2: Domain model `FinancialSummary`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt`

**Interfaces:**
- Produces: `FinancialSummary(currentMonthTotal: Double, previousMonthTotal: Double, averagePricePerUnit: Double?)` com propriedade computada `percentDelta: Double?` — consumido por `GetFinancialSummaryUseCase` (Task 3) e pela UI (Task 8, 10).

- [ ] **Step 1: Adicionar o modelo**

Ao final de `HomeModels.kt`:

```kotlin
/**
 * Resumo financeiro do mês atual (até hoje) comparado ao mês anterior
 * completo — soma abastecimentos + eventos, mesma regra de "gasto total"
 * usada em [com.flowfuel.app.feature.home.presentation.HomeViewModel].
 * Comparar "mês atual até hoje" com "mês anterior completo" é uma
 * aproximação aceita para o MVP, não uma proporção estrita de dias.
 */
data class FinancialSummary(
    val currentMonthTotal: Double,
    val previousMonthTotal: Double,
    val averagePricePerUnit: Double?,
) {
    /** Delta percentual do mês atual vs. anterior; null se o mês anterior não teve gastos. */
    val percentDelta: Double?
        get() = if (previousMonthTotal > 0.0)
            ((currentMonthTotal - previousMonthTotal) / previousMonthTotal) * 100.0
        else null
}
```

- [ ] **Step 2: Verificar compilação**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt
git commit -m "feat(home): adiciona modelo FinancialSummary"
```

---

### Task 3: `GetFinancialSummaryUseCase`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/home/domain/usecase/GetFinancialSummaryUseCase.kt`
- Create: `app/src/test/java/com/flowfuel/app/feature/home/domain/usecase/GetFinancialSummaryUseCaseTest.kt`

**Interfaces:**
- Consumes: `GetRefuelHistoryUseCase(vehicleId: Int, page: Int, size: Int, startDate: LocalDate? = null, endDate: LocalDate? = null): AppResult<RefuelPage>` (feature/history, já existe); `GetVehicleEventsPageUseCase(vehicleId: Int, page: Int, category: EventCategory?, dateFrom: String? = null, dateTo: String? = null): AppResult<PagedVehicleEvents>` (feature/vehicleevent, já existe).
- Produces: `GetFinancialSummaryUseCase(vehicleId: Int): AppResult<FinancialSummary>` — consumido por `HomeViewModel` (Task 6).

- [ ] **Step 1: Escrever os testes (falhando)**

```kotlin
package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.history.domain.model.RefuelPage
import com.flowfuel.app.feature.history.domain.usecase.GetRefuelHistoryUseCase
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.PagedVehicleEvents
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetFinancialSummaryUseCaseTest {

    private val getRefuelHistory: GetRefuelHistoryUseCase = mockk()
    private val getVehicleEventsPage: GetVehicleEventsPageUseCase = mockk()
    private val useCase = GetFinancialSummaryUseCase(getRefuelHistory, getVehicleEventsPage)

    private fun refuel(totalPrice: Double, energyAmount: Double) = RefuelItem(
        id = 1, date = "2026-07-05", energyAmount = energyAmount, pricePerUnit = totalPrice / energyAmount,
        totalPrice = totalPrice, fullTank = true, refuelType = null, odometer = null, trip = null, consumption = null,
    )

    private fun event(amount: Double) = VehicleEvent(
        id = 1, vehicleId = 1, category = EventCategory.MAINTENANCE, title = "Revisão", description = null,
        amount = amount, eventDate = "2026-07-05", odometerKm = null, notes = null, receiptUrl = null,
        createdAt = null, updatedAt = null,
    )

    private fun refuelPage(items: List<RefuelItem>) =
        RefuelPage(items = items, hasMore = false, currentPage = 0, totalElements = items.size)

    private fun eventPage(items: List<VehicleEvent>) =
        PagedVehicleEvents(items = items, currentPage = 0, totalPages = 1, totalElements = items.size)

    @Test
    fun `sums refuels and events for current and previous month`() = runTest {
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returnsMany listOf(
            AppResult.Success(refuelPage(listOf(refuel(200.0, 40.0)))),
            AppResult.Success(refuelPage(listOf(refuel(150.0, 30.0)))),
        )
        coEvery { getVehicleEventsPage(1, 0, null, any(), any()) } returnsMany listOf(
            AppResult.Success(eventPage(listOf(event(50.0)))),
            AppResult.Success(eventPage(listOf(event(30.0)))),
        )

        val summary = (useCase(1) as AppResult.Success).value

        assertEquals(250.0, summary.currentMonthTotal, 0.001)
        assertEquals(180.0, summary.previousMonthTotal, 0.001)
    }

    @Test
    fun `computes average price per unit from current month refuels`() = runTest {
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returnsMany listOf(
            AppResult.Success(refuelPage(listOf(refuel(200.0, 40.0)))),
            AppResult.Success(refuelPage(emptyList())),
        )
        coEvery { getVehicleEventsPage(1, 0, null, any(), any()) } returns AppResult.Success(eventPage(emptyList()))

        val summary = (useCase(1) as AppResult.Success).value

        assertEquals(5.0, summary.averagePricePerUnit!!, 0.001)
    }

    @Test
    fun `averagePricePerUnit is null when there are no refuels this month`() = runTest {
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returns AppResult.Success(refuelPage(emptyList()))
        coEvery { getVehicleEventsPage(1, 0, null, any(), any()) } returns AppResult.Success(eventPage(emptyList()))

        val summary = (useCase(1) as AppResult.Success).value

        assertNull(summary.averagePricePerUnit)
    }

    @Test
    fun `percentDelta is null when previous month had no spending`() = runTest {
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returnsMany listOf(
            AppResult.Success(refuelPage(listOf(refuel(200.0, 40.0)))),
            AppResult.Success(refuelPage(emptyList())),
        )
        coEvery { getVehicleEventsPage(1, 0, null, any(), any()) } returns AppResult.Success(eventPage(emptyList()))

        val summary = (useCase(1) as AppResult.Success).value

        assertNull(summary.percentDelta)
    }

    @Test
    fun `propagates failure from refuel history`() = runTest {
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returns AppResult.Failure(AppError.Network)
        coEvery { getVehicleEventsPage(any(), any(), any(), any(), any()) } returns AppResult.Success(eventPage(emptyList()))

        val result = useCase(1)

        assertEquals(AppError.Network, (result as AppResult.Failure).error)
    }
}
```

- [ ] **Step 2: Rodar os testes para confirmar que falham**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.home.domain.usecase.GetFinancialSummaryUseCaseTest"`
Expected: FAIL — `GetFinancialSummaryUseCase` ainda não existe (erro de compilação/"unresolved reference").

- [ ] **Step 3: Implementar**

```kotlin
package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.history.domain.usecase.GetRefuelHistoryUseCase
import com.flowfuel.app.feature.home.domain.model.FinancialSummary
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * Resumo financeiro do mês atual (até hoje) vs. mês anterior completo,
 * somando abastecimentos ([GetRefuelHistoryUseCase]) e eventos
 * ([GetVehicleEventsPageUseCase]) — as duas fontes que compõem "gasto
 * total" no app. Ambas já suportam filtro de data server-side, então cada
 * mês é buscado com uma janela de datas, sem paginar o histórico inteiro.
 */
class GetFinancialSummaryUseCase @Inject constructor(
    private val getRefuelHistory: GetRefuelHistoryUseCase,
    private val getVehicleEventsPage: GetVehicleEventsPageUseCase,
) {
    suspend operator fun invoke(vehicleId: Int): AppResult<FinancialSummary> {
        val today = LocalDate.now()
        val currentStart = today.withDayOfMonth(1)
        val previousMonth = today.minusMonths(1)
        val previousStart = previousMonth.withDayOfMonth(1)
        val previousEnd = previousMonth.withDayOfMonth(previousMonth.lengthOfMonth())

        val currentRefuelsResult = fetchAllRefuels(vehicleId, currentStart, today)
        if (currentRefuelsResult is AppResult.Failure) return currentRefuelsResult
        val currentRefuels = (currentRefuelsResult as AppResult.Success).value

        val currentEventsResult = fetchAllEvents(vehicleId, currentStart, today)
        if (currentEventsResult is AppResult.Failure) return currentEventsResult
        val currentEvents = (currentEventsResult as AppResult.Success).value

        val previousRefuelsResult = fetchAllRefuels(vehicleId, previousStart, previousEnd)
        if (previousRefuelsResult is AppResult.Failure) return previousRefuelsResult
        val previousRefuels = (previousRefuelsResult as AppResult.Success).value

        val previousEventsResult = fetchAllEvents(vehicleId, previousStart, previousEnd)
        if (previousEventsResult is AppResult.Failure) return previousEventsResult
        val previousEvents = (previousEventsResult as AppResult.Success).value

        val currentRefuelsTotal = currentRefuels.sumOf { it.totalPrice }
        val currentEventsTotal = currentEvents.sumOf { it.amount ?: 0.0 }
        val previousTotal = previousRefuels.sumOf { it.totalPrice } + previousEvents.sumOf { it.amount ?: 0.0 }

        val currentEnergyTotal = currentRefuels.sumOf { it.energyAmount }
        val averagePricePerUnit = if (currentEnergyTotal > 0.0) currentRefuelsTotal / currentEnergyTotal else null

        return AppResult.Success(
            FinancialSummary(
                currentMonthTotal = currentRefuelsTotal + currentEventsTotal,
                previousMonthTotal = previousTotal,
                averagePricePerUnit = averagePricePerUnit,
            )
        )
    }

    private suspend fun fetchAllRefuels(vehicleId: Int, from: LocalDate, to: LocalDate): AppResult<List<RefuelItem>> {
        val items = mutableListOf<RefuelItem>()
        var page = 0
        while (true) {
            when (val result = getRefuelHistory(vehicleId, page, 50, from, to)) {
                is AppResult.Success -> {
                    items.addAll(result.value.items)
                    if (!result.value.hasMore) return AppResult.Success(items)
                    page++
                }
                is AppResult.Failure -> return result
            }
        }
    }

    private suspend fun fetchAllEvents(vehicleId: Int, from: LocalDate, to: LocalDate): AppResult<List<VehicleEvent>> {
        val items = mutableListOf<VehicleEvent>()
        var page = 0
        val fromStr = from.format(isoFmt)
        val toStr = to.format(isoFmt)
        while (true) {
            when (val result = getVehicleEventsPage(vehicleId, page, null, fromStr, toStr)) {
                is AppResult.Success -> {
                    items.addAll(result.value.items)
                    if (!result.value.hasMore) return AppResult.Success(items)
                    page++
                }
                is AppResult.Failure -> return result
            }
        }
    }
}
```

- [ ] **Step 4: Rodar os testes para confirmar que passam**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.home.domain.usecase.GetFinancialSummaryUseCaseTest"`
Expected: BUILD SUCCESSFUL, 5 testes passando.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/domain/usecase/GetFinancialSummaryUseCase.kt app/src/test/java/com/flowfuel/app/feature/home/domain/usecase/GetFinancialSummaryUseCaseTest.kt
git commit -m "feat(home): adiciona GetFinancialSummaryUseCase"
```

---

### Task 4: `GetRecentActivityUseCase`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/home/domain/usecase/GetRecentActivityUseCase.kt`
- Create: `app/src/test/java/com/flowfuel/app/feature/home/domain/usecase/GetRecentActivityUseCaseTest.kt`

**Interfaces:**
- Consumes: `GetRefuelHistoryUseCase(vehicleId: Int, page: Int, size: Int, ...): AppResult<RefuelPage>`; `GetVehicleEventsPageUseCase(vehicleId: Int, page: Int, category: EventCategory?, ...): AppResult<PagedVehicleEvents>`; `VehicleTimelineItem` sealed interface (`feature/vehicleevent/domain/model/VehicleTimelineItem.kt`, já existe, com `EventEntry`/`RefuelEntry` e `sortDate: String`).
- Produces: `GetRecentActivityUseCase(vehicleId: Int): AppResult<List<VehicleTimelineItem>>` — consumido por `HomeViewModel` (Task 6) e `RecentActivityCard` (Task 9).

- [ ] **Step 1: Escrever os testes (falhando)**

```kotlin
package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.history.domain.model.RefuelPage
import com.flowfuel.app.feature.history.domain.usecase.GetRefuelHistoryUseCase
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.PagedVehicleEvents
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetRecentActivityUseCaseTest {

    private val getRefuelHistory: GetRefuelHistoryUseCase = mockk()
    private val getVehicleEventsPage: GetVehicleEventsPageUseCase = mockk()
    private val useCase = GetRecentActivityUseCase(getRefuelHistory, getVehicleEventsPage)

    private fun refuel(id: Int, date: String) = RefuelItem(
        id = id, date = date, energyAmount = 40.0, pricePerUnit = 5.0, totalPrice = 200.0,
        fullTank = true, refuelType = null, odometer = null, trip = null, consumption = null,
    )

    private fun event(id: Int, date: String) = VehicleEvent(
        id = id, vehicleId = 1, category = EventCategory.MAINTENANCE, title = "Revisão", description = null,
        amount = 100.0, eventDate = date, odometerKm = null, notes = null, receiptUrl = null,
        createdAt = null, updatedAt = null,
    )

    @Test
    fun `merges refuels and events sorted by date descending, limited to 4`() = runTest {
        coEvery { getRefuelHistory(1, 0, 4) } returns AppResult.Success(
            RefuelPage(items = listOf(refuel(1, "2026-07-01"), refuel(2, "2026-06-15")), hasMore = false, currentPage = 0, totalElements = 2)
        )
        coEvery { getVehicleEventsPage(1, 0, null) } returns AppResult.Success(
            PagedVehicleEvents(
                items = listOf(event(1, "2026-07-05"), event(2, "2026-06-01"), event(3, "2026-05-01")),
                currentPage = 0, totalPages = 1, totalElements = 3,
            )
        )

        val timeline = (useCase(1) as AppResult.Success).value

        assertEquals(4, timeline.size)
        assertEquals("2026-07-05", timeline[0].sortDate)
        assertEquals("2026-07-01", timeline[1].sortDate)
        assertEquals("2026-06-15", timeline[2].sortDate)
        assertEquals("2026-06-01", timeline[3].sortDate)
    }

    @Test
    fun `propagates failure from events page`() = runTest {
        coEvery { getRefuelHistory(1, 0, 4) } returns AppResult.Success(
            RefuelPage(items = emptyList(), hasMore = false, currentPage = 0, totalElements = 0)
        )
        coEvery { getVehicleEventsPage(1, 0, null) } returns AppResult.Failure(AppError.Network)

        val result = useCase(1)

        assertEquals(AppError.Network, (result as AppResult.Failure).error)
    }
}
```

- [ ] **Step 2: Rodar os testes para confirmar que falham**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.home.domain.usecase.GetRecentActivityUseCaseTest"`
Expected: FAIL — `GetRecentActivityUseCase` não existe.

- [ ] **Step 3: Implementar**

```kotlin
package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.usecase.GetRefuelHistoryUseCase
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleTimelineItem
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import javax.inject.Inject

private const val RECENT_ACTIVITY_LIMIT = 4

/**
 * Combina abastecimentos e eventos num único timeline ordenado por data,
 * mesmo padrão de [com.flowfuel.app.feature.vehicleevent.presentation.list.VehicleEventsViewModel.buildTimeline],
 * truncado para os itens mais recentes.
 */
class GetRecentActivityUseCase @Inject constructor(
    private val getRefuelHistory: GetRefuelHistoryUseCase,
    private val getVehicleEventsPage: GetVehicleEventsPageUseCase,
) {
    suspend operator fun invoke(vehicleId: Int): AppResult<List<VehicleTimelineItem>> {
        val refuelsResult = getRefuelHistory(vehicleId, 0, RECENT_ACTIVITY_LIMIT)
        if (refuelsResult is AppResult.Failure) return refuelsResult
        val refuels = (refuelsResult as AppResult.Success).value.items

        val eventsResult = getVehicleEventsPage(vehicleId, 0, null)
        if (eventsResult is AppResult.Failure) return eventsResult
        val events = (eventsResult as AppResult.Success).value.items

        val timeline = (refuels.map { VehicleTimelineItem.RefuelEntry(it) } +
            events.map { VehicleTimelineItem.EventEntry(it) })
            .sortedByDescending { it.sortDate }
            .take(RECENT_ACTIVITY_LIMIT)

        return AppResult.Success(timeline)
    }
}
```

- [ ] **Step 4: Rodar os testes para confirmar que passam**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.home.domain.usecase.GetRecentActivityUseCaseTest"`
Expected: BUILD SUCCESSFUL, 2 testes passando.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/domain/usecase/GetRecentActivityUseCase.kt app/src/test/java/com/flowfuel/app/feature/home/domain/usecase/GetRecentActivityUseCaseTest.kt
git commit -m "feat(home): adiciona GetRecentActivityUseCase"
```

---

### Task 5: Expandir `HomeUiState`/`HomeScreenState`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeUiState.kt`

**Interfaces:**
- Produces: `SectionState<T>` sealed interface (`Loading`/`Success<T>`/`Error`); `HomeScreenState.Success` ganha `financialSummary: SectionState<FinancialSummary>` e `recentActivity: SectionState<List<VehicleTimelineItem>>` (ambos default `SectionState.Loading`) — consumidos por `HomeViewModel` (Task 6) e `HomeScreen`/`HomeContent` (Task 10).

- [ ] **Step 1: Editar `HomeUiState.kt`**

Adicionar os imports e o novo `SectionState`, e expandir `HomeScreenState.Success`:

```kotlin
package com.flowfuel.app.feature.home.presentation

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.FieldError
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.home.domain.model.FinancialSummary
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleTimelineItem

// ─── Estado da tela ───────────────────────────────────────────────────────────

sealed interface HomeScreenState {
    data object Loading : HomeScreenState
    data class Success(
        val vehicle: ActiveVehicleData,
        val dashboard: DashboardData,
        val financialSummary: SectionState<FinancialSummary> = SectionState.Loading,
        val recentActivity: SectionState<List<VehicleTimelineItem>> = SectionState.Loading,
    ) : HomeScreenState
    data class Error(val error: AppError) : HomeScreenState
}

/** Estado independente de uma seção carregada em paralelo ao restante da tela. */
sealed interface SectionState<out T> {
    data object Loading : SectionState<Nothing>
    data class Success<T>(val value: T) : SectionState<T>
    data class Error(val error: AppError) : SectionState<Nothing>
}
```

(`VehicleSwitcherState`, `OdometerInputMode`, `RefuelFormState`, `HomeUiState`, `HomeEffect` permanecem inalterados — só o bloco de `HomeScreenState` muda.)

- [ ] **Step 2: Verificar compilação**

Run: `./gradlew compileDebugKotlin`
Expected: FAIL — esperado neste ponto, pois `HomeViewModel.kt` ainda constrói `HomeScreenState.Success(vehicle, dashboard)` com a assinatura antiga (2 argumentos posicionais, sem os novos campos). Isso é corrigido na Task 6. Confirme que o erro é exatamente sobre `HomeViewModel.kt`, não sobre `HomeUiState.kt`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeUiState.kt
git commit -m "feat(home): expande HomeScreenState com SectionState por seção"
```

---

### Task 6: Reescrever `HomeViewModel` e `HomeViewModelTest`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `GetFinancialSummaryUseCase` (Task 3), `GetRecentActivityUseCase` (Task 4), `SectionState<T>` (Task 5).
- Produces: `HomeViewModel.retryFinancialSummary()`, `HomeViewModel.retryRecentActivity()` — consumidos pelos cards de erro na UI (Task 8, 9).

- [ ] **Step 1: Atualizar o fixture de teste e adicionar os testes novos (falhando)**

Em `HomeViewModelTest.kt`, adicionar os 2 mocks novos, passá-los ao construtor, e adicionar 3 testes novos ao final da classe (mantendo os testes existentes intactos):

```kotlin
// no topo do arquivo, junto aos outros imports:
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.feature.home.domain.model.FinancialSummary
import com.flowfuel.app.feature.home.domain.usecase.GetFinancialSummaryUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetRecentActivityUseCase
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleTimelineItem

// dentro da classe, junto aos outros mocks:
private val getFinancialSummary: GetFinancialSummaryUseCase = mockk()
private val getRecentActivity: GetRecentActivityUseCase = mockk()

private val testFinancialSummary = FinancialSummary(
    currentMonthTotal = 300.0,
    previousMonthTotal = 250.0,
    averagePricePerUnit = 5.5,
)

// em setUp(), junto aos outros coEvery/construtor:
coEvery { getFinancialSummary(any()) } returns AppResult.Success(testFinancialSummary)
coEvery { getRecentActivity(any()) } returns AppResult.Success(emptyList())
viewModel = HomeViewModel(
    getActiveVehicle, getDashboard, createRefuel, logout,
    sessionStore, getVehicles, setActiveVehicle, stationsPrefetcher, getVehicleEventsTotal,
    getFinancialSummary, getRecentActivity,
)

// novos testes, ao final da classe (antes do último `}`):

// ── Seções independentes (financialSummary / recentActivity) ──────────────

@Test
fun `load() populates financialSummary and recentActivity sections on success`() = runTest {
    viewModel.load()

    val success = viewModel.state.value.screenState as HomeScreenState.Success
    assertEquals(SectionState.Success(testFinancialSummary), success.financialSummary)
    assertEquals(SectionState.Success(emptyList<VehicleTimelineItem>()), success.recentActivity)
}

@Test
fun `load() isolates financialSummary failure without breaking the rest of the screen`() = runTest {
    coEvery { getFinancialSummary(any()) } returns AppResult.Failure(AppError.Network)

    viewModel.load()

    val success = viewModel.state.value.screenState as HomeScreenState.Success
    assertEquals(SectionState.Error(AppError.Network), success.financialSummary)
    assertEquals(SectionState.Success(emptyList<VehicleTimelineItem>()), success.recentActivity)
}

@Test
fun `retryFinancialSummary() re-fetches only the financial summary section`() = runTest {
    coEvery { getFinancialSummary(any()) } returns AppResult.Failure(AppError.Network)
    viewModel.load()
    coEvery { getFinancialSummary(any()) } returns AppResult.Success(testFinancialSummary)

    viewModel.retryFinancialSummary()

    val success = viewModel.state.value.screenState as HomeScreenState.Success
    assertEquals(SectionState.Success(testFinancialSummary), success.financialSummary)
}
```

- [ ] **Step 2: Rodar os testes para confirmar que falham**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.HomeViewModelTest"`
Expected: FAIL — `HomeViewModel` ainda não tem construtor com 11 parâmetros nem `retryFinancialSummary()`.

- [ ] **Step 3: Reescrever `HomeViewModel.kt`**

Substituir o construtor, `load()` e `refresh()`, e adicionar os métodos de retry e os loaders privados das novas seções. `openRefuelSheet`/`closeRefuelSheet`/handlers de formulário/`submitRefuel`/`openVehicleSwitcher`/`closeVehicleSwitcher`/`onVehicleSwitch`/logout/`handleGlobalError`/`fetchDashboardWithEventsTotal` **permanecem exatamente como estão hoje** — só o bloco abaixo muda:

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getActiveVehicle: GetActiveVehicleUseCase,
    private val getDashboard: GetDashboardUseCase,
    private val createRefuel: CreateRefuelUseCase,
    private val logout: LogoutUseCase,
    private val sessionStore: SessionStore,
    private val getVehicles: GetVehiclesUseCase,
    private val setActiveVehicle: SetActiveVehicleUseCase,
    private val stationsPrefetcher: NearbyStationsPrefetcher,
    private val getVehicleEventsTotal: GetVehicleEventsTotalUseCase,
    private val getFinancialSummary: GetFinancialSummaryUseCase,
    private val getRecentActivity: GetRecentActivityUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** ID do veículo carregado por último — usado pelos retries de seção. */
    private var loadedVehicleId: Int? = null

    init { load() }

    // ─── Carregamento ─────────────────────────────────────────────────────────

    fun load() {
        stationsPrefetcher.prefetch()
        _state.update { it.copy(screenState = HomeScreenState.Loading, submitError = null) }
        viewModelScope.launch {
            val storedVehicleId = sessionStore.activeVehicleIdFlow.first()
            val vehicleResult = getActiveVehicle()

            if (vehicleResult is AppResult.Failure) {
                handleGlobalError(vehicleResult.error)
                return@launch
            }

            val vehicle = (vehicleResult as AppResult.Success).value
            val vehicleId = storedVehicleId ?: vehicle.id

            if (storedVehicleId == null) {
                Timber.d("Home › vehicleId não estava no SessionStore, persistindo id=${vehicle.id}")
                sessionStore.saveActiveVehicleId(vehicle.id)
            }
            loadedVehicleId = vehicleId

            when (val dashboardResult = fetchDashboardWithEventsTotal(vehicleId)) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            screenState = HomeScreenState.Success(
                                vehicle = vehicle,
                                dashboard = dashboardResult.value,
                            ),
                        )
                    }
                    launch { loadFinancialSummary(vehicleId) }
                    launch { loadRecentActivity(vehicleId) }
                }
                is AppResult.Failure -> handleGlobalError(dashboardResult.error)
            }
        }
    }

    private suspend fun loadFinancialSummary(vehicleId: Int) {
        val sectionState = when (val result = getFinancialSummary(vehicleId)) {
            is AppResult.Success -> SectionState.Success(result.value)
            is AppResult.Failure -> SectionState.Error(result.error)
        }
        _state.update { state ->
            val success = state.screenState as? HomeScreenState.Success ?: return@update state
            state.copy(screenState = success.copy(financialSummary = sectionState))
        }
    }

    private suspend fun loadRecentActivity(vehicleId: Int) {
        val sectionState = when (val result = getRecentActivity(vehicleId)) {
            is AppResult.Success -> SectionState.Success(result.value)
            is AppResult.Failure -> SectionState.Error(result.error)
        }
        _state.update { state ->
            val success = state.screenState as? HomeScreenState.Success ?: return@update state
            state.copy(screenState = success.copy(recentActivity = sectionState))
        }
    }

    /** Reexecuta só o resumo financeiro, sem recarregar o resto da tela. */
    fun retryFinancialSummary() {
        val vehicleId = loadedVehicleId ?: return
        _state.update { state ->
            val success = state.screenState as? HomeScreenState.Success ?: return@update state
            state.copy(screenState = success.copy(financialSummary = SectionState.Loading))
        }
        viewModelScope.launch { loadFinancialSummary(vehicleId) }
    }

    /** Reexecuta só a atividade recente, sem recarregar o resto da tela. */
    fun retryRecentActivity() {
        val vehicleId = loadedVehicleId ?: return
        _state.update { state ->
            val success = state.screenState as? HomeScreenState.Success ?: return@update state
            state.copy(screenState = success.copy(recentActivity = SectionState.Loading))
        }
        viewModelScope.launch { loadRecentActivity(vehicleId) }
    }

    // ─── Pull-to-refresh ──────────────────────────────────────────────────────

    fun refresh() {
        if (_state.value.screenState !is HomeScreenState.Success) return
        if (_state.value.isRefreshing) return

        _state.update { it.copy(isRefreshing = true, submitError = null) }
        viewModelScope.launch {
            val storedVehicleId = sessionStore.activeVehicleIdFlow.first()
            val vehicleResult = getActiveVehicle()
            if (vehicleResult is AppResult.Failure) {
                _state.update { it.copy(isRefreshing = false) }
                return@launch
            }

            val vehicle = (vehicleResult as AppResult.Success).value
            val vehicleId = storedVehicleId ?: vehicle.id
            loadedVehicleId = vehicleId

            when (val dashboardResult = fetchDashboardWithEventsTotal(vehicleId)) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            isRefreshing = false,
                            screenState = HomeScreenState.Success(
                                vehicle = vehicle,
                                dashboard = dashboardResult.value,
                            ),
                        )
                    }
                    launch { loadFinancialSummary(vehicleId) }
                    launch { loadRecentActivity(vehicleId) }
                }
                is AppResult.Failure ->
                    _state.update { it.copy(isRefreshing = false) }
            }
        }
    }
```

Adicionar os imports novos no topo do arquivo:

```kotlin
import com.flowfuel.app.feature.home.domain.usecase.GetFinancialSummaryUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetRecentActivityUseCase
```

O restante do arquivo (bottom sheet, handlers de formulário, `submitRefuel`, seletor de veículo, logout, `fetchDashboardWithEventsTotal`, `handleGlobalError`) continua idêntico ao atual.

- [ ] **Step 4: Rodar os testes para confirmar que passam**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.HomeViewModelTest"`
Expected: BUILD SUCCESSFUL — todos os testes (os já existentes + os 3 novos) passando.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt
git commit -m "feat(home): carrega resumo financeiro e atividade recente em paralelo, com retry por seção"
```

---

### Task 7: `Formatting.kt` + `VehicleHeader`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/Formatting.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/VehicleHeader.kt`

**Interfaces:**
- Produces: `internal fun formatBrl(Double): String`, `internal fun formatKm(Double): String`, `internal fun formatDate(String): String` (movidos de `HomeScreen.kt`, `internal` = visíveis em todo o módulo `app`); `@Composable fun VehicleHeader(vehicle: ActiveVehicleData, daysSinceLastRefuel: Int?, onVehicleClick: () -> Unit, modifier: Modifier)` — consumidos por `HomeScreen.kt` (Task 10) e demais componentes (Task 8, 9).

- [ ] **Step 1: Criar `Formatting.kt`**

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import java.text.NumberFormat
import java.util.Locale

private val brlFormat: NumberFormat
    get() = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

private val kmFormat: NumberFormat
    get() = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }

internal fun formatBrl(amount: Double): String = brlFormat.format(amount)

internal fun formatKm(km: Double): String = kmFormat.format(km)

/** Converte uma data ISO-8601 (ex: "2024-01-15T10:30:00") para "15/01/2024". */
internal fun formatDate(iso: String): String {
    val datePart = iso.take(10)
    val parts = datePart.split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else datePart
}
```

- [ ] **Step 2: Criar `VehicleHeader.kt`**

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.VehiclePhotoAvatar
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType

@Composable
fun VehicleHeader(
    vehicle: ActiveVehicleData,
    daysSinceLastRefuel: Int?,
    onVehicleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = FFTheme.spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        ) {
            VehiclePhotoAvatar(
                photoUrl = vehicle.photoUrl,
                vehicleType = vehicle.vehicleType,
                size = 48.dp,
                onClick = onVehicleClick,
            )
            Column {
                Text(
                    text = "${vehicle.brand} ${vehicle.model}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = daysSinceRefuelLabel(daysSinceLastRefuel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Reservado para o futuro: hoje é só um ícone estático, sem contagem/lógica de notificações.
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = "Notificações",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun daysSinceRefuelLabel(days: Int?): String = when {
    days == null -> "Pronto para rodar"
    days == 0 -> "Abastecido hoje"
    days == 1 -> "Último abastecimento foi ontem"
    else -> "Há $days dias sem abastecer"
}

@Preview(showBackground = true)
@Composable
private fun VehicleHeaderPreview() {
    VehicleHeader(
        vehicle = ActiveVehicleData(
            id = 1, brand = "Volkswagen", model = "Fox", fuelSubType = null, capacity = null,
            licensePlate = "ABC1D23", energyType = "COMBUSTION", currentKm = 67270,
            photoUrl = null, vehicleType = VehicleType.Car,
        ),
        daysSinceLastRefuel = 3,
        onVehicleClick = {},
    )
}
```

- [ ] **Step 3: Verificar compilação**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL (os dois arquivos ainda não são usados por ninguém, mas devem compilar isoladamente).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/Formatting.kt app/src/main/java/com/flowfuel/app/feature/home/presentation/components/VehicleHeader.kt
git commit -m "feat(home): adiciona Formatting compartilhado e VehicleHeader"
```

---

### Task 8: `FinancialSummaryCard` + `IndicatorsGrid`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/FinancialSummaryCard.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/IndicatorsGrid.kt`

**Interfaces:**
- Produces: `@Composable fun FinancialSummaryCard(currentMonthTotalLabel: String, percentDelta: Double?, modifier: Modifier)`; `data class IndicatorItem(label: String, value: String, unit: String? = null)`; `@Composable fun IndicatorsGrid(consumption: IndicatorItem, averagePrice: IndicatorItem, odometer: IndicatorItem, lastRefuel: IndicatorItem, modifier: Modifier)` — ambos consumidos por `HomeScreen.kt` (Task 10).

- [ ] **Step 1: Criar `FinancialSummaryCard.kt`**

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.components.FFTrend
import com.flowfuel.app.core.designsystem.components.FFTrendBadge
import com.flowfuel.app.core.designsystem.theme.FFTheme
import kotlin.math.abs

@Composable
fun FinancialSummaryCard(
    currentMonthTotalLabel: String,
    percentDelta: Double?,
    modifier: Modifier = Modifier,
) {
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, title = "Gasto do mês") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        ) {
            Text(
                text = currentMonthTotalLabel,
                style = FFTheme.numericTypography.numericLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (percentDelta != null) {
                // Gasto subindo é ruim (positiveIsGood = false): Up vira vermelho, Down vira verde.
                val trend = when {
                    percentDelta > 0.5 -> FFTrend.Up
                    percentDelta < -0.5 -> FFTrend.Down
                    else -> FFTrend.Flat
                }
                FFTrendBadge(
                    trend = trend,
                    label = "%.0f%% vs. mês anterior".format(abs(percentDelta)),
                    positiveIsGood = false,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FinancialSummaryCardPreview() {
    FinancialSummaryCard(currentMonthTotalLabel = "R$ 350,00", percentDelta = 12.0)
}
```

- [ ] **Step 2: Criar `IndicatorsGrid.kt`**

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.flowfuel.app.core.designsystem.components.FFStatTile
import com.flowfuel.app.core.designsystem.theme.FFTheme

data class IndicatorItem(
    val label: String,
    val value: String,
    val unit: String? = null,
)

@Composable
fun IndicatorsGrid(
    consumption: IndicatorItem,
    averagePrice: IndicatorItem,
    odometer: IndicatorItem,
    lastRefuel: IndicatorItem,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap)) {
            IndicatorCard(consumption, modifier = Modifier.weight(1f))
            IndicatorCard(averagePrice, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap)) {
            IndicatorCard(odometer, modifier = Modifier.weight(1f))
            IndicatorCard(lastRefuel, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun IndicatorCard(item: IndicatorItem, modifier: Modifier = Modifier) {
    FFStatTile(
        label = item.label,
        value = item.value,
        unit = item.unit,
        modifier = modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true)
@Composable
private fun IndicatorsGridPreview() {
    IndicatorsGrid(
        consumption = IndicatorItem("Consumo médio", "12.5", "km/L"),
        averagePrice = IndicatorItem("Preço médio", "R$ 5,89"),
        odometer = IndicatorItem("Odômetro", "67.270", "km"),
        lastRefuel = IndicatorItem("Último abastecimento", "Há 3 dias"),
    )
}
```

- [ ] **Step 3: Verificar compilação**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/FinancialSummaryCard.kt app/src/main/java/com/flowfuel/app/feature/home/presentation/components/IndicatorsGrid.kt
git commit -m "feat(home): adiciona FinancialSummaryCard e IndicatorsGrid"
```

---

### Task 9: `InsightCard` + mover `LastRefuelCard` + `RecentActivityCard`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/InsightCard.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/LastRefuelCard.kt` (conteúdo movido de `HomeScreen.kt`)
- Create: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/RecentActivityCard.kt`

**Interfaces:**
- Produces: `@Composable fun InsightCard(modifier: Modifier)`; `@Composable fun LastRefuelCard(dashboard: DashboardData, modifier: Modifier)` (mesma assinatura de hoje, só muda de arquivo/pacote); `@Composable fun RecentActivityCard(items: List<VehicleTimelineItem>, modifier: Modifier)` — todos consumidos por `HomeScreen.kt` (Task 10).

- [ ] **Step 1: Criar `InsightCard.kt`**

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import java.time.LocalDate

private val dailyTips = listOf(
    "Calibrar os pneus regularmente pode economizar até 3% de combustível.",
    "Evite acelerações bruscas: dirigir suave reduz o consumo em até 20%.",
    "Troque o óleo no intervalo recomendado pelo fabricante para manter o motor eficiente.",
    "Ar-condicionado ligado em velocidade baixa consome mais que janelas abertas.",
    "Excesso de peso no porta-malas aumenta o consumo de combustível.",
    "Marcha alta em baixa rotação economiza combustível em trajetos urbanos.",
    "Pneus murchos aumentam o atrito e o consumo — confira a calibragem mensalmente.",
    "Evite deixar o carro ligado parado por muito tempo: prefira desligar o motor.",
    "Filtro de ar sujo reduz a eficiência do motor — verifique a cada revisão.",
    "Planeje trajetos para evitar horários de trânsito intenso e economizar combustível.",
    "Use o freio motor em descidas para poupar as pastilhas e economizar combustível.",
    "Revisões preventivas evitam consumo excessivo por problemas mecânicos não percebidos.",
)

@Composable
fun InsightCard(modifier: Modifier = Modifier) {
    val tip = remember { dailyTips[LocalDate.now().dayOfYear % dailyTips.size] }
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, title = "Dica do dia") {
        Text(
            text = tip,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InsightCardPreview() {
    InsightCard()
}
```

- [ ] **Step 2: Criar `LastRefuelCard.kt`**

Mover exatamente o conteúdo de `LastRefuelCard`/`LastRefuelRow` de `HomeScreen.kt` para este arquivo novo, ajustando só o `package` e os imports (a lógica interna não muda):

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.home.domain.model.DashboardData

@Composable
fun LastRefuelCard(dashboard: DashboardData, modifier: Modifier = Modifier) {
    FFCard(modifier = modifier, title = "Último abastecimento", variant = FFCardVariant.Flat) {
        if (dashboard.totalRefuels == 0 || dashboard.lastRefuelDate == null) {
            Text(
                text = "Nenhum abastecimento registrado ainda.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                LastRefuelRow(label = "Data", value = formatDate(dashboard.lastRefuelDate))
                if (dashboard.lastRefuelEnergyAmount != null) {
                    val unit = dashboard.lastRefuelEnergyUnit ?: "L"
                    LastRefuelRow(
                        label = if (unit == "kWh") "Energia" else "Litros",
                        value = "%.2f %s".format(dashboard.lastRefuelEnergyAmount, unit).replace('.', ','),
                    )
                }
                if (dashboard.lastRefuelAmount != null) {
                    LastRefuelRow(label = "Valor pago", value = formatBrl(dashboard.lastRefuelAmount))
                }
                if (dashboard.lastRefuelEnergyAmount != null && dashboard.lastRefuelAmount != null
                    && dashboard.lastRefuelEnergyAmount > 0.0
                ) {
                    val pricePerUnit = dashboard.lastRefuelAmount / dashboard.lastRefuelEnergyAmount
                    LastRefuelRow(
                        label = if (dashboard.lastRefuelEnergyUnit == "kWh") "Preço/kWh" else "Preço/litro",
                        value = formatBrl(pricePerUnit),
                        highlight = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun LastRefuelRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
```

- [ ] **Step 3: Criar `RecentActivityCard.kt`**

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleTimelineItem

@Composable
fun RecentActivityCard(items: List<VehicleTimelineItem>, modifier: Modifier = Modifier) {
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, title = "Atividade recente") {
        if (items.isEmpty()) {
            Text(
                text = "Nenhuma atividade registrada ainda.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
                items.forEach { item -> RecentActivityRow(item) }
            }
        }
    }
}

private data class RowData(val icon: ImageVector, val title: String, val amount: Double?, val date: String)

@Composable
private fun RecentActivityRow(item: VehicleTimelineItem) {
    val row = when (item) {
        is VehicleTimelineItem.RefuelEntry -> RowData(
            icon = Icons.Default.LocalGasStation,
            title = "Abastecimento",
            amount = item.refuel.totalPrice,
            date = item.refuel.date,
        )
        is VehicleTimelineItem.EventEntry -> RowData(
            icon = categoryIcon(item.event.category),
            title = item.event.title,
            amount = item.event.amount,
            date = item.event.eventDate,
        )
    }
    ListItem(
        leadingContent = { Icon(row.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(row.title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            Text(formatDate(row.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            row.amount?.let {
                Text(formatBrl(it), style = FFTheme.numericTypography.numericSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

private fun categoryIcon(category: EventCategory): ImageVector = when (category) {
    EventCategory.FUEL -> Icons.Default.LocalGasStation
    EventCategory.MAINTENANCE, EventCategory.OIL_CHANGE, EventCategory.TIRES -> Icons.Default.Build
    else -> Icons.Default.Receipt
}

@Preview(showBackground = true)
@Composable
private fun RecentActivityCardPreview() {
    RecentActivityCard(items = emptyList())
}
```

- [ ] **Step 4: Remover `LastRefuelCard`/`LastRefuelRow` de `HomeScreen.kt`**

Deletar as duas funções (`LastRefuelCard` e `LastRefuelRow`) do arquivo `HomeScreen.kt` — o conteúdo já foi movido no Step 2. Não rodar build ainda: `HomeScreen.kt` só volta a compilar depois da Task 10 (que também remove os composables de hero card e reimporta `LastRefuelCard` do novo pacote).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/InsightCard.kt app/src/main/java/com/flowfuel/app/feature/home/presentation/components/LastRefuelCard.kt app/src/main/java/com/flowfuel/app/feature/home/presentation/components/RecentActivityCard.kt app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt
git commit -m "feat(home): adiciona InsightCard e RecentActivityCard, move LastRefuelCard para components/"
```

---

### Task 10: Reescrever `HomeScreen.kt`

Remove o hero card de consumo (`WelcomeHeroCard`/`ConsumptionHeroCard`/`HybridConsumptionHeroCard`/`HybridMetricColumn`) e a `GreetingBanner` — o spec-prompt original não pede um hero card separado; consumo passa a ser só mais um indicador no grid. **Efeito colateral aceito:** o detalhamento visual de consumo híbrido (combustão vs. elétrico lado a lado) que existia só no hero card não tem equivalente no novo grid 2x2 (que mostra um valor único de consumo) — perda de detalhe menor, fora do escopo do spec-prompt.

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`

**Interfaces:**
- Consumes: `VehicleHeader`, `FinancialSummaryCard`, `IndicatorsGrid`+`IndicatorItem`, `InsightCard`, `LastRefuelCard`, `RecentActivityCard`, `formatBrl`/`formatKm` (todos de `feature/home/presentation/components/`, Tasks 7-9); `SectionState<T>` (Task 5); `FFFab`, `FFEmptyState`, `FFErrorState`, `FFSkeletonBlock` (design system, já existentes).

- [ ] **Step 1: Reescrever o arquivo inteiro**

```kotlin
package com.flowfuel.app.feature.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.core.designsystem.components.FFEmptyState
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFFab
import com.flowfuel.app.core.designsystem.components.FFSkeletonBlock
import com.flowfuel.app.core.designsystem.components.FFSkeletonLine
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.home.domain.model.FinancialSummary
import com.flowfuel.app.feature.home.presentation.components.FinancialSummaryCard
import com.flowfuel.app.feature.home.presentation.components.IndicatorItem
import com.flowfuel.app.feature.home.presentation.components.IndicatorsGrid
import com.flowfuel.app.feature.home.presentation.components.InsightCard
import com.flowfuel.app.feature.home.presentation.components.LastRefuelCard
import com.flowfuel.app.feature.home.presentation.components.RecentActivityCard
import com.flowfuel.app.feature.home.presentation.components.VehicleHeader
import com.flowfuel.app.feature.home.presentation.components.formatBrl
import com.flowfuel.app.feature.home.presentation.components.formatKm
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleTimelineItem
import kotlinx.coroutines.flow.collectLatest
import java.util.Calendar

// ─── Tela principal ────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToAddVehicle: () -> Unit,
    openRefuelSheet: Boolean = false,
    onRefuelSheetOpened: () -> Unit = {},
    refreshTrigger: Boolean = false,
    onRefreshConsumed: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(openRefuelSheet) {
        if (openRefuelSheet) {
            viewModel.openRefuelSheet()
            onRefuelSheetOpened()
        }
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger) {
            viewModel.refresh()
            onRefreshConsumed()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                HomeEffect.NavigateToLogin -> onNavigateToLogin()
                HomeEffect.RefuelRegistered -> snackbarHostState.showSnackbar(
                    FFSnackbarVisuals(
                        message = "Abastecimento registrado com sucesso!",
                        kind = FFSnackbarKind.Success,
                        duration = SnackbarDuration.Short,
                    ),
                )
            }
        }
    }

    Scaffold(
        // Zera os insets do sistema: eles já foram consumidos pelo Scaffold
        // externo (MainContainerScreen), evitando padding duplicado.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.screenState is HomeScreenState.Success) {
                FFFab(
                    icon = Icons.Default.LocalGasStation,
                    contentDescription = "Registrar abastecimento",
                    onClick = viewModel::openRefuelSheet,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val s = state.screenState) {
                HomeScreenState.Loading -> HomeLoadingSkeleton(modifier = Modifier.fillMaxSize())

                is HomeScreenState.Error -> FFErrorState(
                    message = s.error.userMessage(),
                    onRetry = viewModel::load,
                    modifier = Modifier.align(Alignment.Center),
                )

                is HomeScreenState.Success -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    HomeContent(
                        vehicle = s.vehicle,
                        dashboard = s.dashboard,
                        financialSummary = s.financialSummary,
                        recentActivity = s.recentActivity,
                        onRegisterRefuel = viewModel::openRefuelSheet,
                        onVehicleClick = viewModel::openVehicleSwitcher,
                        onRetryFinancialSummary = viewModel::retryFinancialSummary,
                        onRetryRecentActivity = viewModel::retryRecentActivity,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (state.showRefuelSheet) {
        val energyType = (state.screenState as? HomeScreenState.Success)
            ?.vehicle?.energyType ?: ""
        QuickRefuelBottomSheet(
            form                      = state.refuelForm,
            isSubmitting              = state.isSubmittingRefuel,
            submitError               = state.submitError,
            energyType                = energyType,
            onOdometerInputModeChange = viewModel::onOdometerInputModeChange,
            onTripKmChange            = viewModel::onTripKmChange,
            onOdometerChange          = viewModel::onOdometerChange,
            onLitersChange = viewModel::onLitersChange,
            onTotalPriceInput = viewModel::onTotalPriceInput,
            onFullTankToggle = viewModel::onFullTankToggle,
            onRefuelTypeChange = viewModel::onRefuelTypeChange,
            onSubmit = viewModel::submitRefuel,
            onDismiss = viewModel::closeRefuelSheet,
        )
    }

    if (state.showVehicleSwitcher) {
        VehicleSwitcherBottomSheet(
            state = state.vehicleSwitcherState,
            onVehicleSelect = viewModel::onVehicleSwitch,
            onAddVehicle = {
                viewModel.closeVehicleSwitcher()
                onNavigateToAddVehicle()
            },
            onRetry = viewModel::openVehicleSwitcher,
            onDismiss = viewModel::closeVehicleSwitcher,
        )
    }
}

// ─── Conteúdo principal (estado Success) ──────────────────────────────────────

@Composable
private fun HomeContent(
    vehicle: ActiveVehicleData,
    dashboard: DashboardData,
    financialSummary: SectionState<FinancialSummary>,
    recentActivity: SectionState<List<VehicleTimelineItem>>,
    onRegisterRefuel: () -> Unit,
    onVehicleClick: () -> Unit,
    onRetryFinancialSummary: () -> Unit,
    onRetryRecentActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFirstUse = dashboard.totalRefuels == 0
    val daysSince = remember(dashboard.lastRefuelDate) { daysSinceRefuel(dashboard.lastRefuelDate) }
    val consumptionUnit = dashboard.consumptionUnit
        ?: if (vehicle.energyType.equals("ELECTRIC", ignoreCase = true)) "km/kWh" else "km/L"
    val consumptionValue = dashboard.averageConsumption?.let { "%.1f".format(it) } ?: "—"

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = FFTheme.spacing.md,
            vertical = FFTheme.spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap),
    ) {
        item {
            VehicleHeader(
                vehicle = vehicle,
                daysSinceLastRefuel = if (isFirstUse) null else daysSince,
                onVehicleClick = onVehicleClick,
            )
        }

        if (isFirstUse) {
            item {
                FFEmptyState(
                    title = "Pronto para começar",
                    description = "Registre seu primeiro abastecimento para ver seus indicadores e resumo financeiro.",
                    actionText = "Registrar abastecimento",
                    onAction = onRegisterRefuel,
                )
            }
        } else {
            item {
                when (financialSummary) {
                    is SectionState.Success -> FinancialSummaryCard(
                        currentMonthTotalLabel = formatBrl(financialSummary.value.currentMonthTotal),
                        percentDelta = financialSummary.value.percentDelta,
                    )
                    SectionState.Loading -> FFSkeletonBlock(height = 96.dp)
                    is SectionState.Error -> SectionErrorCard(onRetry = onRetryFinancialSummary)
                }
            }

            item {
                val averagePrice = (financialSummary as? SectionState.Success)?.value?.averagePricePerUnit
                IndicatorsGrid(
                    consumption = IndicatorItem("Consumo médio", consumptionValue, consumptionUnit),
                    averagePrice = IndicatorItem("Preço médio", averagePrice?.let(::formatBrl) ?: "—"),
                    odometer = IndicatorItem("Odômetro", formatKm(vehicle.currentKm.toDouble()), "km"),
                    lastRefuel = IndicatorItem("Último abastecimento", shortDaysSinceLabel(daysSince)),
                )
            }
        }

        item { InsightCard() }

        if (!isFirstUse) {
            item { LastRefuelCard(dashboard = dashboard) }

            item {
                when (recentActivity) {
                    is SectionState.Success -> RecentActivityCard(items = recentActivity.value)
                    SectionState.Loading -> FFSkeletonBlock(height = 160.dp)
                    is SectionState.Error -> SectionErrorCard(onRetry = onRetryRecentActivity)
                }
            }
        }

        // Espaço para o FAB não sobrepor o último item da lista.
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ─── Erro isolado por seção ────────────────────────────────────────────────────

@Composable
private fun SectionErrorCard(onRetry: () -> Unit) {
    com.flowfuel.app.core.designsystem.components.FFCard(
        variant = com.flowfuel.app.core.designsystem.components.FFCardVariant.Flat,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
            androidx.compose.material3.Text(
                text = "Não foi possível carregar esta seção.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.TextButton(onClick = onRetry) {
                androidx.compose.material3.Text("Tentar novamente")
            }
        }
    }
}

// ─── Skeleton de carregamento ─────────────────────────────────────────────────

@Composable
private fun HomeLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(FFTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap),
    ) {
        FFSkeletonBlock(height = 56.dp)
        FFSkeletonBlock(height = 96.dp)
        FFSkeletonBlock(height = 176.dp)
        FFSkeletonBlock(height = 96.dp)
        FFSkeletonBlock(height = 160.dp)
        FFSkeletonLine(widthFraction = 0.6f)
        FFSkeletonLine(widthFraction = 0.4f)
    }
}

// ─── Helpers de data ──────────────────────────────────────────────────────────

private fun daysSinceRefuel(lastRefuelDate: String?): Int? {
    lastRefuelDate ?: return null
    return runCatching {
        val datePart = lastRefuelDate.take(10)
        val parts = datePart.split("-")
        val refuel = Calendar.getInstance().apply {
            set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        ((today.timeInMillis - refuel.timeInMillis) / 86_400_000L).toInt()
    }.getOrNull()
}

private fun shortDaysSinceLabel(days: Int?): String = when {
    days == null -> "—"
    days == 0 -> "Hoje"
    days == 1 -> "Ontem"
    else -> "Há $days dias"
}
```

Note: os imports totalmente qualificados inline em `SectionErrorCard` (`com.flowfuel.app.core.designsystem.components.FFCard` etc.) são só para manter este snippet do plano autocontido — ao escrever o arquivo de verdade, promova-os para imports normais no topo do arquivo (`FFCard`, `FFCardVariant`, `Text`, `MaterialTheme`, `TextButton`), evitando os nomes qualificados no corpo da função.

- [ ] **Step 2: Rodar o build completo**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL — nenhuma referência a `WelcomeHeroCard`/`ConsumptionHeroCard`/`HybridConsumptionHeroCard`/`HybridMetricColumn`/`GreetingBanner` deve sobrar no módulo (foram todas removidas junto com a reescrita deste arquivo).

- [ ] **Step 3: Rodar toda a suíte de testes de Home**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.home.*"`
Expected: BUILD SUCCESSFUL — `HomeViewModelTest`, `RefuelFormStateTest`, `GetFinancialSummaryUseCaseTest`, `GetRecentActivityUseCaseTest` todos passando.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt
git commit -m "feat(home): reescreve HomeScreen com o novo layout de dashboard e FAB"
```

---

### Task 11: Verificação final

**Files:** nenhum (só comandos de verificação).

- [ ] **Step 1: Rodar a suíte de testes completa**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL — nenhum teste de outra feature quebrou (em especial `feature/auto`, que também constrói `ActiveVehicleData`, e `feature/vehicleevent`, cujos use cases agora são reaproveitados por `feature/home`).

- [ ] **Step 2: Build completo do app**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Verificação manual na tela**

Seguir a skill `verify`/`run` deste projeto: instalar o debug build num emulador ou dispositivo, abrir a Home e conferir:
- Header mostra veículo, avatar/foto (ou ícone de fallback) e dias sem abastecer.
- Card de resumo financeiro mostra o gasto do mês e a badge de comparação (ou vazio/skeleton conforme o caso).
- Grid 2x2 mostra consumo, preço médio, odômetro e último abastecimento.
- Card de dica do dia aparece sempre.
- Card de último abastecimento (detalhado) e card de atividade recente aparecem abaixo.
- FAB abre o mesmo formulário de abastecimento de antes.
- Em um veículo recém-criado sem abastecimentos, aparece o `FFEmptyState` no lugar do resumo financeiro/grid/atividade recente.

- [ ] **Step 4: Commit final (se houver ajustes da verificação manual)**

Só necessário se a verificação manual (Step 3) revelar algo a corrigir; caso contrário, este task não gera commit novo.
