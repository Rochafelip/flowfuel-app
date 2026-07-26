# Integração FIPE no cadastro de veículo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trocar os campos de texto livre de Marca/Modelo/Ano do Step 1 de `AddVehicleScreen` por uma consulta em cascata à tabela FIPE (Marca → Modelo → Ano), com fallback manual, sem alterar o contrato de `POST /vehicles`.

**Architecture:** Uma 3ª instância Retrofit/OkHttp sem autenticação (`@Named("fipe")`) aponta para `parallelum.com.br/fipe/api/v1/`, seguindo o padrão de duas instâncias já existente em `NetworkModule`. Uma vertical fina `FipeApi → FipeRepository → GetFipe*UseCase` alimenta `AddVehicleViewModel` com listas de `FipeOption(code, name)`; a seleção final grava nos mesmos campos `brand`/`model`/`manufactureYear`/`modelYear` que o formulário já usa hoje. Um novo composable reutilizável `FFDropdownField` (Material 3 `ExposedDropdownMenuBox`) substitui 3 dos 4 `FFTextField`s do Step 1; o 4º (`Ano de fabricação`) continua editável.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Retrofit + kotlinx.serialization, Coroutines/StateFlow, JUnit + MockK + Robolectric + Turbine (testes já estabelecidos no projeto).

## Global Constraints

- Nenhuma mudança de backend/contrato de `POST /vehicles` — `brand`, `model`, `manufactureYear`, `modelYear` continuam `String`/`Int` como hoje.
- Provedor FIPE: `https://parallelum.com.br/fipe/api/v1/` — sem chave, sem autenticação. `tipo` = `"carros"` para `VehicleType.Car`, `"motos"` para `VehicleType.Motorcycle`.
- Selecionar "Ano do modelo" na FIPE preenche `modelYear` **e** `manufactureYear` com o mesmo valor; `manufactureYear` continua editável manualmente depois.
- Fallback manual ("Não encontrei meu veículo") sempre disponível e reversível, sem apagar dados já preenchidos.
- `EditVehicleScreen` não é alterado — fora de escopo.
- Sem testes de UI Compose (padrão já estabelecido no projeto) — cobertura via testes de ViewModel/Repository (MockK + Robolectric) e verificação manual no emulador.
- Referência: `docs/superpowers/specs/2026-07-25-fipe-add-vehicle-design.md`.

---

## Task 1: Camada de dados FIPE (API, repositório, use cases, DI)

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/core/network/NetworkModule.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicle/domain/model/FipeOption.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicle/data/remote/fipe/FipeApi.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicle/domain/FipeRepository.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicle/data/FipeRepositoryImpl.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicle/domain/usecase/GetFipeBrandsUseCase.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicle/domain/usecase/GetFipeModelsUseCase.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicle/domain/usecase/GetFipeYearsUseCase.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicle/di/FipeModule.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/vehicle/data/FipeRepositoryImplTest.kt`

**Interfaces:**
- Produces: `FipeOption(val code: String, val name: String)` (domain model, usado por Task 3 e 4).
- Produces: `GetFipeBrandsUseCase.invoke(vehicleType: VehicleType): AppResult<List<FipeOption>>`, `GetFipeModelsUseCase.invoke(vehicleType: VehicleType, brandCode: String): AppResult<List<FipeOption>>`, `GetFipeYearsUseCase.invoke(vehicleType: VehicleType, brandCode: String, modelCode: String): AppResult<List<FipeOption>>` — injetados via Hilt em `AddVehicleViewModel` (Task 3).

- [ ] **Step 1: Criar o modelo de domínio `FipeOption`**

```kotlin
// app/src/main/java/com/flowfuel/app/feature/vehicle/domain/model/FipeOption.kt
package com.flowfuel.app.feature.vehicle.domain.model

data class FipeOption(
    val code: String,
    val name: String,
)
```

- [ ] **Step 2: Escrever o teste (falhando) do repositório**

```kotlin
// app/src/test/java/com/flowfuel/app/feature/vehicle/data/FipeRepositoryImplTest.kt
package com.flowfuel.app.feature.vehicle.data

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.data.remote.fipe.FipeApi
import com.flowfuel.app.feature.vehicle.data.remote.fipe.FipeBrandDto
import com.flowfuel.app.feature.vehicle.data.remote.fipe.FipeModelDto
import com.flowfuel.app.feature.vehicle.data.remote.fipe.FipeModelsResponseDto
import com.flowfuel.app.feature.vehicle.data.remote.fipe.FipeYearDto
import com.flowfuel.app.feature.vehicle.domain.model.FipeOption
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FipeRepositoryImplTest {

    private val api: FipeApi = mockk()
    private val repository = FipeRepositoryImpl(api)

    @Test
    fun `getBrands maps dto list to FipeOption and uses carros path for Car`() = runTest {
        coEvery { api.getBrands("carros") } returns listOf(FipeBrandDto("21", "Fiat"))

        val result = repository.getBrands(VehicleType.Car) as AppResult.Success

        assertEquals(listOf(FipeOption("21", "Fiat")), result.value)
    }

    @Test
    fun `getBrands uses motos path for Motorcycle`() = runTest {
        coEvery { api.getBrands("motos") } returns listOf(FipeBrandDto("80", "Honda"))

        repository.getBrands(VehicleType.Motorcycle)

        coVerify(exactly = 1) { api.getBrands("motos") }
    }

    @Test
    fun `getModels maps nested modelos list to FipeOption`() = runTest {
        coEvery { api.getModels("carros", "21") } returns
            FipeModelsResponseDto(modelos = listOf(FipeModelDto(1004, "Uno")))

        val result = repository.getModels(VehicleType.Car, "21") as AppResult.Success

        assertEquals(listOf(FipeOption("1004", "Uno")), result.value)
    }

    @Test
    fun `getYears maps dto list to FipeOption`() = runTest {
        coEvery { api.getYears("carros", "21", "1004") } returns
            listOf(FipeYearDto("2016-1", "2016 Gasolina"))

        val result = repository.getYears(VehicleType.Car, "21", "1004") as AppResult.Success

        assertEquals(listOf(FipeOption("2016-1", "2016 Gasolina")), result.value)
    }

    @Test
    fun `getBrands network failure returns AppError Network`() = runTest {
        coEvery { api.getBrands("carros") } throws IOException("timeout")

        val result = repository.getBrands(VehicleType.Car) as AppResult.Failure

        assertEquals(AppError.Network, result.error)
    }
}
```

- [ ] **Step 3: Rodar o teste e confirmar que falha (classes ainda não existem)**

Run: `./gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.data.FipeRepositoryImplTest" --console=plain`
Expected: FAIL — erro de compilação (`Unresolved reference: FipeApi` / `FipeRepositoryImpl`).

- [ ] **Step 4: Criar os DTOs e a interface Retrofit `FipeApi`**

```kotlin
// app/src/main/java/com/flowfuel/app/feature/vehicle/data/remote/fipe/FipeApi.kt
package com.flowfuel.app.feature.vehicle.data.remote.fipe

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

@Serializable
data class FipeBrandDto(
    val codigo: String,
    val nome: String,
)

@Serializable
data class FipeModelDto(
    val codigo: Int,
    val nome: String,
)

@Serializable
data class FipeModelsResponseDto(
    val modelos: List<FipeModelDto> = emptyList(),
)

@Serializable
data class FipeYearDto(
    val codigo: String,
    val nome: String,
)

/**
 * API pública e gratuita da tabela FIPE (sem autenticação, sem chave).
 * baseUrl configurada em NetworkModule: https://parallelum.com.br/fipe/api/v1/
 */
interface FipeApi {
    @GET("{tipo}/marcas")
    suspend fun getBrands(@Path("tipo") tipo: String): List<FipeBrandDto>

    @GET("{tipo}/marcas/{marcaCodigo}/modelos")
    suspend fun getModels(
        @Path("tipo") tipo: String,
        @Path("marcaCodigo") marcaCodigo: String,
    ): FipeModelsResponseDto

    @GET("{tipo}/marcas/{marcaCodigo}/modelos/{modeloCodigo}/anos")
    suspend fun getYears(
        @Path("tipo") tipo: String,
        @Path("marcaCodigo") marcaCodigo: String,
        @Path("modeloCodigo") modeloCodigo: String,
    ): List<FipeYearDto>
}
```

- [ ] **Step 5: Criar a interface `FipeRepository`**

```kotlin
// app/src/main/java/com/flowfuel/app/feature/vehicle/domain/FipeRepository.kt
package com.flowfuel.app.feature.vehicle.domain

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.model.FipeOption
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType

interface FipeRepository {
    suspend fun getBrands(vehicleType: VehicleType): AppResult<List<FipeOption>>
    suspend fun getModels(vehicleType: VehicleType, brandCode: String): AppResult<List<FipeOption>>
    suspend fun getYears(vehicleType: VehicleType, brandCode: String, modelCode: String): AppResult<List<FipeOption>>
}
```

- [ ] **Step 6: Criar `FipeRepositoryImpl`**

```kotlin
// app/src/main/java/com/flowfuel/app/feature/vehicle/data/FipeRepositoryImpl.kt
package com.flowfuel.app.feature.vehicle.data

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.map
import com.flowfuel.app.core.network.apiCall
import com.flowfuel.app.feature.vehicle.data.remote.fipe.FipeApi
import com.flowfuel.app.feature.vehicle.domain.FipeRepository
import com.flowfuel.app.feature.vehicle.domain.model.FipeOption
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FipeRepositoryImpl @Inject constructor(
    private val api: FipeApi,
) : FipeRepository {

    override suspend fun getBrands(vehicleType: VehicleType): AppResult<List<FipeOption>> =
        apiCall { api.getBrands(vehicleType.toFipePath()) }
            .map { dtos -> dtos.map { FipeOption(it.codigo, it.nome) } }

    override suspend fun getModels(vehicleType: VehicleType, brandCode: String): AppResult<List<FipeOption>> =
        apiCall { api.getModels(vehicleType.toFipePath(), brandCode) }
            .map { response -> response.modelos.map { FipeOption(it.codigo.toString(), it.nome) } }

    override suspend fun getYears(vehicleType: VehicleType, brandCode: String, modelCode: String): AppResult<List<FipeOption>> =
        apiCall { api.getYears(vehicleType.toFipePath(), brandCode, modelCode) }
            .map { dtos -> dtos.map { FipeOption(it.codigo, it.nome) } }

    private fun VehicleType.toFipePath(): String = when (this) {
        VehicleType.Car        -> "carros"
        VehicleType.Motorcycle -> "motos"
    }
}
```

- [ ] **Step 7: Rodar o teste e confirmar que passa**

Run: `./gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.data.FipeRepositoryImplTest" --console=plain`
Expected: `BUILD SUCCESSFUL` — 5 testes passando.

- [ ] **Step 8: Criar os 3 use cases**

```kotlin
// app/src/main/java/com/flowfuel/app/feature/vehicle/domain/usecase/GetFipeBrandsUseCase.kt
package com.flowfuel.app.feature.vehicle.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.FipeRepository
import com.flowfuel.app.feature.vehicle.domain.model.FipeOption
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import javax.inject.Inject

class GetFipeBrandsUseCase @Inject constructor(
    private val repository: FipeRepository,
) {
    suspend operator fun invoke(vehicleType: VehicleType): AppResult<List<FipeOption>> =
        repository.getBrands(vehicleType)
}
```

```kotlin
// app/src/main/java/com/flowfuel/app/feature/vehicle/domain/usecase/GetFipeModelsUseCase.kt
package com.flowfuel.app.feature.vehicle.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.FipeRepository
import com.flowfuel.app.feature.vehicle.domain.model.FipeOption
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import javax.inject.Inject

class GetFipeModelsUseCase @Inject constructor(
    private val repository: FipeRepository,
) {
    suspend operator fun invoke(vehicleType: VehicleType, brandCode: String): AppResult<List<FipeOption>> =
        repository.getModels(vehicleType, brandCode)
}
```

```kotlin
// app/src/main/java/com/flowfuel/app/feature/vehicle/domain/usecase/GetFipeYearsUseCase.kt
package com.flowfuel.app.feature.vehicle.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.FipeRepository
import com.flowfuel.app.feature.vehicle.domain.model.FipeOption
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import javax.inject.Inject

class GetFipeYearsUseCase @Inject constructor(
    private val repository: FipeRepository,
) {
    suspend operator fun invoke(vehicleType: VehicleType, brandCode: String, modelCode: String): AppResult<List<FipeOption>> =
        repository.getYears(vehicleType, brandCode, modelCode)
}
```

- [ ] **Step 9: Adicionar o cliente OkHttp/Retrofit `@Named("fipe")` em `NetworkModule.kt`**

Abrir `app/src/main/java/com/flowfuel/app/core/network/NetworkModule.kt` e adicionar os dois métodos abaixo dentro do `object NetworkModule`, logo após `provideRefreshRetrofit` (linha 85) e antes de `provideOkHttp` (linha 88):

```kotlin
    /**
     * Cliente HTTP para a API pública da tabela FIPE (parallelum.com.br) — sem
     * autenticação. Timeout mais curto por ser uma dependência externa fora do
     * nosso controle; não deve travar o wizard de cadastro de veículo.
     */
    @Provides @Singleton @Named("fipe")
    fun provideFipeOkHttp(
        logging: HttpLoggingInterceptor,
        chucker: ChuckerInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(chucker)
        .addInterceptor(logging)
        .build()

    /** Retrofit exclusivo para a API pública da tabela FIPE. */
    @Provides @Singleton @Named("fipe")
    fun provideFipeRetrofit(
        @Named("fipe") client: OkHttpClient,
        json: Json,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://parallelum.com.br/fipe/api/v1/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
```

- [ ] **Step 10: Criar o módulo Hilt `FipeModule` (bind do repositório + provide da API)**

```kotlin
// app/src/main/java/com/flowfuel/app/feature/vehicle/di/FipeModule.kt
package com.flowfuel.app.feature.vehicle.di

import com.flowfuel.app.feature.vehicle.data.FipeRepositoryImpl
import com.flowfuel.app.feature.vehicle.data.remote.fipe.FipeApi
import com.flowfuel.app.feature.vehicle.domain.FipeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FipeBindModule {
    @Binds @Singleton
    abstract fun bindFipeRepository(impl: FipeRepositoryImpl): FipeRepository
}

@Module
@InstallIn(SingletonComponent::class)
object FipeApiModule {
    @Provides @Singleton
    fun provideFipeApi(@Named("fipe") retrofit: Retrofit): FipeApi = retrofit.create(FipeApi::class.java)
}
```

- [ ] **Step 11: Compilar o app inteiro para validar a árvore de DI do Hilt**

Run: `./gradlew.bat compileDebugKotlin kaptDebugKotlin -x lint --console=plain` (ou `kspDebugKotlin`, conforme o processador usado no módulo — verificar qual roda hoje em `installDebug`, visto na sessão anterior: `:app:kspDebugKotlin`)

Run correto: `./gradlew.bat kspDebugKotlin compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL` — Hilt consegue resolver `FipeRepository`, `FipeApi` e os 3 use cases sem erro de grafo de dependência.

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/network/NetworkModule.kt \
        app/src/main/java/com/flowfuel/app/feature/vehicle/domain/model/FipeOption.kt \
        app/src/main/java/com/flowfuel/app/feature/vehicle/data/remote/fipe/FipeApi.kt \
        app/src/main/java/com/flowfuel/app/feature/vehicle/domain/FipeRepository.kt \
        app/src/main/java/com/flowfuel/app/feature/vehicle/data/FipeRepositoryImpl.kt \
        app/src/main/java/com/flowfuel/app/feature/vehicle/domain/usecase/GetFipeBrandsUseCase.kt \
        app/src/main/java/com/flowfuel/app/feature/vehicle/domain/usecase/GetFipeModelsUseCase.kt \
        app/src/main/java/com/flowfuel/app/feature/vehicle/domain/usecase/GetFipeYearsUseCase.kt \
        app/src/main/java/com/flowfuel/app/feature/vehicle/di/FipeModule.kt \
        app/src/test/java/com/flowfuel/app/feature/vehicle/data/FipeRepositoryImplTest.kt
git commit -m "feat(vehicle): adicionar camada de dados da API FIPE (marcas/modelos/anos)"
```

---

## Task 2: Componente reutilizável `FFDropdownField`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/core/designsystem/components/FFDropdownField.kt`

**Interfaces:**
- Consumes: nada de tasks anteriores (componente puramente de UI, sem dependência do domínio FIPE).
- Produces: `FFDropdownOption(val code: String, val label: String)` e `@Composable fun FFDropdownField(label: String, selectedLabel: String?, options: List<FFDropdownOption>, onOptionSelected: (FFDropdownOption) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, errorText: String? = null, onRetry: (() -> Unit)? = null)` — usado por Task 4.

- [ ] **Step 1: Criar `FFDropdownField.kt`**

```kotlin
// app/src/main/java/com/flowfuel/app/core/designsystem/components/FFDropdownField.kt
package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Item genérico de uma lista carregada de forma assíncrona (ex.: marca/modelo/ano da FIPE). */
data class FFDropdownOption(val code: String, val label: String)

/**
 * Dropdown de seleção única com opções carregadas de forma assíncrona.
 * Suporta estado de carregamento (spinner no trailing) e de erro (ícone de
 * retry no trailing + texto de suporte), além de `enabled=false` para
 * representar um passo da cascata ainda não liberado (ex.: Modelo antes de
 * escolher Marca).
 */
@Composable
fun FFDropdownField(
    label: String,
    selectedLabel: String?,
    options: List<FFDropdownOption>,
    onOptionSelected: (FFDropdownOption) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    errorText: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val menuEnabled = enabled && !loading

    ExposedDropdownMenuBox(
        expanded = expanded && menuEnabled,
        onExpandedChange = { if (menuEnabled) expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedLabel.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = menuEnabled,
            label = { Text(label) },
            isError = errorText != null,
            supportingText = errorText?.let { { Text(it) } },
            trailingIcon = {
                when {
                    loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    errorText != null -> IconButton(onClick = { onRetry?.invoke() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                    else -> ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, menuEnabled)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded && menuEnabled,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 2: Compilar para validar o componente**

Run: `./gradlew.bat compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`. Sem teste automatizado — projeto não tem testes de UI Compose (ver Global Constraints); a validação funcional deste componente acontece integrada no Task 4 e na verificação manual do Task 5.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/designsystem/components/FFDropdownField.kt
git commit -m "feat(designsystem): adicionar FFDropdownField para listas carregadas assincronamente"
```

---

## Task 3: `AddVehicleViewModel` — estado e lógica da cascata FIPE

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/add/AddVehicleViewModel.kt`
- Modify: `app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/add/AddVehicleViewModelTest.kt`

**Interfaces:**
- Consumes: `GetFipeBrandsUseCase`, `GetFipeModelsUseCase`, `GetFipeYearsUseCase` (Task 1), `FipeOption` (Task 1).
- Produces (novos campos em `AddVehicleUiState` e métodos em `AddVehicleViewModel`, consumidos por Task 4):
  - Estado: `useFipeSearch: Boolean`, `fipeBrands/fipeModels/fipeYears: List<FipeOption>`, `selectedFipeBrandCode/selectedFipeModelCode: String?`, `fipeBrandsLoading/fipeModelsLoading/fipeYearsLoading: Boolean`, `fipeBrandsError/fipeModelsError/fipeYearsError: Boolean`.
  - Métodos: `loadFipeBrandsIfNeeded()`, `onFipeBrandSelected(option: FipeOption)`, `onFipeModelSelected(option: FipeOption)`, `onFipeYearSelected(option: FipeOption)`, `onToggleManualEntry()`, `onRetryLoadFipeBrands()`, `onRetryLoadFipeModels()`, `onRetryLoadFipeYears()`.
  - `onVehicleTypeChange(v: VehicleType)` (já existe) passa a limpar a seleção FIPE e recarregar marcas quando `v` muda de fato.

- [ ] **Step 1: Atualizar o construtor do teste e os mocks para os 3 novos use cases (deixa a suíte quebrada de propósito)**

Em `AddVehicleViewModelTest.kt`, adicionar os imports e mocks, e stubar um retorno padrão de sucesso vazio no `setUp()` (evita que qualquer teste existente precise saber sobre FIPE):

```kotlin
// adicionar aos imports existentes
import com.flowfuel.app.feature.vehicle.domain.model.FipeOption
import com.flowfuel.app.feature.vehicle.domain.usecase.GetFipeBrandsUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.GetFipeModelsUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.GetFipeYearsUseCase
import org.junit.Assert.assertNull
```

```kotlin
    // substitui a declaração de mocks existente, adicionando os 3 novos
    private val testDispatcher = UnconfinedTestDispatcher()
    private val createVehicle: CreateVehicleUseCase = mockk()
    private val uploadVehiclePhoto: UploadVehiclePhotoUseCase = mockk()
    private val imagePickerHelper: ImagePickerHelper = mockk()
    private val getFipeBrands: GetFipeBrandsUseCase = mockk()
    private val getFipeModels: GetFipeModelsUseCase = mockk()
    private val getFipeYears: GetFipeYearsUseCase = mockk()
    private lateinit var viewModel: AddVehicleViewModel
```

```kotlin
    // substitui o setUp() existente
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { getFipeBrands(any()) } returns AppResult.Success(emptyList())
        coEvery { getFipeModels(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { getFipeYears(any(), any(), any()) } returns AppResult.Success(emptyList())
        viewModel = AddVehicleViewModel(
            createVehicle, uploadVehiclePhoto, imagePickerHelper,
            getFipeBrands, getFipeModels, getFipeYears,
        )
    }
```

- [ ] **Step 2: Rodar a suíte e confirmar que falha (construtor ainda não aceita os 3 novos parâmetros)**

Run: `./gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.add.AddVehicleViewModelTest" --console=plain`
Expected: FAIL — erro de compilação, `AddVehicleViewModel` não tem esse construtor.

- [ ] **Step 3: Atualizar `AddVehicleUiState` — novos campos**

Em `AddVehicleViewModel.kt`, adicionar os imports:

```kotlin
import com.flowfuel.app.feature.vehicle.domain.model.FipeOption
import com.flowfuel.app.feature.vehicle.domain.usecase.GetFipeBrandsUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.GetFipeModelsUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.GetFipeYearsUseCase
```

E, dentro de `AddVehicleUiState`, logo depois do bloco "— Etapa 1: Identificação" (depois de `val modelYear: String = ""`), adicionar:

```kotlin
    // — Etapa 1: Busca FIPE
    val useFipeSearch: Boolean = true,
    val fipeBrands: List<FipeOption> = emptyList(),
    val fipeModels: List<FipeOption> = emptyList(),
    val fipeYears: List<FipeOption> = emptyList(),
    val selectedFipeBrandCode: String? = null,
    val selectedFipeModelCode: String? = null,
    val fipeBrandsLoading: Boolean = false,
    val fipeModelsLoading: Boolean = false,
    val fipeYearsLoading: Boolean = false,
    val fipeBrandsError: Boolean = false,
    val fipeModelsError: Boolean = false,
    val fipeYearsError: Boolean = false,
```

- [ ] **Step 4: Atualizar o construtor de `AddVehicleViewModel`**

```kotlin
@HiltViewModel
class AddVehicleViewModel @Inject constructor(
    private val createVehicle: CreateVehicleUseCase,
    private val uploadVehiclePhoto: UploadVehiclePhotoUseCase,
    private val imagePickerHelper: ImagePickerHelper,
    private val getFipeBrands: GetFipeBrandsUseCase,
    private val getFipeModels: GetFipeModelsUseCase,
    private val getFipeYears: GetFipeYearsUseCase,
) : ViewModel() {
```

- [ ] **Step 5: Rodar a suíte de novo — agora deve compilar e passar (comportamento antigo intacto)**

Run: `./gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.add.AddVehicleViewModelTest" --console=plain`
Expected: `BUILD SUCCESSFUL` — todos os testes já existentes voltam a passar (nenhum comportamento novo foi exercitado ainda).

- [ ] **Step 6: Substituir `onVehicleTypeChange` e adicionar os métodos/lógica da cascata FIPE**

Substituir a linha `fun onVehicleTypeChange(v: VehicleType) = _state.update { it.copy(vehicleType = v) }` (dentro do bloco "— Etapa 2") por:

```kotlin
    fun onVehicleTypeChange(v: VehicleType) {
        if (v == _state.value.vehicleType) return
        _state.update {
            it.copy(
                vehicleType = v,
                brand = "", brandError = false, selectedFipeBrandCode = null, fipeBrands = emptyList(), fipeBrandsError = false,
                model = "", modelError = false, selectedFipeModelCode = null, fipeModels = emptyList(), fipeModelsError = false,
                manufactureYear = "", manufactureYearError = false,
                modelYear = "", modelYearError = false, fipeYears = emptyList(), fipeYearsError = false,
            )
        }
        loadFipeBrands()
    }
```

E, logo após `onModelYearChange` (final do bloco "— Etapa 1"), adicionar o bloco novo inteiro:

```kotlin
    // — Etapa 1: Busca FIPE
    fun loadFipeBrandsIfNeeded() {
        val s = _state.value
        if (s.fipeBrands.isNotEmpty() || s.fipeBrandsLoading) return
        loadFipeBrands()
    }

    fun onRetryLoadFipeBrands() = loadFipeBrands()
    fun onRetryLoadFipeModels() = loadFipeModels()
    fun onRetryLoadFipeYears() = loadFipeYears()

    fun onFipeBrandSelected(option: FipeOption) {
        _state.update {
            it.copy(
                brand = option.name,
                brandError = false,
                selectedFipeBrandCode = option.code,
                model = "",
                modelError = false,
                selectedFipeModelCode = null,
                fipeModels = emptyList(),
                fipeModelsError = false,
                modelYear = "",
                modelYearError = false,
                fipeYears = emptyList(),
                fipeYearsError = false,
                error = null,
                serverErrors = null,
            )
        }
        loadFipeModels()
    }

    fun onFipeModelSelected(option: FipeOption) {
        _state.update {
            it.copy(
                model = option.name,
                modelError = false,
                selectedFipeModelCode = option.code,
                modelYear = "",
                modelYearError = false,
                fipeYears = emptyList(),
                fipeYearsError = false,
                error = null,
                serverErrors = null,
            )
        }
        loadFipeYears()
    }

    fun onFipeYearSelected(option: FipeOption) {
        val year = parseFipeYear(option) ?: return
        _state.update {
            it.copy(
                modelYear = year.toString(),
                modelYearError = false,
                manufactureYear = year.toString(),
                manufactureYearError = false,
                error = null,
                serverErrors = null,
            )
        }
    }

    fun onToggleManualEntry() = _state.update { it.copy(useFipeSearch = !it.useFipeSearch) }

    private fun loadFipeBrands() {
        _state.update { it.copy(fipeBrandsLoading = true, fipeBrandsError = false) }
        viewModelScope.launch {
            when (val result = getFipeBrands(_state.value.vehicleType)) {
                is AppResult.Success -> _state.update { it.copy(fipeBrands = result.value, fipeBrandsLoading = false) }
                is AppResult.Failure -> _state.update { it.copy(fipeBrandsLoading = false, fipeBrandsError = true) }
            }
        }
    }

    private fun loadFipeModels() {
        val brandCode = _state.value.selectedFipeBrandCode ?: return
        _state.update { it.copy(fipeModelsLoading = true, fipeModelsError = false) }
        viewModelScope.launch {
            when (val result = getFipeModels(_state.value.vehicleType, brandCode)) {
                is AppResult.Success -> _state.update { it.copy(fipeModels = result.value, fipeModelsLoading = false) }
                is AppResult.Failure -> _state.update { it.copy(fipeModelsLoading = false, fipeModelsError = true) }
            }
        }
    }

    private fun loadFipeYears() {
        val s = _state.value
        val brandCode = s.selectedFipeBrandCode ?: return
        val modelCode = s.selectedFipeModelCode ?: return
        _state.update { it.copy(fipeYearsLoading = true, fipeYearsError = false) }
        viewModelScope.launch {
            when (val result = getFipeYears(s.vehicleType, brandCode, modelCode)) {
                is AppResult.Success -> _state.update { it.copy(fipeYears = result.value, fipeYearsLoading = false) }
                is AppResult.Failure -> _state.update { it.copy(fipeYearsLoading = false, fipeYearsError = true) }
            }
        }
    }
```

Por fim, adicionar a função privada de parsing no final do arquivo (fora da classe):

```kotlin
/** Extrai o ano de um item de "Ano do modelo" da FIPE (código "2016-1" ou nome "2016 Gasolina"). */
private fun parseFipeYear(option: FipeOption): Int? =
    option.code.substringBefore("-").toIntOrNull()
        ?: option.name.take(4).toIntOrNull()
```

- [ ] **Step 7: Escrever os testes novos em `AddVehicleViewModelTest.kt`**

Adicionar ao final da classe, antes do `}` de fechamento:

```kotlin
    // ── Cascata FIPE ──────────────────────────────────────────────────────────

    @Test
    fun `loadFipeBrandsIfNeeded loads brands once and does not reload if already loaded`() = runTest {
        coEvery { getFipeBrands(VehicleType.Car) } returns AppResult.Success(listOf(FipeOption("21", "Fiat")))

        viewModel.loadFipeBrandsIfNeeded()
        viewModel.loadFipeBrandsIfNeeded()

        assertEquals(listOf(FipeOption("21", "Fiat")), viewModel.state.value.fipeBrands)
        coVerify(exactly = 1) { getFipeBrands(VehicleType.Car) }
    }

    @Test
    fun `loadFipeBrandsIfNeeded sets fipeBrandsError on failure`() = runTest {
        coEvery { getFipeBrands(VehicleType.Car) } returns AppResult.Failure(AppError.Network)

        viewModel.loadFipeBrandsIfNeeded()

        assertTrue(viewModel.state.value.fipeBrandsError)
        assertFalse(viewModel.state.value.fipeBrandsLoading)
    }

    @Test
    fun `onRetryLoadFipeBrands retries and clears fipeBrandsError on success`() = runTest {
        coEvery { getFipeBrands(VehicleType.Car) } returns AppResult.Failure(AppError.Network)
        viewModel.loadFipeBrandsIfNeeded()
        assertTrue(viewModel.state.value.fipeBrandsError)

        coEvery { getFipeBrands(VehicleType.Car) } returns AppResult.Success(listOf(FipeOption("21", "Fiat")))
        viewModel.onRetryLoadFipeBrands()

        assertFalse(viewModel.state.value.fipeBrandsError)
        assertEquals(listOf(FipeOption("21", "Fiat")), viewModel.state.value.fipeBrands)
    }

    @Test
    fun `onFipeBrandSelected sets brand fields and triggers loadFipeModels`() = runTest {
        coEvery { getFipeModels(VehicleType.Car, "21") } returns AppResult.Success(listOf(FipeOption("1004", "Uno")))

        viewModel.onFipeBrandSelected(FipeOption("21", "Fiat"))

        val state = viewModel.state.value
        assertEquals("Fiat", state.brand)
        assertEquals("21", state.selectedFipeBrandCode)
        assertEquals(listOf(FipeOption("1004", "Uno")), state.fipeModels)
        coVerify(exactly = 1) { getFipeModels(VehicleType.Car, "21") }
    }

    @Test
    fun `onFipeModelSelected sets model fields and triggers loadFipeYears`() = runTest {
        viewModel.onFipeBrandSelected(FipeOption("21", "Fiat"))
        coEvery { getFipeYears(VehicleType.Car, "21", "1004") } returns
            AppResult.Success(listOf(FipeOption("2016-1", "2016 Gasolina")))

        viewModel.onFipeModelSelected(FipeOption("1004", "Uno"))

        val state = viewModel.state.value
        assertEquals("Uno", state.model)
        assertEquals("1004", state.selectedFipeModelCode)
        assertEquals(listOf(FipeOption("2016-1", "2016 Gasolina")), state.fipeYears)
    }

    @Test
    fun `onFipeYearSelected fills manufactureYear and modelYear with the same parsed year`() {
        viewModel.onFipeYearSelected(FipeOption("2016-1", "2016 Gasolina"))

        val state = viewModel.state.value
        assertEquals("2016", state.modelYear)
        assertEquals("2016", state.manufactureYear)
    }

    @Test
    fun `onFipeYearSelected does not change years when the fipe code is unparseable`() {
        viewModel.onManufactureYearChange("2020")
        viewModel.onModelYearChange("2021")

        viewModel.onFipeYearSelected(FipeOption("", ""))

        val state = viewModel.state.value
        assertEquals("2020", state.manufactureYear)
        assertEquals("2021", state.modelYear)
    }

    @Test
    fun `onVehicleTypeChange to Motorcycle clears previous selection and reloads brands for motos`() = runTest {
        coEvery { getFipeModels(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { getFipeBrands(VehicleType.Motorcycle) } returns AppResult.Success(listOf(FipeOption("80", "Honda")))
        viewModel.onFipeBrandSelected(FipeOption("21", "Fiat"))

        viewModel.onVehicleTypeChange(VehicleType.Motorcycle)

        val state = viewModel.state.value
        assertEquals(VehicleType.Motorcycle, state.vehicleType)
        assertEquals("", state.brand)
        assertNull(state.selectedFipeBrandCode)
        assertEquals(listOf(FipeOption("80", "Honda")), state.fipeBrands)
    }

    @Test
    fun `onVehicleTypeChange to the same type does not reload brands`() = runTest {
        viewModel.loadFipeBrandsIfNeeded() // Car (default)

        viewModel.onVehicleTypeChange(VehicleType.Car)

        coVerify(exactly = 1) { getFipeBrands(VehicleType.Car) }
    }

    @Test
    fun `onToggleManualEntry flips useFipeSearch without touching other fields`() {
        assertTrue(viewModel.state.value.useFipeSearch)
        viewModel.onFipeBrandSelected(FipeOption("21", "Fiat"))

        viewModel.onToggleManualEntry()

        val state = viewModel.state.value
        assertFalse(state.useFipeSearch)
        assertEquals("Fiat", state.brand)

        viewModel.onToggleManualEntry()
        assertTrue(viewModel.state.value.useFipeSearch)
    }
```

- [ ] **Step 8: Rodar a suíte inteira e confirmar que passa**

Run: `./gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.add.AddVehicleViewModelTest" --console=plain`
Expected: `BUILD SUCCESSFUL` — todos os testes (antigos + novos) passando.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/add/AddVehicleViewModel.kt \
        app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/add/AddVehicleViewModelTest.kt
git commit -m "feat(vehicle): cascata de selecao FIPE (marca/modelo/ano) no AddVehicleViewModel"
```

---

## Task 4: UI — reordenar Step 1, adicionar dropdowns FIPE e fallback manual

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/add/AddVehicleScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `FFDropdownField`/`FFDropdownOption` (Task 2), todo o estado/métodos novos de `AddVehicleUiState`/`AddVehicleViewModel` (Task 3), `FipeOption` (Task 1).

- [ ] **Step 1: Adicionar as 3 novas strings**

Em `app/src/main/res/values/strings.xml`, logo após a linha `<string name="vehicle_model_year">Ano do modelo</string>` (linha 167), adicionar:

```xml
    <string name="vehicle_fipe_not_found">Não encontrei meu veículo</string>
    <string name="vehicle_fipe_use_search">Usar busca FIPE</string>
    <string name="vehicle_fipe_load_error">Não foi possível carregar. Toque para tentar novamente.</string>
```

- [ ] **Step 2: Adicionar os 2 novos imports em `AddVehicleScreen.kt`**

Junto aos imports existentes de `com.flowfuel.app.core.designsystem.components.*` (linhas 91-101), adicionar:

```kotlin
import com.flowfuel.app.core.designsystem.components.FFDropdownField
import com.flowfuel.app.core.designsystem.components.FFDropdownOption
```

E junto aos imports de `com.flowfuel.app.feature.vehicle.domain.model.*` (procurar onde `VehicleType`/`EnergyType`/`FuelType` já são importados), adicionar:

```kotlin
import com.flowfuel.app.feature.vehicle.domain.model.FipeOption
```

- [ ] **Step 3: Substituir `Step1Content` inteiro**

Substituir a função `Step1Content` (linhas 259-349 do arquivo original) por:

```kotlin
@Composable
private fun Step1Content(
    state: AddVehicleUiState,
    viewModel: AddVehicleViewModel,
) {
    val focusManufactureYear = remember { FocusRequester() }
    val focusManager         = LocalFocusManager.current

    LaunchedEffect(Unit) { viewModel.loadFipeBrandsIfNeeded() }

    LaunchedEffect(state.stepAttempt) {
        if (state.stepAttempt != 0 && state.manufactureYearError) focusManufactureYear.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        VehicleTypeSelector(
            selected = state.vehicleType,
            onSelect = viewModel::onVehicleTypeChange,
            enabled = !state.isSubmitting,
        )

        if (state.useFipeSearch) {
            FFDropdownField(
                label = stringResource(R.string.vehicle_brand),
                selectedLabel = state.brand.takeIf { it.isNotBlank() },
                options = state.fipeBrands.map { FFDropdownOption(it.code, it.name) },
                onOptionSelected = { viewModel.onFipeBrandSelected(FipeOption(it.code, it.label)) },
                loading = state.fipeBrandsLoading,
                errorText = when {
                    state.fipeBrandsError -> stringResource(R.string.vehicle_fipe_load_error)
                    state.brandError      -> stringResource(R.string.error_required)
                    else                  -> state.serverErrors?.firstOrNull { it.field == "brand" }?.message
                },
                onRetry = viewModel::onRetryLoadFipeBrands,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            FFDropdownField(
                label = stringResource(R.string.vehicle_model),
                selectedLabel = state.model.takeIf { it.isNotBlank() },
                options = state.fipeModels.map { FFDropdownOption(it.code, it.name) },
                onOptionSelected = { viewModel.onFipeModelSelected(FipeOption(it.code, it.label)) },
                loading = state.fipeModelsLoading,
                errorText = when {
                    state.fipeModelsError -> stringResource(R.string.vehicle_fipe_load_error)
                    state.modelError      -> stringResource(R.string.error_required)
                    else                  -> state.serverErrors?.firstOrNull { it.field == "model" }?.message
                },
                onRetry = viewModel::onRetryLoadFipeModels,
                enabled = !state.isSubmitting && state.selectedFipeBrandCode != null,
                modifier = Modifier.fillMaxWidth(),
            )

            FFDropdownField(
                label = stringResource(R.string.vehicle_model_year),
                selectedLabel = state.modelYear.takeIf { it.isNotBlank() },
                options = state.fipeYears.map { FFDropdownOption(it.code, it.name) },
                onOptionSelected = { viewModel.onFipeYearSelected(FipeOption(it.code, it.label)) },
                loading = state.fipeYearsLoading,
                errorText = when {
                    state.fipeYearsError -> stringResource(R.string.vehicle_fipe_load_error)
                    state.modelYearError -> stringResource(R.string.error_model_year_invalid)
                    else                 -> state.serverErrors?.firstOrNull { it.field == "modelYear" }?.message
                },
                onRetry = viewModel::onRetryLoadFipeYears,
                enabled = !state.isSubmitting && state.selectedFipeModelCode != null,
                modifier = Modifier.fillMaxWidth(),
            )

            FFTextField(
                value = state.manufactureYear,
                onValueChange = viewModel::onManufactureYearChange,
                label = stringResource(R.string.vehicle_manufacture_year),
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                errorText = if (state.manufactureYearError) stringResource(R.string.error_year_invalid)
                            else state.serverErrors?.firstOrNull { it.field == "manufactureYear" }?.message,
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusManufactureYear),
            )

            FFButton(
                text = stringResource(R.string.vehicle_fipe_not_found),
                onClick = viewModel::onToggleManualEntry,
                variant = FFButtonVariant.Text,
                enabled = !state.isSubmitting,
            )
        } else {
            val focusBrand     = remember { FocusRequester() }
            val focusModel     = remember { FocusRequester() }
            val focusModelYear = remember { FocusRequester() }

            LaunchedEffect(Unit) { focusBrand.requestFocus() }

            LaunchedEffect(state.stepAttempt) {
                if (state.stepAttempt == 0) return@LaunchedEffect
                when {
                    state.brandError           -> focusBrand.requestFocus()
                    state.modelError           -> focusModel.requestFocus()
                    state.manufactureYearError -> focusManufactureYear.requestFocus()
                    state.modelYearError       -> focusModelYear.requestFocus()
                }
            }

            FFTextField(
                value = state.brand,
                onValueChange = viewModel::onBrandChange,
                label = stringResource(R.string.vehicle_brand),
                imeAction = ImeAction.Next,
                keyboardActions = KeyboardActions(onNext = { focusModel.requestFocus() }),
                errorText = if (state.brandError) stringResource(R.string.error_required)
                            else state.serverErrors?.firstOrNull { it.field == "brand" }?.message,
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusBrand),
            )

            FFTextField(
                value = state.model,
                onValueChange = viewModel::onModelChange,
                label = stringResource(R.string.vehicle_model),
                imeAction = ImeAction.Next,
                keyboardActions = KeyboardActions(onNext = { focusManufactureYear.requestFocus() }),
                errorText = if (state.modelError) stringResource(R.string.error_required)
                            else state.serverErrors?.firstOrNull { it.field == "model" }?.message,
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusModel),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FFTextField(
                    value = state.manufactureYear,
                    onValueChange = viewModel::onManufactureYearChange,
                    label = stringResource(R.string.vehicle_manufacture_year),
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                    keyboardActions = KeyboardActions(onNext = { focusModelYear.requestFocus() }),
                    errorText = if (state.manufactureYearError) stringResource(R.string.error_year_invalid)
                                else state.serverErrors?.firstOrNull { it.field == "manufactureYear" }?.message,
                    enabled = !state.isSubmitting,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusManufactureYear),
                )

                FFTextField(
                    value = state.modelYear,
                    onValueChange = viewModel::onModelYearChange,
                    label = stringResource(R.string.vehicle_model_year),
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    errorText = if (state.modelYearError) stringResource(R.string.error_model_year_invalid)
                                else state.serverErrors?.firstOrNull { it.field == "modelYear" }?.message,
                    enabled = !state.isSubmitting,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusModelYear),
                )
            }

            FFButton(
                text = stringResource(R.string.vehicle_fipe_use_search),
                onClick = viewModel::onToggleManualEntry,
                variant = FFButtonVariant.Text,
                enabled = !state.isSubmitting,
            )
        }
    }
}
```

- [ ] **Step 4: Remover a seção "Tipo do veículo" de `Step2Content`**

Em `Step2Content` (logo abaixo da função acima), remover o primeiro `FormSection` (Tipo) e o `SectionDivider` que vem depois dele. O corpo da função passa a ser:

```kotlin
@Composable
private fun Step2Content(
    state: AddVehicleUiState,
    viewModel: AddVehicleViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        FormSection(title = stringResource(R.string.vehicle_section_energy)) {
            EnergyTypeSelector(
                selected = state.energyType,
                onSelect = viewModel::onEnergyTypeChange,
                enabled = !state.isSubmitting,
            )
        }

        AnimatedVisibility(
            visible = state.showFuelType,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                SectionDivider()
                FormSection(title = stringResource(R.string.vehicle_section_fuel)) {
                    FuelTypeSelector(
                        selected = state.fuelType,
                        onSelect = viewModel::onFuelTypeChange,
                        enabled = !state.isSubmitting,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 5: Compilar**

Run: `./gradlew.bat compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Rodar toda a suíte de testes de vehicle para garantir que nada quebrou**

Run: `./gradlew.bat testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.*" --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/add/AddVehicleScreen.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(vehicle): dropdowns FIPE no Step 1 do cadastro, com fallback manual"
```

---

## Task 5: Verificação manual no emulador

**Files:** nenhum (só verificação; sem mudança de código).

- [ ] **Step 1: Build e instalação**

```bash
cd "C:\Users\rocha\AndroidStudioProjects\flowfuel-app"
./gradlew.bat installDebug -x lint --console=plain
```

Expected: `BUILD SUCCESSFUL`, `Installed on 1 device.`

- [ ] **Step 2: Abrir o cadastro e validar o carregamento inicial**

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" shell monkey -p com.flowfuel.app.debug -c android.intent.category.LAUNCHER 1
```

Navegar manualmente até "Cadastrar veículo" (Perfil → Meus veículos → "+" ou fluxo equivalente). Confirmar visualmente (screenshot via `adb exec-out screencap -p`) que:
- O seletor "Tipo do veículo" aparece no topo do Step 1 (Carro selecionado por padrão).
- O dropdown "Marca" já vem populado com marcas de carro assim que a tela abre.

- [ ] **Step 3: Cascata completa**

Selecionar uma Marca → confirmar que "Modelo" habilita e carrega. Selecionar um Modelo → confirmar que "Ano do modelo" habilita e carrega. Selecionar um Ano → confirmar que "Ano de fabricação" foi preenchido com o mesmo valor. Editar manualmente "Ano de fabricação" e confirmar que o valor digitado persiste.

- [ ] **Step 4: Troca de Tipo**

Trocar "Tipo do veículo" para "Moto". Confirmar que Marca/Modelo/Ano são limpos e que a lista de Marca recarrega com marcas de moto (ex.: Honda, Yamaha).

- [ ] **Step 5: Fallback manual**

Tocar em "Não encontrei meu veículo". Confirmar que os 3 dropdowns viram campos de texto livre (Marca, Modelo, Ano do modelo). Preencher manualmente e completar o cadastro (Steps 2-4) até o fim, confirmando sucesso.

- [ ] **Step 6: Cenário de erro de rede**

Desligar a rede do emulador (`adb shell svc wifi disable` + `adb shell svc data disable`, ou usar o painel de rede do AVD) antes de abrir a tela de cadastro pela primeira vez nesta sessão de app. Confirmar que o dropdown "Marca" mostra o texto de erro com ícone de retry, sem travar a tela. Religar a rede e confirmar que o retry recarrega a lista com sucesso.

- [ ] **Step 7: Confirmar o payload enviado ao backend**

Completar um cadastro do início ao fim via fluxo FIPE. Usando o Chucker (notificação do app em debug) ou logs do Logcat (`HttpLoggingInterceptor` em nível BODY), inspecionar o corpo de `POST /vehicles` e confirmar que `brand`/`model` chegam como string e `manufactureYear`/`modelYear` como inteiros — formato idêntico ao anterior à mudança.

---

## Self-Review

**Cobertura do spec:**
1. Reordenar Step 1 (Tipo primeiro) → Task 4, Step 3-4. ✓
2. Marca/Modelo/Ano em cascata com desabilitação progressiva → Task 3 (estado/lógica) + Task 4 (UI, `enabled = ... selectedFipeBrandCode != null` etc.). ✓
3. Seleção de Ano preenche `modelYear` e `manufactureYear` → Task 3, `onFipeYearSelected`. ✓
4. "Ano de fabricação" continua editável → Task 4, `FFTextField` fora do `when`, sempre visível e habilitado. ✓
5. Fallback "Não encontrei meu veículo" reversível → Task 3 `onToggleManualEntry` + Task 4 UI condicional `if (state.useFipeSearch)`. ✓
6. Erros de rede não bloqueiam o formulário → Task 3 (`fipeBrandsError` etc. não impedem `onNextStep`) + Task 4 (retry inline) + Task 5 Step 6. ✓
7. Validação existente do Step 1 inalterada → nenhuma mudança em `onNextStep()`; testes antigos de `AddVehicleViewModelTest` continuam passando (Task 3, Step 5). ✓
8. `EditVehicleScreen` fora de escopo → nenhuma task o modifica. ✓
9. Provedor Parallelum v1, sem chave → Task 1, `NetworkModule`/`FipeApi`. ✓

**Placeholder scan:** nenhum "TBD"/"TODO" — todo código de cada step está completo e compilável.

**Consistência de tipos:** `FipeOption(code, name)` usado identicamente em Task 1 (repositório), Task 3 (ViewModel) e Task 4 (mapeamento para/de `FFDropdownOption(code, label)`). `GetFipeBrandsUseCase`/`GetFipeModelsUseCase`/`GetFipeYearsUseCase` com as mesmas assinaturas em Task 1 (definição) e Task 3 (uso no ViewModel e nos testes). Nomes de método (`onFipeBrandSelected`, `onFipeModelSelected`, `onFipeYearSelected`, `onToggleManualEntry`, `loadFipeBrandsIfNeeded`, `onRetryLoadFipe*`) idênticos entre Task 3 (definição) e Task 4 (chamada na UI).
