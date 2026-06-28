# Odômetro / Percurso Toggle — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adicionar toggle Percurso/Odômetro no formulário de abastecimento, com "Percurso" como padrão, calculando o odômetro absoluto via `currentKm + tripKm` antes de enviar à API.

**Architecture:** Três camadas isoladas — (1) estado puro em `RefuelFormState`, (2) lógica no `HomeViewModel`, (3) UI no `QuickRefuelBottomSheet`. O backend não muda: continua recebendo odômetro absoluto.

**Tech Stack:** Kotlin, Jetpack Compose, MockK, Turbine, JUnit 4, Coroutines Test

## Global Constraints

- Não alterar camada de dados (repositórios, DTOs, API)
- Não alterar outras telas (EditRefuel, History, Details)
- Padrão de teste: `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [33])` + `UnconfinedTestDispatcher` (ver `RegisterViewModelTest`)
- TDD: escrever teste falhando → implementar → teste passando → commit

---

## File Map

| Arquivo | Ação | Responsabilidade |
|---------|------|-----------------|
| `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeUiState.kt` | Modificar | Enum `OdometerInputMode` + novos campos em `RefuelFormState` + `canSubmit` atualizado |
| `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt` | Modificar | Handlers `onOdometerInputModeChange`, `onTripKmChange`; `submitRefuel` com modo trip |
| `app/src/main/java/com/flowfuel/app/feature/home/presentation/QuickRefuelBottomSheet.kt` | Modificar | Toggle FilterChips + campo dinâmico Percurso/Odômetro |
| `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt` | Modificar | Passar 2 novos callbacks ao `QuickRefuelBottomSheet` |
| `app/src/test/java/com/flowfuel/app/feature/home/presentation/RefuelFormStateTest.kt` | Criar | Testes unitários puros de `RefuelFormState.canSubmit` |
| `app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt` | Criar | Testes do ViewModel: handlers e `submitRefuel` no modo trip |

---

## Task 1: Estado — `RefuelFormState` + testes unitários

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeUiState.kt`
- Create: `app/src/test/java/com/flowfuel/app/feature/home/presentation/RefuelFormStateTest.kt`

**Interfaces:**
- Produces: `enum class OdometerInputMode { TRIP, ODOMETER }` (usado pela Task 2 e Task 3)
- Produces: `RefuelFormState.odometerInputMode`, `RefuelFormState.tripKm`, `RefuelFormState.tripKmError`
- Produces: `RefuelFormState.canSubmit(isHybrid)` atualizado

---

- [ ] **Step 1: Escrever testes falhando para `RefuelFormState`**

Criar o arquivo `app/src/test/java/com/flowfuel/app/feature/home/presentation/RefuelFormStateTest.kt`:

```kotlin
package com.flowfuel.app.feature.home.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class RefuelFormStateTest {

    // ── Defaults ──────────────────────────────────────────────────────────────

    @Test
    fun `default odometerInputMode is TRIP`() {
        assertEquals(OdometerInputMode.TRIP, RefuelFormState().odometerInputMode)
    }

    // ── canSubmit — modo TRIP ─────────────────────────────────────────────────

    @Test
    fun `canSubmit TRIP true when tripKm valid liters and price set`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.TRIP,
            tripKm = "310,6",
            liters = "21,29",
            totalPriceRaw = "14842",
        )
        assertTrue(form.canSubmit(isHybrid = false))
    }

    @Test
    fun `canSubmit TRIP false when tripKm blank`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.TRIP,
            tripKm = "",
            liters = "21,29",
            totalPriceRaw = "14842",
        )
        assertFalse(form.canSubmit(isHybrid = false))
    }

    @Test
    fun `canSubmit TRIP false when tripKm is zero`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.TRIP,
            tripKm = "0",
            liters = "21,29",
            totalPriceRaw = "14842",
        )
        assertFalse(form.canSubmit(isHybrid = false))
    }

    @Test
    fun `canSubmit TRIP false when tripKm is negative`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.TRIP,
            tripKm = "-10",
            liters = "21,29",
            totalPriceRaw = "14842",
        )
        assertFalse(form.canSubmit(isHybrid = false))
    }

    @Test
    fun `canSubmit TRIP false when liters blank`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.TRIP,
            tripKm = "310,6",
            liters = "",
            totalPriceRaw = "14842",
        )
        assertFalse(form.canSubmit(isHybrid = false))
    }

    @Test
    fun `canSubmit TRIP false when price zero`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.TRIP,
            tripKm = "310,6",
            liters = "21,29",
            totalPriceRaw = "",
        )
        assertFalse(form.canSubmit(isHybrid = false))
    }

    @Test
    fun `canSubmit TRIP hybrid false when refuelType null`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.TRIP,
            tripKm = "310,6",
            liters = "21,29",
            totalPriceRaw = "14842",
            refuelType = null,
        )
        assertFalse(form.canSubmit(isHybrid = true))
    }

    @Test
    fun `canSubmit TRIP hybrid true when refuelType set`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.TRIP,
            tripKm = "310,6",
            liters = "21,29",
            totalPriceRaw = "14842",
            refuelType = "FUEL",
        )
        assertTrue(form.canSubmit(isHybrid = true))
    }

    // ── canSubmit — modo ODOMETER ─────────────────────────────────────────────

    @Test
    fun `canSubmit ODOMETER true when odometer liters and price set`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.ODOMETER,
            odometer = "672700",
            liters = "21,29",
            totalPriceRaw = "14842",
        )
        assertTrue(form.canSubmit(isHybrid = false))
    }

    @Test
    fun `canSubmit ODOMETER false when odometer blank`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.ODOMETER,
            odometer = "",
            liters = "21,29",
            totalPriceRaw = "14842",
        )
        assertFalse(form.canSubmit(isHybrid = false))
    }
}
```

- [ ] **Step 2: Rodar testes — confirmar falha de compilação (tipos não existem ainda)**

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.RefuelFormStateTest" --info 2>&1 | Select-String "ERROR|FAILED|error:"
```

Esperado: erro de compilação — `OdometerInputMode` não existe.

- [ ] **Step 3: Implementar as mudanças em `HomeUiState.kt`**

Adicionar o enum **antes** da `RefuelFormState` e atualizar os campos e o `canSubmit`. Arquivo completo da seção afetada:

```kotlin
// ─── Modo de entrada do odômetro ──────────────────────────────────────────────

enum class OdometerInputMode { TRIP, ODOMETER }

// ─── Formulário de abastecimento ─────────────────────────────────────────────

data class RefuelFormState(
    val odometer: String = "",
    val liters: String = "",
    val totalPriceRaw: String = "",
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
    val odometerDouble: Double get() = (odometer.toLongOrNull() ?: 0L) / 10.0
    val totalPriceCents: Long get() = totalPriceRaw.toLongOrNull() ?: 0L
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
```

- [ ] **Step 4: Rodar testes — confirmar que passam**

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.RefuelFormStateTest"
```

Esperado: `BUILD SUCCESSFUL` com 12 testes passando.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeUiState.kt
git add app/src/test/java/com/flowfuel/app/feature/home/presentation/RefuelFormStateTest.kt
git commit -m "feat: add OdometerInputMode enum and tripKm fields to RefuelFormState"
```

---

## Task 2: ViewModel — handlers + `submitRefuel` + testes

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt`
- Create: `app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `OdometerInputMode` (Task 1), `RefuelFormState.tripKm/tripKmError/odometerInputMode` (Task 1)
- Produces: `HomeViewModel.onOdometerInputModeChange(OdometerInputMode)` (usado na Task 3)
- Produces: `HomeViewModel.onTripKmChange(String)` (usado na Task 3)

---

- [ ] **Step 1: Escrever testes falhando para o ViewModel**

Criar `app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt`:

```kotlin
package com.flowfuel.app.feature.home.presentation

import app.cash.turbine.test
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.feature.auth.domain.usecase.LogoutUseCase
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.CreateRefuelRequest
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetDashboardUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehiclesUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.SetActiveVehicleUseCase
import io.mockk.capture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val getActiveVehicle: GetActiveVehicleUseCase = mockk()
    private val getDashboard: GetDashboardUseCase = mockk()
    private val createRefuel: CreateRefuelUseCase = mockk()
    private val logout: LogoutUseCase = mockk(relaxed = true)
    private val sessionStore: SessionStore = mockk(relaxed = true)
    private val getVehicles: GetVehiclesUseCase = mockk(relaxed = true)
    private val setActiveVehicle: SetActiveVehicleUseCase = mockk(relaxed = true)

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
    private val testDashboard = DashboardData(
        averageConsumption = null,
        consumptionUnit = null,
        totalSpent = 0.0,
        totalRefuels = 1,
        lastRefuelDate = null,
        lastRefuelEnergyAmount = null,
        lastRefuelAmount = null,
        lastRefuelEnergyUnit = null,
    )

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { sessionStore.activeVehicleIdFlow } returns flowOf(1)
        coEvery { getActiveVehicle() } returns AppResult.Success(testVehicle)
        coEvery { getDashboard(any()) } returns AppResult.Success(testDashboard)
        viewModel = HomeViewModel(
            getActiveVehicle, getDashboard, createRefuel, logout,
            sessionStore, getVehicles, setActiveVehicle,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── onOdometerInputModeChange ─────────────────────────────────────────────

    @Test
    fun `onOdometerInputModeChange to ODOMETER sets mode`() {
        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)
        assertEquals(OdometerInputMode.ODOMETER, viewModel.state.value.refuelForm.odometerInputMode)
    }

    @Test
    fun `onOdometerInputModeChange clears odometer and tripKm fields`() {
        viewModel.onOdometerChange("672700")
        viewModel.onTripKmChange("310,6")
        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)

        val form = viewModel.state.value.refuelForm
        assertEquals("", form.odometer)
        assertEquals("", form.tripKm)
    }

    @Test
    fun `onOdometerInputModeChange clears errors`() {
        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)

        val form = viewModel.state.value.refuelForm
        assertFalse(form.odometerError)
        assertFalse(form.tripKmError)
    }

    // ── onTripKmChange ────────────────────────────────────────────────────────

    @Test
    fun `onTripKmChange filters to digits comma and dot only`() {
        viewModel.onTripKmChange("310,6abc!@#")
        assertEquals("310,6", viewModel.state.value.refuelForm.tripKm)
    }

    @Test
    fun `onTripKmChange accepts dot as decimal separator`() {
        viewModel.onTripKmChange("310.6")
        assertEquals("310.6", viewModel.state.value.refuelForm.tripKm)
    }

    @Test
    fun `onTripKmChange clears tripKmError`() = runTest {
        // Trigger validation error first
        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")
        viewModel.submitRefuel()
        assertTrue(viewModel.state.value.refuelForm.tripKmError)

        viewModel.onTripKmChange("310,6")
        assertFalse(viewModel.state.value.refuelForm.tripKmError)
    }

    // ── submitRefuel em modo TRIP ─────────────────────────────────────────────

    @Test
    fun `submitRefuel TRIP mode sends currentKm plus tripKm as odometer`() = runTest {
        val requestSlot = slot<CreateRefuelRequest>()
        coEvery { createRefuel(capture(requestSlot)) } returns AppResult.Success(Unit)

        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onTripKmChange("310,6")
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        // 67270.0 + 310.6 = 67580.6
        assertEquals(67580.6, requestSlot.captured.odometer, 0.001)
    }

    @Test
    fun `submitRefuel TRIP mode with comma separator parses correctly`() = runTest {
        val requestSlot = slot<CreateRefuelRequest>()
        coEvery { createRefuel(capture(requestSlot)) } returns AppResult.Success(Unit)

        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onTripKmChange("310,6")
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        assertEquals(67580.6, requestSlot.captured.odometer, 0.001)
    }

    @Test
    fun `submitRefuel TRIP mode blank tripKm sets tripKmError and skips api`() = runTest {
        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        assertTrue(viewModel.state.value.refuelForm.tripKmError)
        coVerify(exactly = 0) { createRefuel(any()) }
    }

    @Test
    fun `submitRefuel TRIP mode zero tripKm sets tripKmError and skips api`() = runTest {
        viewModel.onOdometerInputModeChange(OdometerInputMode.TRIP)
        viewModel.onTripKmChange("0")
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        assertTrue(viewModel.state.value.refuelForm.tripKmError)
        coVerify(exactly = 0) { createRefuel(any()) }
    }

    @Test
    fun `submitRefuel ODOMETER mode uses odometerDouble as before`() = runTest {
        val requestSlot = slot<CreateRefuelRequest>()
        coEvery { createRefuel(capture(requestSlot)) } returns AppResult.Success(Unit)

        viewModel.onOdometerInputModeChange(OdometerInputMode.ODOMETER)
        viewModel.onOdometerChange("675800")   // 67580.0 km
        viewModel.onLitersChange("21,29")
        viewModel.onTotalPriceInput("14842")

        viewModel.submitRefuel()

        assertEquals(67580.0, requestSlot.captured.odometer, 0.001)
    }
}
```

- [ ] **Step 2: Rodar testes — confirmar falha (handlers não existem)**

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.HomeViewModelTest" 2>&1 | Select-String "ERROR|FAILED|Unresolved"
```

Esperado: erro de compilação — `onOdometerInputModeChange` e `onTripKmChange` não existem.

- [ ] **Step 3: Adicionar handlers em `HomeViewModel.kt`**

Adicionar após `onRefuelTypeChange` (linha ~170):

```kotlin
fun onOdometerInputModeChange(mode: OdometerInputMode) = _state.update {
    it.copy(refuelForm = it.refuelForm.copy(
        odometerInputMode = mode,
        odometer          = "",
        tripKm            = "",
        odometerError     = false,
        tripKmError       = false,
        serverErrors      = null,
    ))
}

fun onTripKmChange(v: String) = _state.update {
    it.copy(refuelForm = it.refuelForm.copy(
        tripKm       = v.filter { c -> c.isDigit() || c == ',' || c == '.' },
        tripKmError  = false,
        serverErrors = null,
    ))
}
```

- [ ] **Step 4: Atualizar `submitRefuel` em `HomeViewModel.kt`**

Substituir o bloco de validação e o `CreateRefuelRequest` dentro de `submitRefuel()`:

```kotlin
fun submitRefuel() {
    val s = _state.value
    val form = s.refuelForm
    val vehicle = (s.screenState as? HomeScreenState.Success)?.vehicle ?: return
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
            it.copy(refuelForm = it.refuelForm.copy(
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

    _state.update { it.copy(isSubmittingRefuel = true, submitError = null) }
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
                        isSubmittingRefuel = false,
                        showRefuelSheet    = false,
                        refuelForm         = RefuelFormState(),
                    )
                }
                _effects.send(HomeEffect.RefuelRegistered)
                load()
            }
            is AppResult.Failure -> {
                Timber.e("submitRefuel: ${result.error}")
                val apiErr     = result.error as? AppError.Api
                val fieldErrors = apiErr?.takeIf { it.code == "VALIDATION_FAILED" }?.fieldErrors
                if (!fieldErrors.isNullOrEmpty()) {
                    _state.update {
                        it.copy(
                            isSubmittingRefuel = false,
                            refuelForm         = it.refuelForm.copy(serverErrors = fieldErrors),
                        )
                    }
                } else {
                    _state.update {
                        it.copy(isSubmittingRefuel = false, submitError = result.error)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 5: Rodar testes — confirmar que passam**

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.HomeViewModelTest"
```

Esperado: `BUILD SUCCESSFUL` com 11 testes passando.

- [ ] **Step 6: Rodar também os testes da Task 1 para confirmar que não quebraram**

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.RefuelFormStateTest"
```

Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt
git add app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt
git commit -m "feat: add trip mode handlers and update submitRefuel to support odometer/trip toggle"
```

---

## Task 3: UI — `QuickRefuelBottomSheet` + wiring em `HomeScreen`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/QuickRefuelBottomSheet.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`

**Interfaces:**
- Consumes: `OdometerInputMode` (Task 1), `RefuelFormState.odometerInputMode/tripKm/tripKmError` (Task 1)
- Consumes: `HomeViewModel.onOdometerInputModeChange`, `HomeViewModel.onTripKmChange` (Task 2)

---

- [ ] **Step 1: Atualizar `QuickRefuelBottomSheet.kt`**

Adicionar dois parâmetros após `energyType`:

```kotlin
@Composable
fun QuickRefuelBottomSheet(
    form: RefuelFormState,
    isSubmitting: Boolean,
    submitError: AppError?,
    energyType: String,
    onOdometerInputModeChange: (OdometerInputMode) -> Unit,   // novo
    onTripKmChange: (String) -> Unit,                         // novo
    onOdometerChange: (String) -> Unit,
    onLitersChange: (String) -> Unit,
    onTotalPriceInput: (String) -> Unit,
    onFullTankToggle: (Boolean) -> Unit,
    onRefuelTypeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
)
```

Substituir o bloco do campo de odômetro (o `FFNumberField` com `label = "Odômetro (km)"`) pelo bloco abaixo:

```kotlin
// ── Toggle Percurso / Odômetro ────────────────────────────────────────────
Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
    FilterChip(
        selected = form.odometerInputMode == OdometerInputMode.TRIP,
        onClick  = { onOdometerInputModeChange(OdometerInputMode.TRIP) },
        label    = { Text("Percurso") },
    )
    FilterChip(
        selected = form.odometerInputMode == OdometerInputMode.ODOMETER,
        onClick  = { onOdometerInputModeChange(OdometerInputMode.ODOMETER) },
        label    = { Text("Odômetro") },
    )
}

if (form.odometerInputMode == OdometerInputMode.TRIP) {
    FFNumberField(
        value        = form.tripKm,
        onValueChange = onTripKmChange,
        label        = "Km percorridos",
        kind         = FFNumberKind.Decimal,
        errorText    = if (form.tripKmError) "Informe os km percorridos" else null,
        helper       = "Use vírgula ou ponto como separador decimal",
        imeAction    = ImeAction.Next,
    )
} else {
    FFNumberField(
        value        = form.odometer,
        onValueChange = onOdometerChange,
        label        = "Odômetro (km)",
        kind         = FFNumberKind.Odometer,
        errorText    = if (form.odometerError) "Informe a leitura do odômetro"
                       else form.serverErrors?.firstOrNull { it.field == "odometer" }?.message,
        imeAction    = ImeAction.Next,
    )
}
```

- [ ] **Step 2: Atualizar `HomeScreen.kt` — adicionar os dois novos callbacks**

No bloco `QuickRefuelBottomSheet(...)` (linha ~179), adicionar após `energyType = energyType`:

```kotlin
onOdometerInputModeChange = viewModel::onOdometerInputModeChange,
onTripKmChange = viewModel::onTripKmChange,
```

O bloco completo deve ficar:

```kotlin
QuickRefuelBottomSheet(
    form                      = state.refuelForm,
    isSubmitting              = state.isSubmittingRefuel,
    submitError               = state.submitError,
    energyType                = energyType,
    onOdometerInputModeChange = viewModel::onOdometerInputModeChange,
    onTripKmChange            = viewModel::onTripKmChange,
    onOdometerChange          = viewModel::onOdometerChange,
    onLitersChange            = viewModel::onLitersChange,
    onTotalPriceInput         = viewModel::onTotalPriceInput,
    onFullTankToggle          = viewModel::onFullTankToggle,
    onRefuelTypeChange        = viewModel::onRefuelTypeChange,
    onSubmit                  = viewModel::submitRefuel,
    onDismiss                 = viewModel::closeRefuelSheet,
)
```

- [ ] **Step 3: Build de verificação — confirmar que compila sem erros**

```
.\gradlew.bat :app:assembleDebug 2>&1 | Select-String "error:|BUILD FAILED|BUILD SUCCESSFUL" | Select-Object -Last 5
```

Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Rodar toda a suite de testes**

```
.\gradlew.bat :app:testDebugUnitTest 2>&1 | Select-String "tests were|BUILD"
```

Esperado: todos os testes passando, `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/QuickRefuelBottomSheet.kt
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt
git commit -m "feat: add percurso/odometro toggle to refuel bottom sheet, default to trip mode"
```
