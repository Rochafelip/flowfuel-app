# Design: Página "Combustíveis" no carrossel de gasto (Home)

**Data:** 2026-08-14
**Status:** Aprovado

## Contexto

O `FinancialSummaryCard` na Home tem hoje um carrossel de 2 páginas
(`docs/superpowers/specs/2026-07-08-home-financial-summary-carousel-design.md`):
"Gasto do mês" e "Gasto total". "Gasto total" já é abastecimentos + eventos
de manutenção somados em `HomeViewModel.fetchDashboardWithEventsTotal`
(`dashboard.copy(totalSpent = dashboard.totalSpent + eventsTotal)`) — não
existe hoje nenhum lugar que mostre só o gasto com combustível, histórico
completo, sem os eventos misturados.

## Objetivo

Adicionar uma terceira página "Combustíveis" ao mesmo carrossel, mostrando o
gasto acumulado só com abastecimentos (sem eventos), na ordem: **Mês →
Combustíveis → Totais**. Sem chamada de API nova — o dado já está carregado,
só é sobrescrito antes de chegar na tela.

## Escopo

`DashboardData` (novo campo), `HomeRepositoryImpl` (mapeamento),
`FinancialSummaryCard.kt` (terceira página) e `HomeScreen.kt` (novo
parâmetro passado). Nenhuma mudança de API, nenhuma mudança na lógica de
soma de eventos existente.

---

## Design

### `HomeModels.kt` — `DashboardData`

Novo campo `fuelSpent: Double`, com o mesmo valor que `totalSpent` recebe
inicialmente do endpoint de dashboard — **antes** de `HomeViewModel` somar
os eventos:

```kotlin
data class DashboardData(
    ...
    val totalSpent: Double,
    /** Gasto só com abastecimentos (sem eventos) — sempre o valor bruto do
     *  endpoint de dashboard, mesmo depois de [totalSpent] virar o total
     *  combinado em HomeViewModel.fetchDashboardWithEventsTotal. */
    val fuelSpent: Double,
    ...
)
```

### `HomeRepositoryImpl.kt`

Em `buildDashboardData`, popular o novo campo com o mesmo valor bruto do
DTO:

```kotlin
private fun buildDashboardData(dto: DashboardResponseDto, lastRefuel: RefuelItemDto?) = DashboardData(
    totalSpent = dto.totalSpent ?: 0.0,
    fuelSpent  = dto.totalSpent ?: 0.0,
    ...
)
```

### `HomeViewModel.kt` — `fetchDashboardWithEventsTotal`

**Sem mudança de lógica.** `dashboard.copy(totalSpent = dashboard.totalSpent + eventsTotal)`
só sobrescreve `totalSpent`; `fuelSpent` é preservado automaticamente pelo
`copy()`. Isso significa que, na prática, `totalSpent` = combinado e
`fuelSpent` = só combustível, sem duplicar a chamada a
`getVehicleEventsTotal`.

### `FinancialSummaryCard.kt`

`pageCount` 2 → 3, novo parâmetro `fuelSpentLabel: String`. O `if/else`
binário vira `when(page)`:

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
                    Text(text = title, style = MaterialTheme.typography.titleMedium, ...)
                    Row(...) {
                        Text(text = value, style = FFTheme.numericTypography.numericLarge, ...)
                        if (page == 0 && percentDelta != null) {
                            FFTrendBadge(...) // inalterado — só existe na página 0
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

- `PagerDotsIndicator` não muda — já é genérico em `pageCount`.
- Nenhum badge/tendência na página "Combustíveis", pelo mesmo motivo que
  "Gasto total" não tem: não há "mês anterior" para comparar um acumulado.
- Atualizar `FinancialSummaryCardPreview` com o novo parâmetro
  `fuelSpentLabel`.

### `HomeScreen.kt`

No ponto de chamada existente (dentro de `SectionState.Success ->`):

```kotlin
is SectionState.Success -> FinancialSummaryCard(
    currentMonthTotalLabel = formatBrl(financialSummary.value.currentMonthTotal),
    percentDelta = financialSummary.value.percentDelta,
    fuelSpentLabel = formatBrl(dashboard.fuelSpent),
    totalSpentLabel = formatBrl(dashboard.totalSpent),
)
```

## Comportamento de loading / erro

**Sem mudança.** Mesmo comportamento do carrossel atual: o pager inteiro só
existe dentro do branch `SectionState.Success` de `financialSummary` —
loading mostra `FFSkeletonBlock`, erro mostra `SectionErrorCard`. Se
`dashboard` vier com `totalSpent` nulo do backend, `fuelSpent` usa o mesmo
fallback `0.0` que `totalSpent` já usa hoje.

## Testes

- `HomeViewModelTest`: estender a construção de `testDashboard` com
  `fuelSpent`, e adicionar/ajustar um teste que confirme que somar eventos
  (`fetchDashboardWithEventsTotal`) altera `state.dashboard.totalSpent` mas
  **não** altera `state.dashboard.fuelSpent`.
- Verificação manual no emulador (conta de teste QA): abrir a Home de um
  veículo com abastecimentos + pelo menos um evento de manutenção com custo,
  deslizar as 3 páginas e conferir que "Combustíveis" ≠ "Gasto total" (a
  diferença deve bater com o total de eventos daquele veículo).

## Arquivos alterados

| Arquivo | Mudança |
|---------|---------|
| `feature/home/domain/model/HomeModels.kt` | `DashboardData` ganha campo `fuelSpent: Double` |
| `feature/home/data/HomeRepositoryImpl.kt` | `buildDashboardData` popula `fuelSpent` a partir do DTO |
| `feature/home/presentation/components/FinancialSummaryCard.kt` | Novo parâmetro `fuelSpentLabel`; pager de 2 → 3 páginas; reordena para Mês → Combustíveis → Totais; preview atualizado |
| `feature/home/presentation/HomeScreen.kt` | Passa `fuelSpentLabel = formatBrl(dashboard.fuelSpent)` na chamada existente |
| `feature/home/presentation/HomeViewModelTest.kt` | Novo teste garantindo que `fuelSpent` não é afetado pela soma de eventos |

## Fora do escopo

- Filtrar "Combustíveis" por mês (o endpoint de dashboard não tem esse
  filtro — ver `2026-08-14` conversa que descartou essa opção)
- Breakdown por tipo de energia (HYBRID já tem seu próprio `breakdown` no
  DTO, não misturado com esse carrossel)
- Qualquer alteração nos campos `costPerKm`/`lastOdometer` adicionados
  separadamente ao `DashboardResponseDto`/`DashboardData` no mesmo dia
