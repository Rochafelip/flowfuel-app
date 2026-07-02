# Design: Pré-carregamento em background da lista de Postos

## Contexto

A tela "Postos" (`StationsViewModel.load()`) busca localização (GPS) e
depois a lista de postos via `GetNearbyStationsUseCase` de forma
sequencial, só quando a tela é composta pela primeira vez na sessão. Como
a navegação por bottom-nav já usa `saveState`/`restoreState` (padrão em
`MainContainerScreen.kt`), o `StationsViewModel` sobrevive a trocas de aba
— o atraso percebido pelo usuário é, na prática, o da primeira visita à
aba em cada sessão do app (fix de GPS + chamada de rede, ambos síncronos
dentro de `load()`).

Esta rodada não muda a UI nem o comportamento de filtro/raio já
implementados (`docs/superpowers/specs/2026-07-02-postos-filtro-tipo-card-compacto-design.md`).
É uma mudança de arquitetura interna: buscar os dados **antes** do usuário
abrir a aba, para que ela já nasça com conteúdo.

**Fora deste documento:** exibir rua/número no card de cada posto foi
levantado na mesma conversa, mas exige mudança de contrato na API
(`StationResponseDto`) e no backend (repositório separado, Spring/Maven em
`~/Projetos/flowfuel` no WSL). Fica como um spec independente, a ser
desenhado separadamente.

## Objetivo

Quando o usuário abrir a aba "Postos", se já existir um resultado recente
(mesmo raio padrão) pré-carregado em cache, mostrar a lista imediatamente,
sem skeleton de loading e sem nova chamada de rede.

## Escopo

### Dentro

- Novo componente `NearbyStationsPrefetcher` (`@Singleton`): mantém em
  memória o último resultado bem-sucedido de `getNearbyStations` no raio
  padrão (`DEFAULT_STATION_RADIUS_METERS`), com timestamp de quando foi
  obtido.
- Nova abstração `Clock` (`core/common/Clock.kt`): `nowMillis(): Long`,
  com implementação real `SystemClock`. Necessária para testar expiração
  de cache de forma determinística (não existe nada equivalente no
  projeto hoje).
- Gatilhos de prefetch em `HomeViewModel` (tela inicial do app): dispara
  em `load()` (abertura do app/retomada de sessão) e em
  `onVehicleSwitch()` (troca de veículo ativo) — mesmos pontos onde a Home
  já resincroniza seu próprio dashboard.
- Leitura do cache em `StationsViewModel.init`: se houver cache válido
  (dentro do TTL) no raio padrão, popula o estado direto (`Success`/
  `Empty`), sem chamar `locationProvider`/`getNearbyStations`. Caso
  contrário, cai no `load()` já existente (skeleton normal).
- `load()` bem-sucedido no raio padrão também atualiza o cache
  compartilhado (mantém o cache quente após um pull-to-refresh manual, por
  exemplo).
- TTL de cache: 5 minutos (`300_000` ms), constante interna do
  `NearbyStationsPrefetcher`.

### Fora (não mexer nesta rodada)

- Qualquer mudança de contrato de backend.
- Qualquer mudança visual em `StationsScreen`/`StationCard`/filtros — o
  usuário não percebe nada diferente na UI além da ausência do skeleton
  quando o cache está quente.
- Refresh periódico contínuo em background (ex.: a cada N minutos com o
  app aberto) — avaliado e descartado nesta rodada; só os gatilhos
  explícitos acima disparam prefetch.
- Cache para raios não-padrão (1/3/10 km) — só o raio padrão (5 km, usado
  no primeiro load de cada `StationsViewModel`) é cacheado; trocar de raio
  manualmente sempre dispara uma busca nova, como hoje.

## Arquitetura

### `NearbyStationsPrefetcher`

`app/src/main/java/com/flowfuel/app/feature/station/domain/NearbyStationsPrefetcher.kt`

```kotlin
@Singleton
class NearbyStationsPrefetcher @Inject constructor(
    private val getNearbyStations: GetNearbyStationsUseCase,
    private val locationProvider: LocationProvider,
    private val clock: Clock,
    @IoDispatcher dispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var activeJob: Job? = null

    private data class CachedStations(val stations: List<Station>, val fetchedAtMillis: Long)
    private val cache = MutableStateFlow<CachedStations?>(null)

    fun prefetch() {
        activeJob?.cancel()
        activeJob = scope.launch {
            when (val locationResult = locationProvider.getCurrentLocation()) {
                is LocationResult.Available -> {
                    val result = getNearbyStations(locationResult.location, DEFAULT_STATION_RADIUS_METERS)
                    if (result is AppResult.Success) {
                        cache.value = CachedStations(result.value, clock.nowMillis())
                    }
                    // Failure: no-op, mantém cache anterior.
                }
                // PermissionDenied/Unavailable: no-op, mantém cache anterior.
                else -> Unit
            }
        }
    }

    fun freshCachedStations(): List<Station>? {
        val entry = cache.value ?: return null
        return entry.stations.takeIf { clock.nowMillis() - entry.fetchedAtMillis <= CACHE_TTL_MILLIS }
    }

    fun updateCache(stations: List<Station>) {
        cache.value = CachedStations(stations, clock.nowMillis())
    }

    private companion object {
        const val CACHE_TTL_MILLIS = 5 * 60 * 1000L
    }
}
```

Usa o `@IoDispatcher` já provido por `core/common/Dispatchers.kt` (não
introduz um novo qualifier). `scope` é próprio do singleton — não amarrado
ao ciclo de vida de nenhuma `ViewModel`, então um prefetch iniciado a
partir da Home continua rodando mesmo que a `HomeViewModel` seja limpa.

### `Clock`

`app/src/main/java/com/flowfuel/app/core/common/Clock.kt`

```kotlin
interface Clock {
    fun nowMillis(): Long
}

class SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
```

Provido direto no `DispatcherModule` já existente (`core/common/
Dispatchers.kt`), que já é um `object` com `@Provides` — não precisa de
`@Binds`/módulo abstrato novo nem de `@Inject constructor` em
`SystemClock` (não tem dependências):

```kotlin
@Provides @Singleton
fun clock(): Clock = SystemClock()
```

### `HomeViewModel`

Ganha `private val stationsPrefetcher: NearbyStationsPrefetcher` como novo
parâmetro de construtor. Chama `stationsPrefetcher.prefetch()` (sem
`await`, fire-and-forget) em dois pontos:
- Início de `load()` (não precisa esperar o resultado do veículo/dashboard
  — a busca de postos independe deles).
- Em `onVehicleSwitch()`, junto com `setActiveVehicle(vehicleId)`.

### `StationsViewModel`

Ganha `private val stationsPrefetcher: NearbyStationsPrefetcher` como novo
parâmetro de construtor (5º parâmetro). `init` passa a ser:

```kotlin
init {
    viewModelScope.launch {
        sessionStore.activeVehicleIdFlow.firstOrNull()?.let { vehicleId ->
            // ... sincronização de selectedType, inalterada ...
        }
        val cached = stationsPrefetcher.freshCachedStations()
        if (cached != null) {
            _state.value = if (cached.isEmpty()) StationsUiState.Empty else StationsUiState.Success(cached)
        } else {
            load()
        }
    }
}
```

E `load()` passa a atualizar o cache compartilhado quando o raio da busca
é o padrão:

```kotlin
is AppResult.Success -> {
    _state.value = if (result.value.isEmpty()) StationsUiState.Empty else StationsUiState.Success(result.value)
    if (_radiusMeters.value == DEFAULT_STATION_RADIUS_METERS) {
        stationsPrefetcher.updateCache(result.value)
    }
}
```

## Tratamento de erro / casos de borda

- **Sem permissão de localização ou GPS indisponível durante o prefetch:**
  não faz nada, mantém o cache anterior (se houver) intacto. A tela
  Postos, ao abrir sem cache válido, cai no fluxo de permissão/erro já
  existente normalmente.
- **API falha durante o prefetch:** idem, silencioso, sem efeito visível
  (mesmo padrão de "fail-open silencioso" já usado em `HomeViewModel
  .refresh()`).
- **Prefetch disparado duas vezes rapidamente** (ex.: login seguido de
  troca de veículo antes do primeiro terminar): o `prefetch()` cancela
  qualquer job anterior ainda em andamento antes de iniciar um novo —
  evita duas buscas concorrentes escrevendo no mesmo cache.
- **Cache expirado (> 5 min):** tratado como cache-miss; `StationsViewModel`
  cai no `load()` normal, com skeleton.
- **Cache seria usado, mas o raio da `StationsViewModel` não é o padrão:**
  não se aplica — `radiusMeters` sempre começa em
  `DEFAULT_STATION_RADIUS_METERS` numa `ViewModel` recém-criada, então a
  checagem de cache no `init` sempre compara contra o raio padrão.

## Testes

- **`NearbyStationsPrefetcherTest`** (novo, sem Robolectric — só
  corrotinas + `Clock` fake):
  - `prefetch()` bem-sucedido grava o resultado no cache com o timestamp
    do `Clock`.
  - `prefetch()` com `PermissionDenied`/`Unavailable`/`Failure` não altera
    um cache pré-existente.
  - `freshCachedStations()` retorna a lista quando dentro do TTL e `null`
    quando o `Clock` avança além de 5 minutos.
  - Duas chamadas sequenciais a `prefetch()` — a segunda sobrescreve o
    cache com seu próprio resultado (não testa cancelamento real
    concorrente, só o resultado final).
- **`StationsViewModelTest`** (adições): cache fresco no `init` popula
  `Success`/`Empty` direto, com `coVerify(inverse = true)` confirmando que
  `locationProvider.getCurrentLocation()` e `getNearbyStations` não foram
  chamados; sem cache (stub padrão retornando `null` no `setUp()`, para
  não quebrar os testes já existentes) cai no `load()` de sempre; `load()`
  bem-sucedido no raio padrão chama `stationsPrefetcher.updateCache(...)`;
  em raio não-padrão, não chama.
- **`HomeViewModelTest`** (adições): `load()` e `onVehicleSwitch()`
  chamam `stationsPrefetcher.prefetch()`.

## Riscos identificados

- Introduz uma dependência de `feature/home` em direção a
  `feature/station` (a Home passa a conhecer `NearbyStationsPrefetcher`).
  Consistente com o estilo já usado no projeto (ex.: `StationsViewModel`
  já depende de `GetVehicleByIdUseCase` do módulo `vehicle`), mas vale
  registrar.
- TTL fixo de 5 minutos é um valor arbitrário — fácil de ajustar depois se
  5 minutos se mostrar curto ou longo demais na prática.
