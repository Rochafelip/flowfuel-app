# Design: Carrossel no card "Gasto do mês" (Home)

**Data:** 2026-07-08
**Status:** Aprovado

## Contexto

O card `FinancialSummaryCard` na Home mostra hoje apenas o gasto do mês
atual (com badge de tendência vs. mês anterior). O `DashboardData` que a
Home já carrega traz `totalSpent` — o gasto total do veículo (abastecimentos
+ eventos de manutenção, somados em
`HomeViewModel.fetchDashboardWithEventsTotal`) — mas esse valor nunca é
exibido em nenhum lugar da tela.

## Objetivo

Deixar o usuário deslizar, dentro do mesmo card, entre "Gasto do mês" (como
hoje) e "Gasto total" (histórico completo do veículo), sem precisar de
nenhuma chamada de API nova — o dado já está carregado.

## Escopo

Apenas `FinancialSummaryCard.kt` (novo `HorizontalPager` interno) e o ponto
de chamada em `HomeScreen.kt` (passar `dashboard.totalSpent` como novo
parâmetro). Nenhuma mudança em `HomeViewModel`, camada de dados, API ou
outros cards da Home.

---

## Design

### `FinancialSummaryCard.kt`

Passa a receber um valor adicional e a renderizar duas páginas via
`HorizontalPager` (mesma API usada em `OnboardingScreen`), com um indicador
de pontos (dots) abaixo do conteúdo:

```kotlin
@Composable
fun FinancialSummaryCard(
    currentMonthTotalLabel: String,
    percentDelta: Double?,
    totalSpentLabel: String,
    modifier: Modifier = Modifier,
) {
    val pages = 2
    val pagerState = rememberPagerState(pageCount = { pages })

    FFCard(modifier = modifier, variant = FFCardVariant.Flat) {
        Column {
            HorizontalPager(state = pagerState) { page ->
                Column {
                    Text(
                        text = if (page == 0) "Gasto do mês" else "Gasto total",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, ...) {
                        Text(
                            text = if (page == 0) currentMonthTotalLabel else totalSpentLabel,
                            style = FFTheme.numericTypography.numericLarge,
                        )
                        if (page == 0 && percentDelta != null) {
                            FFTrendBadge(...) // igual hoje, só aparece na página 0
                        }
                    }
                }
            }
            Spacer(Modifier.height(FFTheme.spacing.sm))
            PagerDotsIndicator(pagerState = pagerState, pageCount = pages)
        }
    }
}
```

- Título e valor mudam juntos por página, dentro do próprio `HorizontalPager`
  (não usa mais o parâmetro `title` do `FFCard` — cada página desenha o
  título que lhe cabe).
- `FFTrendBadge` (tendência vs. mês anterior) só existe na página 0 — não faz
  sentido comparar um total acumulado com "mês anterior".
- Indicador de pontos: componente pequeno e local (`Box` com `CircleShape`,
  ~6-8dp), ponto ativo em `MaterialTheme.colorScheme.primary`, inativo em
  `colorScheme.outlineVariant`. Não precisa virar componente de design
  system agora — só este card usa.

### `HomeScreen.kt`

No ponto onde `FinancialSummaryCard` é chamado (dentro do `when
(financialSummary) { is SectionState.Success -> ... }`), passar o novo
parâmetro:

```kotlin
is SectionState.Success -> FinancialSummaryCard(
    currentMonthTotalLabel = formatBrl(financialSummary.value.currentMonthTotal),
    percentDelta = financialSummary.value.percentDelta,
    totalSpentLabel = formatBrl(dashboard.totalSpent),
)
```

`dashboard` já está disponível nesse escopo (`HomeContent` recebe
`dashboard: DashboardData` como parâmetro).

## Comportamento de loading / erro

**Sem mudança.** O carrossel só existe dentro do branch
`SectionState.Success` de `financialSummary` — loading continua mostrando
`FFSkeletonBlock`, erro continua mostrando `SectionErrorCard`. Isso significa
que, tecnicamente, a página "Gasto total" fica indisponível se
`financialSummary` falhar mesmo que `dashboard` tenha carregado — aceitável,
já que hoje o card inteiro já depende de `financialSummary` carregar.

## Arquivos alterados

| Arquivo | Mudança |
|---------|---------|
| `feature/home/presentation/components/FinancialSummaryCard.kt` | Novo parâmetro `totalSpentLabel`; conteúdo vira `HorizontalPager` de 2 páginas com indicador de pontos |
| `feature/home/presentation/HomeScreen.kt` | Passar `dashboard.totalSpent` formatado para `FinancialSummaryCard` |

## Fora do escopo

- Histórico de meses anteriores (só "mês atual" e "total geral", sem meses
  intermediários)
- Breakdown por categoria (combustível vs. manutenção)
- Qualquer chamada de API nova — `totalSpent` já é carregado hoje
