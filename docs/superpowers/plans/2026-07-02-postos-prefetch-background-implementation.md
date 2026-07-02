# Pré-carregamento em background da lista de Postos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When the user opens the "Postos" tab, show the station list instantly (no skeleton, no network call) if a recent result at the default radius was already prefetched in the background.

**Architecture:** A new `@Singleton` `NearbyStationsPrefetcher` holds an in-memory, TTL-bounded cache of the last successful `getNearbyStations` call at the default radius. `HomeViewModel` fires the prefetch (fire-and-forget) at the same points it already resyncs its own dashboard — `load()` and `onVehicleSwitch()`. `StationsViewModel.init` reads the cache first; if fresh, it populates state directly, otherwise it falls back to the existing `load()` flow (which also keeps the cache warm on manual refresh/radius changes at the default radius). A new `Clock` abstraction (`nowMillis(): Long`) makes TTL expiry deterministic in tests — nothing equivalent exists in the codebase today.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, coroutines (`StateFlow`, own `CoroutineScope` for the singleton), MockK + Robolectric (only where already used) for tests — single-module `app` project.

## Global Constraints

- No UI changes. `StationsScreen`/`StationCard`/filters/radius chips are untouched — the only observable difference is the absence of the loading skeleton when the cache is warm.
- Only the **default** radius (`DEFAULT_STATION_RADIUS_METERS` = 5000) is cached. Any manual radius change (1/3/10 km) always triggers a fresh network call, exactly like today.
- Cache TTL is a fixed 5 minutes (`300_000` ms), hardcoded as a private constant inside `NearbyStationsPrefetcher` — no settings/config surface for it.
- No backend/API contract changes.
- No continuous periodic background refresh — only the two explicit trigger points in `HomeViewModel` (`load()`, `onVehicleSwitch()`) start a prefetch.
- Failures during prefetch (no permission, no GPS fix, API error) are silent no-ops that leave any existing cache untouched — same "fail-open silencioso" pattern already used in `HomeViewModel.refresh()`.

---

### Task 1: `Clock` abstraction + DI provider

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/core/common/Clock.kt`
- Modify: `app/src/main/java/com/flowfuel/app/core/common/Dispatchers.kt`
- Test: `app/src/test/java/com/flowfuel/app/core/common/ClockTest.kt`

**Interfaces:**
- Produces: `interface Clock { fun nowMillis(): Long }`, `class SystemClock : Clock` (package `com.flowfuel.app.core.common`). Hilt provides `Clock` as a `@Singleton` via `DispatcherModule`.

- [x] **Step 1: Write the failing test**

Create `app/src/test/java/com/flowfuel/app/core/common/ClockTest.kt`:

```kotlin
package com.flowfuel.app.core.common

import org.junit.Assert.assertTrue
import org.junit.Test

class ClockTest {

    @Test
    fun `SystemClock returns the current wall-clock time in millis`() {
        val before = System.currentTimeMillis()
        val clock = SystemClock()
        val now = clock.nowMillis()
        val after = System.currentTimeMillis()

        assertTrue(now in before..after)
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.core.common.ClockTest"`
Expected: FAIL to compile — `Clock`/`SystemClock` don't exist yet in `com.flowfuel.app.core.common`.

- [x] **Step 3: Implement**

Create `app/src/main/java/com/flowfuel/app/core/common/Clock.kt`:

```kotlin
package com.flowfuel.app.core.common

interface Clock {
    fun nowMillis(): Long
}

class SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
```

Modify `app/src/main/java/com/flowfuel/app/core/common/Dispatchers.kt` — add the `Singleton` import and a `Clock` provider inside the existing `DispatcherModule` object:

```kotlin
package com.flowfuel.app.core.common

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MainDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides @IoDispatcher fun io(): CoroutineDispatcher = Dispatchers.IO
    @Provides @DefaultDispatcher fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
    @Provides @MainDispatcher fun main(): CoroutineDispatcher = Dispatchers.Main
    @Provides @Singleton fun clock(): Clock = SystemClock()
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.core.common.ClockTest"`
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/common/Clock.kt app/src/main/java/com/flowfuel/app/core/common/Dispatchers.kt app/src/test/java/com/flowfuel/app/core/common/ClockTest.kt
git commit -m "feat(core): add Clock abstraction for deterministic TTL testing"
```

---

### Task 2: `NearbyStationsPrefetcher`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/station/domain/NearbyStationsPrefetcher.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/station/domain/NearbyStationsPrefetcherTest.kt`

**Interfaces:**
- Consumes: `Clock.nowMillis(): Long` (Task 1); `GetNearbyStationsUseCase.invoke(location: GeoLocation, radiusMeters: Int): AppResult<List<Station>>`; `LocationProvider.getCurrentLocation(): LocationResult`; `DEFAULT_STATION_RADIUS_METERS: Int` (all pre-existing in `feature/station`); `IoDispatcher` qualifier (Task 1's `Dispatchers.kt`).
- Produces: `NearbyStationsPrefetcher(getNearbyStations, locationProvider, clock, dispatcher)` with `fun prefetch()`, `fun freshCachedStations(): List<Station>?`, `fun updateCache(stations: List<Station>)` (package `com.flowfuel.app.feature.station.domain`). `@Singleton`, `@Inject constructor` — Hilt provides it automatically, no module entry needed.

- [x] **Step 1: Write the failing tests**

Create `app/src/test/java/com/flowfuel/app/feature/station/domain/NearbyStationsPrefetcherTest.kt`:

```kotlin
package com.flowfuel.app.feature.station.domain

import com.flowfuel.app.core.common.Clock
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.model.DEFAULT_STATION_RADIUS_METERS
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.LocationResult
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.model.StationType
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.returnsMany
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyStationsPrefetcherTest {

    private class FakeClock(var millis: Long = 0L) : Clock {
        override fun nowMillis(): Long = millis
    }

    private val getNearbyStations: GetNearbyStationsUseCase = mockk()
    private val locationProvider: LocationProvider = mockk()
    private val clock = FakeClock()
    private val location = GeoLocation(latitude = -8.05, longitude = -34.90)

    private fun station(id: String) = Station(
        placeId = id, name = "Posto $id", type = StationType.Fuel,
        distanceMeters = 100, rating = null, latitude = -8.05, longitude = -34.90,
    )

    private fun buildPrefetcher() =
        NearbyStationsPrefetcher(getNearbyStations, locationProvider, clock, UnconfinedTestDispatcher())

    @Test
    fun `prefetch on success stores the result with the clock's timestamp`() {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a")))
        clock.millis = 1_000L
        val prefetcher = buildPrefetcher()

        prefetcher.prefetch()

        assertEquals(listOf(station("a")), prefetcher.freshCachedStations())
    }

    @Test
    fun `prefetch with PermissionDenied does not touch an existing cache`() {
        coEvery { locationProvider.getCurrentLocation() } returnsMany listOf(
            LocationResult.Available(location),
            LocationResult.PermissionDenied,
        )
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a")))
        val prefetcher = buildPrefetcher()
        prefetcher.prefetch()

        prefetcher.prefetch()

        assertEquals(listOf(station("a")), prefetcher.freshCachedStations())
    }

    @Test
    fun `prefetch with Unavailable does not touch an existing cache`() {
        coEvery { locationProvider.getCurrentLocation() } returnsMany listOf(
            LocationResult.Available(location),
            LocationResult.Unavailable,
        )
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a")))
        val prefetcher = buildPrefetcher()
        prefetcher.prefetch()

        prefetcher.prefetch()

        assertEquals(listOf(station("a")), prefetcher.freshCachedStations())
    }

    @Test
    fun `prefetch with an API failure does not touch an existing cache`() {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returnsMany listOf(
            AppResult.Success(listOf(station("a"))),
            AppResult.Failure(AppError.Network),
        )
        val prefetcher = buildPrefetcher()
        prefetcher.prefetch()

        prefetcher.prefetch()

        assertEquals(listOf(station("a")), prefetcher.freshCachedStations())
    }

    @Test
    fun `freshCachedStations returns the list when within the TTL`() {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a")))
        clock.millis = 0L
        val prefetcher = buildPrefetcher()
        prefetcher.prefetch()

        clock.millis = 300_000L // exactly 5 minutes later, still within TTL (inclusive)

        assertEquals(listOf(station("a")), prefetcher.freshCachedStations())
    }

    @Test
    fun `freshCachedStations returns null once the clock passes the TTL`() {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a")))
        clock.millis = 0L
        val prefetcher = buildPrefetcher()
        prefetcher.prefetch()

        clock.millis = 300_001L

        assertNull(prefetcher.freshCachedStations())
    }

    @Test
    fun `two sequential prefetch calls overwrite the cache with the second result`() {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returnsMany listOf(
            AppResult.Success(listOf(station("a"))),
            AppResult.Success(listOf(station("b"))),
        )
        val prefetcher = buildPrefetcher()

        prefetcher.prefetch()
        prefetcher.prefetch()

        assertEquals(listOf(station("b")), prefetcher.freshCachedStations())
    }

    @Test
    fun `updateCache stores the given stations with the clock's timestamp`() {
        clock.millis = 42L
        val prefetcher = buildPrefetcher()

        prefetcher.updateCache(listOf(station("a")))

        assertEquals(listOf(station("a")), prefetcher.freshCachedStations())
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.domain.NearbyStationsPrefetcherTest"`
Expected: FAIL to compile — `NearbyStationsPrefetcher` doesn't exist yet.

- [x] **Step 3: Implement**

Create `app/src/main/java/com/flowfuel/app/feature/station/domain/NearbyStationsPrefetcher.kt`:

```kotlin
package com.flowfuel.app.feature.station.domain

import com.flowfuel.app.core.common.Clock
import com.flowfuel.app.core.common.IoDispatcher
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.model.DEFAULT_STATION_RADIUS_METERS
import com.flowfuel.app.feature.station.domain.model.LocationResult
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

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

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.domain.NearbyStationsPrefetcherTest"`
Expected: PASS (8 tests)

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/domain/NearbyStationsPrefetcher.kt app/src/test/java/com/flowfuel/app/feature/station/domain/NearbyStationsPrefetcherTest.kt
git commit -m "feat(postos): add NearbyStationsPrefetcher singleton with TTL cache"
```

---

### Task 3: Wire `StationsViewModel` to read/write the shared cache

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModel.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModelTest.kt`

**Interfaces:**
- Consumes: `NearbyStationsPrefetcher.freshCachedStations(): List<Station>?`, `NearbyStationsPrefetcher.updateCache(stations: List<Station>)` (Task 2).
- Produces: `StationsViewModel` constructor gains a 5th parameter `stationsPrefetcher: NearbyStationsPrefetcher`.

- [x] **Step 1: Write the failing tests**

Modify `app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModelTest.kt`:

Add imports:

```kotlin
import com.flowfuel.app.feature.station.domain.NearbyStationsPrefetcher
import io.mockk.Runs
import io.mockk.just
import io.mockk.verify
```

Add the mock field, update `buildViewModel()`, and stub the default no-cache behavior in `setUp()`:

```kotlin
    private val getVehicleById: GetVehicleByIdUseCase = mockk()
    private val stationsPrefetcher: NearbyStationsPrefetcher = mockk()
```

```kotlin
    private fun buildViewModel() =
        StationsViewModel(getNearbyStations, locationProvider, sessionStore, getVehicleById, stationsPrefetcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        every { stationsPrefetcher.freshCachedStations() } returns null
        every { stationsPrefetcher.updateCache(any()) } just Runs
    }
```

Add these tests at the end of the class, before the closing `}`:

```kotlin
    @Test
    fun `init populates Success directly from a fresh prefetch cache, without calling locationProvider or getNearbyStations`() = runTest {
        every { stationsPrefetcher.freshCachedStations() } returns listOf(station("a", 100))

        val vm = buildViewModel()

        val state = vm.state.value
        assertTrue(state is StationsUiState.Success)
        assertEquals(1, (state as StationsUiState.Success).stations.size)
        coVerify(inverse = true) { locationProvider.getCurrentLocation() }
        coVerify(inverse = true) { getNearbyStations(any(), any()) }
    }

    @Test
    fun `init populates Empty when the fresh prefetch cache is an empty list`() = runTest {
        every { stationsPrefetcher.freshCachedStations() } returns emptyList()

        val vm = buildViewModel()

        assertEquals(StationsUiState.Empty, vm.state.value)
        coVerify(inverse = true) { locationProvider.getCurrentLocation() }
    }

    @Test
    fun `init falls back to load() when there is no prefetch cache`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))

        val vm = buildViewModel()

        assertTrue(vm.state.value is StationsUiState.Success)
        coVerify { locationProvider.getCurrentLocation() }
    }

    @Test
    fun `load() at the default radius updates the prefetch cache on success`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))

        buildViewModel()

        verify { stationsPrefetcher.updateCache(listOf(station("a", 100))) }
    }

    @Test
    fun `load() at a non-default radius does not update the prefetch cache again`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))
        coEvery { getNearbyStations(location, 10_000) } returns AppResult.Success(listOf(station("a", 100), station("b", 9000)))
        val vm = buildViewModel()

        vm.onRadiusSelected(10_000)

        verify(exactly = 1) { stationsPrefetcher.updateCache(any()) }
    }
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationsViewModelTest"`
Expected: FAIL to compile — `StationsViewModel(...)` doesn't accept a 5th argument yet.

- [x] **Step 3: Implement**

Modify `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModel.kt`:

```kotlin
package com.flowfuel.app.feature.station.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.NearbyStationsPrefetcher
import com.flowfuel.app.feature.station.domain.model.DEFAULT_STATION_RADIUS_METERS
import com.flowfuel.app.feature.station.domain.model.LocationResult
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.model.StationType
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehicleByIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StationsViewModel @Inject constructor(
    private val getNearbyStations: GetNearbyStationsUseCase,
    private val locationProvider: LocationProvider,
    private val sessionStore: SessionStore,
    private val getVehicleById: GetVehicleByIdUseCase,
    private val stationsPrefetcher: NearbyStationsPrefetcher,
) : ViewModel() {

    private val _state = MutableStateFlow<StationsUiState>(StationsUiState.Loading)
    val state: StateFlow<StationsUiState> = _state.asStateFlow()

    private val _radiusMeters = MutableStateFlow(DEFAULT_STATION_RADIUS_METERS)
    val radiusMeters: StateFlow<Int> = _radiusMeters.asStateFlow()

    private val _selectedType = MutableStateFlow(StationType.Fuel)
    val selectedType: StateFlow<StationType> = _selectedType.asStateFlow()

    private val _effects = Channel<StationsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            sessionStore.activeVehicleIdFlow.firstOrNull()?.let { vehicleId ->
                val result = getVehicleById(vehicleId)
                if (result is AppResult.Success) {
                    _selectedType.value = when (result.value.energyType) {
                        EnergyType.Electric -> StationType.Electric
                        EnergyType.Combustion, EnergyType.Hybrid -> StationType.Fuel
                    }
                }
            }
            val cached = stationsPrefetcher.freshCachedStations()
            if (cached != null) {
                _state.value = if (cached.isEmpty()) StationsUiState.Empty else StationsUiState.Success(cached)
            } else {
                load()
            }
        }
    }

    fun load() {
        _state.value = StationsUiState.Loading
        viewModelScope.launch {
            when (val locationResult = locationProvider.getCurrentLocation()) {
                LocationResult.PermissionDenied -> _state.value = StationsUiState.PermissionRequired
                LocationResult.Unavailable -> _state.value = StationsUiState.LocationUnavailable
                is LocationResult.Available -> {
                    when (val result = getNearbyStations(locationResult.location, _radiusMeters.value)) {
                        is AppResult.Success -> {
                            _state.value = if (result.value.isEmpty()) {
                                StationsUiState.Empty
                            } else {
                                StationsUiState.Success(result.value)
                            }
                            if (_radiusMeters.value == DEFAULT_STATION_RADIUS_METERS) {
                                stationsPrefetcher.updateCache(result.value)
                            }
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

    fun onTypeSelected(type: StationType) {
        _selectedType.value = type
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

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationsViewModelTest"`
Expected: PASS (all existing tests + 5 new ones)

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModel.kt app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModelTest.kt
git commit -m "feat(postos): read/write the prefetch cache in StationsViewModel"
```

---

### Task 4: Wire `HomeViewModel` prefetch triggers

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `NearbyStationsPrefetcher.prefetch(): Unit` (Task 2).
- Produces: `HomeViewModel` constructor gains an 8th parameter `stationsPrefetcher: NearbyStationsPrefetcher`.

- [x] **Step 1: Write the failing tests**

Modify `app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt`:

Add imports:

```kotlin
import com.flowfuel.app.feature.station.domain.NearbyStationsPrefetcher
import io.mockk.verify
```

Add the mock field and pass it to the constructor call in `setUp()`:

```kotlin
    private val setActiveVehicle: SetActiveVehicleUseCase = mockk(relaxed = true)
    private val stationsPrefetcher: NearbyStationsPrefetcher = mockk(relaxed = true)
```

```kotlin
        viewModel = HomeViewModel(
            getActiveVehicle, getDashboard, createRefuel, logout,
            sessionStore, getVehicles, setActiveVehicle, stationsPrefetcher,
        )
```

Add these tests (e.g. near the `onVehicleSwitch`/`load` sections):

```kotlin
    @Test
    fun `load() triggers a stations prefetch`() {
        viewModel.load()

        // 1 call from init's load() in setUp(), +1 from this explicit call
        verify(exactly = 2) { stationsPrefetcher.prefetch() }
    }

    @Test
    fun `onVehicleSwitch() triggers a stations prefetch, both directly and via the load() it triggers`() {
        viewModel.onVehicleSwitch(2)

        // 1 call from init's load() in setUp(), +1 explicit call in onVehicleSwitch(),
        // +1 from the load() that onVehicleSwitch() calls internally
        verify(exactly = 3) { stationsPrefetcher.prefetch() }
    }
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.HomeViewModelTest"`
Expected: FAIL to compile — `HomeViewModel(...)` doesn't accept an 8th argument yet.

- [x] **Step 3: Implement**

Modify `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt`:

Add the import:

```kotlin
import com.flowfuel.app.feature.station.domain.NearbyStationsPrefetcher
```

Add the constructor parameter:

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getActiveVehicle: GetActiveVehicleUseCase,
    private val getDashboard: GetDashboardUseCase,
    private val createRefuel: CreateRefuelUseCase,
    private val logout: LogoutUseCase,
    private val sessionStore: SessionStore,
    private val getVehicles: GetVehiclesUseCase,
    private val setActiveVehicle: SetActiveVehicleUseCase,
    private val stationsPrefetcher: NearbyStationsPrefetcher,
) : ViewModel() {
```

Call `prefetch()` at the start of `load()`:

```kotlin
    fun load() {
        stationsPrefetcher.prefetch()
        _state.update { it.copy(screenState = HomeScreenState.Loading, submitError = null) }
        viewModelScope.launch {
```

(rest of `load()` body unchanged)

Call `prefetch()` inside `onVehicleSwitch()`, alongside `setActiveVehicle`:

```kotlin
    fun onVehicleSwitch(vehicleId: Int) {
        _state.update { it.copy(showVehicleSwitcher = false, vehicleSwitcherState = VehicleSwitcherState.Idle) }
        viewModelScope.launch {
            setActiveVehicle(vehicleId)
            stationsPrefetcher.prefetch()
            load()
        }
    }
```

(all other methods unchanged)

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.HomeViewModelTest"`
Expected: PASS (all existing tests + 2 new ones)

- [x] **Step 5: Run the full test suite**

Run: `./gradlew testDebugUnitTest`
Expected: PASS — no regressions in other suites (`StationRepositoryImplTest`, `FusedLocationProviderTest`, etc. are untouched by this plan).

- [x] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt
git commit -m "feat(postos): trigger stations prefetch from HomeViewModel"
```
