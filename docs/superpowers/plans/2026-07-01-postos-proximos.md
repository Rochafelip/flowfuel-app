# Postos Próximos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Postos" tab that finds nearby fuel stations and EV charging stations (combined list, sorted by distance) and lets the user launch turn-by-turn navigation to one of them, replacing the "Veículos" tab (vehicle management moves to Profile).

**Architecture:** New `feature/station` package following the project's established Clean Architecture pattern (`domain/model`, `domain` repository/provider interfaces, `domain/usecase`, `data/remote` DTOs+Retrofit, `data` repository impl, `presentation/list` ViewModel+UiState+Screen, `di` Hilt module). Location is obtained via `FusedLocationProviderClient`, wrapped behind a `LocationProvider` domain interface so the ViewModel stays testable. Station data comes from the FlowFuel backend's own `GET /stations/nearby` endpoint (the backend proxies Google Places so the API key never ships in the app) — that backend endpoint is **out of scope** for this plan; the Android side is built against the contract below and will show a network error state until the backend ships it.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, Retrofit + kotlinx.serialization, Coroutines/StateFlow, `com.google.android.gms:play-services-location`, Robolectric + MockK for tests.

## Global Constraints

- `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35` — no APIs above 35, no APIs below 26 without a check.
- Kotlin 2.0.21, Compose BOM `2024.12.01`, Hilt `2.53` — reuse existing version catalog entries in `gradle/libs.versions.toml`; only add new ones for `play-services-location`.
- Follow existing state pattern: `sealed interface XxxUiState` with `Loading/Success/Empty/Error`, one-time effects via `Channel<Effect>(BUFFERED)` collected with `LaunchedEffect`, DI via Hilt `@Binds` in `@InstallIn(SingletonComponent::class)`.
- Errors use the shared `AppError` sealed class + `apiCall {}` helper (`core/network/ApiCall.kt`) + `AppError.userMessage()` (`core/ui/ErrorMessages.kt`) — never introduce a parallel error type.
- Tests use `RobolectricTestRunner` with `@Config(sdk = [33])` and MockK, matching every existing test class in `app/src/test`.
- No new string resources — this codebase writes UI copy as inline string literals in Composables (see `VehiclesScreen.kt`), not `strings.xml`, for feature screen text. Keep that convention for the new screen.
- The Places-backed backend endpoint (`GET /stations/nearby`) does not exist yet — do not attempt to build or mock a backend in this repo; the app must degrade gracefully (network `Error` state) until it ships.

---

### Task 1: Location permission + `LocationProvider`

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/flowfuel/app/feature/station/domain/model/GeoLocation.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/station/domain/LocationProvider.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/station/data/FusedLocationProvider.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/station/data/FusedLocationProviderTest.kt`

**Interfaces:**
- Produces: `GeoLocation(latitude: Double, longitude: Double)`, `sealed interface LocationResult { data class Available(val location: GeoLocation); data object PermissionDenied; data object Unavailable }`, `interface LocationProvider { suspend fun getCurrentLocation(): LocationResult }`, `class FusedLocationProvider(context: Context) : LocationProvider`.

- [ ] **Step 1: Add the `play-services-location` dependency to the version catalog**

Edit `gradle/libs.versions.toml`. In the `[versions]` block, add a line right after `androidxTestExt = "1.2.1"`:

```toml
playServicesLocation = "21.3.0"
```

In the `[libraries]` block, add right after the `androidx-espresso-core` line:

```toml
play-services-location = { module = "com.google.android.gms:play-services-location", version.ref = "playServicesLocation" }
```

- [ ] **Step 2: Add the dependency to the app module**

In `app/build.gradle.kts`, inside the `dependencies { ... }` block, add right after `implementation(libs.androidx.biometric)`:

```kotlin
    implementation(libs.play.services.location)
```

- [ ] **Step 3: Declare the location permission**

In `app/src/main/AndroidManifest.xml`, add right after the `POST_NOTIFICATIONS` permission (line 8):

```xml
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

- [ ] **Step 4: Sync Gradle to verify the new dependency resolves**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath -q > /dev/null && echo OK`
Expected: `OK` printed, no dependency resolution error.

- [ ] **Step 5: Write the domain models**

Create `app/src/main/java/com/flowfuel/app/feature/station/domain/model/GeoLocation.kt`:

```kotlin
package com.flowfuel.app.feature.station.domain.model

data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
)

sealed interface LocationResult {
    data class Available(val location: GeoLocation) : LocationResult
    data object PermissionDenied : LocationResult
    data object Unavailable : LocationResult
}
```

- [ ] **Step 6: Write the `LocationProvider` interface**

Create `app/src/main/java/com/flowfuel/app/feature/station/domain/LocationProvider.kt`:

```kotlin
package com.flowfuel.app.feature.station.domain

import com.flowfuel.app.feature.station.domain.model.LocationResult

interface LocationProvider {
    suspend fun getCurrentLocation(): LocationResult
}
```

- [ ] **Step 7: Write the failing test for the permission-denied path**

Create `app/src/test/java/com/flowfuel/app/feature/station/data/FusedLocationProviderTest.kt`:

```kotlin
package com.flowfuel.app.feature.station.data

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.feature.station.domain.model.LocationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// Robolectric grants every manifest-declared permission by default, so the
// "not granted" path must be forced explicitly via denyPermissions().
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FusedLocationProviderTest {

    @Test
    fun `returns PermissionDenied when location permission not granted`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val provider = FusedLocationProvider(context)

        val result = provider.getCurrentLocation()

        assertEquals(LocationResult.PermissionDenied, result)
    }
}
```

- [ ] **Step 8: Run the test to verify it fails (class doesn't exist yet)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.station.data.FusedLocationProviderTest" 2>&1 | tail -40`
Expected: FAIL — `unresolved reference: FusedLocationProvider`.

- [ ] **Step 9: Implement `FusedLocationProvider`**

Create `app/src/main/java/com/flowfuel/app/feature/station/data/FusedLocationProvider.kt`:

```kotlin
package com.flowfuel.app.feature.station.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FusedLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationProvider {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): LocationResult {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return LocationResult.PermissionDenied

        val cancellationSource = CancellationTokenSource()
        return suspendCancellableCoroutine { continuation ->
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationSource.token)
                .addOnSuccessListener { location ->
                    val result = location?.let { GeoLocation(it.latitude, it.longitude) }
                        ?.let { LocationResult.Available(it) }
                        ?: LocationResult.Unavailable
                    continuation.resume(result)
                }
                .addOnFailureListener {
                    continuation.resume(LocationResult.Unavailable)
                }
            continuation.invokeOnCancellation { cancellationSource.cancel() }
        }
    }
}
```

- [ ] **Step 10: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.station.data.FusedLocationProviderTest" 2>&1 | tail -40`
Expected: PASS — 1 test, 0 failures.

- [ ] **Step 11: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/flowfuel/app/feature/station/domain/model/GeoLocation.kt app/src/main/java/com/flowfuel/app/feature/station/domain/LocationProvider.kt app/src/main/java/com/flowfuel/app/feature/station/data/FusedLocationProvider.kt app/src/test/java/com/flowfuel/app/feature/station/data/FusedLocationProviderTest.kt
git commit -m "feat(station): add location permission and FusedLocationProvider"
```

---

### Task 2: `Station` domain model + repository + backend call

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/station/domain/model/Station.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/station/domain/StationRepository.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/station/data/remote/StationApi.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/station/data/StationRepositoryImpl.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/station/domain/usecase/GetNearbyStationsUseCase.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/station/data/StationRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `GeoLocation` from Task 1 (`com.flowfuel.app.feature.station.domain.model.GeoLocation`).
- Produces: `enum class StationType { Fuel, Electric }`, `data class Station(placeId: String, name: String, type: StationType, distanceMeters: Int, rating: Double?, latitude: Double, longitude: Double)`, `interface StationRepository { suspend fun getNearbyStations(location: GeoLocation): AppResult<List<Station>> }`, `class GetNearbyStationsUseCase { suspend operator fun invoke(location: GeoLocation): AppResult<List<Station>> }`.

- [ ] **Step 1: Write the domain model**

Create `app/src/main/java/com/flowfuel/app/feature/station/domain/model/Station.kt`:

```kotlin
package com.flowfuel.app.feature.station.domain.model

enum class StationType { Fuel, Electric }

data class Station(
    val placeId: String,
    val name: String,
    val type: StationType,
    val distanceMeters: Int,
    val rating: Double?,
    val latitude: Double,
    val longitude: Double,
)
```

- [ ] **Step 2: Write the repository interface**

Create `app/src/main/java/com/flowfuel/app/feature/station/domain/StationRepository.kt`:

```kotlin
package com.flowfuel.app.feature.station.domain

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.Station

interface StationRepository {
    suspend fun getNearbyStations(location: GeoLocation): AppResult<List<Station>>
}
```

- [ ] **Step 3: Write the Retrofit API + DTO**

Create `app/src/main/java/com/flowfuel/app/feature/station/data/remote/StationApi.kt`:

```kotlin
package com.flowfuel.app.feature.station.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class StationResponseDto(
    val placeId: String,
    val name: String,
    /** "FUEL" ou "ELECTRIC" — ver StationRepositoryImpl.toDomain(). */
    val type: String,
    val distanceMeters: Int,
    val rating: Double? = null,
    val latitude: Double,
    val longitude: Double,
)

interface StationApi {
    /** Backend próprio, que proxeia o Google Places (gas_station + electric_vehicle_charging_station). */
    @GET("stations/nearby")
    suspend fun getNearbyStations(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radiusMeters: Int = 5000,
    ): List<StationResponseDto>
}
```

- [ ] **Step 4: Write the failing repository test**

Create `app/src/test/java/com/flowfuel/app/feature/station/data/StationRepositoryImplTest.kt`:

```kotlin
package com.flowfuel.app.feature.station.data

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.data.remote.StationApi
import com.flowfuel.app.feature.station.data.remote.StationResponseDto
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.StationType
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StationRepositoryImplTest {

    private val api: StationApi = mockk()
    private val repository = StationRepositoryImpl(api)
    private val location = GeoLocation(latitude = -8.05, longitude = -34.90)

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

        val result = repository.getNearbyStations(location)

        assertTrue(result is AppResult.Success)
        val stations = (result as AppResult.Success).value
        assertEquals(listOf("Shell Boa Viagem", "Ipiranga", "Estação Volta"), stations.map { it.name })
        assertEquals(StationType.Fuel, stations[0].type)
        assertEquals(StationType.Electric, stations[2].type)
    }

    @Test
    fun `returns empty list when backend has no nearby stations`() = runTest {
        coEvery { api.getNearbyStations(any(), any(), any()) } returns emptyList()

        val result = repository.getNearbyStations(location)

        assertTrue(result is AppResult.Success)
        assertEquals(emptyList<Any>(), (result as AppResult.Success).value)
    }

    @Test
    fun `maps network failure to AppError-Network`() = runTest {
        coEvery { api.getNearbyStations(any(), any(), any()) } throws IOException("no network")

        val result = repository.getNearbyStations(location)

        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.Network, (result as AppResult.Failure).error)
    }
}
```

- [ ] **Step 5: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.station.data.StationRepositoryImplTest" 2>&1 | tail -40`
Expected: FAIL — `unresolved reference: StationRepositoryImpl`.

- [ ] **Step 6: Implement `StationRepositoryImpl`**

Create `app/src/main/java/com/flowfuel/app/feature/station/data/StationRepositoryImpl.kt`:

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

private const val DEFAULT_RADIUS_METERS = 5000

@Singleton
class StationRepositoryImpl @Inject constructor(
    private val api: StationApi,
) : StationRepository {

    override suspend fun getNearbyStations(location: GeoLocation): AppResult<List<Station>> =
        apiCall {
            api.getNearbyStations(
                lat = location.latitude,
                lng = location.longitude,
                radiusMeters = DEFAULT_RADIUS_METERS,
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

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.station.data.StationRepositoryImplTest" 2>&1 | tail -40`
Expected: PASS — 3 tests, 0 failures.

- [ ] **Step 8: Write `GetNearbyStationsUseCase`**

Create `app/src/main/java/com/flowfuel/app/feature/station/domain/usecase/GetNearbyStationsUseCase.kt`:

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
    suspend operator fun invoke(location: GeoLocation): AppResult<List<Station>> =
        repository.getNearbyStations(location)
}
```

This is a one-line delegate with no branching logic — consistent with `GetVehiclesUseCase`, which also has no dedicated test file in this codebase. It's exercised indirectly by the `StationsViewModelTest` in Task 4.

- [ ] **Step 9: Compile check**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -30`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/domain/model/Station.kt app/src/main/java/com/flowfuel/app/feature/station/domain/StationRepository.kt app/src/main/java/com/flowfuel/app/feature/station/data/remote/StationApi.kt app/src/main/java/com/flowfuel/app/feature/station/data/StationRepositoryImpl.kt app/src/main/java/com/flowfuel/app/feature/station/domain/usecase/GetNearbyStationsUseCase.kt app/src/test/java/com/flowfuel/app/feature/station/data/StationRepositoryImplTest.kt
git commit -m "feat(station): add Station domain model, repository and backend call"
```

---

### Task 3: `StationsViewModel` + state + effects

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsUiState.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModel.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModelTest.kt`

**Interfaces:**
- Consumes: `GetNearbyStationsUseCase`, `LocationProvider`, `LocationResult` (Tasks 1–2), `SessionStore` (`com.flowfuel.app.core.datastore.SessionStore`, existing — has `suspend fun clear()`), `AppError`/`AppResult` (existing core).
- Produces: `sealed interface StationsUiState { Loading, Success(stations: List<Station>), Empty, Error(error: AppError), LocationUnavailable, PermissionRequired }`, `sealed interface StationsEffect { OpenNavigation(uri: String), NavigateToLogin }`, `class StationsViewModel { val state: StateFlow<StationsUiState>; val effects: Flow<StationsEffect>; fun load(); fun onRouteClick(station: Station) }`.

- [ ] **Step 1: Write the state and effect types**

Create `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsUiState.kt`:

```kotlin
package com.flowfuel.app.feature.station.presentation.list

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.feature.station.domain.model.Station

// ─── Estado da tela ───────────────────────────────────────────────────────────

sealed interface StationsUiState {
    data object Loading : StationsUiState
    data class Success(val stations: List<Station>) : StationsUiState
    data object Empty : StationsUiState
    data class Error(val error: AppError) : StationsUiState
    /** GPS desligado ou localização indisponível — permissão já concedida. */
    data object LocationUnavailable : StationsUiState
    /** Permissão de localização ainda não concedida (ou negada). */
    data object PermissionRequired : StationsUiState
}

// ─── Efeitos pontuais ─────────────────────────────────────────────────────────

sealed interface StationsEffect {
    data class OpenNavigation(val uri: String) : StationsEffect
    data object NavigateToLogin : StationsEffect
}
```

- [ ] **Step 2: Write the failing ViewModel tests**

Create `app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModelTest.kt`:

```kotlin
package com.flowfuel.app.feature.station.presentation.list

import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.LocationResult
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.model.StationType
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StationsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val getNearbyStations: GetNearbyStationsUseCase = mockk()
    private val locationProvider: LocationProvider = mockk()
    private val sessionStore: SessionStore = mockk(relaxed = true)

    private val location = GeoLocation(latitude = -8.05, longitude = -34.90)

    private fun station(id: String, distance: Int) = Station(
        placeId = id, name = "Posto $id", type = StationType.Fuel,
        distanceMeters = distance, rating = null, latitude = -8.05, longitude = -34.90,
    )

    private fun buildViewModel() = StationsViewModel(getNearbyStations, locationProvider, sessionStore)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Success state when stations are found`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location) } returns AppResult.Success(listOf(station("a", 100)))

        val vm = buildViewModel()

        val state = vm.state.value
        assertTrue(state is StationsUiState.Success)
        assertEquals(1, (state as StationsUiState.Success).stations.size)
    }

    @Test
    fun `Empty state when repository returns no stations`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location) } returns AppResult.Success(emptyList())

        val vm = buildViewModel()

        assertEquals(StationsUiState.Empty, vm.state.value)
    }

    @Test
    fun `Error state when repository fails`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location) } returns AppResult.Failure(AppError.Network)

        val vm = buildViewModel()

        assertEquals(StationsUiState.Error(AppError.Network), vm.state.value)
    }

    @Test
    fun `PermissionRequired state when location permission denied`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.PermissionDenied

        val vm = buildViewModel()

        assertEquals(StationsUiState.PermissionRequired, vm.state.value)
        coVerify(inverse = true) { getNearbyStations(any()) }
    }

    @Test
    fun `LocationUnavailable state when GPS has no fix`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Unavailable

        val vm = buildViewModel()

        assertEquals(StationsUiState.LocationUnavailable, vm.state.value)
    }

    @Test
    fun `onRouteClick emits OpenNavigation with lat,lng uri`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location) } returns AppResult.Success(listOf(station("a", 100)))
        val vm = buildViewModel()

        vm.effects.test {
            vm.onRouteClick(station("a", 100))
            val effect = awaitItem()
            assertTrue(effect is StationsEffect.OpenNavigation)
            assertEquals("google.navigation:q=-8.05,-34.9", (effect as StationsEffect.OpenNavigation).uri)
        }
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationsViewModelTest" 2>&1 | tail -50`
Expected: FAIL — `unresolved reference: StationsViewModel`.

- [ ] **Step 4: Implement `StationsViewModel`**

Create `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModel.kt`:

```kotlin
package com.flowfuel.app.feature.station.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.LocationProvider
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
                    when (val result = getNearbyStations(locationResult.location)) {
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

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationsViewModelTest" 2>&1 | tail -50`
Expected: PASS — 6 tests, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsUiState.kt app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModel.kt app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModelTest.kt
git commit -m "feat(station): add StationsViewModel with permission/location state handling"
```

---

### Task 4: `StationCard` composable + distance formatting

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationCard.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationCardTest.kt`

**Interfaces:**
- Consumes: `Station`, `StationType` (Task 2).
- Produces: `@Composable fun StationCard(station: Station, onRouteClick: () -> Unit, modifier: Modifier = Modifier)`, `internal fun formatDistance(meters: Int): String`.

- [ ] **Step 1: Write the failing test for distance formatting**

Create `app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationCardTest.kt`:

```kotlin
package com.flowfuel.app.feature.station.presentation.list

import org.junit.Assert.assertEquals
import org.junit.Test

class StationCardTest {

    @Test
    fun `formats distances under 1000m in meters`() {
        assertEquals("420 m", formatDistance(420))
        assertEquals("0 m", formatDistance(0))
        assertEquals("999 m", formatDistance(999))
    }

    @Test
    fun `formats distances of 1000m or more in kilometers with one decimal`() {
        assertEquals("1,0 km", formatDistance(1000))
        assertEquals("1,2 km", formatDistance(1200))
        assertEquals("12,0 km", formatDistance(12000))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationCardTest" 2>&1 | tail -30`
Expected: FAIL — `unresolved reference: formatDistance`.

- [ ] **Step 3: Implement `StationCard` and `formatDistance`**

Create `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationCard.kt`:

```kotlin
package com.flowfuel.app.feature.station.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
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
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(FFTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (station.type == StationType.Electric) {
                        Icons.Filled.EvStation
                    } else {
                        Icons.Filled.LocalGasStation
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = formatDistance(station.distanceMeters),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (station.rating != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = String.format(Locale("pt", "BR"), "%.1f", station.rating),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            FFButton(
                text = "Traçar rota",
                onClick = onRouteClick,
                variant = FFButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

internal fun formatDistance(meters: Int): String = if (meters < 1000) {
    "$meters m"
} else {
    String.format(Locale("pt", "BR"), "%.1f km", meters / 1000.0)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationCardTest" 2>&1 | tail -30`
Expected: PASS — 2 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationCard.kt app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationCardTest.kt
git commit -m "feat(station): add StationCard composable with distance formatting"
```

---

### Task 5: `StationsScreen` + permission flow + DI module

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsScreen.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/station/di/StationModule.kt`

**Interfaces:**
- Consumes: `StationsViewModel`, `StationsUiState`, `StationsEffect`, `StationCard` (Tasks 2–4), `FFTopBar`, `FFEmptyState`, `FFErrorState`, `FFSkeletonList`, `FFSnackbarHost`, `FFSnackbarVisuals`, `FFSnackbarKind` (existing design system), `userMessage()` (existing `core/ui/ErrorMessages.kt`).
- Produces: `@Composable fun StationsScreen(onNavigateToLogin: () -> Unit = {}, viewModel: StationsViewModel = hiltViewModel())`.

This task has no automated test — Compose screens aren't unit-tested in this codebase (see `VehiclesScreen.kt`, which has no matching test file). It's verified manually in Task 7.

- [ ] **Step 1: Implement `StationsScreen`**

Create `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsScreen.kt`:

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
```

- [ ] **Step 2: Write the Hilt DI module**

Create `app/src/main/java/com/flowfuel/app/feature/station/di/StationModule.kt`:

```kotlin
package com.flowfuel.app.feature.station.di

import com.flowfuel.app.feature.station.data.FusedLocationProvider
import com.flowfuel.app.feature.station.data.StationRepositoryImpl
import com.flowfuel.app.feature.station.data.remote.StationApi
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.StationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StationBindModule {
    @Binds @Singleton
    abstract fun bindStationRepository(impl: StationRepositoryImpl): StationRepository

    @Binds @Singleton
    abstract fun bindLocationProvider(impl: FusedLocationProvider): LocationProvider
}

@Module
@InstallIn(SingletonComponent::class)
object StationApiModule {
    @Provides @Singleton
    fun provideStationApi(retrofit: Retrofit): StationApi = retrofit.create(StationApi::class.java)
}
```

- [ ] **Step 3: Compile check**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -40`
Expected: `BUILD SUCCESSFUL`. If `PullToRefreshBox` is unresolved, confirm the Compose BOM (`libs.versions.toml` → `composeBom = "2024.12.01"`) is at least that version — it ships `PullToRefreshBox` in `androidx.compose.material3:material3` from that BOM onward.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsScreen.kt app/src/main/java/com/flowfuel/app/feature/station/di/StationModule.kt
git commit -m "feat(station): add StationsScreen with location permission flow and DI wiring"
```

---

### Task 6: Navigation — swap "Veículos" tab for "Postos", move vehicle management to Profile

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/navigation/Destinations.kt`
- Modify: `app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt`
- Modify: `app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/manage/VehiclesScreen.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileViewModel.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileScreen.kt`

**Interfaces:**
- Consumes: `StationsScreen` (Task 5).
- Produces: `MainDestinations.STATIONS`, `Destinations.VEHICLE_MANAGE`, `ProfileEffect.NavigateToVehicles`.

This task has no automated test — bottom nav wiring and navigation graphs aren't unit-tested in this codebase. It's verified manually in Task 7.

- [ ] **Step 1: Add the new routes, remove the tab route for Vehicles**

In `app/src/main/java/com/flowfuel/app/navigation/Destinations.kt`, add a new secondary route right after `VEHICLE_ODOMETER`:

```kotlin
    const val VEHICLE_ODOMETER  = "vehicle/odometer/{vehicleId}/{currentKm}"
    /** Gestão completa de veículos (lista/editar/detalhes), acessível a partir do Perfil. */
    const val VEHICLE_MANAGE    = "vehicle/manage"
```

Change the `MainDestinations` object at the bottom of the file from:

```kotlin
object MainDestinations {
    const val HOME     = "main/home"
    const val HISTORY  = "main/history"
    const val VEHICLES = "main/vehicles"
    const val EVENTS   = "main/events"
    const val PROFILE  = "main/profile"
}
```

to:

```kotlin
object MainDestinations {
    const val HOME     = "main/home"
    const val HISTORY  = "main/history"
    const val STATIONS = "main/stations"
    const val EVENTS   = "main/events"
    const val PROFILE  = "main/profile"
}
```

- [ ] **Step 2: Add `onBack` to `VehiclesScreen`**

In `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/manage/VehiclesScreen.kt`, change the function signature (currently at line 61) from:

```kotlin
fun VehiclesScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToAddVehicle: () -> Unit,
```

to:

```kotlin
fun VehiclesScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToAddVehicle: () -> Unit,
    onBack: (() -> Unit)? = null,
```

Then change the `FFTopBar` call (currently at lines 117-121) from:

```kotlin
            FFTopBar(
                title   = "Veículos",
                variant = FFTopBarVariant.Small,
            )
```

to:

```kotlin
            FFTopBar(
                title   = "Veículos",
                variant = FFTopBarVariant.Small,
                onBack  = onBack,
            )
```

- [ ] **Step 3: Update `MainContainerScreen` — swap the tab and remove now-unused vehicle-detail params**

In `app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt`, remove these five now-unused parameters from the `MainContainerScreen` signature (they existed only to thread through to the `VehiclesScreen` tab, which is moving out of the container):

```kotlin
    onNavigateToVehicleDetails: (vehicleId: Int) -> Unit = {},
    onNavigateToEditVehicle: (vehicleId: Int) -> Unit = {},
    onNavigateToVehicleEvents: (vehicleId: Int) -> Unit = {},
```
and
```kotlin
    vehicleUpdated: Boolean = false,
    onVehicleUpdatedConsumed: () -> Unit = {},
```

Remove the `VehiclesScreen` import and add the `StationsScreen` import:

```kotlin
import com.flowfuel.app.feature.vehicle.presentation.manage.VehiclesScreen
```
becomes
```kotlin
import com.flowfuel.app.feature.station.presentation.list.StationsScreen
```

Add the needed icon imports next to the existing `DirectionsCar` ones:

```kotlin
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.outlined.LocalGasStation
```

(remove `DirectionsCar` imports — `filled.DirectionsCar` / `outlined.DirectionsCar` — since they were only used by the Vehicles tab item)

Add a new `onNavigateToVehicles` parameter to the `MainContainerScreen` signature — insert it right after the existing `onNavigateToRefuelDetails` parameter:

```kotlin
    onNavigateToRefuelDetails: (refuelId: Int) -> Unit = {},
    onNavigateToVehicles: () -> Unit = {},
```

In the `tabs` list, replace:

```kotlin
            FFBottomItem(
                route        = MainDestinations.VEHICLES,
                label        = "Veículos",
                icon         = Icons.Outlined.DirectionsCar,
                selectedIcon = Icons.Filled.DirectionsCar,
            ),
```

with:

```kotlin
            FFBottomItem(
                route        = MainDestinations.STATIONS,
                label        = "Postos",
                icon         = Icons.Outlined.LocalGasStation,
                selectedIcon = Icons.Filled.LocalGasStation,
            ),
```

Replace the `// ── Veículos ──` composable block:

```kotlin
            // ── Veículos ──────────────────────────────────────────────────────
            composable(MainDestinations.VEHICLES) {
                VehiclesScreen(
                    onNavigateToLogin           = onNavigateToLogin,
                    onNavigateToAddVehicle      = onNavigateToAddVehicle,
                    onNavigateToVehicleDetails  = onNavigateToVehicleDetails,
                    onNavigateToEditVehicle     = onNavigateToEditVehicle,
                    onNavigateToVehicleEvents   = onNavigateToVehicleEvents,
                    vehicleUpdated              = vehicleUpdated,
                    onVehicleUpdatedConsumed    = onVehicleUpdatedConsumed,
                )
            }
```

with:

```kotlin
            // ── Postos ────────────────────────────────────────────────────────
            composable(MainDestinations.STATIONS) {
                StationsScreen(onNavigateToLogin = onNavigateToLogin)
            }
```

Add a new `onNavigateToVehicles: () -> Unit = {}` parameter to the `MainContainerScreen` signature (next to the other `onNavigateTo*` params), and thread it into the `ProfileScreen` call:

```kotlin
            composable(MainDestinations.PROFILE) {
                ProfileScreen(
                    onNavigateToLogin          = onNavigateToLogin,
                    onNavigateToEditProfile    = onNavigateToEditProfile,
                    onNavigateToChangePassword = onNavigateToChangePassword,
                    onNavigateToVehicles       = onNavigateToVehicles,
                    passwordChanged            = passwordChanged,
                    onPasswordChangedConsumed  = onPasswordChangedConsumed,
                    profileUpdated             = profileUpdated,
                    onProfileUpdatedConsumed   = onProfileUpdatedConsumed,
                )
            }
```

- [ ] **Step 4: Add `NavigateToVehicles` to `ProfileViewModel`**

In `app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileViewModel.kt`, add to the `ProfileEffect` sealed interface (after `NavigateToChangePassword`):

```kotlin
    data object NavigateToVehicles : ProfileEffect
```

Add a new function next to `onChangePassword()`:

```kotlin
    fun onManageVehicles() {
        viewModelScope.launch { _effects.send(ProfileEffect.NavigateToVehicles) }
    }
```

- [ ] **Step 5: Add "Meus veículos" to `ProfileScreen`**

In `app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileScreen.kt`, add the icon import next to the others:

```kotlin
import androidx.compose.material.icons.outlined.DirectionsCar
```

Add `onNavigateToVehicles: () -> Unit = {}` to the `ProfileScreen` function signature (next to `onNavigateToChangePassword`).

In the effects `LaunchedEffect`, add a branch:

```kotlin
                ProfileEffect.NavigateToVehicles       -> onNavigateToVehicles()
```

In the `ProfileContent(...)` call inside `ProfileScreen`, add a new line right after `onChangePassword = viewModel::onChangePassword,`:

```kotlin
                    onManageVehicles      = viewModel::onManageVehicles,
```

In the `private fun ProfileContent(...)` signature, add a new parameter right after `onChangePassword: () -> Unit,`:

```kotlin
    onManageVehicles: () -> Unit,
```

In `ProfileContent`, add a new `ProfileActionRow` **before** the existing "Editar perfil" row:

```kotlin
        ProfileActionRow(
            icon    = Icons.Outlined.DirectionsCar,
            label   = "Meus veículos",
            onClick = onManageVehicles,
        )
        HorizontalDivider()
        ProfileActionRow(
            icon    = Icons.Outlined.Edit,
            label   = "Editar perfil",
            onClick = onEditProfile,
        )
```

- [ ] **Step 6: Wire the new route in `FlowFuelNavHost`**

In `app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt`, add the composable for `VEHICLE_MANAGE` right after the `VEHICLE_EDIT` composable block (before the `REFUEL_DETAILS` block):

```kotlin
        // ── Gestão de veículos (acessível pelo Perfil) ─────────────────────
        composable(Destinations.VEHICLE_MANAGE) { entry ->
            val vehicleUpdated by entry.savedStateHandle
                .getStateFlow("vehicle_updated", false)
                .collectAsStateWithLifecycle()

            VehiclesScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLogin = {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToAddVehicle = { navController.navigate(Destinations.VEHICLE_ADD) },
                onNavigateToVehicleDetails = { vehicleId ->
                    navController.navigate(Destinations.vehicleDetails(vehicleId))
                },
                onNavigateToEditVehicle = { vehicleId ->
                    navController.navigate(Destinations.vehicleEdit(vehicleId))
                },
                onNavigateToVehicleEvents = { vehicleId ->
                    navController.navigate(Destinations.vehicleEvents(vehicleId))
                },
                vehicleUpdated = vehicleUpdated,
                onVehicleUpdatedConsumed = { entry.savedStateHandle["vehicle_updated"] = false },
            )
        }
```

Update the `VEHICLE_EDIT` composable's `onSaved` callback — change from unconditionally signalling `MAIN_CONTAINER`:

```kotlin
                onSaved = {
                    // Sinaliza a VehiclesScreen para recarregar a lista ao retornar
                    runCatching {
                        navController.getBackStackEntry(Destinations.MAIN_CONTAINER)
                            .savedStateHandle["vehicle_updated"] = true
                    }
                    navController.popBackStack()
                },
```

to signal `VEHICLE_MANAGE` instead (it's no longer a tab, so there's no container fallback to signal):

```kotlin
                onSaved = {
                    // Sinaliza a tela de gestão de veículos para recarregar ao retornar
                    runCatching {
                        navController.getBackStackEntry(Destinations.VEHICLE_MANAGE)
                            .savedStateHandle["vehicle_updated"] = true
                    }
                    navController.popBackStack()
                },
```

In the `MAIN_CONTAINER` composable block, remove the now-unused `vehicleUpdated` / `onVehicleUpdatedConsumed` collection and the five removed params from the `MainContainerScreen(...)` call (matching the signature change from Step 3):

Remove:
```kotlin
            val vehicleUpdated by entry.savedStateHandle
                .getStateFlow("vehicle_updated", false)
                .collectAsStateWithLifecycle()
```
and remove these lines from the `MainContainerScreen(...)` call:
```kotlin
                onNavigateToVehicleDetails = { vehicleId ->
                    navController.navigate(Destinations.vehicleDetails(vehicleId))
                },
                onNavigateToEditVehicle = { vehicleId ->
                    navController.navigate(Destinations.vehicleEdit(vehicleId))
                },
                onNavigateToVehicleEvents = { vehicleId ->
                    navController.navigate(Destinations.vehicleEvents(vehicleId))
                },
```
and:
```kotlin
                vehicleUpdated = vehicleUpdated,
                onVehicleUpdatedConsumed = {
                    entry.savedStateHandle["vehicle_updated"] = false
                },
```

Add a new line in the same `MainContainerScreen(...)` call to wire the Profile → Vehicles navigation:

```kotlin
                onNavigateToVehicles = {
                    navController.navigate(Destinations.VEHICLE_MANAGE)
                },
```

- [ ] **Step 7: Compile check**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -60`
Expected: `BUILD SUCCESSFUL`. Fix any leftover reference to the removed `MainDestinations.VEHICLES` or the removed `MainContainerScreen` params if the compiler flags them.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/navigation/Destinations.kt app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/manage/VehiclesScreen.kt app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileViewModel.kt app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileScreen.kt
git commit -m "feat(nav): replace Veículos tab with Postos, move vehicle management to Perfil"
```

---

### Task 7: Full test suite + manual verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest 2>&1 | tail -60`
Expected: `BUILD SUCCESSFUL`, no failing tests (existing suite + the new Station tests from Tasks 1–4).

- [ ] **Step 2: Full debug build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -60`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Manual verification on the emulator**

Use the `run-android-emulator` skill to install and launch the debug build, then check by hand:

1. Bottom nav shows **Home, Histórico, Postos, Eventos, Perfil** (no "Veículos").
2. Tapping **Postos** prompts for location permission (first launch); denying it shows "Precisamos da sua localização" with a button to request again; granting it shows either a station list (if the backend endpoint already exists) or a network error state with "Tentar novamente" (expected until the backend ships `GET /stations/nearby` — the spec explicitly documents this as an external dependency).
3. **Perfil** now shows a "Meus veículos" row above "Editar perfil"; tapping it opens the vehicle list with a back arrow, and normal vehicle list/edit/delete/detail flows still work exactly as before.
4. Editing a vehicle from that screen and saving refreshes the list on return (no stale data).

- [ ] **Step 4: Report results**

If all checks in Step 3 pass, the feature is complete except for the backend endpoint. If the backend team has already deployed `GET /stations/nearby`, confirm the station list renders with real names/distances/ratings and that "Traçar rota" opens the device's navigation app chooser.
