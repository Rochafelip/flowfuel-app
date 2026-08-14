# Design: Grid de indicadores (3 itens) + limite de atividade recente (3)

**Data:** 2026-08-14
**Status:** Aprovado

## Contexto

Sub-projeto 1 de uma decomposição maior (ajustes de UX na Home, ver
conversa 2026-08-14). Dois ajustes pequenos e independentes:

1. O grid de indicadores (`IndicatorsGrid`) tem hoje 4 cards em 2x2:
   Consumo médio, Preço médio, Odômetro, Último abastecimento. Os cards
   não ficam visualmente do mesmo tamanho porque nem todos têm `unit`
   (ex: "Preço médio" não tem, "Consumo médio" tem "km/L") — cada
   `FFStatTile` cresce conforme seu próprio conteúdo, então linhas com
   itens de conteúdo diferente ficam com alturas diferentes. Além disso,
   "Último abastecimento" já aparece duplicado: o mini-card mostra só "Há
   X dias", mas logo abaixo na tela já existe um card completo "Último
   abastecimento" (`LastRefuelCard`) com Data/Litros/Valor pago/Preço por
   litro.
2. "Atividade recente" já tem um limite (`RECENT_ACTIVITY_LIMIT = 4` em
   `GetRecentActivityUseCase.kt`) — só precisa virar 3.

## Objetivo

Remover o mini-card "Último abastecimento" do grid (a informação já existe
no card completo abaixo). Reduzir "Atividade recente" pra mostrar só os 3
itens mais recentes.

## Escopo

`IndicatorsGrid.kt`, `HomeScreen.kt` (ponto de chamada + função auxiliar
que fica órfã), `GetRecentActivityUseCase.kt` e seu teste.

---

## Design

### `IndicatorsGrid.kt`

Remove o parâmetro `lastRefuel`. A segunda linha do grid fica só com
`Odômetro`, ocupando metade da largura (`Modifier.weight(1f)`) + um
espaço vazio equivalente na outra metade, pra manter o alinhamento visual
com a linha de cima (sem esticar o card do Odômetro pra largura cheia):

```kotlin
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
```

### `HomeScreen.kt`

No ponto de chamada, remove o argumento `lastRefuel`:

```kotlin
IndicatorsGrid(
    consumption = IndicatorItem("Consumo médio", consumptionValue, consumptionUnit),
    averagePrice = IndicatorItem("Preço médio", averagePrice?.let(::formatBrl) ?: "—"),
    odometer = IndicatorItem("Odômetro", formatKm(vehicle.currentKm.toDouble()), "km"),
)
```

A função privada `shortDaysSinceLabel(days: Int?): String` (linha ~331)
fica sem nenhum uso depois disso — é **deletada**, não deixada como código
morto. `daysSince`/`daysSinceRefuel` continuam existindo, pois ainda
alimentam `VehicleHeader` (`daysSinceLastRefuel`), que é uma função
diferente (`daysSinceRefuelLabel`, dentro de `VehicleHeader.kt`) — não
mexe nisso.

### `GetRecentActivityUseCase.kt`

```kotlin
private const val RECENT_ACTIVITY_LIMIT = 3
```

Único ponto de mudança — toda a lógica de merge/ordenação/paginação
continua igual, só o valor da constante muda (ela já controla tanto o
`size` da página de abastecimentos quanto o `.take()` final da timeline
combinada).

## Testes

`GetRecentActivityUseCaseTest.kt`: atualizar as duas ocorrências de
`getRefuelHistory(1, 0, 4)` para `getRefuelHistory(1, 0, 3)`, e o teste
`merges refuels and events sorted by date descending` pra esperar 3 itens
(os 3 mais recentes da mesma massa de dados) em vez de 4.

Nenhum teste automatizado cobre `IndicatorsGrid` hoje (componente
puramente visual) — verificação por `@Preview` atualizado + checagem
manual no emulador.

## Fora do escopo

- Qualquer mudança em `LastRefuelCard` (o card completo de último
  abastecimento continua como está)
- O carrossel de composição de gastos e a paleta de cores (sub-projeto 2,
  spec separado)
