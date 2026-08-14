# Design: Pesquisa de bairro/cidade em Postos (Android)

**Data:** 2026-08-14
**Status:** Aprovado

## Contexto

Sub-projeto C de uma decomposição maior (ver conversa 2026-08-14). O
sub-projeto B já entregou e fez deploy do endpoint
`GET /stations/geocode?query={texto}` no backend (repo `flowfuel`,
commits `011a9f6`..`61dd47a`) — resolve bairro/cidade em até 5 candidatos
com nome completo desambiguado (`displayName`) e coordenadas.

A tela `StationsScreen` hoje só busca postos perto da localização GPS do
dispositivo (`LocationProvider.getCurrentLocation()`). Caso de uso real do
usuário: "estou no Cordeiro, Recife, preciso ir pra Boa Viagem, Recife,
quero saber os postos de Boa Viagem" — não há forma de buscar postos perto
de um lugar que não seja onde o usuário está agora.

**Restrição importante herdada do backend:** o endpoint de geocodificação
tem rate limit de 1 req/seg **agregado em todos os usuários do app**
(política do Nominatim). Isso descarta autocomplete "busca a cada letra
digitada" — a busca só pode disparar quando o usuário confirma
(Enter/botão), nunca em live-typing.

## Objetivo

Ícone de busca na tela de Postos que abre um bottom sheet de pesquisa de
localidade; ao escolher um candidato, a lista de postos recarrega usando
essa coordenada em vez do GPS, com um chip visível indicando que a busca
não está mais usando "minha localização".

## Escopo

Só o app Android (`feature/station/`). Não inclui nenhuma mudança no
backend (contrato já fechado no sub-projeto B) nem no fluxo de
"localização atual" existente, que continua sendo o padrão.

---

## Design

### Camada de dados

`StationApi.kt` — novo DTO e método, mesmo padrão de `getNearbyStations`:

```kotlin
@Serializable
data class GeocodeResultDto(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
)

interface StationApi {
    @GET("stations/nearby")
    suspend fun getNearbyStations(...): List<StationResponseDto>

    @GET("stations/geocode")
    suspend fun getGeocode(@Query("query") query: String): List<GeocodeResultDto>
}
```

Novo modelo de domínio `feature/station/domain/model/GeocodeResult.kt`:

```kotlin
package com.flowfuel.app.feature.station.domain.model

data class GeocodeResult(
    val displayName: String,
    val location: GeoLocation,
)
```

`StationRepository.kt` ganha um método novo:

```kotlin
interface StationRepository {
    suspend fun getNearbyStations(location: GeoLocation, radiusMeters: Int): AppResult<List<Station>>
    suspend fun geocode(query: String): AppResult<List<GeocodeResult>>
}
```

`StationRepositoryImpl.kt`:

```kotlin
override suspend fun geocode(query: String): AppResult<List<GeocodeResult>> =
    apiCall { api.getGeocode(query) }.map { list -> list.map { it.toDomain() } }

private fun GeocodeResultDto.toDomain(): GeocodeResult = GeocodeResult(
    displayName = displayName,
    location = GeoLocation(latitude = latitude, longitude = longitude),
)
```

Novo use case `feature/station/domain/usecase/GeocodeLocationsUseCase.kt`,
mesmo molde de `GetNearbyStationsUseCase`:

```kotlin
class GeocodeLocationsUseCase @Inject constructor(
    private val repository: StationRepository,
) {
    suspend operator fun invoke(query: String): AppResult<List<GeocodeResult>> =
        repository.geocode(query)
}
```

### Estado — `StationsUiState.kt`

Novo estado independente pro sheet de busca, mesmo padrão de
`VehicleSwitcherState` (Home):

```kotlin
sealed interface LocationSearchState {
    data object Idle : LocationSearchState
    data object Loading : LocationSearchState
    data class Success(val results: List<GeocodeResult>) : LocationSearchState
    data object Empty : LocationSearchState
    data class Error(val error: AppError) : LocationSearchState
}
```

### `StationsViewModel.kt`

Nova dependência `geocodeLocations: GeocodeLocationsUseCase`. Três novos
`StateFlow`:

```kotlin
private val _showLocationSearch = MutableStateFlow(false)
val showLocationSearch: StateFlow<Boolean> = _showLocationSearch.asStateFlow()

private val _locationSearchState = MutableStateFlow<LocationSearchState>(LocationSearchState.Idle)
val locationSearchState: StateFlow<LocationSearchState> = _locationSearchState.asStateFlow()

/** null = usa a localização atual (GPS, comportamento padrão); preenchido = busca ativa por localidade. */
private val _selectedLocation = MutableStateFlow<GeocodeResult?>(null)
val selectedLocation: StateFlow<GeocodeResult?> = _selectedLocation.asStateFlow()
```

`load()` passa a resolver a localização a partir de `_selectedLocation`
antes de cair no GPS — reaproveitando o wrapper `LocationResult.Available`
já existente, então o `when` que já trata Success/Empty/Error/
PermissionRequired/LocationUnavailable **não muda**:

```kotlin
fun load() {
    _state.value = StationsUiState.Loading
    val requestRadius = _radiusMeters.value
    val band = stationDistanceBand(requestRadius)
    val selected = _selectedLocation.value
    viewModelScope.launch {
        val locationResult = selected?.let { LocationResult.Available(it.location) }
            ?: locationProvider.getCurrentLocation()
        when (locationResult) {
            LocationResult.PermissionDenied -> _state.value = StationsUiState.PermissionRequired
            LocationResult.Unavailable -> _state.value = StationsUiState.LocationUnavailable
            is LocationResult.Available -> {
                when (val result = getNearbyStations(locationResult.location, band.maxMeters)) {
                    is AppResult.Success -> {
                        val stations = result.value.filter { it.distanceMeters >= band.minMeters }
                        _state.value = if (stations.isEmpty()) StationsUiState.Empty else StationsUiState.Success(stations)
                        // Só atualiza o cache de "perto de mim" quando a busca É por GPS —
                        // salvar resultados de uma localidade pesquisada corromperia o
                        // prefetch que a Home usa pra mostrar postos rapidamente.
                        if (requestRadius == DEFAULT_STATION_RADIUS_METERS && selected == null) {
                            stationsPrefetcher.updateCache(stations)
                        }
                    }
                    is AppResult.Failure -> handleFailure(result.error)
                }
            }
        }
    }
}
```

**Ponto de atenção que quase passou despercebido:** sem o `selected ==
null` na condição do `updateCache`, uma busca por "Boa Viagem" com o raio
padrão sobrescreveria o cache de "postos perto de mim" com postos de Boa
Viagem — na próxima vez que o usuário abrisse o app (sem GPS ainda
resolvido), veria postos da cidade errada por alguns segundos.

Novas funções públicas:

```kotlin
fun openLocationSearch() {
    _showLocationSearch.value = true
    _locationSearchState.value = LocationSearchState.Idle
}

fun closeLocationSearch() {
    _showLocationSearch.value = false
}

fun searchLocation(query: String) {
    if (query.isBlank()) return
    _locationSearchState.value = LocationSearchState.Loading
    viewModelScope.launch {
        when (val result = geocodeLocations(query)) {
            is AppResult.Success -> _locationSearchState.value =
                if (result.value.isEmpty()) LocationSearchState.Empty else LocationSearchState.Success(result.value)
            is AppResult.Failure -> _locationSearchState.value = LocationSearchState.Error(result.error)
        }
    }
}

fun onLocationSelected(result: GeocodeResult) {
    _selectedLocation.value = result
    _showLocationSearch.value = false
    load()
}

fun clearSelectedLocation() {
    _selectedLocation.value = null
    load()
}
```

### `LocationSearchBottomSheet.kt` (novo componente)

Mesmo molde de `VehicleSwitcherBottomSheet.kt` (Home): `FFBottomSheet` +
campo de busca no topo + lista de resultados abaixo, cobrindo
Idle/Loading/Success/Empty/Error.

```kotlin
@Composable
fun LocationSearchBottomSheet(
    state: LocationSearchState,
    onSearch: (String) -> Unit,
    onLocationSelected: (GeocodeResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    FFBottomSheet(onDismiss = onDismiss) {
        Text("Buscar localidade", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(FFTheme.spacing.md))

        FFTextField(
            value = query,
            onValueChange = { query = it },
            label = "Bairro ou cidade",
            placeholder = "Ex: Boa Viagem, Recife",
            leadingIcon = Icons.Default.Search,
            imeAction = ImeAction.Search,
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        )
        Spacer(Modifier.height(FFTheme.spacing.md))

        when (state) {
            LocationSearchState.Idle -> Unit
            LocationSearchState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
                repeat(3) { FFSkeletonBlock(height = 56.dp) }
            }
            is LocationSearchState.Success -> Column {
                state.results.forEach { result ->
                    ListItem(
                        headlineContent = { Text(result.displayName) },
                        leadingContent = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
                        modifier = Modifier.clickable { onLocationSelected(result) },
                    )
                }
            }
            LocationSearchState.Empty -> FFEmptyState(
                title = "Nenhum lugar encontrado",
                description = "Tente um nome diferente ou mais específico.",
            )
            is LocationSearchState.Error -> FFErrorState(
                message = state.error.userMessage(),
                onRetry = { onSearch(query) },
            )
        }
    }
}
```

`ListItem` é o componente padrão do Material3 (não precisa de componente
novo no design system pra um item de lista simples e único nesse fluxo).

### `StationsScreen.kt`

- `FFTopBar(title = "Postos próximos", actions = { IconButton(onClick = viewModel::openLocationSearch) { Icon(Icons.Default.Search, contentDescription = "Buscar localidade") } })`
- Renderiza `LocationSearchBottomSheet` quando `showLocationSearch` for `true`, passando `viewModel::searchLocation`, `viewModel::onLocationSelected`, `viewModel::closeLocationSearch`
- Logo abaixo dos filtros existentes (tipo/raio), quando `selectedLocation != null`, um `FFChip` de indicação:

```kotlin
selectedLocation?.let { location ->
    FFChip(
        label = location.displayName,
        kind = FFChipKind.Input,
        leadingIcon = Icons.Outlined.LocationOn,
        onClick = {},
        onTrailingClick = viewModel::clearSelectedLocation,
    )
}
```

## Comportamento de estados / edge cases

- **Sheet fecha automaticamente** ao escolher um candidato (`onLocationSelected` já seta `showLocationSearch = false`).
- **Erro de geocodificação** (Nominatim fora do ar, rate limit estourado): mostra `FFErrorState` dentro do próprio sheet, com retry — não afeta a lista de postos já carregada por trás.
- **Nenhum resultado** pra uma busca: estado `Empty` dedicado, mensagem própria (diferente do `Empty` de "nenhum posto encontrado").
- **Trocar de tipo/raio com uma localidade pesquisada ativa**: `load()` continua respeitando `_selectedLocation` — os filtros de tipo/raio funcionam normalmente sobre a localidade pesquisada, não só sobre o GPS.
- **Fechar o sheet sem escolher nada** (`closeLocationSearch`): não muda `_selectedLocation` nem recarrega a lista — comportamento no-op, mesmo padrão do `VehicleSwitcherBottomSheet`.
- **Permissão de localização negada com uma busca ativa**: não se aplica — `_selectedLocation` não-nulo pula `locationProvider.getCurrentLocation()` inteiramente, então `PermissionRequired`/`LocationUnavailable` só aparecem quando a busca é por GPS.

## Testes

- `GeocodeLocationsUseCaseTest` (se seguir o padrão de `GetNearbyStationsUseCase`, que não tem teste dedicado próprio — decisão de manter consistência será do plano de implementação).
- `StationRepositoryImplTest`: novo caso pra `geocode()`, mesmo padrão dos testes existentes de `getNearbyStations`.
- `StationsViewModelTest`: novos casos — `searchLocation` sucesso/vazio/erro; `onLocationSelected` seta `selectedLocation` e recarrega usando a coordenada escolhida (não chama `locationProvider`); `clearSelectedLocation` volta a usar GPS; `load()` com localidade ativa **não** atualiza o prefetch cache mesmo no raio padrão.
- Verificação manual no emulador (conta de teste QA): buscar "Boa Viagem", escolher um candidato, confirmar que a lista de postos muda pra essa região e que o chip aparece; tocar no ✕ do chip e confirmar que volta pros postos perto da localização atual.

## Arquivos alterados

| Arquivo | Mudança |
|---------|---------|
| `feature/station/data/remote/StationApi.kt` | Novo `GeocodeResultDto` + `getGeocode` |
| `feature/station/domain/model/GeocodeResult.kt` | Novo — modelo de domínio |
| `feature/station/domain/StationRepository.kt` | Novo método `geocode` |
| `feature/station/data/StationRepositoryImpl.kt` | Implementa `geocode` |
| `feature/station/domain/usecase/GeocodeLocationsUseCase.kt` | Novo use case |
| `feature/station/presentation/list/StationsUiState.kt` | Novo `LocationSearchState` |
| `feature/station/presentation/list/StationsViewModel.kt` | Nova dependência, 3 novos `StateFlow`, `load()` passa a checar `_selectedLocation`, 4 novas funções públicas |
| `feature/station/presentation/list/LocationSearchBottomSheet.kt` | Novo componente |
| `feature/station/presentation/list/StationsScreen.kt` | Ícone de busca no `FFTopBar`, renderiza o sheet, chip de localidade ativa |
| Testes correspondentes | `StationRepositoryImplTest`, `StationsViewModelTest` estendidos |

## Fora do escopo

- Autocomplete / busca em tempo real conforme digita (bloqueado pelo rate
  limit global do backend, ver Contexto)
- Histórico de buscas recentes
- Persistir a localidade pesquisada entre sessões do app (sempre volta
  pro GPS ao reabrir)
- Qualquer mudança no backend (contrato já fechado no sub-projeto B)
