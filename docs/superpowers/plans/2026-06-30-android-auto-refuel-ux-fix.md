# Android Auto — Correção UX/UI do Fluxo de Registro + Dashboard Melhorado

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corrigir 3 bugs no fluxo de registro de abastecimento no Android Auto e enriquecer o dashboard com mais informações de consumo e gasto. Ao final, gerar APK de release assinado.

**Architecture:** Os 3 passos do formulário usam `SearchTemplate` (template de busca) em vez de `InputTemplate` (template de formulário). A substituição resolve o problema principal — ausência de botão explícito de submissão. Os outros dois bugs são correções pontuais no `AutoRefuelConfirmScreen`. O dashboard ganha uma 4ª linha com total de abastecimentos e exibe o valor monetário do último abastecimento.

**Tech Stack:** Kotlin, Car App Library 1.4.0, Robolectric + `TestCarContext`

## Global Constraints

- Car App Library: `androidx.car.app:app:1.4.0` (já declarada no `app/build.gradle.kts`)
- `InputTemplate` requer Car App API level 2 — o manifest deve declarar `minCarApiLevel = 2`
- Testes usam `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [33])`, padrão do projeto
- Não introduzir ViewModel nem estado compartilhado — o estado do formulário é passado via construtores (decisão de design existente)
- Todos os templates do Auto devem ter `setHeaderAction(Action.BACK)` ou `Action.APP_ICON`

---

### Task 1: Substituir SearchTemplate por InputTemplate nos Steps 1, 2 e 3

Os 3 screens de input precisam trocar `SearchTemplate.Builder(SearchCallback)` por `InputTemplate.Builder(InputCallback)` e adicionar um botão de ação "Próximo" explícito. A validação acontece tanto no botão quanto no `onInputSubmitted` (tecla Enter do teclado).

O manifest também precisa declarar `minCarApiLevel = 2` para que o Car App Library saiba que `InputTemplate` é necessário.

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/auto/refuel/AutoRefuelStep1Screen.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/auto/refuel/AutoRefuelStep2Screen.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/auto/refuel/AutoRefuelStep3Screen.kt`
- Modify: `app/src/main/AndroidManifest.xml` (minCarApiLevel: 1 → 2)
- Create: `app/src/test/java/com/flowfuel/app/feature/auto/AutoRefuelStepScreensTest.kt`

**Interfaces:**
- Consumes: `ActiveVehicleData`, `CreateRefuelUseCase` (sem alteração de assinatura)
- Produces:
  - `AutoRefuelStep1Screen` com `internal fun testAdvance(text: String)`
  - `AutoRefuelStep2Screen` com `internal fun testAdvance(text: String)`
  - `AutoRefuelStep3Screen` com `internal fun testAdvance(text: String)`

---

- [ ] **Step 1: Escrever os testes que vão falhar**

Criar o arquivo `app/src/test/java/com/flowfuel/app/feature/auto/AutoRefuelStepScreensTest.kt`:

```kotlin
package com.flowfuel.app.feature.auto

import androidx.car.app.model.InputTemplate
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.feature.auto.refuel.AutoRefuelStep1Screen
import com.flowfuel.app.feature.auto.refuel.AutoRefuelStep2Screen
import com.flowfuel.app.feature.auto.refuel.AutoRefuelStep3Screen
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoRefuelStepScreensTest {

    private val carContext = TestCarContext.createCarContext(
        ApplicationProvider.getApplicationContext()
    )
    private val createRefuel: CreateRefuelUseCase = mockk()
    private val vehicle = ActiveVehicleData(
        id = 1, brand = "Toyota", model = "Corolla", fuelSubType = "GASOLINE",
        capacity = 50.0, licensePlate = "ABC1234", energyType = "COMBUSTION", currentKm = 50000,
    )

    // ─── Step 1 ───────────────────────────────────────────────────────────────

    @Test
    fun `Step1 retorna InputTemplate`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        assertTrue(screen.onGetTemplate() is InputTemplate)
    }

    @Test
    fun `Step1 input invalido zero mantem template sem excecao`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        screen.testAdvance("0")
        assertTrue(screen.onGetTemplate() is InputTemplate)
    }

    @Test
    fun `Step1 input invalido texto mantem template sem excecao`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        screen.testAdvance("abc")
        assertTrue(screen.onGetTemplate() is InputTemplate)
    }

    @Test
    fun `Step1 input valido nao lanca excecao`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        screen.testAdvance("150")
    }

    @Test
    fun `Step1 aceita virgula como separador decimal`() {
        val screen = AutoRefuelStep1Screen(carContext, vehicle, createRefuel)
        screen.testAdvance("150,5")
    }

    // ─── Step 2 ───────────────────────────────────────────────────────────────

    @Test
    fun `Step2 retorna InputTemplate`() {
        val screen = AutoRefuelStep2Screen(carContext, vehicle, tripKm = 100.0, createRefuel)
        assertTrue(screen.onGetTemplate() is InputTemplate)
    }

    @Test
    fun `Step2 input invalido mantem template sem excecao`() {
        val screen = AutoRefuelStep2Screen(carContext, vehicle, tripKm = 100.0, createRefuel)
        screen.testAdvance("0")
        assertTrue(screen.onGetTemplate() is InputTemplate)
    }

    @Test
    fun `Step2 input valido nao lanca excecao`() {
        val screen = AutoRefuelStep2Screen(carContext, vehicle, tripKm = 100.0, createRefuel)
        screen.testAdvance("45,5")
    }

    // ─── Step 3 ───────────────────────────────────────────────────────────────

    @Test
    fun `Step3 retorna InputTemplate`() {
        val screen = AutoRefuelStep3Screen(carContext, vehicle, tripKm = 100.0, liters = 45.5, createRefuel)
        assertTrue(screen.onGetTemplate() is InputTemplate)
    }

    @Test
    fun `Step3 input invalido mantem template sem excecao`() {
        val screen = AutoRefuelStep3Screen(carContext, vehicle, tripKm = 100.0, liters = 45.5, createRefuel)
        screen.testAdvance("0")
        assertTrue(screen.onGetTemplate() is InputTemplate)
    }

    @Test
    fun `Step3 input valido nao lanca excecao`() {
        val screen = AutoRefuelStep3Screen(carContext, vehicle, tripKm = 100.0, liters = 45.5, createRefuel)
        screen.testAdvance("289,90")
    }
}
```

- [ ] **Step 2: Rodar os testes e confirmar que falham**

```
./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.auto.AutoRefuelStepScreensTest" --info
```

Esperado: compilação falha com `Unresolved reference: testAdvance` (os métodos ainda não existem).

- [ ] **Step 3: Reescrever AutoRefuelStep1Screen com InputTemplate**

Substituir todo o conteúdo de `app/src/main/java/com/flowfuel/app/feature/auto/refuel/AutoRefuelStep1Screen.kt`:

```kotlin
package com.flowfuel.app.feature.auto.refuel

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.InputCallback
import androidx.car.app.model.InputTemplate
import androidx.car.app.model.Template
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase

class AutoRefuelStep1Screen(
    carContext: CarContext,
    private val vehicle: ActiveVehicleData,
    private val createRefuel: CreateRefuelUseCase,
) : Screen(carContext) {

    private var inputText: String = ""

    internal fun testAdvance(text: String) = advance(text)

    private fun advance(text: String) {
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

    override fun onGetTemplate(): Template = InputTemplate.Builder(
        object : InputCallback {
            override fun onInputTextChanged(text: String) { inputText = text }
            override fun onInputSubmitted(text: String) { advance(text) }
        }
    )
        .setTitle("Passo 1 de 3")
        .setHeaderAction(Action.BACK)
        .setHint("Km percorridos desde o último abastecimento (ex: 150)")
        .addAction(
            Action.Builder()
                .setTitle("Próximo")
                .setOnClickListener { advance(inputText) }
                .build()
        )
        .build()
}
```

- [ ] **Step 4: Reescrever AutoRefuelStep2Screen com InputTemplate**

Substituir todo o conteúdo de `app/src/main/java/com/flowfuel/app/feature/auto/refuel/AutoRefuelStep2Screen.kt`:

```kotlin
package com.flowfuel.app.feature.auto.refuel

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.InputCallback
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

    private var inputText: String = ""

    internal fun testAdvance(text: String) = advance(text)

    private fun advance(text: String) {
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

    override fun onGetTemplate(): Template = InputTemplate.Builder(
        object : InputCallback {
            override fun onInputTextChanged(text: String) { inputText = text }
            override fun onInputSubmitted(text: String) { advance(text) }
        }
    )
        .setTitle("Passo 2 de 3")
        .setHeaderAction(Action.BACK)
        .setHint("Litros abastecidos (ex: 45,5)")
        .addAction(
            Action.Builder()
                .setTitle("Próximo")
                .setOnClickListener { advance(inputText) }
                .build()
        )
        .build()
}
```

- [ ] **Step 5: Reescrever AutoRefuelStep3Screen com InputTemplate**

Substituir todo o conteúdo de `app/src/main/java/com/flowfuel/app/feature/auto/refuel/AutoRefuelStep3Screen.kt`:

```kotlin
package com.flowfuel.app.feature.auto.refuel

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.InputCallback
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

    private var inputText: String = ""

    internal fun testAdvance(text: String) = advance(text)

    private fun advance(text: String) {
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

    override fun onGetTemplate(): Template = InputTemplate.Builder(
        object : InputCallback {
            override fun onInputTextChanged(text: String) { inputText = text }
            override fun onInputSubmitted(text: String) { advance(text) }
        }
    )
        .setTitle("Passo 3 de 3")
        .setHeaderAction(Action.BACK)
        .setHint("Valor total pago em R$ (ex: 289,90)")
        .addAction(
            Action.Builder()
                .setTitle("Próximo")
                .setOnClickListener { advance(inputText) }
                .build()
        )
        .build()
}
```

- [ ] **Step 6: Atualizar minCarApiLevel no manifest**

Em `app/src/main/AndroidManifest.xml`, localizar e alterar:

```xml
<!-- Antes -->
android:value="1"

<!-- Depois — InputTemplate requer API level 2 -->
android:value="2"
```

A linha está dentro do bloco `<meta-data android:name="androidx.car.app.minCarApiLevel" .../>`.

- [ ] **Step 7: Rodar os testes e confirmar que passam**

```
./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.auto.AutoRefuelStepScreensTest"
```

Esperado: `BUILD SUCCESSFUL`, todos os testes passam.

- [ ] **Step 8: Commit**

```
git add app/src/main/java/com/flowfuel/app/feature/auto/refuel/AutoRefuelStep1Screen.kt
git add app/src/main/java/com/flowfuel/app/feature/auto/refuel/AutoRefuelStep2Screen.kt
git add app/src/main/java/com/flowfuel/app/feature/auto/refuel/AutoRefuelStep3Screen.kt
git add app/src/main/AndroidManifest.xml
git add app/src/test/java/com/flowfuel/app/feature/auto/AutoRefuelStepScreensTest.kt
git commit -m "fix(auto): substituir SearchTemplate por InputTemplate nos passos do formulário de abastecimento"
```

---

### Task 2: Corrigir refuelType e navegação do botão "Corrigir"

Dois bugs no `AutoRefuelConfirmScreen`:

1. **`refuelType` errado** — atualmente só funciona para veículos híbridos (`HYBRID → "FUEL"`), mas retorna `null` para combustão e elétrico. Deve ser `"FUEL"` para tudo exceto elétrico, e `"ELECTRIC"` para elétrico.

2. **Botão "Corrigir" vai ao Dashboard** — usa `screenManager.popToRoot()`, que esvazia a pilha até o Dashboard. Deve voltar ao Step 1 para que o usuário recomece o formulário.

O teste existente `AutoRefuelConfirmScreenTest` também está errado: espera `refuelType = null` para veículos de combustão, quando deveria ser `"FUEL"`.

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/auto/refuel/AutoRefuelConfirmScreen.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/auto/AutoRefuelConfirmScreenTest.kt`

**Interfaces:**
- Consumes: `AutoRefuelStep1Screen` (para navegar de volta ao início do formulário)
- Produces: comportamento corrigido de `refuelType` e navegação do "Corrigir"

---

- [ ] **Step 1: Atualizar os testes existentes e adicionar novos**

Substituir todo o conteúdo de `app/src/test/java/com/flowfuel/app/feature/auto/AutoRefuelConfirmScreenTest.kt`:

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoRefuelConfirmScreenTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val carContext = TestCarContext.createCarContext(
        ApplicationProvider.getApplicationContext()
    )
    private val createRefuel: CreateRefuelUseCase = mockk()

    private val combustionVehicle = ActiveVehicleData(
        id = 7, brand = "Honda", model = "Civic", fuelSubType = "GASOLINE",
        capacity = 47.0, licensePlate = "XYZ9876", energyType = "COMBUSTION", currentKm = 80000,
    )
    private val hybridVehicle = combustionVehicle.copy(id = 8, energyType = "HYBRID")
    private val electricVehicle = combustionVehicle.copy(id = 9, energyType = "ELECTRIC")

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
                    refuelType = "FUEL",  // combustão → "FUEL"
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
    fun `submit usa refuelType ELECTRIC para veiculo eletrico`() = runTest {
        coEvery { createRefuel(any()) } returns AppResult.Success(Unit)
        val screen = AutoRefuelConfirmScreen(
            carContext = carContext,
            vehicle = electricVehicle,
            tripKm = 80.0,
            liters = 30.0,
            totalPrice = 60.0,
            createRefuel = createRefuel,
        )
        screen.testSubmit()
        advanceUntilIdle()
        coVerify {
            createRefuel(match { it.refuelType == "ELECTRIC" })
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

- [ ] **Step 2: Rodar os testes e confirmar que o de combustão falha**

```
./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.auto.AutoRefuelConfirmScreenTest"
```

Esperado: teste `submit calcula odometro corretamente para veiculo combustao` falha com mensagem indicando que `refuelType` recebido foi `null` mas esperava `"FUEL"`. Testes de híbrido e erro devem continuar passando.

- [ ] **Step 3: Corrigir refuelType no AutoRefuelConfirmScreen**

Em `app/src/main/java/com/flowfuel/app/feature/auto/refuel/AutoRefuelConfirmScreen.kt`, linha 95, substituir:

```kotlin
// Antes
val refuelType = if (vehicle.energyType == "HYBRID") "FUEL" else null
```

por:

```kotlin
// Depois — elétrico → "ELECTRIC", qualquer outro (combustão, híbrido) → "FUEL"
val refuelType = if (vehicle.energyType == "ELECTRIC") "ELECTRIC" else "FUEL"
```

- [ ] **Step 4: Corrigir navegação do botão "Corrigir"**

No mesmo arquivo `AutoRefuelConfirmScreen.kt`, linha 56, substituir:

```kotlin
// Antes — vai ao Dashboard e descarta todos os dados
.setOnClickListener { screenManager.popToRoot() }
```

por:

```kotlin
// Depois — volta ao Step 1 para o usuário reiniciar o formulário
.setOnClickListener {
    screenManager.popToRoot()
    screenManager.push(AutoRefuelStep1Screen(carContext, vehicle, createRefuel))
}
```

> **Nota:** A pilha ao chegar no ConfirmScreen é `[Dashboard → Step1 → Step2 → Step3 → Confirm]`. O `popToRoot()` limpa tudo até o Dashboard; em seguida o `push()` coloca Step1 no topo. O usuário recomeça o formulário sem sair do contexto do Auto.

- [ ] **Step 5: Rodar todos os testes do módulo Auto**

```
./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.auto.*"
```

Esperado: `BUILD SUCCESSFUL`, todos os testes passam (incluindo o novo de elétrico e o corrigido de combustão).

- [ ] **Step 6: Commit**

```
git add app/src/main/java/com/flowfuel/app/feature/auto/refuel/AutoRefuelConfirmScreen.kt
git add app/src/test/java/com/flowfuel/app/feature/auto/AutoRefuelConfirmScreenTest.kt
git commit -m "fix(auto): corrigir refuelType para combustão/elétrico e navegação do botão Corrigir"
```

---

---

### Task 3: Dashboard Melhorado — 4 linhas com dados de gasto e abastecimento

O `AutoDashboardScreen` atual exibe 3 linhas: consumo médio, gasto total e último abastecimento. Com o `minCarApiLevel = 2` declarado na Task 1, o `PaneTemplate` suporta até 4 linhas. Adicionamos a linha "Abastecimentos" com `totalRefuels` e enriquecemos o "Último abastecimento" com o valor em R$ quando disponível.

`DashboardData` já expõe: `totalRefuels: Int`, `lastRefuelAmount: Double?` (valor monetário), `lastRefuelEnergyAmount: Double?` (litros/kWh), `lastRefuelEnergyUnit: String?`, `lastRefuelDate: String?`.

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/auto/dashboard/AutoDashboardScreen.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/auto/AutoDashboardScreenTest.kt`

**Interfaces:**
- Consumes: `DashboardData.totalRefuels`, `DashboardData.lastRefuelAmount` (já existentes no model)
- Produces: dashboard com 4 linhas; nenhuma interface nova para outras tasks

---

- [ ] **Step 1: Ler o teste existente do dashboard para entender o que já está coberto**

```
cat app/src/test/java/com/flowfuel/app/feature/auto/AutoDashboardScreenTest.kt
```

(Leitura para não apagar cobertura existente ao atualizar o arquivo.)

- [ ] **Step 2: Adicionar testes para as novas linhas**

Adicionar ao final da classe `AutoDashboardScreenTest`, dentro do `}` final da classe, os seguintes testes:

```kotlin
@Test
fun `dashboard exibe total de abastecimentos`() = runTest {
    coEvery { getActiveVehicle() } returns AppResult.Success(vehicle)
    coEvery { getDashboard(vehicle.id) } returns AppResult.Success(
        dashboard.copy(totalRefuels = 12)
    )
    val screen = AutoDashboardScreen(carContext, getActiveVehicle, getDashboard, createRefuel)
    screen.loadData()
    advanceUntilIdle()
    val template = screen.onGetTemplate() as PaneTemplate
    val rows = template.pane!!.rows
    assertTrue("deve ter pelo menos 4 linhas", rows.size >= 4)
    assertTrue(
        "linha de abastecimentos deve conter '12'",
        rows.any { row -> row.texts.any { it.toString().contains("12") } }
    )
}

@Test
fun `dashboard exibe valor monetario do ultimo abastecimento quando disponivel`() = runTest {
    coEvery { getActiveVehicle() } returns AppResult.Success(vehicle)
    coEvery { getDashboard(vehicle.id) } returns AppResult.Success(
        dashboard.copy(
            lastRefuelDate = "2026-06-15T10:00:00",
            lastRefuelEnergyAmount = 42.0,
            lastRefuelEnergyUnit = "L",
            lastRefuelAmount = 289.90,
        )
    )
    val screen = AutoDashboardScreen(carContext, getActiveVehicle, getDashboard, createRefuel)
    screen.loadData()
    advanceUntilIdle()
    val template = screen.onGetTemplate() as PaneTemplate
    val rows = template.pane!!.rows
    assertTrue(
        "linha do ultimo abastecimento deve conter o valor",
        rows.any { row ->
            row.texts.any { it.toString().contains("289") }
        }
    )
}
```

> **Nota:** Os testes verificam o conteúdo de texto nas rows; não testam formatação exata (evita fragilidade).

- [ ] **Step 3: Rodar os testes novos e confirmar que falham**

```
./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.auto.AutoDashboardScreenTest"
```

Esperado: os 2 novos testes falham (dashboard ainda tem 3 linhas, sem `totalRefuels` e sem `lastRefuelAmount`).

- [ ] **Step 4: Atualizar AutoDashboardScreen com as 4 linhas**

Substituir o método `successTemplate` em `app/src/main/java/com/flowfuel/app/feature/auto/dashboard/AutoDashboardScreen.kt`:

```kotlin
private fun successTemplate(v: ActiveVehicleData, d: DashboardData): Template {
    val brLocale = Locale("pt", "BR")
    val currencyFmt = NumberFormat.getCurrencyInstance(brLocale)
    val title = "${v.brand} ${v.model}${v.licensePlate?.let { " ($it)" } ?: ""}"

    val consumptionText = if (d.averageConsumption != null && d.consumptionUnit != null)
        String.format(brLocale, "%.1f %s", d.averageConsumption, d.consumptionUnit)
    else "—"

    val spentText = currencyFmt.format(d.totalSpent)

    val totalRefuelsText = if (d.totalRefuels > 0)
        "${d.totalRefuels} abastecimento${if (d.totalRefuels > 1) "s" else ""}"
    else
        "Nenhum ainda"

    val lastRefuelText = if (d.lastRefuelDate != null && d.lastRefuelEnergyAmount != null) {
        val raw = d.lastRefuelDate
        val date = raw.takeIf { it.length >= 10 }
            ?.let { "${it.substring(8, 10)}/${it.substring(5, 7)}" }
            ?: raw
        val unit = d.lastRefuelEnergyUnit ?: "L"
        val energy = String.format(brLocale, "%.1f %s", d.lastRefuelEnergyAmount, unit)
        val price = d.lastRefuelAmount?.let { " • ${currencyFmt.format(it)}" } ?: ""
        "$date • $energy$price"
    } else {
        "Nenhum ainda"
    }

    return PaneTemplate.Builder(
        Pane.Builder()
            .addRow(Row.Builder().setTitle("Consumo médio").addText(consumptionText).build())
            .addRow(Row.Builder().setTitle("Gasto total").addText(spentText).build())
            .addRow(Row.Builder().setTitle("Abastecimentos").addText(totalRefuelsText).build())
            .addRow(Row.Builder().setTitle("Último abastecimento").addText(lastRefuelText).build())
            .addAction(
                Action.Builder()
                    .setTitle("Registrar abastecimento")
                    .setOnClickListener {
                        screenManager.push(
                            AutoRefuelStep1Screen(
                                carContext,
                                vehicle = v,
                                createRefuel = createRefuel,
                            )
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
```

- [ ] **Step 5: Rodar todos os testes do módulo Auto**

```
./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.auto.*"
```

Esperado: `BUILD SUCCESSFUL`, todos os testes passam.

- [ ] **Step 6: Commit**

```
git add app/src/main/java/com/flowfuel/app/feature/auto/dashboard/AutoDashboardScreen.kt
git add app/src/test/java/com/flowfuel/app/feature/auto/AutoDashboardScreenTest.kt
git commit -m "feat(auto): exibir total de abastecimentos e valor do último no dashboard"
```

---

### Task 4: Build Release APK

Gera o APK de release assinado com o keystore configurado em `local.properties`.

**Files:** nenhum arquivo alterado — apenas build.

---

- [ ] **Step 1: Limpar build anterior e gerar release**

```
./gradlew clean :app:assembleRelease
```

Esperado: `BUILD SUCCESSFUL`. APK gerado em `app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 2: Verificar que o APK está assinado**

```
"C:\Users\rocha\AppData\Local\Android\Sdk\build-tools\<versão>\apksigner.bat" verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Esperado: exibe o certificado `CN=flowfuel` sem erros de verificação.

---

## Self-Review

### Spec coverage

| Requisito | Task |
|---|---|
| `InputTemplate` com hint nos 3 passos | Task 1 |
| Botão "Próximo" explícito em cada passo | Task 1 |
| Validação inline com CarToast | Task 1 (preservado) |
| `"ELECTRIC"` → `refuelType = "ELECTRIC"` | Task 2 |
| Combustão/híbrido → `refuelType = "FUEL"` | Task 2 |
| "Corrigir" volta ao Step 1 | Task 2 |
| Dashboard mostra total de abastecimentos | Task 3 |
| Dashboard mostra valor monetário do último abastecimento | Task 3 |
| APK de release assinado | Task 4 |

### Sem pendências
O `minCarApiLevel = 2` declarado na Task 1 é pré-requisito para 4 linhas no PaneTemplate da Task 3.
