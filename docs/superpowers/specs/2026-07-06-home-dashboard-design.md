# Design: Home Dashboard (reformulação)

## Contexto

A `HomeScreen` atual (`feature/home`) mostra uma saudação, um card hero de
consumo, dois `FFStatTile` (odômetro e gasto total) e o card do último
abastecimento, com um botão fixo "Registrar abastecimento" no rodapé. O
arquivo `docs/FlowFuel_Home_Spec_Prompt.md` pede uma reformulação mais rica:
header com veículo/dias sem abastecer/notificações, card de resumo
financeiro com comparação mensal, grid 2x2 de indicadores, card de insight,
card de último abastecimento, card de atividade recente, FAB central e
bottom navigation (esta última já existe fora da Home, em
`MainContainerScreen`).

Várias dessas seções (comparação mês a mês, preço médio, insight,
notificações) não têm hoje nenhum dado ou lógica de backend por trás.

## Objetivo

Reformular a `HomeScreen` para o novo layout, calculando no cliente tudo que
for possível a partir de dados já existentes, sem depender de nenhum
endpoint novo no backend.

## Escopo

### Dentro do MVP

- Header (`VehicleHeader`): avatar + nome do veículo, "X dias sem
  abastecer", ícone de sino estático (sem badge/lógica — reservado para o
  futuro), tap no veículo abre o `VehicleSwitcherBottomSheet` existente.
- `FinancialSummaryCard`: total gasto no mês atual + comparação percentual
  com o mês anterior (`FFTrendBadge` up/down/flat), calculada no cliente via
  filtro de datas já suportado pela API de eventos.
- `IndicatorsGrid`: grid 2x2 de `IndicatorCard` — Consumo médio, Preço médio
  (derivado: total gasto ÷ energia total, sem chamada nova), Odômetro,
  Último abastecimento (versão compacta, ex. "há 3 dias").
- `InsightCard`: dica rotativa estática (lista fixa de ~12 dicas em
  Kotlin/recursos), seleção estável por dia (`dayOfYear % tips.size`) — sem
  dependência de dados do usuário.
- `LastRefuelCard`: card de detalhe completo do último abastecimento
  (valor, litros/kWh, data) — já existe, apenas movido de arquivo.
- `RecentActivityCard`: últimos 4 eventos do veículo (abastecimento,
  manutenção, despesa) com ícone de categoria, data e valor.
- FAB central "Registrar abastecimento" (`FFFab`, componente já existente
  no design system e hoje sem uso), substituindo o botão fixo atual.
- Skeleton de carregamento por seção e tratamento de erro isolado por card
  (uma seção falhar não derruba a tela toda).

### Fora do escopo (iteração futura)

- Sistema de notificações real (o sino no header é só um ícone estático
  nesta versão).
- "Próximos eventos" como agendamento/lembrete real — nesta versão o card
  mostra atividade **recente** (eventos já registrados), não eventos
  futuros/agendados, já que o app não tem hoje nenhum sistema de lembretes.
- Insight calculado a partir dos dados do usuário (comparação de gasto,
  tendência de consumo) — fica como dica genérica estática por agora.
- Layout dedicado para tablet (o grid e os cards usam `weight`/
  `fillMaxWidth`, funcionam de 6.1" a 7" e não quebram em tablet, mas sem
  otimização específica de tablet nesta versão).
- Endpoint de agregação de total gasto no backend (a soma continua sendo
  feita no cliente sobre páginas de eventos filtradas por data).

## Navegação

Nenhuma mudança de navegação: `HomeScreen` continua sendo o mesmo destino
(`MainDestinations.HOME`) dentro do `NavHost` de `MainContainerScreen.kt`,
na mesma posição da bottom bar (`FFBottomBar`). O `FlowFuelNavHost.kt` raiz
não é afetado.

## Arquitetura de dados e estrutura de pacotes

Evolução do `feature/home` existente (sem novo módulo/feature):

```
feature/home/
├── presentation/
│   ├── HomeScreen.kt                (Scaffold + LazyColumn, reescrito por seções)
│   ├── HomeViewModel.kt             (expandido)
│   ├── HomeUiState.kt               (expandido: + FinancialSummary, Indicators, RecentActivity)
│   └── components/                  (novo subpacote — extrai composables hoje soltos em HomeScreen.kt)
│       ├── VehicleHeader.kt         (novo)
│       ├── FinancialSummaryCard.kt  (novo)
│       ├── IndicatorsGrid.kt + IndicatorCard.kt   (novo)
│       ├── InsightCard.kt           (novo)
│       ├── LastRefuelCard.kt        (já existe, só muda de arquivo)
│       └── RecentActivityCard.kt    (novo)
├── domain/
│   ├── model/HomeModels.kt          (expandido: + FinancialSummary, Indicators, RecentActivityItem)
│   └── usecase/
│       ├── HomeUseCases.kt          (GetActiveVehicleUseCase, GetDashboardUseCase, CreateRefuelUseCase — já existem, inalterados)
│       ├── GetFinancialSummaryUseCase.kt   (novo)
│       └── GetRecentActivityUseCase.kt     (novo)
├── domain/HomeRepository.kt / data/HomeRepositoryImpl.kt / data/remote/HomeApi.kt   (inalterados)
└── di/HomeModule.kt                 (registra os 2 use cases novos)
```

**`GetFinancialSummaryUseCase`**: wrapper fino que chama
`GetVehicleEventsPageUseCase` (de `feature/vehicleevent`, já existente) duas
vezes — uma para o mês atual e uma para o mês anterior — usando
`EventDateFilter.Custom(from, to)` (já existente em
`domain/model/EventDateFilter.kt`) para montar `dateFrom`/`dateTo` do
primeiro/último dia de cada mês. Os parâmetros `startDate`/`endDate` já são
suportados pelo endpoint `GET vehicle-events/vehicle/{vehicleId}` — não é
necessário buscar todos os eventos do veículo, a busca já vem filtrada e
paginada pelo servidor (tipicamente 1-2 páginas por mês). Soma `amount` das
páginas retornadas para cada mês e calcula o delta percentual.

**`GetRecentActivityUseCase`**: wrapper sobre `GetVehicleEventsPageUseCase`
sem filtro de data/categoria, `page = 0`, tamanho de página padrão da API
(20) truncado para os 4 primeiros itens na apresentação.

**Preço médio** (indicador do grid): calculado em `HomeViewModel`/mapper a
partir de campos já presentes em `DashboardData` (total gasto ÷ energia
total), sem use case nem chamada de API novos.

**Dependência entre features:** `feature/home` passa a depender também de
`feature/vehicleevent` (via `GetVehicleEventsPageUseCase` e
`EventDateFilter`) além de `feature/vehicle`, que já usava — mesmo padrão de
dependência entre features já existente no projeto (ex.: `feature/home` já
usa `GetVehicleEventsTotalUseCase` de `feature/vehicleevent` hoje).

## Estados e tratamento de erros

`HomeScreenState` (sealed interface, mesmo padrão já usado):

```kotlin
sealed interface HomeScreenState {
    object Loading : HomeScreenState
    data class Success(val uiState: HomeUiState) : HomeScreenState
    object Empty : HomeScreenState
    data class Error(val message: String) : HomeScreenState
}
```

- **Loading**: as 3 fontes de dados (`GetDashboardUseCase`,
  `GetFinancialSummaryUseCase`, `GetRecentActivityUseCase`) são carregadas
  em paralelo (`coroutineScope` + `async`, mesmo padrão de
  `fetchDashboardWithEventsTotal` hoje). Cada seção mostra seu próprio
  skeleton (`FFSkeletonBlock`/`FFSkeletonLine`) até seu dado chegar — a tela
  não trava esperando a mais lenta.
- **Empty**: veículo ativo sem nenhum evento registrado ainda —
  `FinancialSummaryCard`, `IndicatorsGrid` e `RecentActivityCard` são
  substituídos por `FFEmptyState` ("Registre seu primeiro abastecimento
  para ver seus indicadores"). `VehicleHeader`, `InsightCard` e o FAB
  continuam visíveis (não dependem de eventos).
- **Error (falha isolada por card)**: como as 3 chamadas são independentes,
  se `GetFinancialSummaryUseCase` ou `GetRecentActivityUseCase` falhar
  isoladamente, só o card correspondente mostra um estado de erro inline
  compacto ("Não foi possível carregar" + retry pequeno) — o resto da tela
  continua funcional. Só uma falha em `GetActiveVehicleUseCase` (sem
  veículo ativo carregado, nada para mostrar) leva a tela inteira a
  `HomeScreenState.Error` com `FFErrorState` de tela cheia.

## Tela e componentes de UI

`HomeScreen` (`Scaffold`, `contentWindowInsets = WindowInsets(0)` — mantém
o padrão atual de não duplicar padding com `MainContainerScreen`):

- `floatingActionButton = { FFFab(...) }` — "Registrar abastecimento",
  substitui o `FFButton` fixo no rodapé atual, abre o mesmo fluxo de
  `RefuelFormState`/`CreateRefuelUseCase` já existente.
- `LazyColumn` com, na ordem: `VehicleHeader` → `FinancialSummaryCard` →
  `IndicatorsGrid` → `InsightCard` → `LastRefuelCard` → `RecentActivityCard`.
- `IndicatorsGrid` usa duas `Row`s de dois `IndicatorCard` cada (não
  `LazyVerticalGrid`, já que são sempre 4 itens fixos — dispensa a
  complexidade de um grid preguiçoso para uma lista pequena e estática).
- Reaproveita componentes de design system já existentes: `FFCard`,
  `FFStatTile` (base do `IndicatorCard`), `FFTrendBadge`,
  `FFSectionHeader`, `FFEmptyState`, `FFErrorState`, `FFLoadingSkeleton`,
  `VehiclePhotoAvatar`.
- Responsividade: layout fluido (`weight`/`fillMaxWidth`), sem breakpoints
  dedicados — funciona de 6.1" a 7" e não quebra em tablet, sem otimização
  específica de tablet (fora do escopo, ver acima).

## Testes

`HomeViewModelTest.kt` (Robolectric + MockK + Turbine, padrão já usado),
atualizado incrementalmente com mocks para `GetFinancialSummaryUseCase` e
`GetRecentActivityUseCase`:

- Loading → Success com as 4 seções preenchidas.
- Empty quando o veículo ativo não tem eventos.
- Error de tela cheia quando `GetActiveVehicleUseCase` falha.
- Falha isolada em `GetFinancialSummaryUseCase` ou
  `GetRecentActivityUseCase` não impede o restante da tela de carregar
  (estado de erro só na seção afetada).
- `GetFinancialSummaryUseCase`: cálculo correto do delta percentual mês
  atual vs. anterior, incluindo caso de mês anterior sem gastos (divisão
  por zero → tratado como "sem comparação disponível", não crash).
- Clique no FAB dispara o mesmo efeito/fluxo de `RefuelFormState` já
  testado hoje.

Previews Compose para cada componente novo (`FinancialSummaryCard`,
`IndicatorsGrid`, `IndicatorCard`, `InsightCard`, `RecentActivityCard`)
cobrindo os estados aplicáveis (Success/Loading/Error) e light/dark, mesma
convenção já usada nos componentes do design system.

Sem testes de UI/Compose instrumentados (não é padrão hoje no projeto,
conforme README) e sem testes de backend (nenhuma mudança de backend neste
design).
