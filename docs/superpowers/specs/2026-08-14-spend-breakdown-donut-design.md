# Design: Gráfico de composição de gastos (donut) na Home

**Data:** 2026-08-14
**Status:** Aprovado

## Contexto

A Home tem hoje um card "Dica do dia" (`InsightCard.kt`) com uma frase
educativa fixa por dia do ano, sem nenhum dado do usuário. O usuário quer
substituí-lo por um gráfico que mostre a composição do gasto do veículo —
quanto foi combustível, quanto foi cada tipo de evento de manutenção.

Não existe hoje nenhum endpoint de "resumo por categoria" no backend — o
único jeito de saber quanto foi gasto em cada `EventCategory`
(`Manutenção`, `Troca de Óleo`, `Lavagem`, `Pneus`, `Seguro`, `Imposto`,
`Documentos`, `Outros`) é buscar a lista completa de eventos do veículo e
agrupar no client, mesmo padrão de paginação já usado em
`GetVehicleEventsTotalUseCase`.

**Detalhe descoberto durante o design:** `EventCategory` tem um valor
`FUEL` ("Combustível") — ou seja, um evento manual pode ser categorizado
como combustível, separado do fluxo normal de abastecimento (`refuels`,
que já alimenta `dashboard.fuelSpent` desde
`2026-08-14-financial-summary-fuel-page-design.md`). Se tratássemos os dois
como fatias diferentes, o gráfico teria duas fatias de "combustível"
confusas. A soma das duas na mesma fatia mantém o total do gráfico sempre
igual ao "Gasto total" do carrossel.

## Objetivo

Mostrar, na Home, um donut chart com a composição do gasto total do
veículo (histórico completo) por categoria, com o valor total no centro e
uma legenda com nome + valor + percentual por fatia. Remover o card "Dica
do dia".

## Escopo

- Novo use case `GetVehicleEventsUseCase` (lista completa paginada de
  eventos do veículo)
- Nova função pura `buildSpendBreakdown` que agrupa/funde/agrupa por
  categoria
- Nova seção independente `spendBreakdown` no `HomeUiState`
  (loading/success/error, como `financialSummary`)
- Novo componente `SpendBreakdownCard` (donut + legenda)
- Nova paleta categórica no design system (`FFChartColors`)
- Remoção de `InsightCard.kt` e do seu uso em `HomeScreen.kt`

Fora de escopo: qualquer endpoint novo no backend, filtro de período
(sempre histórico completo, mesmo escopo de "Gasto total"), qualquer
interação de toque nas fatias (drill-down, tooltip) — só leitura.

---

## Design

### Fonte de dados — `GetVehicleEventsUseCase`

Novo use case em `feature/vehicleevent/domain/usecase/`, ao lado de
`GetVehicleEventsTotalUseCase` (que continua existindo e inalterado — é
usado em `HomeViewModel.fetchDashboardWithEventsTotal` para o "Gasto
total" do carrossel; manter os dois separados evita reabrir código já
testado e em produção, ao custo aceito de uma segunda busca paginada de
eventos por carregamento de Home):

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

### Modelo e função de agrupamento — `SpendBreakdown`

Novo arquivo `feature/home/domain/model/SpendBreakdown.kt`:

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
 * categoria FUEL na mesma fatia "Combustível" (ver contexto do design doc
 * 2026-08-14-spend-breakdown-donut-design.md), agrupa o resto dos eventos
 * por categoria e recolhe tudo além das 5 maiores + a categoria nativa
 * "Outros" numa única fatia "Outros" ao final.
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

Fatias com `amount == 0.0` (categoria sem nenhum gasto) não entram —
`amountsByLabel` só ganha uma entrada por categoria quando ela aparece
(FUEL sempre entra, mesmo que `fuelSpent == 0.0`, porque é seedado
incondicionalmente — se o veículo não tem abastecimento nem evento
nenhum, o gráfico mostra 1 fatia de R$ 0,00; esse caso já é coberto pela
guarda `!isFirstUse` mais abaixo, que evita o card aparecer sem nenhum
abastecimento).

### Cores — `FFChartColors`

O design system (`core/designsystem/theme/Color.kt`) não tem paleta
categórica hoje. Adiciono um objeto novo com 8 tons fixos — um por
`EventCategory` real, na ordem declarada do enum (`FUEL, MAINTENANCE,
OIL_CHANGE, WASH, TIRES, INSURANCE, TAX, DOCUMENTS`) — usando a ordem de
matiz já validada contra deuteranopia/protanopia (ΔE ≥ 8 entre pares
adjacentes, claro e escuro) da referência de paleta categórica que uso
para gráficos:

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

**"Outros" não usa um tom da paleta categórica** — usa a cor neutra
`outlineVariant` já existente, convenção padrão pra um grupo "resto" não
competir visualmente com categorias reais.

**Ordem de exibição:** as fatias do donut e as linhas da legenda são
desenhadas na ordem fixa de categoria (a mesma da tabela acima), **não**
pela ordem decrescente de valor — só a escolha de *quais* 5 entram como
fatia nomeada é por valor (Passo `buildSpendBreakdown`). Isso mantém a cor
de cada categoria sempre igual entre uma visita e outra (a mesma categoria
não muda de cor se ela deixar de ser a maior), e mantém pares adjacentes
na tela como subsequência da ordem já validada. "Outros" sempre por
último, independente do valor.

Um mapa `label -> Color` fica junto do `SpendBreakdownCard` (não no
domínio — cor é decisão de apresentação):

```kotlin
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
```

### Componente — `SpendBreakdownCard`

Novo arquivo `feature/home/presentation/components/SpendBreakdownCard.kt`.
Sem biblioteca de gráficos no projeto — desenho com `Canvas`/`drawArc` do
Compose puro, mesmo espírito "artesanal" do `PagerDotsIndicator` já
existente em `FinancialSummaryCard.kt`.

```kotlin
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
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(amountLabel, style = MaterialTheme.typography.bodySmall)
        Text(percentLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

Cada linha da legenda já é o rótulo direto por fatia (nunca só a cor) —
identidade nunca depende só de cor, requisito padrão de acessibilidade
para qualquer gráfico categórico com 2+ séries.

### `HomeUiState` / `HomeViewModel`

`HomeScreenState.Success` ganha uma seção independente, no mesmo padrão de
`financialSummary`/`recentActivity`/`upcomingMaintenance`:

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

`HomeViewModel` ganha a dependência `getVehicleEvents: GetVehicleEventsUseCase`
(injeção Hilt) e uma função `loadSpendBreakdown(vehicleId)` — mesmo
formato de `loadFinancialSummary`, buscando `dashboard.fuelSpent` do
estado atual + `getVehicleEvents(vehicleId)`, combinando com
`buildSpendBreakdown`. Carregada em `launch { ... }` nos mesmos pontos
onde `loadFinancialSummary` é disparada hoje (carregamento inicial e
pull-to-refresh), e uma função pública `retrySpendBreakdown()` análoga a
`retryFinancialSummary()`.

Como `loadSpendBreakdown` depende de `dashboard.fuelSpent`, ela só pode
rodar depois que o dashboard já carregou — dispara-se depois do
`fetchDashboardWithEventsTotal` ter retornado com sucesso, não em
paralelo com ele (diferente de `financialSummary`, que é independente do
dashboard).

### `HomeScreen.kt`

Remove o `item { InsightCard() }` (linha 237) e o import de `InsightCard`.
No lugar, dentro do bloco `if (!isFirstUse) { ... }` (mesma guarda de
`LastRefuelCard`/`RecentActivityCard`), adiciona:

```kotlin
item {
    when (spendBreakdown) {
        is SectionState.Success -> SpendBreakdownCard(breakdown = spendBreakdown.value)
        SectionState.Loading -> FFSkeletonBlock(height = 160.dp)
        is SectionState.Error -> SectionErrorCard(onRetry = onRetrySpendBreakdown)
    }
}
```

`InsightCard.kt` (e a lista `dailyTips`) é deletado — não é reaproveitado
em nenhum outro lugar do app.

## Comportamento de estados

- **Carregando:** `FFSkeletonBlock`, mesmo padrão das outras seções.
- **Erro:** `SectionErrorCard` com retry, mesmo padrão das outras seções.
- **`isFirstUse` (zero abastecimentos):** card não aparece — mesma guarda
  já usada para `LastRefuelCard`/`RecentActivityCard`. Diferente do
  "Dica do dia" antigo, que aparecia sempre.
- **Só 1 categoria com gasto** (ex: só combustível, zero eventos): donut
  mostra a fatia única (círculo cheio de uma cor) + legenda de 1 linha.
  Não é um caso escondido — é só menos interessante visualmente.

## Testes

- `SpendBreakdownTest` (novo, JUnit puro, sem Robolectric — `buildSpendBreakdown`
  não toca Android): casos cobrindo (1) fusão de `fuelSpent` com evento de
  categoria FUEL na mesma fatia "Combustível", (2) dobra da categoria
  nativa "Outros" com o rabo além das 5 maiores, (3) menos de 5 categorias
  presentes (nenhum fold necessário), (4) todas as categorias zeradas
  exceto combustível.
- `HomeViewModelTest`: estender com casos de sucesso/erro/retry para
  `spendBreakdown`, mesmo padrão dos testes existentes de
  `financialSummary`.
- Verificação manual no emulador (conta de teste QA
  `retiko1301@jobraux.com`, veículo Volkswagen Fox vehicleId=6 — já
  confirmado ter abastecimento + eventos com custo, então a diferença
  entre fatias fica visível): confirmar que a soma das fatias bate com
  "Gasto total" do carrossel, que "Dica do dia" sumiu, e que o card não
  aparece se o veículo não tiver abastecimento.

## Arquivos alterados

| Arquivo | Mudança |
|---------|---------|
| `feature/vehicleevent/domain/usecase/GetVehicleEventsUseCase.kt` | Novo — lista completa paginada de eventos |
| `feature/home/domain/model/SpendBreakdown.kt` | Novo — `SpendBreakdown`, `SpendSlice`, `buildSpendBreakdown` |
| `core/designsystem/theme/Color.kt` | Novo objeto `FFChartColors` (8 tons categóricos + reaproveita `outlineVariant` pra "Outros") |
| `feature/home/presentation/components/SpendBreakdownCard.kt` | Novo — donut (Canvas) + legenda |
| `feature/home/presentation/components/InsightCard.kt` | Deletado |
| `feature/home/presentation/HomeUiState.kt` | `Success` ganha campo `spendBreakdown: SectionState<SpendBreakdown>` |
| `feature/home/presentation/HomeViewModel.kt` | Nova dependência `GetVehicleEventsUseCase`; `loadSpendBreakdown`/`retrySpendBreakdown` |
| `feature/home/presentation/HomeScreen.kt` | Remove `InsightCard`; adiciona `SpendBreakdownCard` dentro do bloco `!isFirstUse` |
| `feature/home/domain/model/SpendBreakdownTest.kt` | Novo — testes de `buildSpendBreakdown` |
| `presentation/HomeViewModelTest.kt` | Novos casos pra seção `spendBreakdown` |

## Fora do escopo

- Endpoint de resumo por categoria no backend (agregação continua 100%
  client-side)
- Filtro de período no gráfico (sempre histórico completo — não filtra
  por mês)
- Interação nas fatias (tap pra detalhar, tooltip, navegação pra lista de
  eventos filtrada por categoria)
- Otimizar a segunda busca paginada de eventos (`GetVehicleEventsUseCase`)
  pra não duplicar o que `GetVehicleEventsTotalUseCase` já busca — aceito
  como redundância de uma chamada a mais por carregamento de Home, não
  vale o risco de mexer em código já em produção pra isso agora
