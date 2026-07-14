# Design: Push Notification — Frontend (rationale, status, feedback)

**Data:** 2026-07-14
**Status:** aprovado

## Contexto

A spec e o plano de [fundação de push notification (FCM)](2026-07-14-push-notification-foundation-design.md)
cobrem só a camada de dados/serviço do cliente Android — `FlowFuelFcmService`,
`DeviceTokenRepository`, wiring em login/logout. Nenhuma dessas peças ainda foi
implementada (o plano existe, mas todas as tasks estão com checkbox vazio); esta
spec é complementar e assume que a fundação será implementada (ou está sendo
implementada em paralelo). Ela cobre a parte visível ao usuário, hoje ausente:

1. Hoje `POST_NOTIFICATIONS` (Android 13+) é pedida de forma incondicional em
   `MainActivity.onCreate` (`MainActivity.kt:32-34`), sem nenhuma explicação —
   o prompt do sistema aparece no cold start, antes até do usuário logar.
   Isso prejudica a taxa de aceite (o usuário não tem contexto do porquê) e
   contradiz o texto original da spec de fundação ("solicitada no fluxo de
   login/onboarding"), que o plano de fundação decidiu ignorar por já achar a
   permissão coberta. Esta spec reabre essa decisão: a permissão passa a ser
   pedida com uma explicação prévia, no momento certo do fluxo.
2. Não existe nenhuma forma de o usuário ver ou revisitar o status da permissão
   de notificação depois da decisão inicial — se negou sem querer, não tem como
   reativar de dentro do app.
3. Quando o app abre a partir do toque numa notificação (via o deep link
   `flowfuel://...` que `FlowFuelNavHost` já trata), a navegação acontece mas
   não há nenhum indício visual de que aquilo veio de uma notificação — o
   usuário só percebe que "pulou" pra uma tela.

Fora de escopo: qualquer UI específica por tipo de evento (ex.: layout dedicado
pro convite de compartilhamento de veículo) — o campo `type` do payload segue
reservado e não tratado, igual na spec de fundação. Também fora de escopo um
histórico/inbox de notificações dentro do app.

## Decisões

### 1. Rationale de permissão (dialog pós-login)

- Remove o `notificationPermissionLauncher.launch(...)` incondicional de
  `MainActivity.onCreate` (`MainActivity.kt:32-34`) — a Activity deixa de
  disparar esse pedido.
- Novo composable `NotificationPermissionGate` (em `core/notification`, lado
  UI), montado uma única vez dentro de `MainContainerScreen` (destino
  `Destinations.MAIN_CONTAINER`, o "Home" pós-login/pós-picker) — não em
  `VehiclePickerScreen` nem em `LoginScreen`, pra não interromper o fluxo de
  setup de quem ainda não tem veículo cadastrado.
- Comportamento, só em `Build.VERSION.SDK_INT >= TIRAMISU`:
  1. Permissão já concedida (`NotificationManagerCompat.areNotificationsEnabled()`)? Não faz nada.
  2. Já mostrado antes nesta instalação (flag persistida)? Não faz nada.
  3. Caso contrário: mostra `FFDialog` (`kind = FFDialogKind.Confirm`)
     explicando o motivo ("Ative notificações pra saber quando algo importante
     acontecer, como um convite de compartilhamento de veículo"). Confirmar
     dispara `rememberLauncherForActivityResult(RequestPermission())` com o
     prompt do sistema; dispensar (ou negar o prompt) não bloqueia nada — só
     marca a flag como "mostrado" pra não repetir.
- Persistência: novo `NotificationPrefsStore` (`core/notification/data`),
  mesmo padrão de `UpdatePrefsStore` (`preferencesDataStore`, um
  `booleanPreferencesKey("rationale_shown")`), Hilt-injetado via
  `@ApplicationContext`.
- Abaixo do Android 13 a permissão é implícita (concedida na instalação) — o
  gate não faz nada, nem grava a flag.

### 2. Status de notificações nas Configurações

- Nova `ProfileActionRow` "Notificações" na seção "Ações" de `ProfileScreen.kt`
  (mesmo padrão de `HorizontalDivider` entre linhas já usado ali para "Meus
  veículos" / "Editar perfil" / "Trocar senha" — `ProfileScreen.kt:306-324`).
- Texto trailing (substituindo o `Icons.Outlined.ChevronRight` puro por um
  texto + o chevron) mostra "Ativadas" ou "Desativadas", lido via
  `NotificationManagerCompat.areNotificationsEnabled()` — funciona em qualquer
  versão do Android (não só 13+), pois reflete o toggle geral de notificação
  do app nas configurações do sistema, não só a runtime permission.
- Reavaliado no `onResume` do `ProfileScreen` (`DisposableEffect` com
  `LifecycleEventObserver`, igual ao padrão já necessário porque o usuário
  pode alternar em Configurações do Android e voltar pro app).
- Tocar a linha abre `Settings.ACTION_APP_NOTIFICATION_SETTINGS` com
  `Settings.EXTRA_APP_PACKAGE = context.packageName` — vai direto pra tela de
  notificações do FlowFuel no Android, mais preciso que o
  `ACTION_APPLICATION_DETAILS_SETTINGS` genérico já usado em
  `StationsScreen.kt` pra permissão de localização.
- Sem estado próprio no app: é um espelho da permissão do SO, não um toggle
  que liga/desliga o recebimento de push no backend.

### 3. Feedback visual ao abrir via notificação

- `FlowFuelFcmService.showNotification` (peça da fundação, ainda não
  implementada) passa a incluir `title` e `body` como extras no `Intent`
  (`putExtra("notification_title", ...)`, `putExtra("notification_body", ...)`),
  além do `data` já usado pro deep link.
- `MainActivity` lê esses extras junto com `intent.data` (mesmos pontos onde
  hoje lê `deepLinkUri`: `onCreate` e `onNewIntent`) e repassa pro
  `FlowFuelNavHost` como um novo parâmetro opcional (`notificationMessage:
  Pair<String, String>?` ou um data class simples).
- `FlowFuelNavHost` ganha um `SnackbarHostState` próprio, hospedado num
  `Scaffold` que envolve o `NavHost` interno (acima de tudo, portanto visível
  em qualquer tela de destino, inclusive as que já têm seu próprio `Scaffold`
  com `snackbarHost` local — Scaffolds aninhados são um padrão comum em
  Compose). No `LaunchedEffect(deepLinkUri, start)` que já navega pelo deep
  link genérico (`FlowFuelNavHost.kt:76-86`), depois de
  `navController.navigate(path)` ter sucesso, dispara
  `FFSnackbarVisuals(title + corpo, FFSnackbarKind.Info)` nesse host, caso
  `notificationMessage` não seja nulo.
- Só dispara no caminho de deep link genérico (fora do caso especial
  `activate`, que não vem de notificação) — coerente com o "sem lógica por
  tipo de evento" da fundação.

## Arquivos afetados

```
Novo:
  app/src/main/java/com/flowfuel/app/core/notification/NotificationPermissionGate.kt
  app/src/main/java/com/flowfuel/app/core/notification/data/NotificationPrefsStore.kt

Modificado:
  app/src/main/java/com/flowfuel/app/MainActivity.kt
    (remove launch incondicional; passa a ler extras title/body do Intent)
  app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt
    (novo parâmetro notificationMessage; Scaffold + SnackbarHostState global;
    dispara snackbar após navegação pelo deep link genérico)
  app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt
    (monta NotificationPermissionGate uma vez)
  app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileScreen.kt
    (nova ProfileActionRow "Notificações" com status + ação de abrir configurações)
  app/src/main/java/com/flowfuel/app/core/notification/FlowFuelFcmService.kt
    (ainda não existe — criado pelo plano de fundação; esta spec assume que já
    foi criado e só adiciona title/body como extras do Intent, além do deep
    link já usado como data)
```

## Testes

- **Unitário:** `NotificationPrefsStore` (get/set da flag, mesmo padrão de
  teste de `UpdatePrefsStore` se existir); lógica de "deve mostrar o
  rationale?" isolada numa função pura testável (permissão concedida + flag →
  decisão), sem depender de Compose ou Robolectric.
- **Manual (mesmo padrão da fundação, que já evita UI test automatizado
  nesse escopo):**
  - Instalação limpa em API 33+: dialog de rationale aparece só na primeira
    vez que a Home é alcançada após login, não repete em logins seguintes.
  - Negar o prompt do sistema: app continua funcionando normal, sem bloqueio.
  - Linha "Notificações" no Perfil reflete o estado real e abre a tela certa
    de configurações do Android; voltar pro app atualiza o texto se o estado
    mudou lá.
  - Tocar uma notificação de teste: navega pra rota do `deepLink` e mostra o
    snackbar com título/corpo; com o app em foreground, comportamento de
    exibição já coberto pela fundação (sem navegação automática, logo sem
    snackbar automático também).

## Critérios de Aceitação

- Nenhum prompt de permissão de notificação aparece antes do usuário logar.
- Dialog de rationale aparece uma única vez por instalação, na primeira vez
  que o usuário chega à Home após login/ativação, só em Android 13+.
- Negar a permissão (no dialog ou no prompt do sistema) não bloqueia nenhum
  fluxo do app.
- Tela de Perfil mostra corretamente "Ativadas"/"Desativadas" e o botão leva
  direto às configurações de notificação do app no Android.
- Notificação tocada: app navega pra rota do `deepLink` e exibe um snackbar
  com o título/corpo daquela notificação.
