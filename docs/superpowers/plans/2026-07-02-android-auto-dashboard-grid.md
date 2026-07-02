# Dashboard do Android Auto em grid (sem rolagem) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trocar o `PaneTemplate` da tela de sucesso do `AutoDashboardScreen` por um `GridTemplate` de 5 blocos (4 informativos + 1 de ação), garantindo que tudo fique visível sem rolagem, sem mudar os dados exibidos.

**Architecture:** Um único arquivo de produção (`AutoDashboardScreen.kt`) muda de `Pane`/`Row`/`PaneTemplate` para `ItemList`/`GridItem`/`GridTemplate`. O texto calculado de cada informação (`consumptionText`, `spentText`, `totalRefuelsText`, `lastRefuelText`) não muda — só o container visual. O 5º `GridItem` ("Registrar abastecimento") é clicável e navega pro mesmo `AutoRefuelStep1Screen` que o botão atual.

**Tech Stack:** Kotlin, Car App Library 1.4.0 (`GridTemplate`/`GridItem`/`ItemList`, já disponíveis desde API level 1 — sem mudança de `minCarApiLevel`), Robolectric + `TestCarContext`

## Global Constraints

- `GridItem` **exige** imagem em todo item fora do estado de loading — confirmado decompilando `GridItem.Builder.build()` (`IllegalStateException: When a grid item is loading, the image must not be set and vice versa`). Os 5 itens usam `setImage()` com um `CarIcon` construído a partir de um recurso `drawable` via `IconCompat.createWithResource()`.
- Loading (`MessageTemplate`) e erro (`MessageTemplate`) continuam sem alteração — só `successTemplate()` muda.
- Ordem dos 5 itens é fixa: Consumo médio, Gasto total, Abastecimentos, Último abastecimento, Registrar abastecimento (ação) — mesma ordem do `PaneTemplate` atual, com a ação no final.
- Só os 4 itens informativos usam `text` (`GridItem.setText`); o item de ação usa só `title` + `onClick`, sem `text`.
- Testes usam `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [33])`, padrão já usado em `AutoDashboardScreenTest.kt`.

---

### Task 1: Substituir PaneTemplate por GridTemplate no AutoDashboardScreen

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/auto/dashboard/AutoDashboardScreen.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/auto/AutoDashboardScreenTest.kt`

**Interfaces:**
- Consumes: nenhuma interface nova — usa `androidx.car.app.model.GridTemplate`, `GridItem`, `ItemList` (já presentes no Car App Library 1.4.0 já declarado no projeto).
- Produces: `AutoDashboardScreen.onGetTemplate()` retorna `GridTemplate` (era `PaneTemplate`) no estado de sucesso; `MessageTemplate` continua para loading/erro, sem mudança.

---

- [ ] **Step 1: Atualizar os testes que hoje esperam PaneTemplate**

Substituir todo o conteúdo de `app/src/test/java/com/flowfuel/app/feature/auto/AutoDashboardScreenTest.kt`:

```kotlin
package com.flowfuel.app.feature.auto

import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auto.dashboard.AutoDashboardScreen
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetDashboardUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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
class AutoDashboardScreenTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val carContext: TestCarContext
        get() = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val testVehicle = ActiveVehicleData(
        id = 1, brand = "VW", model = "Fox", fuelSubType = null,
        capacity = null, licensePlate = "ABC1D23", energyType = "COMBUSTION", currentKm = 67270,
    )
    private val testDashboard = DashboardData(
        averageConsumption = 8.4, consumptionUnit = "km/L",
        totalSpent = 1240.0, totalRefuels = 5,
        lastRefuelDate = "2026-06-15", lastRefuelEnergyAmount = 42.0,
        lastRefuelAmount = 289.90, lastRefuelEnergyUnit = "L",
    )

    @Test
    fun `estado inicial retorna MessageTemplate loading`() {
        val screen = AutoDashboardScreen(carContext, mockk(), mockk(), mockk())
        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `apos loadData com sucesso retorna GridTemplate`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        val getDashboard: GetDashboardUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Success(testVehicle)
        coEvery { getDashboard(1) } returns AppResult.Success(testDashboard)

        val screen = AutoDashboardScreen(carContext, getActiveVehicle, getDashboard, mockk())
        screen.loadData()

        assertTrue(screen.onGetTemplate() is GridTemplate)
    }

    @Test
    fun `erro de rede retorna MessageTemplate error`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Failure(AppError.Network)

        val screen = AutoDashboardScreen(carContext, getActiveVehicle, mockk(), mockk())
        screen.loadData()

        assertTrue(screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `erro 401 retorna MessageTemplate sem acao de retry`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Failure(AppError.Unauthorized)
        val screen = AutoDashboardScreen(carContext, getActiveVehicle, mockk(), mockk())
        screen.loadData()

        assertTrue("Deve retornar MessageTemplate para 401", screen.onGetTemplate() is MessageTemplate)
    }

    @Test
    fun `dashboard exibe 5 blocos sem precisar rolar, incluindo o de acao`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        val getDashboard: GetDashboardUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Success(testVehicle)
        coEvery { getDashboard(testVehicle.id) } returns AppResult.Success(testDashboard)
        val screen = AutoDashboardScreen(carContext, getActiveVehicle, getDashboard, mockk())
        screen.loadData()
        val template = screen.onGetTemplate() as GridTemplate
        val items = template.singleList!!.items
        assertTrue("deve ter os 4 blocos de info + 1 de acao", items.size == 5)
        val actionItem = items.last() as GridItem
        assertTrue(
            "ultimo bloco deve ser o de registrar abastecimento",
            actionItem.title.toString().contains("Registrar abastecimento")
        )
        assertNotNull(
            "bloco de acao deve ter onClick pra navegar pro Step1",
            actionItem.onClickDelegate
        )
    }

    @Test
    fun `dashboard exibe total de abastecimentos`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        val getDashboard: GetDashboardUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Success(testVehicle)
        coEvery { getDashboard(testVehicle.id) } returns AppResult.Success(
            testDashboard.copy(totalRefuels = 12)
        )
        val screen = AutoDashboardScreen(carContext, getActiveVehicle, getDashboard, mockk())
        screen.loadData()
        val template = screen.onGetTemplate() as GridTemplate
        val items = template.singleList!!.items
        assertTrue(
            "bloco de abastecimentos deve conter '12'",
            items.any { item -> (item as GridItem).text?.toString()?.contains("12") == true }
        )
    }

    @Test
    fun `dashboard exibe valor monetario do ultimo abastecimento`() = runTest {
        val getActiveVehicle: GetActiveVehicleUseCase = mockk()
        val getDashboard: GetDashboardUseCase = mockk()
        coEvery { getActiveVehicle() } returns AppResult.Success(testVehicle)
        coEvery { getDashboard(testVehicle.id) } returns AppResult.Success(
            testDashboard.copy(
                lastRefuelDate = "2026-06-15",
                lastRefuelEnergyAmount = 42.0,
                lastRefuelEnergyUnit = "L",
                lastRefuelAmount = 289.90,
            )
        )
        val screen = AutoDashboardScreen(carContext, getActiveVehicle, getDashboard, mockk())
        screen.loadData()
        val template = screen.onGetTemplate() as GridTemplate
        val items = template.singleList!!.items
        assertTrue(
            "bloco do ultimo abastecimento deve conter o valor",
            items.any { item -> (item as GridItem).text?.toString()?.contains("289") == true }
        )
    }
}
```

- [ ] **Step 2: Rodar os testes e confirmar que falham**

```
./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.auto.AutoDashboardScreenTest"
```

Esperado: falha de compilação — `Unresolved reference: GridTemplate`/`GridItem` (ainda não importados em `AutoDashboardScreen.kt`) ou, se compilar, os testes que fazem cast pra `GridTemplate` falham porque `onGetTemplate()` ainda retorna `PaneTemplate`.

- [ ] **Step 3: Criar os 5 ícones vetoriais em res/drawable**

`GridItem` exige uma imagem em todo item fora do estado de loading — sem
isso, `GridItem.Builder.build()` lança `IllegalStateException`. Criar 5
arquivos novos em `app/src/main/res/drawable/` (Material Icons clássico,
24dp, path único):

`ic_auto_fuel.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M19.77,7.23l0.01,-0.01l-3.72,-3.72L15,4.56l2.11,2.11c-0.94,0.36 -1.61,1.26 -1.61,2.33 0,1.38 1.12,2.5 2.5,2.5 0.36,0 0.69,-0.08 1,-0.21v7.21c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1V14c0,-1.1 -0.9,-2 -2,-2h-1V5c0,-1.1 -0.9,-2 -2,-2H6C4.9,3 4,3.9 4,5v16h10v-7.5h1.5v5c0,1.38 1.12,2.5 2.5,2.5s2.5,-1.12 2.5,-2.5V9C20.5,8.31 20.22,7.68 19.77,7.23zM12,10H6V5h6V10z"/>
</vector>
```

`ic_auto_money.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M11.8,10.9c-2.27,-0.59 -3,-1.2 -3,-2.15 0,-1.09 1.01,-1.85 2.7,-1.85 1.78,0 2.44,0.85 2.5,2.1h2.21c-0.07,-1.72 -1.12,-3.3 -3.21,-3.81V3h-3v2.16c-1.94,0.42 -3.5,1.68 -3.5,3.61 0,2.31 1.91,3.46 4.7,4.13 2.5,0.6 3,1.48 3,2.41 0,0.69 -0.49,1.79 -2.7,1.79 -2.06,0 -2.87,-0.92 -2.98,-2.1h-2.2c0.12,2.19 1.76,3.42 3.68,3.83V21h3v-2.15c1.95,-0.37 3.5,-1.5 3.5,-3.55 0,-2.84 -2.43,-3.81 -4.7,-4.4z"/>
</vector>
```

`ic_auto_calendar.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M17,12h-5v5h5V12zM16,1v2H8V1H6v2H5C3.89,3 3.01,3.9 3.01,5L3,19c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2h-1V1H16zM19,19H5V8h14V19z"/>
</vector>
```

`ic_auto_history.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M13,3c-4.97,0 -9,4.03 -9,9L1,12l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08L13,8L12,8z"/>
</vector>
```

`ic_auto_add.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>
</vector>
```

- [ ] **Step 4: Substituir successTemplate() por GridTemplate**

Em `app/src/main/java/com/flowfuel/app/feature/auto/dashboard/AutoDashboardScreen.kt`, trocar os imports do topo do arquivo:

```kotlin
// Remove:
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row

// Adiciona:
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.core.graphics.drawable.IconCompat
import com.flowfuel.app.R
```

Substituir o `return` final de `successTemplate()` (o bloco `PaneTemplate.Builder(...)` até o `.build()` final):

```kotlin
        return GridTemplate.Builder()
            .setSingleList(
                ItemList.Builder()
                    .addItem(
                        GridItem.Builder().setTitle("Consumo médio").setText(consumptionText)
                            .setImage(icon(R.drawable.ic_auto_fuel)).build()
                    )
                    .addItem(
                        GridItem.Builder().setTitle("Gasto total").setText(spentText)
                            .setImage(icon(R.drawable.ic_auto_money)).build()
                    )
                    .addItem(
                        GridItem.Builder().setTitle("Abastecimentos").setText(totalRefuelsText)
                            .setImage(icon(R.drawable.ic_auto_calendar)).build()
                    )
                    .addItem(
                        GridItem.Builder().setTitle("Último abastecimento").setText(lastRefuelText)
                            .setImage(icon(R.drawable.ic_auto_history)).build()
                    )
                    .addItem(
                        GridItem.Builder()
                            .setTitle("Registrar abastecimento")
                            .setImage(icon(R.drawable.ic_auto_add))
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

    private fun icon(resId: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()
```

Note que `icon()` é um novo método privado da classe, fechando a chave de
`successTemplate()` antes dele — não fica dentro do método. O restante de
`successTemplate()` (cálculo de `consumptionText`, `spentText`,
`totalRefuelsText`, `lastRefuelText`) fica exatamente como está — não
muda.

- [ ] **Step 5: Rodar os testes e confirmar que passam**

```
./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.auto.AutoDashboardScreenTest"
```

Esperado: `BUILD SUCCESSFUL`, todos os 7 testes passam.

- [ ] **Step 6: Rodar toda a suíte de testes do módulo Auto para confirmar que não há regressão**

```
./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.auto.*"
```

Esperado: `BUILD SUCCESSFUL` — nenhuma regressão em `AutoRefuelStepScreensTest`, `AutoRefuelConfirmScreenTest`, `AutoSessionTest`.

- [ ] **Step 7: Verificação visual manual via DHU**

Usar o playbook `.claude/android-auto-debug-playbook.md` (DHU + celular conectado) pra instalar o app, abrir o Dashboard do FlowFuel no Android Auto e confirmar visualmente:
- Os 5 blocos (4 informações + "Registrar abastecimento") aparecem todos na tela sem precisar rolar.
- Cada bloco mostra um ícone reconhecível junto do texto.
- Tocar no bloco "Registrar abastecimento" navega pro Passo 1 do fluxo de abastecimento, igual ao botão antigo.
- Os valores de cada bloco (consumo, gasto, abastecimentos, último abastecimento) continuam corretos.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/auto/dashboard/AutoDashboardScreen.kt
git add app/src/test/java/com/flowfuel/app/feature/auto/AutoDashboardScreenTest.kt
git add app/src/main/res/drawable/ic_auto_fuel.xml
git add app/src/main/res/drawable/ic_auto_money.xml
git add app/src/main/res/drawable/ic_auto_calendar.xml
git add app/src/main/res/drawable/ic_auto_history.xml
git add app/src/main/res/drawable/ic_auto_add.xml
git commit -m "feat(auto): dashboard em grid de 5 blocos, sem precisar rolar a tela"
```

---

## Self-Review

### Spec coverage

| Requisito do spec | Task |
|---|---|
| Trocar `PaneTemplate` por `GridTemplate` em `successTemplate()` | Task 1 |
| 5 `GridItem`s na ordem: consumo, gasto, abastecimentos, último, ação | Task 1 |
| Itens informativos sem `onClick`, com ícone obrigatório (`setImage`) | Task 1 |
| Item de ação com `title` + ícone + `onClick` pro `AutoRefuelStep1Screen`, sem `text` | Task 1 |
| Loading/erro continuam em `MessageTemplate`, sem mudança | Task 1 (não tocado, teste `estado inicial` e os dois de erro continuam intactos) |
| Dados/formatação (`consumptionText` etc.) sem alteração | Task 1 (cálculo preservado, só o container muda) |
| Atualizar `AutoDashboardScreenTest` pra `GridTemplate`/`ItemList` | Task 1 |
| Sem mudança de `minCarApiLevel` | Task 1 (nenhum step toca `AndroidManifest.xml`) |

### Placeholder scan
Nenhum "TBD"/"implementar depois" — todo código de teste e produção está completo em cada step, incluindo o método `successTemplate()` inteiro.

### Type consistency
`GridTemplate.singleList` (Kotlin property de `getSingleList()`) e `ItemList.items` (de `getItems()`) usados de forma idêntica em todos os testes. `GridItem.text`/`.title`/`.onClickDelegate` (properties de `getText()`/`getTitle()`/`getOnClickDelegate()`) usados consistentemente. Sem divergência de nomes entre os testes e a implementação.
