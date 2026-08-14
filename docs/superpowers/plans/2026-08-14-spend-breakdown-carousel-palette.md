# Carrossel Mês/Total na composição de gastos + paleta intuitiva Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `SpendBreakdownCard` vira um carrossel de 2 páginas (Mês/Total) e ganha uma paleta de cores validada com associação intuitiva por categoria.

**Architecture:** Novo use case (`GetMonthlySpendBreakdownUseCase`) espelha a janela de datas que `GetFinancialSummaryUseCase` já usa, retornando o detalhamento por categoria em vez da soma. `HomeViewModel` combina esse resultado com o breakdown total (já existente) num `SpendBreakdownOverview`. A UI reaproveita o padrão de `HorizontalPager` do `FinancialSummaryCard`, promovendo o indicador de pontos pro design system.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit + MockK + Robolectric.

## Global Constraints

- Design aprovado em `docs/superpowers/specs/2026-08-14-spend-breakdown-carousel-palette-design.md`.
- Sub-projeto 2 de 2 (sub-projeto 1 — grid de indicadores + limite de atividade recente — tem spec/plano separado, sem dependência entre os dois).
- A ordem de categorias em `CATEGORY_DISPLAY_ORDER` (Documentos antes de Imposto) é **acessibilidade, não estética** — não reverter sem rodar `scripts/validate_palette.js` (skill `dataviz`) de novo.
- `GetFinancialSummaryUseCase` não é alterado — o novo use case duplica a paginação por data de propósito (mesma decisão já documentada no spec).

---

### Task 1: `GetMonthlySpendBreakdownUseCase`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/home/domain/usecase/GetMonthlySpendBreakdownUseCase.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/home/domain/usecase/GetMonthlySpendBreakdownUseCaseTest.kt`

**Interfaces:**
- Produces: `GetMonthlySpendBreakdownUseCase.invoke(vehicleId: Int): AppResult<SpendBreakdown>`, consumido pela Task 3.

- [ ] **Step 1: Escrever o teste que falha**

Criar `app/src/test/java/com/flowfuel/app/feature/home/domain/usecase/GetMonthlySpendBreakdownUseCaseTest.kt`:

```kotlin
package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.history.domain.model.RefuelPage
import com.flowfuel.app.feature.history.domain.usecase.GetRefuelHistoryUseCase
import com.flowfuel.app.feature.home.domain.model.SpendSlice
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.PagedVehicleEvents
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetMonthlySpendBreakdownUseCaseTest {

    private val getRefuelHistory: GetRefuelHistoryUseCase = mockk()
    private val getVehicleEventsPage: GetVehicleEventsPageUseCase = mockk()
    private val useCase = GetMonthlySpendBreakdownUseCase(getRefuelHistory, getVehicleEventsPage)

    private fun refuel(totalPrice: Double) = RefuelItem(
        id = 1, date = "2026-08-05", energyAmount = 40.0, pricePerUnit = totalPrice / 40.0,
        totalPrice = totalPrice, fullTank = true, refuelType = null, odometer = null, trip = null, consumption = null,
    )

    private fun event(category: EventCategory, amount: Double) = VehicleEvent(
        id = 1, vehicleId = 1, category = category, title = category.label, description = null,
        amount = amount, eventDate = "2026-08-05", odometerKm = null, notes = null, receiptUrl = null,
        createdAt = null, updatedAt = null,
    )

    private fun refuelPage(items: List<RefuelItem>) =
        RefuelPage(items = items, hasMore = false, currentPage = 0, totalElements = items.size)

    private fun eventPage(items: List<VehicleEvent>) =
        PagedVehicleEvents(items = items, currentPage = 0, totalPages = 1, totalElements = items.size)

    @Test
    fun `sums current month refuels into the Combustível slice and groups events by category`() = runTest {
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returns AppResult.Success(refuelPage(listOf(refuel(200.0))))
        coEvery { getVehicleEventsPage(1, 0, null, any(), any()) } returns AppResult.Success(
            eventPage(listOf(event(EventCategory.MAINTENANCE, 40.0)))
        )

        val breakdown = (useCase(1) as AppResult.Success).value

        assertEquals(240.0, breakdown.totalSpent, 0.001)
        assertEquals(
            listOf(SpendSlice("Combustível", 200.0), SpendSlice("Manutenção", 40.0)),
            breakdown.slices,
        )
    }

    @Test
    fun `requests the current month date window`() = runTest {
        val refuelFromCalls = mutableListOf<LocalDate>()
        val refuelToCalls = mutableListOf<LocalDate>()
        coEvery {
            getRefuelHistory(1, 0, 50, capture(refuelFromCalls), capture(refuelToCalls))
        } returns AppResult.Success(refuelPage(emptyList()))
        coEvery { getVehicleEventsPage(1, 0, null, any(), any()) } returns AppResult.Success(eventPage(emptyList()))

        useCase(1)

        val today = LocalDate.now()
        assertEquals(today.withDayOfMonth(1), refuelFromCalls.single())
        assertEquals(today, refuelToCalls.single())
    }

    @Test
    fun `propagates failure from refuel history`() = runTest {
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returns AppResult.Failure(AppError.Network)
        coEvery { getVehicleEventsPage(any(), any(), any(), any(), any()) } returns AppResult.Success(eventPage(emptyList()))

        val result = useCase(1)

        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.Network, (result as AppResult.Failure).error)
    }

    @Test
    fun `propagates failure from vehicle events`() = runTest {
        coEvery { getRefuelHistory(1, 0, 50, any(), any()) } returns AppResult.Success(refuelPage(emptyList()))
        coEvery { getVehicleEventsPage(any(), any(), any(), any(), any()) } returns AppResult.Failure(AppError.Network)

        val result = useCase(1)

        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.Network, (result as AppResult.Failure).error)
    }
}
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.domain.usecase.GetMonthlySpendBreakdownUseCaseTest"`

Expected: FAIL na compilação — `GetMonthlySpendBreakdownUseCase` não existe ainda.

- [ ] **Step 3: Implementar**

Criar `app/src/main/java/com/flowfuel/app/feature/home/domain/usecase/GetMonthlySpendBreakdownUseCase.kt`:

```kotlin
package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.history.domain.usecase.GetRefuelHistoryUseCase
import com.flowfuel.app.feature.home.domain.model.SpendBreakdown
import com.flowfuel.app.feature.home.domain.model.buildSpendBreakdown
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * Composição de gastos do mês atual (até hoje) por categoria — mesma
 * janela de datas de GetFinancialSummaryUseCase.currentMonthTotal, mas
 * com o detalhamento por categoria em vez de só a soma. Duplica a
 * paginação por data que GetFinancialSummaryUseCase já faz — decisão
 * consciente, mesmo padrão já adotado em GetVehicleEventsUseCase (ver
 * docs/superpowers/specs/2026-08-14-spend-breakdown-donut-design.md):
 * evitar mexer em código já em produção só por reuso.
 */
class GetMonthlySpendBreakdownUseCase @Inject constructor(
    private val getRefuelHistory: GetRefuelHistoryUseCase,
    private val getVehicleEventsPage: GetVehicleEventsPageUseCase,
) {
    suspend operator fun invoke(vehicleId: Int): AppResult<SpendBreakdown> {
        val today = LocalDate.now()
        val monthStart = today.withDayOfMonth(1)

        val refuelsResult = fetchAllRefuels(vehicleId, monthStart, today)
        if (refuelsResult is AppResult.Failure) return refuelsResult
        val refuels = (refuelsResult as AppResult.Success).value

        val eventsResult = fetchAllEvents(vehicleId, monthStart, today)
        if (eventsResult is AppResult.Failure) return eventsResult
        val events = (eventsResult as AppResult.Success).value

        val monthFuelSpent = refuels.sumOf { it.totalPrice }
        return AppResult.Success(buildSpendBreakdown(monthFuelSpent, events))
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

- [ ] **Step 4: Rodar o teste e confirmar que passa**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.domain.usecase.GetMonthlySpendBreakdownUseCaseTest"`

Expected: PASS — os 4 testes verdes.

- [ ] **Step 5: Compilar o projeto inteiro**

Run: `./gradlew.bat compileDebugKotlin compileDebugUnitTestKotlin -q`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/domain/usecase/GetMonthlySpendBreakdownUseCase.kt \
        app/src/test/java/com/flowfuel/app/feature/home/domain/usecase/GetMonthlySpendBreakdownUseCaseTest.kt
git commit -m "feat(home): add GetMonthlySpendBreakdownUseCase"
```

---

### Task 2: Paleta validada + `SpendBreakdownOverview` + reordenação de categorias

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/core/designsystem/theme/Color.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/domain/model/SpendBreakdown.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/home/domain/model/SpendBreakdownTest.kt`

**Interfaces:**
- Produces: `SpendBreakdownOverview(monthly: SpendBreakdown, total: SpendBreakdown)`, consumido pelas Tasks 3 e 4.
- Produces: `FFChartColors` com paleta nova (mesmos nomes de campo — `FuelLight`/`FuelDark`, etc. — só os valores de cor mudam), consumido pela Task 4.

- [ ] **Step 1: Escrever o teste que falha**

Em `app/src/test/java/com/flowfuel/app/feature/home/domain/model/SpendBreakdownTest.kt`, atualizar o teste existente `orders named slices by fixed category order, not by amount` — a asserção passa a esperar **Documentos antes de Imposto** (era o contrário):

```kotlin
    @Test
    fun `orders named slices by fixed category order, not by amount`() {
        // Imposto (TAX) é o maior valor, mas Combustível deve continuar
        // aparecendo primeiro — cor e ordem seguem identidade da categoria,
        // não o ranking de valor (ver design doc). Documentos vem antes de
        // Imposto de propósito: Seguro (verde) e Imposto (vermelho) não
        // podem ficar adjacentes, senão a dupla falha o teste de
        // daltonismo (ver docs/superpowers/specs/2026-08-14-spend-breakdown-carousel-palette-design.md).
        val events = listOf(
            event(EventCategory.TAX, 858.90),
            event(EventCategory.DOCUMENTS, 0.01),
        )

        val breakdown = buildSpendBreakdown(fuelSpent = 148.42, events = events)

        assertEquals(
            listOf(
                SpendSlice("Combustível", 148.42),
                SpendSlice("Documentos", 0.01),
                SpendSlice("Imposto", 858.90),
            ),
            breakdown.slices,
        )
    }
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.domain.model.SpendBreakdownTest"`

Expected: FAIL — `CATEGORY_DISPLAY_ORDER` ainda não foi reordenado, a lista real vem como `[Combustível, Imposto, Documentos]`.

- [ ] **Step 3: Implementar**

Em `app/src/main/java/com/flowfuel/app/feature/home/domain/model/SpendBreakdown.kt`, reordenar `CATEGORY_DISPLAY_ORDER` (só troca a posição de `TAX`/`DOCUMENTS`) e adicionar `SpendBreakdownOverview`:

```kotlin
private val CATEGORY_DISPLAY_ORDER = listOf(
    EventCategory.FUEL,
    EventCategory.MAINTENANCE,
    EventCategory.OIL_CHANGE,
    EventCategory.WASH,
    EventCategory.TIRES,
    EventCategory.INSURANCE,
    EventCategory.DOCUMENTS,
    EventCategory.TAX,
).map { it.label }
```

Adicionar, junto de `SpendBreakdown`/`SpendSlice`:

```kotlin
data class SpendBreakdownOverview(
    val monthly: SpendBreakdown,
    val total: SpendBreakdown,
)
```

Em `app/src/main/java/com/flowfuel/app/core/designsystem/theme/Color.kt`, substituir o objeto `FFChartColors` inteiro:

```kotlin
object FFChartColors {
    // Ordem fixa por categoria (identidade estável, não por rank/valor) —
    // mesma ordem em que as fatias são desenhadas no donut. A ordem
    // importa pra acessibilidade: Seguro (verde) e Imposto (vermelho) NÃO
    // ficam adjacentes de propósito — Documentos (roxo) fica entre os
    // dois, senão a dupla verde/vermelho falha o teste de daltonismo
    // (confirmado com scripts/validate_palette.js da skill dataviz, nos
    // dois modos: claro contra #fcfcfb, escuro contra o SurfaceDark real
    // do app #1E293B).
    val FuelLight = Color(0xFFE06B1D)
    val MaintenanceLight = Color(0xFF0A8FA6)
    val OilChangeLight = Color(0xFFA8672A)
    val WashLight = Color(0xFF0E8FAE)
    val TiresLight = Color(0xFF4257C0)
    val InsuranceLight = Color(0xFF1E9E4A)
    val DocumentsLight = Color(0xFF5B3FA6)
    val TaxLight = Color(0xFF9C2A44)
    val OtherLight = FFColors.OutlineVariantLight

    val FuelDark = Color(0xFFD9752E)
    val MaintenanceDark = Color(0xFF1C96AD)
    val OilChangeDark = Color(0xFFB37A3A)
    val WashDark = Color(0xFF1D93B8)
    val TiresDark = Color(0xFF8A5FE0)
    val InsuranceDark = Color(0xFF1F9E70)
    val DocumentsDark = Color(0xFF8A6FC2)
    val TaxDark = Color(0xFFC2436F)
    val OtherDark = FFColors.OutlineVariantDark
}
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.domain.model.SpendBreakdownTest"`

Expected: PASS — os 5 testes verdes (o reordenado + os 4 que não mudaram).

- [ ] **Step 5: Compilar o projeto inteiro**

Run: `./gradlew.bat compileDebugKotlin compileDebugUnitTestKotlin -q`

Expected: sem erros — `SpendBreakdownCard.kt` ainda usa os nomes antigos de campo de `FFChartColors` (`FuelLight` etc.), que continuam existindo com os mesmos nomes, só o valor de cor muda. Nenhum outro arquivo deve quebrar nesta task (a mudança de tipo em `HomeUiState`/`HomeViewModel`/`SpendBreakdownCard` é a Task 3/4).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/designsystem/theme/Color.kt \
        app/src/main/java/com/flowfuel/app/feature/home/domain/model/SpendBreakdown.kt \
        app/src/test/java/com/flowfuel/app/feature/home/domain/model/SpendBreakdownTest.kt
git commit -m "feat(designsystem): validated intuitive palette for spend categories"
```

---

### Task 3: `HomeUiState`/`HomeViewModel` — combinar mês + total

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeUiState.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/SpendBreakdownCard.kt` (troca mínima de assinatura, só pra manter o projeto compilando — a UI completa do carrossel é a Task 4)
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt` (ponto de chamada)

**Interfaces:**
- Consumes: `GetMonthlySpendBreakdownUseCase` (Task 1), `SpendBreakdownOverview` (Task 2).
- Produces: `HomeScreenState.Success.spendBreakdown: SectionState<SpendBreakdownOverview>`, `SpendBreakdownCard(overview: SpendBreakdownOverview, modifier)` (assinatura definitiva — a Task 4 só reescreve o corpo, não a assinatura), consumidos pela Task 4.

- [ ] **Step 1: Escrever os testes que falham**

Em `app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt`, trocar o import de `SpendBreakdown` por `SpendBreakdownOverview` também, e adicionar o import do novo use case:

```kotlin
import com.flowfuel.app.feature.home.domain.model.SpendBreakdown
import com.flowfuel.app.feature.home.domain.model.SpendBreakdownOverview
import com.flowfuel.app.feature.home.domain.model.SpendSlice
```

```kotlin
import com.flowfuel.app.feature.home.domain.usecase.GetMonthlySpendBreakdownUseCase
```

Adicionar o mock, junto de `getVehicleEvents`:

```kotlin
    private val getMonthlySpendBreakdown: GetMonthlySpendBreakdownUseCase = mockk()
```

Em `setUp()`, adicionar o stub padrão e o novo parâmetro no construtor:

```kotlin
        coEvery { getVehicleEvents(any()) } returns AppResult.Success(emptyList())
        coEvery { getMonthlySpendBreakdown(any()) } returns AppResult.Success(
            SpendBreakdown(0.0, listOf(SpendSlice("Combustível", 0.0))),
        )
        viewModel = HomeViewModel(
            getActiveVehicle, getDashboard, logout,
            sessionStore, getVehicles, setActiveVehicle, stationsPrefetcher, getVehicleEventsTotal,
            getVehicleEvents, getMonthlySpendBreakdown, getFinancialSummary, getRecentActivity,
            getUpcomingMaintenance, maintenancePrefsStore,
        )
```

Substituir os 3 testes de `spendBreakdown` existentes (seção `// ── Composição de gastos (spendBreakdown) ──`):

```kotlin
    @Test
    fun `load() populates spendBreakdown combining monthly and total sources`() = runTest {
        coEvery { getDashboard(any()) } returns AppResult.Success(testDashboard.copy(fuelSpent = 100.0))
        coEvery { getVehicleEvents(any()) } returns AppResult.Success(
            listOf(testVehicleEvent(EventCategory.MAINTENANCE, 40.0)),
        )
        coEvery { getMonthlySpendBreakdown(any()) } returns AppResult.Success(
            SpendBreakdown(60.0, listOf(SpendSlice("Combustível", 60.0))),
        )

        viewModel.load()

        val success = viewModel.state.value.screenState as HomeScreenState.Success
        val overview = (success.spendBreakdown as SectionState.Success).value
        assertEquals(140.0, overview.total.totalSpent, 0.001)
        assertEquals(
            listOf(SpendSlice("Combustível", 100.0), SpendSlice("Manutenção", 40.0)),
            overview.total.slices,
        )
        assertEquals(60.0, overview.monthly.totalSpent, 0.001)
        assertEquals(listOf(SpendSlice("Combustível", 60.0)), overview.monthly.slices)
    }

    @Test
    fun `load() isolates spendBreakdown failure without breaking the rest of the screen`() = runTest {
        coEvery { getVehicleEvents(any()) } returns AppResult.Failure(AppError.Network)

        viewModel.load()

        val success = viewModel.state.value.screenState as HomeScreenState.Success
        assertEquals(SectionState.Error(AppError.Network), success.spendBreakdown)
        assertEquals(SectionState.Success(testFinancialSummary), success.financialSummary)
    }

    @Test
    fun `load() isolates spendBreakdown failure when only the monthly source fails`() = runTest {
        coEvery { getMonthlySpendBreakdown(any()) } returns AppResult.Failure(AppError.Network)

        viewModel.load()

        val success = viewModel.state.value.screenState as HomeScreenState.Success
        assertEquals(SectionState.Error(AppError.Network), success.spendBreakdown)
    }

    @Test
    fun `retrySpendBreakdown() re-fetches only the spend breakdown section`() = runTest {
        coEvery { getVehicleEvents(any()) } returns AppResult.Failure(AppError.Network)
        viewModel.load()
        coEvery { getVehicleEvents(any()) } returns AppResult.Success(emptyList())

        viewModel.retrySpendBreakdown()

        val success = viewModel.state.value.screenState as HomeScreenState.Success
        val overview = (success.spendBreakdown as SectionState.Success).value
        assertEquals(SpendBreakdown(0.0, listOf(SpendSlice("Combustível", 0.0))), overview.total)
        assertEquals(SpendBreakdown(0.0, listOf(SpendSlice("Combustível", 0.0))), overview.monthly)
    }
```

- [ ] **Step 2: Rodar os testes e confirmar que falham**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.HomeViewModelTest"`

Expected: FAIL na compilação — `getMonthlySpendBreakdown` não existe em `HomeViewModel`, `SpendBreakdownOverview` não existe ainda, construtor não bate com o número de argumentos.

- [ ] **Step 3: Implementar**

Em `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeUiState.kt`, trocar o import e o tipo do campo:

```kotlin
import com.flowfuel.app.feature.home.domain.model.SpendBreakdownOverview
```

(remove `import com.flowfuel.app.feature.home.domain.model.SpendBreakdown` — não é mais usado neste arquivo)

```kotlin
        val spendBreakdown: SectionState<SpendBreakdownOverview> = SectionState.Loading,
```

Em `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt`, adicionar os imports:

```kotlin
import com.flowfuel.app.feature.home.domain.model.SpendBreakdownOverview
import com.flowfuel.app.feature.home.domain.usecase.GetMonthlySpendBreakdownUseCase
```

Adicionar o parâmetro no construtor, logo após `getVehicleEvents`:

```kotlin
    private val getVehicleEvents: GetVehicleEventsUseCase,
    private val getMonthlySpendBreakdown: GetMonthlySpendBreakdownUseCase,
```

Substituir `loadSpendBreakdown` inteira:

```kotlin
    private suspend fun loadSpendBreakdown(vehicleId: Int, fuelSpent: Double) {
        val totalResult = getVehicleEvents(vehicleId)
        if (totalResult is AppResult.Failure) {
            applySpendBreakdown(vehicleId, SectionState.Error(totalResult.error))
            return
        }
        val total = buildSpendBreakdown(fuelSpent, (totalResult as AppResult.Success).value)

        val sectionState = when (val monthlyResult = getMonthlySpendBreakdown(vehicleId)) {
            is AppResult.Success -> SectionState.Success(SpendBreakdownOverview(monthly = monthlyResult.value, total = total))
            is AppResult.Failure -> SectionState.Error(monthlyResult.error)
        }
        applySpendBreakdown(vehicleId, sectionState)
    }

    private fun applySpendBreakdown(vehicleId: Int, sectionState: SectionState<SpendBreakdownOverview>) {
        _state.update { state ->
            val success = state.screenState as? HomeScreenState.Success ?: return@update state
            if (success.vehicle.id != vehicleId) return@update state
            state.copy(screenState = success.copy(spendBreakdown = sectionState))
        }
    }
```

`retrySpendBreakdown()` **não muda** — já chama `loadSpendBreakdown(vehicleId, fuelSpent)` com a mesma assinatura.

Pra manter o projeto compilando ao final desta task (a UI completa do
carrossel só vem na Task 4), fazer uma troca mínima e mecânica em
`SpendBreakdownCard.kt`: o parâmetro passa a ser `overview:
SpendBreakdownOverview`, e todo lugar que lia `breakdown.X` passa a ler
`overview.total.X` (mostra só "Total" por enquanto — a Task 4 é que
adiciona a página "Mês"). Em
`app/src/main/java/com/flowfuel/app/feature/home/presentation/components/SpendBreakdownCard.kt`:

```kotlin
import com.flowfuel.app.feature.home.domain.model.SpendBreakdownOverview
```

(troca o import de `SpendBreakdown`)

```kotlin
@Composable
fun SpendBreakdownCard(overview: SpendBreakdownOverview, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, title = "Composição de gastos") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpendBreakdownDonut(
                slices = overview.total.slices,
                totalLabel = formatBrl(overview.total.totalSpent),
                colorFor = { label -> sliceColor(label, isDark) },
                modifier = Modifier.size(140.dp),
            )
            Spacer(Modifier.width(FFTheme.spacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
                overview.total.slices.forEach { slice ->
                    val percent = if (overview.total.totalSpent > 0)
                        slice.amount / overview.total.totalSpent * 100 else 0.0
                    SpendLegendRow(
                        color = sliceColor(slice.label, isDark),
                        label = slice.label,
                        amountLabel = formatBrl(slice.amount),
                        percentLabel = "%.0f%%".format(percent),
                    )
                }
            }
        }
    }
}
```

E o preview, no final do arquivo:

```kotlin
@Preview(showBackground = true)
@Composable
private fun SpendBreakdownCardPreview() {
    SpendBreakdownCard(
        overview = SpendBreakdownOverview(
            monthly = SpendBreakdown(totalSpent = 0.0, slices = emptyList()),
            total = SpendBreakdown(
                totalSpent = 1720.65,
                slices = listOf(
                    SpendSlice("Combustível", 890.0),
                    SpendSlice("Manutenção", 420.0),
                    SpendSlice("Seguro", 300.0),
                    SpendSlice("Outros", 110.65),
                ),
            ),
        ),
    )
}
```

(precisa também do import `com.flowfuel.app.feature.home.domain.model.SpendBreakdownOverview`
junto do `SpendBreakdown`/`SpendSlice` já existentes.)

Em `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`, atualizar o ponto de chamada:

```kotlin
                    is SectionState.Success -> SpendBreakdownCard(overview = spendBreakdown.value)
```

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.HomeViewModelTest"`

Expected: PASS — todos os testes (existentes + os atualizados) verdes.

- [ ] **Step 5: Compilar o projeto inteiro**

Run: `./gradlew.bat compileDebugKotlin compileDebugUnitTestKotlin -q`

Expected: sem erros.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeUiState.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt \
        app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/components/SpendBreakdownCard.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt
git commit -m "feat(home): combine monthly and total spend breakdown in HomeViewModel"
```

---

### Task 4: UI — carrossel + indicador de pontos compartilhado

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/core/designsystem/components/FFPagerDotsIndicator.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/FinancialSummaryCard.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/SpendBreakdownCard.kt` (reescreve o corpo pro carrossel de 2 páginas — assinatura pública já ficou pronta na Task 3)

**Interfaces:**
- Consumes: `SpendBreakdownOverview` (Task 2/3), `SpendBreakdownCard(overview, modifier)` já existente (Task 3).
- Produces: `FFPagerDotsIndicator(pagerState, pageCount, modifier)`.

Sem teste automatizado novo — mesma lacuna já aceita pros outros
carrosséis/cards visuais do app. Verificação por `@Preview` atualizado +
checagem manual no emulador.

- [ ] **Step 1: Criar `FFPagerDotsIndicator.kt`**

```kotlin
package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Indicador de pontos genérico pra qualquer HorizontalPager do design system. */
@Composable
fun FFPagerDotsIndicator(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pageCount) { index ->
            val active = pagerState.currentPage == index
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (active) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}
```

- [ ] **Step 2: Atualizar `FinancialSummaryCard.kt`**

Trocar os imports — remover os que ficam sem uso depois de tirar o
`PagerDotsIndicator` privado (`background`, `Box`, `size`, `CircleShape`,
`clip`, `dp`, `fillMaxWidth`, `PagerState`), adicionar o componente
compartilhado:

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.components.FFPagerDotsIndicator
import com.flowfuel.app.core.designsystem.components.FFTrend
import com.flowfuel.app.core.designsystem.components.FFTrendBadge
import com.flowfuel.app.core.designsystem.theme.FFTheme
import kotlin.math.abs
```

No corpo, trocar a última linha do `Column` externo:

```kotlin
            Spacer(Modifier.height(FFTheme.spacing.sm))
            FFPagerDotsIndicator(pagerState = pagerState, pageCount = pageCount)
```

E **remover completamente** a função privada `PagerDotsIndicator` (era
usada só ali).

- [ ] **Step 3: Atualizar `SpendBreakdownCard.kt`**

Substituir o arquivo inteiro:

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.components.FFPagerDotsIndicator
import com.flowfuel.app.core.designsystem.theme.FFChartColors
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.home.domain.model.SpendBreakdown
import com.flowfuel.app.feature.home.domain.model.SpendBreakdownOverview
import com.flowfuel.app.feature.home.domain.model.SpendSlice

@Composable
fun SpendBreakdownCard(overview: SpendBreakdownOverview, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val pageCount = 2
    val pagerState = rememberPagerState(pageCount = { pageCount })

    FFCard(modifier = modifier, variant = FFCardVariant.Flat, title = "Composição de gastos") {
        Column {
            HorizontalPager(state = pagerState) { page ->
                val label = if (page == 0) "Mês" else "Total"
                val breakdown = if (page == 0) overview.monthly else overview.total
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = FFTheme.spacing.sm),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SpendBreakdownDonut(
                            slices = breakdown.slices,
                            totalLabel = formatBrl(breakdown.totalSpent),
                            colorFor = { sliceLabel -> sliceColor(sliceLabel, isDark) },
                            modifier = Modifier.size(140.dp),
                        )
                        Spacer(Modifier.width(FFTheme.spacing.md))
                        Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
                            breakdown.slices.forEach { slice ->
                                val percent = if (breakdown.totalSpent > 0)
                                    slice.amount / breakdown.totalSpent * 100 else 0.0
                                SpendLegendRow(
                                    color = sliceColor(slice.label, isDark),
                                    label = slice.label,
                                    amountLabel = formatBrl(slice.amount),
                                    percentLabel = "%.0f%%".format(percent),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(FFTheme.spacing.sm))
            FFPagerDotsIndicator(pagerState = pagerState, pageCount = pageCount)
        }
    }
}

@Composable
private fun SpendBreakdownDonut(
    slices: List<SpendSlice>,
    totalLabel: String,
    colorFor: (String) -> Color,
    modifier: Modifier = Modifier,
) {
    val total = slices.sumOf { it.amount }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokeWidth = size.minDimension * 0.18f
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = if (total > 0) (slice.amount / total * 360.0).toFloat() else 0f
                drawArc(
                    color = colorFor(slice.label),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }
        Text(
            text = totalLabel.replaceFirst(Regex("\\s"), "\n"),
            style = FFTheme.numericTypography.numericSmall.copy(
                fontSize = 13.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(84.dp),
        )
    }
}

@Composable
private fun SpendLegendRow(color: Color, label: String, amountLabel: String, percentLabel: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(text = amountLabel, style = MaterialTheme.typography.bodySmall)
        Text(
            text = percentLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun sliceColor(label: String, isDark: Boolean): Color = when (label) {
    "Combustível" -> if (isDark) FFChartColors.FuelDark else FFChartColors.FuelLight
    "Manutenção" -> if (isDark) FFChartColors.MaintenanceDark else FFChartColors.MaintenanceLight
    "Troca de Óleo" -> if (isDark) FFChartColors.OilChangeDark else FFChartColors.OilChangeLight
    "Lavagem" -> if (isDark) FFChartColors.WashDark else FFChartColors.WashLight
    "Pneus" -> if (isDark) FFChartColors.TiresDark else FFChartColors.TiresLight
    "Seguro" -> if (isDark) FFChartColors.InsuranceDark else FFChartColors.InsuranceLight
    "Documentos" -> if (isDark) FFChartColors.DocumentsDark else FFChartColors.DocumentsLight
    "Imposto" -> if (isDark) FFChartColors.TaxDark else FFChartColors.TaxLight
    else -> if (isDark) FFChartColors.OtherDark else FFChartColors.OtherLight // "Outros"
}

@Preview(showBackground = true)
@Composable
private fun SpendBreakdownCardPreview() {
    SpendBreakdownCard(
        overview = SpendBreakdownOverview(
            monthly = SpendBreakdown(
                totalSpent = 303.30,
                slices = listOf(
                    SpendSlice("Combustível", 148.42),
                    SpendSlice("Documentos", 154.88),
                ),
            ),
            total = SpendBreakdown(
                totalSpent = 1720.65,
                slices = listOf(
                    SpendSlice("Combustível", 890.0),
                    SpendSlice("Manutenção", 420.0),
                    SpendSlice("Seguro", 300.0),
                    SpendSlice("Outros", 110.65),
                ),
            ),
        ),
    )
}
```

(o ponto de chamada em `HomeScreen.kt` — `SpendBreakdownCard(overview = spendBreakdown.value)` — já foi ajustado na Task 3 e não muda de novo aqui.)

- [ ] **Step 4: Compilar**

Run: `./gradlew.bat compileDebugKotlin -q`

Expected: sem erros.

- [ ] **Step 5: Rodar a suíte completa da Home**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.*"`

Expected: PASS.

- [ ] **Step 6: Verificar manualmente no emulador**

Use a skill `run-android-emulator` deste projeto pra buildar e instalar o
app debug. Login com a conta de teste QA (`retiko1301@jobraux.com`), abrir
a Home de um veículo com abastecimentos + eventos de categorias
diferentes esse mês e no histórico. Confirmar:
- O card "Composição de gastos" agora tem 2 páginas (indicador de pontos
  mostra 2), deslizáveis
- Página "Mês" mostra só os gastos desse mês; página "Total" mostra o
  histórico completo (mesmo valor de antes)
- As cores das fatias batem com a paleta nova (laranja=Combustível,
  verde=Seguro, roxo=Documentos, vermelho=Imposto, etc.) em modo claro e
  escuro
- Nenhuma fatia verde encosta numa fatia vermelha na roda do donut
- O carrossel "Gasto do mês / Combustíveis / Gasto total"
  (`FinancialSummaryCard`) continua funcionando normalmente (confirma que
  a promoção do `FFPagerDotsIndicator` não quebrou nada ali)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/designsystem/components/FFPagerDotsIndicator.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/components/FinancialSummaryCard.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/components/SpendBreakdownCard.kt
git commit -m "feat(home): turn spend breakdown into a Mês/Total carousel"
```
