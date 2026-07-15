# Compartilhamento de Veículo (Android) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cliente Android pro compartilhamento de veículo: dono convida por email e gerencia o compartilhamento a partir de `VehicleDetailsScreen`; convidado aceita/recusa (via push ou pelo Perfil), vê o veículo emprestado na lista normal com tag "Emprestado", e usa uma Home mínima (atualizar odômetro, registrar evento de categoria permitida) enquanto ele está ativo.

**Architecture:** Novo pacote `core/vehicleshare` (API/DTO/domínio/repositório/use cases, mesmo padrão isolado de `core/notification`). Estado "veículo ativo é emprestado" vive só localmente (`SessionStore`), já que o backend não tem esse conceito pro convidado. Telas novas seguem o padrão MVVM já estabelecido (`StateFlow` + `Channel` de efeitos).

**Tech Stack:** Kotlin, Jetpack Compose, Hilt DI, Retrofit + Kotlinx Serialization, Coroutines, DataStore, MockK + Robolectric + `kotlinx-coroutines-test` para testes de ViewModel.

## Global Constraints

- Convite só por email (backend não tem `username`).
- Categorias de evento permitidas pro convidado: `EventCategory.FUEL`, `EventCategory.WASH` (apiValue `CAR_WASH`), `EventCategory.TIRES`, `EventCategory.OTHER`. As demais o backend rejeita com 403.
- Convidado não pode `GET /vehicles/{id}` nem listar histórico — nenhuma tela de convidado pode depender desses dados; só do que já vem em `VehicleShareResponseDto` (`vehicleId`, `vehicleBrand`, `vehicleModel`, `expiresAt` etc.).
- `PUT /vehicles/{id}/active` é exclusivo do dono — selecionar um veículo emprestado nunca chama esse endpoint, só grava local.
- Aceite/recusa/revogação não disparam push — a tela de convite deve ter um caminho de descoberta sem depender de push (linha no Perfil).
- Rota do convite (`vehicle-share/{shareId}`) precisa bater exatamente com o deep link que o backend manda no payload (`flowfuel://vehicle-share/{shareId}`) pro mecanismo genérico de deep link do `FlowFuelNavHost` funcionar sem mudança nele.

---

### Task 1: Camada de dados — `VehicleShareApi`, domínio, repositório, use cases

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/core/vehicleshare/data/remote/VehicleShareApi.kt`
- Create: `app/src/main/java/com/flowfuel/app/core/vehicleshare/domain/model/VehicleShare.kt`
- Create: `app/src/main/java/com/flowfuel/app/core/vehicleshare/domain/VehicleShareRepository.kt`
- Create: `app/src/main/java/com/flowfuel/app/core/vehicleshare/data/VehicleShareRepositoryImpl.kt`
- Create: `app/src/main/java/com/flowfuel/app/core/vehicleshare/di/VehicleShareModule.kt`
- Create: `app/src/main/java/com/flowfuel/app/core/vehicleshare/domain/usecase/InviteVehicleShareUseCase.kt`
- Create: `app/src/main/java/com/flowfuel/app/core/vehicleshare/domain/usecase/AcceptVehicleShareUseCase.kt`
- Create: `app/src/main/java/com/flowfuel/app/core/vehicleshare/domain/usecase/RejectVehicleShareUseCase.kt`
- Create: `app/src/main/java/com/flowfuel/app/core/vehicleshare/domain/usecase/RevokeVehicleShareUseCase.kt`
- Create: `app/src/main/java/com/flowfuel/app/core/vehicleshare/domain/usecase/GetVehicleShareForVehicleUseCase.kt`
- Create: `app/src/main/java/com/flowfuel/app/core/vehicleshare/domain/usecase/GetPendingVehicleSharesUseCase.kt`
- Create: `app/src/main/java/com/flowfuel/app/core/vehicleshare/domain/usecase/GetActiveSharedVehiclesUseCase.kt`
- Test: `app/src/test/java/com/flowfuel/app/core/vehicleshare/data/VehicleShareRepositoryImplTest.kt`

**Interfaces:**
- Produces: `VehicleShare` (`id: Int`, `vehicleId: Int`, `vehicleBrand: String`, `vehicleModel: String`, `ownerId: Int`, `ownerName: String`, `guestId: Int`, `guestName: String`, `status: VehicleShareStatus`, `createdAt: String?`, `respondedAt: String?`, `expiresAt: String?`); `VehicleShareStatus` (enum `PENDING, ACTIVE, REJECTED, REVOKED, EXPIRED`); `VehicleShareRepository` com `invite(vehicleId: Int, inviteeEmail: String, durationDays: Int): AppResult<VehicleShare>`, `accept(shareId: Int): AppResult<VehicleShare>`, `reject(shareId: Int): AppResult<VehicleShare>`, `revoke(shareId: Int): AppResult<Unit>`, `getForVehicle(vehicleId: Int): AppResult<VehicleShare?>`, `getPending(): AppResult<List<VehicleShare>>`, `getActiveForMe(): AppResult<List<VehicleShare>>`. Sete use cases correspondentes (um `operator fun invoke` cada, mesmo padrão de `SetActiveVehicleUseCase`).

- [ ] **Step 1: Escrever o teste do repositório (vai falhar a compilar — nada disso existe ainda)**

```kotlin
package com.flowfuel.app.core.vehicleshare.data

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.data.remote.VehicleShareApi
import com.flowfuel.app.core.vehicleshare.data.remote.VehicleShareRequestDto
import com.flowfuel.app.core.vehicleshare.data.remote.VehicleShareResponseDto
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShareStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleShareRepositoryImplTest {

    private val api: VehicleShareApi = mockk()
    private val repository = VehicleShareRepositoryImpl(api)

    private fun dto(status: String = "PENDING") = VehicleShareResponseDto(
        id = 100,
        vehicleId = 10,
        vehicleBrand = "Toyota",
        vehicleModel = "Corolla",
        ownerId = 1,
        ownerName = "Dono",
        guestId = 2,
        guestName = "Convidado",
        status = status,
        createdAt = "2026-07-15T10:00:00",
        respondedAt = null,
        expiresAt = null,
    )

    @Test
    fun invite_sucesso_mapeiaParaDominio() = runTest {
        coEvery { api.createShare(VehicleShareRequestDto(10, "guest@test.com", 3)) } returns dto()

        val result = repository.invite(10, "guest@test.com", 3)

        assertTrue(result is AppResult.Success)
        val share = (result as AppResult.Success).value
        assertEquals(100, share.id)
        assertEquals(VehicleShareStatus.PENDING, share.status)
    }

    @Test
    fun accept_sucesso_mapeiaStatusAtivo() = runTest {
        coEvery { api.acceptShare(100) } returns dto(status = "ACTIVE")

        val result = repository.accept(100)

        assertTrue(result is AppResult.Success)
        assertEquals(VehicleShareStatus.ACTIVE, (result as AppResult.Success).value.status)
    }

    @Test
    fun getForVehicle_semShareAtivo_retornaNull() = runTest {
        coEvery { api.getShareForVehicle(10) } returns null

        val result = repository.getForVehicle(10)

        assertTrue(result is AppResult.Success)
        assertNull((result as AppResult.Success).value)
    }

    @Test
    fun getActiveForMe_listaComShares_mapeiaTodos() = runTest {
        coEvery { api.getActiveForMe() } returns listOf(dto(status = "ACTIVE"))

        val result = repository.getActiveForMe()

        assertTrue(result is AppResult.Success)
        assertEquals(1, (result as AppResult.Success).value.size)
    }
}
```

- [ ] **Step 2: Rodar o teste pra confirmar que falha (nada compila ainda)**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.core.vehicleshare.data.VehicleShareRepositoryImplTest"`
Expected: FAIL (erro de compilação)

- [ ] **Step 3: Criar `VehicleShareApi` com DTOs**

```kotlin
package com.flowfuel.app.core.vehicleshare.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class VehicleShareRequestDto(
    val vehicleId: Int,
    val inviteeEmail: String,
    val durationDays: Int,
)

@Serializable
data class VehicleShareResponseDto(
    val id: Int,
    val vehicleId: Int,
    val vehicleBrand: String?,
    val vehicleModel: String?,
    val ownerId: Int,
    val ownerName: String?,
    val guestId: Int,
    val guestName: String?,
    val status: String,
    val createdAt: String?,
    val respondedAt: String?,
    val expiresAt: String?,
)

interface VehicleShareApi {
    @POST("vehicle-shares")
    suspend fun createShare(@Body body: VehicleShareRequestDto): VehicleShareResponseDto

    @POST("vehicle-shares/{id}/accept")
    suspend fun acceptShare(@Path("id") id: Int): VehicleShareResponseDto

    @POST("vehicle-shares/{id}/reject")
    suspend fun rejectShare(@Path("id") id: Int): VehicleShareResponseDto

    @DELETE("vehicle-shares/{id}")
    suspend fun revokeShare(@Path("id") id: Int)

    @GET("vehicle-shares/vehicle/{vehicleId}")
    suspend fun getShareForVehicle(@Path("vehicleId") vehicleId: Int): VehicleShareResponseDto?

    @GET("vehicle-shares/pending")
    suspend fun getPending(): List<VehicleShareResponseDto>

    @GET("vehicle-shares/active-for-me")
    suspend fun getActiveForMe(): List<VehicleShareResponseDto>
}
```

- [ ] **Step 4: Criar o modelo de domínio**

```kotlin
package com.flowfuel.app.core.vehicleshare.domain.model

enum class VehicleShareStatus {
    PENDING, ACTIVE, REJECTED, REVOKED, EXPIRED;

    companion object {
        fun fromApi(raw: String): VehicleShareStatus =
            entries.firstOrNull { it.name == raw } ?: EXPIRED
    }
}

data class VehicleShare(
    val id: Int,
    val vehicleId: Int,
    val vehicleBrand: String,
    val vehicleModel: String,
    val ownerId: Int,
    val ownerName: String,
    val guestId: Int,
    val guestName: String,
    val status: VehicleShareStatus,
    val createdAt: String?,
    val respondedAt: String?,
    val expiresAt: String?,
)
```

- [ ] **Step 5: Criar a interface do repositório**

```kotlin
package com.flowfuel.app.core.vehicleshare.domain

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare

interface VehicleShareRepository {
    suspend fun invite(vehicleId: Int, inviteeEmail: String, durationDays: Int): AppResult<VehicleShare>
    suspend fun accept(shareId: Int): AppResult<VehicleShare>
    suspend fun reject(shareId: Int): AppResult<VehicleShare>
    suspend fun revoke(shareId: Int): AppResult<Unit>
    suspend fun getForVehicle(vehicleId: Int): AppResult<VehicleShare?>
    suspend fun getPending(): AppResult<List<VehicleShare>>
    suspend fun getActiveForMe(): AppResult<List<VehicleShare>>
}
```

- [ ] **Step 6: Criar a implementação**

```kotlin
package com.flowfuel.app.core.vehicleshare.data

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.network.apiCall
import com.flowfuel.app.core.vehicleshare.data.remote.VehicleShareApi
import com.flowfuel.app.core.vehicleshare.data.remote.VehicleShareRequestDto
import com.flowfuel.app.core.vehicleshare.data.remote.VehicleShareResponseDto
import com.flowfuel.app.core.vehicleshare.domain.VehicleShareRepository
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShareStatus
import javax.inject.Inject
import javax.inject.Singleton

private fun VehicleShareResponseDto.toDomain() = VehicleShare(
    id = id,
    vehicleId = vehicleId,
    vehicleBrand = vehicleBrand.orEmpty(),
    vehicleModel = vehicleModel.orEmpty(),
    ownerId = ownerId,
    ownerName = ownerName.orEmpty(),
    guestId = guestId,
    guestName = guestName.orEmpty(),
    status = VehicleShareStatus.fromApi(status),
    createdAt = createdAt,
    respondedAt = respondedAt,
    expiresAt = expiresAt,
)

@Singleton
class VehicleShareRepositoryImpl @Inject constructor(
    private val api: VehicleShareApi,
) : VehicleShareRepository {

    override suspend fun invite(vehicleId: Int, inviteeEmail: String, durationDays: Int): AppResult<VehicleShare> =
        apiCall { api.createShare(VehicleShareRequestDto(vehicleId, inviteeEmail, durationDays)).toDomain() }

    override suspend fun accept(shareId: Int): AppResult<VehicleShare> =
        apiCall { api.acceptShare(shareId).toDomain() }

    override suspend fun reject(shareId: Int): AppResult<VehicleShare> =
        apiCall { api.rejectShare(shareId).toDomain() }

    override suspend fun revoke(shareId: Int): AppResult<Unit> =
        apiCall { api.revokeShare(shareId) }

    override suspend fun getForVehicle(vehicleId: Int): AppResult<VehicleShare?> =
        apiCall { api.getShareForVehicle(vehicleId)?.toDomain() }

    override suspend fun getPending(): AppResult<List<VehicleShare>> =
        apiCall { api.getPending().map { it.toDomain() } }

    override suspend fun getActiveForMe(): AppResult<List<VehicleShare>> =
        apiCall { api.getActiveForMe().map { it.toDomain() } }
}
```

- [ ] **Step 7: Criar o módulo de DI**

```kotlin
package com.flowfuel.app.core.vehicleshare.di

import com.flowfuel.app.core.vehicleshare.data.VehicleShareRepositoryImpl
import com.flowfuel.app.core.vehicleshare.data.remote.VehicleShareApi
import com.flowfuel.app.core.vehicleshare.domain.VehicleShareRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VehicleShareBindModule {
    @Binds @Singleton
    abstract fun bindVehicleShareRepository(impl: VehicleShareRepositoryImpl): VehicleShareRepository
}

@Module
@InstallIn(SingletonComponent::class)
object VehicleShareApiModule {
    @Provides @Singleton
    fun provideVehicleShareApi(retrofit: Retrofit): VehicleShareApi = retrofit.create(VehicleShareApi::class.java)
}
```

- [ ] **Step 8: Criar os 7 use cases**

```kotlin
package com.flowfuel.app.core.vehicleshare.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.VehicleShareRepository
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import javax.inject.Inject

class InviteVehicleShareUseCase @Inject constructor(
    private val repository: VehicleShareRepository,
) {
    suspend operator fun invoke(vehicleId: Int, inviteeEmail: String, durationDays: Int): AppResult<VehicleShare> =
        repository.invite(vehicleId, inviteeEmail, durationDays)
}
```

```kotlin
package com.flowfuel.app.core.vehicleshare.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.VehicleShareRepository
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import javax.inject.Inject

class AcceptVehicleShareUseCase @Inject constructor(
    private val repository: VehicleShareRepository,
) {
    suspend operator fun invoke(shareId: Int): AppResult<VehicleShare> = repository.accept(shareId)
}
```

```kotlin
package com.flowfuel.app.core.vehicleshare.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.VehicleShareRepository
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import javax.inject.Inject

class RejectVehicleShareUseCase @Inject constructor(
    private val repository: VehicleShareRepository,
) {
    suspend operator fun invoke(shareId: Int): AppResult<VehicleShare> = repository.reject(shareId)
}
```

```kotlin
package com.flowfuel.app.core.vehicleshare.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.VehicleShareRepository
import javax.inject.Inject

class RevokeVehicleShareUseCase @Inject constructor(
    private val repository: VehicleShareRepository,
) {
    suspend operator fun invoke(shareId: Int): AppResult<Unit> = repository.revoke(shareId)
}
```

```kotlin
package com.flowfuel.app.core.vehicleshare.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.VehicleShareRepository
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import javax.inject.Inject

class GetVehicleShareForVehicleUseCase @Inject constructor(
    private val repository: VehicleShareRepository,
) {
    suspend operator fun invoke(vehicleId: Int): AppResult<VehicleShare?> = repository.getForVehicle(vehicleId)
}
```

```kotlin
package com.flowfuel.app.core.vehicleshare.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.VehicleShareRepository
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import javax.inject.Inject

class GetPendingVehicleSharesUseCase @Inject constructor(
    private val repository: VehicleShareRepository,
) {
    suspend operator fun invoke(): AppResult<List<VehicleShare>> = repository.getPending()
}
```

```kotlin
package com.flowfuel.app.core.vehicleshare.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.VehicleShareRepository
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import javax.inject.Inject

class GetActiveSharedVehiclesUseCase @Inject constructor(
    private val repository: VehicleShareRepository,
) {
    suspend operator fun invoke(): AppResult<List<VehicleShare>> = repository.getActiveForMe()
}
```

- [ ] **Step 9: Rodar o teste pra confirmar que passa**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.core.vehicleshare.data.VehicleShareRepositoryImplTest"`
Expected: PASS (4 testes)

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/vehicleshare/ app/src/test/java/com/flowfuel/app/core/vehicleshare/
git commit -m "feat(vehicleshare): add data layer, domain model and use cases"
```

---

### Task 2: `SessionStore` — estado local de "veículo ativo emprestado"

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/core/datastore/SessionStore.kt`
- Test: `app/src/test/java/com/flowfuel/app/core/datastore/SessionStoreTest.kt` (criar se não existir; se existir, adicionar os testes abaixo)

**Interfaces:**
- Produces: `SessionStore.activeVehicleIsGuestFlow: Flow<Boolean>`; `saveActiveVehicleId(id: Int, isGuest: Boolean = false)` (assinatura estendida, compatível com todas as chamadas existentes que só passam `id`); `clearActiveVehicleId()` também limpa a flag.

- [ ] **Step 1: Escrever os testes (vão falhar — a API nova não existe)**

```kotlin
package com.flowfuel.app.core.datastore

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionStoreGuestVehicleTest {

    private val sessionStore = SessionStore(ApplicationProvider.getApplicationContext())

    @Test
    fun saveActiveVehicleId_semIsGuest_flagFicaFalse() = runTest {
        sessionStore.saveActiveVehicleId(10)

        assertEquals(10, sessionStore.activeVehicleIdFlow.first())
        assertFalse(sessionStore.activeVehicleIsGuestFlow.first())
    }

    @Test
    fun saveActiveVehicleId_comIsGuestTrue_flagFicaTrue() = runTest {
        sessionStore.saveActiveVehicleId(20, isGuest = true)

        assertEquals(20, sessionStore.activeVehicleIdFlow.first())
        assertTrue(sessionStore.activeVehicleIsGuestFlow.first())
    }

    @Test
    fun saveActiveVehicleId_trocaDeGuestParaProprio_zeraFlag() = runTest {
        sessionStore.saveActiveVehicleId(20, isGuest = true)
        sessionStore.saveActiveVehicleId(30, isGuest = false)

        assertFalse(sessionStore.activeVehicleIsGuestFlow.first())
    }

    @Test
    fun clearActiveVehicleId_limpaIdEFlag() = runTest {
        sessionStore.saveActiveVehicleId(20, isGuest = true)

        sessionStore.clearActiveVehicleId()

        assertEquals(null, sessionStore.activeVehicleIdFlow.first())
        assertFalse(sessionStore.activeVehicleIsGuestFlow.first())
    }
}
```

- [ ] **Step 2: Rodar o teste pra confirmar que falha**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.core.datastore.SessionStoreGuestVehicleTest"`
Expected: FAIL (erro de compilação — `isGuest`/`activeVehicleIsGuestFlow` não existem)

- [ ] **Step 3: Adicionar a chave e os métodos ao `SessionStore`**

Adicionar em `private object Keys`:
```kotlin
        val ACTIVE_VEHICLE_IS_GUEST = booleanPreferencesKey("active_vehicle_is_guest")
```

Substituir a seção "─── Veículo ativo ───" inteira por:
```kotlin
    // ─── Veículo ativo ────────────────────────────────────────────────────────

    /** ID do último veículo selecionado pelo usuário, ou null se ainda não houve seleção. */
    val activeVehicleIdFlow: Flow<Int?> = context.dataStore.data.map { it[Keys.ACTIVE_VEHICLE] }

    /** True quando o veículo ativo é emprestado (o usuário é convidado, não dono). */
    val activeVehicleIsGuestFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ACTIVE_VEHICLE_IS_GUEST] ?: false }

    suspend fun saveActiveVehicleId(id: Int, isGuest: Boolean = false) {
        context.dataStore.edit {
            it[Keys.ACTIVE_VEHICLE] = id
            it[Keys.ACTIVE_VEHICLE_IS_GUEST] = isGuest
        }
    }

    suspend fun clearActiveVehicleId() {
        context.dataStore.edit {
            it.remove(Keys.ACTIVE_VEHICLE)
            it.remove(Keys.ACTIVE_VEHICLE_IS_GUEST)
        }
    }
```

Também adicionar `it.remove(Keys.ACTIVE_VEHICLE_IS_GUEST)` dentro do `clear()` existente, junto de `it.remove(Keys.ACTIVE_VEHICLE)`.

- [ ] **Step 4: Rodar o teste pra confirmar que passa**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.core.datastore.SessionStoreGuestVehicleTest"`
Expected: PASS (4 testes)

- [ ] **Step 5: Rodar a suíte completa do módulo `datastore`/consumidores diretos pra garantir que a assinatura estendida não quebrou nada**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.core.datastore.*" --tests "com.flowfuel.app.feature.vehicle.*"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/datastore/SessionStore.kt app/src/test/java/com/flowfuel/app/core/datastore/SessionStoreGuestVehicleTest.kt
git commit -m "feat(vehicleshare): track active-vehicle-is-guest flag in SessionStore"
```

---

### Task 3: `VehiclePickerScreen` — veículo emprestado na lista

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicle/domain/usecase/SetActiveGuestVehicleUseCase.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/list/VehiclePickerViewModel.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/list/VehiclePickerScreen.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/list/VehiclePickerViewModelTest.kt` (criar se não existir; se existir, adicionar os testes abaixo)

**Interfaces:**
- Consumes: `GetActiveSharedVehiclesUseCase` (Task 1), `VehicleShare`/`VehicleShareStatus` (Task 1), `SessionStore.saveActiveVehicleId(id, isGuest)` (Task 2).
- Produces: `VehiclePickerItem` (sealed interface `Owned(vehicle: Vehicle)` / `Borrowed(share: VehicleShare)`); `VehiclePickerUiState.Success(items: List<VehiclePickerItem>, activeVehicleId: Int?)`; `SetActiveGuestVehicleUseCase.invoke(vehicleId: Int)`.

- [ ] **Step 1: Escrever os testes (vão falhar — `VehiclePickerItem` e o use case não existem)**

```kotlin
    @Test
    fun load_comVeiculoEmprestadoAtivo_incluiItemBorrowed() = runTest {
        coEvery { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        coEvery { getVehiclesPage(0) } returns AppResult.Success(
            PagedResult(items = listOf(fixtureVehicle), hasMore = false),
        )
        val share = VehicleShare(
            id = 100, vehicleId = 99, vehicleBrand = "Fiat", vehicleModel = "Uno",
            ownerId = 5, ownerName = "Outro Dono", guestId = 1, guestName = "Eu",
            status = VehicleShareStatus.ACTIVE, createdAt = null, respondedAt = null,
            expiresAt = "2026-07-20T00:00:00",
        )
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(listOf(share))

        val viewModel = createViewModel()

        val state = viewModel.state.value as VehiclePickerUiState.Success
        assertEquals(2, state.items.size)
        assertTrue(state.items.any { it is VehiclePickerItem.Borrowed && (it as VehiclePickerItem.Borrowed).share.id == 100 })
    }

    @Test
    fun onVehicleSelected_itemBorrowed_naoChamaSetActiveVehicleEUsaSetActiveGuestVehicle() = runTest {
        coEvery { sessionStore.activeVehicleIdFlow } returns flowOf(null)
        coEvery { getVehiclesPage(0) } returns AppResult.Success(PagedResult(items = emptyList(), hasMore = false))
        val share = VehicleShare(
            id = 100, vehicleId = 99, vehicleBrand = "Fiat", vehicleModel = "Uno",
            ownerId = 5, ownerName = "Outro Dono", guestId = 1, guestName = "Eu",
            status = VehicleShareStatus.ACTIVE, createdAt = null, respondedAt = null,
            expiresAt = "2026-07-20T00:00:00",
        )
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(listOf(share))
        coEvery { setActiveGuestVehicle(99) } returns Unit

        val viewModel = createViewModel()
        viewModel.onItemSelected(VehiclePickerItem.Borrowed(share))

        coVerify { setActiveGuestVehicle(99) }
        coVerify(exactly = 0) { setActiveVehicle(any()) }
    }
```

(Esses dois testes assumem um `createViewModel()` / mocks / `fixtureVehicle` já existentes no arquivo de teste, seguindo o padrão de `ProfileViewModelTest` — MockK + `@Config(sdk = [33])` + `RobolectricTestRunner`. Se o arquivo ainda não existe, criar com esse cabeçalho e os mocks de `GetVehiclesPageUseCase`, `SetActiveVehicleUseCase`, `SetActiveGuestVehicleUseCase` (novo), `GetActiveSharedVehiclesUseCase` (novo) e `SessionStore`.)

- [ ] **Step 2: Rodar o teste pra confirmar que falha**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.list.VehiclePickerViewModelTest"`
Expected: FAIL (erro de compilação)

- [ ] **Step 3: Criar `SetActiveGuestVehicleUseCase`**

```kotlin
package com.flowfuel.app.feature.vehicle.domain.usecase

import com.flowfuel.app.core.datastore.SessionStore
import javax.inject.Inject

/**
 * Marca [vehicleId] (um veículo emprestado, não do usuário) como ativo — só
 * localmente. Diferente de [SetActiveVehicleUseCase], nunca chama
 * PUT /vehicles/{id}/active: esse endpoint é exclusivo do dono, o backend
 * escopa `isActive` via `findByUserId`, e o convidado não tem posse do veículo.
 */
class SetActiveGuestVehicleUseCase @Inject constructor(
    private val sessionStore: SessionStore,
) {
    suspend operator fun invoke(vehicleId: Int) {
        sessionStore.saveActiveVehicleId(vehicleId, isGuest = true)
    }
}
```

- [ ] **Step 4: Alterar `VehiclePickerViewModel`**

Adicionar ao topo do arquivo (fora da classe):
```kotlin
sealed interface VehiclePickerItem {
    data class Owned(val vehicle: Vehicle) : VehiclePickerItem
    data class Borrowed(val share: com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare) : VehiclePickerItem
}
```

Trocar `VehiclePickerUiState.Success` de `val vehicles: List<Vehicle>` para `val items: List<VehiclePickerItem>`.

No `ViewModel`, adicionar as duas novas dependências ao construtor:
```kotlin
    private val getActiveSharedVehicles: GetActiveSharedVehiclesUseCase,
    private val setActiveGuestVehicle: SetActiveGuestVehicleUseCase,
```

Em `load()`, depois que `getVehiclesPage(0)` retorna sucesso e antes de montar `_state.value`, buscar os compartilhados e combinar:
```kotlin
                is AppResult.Success -> {
                    val paged = result.value
                    val sharedResult = getActiveSharedVehicles()
                    val borrowedItems = (sharedResult as? AppResult.Success)?.value
                        ?.map { VehiclePickerItem.Borrowed(it) } ?: emptyList()
                    if (paged.items.isEmpty() && borrowedItems.isEmpty()) {
                        _effects.send(VehiclePickerEffect.NavigateToAddVehicle)
                    } else {
                        accumulatedVehicles = paged.items
                        val ownedItems = accumulatedVehicles.map { VehiclePickerItem.Owned(it) }
                        _state.value = VehiclePickerUiState.Success(ownedItems + borrowedItems, activeVehicleId)
                        _pagination.value = PaginationState(currentPage = 0, hasMore = paged.hasMore)
                    }
                }
```
(Falha em buscar compartilhados é tratada como lista vazia — não bloqueia a tela por um problema num recurso secundário.)

Em `loadNextPage()`, ajustar a reconstrução do estado pra combinar `Owned` + os `Borrowed` já carregados:
```kotlin
                    val current = _state.value
                    if (current is VehiclePickerUiState.Success) {
                        val borrowedItems = current.items.filterIsInstance<VehiclePickerItem.Borrowed>()
                        _state.value = current.copy(
                            items = accumulatedVehicles.map { VehiclePickerItem.Owned(it) } + borrowedItems,
                        )
                    }
```

Renomear `onVehicleSelected(vehicle: Vehicle)` para `onItemSelected(item: VehiclePickerItem)`, bifurcando:
```kotlin
    fun onItemSelected(item: VehiclePickerItem) {
        viewModelScope.launch {
            when (item) {
                is VehiclePickerItem.Owned -> {
                    setActiveVehicle(item.vehicle.id)
                    _effects.send(VehiclePickerEffect.NavigateToHome(item.vehicle))
                }
                is VehiclePickerItem.Borrowed -> {
                    setActiveGuestVehicle(item.share.vehicleId)
                    _effects.send(VehiclePickerEffect.NavigateToGuestVehicle(item.share))
                }
            }
        }
    }
```

Adicionar ao `sealed interface VehiclePickerEffect`:
```kotlin
    data class NavigateToGuestVehicle(val share: com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare) : VehiclePickerEffect
```

- [ ] **Step 5: Alterar `VehiclePickerScreen` pra renderizar `VehiclePickerItem`**

No composable que hoje itera `state.vehicles` renderizando cada `Vehicle`, trocar por `state.items`, com `when (item)`: `is VehiclePickerItem.Owned` renderiza o card atual (sem mudança de layout); `is VehiclePickerItem.Borrowed` renderiza um card mais simples — `Text(item.share.vehicleBrand + " " + item.share.vehicleModel)`, um `AssistChip`/badge de texto "Emprestado", e "até " + a data de `item.share.expiresAt` formatada com o mesmo padrão `LocalDate.parse(...).format(ptBrFormatter)` já usado em outras telas do módulo. O `onClick` do item chama `viewModel::onItemSelected(item)` em ambos os casos. Tratar o novo efeito `NavigateToGuestVehicle` no `collectLatest` de efeitos do composable, repassando pro callback de navegação que a Task 8 vai conectar (`onNavigateToGuestVehicle: (VehicleShare) -> Unit`, novo parâmetro do composable, default `{}` até o `NavHost` ser atualizado).

- [ ] **Step 6: Rodar o teste pra confirmar que passa**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.list.VehiclePickerViewModelTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/ app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/list/VehiclePickerViewModelTest.kt
git commit -m "feat(vehicleshare): show borrowed vehicles in VehiclePickerScreen"
```

---

### Task 4: Categorias restritas em `CreateVehicleEventScreen` (modo convidado)

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/components/EventCategorySelector.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/create/CreateVehicleEventScreen.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/create/CreateVehicleEventViewModel.kt`
- Modify: `app/src/main/java/com/flowfuel/app/navigation/Destinations.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/vehicleevent/presentation/create/CreateVehicleEventViewModelTest.kt` (criar se não existir; se existir, adicionar o teste abaixo)

**Interfaces:**
- Produces: `EventCategorySelector(categories: List<EventCategory> = EventCategory.entries, ...)` (novo parâmetro); `CreateVehicleEventUiState.availableCategories: List<EventCategory>`; `Destinations.vehicleEventCreate(vehicleId: Int, category: String? = null, guestMode: Boolean = false)`.

- [ ] **Step 1: Escrever o teste (vai falhar — `guestMode` não existe no `SavedStateHandle` esperado)**

```kotlin
    @Test
    fun init_guestModeTrue_availableCategoriesRestritoAsQuatroPermitidas() {
        val savedStateHandle = SavedStateHandle(mapOf("vehicleId" to 10, "guestMode" to "true"))
        val viewModel = CreateVehicleEventViewModel(savedStateHandle, createVehicleEvent, sessionStore)

        val expected = setOf(EventCategory.FUEL, EventCategory.WASH, EventCategory.TIRES, EventCategory.OTHER)
        assertEquals(expected, viewModel.state.value.availableCategories.toSet())
    }

    @Test
    fun init_guestModeAusente_availableCategoriesTemTodas() {
        val savedStateHandle = SavedStateHandle(mapOf("vehicleId" to 10))
        val viewModel = CreateVehicleEventViewModel(savedStateHandle, createVehicleEvent, sessionStore)

        assertEquals(EventCategory.entries.toSet(), viewModel.state.value.availableCategories.toSet())
    }
```

- [ ] **Step 2: Rodar o teste pra confirmar que falha**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicleevent.presentation.create.CreateVehicleEventViewModelTest"`
Expected: FAIL (erro de compilação — `availableCategories` não existe)

- [ ] **Step 3: Adicionar `guestMode` à rota**

Em `Destinations.kt`, trocar:
```kotlin
    const val VEHICLE_EVENT_CREATE  = "vehicle/events/create/{vehicleId}?category={category}"
```
por:
```kotlin
    const val VEHICLE_EVENT_CREATE  = "vehicle/events/create/{vehicleId}?category={category}&guestMode={guestMode}"
```
E a função:
```kotlin
    fun vehicleEventCreate(vehicleId: Int, category: String? = null, guestMode: Boolean = false): String {
        val base = "vehicle/events/create/$vehicleId"
        val withCategory = if (category == null) base else "$base?category=$category"
        val separator = if (category == null) "?" else "&"
        return if (!guestMode) withCategory else "$withCategory${separator}guestMode=true"
    }
```

- [ ] **Step 4: Filtrar categorias no `EventCategorySelector`**

```kotlin
@Composable
fun EventCategorySelector(
    selected: EventCategory,
    onSelect: (EventCategory) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    categories: List<EventCategory> = EventCategory.entries,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(categories) { category ->
```
(resto do corpo do `items {}` inalterado — só a fonte da lista muda de `EventCategory.entries` pro parâmetro novo)

- [ ] **Step 5: Adicionar `availableCategories` ao `CreateVehicleEventViewModel`**

Em `CreateVehicleEventUiState`, adicionar `val availableCategories: List<EventCategory> = EventCategory.entries`.

No `ViewModel`, antes de montar `_state`:
```kotlin
    private val guestMode: Boolean = savedStateHandle.get<String>("guestMode")?.toBoolean() ?: false
    private val availableCategories: List<EventCategory> =
        if (guestMode) listOf(EventCategory.FUEL, EventCategory.WASH, EventCategory.TIRES, EventCategory.OTHER)
        else EventCategory.entries

    private val _state = MutableStateFlow(
        CreateVehicleEventUiState(
            category = initialCategory,
            eventDate = LocalDate.now().toString(),
            availableCategories = availableCategories,
        ),
    )
```
(Nota: se `guestMode=true` e `initialCategory` vier de fora da lista restrita — não deve acontecer no fluxo normal, já que só `GuestVehicleScreen` vai navegar com `guestMode=true` e sem `category`, então `initialCategory` cai no default `OTHER`, que está na lista — mas por segurança, ajustar `initialCategory` pra `EventCategory.OTHER` se ela não estiver em `availableCategories`.)

No composable `CreateVehicleEventScreen`, passar `categories = state.availableCategories` pro `EventCategorySelector`.

- [ ] **Step 6: Rodar o teste pra confirmar que passa**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicleevent.presentation.create.CreateVehicleEventViewModelTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicleevent/ app/src/main/java/com/flowfuel/app/navigation/Destinations.kt app/src/test/java/com/flowfuel/app/feature/vehicleevent/presentation/create/CreateVehicleEventViewModelTest.kt
git commit -m "feat(vehicleshare): restrict event categories in guest mode"
```

---

### Task 5: `GuestVehicleScreen` — Home mínima do convidado

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/guest/GuestVehicleScreen.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/guest/GuestVehicleViewModel.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/guest/GuestVehicleViewModelTest.kt`

**Interfaces:**
- Consumes: `VehicleApi`/`VehicleRepository` (endpoint de odômetro já existe — reaproveitar via um novo use case fino, já que `UpdateOdometerUseCase` hoje espera o fluxo do dono; ver Step 3); `SessionStore.clearActiveVehicleId()` (Task 2).
- Produces: `GuestVehicleViewModel` com `state: StateFlow<GuestVehicleUiState>`, `onOdometerChange(String)`, `confirmOdometer()`, `onCreateEventClicked()`; `GuestVehicleEffect.NavigateToCreateEvent(vehicleId: Int)`, `GuestVehicleEffect.NavigateToPicker(message: String?)`.

- [ ] **Step 1: Escrever o teste (vai falhar — nada disso existe)**

```kotlin
package com.flowfuel.app.feature.vehicle.presentation.guest

import androidx.lifecycle.SavedStateHandle
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.usecase.UpdateOdometerUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GuestVehicleViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val updateOdometer: UpdateOdometerUseCase = mockk()
    private val sessionStore: SessionStore = mockk(relaxed = true)

    private fun createViewModel(vehicleId: Int = 99, brand: String = "Fiat", model: String = "Uno", expiresAt: String? = "2026-07-20T00:00:00") =
        GuestVehicleViewModel(
            SavedStateHandle(mapOf("vehicleId" to vehicleId, "vehicleBrand" to brand, "vehicleModel" to model, "expiresAt" to expiresAt)),
            updateOdometer,
            sessionStore,
        )

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun confirmOdometer_sucesso_mostraSnackbarDeSucesso() = runTest {
        coEvery { updateOdometer(99, 1050) } returns AppResult.Success(Unit)
        val viewModel = createViewModel()
        viewModel.onOdometerChange("1050")

        viewModel.confirmOdometer()

        coVerify { updateOdometer(99, 1050) }
    }

    @Test
    fun confirmOdometer_erro403_limpaSessaoConvidadoEEnviaEfeitoDeVoltarPraPicker() = runTest {
        coEvery { updateOdometer(99, 1050) } returns AppResult.Failure(AppError.Api("FORBIDDEN_OPERATION", "sem acesso"))
        val viewModel = createViewModel()
        viewModel.onOdometerChange("1050")

        viewModel.confirmOdometer()

        coVerify { sessionStore.clearActiveVehicleId() }
    }
}
```

- [ ] **Step 2: Rodar o teste pra confirmar que falha**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.guest.GuestVehicleViewModelTest"`
Expected: FAIL (erro de compilação)

- [ ] **Step 3: Criar `UpdateOdometerUseCase`** (fino, chama `PUT /vehicles/{id}/odometer` sem depender de `currentKm` conhecido — checar se já existe um use case equivalente usado por `UpdateOdometerViewModel`; se existir com essa assinatura `(vehicleId: Int, newKm: Int): AppResult<Unit>` ou similar, reaproveitar em vez de criar um novo. Se o existente carrega estado extra do dono, criar este como um use case fino separado)

```kotlin
package com.flowfuel.app.feature.vehicle.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import javax.inject.Inject

class UpdateOdometerUseCase @Inject constructor(
    private val repository: VehicleRepository,
) {
    suspend operator fun invoke(vehicleId: Int, newKm: Int): AppResult<Unit> =
        repository.updateOdometer(vehicleId, newKm)
}
```
(Se `VehicleRepository.updateOdometer` já existir com essa assinatura — reaproveitado por `UpdateOdometerViewModel` internamente —, pular a criação deste arquivo e usar o método do repositório/use case existente diretamente no `GuestVehicleViewModel`, ajustando o import no teste acima de acordo.)

- [ ] **Step 4: Criar `GuestVehicleViewModel`**

```kotlin
package com.flowfuel.app.feature.vehicle.presentation.guest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.usecase.UpdateOdometerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GuestVehicleUiState(
    val vehicleId: Int,
    val vehicleBrand: String,
    val vehicleModel: String,
    val expiresAt: String?,
    val odometerInput: String = "",
    val isSavingOdometer: Boolean = false,
    val odometerError: String? = null,
)

sealed interface GuestVehicleEffect {
    data object OdometerUpdated : GuestVehicleEffect
    data class NavigateToCreateEvent(val vehicleId: Int) : GuestVehicleEffect
    data class NavigateToPicker(val message: String?) : GuestVehicleEffect
}

@HiltViewModel
class GuestVehicleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val updateOdometer: UpdateOdometerUseCase,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        GuestVehicleUiState(
            vehicleId = checkNotNull(savedStateHandle["vehicleId"]),
            vehicleBrand = savedStateHandle["vehicleBrand"] ?: "",
            vehicleModel = savedStateHandle["vehicleModel"] ?: "",
            expiresAt = savedStateHandle["expiresAt"],
        ),
    )
    val state: StateFlow<GuestVehicleUiState> = _state.asStateFlow()

    private val _effects = Channel<GuestVehicleEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onOdometerChange(value: String) =
        _state.update { it.copy(odometerInput = value, odometerError = null) }

    fun confirmOdometer() {
        val km = _state.value.odometerInput.toIntOrNull()
        if (km == null || km <= 0) {
            _state.update { it.copy(odometerError = "Informe um valor válido") }
            return
        }
        _state.update { it.copy(isSavingOdometer = true) }
        viewModelScope.launch {
            when (val result = updateOdometer(_state.value.vehicleId, km)) {
                is AppResult.Success -> {
                    _state.update { it.copy(isSavingOdometer = false, odometerInput = "") }
                    _effects.send(GuestVehicleEffect.OdometerUpdated)
                }
                is AppResult.Failure -> handleFailure(result.error)
            }
        }
    }

    fun onCreateEventClicked() {
        viewModelScope.launch {
            _effects.send(GuestVehicleEffect.NavigateToCreateEvent(_state.value.vehicleId))
        }
    }

    private suspend fun handleFailure(error: AppError) {
        _state.update { it.copy(isSavingOdometer = false) }
        val isForbidden = (error as? AppError.Api)?.code == "FORBIDDEN_OPERATION"
        if (isForbidden) {
            sessionStore.clearActiveVehicleId()
            _effects.send(GuestVehicleEffect.NavigateToPicker("Esse veículo não está mais compartilhado com você"))
        } else {
            _state.update { it.copy(odometerError = error.message ?: "Erro ao atualizar odômetro") }
        }
    }
}
```

- [ ] **Step 5: Criar `GuestVehicleScreen`**

```kotlin
package com.flowfuel.app.feature.vehicle.presentation.guest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFNumberField
import com.flowfuel.app.core.designsystem.components.FFNumberKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.theme.FFTheme
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBrFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("pt", "BR"))

@Composable
fun GuestVehicleScreen(
    onNavigateToCreateEvent: (vehicleId: Int) -> Unit,
    onNavigateToPicker: (message: String?) -> Unit,
    onSwitchVehicleClicked: () -> Unit,
    viewModel: GuestVehicleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                GuestVehicleEffect.OdometerUpdated ->
                    snackbarHostState.showSnackbar(FFSnackbarVisuals("Odômetro atualizado", FFSnackbarKind.Success))
                is GuestVehicleEffect.NavigateToCreateEvent -> onNavigateToCreateEvent(effect.vehicleId)
                is GuestVehicleEffect.NavigateToPicker -> onNavigateToPicker(effect.message)
            }
        }
    }

    Scaffold(
        topBar = { FFTopBar(title = "${state.vehicleBrand} ${state.vehicleModel}") },
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = FFTheme.spacing.md, vertical = FFTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.lg),
        ) {
            val expiresLabel = state.expiresAt?.let {
                runCatching { LocalDate.parse(it.take(10)).format(ptBrFormatter) }.getOrNull()
            }
            Text(
                text = "Veículo emprestado" + (expiresLabel?.let { " até $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FFNumberField(
                value = state.odometerInput,
                onValueChange = viewModel::onOdometerChange,
                label = "Atualizar odômetro (km)",
                kind = FFNumberKind.WholeNumber,
                errorText = state.odometerError,
                enabled = !state.isSavingOdometer,
                modifier = Modifier.fillMaxWidth(),
            )
            FFButton(
                text = "Salvar odômetro",
                onClick = viewModel::confirmOdometer,
                enabled = !state.isSavingOdometer,
                loading = state.isSavingOdometer,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(FFTheme.spacing.md))

            FFButton(
                text = "Registrar abastecimento/despesa",
                onClick = viewModel::onCreateEventClicked,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(FFTheme.spacing.xl))

            FFButton(
                text = "Trocar de veículo",
                onClick = onSwitchVehicleClicked,
                variant = com.flowfuel.app.core.designsystem.components.FFButtonVariant.Text,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
```

- [ ] **Step 6: Rodar o teste pra confirmar que passa**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.guest.GuestVehicleViewModelTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/guest/ app/src/main/java/com/flowfuel/app/feature/vehicle/domain/usecase/UpdateOdometerUseCase.kt app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/guest/
git commit -m "feat(vehicleshare): add GuestVehicleScreen (minimal home for borrowed vehicle)"
```

---

### Task 6: `MainContainerScreen` — bottom nav e FAB em modo convidado

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/navigation/MainContainerViewModel.kt`
- Modify: `app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt`
- Test: `app/src/test/java/com/flowfuel/app/navigation/MainContainerViewModelTest.kt`

**Interfaces:**
- Consumes: `SessionStore.activeVehicleIsGuestFlow`, `activeVehicleIdFlow` (Task 2); `GetActiveSharedVehiclesUseCase` (Task 1, pra resolver marca/modelo/`expiresAt` do veículo ativo quando em modo convidado).
- Produces: `MainContainerViewModel.state: StateFlow<MainContainerUiState>` (`isGuestMode: Boolean`, `guestVehicle: VehicleShare?`).

- [ ] **Step 1: Escrever o teste (vai falhar — a classe não existe)**

```kotlin
package com.flowfuel.app.navigation

import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShareStatus
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetActiveSharedVehiclesUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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
class MainContainerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val sessionStore: SessionStore = mockk()
    private val getActiveSharedVehicles: GetActiveSharedVehiclesUseCase = mockk()

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun state_veiculoAtivoProprio_isGuestModeFalse() = runTest {
        every { sessionStore.activeVehicleIsGuestFlow } returns flowOf(false)
        every { sessionStore.activeVehicleIdFlow } returns flowOf(1)
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(emptyList())

        val viewModel = MainContainerViewModel(sessionStore, getActiveSharedVehicles)

        assertFalse(viewModel.state.value.isGuestMode)
    }

    @Test
    fun state_veiculoAtivoEmprestado_isGuestModeTrueEResolveShare() = runTest {
        val share = VehicleShare(
            id = 100, vehicleId = 99, vehicleBrand = "Fiat", vehicleModel = "Uno",
            ownerId = 5, ownerName = "Dono", guestId = 1, guestName = "Eu",
            status = VehicleShareStatus.ACTIVE, createdAt = null, respondedAt = null,
            expiresAt = "2026-07-20T00:00:00",
        )
        every { sessionStore.activeVehicleIsGuestFlow } returns flowOf(true)
        every { sessionStore.activeVehicleIdFlow } returns flowOf(99)
        coEvery { getActiveSharedVehicles() } returns AppResult.Success(listOf(share))

        val viewModel = MainContainerViewModel(sessionStore, getActiveSharedVehicles)

        assertTrue(viewModel.state.value.isGuestMode)
    }
}
```

- [ ] **Step 2: Rodar o teste pra confirmar que falha**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.navigation.MainContainerViewModelTest"`
Expected: FAIL (erro de compilação)

- [ ] **Step 3: Criar `MainContainerViewModel`**

```kotlin
package com.flowfuel.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetActiveSharedVehiclesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

data class MainContainerUiState(
    val isGuestMode: Boolean = false,
    val guestVehicle: VehicleShare? = null,
)

@HiltViewModel
class MainContainerViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val getActiveSharedVehicles: GetActiveSharedVehiclesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MainContainerUiState())
    val state: StateFlow<MainContainerUiState> = _state.asStateFlow()

    init {
        sessionStore.activeVehicleIsGuestFlow
            .combine(sessionStore.activeVehicleIdFlow) { isGuest, vehicleId -> isGuest to vehicleId }
            .onEach { (isGuest, vehicleId) ->
                if (!isGuest || vehicleId == null) {
                    _state.value = MainContainerUiState(isGuestMode = false, guestVehicle = null)
                    return@onEach
                }
                val share = (getActiveSharedVehicles() as? AppResult.Success)
                    ?.value?.firstOrNull { it.vehicleId == vehicleId }
                _state.value = MainContainerUiState(isGuestMode = true, guestVehicle = share)
            }
            .launchIn(viewModelScope)
    }
}
```

- [ ] **Step 4: Alterar `MainContainerScreen`**

Adicionar o parâmetro `mainContainerViewModel: MainContainerViewModel = hiltViewModel()` à assinatura do composable e ler `val containerState by mainContainerViewModel.state.collectAsState()`.

Trocar a lista fixa `tabs` (hoje `remember { listOf(...) }` com 5 itens) por uma lista derivada de `containerState.isGuestMode`:
```kotlin
    val tabs = remember(containerState.isGuestMode) {
        val base = listOf(
            FFBottomItem(route = MainDestinations.HOME, label = "Home", icon = Icons.Outlined.Home, selectedIcon = Icons.Filled.Home),
        )
        val ownerOnly = if (containerState.isGuestMode) emptyList() else listOf(
            FFBottomItem(route = MainDestinations.HISTORY, label = "Histórico", icon = Icons.Outlined.History, selectedIcon = Icons.Filled.History),
        )
        val stationsAndBeyond = listOf(
            FFBottomItem(route = MainDestinations.STATIONS, label = "Postos", icon = Icons.Outlined.LocalGasStation, selectedIcon = Icons.Filled.LocalGasStation),
        ) + (if (containerState.isGuestMode) emptyList() else listOf(
            FFBottomItem(route = MainDestinations.EVENTS, label = "Eventos", icon = Icons.AutoMirrored.Outlined.Assignment, selectedIcon = Icons.AutoMirrored.Filled.Assignment),
        )) + listOf(
            FFBottomItem(route = MainDestinations.PROFILE, label = "Perfil", icon = Icons.Outlined.Person, selectedIcon = Icons.Filled.Person),
        )
        base + ownerOnly + stationsAndBeyond
    }
```

No `floatingActionButton` do `FFBottomBar`, esconder o FAB em modo convidado (a tela `GuestVehicleScreen` já tem seus próprios botões inline):
```kotlin
                floatingActionButton = {
                    if (containerState.isGuestMode) {
                        // sem FAB — GuestVehicleScreen tem os próprios botões de ação
                    } else if (currentRoute == MainDestinations.EVENTS) {
                        FFFab(icon = Icons.Default.Add, contentDescription = "Novo evento", onClick = { triggerEventCreate = true })
                    } else {
                        FFFab(icon = Icons.Default.LocalGasStation, contentDescription = "Registrar abastecimento", onClick = quickRefuelViewModel::openSheet)
                    }
                },
```

No `composable(MainDestinations.HOME)`, trocar `HomeScreen(...)` por uma bifurcação:
```kotlin
            composable(MainDestinations.HOME) {
                val guestVehicle = containerState.guestVehicle
                if (containerState.isGuestMode && guestVehicle != null) {
                    com.flowfuel.app.feature.vehicle.presentation.guest.GuestVehicleScreen(
                        onNavigateToCreateEvent = { vehicleId -> onNavigateToEventCreate(vehicleId) },
                        onNavigateToPicker = { onNavigateToVehiclePicker() },
                        onSwitchVehicleClicked = { onNavigateToVehiclePicker() },
                    )
                } else {
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
            }
```
(`onNavigateToEventCreate` já existe como parâmetro do composable — mas hoje ela não passa `guestMode=true`; isso é ajustado na Task 9 junto com o resto da navegação de `FlowFuelNavHost`, onde `onNavigateToEventCreate` é de fato implementado. Adicionar aqui só o novo parâmetro `onNavigateToVehiclePicker: () -> Unit = {}` à assinatura de `MainContainerScreen`.)

- [ ] **Step 5: Rodar o teste pra confirmar que passa**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.navigation.MainContainerViewModelTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/navigation/ app/src/test/java/com/flowfuel/app/navigation/MainContainerViewModelTest.kt
git commit -m "feat(vehicleshare): restrict bottom nav and swap Home tab in guest mode"
```

---

### Task 7: `ShareVehicleScreen` — dono convida e gerencia

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/share/ShareVehicleScreen.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/share/ShareVehicleViewModel.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/details/VehicleDetailsScreen.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/share/ShareVehicleViewModelTest.kt`

**Interfaces:**
- Consumes: `InviteVehicleShareUseCase`, `RevokeVehicleShareUseCase`, `GetVehicleShareForVehicleUseCase` (Task 1).
- Produces: `ShareVehicleViewModel.state: StateFlow<ShareVehicleUiState>` (sealed: `Loading`, `NoShare`, `Pending(share)`, `Active(share)`, `Error`).

- [ ] **Step 1: Escrever o teste (vai falhar — a classe não existe)**

```kotlin
package com.flowfuel.app.feature.vehicle.presentation.share

import androidx.lifecycle.SavedStateHandle
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShareStatus
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetVehicleShareForVehicleUseCase
import com.flowfuel.app.core.vehicleshare.domain.usecase.InviteVehicleShareUseCase
import com.flowfuel.app.core.vehicleshare.domain.usecase.RevokeVehicleShareUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShareVehicleViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val invite: InviteVehicleShareUseCase = mockk()
    private val revoke: RevokeVehicleShareUseCase = mockk()
    private val getForVehicle: GetVehicleShareForVehicleUseCase = mockk()

    private fun share(status: VehicleShareStatus) = VehicleShare(
        id = 100, vehicleId = 10, vehicleBrand = "Toyota", vehicleModel = "Corolla",
        ownerId = 1, ownerName = "Dono", guestId = 2, guestName = "Convidado",
        status = status, createdAt = null, respondedAt = null, expiresAt = "2026-07-20T00:00:00",
    )

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun init_semShare_estadoNoShare() = runTest {
        coEvery { getForVehicle(10) } returns AppResult.Success(null)
        val viewModel = ShareVehicleViewModel(SavedStateHandle(mapOf("vehicleId" to 10)), invite, revoke, getForVehicle)

        assertTrue(viewModel.state.value is ShareVehicleUiState.NoShare)
    }

    @Test
    fun init_sharePending_estadoPending() = runTest {
        coEvery { getForVehicle(10) } returns AppResult.Success(share(VehicleShareStatus.PENDING))
        val viewModel = ShareVehicleViewModel(SavedStateHandle(mapOf("vehicleId" to 10)), invite, revoke, getForVehicle)

        assertTrue(viewModel.state.value is ShareVehicleUiState.Pending)
    }

    @Test
    fun sendInvite_sucesso_chamaUseCaseComEmailEDuracao() = runTest {
        coEvery { getForVehicle(10) } returns AppResult.Success(null)
        coEvery { invite(10, "guest@test.com", 3) } returns AppResult.Success(share(VehicleShareStatus.PENDING))
        val viewModel = ShareVehicleViewModel(SavedStateHandle(mapOf("vehicleId" to 10)), invite, revoke, getForVehicle)

        viewModel.onEmailChange("guest@test.com")
        viewModel.onDurationChange(3)
        viewModel.sendInvite()

        coVerify { invite(10, "guest@test.com", 3) }
    }
}
```

- [ ] **Step 2: Rodar o teste pra confirmar que falha**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.share.ShareVehicleViewModelTest"`
Expected: FAIL (erro de compilação)

- [ ] **Step 3: Criar `ShareVehicleViewModel`**

```kotlin
package com.flowfuel.app.feature.vehicle.presentation.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetVehicleShareForVehicleUseCase
import com.flowfuel.app.core.vehicleshare.domain.usecase.InviteVehicleShareUseCase
import com.flowfuel.app.core.vehicleshare.domain.usecase.RevokeVehicleShareUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ShareVehicleUiState {
    data object Loading : ShareVehicleUiState
    data class NoShare(val email: String = "", val durationDays: Int = 3, val isSubmitting: Boolean = false, val error: String? = null) : ShareVehicleUiState
    data class Pending(val share: VehicleShare, val isRevoking: Boolean = false) : ShareVehicleUiState
    data class Active(val share: VehicleShare, val isRevoking: Boolean = false) : ShareVehicleUiState
    data class Error(val message: String) : ShareVehicleUiState
}

@HiltViewModel
class ShareVehicleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val invite: InviteVehicleShareUseCase,
    private val revoke: RevokeVehicleShareUseCase,
    private val getForVehicle: GetVehicleShareForVehicleUseCase,
) : ViewModel() {

    private val vehicleId: Int = checkNotNull(savedStateHandle["vehicleId"])

    private val _state = MutableStateFlow<ShareVehicleUiState>(ShareVehicleUiState.Loading)
    val state: StateFlow<ShareVehicleUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            when (val result = getForVehicle(vehicleId)) {
                is AppResult.Success -> _state.value = toState(result.value)
                is AppResult.Failure -> _state.value = ShareVehicleUiState.Error(mapErrorMessage(result.error))
            }
        }
    }

    private fun toState(share: VehicleShare?): ShareVehicleUiState = when {
        share == null -> ShareVehicleUiState.NoShare()
        share.status.name == "ACTIVE" -> ShareVehicleUiState.Active(share)
        else -> ShareVehicleUiState.Pending(share)
    }

    fun onEmailChange(value: String) {
        val current = _state.value as? ShareVehicleUiState.NoShare ?: return
        _state.value = current.copy(email = value, error = null)
    }

    fun onDurationChange(days: Int) {
        val current = _state.value as? ShareVehicleUiState.NoShare ?: return
        _state.value = current.copy(durationDays = days)
    }

    fun sendInvite() {
        val current = _state.value as? ShareVehicleUiState.NoShare ?: return
        if (current.email.isBlank()) {
            _state.value = current.copy(error = "Informe o email do convidado")
            return
        }
        _state.value = current.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            when (val result = invite(vehicleId, current.email.trim(), current.durationDays)) {
                is AppResult.Success -> _state.value = toState(result.value)
                is AppResult.Failure -> _state.value = current.copy(isSubmitting = false, error = mapErrorMessage(result.error))
            }
        }
    }

    fun revokeShare() {
        val shareId = when (val s = _state.value) {
            is ShareVehicleUiState.Pending -> s.share.id
            is ShareVehicleUiState.Active -> s.share.id
            else -> return
        }
        viewModelScope.launch {
            when (revoke(shareId)) {
                is AppResult.Success -> _state.value = ShareVehicleUiState.NoShare()
                is AppResult.Failure -> load()
            }
        }
    }

    private fun mapErrorMessage(error: AppError): String = when {
        error is AppError.Api && error.code == "RESOURCE_NOT_FOUND" -> "Esse email não tem cadastro no FlowFuel"
        error is AppError.Api && error.code == "CONFLICT" -> "Já existe um compartilhamento pendente ou ativo pra esse veículo"
        error is AppError.Api && error.code == "BUSINESS_RULE_VIOLATED" -> error.message ?: "Não foi possível compartilhar"
        error == AppError.Network -> "Sem conexão. Verifique sua internet."
        else -> "Erro inesperado. Tente novamente."
    }
}
```

- [ ] **Step 4: Criar `ShareVehicleScreen`**

```kotlin
package com.flowfuel.app.feature.vehicle.presentation.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFTextField
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.theme.FFTheme

@Composable
fun ShareVehicleScreen(
    onBack: () -> Unit,
    viewModel: ShareVehicleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { FFTopBar(title = "Compartilhar veículo", onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = FFTheme.spacing.md, vertical = FFTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
        ) {
            when (val s = state) {
                ShareVehicleUiState.Loading -> Text("Carregando...")
                is ShareVehicleUiState.NoShare -> {
                    FFTextField(
                        value = s.email,
                        onValueChange = viewModel::onEmailChange,
                        label = "Email do convidado",
                        errorText = s.error,
                        enabled = !s.isSubmitting,
                    )
                    Text("Duração: ${s.durationDays} dia(s)", style = MaterialTheme.typography.bodyMedium)
                    Row {
                        listOf(1, 3, 7, 14, 30).forEach { days ->
                            FFButton(
                                text = "${days}d",
                                onClick = { viewModel.onDurationChange(days) },
                                variant = if (s.durationDays == days) FFButtonVariant.Primary else FFButtonVariant.Secondary,
                                modifier = Modifier.padding(end = FFTheme.spacing.xs),
                            )
                        }
                    }
                    FFButton(
                        text = "Enviar convite",
                        onClick = viewModel::sendInvite,
                        enabled = !s.isSubmitting,
                        loading = s.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is ShareVehicleUiState.Pending -> {
                    Text("Convite enviado para ${s.share.guestName.ifBlank { "o convidado" }}, aguardando resposta.")
                    FFButton(
                        text = "Cancelar convite",
                        onClick = viewModel::revokeShare,
                        variant = FFButtonVariant.Destructive,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is ShareVehicleUiState.Active -> {
                    Text("Compartilhado com ${s.share.guestName} até ${s.share.expiresAt.orEmpty()}")
                    FFButton(
                        text = "Encerrar compartilhamento",
                        onClick = viewModel::revokeShare,
                        variant = FFButtonVariant.Destructive,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is ShareVehicleUiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
```
(`Row` precisa de `import androidx.compose.foundation.layout.Row` no topo do arquivo.)

- [ ] **Step 5: Adicionar o botão em `VehicleDetailsScreen`**

Na área de ações da tela (perto de "Atualizar Odômetro"), adicionar:
```kotlin
                FFButton(
                    text = "Compartilhar veículo",
                    onClick = { onNavigateToShare(vehicleId) },
                    variant = FFButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
```
Novo parâmetro do composable: `onNavigateToShare: (vehicleId: Int) -> Unit`.

- [ ] **Step 6: Rodar o teste pra confirmar que passa**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.share.ShareVehicleViewModelTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/share/ app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/details/VehicleDetailsScreen.kt app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/share/
git commit -m "feat(vehicleshare): add ShareVehicleScreen (owner invite/revoke)"
```

---

### Task 8: `ShareInviteScreen` — convidado aceita/recusa

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/share/ShareInviteScreen.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/share/ShareInviteViewModel.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/share/ShareInviteViewModelTest.kt`

**Interfaces:**
- Consumes: `AcceptVehicleShareUseCase`, `RejectVehicleShareUseCase`, `GetPendingVehicleSharesUseCase` (Task 1).
- Produces: `ShareInviteViewModel.state: StateFlow<ShareInviteUiState>`, `.accept()`, `.reject()`; efeito `NavigateBack`.

- [ ] **Step 1: Escrever o teste (vai falhar — a classe não existe)**

```kotlin
package com.flowfuel.app.feature.vehicle.presentation.share

import androidx.lifecycle.SavedStateHandle
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShareStatus
import com.flowfuel.app.core.vehicleshare.domain.usecase.AcceptVehicleShareUseCase
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetPendingVehicleSharesUseCase
import com.flowfuel.app.core.vehicleshare.domain.usecase.RejectVehicleShareUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class ShareInviteViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val accept: AcceptVehicleShareUseCase = mockk()
    private val reject: RejectVehicleShareUseCase = mockk()
    private val getPending: GetPendingVehicleSharesUseCase = mockk()

    private fun share() = VehicleShare(
        id = 100, vehicleId = 10, vehicleBrand = "Toyota", vehicleModel = "Corolla",
        ownerId = 1, ownerName = "Dono", guestId = 2, guestName = "Eu",
        status = VehicleShareStatus.PENDING, createdAt = null, respondedAt = null, expiresAt = null,
    )

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun init_buscaConvitesPendentesEEncontraOShareId() = runTest {
        coEvery { getPending() } returns AppResult.Success(listOf(share()))
        val viewModel = ShareInviteViewModel(SavedStateHandle(mapOf("shareId" to 100)), accept, reject, getPending)

        val state = viewModel.state.first { it is ShareInviteUiState.Content }
        assertTrue(state is ShareInviteUiState.Content)
    }

    @Test
    fun accept_sucesso_chamaUseCaseComShareId() = runTest {
        coEvery { getPending() } returns AppResult.Success(listOf(share()))
        coEvery { accept(100) } returns AppResult.Success(share())
        val viewModel = ShareInviteViewModel(SavedStateHandle(mapOf("shareId" to 100)), accept, reject, getPending)

        viewModel.accept()

        coVerify { accept(100) }
    }

    @Test
    fun reject_sucesso_chamaUseCaseComShareId() = runTest {
        coEvery { getPending() } returns AppResult.Success(listOf(share()))
        coEvery { reject(100) } returns AppResult.Success(share())
        val viewModel = ShareInviteViewModel(SavedStateHandle(mapOf("shareId" to 100)), accept, reject, getPending)

        viewModel.reject()

        coVerify { reject(100) }
    }
}
```

- [ ] **Step 2: Rodar o teste pra confirmar que falha**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.share.ShareInviteViewModelTest"`
Expected: FAIL (erro de compilação)

- [ ] **Step 3: Criar `ShareInviteViewModel`**

O backend não expõe `GET /vehicle-shares/{id}` — só `GET /vehicle-shares/pending` (lista). A tela busca a lista de pendentes e filtra pelo `shareId` da rota (que vem do deep link do push ou da lista do Perfil).

```kotlin
package com.flowfuel.app.feature.vehicle.presentation.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.usecase.AcceptVehicleShareUseCase
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetPendingVehicleSharesUseCase
import com.flowfuel.app.core.vehicleshare.domain.usecase.RejectVehicleShareUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ShareInviteUiState {
    data object Loading : ShareInviteUiState
    data class Content(val share: VehicleShare, val isSubmitting: Boolean = false) : ShareInviteUiState
    data class NotFound(val message: String = "Convite não encontrado ou já respondido") : ShareInviteUiState
}

sealed interface ShareInviteEffect {
    data object NavigateBack : ShareInviteEffect
}

@HiltViewModel
class ShareInviteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val acceptShare: AcceptVehicleShareUseCase,
    private val rejectShare: RejectVehicleShareUseCase,
    private val getPending: GetPendingVehicleSharesUseCase,
) : ViewModel() {

    private val shareId: Int = checkNotNull(savedStateHandle["shareId"])

    private val _state = MutableStateFlow<ShareInviteUiState>(ShareInviteUiState.Loading)
    val state: StateFlow<ShareInviteUiState> = _state.asStateFlow()

    private val _effects = Channel<ShareInviteEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            when (val result = getPending()) {
                is AppResult.Success -> {
                    val found = result.value.firstOrNull { it.id == shareId }
                    _state.value = if (found != null) ShareInviteUiState.Content(found) else ShareInviteUiState.NotFound()
                }
                is AppResult.Failure -> _state.value = ShareInviteUiState.NotFound("Erro ao carregar o convite")
            }
        }
    }

    fun accept() = respond { acceptShare(shareId) }

    fun reject() = respond { rejectShare(shareId) }

    private fun respond(action: suspend () -> AppResult<VehicleShare>) {
        val current = _state.value as? ShareInviteUiState.Content ?: return
        _state.value = current.copy(isSubmitting = true)
        viewModelScope.launch {
            action()
            _effects.send(ShareInviteEffect.NavigateBack)
        }
    }
}
```

- [ ] **Step 4: Criar `ShareInviteScreen`**

```kotlin
package com.flowfuel.app.feature.vehicle.presentation.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.theme.FFTheme
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ShareInviteScreen(
    onBack: () -> Unit,
    viewModel: ShareInviteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                ShareInviteEffect.NavigateBack -> onBack()
            }
        }
    }

    Scaffold(
        topBar = { FFTopBar(title = "Convite de veículo", onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = FFTheme.spacing.md, vertical = FFTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
        ) {
            when (val s = state) {
                ShareInviteUiState.Loading -> Text("Carregando...")
                is ShareInviteUiState.NotFound -> Text(s.message)
                is ShareInviteUiState.Content -> {
                    Text("${s.share.ownerName} quer compartilhar o ${s.share.vehicleBrand} ${s.share.vehicleModel} com você.")
                    FFButton(
                        text = "Aceitar",
                        onClick = viewModel::accept,
                        enabled = !s.isSubmitting,
                        loading = s.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FFButton(
                        text = "Recusar",
                        onClick = viewModel::reject,
                        enabled = !s.isSubmitting,
                        variant = FFButtonVariant.Destructive,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 5: Registrar as rotas novas em `Destinations.kt` e `FlowFuelNavHost.kt`**

Em `Destinations.kt`:
```kotlin
    const val VEHICLE_SHARE         = "vehicle/share/{vehicleId}"
    const val VEHICLE_SHARE_INVITE  = "vehicle-share/{shareId}"
```
e as funções:
```kotlin
    fun vehicleShare(vehicleId: Int) = "vehicle/share/$vehicleId"
    fun vehicleShareInvite(shareId: Int) = "vehicle-share/$shareId"
```

Em `FlowFuelNavHost.kt`, adicionar dois `composable`s (perto do bloco de veículos):
```kotlin
        // ── Compartilhar veículo (dono) ─────────────────────────────────────
        composable(
            route = Destinations.VEHICLE_SHARE,
            arguments = listOf(navArgument("vehicleId") { type = NavType.IntType }),
        ) {
            com.flowfuel.app.feature.vehicle.presentation.share.ShareVehicleScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // ── Convite de compartilhamento (convidado) ─────────────────────────
        composable(
            route = Destinations.VEHICLE_SHARE_INVITE,
            arguments = listOf(navArgument("shareId") { type = NavType.IntType }),
        ) {
            com.flowfuel.app.feature.vehicle.presentation.share.ShareInviteScreen(
                onBack = { navController.popBackStack() },
            )
        }
```
E conectar `onNavigateToShare` em `VehicleDetailsScreen` (composable `Destinations.VEHICLE_DETAILS`):
```kotlin
                onNavigateToShare = { vehicleId -> navController.navigate(Destinations.vehicleShare(vehicleId)) },
```

- [ ] **Step 6: Rodar o teste pra confirmar que passa**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.share.ShareInviteViewModelTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/share/ app/src/main/java/com/flowfuel/app/navigation/ app/src/test/java/com/flowfuel/app/feature/vehicle/presentation/share/ShareInviteViewModelTest.kt
git commit -m "feat(vehicleshare): add ShareInviteScreen and register deep-link route"
```

---

### Task 9: Perfil — descoberta de convites pendentes sem depender de push

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileViewModel.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileScreen.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileViewModelTest.kt`

**Interfaces:**
- Consumes: `GetPendingVehicleSharesUseCase` (Task 1).
- Produces: `ProfileUiState.Content.pendingShareCount: Int`; `ProfileEffect.NavigateToShareInvites` (nova rota simples de lista, reaproveita `ShareInviteScreen` por item).

- [ ] **Step 1: Escrever o teste (vai falhar — `pendingShareCount` não existe)**

```kotlin
    @Test
    fun load_comConvitesPendentes_expoePendingShareCount() {
        coEvery { getProfile() } returns AppResult.Success(fixtureProfile)
        coEvery { getProfileStats() } returns ProfileStats(vehiclesCount = 0, refuelsCount = 0, eventsCount = 0)
        coEvery { getPendingVehicleShares() } returns AppResult.Success(listOf(fixtureShare))

        val viewModel = createViewModel()

        val content = viewModel.state.value as ProfileUiState.Content
        assertEquals(1, content.pendingShareCount)
    }
```

(Adicionar esse teste ao `ProfileViewModelTest` existente, junto de um `fixtureShare: VehicleShare` e `getPendingVehicleShares: GetPendingVehicleSharesUseCase = mockk()` nos mocks do arquivo, e `getPendingVehicleShares` como novo argumento de `createViewModel()`/`ProfileViewModel(...)`.)

- [ ] **Step 2: Rodar o teste pra confirmar que falha**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.auth.presentation.profile.ProfileViewModelTest"`
Expected: FAIL (erro de compilação)

- [ ] **Step 3: Alterar `ProfileViewModel`**

Adicionar `pendingShareCount: Int = 0` a `ProfileUiState.Content`, `NavigateToShareInvites : ProfileEffect` a `ProfileEffect`, e `private val getPendingVehicleShares: GetPendingVehicleSharesUseCase` ao construtor.

Em `load()`, depois de montar `ProfileUiState.Content(result.value)` e chamar `loadStats()`, adicionar:
```kotlin
                    loadPendingShares()
```
com o novo método:
```kotlin
    private fun loadPendingShares() {
        viewModelScope.launch {
            val result = getPendingVehicleShares()
            val count = (result as? AppResult.Success)?.value?.size ?: 0
            _state.update { current ->
                (current as? ProfileUiState.Content)?.copy(pendingShareCount = count) ?: current
            }
        }
    }
```
E o handler de clique:
```kotlin
    fun onPendingSharesClicked() {
        viewModelScope.launch { _effects.send(ProfileEffect.NavigateToShareInvites) }
    }
```

- [ ] **Step 4: Alterar `ProfileScreen`**

Adicionar, na área de itens de menu do Perfil, uma linha condicional (só quando `content.pendingShareCount > 0`):
```kotlin
                if (content.pendingShareCount > 0) {
                    FFButton(
                        text = "Convites de veículo pendentes (${content.pendingShareCount})",
                        onClick = viewModel::onPendingSharesClicked,
                        variant = FFButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
```
Tratar o novo efeito `ProfileEffect.NavigateToShareInvites` no `collectLatest` já existente, repassando pro parâmetro novo `onNavigateToShareInvites: () -> Unit` do composable.

**Nota de escopo:** essa spec não define uma tela de "lista de convites pendentes" separada — com um único compartilhamento ativo por vez no backend, `getPendingVehicleShares()` normalmente devolve 0 ou 1 item. `onNavigateToShareInvites` navega direto pra `Destinations.vehicleShareInvite(shareId)` do primeiro (único) item da lista, sem tela intermediária. Se `getPendingVehicleShares()` já tiver sido chamado no `ProfileViewModel` (Step 3), reaproveitar o resultado em vez de buscar de novo — trocar `ProfileEffect.NavigateToShareInvites` por `data class NavigateToShareInvite(val shareId: Int) : ProfileEffect`, enviado só quando `pendingShareCount > 0` com o `id` do primeiro item.

- [ ] **Step 5: Conectar em `FlowFuelNavHost.kt`**

No composable `Destinations.MAIN_CONTAINER` → `ProfileScreen(...)`, adicionar:
```kotlin
                onNavigateToShareInvite = { shareId -> navController.navigate(Destinations.vehicleShareInvite(shareId)) },
```

- [ ] **Step 6: Rodar o teste pra confirmar que passa**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.auth.presentation.profile.ProfileViewModelTest"`
Expected: PASS

- [ ] **Step 7: Rodar a suíte completa do app pra garantir que nada quebrou**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt app/src/test/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileViewModelTest.kt
git commit -m "feat(vehicleshare): surface pending vehicle-share invites in Profile"
```

## Verificação manual (fora do escopo automatizado)

Depois de todas as tasks aplicadas: instalar o app em dois dispositivos/contas de teste, convidar por email a partir de `VehicleDetailsScreen`, aceitar pelo push (e separadamente, negando a permissão de notificação, pelo Perfil), confirmar bottom nav restrita, atualizar odômetro, registrar evento de categoria permitida, confirmar que categorias fora da lista não aparecem no seletor, revogar do lado do dono e confirmar que a próxima ação do convidado devolve ele ao picker com mensagem clara.
