# Design: Filtro de raio + redesign do StationCard (Postos)

## Contexto

A tela "Postos" (ver `docs/superpowers/specs/2026-07-01-postos-proximos-design.md`)
está implementada e integrada com o backend real
(`GET /stations/nearby`). O raio de busca hoje é fixo em 5000m,
hardcoded em `StationRepositoryImpl` (`DEFAULT_RADIUS_METERS`), sem nenhum
jeito do usuário mudar. `StationApi.kt` já aceita `radius` como query param —
só falta expor isso na UI e threadar pelas camadas de domínio/apresentação.

Esta rodada também revisita o visual do `StationCard`, que hoje usa `Card`
(M3) genérico em vez do `FFCard` do design system, e diferencia
combustível/elétrico só pelo ícone.

## Objetivo

1. Permitir ao usuário escolher o raio de busca (1/3/5/10 km) via chips
   abaixo da top bar, com resultado atualizado imediatamente.
2. Redesenhar `StationCard` para usar `FFCard` e diferenciar
   Combustível/Elétrico com uma badge colorida (Opção B validada com o
   usuário via mockup), com botão "Traçar rota" preenchido.

## Escopo

### Dentro

- Fileira de chips de raio (`StationDistanceFilterRow`), local ao módulo
  `feature/station`, seguindo o padrão de fileira de filtros já usado em
  `EventDateFilterRow`/`EventCategoryFilterRow` (chips M3 crus em
  `LazyRow`) — **não** extrai componente compartilhado no design system
  nesta rodada.
- Threading de `radiusMeters: Int` por toda a cadeia:
  `StationsViewModel` → `GetNearbyStationsUseCase` → `StationRepository` →
  `StationRepositoryImpl` → `StationApi` (o parâmetro de query já existe).
- Redesign do `StationCard`: migração de `Card` → `FFCard`, badge de tipo
  (ícone + rótulo "Combustível"/"Elétrico") colorida via
  `FFTheme.semanticColors` (`warning` para Combustível, `info` para
  Elétrico — reaproveita tokens existentes, sem cor nova), distância no
  canto oposto da badge, nome do posto abaixo, e `FFButton(variant =
  Primary)` (era `Secondary`) full-width para "Traçar rota".

### Fora (não mexer nesta rodada)

- Top bar, espaçamento geral da tela, estados de loading/erro/empty
  (`FFSkeletonList`/`FFEmptyState`/`FFErrorState`) — ficam como estão.
- Persistência do raio selecionado entre sessões do app (fica só em memória
  no `StationsViewModel`; ao reabrir o app volta para o padrão 5 km).
- Qualquer refactor dos outros 3 filtros hand-rolled existentes
  (`EventCategoryFilterRow`, `EventDateFilterRow`, filtro inline em
  `VehiclesScreen`) — reconhecido como débito técnico, mas fora de escopo
  aqui.
- Raio "personalizado"/livre — só os 4 presets fixos, alinhado com a
  recomendação do contrato de API verificado (raios grandes são mais
  lentos/instáveis no backend).

## Arquitetura: threading do raio

Constantes centralizadas em `feature/station/domain/model/StationRadius.kt`
(novo arquivo), para não duplicar o valor default em múltiplos lugares:

```kotlin
val STATION_RADIUS_PRESETS_METERS = listOf(1_000, 3_000, 5_000, 10_000)
const val DEFAULT_STATION_RADIUS_METERS = 5_000
```

Assinaturas atualizadas:

```kotlin
// domain/StationRepository.kt
interface StationRepository {
    suspend fun getNearbyStations(
        location: GeoLocation,
        radiusMeters: Int,
    ): AppResult<List<Station>>
}

// domain/usecase/GetNearbyStationsUseCase.kt
class GetNearbyStationsUseCase @Inject constructor(
    private val repository: StationRepository,
) {
    suspend operator fun invoke(
        location: GeoLocation,
        radiusMeters: Int,
    ): AppResult<List<Station>> = repository.getNearbyStations(location, radiusMeters)
}

// data/StationRepositoryImpl.kt — remove a constante local DEFAULT_RADIUS_METERS,
// passa radiusMeters recebido direto pro StationApi (já suporta o query param).
override suspend fun getNearbyStations(
    location: GeoLocation,
    radiusMeters: Int,
): AppResult<List<Station>> = apiCall {
    api.getNearbyStations(location.latitude, location.longitude, radiusMeters)
}.map { list -> list.map { it.toDomain() }.sortedBy { it.distanceMeters } }
```

`StationsViewModel` ganha um `StateFlow<Int>` independente do
`StationsUiState` (para sobreviver a transições Loading/Error/Success sem
perder a seleção do usuário):

```kotlin
private val _radiusMeters = MutableStateFlow(DEFAULT_STATION_RADIUS_METERS)
val radiusMeters: StateFlow<Int> = _radiusMeters.asStateFlow()

fun onRadiusSelected(radiusMeters: Int) {
    _radiusMeters.value = radiusMeters
    load()
}
```

`load()` passa a ler `_radiusMeters.value` e repassar pro use case em vez do
valor fixo.

## UI: `StationDistanceFilterRow`

Novo arquivo `feature/station/presentation/list/StationDistanceFilterRow.kt`:

```kotlin
@Composable
fun StationDistanceFilterRow(
    selectedRadiusMeters: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
)
```

- `LazyRow` de `FilterChip` (M3), um por valor de
  `STATION_RADIUS_PRESETS_METERS`, rótulo formatado como "1 km"/"3
  km"/"5 km"/"10 km".
- Sem cor por item (diferente de `EventCategoryFilterRow`, que mapeia cor por
  categoria) — os valores de raio não são categóricos, então usa o estilo
  padrão de seleção do M3 `FilterChip` (mesmo approach do
  `EventDateFilterRow`).
- `contentPadding`/`horizontalArrangement` seguindo o mesmo padrão visual das
  outras filter rows (`Arrangement.spacedBy(8.dp)`,
  `PaddingValues(horizontal = 16.dp)`).

Em `StationsScreen.kt`: renderizado logo abaixo do `FFTopBar`, visível em
todos os estados **exceto** `PermissionRequired` (não faz sentido filtrar
antes de ter localização). Ao selecionar um chip, chama
`viewModel.onRadiusSelected(radius)`, que já dispara um novo `load()` —
sem necessidade de debounce (é uma seleção discreta via toque, não um
slider/scroll contínuo).

## UI: `StationCard` — redesign (Opção B)

Estrutura (substitui o `Card` M3 atual por `FFCard`):

```kotlin
FFCard(modifier = modifier) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationTypeBadge(station.type)        // ícone + "Combustível"/"Elétrico"
        Text(formatDistance(station.distanceMeters), style = MaterialTheme.typography.labelMedium)
    }
    Spacer(Modifier.height(FFTheme.spacing.xs))
    Text(station.name, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(FFTheme.spacing.sm))
    FFButton(
        text = "Traçar rota",
        onClick = onRouteClick,
        variant = FFButtonVariant.Primary,
        modifier = Modifier.fillMaxWidth(),
    )
}
```

`StationTypeBadge` (novo composable privado ou em arquivo próprio, a
definir na fase de plano): pequeno `Surface`/`Row` com o ícone existente
(`LocalGasStation`/`EvStation`, mesmas content descriptions já usadas —
"Posto de combustível"/"Estação de recarga elétrica") + texto
"Combustível"/"Elétrico", usando o par container/content de
`FFTheme.semanticColors.warning` (Combustível) ou
`.info` (Elétrico) — os nomes exatos dos campos desse par devem ser
conferidos em `core/designsystem/theme/Theme.kt` na hora de escrever o
plano (não foram confirmados nesta pesquisa, só que o objeto existe com
`success/warning/info/brandGreen`).

Rating (`rating != null`) continua não exibido no design atual — nenhuma
mudança aqui, já tratado como campo raramente presente.

## Testes

Seguindo o padrão já estabelecido no projeto (`RobolectricTestRunner` +
`@Config(sdk = [33])` + MockK, testes Compose existentes como
`StationCardTest.kt`):

- `StationRepositoryImplTest`: atualizar chamadas para passar
  `radiusMeters` explícito e verificar que é repassado ao `StationApi` sem
  alteração.
- `StationsViewModelTest`: `radiusMeters` inicial é
  `DEFAULT_STATION_RADIUS_METERS`; `onRadiusSelected` atualiza o
  `StateFlow` e dispara novo `load()` com o valor novo (mock do use case
  verificando o argumento recebido).
- `StationDistanceFilterRowTest` (novo): renderiza os 4 chips com os
  rótulos corretos; chip do `selectedRadiusMeters` aparece selecionado;
  clique num chip não selecionado dispara `onSelect` com o valor certo.
- `StationCardTest`: atualizar para a nova estrutura (badge visível com
  texto/ícone certo por tipo, distância formatada no lugar novo, botão
  "Traçar rota" com variante Primary).

## Auto-revisão do spec

- Sem placeholders: todas as assinaturas, presets, cores (por token
  semântico existente) e arquivos estão nomeados explicitamente — a única
  lacuna intencional é o nome exato dos campos de `semanticColors.warning`/
  `.info`, marcada para verificação na fase de plano (não é um "TBD" de
  design, é um detalhe de implementação a confirmar lendo código).
- Consistência interna: o threading de `radiusMeters` é o mesmo em todas as
  camadas citadas (repository/usecase/viewmodel), sem nomes divergentes.
- Escopo: uma única feature coesa (filtro de raio + redesign de card na
  mesma tela), não precisa de decomposição em specs separadas.
- Ambiguidade: comportamento do chip row em cada `StationsUiState` foi
  explicitado (visível em todos exceto `PermissionRequired`); estilo de
  seleção dos chips (sem cor por item) foi explicitado para evitar
  divergência do padrão de `EventCategoryFilterRow`.
