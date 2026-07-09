# Design: Suporte a layout adaptativo (tablet/dobrável)

## Contexto

Hoje o app tem `minSdk = 26` / `targetSdk = 35`, sem restrição de
`<supports-screens>`/`<uses-feature>` no manifest — ou seja, é instalável
em qualquer tamanho de tela, incluindo tablets e dobráveis. Na prática,
porém, não existe nenhuma lógica adaptativa: um único layout fixo,
pensado para celular (`FFBottomBar` fixa embaixo, sem `WindowSizeClass`,
sem qualifiers `values-sw*`/`values-w*`). Em telas largas isso resulta em
conteúdo esticado sem reflow.

Não há usuários reportando problema hoje — este trabalho é preparação
antecipada da base, não resposta a uma reclamação.

## Objetivo

Dar ao app uma segunda forma coerente de se apresentar em telas largas
(tablet, dobrável aberto, Chromebook, multi-window), sem alterar o
comportamento em celular (`compact`):

1. Navegação principal muda de bottom bar (`compact`) para
   `NavigationRail` lateral (`medium`/`expanded`).
2. As três telas de lista mais navegadas (Histórico, Eventos, Veículos)
   ganham layout lado a lado (lista + detalhe) em `expanded`.
3. As telas sem par lista/detalhe (Home, Postos, Perfil) não ficam
   esticadas sem limite em `expanded`.

## Escopo

### Dentro

- Dependência nova: `androidx.compose.material3.adaptive` (`adaptive`,
  `adaptive-layout`, `adaptive-navigation`), versão compatível com
  `composeBom = 2024.12.01`.
- `LocalWindowSizeClass`: `CompositionLocal` novo em
  `core/designsystem/adaptive/WindowAdaptive.kt`, alimentado uma única
  vez em `MainActivity` via `calculateWindowSizeClass(activity)` — fonte
  única de verdade, evita cada tela recalcular por conta própria.
- `MainContainerScreen.kt`: troca do `Scaffold` + `FFBottomBar` (linhas
  186-224 hoje) por `NavigationSuiteScaffold`. Reaproveita a mesma lista
  `tabs`/`currentRoute`/`onSelect` já existente — muda só o container
  visual, não a lógica de navegação das abas. `FFFab` passa a ser
  posicionado manualmente dentro do conteúdo (o `NavigationSuiteScaffold`
  não tem slot de FAB dedicado).
- `FFAdaptiveContentWidth` (nome provisório): `Modifier` novo no design
  system que limita a largura do conteúdo (~840dp) e centraliza com
  padding lateral quando `WindowSizeClass` é `expanded`. Aplicado em
  `HomeScreen`, `StationsScreen` e `ProfileScreen` — únicas telas que não
  ganham par lista/detalhe nesta rodada.
- Três pares lista/detalhe convertidos para `ListDetailPaneScaffold`
  (cada um com seu próprio `rememberListDetailPaneScaffoldNavigator()`,
  hospedado dentro do composable da aba, não mais como destino do
  NavHost raiz em `expanded`):
  - `HistoryScreen` + `RefuelDetailsScreen`
  - `VehicleEventsScreen` + `VehicleEventDetailsScreen`
  - `VehiclesScreen` (gerenciar) + `VehicleDetailsScreen`
- Ajuste pontual nos três ViewModels de detalhe (`RefuelDetailsViewModel`,
  `VehicleEventDetailsViewModel`, `VehicleDetailsViewModel`) para aceitar
  o id diretamente como parâmetro, além do `SavedStateHandle` de hoje —
  necessário porque em `expanded` a tela de detalhe é renderizada como
  pane, sem passar pelo NavController raiz.
- Estado vazio novo para a pane de detalhe antes de qualquer seleção
  (`FFEmptyState`, variante de texto por tela: "Selecione um
  abastecimento/evento/veículo para ver os detalhes").
- Testes Compose novos: um por breakpoint confirmando
  `NavigationSuiteScaffold` renderiza bottom bar vs. rail conforme
  `WindowSizeClass` injetado; um por par lista/detalhe confirmando que
  selecionar um item em `expanded` atualiza a pane de detalhe sem
  disparar navegação no NavController raiz.

### Fora (não mexer nesta rodada)

- **Android Auto** (`feature/auto`): usa templates fixos do Car App
  Library, não participa da adaptação de tela.
- **Bottom sheets/diálogos virando side sheets** em tela larga (ex.:
  `QuickRefuelBottomSheet`, `ExportBottomSheet`) — isso seria Nível 3
  (breakpoints formais aplicados a toda a UI); decisão explícita de
  ficar só no Nível 2 (navegação + list-detail) por ora.
- Qualquer novo destino/rota nas 3 telas convertidas — a URL/rota de
  deep-link (`REFUEL_DETAILS`, `VEHICLE_EVENT_DETAILS`, `VEHICLE_DETAILS`)
  continua existindo e funcionando normalmente em `compact`, para
  notificações/widgets futuros.
- Suporte a orientação de tela como sinal adicional (hoje a decisão é só
  por largura, via `WindowSizeClass`) — fora de escopo, `WindowSizeClass`
  já cobre os casos relevantes de tablet/dobrável/multi-window.

## Arquitetura

### Faseamento

Entrega incremental, cada fase testável e útil isoladamente — não há
dependência forçada de concluir as 4 para ter valor:

1. **Fase 1 — Base:** dependência nova, `LocalWindowSizeClass`,
   `NavigationSuiteScaffold` no `MainContainerScreen`,
   `FFAdaptiveContentWidth` em Home/Postos/Perfil.
2. **Fase 2 — Histórico list-detail** (aba mais usada).
3. **Fase 3 — Eventos list-detail.**
4. **Fase 4 — Veículos list-detail** (dentro do fluxo "Gerenciar
   veículos").

### `LocalWindowSizeClass`

```kotlin
val LocalWindowSizeClass = staticCompositionLocalOf<WindowSizeClass> {
    error("LocalWindowSizeClass não fornecido — deve ser setado em MainActivity")
}
```

Setado uma vez em `MainActivity.onCreate` (`setContent`), envolvendo
`FFTheme`/`FlowFuelNavHost` raiz.

### `MainContainerScreen` — bottom bar → rail

```kotlin
NavigationSuiteScaffold(
    navigationSuiteItems = {
        tabs.forEach { tab ->
            item(
                selected = currentRoute == tab.route,
                onClick  = { /* mesma lógica de navigate já existente */ },
                icon     = { Icon(if (selected) tab.selectedIcon else tab.icon, tab.label) },
                label    = { Text(tab.label) },
            )
        }
    },
) {
    Box(Modifier.fillMaxSize()) {
        NavHost(/* mesmo NavHost aninhado de hoje */)
        FFFab(
            modifier = Modifier.align(Alignment.BottomEnd).padding(FFTheme.spacing.lg),
            /* mesmo ícone/callback contextual por rota já existente */
        )
    }
}
```

`NavigationSuiteScaffold` decide sozinho `NavigationBar` (compact) vs.
`NavigationRail` (medium/expanded) a partir do `WindowSizeClass`
corrente — sem branch manual no código do app.

### Par list-detail (exemplo: Histórico)

```kotlin
@Composable
fun HistoryScreen(/* params existentes */) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Int>()
    val windowSizeClass = LocalWindowSizeClass.current

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value     = navigator.scaffoldValue,
        listPane  = {
            AnimatedPane {
                HistoryListContent(
                    onItemClick = { id ->
                        if (windowSizeClass.isExpanded()) {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id)
                        } else {
                            onNavigateToDetails(id) // comportamento atual, inalterado
                        }
                    },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                navigator.currentDestination?.content?.let { id ->
                    RefuelDetailsScreen(refuelId = id, onBack = { navigator.navigateBack() }, embedded = true)
                } ?: FFEmptyState(message = "Selecione um abastecimento para ver os detalhes")
            }
        },
    )
}
```

- Em `compact`, `navigator.scaffoldValue` só expõe a pane de lista —
  `ListDetailPaneScaffold` já lida com isso, então `onItemClick` segue o
  caminho antigo (`onNavigateToDetails`, NavController raiz), sem
  duplicar lógica de navegação.
- `RefuelDetailsScreen` ganha um parâmetro `refuelId: Int` explícito
  (novo) e um `embedded: Boolean = false` (novo) — quando `embedded`,
  `hiltViewModel()` usa o id passado em vez de ler do `SavedStateHandle`,
  e a tela não renderiza sua própria `TopAppBar` de "voltar" (já tem uma
  pane ao lado, o back é resolvido pelo `navigator`).
- Mesmo padrão para `VehicleEventsScreen`/`VehicleEventDetailsScreen` e
  `VehiclesScreen`/`VehicleDetailsScreen`.

### Back gesture

O `BackHandler` do `ListDetailPaneScaffoldNavigator` só intercepta o back
quando há uma pane empilhada sobre a outra dentro da própria aba (ex.:
usuário estava em `compact` dentro do detalhe e a janela virou `expanded`
no meio do fluxo). Fora isso, o back sobe normalmente para o
`NavController` raiz, como hoje — sem handler duplicado.

## Testes

- **Compose UI**: `NavigationSuiteScaffold` renderiza `NavigationBar` em
  `WindowSizeClass` compact e `NavigationRail` em medium/expanded
  (`WindowSizeClass` injetado via `LocalWindowSizeClass` no teste).
- **Compose UI**, um por par: selecionar item na lista em `expanded`
  atualiza a pane de detalhe (via `navigator.currentDestination`) sem
  chamar `onNavigateToDetails`; em `compact`, selecionar item chama
  `onNavigateToDetails` e não altera `navigator`.
- **Manual**: emulador redimensionável (perfis Pixel Tablet, Pixel Fold)
  e resize ao vivo em modo freeform, confirmando que a transição compact
  ↔ expanded não perde o item selecionado nem duplica o back.
- **Sem impacto em testes existentes**: ViewModels/use cases/repositórios
  não mudam de contrato — só ganham uma forma alternativa (explícita) de
  receber o id, sem quebrar a leitura via `SavedStateHandle` já testada.

## Riscos identificados

- `androidx.compose.material3.adaptive` é uma família de bibliotecas mais
  jovem que o `material3` principal — API pode ter mudanças breaking
  entre versões minor antes de estabilizar de vez; mitigação: fixar
  versão no catálogo (`libs.versions.toml`) e atualizar deliberadamente,
  não via range aberto.
- Duplicar o parâmetro de entrada dos 3 ViewModels de detalhe
  (`SavedStateHandle` vs. id explícito) cria dois caminhos para o mesmo
  dado — risco de divergência se um dos dois for esquecido numa mudança
  futura. Mitigação: um único ponto interno (`init` do ViewModel) resolve
  "id explícito, senão SavedStateHandle" — nunca os dois checados espalhados
  pela tela.
- Faseamento significa que, se o trabalho parar após a Fase 1, o app fica
  num estado "meio adaptativo" (rail + largura limitada, mas Histórico/
  Eventos/Veículos ainda em tela cheia mesmo em `expanded`) — aceitável
  dado que cada fase já é uma melhoria isolada sobre o estado atual (zero
  adaptação), não uma regressão.
