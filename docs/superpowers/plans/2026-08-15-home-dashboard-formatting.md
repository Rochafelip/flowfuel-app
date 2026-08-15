# Home Dashboard Formatting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Apply the exact formatting rules used on the web dashboard (currency, integers, 2-decimal numbers, percentages, dates, relative-date labels) to the Android Home dashboard, and add the two cards that exist on web but not yet on mobile (hybrid fuel/electric metrics, last-refuel detail).

**Architecture:** All formatting stays centralized in `feature/home/presentation/components/Formatting.kt` (package-`internal`, shared by every Home composable). The two new cards (`FuelMetricsCard`, `LastRefuelDetailCard`) are plain stateless Composables fed from `DashboardData`, following the existing `FFCard`/`FFStatTile` pattern. The two new data points they need (`priceUnit` on `DashboardData`, and richer per-type fields on `HybridConsumptionBreakdown`) are already returned by the backend DTO (`FuelMetricsDto.priceUnit/averagePrice/totalSpent`, `DashboardResponseDto.priceUnit`) but currently dropped in `HomeRepositoryImpl` — this plan wires them through.

**Tech Stack:** Kotlin, Jetpack Compose, `java.text.NumberFormat`/`java.time`, JUnit4 + MockK + Robolectric (existing test stack, see `HomeViewModelTest.kt`).

## Global Constraints

- Locale for all number/currency/date formatting is `Locale("pt", "BR")` — never hardcode `"R$"`, `.`, or `,` manually; always go through `NumberFormat`/`DateTimeFormatter`.
- Units (`consumptionUnit`, `priceUnit`) always come from the API response fields — never hardcode `"km/L"` or `"R$/L"` as the primary source, only as a `?:` fallback when the backend sends `null` (mirrors existing `consumptionUnit` fallback pattern in `HomeScreen.kt:180-181`).
- All new/changed formatting functions live in `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/Formatting.kt` and are `internal` (package-visible within `com.flowfuel.app.feature.home.presentation.components`), matching the existing `formatBrl`/`formatDate` style.
- Do not touch `feature/auto/dashboard/AutoDashboardScreen.kt` (Android Auto) or `feature/history/**` (History tab) — they have their own local formatting copies and are out of scope.
- Every new/changed `DashboardData` / `HybridConsumptionBreakdown` field must have a safe default (`null`) so existing call sites (`HomeViewModelTest.kt:94-104`) keep compiling unchanged.

---

## File Structure

- Modify `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/Formatting.kt` — add `formatDecimal2`, `formatInteger`, `formatPercent`, `formatLastRefuelLabel`, `formatActivityDate`; fix `formatKm`.
- Create `app/src/test/java/com/flowfuel/app/feature/home/presentation/components/FormattingTest.kt` — pure JUnit tests for every formatter.
- Modify `app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt` — add `priceUnit`, `lastRefuelPricePerUnit` to `DashboardData`; extend `HybridConsumptionBreakdown` with per-type price/spend fields.
- Modify `app/src/main/java/com/flowfuel/app/feature/home/data/HomeRepositoryImpl.kt` — map the new DTO fields through.
- Create `app/src/test/java/com/flowfuel/app/feature/home/data/HomeRepositoryImplTest.kt` — covers the new mapping.
- Modify `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt` — fix consumption/odometer formatting, relabel the "days since refuel" tile to a relative label, wire in the two new cards.
- Modify `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/SpendBreakdownCard.kt` — use the shared percent formatter.
- Modify `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/RecentActivityCard.kt` — use the shared decimal formatter and `formatActivityDate`.
- Create `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/LastRefuelDetailCard.kt`.
- Create `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/FuelMetricsCard.kt`.

---

### Task 1: Shared formatters

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/Formatting.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/home/presentation/components/FormattingTest.kt`

**Interfaces:**
- Produces: `internal fun formatBrl(amount: Double): String` (unchanged), `internal fun formatDate(iso: String): String` (unchanged), `internal fun formatInteger(value: Int): String`, `internal fun formatDecimal2(value: Double): String`, `internal fun formatPercent(value: Double): String`, `internal fun formatLastRefuelLabel(days: Int?): String`, `internal fun formatActivityDate(iso: String): String`.

- [x] **Step 1: Write the failing tests**

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    @Test
    fun `formatBrl formata em pt-BR`() {
        assertEquals("R$ 480,00", formatBrl(480.0))
    }

    @Test
    fun `formatInteger agrupa milhar sem decimais`() {
        assertEquals("50.000", formatInteger(50_000))
        assertEquals("67.270", formatInteger(67_270))
        assertEquals("0", formatInteger(0))
    }

    @Test
    fun `formatDecimal2 usa sempre 2 casas decimais com virgula`() {
        assertEquals("12,50", formatDecimal2(12.5))
        assertEquals("42,30", formatDecimal2(42.3))
        assertEquals("0,00", formatDecimal2(0.0))
    }

    @Test
    fun `formatPercent arredonda para inteiro sem casas decimais`() {
        assertEquals("27%", formatPercent(27.4))
        assertEquals("28%", formatPercent(27.5))
        assertEquals("0%", formatPercent(0.0))
    }

    @Test
    fun `formatLastRefuelLabel cobre os 4 casos`() {
        assertEquals("Nenhum abastecimento ainda", formatLastRefuelLabel(null))
        assertEquals("Hoje", formatLastRefuelLabel(0))
        assertEquals("Hoje", formatLastRefuelLabel(-1))
        assertEquals("Ontem", formatLastRefuelLabel(1))
        assertEquals("Há 5 dias", formatLastRefuelLabel(5))
    }

    @Test
    fun `formatActivityDate lida com timestamp e com data pura`() {
        assertEquals("15/08/2026", formatActivityDate("2026-08-15T10:30:00"))
        assertEquals("15/08/2026", formatActivityDate("2026-08-15"))
    }

    @Test
    fun `formatDate converte iso para dd-mm-yyyy`() {
        assertEquals("15/08/2026", formatDate("2026-08-15T10:30:00"))
    }
}
```

Note: `formatBrl`'s expected string uses ` ` (non-breaking space) between `R$` and the amount — this already matches the current ICU/`NumberFormat` behavior on the project's JVM (verify with Step 2; if your JVM's ICU data produces a regular space instead, adjust the expected literal to match — this test is pinning existing, unchanged `formatBrl` behavior, not changing it).

- [x] **Step 2: Run tests to verify they fail (compile error — missing functions)**

Run: `./gradlew testDebugUnitTest --tests "*FormattingTest*" --console=plain`
Expected: FAIL — compile error, `formatInteger`/`formatDecimal2`/`formatPercent`/`formatLastRefuelLabel`/`formatActivityDate` unresolved.

- [x] **Step 3: Implement the formatters**

Replace the full contents of `Formatting.kt`:

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBr = Locale("pt", "BR")

private val brlFormat: NumberFormat
    get() = NumberFormat.getCurrencyInstance(ptBr)

private val integerFormat: NumberFormat
    get() = NumberFormat.getIntegerInstance(ptBr)

private val decimal2Format: NumberFormat
    get() = NumberFormat.getNumberInstance(ptBr).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBr)

internal fun formatBrl(amount: Double): String = brlFormat.format(amount)

/** Inteiro com separador de milhar, sem casas decimais (ex: od√¥metro). */
internal fun formatInteger(value: Int): String = integerFormat.format(value)

/** Sempre 2 casas decimais, v√≠rgula pt-BR (ex: consumo, litros/kWh). */
internal fun formatDecimal2(value: Double): String = decimal2Format.format(value)

/** Percentual inteiro, sem casas decimais (ex: fatia do gr√°fico de gastos). */
internal fun formatPercent(value: Double): String = "${Math.round(value)}%"

/** Converte uma data ISO-8601 (ex: "2024-01-15T10:30:00") para "15/01/2024". */
internal fun formatDate(iso: String): String {
    val datePart = iso.take(10)
    val parts = datePart.split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else datePart
}

/**
 * Mesma sa√≠da de [formatDate], mas explicitando as duas origens poss√≠veis do
 * backend (timestamp completo vs. data pura) como no formatador equivalente do
 * dashboard web — no Kotlin nenhum dos dois ramos faz convers√£o de fuso, ent√£o
 * n√£o h√° o bug de off-by-one que motiva a distin√ß√£o em JS.
 */
internal fun formatActivityDate(iso: String): String = runCatching {
    if (iso.contains("T")) {
        LocalDateTime.parse(iso.take(19)).format(dateFormatter)
    } else {
        LocalDate.parse(iso.take(10)).format(dateFormatter)
    }
}.getOrDefault(formatDate(iso))

/** Texto relativo ao último abastecimento — [days] vem de dias corridos desde [lastRefuelDate]. */
internal fun formatLastRefuelLabel(days: Int?): String = when {
    days == null -> "Nenhum abastecimento ainda"
    days <= 0 -> "Hoje"
    days == 1 -> "Ontem"
    else -> "Há $days dias"
}
```

- [x] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*FormattingTest*" --console=plain`
Expected: PASS (5 tests). If `formatBrl`'s non-breaking-space assertion fails, replace ` ` in the test with a plain space (`" "`) to match your JVM's actual ICU output, then re-run.

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/Formatting.kt app/src/test/java/com/flowfuel/app/feature/home/presentation/components/FormattingTest.kt
git commit -m "feat(home): add shared decimal/integer/percent/relative-date formatters"
```

---

### Task 2: Extend domain model + repository mapping for price unit and hybrid per-type metrics

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/data/HomeRepositoryImpl.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/home/data/HomeRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `DashboardResponseDto` (`app/src/main/java/com/flowfuel/app/feature/home/data/remote/HomeApi.kt:32-47`, fields `priceUnit: String?`, `breakdown: HybridBreakdownDto?`), `FuelMetricsDto` (`HomeApi.kt:15-23`, fields `totalSpent`, `averagePrice`, `priceUnit`, `averageConsumption`, `consumptionUnit`), `RefuelItemDto` (`app/src/main/java/com/flowfuel/app/feature/history/data/remote/HistoryApi.kt:15-43`, field `pricePerUnit: Double?`).
- Produces: `DashboardData.priceUnit: String?`, `DashboardData.lastRefuelPricePerUnit: Double?`, `HybridConsumptionBreakdown.fuelAveragePrice/fuelPriceUnit/fuelTotalSpent/electricAveragePrice/electricPriceUnit/electricTotalSpent: Double?/String?/Double?`.

- [x] **Step 1: Write the failing test**

Create `app/src/test/java/com/flowfuel/app/feature/home/data/HomeRepositoryImplTest.kt`:

```kotlin
package com.flowfuel.app.feature.home.data

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.data.remote.HistoryApi
import com.flowfuel.app.feature.history.data.remote.RefuelHistoryPageDto
import com.flowfuel.app.feature.history.data.remote.RefuelItemDto
import com.flowfuel.app.feature.home.data.remote.DashboardApi
import com.flowfuel.app.feature.home.data.remote.DashboardResponseDto
import com.flowfuel.app.feature.home.data.remote.FuelMetricsDto
import com.flowfuel.app.feature.home.data.remote.HybridBreakdownDto
import com.flowfuel.app.feature.home.data.remote.RefuelApi
import com.flowfuel.app.feature.vehicle.data.remote.VehicleApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRepositoryImplTest {

    private val vehicleApi: VehicleApi = mockk()
    private val dashboardApi: DashboardApi = mockk()
    private val refuelApi: RefuelApi = mockk()
    private val historyApi: HistoryApi = mockk()

    private val repository = HomeRepositoryImpl(vehicleApi, dashboardApi, refuelApi, historyApi)

    @Test
    fun `getDashboard mapeia priceUnit, preco do ultimo abastecimento e breakdown hibrido completo`() = runTest {
        coEvery { dashboardApi.getDashboard(1) } returns DashboardResponseDto(
            energyType = "HYBRID",
            totalSpent = 500.0,
            totalRefuels = 3,
            priceUnit = "R$/L",
            lastRefuelDate = "2026-08-10",
            lastOdometer = 50_000,
            breakdown = HybridBreakdownDto(
                fuel = FuelMetricsDto(
                    averageConsumption = 12.5,
                    consumptionUnit = "km/L",
                    averagePrice = 5.89,
                    priceUnit = "R$/L",
                    totalSpent = 300.0,
                ),
                electric = FuelMetricsDto(
                    averageConsumption = 6.2,
                    consumptionUnit = "km/kWh",
                    averagePrice = 0.85,
                    priceUnit = "R$/kWh",
                    totalSpent = 200.0,
                ),
            ),
        )
        coEvery { historyApi.getRefuelHistory(1, page = 0, size = 1) } returns RefuelHistoryPageDto(
            content = listOf(
                RefuelItemDto(
                    id = 1,
                    refuelDate = "2026-08-10",
                    energyAmount = 42.3,
                    pricePerUnit = 6.97,
                    totalAmount = 294.83,
                    refuelType = "FUEL",
                ),
            ),
        )

        val result = repository.getDashboard(1) as AppResult.Success

        assertEquals("R$/L", result.value.priceUnit)
        assertEquals(6.97, result.value.lastRefuelPricePerUnit)
        assertEquals(5.89, result.value.hybridBreakdown?.fuelAveragePrice)
        assertEquals("R$/L", result.value.hybridBreakdown?.fuelPriceUnit)
        assertEquals(300.0, result.value.hybridBreakdown?.fuelTotalSpent)
        assertEquals(0.85, result.value.hybridBreakdown?.electricAveragePrice)
        assertEquals("R$/kWh", result.value.hybridBreakdown?.electricPriceUnit)
        assertEquals(200.0, result.value.hybridBreakdown?.electricTotalSpent)
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*HomeRepositoryImplTest*" --console=plain`
Expected: FAIL — compile error (`priceUnit`, `lastRefuelPricePerUnit`, `fuelAveragePrice` etc. don't exist yet on `DashboardData`/`HybridConsumptionBreakdown`).

- [x] **Step 3: Extend the domain model**

In `HomeModels.kt`, replace lines 22-57 (the `HybridConsumptionBreakdown` and `DashboardData` classes):

```kotlin
/** Consumo e gasto separados por modal para veículos HYBRID. */
data class HybridConsumptionBreakdown(
    val fuelConsumption: Double?,
    val fuelConsumptionUnit: String?,
    val fuelAveragePrice: Double?,
    val fuelPriceUnit: String?,
    val fuelTotalSpent: Double?,
    val electricConsumption: Double?,
    val electricConsumptionUnit: String?,
    val electricAveragePrice: Double?,
    val electricPriceUnit: String?,
    val electricTotalSpent: Double?,
)

/** Dados do painel de controle para um veículo. */
data class DashboardData(
    val averageConsumption: Double?,
    /** Unidade de consumo informada pelo backend (ex: "km/L", "km/kWh"); null para HYBRID. */
    val consumptionUnit: String?,
    val totalSpent: Double,
    /** Gasto só com abastecimentos (sem eventos) — sempre o valor bruto do
     *  endpoint de dashboard, mesmo depois de [totalSpent] virar o total
     *  combinado em HomeViewModel.fetchDashboardWithEventsTotal. */
    val fuelSpent: Double,
    val totalRefuels: Int,
    val lastRefuelDate: String?,
    /** Litros ou kWh do último abastecimento — vem de /refuels, não do endpoint de dashboard. */
    val lastRefuelEnergyAmount: Double?,
    val lastRefuelAmount: Double?,
    /** Unidade do último abastecimento (ex: "L", "kWh"), inferida do refuelType/energyType. */
    val lastRefuelEnergyUnit: String?,
    /** Preço por litro/kWh pago no último abastecimento — vem de /refuels. */
    val lastRefuelPricePerUnit: Double? = null,
    /** Detalhamento por combustão/elétrico; preenchido apenas para HYBRID. */
    val hybridBreakdown: HybridConsumptionBreakdown? = null,
    /** Odômetro do último abastecimento registrado; null se não houver abastecimentos. */
    val lastOdometer: Int? = null,
    /** Custo médio por km rodado (totalSpent / km rodados no período). */
    val costPerKm: Double? = null,
    /** Preço médio por litro/kWh em todo o histórico de abastecimentos (não só o mês atual). */
    val averagePricePerUnit: Double? = null,
    /** Unidade do preço médio (ex: "R$/L", "R$/kWh"), informada pelo backend. */
    val priceUnit: String? = null,
) {
    val hasRefuels: Boolean get() = totalRefuels > 0
}
```

- [x] **Step 4: Wire the new fields through the repository**

In `HomeRepositoryImpl.kt`, replace the `buildDashboardData` function (lines 56-77):

```kotlin
    private fun buildDashboardData(dto: DashboardResponseDto, lastRefuel: RefuelItemDto?) = DashboardData(
        averageConsumption     = dto.averageConsumption,
        consumptionUnit        = dto.consumptionUnit,
        totalSpent              = dto.totalSpent ?: 0.0,
        fuelSpent                = dto.totalSpent ?: 0.0,
        totalRefuels            = dto.totalRefuels ?: 0,
        lastRefuelDate          = dto.lastRefuelDate,
        lastRefuelEnergyAmount  = lastRefuel?.energyAmount,
        lastRefuelAmount        = lastRefuel?.totalAmount,
        lastRefuelEnergyUnit    = lastRefuelEnergyUnit(dto.energyType, lastRefuel?.refuelType),
        lastRefuelPricePerUnit  = lastRefuel?.pricePerUnit,
        lastOdometer            = dto.lastOdometer,
        costPerKm               = dto.costPerKm,
        averagePricePerUnit     = dto.averagePrice,
        priceUnit               = dto.priceUnit,
        hybridBreakdown         = dto.breakdown?.let { b ->
            HybridConsumptionBreakdown(
                fuelConsumption         = b.fuel?.averageConsumption,
                fuelConsumptionUnit     = b.fuel?.consumptionUnit ?: "km/L",
                fuelAveragePrice        = b.fuel?.averagePrice,
                fuelPriceUnit           = b.fuel?.priceUnit ?: "R$/L",
                fuelTotalSpent          = b.fuel?.totalSpent,
                electricConsumption     = b.electric?.averageConsumption,
                electricConsumptionUnit = b.electric?.consumptionUnit ?: "km/kWh",
                electricAveragePrice    = b.electric?.averagePrice,
                electricPriceUnit       = b.electric?.priceUnit ?: "R$/kWh",
                electricTotalSpent      = b.electric?.totalSpent,
            )
        },
    )
```

- [x] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*HomeRepositoryImplTest*" --console=plain`
Expected: PASS (1 test).

- [x] **Step 6: Run the existing Home test suite to confirm nothing broke**

Run: `./gradlew testDebugUnitTest --tests "*com.flowfuel.app.feature.home*" --console=plain`
Expected: PASS (all existing Home tests, including `HomeViewModelTest`, still green — the new `DashboardData`/`HybridConsumptionBreakdown` fields all have safe defaults or are supplied via named args, so `HomeViewModelTest.kt:94-104`'s `testDashboard` fixture keeps compiling unchanged).

- [x] **Step 7: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt app/src/main/java/com/flowfuel/app/feature/home/data/HomeRepositoryImpl.kt app/src/test/java/com/flowfuel/app/feature/home/data/HomeRepositoryImplTest.kt
git commit -m "feat(home): map priceUnit and hybrid per-type price/spend fields from the dashboard DTO"
```

---

### Task 3: Fix formatting in HomeScreen.kt (consumption, odometer, last-refuel label)

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`

**Interfaces:**
- Consumes: `formatDecimal2`, `formatInteger`, `formatLastRefuelLabel` from Task 1's `Formatting.kt`.

- [x] **Step 1: Update imports**

In `HomeScreen.kt`, replace line 45 (`import ...formatKm`):

```kotlin
import com.flowfuel.app.feature.home.presentation.components.formatDecimal2
import com.flowfuel.app.feature.home.presentation.components.formatInteger
import com.flowfuel.app.feature.home.presentation.components.formatLastRefuelLabel
```

(Keep the existing `import ...formatBrl` on line 44 as-is.)

- [x] **Step 2: Fix `consumptionValue` (2 decimals, locale comma) — line 182**

Replace:
```kotlin
    val consumptionValue = dashboard.averageConsumption?.let { "%.1f".format(it) } ?: "—"
```
with:
```kotlin
    val consumptionValue = dashboard.averageConsumption?.let(::formatDecimal2) ?: "—"
```

- [x] **Step 3: Fix the odometer tile and relabel the "days since refuel" tile — lines 218-225**

Replace:
```kotlin
            item {
                IndicatorsGrid(
                    consumption = IndicatorItem("Consumo médio", consumptionValue, consumptionUnit),
                    averagePrice = IndicatorItem("Preço médio", dashboard.averagePricePerUnit?.let(::formatBrl) ?: "—"),
                    odometer = IndicatorItem("Odômetro", formatKm(vehicle.currentKm.toDouble()), "km"),
                    daysSinceRefuel = IndicatorItem("Dias sem abastecer", daysSince?.toString() ?: "—", "dias"),
                )
            }
```
with:
```kotlin
            item {
                IndicatorsGrid(
                    consumption = IndicatorItem("Consumo médio", consumptionValue, consumptionUnit),
                    averagePrice = IndicatorItem(
                        "Preço médio",
                        dashboard.averagePricePerUnit?.let(::formatBrl) ?: "—",
                        dashboard.priceUnit,
                    ),
                    odometer = IndicatorItem("Odômetro", formatInteger(vehicle.currentKm), "km"),
                    daysSinceRefuel = IndicatorItem("Último abastecimento", formatLastRefuelLabel(daysSince)),
                )
            }
```

Note: `odometer` keeps reading from `vehicle.currentKm` (an `Int`, never null) rather than switching to `dashboard.lastOdometer` (`Int?`) — this is a pure format fix (bare thousands-grouped integer instead of a spurious ",0" decimal), not a data-source change. `vehicle.currentKm` is non-nullable, so the "—" null case from the spec is unreachable here and intentionally not coded.

- [x] **Step 4: Run a full Gradle compile check**

Run: `./gradlew compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL — confirms `formatKm` (now unused) doesn't break anything and the new imports/calls resolve. `formatKm` in `Formatting.kt` was already replaced by `formatInteger` in Task 1's rewrite of the file, so there's nothing stale to remove here.

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt
git commit -m "fix(home): 2-decimal consumption, integer odometer, relative last-refuel label"
```

---

### Task 4: Centralize percent formatting in SpendBreakdownCard.kt

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/SpendBreakdownCard.kt`

**Interfaces:**
- Consumes: `formatPercent` from Task 1's `Formatting.kt` (same package, no import needed).

- [x] **Step 1: Replace the two inline `"%.0f%%".format(...)` calls**

Line 82 (trend badge label):
```kotlin
                            FFTrendBadge(
                                trend = trend,
                                label = "${formatPercent(abs(overview.percentDelta))} vs. mês anterior",
                                positiveIsGood = false,
                            )
```

Line 114 (legend row percent):
```kotlin
                                    SpendLegendRow(
                                        color = sliceColor(index, slice.label, isDark),
                                        label = slice.label,
                                        amountLabel = formatBrl(slice.amount),
                                        percentLabel = formatPercent(percent),
                                    )
```

- [x] **Step 2: Run a compile check**

Run: `./gradlew compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/SpendBreakdownCard.kt
git commit -m "refactor(home): use shared formatPercent instead of inline %.0f%% formatting"
```

---

### Task 5: Fix RecentActivityCard.kt (locale-safe 2-decimal + formatActivityDate)

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/RecentActivityCard.kt`

**Interfaces:**
- Consumes: `formatDecimal2`, `formatActivityDate` from Task 1's `Formatting.kt` (same package, no import needed).

- [x] **Step 1: Replace the manual `.replace('.', ',')` liters formatter — line 59**

Replace:
```kotlin
            val litersLabel = "%.2f %s".format(item.refuel.energyAmount, unit).replace('.', ',')
```
with:
```kotlin
            val litersLabel = "${formatDecimal2(item.refuel.energyAmount)} $unit"
```

- [x] **Step 2: Use `formatActivityDate` for the row date — line 80**

Replace:
```kotlin
                Text(formatDate(row.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
```
with:
```kotlin
                Text(formatActivityDate(row.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
```

- [x] **Step 3: Run a compile check**

Run: `./gradlew compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [x] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/RecentActivityCard.kt
git commit -m "fix(home): locale-safe 2-decimal liters and formatActivityDate in recent activity"
```

---

### Task 6: LastRefuelDetailCard (new)

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/LastRefuelDetailCard.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`

**Interfaces:**
- Consumes: `DashboardData` fields `lastRefuelDate: String?`, `lastRefuelEnergyAmount: Double?`, `lastRefuelAmount: Double?`, `lastRefuelEnergyUnit: String?`, `lastRefuelPricePerUnit: Double?` (Task 2); `formatBrl`, `formatDate`, `formatDecimal2` from `Formatting.kt` (Task 1).
- Produces: `@Composable fun LastRefuelDetailCard(dashboard: DashboardData, modifier: Modifier = Modifier)`.

- [x] **Step 1: Create the card**

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.home.domain.model.DashboardData

@Composable
fun LastRefuelDetailCard(dashboard: DashboardData, modifier: Modifier = Modifier) {
    val unit = dashboard.lastRefuelEnergyUnit ?: "L"
    val isElectric = unit.equals("kWh", ignoreCase = true)

    FFCard(modifier = modifier, variant = FFCardVariant.Flat, title = "Último abastecimento") {
        Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
            DetailRow("Data", dashboard.lastRefuelDate?.let(::formatDate) ?: "—")
            DetailRow(
                if (isElectric) "Energia" else "Litros",
                dashboard.lastRefuelEnergyAmount?.let { "${formatDecimal2(it)} $unit" } ?: "—",
            )
            DetailRow("Valor pago", dashboard.lastRefuelAmount?.let(::formatBrl) ?: "—")
            DetailRow(
                "Preço por unidade",
                dashboard.lastRefuelPricePerUnit?.let { "${formatBrl(it)}/$unit" } ?: "—",
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Preview(showBackground = true)
@Composable
private fun LastRefuelDetailCardPreview() {
    LastRefuelDetailCard(
        dashboard = DashboardData(
            averageConsumption = 12.5,
            consumptionUnit = "km/L",
            totalSpent = 1200.0,
            fuelSpent = 1200.0,
            totalRefuels = 5,
            lastRefuelDate = "2026-08-15",
            lastRefuelEnergyAmount = 42.3,
            lastRefuelAmount = 294.83,
            lastRefuelEnergyUnit = "L",
            lastRefuelPricePerUnit = 6.97,
        ),
    )
}
```

- [x] **Step 2: Wire it into HomeScreen.kt**

Add import (alongside the other component imports, e.g. after line 40's `RecentActivityCard` import):
```kotlin
import com.flowfuel.app.feature.home.presentation.components.LastRefuelDetailCard
```

In `HomeContent`, insert a new `item {}` right after the `IndicatorsGrid` item (after the block ending at line 225, before the `RecentActivityCard` item at line 227) — only when there's a last refuel to show:
```kotlin
            if (dashboard.lastRefuelDate != null) {
                item {
                    LastRefuelDetailCard(dashboard = dashboard)
                }
            }
```

- [x] **Step 3: Run a compile check**

Run: `./gradlew compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [x] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/LastRefuelDetailCard.kt app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt
git commit -m "feat(home): add LastRefuelDetailCard showing date, energy, amount paid and price per unit"
```

---

### Task 7: FuelMetricsCard for hybrid vehicles (new)

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/FuelMetricsCard.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`

**Interfaces:**
- Consumes: `HybridConsumptionBreakdown` (Task 2) fields `fuelConsumption/fuelConsumptionUnit/fuelAveragePrice/fuelPriceUnit/fuelTotalSpent` and the `electric*` equivalents; `formatBrl`, `formatDecimal2` from `Formatting.kt` (Task 1).
- Produces: `@Composable fun FuelMetricsCard(breakdown: HybridConsumptionBreakdown, modifier: Modifier = Modifier)`.

- [x] **Step 1: Create the card**

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.home.domain.model.HybridConsumptionBreakdown

@Composable
fun FuelMetricsCard(breakdown: HybridConsumptionBreakdown, modifier: Modifier = Modifier) {
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, title = "Combustível x Elétrico") {
        Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md)) {
            FuelMetricsBlock(
                icon = Icons.Default.LocalGasStation,
                label = "Combustível",
                consumption = breakdown.fuelConsumption,
                consumptionUnit = breakdown.fuelConsumptionUnit ?: "km/L",
                averagePrice = breakdown.fuelAveragePrice,
                priceUnit = breakdown.fuelPriceUnit ?: "R$/L",
                totalSpent = breakdown.fuelTotalSpent,
            )
            FuelMetricsBlock(
                icon = Icons.Default.Bolt,
                label = "Elétrico",
                consumption = breakdown.electricConsumption,
                consumptionUnit = breakdown.electricConsumptionUnit ?: "km/kWh",
                averagePrice = breakdown.electricAveragePrice,
                priceUnit = breakdown.electricPriceUnit ?: "R$/kWh",
                totalSpent = breakdown.electricTotalSpent,
            )
        }
    }
}

@Composable
private fun FuelMetricsBlock(
    icon: ImageVector,
    label: String,
    consumption: Double?,
    consumptionUnit: String,
    averagePrice: Double?,
    priceUnit: String,
    totalSpent: Double?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
        Row {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MetricRow("Consumo médio", consumption?.let { "${formatDecimal2(it)} $consumptionUnit" } ?: "—")
        MetricRow("Preço médio", averagePrice?.let { "${formatBrl(it)} $priceUnit" } ?: "—")
        MetricRow("Total gasto", totalSpent?.let(::formatBrl) ?: "—")
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Preview(showBackground = true)
@Composable
private fun FuelMetricsCardPreview() {
    FuelMetricsCard(
        breakdown = HybridConsumptionBreakdown(
            fuelConsumption = 12.5,
            fuelConsumptionUnit = "km/L",
            fuelAveragePrice = 5.89,
            fuelPriceUnit = "R$/L",
            fuelTotalSpent = 890.0,
            electricConsumption = 6.2,
            electricConsumptionUnit = "km/kWh",
            electricAveragePrice = 0.85,
            electricPriceUnit = "R$/kWh",
            electricTotalSpent = 210.0,
        ),
    )
}
```

- [x] **Step 2: Wire it into HomeScreen.kt**

Add import:
```kotlin
import com.flowfuel.app.feature.home.presentation.components.FuelMetricsCard
```

In `HomeContent`, insert a new `item {}` right after the `IndicatorsGrid` item and before the `LastRefuelDetailCard` item added in Task 6 — only for hybrid vehicles with breakdown data:
```kotlin
            dashboard.hybridBreakdown?.let { breakdown ->
                item {
                    FuelMetricsCard(breakdown = breakdown)
                }
            }
```

Final item order inside the non-`isFirstUse` branch of `HomeContent` is: `SpendBreakdownCard` → `IndicatorsGrid` → `FuelMetricsCard` (hybrid only) → `LastRefuelDetailCard` (when there's a last refuel) → `RecentActivityCard`.

- [x] **Step 3: Run a compile check**

Run: `./gradlew compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [x] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/FuelMetricsCard.kt app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt
git commit -m "feat(home): add FuelMetricsCard showing fuel vs electric consumption/price/spend for hybrids"
```

---

### Task 8: Full verification pass

**Files:** none (verification only).

- [x] **Step 1: Run the whole Home test suite**

Run: `./gradlew testDebugUnitTest --tests "*com.flowfuel.app.feature.home*" --console=plain`
Expected: PASS — all tests including the two new files from Tasks 1 and 2.

- [x] **Step 2: Full debug compile**

Run: `./gradlew compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Manual check on the emulator**

Use the `run-android-emulator` skill to launch the app against the QA test account (see memory `project_qa_test_account`), open the Home tab, and visually confirm:
- Consumo médio shows 2 decimals (e.g. "12,50 km/L").
- Odômetro shows a bare thousands-grouped integer (e.g. "67.270 km", no ",0").
- The former "Dias sem abastecer" tile now reads "Hoje" / "Ontem" / "Há N dias" / "Nenhum abastecimento ainda".
- Preço médio shows a unit suffix from the API when present.
- A new "Último abastecimento" detail card appears below the indicators grid (when there's at least one refuel).
- For a HYBRID vehicle (switch to one via the vehicle switcher, or note if the QA account has none — in that case just confirm no crash/blank card for non-hybrid vehicles, since `dashboard.hybridBreakdown` is `null` and the card is skipped), the "Combustível x Elétrico" card appears with both blocks populated.
- Recent activity dates and liters render correctly (no `.` where a `,` is expected).

No commit for this task — it's verification only.
