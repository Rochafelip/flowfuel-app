# Lembretes de Manutenção (Próximos Eventos) — Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Próximos eventos" section to the FlowFuel Home screen showing three maintenance reminders (oil change, tire rotation, licensing), computed from existing vehicle-event data plus a small amount of new local-only state, with no backend changes.

**Architecture:** New `GetUpcomingMaintenanceUseCase` in `feature/home/domain/usecase/` combines the last matching `VehicleEvent` per category (via the existing `GetVehicleEventsPageUseCase`) with a new local `VehicleMaintenancePrefsStore` (Preferences DataStore, same pattern as `SessionStore`) that holds the one-time "no history yet" km anchor and the user-entered licensing due date. `HomeViewModel` loads this as a fourth independent `SectionState`, same isolated-failure/skeleton pattern already used for `financialSummary`/`recentActivity`. A new `UpcomingEventsSection`/`UpcomingEventCard` composable renders it; tapping an oil/tire card navigates to the existing create-event screen with the category prefilled (small addition: one optional query param on the existing route); tapping the licensing card (when unset) opens a small local `LicensingDueDateDialog`.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, Coroutines/StateFlow, Preferences DataStore, MockK + JUnit + Robolectric for tests.

## Global Constraints

- No backend/API changes — this repo contains only the Android client (backend is a separate Spring Boot service documented in `.claude/docs_api`, no source here). `licensingDueDate` and the km anchors are stored **locally on-device only** (do not sync across devices/reinstalls).
- Oil-change and tire-rotation intervals are fixed constants (10.000 km each), not user-configurable in this iteration.
- No "Ver todos" link, no dedicated list screen for reminders — always exactly 3 fixed cards.
- No new Material icons — reuse `EventCategory.icon` (`Icons.Outlined.Opacity`, `Icons.Outlined.TireRepair`, `Icons.AutoMirrored.Outlined.Article`) already defined in `EventCategoryChip.kt`.
- No new colors — reuse `FFTheme.semanticColors` (`warning`, `success`) and `MaterialTheme.colorScheme` (`secondary`, `error`); no purple token exists in this design system.
- Follow existing patterns exactly: `SectionState<T>` for independent-section loading/error, `FFCard`/`FFSkeletonBlock` for cards/skeletons, MockK/JUnit/Robolectric for tests, Portuguese (pt-BR) for all user-facing strings and code comments (matching the rest of the codebase).
- Reference spec: `docs/superpowers/specs/2026-07-07-home-upcoming-maintenance-design.md`.

---

### Task 1: `VehicleMaintenancePrefsStore` (local storage)

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/core/datastore/VehicleMaintenancePrefsStore.kt`
- Test: `app/src/test/java/com/flowfuel/app/core/datastore/VehicleMaintenancePrefsStoreTest.kt`

**Interfaces:**
- Produces: `VehicleMaintenancePrefsStore` (Hilt `@Singleton`, `@Inject constructor(@ApplicationContext context: Context)`) with:
  - `fun licensingDueDateFlow(vehicleId: Int): Flow<String?>`
  - `suspend fun saveLicensingDueDate(vehicleId: Int, isoDate: String)`
  - `fun anchorKmFlow(vehicleId: Int, category: EventCategory): Flow<Int?>`
  - `suspend fun saveAnchorKm(vehicleId: Int, category: EventCategory, km: Int)`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/flowfuel/app/core/datastore/VehicleMaintenancePrefsStoreTest.kt`:

```kotlin
package com.flowfuel.app.core.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VehicleMaintenancePrefsStoreTest {

    private lateinit var store: VehicleMaintenancePrefsStore

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        store = VehicleMaintenancePrefsStore(context)
    }

    @Test
    fun `licensingDueDateFlow is null before anything is saved`() = runTest {
        assertNull(store.licensingDueDateFlow(1).first())
    }

    @Test
    fun `saveLicensingDueDate persists and round-trips per vehicle`() = runTest {
        store.saveLicensingDueDate(1, "2026-08-15")
        store.saveLicensingDueDate(2, "2027-01-01")

        assertEquals("2026-08-15", store.licensingDueDateFlow(1).first())
        assertEquals("2027-01-01", store.licensingDueDateFlow(2).first())
    }

    @Test
    fun `anchorKmFlow is null before anything is saved`() = runTest {
        assertNull(store.anchorKmFlow(1, EventCategory.OIL_CHANGE).first())
    }

    @Test
    fun `saveAnchorKm persists per vehicle and category independently`() = runTest {
        store.saveAnchorKm(1, EventCategory.OIL_CHANGE, 50_000)
        store.saveAnchorKm(1, EventCategory.TIRES, 48_000)
        store.saveAnchorKm(2, EventCategory.OIL_CHANGE, 12_000)

        assertEquals(50_000, store.anchorKmFlow(1, EventCategory.OIL_CHANGE).first())
        assertEquals(48_000, store.anchorKmFlow(1, EventCategory.TIRES).first())
        assertEquals(12_000, store.anchorKmFlow(2, EventCategory.OIL_CHANGE).first())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (class doesn't exist yet)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.core.datastore.VehicleMaintenancePrefsStoreTest"`
Expected: FAIL — compilation error, `VehicleMaintenancePrefsStore` unresolved reference.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/flowfuel/app/core/datastore/VehicleMaintenancePrefsStore.kt`:

```kotlin
package com.flowfuel.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.vehicleMaintenanceDataStore by preferencesDataStore(name = "flowfuel_vehicle_maintenance")

/**
 * Lembretes de manutenção (ver GetUpcomingMaintenanceUseCase) dependem de dois dados
 * que não existem no backend — este repositório contém só o cliente Android, o
 * backend é um serviço separado — então ficam só neste dispositivo, sem sincronizar
 * entre aparelhos/reinstalações:
 *
 * - Data de licenciamento definida manualmente pelo usuário, por veículo.
 * - Âncora de km usada só enquanto não existe nenhum VehicleEvent real da categoria
 *   (óleo/pneus): o odômetro do veículo no momento em que o lembrete foi calculado
 *   por falta de histórico, gravada uma única vez para a contagem não "resetar" a
 *   cada abastecimento.
 */
@Singleton
class VehicleMaintenancePrefsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun licensingDueDateFlow(vehicleId: Int): Flow<String?> =
        context.vehicleMaintenanceDataStore.data.map { it[licensingDueDateKey(vehicleId)] }

    suspend fun saveLicensingDueDate(vehicleId: Int, isoDate: String) {
        context.vehicleMaintenanceDataStore.edit { it[licensingDueDateKey(vehicleId)] = isoDate }
    }

    fun anchorKmFlow(vehicleId: Int, category: EventCategory): Flow<Int?> =
        context.vehicleMaintenanceDataStore.data.map { it[anchorKmKey(vehicleId, category)] }

    suspend fun saveAnchorKm(vehicleId: Int, category: EventCategory, km: Int) {
        context.vehicleMaintenanceDataStore.edit { it[anchorKmKey(vehicleId, category)] = km }
    }

    private fun licensingDueDateKey(vehicleId: Int) = stringPreferencesKey("licensing_due_$vehicleId")

    private fun anchorKmKey(vehicleId: Int, category: EventCategory) =
        intPreferencesKey("anchor_${category.name}_$vehicleId")
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.core.datastore.VehicleMaintenancePrefsStoreTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/datastore/VehicleMaintenancePrefsStore.kt app/src/test/java/com/flowfuel/app/core/datastore/VehicleMaintenancePrefsStoreTest.kt
git commit -m "feat(home): adiciona VehicleMaintenancePrefsStore para lembretes de manutencao locais"
```

---

### Task 2: `GetUpcomingMaintenanceUseCase` + domain models

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/home/domain/usecase/GetUpcomingMaintenanceUseCase.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/home/domain/usecase/GetUpcomingMaintenanceUseCaseTest.kt`

**Interfaces:**
- Consumes: `VehicleMaintenancePrefsStore` (Task 1); `GetVehicleEventsPageUseCase.invoke(vehicleId: Int, page: Int, category: EventCategory?, dateFrom: String? = null, dateTo: String? = null): AppResult<PagedVehicleEvents>` (existing); `VehicleEvent.odometerKm: Int?`, `VehicleEvent.eventDate: String` (existing).
- Produces: `UpcomingMaintenanceType` enum (`OIL_CHANGE`, `TIRE_ROTATION`, `LICENSING`); `UpcomingMaintenanceItem(type, remainingKm: Int?, remainingDays: Int?, isOverdue: Boolean, needsSetup: Boolean)`; `GetUpcomingMaintenanceUseCase.invoke(vehicleId: Int, currentKm: Int): AppResult<List<UpcomingMaintenanceItem>>` — consumed by Task 3 (HomeViewModel).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/flowfuel/app/feature/home/domain/usecase/GetUpcomingMaintenanceUseCaseTest.kt`:

```kotlin
package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.datastore.VehicleMaintenancePrefsStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceType
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.PagedVehicleEvents
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetUpcomingMaintenanceUseCaseTest {

    private val getVehicleEventsPage: GetVehicleEventsPageUseCase = mockk()
    private val prefsStore: VehicleMaintenancePrefsStore = mockk()
    private val useCase = GetUpcomingMaintenanceUseCase(getVehicleEventsPage, prefsStore)

    private fun event(category: EventCategory, odometerKm: Int?, eventDate: String) = VehicleEvent(
        id = 1, vehicleId = 1, category = category, title = "Evento", description = null,
        amount = null, eventDate = eventDate, odometerKm = odometerKm, notes = null,
        receiptUrl = null, createdAt = null, updatedAt = null,
    )

    private fun eventPage(items: List<VehicleEvent>) =
        PagedVehicleEvents(items = items, currentPage = 0, totalPages = 1, totalElements = items.size)

    private fun stubNoEvents() {
        coEvery { getVehicleEventsPage(1, 0, EventCategory.OIL_CHANGE) } returns AppResult.Success(eventPage(emptyList()))
        coEvery { getVehicleEventsPage(1, 0, EventCategory.TIRES) } returns AppResult.Success(eventPage(emptyList()))
    }

    private fun stubNoAnchors() {
        every { prefsStore.anchorKmFlow(1, EventCategory.OIL_CHANGE) } returns flowOf(null)
        every { prefsStore.anchorKmFlow(1, EventCategory.TIRES) } returns flowOf(null)
        coEvery { prefsStore.saveAnchorKm(any(), any(), any()) } returns Unit
    }

    private fun stubNoLicensing() {
        every { prefsStore.licensingDueDateFlow(1) } returns flowOf(null)
    }

    @Test
    fun `oil change uses last event odometer plus interval, ignoring any anchor`() = runTest {
        coEvery { getVehicleEventsPage(1, 0, EventCategory.OIL_CHANGE) } returns AppResult.Success(
            eventPage(listOf(event(EventCategory.OIL_CHANGE, odometerKm = 60_000, eventDate = "2026-01-10"))),
        )
        coEvery { getVehicleEventsPage(1, 0, EventCategory.TIRES) } returns AppResult.Success(eventPage(emptyList()))
        stubNoAnchors()
        stubNoLicensing()

        val items = (useCase(1, currentKm = 65_000) as AppResult.Success).value
        val oil = items.first { it.type == UpcomingMaintenanceType.OIL_CHANGE }

        // due = 60_000 + 10_000 = 70_000; remaining = 70_000 - 65_000
        assertEquals(5_000, oil.remainingKm)
        assertFalse(oil.isOverdue)
        coVerify(exactly = 0) { prefsStore.saveAnchorKm(1, EventCategory.OIL_CHANGE, any()) }
    }

    @Test
    fun `oil change with no event and no anchor saves current km as anchor and returns full interval`() = runTest {
        stubNoEvents()
        stubNoAnchors()
        stubNoLicensing()

        val items = (useCase(1, currentKm = 50_000) as AppResult.Success).value
        val oil = items.first { it.type == UpcomingMaintenanceType.OIL_CHANGE }

        assertEquals(10_000, oil.remainingKm)
        coVerify(exactly = 1) { prefsStore.saveAnchorKm(1, EventCategory.OIL_CHANGE, 50_000) }
    }

    @Test
    fun `oil change with no event but an existing anchor reuses it without saving again`() = runTest {
        stubNoEvents()
        every { prefsStore.anchorKmFlow(1, EventCategory.OIL_CHANGE) } returns flowOf(50_000)
        every { prefsStore.anchorKmFlow(1, EventCategory.TIRES) } returns flowOf(null)
        coEvery { prefsStore.saveAnchorKm(any(), any(), any()) } returns Unit
        stubNoLicensing()

        // O odômetro avançou desde que a âncora foi gravada; due continua fixo em 50_000 + 10_000.
        val items = (useCase(1, currentKm = 58_000) as AppResult.Success).value
        val oil = items.first { it.type == UpcomingMaintenanceType.OIL_CHANGE }

        assertEquals(2_000, oil.remainingKm)
        coVerify(exactly = 0) { prefsStore.saveAnchorKm(1, EventCategory.OIL_CHANGE, any()) }
    }

    @Test
    fun `negative remainingKm marks the item overdue`() = runTest {
        coEvery { getVehicleEventsPage(1, 0, EventCategory.OIL_CHANGE) } returns AppResult.Success(
            eventPage(listOf(event(EventCategory.OIL_CHANGE, odometerKm = 60_000, eventDate = "2026-01-10"))),
        )
        coEvery { getVehicleEventsPage(1, 0, EventCategory.TIRES) } returns AppResult.Success(eventPage(emptyList()))
        stubNoAnchors()
        stubNoLicensing()

        // due = 70_000; currentKm já passou disso.
        val items = (useCase(1, currentKm = 71_500) as AppResult.Success).value
        val oil = items.first { it.type == UpcomingMaintenanceType.OIL_CHANGE }

        assertEquals(-1_500, oil.remainingKm)
        assertTrue(oil.isOverdue)
    }

    @Test
    fun `licensing without a due date returns needsSetup`() = runTest {
        stubNoEvents()
        stubNoAnchors()
        every { prefsStore.licensingDueDateFlow(1) } returns flowOf(null)

        val items = (useCase(1, currentKm = 50_000) as AppResult.Success).value
        val licensing = items.first { it.type == UpcomingMaintenanceType.LICENSING }

        assertTrue(licensing.needsSetup)
        assertNull(licensing.remainingDays)
    }

    @Test
    fun `licensing with a future due date computes remainingDays and is not overdue`() = runTest {
        stubNoEvents()
        stubNoAnchors()
        val futureDate = LocalDate.now().plusDays(18)
        every { prefsStore.licensingDueDateFlow(1) } returns flowOf(futureDate.toString())

        val items = (useCase(1, currentKm = 50_000) as AppResult.Success).value
        val licensing = items.first { it.type == UpcomingMaintenanceType.LICENSING }

        assertEquals(18, licensing.remainingDays)
        assertFalse(licensing.isOverdue)
    }

    @Test
    fun `licensing with a past due date is overdue`() = runTest {
        stubNoEvents()
        stubNoAnchors()
        val pastDate = LocalDate.now().minusDays(3)
        every { prefsStore.licensingDueDateFlow(1) } returns flowOf(pastDate.toString())

        val items = (useCase(1, currentKm = 50_000) as AppResult.Success).value
        val licensing = items.first { it.type == UpcomingMaintenanceType.LICENSING }

        assertEquals(-3, licensing.remainingDays)
        assertTrue(licensing.isOverdue)
    }

    @Test
    fun `propagates failure from the oil change lookup without querying tires`() = runTest {
        coEvery { getVehicleEventsPage(1, 0, EventCategory.OIL_CHANGE) } returns AppResult.Failure(AppError.Network)

        val result = useCase(1, currentKm = 50_000)

        assertEquals(AppError.Network, (result as AppResult.Failure).error)
        coVerify(exactly = 0) { getVehicleEventsPage(1, 0, EventCategory.TIRES) }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.domain.usecase.GetUpcomingMaintenanceUseCaseTest"`
Expected: FAIL — compilation error, `GetUpcomingMaintenanceUseCase`/`UpcomingMaintenanceType` unresolved.

- [ ] **Step 3: Add the domain models**

In `app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt`, append at the end of the file (after the existing `FinancialSummary` data class):

```kotlin

/** Tipo de lembrete de manutenção exibido na seção "Próximos eventos" da Home. */
enum class UpcomingMaintenanceType { OIL_CHANGE, TIRE_ROTATION, LICENSING }

/**
 * Um dos 3 lembretes da seção "Próximos eventos". [remainingKm] é usado por
 * OIL_CHANGE/TIRE_ROTATION, [remainingDays] por LICENSING — os dois nunca são
 * preenchidos ao mesmo tempo. [needsSetup] só é true para LICENSING sem data
 * definida ainda pelo usuário (ver [com.flowfuel.app.core.datastore.VehicleMaintenancePrefsStore]).
 */
data class UpcomingMaintenanceItem(
    val type: UpcomingMaintenanceType,
    val remainingKm: Int? = null,
    val remainingDays: Int? = null,
    val isOverdue: Boolean = false,
    val needsSetup: Boolean = false,
)
```

- [ ] **Step 4: Write the use case implementation**

Create `app/src/main/java/com/flowfuel/app/feature/home/domain/usecase/GetUpcomingMaintenanceUseCase.kt`:

```kotlin
package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.datastore.VehicleMaintenancePrefsStore
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceItem
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceType
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first

private const val OIL_CHANGE_INTERVAL_KM = 10_000
private const val TIRE_ROTATION_INTERVAL_KM = 10_000

/**
 * Lembretes de manutenção (troca de óleo, rodízio de pneus, licenciamento) exibidos
 * na seção "Próximos eventos" da Home. Óleo/pneus: último [com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent]
 * da categoria correspondente + um intervalo fixo em km; sem histórico, usa uma âncora
 * local persistida ([VehicleMaintenancePrefsStore]) gravada uma única vez, para não
 * recalcular a partir do odômetro atual (que muda) a cada chamada. Licenciamento usa
 * uma data definida manualmente pelo usuário, também local.
 */
class GetUpcomingMaintenanceUseCase @Inject constructor(
    private val getVehicleEventsPage: GetVehicleEventsPageUseCase,
    private val prefsStore: VehicleMaintenancePrefsStore,
) {
    suspend operator fun invoke(vehicleId: Int, currentKm: Int): AppResult<List<UpcomingMaintenanceItem>> {
        val oilResult = kmBasedItem(
            vehicleId, currentKm, EventCategory.OIL_CHANGE,
            UpcomingMaintenanceType.OIL_CHANGE, OIL_CHANGE_INTERVAL_KM,
        )
        if (oilResult is AppResult.Failure) return oilResult

        val tiresResult = kmBasedItem(
            vehicleId, currentKm, EventCategory.TIRES,
            UpcomingMaintenanceType.TIRE_ROTATION, TIRE_ROTATION_INTERVAL_KM,
        )
        if (tiresResult is AppResult.Failure) return tiresResult

        val licensing = licensingItem(vehicleId)

        return AppResult.Success(
            listOf(
                (oilResult as AppResult.Success).value,
                (tiresResult as AppResult.Success).value,
                licensing,
            ),
        )
    }

    private suspend fun kmBasedItem(
        vehicleId: Int,
        currentKm: Int,
        category: EventCategory,
        type: UpcomingMaintenanceType,
        intervalKm: Int,
    ): AppResult<UpcomingMaintenanceItem> {
        val eventsResult = getVehicleEventsPage(vehicleId, 0, category)
        if (eventsResult is AppResult.Failure) return eventsResult
        val lastEvent = (eventsResult as AppResult.Success).value.items
            .filter { it.odometerKm != null }
            .maxByOrNull { it.eventDate }

        val dueKm = if (lastEvent != null) {
            lastEvent.odometerKm!! + intervalKm
        } else {
            val anchor = prefsStore.anchorKmFlow(vehicleId, category).first()
            if (anchor != null) {
                anchor + intervalKm
            } else {
                prefsStore.saveAnchorKm(vehicleId, category, currentKm)
                currentKm + intervalKm
            }
        }

        val remainingKm = dueKm - currentKm
        return AppResult.Success(
            UpcomingMaintenanceItem(
                type = type,
                remainingKm = remainingKm,
                isOverdue = remainingKm < 0,
            ),
        )
    }

    private suspend fun licensingItem(vehicleId: Int): UpcomingMaintenanceItem {
        val dueDateIso = prefsStore.licensingDueDateFlow(vehicleId).first()
            ?: return UpcomingMaintenanceItem(type = UpcomingMaintenanceType.LICENSING, needsSetup = true)

        val remainingDays = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(dueDateIso)).toInt()
        return UpcomingMaintenanceItem(
            type = UpcomingMaintenanceType.LICENSING,
            remainingDays = remainingDays,
            isOverdue = remainingDays < 0,
        )
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.domain.usecase.GetUpcomingMaintenanceUseCaseTest"`
Expected: PASS (8 tests)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/domain/model/HomeModels.kt app/src/main/java/com/flowfuel/app/feature/home/domain/usecase/GetUpcomingMaintenanceUseCase.kt app/src/test/java/com/flowfuel/app/feature/home/domain/usecase/GetUpcomingMaintenanceUseCaseTest.kt
git commit -m "feat(home): adiciona GetUpcomingMaintenanceUseCase para lembretes de manutencao"
```

---

### Task 3: `HomeUiState`/`HomeViewModel` wiring

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeUiState.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `GetUpcomingMaintenanceUseCase` and `VehicleMaintenancePrefsStore` (Task 1/2).
- Produces: `HomeScreenState.Success.upcomingMaintenance: SectionState<List<UpcomingMaintenanceItem>>`; `HomeUiState.showLicensingDueDatePicker: Boolean`; `HomeViewModel.retryUpcomingMaintenance()`, `HomeViewModel.openLicensingDueDatePicker()`, `HomeViewModel.closeLicensingDueDatePicker()`, `HomeViewModel.onLicensingDueDateSelected(isoDate: String)` — all consumed by Task 6 (`HomeScreen.kt`).

- [ ] **Step 1: Write the failing test**

In `app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt`, add the two new mocked dependencies next to the existing ones, update the `viewModel = HomeViewModel(...)` construction, and add new tests. Apply this full replacement of the top of the class (from the field declarations through `setUp()`):

```kotlin
    private val getVehicleEventsTotal: GetVehicleEventsTotalUseCase = mockk(relaxed = true)
    private val getFinancialSummary: GetFinancialSummaryUseCase = mockk()
    private val getRecentActivity: GetRecentActivityUseCase = mockk()
    private val getUpcomingMaintenance: GetUpcomingMaintenanceUseCase = mockk()
    private val maintenancePrefsStore: VehicleMaintenancePrefsStore = mockk(relaxed = true)

    private val testFinancialSummary = FinancialSummary(
        currentMonthTotal = 300.0,
        previousMonthTotal = 250.0,
        averagePricePerUnit = 5.5,
    )

    private val testUpcomingMaintenance = listOf(
        UpcomingMaintenanceItem(type = UpcomingMaintenanceType.OIL_CHANGE, remainingKm = 320),
        UpcomingMaintenanceItem(type = UpcomingMaintenanceType.TIRE_ROTATION, remainingKm = 900),
        UpcomingMaintenanceItem(type = UpcomingMaintenanceType.LICENSING, remainingDays = 18),
    )

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
        coEvery { getFinancialSummary(any()) } returns AppResult.Success(testFinancialSummary)
        coEvery { getRecentActivity(any()) } returns AppResult.Success(emptyList())
        coEvery { getUpcomingMaintenance(any(), any()) } returns AppResult.Success(testUpcomingMaintenance)
        viewModel = HomeViewModel(
            getActiveVehicle, getDashboard, createRefuel, logout,
            sessionStore, getVehicles, setActiveVehicle, stationsPrefetcher, getVehicleEventsTotal,
            getFinancialSummary, getRecentActivity, getUpcomingMaintenance, maintenancePrefsStore,
        )
    }
```

Add these imports at the top of the file, alongside the existing ones:

```kotlin
import com.flowfuel.app.core.datastore.VehicleMaintenancePrefsStore
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceItem
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceType
import com.flowfuel.app.feature.home.domain.usecase.GetUpcomingMaintenanceUseCase
```

Then add these new tests at the end of the class, right before the final closing `}`:

```kotlin

    // ── Seção independente (upcomingMaintenance) ──────────────────────────────

    @Test
    fun `load() populates upcomingMaintenance section on success`() = runTest {
        viewModel.load()

        val success = viewModel.state.value.screenState as HomeScreenState.Success
        assertEquals(SectionState.Success(testUpcomingMaintenance), success.upcomingMaintenance)
    }

    @Test
    fun `load() isolates upcomingMaintenance failure without breaking the rest of the screen`() = runTest {
        coEvery { getUpcomingMaintenance(any(), any()) } returns AppResult.Failure(AppError.Network)

        viewModel.load()

        val success = viewModel.state.value.screenState as HomeScreenState.Success
        assertEquals(SectionState.Error(AppError.Network), success.upcomingMaintenance)
        assertEquals(SectionState.Success(testFinancialSummary), success.financialSummary)
    }

    @Test
    fun `retryUpcomingMaintenance() re-fetches only that section, using the current vehicle's km`() = runTest {
        coEvery { getUpcomingMaintenance(any(), any()) } returns AppResult.Failure(AppError.Network)
        viewModel.load()
        coEvery { getUpcomingMaintenance(any(), any()) } returns AppResult.Success(testUpcomingMaintenance)

        viewModel.retryUpcomingMaintenance()

        val success = viewModel.state.value.screenState as HomeScreenState.Success
        assertEquals(SectionState.Success(testUpcomingMaintenance), success.upcomingMaintenance)
        coVerify { getUpcomingMaintenance(1, 67270) }
    }

    // ── Diálogo de data de licenciamento ───────────────────────────────────────

    @Test
    fun `openLicensingDueDatePicker() shows the dialog`() {
        viewModel.openLicensingDueDatePicker()
        assertTrue(viewModel.state.value.showLicensingDueDatePicker)
    }

    @Test
    fun `closeLicensingDueDatePicker() hides the dialog`() {
        viewModel.openLicensingDueDatePicker()
        viewModel.closeLicensingDueDatePicker()
        assertFalse(viewModel.state.value.showLicensingDueDatePicker)
    }

    @Test
    fun `onLicensingDueDateSelected() saves the date, closes the dialog, and reloads the section`() = runTest {
        viewModel.load()
        viewModel.openLicensingDueDatePicker()

        viewModel.onLicensingDueDateSelected("2026-08-15")

        assertFalse(viewModel.state.value.showLicensingDueDatePicker)
        coVerify { maintenancePrefsStore.saveLicensingDueDate(1, "2026-08-15") }
        coVerify(atLeast = 2) { getUpcomingMaintenance(1, 67270) } // 1x do load() + 1x do retry pós-seleção
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.HomeViewModelTest"`
Expected: FAIL — compilation error (`GetUpcomingMaintenanceUseCase` constructor param count mismatch, `showLicensingDueDatePicker`/`upcomingMaintenance` unresolved, `retryUpcomingMaintenance`/`openLicensingDueDatePicker`/etc. unresolved).

- [ ] **Step 3: Update `HomeUiState.kt`**

In `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeUiState.kt`, add the import:

```kotlin
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceItem
```

Change the `HomeScreenState.Success` data class to add the new section:

```kotlin
    data class Success(
        val vehicle: ActiveVehicleData,
        val dashboard: DashboardData,
        val financialSummary: SectionState<FinancialSummary> = SectionState.Loading,
        val recentActivity: SectionState<List<VehicleTimelineItem>> = SectionState.Loading,
        val upcomingMaintenance: SectionState<List<UpcomingMaintenanceItem>> = SectionState.Loading,
    ) : HomeScreenState
```

Change `HomeUiState` to add the dialog flag (right after `showLogoutDialog`):

```kotlin
    // ── Confirmação de logout ──────────────────────────────────────────────
    val showLogoutDialog: Boolean = false,
    // ── Data de licenciamento (lembrete de manutenção) ─────────────────────
    val showLicensingDueDatePicker: Boolean = false,
)
```

- [ ] **Step 4: Update `HomeViewModel.kt`**

Add two constructor params, right after `getRecentActivity`:

```kotlin
    private val getFinancialSummary: GetFinancialSummaryUseCase,
    private val getRecentActivity: GetRecentActivityUseCase,
    private val getUpcomingMaintenance: GetUpcomingMaintenanceUseCase,
    private val maintenancePrefsStore: VehicleMaintenancePrefsStore,
) : ViewModel() {
```

Add imports:

```kotlin
import com.flowfuel.app.core.datastore.VehicleMaintenancePrefsStore
import com.flowfuel.app.feature.home.domain.usecase.GetUpcomingMaintenanceUseCase
```

In `load()`, change the `is AppResult.Success ->` branch of `fetchDashboardWithEventsTotal` to also launch the new section:

```kotlin
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            screenState = HomeScreenState.Success(
                                vehicle = vehicle,
                                dashboard = dashboardResult.value,
                            ),
                        )
                    }
                    launch { loadFinancialSummary(vehicleId) }
                    launch { loadRecentActivity(vehicleId) }
                    launch { loadUpcomingMaintenance(vehicleId, vehicle.currentKm) }
                }
```

Do the same in `refresh()`'s success branch:

```kotlin
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            isRefreshing = false,
                            screenState = HomeScreenState.Success(
                                vehicle = vehicle,
                                dashboard = dashboardResult.value,
                            ),
                        )
                    }
                    launch { loadFinancialSummary(vehicleId) }
                    launch { loadRecentActivity(vehicleId) }
                    launch { loadUpcomingMaintenance(vehicleId, vehicle.currentKm) }
                }
```

Add these methods right after `loadRecentActivity`/`retryRecentActivity` (i.e. after the closing `}` of `retryRecentActivity()`):

```kotlin

    private suspend fun loadUpcomingMaintenance(vehicleId: Int, currentKm: Int) {
        val sectionState = when (val result = getUpcomingMaintenance(vehicleId, currentKm)) {
            is AppResult.Success -> SectionState.Success(result.value)
            is AppResult.Failure -> SectionState.Error(result.error)
        }
        _state.update { state ->
            val success = state.screenState as? HomeScreenState.Success ?: return@update state
            if (success.vehicle.id != vehicleId) return@update state
            state.copy(screenState = success.copy(upcomingMaintenance = sectionState))
        }
    }

    /** Reexecuta só os lembretes de manutenção, sem recarregar o resto da tela. */
    fun retryUpcomingMaintenance() {
        val success = _state.value.screenState as? HomeScreenState.Success ?: return
        _state.update { it.copy(screenState = success.copy(upcomingMaintenance = SectionState.Loading)) }
        viewModelScope.launch { loadUpcomingMaintenance(success.vehicle.id, success.vehicle.currentKm) }
    }

    // ─── Data de licenciamento ────────────────────────────────────────────────

    fun openLicensingDueDatePicker() = _state.update { it.copy(showLicensingDueDatePicker = true) }

    fun closeLicensingDueDatePicker() = _state.update { it.copy(showLicensingDueDatePicker = false) }

    /** Salva a data escolhida localmente e recarrega só a seção de lembretes. */
    fun onLicensingDueDateSelected(isoDate: String) {
        val vehicleId = loadedVehicleId ?: return
        _state.update { it.copy(showLicensingDueDatePicker = false) }
        viewModelScope.launch {
            maintenancePrefsStore.saveLicensingDueDate(vehicleId, isoDate)
            retryUpcomingMaintenance()
        }
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.presentation.HomeViewModelTest"`
Expected: PASS (all existing tests + 6 new ones)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeUiState.kt app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeViewModel.kt app/src/test/java/com/flowfuel/app/feature/home/presentation/HomeViewModelTest.kt
git commit -m "feat(home): carrega lembretes de manutencao como secao independente na Home"
```

---

### Task 4: `UpcomingEventsSection` / `UpcomingEventCard` composables

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/UpcomingEventsSection.kt`

**Interfaces:**
- Consumes: `UpcomingMaintenanceItem`/`UpcomingMaintenanceType` (Task 2); `EventCategory.icon` extension from `com.flowfuel.app.feature.vehicleevent.presentation.components` (existing, `internal`, same Gradle module so importable); `FFCard`, `FFCardVariant`, `FFTheme` (existing design system).
- Produces: `@Composable fun UpcomingEventsSection(items: List<UpcomingMaintenanceItem>, onCardClick: (UpcomingMaintenanceType) -> Unit, modifier: Modifier = Modifier)` — consumed by Task 6 (`HomeScreen.kt`).

This task has no unit test (pure Compose UI, no business logic beyond simple label formatting) — verified via the `@Preview` composables added below and later via the manual verification in Task 6. This matches the project's existing convention (no instrumented Compose tests; see `docs/superpowers/specs/2026-07-06-home-dashboard-design.md`, "Sem testes de UI/Compose instrumentados").

- [ ] **Step 1: Write the composable**

Create `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/UpcomingEventsSection.kt`:

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceItem
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceType
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.presentation.components.icon

@Composable
fun UpcomingEventsSection(
    items: List<UpcomingMaintenanceItem>,
    onCardClick: (UpcomingMaintenanceType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
        Text(
            text = "Próximos eventos",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
            items.forEach { item ->
                UpcomingEventCard(
                    item = item,
                    onClick = { onCardClick(item.type) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun UpcomingEventCard(item: UpcomingMaintenanceItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val presentation = item.toPresentation()
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(presentation.accent.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = presentation.icon,
                    contentDescription = null,
                    tint = presentation.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = presentation.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = presentation.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private data class CardPresentation(
    val icon: ImageVector,
    val accent: Color,
    val title: String,
    val subtitle: String,
)

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
    return CardPresentation(icon, accent, title, subtitle)
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

@Preview(showBackground = true)
@Composable
private fun UpcomingEventsSectionPreview() {
    UpcomingEventsSection(
        items = listOf(
            UpcomingMaintenanceItem(type = UpcomingMaintenanceType.OIL_CHANGE, remainingKm = 320),
            UpcomingMaintenanceItem(type = UpcomingMaintenanceType.TIRE_ROTATION, remainingKm = 900),
            UpcomingMaintenanceItem(type = UpcomingMaintenanceType.LICENSING, remainingDays = 18),
        ),
        onCardClick = {},
    )
}

@Preview(showBackground = true, name = "Overdue + prompt")
@Composable
private fun UpcomingEventsSectionOverduePreview() {
    UpcomingEventsSection(
        items = listOf(
            UpcomingMaintenanceItem(type = UpcomingMaintenanceType.OIL_CHANGE, remainingKm = -150, isOverdue = true),
            UpcomingMaintenanceItem(type = UpcomingMaintenanceType.TIRE_ROTATION, remainingKm = 4200),
            UpcomingMaintenanceItem(type = UpcomingMaintenanceType.LICENSING, needsSetup = true),
        ),
        onCardClick = {},
    )
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/UpcomingEventsSection.kt
git commit -m "feat(home): adiciona UpcomingEventsSection e UpcomingEventCard"
```

---

### Task 5: `LicensingDueDateDialog`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/home/presentation/LicensingDueDateDialog.kt`

**Interfaces:**
- Produces: `@Composable fun LicensingDueDateDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit)` — consumed by Task 6 (`HomeScreen.kt`). `onConfirm` receives an ISO `yyyy-MM-dd` string.

No unit test (Compose dialog with no business logic — the ISO date conversion is the same one-line `Instant.ofEpochMilli(...).atZone(ZoneOffset.UTC).toLocalDate().toString()` pattern already used untested in `CreateVehicleEventScreen.kt`'s `EventDatePickerDialog`).

- [ ] **Step 1: Write the composable**

Create `app/src/main/java/com/flowfuel/app/feature/home/presentation/LicensingDueDateDialog.kt`:

```kotlin
package com.flowfuel.app.feature.home.presentation

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import java.time.Instant
import java.time.ZoneOffset

/**
 * Diálogo dedicado para definir a data de licenciamento, aberto direto a partir do
 * cartão de lembrete na Home — não a tela de edição de veículo, já que este campo é
 * armazenado só localmente (ver [com.flowfuel.app.core.datastore.VehicleMaintenancePrefsStore]),
 * diferente dos demais campos daquela tela, que são sincronizados com o backend.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensingDueDateDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val datePickerState = rememberDatePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onConfirm(date.toString())
                }
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

@Preview(showBackground = true)
@Composable
private fun LicensingDueDateDialogPreview() {
    LicensingDueDateDialog(onConfirm = {}, onDismiss = {})
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/LicensingDueDateDialog.kt
git commit -m "feat(home): adiciona LicensingDueDateDialog"
```

---

### Task 6: Wire the section into `HomeScreen`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`

**Interfaces:**
- Consumes: `UpcomingEventsSection` (Task 4), `LicensingDueDateDialog` (Task 5), `HomeViewModel.retryUpcomingMaintenance/openLicensingDueDatePicker/closeLicensingDueDatePicker/onLicensingDueDateSelected` and `HomeUiState.showLicensingDueDatePicker`/`HomeScreenState.Success.upcomingMaintenance` (Task 3).
- Produces: `HomeScreen(..., onNavigateToMaintenanceEventCreate: (vehicleId: Int, category: EventCategory) -> Unit = { _, _ -> })` — new parameter consumed by Task 7 (`MainContainerScreen.kt`).

No new unit test: `HomeScreen`/`HomeContent` have no existing Compose UI tests to extend (confirmed — no instrumented tests in this project). Verified manually via the `run-android-emulator` skill at the end of this plan.

- [ ] **Step 1: Add imports**

At the top of `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`, add:

```kotlin
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceItem
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceType
import com.flowfuel.app.feature.home.presentation.components.UpcomingEventsSection
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
```

- [ ] **Step 2: Add the new parameter and click-routing to `HomeScreen`**

Change the `HomeScreen` composable signature to add the new navigation param:

```kotlin
@Composable
fun HomeScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToAddVehicle: () -> Unit,
    onNavigateToMaintenanceEventCreate: (vehicleId: Int, category: EventCategory) -> Unit = { _, _ -> },
    openRefuelSheet: Boolean = false,
    onRefuelSheetOpened: () -> Unit = {},
    refreshTrigger: Boolean = false,
    onRefreshConsumed: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
```

Right after the `val snackbarHostState = remember { SnackbarHostState() }` line, add the click-routing lambda:

```kotlin
    val onUpcomingEventClick: (UpcomingMaintenanceType) -> Unit = { type ->
        when (type) {
            UpcomingMaintenanceType.LICENSING -> viewModel.openLicensingDueDatePicker()
            UpcomingMaintenanceType.OIL_CHANGE, UpcomingMaintenanceType.TIRE_ROTATION -> {
                val vehicleId = (state.screenState as? HomeScreenState.Success)?.vehicle?.id
                val category = if (type == UpcomingMaintenanceType.OIL_CHANGE) EventCategory.OIL_CHANGE else EventCategory.TIRES
                if (vehicleId != null) onNavigateToMaintenanceEventCreate(vehicleId, category)
            }
        }
    }
```

- [ ] **Step 3: Render the `LicensingDueDateDialog`**

Right after the closing `}` of the `if (state.showVehicleSwitcher) { ... }` block at the end of `HomeScreen`, add:

```kotlin

    if (state.showLicensingDueDatePicker) {
        LicensingDueDateDialog(
            onConfirm = viewModel::onLicensingDueDateSelected,
            onDismiss = viewModel::closeLicensingDueDatePicker,
        )
    }
```

- [ ] **Step 4: Pass the new params from `HomeScreen` to `HomeContent`**

In the `is HomeScreenState.Success -> PullToRefreshBox(...)` block, change the `HomeContent(...)` call to add the three new arguments:

```kotlin
                    HomeContent(
                        vehicle = s.vehicle,
                        dashboard = s.dashboard,
                        financialSummary = s.financialSummary,
                        recentActivity = s.recentActivity,
                        upcomingMaintenance = s.upcomingMaintenance,
                        onRegisterRefuel = viewModel::openRefuelSheet,
                        onVehicleClick = viewModel::openVehicleSwitcher,
                        onRetryFinancialSummary = viewModel::retryFinancialSummary,
                        onRetryRecentActivity = viewModel::retryRecentActivity,
                        onRetryUpcomingMaintenance = viewModel::retryUpcomingMaintenance,
                        onUpcomingEventClick = onUpcomingEventClick,
                        modifier = Modifier.fillMaxSize(),
                    )
```

- [ ] **Step 5: Update `HomeContent`'s signature and body**

Change the `HomeContent` signature to add the three new params:

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
    onRetryFinancialSummary: () -> Unit,
    onRetryRecentActivity: () -> Unit,
    onRetryUpcomingMaintenance: () -> Unit,
    onUpcomingEventClick: (UpcomingMaintenanceType) -> Unit,
    modifier: Modifier = Modifier,
) {
```

Right after the closing `}` of the `if (!isFirstUse) { ... }` block (the block containing `LastRefuelCard` and `RecentActivityCard`) and before the `// Espaço para o FAB...` comment/`Spacer` item, add a new **unconditional** item — it renders regardless of `isFirstUse` because oil/tire reminders only need `vehicle.currentKm` (always known) and licensing needs no refuel history at all:

```kotlin

        item {
            when (upcomingMaintenance) {
                is SectionState.Success -> UpcomingEventsSection(
                    items = upcomingMaintenance.value,
                    onCardClick = onUpcomingEventClick,
                )
                SectionState.Loading -> FFSkeletonBlock(height = 96.dp)
                is SectionState.Error -> SectionErrorCard(onRetry = onRetryUpcomingMaintenance)
            }
        }
```

- [ ] **Step 6: Add a matching block to the full-screen loading skeleton**

In `HomeLoadingSkeleton`, add one more block so the initial skeleton visually matches the final layout:

```kotlin
@Composable
private fun HomeLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(FFTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap),
    ) {
        FFSkeletonBlock(height = 56.dp)
        FFSkeletonBlock(height = 96.dp)
        FFSkeletonBlock(height = 176.dp)
        FFSkeletonBlock(height = 96.dp)
        FFSkeletonBlock(height = 160.dp)
        FFSkeletonBlock(height = 96.dp)
        FFSkeletonLine(widthFraction = 0.6f)
        FFSkeletonLine(widthFraction = 0.4f)
    }
}
```

- [ ] **Step 7: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Run the full Home test suite to check nothing regressed**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.home.*"`
Expected: PASS (all tests, including Task 3's new ones)

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt
git commit -m "feat(home): exibe UpcomingEventsSection e LicensingDueDateDialog na HomeScreen"
```

---

### Task 7: Navigation plumbing (prefilled category on create-event route)

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/navigation/Destinations.kt`
- Modify: `app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt`
- Modify: `app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/create/CreateVehicleEventViewModel.kt`

**Interfaces:**
- Consumes: `HomeScreen`'s `onNavigateToMaintenanceEventCreate: (vehicleId: Int, category: EventCategory) -> Unit` param (Task 6).
- Produces: `Destinations.vehicleEventCreate(vehicleId: Int, category: String? = null): String` (backward-compatible overload — existing 1-arg call sites keep compiling unchanged).

No new automated test: this is pure navigation wiring (route strings, `NavHost` argument declarations, one `SavedStateHandle` read) with no independent business logic. Verified manually via the `run-android-emulator` skill at the end of this plan (tap an oil/tire reminder card → confirm the create-event screen opens with that category pre-selected).

- [ ] **Step 1: Extend the route in `Destinations.kt`**

In `app/src/main/java/com/flowfuel/app/navigation/Destinations.kt`, change:

```kotlin
    const val VEHICLE_EVENT_CREATE  = "vehicle/events/create/{vehicleId}"
```

to:

```kotlin
    const val VEHICLE_EVENT_CREATE  = "vehicle/events/create/{vehicleId}?category={category}"
```

And change:

```kotlin
    fun vehicleEventCreate(vehicleId: Int)         = "vehicle/events/create/$vehicleId"
```

to:

```kotlin
    fun vehicleEventCreate(vehicleId: Int, category: String? = null): String {
        val base = "vehicle/events/create/$vehicleId"
        return if (category == null) base else "$base?category=$category"
    }
```

- [ ] **Step 2: Accept the optional argument in `FlowFuelNavHost.kt`**

In `app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt`, change the `VEHICLE_EVENT_CREATE` composable's arguments (around line 496-499):

```kotlin
        composable(
            route = Destinations.VEHICLE_EVENT_CREATE,
            arguments = listOf(navArgument("vehicleId") { type = NavType.IntType }),
        ) {
```

to:

```kotlin
        composable(
            route = Destinations.VEHICLE_EVENT_CREATE,
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.IntType },
                navArgument("category") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
```

- [ ] **Step 3: Add the new callback to `MainContainerScreen`**

In `app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt`, add the import:

```kotlin
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
```

Add a new parameter right after `onNavigateToEventCreate`:

```kotlin
    onNavigateToEventCreate: (vehicleId: Int) -> Unit = {},
    onNavigateToMaintenanceEventCreate: (vehicleId: Int, category: EventCategory) -> Unit = { _, _ -> },
```

In the `composable(MainDestinations.HOME) { ... }` block, pass it through to `HomeScreen`:

```kotlin
            composable(MainDestinations.HOME) {
                HomeScreen(
                    onNavigateToLogin      = onNavigateToLogin,
                    onNavigateToAddVehicle = onNavigateToAddVehicle,
                    onNavigateToMaintenanceEventCreate = onNavigateToMaintenanceEventCreate,
                    openRefuelSheet        = openRefuelSheet,
                    onRefuelSheetOpened    = { openRefuelSheet = false },
                    refreshTrigger         = homeNeedsRefresh,
                    onRefreshConsumed      = onHomeRefreshConsumed,
                )
            }
```

- [ ] **Step 4: Wire it in `FlowFuelNavHost.kt`'s `MainContainerScreen(...)` call**

In `app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt`, in the `MainContainerScreen(...)` invocation (around line 634), add a new argument right after `onNavigateToEventCreate`:

```kotlin
                onNavigateToEventCreate = { vehicleId ->
                    navController.navigate(Destinations.vehicleEventCreate(vehicleId))
                },
                onNavigateToMaintenanceEventCreate = { vehicleId, category ->
                    navController.navigate(Destinations.vehicleEventCreate(vehicleId, category.name))
                },
```

- [ ] **Step 5: Read the optional category in `CreateVehicleEventViewModel`**

In `app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/create/CreateVehicleEventViewModel.kt`, change:

```kotlin
    private val vehicleId: Int = checkNotNull(savedStateHandle["vehicleId"])

    private val _state = MutableStateFlow(
        CreateVehicleEventUiState(eventDate = LocalDate.now().toString()),
    )
```

to:

```kotlin
    private val vehicleId: Int = checkNotNull(savedStateHandle["vehicleId"])

    /** Categoria pré-selecionada ao abrir a partir de um lembrete de manutenção da Home. */
    private val initialCategory: EventCategory = savedStateHandle.get<String>("category")
        ?.let { raw -> EventCategory.entries.firstOrNull { it.name == raw } }
        ?: EventCategory.OTHER

    private val _state = MutableStateFlow(
        CreateVehicleEventUiState(category = initialCategory, eventDate = LocalDate.now().toString()),
    )
```

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (no regressions across the whole module)

- [ ] **Step 8: Manual verification**

Use the `run-android-emulator` skill to launch the app, then:
1. Open Home, confirm the "Próximos eventos" row renders 3 cards (oil change, tire rotation, licensing).
2. Tap the oil-change card → confirm it opens "Novo Evento" with "Troca de Óleo" pre-selected as the category.
3. Go back, tap the licensing card (no due date set) → confirm it shows "Defina a data de licenciamento" and tapping opens a date picker; pick a date → confirm the card updates to "Vence em N dias".
4. Pull to refresh and confirm the section reloads without flashing the other sections' skeletons.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/navigation/Destinations.kt app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/create/CreateVehicleEventViewModel.kt
git commit -m "feat(home): navega para criar evento com categoria pre-selecionada a partir dos lembretes"
```
