# Design: Filtro de tipo + card compacto (Postos)

## Contexto

A tela "Postos" (`docs/superpowers/specs/2026-07-01-postos-proximos-design.md`,
redesign de card em `docs/superpowers/specs/2026-07-01-postos-filtro-raio-ux-design.md`)
já está implementada e integrada ao backend real (`GET /stations/nearby`),
que retorna uma lista **combinada** de postos de combustível (OSM Overpass)
e estações de recarga elétrica (Open Charge Map), ordenada por distância.
Hoje a tela mostra os dois tipos misturados na mesma lista, diferenciados só
por uma badge colorida no card.

Esta rodada adiciona um filtro de tipo e reduz a altura do `StationCard`,
sem exigir nenhuma mudança de contrato no backend (`StationResponseDto`
continua com os mesmos campos: `placeId/name/type/distanceMeters/rating/
latitude/longitude`).

**Fora deste documento:** durante o brainstorm surgiu uma visão bem mais
ampla de "Encontrar Postos" (busca por CEP/bairro, mapa com marcadores por
preço, sistema de preços colaborativo, favoritos, avaliações com fotos,
tela de detalhes completa, reorganização da bottom nav). Essa visão foi
decomposta em sub-projetos e fica registrada como roadmap — ver memória
`project_stations_full_vision_roadmap`. Este spec cobre apenas o primeiro
sub-projeto entregável (filtro de tipo + card compacto), que não depende de
nenhuma fonte de dado nova.

## Objetivo

1. Permitir ao usuário filtrar a lista combinada por tipo — Combustível ou
   Elétrico — via toggle exclusivo, sem gerar nova chamada de rede (a lista
   completa já está em memória).
2. Iniciar a tela com o tipo já sincronizado com o veículo ativo do
   usuário, evitando um toque extra no caso comum.
3. Reduzir a altura do `StationCard`, removendo redundância visual (a badge
   de tipo por card fica menos necessária já que a lista está filtrada por
   tipo) e aproveitando o campo `rating`, hoje capturado mas nunca exibido.

## Escopo

### Dentro

- Novo componente `StationTypeFilterRow`: 2 botões exclusivos (Combustível/
  Elétrico), renderizado **acima** de `StationDistanceFilterRow` existente,
  mesma visibilidade condicional (oculto em `PermissionRequired`).
- Novo estado `selectedType: StateFlow<StationType>` em `StationsViewModel`,
  independente de `StationsUiState` (mesmo padrão de `radiusMeters`) — mudar
  o tipo **não** dispara `load()`, só reflow do filtro client-side.
- Sincronização do valor inicial de `selectedType` com o veículo ativo do
  usuário (`SessionStore.activeVehicleIdFlow` + `VehicleRepository
  .getVehicleById`), lida uma vez no `init` antes do primeiro `load()`.
- Filtragem client-side em `StationsScreen`: deriva a lista exibida a partir
  de `StationsUiState.Success.stations` + `selectedType`.
- Novo estado vazio específico por tipo (distinto do `StationsUiState.Empty`
  genérico, que continua representando "0 resultados totais vindos da
  API").
- Redesign compacto de `StationCard`: remove a badge de tipo em pill,
  adiciona ícone de tipo inline antes do nome, exibe `rating` quando
  presente, substitui o `FFButton` full-width por um `IconButton` de rota.

### Fora (não mexer nesta rodada)

- Qualquer campo novo no contrato do backend (endereço, bandeira, horário,
  telefone, site, preço, avaliações com comentário/foto) — nenhum desses
  dados existe nas fontes atuais (OSM Overpass / Open Charge Map).
- Seletor manual de app de navegação (Waze vs Google Maps) — mantém
  `google.navigation:` deixando o Android decidir, como hoje.
- Persistência do tipo selecionado entre sessões do app (fica em memória no
  `StationsViewModel`, como já é o caso do raio).
- Toggle Lista/Mapa, busca por cidade/bairro/CEP, categorias adicionais de
  serviço (conveniência, lava-jato etc.), sistema de preços colaborativo,
  favoritos, avaliações, reorganização da bottom nav — tudo isso faz parte
  da visão maior registrada como roadmap, fora de escopo aqui.
- Mudanças no componente compartilhado `FFCard` do design system.

## Arquitetura: filtro de tipo

### Domínio — sem mudança de contrato

`Station.type: StationType` já existe (`Fuel`/`Electric`). O filtro é
aplicado sobre a lista já carregada, não no `GetNearbyStationsUseCase` nem
no `StationRepository` — o backend continua sendo chamado uma única vez por
combinação de localização+raio, igual hoje.

### `StationsViewModel` — novo estado `selectedType`

```kotlin
private val _selectedType = MutableStateFlow(StationType.Fuel)
val selectedType: StateFlow<StationType> = _selectedType.asStateFlow()

fun onTypeSelected(type: StationType) {
    _selectedType.value = type
}
```

Diferente de `onRadiusSelected`, `onTypeSelected` **não chama `load()`** —
o raio é parâmetro de query da API (precisa de novo request), o tipo é
filtro puro sobre dado já em memória.

### Sincronização com veículo ativo — `init`

Mesmo padrão já usado em `VehicleEventsViewModel` (ver
`project_architecture` memória: "ViewModel com vehicleId opcional... lê
`sessionStore.activeVehicleIdFlow.firstOrNull()` no `init`"):

```kotlin
init {
    viewModelScope.launch {
        sessionStore.activeVehicleIdFlow.firstOrNull()?.let { vehicleId ->
            (vehicleRepository.getVehicleById(vehicleId) as? AppResult.Success)
                ?.value?.energyType?.let { energyType ->
                    _selectedType.value = when (energyType) {
                        EnergyType.Electric -> StationType.Electric
                        EnergyType.Combustion, EnergyType.Hybrid -> StationType.Fuel
                    }
                }
        }
        load()
    }
}
```

- Falha silenciosa: se não houver veículo ativo, ou `getVehicleById` falhar,
  `_selectedType` permanece no padrão `StationType.Fuel` e a tela carrega
  normalmente — a sincronização é um refinamento de UX, não uma dependência
  bloqueante do carregamento de postos.
- `StationsViewModel` ganha uma nova dependência: `VehicleRepository`
  (interface já existente em `feature/vehicle/domain`, injetável via Hilt
  sem novo módulo).
- `load()` deixa de ser chamado direto no `init` atual (linha 39 de
  `StationsViewModel.kt`) — passa a ser chamado dentro dessa `viewModelScope
  .launch`, depois da tentativa de sincronização.

## UI: `StationTypeFilterRow` (novo componente)

Novo arquivo `feature/station/presentation/list/StationTypeFilterRow.kt`:

```kotlin
@Composable
fun StationTypeFilterRow(
    selectedType: StationType,
    onSelect: (StationType) -> Unit,
    modifier: Modifier = Modifier,
)
```

- 2 segmentos exclusivos (Material3 `SingleChoiceSegmentedButtonRow` +
  `SegmentedButton`, um por valor de `StationType`), sempre exatamente um
  selecionado — não existe estado "nenhum" nem "os dois".
  - Combustível: ícone `Icons.Filled.LocalGasStation`, rótulo
    "Combustível".
  - Elétrico: ícone `Icons.Filled.EvStation`, rótulo "Elétrico".
  - Reaproveita `StationTypeBadgeContent`/`badgeContent()` já existentes em
    `StationCard.kt` para ícone/rótulo/content description, evitando
    duplicar os literais.
- Em `StationsScreen.kt`: renderizado **acima** de
  `StationDistanceFilterRow`, mesma condição de visibilidade (`state !=
  StationsUiState.PermissionRequired`). Ao selecionar, chama
  `viewModel.onTypeSelected(type)` — sem debounce, é seleção discreta.

## UI: filtragem client-side em `StationsScreen`

No branch `is StationsUiState.Success` do `when (state)`:

```kotlin
is StationsUiState.Success -> {
    val filtered = remember(s.stations, selectedType) {
        s.stations.filter { it.type == selectedType }
    }
    if (filtered.isEmpty()) {
        FFEmptyState(
            title = when (selectedType) {
                StationType.Fuel -> "Nenhum posto de combustível encontrado por perto"
                StationType.Electric -> "Nenhum posto elétrico encontrado por perto"
            },
            description = "Tente aumentar o raio de busca ou trocar o filtro de tipo.",
            actionText = "Tentar novamente",
            onAction = viewModel::load,
        )
    } else {
        PullToRefreshBox(/* ... como hoje, usando `filtered` em vez de `s.stations` ... */)
    }
}
```

- `StationsUiState.Empty` (nível de `StationsUiState`, ver
  `StationsUiState.kt`) continua representando "0 resultados totais vindos
  da API" — não muda de significado.
- O novo estado vazio "0 depois do filtro de tipo" é local ao branch
  `Success` da tela, não um novo caso de `StationsUiState` — evita duplicar
  a máquina de estados por um filtro que é puramente de apresentação.

## UI: `StationCard` — redesign compacto

Estrutura em 2 linhas (era: badge + spacer + nome + spacer + botão
full-width):

```kotlin
FFCard(modifier = modifier) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = station.type.badgeContent().icon,
            contentDescription = station.type.badgeContent().contentDescription,
            tint = /* mesma cor usada hoje pela badge: warning p/ Fuel, info p/ Electric */,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = station.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatDistance(station.distanceMeters),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(FFTheme.spacing.xs))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (station.rating != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = FFTheme.semanticColors.warning,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    String.format(Locale("pt", "BR"), "%.1f", station.rating),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        } else {
            Spacer(Modifier.size(1.dp)) // mantém a Row com 2 filhos p/ SpaceBetween funcionar sem rating
        }
        IconButton(onClick = onRouteClick) {
            Icon(Icons.Filled.Navigation, contentDescription = "Traçar rota")
        }
    }
}
```

- Badge em pill (`StationTypeBadge` composable) é **removida** —
  `StationTypeBadgeContent`/`badgeContent()` continuam existindo (agora
  reaproveitados também por `StationTypeFilterRow`), mas o `Surface`
  colorido de pill sai do card.
- `rating` (`Double?`) — quando `null`, a linha 2 mostra só o `IconButton`
  de rota alinhado à direita (o `Spacer` de 1dp preserva a mesma altura de
  linha entre cards com e sem rating, evitando lista com alturas
  inconsistentes).
- `IconButton` (M3 puro, mesmo padrão já usado em `FFTopBar.kt` — não existe
  wrapper `FFIconButton` no design system) substitui o `FFButton` full-width
  anterior. Ícone exato (`Icons.Filled.Navigation`) e content description a
  confirmar/ajustar na fase de plano se a extended icons library tiver uma
  opção mais próxima de "traçar rota" (ex.: `Icons.AutoMirrored.Filled
  .DirectionsCar`).
- `onRouteClick` continua disparando `viewModel.onRouteClick(station)` sem
  mudança de comportamento (intent `google.navigation:`).

## Testes

Seguindo o padrão já estabelecido (`RobolectricTestRunner` + `@Config(sdk =
[33])` + MockK):

- `StationsViewModelTest`:
  - `selectedType` inicial é `StationType.Fuel` quando não há veículo
    ativo (`activeVehicleIdFlow` retorna `null`).
  - `selectedType` inicial é `StationType.Electric` quando o veículo ativo
    tem `energyType = Electric`; é `StationType.Fuel` para `Combustion` e
    para `Hybrid`.
  - Falha de `getVehicleById` não impede `load()` de rodar (cai no padrão
    `Fuel`).
  - `onTypeSelected` atualiza `selectedType` **sem** chamar `getNearbyStations`
    de novo (mock do use case verificando `verify(exactly = 1)` continua
    valendo após a troca de tipo).
- `StationTypeFilterRowTest` (novo): renderiza os 2 segmentos com
  ícone/rótulo corretos; segmento de `selectedType` aparece selecionado;
  clique no segmento não selecionado dispara `onSelect` com o valor certo.
- `StationCardTest`: atualizar para a nova estrutura — badge em pill não
  existe mais; ícone de tipo aparece inline antes do nome; rating exibido
  quando presente (texto formatado `%.1f`) e ausente quando `null`;
  `IconButton` de rota dispara `onRouteClick`.
- `StationsScreenTest` (se existir, senão criar): selecionar um tipo sem
  resultados mostra o `FFEmptyState` com o título específico do tipo
  (distinto do empty state genérico de "nenhum posto encontrado").

## Auto-revisão do spec

- **Sem placeholders:** todas as assinaturas, composables e testes estão
  nomeados explicitamente. A única lacuna intencional é o ícone exato do
  botão de rota (`Navigation` vs `DirectionsCar`), marcada como detalhe de
  implementação a confirmar na fase de plano — não afeta comportamento nem
  arquitetura.
- **Consistência interna:** o padrão "`StateFlow` próprio fora de
  `StationsUiState`, sem disparar `load()`" é coerente com o motivo dado
  (filtro client-side vs parâmetro de API); o mapeamento `EnergyType` →
  `StationType` é único e não se repete com valores divergentes em nenhuma
  seção.
- **Escopo:** uma única feature coesa (filtro de tipo + sync com veículo +
  redesign de card compacto, todos na mesma tela `StationsScreen`), sem
  necessidade de decomposição adicional — a decomposição maior (roadmap) já
  foi feita e fica fora deste documento.
- **Ambiguidade:** comportamento do `StationsUiState.Empty` genérico vs
  empty state por tipo foi explicitado (são coisas diferentes, o segundo é
  local ao branch `Success`); comportamento de falha na sincronização com
  veículo ativo foi explicitado (silenciosa, cai no padrão `Fuel`); altura
  do card com/sem rating foi explicitada (mantida constante via spacer).
