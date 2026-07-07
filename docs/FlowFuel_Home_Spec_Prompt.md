# FlowFuel Home Screen Spec Prompt

Você é um Engenheiro de Software Sênior especialista em Android Nativo
utilizando **Kotlin**, **Jetpack Compose** e arquitetura **MVVM + Clean
Architecture**.

## Objetivo

Criar uma especificação técnica completa para implementar a tela Home do
FlowFuel.

## Stack

-   Kotlin
-   Jetpack Compose
-   Material Design 3
-   Navigation Compose
-   Hilt
-   Coroutines
-   StateFlow
-   MVVM
-   Clean Architecture

## Estrutura da Tela

-   Header com veículo, dias sem abastecer, notificações e troca de
    veículo.
-   Card Resumo Financeiro (mês, total e comparação).
-   Grid 2x2 de indicadores (Consumo, Preço médio, Odômetro, Último
    abastecimento).
-   Card Insight do mês.
-   Card Último abastecimento.
-   Card Próximos eventos.
-   FAB central "Registrar abastecimento".
-   Bottom Navigation.

## Componentes

-   VehicleHeader
-   FinancialSummaryCard
-   IndicatorCard
-   IndicatorsGrid
-   InsightCard
-   LastFuelCard
-   EventsCard
-   EventItem
-   FlowFuelFAB
-   FlowFuelBottomBar
-   LoadingScreen
-   EmptyState
-   ErrorState

## Estados

Loading, Success, Empty, Error.

## UI State

HomeUiState contendo Vehicle, FinancialSummary, Indicators, Insight,
LastFuel, Events, Loading e Error.

## Responsividade

Suportar 6.1", 6.5", 7" e tablets.

## Design System

Colors, Typography, Shapes, Spacing, Elevation, Icons.

## Performance

LazyColumn, estados imutáveis, evitar recomposições desnecessárias.

## Entregáveis

1.  Arquitetura
2.  Navegação
3.  Estrutura de pacotes
4.  Componentes Compose
5.  Modelos
6.  UI State
7.  Eventos
8.  Previews
9.  Testes
10. Fluxo ViewModel → UI
11. Boas práticas
12. Checklist

A documentação deve permitir implementar a tela sem decisões adicionais
de arquitetura ou design.
