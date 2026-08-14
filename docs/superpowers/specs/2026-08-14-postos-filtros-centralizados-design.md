# Design: Centralizar filtros na tela de Postos

**Data:** 2026-08-14
**Status:** Aprovado

## Contexto

Sub-projeto A de uma decomposição maior (feature de pesquisa de cidade em
Postos, ver conversa 2026-08-14). Os filtros da tela `StationsScreen`
(tipo de posto — Combustível/Elétrico — e raio de busca — 1/3/5/10 km)
ficam hoje colados à esquerda da tela, sem centralização.

## Objetivo

Centralizar visualmente os dois filtros, sem mudar nenhum comportamento.

## Escopo

Só `StationsScreen.kt` (ponto de chamada) e `StationDistanceFilterRow.kt`
(arranjo interno). `StationTypeFilterRow.kt` não muda.

---

## Design

### `StationTypeFilterRow` (botões Combustível/Elétrico)

`SingleChoiceSegmentedButtonRow` não preenche a largura da tela — fica do
tamanho do conteúdo, e por ser filho direto do `Column` em
`StationsScreen.kt` sem alinhamento explícito, o `Column` o posiciona no
início (esquerda). Fix: no ponto de chamada em `StationsScreen.kt`,
adicionar `.align(Alignment.CenterHorizontally)` ao modifier já passado
(`Modifier.padding(top = FFTheme.spacing.sm)`). Nenhuma mudança dentro de
`StationTypeFilterRow.kt`.

### `StationDistanceFilterRow` (chips de raio 1/3/5/10 km)

É um `LazyRow` que já ocupa a largura da tela, mas os itens (`items(...)`)
ficam alinhados ao início por padrão. Fix: adicionar
`horizontalArrangement = Arrangement.Center` ao `LazyRow`. Como são só 4
chips pequenos (`STATION_RADIUS_PRESETS_METERS = [1000, 3000, 5000,
10000]`), cabem confortavelmente em qualquer largura de tela sem precisar
rolar — centralizar não esconde nenhum chip.

## Fora do escopo

- Qualquer mudança de comportamento dos filtros (seleção, valores)
- A pesquisa por cidade (sub-projetos B e C, specs separados)
