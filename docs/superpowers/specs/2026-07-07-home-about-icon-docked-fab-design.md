# Design: Ícone "Sobre" na Home + FAB docado na bottom bar

## Contexto

[`docs/FlowFuel_Home_Spec.md`](../../FlowFuel_Home_Spec.md) (documento de
referência externo) descreve, entre outras peças, um `TopBar` com menu
hambúrguer/notificação (seção 4.1) e uma bottom bar com FAB central
"encaixado" (seção 4.8). A spec de
[2026-07-07-home-upcoming-maintenance-design.md](2026-07-07-home-upcoming-maintenance-design.md)
já havia implementado a seção "Próximos eventos" desse mesmo documento e
deixou essas duas peças explicitamente de fora, para specs futuras e
independentes:

> "As outras duas peças ausentes da spec recebida — `TopBar` com menu
> hambúrguer/gaveta e FAB encaixado na bottom bar — não fazem parte deste
> design e ficam para specs futuras e independentes."

Este documento cobre essas duas peças, com escopo revisto após
levantamento do código atual:

- **Não existe hoje nenhuma infraestrutura de gaveta de navegação**
  (`ModalNavigationDrawer`, `DrawerState`, etc.) no app.
- **O tab "Perfil" já cobre** conta, logout, exclusão de conta, troca de
  senha e gestão de veículos — uma gaveta com os mesmos itens seria
  redundante.
- **Não existe hoje** e-mail de suporte, FAQ, nem URLs de termos/política
  de privacidade em nenhum lugar do código — um item "Ajuda/Suporte" não
  tem conteúdo real para abrir ainda.
- `VehicleHeader` (`feature/home/presentation/components/VehicleHeader.kt`)
  já tem um ícone de sino no canto superior direito, hoje inerte
  (`onClick = {}`, comentado "reservado para o futuro: notificações") —
  ou seja, já existe o slot visual que a spec de referência chama de
  "TopBar".
- A bottom bar (`FFBottomBar`) tem **5 abas** hoje (Home, Histórico,
  Postos, Eventos, Perfil), não as 4 do documento de referência — não há
  espaço "livre" para um FAB central sem redesenhar a navegação.
- **Home, Histórico e Eventos já têm cada um seu próprio FAB flutuante**
  hoje: Home e Histórico com a mesma ação ("Registrar abastecimento",
  duplicada), Eventos com uma ação diferente ("Novo evento").

Diante disso, o escopo foi reduzido ao que tem valor real e conteúdo
concreto hoje — ver "Escopo" abaixo.

## Objetivo

1. Dar um destino real e útil ao ícone de canto superior direito da Home
   (hoje inerte): abrir uma tela "Sobre" com nome, logo e versão do app.
2. Consolidar em um único FAB, docado na bottom bar e visível nas 5 abas,
   a ação de "Registrar abastecimento" (hoje duplicada em Home e
   Histórico) e "Novo evento" (hoje só em Eventos) — sem remover nenhuma
   aba existente.

## Escopo

### Dentro do MVP

**Ícone "Sobre" na Home:**

- Novo `IconButton` (ícone `Icons.Outlined.Info`) em
  `VehicleHeader.kt`, posicionado à esquerda do sino de notificações já
  existente, no mesmo `Row`. Novo parâmetro `onInfoClick: () -> Unit`.
  Nenhuma `FFTopBar` nova é criada — sem gasto extra de altura de tela,
  sem título duplicado (o nome do veículo já aparece ali).
- Toque abre um `AlertDialog`: ícone/logo do app (`drawable/ic_launcher_foreground`),
  nome "FlowFuel", versão (`BuildConfig.VERSION_NAME`) e botão "Fechar".
  Sem links de termos/privacidade (não existem hoje) e sem
  "Ajuda/Suporte" (sem canal definido — ver "Fora do escopo").
- Escopo só na `HomeScreen`; as outras 4 abas não mudam.
- Novo estado `showAboutDialog: Boolean` em `HomeUiState` +
  `openAboutDialog()`/`closeAboutDialog()` em `HomeViewModel` — mesmo
  padrão dos outros diálogos que a Home já tem
  (`LicensingDueDateDialog`, `VehicleSwitcherBottomSheet`).
- O sino de notificações permanece exatamente como está hoje (inerte,
  reservado para uma spec futura de notificações reais).

**FAB docado na bottom bar:**

- `FFBottomBar` passa de `NavigationBar` para `BottomAppBar` (Material 3,
  parâmetro `floatingActionButton`) — as 5 abas continuam todas visíveis;
  o FAB fica elevado, sobrepondo levemente a barra (efeito "docado"
  nativo do M3), sem remover nenhuma aba.
- **FAB contextual por rota**, dono passa a ser `MainContainerScreen`
  (não mais `HomeScreen`/`HistoryScreen`/`VehicleEventsScreen`
  individualmente):
  - Rota `EVENTS` → ícone `Add`, texto "Novo evento".
  - Qualquer outra rota (`HOME`, `HISTORY`, `STATIONS`, `PROFILE`) →
    ícone `LocalGasStation`, "Registrar abastecimento".
- **Novo ViewModel compartilhado** (`QuickRefuelViewModel`, `hiltViewModel()`
  obtido direto em `MainContainerScreen`) recebe o que hoje vive em
  `HomeViewModel`: `RefuelFormState`, `showRefuelSheet`,
  `isSubmittingRefuel`, `submitError`, e as funções de edição/submit do
  formulário (`onOdometerChange`, `onLitersChange`, `onTotalPriceInput`,
  `onFullTankToggle`, `onRefuelTypeChange`, `submitRefuel`,
  `closeRefuelSheet`, etc.) e a busca do veículo ativo (para
  `energyType`, já usado pelo formulário).
- `MainContainerScreen` renderiza `QuickRefuelBottomSheet` no seu próprio
  nível (fora do `NavHost` interno), assim ele abre a partir de qualquer
  aba, sem trocar de aba. Ganha também um `SnackbarHost` próprio para o
  feedback de sucesso ("Abastecimento registrado com sucesso!"), que hoje
  é responsabilidade da `HomeScreen`.
- Toque no FAB estando na aba `EVENTS`: reaproveita o fluxo de criação já
  existente (`VehicleEventsViewModel.onCreateClick`) via um booleano
  local levantado em `MainContainerScreen` (`triggerEventCreate`),
  passado para `VehicleEventsScreen` como novo parâmetro
  `triggerCreate: Boolean` + `onCreateTriggerConsumed: () -> Unit`,
  consumido via `LaunchedEffect` — mesmo padrão de `tabEventCreated` que
  a tela já usa hoje.
- **Atualização cross-aba após registrar abastecimento:** reaproveita o
  mecanismo que já existe hoje — `refreshTrigger`/`onRefreshConsumed` na
  `HomeScreen` e `historyNeedsRefresh`/`onHistoryRefreshConsumed` na
  `HistoryScreen`. `MainContainerScreen` seta um booleano local
  (`refuelJustCreated`) ao receber sucesso do `QuickRefuelViewModel` e o
  combina (`||`) com os sinais que já vêm de fora
  (`homeNeedsRefresh`/`historyNeedsRefresh`), do mesmo jeito que já faz
  hoje com `openRefuelSheet`. Nenhum mecanismo novo de comunicação entre
  telas é introduzido.

**Limpeza decorrente (resolve conflito direto do FAB único):**

- `HomeScreen`: remove o `floatingActionButton` próprio (`FFFab`
  "Registrar abastecimento"), o `SnackbarHost`/`SnackbarHostState`
  (ficariam órfãos — seu único uso hoje é o efeito `RefuelRegistered`,
  que migra para o `QuickRefuelViewModel`) e o `Spacer(Modifier.height(80.dp))`
  de compensação no fim do `LazyColumn` (não é mais necessário: o FAB
  docado ocupa espaço próprio reservado pelo `Scaffold`/`BottomAppBar`,
  diferente do FAB flutuante de hoje, que sobrepõe o conteúdo por cima).
  `HomeViewModel` perde `RefuelFormState`, `showRefuelSheet`,
  `isSubmittingRefuel`, `submitError` e as funções de formulário/submit
  associadas — mantém tudo o mais (dashboard, resumo financeiro,
  atividade recente, manutenções, seletor de veículo, diálogo de
  licenciamento, e agora também o diálogo "Sobre").
- `HistoryScreen`: remove o `floatingActionButton` próprio ("Registrar",
  que duplicava a mesma ação de Home) e o parâmetro `onAddRefuel` (junto
  com a navegação forçada para a aba Home que ele fazia em
  `MainContainerScreen`).
- `VehicleEventsScreen`: remove o `floatingActionButton` próprio ("Novo
  Evento"); a criação passa a ser disparada pelo FAB global via
  `triggerCreate` (ver acima).

### Fora do escopo (specs futuras e independentes)

- Notificações reais (o sino no `VehicleHeader` continua estático).
- Gaveta de navegação (`ModalNavigationDrawer`) — sem conteúdo próprio
  hoje que justifique a complexidade; se surgir necessidade real
  (ajuda/suporte com canal definido, configurações do app, etc.), fica
  para uma spec dedicada.
- Termos de uso / política de privacidade no diálogo "Sobre" — não
  existem URLs hoje; adicionar quando existirem.
- Qualquer mudança de IA da bottom bar (reduzir/reorganizar abas).

## Arquitetura e estrutura de pacotes

```
core/designsystem/components/
├── FFBottomBar.kt        (NavigationBar → BottomAppBar; ganha floatingActionButton)
└── FFFab.kt               (inalterado)

feature/home/presentation/
├── components/VehicleHeader.kt   (novo IconButton + onInfoClick)
├── HomeUiState.kt         (+ showAboutDialog; − RefuelFormState/showRefuelSheet/
│                             isSubmittingRefuel/submitError)
├── HomeViewModel.kt       (+ openAboutDialog/closeAboutDialog; − lógica de
│                             formulário/submit de abastecimento, que migra)
├── HomeScreen.kt          (− floatingActionButton, SnackbarHost, Spacer(80.dp);
│                             + AlertDialog "Sobre")
├── QuickRefuelBottomSheet.kt   (inalterado — só muda quem o hospeda/alimenta)
└── QuickRefuelViewModel.kt     (novo — extraído de HomeViewModel: RefuelFormState,
                                  submitRefuel, energyType do veículo ativo,
                                  efeito de sucesso; permanece em feature/home
                                  porque reaproveita os mesmos use cases que
                                  HomeViewModel já injeta hoje — CreateRefuelUseCase,
                                  GetActiveVehicleUseCase — sem depender de nada
                                  novo fora do módulo)

navigation/
├── MainContainerScreen.kt (BottomAppBar com FAB contextual por rota;
│                             hospeda QuickRefuelBottomSheet + SnackbarHost;
│                             booleanos locais: triggerEventCreate, refuelJustCreated)

feature/history/presentation/HistoryScreen.kt   (− floatingActionButton, onAddRefuel)
feature/vehicleevent/presentation/list/
├── VehicleEventsScreen.kt   (− floatingActionButton; + triggerCreate/onCreateTriggerConsumed)
└── VehicleEventsViewModel.kt (onCreateClick reaproveitado, sem mudança de lógica interna)
```

## Estados e tratamento de erros

- **Diálogo "Sobre":** sem estado de erro possível (dados vêm de
  `BuildConfig`, sempre disponíveis em tempo de compilação).
- **FAB contextual:** decisão de qual ação/ícone mostrar é pura função de
  `currentRoute` (já disponível em `MainContainerScreen` via
  `backStackEntry`), sem estado assíncrono.
- **Sheet de abastecimento:** mesmos estados que já existem hoje em
  `HomeViewModel` (`isSubmittingRefuel`, `submitError`), agora expostos
  pelo `QuickRefuelViewModel` — nenhuma mudança de comportamento, só de
  dono.
- **Criação de evento pela aba Eventos:** mesmo tratamento de erro que já
  existe em `VehicleEventsViewModel.onCreateClick` hoje — inalterado.

## Testes

- `VehicleHeader`: novo teste/preview cobrindo o clique no ícone "Sobre"
  (`onInfoClick` chamado).
- `HomeViewModelTest.kt`: remove os testes de formulário/submit de
  abastecimento (migram para `QuickRefuelViewModelTest.kt`, novo);
  adiciona teste de `openAboutDialog`/`closeAboutDialog`.
- `QuickRefuelViewModelTest.kt` (novo, Robolectric + MockK + Turbine,
  mesmo padrão já usado no projeto): submit com sucesso emite efeito
  consumido por `MainContainerScreen`; erro de submissão expõe
  `submitError`; formulário reflete os `onXChange` chamados.
- `MainContainerScreen`: teste (ou verificação manual, já que a tela não
  tem `ViewModel` próprio hoje) de que o FAB mostra o ícone/texto correto
  por rota, e que tocar nele na aba Eventos dispara `triggerCreate` em
  vez de abrir o sheet de abastecimento.
- Previews Compose para `VehicleHeader` (com e sem clique no ícone
  "Sobre") e para o novo `AlertDialog` "Sobre", light/dark — mesma
  convenção já usada no design system.
- Sem testes de UI/Compose instrumentados (não é padrão hoje no projeto)
  e sem testes de backend (nenhuma mudança de backend neste design).
