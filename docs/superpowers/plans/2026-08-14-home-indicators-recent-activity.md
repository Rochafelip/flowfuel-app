# Grid de indicadores (3 itens) + limite de atividade recente (3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remover o mini-card "Último abastecimento" do grid de indicadores (a informação já existe no card completo abaixo) e reduzir "Atividade recente" pra mostrar só os 3 itens mais recentes.

**Architecture:** Ajuste de layout Compose (`IndicatorsGrid`) + mudança de uma constante (`GetRecentActivityUseCase`). Nenhuma mudança de contrato de API, nenhum estado novo.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit + MockK.

## Global Constraints

- Design aprovado em `docs/superpowers/specs/2026-08-14-home-indicators-recent-activity-design.md`.
- Sub-projeto 1 de 2 (sub-projeto 2 — carrossel Mês/Total + paleta — tem spec/plano separado, sem dependência entre os dois).
- Não mexer em `LastRefuelCard` nem em `SpendBreakdownCard`.

---

### Task 1: `IndicatorsGrid` com 3 itens

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/IndicatorsGrid.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt:186,233-239,314-336`

**Interfaces:** Nenhuma — mudança de layout local, sem novos parâmetros ou tipos compartilhados com outras tasks.

Nenhum teste automatizado cobre `IndicatorsGrid` hoje (componente
puramente visual). Verificação por `@Preview` atualizado + checagem
manual no emulador.

- [ ] **Step 1: Atualizar `IndicatorsGrid.kt`**

Substituir o arquivo inteiro:

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

@Composable
fun IndicatorsGrid(
    consumption: IndicatorItem,
    averagePrice: IndicatorItem,
    odometer: IndicatorItem,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap)) {
            IndicatorCard(consumption, modifier = Modifier.weight(1f))
            IndicatorCard(averagePrice, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap)) {
            IndicatorCard(odometer, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f))
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
    )
}
```

- [ ] **Step 2: Atualizar o ponto de chamada em `HomeScreen.kt`**

Remover o argumento `lastRefuel`:

```kotlin
            item {
                val averagePrice = (financialSummary as? SectionState.Success)?.value?.averagePricePerUnit
                IndicatorsGrid(
                    consumption = IndicatorItem("Consumo médio", consumptionValue, consumptionUnit),
                    averagePrice = IndicatorItem("Preço médio", averagePrice?.let(::formatBrl) ?: "—"),
                    odometer = IndicatorItem("Odômetro", formatKm(vehicle.currentKm.toDouble()), "km"),
                )
            }
```

- [ ] **Step 3: Deletar a função órfã `shortDaysSinceLabel`**

Em `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`, remover completamente (era usada só na chamada do Step 2, que não existe mais):

```kotlin
private fun shortDaysSinceLabel(days: Int?): String = when {
    days == null -> "—"
    days == 0 -> "Hoje"
    days == 1 -> "Ontem"
    else -> "Há $days dias"
}
```

`daysSince`/`daysSinceRefuel` (linha 186) **não** mudam — ainda alimentam
`VehicleHeader` (`daysSinceLastRefuel`, linha 202).

- [ ] **Step 4: Compilar**

Run: `./gradlew.bat compileDebugKotlin -q`

Expected: sem erros — confirma que `shortDaysSinceLabel` realmente não tinha nenhum outro uso.

- [ ] **Step 5: Verificar manualmente no emulador**

Use a skill `run-android-emulator` deste projeto pra buildar e instalar o
app debug. Login com a conta de teste QA (`retiko1301@jobraux.com`), abrir
a Home. Confirmar:
- O grid mostra 3 cards (Consumo médio, Preço médio, Odômetro), sem
  "Último abastecimento"
- Os 3 cards têm o mesmo tamanho visual
- A segunda linha do grid tem o card do Odômetro do lado esquerdo e um
  espaço vazio do lado direito (não esticado pra largura cheia)
- O card completo "Último abastecimento" mais abaixo na tela continua
  aparecendo normalmente

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/IndicatorsGrid.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt
git commit -m "feat(home): drop redundant last-refuel tile from indicators grid"
```

---

### Task 2: Limite de atividade recente (4 → 3)

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/domain/usecase/GetRecentActivityUseCase.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/home/domain/usecase/GetRecentActivityUseCaseTest.kt`

**Interfaces:** Nenhuma — `GetRecentActivityUseCase.invoke(vehicleId: Int): AppResult<List<VehicleTimelineItem>>` não muda de assinatura, só o tamanho da lista retornada.

- [ ] **Step 1: Escrever o teste que falha**

Em `app/src/test/java/com/flowfuel/app/feature/home/domain/usecase/GetRecentActivityUseCaseTest.kt`, atualizar o teste `merges refuels and events sorted by date descending, limited to 4` pra esperar 3 itens em vez de 4 (mesma massa de dados, só o limite muda):

```kotlin
    @Test
    fun `merges refuels and events sorted by date descending, limited to 3`() = runTest {
        coEvery { getRefuelHistory(1, 0, 3) } returns AppResult.Success(
            RefuelPage(items = listOf(refuel(1, "2026-07-01"), refuel(2, "2026-06-15")), hasMore = false, currentPage = 0, totalElements = 2)
        )
        coEvery { getVehicleEventsPage(1, 0, null) } returns AppResult.Success(
            PagedVehicleEvents(
                items = listOf(event(1, "2026-07-05"), event(2, "2026-06-01"), event(3, "2026-05-01")),
                currentPage = 0, totalPages = 1, totalElements = 3,
            )
        )

        val timeline = (useCase(1) as AppResult.Success).value

        assertEquals(3, timeline.size)
        assertEquals("2026-07-05", timeline[0].sortDate)
        assertEquals("2026-07-01", timeline[1].sortDate)
        assertEquals("2026-06-15", timeline[2].sortDate)
        assertEquals(VehicleTimelineItem.EventEntry::class, timeline[0]::class)
        assertEquals(VehicleTimelineItem.RefuelEntry::class, timeline[1]::class)
        assertEquals(VehicleTimelineItem.RefuelEntry::class, timeline[2]::class)
    }
```

E atualizar as duas ocorrências de `getRefuelHistory(1, 0, 4)` no teste
`propagates failure from events page` pra `getRefuelHistory(1, 0, 3)`:

```kotlin
    @Test
    fun `propagates failure from events page`() = runTest {
        coEvery { getRefuelHistory(1, 0, 3) } returns AppResult.Success(
            RefuelPage(items = emptyList(), hasMore = false, currentPage = 0, totalElements = 0)
        )
        coEvery { getVehicleEventsPage(1, 0, null) } returns AppResult.Failure(AppError.Network)

        val result = useCase(1)

        assertEquals(AppError.Network, (result as AppResult.Failure).error)
    }
```

- [ ] **Step 2: Rodar os testes e confirmar que falham**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.domain.usecase.GetRecentActivityUseCaseTest"`

Expected: FAIL — os mocks agora esperam `getRefuelHistory(1, 0, 3)`, mas o use case ainda chama com `4` (constante não mudou), então o stub não bate e o teste quebra (`MockKException` de chamada não configurada, ou a asserção de tamanho falha).

- [ ] **Step 3: Implementar**

Em `app/src/main/java/com/flowfuel/app/feature/home/domain/usecase/GetRecentActivityUseCase.kt`:

```kotlin
private const val RECENT_ACTIVITY_LIMIT = 3
```

(única linha alterada — resto do arquivo continua idêntico.)

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.domain.usecase.GetRecentActivityUseCaseTest"`

Expected: PASS — os 2 testes verdes.

- [ ] **Step 5: Rodar a suíte completa da Home e compilar**

Run: `./gradlew.bat compileDebugKotlin compileDebugUnitTestKotlin -q && ./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.*"`

Expected: sem erros, todos os testes passando.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/domain/usecase/GetRecentActivityUseCase.kt \
        app/src/test/java/com/flowfuel/app/feature/home/domain/usecase/GetRecentActivityUseCaseTest.kt
git commit -m "feat(home): limit recent activity to the 3 most recent items"
```
