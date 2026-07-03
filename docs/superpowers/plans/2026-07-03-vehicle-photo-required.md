# Foto obrigatória na criação de veículo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tornar obrigatório selecionar uma foto do veículo em `AddVehicleScreen` antes de conseguir criar o veículo, enviando a foto ao backend logo após a criação bem-sucedida do registro.

**Architecture:** Novo 4º passo do wizard (`Step4Content`, "Foto"). Submissão em duas chamadas sequenciais e não-atômicas: `POST /vehicles` (já existente) cria o veículo e retorna o `id`; em seguida um novo `POST /vehicles/{id}/photo` (multipart, espelhando o padrão já usado para foto de perfil) envia a imagem comprimida. Se o upload falhar após o veículo já ter sido criado, o `id` fica guardado no estado da ViewModel para que o retry reenvie só a foto, sem recriar o veículo.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Retrofit + Kotlinx Serialization, Coroutines/StateFlow, Coil 2.7 (`coil.compose.AsyncImage`), `ActivityResultContracts.PickVisualMedia` (Android Photo Picker), MockK + Robolectric + Turbine para testes.

## Global Constraints

- O endpoint de backend `POST /api/v1/vehicles/{id}/photo` **não existe ainda** — foi projetado neste plano espelhando `POST /api/v1/auth/{userId}/upload-profile-picture` (multipart, campo `file`, limite 5MB, JPEG/PNG/WEBP). O app é construído contra esse contrato esperado; o teste end-to-end real só é possível quando o backend implementar o endpoint. Ver `docs/superpowers/specs/2026-07-03-vehicle-photo-required-design.md`.
- A foto é obrigatória em **toda** criação de veículo (não só a primeira pós-cadastro) — `AddVehicleScreen` é reusada nos dois fluxos, sem flag de navegação.
- A foto vem **só da galeria** (Android Photo Picker) — sem captura por câmera, sem nova permissão de runtime.
- Não alterar `VehicleDetailsScreen`, cards de lista de veículos, `VehiclePickerScreen` ou `EditVehicleScreen` — exibição/edição da foto fica fora deste plano.
- Seguir os padrões já estabelecidos no módulo `feature/vehicle` e no equivalente `feature/auth` (upload de foto de perfil): `AppResult<T>`/`AppError` para erros, `apiCall {}` para chamadas Retrofit padrão, `ImagePickerHelper.compressToJpeg` para compressão, Use Case fino por cima do repositório.

---

### Task 1: Endpoint de upload de foto no `VehicleApi`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/data/remote/VehicleApi.kt`

**Interfaces:**
- Consumes: nada (camada mais externa).
- Produces: `VehiclePhotoResponseDto(photo: String?)`, `VehicleApi.uploadVehiclePhoto(id: Int, file: MultipartBody.Part): VehiclePhotoResponseDto` — consumido pela Task 2.

Esta tela não tem teste dedicado nesta camada — segue o mesmo padrão do `ProfileApi` (upload de foto de perfil), que também não tem teste de interface Retrofit isolado no projeto. A cobertura de comportamento fica nos testes da ViewModel (Task 4).

- [ ] **Step 1: Adicionar imports necessários**

No topo de `VehicleApi.kt`, adicionar aos imports existentes:

```kotlin
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.Part
```

- [ ] **Step 2: Adicionar o DTO de resposta do upload**

Logo abaixo de `VehicleResponseDto` (depois da linha `)` que fecha essa data class, adicionar:

```kotlin
@Serializable
data class VehiclePhotoResponseDto(
    val photo: String? = null,
)
```

- [ ] **Step 3: Adicionar o método de upload na interface `VehicleApi`**

Dentro da `interface VehicleApi { ... }`, logo após o método `updateOdometer`, adicionar:

```kotlin
    /**
     * Envia a foto do veículo (multipart). Chamado logo após a criação do
     * veículo (ver [createVehicle]) — o endpoint espera o veículo já existir.
     * Contrato espelha POST /auth/{userId}/upload-profile-picture (ver
     * docs/superpowers/specs/2026-07-03-vehicle-photo-required-design.md).
     */
    @Multipart
    @POST("vehicles/{id}/photo")
    suspend fun uploadVehiclePhoto(
        @Path("id") id: Int,
        @Part file: MultipartBody.Part,
    ): VehiclePhotoResponseDto
```

- [ ] **Step 4: Compilar para verificar que não há erros**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/data/remote/VehicleApi.kt
git commit -m "feat(vehicle): adiciona endpoint de upload de foto ao VehicleApi"
```

---

### Task 2: `VehicleRepository.uploadVehiclePhoto`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/domain/VehicleRepository.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/data/VehicleRepositoryImpl.kt`

**Interfaces:**
- Consumes: `VehicleApi.uploadVehiclePhoto` (Task 1), `ImagePickerHelper.compressToJpeg(uri: Uri): ByteArray` (já existe em `core/media`), `apiCall {}` (já existe em `core/network/ApiCall.kt`).
- Produces: `VehicleRepository.uploadVehiclePhoto(vehicleId: Int, uri: Uri): AppResult<String>` — consumido pela Task 3.

Sem teste dedicado nesta camada — mesmo padrão de `ProfileRepositoryImpl.uploadProfilePicture`, que também não tem teste isolado no projeto (a única classe com testes de repositório no projeto é para lógica não-trivial de paginação/erro, não para uploads simples).

- [ ] **Step 1: Adicionar o método à interface `VehicleRepository`**

Adicionar `import android.net.Uri` no topo do arquivo. Dentro da `interface VehicleRepository { ... }`, logo após `updateOdometer`, adicionar:

```kotlin

    /** Envia a foto do veículo recém-criado. Comprime a imagem antes do upload. */
    suspend fun uploadVehiclePhoto(vehicleId: Int, uri: Uri): AppResult<String>
```

- [ ] **Step 2: Implementar em `VehicleRepositoryImpl`**

Adicionar aos imports do topo do arquivo:

```kotlin
import android.net.Uri
import com.flowfuel.app.core.media.ImagePickerHelper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
```

Alterar o construtor da classe para injetar o helper de compressão:

```kotlin
@Singleton
class VehicleRepositoryImpl @Inject constructor(
    private val api: VehicleApi,
    private val imagePickerHelper: ImagePickerHelper,
) : VehicleRepository {
```

Adicionar o método logo após `updateVehicle` (antes do `private fun VehicleResponseDto.toDomain()`):

```kotlin
    // ─── Foto ─────────────────────────────────────────────────────────────────

    override suspend fun uploadVehiclePhoto(vehicleId: Int, uri: Uri): AppResult<String> = try {
        val compressed = imagePickerHelper.compressToJpeg(uri)
        val requestBody = compressed.toRequestBody("image/jpeg".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "vehicle.jpg", requestBody)
        apiCall { api.uploadVehiclePhoto(vehicleId, part) }.map { it.photo.orEmpty() }
    } catch (e: Throwable) {
        Timber.e(e, "VehicleRepo › erro ao comprimir imagem")
        AppResult.Failure(AppError.Unknown(e))
    }
```

- [ ] **Step 3: Compilar para verificar que não há erros**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Hilt resolve `ImagePickerHelper` automaticamente via `@Singleton @Inject constructor` já existente — nenhuma mudança de módulo DI é necessária, mesmo padrão de `ProfileRepositoryImpl`)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/domain/VehicleRepository.kt app/src/main/java/com/flowfuel/app/feature/vehicle/data/VehicleRepositoryImpl.kt
git commit -m "feat(vehicle): implementa upload de foto no VehicleRepository"
```

---

### Task 3: `UploadVehiclePhotoUseCase`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicle/domain/usecase/UploadVehiclePhotoUseCase.kt`

**Interfaces:**
- Consumes: `VehicleRepository.uploadVehiclePhoto(vehicleId: Int, uri: Uri): AppResult<String>` (Task 2).
- Produces: `UploadVehiclePhotoUseCase.invoke(vehicleId: Int, uri: Uri): AppResult<String>` — consumido pela Task 4 (`AddVehicleViewModel`).

Sem teste dedicado — mesmo padrão de `UploadProfilePictureUseCase`, que também não tem teste próprio (é um passthrough fino sobre o repositório).

- [ ] **Step 1: Criar o Use Case**

```kotlin
package com.flowfuel.app.feature.vehicle.domain.usecase

import android.net.Uri
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import javax.inject.Inject

class UploadVehiclePhotoUseCase @Inject constructor(
    private val repository: VehicleRepository,
) {
    suspend operator fun invoke(vehicleId: Int, uri: Uri): AppResult<String> =
        repository.uploadVehiclePhoto(vehicleId, uri)
}
```

- [ ] **Step 2: Compilar para verificar que não há erros**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/domain/usecase/UploadVehiclePhotoUseCase.kt
git commit -m "feat(vehicle): adiciona UploadVehiclePhotoUseCase"
```

---

### Task 4: `AddVehicleViewModel` — 4º passo, estado e fluxo de submissão

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/add/AddVehicleViewModel.kt`
- Create: `app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/add/AddVehicleViewModelTest.kt`

**Interfaces:**
- Consumes: `CreateVehicleUseCase` (já existe, inalterado), `UploadVehiclePhotoUseCase.invoke(vehicleId: Int, uri: Uri): AppResult<String>` (Task 3).
- Produces: `AddVehicleUiState` com `photoUri: Uri?`, `photoUploadError: AppError?`, `createdVehicleId: Int?`, `canSubmit: Boolean` (redefinido); `AddVehicleViewModel.onPhotoPicked(uri: Uri)`, `onSkipToPhotoStep()`, `onNextStep()` (agora trata `currentStep == 3`), `submit()` (sem parâmetro `skipOptional`) — consumidos pela Task 5 (`AddVehicleScreen`).

Este é o núcleo de lógica de negócio da feature — segue TDD com testes escritos antes da implementação, seguindo o padrão de `RegisterViewModelTest` (Robolectric + MockK + Turbine, use cases mockados diretamente).

- [ ] **Step 1: Escrever o teste completo (falhando)**

Criar `app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/add/AddVehicleViewModelTest.kt`:

```kotlin
package com.flowfuel.app.feature.vehicle.presentation.add

import android.net.Uri
import app.cash.turbine.test
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import com.flowfuel.app.feature.vehicle.domain.usecase.CreateVehicleUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.UploadVehiclePhotoUseCase
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AddVehicleViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val createVehicle: CreateVehicleUseCase = mockk()
    private val uploadVehiclePhoto: UploadVehiclePhotoUseCase = mockk()
    private lateinit var viewModel: AddVehicleViewModel

    private val photoUri: Uri = Uri.parse("content://media/test/photo.jpg")

    private val fixtureVehicle = Vehicle(
        id = 42,
        brand = "Toyota",
        model = "Corolla",
        manufactureYear = 2022,
        modelYear = 2023,
        licensePlate = "ABC1234",
        color = "Prata",
        type = VehicleType.Car,
        energyType = EnergyType.Combustion,
        fuelType = FuelType.Flex,
        odometerKm = 15000,
        tankCapacityL = 50.0,
        batteryCapacityKwh = null,
        isActive = true,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AddVehicleViewModel(createVehicle, uploadVehiclePhoto)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fillStep1() {
        viewModel.onBrandChange("Toyota")
        viewModel.onModelChange("Corolla")
        viewModel.onManufactureYearChange("2022")
        viewModel.onModelYearChange("2023")
    }

    private fun advanceToStep3() {
        fillStep1()
        viewModel.onNextStep() // 1 -> 2
        viewModel.onNextStep() // 2 -> 3
    }

    // ── Navegação do wizard ───────────────────────────────────────────────────

    @Test
    fun `onNextStep from step 3 with invalid plate sets licensePlateError and stays on step 3`() {
        advanceToStep3()
        viewModel.onLicensePlateChange("ABC")

        viewModel.onNextStep()

        assertEquals(3, viewModel.state.value.currentStep)
        assertTrue(viewModel.state.value.licensePlateError)
    }

    @Test
    fun `onNextStep from step 3 with valid plate advances to step 4`() {
        advanceToStep3()
        viewModel.onLicensePlateChange("ABC1234")

        viewModel.onNextStep()

        assertEquals(4, viewModel.state.value.currentStep)
        assertFalse(viewModel.state.value.licensePlateError)
    }

    @Test
    fun `onSkipToPhotoStep advances to step 4 without validating plate`() {
        advanceToStep3()

        viewModel.onSkipToPhotoStep()

        assertEquals(4, viewModel.state.value.currentStep)
        assertFalse(viewModel.state.value.licensePlateError)
    }

    // ── canSubmit ──────────────────────────────────────────────────────────────

    @Test
    fun `canSubmit false without photo`() {
        assertFalse(viewModel.state.value.canSubmit)
    }

    @Test
    fun `canSubmit true after photo picked`() {
        viewModel.onPhotoPicked(photoUri)

        assertTrue(viewModel.state.value.canSubmit)
    }

    // ── onPhotoPicked ──────────────────────────────────────────────────────────

    @Test
    fun `onPhotoPicked sets photoUri and clears previous upload error`() {
        coEvery { createVehicle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            AppResult.Success(fixtureVehicle)
        coEvery { uploadVehiclePhoto(any(), any()) } returns AppResult.Failure(AppError.Network)
        fillStep1()
        viewModel.onPhotoPicked(photoUri)
        viewModel.submit()
        assertNotNull(viewModel.state.value.photoUploadError)

        viewModel.onPhotoPicked(photoUri)

        assertEquals(photoUri, viewModel.state.value.photoUri)
        assertNull(viewModel.state.value.photoUploadError)
    }

    // ── submit — guard ────────────────────────────────────────────────────────

    @Test
    fun `submit without photo does not call createVehicle`() {
        fillStep1()

        viewModel.submit()

        coVerify(exactly = 0) { createVehicle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    // ── submit — sucesso total ───────────────────────────────────────────────

    @Test
    fun `submit success creates vehicle then uploads photo and emits NavigateBack`() = runTest {
        coEvery { createVehicle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            AppResult.Success(fixtureVehicle)
        coEvery { uploadVehiclePhoto(any(), any()) } returns AppResult.Success("https://cdn.example.com/photo.jpg")
        fillStep1()
        viewModel.onPhotoPicked(photoUri)

        viewModel.effects.test {
            viewModel.submit()
            assertEquals(AddVehicleEffect.NavigateBack, awaitItem())
        }
        coVerify(exactly = 1) { uploadVehiclePhoto(42, photoUri) }
    }

    @Test
    fun `submit success resets isSubmitting to false`() {
        coEvery { createVehicle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            AppResult.Success(fixtureVehicle)
        coEvery { uploadVehiclePhoto(any(), any()) } returns AppResult.Success("https://cdn.example.com/photo.jpg")
        fillStep1()
        viewModel.onPhotoPicked(photoUri)

        viewModel.submit()

        assertFalse(viewModel.state.value.isSubmitting)
    }

    // ── submit — falha na criação ────────────────────────────────────────────

    @Test
    fun `submit create failure does not call uploadVehiclePhoto`() {
        coEvery { createVehicle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            AppResult.Failure(AppError.Network)
        fillStep1()
        viewModel.onPhotoPicked(photoUri)

        viewModel.submit()

        coVerify(exactly = 0) { uploadVehiclePhoto(any(), any()) }
        assertEquals(AppError.Network, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    // ── submit — falha só no upload (retry) ──────────────────────────────────

    @Test
    fun `submit upload failure keeps vehicle id and sets photoUploadError without navigating`() = runTest {
        coEvery { createVehicle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            AppResult.Success(fixtureVehicle)
        coEvery { uploadVehiclePhoto(any(), any()) } returns AppResult.Failure(AppError.Network)
        fillStep1()
        viewModel.onPhotoPicked(photoUri)

        viewModel.effects.test {
            viewModel.submit()
            expectNoEvents()
        }
        assertEquals(42, viewModel.state.value.createdVehicleId)
        assertEquals(AppError.Network, viewModel.state.value.photoUploadError)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun `submit retry after upload failure does not call createVehicle again`() = runTest {
        coEvery { createVehicle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            AppResult.Success(fixtureVehicle)
        coEvery { uploadVehiclePhoto(any(), any()) } returns AppResult.Failure(AppError.Network)
        fillStep1()
        viewModel.onPhotoPicked(photoUri)
        viewModel.submit() // primeira tentativa: cria veículo, upload falha

        coEvery { uploadVehiclePhoto(any(), any()) } returns AppResult.Success("https://cdn.example.com/photo.jpg")
        viewModel.effects.test {
            viewModel.submit() // retry
            assertEquals(AddVehicleEffect.NavigateBack, awaitItem())
        }

        coVerify(exactly = 1) { createVehicle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 2) { uploadVehiclePhoto(42, photoUri) }
    }
}
```

- [ ] **Step 2: Rodar os testes para confirmar que falham**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.add.AddVehicleViewModelTest"`
Expected: FAIL — não compila ainda (`AddVehicleViewModel` não tem `photoUri`, `onPhotoPicked`, `onSkipToPhotoStep`, `createdVehicleId`, `photoUploadError`, nem construtor com `UploadVehiclePhotoUseCase`)

- [ ] **Step 3: Reescrever `AddVehicleViewModel.kt` com o novo estado e fluxo**

Substituir o conteúdo completo de `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/add/AddVehicleViewModel.kt` por:

```kotlin
package com.flowfuel.app.feature.vehicle.presentation.add

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.FieldError
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import com.flowfuel.app.feature.vehicle.domain.usecase.CreateVehicleUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.UploadVehiclePhotoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddVehicleUiState(
    // — Etapa 1: Identificação
    val brand: String = "",
    val model: String = "",
    val manufactureYear: String = "",
    val modelYear: String = "",
    // — Etapa 2: Classificação
    val vehicleType: VehicleType = VehicleType.Car,
    val energyType: EnergyType = EnergyType.Combustion,
    val fuelType: FuelType = FuelType.Flex,
    // — Etapa 3: Detalhes
    val licensePlate: String = "",
    val color: String = "",
    val odometer: String = "",
    val tankCapacity: String = "",
    val batteryCapacity: String = "",
    // — Etapa 4: Foto
    val photoUri: Uri? = null,
    val photoUploadError: AppError? = null,
    /** Preenchido após a criação bem-sucedida do veículo; usado para retry do upload de foto. */
    val createdVehicleId: Int? = null,
    // — Wizard
    val currentStep: Int = 1,
    /**
     * Incrementado a cada falha de validação para que a UI possa reagir via
     * [LaunchedEffect] mesmo quando os campos inválidos são os mesmos da tentativa anterior.
     */
    val stepAttempt: Int = 0,
    // — Erros de validação por campo
    val brandError: Boolean = false,
    val modelError: Boolean = false,
    val manufactureYearError: Boolean = false,
    val modelYearError: Boolean = false,
    val licensePlateError: Boolean = false,
    // — Estado global
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    val serverErrors: List<FieldError>? = null,
) {
    val showFuelType: Boolean
        get() = energyType == EnergyType.Combustion || energyType == EnergyType.Hybrid

    val showTankCapacity: Boolean
        get() = energyType == EnergyType.Combustion || energyType == EnergyType.Hybrid

    val showBatteryCapacity: Boolean
        get() = energyType == EnergyType.Electric || energyType == EnergyType.Hybrid

    /**
     * Placa é validada (ou pulada via "Preencher depois") ao sair do Step 3;
     * a foto é sempre obrigatória para concluir o cadastro (Step 4).
     */
    val canSubmit: Boolean
        get() = photoUri != null && !isSubmitting
}

sealed interface AddVehicleEffect {
    data object NavigateBack : AddVehicleEffect
}

@HiltViewModel
class AddVehicleViewModel @Inject constructor(
    private val createVehicle: CreateVehicleUseCase,
    private val uploadVehiclePhoto: UploadVehiclePhotoUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AddVehicleUiState())
    val state: StateFlow<AddVehicleUiState> = _state.asStateFlow()

    private val _effects = Channel<AddVehicleEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    // — Etapa 1
    fun onBrandChange(v: String) =
        _state.update { it.copy(brand = v, brandError = false, error = null, serverErrors = null) }

    fun onModelChange(v: String) =
        _state.update { it.copy(model = v, modelError = false, error = null, serverErrors = null) }

    fun onManufactureYearChange(v: String) =
        _state.update {
            it.copy(
                manufactureYear      = v.filter(Char::isDigit).take(4),
                manufactureYearError = false,
                error                = null,
                serverErrors         = null,
            )
        }

    fun onModelYearChange(v: String) =
        _state.update {
            it.copy(
                modelYear      = v.filter(Char::isDigit).take(4),
                modelYearError = false,
                error          = null,
                serverErrors   = null,
            )
        }

    // — Etapa 2
    fun onVehicleTypeChange(v: VehicleType) = _state.update { it.copy(vehicleType = v) }
    fun onEnergyTypeChange(v: EnergyType)   = _state.update { it.copy(energyType = v) }
    fun onFuelTypeChange(v: FuelType)       = _state.update { it.copy(fuelType = v) }

    // — Etapa 3
    fun onLicensePlateChange(v: String) =
        _state.update { it.copy(licensePlate = v.uppercase().take(7), licensePlateError = false, error = null, serverErrors = null) }

    fun onColorChange(v: String) = _state.update { it.copy(color = v) }

    fun onOdometerChange(v: String) =
        _state.update { it.copy(odometer = v, error = null, serverErrors = null) }

    fun onTankCapacityChange(v: String)    = _state.update { it.copy(tankCapacity = v) }
    fun onBatteryCapacityChange(v: String) = _state.update { it.copy(batteryCapacity = v) }

    // — Etapa 4
    fun onPhotoPicked(uri: Uri) =
        _state.update { it.copy(photoUri = uri, photoUploadError = null) }

    fun clearError() = _state.update { it.copy(error = null) }

    /** Avança para a próxima etapa validando os campos da etapa atual. */
    fun onNextStep() {
        val s = _state.value
        when (s.currentStep) {
            1 -> {
                val brandInvalid  = s.brand.isBlank()
                val modelInvalid  = s.model.isBlank()
                val mfYearInvalid = s.manufactureYear.length < 4 || s.manufactureYear.toIntOrNull() == null
                val mdYearInvalid = s.modelYear.length < 4 || s.modelYear.toIntOrNull() == null
                if (brandInvalid || modelInvalid || mfYearInvalid || mdYearInvalid) {
                    _state.update {
                        it.copy(
                            brandError           = brandInvalid,
                            modelError           = modelInvalid,
                            manufactureYearError = mfYearInvalid,
                            modelYearError       = mdYearInvalid,
                            stepAttempt          = it.stepAttempt + 1,
                        )
                    }
                    return
                }
                _state.update { it.copy(currentStep = 2) }
            }
            2 -> _state.update { it.copy(currentStep = 3) }
            3 -> {
                val licensePlateInvalid = s.licensePlate.length < 7
                if (licensePlateInvalid) {
                    _state.update {
                        it.copy(
                            licensePlateError = true,
                            stepAttempt       = it.stepAttempt + 1,
                        )
                    }
                    return
                }
                _state.update { it.copy(currentStep = 4) }
            }
        }
    }

    /** Avança da Etapa 3 para a Etapa 4 sem validar a placa ("Preencher depois"). */
    fun onSkipToPhotoStep() {
        _state.update { it.copy(currentStep = 4, licensePlateError = false) }
    }

    fun onPreviousStep() {
        _state.update { it.copy(currentStep = (it.currentStep - 1).coerceAtLeast(1)) }
    }

    /**
     * Cria o veículo (se ainda não criado) e envia a foto.
     * Se uma tentativa anterior já criou o veículo mas o upload da foto falhou
     * ([AddVehicleUiState.createdVehicleId] não nulo), reenvia só a foto, sem
     * recriar o veículo.
     */
    fun submit() {
        val s = _state.value
        val photoUri = s.photoUri
        if (photoUri == null || s.isSubmitting) return

        _state.update { it.copy(isSubmitting = true, error = null, serverErrors = null, photoUploadError = null) }

        viewModelScope.launch {
            val vehicleId: Int = s.createdVehicleId ?: run {
                val result = createVehicle(
                    brand              = s.brand.trim(),
                    model              = s.model.trim(),
                    manufactureYear    = s.manufactureYear.toInt(),
                    modelYear          = s.modelYear.toInt(),
                    licensePlate       = s.licensePlate,
                    color              = s.color.trim().takeIf { it.isNotBlank() },
                    type               = s.vehicleType,
                    energyType         = s.energyType,
                    fuelType           = if (s.showFuelType) s.fuelType else null,
                    odometerKm         = s.odometer.toIntOrNull() ?: 0,
                    tankCapacityL      = if (s.showTankCapacity) {
                        s.tankCapacity.replace(",", ".").toDoubleOrNull()
                    } else null,
                    batteryCapacityKwh = if (s.showBatteryCapacity) {
                        s.batteryCapacity.replace(",", ".").toDoubleOrNull()
                    } else null,
                )
                when (result) {
                    is AppResult.Success -> result.value.id
                    is AppResult.Failure -> {
                        val apiErr      = result.error as? AppError.Api
                        val fieldErrors = apiErr?.takeIf { it.code == "VALIDATION_FAILED" }?.fieldErrors
                        if (!fieldErrors.isNullOrEmpty()) {
                            _state.update { it.copy(isSubmitting = false, serverErrors = fieldErrors) }
                        } else {
                            _state.update { it.copy(isSubmitting = false, error = result.error) }
                        }
                        return@launch
                    }
                }
            }

            _state.update { it.copy(createdVehicleId = vehicleId) }

            when (val uploadResult = uploadVehiclePhoto(vehicleId, photoUri)) {
                is AppResult.Success -> {
                    _state.update { it.copy(isSubmitting = false) }
                    _effects.send(AddVehicleEffect.NavigateBack)
                }
                is AppResult.Failure -> {
                    _state.update { it.copy(isSubmitting = false, photoUploadError = uploadResult.error) }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Rodar os testes para confirmar que passam**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.add.AddVehicleViewModelTest"`
Expected: PASS — todos os testes verdes

- [ ] **Step 5: Rodar a suíte completa de testes unitários para checar regressão**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, nenhum teste pré-existente quebrado (ex.: nenhum outro arquivo referenciava `submit(skipOptional: Boolean)` fora de `AddVehicleScreen.kt`, que é ajustado na Task 5)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/add/AddVehicleViewModel.kt app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/add/AddVehicleViewModelTest.kt
git commit -m "feat(vehicle): adiciona 4o passo obrigatorio de foto ao AddVehicleViewModel"
```

---

### Task 5: `AddVehicleScreen` — UI do 4º passo e strings

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/add/AddVehicleScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `AddVehicleUiState.photoUri`, `.photoUploadError`, `.canSubmit` (redefinido), `AddVehicleViewModel.onPhotoPicked(uri: Uri)`, `.onSkipToPhotoStep()`, `.onNextStep()`, `.submit()` (Task 4).
- Produces: nada consumido por outras tasks — é a ponta final da UI.

Sem teste automatizado (a tela já não tinha testes de UI/Compose antes desta mudança); a verificação é manual, coberta no Step 5 abaixo.

- [ ] **Step 1: Adicionar strings novas**

Em `app/src/main/res/values/strings.xml`, dentro do bloco `<!-- Vehicle — Cadastro -->` (linhas 137-144), logo após `vehicle_wizard_step3`, adicionar:

```xml
    <string name="vehicle_wizard_step4">Foto</string>
    <string name="vehicle_photo_instructions">Adicione uma foto do seu veículo. Isso ajuda a identificá-lo rapidamente no app.</string>
    <string name="vehicle_photo_pick_cta">Toque para escolher uma foto</string>
    <string name="vehicle_photo_change">Trocar foto</string>
    <string name="vehicle_photo_content_description">Foto do veículo</string>
    <string name="vehicle_photo_upload_error">Não foi possível enviar a foto. Tente novamente.</string>
    <string name="vehicle_photo_retry">Tentar novamente</string>
```

- [ ] **Step 2: Adicionar imports necessários em `AddVehicleScreen.kt`**

Adicionar aos imports existentes:

```kotlin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
```

- [ ] **Step 3: Atualizar `WizardStepper` para 4 passos**

Em `WizardStepper` (função privada), alterar:

```kotlin
    val stepLabels = listOf(
        stringResource(R.string.vehicle_wizard_step1),
        stringResource(R.string.vehicle_wizard_step2),
        stringResource(R.string.vehicle_wizard_step3),
    )
```

para:

```kotlin
    val stepLabels = listOf(
        stringResource(R.string.vehicle_wizard_step1),
        stringResource(R.string.vehicle_wizard_step2),
        stringResource(R.string.vehicle_wizard_step3),
        stringResource(R.string.vehicle_wizard_step4),
    )
```

- [ ] **Step 4: Atualizar o dispatch de passos no `AnimatedContent`**

Dentro de `AddVehicleScreen`, alterar:

```kotlin
            ) { step ->
                when (step) {
                    1 -> Step1Content(state = state, viewModel = viewModel)
                    2 -> Step2Content(state = state, viewModel = viewModel)
                    else -> Step3Content(state = state, viewModel = viewModel)
                }
            }
```

para:

```kotlin
            ) { step ->
                when (step) {
                    1 -> Step1Content(state = state, viewModel = viewModel)
                    2 -> Step2Content(state = state, viewModel = viewModel)
                    3 -> Step3Content(state = state, viewModel = viewModel)
                    else -> Step4Content(state = state, viewModel = viewModel)
                }
            }
```

- [ ] **Step 5: Atualizar a bottom bar para 4 passos**

Alterar o bloco da `bottomBar` (dentro de `Scaffold`), de:

```kotlin
                    FFButton(
                        text = if (state.currentStep < 3) stringResource(R.string.vehicle_add_continue)
                               else stringResource(R.string.vehicle_add_cta),
                        onClick = if (state.currentStep < 3) viewModel::onNextStep
                                  else ({ viewModel.submit() }),
                        enabled = if (state.currentStep == 3) state.canSubmit else !state.isSubmitting,
                        loading = state.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.currentStep == 3) {
                        FFButton(
                            text = stringResource(R.string.vehicle_add_fill_later),
                            onClick = { viewModel.submit(skipOptional = true) },
                            enabled = !state.isSubmitting,
                            variant = FFButtonVariant.Text,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
```

para:

```kotlin
                    FFButton(
                        text = when {
                            state.currentStep < 4        -> stringResource(R.string.vehicle_add_continue)
                            state.photoUploadError != null -> stringResource(R.string.vehicle_photo_retry)
                            else                          -> stringResource(R.string.vehicle_add_cta)
                        },
                        onClick = if (state.currentStep < 4) viewModel::onNextStep
                                  else ({ viewModel.submit() }),
                        enabled = if (state.currentStep == 4) state.canSubmit else !state.isSubmitting,
                        loading = state.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.currentStep == 3) {
                        FFButton(
                            text = stringResource(R.string.vehicle_add_fill_later),
                            onClick = viewModel::onSkipToPhotoStep,
                            enabled = !state.isSubmitting,
                            variant = FFButtonVariant.Text,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
```

- [ ] **Step 6: Adicionar `Step4Content`**

Logo após a função `Step3Content` (antes da seção `// ─── Stepper visual`), adicionar:

```kotlin
@Composable
private fun Step4Content(
    state: AddVehicleUiState,
    viewModel: AddVehicleViewModel,
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.onPhotoPicked(it) } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.vehicle_photo_instructions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(MaterialTheme.shapes.medium)
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.medium,
                )
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .clickable(enabled = !state.isSubmitting) {
                    launcher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (state.photoUri != null) {
                AsyncImage(
                    model = state.photoUri,
                    contentDescription = stringResource(R.string.vehicle_photo_content_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddAPhoto,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        text = stringResource(R.string.vehicle_photo_pick_cta),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (state.photoUri != null) {
            FFButton(
                text = stringResource(R.string.vehicle_photo_change),
                onClick = {
                    launcher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = !state.isSubmitting,
                variant = FFButtonVariant.Text,
            )
        }

        if (state.photoUploadError != null) {
            Text(
                text = stringResource(R.string.vehicle_photo_upload_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}
```

- [ ] **Step 7: Compilar**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Rodar a suíte completa de testes unitários**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, todos os testes passando (incluindo os novos de `AddVehicleViewModelTest`)

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/add/AddVehicleScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat(vehicle): adiciona UI do 4o passo (foto obrigatoria) ao wizard de cadastro"
```

---

### Task 6: Verificação manual end-to-end da UI (bloqueada parcialmente pelo backend)

**Files:** nenhum arquivo novo — só verificação.

- [ ] **Step 1: Rodar o app no emulador**

Usar a skill `run-android-emulator` (ou `./gradlew :app:installDebug` + abrir manualmente) para instalar e abrir o app.

- [ ] **Step 2: Verificar a navegação do wizard até o novo passo**

Ir em Perfil → Meus veículos → "Novo veículo" (ou o fluxo pós-cadastro se houver 0 veículos). Preencher os passos 1–3 normalmente. Confirmar que:
- O `WizardStepper` mostra 4 círculos, o 4º com o label "Foto".
- Ao clicar "Continuar" no passo 3 com placa válida, avança para o passo 4.
- Ao clicar "Preencher depois" no passo 3, também avança direto para o passo 4 (sem validar placa).

- [ ] **Step 3: Verificar o comportamento do passo 4**

No passo 4, confirmar que:
- O botão final ("Cadastrar veículo") está desabilitado antes de escolher uma foto.
- Tocar na área de foto abre o seletor de imagens (Photo Picker) sem pedir nenhuma permissão de runtime.
- Após escolher uma imagem, o preview aparece no lugar do ícone, e o botão "Cadastrar veículo" fica habilitado.
- "Trocar foto" reabre o seletor.

- [ ] **Step 4: Documentar o bloqueio esperado do backend**

Ao confirmar "Cadastrar veículo": a criação do veículo (`POST /vehicles`) deve funcionar normalmente (endpoint já existe), mas o upload da foto (`POST /vehicles/{id}/photo`) vai falhar com 404 até o backend implementar o endpoint (ver Global Constraints). Confirmar que, nesse cenário:
- A tela mostra a mensagem de erro de upload (`vehicle_photo_upload_error`).
- O botão muda para "Tentar novamente".
- O app **não trava nem perde os dados já preenchidos** ao tentar novamente.

Isso é o comportamento esperado e correto do app dado que o backend ainda não expõe o endpoint — não é um bug a corrigir neste plano. Reportar ao time de backend a necessidade de implementar `POST /api/v1/vehicles/{id}/photo` conforme o contrato documentado na spec.

- [ ] **Step 5: Registrar o resultado**

Nenhum commit necessário neste task — é só validação manual. Se algo do Step 2/3 falhar (não relacionado ao backend ausente), voltar para a Task 5 e corrigir antes de finalizar.

---

## Self-Review

**Cobertura da spec:** as 4 seções "Dentro do escopo" da spec estão cobertas — 4º passo do wizard (Tasks 4-5), fluxo de submissão em duas chamadas com retry (Task 4), endpoint/DTO/repositório/use case (Tasks 1-3), strings novas (Task 5). As seções "Fora do escopo" (câmera, exibição em outras telas, edição, backend real) não têm tasks correspondentes, como esperado.

**Consistência de tipos:** `UploadVehiclePhotoUseCase.invoke(vehicleId: Int, uri: Uri): AppResult<String>` (Task 3) é o mesmo assinatura usada em `AddVehicleViewModel.submit()` (Task 4) e mockada em `AddVehicleViewModelTest`. `VehicleRepository.uploadVehiclePhoto(vehicleId: Int, uri: Uri)` (Task 2) casa com o consumo em `UploadVehiclePhotoUseCase`. `canSubmit` foi redefinido uma única vez (Task 4) e todo uso em `AddVehicleScreen.kt` (Task 5) reflete a nova semântica (gate do passo 4, não mais do passo 3).
