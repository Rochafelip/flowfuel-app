# Design: Carrossel Mês/Total na composição de gastos + paleta intuitiva

**Data:** 2026-08-14
**Status:** Aprovado

## Contexto

Sub-projeto 2 de uma decomposição maior (ajustes de UX na Home, ver
conversa 2026-08-14; sub-projeto 1 — grid de indicadores + limite de
atividade recente — tem spec separado). O `SpendBreakdownCard` (donut de
composição de gastos, entregue em
`docs/superpowers/specs/2026-08-14-spend-breakdown-donut-design.md`) hoje
só mostra o histórico completo do veículo. O usuário quer ver também a
composição **do mês atual**, num carrossel — mesmo padrão de
`HorizontalPager` já usado no `FinancialSummaryCard`. Também quer uma
paleta de cores mais intuitiva por categoria (a atual foi escolhida só
por contraste/distinguibilidade, sem ligação com o que cada categoria
representa).

## Objetivo

1. `SpendBreakdownCard` vira um carrossel de 2 páginas: "Mês" e "Total".
2. Nova paleta categórica com associação de cor mais intuitiva por
   categoria, validada contra os mesmos critérios de acessibilidade
   (contraste, daltonismo) já usados na paleta atual.

## Escopo

Novo use case de busca (mês por categoria), modelo de domínio, estado do
`HomeViewModel`, `SpendBreakdownCard.kt`, `FFChartColors`, e a promoção do
indicador de pontos do carrossel (hoje privado em `FinancialSummaryCard`)
pra um componente compartilhado do design system, já que agora dois
carrosséis precisam dele.

---

## Design

### Dados — `GetMonthlySpendBreakdownUseCase` (novo)

Mesma janela de datas que `GetFinancialSummaryUseCase.currentMonthTotal`
já usa (`[primeiro dia do mês, hoje]`), mas retornando o detalhamento por
categoria em vez de só a soma. Reaproveita os mesmos use cases de busca
paginada por data (`GetRefuelHistoryUseCase`, `GetVehicleEventsPageUseCase`)
e a função `buildSpendBreakdown` já existente:

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
 * spec do donut original): evitar mexer em código já em produção só por
 * reuso.
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

### Modelo — `SpendBreakdownOverview` (novo, em `SpendBreakdown.kt`)

```kotlin
data class SpendBreakdownOverview(
    val monthly: SpendBreakdown,
    val total: SpendBreakdown,
)
```

`HomeUiState.HomeScreenState.Success.spendBreakdown` passa de
`SectionState<SpendBreakdown>` pra `SectionState<SpendBreakdownOverview>`.

### `HomeViewModel.kt`

Nova dependência `getMonthlySpendBreakdown: GetMonthlySpendBreakdownUseCase`.
`loadSpendBreakdown` busca as duas fontes (eventos completos pro total,
que já existia; + o novo use case pro mês) e só monta `Success` se ambas
derem certo — se qualquer uma falhar, a seção inteira vira `Error` (mesmo
padrão binário de erro que as outras seções já usam, sem estado
"parcial"):

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

`applySpendBreakdown` é um helper novo (extraído do corpo que já existia)
pra não duplicar o bloco `_state.update` nos dois pontos de saída da
função. `retrySpendBreakdown()` não muda de assinatura, só o tipo
genérico interno.

### `FFPagerDotsIndicator` — promovido pro design system

Hoje é uma função privada dentro de `FinancialSummaryCard.kt`. Como o
`SpendBreakdownCard` também vira um carrossel de 2 páginas, extraio pra
`core/designsystem/components/FFPagerDotsIndicator.kt` (código idêntico
ao que já existe, só muda de escopo/nome do arquivo):

```kotlin
package com.flowfuel.app.core.designsystem.components

@Composable
fun FFPagerDotsIndicator(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(pageCount) { index ->
            val active = pagerState.currentPage == index
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (active) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}
```

`FinancialSummaryCard.kt` passa a chamar `FFPagerDotsIndicator` em vez da
versão privada (que é removida do arquivo).

### `SpendBreakdownCard.kt` — vira carrossel de 2 páginas

Mesmo padrão de `HorizontalPager` do `FinancialSummaryCard`: assinatura
muda de `breakdown: SpendBreakdown` pra `overview: SpendBreakdownOverview`;
cada página troca o rótulo ("Mês"/"Total") e qual `SpendBreakdown` é
desenhado (donut + legenda), reaproveitando `SpendBreakdownDonut` e
`SpendLegendRow` exatamente como já existem hoje — só a função que
escolhe qual `breakdown` desenhar muda.

```kotlin
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
```

Caso degenerado aceito sem tratamento especial: se o mês não tiver nenhum
gasto, `breakdown.slices` ainda tem 1 item ("Combustível", R$ 0,00) —
porque `buildSpendBreakdown` sempre semeia essa fatia, mesmo com valor
zero (ver `SpendBreakdown.kt`). O donut renderiza um anel sem nenhum arco
visível (todos os `sweepAngle` ficam 0) e o centro mostra "R$ 0,00" — não
quebra, só fica visualmente vazio. Não vale a complexidade de um estado
vazio dedicado só pra essa página do carrossel.

### Paleta — `FFChartColors` (nova, validada)

Mapeamento intuitivo aprovado: Combustível→laranja, Manutenção→azul-petróleo,
Troca de Óleo→marrom, Lavagem→ciano, Pneus→azul-violeta escuro, Seguro→verde,
Documentos→roxo, Imposto→vermelho-bordô.

**Rodei `scripts/validate_palette.js` (skill `dataviz`) contra essa
paleta** — achou um problema real na primeira tentativa: com Seguro
(verde) e Imposto (vermelho) adjacentes na ordem de exibição, a dupla
falha o teste de separação por daltonismo (deuteranopia ΔE 2–5, bem
abaixo do piso de 8). Fix: **Documentos (roxo) entra entre os dois** na
ordem de exibição — mesma técnica que a paleta de referência da skill já
usa (verde → violeta → vermelho, nunca verde-vermelho direto). Com essa
reordenação, os 5 critérios (banda de luminosidade, piso de croma,
separação CVD, piso de visão normal, contraste) passam nos dois modos
(claro contra `#fcfcfb`, escuro contra o `SurfaceDark` real do app,
`#1E293B`):

```kotlin
object FFChartColors {
    // Ordem fixa por categoria (identidade estável, não por rank/valor) —
    // mesma ordem em que as fatias são desenhadas no donut. A ordem
    // importa pra acessibilidade: Seguro (verde) e Imposto (vermelho) NÃO
    // ficam adjacentes de propósito — Documentos (roxo) fica entre os
    // dois, senão a dupla verde/vermelho falha o teste de daltonismo
    // (confirmado com scripts/validate_palette.js da skill dataviz).
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

`sliceColor()` em `SpendBreakdownCard.kt` continua um `when(label)` — só
os valores de cor mudam, a estrutura da função é idêntica.

### `SpendBreakdown.kt` — `CATEGORY_DISPLAY_ORDER` reordenado

Mesma reordenação da paleta (Documentos antes de Imposto), porque essa é
a lista que define tanto a ordem de exibição das fatias quanto — junto
com `sliceColor()` — a identidade cor-categoria:

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

(troca de posição só entre `EventCategory.TAX` e `EventCategory.DOCUMENTS`
em relação à ordem atual — resto igual.)

## Testes

- `GetMonthlySpendBreakdownUseCaseTest` (novo): mesmo padrão de
  `GetFinancialSummaryUseCaseTest` — verifica a janela de datas
  (`capture` dos argumentos `from`/`to` passados pros mocks), soma de
  abastecimentos do mês na fatia "Combustível", agrupamento de eventos do
  mês por categoria, propagação de falha de qualquer uma das duas fontes.
- `HomeViewModelTest`: estender os casos de `spendBreakdown` existentes
  pra cobrir `SpendBreakdownOverview` (sucesso combinando as duas fontes;
  falha em qualquer uma das duas vira `Error` da seção inteira).
- `SpendBreakdownTest`: adicionar um teste confirmando que a nova ordem
  (`CATEGORY_DISPLAY_ORDER`) coloca Documentos antes de Imposto —
  regressão direta do bug de acessibilidade que motivou a reordenação.
- Verificação manual no emulador: confirmar que o carrossel desliza entre
  "Mês" e "Total", que as cores batem com a paleta nova em modo claro e
  escuro, e que a legenda continua legível.

## Fora do escopo

- Filtro de período customizado (só Mês e Total, como já combinado)
- Otimizar a redundância de busca por data entre
  `GetFinancialSummaryUseCase` e `GetMonthlySpendBreakdownUseCase` — aceito
  como redundância consciente, mesma decisão já tomada antes hoje
- Estado vazio dedicado pra mês sem nenhum gasto (ver seção de design
  acima — aceito o donut degenerado como está)
