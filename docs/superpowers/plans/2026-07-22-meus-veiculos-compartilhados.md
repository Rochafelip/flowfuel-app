# Meus Veículos: veículos compartilhados Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fazer `VehiclesScreen` ("Meus Veículos", acessada via Perfil) mostrar tanto os veículos próprios do usuário quanto os veículos compartilhados ativos com ele, em duas seções visualmente distintas, com o veículo emprestado levando ao modo convidado ao ser tocado.

**Architecture:** Reaproveita o padrão já validado em `VehiclePickerScreen`/`VehiclePickerViewModel` (`GetActiveSharedVehiclesUseCase` + `SetActiveGuestVehicleUseCase`), mas com estado separado em duas listas tipadas (`ownedItems: List<Vehicle>`, `borrowedItems: List<VehicleShare>`) em vez de um sealed item único, porque a UI precisa renderizar duas seções com cabeçalho em vez de uma lista misturada. O card visual de veículo emprestado é extraído do picker para um componente compartilhado do design system, reaproveitado nas duas telas.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, coroutines/Flow, MockK + Robolectric + Turbine para testes de ViewModel (não há testes de UI Compose neste projeto — a verificação de telas é indireta, via compilação forçada pela suíte de testes do módulo).

## Global Constraints

- Todo texto de UI em português (pt-BR), seguindo o padrão de cópia já usado nas telas do módulo `vehicle` (ex.: "Emprestado", "até {data}").
- Comandos de build/teste no Windows usam `.\gradlew.bat` (não `./gradlew`).
- Este projeto não tem testes de UI Compose (`createComposeRule`) — a verificação de mudanças de tela é feita rodando a suíte de testes de ViewModel do módulo afetado, que força a compilação de todo o código principal (incluindo as telas) antes de rodar.
- Specs/plans deste projeto ficam em `docs/superpowers/`; a spec desta feature está em `docs/superpowers/specs/2026-07-22-meus-veiculos-compartilhados-design.md`.

---

### Task 1: Extrair `FFBorrowedVehicleCard` para o design system

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/core/designsystem/components/FFBorrowedVehicleCard.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/list/VehiclePickerScreen.kt`

**Interfaces:**
- Consumes: `VehicleShare` (existente, `com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare`), `FFCard`/`FFCardVariant` (existentes em `core/designsystem/components/FFCard.kt`), `FFTheme` (existente).
- Produces: `FFBorrowedVehicleCard(share: VehicleShare, modifier: Modifier = Modifier, onClick: () -> Unit)` — usado por Task 3.

Este task é um refactor puro (move código, sem mudar comportamento): o card `BorrowedVehicleCard` hoje é privado dentro de `VehiclePickerScreen.kt` (nome/modelo + badge pill "Emprestado" + "até {data de expiração}"). Vira um composable público reutilizável.

- [ ] **Step 1: Criar o componente compartilhado**

Criar `app/src/main/java/com/flowfuel/app/core/designsystem/components/FFBorrowedVehicleCard.kt`:

```kotlin
package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Card de veículo compartilhado com o usuário (não é dono). Usado em
 * `VehiclePickerScreen` e `VehiclesScreen` — mesmo visual nas duas telas
 * para deixar claro que é um veículo diferente de um próprio.
 */
@Composable
fun FFBorrowedVehicleCard(
    share: VehicleShare,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        ) {
            Text(
                text = "${share.vehicleBrand} ${share.vehicleModel}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Surface(
                color = FFTheme.semanticColors.info,
                contentColor = FFTheme.semanticColors.onInfo,
                shape = FFTheme.extraShapes.pill,
            ) {
                Text(
                    text = "Emprestado",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(
                        start = FFTheme.spacing.sm,
                        end = FFTheme.spacing.sm,
                        top = 2.dp,
                        bottom = 2.dp,
                    ),
                )
            }
        }
        Text(
            text = "até ${share.expiresAt?.formatShareExpiry() ?: "—"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val shareExpiryFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("pt", "BR"))

private fun String.formatShareExpiry(): String =
    runCatching { LocalDate.parse(take(10)).format(shareExpiryFormatter) }.getOrDefault(this)
```

- [ ] **Step 2: Atualizar `VehiclePickerScreen.kt` para usar o componente extraído**

Remover do arquivo (não existem mais depois deste step):
- O composable privado `BorrowedVehicleCard` (linhas 236-276 do arquivo atual).
- `private val shareExpiryFormatter` e `private fun String.formatShareExpiry()` (linhas 278-282).
- Os imports que só eram usados por esse código removido: `androidx.compose.foundation.layout.Row`, `androidx.compose.material3.Surface`, `androidx.compose.ui.unit.dp`, `com.flowfuel.app.core.designsystem.components.FFCard`, `com.flowfuel.app.core.designsystem.components.FFCardVariant`, `java.time.LocalDate`, `java.time.format.DateTimeFormatter`.

Adicionar o import:
```kotlin
import com.flowfuel.app.core.designsystem.components.FFBorrowedVehicleCard
```

No branch `is VehiclePickerItem.Borrowed ->` dentro do `when (item)` da `LazyColumn`, trocar a chamada:
```kotlin
                                    is VehiclePickerItem.Borrowed -> {
                                        FFBorrowedVehicleCard(
                                            share = item.share,
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = { viewModel.onItemSelected(item) },
                                        )
                                    }
```

(Mantém `java.util.Locale` importado — ainda é usado pelo card `Owned`, em `String.format(Locale("pt", "BR"), "%,d", vehicle.odometerKm)`.)

- [ ] **Step 3: Rodar a suíte de testes do picker pra garantir que o refactor não quebrou nada**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.list.VehiclePickerViewModelTest"`
Expected: BUILD SUCCESSFUL (isso também força a compilação de `VehiclePickerScreen.kt` e do novo `FFBorrowedVehicleCard.kt` — se houver erro de sintaxe/import em qualquer um dos dois, o build falha aqui)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/designsystem/components/FFBorrowedVehicleCard.kt app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/list/VehiclePickerScreen.kt
git commit -m "refactor(vehicle): extract FFBorrowedVehicleCard from VehiclePickerScreen"
```

---

### Task 2: `VehiclesViewModel` — carregar e separar veículos próprios e compartilhados

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/manage/VehiclesUiState.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/manage/VehiclesViewModel.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/manage/VehiclesViewModelTest.kt` (novo arquivo)

**Interfaces:**
- Consumes: `GetActiveSharedVehiclesUseCase.invoke(): AppResult<List<VehicleShare>>` (existente), `SetActiveGuestVehicleUseCase.invoke(vehicleId: Int)` (existente), `VehicleShare` (existente).
- Produces: `VehiclesScreenState.Success(ownedItems: List<Vehicle>, borrowedItems: List<VehicleShare>)`; `VehiclesEffect.NavigateToGuestVehicle(share: VehicleShare)`; `VehiclesViewModel.onBorrowedSelected(share: VehicleShare)` — usados por Task 3.

- [ ] **Step 1: Escrever os testes (vão falhar — `ownedItems`/`borrowedItems`/`onBorrowedSelected` não existem ainda)**

Criar `app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/manage/VehiclesViewModelTest.kt`:

```kotlin
package com.flowfuel.app.feature.vehicle.presentation.manage

import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShareStatus
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetActiveSharedVehiclesUseCase
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.PagedVehicles
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import com.flowfuel.app.feature.vehicle.domain.usecase.DeleteVehicleUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehiclesPageUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.SetActiveGuestVehicleUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.SetActiveVehicleUseCase
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
class VehiclesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val getVehiclesPage: GetVehiclesPageUseCase = mockk()
    private val setActiveVehicle: SetActiveVehicleUseCase = mockk(relaxed = true)
    private val deleteVehicle: DeleteVehicleUseCase = mockk(relaxed = true)
    private val sessionStore: SessionStore = mockk(relaxed = true)
    private val getActiveSharedVehicles: GetActiveSharedVehiclesUseCase = mockk()
    private val setActiveGuestVehicle: SetActiveGuestVehicleUseCase = mockk(relaxed = true)

    private val fixtureVehicle = Vehicle(
        id = 1,
        brand = "Toyota",
        model = "Corolla",
        manufactureYear = 2020,
        modelYear = 2020,
        licensePlate = "ABC1234",
        color = "Prata",
        type = VehicleType.Car,
        energyType = EnergyType.Combustion,
        fuelType = null,
        odometerKm = 10000,
        tankCapacityL = 50.0,
        batteryCapacityKwh = null,
        isActive = true,
    )

    private val fixtureShare = VehicleShare(
        id = 100, vehicleId = 99, vehicleBrand = "Fiat", vehicleModel = "Uno",
        ownerId = 5, ownerName = "Outro Dono", guestId = 1, guestName = "Eu",
        status = VehicleShareStatus.ACTIVE, createdAt = null, respondedAt = null,
        expiresAt = "2026-07-20T00:00:00",
    )

    private fun createViewModel(): VehiclesViewModel {
        // Cada teste deve stubar getVehiclesPage(0) e getActiveSharedVehicles()
        // antes de chamar createViewModel() — o init{} já dispara load().
        return VehiclesViewModel(
            getVehiclesPage,
            setActiveVehicle,
            deleteVehicle,
            sessionStore,
            getActiveSharedVehicles,
            setActiveGuestVehicle,
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_comVeiculosPropriosECompartilhados_separaEmDoisGrupos() = runTest {
        coEvery { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        coEvery { getVehiclesPage(0) } returns AppResult.Success(
            PagedVehicles(items = listOf(fixtureVehicle), currentPage = 0, totalPages = 1, totalElements = 1),
        )
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(listOf(fixtureShare))

        val viewModel = createViewModel()

        val screenState = viewModel.state.value.screenState as VehiclesScreenState.Success
        assertEquals(listOf(fixtureVehicle), screenState.ownedItems)
        assertEquals(listOf(fixtureShare), screenState.borrowedItems)
    }

    @Test
    fun load_semVeiculosPropriosMasComCompartilhados_naoDisparaEmpty() = runTest {
        coEvery { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        coEvery { getVehiclesPage(0) } returns AppResult.Success(
            PagedVehicles(items = emptyList(), currentPage = 0, totalPages = 1, totalElements = 0),
        )
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(listOf(fixtureShare))

        val viewModel = createViewModel()

        val screenState = viewModel.state.value.screenState as VehiclesScreenState.Success
        assertTrue(screenState.ownedItems.isEmpty())
        assertEquals(listOf(fixtureShare), screenState.borrowedItems)
    }

    @Test
    fun load_semVeiculosEmAmbos_disparaEmpty() = runTest {
        coEvery { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        coEvery { getVehiclesPage(0) } returns AppResult.Success(
            PagedVehicles(items = emptyList(), currentPage = 0, totalPages = 1, totalElements = 0),
        )
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(emptyList())

        val viewModel = createViewModel()

        assertEquals(VehiclesScreenState.Empty, viewModel.state.value.screenState)
    }

    @Test
    fun load_falhaAoBuscarCompartilhados_trataComoListaVaziaSemErroGlobal() = runTest {
        coEvery { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        coEvery { getVehiclesPage(0) } returns AppResult.Success(
            PagedVehicles(items = listOf(fixtureVehicle), currentPage = 0, totalPages = 1, totalElements = 1),
        )
        coEvery { getActiveSharedVehicles() } returns AppResult.Failure(AppError.Network)

        val viewModel = createViewModel()

        val screenState = viewModel.state.value.screenState as VehiclesScreenState.Success
        assertEquals(listOf(fixtureVehicle), screenState.ownedItems)
        assertTrue(screenState.borrowedItems.isEmpty())
    }

    @Test
    fun onBorrowedSelected_chamaSetActiveGuestVehicleEEmiteEfeito() = runTest {
        coEvery { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        coEvery { getVehiclesPage(0) } returns AppResult.Success(
            PagedVehicles(items = listOf(fixtureVehicle), currentPage = 0, totalPages = 1, totalElements = 1),
        )
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(listOf(fixtureShare))
        coEvery { setActiveGuestVehicle(99) } returns Unit

        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.onBorrowedSelected(fixtureShare)
            assertEquals(VehiclesEffect.NavigateToGuestVehicle(fixtureShare), awaitItem())
        }
        coVerify { setActiveGuestVehicle(99) }
    }
}
```

- [ ] **Step 2: Rodar o teste pra confirmar que falha**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.manage.VehiclesViewModelTest"`
Expected: FAIL (erro de compilação — `ownedItems`, `borrowedItems`, `NavigateToGuestVehicle`, `onBorrowedSelected` e o construtor de 6 argumentos ainda não existem)

- [ ] **Step 3: Alterar `VehiclesUiState.kt`**

Conteúdo completo do arquivo:

```kotlin
package com.flowfuel.app.feature.vehicle.presentation.manage

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle

// ─── Estado da tela ───────────────────────────────────────────────────────────

sealed interface VehiclesScreenState {
    data object Loading : VehiclesScreenState
    data object Empty : VehiclesScreenState
    data class Error(val error: AppError) : VehiclesScreenState
    data class Success(
        val ownedItems: List<Vehicle>,
        val borrowedItems: List<VehicleShare>,
    ) : VehiclesScreenState
}

// ─── Estado global ────────────────────────────────────────────────────────────

data class VehiclesUiState(
    val screenState: VehiclesScreenState = VehiclesScreenState.Loading,
    /** ID do veículo atualmente ativo; atualizado otimisticamente ao trocar. */
    val activeVehicleId: Int? = null,
    /** Não-null enquanto o dialog de confirmação de exclusão está visível. */
    val vehiclePendingDelete: Vehicle? = null,
    /** true enquanto a chamada de exclusão está em andamento. */
    val isDeleting: Boolean = false,
)

// ─── Efeitos pontuais ─────────────────────────────────────────────────────────

sealed interface VehiclesEffect {
    data object NavigateToLogin : VehiclesEffect
    data class NavigateToGuestVehicle(val share: VehicleShare) : VehiclesEffect
}
```

- [ ] **Step 4: Alterar `VehiclesViewModel.kt`**

Adicionar aos imports:
```kotlin
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetActiveSharedVehiclesUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.SetActiveGuestVehicleUseCase
```

Adicionar ao construtor (depois de `sessionStore`):
```kotlin
    private val sessionStore: SessionStore,
    private val getActiveSharedVehicles: GetActiveSharedVehiclesUseCase,
    private val setActiveGuestVehicle: SetActiveGuestVehicleUseCase,
) : ViewModel() {
```

Substituir `load()` inteiro por:
```kotlin
    fun load() {
        _state.update { it.copy(screenState = VehiclesScreenState.Loading, isDeleting = false) }
        accumulatedVehicles = emptyList()
        _pagination.value = PaginationState()
        isLoadingMore = false
        viewModelScope.launch {
            val activeId = sessionStore.activeVehicleIdFlow.first()
            when (val result = getVehiclesPage(0)) {
                is AppResult.Success -> {
                    val paged = result.value
                    accumulatedVehicles = paged.items
                    val sharedResult = getActiveSharedVehicles()
                    val borrowedItems = (sharedResult as? AppResult.Success)?.value ?: emptyList()
                    _state.update {
                        it.copy(
                            activeVehicleId = activeId,
                            screenState = if (paged.items.isEmpty() && borrowedItems.isEmpty())
                                VehiclesScreenState.Empty
                            else
                                VehiclesScreenState.Success(accumulatedVehicles, borrowedItems),
                        )
                    }
                    _pagination.value = PaginationState(currentPage = 0, hasMore = paged.hasMore)
                }
                is AppResult.Failure -> {
                    Timber.e("Vehicles: error → ${result.error}")
                    handleGlobalError(result.error)
                }
            }
        }
    }
```

Substituir o corpo do `is AppResult.Success ->` dentro de `loadNextPage()` por (mantém o resto do método igual):
```kotlin
                is AppResult.Success -> {
                    val existingIds = accumulatedVehicles.map { it.id }.toSet()
                    val deduped = result.value.items.filter { it.id !in existingIds }
                    accumulatedVehicles = accumulatedVehicles + deduped

                    val currentBorrowed = (_state.value.screenState as? VehiclesScreenState.Success)
                        ?.borrowedItems ?: emptyList()
                    _state.update {
                        it.copy(screenState = VehiclesScreenState.Success(accumulatedVehicles, currentBorrowed))
                    }
                    _pagination.update {
                        it.copy(
                            currentPage = nextPage,
                            isLoadingMore = false,
                            hasMore = result.value.hasMore,
                        )
                    }
                }
```

Adicionar um novo método, próximo de `onSetActive` (seção "Troca de veículo ativo"):
```kotlin
    /** Define [share] como veículo ativo em modo convidado e navega para sua Home mínima. */
    fun onBorrowedSelected(share: VehicleShare) {
        viewModelScope.launch {
            setActiveGuestVehicle(share.vehicleId)
            _effects.send(VehiclesEffect.NavigateToGuestVehicle(share))
        }
    }
```

- [ ] **Step 5: Rodar o teste pra confirmar que passa**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.manage.VehiclesViewModelTest"`
Expected: PASS (5 testes)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/manage/VehiclesUiState.kt app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/manage/VehiclesViewModel.kt app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/manage/VehiclesViewModelTest.kt
git commit -m "feat(vehicle): VehiclesViewModel separa veículos próprios e compartilhados"
```

---

### Task 3: `VehiclesScreen` — renderizar as duas seções

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/manage/VehiclesScreen.kt`

**Interfaces:**
- Consumes: `VehiclesScreenState.Success(ownedItems, borrowedItems)` (Task 2), `VehiclesEffect.NavigateToGuestVehicle` (Task 2), `VehiclesViewModel.onBorrowedSelected` (Task 2), `FFBorrowedVehicleCard` (Task 1).
- Produces: novo parâmetro `VehiclesScreen(..., onNavigateToGuestVehicle: (VehicleShare) -> Unit = {})` — usado por Task 4.

Sem teste automatizado dedicado (não há testes de UI Compose neste projeto). A verificação é: build limpo (Step 3, forçado ao rodar a suíte de testes do módulo) + verificação manual ao final do plano.

- [ ] **Step 1: Adicionar imports necessários**

No topo de `VehiclesScreen.kt`, adicionar:
```kotlin
import androidx.compose.foundation.lazy.items
import com.flowfuel.app.core.designsystem.components.FFBorrowedVehicleCard
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
```

- [ ] **Step 2: Adicionar o novo parâmetro à assinatura de `VehiclesScreen`**

Depois de `onNavigateToVehicleEvents: (vehicleId: Int) -> Unit = {},`, adicionar:
```kotlin
    onNavigateToGuestVehicle: (VehicleShare) -> Unit = {},
```

- [ ] **Step 3: Tratar o novo efeito**

No `LaunchedEffect(viewModel) { viewModel.effects.collectLatest { effect -> when (effect) { ... } } }`, adicionar o novo branch:
```kotlin
            when (effect) {
                VehiclesEffect.NavigateToLogin -> onNavigateToLogin()
                is VehiclesEffect.NavigateToGuestVehicle -> onNavigateToGuestVehicle(effect.share)
            }
```

- [ ] **Step 4: Renderizar as duas seções na `LazyColumn`**

Substituir o corpo do branch `is VehiclesScreenState.Success -> LazyColumn(...) { ... }` (hoje itera `s.vehicles` com `itemsIndexed`) por:

```kotlin
                is VehiclesScreenState.Success -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start  = FFTheme.spacing.md,
                        end    = FFTheme.spacing.md,
                        top    = FFTheme.spacing.md,
                        // Espaço extra para o FAB não cobrir o último item
                        bottom = FFTheme.spacing.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap),
                ) {
                    if (s.ownedItems.isNotEmpty()) {
                        item(key = "owned_header") {
                            Text(
                                text = "Meus veículos",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    itemsIndexed(
                        items = s.ownedItems,
                        key   = { _, v -> v.id },
                    ) { _, vehicle ->
                        VehicleManageItem(
                            vehicle                    = vehicle,
                            activeVehicleId            = state.activeVehicleId,
                            onNavigateToVehicleDetails = { onNavigateToVehicleDetails(vehicle.id) },
                            onSetActive                = { viewModel.onSetActive(vehicle) },
                            onEdit                     = { onNavigateToEditVehicle(vehicle.id) },
                            onDeleteRequest            = { viewModel.onDeleteRequest(vehicle) },
                            onNavigateToEvents         = { onNavigateToVehicleEvents(vehicle.id) },
                        )
                    }

                    if (paginationState.isLoadingMore) {
                        item(key = "loading_more") {
                            FFSkeletonBlock(
                                modifier = Modifier.padding(top = FFTheme.spacing.cardGap),
                            )
                        }
                    }

                    paginationState.pageError?.let { error ->
                        item(key = "error_retry") {
                            VehiclesPaginationErrorRetry(
                                message = error.userMessage(),
                                onRetry = viewModel::loadNextPage,
                                modifier = Modifier.padding(top = FFTheme.spacing.sm),
                            )
                        }
                    }

                    if (s.borrowedItems.isNotEmpty()) {
                        item(key = "borrowed_header") {
                            Text(
                                text = "Compartilhados comigo",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = FFTheme.spacing.sm),
                            )
                        }

                        items(
                            items = s.borrowedItems,
                            key   = { share -> "borrowed_${share.id}" },
                        ) { share ->
                            FFBorrowedVehicleCard(
                                share    = share,
                                modifier = Modifier.fillMaxWidth(),
                                onClick  = { viewModel.onBorrowedSelected(share) },
                            )
                        }
                    }
                }
```

(A seção "Meus veículos" some se `ownedItems` estiver vazio, e "Compartilhados comigo" some se `borrowedItems` estiver vazio — nunca mostra um cabeçalho de seção sem itens embaixo.)

- [ ] **Step 5: Rodar a suíte de testes do módulo vehicle pra garantir que compila e nada quebrou**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.*"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/manage/VehiclesScreen.kt
git commit -m "feat(vehicle): VehiclesScreen exibe veículos compartilhados em seção separada"
```

---

### Task 4: Conectar a navegação para o modo convidado a partir de Meus Veículos

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt`

**Interfaces:**
- Consumes: `VehiclesScreen(..., onNavigateToGuestVehicle: (VehicleShare) -> Unit)` (Task 3), `Destinations.MAIN_CONTAINER`, `Destinations.VEHICLE_MANAGE` (existentes).

- [ ] **Step 1: Adicionar o callback na composable de `Destinations.VEHICLE_MANAGE`**

No bloco `composable(Destinations.VEHICLE_MANAGE) { entry -> ... VehiclesScreen(...) }`, depois de `onNavigateToVehicleEvents = { vehicleId -> navController.navigate(Destinations.vehicleEvents(vehicleId)) },`, adicionar:

```kotlin
                onNavigateToGuestVehicle = { _ ->
                    // Mesmo padrão já usado na rota do picker (Destinations.VEHICLE_PICKER):
                    // entrar em modo convidado sempre reseta a navegação pra raiz,
                    // mesmo vindo de "Meus Veículos" empilhada sobre o Perfil.
                    navController.navigate(Destinations.MAIN_CONTAINER) {
                        popUpTo(0) { inclusive = true }
                    }
                },
```

- [ ] **Step 2: Rodar a suíte completa do app pra garantir que nada quebrou**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt
git commit -m "feat(vehicle): navegar para modo convidado a partir de Meus Veículos"
```

## Verificação manual (fora do escopo automatizado)

Com uma conta que tenha ao menos 1 veículo próprio e 1 compartilhamento ativo aceito como convidado (ver [[project_qa_test_account]] — ou repetir o convite de teste já documentado em [[project_vehicleshare_module]]): abrir Perfil → Meus Veículos e confirmar (1) as duas seções aparecem com os cabeçalhos corretos, (2) o card do veículo emprestado mostra o badge "Emprestado" e a data de expiração, sem menu de 3 pontos, (3) tocar nele leva à Home mínima de convidado (`GuestVehicleScreen`) com bottom nav restrita, (4) voltar não retorna para "Meus Veículos" nem para o Perfil (pilha foi resetada). Testar também os casos de borda: conta só com veículos próprios (seção "Compartilhados comigo" não aparece) e, se possível, conta só com veículo emprestado e nenhum próprio (seção "Meus veículos" não aparece, sem cair no estado vazio "Nenhum veículo cadastrado").
