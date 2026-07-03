# Exibição e troca da foto do veículo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Exibir a foto do veículo (já enviada na criação) em `VehicleDetailsScreen` e permitir trocá-la em `EditVehicleScreen`, usando os endpoints `GET`/`DELETE /vehicles/{id}/photo` que o backend acabou de implementar (o `POST` já existe desde o plano `2026-07-03-vehicle-photo-required`).

**Architecture:** `Vehicle` (domínio) ganha `photoUrl: String?`, mapeado a partir de `VehicleResponseDto.photo` do mesmo jeito que `UserResponseDto` monta `profilePictureUrl` (URL interna autenticada — o Coil `ImageLoader` global já compartilha o `OkHttpClient` autenticado, então basta um `AsyncImage` comum). Um novo composable compartilhado `VehiclePhotoAvatar` (mesma técnica de fallback-atrás/imagem-na-frente de `UserAvatar`) é usado em `VehicleDetailsScreen` (só leitura) e em `EditVehicleScreen` (clicável, reenvia via o `POST` de upload já existente, upload imediato e independente do botão "Salvar" — mesmo padrão de `ProfileViewModel.onPickImage`). `DELETE /vehicles/{id}/photo` é implementado no client (`VehicleApi`/`VehicleRepository`/`DeleteVehiclePhotoUseCase`) mas não é chamado de nenhuma tela nesta rodada.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Retrofit + Kotlinx Serialization, Coroutines/StateFlow, Coil 2.7 (`coil.compose.AsyncImage`), `ActivityResultContracts.PickVisualMedia`, MockK + Robolectric para testes de ViewModel.

## Global Constraints

- Nenhum botão de "remover foto" na UI — `DeleteVehiclePhotoUseCase` fica implementado e pronto, mas sem chamador.
- Trocar a foto em `EditVehicleScreen` é só `POST` sobrescrevendo — não chama `DELETE` antes.
- Sem mudanças em `VehiclesScreen`/`VehiclePickerScreen` (cards de lista continuam sem thumbnail).
- Sem captura por câmera — segue só galeria (`PickVisualMedia`, sem permissão de runtime).
- **Verificar empiricamente o formato real da URL retornada em `VehicleResponseDto.photo`** antes de considerar a feature concluída (Task 7) — este mesmo fluxo já teve um contrato assumido incorretamente uma vez (`{photo}` vs `{internalUrl}` no `POST`, corrigido no commit `d3872c8`).

---

### Task 1: `Vehicle.photoUrl` — domínio e mapeamento

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/domain/model/VehicleModels.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/data/VehicleRepositoryImpl.kt`

**Interfaces:**
- Consumes: `VehicleResponseDto.photo: String?` (já existe em `VehicleApi.kt`).
- Produces: `Vehicle.photoUrl: String?` — consumido pela Task 3 (`VehiclePhotoAvatar`), Task 4 (`VehicleDetailsScreen`) e Task 5 (`EditVehicleViewModel`).

Sem teste dedicado — não existe nenhum arquivo de teste de `VehicleRepositoryImpl` no projeto hoje (mapeamento de DTO é sempre coberto indiretamente pelos testes de ViewModel que consomem os use cases).

- [ ] **Step 1: Adicionar `photoUrl` ao domínio `Vehicle`**

Em `VehicleModels.kt`, o `data class Vehicle` termina em:

```kotlin
    val isActive: Boolean,
)
```

Alterar para:

```kotlin
    val isActive: Boolean,
    /** URL interna autenticada da foto do veículo, ou null se não houver foto. */
    val photoUrl: String? = null,
)
```

- [ ] **Step 2: Mapear `photo` → `photoUrl` em `VehicleRepositoryImpl.toDomain()`**

Adicionar o import no topo de `VehicleRepositoryImpl.kt` (junto aos demais imports `com.flowfuel.app.*`):

```kotlin
import com.flowfuel.app.BuildConfig
```

Em `VehicleRepositoryImpl.kt`, o final de `toDomain()` está assim:

```kotlin
            batteryCapacityKwh = capacity.takeIf {
                resolvedEnergyType == EnergyType.Electric || resolvedEnergyType == EnergyType.Hybrid
            },
            isActive = isActive,
        )
    }
}
```

Alterar para:

```kotlin
            batteryCapacityKwh = capacity.takeIf {
                resolvedEnergyType == EnergyType.Electric || resolvedEnergyType == EnergyType.Hybrid
            },
            isActive = isActive,
            photoUrl = photo?.let { BuildConfig.API_BASE_URL.trimEnd('/') + it },
        )
    }
}
```

- [ ] **Step 3: Compilar para verificar que não há erros**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Rodar a suíte completa de testes unitários para checar regressão**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — `photoUrl` tem valor padrão `null`, então nenhuma construção existente de `Vehicle` (todas usam argumentos nomeados) quebra.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/domain/model/VehicleModels.kt app/src/main/java/com/flowfuel/app/feature/vehicle/data/VehicleRepositoryImpl.kt
git commit -m "feat(vehicle): adiciona photoUrl ao dominio Vehicle e mapeia a partir do DTO"
```

---

### Task 2: Cliente para `DELETE /vehicles/{id}/photo`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/data/remote/VehicleApi.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/domain/VehicleRepository.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/data/VehicleRepositoryImpl.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicle/domain/usecase/DeleteVehiclePhotoUseCase.kt`

**Interfaces:**
- Consumes: nada de tasks anteriores.
- Produces: `DeleteVehiclePhotoUseCase.invoke(vehicleId: Int): AppResult<Unit>` — não é consumido por nenhuma task deste plano (fica pronto para uso futuro, conforme o escopo do design).

Sem teste dedicado — mesmo padrão de `deleteVehicle`/`setActiveVehicle` (try-catch manual sobre `ResponseBody?`, sem teste de repositório no projeto) e de `UploadVehiclePhotoUseCase` (use case passthrough sem teste próprio).

- [ ] **Step 1: Adicionar o método à interface `VehicleApi`**

Em `VehicleApi.kt`, `DELETE` e `ResponseBody` já estão importados (usados por `deleteVehicle`). Adicionar, logo após o método `uploadVehiclePhoto` (último método da interface):

```kotlin

    /**
     * Remove a foto do veículo.
     * Usa [ResponseBody]? (mesmo padrão de [deleteVehicle]) pois 204 sem corpo
     * não passa pelo conversor JSON.
     */
    @DELETE("vehicles/{id}/photo")
    suspend fun deleteVehiclePhoto(@Path("id") id: Int): ResponseBody?
```

- [ ] **Step 2: Adicionar o método à interface `VehicleRepository`**

Em `VehicleRepository.kt`, adicionar ao final da interface, logo após `uploadVehiclePhoto`:

```kotlin

    /** Remove a foto do veículo. Retorna sucesso mesmo para 204 sem corpo. */
    suspend fun deletePhoto(vehicleId: Int): AppResult<Unit>
```

- [ ] **Step 3: Implementar em `VehicleRepositoryImpl`**

Em `VehicleRepositoryImpl.kt`, adicionar logo após `uploadVehiclePhoto` (antes de `private fun VehicleResponseDto.toDomain()`):

```kotlin

    /**
     * Chama DELETE /vehicles/{id}/photo.
     * Mesmo padrão try-catch manual de [deleteVehicle]/[setActiveVehicle].
     */
    override suspend fun deletePhoto(vehicleId: Int): AppResult<Unit> = try {
        api.deleteVehiclePhoto(vehicleId)?.close()
        AppResult.Success(Unit)
    } catch (e: HttpException) {
        Timber.w("deletePhoto: HTTP ${e.code()}")
        if (e.code() == 401) AppResult.Failure(AppError.Unauthorized)
        else AppResult.Failure(AppError.Api("HTTP_${e.code()}", e.message()))
    } catch (e: IOException) {
        Timber.w(e, "deletePhoto: network error")
        AppResult.Failure(AppError.Network)
    } catch (e: Throwable) {
        Timber.e(e, "deletePhoto: unexpected error")
        AppResult.Failure(AppError.Unknown(e))
    }
```

- [ ] **Step 4: Criar o Use Case**

```kotlin
package com.flowfuel.app.feature.vehicle.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import javax.inject.Inject

class DeleteVehiclePhotoUseCase @Inject constructor(
    private val repository: VehicleRepository,
) {
    suspend operator fun invoke(vehicleId: Int): AppResult<Unit> =
        repository.deletePhoto(vehicleId)
}
```

- [ ] **Step 5: Compilar para verificar que não há erros**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/data/remote/VehicleApi.kt app/src/main/java/com/flowfuel/app/feature/vehicle/domain/VehicleRepository.kt app/src/main/java/com/flowfuel/app/feature/vehicle/data/VehicleRepositoryImpl.kt app/src/main/java/com/flowfuel/app/feature/vehicle/domain/usecase/DeleteVehiclePhotoUseCase.kt
git commit -m "feat(vehicle): implementa cliente para DELETE /vehicles/{id}/photo"
```

---

### Task 3: `VehiclePhotoAvatar` — composable compartilhado

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/core/designsystem/components/VehiclePhotoAvatar.kt`

**Interfaces:**
- Consumes: `VehicleType` (`com.flowfuel.app.feature.vehicle.domain.model.VehicleType`, já existe).
- Produces: `VehiclePhotoAvatar(photoUrl: String?, vehicleType: VehicleType, modifier: Modifier = Modifier, size: Dp = 64.dp, onClick: (() -> Unit)? = null)` — consumido pela Task 4 (`VehicleDetailsScreen`) e Task 6 (`EditVehicleScreen`).

Sem teste automatizado — nenhum composable do projeto tem teste de UI/Compose isolado (mesmo padrão de `UserAvatar`, que esta implementação espelha diretamente). Verificação visual na Task 7.

- [ ] **Step 1: Criar o composable**

```kotlin
package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType

/**
 * Avatar circular do veículo: exibe [photoUrl] via Coil quando presente,
 * com fallback para o ícone de [vehicleType] (mesma técnica de
 * fallback-atrás/imagem-crossfade-na-frente de [UserAvatar]).
 */
@Composable
fun VehiclePhotoAvatar(
    photoUrl: String?,
    vehicleType: VehicleType,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    onClick: (() -> Unit)? = null,
) {
    val badgeSize = (size.value * 0.29f).coerceAtLeast(16f).dp
    val badgeIconSize = (badgeSize.value * 0.57f).dp

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .then(
                    if (onClick != null) Modifier
                        .clickable { onClick() }
                        .semantics { contentDescription = "Foto do veículo. Toque para alterar" }
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Fallback sempre renderizado atrás — visível durante o carregamento e em erro
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (vehicleType == VehicleType.Motorcycle) Icons.Filled.TwoWheeler
                                      else Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(0.5f),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            // Imagem crossfade por cima — invisível durante o carregamento e em erro
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photoUrl)
                        .crossfade(300)
                        .build(),
                    contentDescription = "Foto do veículo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (onClick != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(badgeSize),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(badgeIconSize),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Compilar para verificar que não há erros**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/designsystem/components/VehiclePhotoAvatar.kt
git commit -m "feat(designsystem): adiciona VehiclePhotoAvatar compartilhado"
```

---

### Task 4: `VehicleDetailsScreen` — exibição da foto

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/details/VehicleDetailsScreen.kt`

**Interfaces:**
- Consumes: `Vehicle.photoUrl` (Task 1), `VehiclePhotoAvatar` (Task 3).
- Produces: nada consumido por outras tasks.

Sem teste automatizado — a tela não tem testes de UI/Compose hoje (a lógica de estado fica em `VehicleDetailsViewModel`, que não muda neste plano). Verificação manual na Task 7.

- [ ] **Step 1: Adicionar o import**

```kotlin
import com.flowfuel.app.core.designsystem.components.VehiclePhotoAvatar
```

- [ ] **Step 2: Substituir o avatar de ícone fixo por `VehiclePhotoAvatar` em `VehicleHeader`**

Em `VehicleDetailsScreen.kt`, `VehicleHeader` está assim:

```kotlin
@Composable
private fun VehicleHeader(vehicle: Vehicle) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = if (vehicle.type == VehicleType.Motorcycle) Icons.Default.TwoWheeler
                                  else Icons.Default.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Column(
```

Alterar para:

```kotlin
@Composable
private fun VehicleHeader(vehicle: Vehicle) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
    ) {
        VehiclePhotoAvatar(
            photoUrl = vehicle.photoUrl,
            vehicleType = vehicle.type,
            size = 64.dp,
        )
        Column(
```

- [ ] **Step 3: Compilar para verificar que não há erros**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/details/VehicleDetailsScreen.kt
git commit -m "feat(vehicle): exibe a foto do veiculo em VehicleDetailsScreen"
```

---

### Task 5: `EditVehicleUiState` + `EditVehicleViewModel` — estado e upload de foto (TDD)

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/edit/EditVehicleUiState.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/edit/EditVehicleViewModel.kt`
- Create: `app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/edit/EditVehicleViewModelTest.kt`

**Interfaces:**
- Consumes: `UploadVehiclePhotoUseCase.invoke(vehicleId: Int, uri: Uri): AppResult<String>` (já existe, criado no plano `2026-07-03-vehicle-photo-required`), `Vehicle.photoUrl` (Task 1).
- Produces: `EditVehicleUiState.photoUrl: String?`, `.isUploadingPhoto: Boolean`, `.photoUploadError: AppError?`; `EditVehicleViewModel.onPhotoPicked(uri: Uri)`, `.clearPhotoUploadError()` — consumidos pela Task 6 (`EditVehicleScreen`).

Este é o núcleo de lógica de negócio da task — TDD com testes escritos antes da implementação, seguindo o padrão de `AddVehicleViewModelTest` (Robolectric + MockK) e o padrão de `SavedStateHandle` de `VehicleEventsViewModelTest`. Não existe hoje nenhum arquivo `EditVehicleViewModelTest`.

- [ ] **Step 1: Adicionar os campos novos a `EditVehicleUiState`**

Em `EditVehicleUiState.kt`, o final do `data class EditVehicleUiState` (antes do `)`) está assim:

```kotlin
    val isSubmitting: Boolean = false,
    val formError: AppError? = null,
    val serverErrors: List<FieldError>? = null,
    val submitAttempt: Int = 0,
    val isDirty: Boolean = false,
    val showDiscardDialog: Boolean = false,
) {
```

Alterar para:

```kotlin
    val isSubmitting: Boolean = false,
    val formError: AppError? = null,
    val serverErrors: List<FieldError>? = null,
    val submitAttempt: Int = 0,
    val isDirty: Boolean = false,
    val showDiscardDialog: Boolean = false,
    // — Foto (upload imediato, independente do botão Salvar)
    val photoUrl: String? = null,
    val isUploadingPhoto: Boolean = false,
    val photoUploadError: AppError? = null,
) {
```

- [ ] **Step 2: Escrever o teste completo (falhando)**

Criar `app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/edit/EditVehicleViewModelTest.kt`:

```kotlin
package com.flowfuel.app.feature.vehicle.presentation.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import com.flowfuel.app.feature.vehicle.domain.usecase.GetVehicleByIdUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.UpdateVehicleUseCase
import com.flowfuel.app.feature.vehicle.domain.usecase.UploadVehiclePhotoUseCase
import io.mockk.coEvery
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EditVehicleViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val getVehicleById: GetVehicleByIdUseCase = mockk()
    private val updateVehicle: UpdateVehicleUseCase = mockk()
    private val uploadVehiclePhoto: UploadVehiclePhotoUseCase = mockk()
    private val sessionStore: SessionStore = mockk(relaxed = true)

    private val savedStateHandle = SavedStateHandle(mapOf("vehicleId" to 42))
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
        photoUrl = "https://cdn.example.com/old-photo.jpg",
    )

    private fun createViewModel(): EditVehicleViewModel {
        coEvery { getVehicleById(42) } returns AppResult.Success(fixtureVehicle)
        return EditVehicleViewModel(savedStateHandle, getVehicleById, updateVehicle, sessionStore, uploadVehiclePhoto)
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
    fun `load populates photoUrl from loaded vehicle`() {
        val viewModel = createViewModel()

        assertEquals("https://cdn.example.com/old-photo.jpg", viewModel.state.value.photoUrl)
    }

    @Test
    fun `onPhotoPicked success updates photoUrl and resets isUploadingPhoto`() {
        val viewModel = createViewModel()
        coEvery { uploadVehiclePhoto(42, photoUri) } returns AppResult.Success("https://cdn.example.com/new-photo.jpg")

        viewModel.onPhotoPicked(photoUri)

        assertEquals("https://cdn.example.com/new-photo.jpg", viewModel.state.value.photoUrl)
        assertFalse(viewModel.state.value.isUploadingPhoto)
        assertNull(viewModel.state.value.photoUploadError)
    }

    @Test
    fun `onPhotoPicked failure sets photoUploadError, resets isUploadingPhoto and keeps previous photoUrl`() {
        val viewModel = createViewModel()
        coEvery { uploadVehiclePhoto(42, photoUri) } returns AppResult.Failure(AppError.Network)

        viewModel.onPhotoPicked(photoUri)

        assertEquals(AppError.Network, viewModel.state.value.photoUploadError)
        assertFalse(viewModel.state.value.isUploadingPhoto)
        assertEquals("https://cdn.example.com/old-photo.jpg", viewModel.state.value.photoUrl)
    }

    @Test
    fun `clearPhotoUploadError resets error to null`() {
        val viewModel = createViewModel()
        coEvery { uploadVehiclePhoto(42, photoUri) } returns AppResult.Failure(AppError.Network)
        viewModel.onPhotoPicked(photoUri)

        viewModel.clearPhotoUploadError()

        assertNull(viewModel.state.value.photoUploadError)
    }
}
```

- [ ] **Step 3: Rodar os testes para confirmar que falham**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.edit.EditVehicleViewModelTest"`
Expected: FAIL — não compila ainda (`EditVehicleViewModel` não aceita `UploadVehiclePhotoUseCase` no construtor, `EditVehicleUiState` não tem `photoUrl`/`isUploadingPhoto`/`photoUploadError`, `onPhotoPicked`/`clearPhotoUploadError` não existem)

- [ ] **Step 4: Adicionar os imports e o parâmetro de construtor em `EditVehicleViewModel`**

No topo de `EditVehicleViewModel.kt`, adicionar:

```kotlin
import android.net.Uri
```

e, junto aos demais imports de use case:

```kotlin
import com.flowfuel.app.feature.vehicle.domain.usecase.UploadVehiclePhotoUseCase
```

e:

```kotlin
import timber.log.Timber
```

Alterar a assinatura do construtor de:

```kotlin
class EditVehicleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getVehicleById: GetVehicleByIdUseCase,
    private val updateVehicle: UpdateVehicleUseCase,
    private val sessionStore: SessionStore,
) : ViewModel() {
```

para:

```kotlin
class EditVehicleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getVehicleById: GetVehicleByIdUseCase,
    private val updateVehicle: UpdateVehicleUseCase,
    private val sessionStore: SessionStore,
    private val uploadVehiclePhoto: UploadVehiclePhotoUseCase,
) : ViewModel() {
```

- [ ] **Step 5: Popular `photoUrl` em `load()`**

Em `load()`, o bloco de construção de `loaded` está assim:

```kotlin
                    val loaded = EditVehicleUiState(
                        screenState      = EditVehicleScreenState.Editing,
                        brand            = vehicle.brand,
                        model            = vehicle.model,
                        manufactureYear  = vehicle.manufactureYear?.toString() ?: "",
                        modelYear        = vehicle.modelYear?.toString() ?: "",
                        licensePlate     = vehicle.licensePlate ?: "",
                        color            = vehicle.color ?: "",
                        vehicleType      = vehicle.type,
                        energyType       = vehicle.energyType,
                        fuelType         = vehicle.fuelType ?: FuelType.Flex,
                        odometer         = vehicle.odometerKm.toString(),
                        tankCapacity     = vehicle.tankCapacityL?.toFormString() ?: "",
                        batteryCapacity  = vehicle.batteryCapacityKwh?.toFormString() ?: "",
                    )
```

Alterar para (só a última linha é nova):

```kotlin
                    val loaded = EditVehicleUiState(
                        screenState      = EditVehicleScreenState.Editing,
                        brand            = vehicle.brand,
                        model            = vehicle.model,
                        manufactureYear  = vehicle.manufactureYear?.toString() ?: "",
                        modelYear        = vehicle.modelYear?.toString() ?: "",
                        licensePlate     = vehicle.licensePlate ?: "",
                        color            = vehicle.color ?: "",
                        vehicleType      = vehicle.type,
                        energyType       = vehicle.energyType,
                        fuelType         = vehicle.fuelType ?: FuelType.Flex,
                        odometer         = vehicle.odometerKm.toString(),
                        tankCapacity     = vehicle.tankCapacityL?.toFormString() ?: "",
                        batteryCapacity  = vehicle.batteryCapacityKwh?.toFormString() ?: "",
                        photoUrl         = vehicle.photoUrl,
                    )
```

- [ ] **Step 6: Adicionar `onPhotoPicked` e `clearPhotoUploadError`**

Em `EditVehicleViewModel.kt`, adicionar logo após `clearError()`:

```kotlin

    /**
     * Envia a foto imediatamente ao ser escolhida — independente do botão
     * "Salvar" e do isDirty/diálogo de descarte dos demais campos, mesmo
     * padrão de [ProfileViewModel.onPickImage].
     */
    fun onPhotoPicked(uri: Uri) {
        _state.update { it.copy(isUploadingPhoto = true, photoUploadError = null) }
        viewModelScope.launch {
            when (val result = uploadVehiclePhoto(vehicleId, uri)) {
                is AppResult.Success -> _state.update {
                    it.copy(isUploadingPhoto = false, photoUrl = result.value)
                }
                is AppResult.Failure -> {
                    Timber.e("EditVehicle › erro ao enviar foto: ${result.error}")
                    _state.update { it.copy(isUploadingPhoto = false, photoUploadError = result.error) }
                }
            }
        }
    }

    fun clearPhotoUploadError() = _state.update { it.copy(photoUploadError = null) }
```

- [ ] **Step 7: Rodar os testes para confirmar que passam**

Run: `./gradlew :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.edit.EditVehicleViewModelTest"`
Expected: PASS — todos os testes verdes

- [ ] **Step 8: Rodar a suíte completa de testes unitários para checar regressão**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, nenhum teste pré-existente quebrado

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/edit/EditVehicleUiState.kt app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/edit/EditVehicleViewModel.kt app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/edit/EditVehicleViewModelTest.kt
git commit -m "feat(vehicle): adiciona upload de foto ao EditVehicleViewModel"
```

---

### Task 6: `EditVehicleScreen` — UI de troca de foto

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/edit/EditVehicleScreen.kt`

**Interfaces:**
- Consumes: `EditVehicleUiState.photoUrl`, `.isUploadingPhoto`, `.photoUploadError` (Task 5), `EditVehicleViewModel.onPhotoPicked(uri: Uri)`, `.clearPhotoUploadError()` (Task 5), `VehiclePhotoAvatar` (Task 3). Strings já existentes `R.string.vehicle_photo_change` ("Trocar foto") e `R.string.vehicle_photo_upload_error` ("Não foi possível enviar a foto. Tente novamente.") — nenhuma string nova é necessária.
- Produces: nada consumido por outras tasks — é a ponta final da UI.

Sem teste automatizado (a tela já não tinha testes de UI/Compose antes desta mudança); a verificação é manual, coberta na Task 7.

- [ ] **Step 1: Adicionar os imports necessários**

No topo de `EditVehicleScreen.kt`, adicionar aos imports existentes:

```kotlin
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.flowfuel.app.core.designsystem.components.VehiclePhotoAvatar
```

- [ ] **Step 2: Mostrar snackbar de erro de upload de foto**

Em `EditVehicleScreen.kt`, logo após o bloco existente:

```kotlin
    // Erro de formulário (rede/servidor)
    val formErrorMsg = state.formError?.userMessage()
    LaunchedEffect(formErrorMsg) {
        if (formErrorMsg != null) {
            snackbarHostState.showSnackbar(FFSnackbarVisuals(formErrorMsg, FFSnackbarKind.Error))
            viewModel.clearError()
        }
    }
```

adicionar:

```kotlin

    // Erro de upload de foto (independente do formError)
    val photoErrorMsg = state.photoUploadError?.userMessage()
    LaunchedEffect(photoErrorMsg) {
        if (photoErrorMsg != null) {
            snackbarHostState.showSnackbar(FFSnackbarVisuals(photoErrorMsg, FFSnackbarKind.Error))
            viewModel.clearPhotoUploadError()
        }
    }
```

- [ ] **Step 3: Inserir a seção de foto no topo do formulário**

Em `EditVehicleScreen.kt`, dentro do branch `EditVehicleScreenState.Editing`, o início do `Column` está assim:

```kotlin
                EditVehicleScreenState.Editing -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = FFTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xl),
                ) {
                    Spacer(Modifier.height(FFTheme.spacing.xs))

                    // ── Seção 1: Informações principais ──────────────────────
                    EditFormSection(title = stringResource(R.string.vehicle_section_info)) {
```

Alterar para:

```kotlin
                EditVehicleScreenState.Editing -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = FFTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xl),
                ) {
                    Spacer(Modifier.height(FFTheme.spacing.xs))

                    // ── Foto do veículo ───────────────────────────────────────
                    EditVehiclePhotoSection(
                        photoUrl = state.photoUrl,
                        vehicleType = state.vehicleType,
                        isUploading = state.isUploadingPhoto,
                        onPhotoPicked = viewModel::onPhotoPicked,
                    )

                    EditSectionDivider()

                    // ── Seção 1: Informações principais ──────────────────────
                    EditFormSection(title = stringResource(R.string.vehicle_section_info)) {
```

- [ ] **Step 4: Adicionar o composable `EditVehiclePhotoSection`**

Logo após a função `EditSectionDivider` (privada, próxima ao topo dos composables auxiliares):

```kotlin
@Composable
private fun EditVehiclePhotoSection(
    photoUrl: String?,
    vehicleType: VehicleType,
    isUploading: Boolean,
    onPhotoPicked: (Uri) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onPhotoPicked) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.size(96.dp),
            contentAlignment = Alignment.Center,
        ) {
            VehiclePhotoAvatar(
                photoUrl = photoUrl,
                vehicleType = vehicleType,
                size = 96.dp,
                onClick = if (!isUploading) {
                    {
                        launcher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                } else null,
            )

            if (isUploading) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.White,
                        strokeWidth = 3.dp,
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.vehicle_photo_change),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
```

- [ ] **Step 5: Compilar**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Rodar a suíte completa de testes unitários**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, todos os testes passando

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/edit/EditVehicleScreen.kt
git commit -m "feat(vehicle): adiciona secao de troca de foto a EditVehicleScreen"
```

---

### Task 7: Verificação manual end-to-end contra o backend real

**Files:** nenhum arquivo novo — só verificação (e, se necessário, um commit de correção pontual caso o formato real da URL não bata com a Task 1).

- [ ] **Step 1: Rodar o app no emulador**

Usar a skill `run-android-emulator` (ou `./gradlew :app:installDebug` + abrir manualmente).

- [ ] **Step 2: Confirmar exibição da foto em `VehicleDetailsScreen`**

Abrir um veículo que já teve foto enviada (fluxo de criação já implementado e testado em produção). Em Perfil → Meus veículos → tocar no veículo → confirmar que a foto aparece no avatar de 64dp no topo dos detalhes (não só o ícone de carro/moto).

Se a foto **não** aparecer (ícone de fallback aparece mesmo tendo foto), inspecionar via logcat/Charles/proxy o valor real de `photo` no JSON de resposta de `GET /vehicles/{id}` e comparar com a lógica de `photoUrl` da Task 1 (`BuildConfig.API_BASE_URL.trimEnd('/') + photo`). Ajustar a lógica de concatenação em `VehicleRepositoryImpl.toDomain()` se o formato real for diferente do assumido (mesmo tipo de ajuste feito no commit `d3872c8` para o `POST`), e commitar a correção separadamente.

- [ ] **Step 3: Confirmar troca de foto em `EditVehicleScreen`**

Tocar no FAB de editar → tela de edição deve mostrar a foto atual no topo com o texto "Trocar foto" abaixo. Tocar no avatar abre o seletor de imagens sem nenhum prompt de permissão. Escolher uma nova imagem: o avatar mostra um spinner sobre um overlay escuro durante o envio, depois atualiza para a nova foto — sem esperar o botão "Salvar".

- [ ] **Step 4: Confirmar que a troca reflete em `VehicleDetailsScreen`**

Voltar para `VehicleDetailsScreen` (sem alterar mais nada) e confirmar que a nova foto aparece (a tela recarrega via o mecanismo já existente de `savedStateHandle["vehicle_updated"]`/`refresh()`, ou reabrindo a tela).

- [ ] **Step 5: Confirmar isolamento do fluxo de foto em relação ao formulário**

Trocar a foto e, **sem tocar em nenhum outro campo**, apertar voltar: não deve aparecer o diálogo de descarte (a troca de foto já foi salva no servidor, não é um campo pendente do formulário).

- [ ] **Step 6: Registrar o resultado**

Nenhum commit necessário neste task, a menos que o Step 2 exija correção da lógica de URL (ver acima). Se algo dos Steps 3–5 falhar, voltar para a Task 5/6 e corrigir antes de finalizar.

---

## Self-Review

**Cobertura da spec:** as 3 metas do design (exibir em `VehicleDetailsScreen`, trocar em `EditVehicleScreen`, cliente de `DELETE` disponível sem uso de UI) têm tasks correspondentes — Task 4, Tasks 5-6, e Task 2, respectivamente. A seção "Fora" do design (sem botão de remover, sem mudança em `VehiclesScreen`/`VehiclePickerScreen`, sem câmera, sem DELETE+POST) não tem tasks correspondentes, como esperado.

**Consistência de tipos:** `Vehicle.photoUrl: String?` (Task 1) é o campo consumido por `VehiclePhotoAvatar(photoUrl: String?, ...)` (Task 3), por `VehicleHeader` (Task 4) e por `EditVehicleUiState.photoUrl`/`EditVehicleViewModel.load()` (Task 5). `UploadVehiclePhotoUseCase.invoke(vehicleId: Int, uri: Uri): AppResult<String>` (já existente) é consumido com a mesma assinatura em `EditVehicleViewModel.onPhotoPicked` (Task 5) e mockado com a mesma assinatura em `EditVehicleViewModelTest` (Task 5). `DeleteVehiclePhotoUseCase.invoke(vehicleId: Int): AppResult<Unit>` (Task 2) espelha exatamente `VehicleRepository.deletePhoto(vehicleId: Int): AppResult<Unit>` da mesma task.
