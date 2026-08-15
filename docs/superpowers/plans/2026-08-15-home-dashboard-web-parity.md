# Home Dashboard Web Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Bring the Android Home dashboard to feature parity with the web dashboard spec ("Tela Dashboard" article): a 3-page spend carousel (Mês/Combustível/Total) instead of 2, a 6-month spending bar chart (currently missing entirely, both as a fixed card and duplicated inside the carousel's month page), `costPerKm` shown on the month page, hidden (not "—") consumption/price tiles for HYBRID vehicles, recent-activity that excludes the refuel already shown in the last-refuel card, and an always-visible "Novo Abastecimento" button at the end of the screen.

**Architecture:** Extends the existing Home feature (Kotlin/Jetpack Compose, Hilt DI, Retrofit+Kotlinx Serialization, MVVM with `StateFlow`). All new data flows through the existing `DashboardApi → HomeRepositoryImpl → DashboardData → HomeViewModel → HomeScreen` pipeline. The backend (`DashboardDTO.java` in the `flowfuel` Spring Boot repo, WSL `~/Projetos/flowfuel`) already returns a `monthlySpending` field that the Android app currently drops silently — no backend changes are needed.

**Tech Stack:** Kotlin, Jetpack Compose (Canvas/Box for the bar chart — no charting library in this project), Hilt, Retrofit + Kotlinx Serialization, JUnit + MockK for unit tests.

**Status: concluído em 2026-08-15**, todas as 8 tasks implementadas, testadas (unit + build) e verificadas manualmente no emulador (Pixel_6, conta QA), commitado direto na `main` (7 commits, `f6c382b`..`67bbaa8`). Desvio pós-verificação: o usuário pediu para remover o gráfico de barras embutido na página "Mês" do carrossel (Task 4), mantendo só o donut ali — o card fixo "Gastos por mês" (Task 3) continua mostrando o gráfico de barras normalmente. Commit `e0d6da6`.

## Global Constraints

- Locale is always `Locale("pt", "BR")` — reuse `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/Formatting.kt`, do not introduce a second formatting utility.
- Currency: `formatBrl` (`NumberFormat.getCurrencyInstance` pt-BR). Integers: `formatInteger`. Never hand-roll `String.format`.
- Do **not** touch formatting correctness, `FuelMetricsCard`, or `LastRefuelDetailCard` — those were already implemented and verified in `docs/superpowers/plans/2026-08-15-home-dashboard-formatting.md` (completed same day, commit `e10ca1a`).
- Follow established ViewModel/State patterns from `[[project_architecture]]`: `MutableStateFlow` + `StateFlow`, `sealed interface` states, Hilt `@Inject constructor`.
- This is a single-module app (`app/`). All Gradle commands run from the repo root using `./gradlew` (Git Bash) — do not invoke `gradlew.bat` from PowerShell mid-plan, stay consistent with `./gradlew ... --console=plain`.
- Test convention in this codebase: only pure functions, use cases, repositories, and ViewModels get JUnit+MockK unit tests. Composables get `@Preview` functions, not Compose UI tests — this plan follows that convention; final visual verification is manual (Task 8).
- Reuse existing design-system components verbatim: `FFCard(variant = FFCardVariant.Flat, title = ...)`, `FFButton(text, onClick, variant)`, `FFTheme.spacing.*`, `FFTheme.semanticColors.brandGreen`, `FFPagerDotsIndicator`.

---

## File Structure

| File | Responsibility |
|---|---|
| `feature/home/data/remote/HomeApi.kt` | Modify — add `MonthlySpendingDto` + `monthlySpending` field to `DashboardResponseDto`. |
| `feature/home/data/HomeRepositoryImpl.kt` | Modify — map `monthlySpending` DTOs into domain `MonthlySpendingEntry` list. |
| `feature/home/domain/model/HomeModels.kt` | Modify — add `MonthlySpendingEntry` data class + `monthlySpending` field on `DashboardData`. |
| `feature/home/presentation/components/Formatting.kt` | Modify — add `formatMonthAbbrev`. |
| `feature/home/presentation/components/MonthlySpendingBarChart.kt` | Create — pure `barHeightFraction` fn + the 6-bar chart composable, reused by both the carousel and the fixed card. |
| `feature/home/presentation/components/MonthlySpendingCard.kt` | Create — fixed "Gastos por mês" card (spec section 03), wraps the bar chart. |
| `feature/home/presentation/components/SpendBreakdownCard.kt` | Modify — 2 pages → 3 pages (Mês / Combustível / Total), add `costPerKm` caption + embedded bar chart on the Mês page. |
| `feature/home/presentation/components/IndicatorsGrid.kt` | Modify — fixed 4-tile grid → variable-length list, so HYBRID vehicles don't show dead "—" tiles. |
| `feature/home/domain/usecase/GetRecentActivityUseCase.kt` | Modify — exclude the most recent refuel (already shown in `LastRefuelDetailCard`). |
| `feature/home/presentation/HomeScreen.kt` | Modify — wire `MonthlySpendingCard`, new `IndicatorsGrid` call, persistent CTA button. |
| `app/src/test/.../home/data/HomeRepositoryImplTest.kt` | Modify — add `monthlySpending` mapping test. |
| `app/src/test/.../home/presentation/components/FormattingTest.kt` | Modify — add `formatMonthAbbrev` tests. |
| `app/src/test/.../home/presentation/components/MonthlySpendingBarChartTest.kt` | Create — tests for `barHeightFraction`. |
| `app/src/test/.../home/domain/usecase/GetRecentActivityUseCaseTest.kt` | Modify — update expectations for the excluded-latest-refuel behavior. |

---

## Task 1: `monthlySpending` data plumbing (DTO → repository → domain model)

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/data/remote/HomeApi.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/data/HomeRepositoryImpl.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/home/data/HomeRepositoryImplTest.kt`

**Interfaces:**
- Produces: `MonthlySpendingEntry(month: String, amount: Double)` in `HomeModels.kt`, and `DashboardData.monthlySpending: List<MonthlySpendingEntry>` (default `emptyList()`) — consumed by Task 2/3/4.

- [x] **Step 1: Write the failing repository test**

Add to `app/src/test/java/com/flowfuel/app/feature/home/data/HomeRepositoryImplTest.kt` (new `@Test`, alongside the existing one — keep imports, just add):

```kotlin
    @Test
    fun `getDashboard mapeia monthlySpending, usando 0 quando amount vier nulo`() = runTest {
        coEvery { dashboardApi.getDashboard(1) } returns DashboardResponseDto(
            totalSpent = 500.0,
            totalRefuels = 3,
            monthlySpending = listOf(
                MonthlySpendingDto(month = "2026-03", amount = 210.0),
                MonthlySpendingDto(month = "2026-04", amount = null),
            ),
        )
        coEvery { historyApi.getRefuelHistory(1, page = 0, size = 1) } returns RefuelHistoryPageDto(content = emptyList())

        val result = repository.getDashboard(1) as AppResult.Success

        assertEquals(2, result.value.monthlySpending.size)
        assertEquals("2026-03", result.value.monthlySpending[0].month)
        assertEquals(210.0, result.value.monthlySpending[0].amount)
        assertEquals(0.0, result.value.monthlySpending[1].amount)
    }
```

Add the missing import at the top of the file: `import com.flowfuel.app.feature.home.data.remote.MonthlySpendingDto`.

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*HomeRepositoryImplTest*" --console=plain`
Expected: FAIL — `MonthlySpendingDto` is unresolved (doesn't exist yet) / `monthlySpending` unresolved on `DashboardResponseDto`.

- [x] **Step 3: Add `MonthlySpendingDto` and the DTO field**

In `app/src/main/java/com/flowfuel/app/feature/home/data/remote/HomeApi.kt`, add above `DashboardResponseDto` (after `HybridBreakdownDto`):

```kotlin
@Serializable
data class MonthlySpendingDto(
    val month: String? = null,
    val amount: Double? = null,
)
```

Add the field to `DashboardResponseDto` (after `costPerKm`):

```kotlin
    val costPerKm: Double? = null,
    val monthlySpending: List<MonthlySpendingDto>? = null,
```

- [x] **Step 4: Add `MonthlySpendingEntry` and the domain field**

In `app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt`, add above `DashboardData`:

```kotlin
/** Um ponto do gráfico de gastos mensais (últimos 6 meses). [month] no formato ISO "yyyy-MM". */
data class MonthlySpendingEntry(
    val month: String,
    val amount: Double,
)
```

Add the field to `DashboardData` (after `priceUnit`):

```kotlin
    val priceUnit: String? = null,
    /** Gasto total (combustível + eventos) dos últimos 6 meses, do mais antigo ao mais recente. Sempre 6 entradas (backend garante). */
    val monthlySpending: List<MonthlySpendingEntry> = emptyList(),
) {
```

(Keep the existing `val hasRefuels: Boolean get() = totalRefuels > 0` line inside the body — just add the new field above the closing `) {`.)

- [x] **Step 5: Map the field in the repository**

In `app/src/main/java/com/flowfuel/app/feature/home/data/HomeRepositoryImpl.kt`, add the import:

```kotlin
import com.flowfuel.app.feature.home.domain.model.MonthlySpendingEntry
```

In `buildDashboardData`, add the mapping (after `priceUnit = dto.priceUnit,`):

```kotlin
        priceUnit               = dto.priceUnit,
        monthlySpending         = dto.monthlySpending?.map {
            MonthlySpendingEntry(month = it.month.orEmpty(), amount = it.amount ?: 0.0)
        } ?: emptyList(),
```

- [x] **Step 6: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*HomeRepositoryImplTest*" --console=plain`
Expected: PASS (2 tests).

- [x] **Step 7: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/data/remote/HomeApi.kt \
        app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt \
        app/src/main/java/com/flowfuel/app/feature/home/data/HomeRepositoryImpl.kt \
        app/src/test/java/com/flowfuel/app/feature/home/data/HomeRepositoryImplTest.kt
git commit -m "feat(home): map monthlySpending from dashboard endpoint"
```

---

## Task 2: Month abbreviation formatter + bar chart math + composable

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/Formatting.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/home/presentation/components/FormattingTest.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/MonthlySpendingBarChart.kt`
- Create: `app/src/test/java/com/flowfuel/app/feature/home/presentation/components/MonthlySpendingBarChartTest.kt`

**Interfaces:**
- Consumes: `MonthlySpendingEntry(month, amount)` from Task 1.
- Produces: `internal fun formatMonthAbbrev(monthIso: String): String` (Formatting.kt), `internal fun barHeightFraction(amount: Double, maxAmount: Double): Float` and `@Composable fun MonthlySpendingBarChart(entries: List<MonthlySpendingEntry>, modifier: Modifier = Modifier)` — consumed by Task 3 (fixed card) and Task 4 (carousel Mês page).

- [x] **Step 1: Write the failing formatter test**

Add to `app/src/test/java/com/flowfuel/app/feature/home/presentation/components/FormattingTest.kt`:

```kotlin
    @Test
    fun `formatMonthAbbrev converte yyyy-MM para abreviacao pt-BR`() {
        assertEquals("jan", formatMonthAbbrev("2026-01"))
        assertEquals("mar", formatMonthAbbrev("2026-03"))
        assertEquals("ago", formatMonthAbbrev("2026-08"))
        assertEquals("dez", formatMonthAbbrev("2026-12"))
    }
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*FormattingTest*" --console=plain`
Expected: FAIL — `formatMonthAbbrev` unresolved reference.

- [x] **Step 3: Implement `formatMonthAbbrev`**

Add to `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/Formatting.kt` (fixed lookup table — deterministic, no ICU/CLDR locale-data dependency across JVM versions):

```kotlin
private val monthAbbreviations = listOf(
    "jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez",
)

/** Converte "yyyy-MM" (ex: "2026-08") para o mês abreviado em pt-BR (ex: "ago"). */
internal fun formatMonthAbbrev(monthIso: String): String {
    val month = monthIso.substring(5, 7).toInt()
    return monthAbbreviations[month - 1]
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*FormattingTest*" --console=plain`
Expected: PASS.

- [x] **Step 5: Write the failing bar-height-fraction test**

Create `app/src/test/java/com/flowfuel/app/feature/home/presentation/components/MonthlySpendingBarChartTest.kt`:

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MonthlySpendingBarChartTest {

    @Test
    fun `barHeightFraction e proporcional ao maior valor da serie`() {
        assertEquals(1.0f, barHeightFraction(amount = 543.0, maxAmount = 543.0), 0.001f)
        assertEquals(0.5f, barHeightFraction(amount = 210.0, maxAmount = 420.0), 0.001f)
    }

    @Test
    fun `barHeightFraction nunca fica abaixo do piso minimo, mesmo para valor 0`() {
        assertEquals(0.04f, barHeightFraction(amount = 0.0, maxAmount = 500.0), 0.001f)
        assertEquals(0.04f, barHeightFraction(amount = 1.0, maxAmount = 500.0), 0.001f)
    }

    @Test
    fun `barHeightFraction e 0 quando toda a serie e zero`() {
        assertEquals(0.0f, barHeightFraction(amount = 0.0, maxAmount = 0.0), 0.001f)
    }
}
```

- [x] **Step 6: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*MonthlySpendingBarChartTest*" --console=plain`
Expected: FAIL — file/function doesn't exist yet.

- [x] **Step 7: Implement `barHeightFraction` and the chart composable**

Create `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/MonthlySpendingBarChart.kt`:

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.home.domain.model.MonthlySpendingEntry
import java.time.YearMonth

private const val MIN_BAR_HEIGHT_FRACTION = 0.04f

/** Altura da barra como fração de [maxAmount], com piso mínimo para nunca "sumir" — exceto quando a série inteira é zero. */
internal fun barHeightFraction(amount: Double, maxAmount: Double): Float {
    if (maxAmount <= 0.0) return 0f
    return (amount / maxAmount).toFloat().coerceAtLeast(MIN_BAR_HEIGHT_FRACTION)
}

/**
 * Gráfico de barras dos últimos 6 meses. Sempre 6 barras, mês mais antigo à
 * esquerda; se todos os valores forem 0, mostra texto no lugar do gráfico.
 * Reutilizado tanto pelo card fixo (seção 03) quanto pela página "Mês" do
 * carrossel de gastos — mesmo conteúdo, de propósito.
 */
@Composable
fun MonthlySpendingBarChart(entries: List<MonthlySpendingEntry>, modifier: Modifier = Modifier) {
    val allZero = entries.all { it.amount <= 0.0 }
    if (entries.isEmpty() || allZero) {
        Text(
            text = "Sem gastos nos últimos 6 meses.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    val maxAmount = entries.maxOf { it.amount }
    val currentMonth = remember(entries) { YearMonth.now().toString() }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
    ) {
        entries.forEach { entry ->
            val isCurrent = entry.month == currentMonth
            val barColor = if (isCurrent) FFTheme.semanticColors.brandGreen else MaterialTheme.colorScheme.surfaceVariant
            val labelColor = if (isCurrent) FFTheme.semanticColors.brandGreen else MaterialTheme.colorScheme.onSurfaceVariant

            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = formatInteger(Math.round(entry.amount).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = labelColor,
                )
                Spacer(Modifier.height(FFTheme.spacing.xs))
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .fillMaxHeight(barHeightFraction(entry.amount, maxAmount))
                            .background(barColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                    )
                }
                Spacer(Modifier.height(FFTheme.spacing.xs))
                Text(
                    text = formatMonthAbbrev(entry.month),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = labelColor,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MonthlySpendingBarChartPreview() {
    MonthlySpendingBarChart(
        entries = listOf(
            MonthlySpendingEntry("2026-03", 210.0),
            MonthlySpendingEntry("2026-04", 380.0),
            MonthlySpendingEntry("2026-05", 190.0),
            MonthlySpendingEntry("2026-06", 410.0),
            MonthlySpendingEntry("2026-07", 330.0),
            MonthlySpendingEntry("2026-08", 543.0),
        ),
        modifier = Modifier.height(140.dp),
    )
}
```

Note: this file uses `remember` — add the import `androidx.compose.runtime.remember`.

- [x] **Step 8: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*MonthlySpendingBarChartTest*" --console=plain`
Expected: PASS (3 tests).

- [x] **Step 9: Compile the module to catch Compose errors the JVM unit test won't**

Run: `./gradlew compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [x] **Step 10: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/Formatting.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/components/MonthlySpendingBarChart.kt \
        app/src/test/java/com/flowfuel/app/feature/home/presentation/components/FormattingTest.kt \
        app/src/test/java/com/flowfuel/app/feature/home/presentation/components/MonthlySpendingBarChartTest.kt
git commit -m "feat(home): add 6-month spending bar chart component"
```

---

## Task 3: Fixed "Gastos por mês" card (spec section 03)

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/MonthlySpendingCard.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`

**Interfaces:**
- Consumes: `MonthlySpendingBarChart` (Task 2), `dashboard.monthlySpending` (Task 1).
- Produces: `@Composable fun MonthlySpendingCard(entries: List<MonthlySpendingEntry>, modifier: Modifier = Modifier)`.

- [x] **Step 1: Create the card**

Create `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/MonthlySpendingCard.kt`:

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.home.domain.model.MonthlySpendingEntry

/** Card fixo (não faz parte do carrossel) com o gráfico de barras dos últimos 6 meses — spec seção 03. */
@Composable
fun MonthlySpendingCard(entries: List<MonthlySpendingEntry>, modifier: Modifier = Modifier) {
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, title = "Gastos por mês") {
        Column {
            MonthlySpendingBarChart(entries = entries, modifier = Modifier.fillMaxWidth().height(120.dp))
            Spacer(Modifier.height(FFTheme.spacing.xs))
            Text(
                text = "Valores em R$",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MonthlySpendingCardPreview() {
    MonthlySpendingCard(
        entries = listOf(
            MonthlySpendingEntry("2026-03", 210.0),
            MonthlySpendingEntry("2026-04", 380.0),
            MonthlySpendingEntry("2026-05", 190.0),
            MonthlySpendingEntry("2026-06", 410.0),
            MonthlySpendingEntry("2026-07", 330.0),
            MonthlySpendingEntry("2026-08", 543.0),
        ),
    )
}
```

- [x] **Step 2: Wire it into `HomeScreen.kt`**

In `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`, add the import:

```kotlin
import com.flowfuel.app.feature.home.presentation.components.MonthlySpendingCard
```

In `HomeContent`, insert a new item right after the `SpendBreakdownCard` item block (still inside the `else` branch, before the `IndicatorsGrid` item):

```kotlin
            item {
                when (spendBreakdown) {
                    is SectionState.Success -> SpendBreakdownCard(overview = spendBreakdown.value)
                    SectionState.Loading -> FFSkeletonBlock(height = 220.dp)
                    is SectionState.Error -> SectionErrorCard(onRetry = onRetrySpendBreakdown)
                }
            }

            item {
                MonthlySpendingCard(entries = dashboard.monthlySpending)
            }

            item {
```

(This step only adds the `MonthlySpendingCard` item block — the `SpendBreakdownCard` call itself is updated with new parameters in Task 4, do not change its arguments here.)

- [x] **Step 3: Compile**

Run: `./gradlew compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [x] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/MonthlySpendingCard.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt
git commit -m "feat(home): add fixed monthly spending card (spec section 03)"
```

---

## Task 4: Extend the spend carousel to 3 pages + costPerKm + embedded bar chart

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/SpendBreakdownCard.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`

**Interfaces:**
- Consumes: `dashboard.fuelSpent: Double`, `dashboard.costPerKm: Double?`, `dashboard.monthlySpending: List<MonthlySpendingEntry>` (all already on `DashboardData` — `fuelSpent`/`costPerKm` since 2026-08-14, `monthlySpending` since Task 1), `MonthlySpendingBarChart` (Task 2).
- Produces: `SpendBreakdownCard(overview, fuelSpent, costPerKm, monthlySpending, modifier)` — new signature, breaking change for the one call site in `HomeScreen.kt`.

- [x] **Step 1: Update `SpendBreakdownCard` signature and page content**

In `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/SpendBreakdownCard.kt`, replace the function signature and pager body:

```kotlin
@Composable
fun SpendBreakdownCard(
    overview: SpendBreakdownOverview,
    fuelSpent: Double,
    costPerKm: Double?,
    monthlySpending: List<MonthlySpendingEntry>,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val pageCount = 3
    val pagerState = rememberPagerState(pageCount = { pageCount })

    FFCard(modifier = modifier, variant = FFCardVariant.Flat) {
        Column {
            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> MonthPage(overview = overview, costPerKm = costPerKm, monthlySpending = monthlySpending, isDark = isDark)
                    1 -> FuelPage(fuelSpent = fuelSpent)
                    else -> TotalPage(breakdown = overview.total, isDark = isDark)
                }
            }
            Spacer(Modifier.height(FFTheme.spacing.sm))
            FFPagerDotsIndicator(pagerState = pagerState, pageCount = pageCount)
        }
    }
}

@Composable
private fun MonthPage(
    overview: SpendBreakdownOverview,
    costPerKm: Double?,
    monthlySpending: List<MonthlySpendingEntry>,
    isDark: Boolean,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Gasto do Mês",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (overview.percentDelta != null) {
                val trend = when {
                    overview.percentDelta > 0.5 -> FFTrend.Up
                    overview.percentDelta < -0.5 -> FFTrend.Down
                    else -> FFTrend.Flat
                }
                FFTrendBadge(
                    trend = trend,
                    label = "${formatPercent(abs(overview.percentDelta))} vs. mês anterior",
                    positiveIsGood = false,
                )
            }
        }
        Spacer(Modifier.height(FFTheme.spacing.xs))
        Text(
            text = formatBrl(overview.monthly.totalSpent),
            style = FFTheme.numericTypography.numericLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (costPerKm != null && costPerKm > 0.0) {
            Text(
                text = "${formatBrl(costPerKm)}/km",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(FFTheme.spacing.sm))
        BreakdownRow(breakdown = overview.monthly, isDark = isDark)
        Spacer(Modifier.height(FFTheme.spacing.md))
        MonthlySpendingBarChart(entries = monthlySpending, modifier = Modifier.fillMaxWidth().height(96.dp))
    }
}

@Composable
private fun FuelPage(fuelSpent: Double) {
    Column {
        Text(
            text = "Gasto de Combustível",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(FFTheme.spacing.xs))
        Text(
            text = formatBrl(fuelSpent),
            style = FFTheme.numericTypography.numericLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TotalPage(breakdown: SpendBreakdown, isDark: Boolean) {
    Column {
        Text(
            text = "Gastos Totais",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(FFTheme.spacing.xs))
        Text(
            text = formatBrl(breakdown.totalSpent),
            style = FFTheme.numericTypography.numericLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(FFTheme.spacing.sm))
        BreakdownRow(breakdown = breakdown, isDark = isDark)
    }
}

@Composable
private fun BreakdownRow(breakdown: SpendBreakdown, isDark: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SpendBreakdownDonut(
            slices = breakdown.slices,
            colorFor = { index, sliceLabel -> sliceColor(index, sliceLabel, isDark) },
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.width(FFTheme.spacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
            for (index in 0 until MAX_LEGEND_ROWS) {
                val slice = breakdown.slices.getOrNull(index)
                if (slice == null) {
                    SpendLegendRow(color = Color.Transparent, label = "", amountLabel = "", percentLabel = "")
                } else {
                    val percent = if (breakdown.totalSpent > 0)
                        slice.amount / breakdown.totalSpent * 100 else 0.0
                    SpendLegendRow(
                        color = sliceColor(index, slice.label, isDark),
                        label = slice.label,
                        amountLabel = formatBrl(slice.amount),
                        percentLabel = formatPercent(percent),
                    )
                }
            }
        }
    }
}
```

This replaces the old inline `HorizontalPager { page -> ... }` body (the `val label = if (page == 0) ...` block through the end of the `Row(verticalAlignment ...)` donut+legend block) with the `when (page)` dispatch above, and factors the shared donut+legend row into `BreakdownRow` (used by both `MonthPage` and `TotalPage`). Everything below it in the file (`SpendBreakdownDonut`, `SpendLegendRow`, `sliceColor`) is unchanged.

Add the new imports at the top of the file:

```kotlin
import androidx.compose.foundation.layout.height
import com.flowfuel.app.feature.home.domain.model.MonthlySpendingEntry
```

- [x] **Step 2: Update the preview to match the new signature**

Replace `SpendBreakdownCardPreview` at the bottom of the file:

```kotlin
@Preview(showBackground = true)
@Composable
private fun SpendBreakdownCardPreview() {
    SpendBreakdownCard(
        overview = SpendBreakdownOverview(
            monthly = SpendBreakdown(
                totalSpent = 542.80,
                slices = listOf(
                    SpendSlice("Combustível", 298.50),
                    SpendSlice("Manutenção", 135.80),
                    SpendSlice("Outros", 108.50),
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
            percentDelta = 12.0,
        ),
        fuelSpent = 298.50,
        costPerKm = 0.68,
        monthlySpending = listOf(
            MonthlySpendingEntry("2026-03", 210.0),
            MonthlySpendingEntry("2026-04", 380.0),
            MonthlySpendingEntry("2026-05", 190.0),
            MonthlySpendingEntry("2026-06", 410.0),
            MonthlySpendingEntry("2026-07", 330.0),
            MonthlySpendingEntry("2026-08", 543.0),
        ),
    )
}
```

- [x] **Step 3: Update the call site in `HomeScreen.kt`**

In `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`, update the `SpendBreakdownCard` call added in Task 3 Step 2:

```kotlin
            item {
                when (spendBreakdown) {
                    is SectionState.Success -> SpendBreakdownCard(
                        overview = spendBreakdown.value,
                        fuelSpent = dashboard.fuelSpent,
                        costPerKm = dashboard.costPerKm,
                        monthlySpending = dashboard.monthlySpending,
                    )
                    SectionState.Loading -> FFSkeletonBlock(height = 220.dp)
                    is SectionState.Error -> SectionErrorCard(onRetry = onRetrySpendBreakdown)
                }
            }
```

- [x] **Step 4: Compile**

Run: `./gradlew compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [x] **Step 5: Run the full Home unit test suite (regression check)**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.home.*" --console=plain`
Expected: PASS, no regressions.

- [x] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/SpendBreakdownCard.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt
git commit -m "feat(home): expand spend carousel to 3 pages (Mês/Combustível/Total), add costPerKm and bar chart to Mês page"
```

---

## Task 5: Hide (not "—") consumption/price tiles for HYBRID or null values

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/IndicatorsGrid.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`

**Interfaces:**
- Produces: `IndicatorsGrid(items: List<IndicatorItem>, modifier: Modifier = Modifier)` — replaces the old 4-fixed-parameter signature.

- [x] **Step 1: Rewrite `IndicatorsGrid` to accept a variable-length list**

Replace the contents of `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/IndicatorsGrid.kt`:

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

/**
 * Grid 2 colunas com número variável de tiles — Consumo médio/Preço médio
 * são omitidos pelo chamador para veículos HYBRID (ou quando o valor vem
 * null), em vez de mostrar um tile com "—" (spec seção 04).
 */
@Composable
fun IndicatorsGrid(items: List<IndicatorItem>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap)) {
                row.forEach { item -> IndicatorCard(item, modifier = Modifier.weight(1f)) }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
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
        items = listOf(
            IndicatorItem("Consumo médio", "12,50", "km/L"),
            IndicatorItem("Preço médio", "R$ 5,89"),
            IndicatorItem("Odômetro", "67.270", "km"),
            IndicatorItem("Último abastecimento", "há 3 dias"),
        ),
    )
}
```

The `Row(...) { row.forEach { ... } }` uses `Modifier.weight(1f)` inside a `RowScope` — this only compiles inside `Row`, which is already the case here (unchanged from the original).

- [x] **Step 2: Update the call site and build the conditional list in `HomeScreen.kt`**

In `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`, inside `HomeContent`, add right after the existing `consumptionValue`/`consumptionUnit` local vals:

```kotlin
    val consumptionValue = dashboard.averageConsumption?.let(::formatDecimal2) ?: "—"
    val isHybrid = vehicle.energyType.equals("HYBRID", ignoreCase = true)
    val indicators = buildList {
        if (!isHybrid && dashboard.averageConsumption != null) {
            add(IndicatorItem("Consumo médio", consumptionValue, consumptionUnit))
        }
        if (!isHybrid && dashboard.averagePricePerUnit != null) {
            add(IndicatorItem("Preço médio", formatBrl(dashboard.averagePricePerUnit), dashboard.priceUnit))
        }
        add(IndicatorItem("Odômetro", formatInteger(vehicle.currentKm), "km"))
        add(IndicatorItem("Último abastecimento", daysSince?.let(::formatLastRefuelLabel) ?: "—"))
    }
```

Replace the `IndicatorsGrid(...)` call:

```kotlin
            item {
                IndicatorsGrid(items = indicators)
            }
```

- [x] **Step 3: Compile**

Run: `./gradlew compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [x] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/IndicatorsGrid.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt
git commit -m "fix(home): hide consumption/price tiles instead of showing dash for HYBRID vehicles"
```

---

## Task 6: Recent activity excludes the most recent refuel

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/domain/usecase/GetRecentActivityUseCase.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/home/domain/usecase/GetRecentActivityUseCaseTest.kt`

**Interfaces:**
- No signature change — `GetRecentActivityUseCase(vehicleId: Int): AppResult<List<VehicleTimelineItem>>` behavior only.

- [x] **Step 1: Write the failing test**

Replace the first test in `app/src/test/java/com/flowfuel/app/feature/home/domain/usecase/GetRecentActivityUseCaseTest.kt`:

```kotlin
    @Test
    fun `exclui o abastecimento mais recente, ja mostrado em LastRefuelDetailCard`() = runTest {
        coEvery { getRefuelHistory(1, 0, 4) } returns AppResult.Success(
            RefuelPage(
                items = listOf(
                    refuel(1, "2026-07-10"), // mais recente — deve ser excluído
                    refuel(2, "2026-07-01"),
                    refuel(3, "2026-06-15"),
                    refuel(4, "2026-06-01"),
                ),
                hasMore = false, currentPage = 0, totalElements = 4,
            )
        )
        coEvery { getVehicleEventsPage(1, 0, null) } returns AppResult.Success(
            PagedVehicleEvents(
                items = listOf(event(1, "2026-07-05"), event(2, "2026-06-20"), event(3, "2026-05-01")),
                currentPage = 0, totalPages = 1, totalElements = 3,
            )
        )

        val timeline = (useCase(1) as AppResult.Success).value

        assertEquals(3, timeline.size)
        assertEquals("2026-07-05", timeline[0].sortDate)
        assertEquals("2026-07-01", timeline[1].sortDate)
        assertEquals("2026-06-20", timeline[2].sortDate)
        assertEquals(VehicleTimelineItem.EventEntry::class, timeline[0]::class)
        assertEquals(VehicleTimelineItem.RefuelEntry::class, timeline[1]::class)
        assertEquals(VehicleTimelineItem.EventEntry::class, timeline[2]::class)
    }
```

Update the second test's stub call too (`getRefuelHistory(1, 0, 3)` → `getRefuelHistory(1, 0, 4)`):

```kotlin
    @Test
    fun `propagates failure from events page`() = runTest {
        coEvery { getRefuelHistory(1, 0, 4) } returns AppResult.Success(
            RefuelPage(items = emptyList(), hasMore = false, currentPage = 0, totalElements = 0)
        )
        coEvery { getVehicleEventsPage(1, 0, null) } returns AppResult.Failure(AppError.Network)

        val result = useCase(1)

        assertEquals(AppError.Network, (result as AppResult.Failure).error)
    }
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*GetRecentActivityUseCaseTest*" --console=plain`
Expected: FAIL — current code requests page size 3, not 4, and doesn't drop the first refuel, so the returned timeline still contains the 2026-07-10 refuel and only has room for 2 events instead of the expected mix.

- [x] **Step 3: Fix the use case**

Replace the body of `app/src/main/java/com/flowfuel/app/feature/home/domain/usecase/GetRecentActivityUseCase.kt`:

```kotlin
package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.usecase.GetRefuelHistoryUseCase
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleTimelineItem
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import javax.inject.Inject

private const val RECENT_ACTIVITY_LIMIT = 3

/**
 * Combina abastecimentos e eventos num único timeline ordenado por data,
 * mesmo padrão de [com.flowfuel.app.feature.vehicleevent.presentation.list.VehicleEventsViewModel.buildTimeline],
 * truncado para os itens mais recentes.
 *
 * O abastecimento mais recente é excluído da mistura: ele já aparece em
 * [com.flowfuel.app.feature.home.presentation.components.LastRefuelDetailCard]
 * (spec seção 06/07), então buscamos um a mais e descartamos o primeiro.
 */
class GetRecentActivityUseCase @Inject constructor(
    private val getRefuelHistory: GetRefuelHistoryUseCase,
    private val getVehicleEventsPage: GetVehicleEventsPageUseCase,
) {
    suspend operator fun invoke(vehicleId: Int): AppResult<List<VehicleTimelineItem>> {
        val refuelsResult = getRefuelHistory(vehicleId, 0, RECENT_ACTIVITY_LIMIT + 1)
        if (refuelsResult is AppResult.Failure) return refuelsResult
        val refuels = (refuelsResult as AppResult.Success).value.items.drop(1)

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

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*GetRecentActivityUseCaseTest*" --console=plain`
Expected: PASS (2 tests).

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/domain/usecase/GetRecentActivityUseCase.kt \
        app/src/test/java/com/flowfuel/app/feature/home/domain/usecase/GetRecentActivityUseCaseTest.kt
git commit -m "fix(home): recent activity excludes latest refuel already shown in LastRefuelDetailCard"
```

---

## Task 7: Persistent "Novo Abastecimento" CTA button

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`

**Interfaces:**
- Consumes: `FFButton(text, onClick, modifier)` from `core/designsystem/components/FFButton.kt` (already exists, no changes needed there).

- [x] **Step 1: Add the button as the last item in `HomeContent`**

In `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`, add the import:

```kotlin
import com.flowfuel.app.core.designsystem.components.FFButton
```

At the end of the `LazyColumn` block in `HomeContent`, after the always-visible `UpcomingEventsSection` item, add:

```kotlin
        item {
            when (upcomingMaintenance) {
                is SectionState.Success -> UpcomingEventsSection(
                    items = upcomingMaintenance.value,
                    onCardClick = onUpcomingEventClick,
                )
                SectionState.Loading -> FFSkeletonBlock(height = 96.dp)
                is SectionState.Error -> SectionErrorCard(onRetry = onRetryUpcomingMaintenance)
            }
        }

        if (!isFirstUse) {
            item {
                FFButton(
                    text = "Novo Abastecimento",
                    onClick = onRegisterRefuel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
```

(The first-use empty state already renders its own CTA via `FFEmptyState(actionText = "Registrar abastecimento", onAction = onRegisterRefuel)`, so this new button is gated on `!isFirstUse` to avoid showing two register-refuel buttons stacked on first use — both use the same `onRegisterRefuel` callback, matching the spec's "é o mesmo botão de ação usado no empty state".)

- [x] **Step 2: Compile**

Run: `./gradlew compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt
git commit -m "feat(home): add persistent Novo Abastecimento button at end of screen"
```

---

## Task 8: Full verification pass

**Files:** none (verification only).

- [x] **Step 1: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL, all tests pass, no regressions outside `feature/home`.

- [x] **Step 2: Full debug build**

Run: `./gradlew assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Manual verification on the emulator**

Use the `run-android-emulator` skill to boot the Pixel_6 AVD, install the debug APK, and launch `MainActivity`. Log in with the QA test account (`retiko1301@jobraux.com` — see `[[project_qa_test_account]]`). Verify, for at least one COMBUSTION/ELECTRIC vehicle and one HYBRID vehicle:

- Spend carousel has 3 dots and swipes through Mês → Combustível → Total; Mês page shows `costPerKm` (if > 0) below the total and a 6-bar chart below the donut; Combustível page shows only the big fuel total, no donut.
- A separate "Gastos por mês" card is always visible below the carousel (not just on carousel page 0), showing the same 6 bars.
- Bar chart: 6 bars oldest→newest, current month bold+green, others gray, value labels with no decimals, month labels abbreviated pt-BR; a vehicle with 6 months of zero spend shows "Sem gastos nos últimos 6 meses." instead.
- For the HYBRID vehicle: the indicator grid shows only "Odômetro" and "Último abastecimento" (2 tiles, not 4 with dashes).
- "Atividade recente" no longer repeats the same refuel shown in "Último abastecimento".
- A "Novo Abastecimento" button is visible and tappable at the very bottom of the screen after scrolling, for a vehicle that already has refuels (not just in the first-use empty state).

- [x] **Step 4: Report results**

Summarize pass/fail for each bullet above; file follow-up notes for anything that doesn't match before considering this plan complete.
