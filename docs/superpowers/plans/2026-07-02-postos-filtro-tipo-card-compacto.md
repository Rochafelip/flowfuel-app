# Postos — Filtro de Tipo + Card Compacto Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adicionar um toggle exclusivo Combustível/Elétrico à tela "Postos" (filtragem client-side, sem novo request), sincronizar o tipo inicial com o veículo ativo do usuário, e deixar o `StationCard` mais compacto exibindo o `rating` (hoje capturado mas nunca mostrado).

**Architecture:** Segue o padrão MVVM já estabelecido em `feature/station`: `StationsViewModel` ganha um segundo `StateFlow` independente (`selectedType`, igual ao já existente `radiusMeters`), mas que **não** dispara `load()` — é filtro puro sobre a lista já carregada, aplicado em `StationsScreen`. A sincronização com o veículo ativo reaproveita o padrão já usado em `VehicleEventsViewModel` (`sessionStore.activeVehicleIdFlow` + `GetVehicleByIdUseCase`).

**Tech Stack:** Kotlin, Jetpack Compose (Material3 `SingleChoiceSegmentedButtonRow`/`SegmentedButton`), Hilt DI (injeção por construtor, sem novo módulo), StateFlow, JUnit + MockK + Robolectric (`RobolectricTestRunner`, `@Config(sdk = [33])`) para testes de ViewModel; testes puros (sem Robolectric) para funções de formatação, seguindo o padrão já usado em `formatDistance`/`formatRadiusLabel`.

## Global Constraints

- **Sem mudança de contrato de backend** — `StationResponseDto`/`StationApi.getNearbyStations` permanecem exatamente como estão.
- **Sem novo campo em `Station`** — `placeId/name/type/distanceMeters/rating/latitude/longitude` inalterados.
- **Sem mudança em `FFCard`** (componente compartilhado do design system).
- **Strings de UI em português, hardcoded inline** nos composables — segue a convenção já usada em `StationCard.kt`/`StationsScreen.kt` (não há `strings.xml` para essas telas).
- **Sem teste de renderização Compose** para os novos componentes de filtro/card — a convenção já estabelecida no módulo (`StationCardTest.kt`, `StationDistanceFilterRowTest.kt`) testa só funções puras extraídas (`formatDistance`, `formatRadiusLabel`, `badgeContent()`), não usa `createComposeRule`/Robolectric para renderizar. Este plano segue a mesma convenção.
- Após a última task, rodar o app no emulador (skill `run-android-emulator` já configurada neste projeto) para verificação visual — é UI, não basta passar nos testes.

---

### Task 1: `StationsViewModel` — estado `selectedType` (sem sincronização ainda)

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModel.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModelTest.kt`

**Interfaces:**
- Produces: `StationsViewModel.selectedType: StateFlow<StationType>` (inicia em `StationType.Fuel`), `StationsViewModel.onTypeSelected(type: StationType): Unit`.

- [ ] **Step 1: Escrever os testes que falham**

Adicionar ao final da classe `StationsViewModelTest` (antes do `}` de fechamento, depois do teste `onRadiusSelected updates radiusMeters...`):

```kotlin
    @Test
    fun `selectedType starts at Fuel by default`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))

        val vm = buildViewModel()

        assertEquals(StationType.Fuel, vm.selectedType.value)
    }

    @Test
    fun `onTypeSelected updates selectedType without triggering a new load`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))
        val vm = buildViewModel()

        vm.onTypeSelected(StationType.Electric)

        assertEquals(StationType.Electric, vm.selectedType.value)
        coVerify(exactly = 1) { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) }
    }
```

- [ ] **Step 2: Rodar os testes e confirmar que falham**

Run: `./gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationsViewModelTest" --console=plain`
Expected: FAIL — `selectedType`/`onTypeSelected` não existem em `StationsViewModel` (erro de compilação).

- [ ] **Step 3: Implementar `selectedType`/`onTypeSelected`**

Em `StationsViewModel.kt`, logo depois do bloco existente de `radiusMeters` (depois da linha `val radiusMeters: StateFlow<Int> = _radiusMeters.asStateFlow()`):

```kotlin
    private val _selectedType = MutableStateFlow(StationType.Fuel)
    val selectedType: StateFlow<StationType> = _selectedType.asStateFlow()
```

E depois de `fun onRadiusSelected(radiusMeters: Int) { ... }`:

```kotlin
    fun onTypeSelected(type: StationType) {
        _selectedType.value = type
    }
```

Adicionar o import: `import com.flowfuel.app.feature.station.domain.model.StationType`.

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `./gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationsViewModelTest" --console=plain`
Expected: PASS (todos os testes, incluindo os já existentes)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModel.kt app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModelTest.kt
git commit -m "feat(postos): add selectedType state to StationsViewModel"
```

---

### Task 2: `StationsViewModel` — sincronizar tipo inicial com o veículo ativo

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModel.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModelTest.kt`

**Interfaces:**
- Consumes: `SessionStore.activeVehicleIdFlow: Flow<Int?>` (já existe); `GetVehicleByIdUseCase.invoke(id: Int): AppResult<Vehicle>` (já existe, `app/src/main/java/com/flowfuel/app/feature/vehicle/domain/usecase/GetVehicleByIdUseCase.kt`); `Vehicle.energyType: EnergyType` (já existe, `Combustion`/`Electric`/`Hybrid`).
- Produces: `StationsViewModel` construtor ganha um 4º parâmetro `getVehicleById: GetVehicleByIdUseCase`.

- [ ] **Step 1: Escrever os testes que falham**

No topo de `StationsViewModelTest.kt`, adicionar aos imports:

```kotlin
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehicleByIdUseCase
import io.mockk.every
import kotlinx.coroutines.flow.flowOf
```

Adicionar o novo mock como propriedade da classe (junto dos outros `mockk()`):

```kotlin
    private val getVehicleById: GetVehicleByIdUseCase = mockk()
```

Atualizar `buildViewModel()` para passar o novo parâmetro:

```kotlin
    private fun buildViewModel() = StationsViewModel(getNearbyStations, locationProvider, sessionStore, getVehicleById)
```

Adicionar um stub padrão de "sem veículo ativo" em `setUp()`, para que os testes já existentes continuem passando sem modificação (a sincronização cai no padrão `Fuel` quando não há veículo ativo):

```kotlin
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { sessionStore.activeVehicleIdFlow } returns flowOf(null)
    }
```

Adicionar um helper de fixture, logo abaixo de `station(id, distance)`:

```kotlin
    private fun vehicle(energyType: EnergyType) = Vehicle(
        id = 7, brand = "Toyota", model = "Corolla", manufactureYear = 2020, modelYear = 2020,
        licensePlate = "ABC1234", color = "Prata", type = VehicleType.Car, energyType = energyType,
        fuelType = null, odometerKm = 1000, tankCapacityL = null, batteryCapacityKwh = null, isActive = true,
    )
```

Adicionar os novos testes ao final da classe:

```kotlin
    @Test
    fun `selectedType defaults to Fuel when there is no active vehicle`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))

        val vm = buildViewModel()

        assertEquals(StationType.Fuel, vm.selectedType.value)
        coVerify(inverse = true) { getVehicleById(any()) }
    }

    @Test
    fun `selectedType syncs to Electric when the active vehicle is Electric`() = runTest {
        every { sessionStore.activeVehicleIdFlow } returns flowOf(7)
        coEvery { getVehicleById(7) } returns AppResult.Success(vehicle(EnergyType.Electric))
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))

        val vm = buildViewModel()

        assertEquals(StationType.Electric, vm.selectedType.value)
    }

    @Test
    fun `selectedType syncs to Fuel when the active vehicle is Combustion or Hybrid`() = runTest {
        every { sessionStore.activeVehicleIdFlow } returns flowOf(7)
        coEvery { getVehicleById(7) } returns AppResult.Success(vehicle(EnergyType.Hybrid))
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))

        val vm = buildViewModel()

        assertEquals(StationType.Fuel, vm.selectedType.value)
    }

    @Test
    fun `selectedType falls back to Fuel and load still runs when getVehicleById fails`() = runTest {
        every { sessionStore.activeVehicleIdFlow } returns flowOf(7)
        coEvery { getVehicleById(7) } returns AppResult.Failure(AppError.Network)
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, DEFAULT_STATION_RADIUS_METERS) } returns AppResult.Success(listOf(station("a", 100)))

        val vm = buildViewModel()

        assertEquals(StationType.Fuel, vm.selectedType.value)
        assertTrue(vm.state.value is StationsUiState.Success)
    }
```

- [ ] **Step 2: Rodar os testes e confirmar que falham**

Run: `./gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationsViewModelTest" --console=plain`
Expected: FAIL — erro de compilação (`StationsViewModel` ainda não aceita 4 parâmetros).

- [ ] **Step 3: Implementar a sincronização no `StationsViewModel`**

Atualizar a assinatura do construtor:

```kotlin
class StationsViewModel @Inject constructor(
    private val getNearbyStations: GetNearbyStationsUseCase,
    private val locationProvider: LocationProvider,
    private val sessionStore: SessionStore,
    private val getVehicleById: GetVehicleByIdUseCase,
) : ViewModel() {
```

Substituir o `init` atual:

```kotlin
    init {
        load()
    }
```

por:

```kotlin
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
            load()
        }
    }
```

Adicionar os imports:

```kotlin
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehicleByIdUseCase
import kotlinx.coroutines.flow.firstOrNull
```

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `./gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationsViewModelTest" --console=plain`
Expected: PASS (todos os 13 testes da classe)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModel.kt app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationsViewModelTest.kt
git commit -m "feat(postos): sync default station type filter with the active vehicle's energy type"
```

---

### Task 3: `StationTypeFilterRow` — novo componente de toggle exclusivo

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationTypeFilterRow.kt`

**Interfaces:**
- Consumes: `StationType` (`app/src/main/java/com/flowfuel/app/feature/station/domain/model/Station.kt`), `StationType.badgeContent(): StationTypeBadgeContent` (já existe em `StationCard.kt`, `internal`, mesmo pacote `presentation.list` — visível sem import extra).
- Produces: `@Composable fun StationTypeFilterRow(selectedType: StationType, onSelect: (StationType) -> Unit, modifier: Modifier = Modifier)`.

Sem passo de teste automatizado nesta task — segue a convenção já estabelecida no módulo (`StationDistanceFilterRow` também não tem teste de renderização; a lógica reaproveitada, `badgeContent()`, já é coberta em `StationCardTest.kt`). A verificação visual acontece na Task 6.

- [ ] **Step 1: Criar o arquivo**

```kotlin
package com.flowfuel.app.feature.station.presentation.list

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowfuel.app.feature.station.domain.model.StationType

private val STATION_TYPES = listOf(StationType.Fuel, StationType.Electric)

/**
 * Toggle exclusivo — sempre exatamente um tipo selecionado, sem estado "todos".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationTypeFilterRow(
    selectedType: StationType,
    onSelect: (StationType) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.padding(horizontal = 16.dp),
    ) {
        STATION_TYPES.forEachIndexed { index, type ->
            val content = type.badgeContent()
            SegmentedButton(
                selected = type == selectedType,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = STATION_TYPES.size),
                icon = {
                    Icon(imageVector = content.icon, contentDescription = null)
                },
                label = { Text(content.label) },
            )
        }
    }
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew.bat compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationTypeFilterRow.kt
git commit -m "feat(postos): add StationTypeFilterRow exclusive toggle component"
```

---

### Task 4: Integrar `StationTypeFilterRow` + filtro client-side em `StationsScreen`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsScreen.kt`

**Interfaces:**
- Consumes: `StationsViewModel.selectedType: StateFlow<StationType>` e `onTypeSelected: (StationType) -> Unit` (Task 1/2); `StationTypeFilterRow` (Task 3).

Sem passo de teste automatizado — não existe suíte de teste de renderização para `StationsScreen` neste módulo (confirmado: nenhum arquivo `StationsScreenTest.kt`). A verificação acontece manualmente no emulador na Task 6.

- [ ] **Step 1: Coletar `selectedType` e adicionar o import de `StationType`**

Em `StationsScreen.kt`, logo abaixo de `val radiusMeters by viewModel.radiusMeters.collectAsState()`:

```kotlin
    val selectedType by viewModel.selectedType.collectAsState()
```

Adicionar o import:

```kotlin
import com.flowfuel.app.feature.station.domain.model.StationType
```

- [ ] **Step 2: Renderizar `StationTypeFilterRow` acima de `StationDistanceFilterRow`**

Substituir o bloco:

```kotlin
            if (state != StationsUiState.PermissionRequired) {
                StationDistanceFilterRow(
                    selectedRadiusMeters = radiusMeters,
                    onSelect = viewModel::onRadiusSelected,
                    modifier = Modifier.padding(vertical = FFTheme.spacing.sm),
                )
            }
```

por:

```kotlin
            if (state != StationsUiState.PermissionRequired) {
                StationTypeFilterRow(
                    selectedType = selectedType,
                    onSelect = viewModel::onTypeSelected,
                    modifier = Modifier.padding(top = FFTheme.spacing.sm),
                )
                StationDistanceFilterRow(
                    selectedRadiusMeters = radiusMeters,
                    onSelect = viewModel::onRadiusSelected,
                    modifier = Modifier.padding(vertical = FFTheme.spacing.sm),
                )
            }
```

- [ ] **Step 3: Filtrar a lista por `selectedType` e tratar o vazio específico por tipo**

Substituir o branch `is StationsUiState.Success -> PullToRefreshBox(...)`:

```kotlin
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
```

por:

```kotlin
                    is StationsUiState.Success -> {
                        val filteredStations = remember(s.stations, selectedType) {
                            s.stations.filter { it.type == selectedType }
                        }
                        if (filteredStations.isEmpty()) {
                            FFEmptyState(
                                title = when (selectedType) {
                                    StationType.Fuel -> "Nenhum posto de combustível encontrado por perto"
                                    StationType.Electric -> "Nenhum posto elétrico encontrado por perto"
                                },
                                description = "Tente aumentar o raio de busca ou trocar o filtro de tipo.",
                                actionText = "Tentar novamente",
                                onAction = viewModel::load,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        } else {
                            PullToRefreshBox(
                                isRefreshing = false,
                                onRefresh = viewModel::load,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(FFTheme.spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                                ) {
                                    items(filteredStations, key = { it.placeId }) { station ->
                                        StationCard(station = station, onRouteClick = { viewModel.onRouteClick(station) })
                                    }
                                }
                            }
                        }
                    }
```

Note: o `when (val s = state)` continua igual — `s` permanece disponível dentro deste novo branch `{ }`, já que ele substitui o corpo do case `is StationsUiState.Success ->`, só trocando de expressão única por bloco `{ }`.

- [ ] **Step 4: Compilar**

Run: `./gradlew.bat compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsScreen.kt
git commit -m "feat(postos): wire StationTypeFilterRow and filter the station list by selected type"
```

---

### Task 5: Redesign compacto do `StationCard`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationCard.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationCardTest.kt`

**Interfaces:**
- Produces: `internal fun formatRating(rating: Double): String` (novo). `StationTypeBadgeContent`/`badgeContent()` permanecem inalterados (já usados por `StationTypeFilterRow`, Task 3). `StationTypeBadge` (composable de pill) é **removido**.

- [ ] **Step 1: Escrever o teste que falha**

Adicionar ao final de `StationCardTest.kt`:

```kotlin
    @Test
    fun `formats rating with one decimal using pt-BR comma separator`() {
        assertEquals("4,8", formatRating(4.8))
        assertEquals("5,0", formatRating(5.0))
        assertEquals("3,7", formatRating(3.7))
    }
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `./gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationCardTest" --console=plain`
Expected: FAIL — `formatRating` não existe (erro de compilação).

- [ ] **Step 3: Reescrever `StationCard.kt` com o layout compacto**

Substituir o conteúdo inteiro do arquivo por:

```kotlin
package com.flowfuel.app.feature.station.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val content = station.type.badgeContent()
    val iconTint = when (station.type) {
        StationType.Fuel -> FFTheme.semanticColors.warning
        StationType.Electric -> FFTheme.semanticColors.info
    }
    FFCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = content.icon,
                contentDescription = content.contentDescription,
                tint = iconTint,
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
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = FFTheme.semanticColors.warning,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(formatRating(station.rating), style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Spacer(Modifier)
            }
            IconButton(onClick = onRouteClick) {
                Icon(imageVector = Icons.Filled.Navigation, contentDescription = "Traçar rota")
            }
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

internal fun formatRating(rating: Double): String =
    String.format(Locale("pt", "BR"), "%.1f", rating)
```

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `./gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationCardTest" --console=plain`
Expected: PASS (todos os testes, incluindo os já existentes de `formatDistance`/`badgeContent()`, que não mudaram de assinatura)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationCard.kt app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationCardTest.kt
git commit -m "feat(postos): redesign StationCard into a compact 2-row layout with rating and route icon button"
```

---

### Task 6: Verificação completa — suíte de testes + emulador

**Files:** nenhum (task de verificação, sem alteração de código).

- [ ] **Step 1: Rodar a suíte completa do módulo `station`**

Run: `./gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.station.*" --console=plain`
Expected: BUILD SUCCESSFUL, todos os testes passando (`StationsViewModelTest`, `StationCardTest`, `StationDistanceFilterRowTest`, `StationRepositoryImplTest`, `FusedLocationProviderTest`).

- [ ] **Step 2: Rodar o app no emulador**

Usar a skill `run-android-emulator` deste projeto (emulador `Pixel_6`, `-gpu swiftshader_indirect`) se não estiver rodando; se já estiver rodando (verificar com `adb devices`), pular direto para o build:

```bash
cd "C:\Users\rocha\AndroidStudioProjects\flowfuel-app"
./gradlew.bat installDebug -x lint --console=plain
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" shell monkey -p com.flowfuel.app.debug -c android.intent.category.LAUNCHER 1
```

- [ ] **Step 3: Navegar até a aba "Postos" e verificar visualmente**

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" shell input tap 540 2228
```

(coordenada da aba "Postos" na bottom nav, confirmada em sessão anterior neste dispositivo — ajustar se o layout de tela mudou)

Capturar screenshot e ler o resultado:

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" exec-out screencap -p > "<scratchpad>/postos_verificacao.png"
```

Confirmar visualmente:
- O toggle Combustível/Elétrico aparece acima do filtro de raio (1/3/5/10 km).
- O toggle inicia selecionado de acordo com o tipo de energia do veículo ativo (ex.: veículo Flex → "Combustível" selecionado).
- Trocar o toggle filtra a lista sem mostrar skeleton de loading (é filtro client-side, não deve haver novo request).
- Os cards estão mais compactos que antes: ícone + nome + distância na primeira linha, rating (se houver) + botão de rota (ícone) na segunda.
- Se o backend ainda não estiver implementando `GET /stations/nearby` de verdade (ver `project_stations_feature` — pendência conhecida), a tela pode ficar no skeleton de loading indefinidamente; nesse caso, validar pelo menos que o toggle de tipo e o filtro de raio renderizam corretamente acima da lista vazia/skeleton, e registrar a limitação no relatório final em vez de bloquear a task.

- [ ] **Step 4: Reportar o resultado**

Sem commit nesta task — é só verificação. Se algo visual estiver incorreto, voltar à task correspondente (3, 4 ou 5) e corrigir antes de finalizar.

---

## Self-Review

**Cobertura da spec** (`docs/superpowers/specs/2026-07-02-postos-filtro-tipo-card-compacto-design.md`):
- Toggle exclusivo Combustível/Elétrico → Task 3 (componente) + Task 4 (integração).
- Filtro client-side sem novo request → Task 1 (`onTypeSelected` não chama `load()`, testado explicitamente) + Task 4 (filtragem em `StationsScreen`).
- Sincronização com veículo ativo (`Electric`→Electric, `Combustion`/`Hybrid`→Fuel, falha silenciosa) → Task 2, com 4 testes cobrindo cada caso do spec.
- Estado vazio específico por tipo, distinto do `StationsUiState.Empty` genérico → Task 4, Step 3.
- Redesign compacto do card (ícone inline, nome, distância, rating, ícone de rota) → Task 5.
- Remoção da badge em pill → Task 5 (arquivo reescrito sem `StationTypeBadge`/`Surface`).
- Fora de escopo (endereço, seletor de app, `FFCard` compartilhado) → nenhuma task toca esses pontos.

**Placeholders:** nenhum "TBD"/"implementar depois" — o único ponto que o spec deixou em aberto (ícone exato do botão de rota) foi resolvido na Task 5 (`Icons.Filled.Navigation`, confirmado existente na dependência `material-icons-extended` já usada pelo projeto).

**Consistência de tipos:** `StationType.Fuel`/`StationType.Electric` usados de forma idêntica em todas as tasks; `selectedType: StateFlow<StationType>` e `onTypeSelected(type: StationType)` mantêm a mesma assinatura entre Task 1 (produção) e Tasks 3/4 (consumo); `formatRating(rating: Double): String` definido na Task 5 e usado só ali (sem uso divergente em outra task).

**Riscos identificados:**
- Task 2 muda a assinatura pública do construtor de `StationsViewModel` (Hilt `@Inject constructor`) — como a injeção é 100% por construtor sem módulo Hilt manual para essa classe, não há arquivo de DI adicional para atualizar.
- Backend real de `GET /stations/nearby` pode não estar implementado ainda (pendência documentada na memória `project_stations_feature`) — Task 6 já prevê esse cenário e não bloqueia nele.
