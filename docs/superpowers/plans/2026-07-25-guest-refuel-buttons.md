# GuestVehicleScreen: separar "Abastecer" e "Registrar despesa" — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single "Registrar abastecimento/despesa" button on the guest's minimal home (`GuestVehicleScreen`) with two buttons — "Abastecer" and "Registrar despesa" — each opening the event-creation form with the correct `EventCategory` pre-selected (`FUEL` vs `OTHER`), instead of always defaulting to `OTHER`.

**Architecture:** A category value flows from a new `EventCategory` field on `GuestVehicleEffect.NavigateToCreateEvent`, through `GuestVehicleScreen`'s `onNavigateToCreateEvent` callback, through `MainContainerScreen`'s `onNavigateToGuestEventCreate` callback, to `FlowFuelNavHost`'s navigation call — mirroring the existing `(vehicleId, category)` pattern already used by `onNavigateToMaintenanceEventCreate` for the owner's maintenance flow. No backend or `CreateVehicleEventViewModel` changes: that screen already reads `category` from `SavedStateHandle` and already restricts guest categories to `FUEL, WASH, TIRES, OTHER`.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt ViewModels, JUnit4 + MockK + Turbine (`viewModel.effects.test { }`) for ViewModel tests, Robolectric.

## Global Constraints

- Windows environment: build/test commands use `.\gradlew.bat` (not `./gradlew`).
- Project has no Compose UI tests (`createComposeRule`) — verification of screen/navigation wiring is by full-module compilation + unit tests + manual reasoning, not automated UI tests. This is the established pattern (see `docs/superpowers/plans/2026-07-23-ffborrowedvehiclecard-avatar.md`).
- `EventCategory` lives at `com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory`.
- Spec: `docs/superpowers/specs/2026-07-25-guest-refuel-buttons-design.md`.

---

## File Structure

All four files already exist; this plan only modifies them:

- `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/guest/GuestVehicleViewModel.kt` — owns `GuestVehicleEffect.NavigateToCreateEvent` and the click handlers. Splits `onCreateEventClicked()` into `onRefuelClicked()` / `onExpenseClicked()`.
- `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/guest/GuestVehicleScreen.kt` — owns the two-button UI and the effect→callback bridge.
- `app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt` — owns the `onNavigateToGuestEventCreate` callback signature and wiring to `GuestVehicleScreen`.
- `app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt` — owns the actual `NavController.navigate(...)` call that builds the route string.
- `app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/guest/GuestVehicleViewModelTest.kt` — new tests for the two click handlers.

These four production files change together as one atomic unit: `GuestVehicleScreen.kt` calls `viewModel::onCreateEventClicked` directly by method reference, so removing that method without updating the screen breaks compilation of the whole module — there's no way to land this as independently-compilable sub-steps. This is reflected below as a single task with sequential implementation steps, verified once at the end by a full module compile + test run.

---

### Task 1: Split guest event-creation into "Abastecer" / "Registrar despesa" with pre-selected categories

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/guest/GuestVehicleViewModel.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/guest/GuestVehicleScreen.kt`
- Modify: `app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt:111,278`
- Modify: `app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt:716-718`
- Test: `app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/guest/GuestVehicleViewModelTest.kt`

**Interfaces:**
- Consumes: `EventCategory` enum (`com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory`, values include `FUEL`, `OTHER`) — already defined, no changes.
- Produces: `GuestVehicleEffect.NavigateToCreateEvent(vehicleId: Int, category: EventCategory)`; `GuestVehicleViewModel.onRefuelClicked()`; `GuestVehicleViewModel.onExpenseClicked()`; `MainContainerScreen`'s `onNavigateToGuestEventCreate: (vehicleId: Int, category: EventCategory) -> Unit`.

- [ ] **Step 1: Write the failing tests**

Open `app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/guest/GuestVehicleViewModelTest.kt`. Add the import below alongside the existing imports (after the `com.flowfuel.app.core.vehicleshare.domain.model.VehicleShareStatus` import):

```kotlin
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
```

Add these two tests at the end of the class, right before the closing `}` of `GuestVehicleViewModelTest`:

```kotlin
    @Test
    fun onRefuelClicked_emiteNavigateToCreateEventComCategoriaFuel() = runTest {
        val viewModel = createViewModel(vehicleId = 42)

        viewModel.effects.test {
            viewModel.onRefuelClicked()
            assertEquals(GuestVehicleEffect.NavigateToCreateEvent(42, EventCategory.FUEL), awaitItem())
        }
    }

    @Test
    fun onExpenseClicked_emiteNavigateToCreateEventComCategoriaOther() = runTest {
        val viewModel = createViewModel(vehicleId = 42)

        viewModel.effects.test {
            viewModel.onExpenseClicked()
            assertEquals(GuestVehicleEffect.NavigateToCreateEvent(42, EventCategory.OTHER), awaitItem())
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.guest.GuestVehicleViewModelTest"`

Expected: **compile failure** — `onRefuelClicked`, `onExpenseClicked`, and the two-arg `GuestVehicleEffect.NavigateToCreateEvent(Int, EventCategory)` constructor don't exist yet. A compile error is the correct RED state here (statically-typed language, method doesn't exist yet) — proceed to Step 3.

- [ ] **Step 3: Implement `GuestVehicleViewModel.kt`**

Add this import alongside the existing imports (after `com.flowfuel.app.feature.vehicle.domain.VehicleRepository`):

```kotlin
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
```

Change the `NavigateToCreateEvent` effect from:

```kotlin
    data class NavigateToCreateEvent(val vehicleId: Int) : GuestVehicleEffect
```

to:

```kotlin
    data class NavigateToCreateEvent(val vehicleId: Int, val category: EventCategory) : GuestVehicleEffect
```

Replace `onCreateEventClicked()`:

```kotlin
    fun onCreateEventClicked() {
        viewModelScope.launch {
            _effects.send(GuestVehicleEffect.NavigateToCreateEvent(_state.value.vehicleId))
        }
    }
```

with:

```kotlin
    fun onRefuelClicked() {
        viewModelScope.launch {
            _effects.send(GuestVehicleEffect.NavigateToCreateEvent(_state.value.vehicleId, EventCategory.FUEL))
        }
    }

    fun onExpenseClicked() {
        viewModelScope.launch {
            _effects.send(GuestVehicleEffect.NavigateToCreateEvent(_state.value.vehicleId, EventCategory.OTHER))
        }
    }
```

- [ ] **Step 4: Implement `GuestVehicleScreen.kt`**

Add this import alongside the existing imports (after `com.flowfuel.app.core.designsystem.theme.FFTheme`):

```kotlin
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
```

Change the composable's parameter from:

```kotlin
fun GuestVehicleScreen(
    guestVehicle: VehicleShare,
    onNavigateToCreateEvent: (vehicleId: Int) -> Unit,
    onNavigateToPicker: (message: String?) -> Unit,
    onSwitchVehicleClicked: () -> Unit,
    viewModel: GuestVehicleViewModel = hiltViewModel(),
) {
```

to:

```kotlin
fun GuestVehicleScreen(
    guestVehicle: VehicleShare,
    onNavigateToCreateEvent: (vehicleId: Int, category: EventCategory) -> Unit,
    onNavigateToPicker: (message: String?) -> Unit,
    onSwitchVehicleClicked: () -> Unit,
    viewModel: GuestVehicleViewModel = hiltViewModel(),
) {
```

Change the effect handler from:

```kotlin
                is GuestVehicleEffect.NavigateToCreateEvent -> onNavigateToCreateEvent(effect.vehicleId)
```

to:

```kotlin
                is GuestVehicleEffect.NavigateToCreateEvent -> onNavigateToCreateEvent(effect.vehicleId, effect.category)
```

Replace the single button block:

```kotlin
            Spacer(Modifier.height(FFTheme.spacing.md))

            FFButton(
                text = "Registrar abastecimento/despesa",
                onClick = viewModel::onCreateEventClicked,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(FFTheme.spacing.xl))
```

with two buttons:

```kotlin
            Spacer(Modifier.height(FFTheme.spacing.md))

            FFButton(
                text = "Abastecer",
                onClick = viewModel::onRefuelClicked,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(FFTheme.spacing.sm))

            FFButton(
                text = "Registrar despesa",
                onClick = viewModel::onExpenseClicked,
                variant = FFButtonVariant.Text,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(FFTheme.spacing.xl))
```

- [ ] **Step 5: Implement `MainContainerScreen.kt`**

At `MainContainerScreen.kt:111`, change:

```kotlin
    onNavigateToGuestEventCreate: (vehicleId: Int) -> Unit = {},
```

to:

```kotlin
    onNavigateToGuestEventCreate: (vehicleId: Int, category: EventCategory) -> Unit = { _, _ -> },
```

At `MainContainerScreen.kt:278`, change:

```kotlin
                        onNavigateToCreateEvent = { vehicleId -> onNavigateToGuestEventCreate(vehicleId) },
```

to:

```kotlin
                        onNavigateToCreateEvent = { vehicleId, category -> onNavigateToGuestEventCreate(vehicleId, category) },
```

- [ ] **Step 6: Implement `FlowFuelNavHost.kt`**

At `FlowFuelNavHost.kt:716-718`, change:

```kotlin
                onNavigateToGuestEventCreate = { vehicleId ->
                    navController.navigate(Destinations.vehicleEventCreate(vehicleId, guestMode = true))
                },
```

to:

```kotlin
                onNavigateToGuestEventCreate = { vehicleId, category ->
                    navController.navigate(Destinations.vehicleEventCreate(vehicleId, category.name, guestMode = true))
                },
```

- [ ] **Step 7: Run the full unit test suite to verify GREEN**

Run: `.\gradlew.bat :app:testDebugUnitTest`

Expected: **BUILD SUCCESSFUL**, all tests pass, including the two new tests from Step 1. This single command also confirms the whole module (all four production files) compiles together — the earlier compile failure from Step 2 is gone.

- [ ] **Step 8: Manually verify the navigation chain by reading the four files together**

This project has no automated Compose UI/navigation tests (see Global Constraints), and `docs/superpowers/specs/2026-07-25-guest-refuel-buttons-design.md` and prior work on this feature (`project_vehicleshare_module` memory) both note that cross-file navigation wiring bugs have escaped unit tests before. Before committing, re-read the four changed call sites in sequence and confirm the types line up end to end:

1. `GuestVehicleViewModel.onRefuelClicked()` / `onExpenseClicked()` → sends `NavigateToCreateEvent(vehicleId, FUEL|OTHER)`.
2. `GuestVehicleScreen`'s effect handler → calls `onNavigateToCreateEvent(effect.vehicleId, effect.category)`.
3. `MainContainerScreen.kt:278` → `GuestVehicleScreen(onNavigateToCreateEvent = { vehicleId, category -> onNavigateToGuestEventCreate(vehicleId, category) }, ...)`.
4. `FlowFuelNavHost.kt:716-718` → `onNavigateToGuestEventCreate = { vehicleId, category -> navController.navigate(Destinations.vehicleEventCreate(vehicleId, category.name, guestMode = true)) }`.

Confirm no step drops or mismatches the `category` argument. If the emulator is available and a test account with an active vehicle share is set up (the current QA account does not have one — see `project_qa_test_account` memory), optionally launch the app, open a borrowed vehicle, and confirm "Abastecer" opens the create-event form with "Combustível" pre-selected and "Registrar despesa" opens it with "Outro" pre-selected.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/guest/GuestVehicleViewModel.kt app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/guest/GuestVehicleScreen.kt app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/guest/GuestVehicleViewModelTest.kt
git commit -m "feat(vehicleshare): separar abastecer/registrar despesa na tela do convidado

Antes o unico botao 'Registrar abastecimento/despesa' abria o formulario
de evento sempre com categoria Outro, mesmo quando a intencao era
abastecer. Agora sao dois botoes, cada um pre-selecionando a categoria
certa (FUEL / OTHER)."
```

---

## Self-Review Notes

- **Spec coverage:** Requirement 1 (two buttons, primary "Abastecer" / secondary "Registrar despesa", correct pre-selected categories) → Steps 3–6. Requirement 2 (category still changeable in the form) → no code change needed, already true of `CreateVehicleEventScreen`; called out in Step 8 verification. Requirement 3 (no change to odometer/switch-vehicle/error handling) → confirmed by the diffs above touching only the event-creation button block and its wiring.
- **Placeholder scan:** No TBDs; every step has literal before/after code.
- **Type consistency:** `NavigateToCreateEvent(vehicleId: Int, category: EventCategory)` (Step 3) matches the test assertions (Step 1), the screen's callback type `(vehicleId: Int, category: EventCategory) -> Unit` (Step 4), `MainContainerScreen`'s `onNavigateToGuestEventCreate` (Step 5), and the lambda destructuring in `FlowFuelNavHost` (Step 6) — same order and names throughout.
