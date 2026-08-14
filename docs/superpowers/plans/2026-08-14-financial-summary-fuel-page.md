# Página "Combustíveis" no carrossel de gasto (Home) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adicionar uma terceira página "Combustíveis" ao carrossel `FinancialSummaryCard` da Home, mostrando o gasto acumulado só com abastecimentos (sem eventos de manutenção), na ordem Mês → Combustíveis → Totais.

**Architecture:** `DashboardData` ganha um novo campo `fuelSpent` que preserva o `totalSpent` bruto do endpoint de dashboard antes de `HomeViewModel` somar os eventos de manutenção nele. `FinancialSummaryCard` passa de 2 para 3 páginas no `HorizontalPager` existente, usando esse novo valor na página do meio. Nenhuma chamada de API nova.

**Tech Stack:** Kotlin, Jetpack Compose (`HorizontalPager`), MockK + JUnit + Robolectric para testes de ViewModel.

## Global Constraints

- Design aprovado em `docs/superpowers/specs/2026-08-14-financial-summary-fuel-page-design.md` — qualquer desvio deste plano em relação ao spec deve ser sinalizado, não decidido silenciosamente.
- Nenhuma chamada de API nova — `fuelSpent` reaproveita o `totalSpent` já retornado por `GET dashboard/vehicle/{vehicleId}`.
- Ordem fixa das páginas do carrossel: 0 = "Gasto do mês", 1 = "Combustíveis", 2 = "Gasto total". Não reordenar.
- `FFTrendBadge` (badge de tendência) só aparece na página 0 — não adicionar em "Combustíveis" nem em "Gasto total".
- Seguir o estilo de código já usado nos arquivos tocados (indentação alinhada em `DashboardData`/`buildDashboardData`, nomes de teste em crase com espaços como já usado em `HomeViewModelTest.kt`).

---

### Task 1: Campo `fuelSpent` em `DashboardData` + mapeamento no repositório

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt:31-47` (classe `DashboardData`)
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/data/HomeRepositoryImpl.kt:56-75` (`buildDashboardData`)
- Modify: `app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt:88-97` (fixture `testDashboard`) e novo teste
- Modify: `app/src/test/java/com/flowfuel/app/feature/auto/AutoDashboardScreenTest.kt:48-53` (fixture `testDashboard`, só para compilar — este teste não usa `fuelSpent`)

**Interfaces:**
- Produces: `DashboardData.fuelSpent: Double` — gasto acumulado só com abastecimentos, sem eventos. Task 2 consome este campo em `HomeScreen.kt` como `dashboard.fuelSpent`.

- [ ] **Step 1: Escrever o teste que falha**

Em `app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt`, edite a fixture `testDashboard` (linhas 88-97) para incluir o novo campo:

```kotlin
    private val testDashboard = DashboardData(
        averageConsumption = null,
        consumptionUnit = null,
        totalSpent = 0.0,
        fuelSpent = 0.0,
        totalRefuels = 1,
        lastRefuelDate = null,
        lastRefuelEnergyAmount = null,
        lastRefuelAmount = null,
        lastRefuelEnergyUnit = null,
    )
```

Logo após o bloco `tearDown()` (linhas 117-120), antes da seção `// ── Estações (prefetch) ──`, adicione uma nova seção com o teste:

```kotlin
    // ── Dashboard (gasto combinado vs. combustível) ────────────────────────────

    @Test
    fun `fetchDashboardWithEventsTotal combines totalSpent with events but leaves fuelSpent untouched`() = runTest {
        coEvery { getDashboard(any()) } returns AppResult.Success(testDashboard.copy(totalSpent = 200.0, fuelSpent = 200.0))
        coEvery { getVehicleEventsTotal(any()) } returns AppResult.Success(50.0)

        viewModel.load()

        val success = viewModel.state.value.screenState as HomeScreenState.Success
        assertEquals(250.0, success.dashboard.totalSpent, 0.001)
        assertEquals(200.0, success.dashboard.fuelSpent, 0.001)
    }
```

Em `app/src/test/java/com/flowfuel/app/feature/auto/AutoDashboardScreenTest.kt`, edite a fixture `testDashboard` (linhas 48-53) para compilar (este teste não verifica `fuelSpent`, só precisa de um valor válido):

```kotlin
    private val testDashboard = DashboardData(
        averageConsumption = 8.4, consumptionUnit = "km/L",
        totalSpent = 1240.0, fuelSpent = 1240.0, totalRefuels = 5,
        lastRefuelDate = "2026-06-15", lastRefuelEnergyAmount = 42.0,
        lastRefuelAmount = 289.90, lastRefuelEnergyUnit = "L",
    )
```

- [ ] **Step 2: Rodar os testes e confirmar que falham**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.HomeViewModelTest" --tests "com.flowfuel.app.feature.auto.AutoDashboardScreenTest"`

Expected: FAIL na compilação — `unresolved reference: fuelSpent` (o campo ainda não existe em `DashboardData`).

- [ ] **Step 3: Implementar o campo e o mapeamento**

Em `app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt`, dentro de `DashboardData` (linhas 31-47), adicione o campo logo após `totalSpent`:

```kotlin
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
    /** Detalhamento por combustão/elétrico; preenchido apenas para HYBRID. */
    val hybridBreakdown: HybridConsumptionBreakdown? = null,
    /** Odômetro do último abastecimento registrado; null se não houver abastecimentos. */
    val lastOdometer: Int? = null,
    /** Custo médio por km rodado (totalSpent / km rodados no período). */
    val costPerKm: Double? = null,
) {
    val hasRefuels: Boolean get() = totalRefuels > 0
}
```

Em `app/src/main/java/com/flowfuel/app/feature/home/data/HomeRepositoryImpl.kt`, dentro de `buildDashboardData` (linhas 56-75), adicione o mapeamento logo após `totalSpent`:

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
        lastOdometer            = dto.lastOdometer,
        costPerKm               = dto.costPerKm,
        hybridBreakdown         = dto.breakdown?.let { b ->
            HybridConsumptionBreakdown(
                fuelConsumption         = b.fuel?.averageConsumption,
                fuelConsumptionUnit     = b.fuel?.consumptionUnit ?: "km/L",
                electricConsumption     = b.electric?.averageConsumption,
                electricConsumptionUnit = b.electric?.consumptionUnit ?: "km/kWh",
            )
        },
    )
```

**Não altere `fetchDashboardWithEventsTotal` em `HomeViewModel.kt`** — o `.copy(totalSpent = dashboard.totalSpent + eventsTotal)` já existente preserva `fuelSpent` automaticamente, porque `copy()` só sobrescreve o campo citado.

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.HomeViewModelTest" --tests "com.flowfuel.app.feature.auto.AutoDashboardScreenTest"`

Expected: PASS — todos os testes de `HomeViewModelTest` (incluindo o novo) e `AutoDashboardScreenTest` verdes.

- [ ] **Step 5: Compilar o projeto inteiro**

Run: `./gradlew.bat compileDebugKotlin compileDebugUnitTestKotlin -q`

Expected: sem erros — confirma que não sobrou nenhum outro call site de `DashboardData(...)` quebrado.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt \
        app/src/main/java/com/flowfuel/app/feature/home/data/HomeRepositoryImpl.kt \
        app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt \
        app/src/test/java/com/flowfuel/app/feature/auto/AutoDashboardScreenTest.kt
git commit -m "feat(home): preserve fuel-only totalSpent as DashboardData.fuelSpent"
```

---

### Task 2: Terceira página "Combustíveis" no `FinancialSummaryCard`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/FinancialSummaryCard.kt:32-117`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt:213-223`

**Interfaces:**
- Consumes: `DashboardData.fuelSpent: Double` (produzido na Task 1); `formatBrl(Double): String` (já existente em `HomeScreen.kt`, sem mudança de assinatura).
- Produces: `FinancialSummaryCard(currentMonthTotalLabel: String, percentDelta: Double?, fuelSpentLabel: String, totalSpentLabel: String, modifier: Modifier = Modifier)` — novo parâmetro `fuelSpentLabel` inserido antes de `totalSpentLabel`.

Este componente não tem teste automatizado hoje (nenhum arquivo `FinancialSummaryCardTest`) — a verificação é via `@Preview` atualizado e checagem manual no emulador (mesmo padrão já usado quando o carrossel de 2 páginas foi criado em `docs/superpowers/specs/2026-07-08-home-financial-summary-carousel-design.md`).

- [ ] **Step 1: Atualizar `FinancialSummaryCard` para 3 páginas**

Em `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/FinancialSummaryCard.kt`, substitua a função inteira (linhas 32-81):

```kotlin
@Composable
fun FinancialSummaryCard(
    currentMonthTotalLabel: String,
    percentDelta: Double?,
    fuelSpentLabel: String,
    totalSpentLabel: String,
    modifier: Modifier = Modifier,
) {
    val pageCount = 3
    val pagerState = rememberPagerState(pageCount = { pageCount })

    FFCard(modifier = modifier, variant = FFCardVariant.Flat) {
        Column {
            HorizontalPager(state = pagerState) { page ->
                val title = when (page) {
                    0 -> "Gasto do mês"
                    1 -> "Combustíveis"
                    else -> "Gasto total"
                }
                val value = when (page) {
                    0 -> currentMonthTotalLabel
                    1 -> fuelSpentLabel
                    else -> totalSpentLabel
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = FFTheme.spacing.sm),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                    ) {
                        Text(
                            text = value,
                            style = FFTheme.numericTypography.numericLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (page == 0 && percentDelta != null) {
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
            Spacer(Modifier.height(FFTheme.spacing.sm))
            PagerDotsIndicator(pagerState = pagerState, pageCount = pageCount)
        }
    }
}
```

`PagerDotsIndicator` (linhas 83-107) não muda — já é genérico em `pageCount`.

Atualize o preview (linhas 109-117):

```kotlin
@Preview(showBackground = true)
@Composable
private fun FinancialSummaryCardPreview() {
    FinancialSummaryCard(
        currentMonthTotalLabel = "R$ 350,00",
        percentDelta = 12.0,
        fuelSpentLabel = "R$ 9.320,00",
        totalSpentLabel = "R$ 12.480,00",
    )
}
```

- [ ] **Step 2: Passar o novo valor no ponto de chamada**

Em `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`, dentro do bloco `when (financialSummary)` (linhas 213-223):

```kotlin
            item {
                when (financialSummary) {
                    is SectionState.Success -> FinancialSummaryCard(
                        currentMonthTotalLabel = formatBrl(financialSummary.value.currentMonthTotal),
                        percentDelta = financialSummary.value.percentDelta,
                        fuelSpentLabel = formatBrl(dashboard.fuelSpent),
                        totalSpentLabel = formatBrl(dashboard.totalSpent),
                    )
                    SectionState.Loading -> FFSkeletonBlock(height = 96.dp)
                    is SectionState.Error -> SectionErrorCard(onRetry = onRetryFinancialSummary)
                }
            }
```

- [ ] **Step 3: Compilar**

Run: `./gradlew.bat compileDebugKotlin -q`

Expected: sem erros.

- [ ] **Step 4: Rodar a suíte de testes completa da Home**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.*"`

Expected: PASS — nenhum teste existente quebrou com a mudança de assinatura do `FinancialSummaryCard` (nenhum teste chama esse composable diretamente hoje, então isso confirma só que o resto da feature Home continua íntegro).

- [ ] **Step 5: Verificar manualmente no emulador**

Use a skill `run-android-emulator` deste projeto para builda e instalar o app debug. Faça login com a conta de teste QA (`retiko1301@jobraux.com`) e abra a Home de um veículo que tenha **abastecimentos e pelo menos um evento de manutenção com custo** (necessário para diferenciar "Combustíveis" de "Gasto total" — se o veículo não tiver eventos, as duas páginas mostram o mesmo valor e a mudança fica invisível). Deslize as 3 páginas do carrossel e confirme:
- Ordem: "Gasto do mês" → "Combustíveis" → "Gasto total"
- O badge de tendência (ex: "12% vs. mês anterior") só aparece na página "Gasto do mês"
- "Combustíveis" mostra um valor menor ou igual a "Gasto total" (nunca maior)
- Os 3 pontos do indicador embaixo do carrossel acompanham a página atual

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/FinancialSummaryCard.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt
git commit -m "feat(home): add Combustíveis page to the spend carousel"
```
