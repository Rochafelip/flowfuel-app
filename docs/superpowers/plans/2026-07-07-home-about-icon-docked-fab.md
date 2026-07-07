# Ícone "Sobre" + FAB docado na bottom bar — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar um destino real ao ícone inerte do `VehicleHeader` (abre um diálogo "Sobre") e substituir os três FABs duplicados hoje (Home, Histórico, Eventos) por um único FAB docado na bottom bar, contextual por aba.

**Architecture:** `FFBottomBar` passa de `NavigationBar` para `BottomAppBar` com um slot de FAB. `MainContainerScreen` passa a ser dono do FAB (ícone/ação conforme a rota atual) e de um novo `QuickRefuelViewModel` (extraído de `HomeViewModel`), que hospeda `QuickRefuelBottomSheet` no nível do Scaffold externo — assim o sheet abre a partir de qualquer aba, sem trocar de aba. `HomeScreen`, `HistoryScreen` e `VehicleEventsScreen` perdem seus FABs próprios.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, StateFlow, Robolectric + MockK + Turbine (testes de ViewModel).

## Global Constraints

- Nenhum teste de UI/Compose instrumentado é escrito neste plano — não é padrão do projeto hoje (ver README). Tarefas que só tocam Composables são verificadas por compilação (`./gradlew compileDebugKotlin`) e `@Preview`, não por JUnit.
- Tarefas que tocam `ViewModel` seguem TDD real com Robolectric + MockK + Turbine, mesmo padrão de `HomeViewModelTest.kt` (`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [33])`, `UnconfinedTestDispatcher`).
- Onde uma tarefa adiciona um parâmetro novo a uma função composable já chamada por outra tarefa ainda não executada, o novo parâmetro recebe um valor padrão neutro (`= {}` / `= false`) para manter o projeto compilável a cada commit. A tarefa de integração final substitui esses padrões pela wiring real.
- Todo texto de UI em pt-BR, seguindo o vocabulário já usado no arquivo tocado.

---

## File Structure

```
core/designsystem/components/FFBottomBar.kt        MODIFY — NavigationBar → BottomAppBar + slot de FAB
feature/home/presentation/
├── components/VehicleHeader.kt                     MODIFY — novo IconButton "Sobre"
├── AboutDialog.kt                                  NEW    — diálogo "Sobre"
├── QuickRefuelViewModel.kt                         NEW    — RefuelFormState/OdometerInputMode movidos de HomeUiState.kt + novo ViewModel
├── HomeUiState.kt                                  MODIFY — remove campos de abastecimento; adiciona showAboutDialog
├── HomeViewModel.kt                                MODIFY — remove lógica de abastecimento; adiciona openAboutDialog/closeAboutDialog
├── HomeScreen.kt                                   MODIFY — remove FAB/snackbar/sheet/spacer; adiciona AboutDialog
└── QuickRefuelBottomSheet.kt                        inalterado (só muda quem o hospeda)
feature/vehicleevent/presentation/list/VehicleEventsScreen.kt   MODIFY — remove FAB; adiciona triggerCreate
feature/history/presentation/HistoryScreen.kt       MODIFY — remove FAB/onAddRefuel
navigation/MainContainerScreen.kt                   MODIFY — integração final (FAB contextual, QuickRefuelViewModel, sheet, snackbar)

app/src/test/java/com/flowfuel/app/feature/home/presentation/
├── HomeViewModelTest.kt                             MODIFY — remove testes de abastecimento; adiciona testes do diálogo Sobre
└── QuickRefuelViewModelTest.kt                       NEW    — testes movidos + novos (fetch de veículo, efeito)
```

---

### Task 1: FFBottomBar — BottomAppBar com slot de FAB docado

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/core/designsystem/components/FFBottomBar.kt`

**Interfaces:**
- Produces: `FFBottomBar(items: List<FFBottomItem>, currentRoute: String?, onSelect: (FFBottomItem) -> Unit, modifier: Modifier = Modifier, floatingActionButton: @Composable (() -> Unit)? = null)` — novo parâmetro opcional, `null` por padrão (compatível com os chamadores atuais — `MainContainerScreen`, até a Task 6, e o preview `core/designsystem/preview/UiKitDemoScreen.kt`, que não passa esse parâmetro).

- [ ] **Step 1: Substituir `NavigationBar` por `BottomAppBar` com slot de FAB**

Conteúdo completo do arquivo:

```kotlin
package com.flowfuel.app.core.designsystem.components

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

data class FFBottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badgeCount: Int? = null,
)

/**
 * `floatingActionButton`, quando fornecido, fica "docado" (elevado, sobrepondo
 * levemente a barra) — comportamento nativo de `BottomAppBar`. As abas em [items]
 * continuam todas visíveis ao redor dele.
 */
@Composable
fun FFBottomBar(
    items: List<FFBottomItem>,
    currentRoute: String?,
    onSelect: (FFBottomItem) -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: @Composable (() -> Unit)? = null,
) {
    BottomAppBar(
        modifier = modifier,
        floatingActionButton = floatingActionButton,
        actions = {
            items.forEach { item ->
                val selected = currentRoute == item.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(item) },
                    icon = {
                        BadgedBox(
                            badge = {
                                val count = item.badgeCount
                                if (count != null && count > 0) {
                                    Badge { Text(if (count > 99) "99+" else count.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.icon,
                                contentDescription = item.label
                            )
                        }
                    },
                    label = { Text(item.label) },
                    alwaysShowLabel = true,
                )
            }
        },
    )
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL (o único chamador, `MainContainerScreen.FFBottomBar(items=..., currentRoute=..., onSelect=...)`, continua compilando sem alterações — `floatingActionButton` usa o padrão `null`, a barra aparece sem FAB até a Task 6).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/designsystem/components/FFBottomBar.kt
git commit -m "feat(design-system): FFBottomBar ganha slot de FAB docado (BottomAppBar)"
```

---

### Task 2: Ícone "Sobre" no VehicleHeader + AboutDialog

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/VehicleHeader.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/home/presentation/AboutDialog.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeUiState.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt`

**Interfaces:**
- Produces: `VehicleHeader(vehicle, daysSinceLastRefuel, onVehicleClick, onInfoClick: () -> Unit, modifier = Modifier)` — novo parâmetro obrigatório.
- Produces: `AboutDialog(onDismiss: () -> Unit)`.
- Produces: `HomeUiState.showAboutDialog: Boolean`, `HomeViewModel.openAboutDialog()`, `HomeViewModel.closeAboutDialog()`.

- [ ] **Step 1: Escrever o teste que falha (diálogo Sobre no HomeViewModel)**

Adicionar ao final de `HomeViewModelTest.kt`, antes do último `}`:

```kotlin
    // ── Diálogo "Sobre" ─────────────────────────────────────────────────────

    @Test
    fun `openAboutDialog() shows the dialog`() {
        viewModel.openAboutDialog()
        assertTrue(viewModel.state.value.showAboutDialog)
    }

    @Test
    fun `closeAboutDialog() hides the dialog`() {
        viewModel.openAboutDialog()
        viewModel.closeAboutDialog()
        assertFalse(viewModel.state.value.showAboutDialog)
    }
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.HomeViewModelTest"`
Expected: FAIL — `showAboutDialog`/`openAboutDialog`/`closeAboutDialog` não existem ainda (erro de compilação do teste).

- [ ] **Step 3: Adicionar `showAboutDialog` a `HomeUiState`**

Em `HomeUiState.kt`, no `data class HomeUiState`, adicionar o campo após `showLicensingDueDatePicker`:

```kotlin
    // ── Data de licenciamento (lembrete de manutenção) ─────────────────────
    val showLicensingDueDatePicker: Boolean = false,
    // ── Diálogo "Sobre" ─────────────────────────────────────────────────────
    val showAboutDialog: Boolean = false,
)
```

- [ ] **Step 4: Adicionar `openAboutDialog`/`closeAboutDialog` a `HomeViewModel`**

Em `HomeViewModel.kt`, logo após o bloco `// ─── Data de licenciamento ───...` (após `onLicensingDueDateSelected`), adicionar:

```kotlin
    // ─── Diálogo "Sobre" ──────────────────────────────────────────────────────

    fun openAboutDialog() = _state.update { it.copy(showAboutDialog = true) }

    fun closeAboutDialog() = _state.update { it.copy(showAboutDialog = false) }
```

- [ ] **Step 5: Rodar o teste e confirmar que passa**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.HomeViewModelTest"`
Expected: PASS (todos os testes, incluindo os 2 novos).

- [ ] **Step 6: Criar `AboutDialog`**

```kotlin
package com.flowfuel.app.feature.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flowfuel.app.BuildConfig
import com.flowfuel.app.R
import com.flowfuel.app.core.designsystem.theme.FFTheme

/** Diálogo simples de "Sobre": logo, nome e versão do app. */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        },
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_splash),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Text("FlowFuel", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Text(
                text = "Versão ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun AboutDialogPreview() {
    AboutDialog(onDismiss = {})
}
```

- [ ] **Step 7: Adicionar o ícone "Sobre" ao `VehicleHeader`**

Substituir o `Row` externo de `VehicleHeader` (o `IconButton` do sino de notificações) por:

```kotlin
@Composable
fun VehicleHeader(
    vehicle: ActiveVehicleData,
    daysSinceLastRefuel: Int?,
    onVehicleClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = FFTheme.spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        ) {
            VehiclePhotoAvatar(
                photoUrl = vehicle.photoUrl,
                vehicleType = vehicle.vehicleType,
                size = 48.dp,
                onClick = onVehicleClick,
            )
            Column {
                Text(
                    text = "${vehicle.brand} ${vehicle.model}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = daysSinceRefuelLabel(daysSinceLastRefuel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onInfoClick) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Sobre o app",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Reservado para o futuro: hoje é só um ícone estático, sem contagem/lógica de notificações.
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Notificações",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

Adicionar o import `androidx.compose.material.icons.outlined.Info` (junto aos demais imports `androidx.compose.material.icons.outlined.*` já existentes no arquivo).

Atualizar o `@Preview` no final do arquivo, adicionando `onInfoClick = {}` à chamada de `VehicleHeader(...)`.

- [ ] **Step 8: Wire no `HomeScreen`**

Em `HomeScreen.kt` (`AboutDialog` está no mesmo pacote `com.flowfuel.app.feature.home.presentation`, então nenhum import novo é necessário para usá-lo):

1. Na chamada de `VehicleHeader` dentro de `HomeContent` (função privada), adicionar o parâmetro `onInfoClick: () -> Unit` à assinatura de `HomeContent` e repassar:

```kotlin
@Composable
private fun HomeContent(
    vehicle: ActiveVehicleData,
    dashboard: DashboardData,
    financialSummary: SectionState<FinancialSummary>,
    recentActivity: SectionState<List<VehicleTimelineItem>>,
    upcomingMaintenance: SectionState<List<UpcomingMaintenanceItem>>,
    onRegisterRefuel: () -> Unit,
    onVehicleClick: () -> Unit,
    onInfoClick: () -> Unit,
    onRetryFinancialSummary: () -> Unit,
    onRetryRecentActivity: () -> Unit,
    onRetryUpcomingMaintenance: () -> Unit,
    onUpcomingEventClick: (UpcomingMaintenanceType) -> Unit,
    modifier: Modifier = Modifier,
) {
    ...
    item {
        VehicleHeader(
            vehicle = vehicle,
            daysSinceLastRefuel = if (isFirstUse) null else daysSince,
            onVehicleClick = onVehicleClick,
            onInfoClick = onInfoClick,
        )
    }
    ...
}
```

3. No corpo de `HomeScreen` (função pública), passar `onInfoClick = viewModel::openAboutDialog` na chamada de `HomeContent(...)` dentro do bloco `HomeScreenState.Success`.
4. Adicionar o diálogo, junto aos outros diálogos condicionais no final de `HomeScreen` (após o bloco `if (state.showLicensingDueDatePicker) { ... }`):

```kotlin
    if (state.showAboutDialog) {
        AboutDialog(onDismiss = viewModel::closeAboutDialog)
    }
```

- [ ] **Step 9: Compilar**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/VehicleHeader.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/AboutDialog.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeUiState.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt \
        app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt
git commit -m "feat(home): icone Sobre no VehicleHeader abre dialogo com nome/logo/versao"
```

---

### Task 3: VehicleEventsScreen — remove FAB próprio, adiciona `triggerCreate`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/list/VehicleEventsScreen.kt`

**Interfaces:**
- Consumes: `VehicleEventsViewModel.onCreateClick()` (já existe, inalterado — envia `VehicleEventsEffect.NavigateToCreate(vehicleId)`, já consumido pelo `LaunchedEffect(viewModel)` existente).
- Produces: novos parâmetros `triggerCreate: Boolean = false`, `onCreateTriggerConsumed: () -> Unit = {}` em `VehicleEventsScreen`.

- [ ] **Step 1: Adicionar `triggerCreate`/`onCreateTriggerConsumed` e remover o FAB**

Na assinatura de `VehicleEventsScreen`, adicionar os dois novos parâmetros (com valor padrão, para não quebrar o único chamador, `MainContainerScreen`, antes da Task 6):

```kotlin
@Composable
fun VehicleEventsScreen(
    onBack: (() -> Unit)? = null,
    onNavigateToCreate: (vehicleId: Int) -> Unit,
    onNavigateToDetails: (eventId: Int) -> Unit,
    onNavigateToRefuelDetails: (refuelId: Int) -> Unit,
    onNavigateToLogin: () -> Unit,
    eventCreated: Boolean = false,
    onEventCreatedConsumed: () -> Unit = {},
    eventDeleted: Int = -1,
    onEventDeletedConsumed: () -> Unit = {},
    eventUpdated: Boolean = false,
    onEventUpdatedConsumed: () -> Unit = {},
    triggerCreate: Boolean = false,
    onCreateTriggerConsumed: () -> Unit = {},
    viewModel: VehicleEventsViewModel = hiltViewModel(),
) {
```

Logo após o `LaunchedEffect(eventUpdated) { ... }` já existente, adicionar:

```kotlin
    LaunchedEffect(triggerCreate) {
        if (triggerCreate) {
            viewModel.onCreateClick()
            onCreateTriggerConsumed()
        }
    }
```

No `Scaffold(...)`, remover inteiramente o parâmetro `floatingActionButton = { FFFab(...) }`.

Remover os imports agora não usados: `androidx.compose.material.icons.filled.Add` e `com.flowfuel.app.core.designsystem.components.FFFab`.

- [ ] **Step 2: Compilar**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL — a criação de evento pela aba Eventos fica temporariamente sem gatilho de UI (nenhum FAB chama `triggerCreate`/`viewModel::onCreateClick` ainda); volta a funcionar na Task 6.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/list/VehicleEventsScreen.kt
git commit -m "refactor(vehicleevent): remove FAB proprio, prepara para o FAB global via triggerCreate"
```

---

### Task 4: HistoryScreen — remove FAB próprio e `onAddRefuel`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/history/presentation/HistoryScreen.kt`
- Modify: `app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt`

**Interfaces:**
- Removes: parâmetro `onAddRefuel: () -> Unit` de `HistoryScreen`.

- [ ] **Step 1: Remover o FAB e o parâmetro `onAddRefuel` de `HistoryScreen`**

Na assinatura de `HistoryScreen`, remover a linha `onAddRefuel: () -> Unit = {},`.

No `Scaffold(...)`, remover inteiramente o parâmetro `floatingActionButton = { FFFab(...) }`.

Remover os imports agora não usados: `androidx.compose.material.icons.filled.LocalGasStation` e `com.flowfuel.app.core.designsystem.components.FFFab`.

- [ ] **Step 2: Remover a wiring de `onAddRefuel` em `MainContainerScreen`**

No bloco `composable(MainDestinations.HISTORY) { ... }` de `MainContainerScreen.kt`, remover o parâmetro `onAddRefuel = { ... }` inteiro (incluindo a navegação para `MainDestinations.HOME` que ele fazia), deixando:

```kotlin
            // ── Histórico ─────────────────────────────────────────────────────
            composable(MainDestinations.HISTORY) {
                HistoryScreen(
                    onNavigateToLogin        = onNavigateToLogin,
                    onNavigateToDetails      = onNavigateToRefuelDetails,
                    historyNeedsRefresh      = historyNeedsRefresh,
                    onHistoryRefreshConsumed = onHistoryRefreshConsumed,
                )
            }
```

Não remover ainda a declaração `var openRefuelSheet by remember { mutableStateOf(false) }` nem sua passagem para `HomeScreen` — isso é feito na Task 5, quando `HomeScreen` deixa de aceitar esse parâmetro.

- [ ] **Step 3: Compilar**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL — "Registrar abastecimento" a partir da aba Histórico fica temporariamente sem gatilho de UI; volta a funcionar na Task 6.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/history/presentation/HistoryScreen.kt \
        app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt
git commit -m "refactor(history): remove FAB proprio, acao passa a vir do FAB global"
```

---

### Task 5: Extrair `QuickRefuelViewModel`; Home deixa de ser dono do sheet

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/home/presentation/QuickRefuelViewModel.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeUiState.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`
- Modify: `app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt`
- Create: `app/src/test/java/com/flowfuel/app/feature/home/presentation/QuickRefuelViewModelTest.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `GetActiveVehicleUseCase`, `CreateRefuelUseCase` (já existem, injetados hoje em `HomeViewModel`).
- Produces: `QuickRefuelUiState(showSheet: Boolean, vehicle: ActiveVehicleData?, form: RefuelFormState, isSubmitting: Boolean, submitError: AppError?)`, `QuickRefuelEffect.RefuelRegistered`, `QuickRefuelViewModel` com `state: StateFlow<QuickRefuelUiState>`, `effects: Flow<QuickRefuelEffect>`, `openSheet()`, `closeSheet()`, `onOdometerInputModeChange`, `onTripKmChange`, `onOdometerChange`, `onLitersChange`, `onTotalPriceInput`, `onFullTankToggle`, `onRefuelTypeChange`, `submitRefuel()`.
- Produces: `HomeScreen(..., onOpenRefuelSheet: () -> Unit = {})` — novo parâmetro, usado só pelo CTA do estado vazio ("Pronto para começar").

- [ ] **Step 1: Criar `QuickRefuelViewModelTest.kt` com os testes migrados de `HomeViewModelTest.kt` (falhando — a classe ainda não existe)**

```kotlin
package com.flowfuel.app.feature.home.presentation

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.CreateRefuelRequest
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QuickRefuelViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val getActiveVehicle: GetActiveVehicleUseCase = mockk()
    private val createRefuel: CreateRefuelUseCase = mockk()

    private val testVehicle = ActiveVehicleData(
        id = 1,
        brand = "Volkswagen",
        model = "Fox",
        fuelSubType = null,
        capacity = null,
        licensePlate = "ABC1D23",
        energyType = "COMBUSTION",
        currentKm = 67270,
    )

    private lateinit var viewModel: QuickRefuelViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { getActiveVehicle() } returns AppResult.Success(testVehicle)
        viewModel = QuickRefuelViewModel(getActiveVehicle, createRefuel)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── openSheet ──────────────────────────────────────────────────────────────

    @Test
    fun `openSheet() shows the sheet and fetches the active vehicle`() = runTest {
        viewModel.openSheet()

        assertTrue(viewModel.state.value.showSheet)
        assertEquals(testVehicle, viewModel.state.value.vehicle)
    }

    @Test
    fun `closeSheet() hides the sheet and resets the form`() = runTest {
        viewModel.openSheet()
        viewModel.onLitersChange("21,29")

        viewModel.closeSheet()

        assertFalse(viewModel.state.value.showSheet)
        assertEquals("", viewModel.state.value.form.liters)
    }

    // ── onOdometerInputModeChange ─────────────────────────────────────────────

    @Test
    fun `onOdometerInputModeChange to ODOMETER sets mode`() {
        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)
        assertEquals(OdometerInputMode.ODOMETER, viewModel.state.value.form.odometerInputMode)
    }

    @Test
    fun `onOdometerInputModeChange clears odometer and tripKm fields`() {
        viewModel.onOdometerChange("672700")
        viewModel.onTripKmChange("310,6")
        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)

        val form = viewModel.state.value.form
        assertEquals("", form.odometer)
        assertEquals("", form.tripKm)
    }

    @Test
    fun `onOdometerInputModeChange clears errors`() {
        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)

        val form = viewModel.state.value.form
        assertFalse(form.odometerError)
        assertFalse(form.tripKmError)
    }

    // ── onTripKmChange ────────────────────────────────────────────────────────

    @Test
    fun `onTripKmChange filters to digits comma and dot only`() {
        viewModel.onTripKmChange("310,6abc!@#")
        assertEquals("310,6", viewModel.state.value.form.tripKm)
    }

    @Test
    fun `onTripKmChange accepts dot as decimal separator`() {
        viewModel.onTripKmChange("310.6")
        assertEquals("310.6", viewModel.state.value.form.tripKm)
    }

    @Test
    fun `onTripKmChange clears tripKmError`() = runTest {
        // Dispara o erro de validação primeiro
        viewModel.openSheet()
        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")
        viewModel.submitRefuel()
        assertTrue(viewModel.state.value.form.tripKmError)

        viewModel.onTripKmChange("310,6")
        assertFalse(viewModel.state.value.form.tripKmError)
    }

    // ── submitRefuel em modo TRIP ─────────────────────────────────────────────

    @Test
    fun `submitRefuel TRIP mode sends currentKm plus tripKm as odometer`() = runTest {
        var capturedRequest: CreateRefuelRequest? = null
        coEvery { createRefuel(any()) } answers {
            capturedRequest = firstArg()
            AppResult.Success(Unit)
        }

        viewModel.openSheet()
        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onTripKmChange("310,6")
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        // 67270.0 + 310.6 = 67580.6
        assertEquals(67580.6, capturedRequest!!.odometer, 0.001)
    }

    @Test
    fun `submitRefuel TRIP mode with comma separator parses correctly`() = runTest {
        var capturedRequest: CreateRefuelRequest? = null
        coEvery { createRefuel(any()) } answers {
            capturedRequest = firstArg()
            AppResult.Success(Unit)
        }

        viewModel.openSheet()
        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onTripKmChange("310.6")
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        assertEquals(67580.6, capturedRequest!!.odometer, 0.001)
    }

    @Test
    fun `submitRefuel TRIP mode blank tripKm sets tripKmError and skips api`() = runTest {
        viewModel.openSheet()
        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        assertTrue(viewModel.state.value.form.tripKmError)
        coVerify(exactly = 0) { createRefuel(any()) }
    }

    @Test
    fun `submitRefuel TRIP mode zero tripKm sets tripKmError and skips api`() = runTest {
        viewModel.openSheet()
        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onTripKmChange("0")
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        assertTrue(viewModel.state.value.form.tripKmError)
        coVerify(exactly = 0) { createRefuel(any()) }
    }

    @Test
    fun `submitRefuel ODOMETER mode uses odometerDouble as before`() = runTest {
        var capturedRequest: CreateRefuelRequest? = null
        coEvery { createRefuel(any()) } answers {
            capturedRequest = firstArg()
            AppResult.Success(Unit)
        }

        viewModel.openSheet()
        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)
        viewModel.onOdometerChange("675800")   // 67580.0 km
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        assertEquals(67580.0, capturedRequest!!.odometer, 0.001)
    }

    // ── submitRefuel: sucesso e erro ────────────────────────────────────────────

    @Test
    fun `submitRefuel on success closes the sheet, resets the form and emits RefuelRegistered`() = runTest {
        coEvery { createRefuel(any()) } returns AppResult.Success(Unit)
        viewModel.openSheet()
        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)
        viewModel.onOdometerChange("675800")
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        var effect: QuickRefuelEffect? = null
        val job = launch { effect = viewModel.effects.first() }

        viewModel.submitRefuel()
        job.join()

        assertFalse(viewModel.state.value.showSheet)
        assertEquals("", viewModel.state.value.form.liters)
        assertEquals(QuickRefuelEffect.RefuelRegistered, effect)
    }

    @Test
    fun `submitRefuel on failure keeps the sheet open and exposes submitError`() = runTest {
        coEvery { createRefuel(any()) } returns AppResult.Failure(AppError.Network)
        viewModel.openSheet()
        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)
        viewModel.onOdometerChange("675800")
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        assertTrue(viewModel.state.value.showSheet)
        assertEquals(AppError.Network, viewModel.state.value.submitError)
    }
}
```

Adicionar o import `kotlinx.coroutines.flow.first` e `kotlinx.coroutines.launch` no topo do arquivo (usados no teste de sucesso acima).

- [ ] **Step 2: Rodar os testes e confirmar que falham (classe ainda não existe)**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.QuickRefuelViewModelTest"`
Expected: FAIL — erro de compilação, `QuickRefuelViewModel`/`QuickRefuelUiState`/`QuickRefuelEffect` não existem.

- [ ] **Step 3: Criar `QuickRefuelViewModel.kt`**

```kotlin
package com.flowfuel.app.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.FieldError
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.CreateRefuelRequest
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ─── Modo de entrada do odômetro ──────────────────────────────────────────────

enum class OdometerInputMode { TRIP, ODOMETER }

// ─── Formulário de abastecimento ─────────────────────────────────────────────

/**
 * Estado do formulário de registro rápido.
 *
 * [odometer] armazena apenas dígitos representando décimos de km
 * (ex: "1000005" = 100.000,5 km). A [OdometerVisualTransformation] formata
 * em tempo real no campo de texto.
 *
 * [totalPriceRaw] armazena apenas dígitos (ex: "24944" = R$ 249,44).
 * A transformação visual formata em tempo real no campo de texto.
 *
 * [refuelType] é `null` para veículos de combustão/elétrico puro (inferido
 * automaticamente). Para híbridos o usuário deve escolher "FUEL" ou "ELECTRIC".
 */
data class RefuelFormState(
    val odometer: String = "",   // somente dígitos, representa décimos de km
    val liters: String = "",
    val totalPriceRaw: String = "",   // somente dígitos, representa centavos
    val fullTank: Boolean = true,
    val refuelType: String? = null,
    val odometerInputMode: OdometerInputMode = OdometerInputMode.TRIP,
    val tripKm: String = "",
    val odometerError: Boolean = false,
    val litersError: Boolean = false,
    val totalPriceError: Boolean = false,
    val refuelTypeError: Boolean = false,
    val tripKmError: Boolean = false,
    val serverErrors: List<FieldError>? = null,
) {
    /** Odômetro em km como Double (décimos → km). Ex: "1000005" → 100000.5 */
    val odometerDouble: Double get() = (odometer.toLongOrNull() ?: 0L) / 10.0

    /** Valor em centavos para cálculos internos. */
    val totalPriceCents: Long get() = totalPriceRaw.toLongOrNull() ?: 0L

    /** Valor como Double para enviar à API. */
    val totalPriceDouble: Double get() = totalPriceCents / 100.0

    fun canSubmit(isHybrid: Boolean): Boolean {
        val inputValid = when (odometerInputMode) {
            OdometerInputMode.TRIP ->
                tripKm.isNotBlank() &&
                tripKm.replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } == true
            OdometerInputMode.ODOMETER ->
                odometer.isNotBlank()
        }
        return inputValid
            && liters.isNotBlank()
            && totalPriceCents > 0
            && (!isHybrid || refuelType != null)
    }
}

// ─── Estado e efeitos ─────────────────────────────────────────────────────────

data class QuickRefuelUiState(
    val showSheet: Boolean = false,
    /** Veículo ativo, buscado ao abrir o sheet; null enquanto carrega. */
    val vehicle: ActiveVehicleData? = null,
    val form: RefuelFormState = RefuelFormState(),
    val isSubmitting: Boolean = false,
    val submitError: AppError? = null,
)

sealed interface QuickRefuelEffect {
    data object RefuelRegistered : QuickRefuelEffect
}

/**
 * Dono do sheet de registro rápido de abastecimento. Vive no escopo do
 * [com.flowfuel.app.navigation.MainContainerScreen] (não em [HomeViewModel]),
 * para que o FAB global consiga abri-lo a partir de qualquer aba, não só da Home.
 */
@HiltViewModel
class QuickRefuelViewModel @Inject constructor(
    private val getActiveVehicle: GetActiveVehicleUseCase,
    private val createRefuel: CreateRefuelUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickRefuelUiState())
    val state: StateFlow<QuickRefuelUiState> = _state.asStateFlow()

    private val _effects = Channel<QuickRefuelEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun openSheet() {
        _state.update { it.copy(showSheet = true) }
        viewModelScope.launch {
            when (val result = getActiveVehicle()) {
                is AppResult.Success -> _state.update { it.copy(vehicle = result.value) }
                is AppResult.Failure ->
                    Timber.e("QuickRefuel: falha ao buscar veículo ativo → ${result.error}")
            }
        }
    }

    fun closeSheet() = _state.update {
        it.copy(showSheet = false, form = RefuelFormState(), submitError = null)
    }

    // ─── Handlers do formulário ───────────────────────────────────────────────

    fun onOdometerChange(v: String) = _state.update {
        it.copy(form = it.form.copy(
            odometer = v.filter(Char::isDigit),
            odometerError = false,
            serverErrors = null,
        ))
    }

    fun onLitersChange(v: String) = _state.update {
        it.copy(form = it.form.copy(
            liters = v.filter { c -> c.isDigit() || c == ',' || c == '.' },
            litersError = false,
            serverErrors = null,
        ))
    }

    /** Recebe apenas dígitos; armazena em centavos. Ex: "24944" = R$ 249,44 */
    fun onTotalPriceInput(digits: String) {
        val cleaned = digits.filter(Char::isDigit).trimStart('0').ifEmpty { "" }
        _state.update {
            it.copy(form = it.form.copy(
                totalPriceRaw = cleaned.take(7),   // max R$ 99.999,99
                totalPriceError = false,
                serverErrors = null,
            ))
        }
    }

    fun onFullTankToggle(checked: Boolean) = _state.update {
        it.copy(form = it.form.copy(fullTank = checked))
    }

    fun onRefuelTypeChange(type: String) = _state.update {
        it.copy(form = it.form.copy(refuelType = type, refuelTypeError = false, serverErrors = null))
    }

    fun onOdometerInputModeChange(mode: OdometerInputMode) = _state.update {
        it.copy(form = it.form.copy(
            odometerInputMode = mode,
            odometer          = "",
            tripKm            = "",
            odometerError     = false,
            tripKmError       = false,
            serverErrors      = null,
        ))
    }

    fun onTripKmChange(v: String) = _state.update {
        it.copy(form = it.form.copy(
            tripKm       = v.filter { c -> c.isDigit() || c == ',' || c == '.' },
            tripKmError  = false,
            serverErrors = null,
        ))
    }

    // ─── Submissão do formulário ──────────────────────────────────────────────

    fun submitRefuel() {
        val s = _state.value
        val form = s.form
        val vehicle = s.vehicle ?: return
        val isHybrid = vehicle.energyType.equals("HYBRID", ignoreCase = true)

        val isTripMode = form.odometerInputMode == OdometerInputMode.TRIP

        val odometerInvalid = !isTripMode && form.odometer.isBlank()
        val tripInvalid = isTripMode && (
            form.tripKm.isBlank() ||
            form.tripKm.replace(',', '.').toDoubleOrNull()?.let { it <= 0.0 } != false
        )
        val litersInvalid = form.liters.isBlank()
            || form.liters.replace(',', '.').toDoubleOrNull() == null
        val priceInvalid      = form.totalPriceCents == 0L
        val refuelTypeInvalid = isHybrid && form.refuelType == null

        if (odometerInvalid || tripInvalid || litersInvalid || priceInvalid || refuelTypeInvalid) {
            _state.update {
                it.copy(form = it.form.copy(
                    odometerError   = odometerInvalid,
                    tripKmError     = tripInvalid,
                    litersError     = litersInvalid,
                    totalPriceError = priceInvalid,
                    refuelTypeError = refuelTypeInvalid,
                ))
            }
            return
        }

        val resolvedRefuelType = when {
            isHybrid -> form.refuelType
            vehicle.energyType.equals("ELECTRIC", ignoreCase = true) -> "ELECTRIC"
            else -> "FUEL"
        }

        val odometer = if (isTripMode)
            vehicle.currentKm.toDouble() + form.tripKm.replace(',', '.').toDouble()
        else
            form.odometerDouble

        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            val result = createRefuel(
                CreateRefuelRequest(
                    vehicleId  = vehicle.id,
                    odometer   = odometer,
                    liters     = form.liters.replace(',', '.').toDouble(),
                    totalPrice = form.totalPriceDouble,
                    fullTank   = form.fullTank,
                    refuelType = resolvedRefuelType,
                )
            )
            when (result) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            showSheet    = false,
                            form         = RefuelFormState(),
                        )
                    }
                    _effects.send(QuickRefuelEffect.RefuelRegistered)
                }
                is AppResult.Failure -> {
                    Timber.e("submitRefuel: ${result.error}")
                    val apiErr     = result.error as? AppError.Api
                    val fieldErrors = apiErr?.takeIf { it.code == "VALIDATION_FAILED" }?.fieldErrors
                    if (!fieldErrors.isNullOrEmpty()) {
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                form         = it.form.copy(serverErrors = fieldErrors),
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(isSubmitting = false, submitError = result.error)
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.QuickRefuelViewModelTest"`
Expected: PASS (todos os testes).

- [ ] **Step 5: Remover `RefuelFormState`/`OdometerInputMode` e os campos de abastecimento de `HomeUiState.kt`**

Remover de `HomeUiState.kt`: o `enum class OdometerInputMode`, o `data class RefuelFormState` inteiro (migraram para `QuickRefuelViewModel.kt`, mesmo pacote — nenhum import extra necessário em `QuickRefuelBottomSheet.kt`, que já não importava essas classes explicitamente), e de `HomeUiState`: `showRefuelSheet`, `refuelForm`, `isSubmittingRefuel`, `submitError`. E de `HomeEffect`: `RefuelRegistered`.

`HomeUiState.kt` resultante (seções alteradas):

```kotlin
data class HomeUiState(
    val screenState: HomeScreenState = HomeScreenState.Loading,
    // ── Pull-to-refresh ────────────────────────────────────────────────────
    /** true somente durante um pull-to-refresh; não afeta o screenState. */
    val isRefreshing: Boolean = false,
    // ── Seletor de veículo ─────────────────────────────────────────────────
    val showVehicleSwitcher: Boolean = false,
    val vehicleSwitcherState: VehicleSwitcherState = VehicleSwitcherState.Idle,
    // ── Confirmação de logout ──────────────────────────────────────────────
    val showLogoutDialog: Boolean = false,
    // ── Data de licenciamento (lembrete de manutenção) ─────────────────────
    val showLicensingDueDatePicker: Boolean = false,
    // ── Diálogo "Sobre" ─────────────────────────────────────────────────────
    val showAboutDialog: Boolean = false,
)

// ─── Efeitos de navegação ─────────────────────────────────────────────────────

sealed interface HomeEffect {
    data object NavigateToLogin : HomeEffect
}
```

Remover também o import `com.flowfuel.app.core.domain.FieldError` de `HomeUiState.kt` — ficava órfão sem `RefuelFormState`, que era o único usuário.

- [ ] **Step 6: Remover a lógica de abastecimento de `HomeViewModel.kt`**

Remover de `HomeViewModel.kt`:
- Os parâmetros de construtor `createRefuel: CreateRefuelUseCase` (não é mais usado aqui).
- `submitError = null` das chamadas `_state.update` em `load()` e `refresh()` (ficam só `screenState = ...`/`isRefreshing = ...`).
- Toda a seção `// ─── Bottom Sheet ───...` e `// ─── Handlers do formulário ───...` (`openRefuelSheet`, `closeRefuelSheet`, `onOdometerChange`, `onLitersChange`, `onTotalPriceInput`, `onFullTankToggle`, `onRefuelTypeChange`, `onOdometerInputModeChange`, `onTripKmChange`).
- Toda a seção `// ─── Submissão do formulário ───...` (`submitRefuel`, `clearSubmitError`).

Import a remover: `com.flowfuel.app.feature.home.domain.model.CreateRefuelRequest`, `com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase`.

- [ ] **Step 7: Atualizar `HomeViewModelTest.kt` — remover testes migrados, atualizar construção do ViewModel**

Remover os testes das seções `// ── onOdometerInputModeChange ...`, `// ── onTripKmChange ...` e `// ── submitRefuel em modo TRIP ...` (migraram para `QuickRefuelViewModelTest.kt` no Step 1).

Remover o mock `private val createRefuel: CreateRefuelUseCase = mockk()` e seu import.

Atualizar a construção do ViewModel em `setUp()`:

```kotlin
        viewModel = HomeViewModel(
            getActiveVehicle, getDashboard, logout,
            sessionStore, getVehicles, setActiveVehicle, stationsPrefetcher, getVehicleEventsTotal,
            getFinancialSummary, getRecentActivity, getUpcomingMaintenance, maintenancePrefsStore,
        )
```

- [ ] **Step 8: Rodar `HomeViewModelTest` e confirmar que passa**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.HomeViewModelTest"`
Expected: PASS (todos os testes restantes).

- [ ] **Step 9: Remover FAB/snackbar/sheet/spacer de `HomeScreen.kt`; adicionar `onOpenRefuelSheet`**

Na assinatura de `HomeScreen`, adicionar `onOpenRefuelSheet: () -> Unit = {}` e remover `openRefuelSheet: Boolean = false` e `onRefuelSheetOpened: () -> Unit = {}`:

```kotlin
@Composable
fun HomeScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToAddVehicle: () -> Unit,
    onNavigateToMaintenanceEventCreate: (vehicleId: Int, category: EventCategory) -> Unit = { _, _ -> },
    onOpenRefuelSheet: () -> Unit = {},
    refreshTrigger: Boolean = false,
    onRefreshConsumed: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
```

Remover o `LaunchedEffect(openRefuelSheet) { ... }` inteiro.

No `LaunchedEffect(viewModel) { viewModel.effects.collectLatest { ... } }`, remover o `when`-branch `HomeEffect.RefuelRegistered -> ...`, deixando só `HomeEffect.NavigateToLogin -> onNavigateToLogin()`.

Remover `val snackbarHostState = remember { SnackbarHostState() }` e, no `Scaffold(...)`, remover `snackbarHost = { FFSnackbarHost(snackbarHostState) }` e o parâmetro `floatingActionButton = { ... FFFab(...) ... }` inteiro.

Na chamada de `HomeContent(...)` dentro do `is HomeScreenState.Success -> ...`, trocar `onRegisterRefuel = viewModel::openRefuelSheet` por `onRegisterRefuel = onOpenRefuelSheet`, e adicionar `onInfoClick = viewModel::openAboutDialog` (já feito na Task 2 — aqui só confirma que a assinatura de `HomeContent` já recebe `onInfoClick`).

Remover inteiramente o bloco `if (state.showRefuelSheet) { QuickRefuelBottomSheet(...) }`.

No final de `HomeContent`, remover o comentário e a linha `item { Spacer(Modifier.height(80.dp)) }` — não é mais necessário compensar um FAB flutuante.

Remover os imports agora não usados: `com.flowfuel.app.core.designsystem.components.FFFab`, `com.flowfuel.app.core.designsystem.components.FFSnackbarHost`, `com.flowfuel.app.core.designsystem.components.FFSnackbarKind`, `com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals`, `androidx.compose.material3.SnackbarDuration`, `androidx.compose.material3.SnackbarHostState`, `androidx.compose.material.icons.filled.LocalGasStation`, `androidx.compose.foundation.layout.Spacer`, `androidx.compose.foundation.layout.height` (confirmar se `height` ainda é usado em outro lugar do arquivo antes de remover — não é).

- [ ] **Step 10: Atualizar a chamada de `HomeScreen` em `MainContainerScreen.kt`**

No bloco `composable(MainDestinations.HOME) { ... }`, remover `openRefuelSheet = openRefuelSheet` e `onRefuelSheetOpened = { openRefuelSheet = false }`. Remover também a declaração agora morta `var openRefuelSheet by remember { mutableStateOf(false) }` (seu único uso restante era este). O parâmetro `onOpenRefuelSheet` fica sem wiring real por enquanto (usa o padrão `{}` de `HomeScreen`) — a Task 6 conecta.

```kotlin
            // ── Home ──────────────────────────────────────────────────────────
            composable(MainDestinations.HOME) {
                HomeScreen(
                    onNavigateToLogin      = onNavigateToLogin,
                    onNavigateToAddVehicle = onNavigateToAddVehicle,
                    onNavigateToMaintenanceEventCreate = onNavigateToMaintenanceEventCreate,
                    refreshTrigger         = homeNeedsRefresh,
                    onRefreshConsumed      = onHomeRefreshConsumed,
                )
            }
```

- [ ] **Step 11: Compilar**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL — "Registrar abastecimento" fica temporariamente inacessível em toda a Home (FAB removido, CTA do estado vazio usa `onOpenRefuelSheet` que ainda não tem wiring real); volta a funcionar na Task 6.

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/QuickRefuelViewModel.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeUiState.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt \
        app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt \
        app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt \
        app/src/test/java/com/flowfuel/app/feature/home/presentation/QuickRefuelViewModelTest.kt \
        app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt
git commit -m "refactor(home): extrai QuickRefuelViewModel; Home deixa de ser dono do sheet de abastecimento"
```

---

### Task 6: MainContainerScreen — FAB global contextual + integração final

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt`

**Interfaces:**
- Consumes: `FFBottomBar(..., floatingActionButton = ...)` (Task 1), `QuickRefuelViewModel` (Task 5), `VehicleEventsScreen(..., triggerCreate, onCreateTriggerConsumed)` (Task 3), `HomeScreen(..., onOpenRefuelSheet)` (Task 5).

- [ ] **Step 1: Reescrever `MainContainerScreen.kt`**

Conteúdo completo do arquivo:

```kotlin
package com.flowfuel.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.flowfuel.app.core.designsystem.components.FFBottomBar
import com.flowfuel.app.core.designsystem.components.FFBottomItem
import com.flowfuel.app.core.designsystem.components.FFBottomSheet
import com.flowfuel.app.core.designsystem.components.FFFab
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.auth.presentation.profile.ProfileScreen
import com.flowfuel.app.feature.history.presentation.HistoryScreen
import com.flowfuel.app.feature.home.presentation.HomeScreen
import com.flowfuel.app.feature.home.presentation.QuickRefuelBottomSheet
import com.flowfuel.app.feature.home.presentation.QuickRefuelEffect
import com.flowfuel.app.feature.home.presentation.QuickRefuelViewModel
import com.flowfuel.app.feature.station.presentation.list.StationsScreen
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.presentation.list.VehicleEventsScreen
import kotlinx.coroutines.flow.collectLatest

/**
 * Container principal autenticado.
 *
 * Responsável por:
 * - Exibir a [FFBottomBar] com as 5 abas do app e o FAB global docado
 *   (contextual: "Registrar abastecimento" em Home/Histórico/Postos/Perfil,
 *   "Novo evento" em Eventos).
 * - Hospedar o [NavHost] aninhado das abas.
 * - Hospedar o [QuickRefuelBottomSheet] (via [QuickRefuelViewModel]), para que
 *   o FAB abra o sheet a partir de qualquer aba, sem trocar de aba.
 * - Repassar callbacks de navegação "para fora" (login, add vehicle)
 *   ao NavHost raiz via lambdas, sem acoplamento direto.
 *
 * As abas preservam estado entre si via [launchSingleTop] + [saveState] /
 * [restoreState] + [popUpTo(findStartDestination)].
 */
@Composable
fun MainContainerScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToAddVehicle: () -> Unit,
    onNavigateToEventCreate: (vehicleId: Int) -> Unit = {},
    onNavigateToMaintenanceEventCreate: (vehicleId: Int, category: EventCategory) -> Unit = { _, _ -> },
    onNavigateToEventDetails: (eventId: Int) -> Unit = {},
    onNavigateToRefuelDetails: (refuelId: Int) -> Unit = {},
    onNavigateToVehicles: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToChangePassword: () -> Unit = {},
    passwordChanged: Boolean = false,
    onPasswordChangedConsumed: () -> Unit = {},
    profileUpdated: Boolean = false,
    onProfileUpdatedConsumed: () -> Unit = {},
    historyNeedsRefresh: Boolean = false,
    onHistoryRefreshConsumed: () -> Unit = {},
    homeNeedsRefresh: Boolean = false,
    onHomeRefreshConsumed: () -> Unit = {},
    tabEventCreated: Boolean = false,
    onTabEventCreatedConsumed: () -> Unit = {},
    tabEventDeleted: Int = -1,
    onTabEventDeletedConsumed: () -> Unit = {},
    tabEventUpdated: Boolean = false,
    onTabEventUpdatedConsumed: () -> Unit = {},
    quickRefuelViewModel: QuickRefuelViewModel = hiltViewModel(),
) {
    val innerNavController = rememberNavController()
    val backStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val quickRefuelState by quickRefuelViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var triggerEventCreate by remember { mutableStateOf(false) }
    // Sinalizadores locais de "houve um abastecimento registrado agora", um por
    // aba consumidora — cada um só é resetado quando a própria aba (se e quando
    // estiver ativa) processa o refresh, mesmo padrão de homeNeedsRefresh/
    // historyNeedsRefresh vindos de fora (ver FlowFuelNavHost.kt).
    var homeRefuelPending by remember { mutableStateOf(false) }
    var historyRefuelPending by remember { mutableStateOf(false) }

    LaunchedEffect(quickRefuelViewModel) {
        quickRefuelViewModel.effects.collectLatest { effect ->
            when (effect) {
                QuickRefuelEffect.RefuelRegistered -> {
                    homeRefuelPending = true
                    historyRefuelPending = true
                    snackbarHostState.showSnackbar(
                        FFSnackbarVisuals(
                            message = "Abastecimento registrado com sucesso!",
                            kind = FFSnackbarKind.Success,
                            duration = SnackbarDuration.Short,
                        ),
                    )
                }
            }
        }
    }

    val tabs = remember {
        listOf(
            FFBottomItem(
                route        = MainDestinations.HOME,
                label        = "Home",
                icon         = Icons.Outlined.Home,
                selectedIcon = Icons.Filled.Home,
            ),
            FFBottomItem(
                route        = MainDestinations.HISTORY,
                label        = "Histórico",
                icon         = Icons.Outlined.History,
                selectedIcon = Icons.Filled.History,
            ),
            FFBottomItem(
                route        = MainDestinations.STATIONS,
                label        = "Postos",
                icon         = Icons.Outlined.LocalGasStation,
                selectedIcon = Icons.Filled.LocalGasStation,
            ),
            FFBottomItem(
                route        = MainDestinations.EVENTS,
                label        = "Eventos",
                icon         = Icons.AutoMirrored.Outlined.Assignment,
                selectedIcon = Icons.AutoMirrored.Filled.Assignment,
            ),
            FFBottomItem(
                route        = MainDestinations.PROFILE,
                label        = "Perfil",
                icon         = Icons.Outlined.Person,
                selectedIcon = Icons.Filled.Person,
            ),
        )
    }

    Scaffold(
        // O Scaffold raiz consome os insets do sistema (nav bar, status bar).
        // As telas internas recebem contentWindowInsets = WindowInsets(0) via
        // consumeWindowInsets no NavHost para não duplicar o padding.
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
        bottomBar = {
            FFBottomBar(
                items        = tabs,
                currentRoute = currentRoute,
                onSelect     = { item ->
                    innerNavController.navigate(item.route) {
                        // Mantém o start destination na back stack e salva estado
                        // de cada aba para restaurar quando voltar a ela.
                        popUpTo(innerNavController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Evita múltiplas cópias da mesma aba na back stack
                        launchSingleTop = true
                        // Restaura o estado salvo ao re-selecionar a aba
                        restoreState = true
                    }
                },
                floatingActionButton = {
                    if (currentRoute == MainDestinations.EVENTS) {
                        FFFab(
                            icon               = Icons.Default.Add,
                            contentDescription = "Novo evento",
                            text               = "Novo Evento",
                            onClick            = { triggerEventCreate = true },
                        )
                    } else {
                        FFFab(
                            icon               = Icons.Default.LocalGasStation,
                            contentDescription = "Registrar abastecimento",
                            onClick            = quickRefuelViewModel::openSheet,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController   = innerNavController,
            startDestination = MainDestinations.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Informa ao Compose que os insets já foram consumidos pelo Scaffold
                // externo, evitando padding duplo em Scaffolds filhos.
                .consumeWindowInsets(innerPadding),
            enterTransition   = { fadeIn(animationSpec = tween(200)) },
            exitTransition    = { fadeOut(animationSpec = tween(150)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition  = { fadeOut(animationSpec = tween(150)) },
        ) {
            // ── Home ──────────────────────────────────────────────────────────
            composable(MainDestinations.HOME) {
                HomeScreen(
                    onNavigateToLogin      = onNavigateToLogin,
                    onNavigateToAddVehicle = onNavigateToAddVehicle,
                    onNavigateToMaintenanceEventCreate = onNavigateToMaintenanceEventCreate,
                    onOpenRefuelSheet      = quickRefuelViewModel::openSheet,
                    refreshTrigger         = homeNeedsRefresh || homeRefuelPending,
                    onRefreshConsumed      = {
                        onHomeRefreshConsumed()
                        homeRefuelPending = false
                    },
                )
            }

            // ── Histórico ─────────────────────────────────────────────────────
            composable(MainDestinations.HISTORY) {
                HistoryScreen(
                    onNavigateToLogin        = onNavigateToLogin,
                    onNavigateToDetails      = onNavigateToRefuelDetails,
                    historyNeedsRefresh      = historyNeedsRefresh || historyRefuelPending,
                    onHistoryRefreshConsumed = {
                        onHistoryRefreshConsumed()
                        historyRefuelPending = false
                    },
                )
            }

            // ── Postos ────────────────────────────────────────────────────────
            composable(MainDestinations.STATIONS) {
                StationsScreen(onNavigateToLogin = onNavigateToLogin)
            }

            // ── Eventos ───────────────────────────────────────────────────────
            composable(MainDestinations.EVENTS) {
                VehicleEventsScreen(
                    onBack = null,
                    onNavigateToCreate = onNavigateToEventCreate,
                    onNavigateToDetails = onNavigateToEventDetails,
                    onNavigateToRefuelDetails = onNavigateToRefuelDetails,
                    onNavigateToLogin = onNavigateToLogin,
                    eventCreated = tabEventCreated,
                    onEventCreatedConsumed = onTabEventCreatedConsumed,
                    eventDeleted = tabEventDeleted,
                    onEventDeletedConsumed = onTabEventDeletedConsumed,
                    eventUpdated = tabEventUpdated,
                    onEventUpdatedConsumed = onTabEventUpdatedConsumed,
                    triggerCreate = triggerEventCreate,
                    onCreateTriggerConsumed = { triggerEventCreate = false },
                )
            }

            // ── Perfil ────────────────────────────────────────────────────────
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
        }
    }

    if (quickRefuelState.showSheet) {
        val vehicle = quickRefuelState.vehicle
        if (vehicle == null) {
            FFBottomSheet(onDismiss = quickRefuelViewModel::closeSheet) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(FFTheme.spacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            QuickRefuelBottomSheet(
                form                      = quickRefuelState.form,
                isSubmitting              = quickRefuelState.isSubmitting,
                submitError               = quickRefuelState.submitError,
                energyType                = vehicle.energyType,
                onOdometerInputModeChange = quickRefuelViewModel::onOdometerInputModeChange,
                onTripKmChange            = quickRefuelViewModel::onTripKmChange,
                onOdometerChange          = quickRefuelViewModel::onOdometerChange,
                onLitersChange            = quickRefuelViewModel::onLitersChange,
                onTotalPriceInput         = quickRefuelViewModel::onTotalPriceInput,
                onFullTankToggle          = quickRefuelViewModel::onFullTankToggle,
                onRefuelTypeChange        = quickRefuelViewModel::onRefuelTypeChange,
                onSubmit                  = quickRefuelViewModel::submitRefuel,
                onDismiss                 = quickRefuelViewModel::closeSheet,
            )
        }
    }
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Rodar a suíte de testes completa**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL — todos os testes (incluindo `HomeViewModelTest`, `QuickRefuelViewModelTest` e os já existentes de outras features) passam.

- [ ] **Step 4: Verificação manual no emulador**

Usar a skill `run-android-emulator` (`.claude/skills/run-android-emulator`) para instalar e abrir o app. Conferir:
- O FAB aparece docado na bottom bar em todas as 5 abas.
- Em Home/Histórico/Postos/Perfil, o FAB abre o sheet "Registrar abastecimento" (inclusive fora da Home, sem trocar de aba).
- Em Eventos, o FAB abre o fluxo de criação de evento (mesmo comportamento de antes).
- Depois de registrar um abastecimento a partir de uma aba diferente de Home, navegar para Home e Histórico e confirmar que ambos refletem o novo abastecimento.
- O ícone "Sobre" no `VehicleHeader` da Home abre o diálogo com logo, nome e versão.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt
git commit -m "feat(navigation): FAB global contextual na bottom bar, docado via BottomAppBar"
```

---

## Self-Review

**1. Cobertura da spec:**
- Ícone "Sobre" no `VehicleHeader`, diálogo com logo/nome/versão → Task 2.
- `FFBottomBar` → `BottomAppBar`, 5 abas mantidas → Task 1.
- FAB contextual por rota, dono em `MainContainerScreen` → Task 6.
- `QuickRefuelViewModel` compartilhado, sheet hospedado fora da Home → Task 5 (extração) + Task 6 (hospedagem).
- Atualização cross-aba via `refreshTrigger`/`historyNeedsRefresh` reaproveitados → Task 6.
- Limpeza: FAB próprio removido de Home (Task 5), Histórico (Task 4) e Eventos (Task 3).
- Testes: `QuickRefuelViewModelTest` novo, `HomeViewModelTest` atualizado → Task 2 e Task 5.

**2. Placeholders:** nenhum "TBD"/"implementar depois" — todo código de cada step está completo.

**3. Consistência de tipos:** `QuickRefuelUiState.form`/`RefuelFormState`/`OdometerInputMode` usados de forma consistente entre `QuickRefuelViewModel.kt` (Task 5), `QuickRefuelViewModelTest.kt` (Task 5) e a chamada de `QuickRefuelBottomSheet` em `MainContainerScreen.kt` (Task 6). `HomeScreen`'s `onOpenRefuelSheet` (Task 5) é consumido em `MainContainerScreen` (Task 6) com o mesmo nome. `VehicleEventsScreen`'s `triggerCreate`/`onCreateTriggerConsumed` (Task 3) são consumidos em `MainContainerScreen` (Task 6) com os mesmos nomes.
