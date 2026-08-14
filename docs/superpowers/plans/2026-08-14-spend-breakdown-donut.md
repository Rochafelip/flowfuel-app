# Gráfico de composição de gastos (donut) na Home Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Substituir o card "Dica do dia" na Home por um donut chart mostrando a composição do gasto total do veículo por categoria (Combustível + categorias de evento), com legenda e valor total no centro.

**Architecture:** Novo use case pagina a lista completa de eventos do veículo; uma função pura funde essa lista com `dashboard.fuelSpent` numa lista de até 6 fatias (5 categorias nomeadas + "Outros"); uma nova seção independente no `HomeUiState` carrega esse dado em paralelo às outras seções da Home; um novo componente Compose desenha o donut com `Canvas`/`drawArc` e a legenda.

**Tech Stack:** Kotlin, Jetpack Compose (`Canvas`, `drawArc`), MockK + JUnit + Robolectric para testes de ViewModel, JUnit puro para a função de agregação.

## Global Constraints

- Design aprovado em `docs/superpowers/specs/2026-08-14-spend-breakdown-donut-design.md` — qualquer desvio deste plano em relação ao spec deve ser sinalizado, não decidido silenciosamente.
- Nenhum endpoint novo no backend — agregação por categoria é 100% client-side.
- Sempre histórico completo (sem filtro de período no gráfico).
- No máximo 6 fatias: até 5 categorias nomeadas (por valor decrescente) + "Outros" agrupando o resto. "Outros" sempre por último, cor cinza neutra (`outlineVariant`), nunca um tom da paleta categórica.
- Fatias e legenda desenhadas na ordem fixa de categoria (`FUEL, MAINTENANCE, OIL_CHANGE, WASH, TIRES, INSURANCE, TAX, DOCUMENTS`), não por valor — só a seleção de quais 5 entram é por valor.
- `GetVehicleEventsTotalUseCase` (usado pelo carrossel "Gasto total") não é alterado — o novo use case é paralelo e independente, mesmo que isso signifique duas buscas paginadas de eventos por carregamento de Home.

---

### Task 1: `SpendBreakdown` — modelo, agregação e use case de eventos

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicleevent/domain/usecase/GetVehicleEventsUseCase.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/home/domain/model/SpendBreakdown.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/home/domain/model/SpendBreakdownTest.kt`

**Interfaces:**
- Produces: `GetVehicleEventsUseCase(vehicleId: Int): AppResult<List<VehicleEvent>>` — consumido pela Task 3 (`HomeViewModel`).
- Produces: `data class SpendBreakdown(totalSpent: Double, slices: List<SpendSlice>)`, `data class SpendSlice(label: String, amount: Double)`, `fun buildSpendBreakdown(fuelSpent: Double, events: List<VehicleEvent>): SpendBreakdown` — consumidos pela Task 3 (`HomeViewModel`) e Task 4 (`SpendBreakdownCard`).

- [ ] **Step 1: Escrever os testes que falham**

Criar `app/src/test/java/com/flowfuel/app/feature/home/domain/model/SpendBreakdownTest.kt`:

```kotlin
package com.flowfuel.app.feature.home.domain.model

import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class SpendBreakdownTest {

    private fun event(category: EventCategory, amount: Double?) = VehicleEvent(
        id = 1,
        vehicleId = 1,
        category = category,
        title = category.label,
        description = null,
        amount = amount,
        eventDate = "2026-01-01",
        odometerKm = null,
        notes = null,
        receiptUrl = null,
        createdAt = null,
        updatedAt = null,
    )

    @Test
    fun `merges FUEL-category events into the same Combustível slice as fuelSpent`() {
        val events = listOf(event(EventCategory.FUEL, 50.0))

        val breakdown = buildSpendBreakdown(fuelSpent = 100.0, events = events)

        assertEquals(150.0, breakdown.totalSpent, 0.001)
        assertEquals(listOf(SpendSlice("Combustível", 150.0)), breakdown.slices)
    }

    @Test
    fun `folds categories beyond the top 5 plus the native Outros category into a single Outros slice`() {
        val events = listOf(
            event(EventCategory.MAINTENANCE, 100.0),
            event(EventCategory.OIL_CHANGE, 90.0),
            event(EventCategory.WASH, 80.0),
            event(EventCategory.TIRES, 70.0),
            event(EventCategory.INSURANCE, 60.0),
            event(EventCategory.TAX, 10.0),
            event(EventCategory.DOCUMENTS, 5.0),
            event(EventCategory.OTHER, 3.0),
        )

        val breakdown = buildSpendBreakdown(fuelSpent = 200.0, events = events)

        // top 5 named: Combustível(200), Manutenção(100), Troca de Óleo(90), Lavagem(80), Pneus(70)
        // resto (Seguro 60 + Imposto 10 + Documentos 5 + Outros nativo 3) = 78 -> 1 fatia "Outros"
        assertEquals(6, breakdown.slices.size)
        assertEquals(SpendSlice("Outros", 78.0), breakdown.slices.last())
        assertEquals(618.0, breakdown.totalSpent, 0.001)
    }

    @Test
    fun `does not fold when there are 5 or fewer categories with spend`() {
        val events = listOf(
            event(EventCategory.MAINTENANCE, 100.0),
            event(EventCategory.OIL_CHANGE, 50.0),
        )

        val breakdown = buildSpendBreakdown(fuelSpent = 200.0, events = events)

        assertEquals(3, breakdown.slices.size)
        assertEquals(
            setOf("Combustível", "Manutenção", "Troca de Óleo"),
            breakdown.slices.map { it.label }.toSet(),
        )
    }

    @Test
    fun `returns a single Combustível slice when there are no events`() {
        val breakdown = buildSpendBreakdown(fuelSpent = 148.42, events = emptyList())

        assertEquals(listOf(SpendSlice("Combustível", 148.42)), breakdown.slices)
        assertEquals(148.42, breakdown.totalSpent, 0.001)
    }
}
```

- [ ] **Step 2: Rodar os testes e confirmar que falham**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.domain.model.SpendBreakdownTest"`

Expected: FAIL na compilação — `unresolved reference: buildSpendBreakdown` (o arquivo `SpendBreakdown.kt` ainda não existe).

- [ ] **Step 3: Implementar `SpendBreakdown.kt`**

Criar `app/src/main/java/com/flowfuel/app/feature/home/domain/model/SpendBreakdown.kt`:

```kotlin
package com.flowfuel.app.feature.home.domain.model

import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent

data class SpendBreakdown(
    val totalSpent: Double,
    /** No máximo 6 fatias: até 5 categorias nomeadas + "Outros" agrupando o resto. */
    val slices: List<SpendSlice>,
)

data class SpendSlice(
    val label: String,
    val amount: Double,
)

private const val MAX_NAMED_SLICES = 5
private const val OTHER_LABEL = "Outros"

/**
 * Funde o gasto com abastecimentos ([fuelSpent]) com eventos manuais de
 * categoria FUEL na mesma fatia "Combustível" (ver
 * docs/superpowers/specs/2026-08-14-spend-breakdown-donut-design.md),
 * agrupa o resto dos eventos por categoria e recolhe tudo além das 5
 * maiores + a categoria nativa "Outros" numa única fatia "Outros" ao final.
 */
fun buildSpendBreakdown(fuelSpent: Double, events: List<VehicleEvent>): SpendBreakdown {
    val amountsByLabel = linkedMapOf<String, Double>()
    amountsByLabel[EventCategory.FUEL.label] = fuelSpent
    for (event in events) {
        val label = event.category.label
        amountsByLabel[label] = (amountsByLabel[label] ?: 0.0) + (event.amount ?: 0.0)
    }

    val otherAmount = amountsByLabel.remove(EventCategory.OTHER.label) ?: 0.0
    val sorted = amountsByLabel.entries.sortedByDescending { it.value }
    val kept = sorted.take(MAX_NAMED_SLICES)
    val foldedTail = sorted.drop(MAX_NAMED_SLICES).sumOf { it.value } + otherAmount

    val slices = kept.map { SpendSlice(it.key, it.value) } +
        if (foldedTail > 0.0) listOf(SpendSlice(OTHER_LABEL, foldedTail)) else emptyList()

    return SpendBreakdown(
        totalSpent = fuelSpent + events.sumOf { it.amount ?: 0.0 },
        slices = slices,
    )
}
```

Criar `app/src/main/java/com/flowfuel/app/feature/vehicleevent/domain/usecase/GetVehicleEventsUseCase.kt`:

```kotlin
package com.flowfuel.app.feature.vehicleevent.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicleevent.domain.VehicleEventRepository
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import javax.inject.Inject

/**
 * Busca todos os eventos de um veículo, percorrendo a paginação no client
 * (mesmo padrão de GetVehicleEventsTotalUseCase) — a API não expõe um
 * endpoint que já retorne a lista completa.
 */
class GetVehicleEventsUseCase @Inject constructor(
    private val repository: VehicleEventRepository,
) {
    suspend operator fun invoke(vehicleId: Int): AppResult<List<VehicleEvent>> {
        val allItems = mutableListOf<VehicleEvent>()
        var page = 0
        while (true) {
            when (val result = repository.getEventsByVehicle(vehicleId, page, category = null)) {
                is AppResult.Success -> {
                    allItems += result.value.items
                    if (!result.value.hasMore) return AppResult.Success(allItems)
                    page++
                }
                is AppResult.Failure -> return result
            }
        }
    }
}
```

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.domain.model.SpendBreakdownTest"`

Expected: PASS — os 4 testes verdes.

- [ ] **Step 5: Compilar o projeto inteiro**

Run: `./gradlew.bat compileDebugKotlin compileDebugUnitTestKotlin -q`

Expected: sem erros.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicleevent/domain/usecase/GetVehicleEventsUseCase.kt \
        app/src/main/java/com/flowfuel/app/feature/home/domain/model/SpendBreakdown.kt \
        app/src/test/java/com/flowfuel/app/feature/home/domain/model/SpendBreakdownTest.kt
git commit -m "feat(home): add spend breakdown aggregation by category"
```

---

### Task 2: Paleta categórica `FFChartColors`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/core/designsystem/theme/Color.kt`

**Interfaces:**
- Produces: `object FFChartColors` com um par `*Light`/`*Dark` por `EventCategory` real (`Fuel`, `Maintenance`, `OilChange`, `Wash`, `Tires`, `Insurance`, `Tax`, `Documents`) + `OtherLight`/`OtherDark` (aliases de `FFColors.OutlineVariantLight`/`Dark`). Consumido pela Task 4 (`SpendBreakdownCard`).

Não há teste automatizado para constantes de cor — a verificação é compilação + inspeção visual na Task 4.

- [ ] **Step 1: Adicionar `FFChartColors`**

Em `app/src/main/java/com/flowfuel/app/core/designsystem/theme/Color.kt`, logo após o fechamento do `object FFExtraColors` (linha 106, antes de `data class FFSemanticColors`):

```kotlin
object FFChartColors {
    // Ordem fixa por categoria (identidade estável, não por rank/valor) —
    // mesma ordem em que as fatias são desenhadas no donut, então pares
    // adjacentes na tela preservam a validação de contraste da sequência.
    val FuelLight = Color(0xFF2A78D6)
    val MaintenanceLight = Color(0xFFEB6834)
    val OilChangeLight = Color(0xFF1BAF7A)
    val WashLight = Color(0xFFEDA100)
    val TiresLight = Color(0xFFE87BA4)
    val InsuranceLight = Color(0xFF008300)
    val TaxLight = Color(0xFF4A3AA7)
    val DocumentsLight = Color(0xFFE34948)
    val OtherLight = FFColors.OutlineVariantLight

    val FuelDark = Color(0xFF3987E5)
    val MaintenanceDark = Color(0xFFD95926)
    val OilChangeDark = Color(0xFF199E70)
    val WashDark = Color(0xFFC98500)
    val TiresDark = Color(0xFFD55181)
    val InsuranceDark = Color(0xFF008300)
    val TaxDark = Color(0xFF9085E9)
    val DocumentsDark = Color(0xFFE66767)
    val OtherDark = FFColors.OutlineVariantDark
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew.bat compileDebugKotlin -q`

Expected: sem erros.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/designsystem/theme/Color.kt
git commit -m "feat(designsystem): add FFChartColors categorical palette"
```

---

### Task 3: Seção `spendBreakdown` no `HomeUiState`/`HomeViewModel`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeUiState.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `GetVehicleEventsUseCase` e `buildSpendBreakdown`/`SpendBreakdown`/`SpendSlice` (Task 1).
- Produces: `HomeScreenState.Success.spendBreakdown: SectionState<SpendBreakdown>`; `HomeViewModel.retrySpendBreakdown(): Unit`. Consumidos pela Task 4 (`HomeScreen.kt`).

- [ ] **Step 1: Escrever os testes que falham**

Em `app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt`, adicionar os imports:

```kotlin
import com.flowfuel.app.feature.home.domain.model.SpendBreakdown
import com.flowfuel.app.feature.home.domain.model.SpendSlice
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsUseCase
```

Adicionar o mock, logo após `getVehicleEventsTotal`:

```kotlin
    private val getVehicleEventsTotal: GetVehicleEventsTotalUseCase = mockk(relaxed = true)
    private val getVehicleEvents: GetVehicleEventsUseCase = mockk()
```

Adicionar um helper de fixture, junto de `testDashboard`:

```kotlin
    private fun testVehicleEvent(category: EventCategory, amount: Double?) = VehicleEvent(
        id = 1,
        vehicleId = 1,
        category = category,
        title = category.label,
        description = null,
        amount = amount,
        eventDate = "2026-01-01",
        odometerKm = null,
        notes = null,
        receiptUrl = null,
        createdAt = null,
        updatedAt = null,
    )
```

Em `setUp()`, adicionar o stub padrão (sem eventos) e passar a nova dependência ao construtor:

```kotlin
        coEvery { getVehicleEvents(any()) } returns AppResult.Success(emptyList())
        viewModel = HomeViewModel(
            getActiveVehicle, getDashboard, logout,
            sessionStore, getVehicles, setActiveVehicle, stationsPrefetcher, getVehicleEventsTotal,
            getVehicleEvents, getFinancialSummary, getRecentActivity, getUpcomingMaintenance, maintenancePrefsStore,
        )
```

Adicionar a nova seção de testes, logo após a seção `// ── Dashboard (gasto combinado vs. combustível) ──`:

```kotlin
    // ── Composição de gastos (spendBreakdown) ──────────────────────────────────

    @Test
    fun `load() populates spendBreakdown from fuelSpent and vehicle events`() = runTest {
        coEvery { getDashboard(any()) } returns AppResult.Success(testDashboard.copy(fuelSpent = 100.0))
        coEvery { getVehicleEvents(any()) } returns AppResult.Success(
            listOf(testVehicleEvent(EventCategory.MAINTENANCE, 40.0)),
        )

        viewModel.load()

        val success = viewModel.state.value.screenState as HomeScreenState.Success
        val breakdown = (success.spendBreakdown as SectionState.Success).value
        assertEquals(140.0, breakdown.totalSpent, 0.001)
        assertEquals(
            listOf(SpendSlice("Combustível", 100.0), SpendSlice("Manutenção", 40.0)),
            breakdown.slices,
        )
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
    fun `retrySpendBreakdown() re-fetches only the spend breakdown section`() = runTest {
        coEvery { getVehicleEvents(any()) } returns AppResult.Failure(AppError.Network)
        viewModel.load()
        coEvery { getVehicleEvents(any()) } returns AppResult.Success(emptyList())

        viewModel.retrySpendBreakdown()

        val success = viewModel.state.value.screenState as HomeScreenState.Success
        assertEquals(
            SectionState.Success(SpendBreakdown(0.0, listOf(SpendSlice("Combustível", 0.0)))),
            success.spendBreakdown,
        )
    }
```

- [ ] **Step 2: Rodar os testes e confirmar que falham**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.HomeViewModelTest"`

Expected: FAIL na compilação — `unresolved reference: getVehicleEvents` / `unresolved reference: spendBreakdown` / construtor de `HomeViewModel` não bate com o número de argumentos.

- [ ] **Step 3: Implementar `HomeUiState.kt` e `HomeViewModel.kt`**

Em `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeUiState.kt`, adicionar o import e o campo:

```kotlin
import com.flowfuel.app.feature.home.domain.model.SpendBreakdown
```

```kotlin
    data class Success(
        val vehicle: ActiveVehicleData,
        val dashboard: DashboardData,
        val financialSummary: SectionState<FinancialSummary> = SectionState.Loading,
        val recentActivity: SectionState<List<VehicleTimelineItem>> = SectionState.Loading,
        val upcomingMaintenance: SectionState<List<UpcomingMaintenanceItem>> = SectionState.Loading,
        val spendBreakdown: SectionState<SpendBreakdown> = SectionState.Loading,
    ) : HomeScreenState
```

Em `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt`, adicionar os imports:

```kotlin
import com.flowfuel.app.feature.home.domain.model.SpendBreakdown
import com.flowfuel.app.feature.home.domain.model.buildSpendBreakdown
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsUseCase
```

Adicionar o parâmetro no construtor, logo após `getVehicleEventsTotal`:

```kotlin
    private val getVehicleEventsTotal: GetVehicleEventsTotalUseCase,
    private val getVehicleEvents: GetVehicleEventsUseCase,
```

Em `load()` (dentro do branch `is AppResult.Success ->`, junto dos outros `launch`), adicionar:

```kotlin
                    launch { loadFinancialSummary(vehicleId) }
                    launch { loadRecentActivity(vehicleId) }
                    launch { loadUpcomingMaintenance(vehicleId, vehicle.currentKm) }
                    launch { loadSpendBreakdown(vehicleId, dashboardResult.value.fuelSpent) }
```

Em `refresh()` (mesmo branch), adicionar a mesma linha:

```kotlin
                    launch { loadFinancialSummary(vehicleId) }
                    launch { loadRecentActivity(vehicleId) }
                    launch { loadUpcomingMaintenance(vehicleId, vehicle.currentKm) }
                    launch { loadSpendBreakdown(vehicleId, dashboardResult.value.fuelSpent) }
```

Adicionar as duas novas funções, próximo de `loadFinancialSummary`/`retryFinancialSummary`:

```kotlin
    private suspend fun loadSpendBreakdown(vehicleId: Int, fuelSpent: Double) {
        val sectionState = when (val result = getVehicleEvents(vehicleId)) {
            is AppResult.Success -> SectionState.Success(buildSpendBreakdown(fuelSpent, result.value))
            is AppResult.Failure -> SectionState.Error(result.error)
        }
        _state.update { state ->
            val success = state.screenState as? HomeScreenState.Success ?: return@update state
            if (success.vehicle.id != vehicleId) return@update state
            state.copy(screenState = success.copy(spendBreakdown = sectionState))
        }
    }

    /** Reexecuta só a composição de gastos, sem recarregar o resto da tela. */
    fun retrySpendBreakdown() {
        val vehicleId = loadedVehicleId ?: return
        val fuelSpent = (_state.value.screenState as? HomeScreenState.Success)?.dashboard?.fuelSpent ?: return
        _state.update { state ->
            val success = state.screenState as? HomeScreenState.Success ?: return@update state
            state.copy(screenState = success.copy(spendBreakdown = SectionState.Loading))
        }
        viewModelScope.launch { loadSpendBreakdown(vehicleId, fuelSpent) }
    }
```

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.HomeViewModelTest"`

Expected: PASS — todos os testes de `HomeViewModelTest` (existentes + os 3 novos) verdes.

- [ ] **Step 5: Compilar o projeto inteiro**

Run: `./gradlew.bat compileDebugKotlin compileDebugUnitTestKotlin -q`

Expected: sem erros — confirma que não sobrou nenhum outro call site de `HomeViewModel(...)` quebrado.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeUiState.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt \
        app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt
git commit -m "feat(home): load spendBreakdown section in HomeViewModel"
```

---

### Task 4: `SpendBreakdownCard` — UI e substituição de "Dica do dia"

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/SpendBreakdownCard.kt`
- Delete: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/InsightCard.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`

**Interfaces:**
- Consumes: `SpendBreakdown`/`SpendSlice` (Task 1), `FFChartColors` (Task 2), `HomeScreenState.Success.spendBreakdown`/`retrySpendBreakdown()` (Task 3), `formatBrl` (já existe em `feature/home/presentation/components`).
- Produces: `SpendBreakdownCard(breakdown: SpendBreakdown, modifier: Modifier = Modifier)`.

Sem teste automatizado — nenhum outro composable da Home tem teste de UI hoje (mesma lacuna de `FinancialSummaryCard`). Verificação por `@Preview` + checagem manual no emulador.

- [ ] **Step 1: Criar `SpendBreakdownCard.kt`**

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
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.theme.FFChartColors
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.home.domain.model.SpendBreakdown
import com.flowfuel.app.feature.home.domain.model.SpendSlice

@Composable
fun SpendBreakdownCard(breakdown: SpendBreakdown, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, title = "Composição de gastos") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpendBreakdownDonut(
                slices = breakdown.slices,
                totalLabel = formatBrl(breakdown.totalSpent),
                colorFor = { label -> sliceColor(label, isDark) },
                modifier = Modifier.size(120.dp),
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
            val strokeWidth = size.minDimension * 0.22f
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
            text = totalLabel,
            style = FFTheme.numericTypography.numericSmall,
            color = MaterialTheme.colorScheme.onSurface,
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
    "Imposto" -> if (isDark) FFChartColors.TaxDark else FFChartColors.TaxLight
    "Documentos" -> if (isDark) FFChartColors.DocumentsDark else FFChartColors.DocumentsLight
    else -> if (isDark) FFChartColors.OtherDark else FFChartColors.OtherLight // "Outros"
}

@Preview(showBackground = true)
@Composable
private fun SpendBreakdownCardPreview() {
    SpendBreakdownCard(
        breakdown = SpendBreakdown(
            totalSpent = 1720.65,
            slices = listOf(
                SpendSlice("Combustível", 890.0),
                SpendSlice("Manutenção", 420.0),
                SpendSlice("Seguro", 300.0),
                SpendSlice("Outros", 110.65),
            ),
        ),
    )
}
```

- [ ] **Step 2: Deletar `InsightCard.kt`**

```bash
rm "app/src/main/java/com/flowfuel/app/feature/home/presentation/components/InsightCard.kt"
```

- [ ] **Step 3: Atualizar `HomeScreen.kt`**

Trocar o import de `InsightCard` pelo de `SpendBreakdownCard`:

```kotlin
import com.flowfuel.app.feature.home.presentation.components.SpendBreakdownCard
```

(remove `import com.flowfuel.app.feature.home.presentation.components.InsightCard`)

Adicionar o import do modelo:

```kotlin
import com.flowfuel.app.feature.home.domain.model.SpendBreakdown
```

No `HomeContent(...)` (chamada dentro de `HomeScreen`, bloco `is HomeScreenState.Success ->`), adicionar dois argumentos:

```kotlin
                    HomeContent(
                        vehicle = s.vehicle,
                        dashboard = s.dashboard,
                        financialSummary = s.financialSummary,
                        recentActivity = s.recentActivity,
                        upcomingMaintenance = s.upcomingMaintenance,
                        spendBreakdown = s.spendBreakdown,
                        onRegisterRefuel = onOpenRefuelSheet,
                        onVehicleClick = viewModel::openVehicleSwitcher,
                        onInfoClick = viewModel::openAboutDialog,
                        onRetryFinancialSummary = viewModel::retryFinancialSummary,
                        onRetryRecentActivity = viewModel::retryRecentActivity,
                        onRetryUpcomingMaintenance = viewModel::retryUpcomingMaintenance,
                        onRetrySpendBreakdown = viewModel::retrySpendBreakdown,
                        onUpcomingEventClick = onUpcomingEventClick,
                        modifier = Modifier.fillMaxSize(),
                    )
```

Na declaração de `HomeContent` (função privada), adicionar os mesmos dois parâmetros:

```kotlin
@Composable
private fun HomeContent(
    vehicle: ActiveVehicleData,
    dashboard: DashboardData,
    financialSummary: SectionState<FinancialSummary>,
    recentActivity: SectionState<List<VehicleTimelineItem>>,
    upcomingMaintenance: SectionState<List<UpcomingMaintenanceItem>>,
    spendBreakdown: SectionState<SpendBreakdown>,
    onRegisterRefuel: () -> Unit,
    onVehicleClick: () -> Unit,
    onInfoClick: () -> Unit,
    onRetryFinancialSummary: () -> Unit,
    onRetryRecentActivity: () -> Unit,
    onRetryUpcomingMaintenance: () -> Unit,
    onRetrySpendBreakdown: () -> Unit,
    onUpcomingEventClick: (UpcomingMaintenanceType) -> Unit,
    modifier: Modifier = Modifier,
) {
```

No corpo do `LazyColumn`, remover a linha `item { InsightCard() }` e, dentro do bloco `if (!isFirstUse) { ... }`, adicionar o novo item como primeiro filho do bloco (mesma posição relativa que "Dica do dia" ocupava antes, agora dentro da guarda):

```kotlin
        if (!isFirstUse) {
            item {
                when (spendBreakdown) {
                    is SectionState.Success -> SpendBreakdownCard(breakdown = spendBreakdown.value)
                    SectionState.Loading -> FFSkeletonBlock(height = 160.dp)
                    is SectionState.Error -> SectionErrorCard(onRetry = onRetrySpendBreakdown)
                }
            }

            item { LastRefuelCard(dashboard = dashboard) }

            item {
                when (recentActivity) {
                    is SectionState.Success -> RecentActivityCard(items = recentActivity.value)
                    SectionState.Loading -> FFSkeletonBlock(height = 160.dp)
                    is SectionState.Error -> SectionErrorCard(onRetry = onRetryRecentActivity)
                }
            }
        }
```

(isso substitui o bloco atual — linha `item { InsightCard() }` some, e o `if (!isFirstUse) { ... }` ganha o novo `item` no topo, antes de `LastRefuelCard`).

- [ ] **Step 4: Compilar**

Run: `./gradlew.bat compileDebugKotlin -q`

Expected: sem erros.

- [ ] **Step 5: Rodar a suíte de testes completa da Home**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.*"`

Expected: PASS.

- [ ] **Step 6: Verificar manualmente no emulador**

Use a skill `run-android-emulator` deste projeto pra buildar e instalar o app debug. Login com a conta de teste QA (`retiko1301@jobraux.com`), veículo Volkswagen Fox (vehicleId=6 — já confirmado ter abastecimento + eventos com custo, então o gráfico tem mais de 1 fatia). Confirmar:
- "Dica do dia" não aparece mais em nenhum lugar da Home
- O card "Composição de gastos" aparece no lugar onde "Dica do dia" estava, com donut + legenda + valor total no centro
- A soma das fatias da legenda bate com o "Gasto total" do carrossel `FinancialSummaryCard`
- Em modo claro e escuro, as cores das fatias continuam distinguíveis e a legenda continua legível
- Card não aparece se o veículo não tiver nenhum abastecimento (testar trocando pra um veículo vazio, se houver algum, ou aceitar a garantia de código já que segue a mesma guarda de `LastRefuelCard`)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/SpendBreakdownCard.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/components/InsightCard.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt
git commit -m "feat(home): replace daily tip card with spend breakdown donut"
```
