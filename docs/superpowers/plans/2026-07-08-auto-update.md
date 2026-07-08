# Auto-atualização via GitHub Releases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** O app FlowFuel checa sozinho, ao abrir (só em build release), se há uma versão mais nova publicada como GitHub Release; se houver, mostra um diálogo dispensável e, se o usuário topar, baixa e instala o APK sem sair do app.

**Architecture:** Novo módulo `feature/update/` (data/domain/presentation/di), seguindo exatamente a organização das demais features do projeto. Rede via um terceiro cliente Retrofit qualificado (`@Named("github")`) apontando pra API pública do GitHub. Download via `DownloadManager` do Android (sobrevive a app-em-background). Estado exposto por um `UpdateViewModel` hospedado em `MainContainerScreen`, mesmo padrão já usado pelo `QuickRefuelViewModel`/FAB.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Retrofit + OkHttp + kotlinx.serialization, DataStore Preferences, Android `DownloadManager` + `FileProvider`. Testes: JUnit4 + Robolectric + MockK + Turbine.

## Global Constraints

- `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35` (`app/build.gradle.kts`).
- Checagem de update roda **apenas em build release** — nunca em debug.
- Nome do asset da release é sempre `flowfuel-app.apk` (fixado em `.github/workflows/release.yml`).
- Endpoint: `GET https://api.github.com/repos/Rochafelip/flowfuel-app/releases/latest` (API pública, sem autenticação).
- Authority do `FileProvider` já configurada: `${applicationId}.provider`, cobrindo `getExternalFilesDir(DIRECTORY_DOWNLOADS)` via `res/xml/export_file_paths.xml` (não precisa alterar esse XML).
- Diálogo de update é sempre dispensável — nunca bloqueia o app.
- Testes seguem o padrão do projeto: `@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [33])`, MockK (`mockk`/`coEvery`/`every`/`coVerify`), Turbine para sequências de estado quando aplicável, `UnconfinedTestDispatcher` + `Dispatchers.setMain/resetMain`.
- Módulos Hilt em `di/`, um `abstract class` para `@Binds` e um `object` para `@Provides`, mesmo padrão de `feature/vehicle/di/VehicleModule.kt`.

---

### Task 1: `VersionComparator`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/update/domain/VersionComparator.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/update/domain/VersionComparatorTest.kt`

**Interfaces:**
- Produces: `object VersionComparator { fun isNewer(remoteTag: String, currentVersionName: String): Boolean }` — usado pelo `UpdateRepositoryImpl` na Task 2.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.flowfuel.app.feature.update.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun `returns true when remote major version is newer`() {
        assertTrue(VersionComparator.isNewer("v2.0.0", "1.9.9"))
    }

    @Test
    fun `returns true when remote minor version is newer`() {
        assertTrue(VersionComparator.isNewer("v1.2.0", "1.1.0"))
    }

    @Test
    fun `returns true when remote patch version is newer`() {
        assertTrue(VersionComparator.isNewer("v1.1.1", "1.1.0"))
    }

    @Test
    fun `compares numerically, not lexicographically`() {
        assertTrue(VersionComparator.isNewer("v1.10.0", "1.9.0"))
    }

    @Test
    fun `returns false when versions are equal`() {
        assertFalse(VersionComparator.isNewer("v1.1.0", "1.1.0"))
    }

    @Test
    fun `returns false when remote version is older`() {
        assertFalse(VersionComparator.isNewer("v1.0.0", "1.1.0"))
    }

    @Test
    fun `ignores a debug suffix on the installed version`() {
        assertTrue(VersionComparator.isNewer("v1.2.0", "1.1.0-debug"))
        assertFalse(VersionComparator.isNewer("v1.1.0", "1.1.0-debug"))
    }

    @Test
    fun `normalizes a leading v prefix on the remote tag`() {
        assertTrue(VersionComparator.isNewer("v1.2.0", "1.1.0"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.update.domain.VersionComparatorTest"`
Expected: FAIL — `VersionComparator` unresolved reference (classe ainda não existe).

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.flowfuel.app.feature.update.domain

object VersionComparator {

    /** true se [remoteTag] (ex.: "v1.2.0") for mais novo que [currentVersionName] (ex.: "1.1.0" ou "1.1.0-debug"). */
    fun isNewer(remoteTag: String, currentVersionName: String): Boolean {
        val remote = parse(remoteTag.removePrefix("v"))
        val current = parse(currentVersionName.substringBefore("-"))
        for (i in 0..2) {
            if (remote[i] != current[i]) return remote[i] > current[i]
        }
        return false
    }

    private fun parse(version: String): List<Int> {
        val parts = version.split(".").map { it.toIntOrNull() ?: 0 }
        return parts + List(3 - parts.size) { 0 }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.update.domain.VersionComparatorTest"`
Expected: PASS (8 testes).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/update/domain/VersionComparator.kt app/src/test/java/com/flowfuel/app/feature/update/domain/VersionComparatorTest.kt
git commit -m "feat(update): add VersionComparator for semantic version checks"
```

---

### Task 2: Camada de dados — DTOs, `GithubReleasesApi`, `UpdatePrefsStore`, `UpdateRepository`/`Impl`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/update/data/remote/dto/GithubReleaseDto.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/update/data/remote/GithubReleasesApi.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/update/domain/model/UpdateInfo.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/update/domain/UpdateRepository.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/update/data/UpdatePrefsStore.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/update/data/UpdateRepositoryImpl.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/update/data/UpdateRepositoryImplTest.kt`
- Modify: `app/src/main/AndroidManifest.xml:9-12` (nova permissão)

**Interfaces:**
- Consumes: `VersionComparator.isNewer(remoteTag, currentVersionName): Boolean` (Task 1).
- Produces:
  - `data class UpdateInfo(val tag: String, val versionLabel: String, val releaseNotes: String?, val downloadUrl: String)`
  - `interface UpdateRepository { suspend fun checkForUpdate(): UpdateInfo?; fun enqueueDownload(info: UpdateInfo): Long; fun isDownloadComplete(downloadId: Long): Boolean; fun installUri(downloadId: Long): Uri?; fun canRequestPackageInstalls(): Boolean; suspend fun dismiss(tag: String) }` — consumido pelo `UpdateViewModel` na Task 4.

- [ ] **Step 1: Adicionar a permissão de instalação no Manifest**

Em `app/src/main/AndroidManifest.xml`, o bloco de permissões hoje é (linhas 5-12):

```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.USE_BIOMETRIC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission
        android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
```

Adicionar uma linha logo após `ACCESS_FINE_LOCATION`, ficando:

```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.USE_BIOMETRIC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
    <uses-permission
        android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
```

- [ ] **Step 2: Criar os DTOs do GitHub**

```kotlin
package com.flowfuel.app.feature.update.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("body") val body: String? = null,
    @SerialName("assets") val assets: List<GithubReleaseAssetDto> = emptyList(),
)

@Serializable
data class GithubReleaseAssetDto(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
)
```

- [ ] **Step 3: Criar a interface Retrofit**

```kotlin
package com.flowfuel.app.feature.update.data.remote

import com.flowfuel.app.feature.update.data.remote.dto.GithubReleaseDto
import retrofit2.http.GET

interface GithubReleasesApi {
    @GET("repos/Rochafelip/flowfuel-app/releases/latest")
    suspend fun getLatestRelease(): GithubReleaseDto
}
```

- [ ] **Step 4: Criar o modelo de domínio `UpdateInfo`**

```kotlin
package com.flowfuel.app.feature.update.domain.model

data class UpdateInfo(
    val tag: String,
    val versionLabel: String,
    val releaseNotes: String?,
    val downloadUrl: String,
)
```

- [ ] **Step 5: Criar a interface `UpdateRepository`**

```kotlin
package com.flowfuel.app.feature.update.domain

import android.net.Uri
import com.flowfuel.app.feature.update.domain.model.UpdateInfo

interface UpdateRepository {
    /** Busca a release mais recente no GitHub; retorna null se não há update, se o asset .apk não existe, ou em caso de erro. */
    suspend fun checkForUpdate(): UpdateInfo?

    /** Enfileira o download do APK via DownloadManager; retorna o downloadId. */
    fun enqueueDownload(info: UpdateInfo): Long

    /** true se o download terminou com sucesso (STATUS_SUCCESSFUL); false se falhou ou não foi encontrado. */
    fun isDownloadComplete(downloadId: Long): Boolean

    /** Uri (via FileProvider) do APK baixado, pronta para abrir o instalador; null se o arquivo não existe. */
    fun installUri(downloadId: Long): Uri?

    fun canRequestPackageInstalls(): Boolean

    /** Marca [tag] como dispensada — não volta a avisar sobre essa versão. */
    suspend fun dismiss(tag: String)
}
```

- [ ] **Step 6: Criar o `UpdatePrefsStore`**

```kotlin
package com.flowfuel.app.feature.update.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updateDataStore by preferencesDataStore(name = "flowfuel_update")

@Singleton
class UpdatePrefsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val DISMISSED_VERSION = stringPreferencesKey("dismissed_version")
    }

    suspend fun dismissedVersion(): String? =
        context.updateDataStore.data.firstOrNull()?.get(Keys.DISMISSED_VERSION)

    suspend fun dismissVersion(tag: String) {
        context.updateDataStore.edit { it[Keys.DISMISSED_VERSION] = tag }
    }
}
```

- [ ] **Step 7: Write the failing test for `UpdateRepositoryImpl.checkForUpdate()`/`dismiss()`**

> Nota: seguindo o mesmo critério já usado em `ExportRepositoryImplTest` (que não testa a parte de `FileProvider`/gravação de arquivo por limitações do Robolectric nesse ambiente), este teste cobre `checkForUpdate()`, `dismiss()` e `canRequestPackageInstalls()` — não `enqueueDownload()`/`isDownloadComplete()`/`installUri()`, que dependem do `DownloadManager` real do sistema.

```kotlin
package com.flowfuel.app.feature.update.data

import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.feature.update.data.remote.GithubReleasesApi
import com.flowfuel.app.feature.update.data.remote.dto.GithubReleaseAssetDto
import com.flowfuel.app.feature.update.data.remote.dto.GithubReleaseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdateRepositoryImplTest {

    private val githubReleasesApi: GithubReleasesApi = mockk()
    private val updatePrefsStore: UpdatePrefsStore = mockk()
    private lateinit var repository: UpdateRepositoryImpl

    @Before
    fun setUp() {
        repository = UpdateRepositoryImpl(
            context = ApplicationProvider.getApplicationContext(),
            githubReleasesApi = githubReleasesApi,
            updatePrefsStore = updatePrefsStore,
        )
    }

    private fun releaseDto(tagName: String, assetName: String = "flowfuel-app.apk") = GithubReleaseDto(
        tagName = tagName,
        body = "Notas da versão $tagName",
        assets = listOf(GithubReleaseAssetDto(name = assetName, downloadUrl = "https://example.com/$assetName")),
    )

    @Test
    fun `checkForUpdate returns UpdateInfo when remote tag is newer than the installed version`() = runTest {
        coEvery { githubReleasesApi.getLatestRelease() } returns releaseDto("v999.0.0")
        coEvery { updatePrefsStore.dismissedVersion() } returns null

        val result = repository.checkForUpdate()

        assertEquals("v999.0.0", result?.tag)
        assertEquals("999.0.0", result?.versionLabel)
        assertEquals("Notas da versão v999.0.0", result?.releaseNotes)
        assertEquals("https://example.com/flowfuel-app.apk", result?.downloadUrl)
    }

    @Test
    fun `checkForUpdate returns null when remote tag is not newer than the installed version`() = runTest {
        coEvery { githubReleasesApi.getLatestRelease() } returns releaseDto("v0.0.1")
        coEvery { updatePrefsStore.dismissedVersion() } returns null

        assertNull(repository.checkForUpdate())
    }

    @Test
    fun `checkForUpdate returns null when the release has no apk asset`() = runTest {
        coEvery { githubReleasesApi.getLatestRelease() } returns releaseDto("v999.0.0", assetName = "other-file.txt")
        coEvery { updatePrefsStore.dismissedVersion() } returns null

        assertNull(repository.checkForUpdate())
    }

    @Test
    fun `checkForUpdate returns null when the newer tag was already dismissed`() = runTest {
        coEvery { githubReleasesApi.getLatestRelease() } returns releaseDto("v999.0.0")
        coEvery { updatePrefsStore.dismissedVersion() } returns "v999.0.0"

        assertNull(repository.checkForUpdate())
    }

    @Test
    fun `checkForUpdate returns null and does not throw when the network call fails`() = runTest {
        coEvery { githubReleasesApi.getLatestRelease() } throws IOException("sem conexão")

        assertNull(repository.checkForUpdate())
    }

    @Test
    fun `dismiss delegates to UpdatePrefsStore with the given tag`() = runTest {
        coEvery { updatePrefsStore.dismissVersion("v1.2.0") } returns Unit

        repository.dismiss("v1.2.0")

        coVerify { updatePrefsStore.dismissVersion("v1.2.0") }
    }
}
```

- [ ] **Step 8: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.update.data.UpdateRepositoryImplTest"`
Expected: FAIL — `UpdateRepositoryImpl` unresolved reference (classe ainda não existe).

- [ ] **Step 9: Write the implementation**

```kotlin
package com.flowfuel.app.feature.update.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.flowfuel.app.BuildConfig
import com.flowfuel.app.feature.update.data.remote.GithubReleasesApi
import com.flowfuel.app.feature.update.domain.UpdateRepository
import com.flowfuel.app.feature.update.domain.VersionComparator
import com.flowfuel.app.feature.update.domain.model.UpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val githubReleasesApi: GithubReleasesApi,
    private val updatePrefsStore: UpdatePrefsStore,
) : UpdateRepository {

    override suspend fun checkForUpdate(): UpdateInfo? = try {
        val release = githubReleasesApi.getLatestRelease()
        val asset = release.assets.firstOrNull { it.name == APK_ASSET_NAME }
        when {
            asset == null -> null
            !VersionComparator.isNewer(release.tagName, BuildConfig.VERSION_NAME) -> null
            updatePrefsStore.dismissedVersion() == release.tagName -> null
            else -> UpdateInfo(
                tag = release.tagName,
                versionLabel = release.tagName.removePrefix("v"),
                releaseNotes = release.body,
                downloadUrl = asset.downloadUrl,
            )
        }
    } catch (e: IOException) {
        Timber.w(e, "UpdateRepository: falha de rede ao checar atualização")
        null
    } catch (e: HttpException) {
        Timber.w(e, "UpdateRepository: erro HTTP ao checar atualização")
        null
    } catch (e: SerializationException) {
        Timber.w(e, "UpdateRepository: resposta do GitHub não corresponde ao DTO esperado")
        null
    }

    override fun enqueueDownload(info: UpdateInfo): Long {
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        val file = apkFile()
        if (file.exists()) file.delete()
        val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("Atualização do FlowFuel")
            .setDescription("Baixando versão ${info.versionLabel}")
            .setDestinationUri(Uri.fromFile(file))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        return downloadManager.enqueue(request)
    }

    override fun isDownloadComplete(downloadId: Long): Boolean {
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        return cursor.use {
            if (!it.moveToFirst()) {
                false
            } else {
                val statusIndex = it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                it.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL
            }
        }
    }

    override fun installUri(downloadId: Long): Uri? {
        Timber.d("UpdateRepository: montando URI de instalação para downloadId=$downloadId")
        val file = apkFile()
        if (!file.exists()) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    override fun canRequestPackageInstalls(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    override suspend fun dismiss(tag: String) = updatePrefsStore.dismissVersion(tag)

    private fun apkFile(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(dir, "flowfuel-update.apk")
    }

    private companion object {
        const val APK_ASSET_NAME = "flowfuel-app.apk"
    }
}
```

- [ ] **Step 10: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.update.data.UpdateRepositoryImplTest"`
Expected: PASS (6 testes).

- [ ] **Step 11: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/flowfuel/app/feature/update/data app/src/main/java/com/flowfuel/app/feature/update/domain app/src/test/java/com/flowfuel/app/feature/update/data
git commit -m "feat(update): add GitHub releases data layer and repository"
```

---

### Task 3: DI (`UpdateModule`) e verificação de build

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/update/di/UpdateModule.kt`

**Interfaces:**
- Consumes: `UpdateRepositoryImpl` (Task 2), `GithubReleasesApi` (Task 2).
- Produces: `UpdateRepository` resolvível via Hilt; `@Named("isDebugBuild") Boolean` resolvível via Hilt — consumido pelo `UpdateViewModel` na Task 4 (necessário para o gate "só roda em release" ser testável: `BuildConfig.DEBUG` é sempre `true` nos testes unitários, que rodam contra a variante debug, então o valor precisa ser injetável em vez de referenciado direto no `ViewModel`).

- [ ] **Step 1: Criar o módulo Hilt**

```kotlin
package com.flowfuel.app.feature.update.di

import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.flowfuel.app.BuildConfig
import com.flowfuel.app.feature.update.data.UpdateRepositoryImpl
import com.flowfuel.app.feature.update.data.remote.GithubReleasesApi
import com.flowfuel.app.feature.update.domain.UpdateRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateBindModule {
    @Binds @Singleton
    abstract fun bindUpdateRepository(impl: UpdateRepositoryImpl): UpdateRepository
}

@Module
@InstallIn(SingletonComponent::class)
object UpdateNetworkModule {

    @Provides @Singleton @Named("isDebugBuild")
    fun provideIsDebugBuild(): Boolean = BuildConfig.DEBUG

    /** Cliente HTTP dedicado à API pública do GitHub — sem AuthInterceptor/TokenRefreshAuthenticator, mesmo molde do cliente @Named("refresh") em NetworkModule. */
    @Provides @Singleton @Named("github")
    fun provideGithubOkHttp(
        logging: HttpLoggingInterceptor,
        chucker: ChuckerInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(chucker)
        .addInterceptor(logging)
        .build()

    @Provides @Singleton @Named("github")
    fun provideGithubRetrofit(
        @Named("github") client: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton
    fun provideGithubReleasesApi(@Named("github") retrofit: Retrofit): GithubReleasesApi =
        retrofit.create(GithubReleasesApi::class.java)
}
```

- [ ] **Step 2: Rodar o build para confirmar que o grafo do Hilt resolve**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` — se houver `@Binds`/`@Provides` faltando ou duplicado, o `kspDebugKotlin`/`hiltAggregateDepsDebug` falha aqui com uma mensagem clara do Dagger/Hilt.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/update/di/UpdateModule.kt
git commit -m "feat(update): wire GitHub API client and repository into Hilt"
```

---

### Task 4: `UpdateUiState` + `UpdateViewModel`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/update/presentation/UpdateUiState.kt`
- Create: `app/src/main/java/com/flowfuel/app/feature/update/presentation/UpdateViewModel.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/update/presentation/UpdateViewModelTest.kt`

**Interfaces:**
- Consumes: `UpdateRepository` (Task 2), `@Named("isDebugBuild") Boolean` (Task 3).
- Produces:
  - `sealed interface UpdateUiState` com `Idle`, `Available(info)`, `RequestingInstallPermission(info)`, `Downloading(info)`, `ReadyToInstall(installUri)`.
  - `class UpdateViewModel { val state: StateFlow<UpdateUiState>; fun onUpdateClick(); fun onInstallPermissionResumed(); fun onDismiss() }` — consumido por `UpdateAvailableDialog` (Task 5) e `MainContainerScreen` (Task 6).

- [ ] **Step 1: Criar `UpdateUiState`**

```kotlin
package com.flowfuel.app.feature.update.presentation

import android.net.Uri
import com.flowfuel.app.feature.update.domain.model.UpdateInfo

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class RequestingInstallPermission(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo) : UpdateUiState
    data class ReadyToInstall(val installUri: Uri) : UpdateUiState
}
```

- [ ] **Step 2: Write the failing test (todas as transições de estado)**

```kotlin
package com.flowfuel.app.feature.update.presentation

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.feature.update.domain.UpdateRepository
import com.flowfuel.app.feature.update.domain.model.UpdateInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdateViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val updateRepository: UpdateRepository = mockk()
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun updateInfo(tag: String = "v9.9.9") = UpdateInfo(
        tag = tag,
        versionLabel = tag.removePrefix("v"),
        releaseNotes = "notas",
        downloadUrl = "https://example.com/a.apk",
    )

    private fun buildViewModel(isDebugBuild: Boolean = false) =
        UpdateViewModel(updateRepository, context, isDebugBuild)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts checking for an update and shows Available when a newer release exists`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()

        val vm = buildViewModel()

        assertEquals(UpdateUiState.Available(updateInfo()), vm.state.value)
    }

    @Test
    fun `stays Idle when there is no update available`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns null

        val vm = buildViewModel()

        assertEquals(UpdateUiState.Idle, vm.state.value)
    }

    @Test
    fun `never checks for an update on a debug build`() = runTest {
        val vm = buildViewModel(isDebugBuild = true)

        assertEquals(UpdateUiState.Idle, vm.state.value)
        coVerify(inverse = true) { updateRepository.checkForUpdate() }
    }

    @Test
    fun `onDismiss persists the dismissed tag and returns to Idle`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()
        coEvery { updateRepository.dismiss("v9.9.9") } returns Unit
        val vm = buildViewModel()

        vm.onDismiss()

        assertEquals(UpdateUiState.Idle, vm.state.value)
        coVerify { updateRepository.dismiss("v9.9.9") }
    }

    @Test
    fun `onUpdateClick starts a download directly when install permission is already granted`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()
        every { updateRepository.canRequestPackageInstalls() } returns true
        every { updateRepository.enqueueDownload(updateInfo()) } returns 42L
        val vm = buildViewModel()

        vm.onUpdateClick()

        assertEquals(UpdateUiState.Downloading(updateInfo()), vm.state.value)
    }

    @Test
    fun `onUpdateClick asks for install permission first when it is not granted`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()
        every { updateRepository.canRequestPackageInstalls() } returns false
        val vm = buildViewModel()

        vm.onUpdateClick()

        assertEquals(UpdateUiState.RequestingInstallPermission(updateInfo()), vm.state.value)
    }

    @Test
    fun `onInstallPermissionResumed starts the download once permission is granted`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()
        every { updateRepository.canRequestPackageInstalls() } returnsMany listOf(false, true)
        every { updateRepository.enqueueDownload(updateInfo()) } returns 42L
        val vm = buildViewModel()
        vm.onUpdateClick()

        vm.onInstallPermissionResumed()

        assertEquals(UpdateUiState.Downloading(updateInfo()), vm.state.value)
    }

    @Test
    fun `onInstallPermissionResumed goes back to Available when permission is still missing`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()
        every { updateRepository.canRequestPackageInstalls() } returns false
        val vm = buildViewModel()
        vm.onUpdateClick()

        vm.onInstallPermissionResumed()

        assertEquals(UpdateUiState.Available(updateInfo()), vm.state.value)
    }

    @Test
    fun `transitions to ReadyToInstall when the tracked download completes successfully`() = runTest {
        val installUri = Uri.parse("content://com.flowfuel.app.debug.provider/downloads/flowfuel-update.apk")
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()
        every { updateRepository.canRequestPackageInstalls() } returns true
        every { updateRepository.enqueueDownload(updateInfo()) } returns 42L
        every { updateRepository.isDownloadComplete(42L) } returns true
        every { updateRepository.installUri(42L) } returns installUri
        val vm = buildViewModel()
        vm.onUpdateClick()

        context.sendBroadcast(
            Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE).putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 42L)
        )

        assertEquals(UpdateUiState.ReadyToInstall(installUri), vm.state.value)
    }

    @Test
    fun `goes back to Available when the tracked download fails`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()
        every { updateRepository.canRequestPackageInstalls() } returns true
        every { updateRepository.enqueueDownload(updateInfo()) } returns 42L
        every { updateRepository.isDownloadComplete(42L) } returns false
        val vm = buildViewModel()
        vm.onUpdateClick()

        context.sendBroadcast(
            Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE).putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 42L)
        )

        assertEquals(UpdateUiState.Available(updateInfo()), vm.state.value)
    }

    @Test
    fun `ignores a download-complete broadcast for a different downloadId`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()
        every { updateRepository.canRequestPackageInstalls() } returns true
        every { updateRepository.enqueueDownload(updateInfo()) } returns 42L
        val vm = buildViewModel()
        vm.onUpdateClick()

        context.sendBroadcast(
            Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE).putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 999L)
        )

        assertEquals(UpdateUiState.Downloading(updateInfo()), vm.state.value)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.update.presentation.UpdateViewModelTest"`
Expected: FAIL — `UpdateViewModel` unresolved reference (classe ainda não existe).

- [ ] **Step 4: Write the implementation**

```kotlin
package com.flowfuel.app.feature.update.presentation

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.feature.update.domain.UpdateRepository
import com.flowfuel.app.feature.update.domain.model.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    @ApplicationContext private val context: Context,
    @Named("isDebugBuild") private val isDebugBuild: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var downloadReceiver: BroadcastReceiver? = null

    init {
        if (!isDebugBuild) {
            viewModelScope.launch {
                updateRepository.checkForUpdate()?.let { info ->
                    _state.value = UpdateUiState.Available(info)
                }
            }
        }
    }

    fun onUpdateClick() {
        val info = currentInfo() ?: return
        if (updateRepository.canRequestPackageInstalls()) {
            startDownload(info)
        } else {
            _state.value = UpdateUiState.RequestingInstallPermission(info)
        }
    }

    fun onInstallPermissionResumed() {
        val info = (_state.value as? UpdateUiState.RequestingInstallPermission)?.info ?: return
        if (updateRepository.canRequestPackageInstalls()) {
            startDownload(info)
        } else {
            _state.value = UpdateUiState.Available(info)
        }
    }

    fun onDismiss() {
        val info = currentInfo() ?: return
        viewModelScope.launch {
            updateRepository.dismiss(info.tag)
            _state.value = UpdateUiState.Idle
        }
    }

    private fun currentInfo(): UpdateInfo? = when (val s = _state.value) {
        is UpdateUiState.Available -> s.info
        is UpdateUiState.RequestingInstallPermission -> s.info
        else -> null
    }

    private fun startDownload(info: UpdateInfo) {
        _state.value = UpdateUiState.Downloading(info)
        val downloadId = updateRepository.enqueueDownload(info)
        registerDownloadReceiver(downloadId)
    }

    private fun registerDownloadReceiver(downloadId: Long) {
        unregisterDownloadReceiver()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (completedId != downloadId) return
                onDownloadFinished(downloadId)
            }
        }
        downloadReceiver = receiver
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun onDownloadFinished(downloadId: Long) {
        val info = (_state.value as? UpdateUiState.Downloading)?.info
        unregisterDownloadReceiver()
        _state.value = if (updateRepository.isDownloadComplete(downloadId)) {
            updateRepository.installUri(downloadId)?.let { UpdateUiState.ReadyToInstall(it) }
                ?: info?.let { UpdateUiState.Available(it) } ?: UpdateUiState.Idle
        } else {
            info?.let { UpdateUiState.Available(it) } ?: UpdateUiState.Idle
        }
    }

    private fun unregisterDownloadReceiver() {
        downloadReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        downloadReceiver = null
    }

    override fun onCleared() {
        unregisterDownloadReceiver()
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.update.presentation.UpdateViewModelTest"`
Expected: PASS (11 testes).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/update/presentation/UpdateUiState.kt app/src/main/java/com/flowfuel/app/feature/update/presentation/UpdateViewModel.kt app/src/test/java/com/flowfuel/app/feature/update/presentation/UpdateViewModelTest.kt
git commit -m "feat(update): add UpdateViewModel state machine with download tracking"
```

---

### Task 5: `UpdateAvailableDialog`

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/update/presentation/UpdateAvailableDialog.kt`

**Interfaces:**
- Consumes: `UpdateUiState` (Task 4), `com.flowfuel.app.core.designsystem.components.FFDialog`/`FFDialogKind` (já existentes em `core/designsystem/components/FFDialog.kt`).
- Produces: `@Composable fun UpdateAvailableDialog(state: UpdateUiState, onUpdateClick: () -> Unit, onDismiss: () -> Unit)` — consumido por `MainContainerScreen` (Task 6).

Sem teste dedicado nesta task: o projeto não tem testes de UI Compose (`app/src/androidTest` está vazio) — toda a cobertura de Compose é feita manualmente, mesmo padrão de `AboutDialog`/`ExportBottomSheet`/`QuickRefuelBottomSheet`.

- [ ] **Step 1: Criar o composable**

```kotlin
package com.flowfuel.app.feature.update.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.flowfuel.app.core.designsystem.components.FFDialog
import com.flowfuel.app.core.designsystem.theme.FFTheme

@Composable
fun UpdateAvailableDialog(
    state: UpdateUiState,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateUiState.Available -> FFDialog(
            title = "Nova versão disponível",
            message = buildString {
                append("FlowFuel ${state.info.versionLabel} já está disponível.")
                state.info.releaseNotes?.takeIf { it.isNotBlank() }?.let {
                    append("\n\n")
                    append(it)
                }
            },
            confirmText = "Atualizar",
            dismissText = "Depois",
            onConfirm = onUpdateClick,
            onDismiss = onDismiss,
        )

        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Baixando atualização") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(FFTheme.spacing.md))
                    Text("Baixando FlowFuel ${state.info.versionLabel}...")
                }
            },
        )

        else -> Unit
    }
}
```

- [ ] **Step 2: Confirmar que compila**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/update/presentation/UpdateAvailableDialog.kt
git commit -m "feat(update): add UpdateAvailableDialog composable"
```

---

### Task 6: Integração em `MainContainerScreen` e verificação final

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt`

**Interfaces:**
- Consumes: `UpdateViewModel`, `UpdateUiState` (Task 4), `UpdateAvailableDialog` (Task 5).

- [ ] **Step 1: Adicionar os novos imports**

Em `app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt`, o bloco de imports hoje termina em (linhas 57-59):

```kotlin
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.presentation.list.VehicleEventsScreen
import kotlinx.coroutines.flow.collectLatest
```

Adicionar, mantendo ordem alfabética dentro de cada bloco (imports do Android/Compose antes dos `com.flowfuel.app.*`):

```kotlin
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flowfuel.app.feature.update.presentation.UpdateAvailableDialog
import com.flowfuel.app.feature.update.presentation.UpdateUiState
import com.flowfuel.app.feature.update.presentation.UpdateViewModel
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.presentation.list.VehicleEventsScreen
import kotlinx.coroutines.flow.collectLatest
```

(Os imports `android.*`/`androidx.*` novos vão intercalados nos blocos já existentes de `android`/`androidx` mais acima no arquivo, e os três `com.flowfuel.app.feature.update.*` entram em ordem alfabética, antes de `com.flowfuel.app.feature.vehicleevent.*` — a ordem exata dentro do arquivo não é validada por lint neste projeto, mas mantenha os imports agrupados de forma legível.)

- [ ] **Step 2: Adicionar o parâmetro `updateViewModel` à assinatura da função**

Em `MainContainerScreen.kt:102`, o último parâmetro da função é:

```kotlin
    quickRefuelViewModel: QuickRefuelViewModel = hiltViewModel(),
) {
```

Alterar para:

```kotlin
    quickRefuelViewModel: QuickRefuelViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
```

- [ ] **Step 3: Coletar o estado do update**

Em `MainContainerScreen.kt:108`, logo após:

```kotlin
    val quickRefuelState by quickRefuelViewModel.state.collectAsState()
```

Adicionar:

```kotlin
    val updateState by updateViewModel.state.collectAsState()
    val context = LocalContext.current
```

- [ ] **Step 4: Adicionar o bloco de UI do update, após o bloco existente do `quickRefuelState`**

O arquivo hoje termina (linhas 293-325) com:

```kotlin
    if (quickRefuelState.showSheet) {
        val vehicle = quickRefuelState.vehicle
        if (vehicle == null) {
            FFBottomSheet(onDismiss = quickRefuelViewModel::closeSheet) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(FFTheme.spacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            QuickRefuelBottomSheet(
                form                      = quickRefuelState.form,
                isSubmitting              = quickRefuelState.isSubmitting,
                submitError               = quickRefuelState.submitError,
                energyType                = vehicle.energyType,
                onOdometerInputModeChange = quickRefuelViewModel::onOdometerInputModeChange,
                onTripKmChange            = quickRefuelViewModel::onTripKmChange,
                onOdometerChange          = quickRefuelViewModel::onOdometerChange,
                onLitersChange            = quickRefuelViewModel::onLitersChange,
                onTotalPriceInput         = quickRefuelViewModel::onTotalPriceInput,
                onFullTankToggle          = quickRefuelViewModel::onFullTankToggle,
                onRefuelTypeChange        = quickRefuelViewModel::onRefuelTypeChange,
                onSubmit                  = quickRefuelViewModel::submitRefuel,
                onDismiss                 = quickRefuelViewModel::closeSheet,
            )
        }
    }
}
```

Alterar o final para (adicionando o bloco de update antes da chave de fechamento da função):

```kotlin
    if (quickRefuelState.showSheet) {
        val vehicle = quickRefuelState.vehicle
        if (vehicle == null) {
            FFBottomSheet(onDismiss = quickRefuelViewModel::closeSheet) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(FFTheme.spacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            QuickRefuelBottomSheet(
                form                      = quickRefuelState.form,
                isSubmitting              = quickRefuelState.isSubmitting,
                submitError               = quickRefuelState.submitError,
                energyType                = vehicle.energyType,
                onOdometerInputModeChange = quickRefuelViewModel::onOdometerInputModeChange,
                onTripKmChange            = quickRefuelViewModel::onTripKmChange,
                onOdometerChange          = quickRefuelViewModel::onOdometerChange,
                onLitersChange            = quickRefuelViewModel::onLitersChange,
                onTotalPriceInput         = quickRefuelViewModel::onTotalPriceInput,
                onFullTankToggle          = quickRefuelViewModel::onFullTankToggle,
                onRefuelTypeChange        = quickRefuelViewModel::onRefuelTypeChange,
                onSubmit                  = quickRefuelViewModel::submitRefuel,
                onDismiss                 = quickRefuelViewModel::closeSheet,
            )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updateViewModel.onInstallPermissionResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (val update = updateState) {
        is UpdateUiState.Available, is UpdateUiState.Downloading -> UpdateAvailableDialog(
            state         = update,
            onUpdateClick = updateViewModel::onUpdateClick,
            onDismiss     = updateViewModel::onDismiss,
        )

        is UpdateUiState.RequestingInstallPermission -> LaunchedEffect(Unit) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                )
            )
        }

        is UpdateUiState.ReadyToInstall -> LaunchedEffect(update.installUri) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(update.installUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        UpdateUiState.Idle -> Unit
    }
}
```

- [ ] **Step 5: Rodar o build completo e a suíte de testes**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, todos os testes existentes + os novos (`VersionComparatorTest`, `UpdateRepositoryImplTest`, `UpdateViewModelTest`) passando.

- [ ] **Step 6: Verificação manual no emulador (smoke test)**

Como não há testes instrumentados no projeto, confirme manualmente que nada quebrou na tela principal:

```bash
./gradlew installDebug
adb shell monkey -p com.flowfuel.app.debug -c android.intent.category.LAUNCHER 1
```

Expected: o app abre normalmente na Home, sem crash e sem nenhum diálogo de update aparecendo (build debug — a checagem está desabilitada). Isso confirma que a integração não quebrou o fluxo principal; o fluxo completo de update (diálogo, download, instalação) só é observável numa build release de verdade instalada por cima de uma versão mais antiga, fora do escopo de verificação automatizável neste plano.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt
git commit -m "feat(update): show update dialog and drive download/install flow from MainContainerScreen"
```

---

## Self-Review

**Cobertura do spec:** checagem automática só-release (Task 4, `isDebugBuild`) ✓; comparação semântica (Task 1) ✓; diálogo sempre dispensável com "Depois"/"Atualizar" (Task 5 + Task 6) ✓; "Depois" não pergunta mais pra essa versão (Task 2 `UpdatePrefsStore` + Task 4 `onDismiss`) ✓; download via `DownloadManager` (Task 2 `enqueueDownload`) ✓; permissão de instalar apps desconhecidos + redirecionamento a Configurações (Task 4 `onInstallPermissionResumed` + Task 6) ✓; instalação automática ao concluir (Task 6 `ReadyToInstall`) ✓; falhas silenciosas (Task 2 `checkForUpdate` catch, Task 4 `onDownloadFinished` fallback pra `Available`) ✓; permissão no Manifest (Task 2) ✓.

**Placeholders:** nenhum "TBD"/"implementar depois" — todo step tem código completo.

**Consistência de tipos:** `UpdateInfo`, `UpdateUiState.*`, `UpdateRepository` usados de forma idêntica em todas as tasks (checado manualmente contra as assinaturas definidas nas Tasks 2 e 4).

**Risco aceito e registrado (já estava na spec):** `UpdateRepositoryImplTest` não cobre `enqueueDownload`/`isDownloadComplete`/`installUri` (dependem do `DownloadManager`/`FileProvider` reais do sistema) — mesmo critério já usado em `ExportRepositoryImplTest` no projeto.
