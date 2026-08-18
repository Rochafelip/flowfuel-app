# Redesenho do Android Auto: menu de navegação Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Substituir a tela única do Android Auto (`AutoDashboardScreen`, um grid de 4 stats + ação) por um menu de navegação (`AutoMenuScreen`) com 4 destinos: Registrar abastecimento (existente), Postos próximos (novo), Eventos (novo) e Informações importantes para o motorista (novo).

**Architecture:** 3 telas novas (`AutoStationsScreen`, `AutoEventsScreen`, `AutoDriverInfoScreen`) reaproveitam use cases que já existem para o app no celular (`GetNearbyStationsUseCase`+`LocationProvider`, `GetVehicleEventsUseCase`, `GetUpcomingMaintenanceUseCase`). Uma 4ª tela nova (`AutoMenuScreen`) substitui `AutoDashboardScreen` como raiz pós-login e empurra (`screenManager.push`) pras 4 telas de destino. Todas seguem o padrão sealed `State` (Loading/Success/Error) + `MessageTemplate` já usado em `AutoDashboardScreen`/`AutoRefuelConfirmScreen`. Um trecho de formatação de texto hoje só dentro de um `@Composable` (`UpcomingEventsSection.toPresentation()`) é extraído pra uma função Kotlin pura reaproveitável pelo Android Auto.

**Tech Stack:** Kotlin, Car App Library 1.4.0 (`ListTemplate`/`Row`/`ItemList`, já disponíveis desde API level 1 — sem mudança de `minCarApiLevel`), Hilt (`SingletonComponent`), Robolectric + `TestCarContext` (`@Config(sdk = [33])`), MockK.

**Spec:** `docs/superpowers/specs/2026-08-18-android-auto-menu-redesign-design.md`

## Global Constraints

- Raio de busca de postos fixo em `3_000` metros (mesmo valor de `DEFAULT_STATION_RADIUS_METERS` usado no celular) — sem seletor manual no carro.
- Máximo de 6 postos exibidos em Postos próximos, ordenados por `distanceMeters` ascendente.
- Máximo de 10 eventos exibidos em Eventos, ordenados por `eventDate` descendente.
- Tela de Eventos é somente leitura — nenhuma `Row` tem `onClickListener`.
- Formatação monetária/numérica sempre com `Locale("pt", "BR")`, igual ao padrão já usado em `AutoDashboardScreen`.
- Testes de tela usam Robolectric (`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [33])`) com `TestCarContext.createCarContext(...)`, seguindo exatamente o padrão de `AutoDashboardScreenTest.kt`.
- Projeto é módulo único (`:app`, ver `settings.gradle.kts`) — `internal` do Kotlin é visível no módulo inteiro, então `formatDistance()` (hoje `internal` em `feature.station.presentation.list`) pode ser importada direto por `feature.auto` sem mudar visibilidade.
- Toda tela pós-login usa `AppError.Unauthorized` → mensagem "Sessão expirada. Abra o FlowFuel no celular para entrar novamente." sem ação de retry; qualquer outro erro → mensagem genérica + ação "Tentar novamente" que chama `loadData()`.

---

### Task 1: Extrair formatação de status de manutenção pra função Kotlin pura

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/UpcomingEventsSection.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/home/domain/model/HomeModelsTest.kt`

**Interfaces:**
- Produces: `data class MaintenanceStatusText(val title: String, val subtitle: String)` e `fun UpcomingMaintenanceItem.toStatusText(): MaintenanceStatusText`, ambos em `com.flowfuel.app.feature.home.domain.model` — usados pelo Task 4 (`AutoDriverInfoScreen`) e por `UpcomingEventsSection` (celular).

- [ ] **Step 1: Escrever o teste que falha**

Criar `app/src/test/java/com/flowfuel/app/feature/home/domain/model/HomeModelsTest.kt`:

```kotlin
package com.flowfuel.app.feature.home.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeModelsTest {

    @Test
    fun `troca de oleo em dia mostra km restantes`() {
        val item = UpcomingMaintenanceItem(type = UpcomingMaintenanceType.OIL_CHANGE, remainingKm = 320)
        val status = item.toStatusText()
        assertEquals("Troca de óleo", status.title)
        assertEquals("Em 320 km", status.subtitle)
    }

    @Test
    fun `rodizio de pneus atrasado por km`() {
        val item = UpcomingMaintenanceItem(
            type = UpcomingMaintenanceType.TIRE_ROTATION, remainingKm = -150, isOverdue = true,
        )
        val status = item.toStatusText()
        assertEquals("Rodízio de pneus", status.title)
        assertEquals("Atrasado 150 km", status.subtitle)
    }

    @Test
    fun `licenciamento vence em N dias`() {
        val item = UpcomingMaintenanceItem(type = UpcomingMaintenanceType.LICENSING, remainingDays = 18)
        val status = item.toStatusText()
        assertEquals("Licenciamento", status.title)
        assertEquals("Vence em 18 dias", status.subtitle)
    }

    @Test
    fun `licenciamento vence hoje`() {
        val item = UpcomingMaintenanceItem(type = UpcomingMaintenanceType.LICENSING, remainingDays = 0)
        assertEquals("Vence hoje", item.toStatusText().subtitle)
    }

    @Test
    fun `licenciamento atrasado por dias`() {
        val item = UpcomingMaintenanceItem(
            type = UpcomingMaintenanceType.LICENSING, remainingDays = -5, isOverdue = true,
        )
        assertEquals("Venceu há 5 dias", item.toStatusText().subtitle)
    }

    @Test
    fun `licenciamento sem data configurada pede pra definir`() {
        val item = UpcomingMaintenanceItem(type = UpcomingMaintenanceType.LICENSING, needsSetup = true)
        assertEquals("Defina a data de licenciamento", item.toStatusText().subtitle)
    }
}
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.home.domain.model.HomeModelsTest"`
Expected: FAIL — erro de compilação `Unresolved reference: toStatusText` (a função ainda não existe).

- [ ] **Step 3: Adicionar `MaintenanceStatusText`/`toStatusText()` em HomeModels.kt**

No fim de `app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt` (depois do `data class UpcomingMaintenanceItem`), adicionar:

```kotlin

/** Texto de status pronto pra exibição, sem depender de Compose — usado no celular e no Android Auto. */
data class MaintenanceStatusText(val title: String, val subtitle: String)

fun UpcomingMaintenanceItem.toStatusText(): MaintenanceStatusText {
    val title = when (type) {
        UpcomingMaintenanceType.OIL_CHANGE -> "Troca de óleo"
        UpcomingMaintenanceType.TIRE_ROTATION -> "Rodízio de pneus"
        UpcomingMaintenanceType.LICENSING -> "Licenciamento"
    }
    val subtitle = when {
        needsSetup -> "Defina a data de licenciamento"
        isOverdue && remainingKm != null -> "Atrasado ${-remainingKm} km"
        isOverdue && remainingDays != null -> overdueDaysLabel(-remainingDays)
        remainingKm != null -> "Em $remainingKm km"
        remainingDays != null -> dueDaysLabel(remainingDays)
        else -> "—"
    }
    return MaintenanceStatusText(title, subtitle)
}

private fun dueDaysLabel(days: Int): String = when (days) {
    0 -> "Vence hoje"
    1 -> "Vence em 1 dia"
    else -> "Vence em $days dias"
}

private fun overdueDaysLabel(days: Int): String = when (days) {
    0 -> "Venceu hoje"
    1 -> "Venceu há 1 dia"
    else -> "Venceu há $days dias"
}
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.home.domain.model.HomeModelsTest"`
Expected: `BUILD SUCCESSFUL`, 6 testes passam.

- [ ] **Step 5: Fazer `UpcomingEventsSection` reaproveitar `toStatusText()`**

Em `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/UpcomingEventsSection.kt`, adicionar o import (junto dos outros imports de `com.flowfuel.app.feature.home.domain.model`):

```kotlin
import com.flowfuel.app.feature.home.domain.model.toStatusText
```

Substituir o método `toPresentation()` inteiro (que hoje calcula `title`/`subtitle` inline) por:

```kotlin
@Composable
private fun UpcomingMaintenanceItem.toPresentation(): CardPresentation {
    val accent = when {
        isOverdue -> MaterialTheme.colorScheme.error
        type == UpcomingMaintenanceType.OIL_CHANGE -> FFTheme.semanticColors.warning
        type == UpcomingMaintenanceType.TIRE_ROTATION -> FFTheme.semanticColors.success
        else -> MaterialTheme.colorScheme.secondary
    }
    val icon = when (type) {
        UpcomingMaintenanceType.OIL_CHANGE -> EventCategory.OIL_CHANGE.icon
        UpcomingMaintenanceType.TIRE_ROTATION -> EventCategory.TIRES.icon
        UpcomingMaintenanceType.LICENSING -> EventCategory.DOCUMENTS.icon
    }
    val statusText = toStatusText()
    return CardPresentation(icon, accent, statusText.title, statusText.subtitle)
}
```

E remover as duas funções privadas `dueDaysLabel`/`overdueDaysLabel` que ficavam logo abaixo dela nesse arquivo (agora vivem em `HomeModels.kt`).

- [ ] **Step 6: Rodar a suíte de Home pra confirmar que não há regressão**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.home.*"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/UpcomingEventsSection.kt
git add app/src/test/java/com/flowfuel/app/feature/home/domain/model/HomeModelsTest.kt
git commit -m "refactor(home): extract maintenance status text into toStatusText(), reusable outside Compose"
```

---

### Task 2: AutoStationsScreen (Postos próximos)

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/auto/stations/AutoStationsScreen.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/auto/AutoStationsScreenTest.kt`

**Interfaces:**
- Consumes: `GetNearbyStationsUseCase.invoke(location: GeoLocation, radiusMeters: Int): AppResult<List<Station>>` (`com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase`); `LocationProvider.getCurrentLocation(): LocationResult` (`com.flowfuel.app.feature.station.domain.LocationProvider`, `LocationResult` em `com.flowfuel.app.feature.station.domain.model.GeoLocation.kt`); `formatDistance(meters: Int): String` (`com.flowfuel.app.feature.station.presentation.list.StationCard.kt`); `ActiveVehicleData` (`com.flowfuel.app.feature.home.domain.model`).
- Produces: `class AutoStationsScreen(carContext: CarContext, vehicle: ActiveVehicleData, getNearbyStations: GetNearbyStationsUseCase, locationProvider: LocationProvider) : Screen(carContext)` — usado pelo Task 5 (`AutoMenuScreen`).

- [ ] **Step 1: Escrever o teste que falha**

Criar `app/src/test/java/com/flowfuel/app/feature/auto/AutoStationsScreenTest.kt`:

```kotlin
package com.flowfuel.app.feature.auto

import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auto.stations.AutoStationsScreen
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.LocationResult
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.model.StationType
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import io.mockk.coEvery
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
class AutoStationsScreenTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val carContext: TestCarContext
        get() = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val fuelVehicle = ActiveVehicleData(
        id = 1, brand = "VW", model = "Fox", fuelSubType = null,
        capacity = null, licensePlate = "ABC1D23", energyType = "COMBUSTION", currentKm = 67270,
    )
    private val location = GeoLocation(-23.5, -46.6)

    private fun station(
        name: String, type: StationType, distance: Int,
        lat: Double = -23.55, lng: Double = -46.63,
    ) = Station(
        placeId = name, name = name, type = type, distanceMeters = distance,
        rating = null, latitude = lat, longitude = lng,
    )

    @Test
    fun `estado inicial retorna MessageTemplate loading`() {
        val screen = AutoStationsScreen(carContext, fuelVehicle, mockk(), mockk())
        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `permissao negada retorna mensagem sem acao de retry`() = runTest {
        val locationProvider: LocationProvider = mockk()
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.PermissionDenied

        val screen = AutoStationsScreen(carContext, fuelVehicle, mockk(), locationProvider)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isEmpty())
    }

    @Test
    fun `sem fix de gps retorna mensagem informativa`() = runTest {
        val locationProvider: LocationProvider = mockk()
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Unavailable

        val screen = AutoStationsScreen(carContext, fuelVehicle, mockk(), locationProvider)
        screen.loadData()

        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `veiculo a combustao filtra so postos de combustivel, ordena por distancia e limita a 6`() = runTest {
        val locationProvider: LocationProvider = mockk()
        val getNearbyStations: GetNearbyStationsUseCase = mockk()
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        val stations = (1..8).map { station("Posto $it", StationType.Fuel, distance = it * 100) } +
            station("Eletroposto", StationType.Electric, distance = 50)
        coEvery { getNearbyStations(location, 3_000) } returns AppResult.Success(stations)

        val screen = AutoStationsScreen(carContext, fuelVehicle, getNearbyStations, locationProvider)
        screen.loadData()

        val template = screen.onGetTemplate() as ListTemplate
        val items = template.singleList!!.items
        assertTrue("deve limitar a 6 postos", items.size == 6)
        assertEquals("Posto 1", (items.first() as Row).title.toString())
    }

    @Test
    fun `veiculo eletrico filtra so postos eletricos`() = runTest {
        val electricVehicle = fuelVehicle.copy(energyType = "ELECTRIC")
        val locationProvider: LocationProvider = mockk()
        val getNearbyStations: GetNearbyStationsUseCase = mockk()
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        val stations = listOf(
            station("Posto combustível", StationType.Fuel, distance = 10),
            station("Eletroposto", StationType.Electric, distance = 500),
        )
        coEvery { getNearbyStations(location, 3_000) } returns AppResult.Success(stations)

        val screen = AutoStationsScreen(carContext, electricVehicle, getNearbyStations, locationProvider)
        screen.loadData()

        val template = screen.onGetTemplate() as ListTemplate
        val items = template.singleList!!.items
        assertTrue(items.size == 1)
        assertEquals("Eletroposto", (items.first() as Row).title.toString())
    }

    @Test
    fun `lista vazia apos filtro retorna mensagem informativa`() = runTest {
        val locationProvider: LocationProvider = mockk()
        val getNearbyStations: GetNearbyStationsUseCase = mockk()
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, 3_000) } returns AppResult.Success(emptyList())

        val screen = AutoStationsScreen(carContext, fuelVehicle, getNearbyStations, locationProvider)
        screen.loadData()

        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `erro de rede retorna mensagem com acao de tentar novamente`() = runTest {
        val locationProvider: LocationProvider = mockk()
        val getNearbyStations: GetNearbyStationsUseCase = mockk()
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, 3_000) } returns AppResult.Failure(AppError.Network)

        val screen = AutoStationsScreen(carContext, fuelVehicle, getNearbyStations, locationProvider)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isNotEmpty())
    }

    @Test
    fun `erro 401 retorna mensagem sem acao de retry`() = runTest {
        val locationProvider: LocationProvider = mockk()
        val getNearbyStations: GetNearbyStationsUseCase = mockk()
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, 3_000) } returns AppResult.Failure(AppError.Unauthorized)

        val screen = AutoStationsScreen(carContext, fuelVehicle, getNearbyStations, locationProvider)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isEmpty())
    }

    @Test
    fun `testNavigateTo dispara ACTION_NAVIGATE com geo do posto`() {
        val testContext = carContext
        val screen = AutoStationsScreen(testContext, fuelVehicle, mockk(), mockk())
        val target = station("Posto Ipiranga", StationType.Fuel, distance = 200, lat = -23.55, lng = -46.63)

        screen.testNavigateTo(target)

        val intent = testContext.startCarAppIntents.single()
        assertEquals(CarContext.ACTION_NAVIGATE, intent.action)
        assertEquals(Uri.parse("geo:-23.55,-46.63"), intent.data)
    }
}
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.auto.AutoStationsScreenTest"`
Expected: FAIL — `Unresolved reference: AutoStationsScreen` (classe ainda não existe).

- [ ] **Step 3: Implementar AutoStationsScreen**

Criar `app/src/main/java/com/flowfuel/app/feature/auto/stations/AutoStationsScreen.kt`:

```kotlin
package com.flowfuel.app.feature.auto.stations

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.model.LocationResult
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.model.StationType
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import com.flowfuel.app.feature.station.presentation.list.formatDistance
import kotlinx.coroutines.launch

private const val STATION_RADIUS_METERS = 3_000
private const val MAX_STATIONS_SHOWN = 6

class AutoStationsScreen(
    carContext: CarContext,
    private val vehicle: ActiveVehicleData,
    private val getNearbyStations: GetNearbyStationsUseCase,
    private val locationProvider: LocationProvider,
) : Screen(carContext) {

    private sealed interface State {
        data object Loading : State
        data class Success(val stations: List<Station>) : State
        data object PermissionDenied : State
        data object Empty : State
        data class Error(val error: AppError) : State
    }

    private var state: State = State.Loading

    init {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) loadData()
        })
    }

    internal fun loadData() {
        lifecycleScope.launch {
            state = State.Loading
            invalidate()

            when (val locationResult = locationProvider.getCurrentLocation()) {
                LocationResult.PermissionDenied -> {
                    state = State.PermissionDenied
                    invalidate()
                }
                LocationResult.Unavailable -> {
                    state = State.Empty
                    invalidate()
                }
                is LocationResult.Available -> {
                    val wantedType = if (vehicle.energyType == "ELECTRIC") StationType.Electric else StationType.Fuel
                    when (val result = getNearbyStations(locationResult.location, STATION_RADIUS_METERS)) {
                        is AppResult.Success -> {
                            val stations = result.value
                                .filter { it.type == wantedType }
                                .take(MAX_STATIONS_SHOWN)
                            state = if (stations.isEmpty()) State.Empty else State.Success(stations)
                            invalidate()
                        }
                        is AppResult.Failure -> {
                            state = State.Error(result.error)
                            invalidate()
                        }
                    }
                }
            }
        }
    }

    internal fun testNavigateTo(station: Station) = navigateTo(station)

    private fun navigateTo(station: Station) {
        carContext.startCarApp(
            Intent(CarContext.ACTION_NAVIGATE)
                .setData(Uri.parse("geo:${station.latitude},${station.longitude}"))
        )
    }

    override fun onGetTemplate(): Template = when (val s = state) {
        State.Loading -> loadingTemplate()
        State.PermissionDenied -> messageTemplate(
            "Ative a permissão de localização no FlowFuel do celular para ver postos próximos."
        )
        State.Empty -> messageTemplate("Nenhum posto encontrado a até 3 km.")
        is State.Error -> errorTemplate(s.error)
        is State.Success -> successTemplate(s.stations)
    }

    private fun loadingTemplate(): Template =
        MessageTemplate.Builder("Carregando…")
            .setTitle("Postos próximos")
            .setHeaderAction(Action.BACK)
            .setLoading(true)
            .build()

    private fun messageTemplate(message: String): Template =
        MessageTemplate.Builder(message)
            .setTitle("Postos próximos")
            .setHeaderAction(Action.BACK)
            .build()

    private fun errorTemplate(error: AppError): Template {
        val msg = if (error == AppError.Unauthorized)
            "Sessão expirada. Abra o FlowFuel no celular para entrar novamente."
        else
            "Erro ao carregar postos. Verifique sua conexão."
        val builder = MessageTemplate.Builder(msg)
            .setTitle("Postos próximos")
            .setHeaderAction(Action.BACK)
        if (error != AppError.Unauthorized) {
            builder.addAction(
                Action.Builder()
                    .setTitle("Tentar novamente")
                    .setOnClickListener { loadData() }
                    .build()
            )
        }
        return builder.build()
    }

    private fun successTemplate(stations: List<Station>): Template {
        val listBuilder = ItemList.Builder()
        stations.forEach { station ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(station.name)
                    .addText(formatDistance(station.distanceMeters))
                    .setOnClickListener { navigateTo(station) }
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("Postos próximos")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.auto.AutoStationsScreenTest"`
Expected: `BUILD SUCCESSFUL`, 9 testes passam.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/auto/stations/AutoStationsScreen.kt
git add app/src/test/java/com/flowfuel/app/feature/auto/AutoStationsScreenTest.kt
git commit -m "feat(auto): add AutoStationsScreen (Postos próximos) with real navigation on tap"
```

---

### Task 3: AutoEventsScreen (Eventos)

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/auto/events/AutoEventsScreen.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/auto/AutoEventsScreenTest.kt`

**Interfaces:**
- Consumes: `GetVehicleEventsUseCase.invoke(vehicleId: Int): AppResult<List<VehicleEvent>>` (`com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsUseCase`); `VehicleEvent` (`id`, `category: EventCategory`, `title`, `amount: Double?`, `eventDate: String` ISO `yyyy-MM-dd`) em `com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEventModels.kt`; `EventCategory.label: String`.
- Produces: `class AutoEventsScreen(carContext: CarContext, vehicleId: Int, getVehicleEvents: GetVehicleEventsUseCase) : Screen(carContext)` — usado pelo Task 5 (`AutoMenuScreen`).

- [ ] **Step 1: Escrever o teste que falha**

Criar `app/src/test/java/com/flowfuel/app/feature/auto/AutoEventsScreenTest.kt`:

```kotlin
package com.flowfuel.app.feature.auto

import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auto.events.AutoEventsScreen
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoEventsScreenTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val carContext: TestCarContext
        get() = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun event(
        id: Int, title: String, date: String, amount: Double? = null,
        category: EventCategory = EventCategory.MAINTENANCE,
    ) = VehicleEvent(
        id = id, vehicleId = 1, category = category, title = title, description = null,
        amount = amount, eventDate = date, odometerKm = null, notes = null,
        receiptUrl = null, createdAt = null, updatedAt = null,
    )

    @Test
    fun `estado inicial retorna MessageTemplate loading`() {
        val screen = AutoEventsScreen(carContext, vehicleId = 1, getVehicleEvents = mockk())
        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `lista vazia retorna mensagem informativa`() = runTest {
        val getVehicleEvents: GetVehicleEventsUseCase = mockk()
        coEvery { getVehicleEvents(1) } returns AppResult.Success(emptyList())

        val screen = AutoEventsScreen(carContext, vehicleId = 1, getVehicleEvents)
        screen.loadData()

        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `erro de rede retorna mensagem com acao de tentar novamente`() = runTest {
        val getVehicleEvents: GetVehicleEventsUseCase = mockk()
        coEvery { getVehicleEvents(1) } returns AppResult.Failure(AppError.Network)

        val screen = AutoEventsScreen(carContext, vehicleId = 1, getVehicleEvents)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isNotEmpty())
    }

    @Test
    fun `erro 401 retorna mensagem sem acao de retry`() = runTest {
        val getVehicleEvents: GetVehicleEventsUseCase = mockk()
        coEvery { getVehicleEvents(1) } returns AppResult.Failure(AppError.Unauthorized)

        val screen = AutoEventsScreen(carContext, vehicleId = 1, getVehicleEvents)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isEmpty())
    }

    @Test
    fun `sucesso ordena por data desc e limita a 10, sem onClick nas linhas`() = runTest {
        val getVehicleEvents: GetVehicleEventsUseCase = mockk()
        val events = (1..12).map { event(it, "Evento $it", date = "2026-01-%02d".format(it)) }
        coEvery { getVehicleEvents(1) } returns AppResult.Success(events)

        val screen = AutoEventsScreen(carContext, vehicleId = 1, getVehicleEvents)
        screen.loadData()

        val template = screen.onGetTemplate() as ListTemplate
        val items = template.singleList!!.items
        assertTrue("deve limitar a 10 eventos", items.size == 10)
        val first = items.first() as Row
        assertEquals("Evento 12", first.title.toString())
        assertNull("Eventos sao somente leitura, sem onClick", first.onClickDelegate)
    }

    @Test
    fun `titulo vazio usa o label da categoria`() = runTest {
        val getVehicleEvents: GetVehicleEventsUseCase = mockk()
        val events = listOf(event(1, title = "", date = "2026-06-15", category = EventCategory.OIL_CHANGE))
        coEvery { getVehicleEvents(1) } returns AppResult.Success(events)

        val screen = AutoEventsScreen(carContext, vehicleId = 1, getVehicleEvents)
        screen.loadData()

        val template = screen.onGetTemplate() as ListTemplate
        val row = template.singleList!!.items.first() as Row
        assertEquals("Troca de Óleo", row.title.toString())
    }

    @Test
    fun `linha mostra data e valor formatados`() = runTest {
        val getVehicleEvents: GetVehicleEventsUseCase = mockk()
        val events = listOf(event(1, title = "Troca de óleo", date = "2026-06-15", amount = 289.90))
        coEvery { getVehicleEvents(1) } returns AppResult.Success(events)

        val screen = AutoEventsScreen(carContext, vehicleId = 1, getVehicleEvents)
        screen.loadData()

        val template = screen.onGetTemplate() as ListTemplate
        val row = template.singleList!!.items.first() as Row
        val text = row.texts.first().toString()
        assertTrue("deve conter a data", text.contains("15/06"))
        assertTrue("deve conter o valor", text.contains("289"))
    }
}
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.auto.AutoEventsScreenTest"`
Expected: FAIL — `Unresolved reference: AutoEventsScreen`.

- [ ] **Step 3: Implementar AutoEventsScreen**

Criar `app/src/main/java/com/flowfuel/app/feature/auto/events/AutoEventsScreen.kt`:

```kotlin
package com.flowfuel.app.feature.auto.events

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsUseCase
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private const val MAX_EVENTS_SHOWN = 10

class AutoEventsScreen(
    carContext: CarContext,
    private val vehicleId: Int,
    private val getVehicleEvents: GetVehicleEventsUseCase,
) : Screen(carContext) {

    private sealed interface State {
        data object Loading : State
        data class Success(val events: List<VehicleEvent>) : State
        data object Empty : State
        data class Error(val error: AppError) : State
    }

    private var state: State = State.Loading

    init {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) loadData()
        })
    }

    internal fun loadData() {
        lifecycleScope.launch {
            state = State.Loading
            invalidate()

            when (val result = getVehicleEvents(vehicleId)) {
                is AppResult.Success -> {
                    val events = result.value.sortedByDescending { it.eventDate }.take(MAX_EVENTS_SHOWN)
                    state = if (events.isEmpty()) State.Empty else State.Success(events)
                    invalidate()
                }
                is AppResult.Failure -> {
                    state = State.Error(result.error)
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template = when (val s = state) {
        State.Loading -> loadingTemplate()
        State.Empty -> messageTemplate("Nenhum evento registrado ainda.")
        is State.Error -> errorTemplate(s.error)
        is State.Success -> successTemplate(s.events)
    }

    private fun loadingTemplate(): Template =
        MessageTemplate.Builder("Carregando…")
            .setTitle("Eventos")
            .setHeaderAction(Action.BACK)
            .setLoading(true)
            .build()

    private fun messageTemplate(message: String): Template =
        MessageTemplate.Builder(message)
            .setTitle("Eventos")
            .setHeaderAction(Action.BACK)
            .build()

    private fun errorTemplate(error: AppError): Template {
        val msg = if (error == AppError.Unauthorized)
            "Sessão expirada. Abra o FlowFuel no celular para entrar novamente."
        else
            "Erro ao carregar eventos. Verifique sua conexão."
        val builder = MessageTemplate.Builder(msg)
            .setTitle("Eventos")
            .setHeaderAction(Action.BACK)
        if (error != AppError.Unauthorized) {
            builder.addAction(
                Action.Builder()
                    .setTitle("Tentar novamente")
                    .setOnClickListener { loadData() }
                    .build()
            )
        }
        return builder.build()
    }

    private fun successTemplate(events: List<VehicleEvent>): Template {
        val brLocale = Locale("pt", "BR")
        val currencyFmt = NumberFormat.getCurrencyInstance(brLocale)
        val listBuilder = ItemList.Builder()
        events.forEach { event ->
            val title = event.title.ifBlank { event.category.label }
            val date = event.eventDate.takeIf { it.length >= 10 }
                ?.let { "${it.substring(8, 10)}/${it.substring(5, 7)}" }
                ?: event.eventDate
            val amount = event.amount?.let { " • ${currencyFmt.format(it)}" } ?: ""
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(title)
                    .addText("$date$amount")
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("Eventos")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.auto.AutoEventsScreenTest"`
Expected: `BUILD SUCCESSFUL`, 7 testes passam.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/auto/events/AutoEventsScreen.kt
git add app/src/test/java/com/flowfuel/app/feature/auto/AutoEventsScreenTest.kt
git commit -m "feat(auto): add AutoEventsScreen (Eventos), read-only list of the 10 most recent"
```

---

### Task 4: AutoDriverInfoScreen (Informações importantes para o motorista)

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/auto/driverinfo/AutoDriverInfoScreen.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/auto/AutoDriverInfoScreenTest.kt`

**Interfaces:**
- Consumes: `GetUpcomingMaintenanceUseCase.invoke(vehicleId: Int, currentKm: Int): AppResult<List<UpcomingMaintenanceItem>>` (`com.flowfuel.app.feature.home.domain.usecase.GetUpcomingMaintenanceUseCase`); `UpcomingMaintenanceItem.toStatusText(): MaintenanceStatusText` (Task 1); `ActiveVehicleData`.
- Produces: `class AutoDriverInfoScreen(carContext: CarContext, vehicle: ActiveVehicleData, getUpcomingMaintenance: GetUpcomingMaintenanceUseCase) : Screen(carContext)` — usado pelo Task 5 (`AutoMenuScreen`).

- [ ] **Step 1: Escrever o teste que falha**

Criar `app/src/test/java/com/flowfuel/app/feature/auto/AutoDriverInfoScreenTest.kt`:

```kotlin
package com.flowfuel.app.feature.auto

import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auto.driverinfo.AutoDriverInfoScreen
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceItem
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceType
import com.flowfuel.app.feature.home.domain.usecase.GetUpcomingMaintenanceUseCase
import io.mockk.coEvery
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
class AutoDriverInfoScreenTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val carContext: TestCarContext
        get() = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val vehicle = ActiveVehicleData(
        id = 1, brand = "VW", model = "Fox", fuelSubType = null,
        capacity = null, licensePlate = "ABC1D23", energyType = "COMBUSTION", currentKm = 67270,
    )

    @Test
    fun `estado inicial retorna MessageTemplate loading`() {
        val screen = AutoDriverInfoScreen(carContext, vehicle, mockk())
        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `erro de rede retorna mensagem com acao de tentar novamente`() = runTest {
        val getUpcomingMaintenance: GetUpcomingMaintenanceUseCase = mockk()
        coEvery { getUpcomingMaintenance(1, 67270) } returns AppResult.Failure(AppError.Network)

        val screen = AutoDriverInfoScreen(carContext, vehicle, getUpcomingMaintenance)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isNotEmpty())
    }

    @Test
    fun `erro 401 retorna mensagem sem acao de retry`() = runTest {
        val getUpcomingMaintenance: GetUpcomingMaintenanceUseCase = mockk()
        coEvery { getUpcomingMaintenance(1, 67270) } returns AppResult.Failure(AppError.Unauthorized)

        val screen = AutoDriverInfoScreen(carContext, vehicle, getUpcomingMaintenance)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isEmpty())
    }

    @Test
    fun `sucesso mostra as 3 linhas fixas com titulo e status`() = runTest {
        val getUpcomingMaintenance: GetUpcomingMaintenanceUseCase = mockk()
        val items = listOf(
            UpcomingMaintenanceItem(type = UpcomingMaintenanceType.OIL_CHANGE, remainingKm = 320),
            UpcomingMaintenanceItem(type = UpcomingMaintenanceType.TIRE_ROTATION, remainingKm = -150, isOverdue = true),
            UpcomingMaintenanceItem(type = UpcomingMaintenanceType.LICENSING, needsSetup = true),
        )
        coEvery { getUpcomingMaintenance(1, 67270) } returns AppResult.Success(items)

        val screen = AutoDriverInfoScreen(carContext, vehicle, getUpcomingMaintenance)
        screen.loadData()

        val template = screen.onGetTemplate() as ListTemplate
        val rows = template.singleList!!.items.map { it as Row }
        assertTrue("deve ter 3 linhas fixas", rows.size == 3)
        assertEquals("Troca de óleo", rows[0].title.toString())
        assertEquals("Em 320 km", rows[0].texts.first().toString())
        assertEquals("Rodízio de pneus", rows[1].title.toString())
        assertEquals("Atrasado 150 km", rows[1].texts.first().toString())
        assertEquals("Licenciamento", rows[2].title.toString())
        assertEquals("Defina a data de licenciamento", rows[2].texts.first().toString())
    }
}
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.auto.AutoDriverInfoScreenTest"`
Expected: FAIL — `Unresolved reference: AutoDriverInfoScreen`.

- [ ] **Step 3: Implementar AutoDriverInfoScreen**

Criar `app/src/main/java/com/flowfuel/app/feature/auto/driverinfo/AutoDriverInfoScreen.kt`:

```kotlin
package com.flowfuel.app.feature.auto.driverinfo

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceItem
import com.flowfuel.app.feature.home.domain.model.toStatusText
import com.flowfuel.app.feature.home.domain.usecase.GetUpcomingMaintenanceUseCase
import kotlinx.coroutines.launch

class AutoDriverInfoScreen(
    carContext: CarContext,
    private val vehicle: ActiveVehicleData,
    private val getUpcomingMaintenance: GetUpcomingMaintenanceUseCase,
) : Screen(carContext) {

    private sealed interface State {
        data object Loading : State
        data class Success(val items: List<UpcomingMaintenanceItem>) : State
        data class Error(val error: AppError) : State
    }

    private var state: State = State.Loading

    init {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) loadData()
        })
    }

    internal fun loadData() {
        lifecycleScope.launch {
            state = State.Loading
            invalidate()

            when (val result = getUpcomingMaintenance(vehicle.id, vehicle.currentKm)) {
                is AppResult.Success -> {
                    state = State.Success(result.value)
                    invalidate()
                }
                is AppResult.Failure -> {
                    state = State.Error(result.error)
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template = when (val s = state) {
        State.Loading -> loadingTemplate()
        is State.Error -> errorTemplate(s.error)
        is State.Success -> successTemplate(s.items)
    }

    private fun loadingTemplate(): Template =
        MessageTemplate.Builder("Carregando…")
            .setTitle("Informações importantes")
            .setHeaderAction(Action.BACK)
            .setLoading(true)
            .build()

    private fun errorTemplate(error: AppError): Template {
        val msg = if (error == AppError.Unauthorized)
            "Sessão expirada. Abra o FlowFuel no celular para entrar novamente."
        else
            "Erro ao carregar informações. Verifique sua conexão."
        val builder = MessageTemplate.Builder(msg)
            .setTitle("Informações importantes")
            .setHeaderAction(Action.BACK)
        if (error != AppError.Unauthorized) {
            builder.addAction(
                Action.Builder()
                    .setTitle("Tentar novamente")
                    .setOnClickListener { loadData() }
                    .build()
            )
        }
        return builder.build()
    }

    private fun successTemplate(items: List<UpcomingMaintenanceItem>): Template {
        val listBuilder = ItemList.Builder()
        items.forEach { item ->
            val statusText = item.toStatusText()
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(statusText.title)
                    .addText(statusText.subtitle)
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("Informações importantes")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.auto.AutoDriverInfoScreenTest"`
Expected: `BUILD SUCCESSFUL`, 4 testes passam.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/auto/driverinfo/AutoDriverInfoScreen.kt
git add app/src/test/java/com/flowfuel/app/feature/auto/AutoDriverInfoScreenTest.kt
git commit -m "feat(auto): add AutoDriverInfoScreen (Informações importantes), reusing GetUpcomingMaintenanceUseCase"
```

---

### Task 5: AutoMenuScreen (menu de navegação, substitui o dashboard)

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/auto/menu/AutoMenuScreen.kt`
- Create: `app/src/main/res/drawable/ic_auto_station.xml`
- Create: `app/src/main/res/drawable/ic_auto_info.xml`
- Test: `app/src/test/java/com/flowfuel/app/feature/auto/AutoMenuScreenTest.kt`

**Interfaces:**
- Consumes: `GetActiveVehicleUseCase`, `CreateRefuelUseCase` (já existentes, usados por `AutoDashboardScreen` hoje); `GetNearbyStationsUseCase`+`LocationProvider` (Task 2); `GetVehicleEventsUseCase` (Task 3); `GetUpcomingMaintenanceUseCase` (Task 4); `AutoRefuelStep1Screen(carContext, vehicle, createRefuel)` (já existente).
- Produces: `class AutoMenuScreen(carContext: CarContext, getActiveVehicle: GetActiveVehicleUseCase, createRefuel: CreateRefuelUseCase, getNearbyStations: GetNearbyStationsUseCase, locationProvider: LocationProvider, getVehicleEvents: GetVehicleEventsUseCase, getUpcomingMaintenance: GetUpcomingMaintenanceUseCase) : Screen(carContext)` — usado pelo Task 6 (`AutoSession`).

- [ ] **Step 1: Criar os 2 ícones novos**

`app/src/main/res/drawable/ic_auto_station.xml` (Material Icons "place", 24dp):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7zM12,11.5c-1.38,0 -2.5,-1.12 -2.5,-2.5s1.12,-2.5 2.5,-2.5 2.5,1.12 2.5,2.5 -1.12,2.5 -2.5,2.5z"/>
</vector>
```

`app/src/main/res/drawable/ic_auto_info.xml` (Material Icons "info", 24dp):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M11,7h2v2h-2zM11,11h2v6h-2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z"/>
</vector>
```

- [ ] **Step 2: Escrever o teste que falha**

Criar `app/src/test/java/com/flowfuel/app/feature/auto/AutoMenuScreenTest.kt`:

```kotlin
package com.flowfuel.app.feature.auto

import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auto.menu.AutoMenuScreen
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoMenuScreenTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val carContext: TestCarContext
        get() = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val testVehicle = ActiveVehicleData(
        id = 1, brand = "VW", model = "Fox", fuelSubType = null,
        capacity = null, licensePlate = "ABC1D23", energyType = "COMBUSTION", currentKm = 67270,
    )

    private fun makeScreen(getActiveVehicle: GetActiveVehicleUseCase) = AutoMenuScreen(
        carContext, getActiveVehicle, mockk(), mockk(), mockk(), mockk(), mockk(),
    )

    @Test
    fun `estado inicial retorna MessageTemplate loading`() {
        val screen = makeScreen(mockk())
        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `apos loadData com sucesso retorna ListTemplate com os 4 itens do menu`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Success(testVehicle)

        val screen = makeScreen(getActiveVehicle)
        screen.loadData()

        val template = screen.onGetTemplate() as ListTemplate
        val items = template.singleList!!.items
        assertTrue("deve ter os 4 itens do menu", items.size == 4)
        val titles = items.map { (it as Row).title.toString() }
        assertEquals(
            listOf("Registrar abastecimento", "Postos próximos", "Eventos", "Informações importantes"),
            titles,
        )
        items.forEach { assertNotNull((it as Row).onClickDelegate) }
    }

    @Test
    fun `erro de rede retorna MessageTemplate com acao de tentar novamente`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Failure(AppError.Network)

        val screen = makeScreen(getActiveVehicle)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isNotEmpty())
    }

    @Test
    fun `erro 401 retorna MessageTemplate sem acao de retry`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Failure(AppError.Unauthorized)

        val screen = makeScreen(getActiveVehicle)
        screen.loadData()

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(template.actions.isEmpty())
    }
}
```

- [ ] **Step 3: Rodar o teste e confirmar que falha**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.auto.AutoMenuScreenTest"`
Expected: FAIL — `Unresolved reference: AutoMenuScreen`.

- [ ] **Step 4: Implementar AutoMenuScreen**

Criar `app/src/main/java/com/flowfuel/app/feature/auto/menu/AutoMenuScreen.kt`:

```kotlin
package com.flowfuel.app.feature.auto.menu

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.flowfuel.app.R
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auto.driverinfo.AutoDriverInfoScreen
import com.flowfuel.app.feature.auto.events.AutoEventsScreen
import com.flowfuel.app.feature.auto.refuel.AutoRefuelStep1Screen
import com.flowfuel.app.feature.auto.stations.AutoStationsScreen
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetUpcomingMaintenanceUseCase
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsUseCase
import kotlinx.coroutines.launch

class AutoMenuScreen(
    carContext: CarContext,
    private val getActiveVehicle: GetActiveVehicleUseCase,
    private val createRefuel: CreateRefuelUseCase,
    private val getNearbyStations: GetNearbyStationsUseCase,
    private val locationProvider: LocationProvider,
    private val getVehicleEvents: GetVehicleEventsUseCase,
    private val getUpcomingMaintenance: GetUpcomingMaintenanceUseCase,
) : Screen(carContext) {

    private sealed interface State {
        data object Loading : State
        data class Success(val vehicle: ActiveVehicleData) : State
        data class Error(val error: AppError) : State
    }

    private var state: State = State.Loading

    init {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) loadData()
        })
    }

    internal fun loadData() {
        lifecycleScope.launch {
            state = State.Loading
            invalidate()

            when (val result = getActiveVehicle()) {
                is AppResult.Success -> {
                    state = State.Success(result.value)
                    invalidate()
                }
                is AppResult.Failure -> {
                    state = State.Error(result.error)
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template = when (val s = state) {
        State.Loading -> loadingTemplate()
        is State.Error -> errorTemplate(s.error)
        is State.Success -> successTemplate(s.vehicle)
    }

    private fun loadingTemplate(): Template =
        MessageTemplate.Builder("Carregando…")
            .setTitle("FlowFuel")
            .setHeaderAction(Action.APP_ICON)
            .setLoading(true)
            .build()

    private fun errorTemplate(error: AppError): Template {
        val msg = if (error == AppError.Unauthorized)
            "Sessão expirada. Abra o FlowFuel no celular para entrar novamente."
        else
            "Erro ao carregar dados. Verifique sua conexão."
        val builder = MessageTemplate.Builder(msg)
            .setTitle("FlowFuel")
            .setHeaderAction(Action.APP_ICON)
        if (error != AppError.Unauthorized) {
            builder.addAction(
                Action.Builder()
                    .setTitle("Tentar novamente")
                    .setOnClickListener { loadData() }
                    .build()
            )
        }
        return builder.build()
    }

    private fun successTemplate(vehicle: ActiveVehicleData): Template {
        val title = "${vehicle.brand} ${vehicle.model}${vehicle.licensePlate?.let { " ($it)" } ?: ""}"
        return ListTemplate.Builder()
            .setSingleList(
                ItemList.Builder()
                    .addItem(
                        Row.Builder()
                            .setTitle("Registrar abastecimento")
                            .setImage(icon(R.drawable.ic_auto_add))
                            .setOnClickListener {
                                screenManager.push(AutoRefuelStep1Screen(carContext, vehicle, createRefuel))
                            }
                            .build()
                    )
                    .addItem(
                        Row.Builder()
                            .setTitle("Postos próximos")
                            .setImage(icon(R.drawable.ic_auto_station))
                            .setOnClickListener {
                                screenManager.push(
                                    AutoStationsScreen(carContext, vehicle, getNearbyStations, locationProvider)
                                )
                            }
                            .build()
                    )
                    .addItem(
                        Row.Builder()
                            .setTitle("Eventos")
                            .setImage(icon(R.drawable.ic_auto_history))
                            .setOnClickListener {
                                screenManager.push(AutoEventsScreen(carContext, vehicle.id, getVehicleEvents))
                            }
                            .build()
                    )
                    .addItem(
                        Row.Builder()
                            .setTitle("Informações importantes")
                            .setImage(icon(R.drawable.ic_auto_info))
                            .setOnClickListener {
                                screenManager.push(
                                    AutoDriverInfoScreen(carContext, vehicle, getUpcomingMaintenance)
                                )
                            }
                            .build()
                    )
                    .build()
            )
            .setTitle(title)
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun icon(resId: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()
}
```

- [ ] **Step 5: Rodar o teste e confirmar que passa**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.auto.AutoMenuScreenTest"`
Expected: `BUILD SUCCESSFUL`, 4 testes passam.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/auto/menu/AutoMenuScreen.kt
git add app/src/main/res/drawable/ic_auto_station.xml
git add app/src/main/res/drawable/ic_auto_info.xml
git add app/src/test/java/com/flowfuel/app/feature/auto/AutoMenuScreenTest.kt
git commit -m "feat(auto): add AutoMenuScreen, navigating to Abastecimento/Postos/Eventos/Info do motorista"
```

---

### Task 6: Trocar a raiz do Android Auto pra AutoMenuScreen e remover o dashboard antigo

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/auto/AutoCarAppService.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/auto/AutoSession.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/auto/AutoSessionTest.kt`
- Delete: `app/src/main/java/com/flowfuel/app/feature/auto/dashboard/AutoDashboardScreen.kt`
- Delete: `app/src/test/java/com/flowfuel/app/feature/auto/AutoDashboardScreenTest.kt`

**Interfaces:**
- Consumes: `AutoMenuScreen` (Task 5) e todas as suas dependências.
- Produces: `AutoSession.createInitialScreen(carContext, token)` retorna `AutoMenuScreen` em vez de `AutoDashboardScreen` pra token válido — comportamento final da navegação.

- [ ] **Step 1: Atualizar o teste que hoje espera AutoDashboardScreen (deve falhar)**

Substituir todo o conteúdo de `app/src/test/java/com/flowfuel/app/feature/auto/AutoSessionTest.kt`:

```kotlin
package com.flowfuel.app.feature.auto

import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.feature.auto.menu.AutoMenuScreen
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoSessionTest {

    private val carContext: TestCarContext
        get() = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())

    private fun makeSession() = AutoSession(
        sessionStore = mockk(relaxed = true),
        getActiveVehicle = mockk(),
        createRefuel = mockk(),
        getNearbyStations = mockk(),
        locationProvider = mockk(),
        getVehicleEvents = mockk(),
        getUpcomingMaintenance = mockk(),
    )

    @Test
    fun `null token returns AutoLoginScreen`() {
        val screen = makeSession().createInitialScreen(carContext, token = null)
        assertTrue(screen is AutoLoginScreen)
    }

    @Test
    fun `blank token returns AutoLoginScreen`() {
        val screen = makeSession().createInitialScreen(carContext, token = "   ")
        assertTrue(screen is AutoLoginScreen)
    }

    @Test
    fun `valid token returns AutoMenuScreen`() {
        val screen = makeSession().createInitialScreen(carContext, token = "some-jwt")
        assertTrue(screen is AutoMenuScreen)
    }
}
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.auto.AutoSessionTest"`
Expected: FAIL — erro de compilação, `AutoSession` ainda não tem esse construtor nem `createInitialScreen` retorna `AutoMenuScreen`.

- [ ] **Step 3: Atualizar AutoCarAppService.kt (entry point)**

Substituir todo o conteúdo de `app/src/main/java/com/flowfuel/app/feature/auto/AutoCarAppService.kt`:

```kotlin
package com.flowfuel.app.feature.auto

import androidx.car.app.CarAppService
import androidx.car.app.validation.HostValidator
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetUpcomingMaintenanceUseCase
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoCarAppServiceEntryPoint {
    fun sessionStore(): SessionStore
    fun getActiveVehicle(): GetActiveVehicleUseCase
    fun createRefuel(): CreateRefuelUseCase
    fun getNearbyStations(): GetNearbyStationsUseCase
    fun locationProvider(): LocationProvider
    fun getVehicleEvents(): GetVehicleEventsUseCase
    fun getUpcomingMaintenance(): GetUpcomingMaintenanceUseCase
}

class AutoCarAppService : CarAppService() {

    // FIXME: Replace with signed-host validator before Google Play submission
    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): androidx.car.app.Session {
        val ep = EntryPointAccessors.fromApplication(
            applicationContext,
            AutoCarAppServiceEntryPoint::class.java,
        )
        return AutoSession(
            ep.sessionStore(),
            ep.getActiveVehicle(),
            ep.createRefuel(),
            ep.getNearbyStations(),
            ep.locationProvider(),
            ep.getVehicleEvents(),
            ep.getUpcomingMaintenance(),
        )
    }
}
```

- [ ] **Step 4: Atualizar AutoSession.kt**

Substituir todo o conteúdo de `app/src/main/java/com/flowfuel/app/feature/auto/AutoSession.kt`:

```kotlin
package com.flowfuel.app.feature.auto

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.feature.auto.menu.AutoMenuScreen
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetUpcomingMaintenanceUseCase
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsUseCase
import kotlinx.coroutines.runBlocking

class AutoSession(
    private val sessionStore: SessionStore,
    private val getActiveVehicle: GetActiveVehicleUseCase,
    private val createRefuel: CreateRefuelUseCase,
    private val getNearbyStations: GetNearbyStationsUseCase,
    private val locationProvider: LocationProvider,
    private val getVehicleEvents: GetVehicleEventsUseCase,
    private val getUpcomingMaintenance: GetUpcomingMaintenanceUseCase,
) : androidx.car.app.Session() {

    override fun onCreateScreen(intent: Intent): Screen = runBlocking {
        createInitialScreen(carContext, sessionStore.accessToken())
    }

    internal fun createInitialScreen(carContext: CarContext, token: String?): Screen =
        if (token.isNullOrBlank()) AutoLoginScreen(carContext)
        else AutoMenuScreen(
            carContext,
            getActiveVehicle,
            createRefuel,
            getNearbyStations,
            locationProvider,
            getVehicleEvents,
            getUpcomingMaintenance,
        )
}
```

- [ ] **Step 5: Apagar o dashboard antigo e seu teste**

```bash
git rm app/src/main/java/com/flowfuel/app/feature/auto/dashboard/AutoDashboardScreen.kt
git rm app/src/test/java/com/flowfuel/app/feature/auto/AutoDashboardScreenTest.kt
```

- [ ] **Step 6: Rodar o teste e confirmar que passa**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.auto.AutoSessionTest"`
Expected: `BUILD SUCCESSFUL`, 3 testes passam.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/auto/AutoCarAppService.kt
git add app/src/main/java/com/flowfuel/app/feature/auto/AutoSession.kt
git add app/src/test/java/com/flowfuel/app/feature/auto/AutoSessionTest.kt
git commit -m "feat(auto): make AutoMenuScreen the post-login root, remove AutoDashboardScreen"
```

---

### Task 7: Suíte completa + verificação manual via DHU

**Files:** nenhum arquivo novo — só verificação.

- [ ] **Step 1: Rodar toda a suíte de testes do módulo Auto**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.auto.*"`
Expected: `BUILD SUCCESSFUL` — todos os testes de `AutoSessionTest`, `AutoMenuScreenTest`, `AutoStationsScreenTest`, `AutoEventsScreenTest`, `AutoDriverInfoScreenTest`, `AutoRefuelStepScreensTest`, `AutoRefuelConfirmScreenTest` passam; nenhuma referência residual a `AutoDashboardScreen`.

- [ ] **Step 2: Rodar a suíte completa do projeto pra garantir que nada mais quebrou**

Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Verificação visual manual via DHU**

Usar o playbook `.claude/android-auto-debug-playbook.md` (DHU + celular conectado) pra instalar o app e confirmar visualmente no FlowFuel do Android Auto:
- A tela inicial pós-login é o menu com 4 itens: Registrar abastecimento, Postos próximos, Eventos, Informações importantes — não aparece mais o grid antigo.
- "Registrar abastecimento" abre o mesmo fluxo de 3 passos de sempre e, ao concluir, volta pro menu (não pro grid antigo).
- "Postos próximos" mostra a lista de postos com distância e, ao tocar num posto, abre o app de navegação do carro com destino nele.
- "Eventos" mostra os eventos mais recentes do veículo, sem nenhuma ação de toque.
- "Informações importantes" mostra as 3 linhas de troca de óleo/pneus/licenciamento com o status correto pro veículo ativo.
- Voltar (seta/gesto de voltar) de qualquer uma das 4 telas volta pro menu.

- [ ] **Step 4: Commit final (se a verificação manual revelar algum ajuste)**

Se a verificação do Step 3 não pedir nenhum ajuste, nenhum commit é necessário aqui — a Task 6 já é o commit final da feature. Se algo precisar de correção, corrigir, rodar `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.auto.*"` de novo, e commitar:

```bash
git add -A
git commit -m "fix(auto): address manual DHU verification findings for the new menu"
```

---

## Self-Review

### Spec coverage

| Requisito do spec | Task |
|---|---|
| `AutoMenuScreen` substitui `AutoDashboardScreen` como raiz, 4 itens fixos | Task 5, Task 6 |
| Grid de 4 stats sai de circulação por completo | Task 6 (delete `AutoDashboardScreen.kt`) |
| `AutoStationsScreen`: raio fixo 3km, filtro automático por `energyType`, máx. 6, ordenado por distância | Task 2 |
| Toque no posto dispara `ACTION_NAVIGATE` com `geo:` | Task 2 |
| `AutoEventsScreen`: somente leitura, 10 mais recentes, ordenado por data desc | Task 3 |
| `AutoDriverInfoScreen`: reaproveita `GetUpcomingMaintenanceUseCase`, 3 linhas fixas | Task 4 |
| Extração de `toStatusText()` pra fora do Compose | Task 1 |
| `formatDistance()` reaproveitada via import (não duplicada) | Task 2 |
| Ícones novos `ic_auto_station`/`ic_auto_info`, reaproveita `ic_auto_add`/`ic_auto_history` | Task 5 |
| Entry point/DI: remove `getDashboard()`, adiciona os 5 novos getters | Task 6 |
| Padrão de erro/loading (Unauthorized sem retry, outros com retry) em todas as 4 telas novas | Tasks 2, 3, 4, 5 |
| Testes Robolectric por tela nova + `AutoSessionTest` atualizado + `AutoDashboardScreenTest` removido | Tasks 2, 3, 4, 5, 6 |
| Verificação manual via DHU | Task 7 |

### Placeholder scan

Nenhum "TBD"/"implementar depois" — todo código de teste e produção está completo em cada step, incluindo as 4 classes de tela inteiras e os arquivos de teste inteiros.

### Type consistency

`AutoStationsScreen`, `AutoEventsScreen`, `AutoDriverInfoScreen` e `AutoMenuScreen` usam consistentemente `ItemList.Builder().addItem(Row.Builder()...build()).build()` e `ListTemplate.Builder().setSingleList(...).setTitle(...).setHeaderAction(Action.BACK).build()` (as 3 telas de destino) ou `Action.APP_ICON` (o menu, que é raiz). `template.singleList!!.items` e `(item as Row).title`/`.texts`/`.onClickDelegate` são usados de forma idêntica em todos os testes. `AutoSession`/`AutoCarAppService` (Task 6) usam exatamente os nomes de parâmetro definidos nos construtores das Tasks 2–5 (`getNearbyStations`, `locationProvider`, `getVehicleEvents`, `getUpcomingMaintenance`) sem divergência.
