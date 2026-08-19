# Redesenho do Android Auto: menu de navegação (Postos, Eventos, Abastecimento, Info do motorista)

## Contexto

Hoje o Android Auto tem uma única tela pós-login, `AutoDashboardScreen`
(`app/src/main/java/com/flowfuel/app/feature/auto/dashboard/AutoDashboardScreen.kt`):
um `GridTemplate` de 5 blocos (consumo médio, gasto total, nº de
abastecimentos, último abastecimento, e um botão de ação "Registrar
abastecimento" que empurra o fluxo `AutoRefuelStep1Screen` → ... →
`AutoRefuelSuccessScreen`).

O objetivo é substituir essa tela por um menu de navegação com 4
seções: **Registrar abastecimento** (fluxo já existente), **Postos
próximos**, **Eventos** e **Informações importantes para o
motorista** — as duas últimas novas, reaproveitando use cases que já
existem para o app no celular.

As 4 stats atuais do grid saem de circulação por completo — não há
mais nenhuma tela no Android Auto que as mostre.

## Navegação

```
AutoSession.onCreateScreen()
  └─ token nulo/vazio → AutoLoginScreen (sem mudança)
  └─ token válido → AutoMenuScreen (NOVA — substitui AutoDashboardScreen)
       ├─ "Registrar abastecimento" → AutoRefuelStep1Screen (sem mudança)
       ├─ "Postos próximos"         → AutoStationsScreen (NOVA)
       ├─ "Eventos"                 → AutoEventsScreen (NOVA)
       └─ "Informações importantes" → AutoDriverInfoScreen (NOVA)
```

`AutoDashboardScreen.kt` e seu teste são deletados. `GetDashboardUseCase`
sai do `AutoCarAppServiceEntryPoint` (só era usado ali).

## AutoMenuScreen

Substitui `AutoDashboardScreen` como tela inicial pós-login.

- **Carrega uma vez**, no `ON_START`: `getActiveVehicle()`. O
  `ActiveVehicleData` resultante (`id`, `currentKm`, `energyType`,
  `brand`/`model`/`licensePlate` pro título) é passado como parâmetro
  construtor pras 4 telas de destino — nenhuma delas chama
  `getActiveVehicle()` de novo.
- Estado: `Loading` (`MessageTemplate` com `setLoading(true)`) /
  `Success(vehicle)` / `Error(AppError)` — mesmo padrão do
  `AutoDashboardScreen` atual (retry exceto em `Unauthorized`, que só
  orienta abrir o app no celular).
- Template de sucesso: `ListTemplate` com título
  `"${brand} ${model}${placa}"` (igual ao grid atual) e 4 `Row`s, cada
  uma com ícone (`setImage`, drawable novo/reaproveitado) + título:
  1. "Registrar abastecimento" → `ic_auto_add` (existente) → push
     `AutoRefuelStep1Screen(carContext, vehicle, createRefuel)`
  2. "Postos próximos" → `ic_auto_station` (novo) → push
     `AutoStationsScreen(carContext, vehicle, getNearbyStations, locationProvider)`
  3. "Eventos" → `ic_auto_history` (existente, reaproveitado) → push
     `AutoEventsScreen(carContext, vehicle.id, getVehicleEvents)`
  4. "Informações importantes" → `ic_auto_info` (novo) → push
     `AutoDriverInfoScreen(carContext, vehicle, getUpcomingMaintenance)`

## AutoStationsScreen (Postos próximos)

- **Carrega no `ON_START`**: `locationProvider.getCurrentLocation()`
  (mesma interface `LocationProvider`/`FusedLocationProvider` do
  celular, injetada via o entry point do Hilt) → se
  `Available(location)`, chama
  `getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS)` (3km,
  a mesma constante usada no celular).
- **Filtro fixo, sem seletor no carro**: `vehicle.energyType == "ELECTRIC"`
  → só `StationType.Electric`; qualquer outro valor (`COMBUSTION`,
  `HYBRID`) → só `StationType.Fuel`. Mesma regra do
  `StationsViewModel` (`app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModel.kt:67-69`).
- Ordena por `distanceMeters` ascendente, pega os 6 primeiros.
- Estados:
  - `PermissionDenied` → `MessageTemplate`: "Ative a permissão de
    localização no FlowFuel do celular para ver postos próximos."
  - `Unavailable` (sem fix de GPS) ou lista vazia → `MessageTemplate`
    informativa, sem erro (não é falha, é "nada encontrado").
  - Falha de rede (`AppResult.Failure` de `getNearbyStations`) →
    `MessageTemplate` de erro com "Tentar novamente".
  - Sucesso com itens → `ListTemplate`, uma `Row` por posto: título =
    nome do posto, texto = distância formatada (`"350 m"` abaixo de
    1km, `"1,2 km"` acima). Reaproveita
    `formatDistance(meters: Int): String`
    (`app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationCard.kt:125`)
    via import direto — não duplicar a função. É `internal`, mas
    `internal` no Kotlin é visível em todo o módulo `:app` (projeto de
    módulo único, ver `settings.gradle.kts`), não só no pacote, então
    já é acessível de `feature.auto` sem mudar a visibilidade.
- **Ao tocar num posto**: dispara navegação real —
  `carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE).setData(Uri.parse("geo:${station.latitude},${station.longitude}")))`,
  que abre o app de navegação padrão do carro com destino no posto.

## AutoEventsScreen (Eventos)

- **Carrega no `ON_START`**: `getVehicleEvents(vehicleId)` (já existe,
  pagina internamente e devolve a lista completa).
- Ordena por `eventDate` desc, pega os 10 mais recentes.
- Somente leitura — **sem `onClick`** nas linhas, sem fluxo de criar
  ou editar (isso continua exclusivo do celular).
- Template de sucesso: `ListTemplate`, uma `Row` por evento — título =
  `event.title.ifBlank { event.category.label }`, texto = data
  formatada (`dd/MM`) + valor (se `amount != null`, formatado em
  `NumberFormat.getCurrencyInstance(pt-BR)`, mesmo padrão do
  `AutoDashboardScreen` atual).
- Lista vazia → `MessageTemplate`: "Nenhum evento registrado ainda."
- Falha (`AppResult.Failure`) → `MessageTemplate` de erro com "Tentar
  novamente" (exceto `Unauthorized`).

## AutoDriverInfoScreen (Informações importantes para o motorista)

- **Carrega no `ON_START`**:
  `getUpcomingMaintenance(vehicle.id, vehicle.currentKm)` — mesmo use
  case (`GetUpcomingMaintenanceUseCase`) que hoje só é chamado pelo
  `HomeViewModel` pra seção "Próximos eventos" da Home no celular.
  Devolve sempre 3 itens fixos: troca de óleo, rodízio de pneus,
  licenciamento.
- Template de sucesso: `ListTemplate`, 3 `Row`s fixas (título +
  status), sem `onClick`.
- **Extração necessária**: a lógica de título/subtítulo hoje mora
  dentro de `UpcomingEventsSection.toPresentation()`
  (`app/src/main/java/com/flowfuel/app/feature/home/presentation/components/UpcomingEventsSection.kt:96-122`),
  que é `@Composable` (usa `MaterialTheme` pra cor/ícone) e portanto
  não pode ser chamada do Car App Library. Extrair só a parte de
  **texto** (título + subtítulo, sem cor/ícone) pra uma função Kotlin
  pura nova, ex. `UpcomingMaintenanceItem.toStatusText(): Pair<String, String>`
  em `app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt`
  (ou arquivo companheiro no mesmo pacote). `UpcomingEventsSection`
  passa a chamar essa função pro título/subtítulo e mantém local só a
  lógica de cor/ícone (que depende de `MaterialTheme`/`FFTheme`, fica
  no Compose). `AutoDriverInfoScreen` chama a mesma função.
- Sem estado de "vazio" (o use case sempre devolve os 3 itens, com
  `needsSetup=true` quando aplicável). Erro (`AppResult.Failure` de
  `getUpcomingMaintenance`) → `MessageTemplate` de erro com "Tentar
  novamente" (exceto `Unauthorized`).

## Entry point / DI

`AutoCarAppServiceEntryPoint`
(`app/src/main/java/com/flowfuel/app/feature/auto/AutoCarAppService.kt`)
passa a expor:

```kotlin
interface AutoCarAppServiceEntryPoint {
    fun sessionStore(): SessionStore
    fun getActiveVehicle(): GetActiveVehicleUseCase
    fun createRefuel(): CreateRefuelUseCase
    fun getNearbyStations(): GetNearbyStationsUseCase
    fun locationProvider(): LocationProvider
    fun getVehicleEvents(): GetVehicleEventsUseCase
    fun getUpcomingMaintenance(): GetUpcomingMaintenanceUseCase
    // getDashboard() removido — não usado mais no Auto
}
```

`AutoSession` recebe essas dependências extras no construtor e as
propaga pro `AutoMenuScreen`, que por sua vez as espalha pras 4 telas
de destino (cada tela só recebe as dependências que efetivamente usa,
não o conjunto inteiro).

## Ícones novos

Dois drawables novos em `app/src/main/res/drawable/` (Material Icons
clássico, 24dp, path único, mesmo padrão dos 5 existentes):

- `ic_auto_station.xml` (pin/local de posto — para "Postos próximos")
- `ic_auto_info.xml` (círculo com "i" — para "Informações importantes")

`ic_auto_add.xml` e `ic_auto_history.xml` (já existentes) são
reaproveitados no menu sem alteração. `ic_auto_fuel.xml`,
`ic_auto_money.xml` e `ic_auto_calendar.xml` ficam sem uso após a
remoção do `AutoDashboardScreen` — **não são deletados** nesta tarefa
(fora de escopo remover assets não referenciados; podem ser limpos
depois com uma verificação de uso em todo o projeto).

## Testes

Um arquivo de teste por tela nova, mesmo padrão de
`AutoDashboardScreenTest.kt` (Robolectric + `TestCarContext`,
`@Config(sdk = [33])`, mock das use cases com MockK):

- `AutoMenuScreenTest` — loading/erro/sucesso, sucesso tem 4 `Row`s na
  ordem certa, cada uma com `onClickDelegate` não nulo.
- `AutoStationsScreenTest` — permissão negada, sem fix de GPS, lista
  vazia, erro de rede, sucesso filtra por tipo e ordena por distância
  (máx. 6), `onClick` de uma linha dispara `startCarApp` com o
  `ACTION_NAVIGATE` certo.
- `AutoEventsScreenTest` — lista vazia, erro, sucesso ordena por data
  desc e limita a 10, linhas sem `onClickDelegate`.
- `AutoDriverInfoScreenTest` — erro, sucesso sempre com 3 linhas
  (óleo/pneus/licenciamento), texto de status bate com os cenários de
  atrasado/em dia/precisa configurar.
- `AutoSessionTest` — atualizar o teste "valid token" pra esperar
  `AutoMenuScreen` em vez de `AutoDashboardScreen`; construtor do
  `AutoSession` ganha os novos parâmetros mockados.
- `AutoDashboardScreenTest.kt` é deletado.
- Teste unitário novo (ou no arquivo de testes do `HomeModels`/
  `UpcomingEventsSection`) pra `toStatusText()` cobrindo os mesmos 5
  cenários que hoje só existem via preview do Compose (em dia,
  atrasado por km, atrasado por dias, vence em N dias, precisa
  configurar licenciamento).

## Fora de escopo

- Seletor manual de raio/tipo de posto no Android Auto (fixo em 3km +
  tipo automático, como já aprovado).
- Criar/editar eventos a partir do carro.
- Remover os drawables/ícones que ficam sem uso após a remoção do
  grid antigo.
- Mudar a Home do celular (a extração de `toStatusText()` só move
  texto que já existe pra um lugar compartilhado; o visual do celular
  não muda).
