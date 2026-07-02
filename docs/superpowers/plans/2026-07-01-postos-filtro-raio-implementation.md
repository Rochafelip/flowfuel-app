# Filtro de raio + redesign do StationCard (Postos) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user pick the search radius (1/3/5/10 km) on the "Postos" screen via chips, and redesign `StationCard` to use `FFCard` with a colored type badge.

**Architecture:** Thread a `radiusMeters: Int` parameter from a new `StationDistanceFilterRow` chip row, through `StationsViewModel` (independent `StateFlow<Int>`, not part of `StationsUiState`), through `GetNearbyStationsUseCase` and `StationRepository`, down to the already-radius-capable `StationApi`. Separately, redesign `StationCard` to wrap its content in `FFCard` with a new `StationTypeBadge` colored via `FFTheme.semanticColors` (`warning`/`onWarning` for Combustível, `info`/`onInfo` for Elétrico).

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Retrofit/kotlinx.serialization, Robolectric + MockK + Turbine for tests (single-module `app` project — no Gradle multi-module split).

## Global Constraints

- `StationDistanceFilterRow` stays **local** to `feature/station/presentation/list` — do not extract a shared design-system component this round (matches the hand-rolled pattern of `EventDateFilterRow`/`EventCategoryFilterRow`).
- Only 4 fixed presets: 1 000 / 3 000 / 5 000 / 10 000 meters. No free/custom radius input.
- The selected radius lives only in `StationsViewModel` memory — no persistence across app restarts (always resets to `DEFAULT_STATION_RADIUS_METERS` = 5000).
- Do not touch: `FFTopBar`, overall screen spacing, `Loading`/`Error`/`Empty` state rendering (`FFSkeletonList`/`FFEmptyState`/`FFErrorState`), or the other 3 existing hand-rolled filter rows (`EventCategoryFilterRow`, `EventDateFilterRow`, the inline filter in `VehiclesScreen`).
- Chip selection colors follow the plain M3 `FilterChip` default style used by `EventDateFilterRow` (no per-item color mapping — radius values aren't categorical).

## Spec Deviations (called out explicitly, not silent)

1. **No Compose UI interaction test for `StationDistanceFilterRow`.** The spec's "Testes" section asks for a `StationDistanceFilterRowTest` that renders the 4 chips and asserts selection/click behavior. This codebase has **zero** Compose UI test infrastructure today (no `createComposeRule` usage anywhere, `androidx.compose.ui.test.junit4` is declared in `build.gradle.kts` but unused, no `app/src/androidTest` files exist at all). The two components this spec explicitly asks to mirror — `EventDateFilterRow` and `EventCategoryFilterRow` — also have **no test files** of their own. Introducing the first-ever instrumented Compose test in this repo is a bigger infra decision than this feature warrants. Task 3 instead extracts and tests the pure label-formatting function (`formatRadiusLabel`), matching the existing `StationCardTest` pattern (pure top-level function, plain JUnit, no Robolectric).
2. **Rating is dropped from the redesigned `StationCard`, not just left alone.** The spec's prose says "rating continua não exibido... nenhuma mudança aqui", but the spec's own code sample for the new card structure has no rating row at all (current code *does* conditionally render a star+rating row today). Task 5 follows the literal code sample — validated via the Opção B mockup — and removes the rating row entirely.

---

### Task 1: Radius constants + repository/use case threading

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/station/domain/model/StationRadius.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/domain/StationRepository.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/data/StationRepositoryImpl.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/domain/usecase/GetNearbyStationsUseCase.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/station/data/StationRepositoryImplTest.kt`

**Interfaces:**
- Produces: `STATION_RADIUS_PRESETS_METERS: List<Int>` = `[1000, 3000, 5000, 10000]`, `DEFAULT_STATION_RADIUS_METERS: Int` = `5000` (package `com.flowfuel.app.feature.station.domain.model`)
- Produces: `StationRepository.getNearbyStations(location: GeoLocation, radiusMeters: Int): AppResult<List<Station>>`
- Produces: `GetNearbyStationsUseCase.invoke(location: GeoLocation, radiusMeters: Int): AppResult<List<Station>>`

- [ ] **Step 1: Write the failing test**

Add a new test to the existing `StationRepositoryImplTest.kt` (keep the other 3 existing tests as-is for now — they'll be fixed to compile in Step 3):

```kotlin
    @Test
    fun `forwards radiusMeters to the API unchanged`() = runTest {
        coEvery { api.getNearbyStations(any(), any(), any()) } returns emptyList()

        repository.getNearbyStations(location, radiusMeters = 3000)

        coVerify { api.getNearbyStations(lat = location.latitude, lng = location.longitude, radiusMeters = 3000) }
    }
```

Add `import io.mockk.coVerify` to the file's imports.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.data.StationRepositoryImplTest"`
Expected: FAIL to compile — `getNearbyStations(location, radiusMeters = 3000)` doesn't resolve because `StationRepository.getNearbyStations` still takes only `location`.

- [ ] **Step 3: Implement**

Create `app/src/main/java/com/flowfuel/app/feature/station/domain/model/StationRadius.kt`:

```kotlin
package com.flowfuel.app.feature.station.domain.model

val STATION_RADIUS_PRESETS_METERS = listOf(1_000, 3_000, 5_000, 10_000)
const val DEFAULT_STATION_RADIUS_METERS = 5_000
```

Modify `app/src/main/java/com/flowfuel/app/feature/station/domain/StationRepository.kt`:

```kotlin
package com.flowfuel.app.feature.station.domain

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.Station

interface StationRepository {
    suspend fun getNearbyStations(location: GeoLocation, radiusMeters: Int): AppResult<List<Station>>
}
```

Modify `app/src/main/java/com/flowfuel/app/feature/station/data/StationRepositoryImpl.kt` (removes the private `DEFAULT_RADIUS_METERS` constant, threads the parameter through):

```kotlin
package com.flowfuel.app.feature.station.data

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.map
import com.flowfuel.app.core.network.apiCall
import com.flowfuel.app.feature.station.data.remote.StationApi
import com.flowfuel.app.feature.station.data.remote.StationResponseDto
import com.flowfuel.app.feature.station.domain.StationRepository
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.model.StationType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StationRepositoryImpl @Inject constructor(
    private val api: StationApi,
) : StationRepository {

    override suspend fun getNearbyStations(location: GeoLocation, radiusMeters: Int): AppResult<List<Station>> =
        apiCall {
            api.getNearbyStations(
                lat = location.latitude,
                lng = location.longitude,
                radiusMeters = radiusMeters,
            )
        }.map { list -> list.map { it.toDomain() }.sortedBy { it.distanceMeters } }

    private fun StationResponseDto.toDomain(): Station = Station(
        placeId = placeId,
        name = name,
        type = if (type.equals("ELECTRIC", ignoreCase = true)) StationType.Electric else StationType.Fuel,
        distanceMeters = distanceMeters,
        rating = rating,
        latitude = latitude,
        longitude = longitude,
    )
}
```

Modify `app/src/main/java/com/flowfuel/app/feature/station/domain/usecase/GetNearbyStationsUseCase.kt`:

```kotlin
package com.flowfuel.app.feature.station.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.StationRepository
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.Station
import javax.inject.Inject

class GetNearbyStationsUseCase @Inject constructor(
    private val repository: StationRepository,
) {
    suspend operator fun invoke(location: GeoLocation, radiusMeters: Int): AppResult<List<Station>> =
        repository.getNearbyStations(location, radiusMeters)
}
```

Fix the 3 pre-existing tests in `StationRepositoryImplTest.kt` to pass `radiusMeters = 5000` explicitly (they now fail to compile with only 1 arg):

```kotlin
    @Test
    fun `maps dtos to domain and sorts by distance`() = runTest {
        coEvery { api.getNearbyStations(any(), any(), any()) } returns listOf(
            StationResponseDto(
                placeId = "b", name = "Ipiranga", type = "FUEL",
                distanceMeters = 650, rating = null, latitude = -8.05, longitude = -34.90,
            ),
            StationResponseDto(
                placeId = "a", name = "Shell Boa Viagem", type = "FUEL",
                distanceMeters = 420, rating = 4.6, latitude = -8.05, longitude = -34.91,
            ),
            StationResponseDto(
                placeId = "c", name = "Estação Volta", type = "ELECTRIC",
                distanceMeters = 900, rating = null, latitude = -8.06, longitude = -34.92,
            ),
        )

        val result = repository.getNearbyStations(location, radiusMeters = 5000)

        assertTrue(result is AppResult.Success)
        val stations = (result as AppResult.Success).value
        assertEquals(listOf("Shell Boa Viagem", "Ipiranga", "Estação Volta"), stations.map { it.name })
        assertEquals(StationType.Fuel, stations[0].type)
        assertEquals(StationType.Electric, stations[2].type)
    }

    @Test
    fun `returns empty list when backend has no nearby stations`() = runTest {
        coEvery { api.getNearbyStations(any(), any(), any()) } returns emptyList()

        val result = repository.getNearbyStations(location, radiusMeters = 5000)

        assertTrue(result is AppResult.Success)
        assertEquals(emptyList<Any>(), (result as AppResult.Success).value)
    }

    @Test
    fun `maps network failure to AppError-Network`() = runTest {
        coEvery { api.getNearbyStations(any(), any(), any()) } throws IOException("no network")

        val result = repository.getNearbyStations(location, radiusMeters = 5000)

        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.Network, (result as AppResult.Failure).error)
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.data.StationRepositoryImplTest"`
Expected: PASS — all 4 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/domain/model/StationRadius.kt app/src/main/java/com/flowfuel/app/feature/station/domain/StationRepository.kt app/src/main/java/com/flowfuel/app/feature/station/data/StationRepositoryImpl.kt app/src/main/java/com/flowfuel/app/feature/station/domain/usecase/GetNearbyStationsUseCase.kt app/src/test/java/com/flowfuel/app/feature/station/data/StationRepositoryImplTest.kt
git commit -m "feat(postos): thread radiusMeters through repository and use case"
```

---

### Task 2: `StationsViewModel` radius state

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModel.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModelTest.kt`

**Interfaces:**
- Consumes: `GetNearbyStationsUseCase.invoke(location, radiusMeters)`, `DEFAULT_STATION_RADIUS_METERS` (Task 1)
- Produces: `StationsViewModel.radiusMeters: StateFlow<Int>`, `StationsViewModel.onRadiusSelected(radiusMeters: Int): Unit`

- [ ] **Step 1: Write the failing tests**

Add to `StationsViewModelTest.kt` (imports `DEFAULT_STATION_RADIUS_METERS` — add `import com.flowfuel.app.feature.station.domain.model.DEFAULT_STATION_RADIUS_METERS`):

```kotlin
    @Test
    fun `radiusMeters starts at the default preset`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))

        val vm = buildViewModel()

        assertEquals(DEFAULT_STATION_RADIUS_METERS, vm.radiusMeters.value)
    }

    @Test
    fun `onRadiusSelected updates radiusMeters and reloads stations with the new radius`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))
        coEvery { getNearbyStations(location, 10_000) } returns AppResult.Success(listOf(station("a", 100), station("b", 9000)))
        val vm = buildViewModel()

        vm.onRadiusSelected(10_000)

        assertEquals(10_000, vm.radiusMeters.value)
        val state = vm.state.value
        assertTrue(state is StationsUiState.Success)
        assertEquals(2, (state as StationsUiState.Success).stations.size)
        coVerify { getNearbyStations(location, 10_000) }
    }
```

Also update the 4 existing tests that stub `getNearbyStations(location)` to instead stub `getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS)` (`` `Success state when stations are found` ``, `` `Empty state when repository returns no stations` ``, `` `Error state when repository fails` ``, `` `onRouteClick emits OpenNavigation with lat,lng uri` ``), and update the `PermissionRequired` test's negative verification from `coVerify(inverse = true) { getNearbyStations(any()) }` to `coVerify(inverse = true) { getNearbyStations(any(), any()) }`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationsViewModelTest"`
Expected: FAIL to compile — `vm.radiusMeters` and `vm.onRadiusSelected` don't exist yet, and `getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS)` doesn't match the mock's still-1-arg-based invocation shape used internally by the ViewModel.

- [ ] **Step 3: Implement**

Modify `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModel.kt`:

```kotlin
package com.flowfuel.app.feature.station.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.model.DEFAULT_STATION_RADIUS_METERS
import com.flowfuel.app.feature.station.domain.model.LocationResult
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StationsViewModel @Inject constructor(
    private val getNearbyStations: GetNearbyStationsUseCase,
    private val locationProvider: LocationProvider,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow<StationsUiState>(StationsUiState.Loading)
    val state: StateFlow<StationsUiState> = _state.asStateFlow()

    private val _radiusMeters = MutableStateFlow(DEFAULT_STATION_RADIUS_METERS)
    val radiusMeters: StateFlow<Int> = _radiusMeters.asStateFlow()

    private val _effects = Channel<StationsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _state.value = StationsUiState.Loading
        viewModelScope.launch {
            when (val locationResult = locationProvider.getCurrentLocation()) {
                LocationResult.PermissionDenied -> _state.value = StationsUiState.PermissionRequired
                LocationResult.Unavailable -> _state.value = StationsUiState.LocationUnavailable
                is LocationResult.Available -> {
                    when (val result = getNearbyStations(locationResult.location, _radiusMeters.value)) {
                        is AppResult.Success -> _state.value = if (result.value.isEmpty()) {
                            StationsUiState.Empty
                        } else {
                            StationsUiState.Success(result.value)
                        }
                        is AppResult.Failure -> handleFailure(result.error)
                    }
                }
            }
        }
    }

    fun onRadiusSelected(radiusMeters: Int) {
        _radiusMeters.value = radiusMeters
        load()
    }

    fun onRouteClick(station: Station) {
        val uri = "google.navigation:q=${station.latitude},${station.longitude}"
        viewModelScope.launch { _effects.send(StationsEffect.OpenNavigation(uri)) }
    }

    private suspend fun handleFailure(error: AppError) {
        if (error == AppError.Unauthorized) {
            sessionStore.clear()
            _effects.send(StationsEffect.NavigateToLogin)
        } else {
            _state.value = StationsUiState.Error(error)
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationsViewModelTest"`
Expected: PASS — all 9 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModel.kt app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModelTest.kt
git commit -m "feat(postos): add radiusMeters state and onRadiusSelected to StationsViewModel"
```

---

### Task 3: `StationDistanceFilterRow` component

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationDistanceFilterRow.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationDistanceFilterRowTest.kt`

**Interfaces:**
- Consumes: `STATION_RADIUS_PRESETS_METERS` (Task 1)
- Produces: `StationDistanceFilterRow(selectedRadiusMeters: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier): Unit` (Composable), `formatRadiusLabel(radiusMeters: Int): String` (internal, pure — consumed by Task 4 indirectly through the composable, not called directly elsewhere)

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationDistanceFilterRowTest.kt`:

```kotlin
package com.flowfuel.app.feature.station.presentation.list

import org.junit.Assert.assertEquals
import org.junit.Test

class StationDistanceFilterRowTest {

    @Test
    fun `formats radius presets in whole kilometers`() {
        assertEquals("1 km", formatRadiusLabel(1000))
        assertEquals("3 km", formatRadiusLabel(3000))
        assertEquals("5 km", formatRadiusLabel(5000))
        assertEquals("10 km", formatRadiusLabel(10000))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationDistanceFilterRowTest"`
Expected: FAIL to compile — `formatRadiusLabel` doesn't exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationDistanceFilterRow.kt`:

```kotlin
package com.flowfuel.app.feature.station.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowfuel.app.feature.station.domain.model.STATION_RADIUS_PRESETS_METERS

internal fun formatRadiusLabel(radiusMeters: Int): String = "${radiusMeters / 1000} km"

/**
 * Filtro horizontal de raio de busca — presets fixos (sem raio customizado).
 */
@Composable
fun StationDistanceFilterRow(
    selectedRadiusMeters: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(STATION_RADIUS_PRESETS_METERS) { radiusMeters ->
            FilterChip(
                selected = radiusMeters == selectedRadiusMeters,
                onClick = { onSelect(radiusMeters) },
                label = { Text(formatRadiusLabel(radiusMeters)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationDistanceFilterRowTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationDistanceFilterRow.kt app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationDistanceFilterRowTest.kt
git commit -m "feat(postos): add StationDistanceFilterRow radius chip row"
```

---

### Task 4: Wire the filter row into `StationsScreen`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsScreen.kt`

**Interfaces:**
- Consumes: `StationsViewModel.radiusMeters` / `.onRadiusSelected` (Task 2), `StationDistanceFilterRow` (Task 3)

- [ ] **Step 1: Implement**

Modify `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsScreen.kt` — add `radiusMeters` collection, add 2 imports (`Column`, `weight`, `fillMaxWidth` already present), restructure the `Scaffold` body from a single `Box` into a `Column` containing the conditional filter row plus a weighted `Box`:

```kotlin
package com.flowfuel.app.feature.station.presentation.list

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flowfuel.app.core.designsystem.components.FFEmptyState
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFSkeletonList
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsScreen(
    onNavigateToLogin: () -> Unit = {},
    viewModel: StationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val radiusMeters by viewModel.radiusMeters.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var hasPermanentlyDeniedPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.load()
        } else {
            val activity = context as? Activity
            hasPermanentlyDeniedPermission = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.ACCESS_FINE_LOCATION,
                )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                when (state) {
                    StationsUiState.PermissionRequired, StationsUiState.LocationUnavailable -> viewModel.load()
                    else -> Unit
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is StationsEffect.OpenNavigation -> try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effect.uri)))
                } catch (_: ActivityNotFoundException) {
                    snackbarHostState.showSnackbar(
                        FFSnackbarVisuals("Nenhum app de navegação instalado", FFSnackbarKind.Error)
                    )
                }
                StationsEffect.NavigateToLogin -> onNavigateToLogin()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { FFTopBar(title = "Postos próximos") },
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state != StationsUiState.PermissionRequired) {
                StationDistanceFilterRow(
                    selectedRadiusMeters = radiusMeters,
                    onSelect = viewModel::onRadiusSelected,
                    modifier = Modifier.padding(vertical = FFTheme.spacing.sm),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (val s = state) {
                    StationsUiState.Loading -> FFSkeletonList(modifier = Modifier.fillMaxSize(), itemCount = 4)

                    is StationsUiState.Success -> PullToRefreshBox(
                        isRefreshing = false,
                        onRefresh = viewModel::load,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(FFTheme.spacing.md),
                            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                        ) {
                            items(s.stations, key = { it.placeId }) { station ->
                                StationCard(station = station, onRouteClick = { viewModel.onRouteClick(station) })
                            }
                        }
                    }

                    StationsUiState.Empty -> FFEmptyState(
                        title = "Nenhum posto encontrado por perto",
                        description = "Tente novamente em uma área com mais cobertura.",
                        actionText = "Tentar novamente",
                        onAction = viewModel::load,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    is StationsUiState.Error -> FFErrorState(
                        message = s.error.userMessage(),
                        onRetry = viewModel::load,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    StationsUiState.LocationUnavailable -> FFErrorState(
                        title = "Localização indisponível",
                        message = "Não foi possível obter sua localização. Verifique se o GPS está ativado.",
                        onRetry = viewModel::load,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    StationsUiState.PermissionRequired -> FFEmptyState(
                        title = "Precisamos da sua localização",
                        description = "Para mostrar postos e estações próximos, permita o acesso à localização.",
                        icon = Icons.Outlined.LocationOn,
                        actionText = if (hasPermanentlyDeniedPermission) "Abrir configurações" else "Permitir acesso à localização",
                        onAction = {
                            if (hasPermanentlyDeniedPermission) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    )
                                )
                            } else {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL — no unresolved references (`StationDistanceFilterRow`, `viewModel.radiusMeters`, `Modifier.weight` all resolve).

- [ ] **Step 3: Run the full station test suite to confirm no regression**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.*"`
Expected: PASS — all existing + new station tests green (this task has no new unit-testable logic of its own; screen composition is verified by compiling and, when available, a manual run via the `run-android-emulator` skill).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsScreen.kt
git commit -m "feat(postos): render StationDistanceFilterRow above the station list"
```

---

### Task 5: `StationCard` redesign — `FFCard` + colored type badge

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationCard.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationCardTest.kt`

**Interfaces:**
- Consumes: `FFCard` (`variant`, `title`, `onClick`, `content`), `FFButton` (`variant: FFButtonVariant`), `FFTheme.spacing.{xs,sm}`, `FFTheme.semanticColors.{warning,onWarning,info,onInfo}` — all pre-existing, no changes needed to those files.
- Produces: `internal data class StationTypeBadgeContent(label: String, icon: ImageVector, contentDescription: String)`, `internal fun StationType.badgeContent(): StationTypeBadgeContent` — pure, testable without Compose/Robolectric.

- [ ] **Step 1: Write the failing tests**

Add to `StationCardTest.kt`:

```kotlin
    @Test
    fun `fuel type badge shows Combustivel label and preserves the existing content description`() {
        val content = StationType.Fuel.badgeContent()
        assertEquals("Combustível", content.label)
        assertEquals("Posto de combustível", content.contentDescription)
    }

    @Test
    fun `electric type badge shows Eletrico label and preserves the existing content description`() {
        val content = StationType.Electric.badgeContent()
        assertEquals("Elétrico", content.label)
        assertEquals("Estação de recarga elétrica", content.contentDescription)
    }
```

Add `import com.flowfuel.app.feature.station.domain.model.StationType` to the test file's imports.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationCardTest"`
Expected: FAIL to compile — `StationType.badgeContent()` doesn't exist yet.

- [ ] **Step 3: Implement the pure mapping function**

In `StationCard.kt`, add (this alone is enough to make Step 2's tests pass, before touching the composable):

```kotlin
internal data class StationTypeBadgeContent(
    val label: String,
    val icon: ImageVector,
    val contentDescription: String,
)

internal fun StationType.badgeContent(): StationTypeBadgeContent = when (this) {
    StationType.Fuel -> StationTypeBadgeContent(
        label = "Combustível",
        icon = Icons.Filled.LocalGasStation,
        contentDescription = "Posto de combustível",
    )
    StationType.Electric -> StationTypeBadgeContent(
        label = "Elétrico",
        icon = Icons.Filled.EvStation,
        contentDescription = "Estação de recarga elétrica",
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationCardTest"`
Expected: PASS — 6 tests green (2 new + existing 4 `formatDistance` tests, unaffected).

- [ ] **Step 5: Redesign the composable**

Replace the full content of `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationCard.kt`:

```kotlin
package com.flowfuel.app.feature.station.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.model.StationType
import java.util.Locale

@Composable
fun StationCard(
    station: Station,
    onRouteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FFCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StationTypeBadge(station.type)
            Text(
                text = formatDistance(station.distanceMeters),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
}

@Composable
private fun StationTypeBadge(type: StationType, modifier: Modifier = Modifier) {
    val content = type.badgeContent()
    val (containerColor, contentColor) = when (type) {
        StationType.Fuel -> FFTheme.semanticColors.warning to FFTheme.semanticColors.onWarning
        StationType.Electric -> FFTheme.semanticColors.info to FFTheme.semanticColors.onInfo
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(FFTheme.spacing.xs),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FFTheme.spacing.sm, vertical = FFTheme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = content.icon,
                contentDescription = content.contentDescription,
                modifier = Modifier.size(16.dp),
            )
            Text(content.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

internal data class StationTypeBadgeContent(
    val label: String,
    val icon: ImageVector,
    val contentDescription: String,
)

internal fun StationType.badgeContent(): StationTypeBadgeContent = when (this) {
    StationType.Fuel -> StationTypeBadgeContent(
        label = "Combustível",
        icon = Icons.Filled.LocalGasStation,
        contentDescription = "Posto de combustível",
    )
    StationType.Electric -> StationTypeBadgeContent(
        label = "Elétrico",
        icon = Icons.Filled.EvStation,
        contentDescription = "Estação de recarga elétrica",
    )
}

internal fun formatDistance(meters: Int): String = if (meters < 1000) {
    "$meters m"
} else {
    String.format(Locale("pt", "BR"), "%.1f km", meters / 1000.0)
}
```

- [ ] **Step 6: Run tests to confirm no regression**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationCardTest"`
Expected: PASS — same 6 tests green.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationCard.kt app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationCardTest.kt
git commit -m "feat(postos): redesign StationCard with FFCard and colored type badge"
```

---

## Self-Review

**1. Spec coverage:**
- Chip row 1/3/5/10 km, local component, `EventDateFilterRow`-style → Task 3.
- `radiusMeters` threading through ViewModel → UseCase → Repository → Impl → Api → Tasks 1, 2.
- `StationCard` → `FFCard`, colored badge, `FFButton(Primary)` full-width → Task 5.
- Filter row visible in all states except `PermissionRequired` → Task 4.
- No persistence between sessions → satisfied by design (radius lives only in ViewModel's in-memory `StateFlow`, reset on process death, as in Global Constraints).
- No custom radius, only the 4 presets → `STATION_RADIUS_PRESETS_METERS` is the only source of chip values (Task 1, Task 3).
- `semanticColors.warning/onWarning` (Combustível) and `.info/.onInfo` (Elétrico) → confirmed exact field names by reading `Color.kt` directly; used in Task 5, no gap remains (the spec's own noted "to verify" gap is now resolved).
- Tests for repository radius propagation, ViewModel radius state, filter row rendering, card redesign → Tasks 1, 2, 3, 5 respectively (Task 3's test is narrowed to the pure label function — see Spec Deviations).

**2. Placeholder scan:** No TBD/TODO, no "similar to Task N" shortcuts — every step has literal code. Task 4's screen change is fully written out in full (not a diff-only reference) since it's a full-file replace.

**3. Type consistency:** `radiusMeters: Int` name and type match across `StationRepository` (Task 1) → `GetNearbyStationsUseCase` (Task 1) → `StationsViewModel._radiusMeters`/`radiusMeters`/`onRadiusSelected` (Task 2) → `StationDistanceFilterRow(selectedRadiusMeters, onSelect: (Int) -> Unit)` (Task 3) → `StationsScreen` wiring (Task 4). `StationTypeBadgeContent`/`badgeContent()` naming is consistent between its definition and use in Task 5 (both in the same file/task, no cross-task drift risk).
