# Android Auto Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adicionar suporte ao Android Auto com Dashboard de stats e fluxo de registro de abastecimento em 3 steps.

**Architecture:** Cada tela é uma subclasse de `Screen` da Car App Library que retorna um `Template` síncrono em `onGetTemplate()`; estado interno (loading/success/error) é gerenciado como campos da própria tela e atualizado via `invalidate()` após coroutines que rodam no `lifecycleScope`. Dados passam por construtores entre steps (sem ViewModel compartilhado). `AutoCarAppService` recebe UseCases via Hilt field-injection (`@AndroidEntryPoint`) e os repassa ao `AutoSession` por construtor.

**Tech Stack:** Car App Library 1.4.0 (`androidx.car.app:app`), Car App Testing (`androidx.car.app:app-testing`), Hilt, kotlinx-coroutines, JUnit4 + MockK + Robolectric

## Global Constraints

- Car App Library version: `1.4.0` — não bumpar
- `minSdk 26`, `compileSdk 35`
- Category `IOT` no manifest (não Navigation, Parking nem Messaging)
- `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` — aceitável antes da revisão do Google Play
- Sem ViewModel nas telas Auto — estado via campos da Screen + construtores
- Não modificar UseCases, repositórios, ou modelos existentes
- Reuso obrigatório: `GetActiveVehicleUseCase`, `GetDashboardUseCase`, `CreateRefuelUseCase`, `SessionStore`
- `fullTank = false` em todos os abastecimentos registrados pelo Auto
- `refuelType`: `"FUEL"` apenas para híbridos (`energyType == "HYBRID"`), `null` para os demais

---

## File Map

| Arquivo | Ação | Responsabilidade |
|---------|------|-----------------|
| `gradle/libs.versions.toml` | Modificar | Adicionar alias `car-app-testing` |
| `app/build.gradle.kts` | Modificar | Adicionar `car-app` + `car-app-testing` |
| `app/src/main/AndroidManifest.xml` | Modificar | Declarar `AutoCarAppService` + meta-data automotive |
| `app/src/main/res/xml/automotive_app_desc.xml` | Criar | Declarar uso de templates |
| `feature/auto/AutoCarAppService.kt` | Criar | Entry point do Car App; Hilt + criação de Session |
| `feature/auto/AutoSession.kt` | Criar | Guarda de autenticação; decide tela inicial |
| `feature/auto/dashboard/AutoDashboardScreen.kt` | Criar | PaneTemplate com stats + botão registrar |
| `feature/auto/refuel/AutoRefuelStep1Screen.kt` | Criar | InputTemplate: km percorridos |
| `feature/auto/refuel/AutoRefuelStep2Screen.kt` | Criar | InputTemplate: litros |
| `feature/auto/refuel/AutoRefuelStep3Screen.kt` | Criar | InputTemplate: valor total |
| `feature/auto/refuel/AutoRefuelConfirmScreen.kt` | Criar | MessageTemplate: resumo + submit |
| `feature/auto/refuel/AutoRefuelSuccessScreen.kt` | Criar | MessageTemplate: sucesso + volta ao painel |
| `test/.../auto/AutoSessionTest.kt` | Criar | Testa rota auth/não-auth |
| `test/.../auto/AutoDashboardScreenTest.kt` | Criar | Testa loading/success/error/401 |
| `test/.../auto/AutoRefuelConfirmScreenTest.kt` | Criar | Testa cálculo de odômetro e submit |

---

### Task 1: Build wiring, Manifest e automotive_app_desc.xml

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/automotive_app_desc.xml`

**Context:** A versão `carApp = "1.4.0"` e o alias `car-app` já existem no catálogo. Faltam: alias de teste, dependências no gradle, service no manifest e o XML descritivo.

- [ ] **Step 1: Adicionar alias `car-app-testing` em `gradle/libs.versions.toml`**

Na seção `[libraries]`, após a linha `car-app-projected = ...`:

```toml
car-app-testing = { module = "androidx.car.app:app-testing", version.ref = "carApp" }
```

- [ ] **Step 2: Adicionar dependências em `app/build.gradle.kts`**

No bloco `dependencies`, após `implementation(libs.sentry.android)`:

```kotlin
implementation(libs.car.app)
testImplementation(libs.car.app.testing)
```

- [ ] **Step 3: Criar `app/src/main/res/xml/automotive_app_desc.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<automotiveApp>
    <uses name="template" />
</automotiveApp>
```

- [ ] **Step 4: Adicionar service e meta-data em `AndroidManifest.xml`**

Dentro de `<application>`, após o `<provider>` do FileProvider:

```xml
<service
    android:name=".feature.auto.AutoCarAppService"
    android:exported="true">
    <intent-filter>
        <action android:name="androidx.car.app.CarAppService" />
        <category android:name="androidx.car.app.category.IOT" />
    </intent-filter>
</service>

<meta-data
    android:name="com.google.android.gms.car.application"
    android:resource="@xml/automotive_app_desc" />
```

- [ ] **Step 5: Compilar**

```bash
./gradlew assembleDebug
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
    app/src/main/AndroidManifest.xml \
    app/src/main/res/xml/automotive_app_desc.xml
git commit -m "chore: wiring Android Auto — dependência, manifest e automotive_app_desc"
```

---

### Task 2: AutoCarAppService + AutoSession

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/auto/AutoCarAppService.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/auto/AutoSession.kt`
- Create: `app/src/test/java/com/flowfuel/app/feature/auto/AutoSessionTest.kt`

**Interfaces:**
- Produz: `AutoCarAppService` (entry point declarado no Manifest), `AutoSession` (passado com UseCases por construtor)
- Consome: `GetActiveVehicleUseCase`, `GetDashboardUseCase`, `CreateRefuelUseCase`, `SessionStore`

- [ ] **Step 1: Escrever o teste**

`app/src/test/java/com/flowfuel/app/feature/auto/AutoSessionTest.kt`:

```kotlin
package com.flowfuel.app.feature.auto

import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetDashboardUseCase
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.feature.auto.dashboard.AutoDashboardScreen
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoSessionTest {

    private val carContext = TestCarContext.createCarContext(
        ApplicationProvider.getApplicationContext()
    )
    private val getActiveVehicle: GetActiveVehicleUseCase = mockk()
    private val getDashboard: GetDashboardUseCase = mockk()
    private val createRefuel: CreateRefuelUseCase = mockk()
    private val sessionStore: SessionStore = mockk()

    @Test
    fun `quando sem token retorna AutoNotAuthedScreen`() {
        coEvery { sessionStore.accessToken() } returns null
        val session = AutoSession(getActiveVehicle, getDashboard, createRefuel, sessionStore)
        session.carContext  // triggers Session attachment to carContext
        val screen = session.testOnCreateScreen()
        assertFalse("Deve ser AutoNotAuthedScreen, não Dashboard",
            screen is AutoDashboardScreen)
    }

    @Test
    fun `quando com token retorna AutoDashboardScreen`() {
        coEvery { sessionStore.accessToken() } returns "valid-token"
        val session = AutoSession(getActiveVehicle, getDashboard, createRefuel, sessionStore)
        val screen = session.testOnCreateScreen()
        assertTrue(screen is AutoDashboardScreen)
    }
}
```

**Nota:** `TestCarContext` da Car App Testing expõe `testOnCreateScreen()` para verificar a tela inicial sem precisar de um headunit real.

- [ ] **Step 2: Executar o teste para confirmar que falha**

```bash
./gradlew :app:test --tests "com.flowfuel.app.feature.auto.AutoSessionTest" 2>&1 | tail -20
```

Esperado: `FAILED` — classes não existem ainda.

- [ ] **Step 3: Implementar `AutoCarAppService.kt`**

```kotlin
package com.flowfuel.app.feature.auto

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetDashboardUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AutoCarAppService : CarAppService() {

    @Inject lateinit var getActiveVehicle: GetActiveVehicleUseCase
    @Inject lateinit var getDashboard: GetDashboardUseCase
    @Inject lateinit var createRefuel: CreateRefuelUseCase
    @Inject lateinit var sessionStore: SessionStore

    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session =
        AutoSession(getActiveVehicle, getDashboard, createRefuel, sessionStore)
}
```

- [ ] **Step 4: Implementar `AutoSession.kt`**

```kotlin
package com.flowfuel.app.feature.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.feature.auto.dashboard.AutoDashboardScreen
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetDashboardUseCase
import kotlinx.coroutines.runBlocking

class AutoSession(
    private val getActiveVehicle: GetActiveVehicleUseCase,
    private val getDashboard: GetDashboardUseCase,
    private val createRefuel: CreateRefuelUseCase,
    private val sessionStore: SessionStore,
) : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        val token = runBlocking { sessionStore.accessToken() }
        return if (token.isNullOrBlank()) {
            AutoNotAuthedScreen(carContext)
        } else {
            AutoDashboardScreen(carContext, getActiveVehicle, getDashboard, createRefuel)
        }
    }
}

internal class AutoNotAuthedScreen(carContext: androidx.car.app.CarContext) : Screen(carContext) {
    override fun onGetTemplate() = MessageTemplate.Builder(
        "Abra o app FlowFuel no celular para fazer login"
    )
        .setTitle("FlowFuel")
        .setHeaderAction(Action.APP_ICON)
        .build()
}
```

- [ ] **Step 5: Executar o teste**

```bash
./gradlew :app:test --tests "com.flowfuel.app.feature.auto.AutoSessionTest" 2>&1 | tail -20
```

Esperado: `PASSED`

- [ ] **Step 6: Compilar**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/auto/AutoCarAppService.kt \
    app/src/main/java/com/flowfuel/app/feature/auto/AutoSession.kt \
    app/src/test/java/com/flowfuel/app/feature/auto/AutoSessionTest.kt
git commit -m "feat: AutoCarAppService + AutoSession com guarda de autenticação"
```

---

### Task 3: AutoDashboardScreen

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/auto/dashboard/AutoDashboardScreen.kt`
- Create: `app/src/test/java/com/flowfuel/app/feature/auto/AutoDashboardScreenTest.kt`

**Interfaces:**
- Consome: `GetActiveVehicleUseCase(): AppResult<ActiveVehicleData>`, `GetDashboardUseCase(vehicleId: Int): AppResult<DashboardData>`, `CreateRefuelUseCase` (repassado ao fluxo de abastecimento)
- Produz: `AutoDashboardScreen(carContext, getActiveVehicle, getDashboard, createRefuel)` — referenciado por `AutoSession`

**Modelos relevantes:**
```kotlin
// ActiveVehicleData
data class ActiveVehicleData(val id: Int, val brand: String, val model: String,
    val licensePlate: String?, val energyType: String, val currentKm: Int, ...)

// DashboardData
data class DashboardData(val averageConsumption: Double?, val consumptionUnit: String?,
    val totalSpent: Double, val totalRefuels: Int,
    val lastRefuelDate: String?, val lastRefuelEnergyAmount: Double?,
    val lastRefuelEnergyUnit: String?, ...)
```

- [ ] **Step 1: Escrever os testes**

`app/src/test/java/com/flowfuel/app/feature/auto/AutoDashboardScreenTest.kt`:

```kotlin
package com.flowfuel.app.feature.auto

import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.PaneTemplate
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auto.dashboard.AutoDashboardScreen
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetDashboardUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoDashboardScreenTest {

    private val carContext = TestCarContext.createCarContext(
        ApplicationProvider.getApplicationContext()
    )
    private val getActiveVehicle: GetActiveVehicleUseCase = mockk()
    private val getDashboard: GetDashboardUseCase = mockk()
    private val createRefuel: CreateRefuelUseCase = mockk()

    private val fakeVehicle = ActiveVehicleData(
        id = 1, brand = "Toyota", model = "Corolla", fuelSubType = "GASOLINE",
        capacity = 55.0, licensePlate = "ABC1234", energyType = "COMBUSTION", currentKm = 50000,
    )
    private val fakeDashboard = DashboardData(
        averageConsumption = 12.5, consumptionUnit = "km/L",
        totalSpent = 1500.0, totalRefuels = 10,
        lastRefuelDate = "2026-06-01", lastRefuelEnergyAmount = 42.0,
        lastRefuelAmount = 280.0, lastRefuelEnergyUnit = "L",
    )

    @Test
    fun `estado inicial retorna MessageTemplate de loading`() {
        coEvery { getActiveVehicle() } coAnswers { kotlinx.coroutines.delay(1000); AppResult.Success(fakeVehicle) }
        coEvery { getDashboard(any()) } coAnswers { AppResult.Success(fakeDashboard) }
        val screen = AutoDashboardScreen(carContext, getActiveVehicle, getDashboard, createRefuel)
        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `apos carregamento retorna PaneTemplate com dados do veículo`() = runTest {
        coEvery { getActiveVehicle() } returns AppResult.Success(fakeVehicle)
        coEvery { getDashboard(1) } returns AppResult.Success(fakeDashboard)
        val screen = AutoDashboardScreen(carContext, getActiveVehicle, getDashboard, createRefuel)
        advanceUntilIdle()
        val template = screen.onGetTemplate()
        assertTrue("Deve ser PaneTemplate após carregamento", template is PaneTemplate)
    }

    @Test
    fun `erro de rede retorna MessageTemplate com botao tentar novamente`() = runTest {
        coEvery { getActiveVehicle() } returns AppResult.Failure(AppError.Network)
        val screen = AutoDashboardScreen(carContext, getActiveVehicle, getDashboard, createRefuel)
        advanceUntilIdle()
        val template = screen.onGetTemplate()
        assertTrue(template is MessageTemplate)
    }

    @Test
    fun `erro 401 retorna MessageTemplate de sessão expirada`() = runTest {
        coEvery { getActiveVehicle() } returns AppResult.Failure(AppError.Unauthorized)
        val screen = AutoDashboardScreen(carContext, getActiveVehicle, getDashboard, createRefuel)
        advanceUntilIdle()
        val template = screen.onGetTemplate()
        assertTrue(template is MessageTemplate)
    }
}
```

- [ ] **Step 2: Executar os testes para confirmar que falham**

```bash
./gradlew :app:test --tests "com.flowfuel.app.feature.auto.AutoDashboardScreenTest" 2>&1 | tail -20
```

Esperado: `FAILED` — classe não existe.

- [ ] **Step 3: Implementar `AutoDashboardScreen.kt`**

```kotlin
package com.flowfuel.app.feature.auto.dashboard

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auto.refuel.AutoRefuelStep1Screen
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetDashboardUseCase
import kotlinx.coroutines.launch
import java.util.Locale

class AutoDashboardScreen(
    carContext: CarContext,
    private val getActiveVehicle: GetActiveVehicleUseCase,
    private val getDashboard: GetDashboardUseCase,
    private val createRefuel: CreateRefuelUseCase,
) : Screen(carContext) {

    private sealed interface State {
        data object Loading : State
        data class Success(val vehicle: ActiveVehicleData, val dashboard: DashboardData) : State
        data class Error(val message: String, val unauthorized: Boolean = false) : State
    }

    private var state: State = State.Loading

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { loadData() }
        })
    }

    override fun onGetTemplate(): Template = when (val s = state) {
        is State.Loading -> MessageTemplate.Builder("Carregando…")
            .setTitle("FlowFuel")
            .setLoading(true)
            .build()

        is State.Error -> MessageTemplate.Builder(s.message)
            .setTitle("FlowFuel")
            .setHeaderAction(Action.APP_ICON)
            .apply {
                if (!s.unauthorized) {
                    addAction(
                        Action.Builder()
                            .setTitle("Tentar novamente")
                            .setOnClickListener { loadData() }
                            .build()
                    )
                }
            }
            .build()

        is State.Success -> buildPaneTemplate(s.vehicle, s.dashboard)
    }

    private fun buildPaneTemplate(vehicle: ActiveVehicleData, dashboard: DashboardData): Template {
        val title = buildString {
            append(vehicle.brand)
            append(" ")
            append(vehicle.model)
            if (vehicle.licensePlate != null) append(" (${vehicle.licensePlate})")
        }

        val consumptionText = if (dashboard.averageConsumption != null) {
            "%.1f %s".format(dashboard.averageConsumption, dashboard.consumptionUnit ?: "")
        } else {
            "—"
        }

        val totalSpentText = "R$ %,.2f".format(dashboard.totalSpent)

        val lastRefuelText = if (dashboard.lastRefuelDate != null && dashboard.lastRefuelEnergyAmount != null) {
            val date = dashboard.lastRefuelDate.take(10).replace("-", "/").let {
                val parts = it.split("/")
                "${parts[2]}/${parts[1]}"
            }
            "%.1f %s".format(dashboard.lastRefuelEnergyAmount, dashboard.lastRefuelEnergyUnit ?: "L").let { amt ->
                "$date • $amt"
            }
        } else {
            "Nenhum ainda"
        }

        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle("Consumo médio").addText(consumptionText).build())
            .addRow(Row.Builder().setTitle("Gasto total").addText(totalSpentText).build())
            .addRow(Row.Builder().setTitle("Último abastecimento").addText(lastRefuelText).build())
            .addAction(
                Action.Builder()
                    .setTitle("Registrar abastecimento")
                    .setOnClickListener {
                        screenManager.push(
                            AutoRefuelStep1Screen(
                                carContext,
                                vehicle = (state as? State.Success)?.vehicle ?: return@setOnClickListener,
                                createRefuel = createRefuel,
                            )
                        )
                    }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle(title)
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun loadData() {
        state = State.Loading
        invalidate()
        lifecycleScope.launch {
            when (val vehicleResult = getActiveVehicle()) {
                is AppResult.Failure -> {
                    state = if (vehicleResult.error == AppError.Unauthorized) {
                        State.Error(
                            "Sessão expirada. Abra o FlowFuel no celular para entrar novamente.",
                            unauthorized = true,
                        )
                    } else {
                        State.Error("Não foi possível carregar os dados. Verifique sua conexão.")
                    }
                    invalidate()
                }
                is AppResult.Success -> {
                    val vehicle = vehicleResult.value
                    when (val dashResult = getDashboard(vehicle.id)) {
                        is AppResult.Failure -> {
                            state = State.Error("Não foi possível carregar o painel.")
                            invalidate()
                        }
                        is AppResult.Success -> {
                            state = State.Success(vehicle, dashResult.value)
                            invalidate()
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Executar os testes**

```bash
./gradlew :app:test --tests "com.flowfuel.app.feature.auto.AutoDashboardScreenTest" 2>&1 | tail -20
```

Esperado: `PASSED` (4 testes)

- [ ] **Step 5: Compilar**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/auto/dashboard/AutoDashboardScreen.kt \
    app/src/test/java/com/flowfuel/app/feature/auto/AutoDashboardScreenTest.kt
git commit -m "feat: AutoDashboardScreen com PaneTemplate de stats e estado loading/erro"
```

---

### Task 4: Fluxo de Abastecimento (Steps 1–3 + Confirm + Success)

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/auto/refuel/AutoRefuelStep1Screen.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/auto/refuel/AutoRefuelStep2Screen.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/auto/refuel/AutoRefuelStep3Screen.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/auto/refuel/AutoRefuelConfirmScreen.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/auto/refuel/AutoRefuelSuccessScreen.kt`
- Create: `app/src/test/java/com/flowfuel/app/feature/auto/AutoRefuelConfirmScreenTest.kt`

**Interfaces:**
- Consome: `ActiveVehicleData` (da Task 3), `CreateRefuelUseCase`
- `CreateRefuelRequest(vehicleId, odometer, liters, totalPrice, fullTank=false, refuelType?)`
- `refuelType`: `"FUEL"` se `vehicle.energyType == "HYBRID"`, senão `null`
- `odometer`: `vehicle.currentKm.toDouble() + tripKm`

- [ ] **Step 1: Escrever os testes para `AutoRefuelConfirmScreen`**

`app/src/test/java/com/flowfuel/app/feature/auto/AutoRefuelConfirmScreenTest.kt`:

```kotlin
package com.flowfuel.app.feature.auto

import androidx.car.app.model.MessageTemplate
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auto.refuel.AutoRefuelConfirmScreen
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.CreateRefuelRequest
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoRefuelConfirmScreenTest {

    private val carContext = TestCarContext.createCarContext(
        ApplicationProvider.getApplicationContext()
    )
    private val createRefuel: CreateRefuelUseCase = mockk()

    private val combustionVehicle = ActiveVehicleData(
        id = 7, brand = "Honda", model = "Civic", fuelSubType = "GASOLINE",
        capacity = 47.0, licensePlate = "XYZ9876", energyType = "COMBUSTION", currentKm = 80000,
    )
    private val hybridVehicle = combustionVehicle.copy(id = 8, energyType = "HYBRID")

    @Test
    fun `submit calcula odometro corretamente para veiculo combustao`() = runTest {
        coEvery { createRefuel(any()) } returns AppResult.Success(Unit)
        val screen = AutoRefuelConfirmScreen(
            carContext = carContext,
            vehicle = combustionVehicle,
            tripKm = 150.0,
            liters = 45.5,
            totalPrice = 289.90,
            createRefuel = createRefuel,
        )
        screen.testSubmit()
        advanceUntilIdle()
        coVerify {
            createRefuel(
                CreateRefuelRequest(
                    vehicleId = 7,
                    odometer = 80150.0,
                    liters = 45.5,
                    totalPrice = 289.90,
                    fullTank = false,
                    refuelType = null,
                )
            )
        }
    }

    @Test
    fun `submit usa refuelType FUEL para veiculo hibrido`() = runTest {
        coEvery { createRefuel(any()) } returns AppResult.Success(Unit)
        val screen = AutoRefuelConfirmScreen(
            carContext = carContext,
            vehicle = hybridVehicle,
            tripKm = 100.0,
            liters = 30.0,
            totalPrice = 200.0,
            createRefuel = createRefuel,
        )
        screen.testSubmit()
        advanceUntilIdle()
        coVerify {
            createRefuel(match { it.refuelType == "FUEL" })
        }
    }

    @Test
    fun `erro 401 durante submit exibe MessageTemplate de sessão expirada`() = runTest {
        coEvery { createRefuel(any()) } returns AppResult.Failure(AppError.Unauthorized)
        val screen = AutoRefuelConfirmScreen(
            carContext = carContext,
            vehicle = combustionVehicle,
            tripKm = 100.0,
            liters = 40.0,
            totalPrice = 250.0,
            createRefuel = createRefuel,
        )
        screen.testSubmit()
        advanceUntilIdle()
        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `erro de rede exibe MessageTemplate com botao tentar novamente`() = runTest {
        coEvery { createRefuel(any()) } returns AppResult.Failure(AppError.Network)
        val screen = AutoRefuelConfirmScreen(
            carContext = carContext,
            vehicle = combustionVehicle,
            tripKm = 100.0,
            liters = 40.0,
            totalPrice = 250.0,
            createRefuel = createRefuel,
        )
        screen.testSubmit()
        advanceUntilIdle()
        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }
}
```

- [ ] **Step 2: Executar os testes para confirmar que falham**

```bash
./gradlew :app:test --tests "com.flowfuel.app.feature.auto.AutoRefuelConfirmScreenTest" 2>&1 | tail -20
```

Esperado: `FAILED` — classes não existem.

- [ ] **Step 3: Implementar `AutoRefuelStep1Screen.kt`**

```kotlin
package com.flowfuel.app.feature.auto.refuel

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.InputTemplate
import androidx.car.app.model.Template
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase

class AutoRefuelStep1Screen(
    carContext: CarContext,
    private val vehicle: ActiveVehicleData,
    private val createRefuel: CreateRefuelUseCase,
) : Screen(carContext) {

    override fun onGetTemplate(): Template = InputTemplate.Builder()
        .setTitle("Abastecimento — Passo 1 de 3")
        .setHeaderAction(Action.BACK)
        .setHint("Ex: 150")
        .setInputType(InputTemplate.INPUT_TYPE_DEFAULT)
        .setOnInputCompletedListener { text ->
            val km = text.trim().replace(",", ".").toDoubleOrNull()
            if (km == null || km <= 0) {
                CarToast.makeText(
                    carContext,
                    "Informe km percorridos válidos (ex: 150)",
                    CarToast.LENGTH_SHORT,
                ).show()
            } else {
                screenManager.push(
                    AutoRefuelStep2Screen(carContext, vehicle, tripKm = km, createRefuel)
                )
            }
        }
        .build()
}
```

- [ ] **Step 4: Implementar `AutoRefuelStep2Screen.kt`**

```kotlin
package com.flowfuel.app.feature.auto.refuel

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.InputTemplate
import androidx.car.app.model.Template
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase

class AutoRefuelStep2Screen(
    carContext: CarContext,
    private val vehicle: ActiveVehicleData,
    private val tripKm: Double,
    private val createRefuel: CreateRefuelUseCase,
) : Screen(carContext) {

    override fun onGetTemplate(): Template = InputTemplate.Builder()
        .setTitle("Abastecimento — Passo 2 de 3")
        .setHeaderAction(Action.BACK)
        .setHint("Ex: 45,5")
        .setInputType(InputTemplate.INPUT_TYPE_DEFAULT)
        .setOnInputCompletedListener { text ->
            val liters = text.trim().replace(",", ".").toDoubleOrNull()
            if (liters == null || liters <= 0) {
                CarToast.makeText(
                    carContext,
                    "Informe litros abastecidos válidos (ex: 45,5)",
                    CarToast.LENGTH_SHORT,
                ).show()
            } else {
                screenManager.push(
                    AutoRefuelStep3Screen(carContext, vehicle, tripKm, liters, createRefuel)
                )
            }
        }
        .build()
}
```

- [ ] **Step 5: Implementar `AutoRefuelStep3Screen.kt`**

```kotlin
package com.flowfuel.app.feature.auto.refuel

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.InputTemplate
import androidx.car.app.model.Template
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase

class AutoRefuelStep3Screen(
    carContext: CarContext,
    private val vehicle: ActiveVehicleData,
    private val tripKm: Double,
    private val liters: Double,
    private val createRefuel: CreateRefuelUseCase,
) : Screen(carContext) {

    override fun onGetTemplate(): Template = InputTemplate.Builder()
        .setTitle("Abastecimento — Passo 3 de 3")
        .setHeaderAction(Action.BACK)
        .setHint("Ex: 289,90")
        .setInputType(InputTemplate.INPUT_TYPE_DEFAULT)
        .setOnInputCompletedListener { text ->
            val price = text.trim().replace(",", ".").toDoubleOrNull()
            if (price == null || price <= 0) {
                CarToast.makeText(
                    carContext,
                    "Informe o valor total válido (ex: 289,90)",
                    CarToast.LENGTH_SHORT,
                ).show()
            } else {
                screenManager.push(
                    AutoRefuelConfirmScreen(carContext, vehicle, tripKm, liters, price, createRefuel)
                )
            }
        }
        .build()
}
```

- [ ] **Step 6: Implementar `AutoRefuelConfirmScreen.kt`**

```kotlin
package com.flowfuel.app.feature.auto.refuel

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.CreateRefuelRequest
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import kotlinx.coroutines.launch

class AutoRefuelConfirmScreen(
    carContext: CarContext,
    private val vehicle: ActiveVehicleData,
    private val tripKm: Double,
    private val liters: Double,
    private val totalPrice: Double,
    private val createRefuel: CreateRefuelUseCase,
) : Screen(carContext) {

    private sealed interface State {
        data object Idle : State
        data object Submitting : State
        data class Error(val message: String) : State
    }

    private var state: State = State.Idle

    override fun onGetTemplate(): Template = when (val s = state) {
        is State.Idle, is State.Submitting -> MessageTemplate.Builder(
            buildString {
                appendLine("Percurso: %.0f km".format(tripKm))
                appendLine("Litros: %.1f L".format(liters))
                append("Valor: R$ %.2f".format(totalPrice))
            }
        )
            .setTitle("Confirmar abastecimento")
            .setHeaderAction(Action.BACK)
            .setLoading(s is State.Submitting)
            .apply {
                if (s is State.Idle) {
                    addAction(
                        Action.Builder()
                            .setTitle("Confirmar")
                            .setOnClickListener { submit() }
                            .build()
                    )
                    addAction(
                        Action.Builder()
                            .setTitle("Corrigir")
                            .setOnClickListener { screenManager.popToRoot() }
                            .build()
                    )
                }
            }
            .build()

        is State.Error -> MessageTemplate.Builder(s.message)
            .setTitle("Erro")
            .setHeaderAction(Action.BACK)
            .apply {
                if (s.message != "Sessão expirada. Abra o FlowFuel no celular para entrar novamente.") {
                    addAction(
                        Action.Builder()
                            .setTitle("Tentar novamente")
                            .setOnClickListener {
                                state = State.Idle
                                invalidate()
                            }
                            .build()
                    )
                }
                addAction(
                    Action.Builder()
                        .setTitle("Cancelar")
                        .setOnClickListener { screenManager.popToRoot() }
                        .build()
                )
            }
            .build()
    }

    internal fun testSubmit() = submit()

    private fun submit() {
        if (state is State.Submitting) return
        state = State.Submitting
        invalidate()
        lifecycleScope.launch {
            val odometer = vehicle.currentKm.toDouble() + tripKm
            val refuelType = if (vehicle.energyType == "HYBRID") "FUEL" else null
            val request = CreateRefuelRequest(
                vehicleId = vehicle.id,
                odometer = odometer,
                liters = liters,
                totalPrice = totalPrice,
                fullTank = false,
                refuelType = refuelType,
            )
            when (val result = createRefuel(request)) {
                is AppResult.Success -> {
                    screenManager.push(AutoRefuelSuccessScreen(carContext))
                }
                is AppResult.Failure -> {
                    state = State.Error(
                        if (result.error == AppError.Unauthorized)
                            "Sessão expirada. Abra o FlowFuel no celular para entrar novamente."
                        else
                            "Não foi possível registrar. Verifique sua conexão e tente novamente."
                    )
                    invalidate()
                }
            }
        }
    }
}
```

- [ ] **Step 7: Implementar `AutoRefuelSuccessScreen.kt`**

```kotlin
package com.flowfuel.app.feature.auto.refuel

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

class AutoRefuelSuccessScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = MessageTemplate.Builder(
        "Abastecimento registrado com sucesso!"
    )
        .setTitle("FlowFuel")
        .setHeaderAction(Action.APP_ICON)
        .addAction(
            Action.Builder()
                .setTitle("Voltar ao painel")
                .setOnClickListener { screenManager.popToRoot() }
                .build()
        )
        .build()
}
```

- [ ] **Step 8: Executar os testes**

```bash
./gradlew :app:test --tests "com.flowfuel.app.feature.auto.AutoRefuelConfirmScreenTest" 2>&1 | tail -20
```

Esperado: `PASSED` (4 testes)

- [ ] **Step 9: Executar todos os testes do módulo Auto**

```bash
./gradlew :app:test --tests "com.flowfuel.app.feature.auto.*" 2>&1 | tail -20
```

Esperado: todos `PASSED`

- [ ] **Step 10: Compilar**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 11: Commit**

```bash
git add \
    app/src/main/java/com/flowfuel/app/feature/auto/refuel/ \
    app/src/test/java/com/flowfuel/app/feature/auto/AutoRefuelConfirmScreenTest.kt
git commit -m "feat: fluxo de abastecimento no Android Auto (steps 1-3, confirm, success)"
```

---

## Self-Review

### Spec coverage

| Requisito do spec | Task |
|---|---|
| Dependência `androidx.car.app:app:1.4.0` | Task 1 |
| `automotive_app_desc.xml` com `<uses name="template" />` | Task 1 |
| Service `AutoCarAppService` no manifest, categoria `IOT` | Task 1 |
| `AutoCarAppService` com `@AndroidEntryPoint` + Hilt | Task 2 |
| `AutoSession` com guarda de token via `runBlocking` | Task 2 |
| MessageTemplate "Abra o app FlowFuel no celular para fazer login" sem botão de login | Task 2 |
| `AutoDashboardScreen` — PaneTemplate com 3 rows e ação registrar | Task 3 |
| Estado de loading (MessageTemplate com `setLoading(true)`) | Task 3 |
| Estado de erro com "Tentar novamente" | Task 3 |
| Erro 401 — MessageTemplate de sessão expirada, sem retry | Task 3 |
| `AutoRefuelStep1/2/3Screen` com `InputTemplate` + `CarToast` na validação | Task 4 |
| `AutoRefuelConfirmScreen` — resumo + Confirmar + Corrigir | Task 4 |
| `odometer = vehicle.currentKm.toDouble() + tripKm` | Task 4 |
| `refuelType = "FUEL"` apenas para híbridos | Task 4 |
| `fullTank = false` | Task 4 |
| `AutoRefuelSuccessScreen` com "Voltar ao painel" → `popToRoot()` | Task 4 |
| Testes: `AutoSession` (sem token / com token) | Task 2 |
| Testes: `AutoDashboardScreen` (loading/success/error/401) | Task 3 |
| Testes: `AutoRefuelConfirmScreen` (odômetro, tipo, 401, rede) | Task 4 |

### Sem placeholders ✓
### Consistência de tipos ✓ — `ActiveVehicleData`, `CreateRefuelRequest`, `CreateRefuelUseCase` usados com as assinaturas exatas do código existente
