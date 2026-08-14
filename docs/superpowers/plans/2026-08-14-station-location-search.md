# Pesquisa de bairro/cidade em Postos (Android) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ícone de busca na tela de Postos que abre um bottom sheet de pesquisa de bairro/cidade (via `GET /stations/geocode`, já em produção); ao escolher um candidato, a lista de postos recarrega usando essa coordenada em vez do GPS, com um chip indicando a busca ativa.

**Architecture:** Nova camada de dados espelhando `getNearbyStations` (DTO → domínio → use case). `StationsViewModel` ganha um `StateFlow<GeocodeResult?>` que, quando preenchido, desvia `load()` do GPS pra essa coordenada, reaproveitando o `when` de estados já existente via o wrapper `LocationResult.Available`. UI nova é um bottom sheet no mesmo molde do `VehicleSwitcherBottomSheet` da Home.

**Tech Stack:** Kotlin, Jetpack Compose, MockK + JUnit + Robolectric + Turbine.

## Global Constraints

- Design aprovado em `docs/superpowers/specs/2026-08-14-station-location-search-design.md`.
- Busca só dispara por ação explícita (Enter/botão) — nunca a cada tecla digitada (rate limit global de 1/seg no backend).
- `load()` com localidade pesquisada ativa **nunca** deve sobrescrever o prefetch cache de "postos perto de mim" (`stationsPrefetcher.updateCache`) — só quando a busca é por GPS.
- Nenhuma mudança no backend — contrato de `/stations/geocode` já fechado e em produção (sub-projeto B).
- Sub-projeto C de 3 (A e B já implementados e pushed).

---

### Task 1: Camada de dados — `geocode` no repositório

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/data/remote/StationApi.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/station/domain/model/GeocodeResult.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/domain/StationRepository.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/data/StationRepositoryImpl.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/station/domain/usecase/GeocodeLocationsUseCase.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/station/data/StationRepositoryImplTest.kt`

**Interfaces:**
- Produces: `GeocodeResult(displayName: String, location: GeoLocation)`, consumido pela Task 2 (`StationsViewModel`) e Task 3 (UI).
- Produces: `GeocodeLocationsUseCase(query: String): AppResult<List<GeocodeResult>>`, consumido pela Task 2.

- [ ] **Step 1: Escrever o teste que falha**

Em `app/src/test/java/com/flowfuel/app/feature/station/data/StationRepositoryImplTest.kt`, adicionar o import:

```kotlin
import com.flowfuel.app.feature.station.data.remote.GeocodeResultDto
```

Adicionar os testes, ao final da classe (antes do `}` de fechamento):

```kotlin
    @Test
    fun `geocode maps dtos to domain`() = runTest {
        coEvery { api.getGeocode(any()) } returns listOf(
            GeocodeResultDto(displayName = "Boa Viagem, Recife, Pernambuco, Brasil", latitude = -8.12, longitude = -34.90),
        )

        val result = repository.geocode("Boa Viagem, Recife")

        assertTrue(result is AppResult.Success)
        val results = (result as AppResult.Success).value
        assertEquals(1, results.size)
        assertEquals("Boa Viagem, Recife, Pernambuco, Brasil", results[0].displayName)
        assertEquals(GeoLocation(-8.12, -34.90), results[0].location)
    }

    @Test
    fun `geocode returns empty list when backend has no matches`() = runTest {
        coEvery { api.getGeocode(any()) } returns emptyList()

        val result = repository.geocode("lugarquenaoexiste")

        assertTrue(result is AppResult.Success)
        assertEquals(emptyList<Any>(), (result as AppResult.Success).value)
    }

    @Test
    fun `geocode maps network failure to AppError-Network`() = runTest {
        coEvery { api.getGeocode(any()) } throws IOException("no network")

        val result = repository.geocode("Boa Viagem")

        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.Network, (result as AppResult.Failure).error)
    }

    @Test
    fun `geocode forwards query to the API unchanged`() = runTest {
        coEvery { api.getGeocode(any()) } returns emptyList()

        repository.geocode("Boa Viagem, Recife")

        coVerify { api.getGeocode("Boa Viagem, Recife") }
    }
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.station.data.StationRepositoryImplTest"`

Expected: FAIL na compilação — `GeocodeResultDto`, `api.getGeocode`, `repository.geocode` não existem ainda.

- [ ] **Step 3: Implementar a camada de dados**

Em `app/src/main/java/com/flowfuel/app/feature/station/data/remote/StationApi.kt`, adicionar o DTO e o método:

```kotlin
@Serializable
data class GeocodeResultDto(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
)

interface StationApi {
    @GET("stations/nearby")
    suspend fun getNearbyStations(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radiusMeters: Int = 5000,
    ): List<StationResponseDto>

    @GET("stations/geocode")
    suspend fun getGeocode(@Query("query") query: String): List<GeocodeResultDto>
}
```

Criar `app/src/main/java/com/flowfuel/app/feature/station/domain/model/GeocodeResult.kt`:

```kotlin
package com.flowfuel.app.feature.station.domain.model

data class GeocodeResult(
    val displayName: String,
    val location: GeoLocation,
)
```

Em `app/src/main/java/com/flowfuel/app/feature/station/domain/StationRepository.kt`:

```kotlin
package com.flowfuel.app.feature.station.domain

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.GeocodeResult
import com.flowfuel.app.feature.station.domain.model.Station

interface StationRepository {
    suspend fun getNearbyStations(location: GeoLocation, radiusMeters: Int): AppResult<List<Station>>
    suspend fun geocode(query: String): AppResult<List<GeocodeResult>>
}
```

Em `app/src/main/java/com/flowfuel/app/feature/station/data/StationRepositoryImpl.kt`, adicionar os imports:

```kotlin
import com.flowfuel.app.feature.station.data.remote.GeocodeResultDto
import com.flowfuel.app.feature.station.domain.model.GeocodeResult
```

E o método + mapper, logo após `getNearbyStations`:

```kotlin
    override suspend fun geocode(query: String): AppResult<List<GeocodeResult>> =
        apiCall { api.getGeocode(query) }.map { list -> list.map { it.toDomain() } }

    private fun GeocodeResultDto.toDomain(): GeocodeResult = GeocodeResult(
        displayName = displayName,
        location = GeoLocation(latitude = latitude, longitude = longitude),
    )
```

Criar `app/src/main/java/com/flowfuel/app/feature/station/domain/usecase/GeocodeLocationsUseCase.kt`:

```kotlin
package com.flowfuel.app.feature.station.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.StationRepository
import com.flowfuel.app.feature.station.domain.model.GeocodeResult
import javax.inject.Inject

class GeocodeLocationsUseCase @Inject constructor(
    private val repository: StationRepository,
) {
    suspend operator fun invoke(query: String): AppResult<List<GeocodeResult>> =
        repository.geocode(query)
}
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.station.data.StationRepositoryImplTest"`

Expected: PASS — todos os testes (existentes + os 4 novos) verdes.

- [ ] **Step 5: Compilar o projeto inteiro**

Run: `./gradlew.bat compileDebugKotlin compileDebugUnitTestKotlin -q`

Expected: sem erros.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/data/remote/StationApi.kt \
        app/src/main/java/com/flowfuel/app/feature/station/domain/model/GeocodeResult.kt \
        app/src/main/java/com/flowfuel/app/feature/station/domain/StationRepository.kt \
        app/src/main/java/com/flowfuel/app/feature/station/data/StationRepositoryImpl.kt \
        app/src/main/java/com/flowfuel/app/feature/station/domain/usecase/GeocodeLocationsUseCase.kt \
        app/src/test/java/com/flowfuel/app/feature/station/data/StationRepositoryImplTest.kt
git commit -m "feat(stations): add geocode data layer (API, domain, use case)"
```

---

### Task 2: `StationsViewModel` — estado de busca + desvio de localização

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsUiState.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModel.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModelTest.kt`

**Interfaces:**
- Consumes: `GeocodeLocationsUseCase`, `GeocodeResult` (Task 1).
- Produces: `StationsViewModel.showLocationSearch/locationSearchState/selectedLocation: StateFlow<...>`, `openLocationSearch()/closeLocationSearch()/searchLocation(query)/onLocationSelected(result)/clearSelectedLocation()`, consumidos pela Task 3.

- [ ] **Step 1: Escrever os testes que falham**

Em `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsUiState.kt`, adicionar o import e o novo sealed interface:

```kotlin
import com.flowfuel.app.feature.station.domain.model.GeocodeResult
```

```kotlin
sealed interface LocationSearchState {
    data object Idle : LocationSearchState
    data object Loading : LocationSearchState
    data class Success(val results: List<GeocodeResult>) : LocationSearchState
    data object Empty : LocationSearchState
    data class Error(val error: AppError) : LocationSearchState
}
```

Em `app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModelTest.kt`, adicionar os imports:

```kotlin
import com.flowfuel.app.feature.station.domain.model.GeocodeResult
import com.flowfuel.app.feature.station.domain.usecase.GeocodeLocationsUseCase
```

Adicionar o mock e atualizar `buildViewModel()`:

```kotlin
    private val geocodeLocations: GeocodeLocationsUseCase = mockk()
```

```kotlin
    private fun buildViewModel() =
        StationsViewModel(getNearbyStations, locationProvider, sessionStore, getVehicleById, stationsPrefetcher, geocodeLocations)
```

Adicionar os novos testes, ao final da classe (antes do `}` de fechamento):

```kotlin
    @Test
    fun `showLocationSearch starts false`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))

        val vm = buildViewModel()

        assertEquals(false, vm.showLocationSearch.value)
    }

    @Test
    fun `openLocationSearch shows the sheet and resets locationSearchState to Idle`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))
        val vm = buildViewModel()

        vm.openLocationSearch()

        assertEquals(true, vm.showLocationSearch.value)
        assertEquals(LocationSearchState.Idle, vm.locationSearchState.value)
    }

    @Test
    fun `searchLocation populates locationSearchState with results`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))
        val results = listOf(GeocodeResult("Boa Viagem, Recife, Pernambuco, Brasil", GeoLocation(-8.12, -34.90)))
        coEvery { geocodeLocations("Boa Viagem") } returns AppResult.Success(results)
        val vm = buildViewModel()

        vm.searchLocation("Boa Viagem")

        assertEquals(LocationSearchState.Success(results), vm.locationSearchState.value)
    }

    @Test
    fun `searchLocation with no matches sets Empty`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))
        coEvery { geocodeLocations("zzz") } returns AppResult.Success(emptyList())
        val vm = buildViewModel()

        vm.searchLocation("zzz")

        assertEquals(LocationSearchState.Empty, vm.locationSearchState.value)
    }

    @Test
    fun `searchLocation failure sets Error`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))
        coEvery { geocodeLocations("Boa Viagem") } returns AppResult.Failure(AppError.Network)
        val vm = buildViewModel()

        vm.searchLocation("Boa Viagem")

        assertEquals(LocationSearchState.Error(AppError.Network), vm.locationSearchState.value)
    }

    @Test
    fun `searchLocation ignores blank query`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))
        val vm = buildViewModel()

        vm.searchLocation("   ")

        assertEquals(LocationSearchState.Idle, vm.locationSearchState.value)
        coVerify(inverse = true) { geocodeLocations(any()) }
    }

    @Test
    fun `onLocationSelected sets selectedLocation, closes the sheet and reloads stations using the picked coordinate`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))
        val boaViagem = GeocodeResult("Boa Viagem, Recife, Pernambuco, Brasil", GeoLocation(-8.12, -34.90))
        coEvery { getNearbyStations(boaViagem.location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("b")))
        val vm = buildViewModel()
        vm.openLocationSearch()

        vm.onLocationSelected(boaViagem)

        assertEquals(boaViagem, vm.selectedLocation.value)
        assertEquals(false, vm.showLocationSearch.value)
        val state = vm.state.value
        assertTrue(state is StationsUiState.Success)
        assertEquals(listOf(station("b")), (state as StationsUiState.Success).stations)
        coVerify(exactly = 1) { locationProvider.getCurrentLocation() } // só a carga inicial, não a de Boa Viagem
    }

    @Test
    fun `onLocationSelected at default radius does not overwrite the nearby-me prefetch cache`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))
        val boaViagem = GeocodeResult("Boa Viagem, Recife, Pernambuco, Brasil", GeoLocation(-8.12, -34.90))
        coEvery { getNearbyStations(boaViagem.location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("b")))
        val vm = buildViewModel()

        vm.onLocationSelected(boaViagem)

        verify(exactly = 1) { stationsPrefetcher.updateCache(any()) } // só a carga inicial (GPS)
    }

    @Test
    fun `clearSelectedLocation reverts to the current GPS location`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))
        val boaViagem = GeocodeResult("Boa Viagem, Recife, Pernambuco, Brasil", GeoLocation(-8.12, -34.90))
        coEvery { getNearbyStations(boaViagem.location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("b")))
        val vm = buildViewModel()
        vm.onLocationSelected(boaViagem)

        vm.clearSelectedLocation()

        assertEquals(null, vm.selectedLocation.value)
        coVerify(exactly = 2) { locationProvider.getCurrentLocation() } // carga inicial + após limpar
    }
```

- [ ] **Step 2: Rodar os testes e confirmar que falham**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationsViewModelTest"`

Expected: FAIL na compilação — `LocationSearchState`, `geocodeLocations`, os novos métodos e `StateFlow`s não existem ainda; construtor de `StationsViewModel` não bate com o número de argumentos.

- [ ] **Step 3: Implementar em `StationsViewModel.kt`**

Adicionar os imports:

```kotlin
import com.flowfuel.app.feature.station.domain.model.GeocodeResult
import com.flowfuel.app.feature.station.domain.usecase.GeocodeLocationsUseCase
```

Adicionar o parâmetro no construtor, ao final:

```kotlin
@HiltViewModel
class StationsViewModel @Inject constructor(
    private val getNearbyStations: GetNearbyStationsUseCase,
    private val locationProvider: LocationProvider,
    private val sessionStore: SessionStore,
    private val getVehicleById: GetVehicleByIdUseCase,
    private val stationsPrefetcher: NearbyStationsPrefetcher,
    private val geocodeLocations: GeocodeLocationsUseCase,
) : ViewModel() {
```

Adicionar os três novos `StateFlow`, junto dos existentes (`_radiusMeters`, `_selectedType`):

```kotlin
    private val _showLocationSearch = MutableStateFlow(false)
    val showLocationSearch: StateFlow<Boolean> = _showLocationSearch.asStateFlow()

    private val _locationSearchState = MutableStateFlow<LocationSearchState>(LocationSearchState.Idle)
    val locationSearchState: StateFlow<LocationSearchState> = _locationSearchState.asStateFlow()

    /** null = usa a localização atual (GPS); preenchido = busca ativa por localidade. */
    private val _selectedLocation = MutableStateFlow<GeocodeResult?>(null)
    val selectedLocation: StateFlow<GeocodeResult?> = _selectedLocation.asStateFlow()
```

Substituir o corpo de `load()`:

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
                            _state.value = if (stations.isEmpty()) {
                                StationsUiState.Empty
                            } else {
                                StationsUiState.Success(stations)
                            }
                            // Só atualiza o cache de "perto de mim" quando a busca é por GPS —
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

Adicionar as quatro novas funções públicas, logo após `onTypeSelected`:

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

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationsViewModelTest"`

Expected: PASS — todos os testes (existentes + os 9 novos) verdes.

- [ ] **Step 5: Compilar o projeto inteiro**

Run: `./gradlew.bat compileDebugKotlin compileDebugUnitTestKotlin -q`

Expected: sem erros — confirma que não sobrou nenhum outro call site de `StationsViewModel(...)` quebrado.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsUiState.kt \
        app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModel.kt \
        app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModelTest.kt
git commit -m "feat(stations): add location search state and GPS bypass to StationsViewModel"
```

---

### Task 3: UI — bottom sheet de busca + chip de localidade ativa

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/LocationSearchBottomSheet.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsScreen.kt`

**Interfaces:**
- Consumes: `LocationSearchState`, `GeocodeResult` (Task 1/2), `StationsViewModel.showLocationSearch/locationSearchState/selectedLocation` e as 4 novas funções (Task 2).
- Produces: `LocationSearchBottomSheet(state, onSearch, onLocationSelected, onDismiss)`.

Sem teste automatizado — nenhum outro bottom sheet/sheet de busca do app tem teste de UI hoje (mesma lacuna de `VehicleSwitcherBottomSheet`, `FinancialSummaryCard`). Verificação manual no emulador.

- [ ] **Step 1: Criar `LocationSearchBottomSheet.kt`**

```kotlin
package com.flowfuel.app.feature.station.presentation.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.FFBottomSheet
import com.flowfuel.app.core.designsystem.components.FFEmptyState
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFSkeletonBlock
import com.flowfuel.app.core.designsystem.components.FFTextField
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.feature.station.domain.model.GeocodeResult

/**
 * BottomSheet de pesquisa de bairro/cidade — busca só dispara ao confirmar
 * (Enter/ação de busca do teclado), nunca a cada tecla digitada: o backend
 * tem rate limit de 1 req/seg agregado em todos os usuários do app.
 */
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

- [ ] **Step 2: Atualizar `StationsScreen.kt`**

Adicionar os imports:

```kotlin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.flowfuel.app.core.designsystem.components.FFChip
import com.flowfuel.app.core.designsystem.components.FFChipKind
```

Coletar os três novos estados, junto de `radiusMeters`/`selectedType`:

```kotlin
    val showLocationSearch by viewModel.showLocationSearch.collectAsState()
    val locationSearchState by viewModel.locationSearchState.collectAsState()
    val selectedLocation by viewModel.selectedLocation.collectAsState()
```

Adicionar o ícone de busca no `FFTopBar`:

```kotlin
        topBar = {
            FFTopBar(
                title = "Postos próximos",
                actions = {
                    IconButton(onClick = viewModel::openLocationSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar localidade")
                    }
                },
            )
        },
```

Dentro do bloco `if (state != StationsUiState.PermissionRequired) { ... }`, logo após `StationDistanceFilterRow`, adicionar o chip de localidade ativa:

```kotlin
                StationDistanceFilterRow(
                    selectedRadiusMeters = radiusMeters,
                    onSelect = viewModel::onRadiusSelected,
                    modifier = Modifier.padding(vertical = FFTheme.spacing.sm),
                )
                selectedLocation?.let { location ->
                    FFChip(
                        label = location.displayName,
                        kind = FFChipKind.Input,
                        leadingIcon = Icons.Outlined.LocationOn,
                        onClick = {},
                        onTrailingClick = viewModel::clearSelectedLocation,
                        modifier = Modifier.padding(horizontal = FFTheme.spacing.md),
                    )
                }
            }
```

Renderizar o sheet, no mesmo nível do `Scaffold` (fora dele, como um `if` a mais no corpo do composable — mesmo padrão de outros dialogs/sheets do app):

```kotlin
    if (showLocationSearch) {
        LocationSearchBottomSheet(
            state = locationSearchState,
            onSearch = viewModel::searchLocation,
            onLocationSelected = viewModel::onLocationSelected,
            onDismiss = viewModel::closeLocationSearch,
        )
    }
```

- [ ] **Step 3: Compilar**

Run: `./gradlew.bat compileDebugKotlin -q`

Expected: sem erros.

- [ ] **Step 4: Rodar a suíte de testes completa de Postos**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.station.*"`

Expected: PASS.

- [ ] **Step 5: Verificar manualmente no emulador**

Use a skill `run-android-emulator` deste projeto pra buildar e instalar o app debug. Login com a conta de teste QA (`retiko1301@jobraux.com`), abrir a aba "Postos". Confirmar:
- Ícone de busca aparece no topo da tela
- Tocar nele abre o bottom sheet com o campo de busca
- Digitar "Boa Viagem" e confirmar (Enter/ação de busca) mostra candidatos com nome completo (cidade/estado)
- Escolher um candidato fecha o sheet, a lista de postos recarrega pra essa região, e um chip aparece abaixo dos filtros com o nome do lugar
- Tocar no ✕ do chip volta a mostrar postos perto da localização atual e o chip some
- Trocar o filtro de tipo/raio com uma localidade pesquisada ativa continua funcionando (não volta pro GPS sozinho)
- Buscar algo sem resultado (ex: "zzzznaoexiste") mostra o estado vazio dedicado, não o erro genérico

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/presentation/list/LocationSearchBottomSheet.kt \
        app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsScreen.kt
git commit -m "feat(stations): add location search bottom sheet and active-location chip"
```
