# Design: Auto-atualização via GitHub Releases

## Contexto

O FlowFuel não está na Play Store — builds de release são publicadas como
GitHub Release (asset `flowfuel-app.apk`) sempre que uma tag `vX.Y.Z` é
enviada ao repositório (`.github/workflows/release.yml`), usando
`softprops/action-gh-release` num repositório público
(`Rochafelip/flowfuel-app`). Hoje, descobrir que existe uma versão nova é
manual: o usuário precisa saber que deve checar a página de releases no
GitHub. O `AboutDialog.kt` já mostra `BuildConfig.VERSION_NAME` e um link
para o GitHub do desenvolvedor, mas não faz nenhuma checagem de versão.

Este documento cobre uma feature nova: o app checar sozinho, ao abrir, se
há uma versão mais nova publicada, avisar o usuário (de forma dispensável)
e, se ele topar, baixar e abrir o instalador do APK sem sair do app.

## Objetivo

Ao chegar na tela principal do app (`MainContainerScreen`), numa build de
**release**, o app verifica em segundo plano a release mais recente no
GitHub. Se for mais nova que a versão instalada e o usuário ainda não
tiver dispensado essa versão específica, mostra um diálogo com opção de
baixar e instalar o update sem sair do app, ou adiar.

## Escopo

### Dentro

- Checagem automática, silenciosa, disparada uma vez por sessão ao entrar
  em `MainContainerScreen` — **não** roda em build debug
  (`BuildConfig.DEBUG`).
- Comparação semântica entre a tag da release mais recente (`vX.Y.Z`) e
  `BuildConfig.VERSION_NAME`.
- Diálogo sempre dispensável ("Depois" / "Atualizar"), nunca bloqueia o
  uso do app.
- "Depois" grava a versão dispensada; só volta a perguntar se uma versão
  **ainda mais nova** for publicada.
- Download do APK via `DownloadManager` do Android (sobrevive a
  app-em-background e a morte de processo).
- Checagem/pedido da permissão "instalar apps desconhecidos"
  (`REQUEST_INSTALL_PACKAGES`) antes de baixar, se ainda não concedida.
- Abertura automática do instalador do Android quando o download termina,
  reaproveitando o `FileProvider` já configurado no projeto.
- Falhas em qualquer etapa (rede, GitHub fora do ar, sem asset `.apk`,
  download falho, permissão negada) são silenciosas — sem tela de erro,
  só log (Timber/Sentry, nível `warning`).

### Fora (não mexer nesta rodada)

- Atualização obrigatória/bloqueante por release (todas as releases são
  igualmente dispensáveis nesta versão da feature).
- Checagem periódica em background (WorkManager) — só checa no momento em
  que o usuário abre o app e navega até o container principal.
- Barra de progresso de download customizada no visual do app — a UI usa
  o estado "baixando" simples (indeterminado) e a notificação de
  progresso nativa do `DownloadManager`.
- Retry automático de download falho — o usuário pode tocar "Atualizar"
  de novo manualmente.
- Release notes ricas (markdown renderizado) — mostra o campo `body` da
  release como texto simples, sem parsing de markdown.
- Mudança no pipeline de release (`.github/workflows/release.yml`) — o
  endpoint público `GET /repos/{owner}/{repo}/releases/latest` e o asset
  já existem hoje, nada muda no CI.

## Arquitetura

Novo módulo `feature/update/`, seguindo a mesma organização
`data/domain/presentation/di` das demais features.

```
app/src/main/java/com/flowfuel/app/feature/update/
├── data/
│   ├── remote/
│   │   ├── GithubReleasesApi.kt
│   │   └── dto/GithubReleaseDto.kt
│   ├── UpdatePrefsStore.kt
│   └── UpdateRepositoryImpl.kt
├── domain/
│   ├── UpdateRepository.kt
│   ├── model/UpdateInfo.kt
│   └── VersionComparator.kt
├── presentation/
│   ├── UpdateViewModel.kt
│   ├── UpdateUiState.kt
│   └── UpdateAvailableDialog.kt
└── di/
    └── UpdateModule.kt
```

### Rede: `GithubReleasesApi`

Repositório público, sem necessidade de autenticação. Segue o mesmo
padrão do cliente `@Named("refresh")` já existente em
`core/network/NetworkModule.kt` (cliente OkHttp/Retrofit isolado, sem
`AuthInterceptor`/`TokenRefreshAuthenticator`), mas com `baseUrl` própria.
Provido em `feature/update/di/UpdateModule.kt` (não em `NetworkModule`,
para manter o cliente isolado e específico da feature):

```kotlin
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
```

```kotlin
interface GithubReleasesApi {
    @GET("repos/Rochafelip/flowfuel-app/releases/latest")
    suspend fun getLatestRelease(): GithubReleaseDto
}

@Serializable
data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("assets") val assets: List<GithubReleaseAssetDto> = emptyList(),
)

@Serializable
data class GithubReleaseAssetDto(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
)
```

### `VersionComparator`

`feature/update/domain/VersionComparator.kt` — função pura, sem
dependências:

```kotlin
object VersionComparator {
    /** true se [remoteTag] (ex.: "v1.2.0") for mais novo que [currentVersionName] (ex.: "1.1.0"). */
    fun isNewer(remoteTag: String, currentVersionName: String): Boolean {
        val remote = parse(remoteTag.removePrefix("v"))
        val current = parse(currentVersionName.substringBefore("-")) // remove sufixo "-debug", se houver
        for (i in 0..2) {
            if (remote[i] != current[i]) return remote[i] > current[i]
        }
        return false
    }

    private fun parse(version: String): List<Int> =
        version.split(".").map { it.toIntOrNull() ?: 0 }.let { it + List(3 - it.size) { 0 } }
}
```

### `UpdatePrefsStore`

`feature/update/data/UpdatePrefsStore.kt` — mesmo padrão de
`core/datastore/SessionStore.kt`, DataStore próprio (não reaproveita o
`flowfuel_session` existente, mesmo critério já usado por
`VehicleMaintenancePrefsStore`):

```kotlin
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

### `UpdateRepository` / `UpdateRepositoryImpl`

```kotlin
interface UpdateRepository {
    suspend fun checkForUpdate(): UpdateInfo?
    fun enqueueDownload(info: UpdateInfo): Long
    fun isDownloadComplete(downloadId: Long): Boolean
    fun installUri(downloadId: Long): Uri?
    fun canRequestPackageInstalls(): Boolean
    suspend fun dismiss(tag: String)
}

data class UpdateInfo(
    val tag: String,           // "v1.2.0"
    val versionLabel: String,  // "1.2.0", sem o "v"
    val releaseNotes: String?,
    val downloadUrl: String,
)
```

`checkForUpdate()`: chama `GithubReleasesApi.getLatestRelease()`, acha o
asset cujo `name == "flowfuel-app.apk"`; se não achar, retorna `null`. Usa
`VersionComparator.isNewer(dto.tagName, BuildConfig.VERSION_NAME)`; se
`false`, retorna `null`. Se `true`, checa `UpdatePrefsStore
.dismissedVersion() == dto.tagName` — se igual, retorna `null` (já
dispensada); senão retorna o `UpdateInfo`. Qualquer exceção (`IOException`,
`HttpException`) é capturada e vira `null` (fail silencioso).

`enqueueDownload(info)`: usa o `DownloadManager` do sistema
(`context.getSystemService(DownloadManager::class.java)`), salva em
`getExternalFilesDir(DIRECTORY_DOWNLOADS)/flowfuel-update.apk` — mesmo
diretório já coberto por `res/xml/export_file_paths.xml`
(`external-files-path name="downloads" path="Download/"`), então **não
precisa alterar esse XML**. Retorna o `downloadId` do
`DownloadManager.Request`.

`installUri(downloadId)`: monta o `Uri` do arquivo baixado via
`FileProvider.getUriForFile(context, "${context.packageName}.provider", file)`
— mesmo padrão de `ExportRepositoryImpl.saveFile()`.

`canRequestPackageInstalls()`: `context.packageManager
.canRequestPackageInstalls()` (API 26+; o `minSdk` do projeto é
exatamente 26, então a API está disponível em 100% dos dispositivos
suportados, sem checagem de versão de SDK em runtime).

### `UpdateViewModel` / `UpdateUiState`

```kotlin
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data object RequestingInstallPermission : UpdateUiState
    data class Downloading(val info: UpdateInfo) : UpdateUiState
    data class ReadyToInstall(val installUri: Uri) : UpdateUiState
}
```

`init` roda a checagem (`if (!BuildConfig.DEBUG)`), guardada por uma flag
para não repetir se o `ViewModel` recompuser (mesmo `viewModelScope
.launch` único no `init`, como o restante do projeto já faz). Métodos
expostos: `onUpdateClick()` (checa permissão → `RequestingInstallPermission`
se faltar, senão enfileira download e observa conclusão),
`onInstallPermissionResumed()` (chamado quando a tela volta de foreground
após o usuário passar pela tela de configurações, para tentar o download
de novo), `onDismiss()` (grava em `UpdatePrefsStore` e volta pra `Idle`),
`onInstallClick()` (dispara o `Intent` de instalação — feito no
Composable, com o `Uri` do estado).

A conclusão do download é observada com um `BroadcastReceiver` registrado
no `Context` de aplicação para `DownloadManager.ACTION_DOWNLOAD_COMPLETE`,
filtrando pelo `downloadId` retornado por `enqueueDownload`, registrado em
`init` e removido em `onCleared()`.

### Integração em `MainContainerScreen`

Mesmo padrão do `quickRefuelViewModel` já hospedado ali:

```kotlin
updateViewModel: UpdateViewModel = hiltViewModel(),
```

```kotlin
val updateState by updateViewModel.state.collectAsState()
```

```kotlin
if (updateState is UpdateUiState.Available || updateState is UpdateUiState.Downloading) {
    UpdateAvailableDialog(
        state = updateState,
        onUpdateClick = updateViewModel::onUpdateClick,
        onDismiss = updateViewModel::onDismiss,
    )
}
```

`RequestingInstallPermission` dispara (fora do dialog, via `LaunchedEffect`
observando o estado) um `Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
Uri.parse("package:${context.packageName}"))`. `ReadyToInstall` dispara o
`Intent(ACTION_VIEW)` de instalação automaticamente (sem exigir mais um
toque do usuário), com `setDataAndType(uri, "application/vnd.android
.package-archive")` + `FLAG_GRANT_READ_URI_PERMISSION` +
`FLAG_ACTIVITY_NEW_TASK` — mesmo padrão do `ExportBottomSheet.kt`.

### Manifest

Nova permissão:

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

Nenhuma outra mudança de manifest — o `FileProvider` e o diretório de
`Download/` já existem e cobrem o caso de uso.

## Tratamento de erro / casos de borda

- **Sem internet / GitHub indisponível / rate limit da API pública durante
  a checagem:** `checkForUpdate()` retorna `null`, estado permanece
  `Idle`, log `warning` (Timber + Sentry breadcrumb, sem `captureException`
  — não é um erro acionável).
- **Release mais recente sem asset `.apk` anexado:** tratado como "sem
  update disponível" (mesmo fluxo do item acima).
- **Build debug:** checagem nunca dispara — `UpdateViewModel.init` sai
  cedo se `BuildConfig.DEBUG`.
- **Usuário nega a permissão de instalar apps desconhecidos:** ao voltar
  pro app (`ON_RESUME` do `MainContainerScreen`/`Activity`), se a
  permissão continuar negada, volta para `Available` (o diálogo reaparece
  e o usuário pode tentar de novo ou dispensar); nenhuma ação forçada.
- **Download falha (`DownloadManager` retorna `STATUS_FAILED` na
  consulta):** volta para `Available`; sem retry automático, usuário pode
  tocar "Atualizar" de novo.
- **App fechado/processo morto durante o download:** o `DownloadManager`
  continua o download no sistema independente do processo do app; na
  próxima abertura do app, se o download já tiver terminado, a checagem
  de update roda de novo do zero (não há retomada de estado
  "download em andamento" entre sessões — simplificação aceitável dado
  que o `DownloadManager` é rápido o suficiente e o app raramente fica
  minimizado por muito tempo durante um update).
- **Usuário dispensa (`Depois`) e uma release **igual** é republicada
  (mesma tag, asset trocado):** não é recoberto — `dismissedVersion`
  compara só a tag, não o conteúdo; cenário considerado raro o bastante
  pra não tratar nesta rodada.

## Testes

Seguindo o padrão do projeto (JUnit4 + Robolectric + MockK + Turbine):

- **`VersionComparatorTest`** (puro, sem mocks/Robolectric): `1.2.0` >
  `1.1.0`; `1.10.0` > `1.9.0` (comparação numérica, não lexicográfica);
  `1.1.0` não é mais novo que `1.1.0` (igual não conta); tag com prefixo
  `v` é normalizada; versão atual com sufixo `-debug` é comparada
  ignorando o sufixo.
- **`UpdateRepositoryImplTest`**: mocka `GithubReleasesApi` — release mais
  nova com asset correto retorna `UpdateInfo`; release mais nova mas sem
  asset `.apk` retorna `null`; release igual/mais antiga retorna `null`;
  exceção de rede (`IOException`) retorna `null` sem propagar; versão já
  dispensada (`UpdatePrefsStore.dismissedVersion()` igual à tag) retorna
  `null`.
- **`UpdateViewModelTest`**: mocka `UpdateRepository`, valida sequência de
  estados com Turbine — `Idle→Available` quando há update;
  `Idle` permanece `Idle` quando não há; `Available→Idle` (persistindo a
  versão) ao chamar `onDismiss()`; `Available→Downloading→ReadyToInstall`
  no fluxo feliz de `onUpdateClick()` com permissão já concedida;
  `Available→RequestingInstallPermission` quando falta permissão.

## Riscos identificados

- Rate limit não-autenticado da API do GitHub é 60 requisições/hora **por
  IP** — em uso normal (uma checagem por abertura do app) isso não é
  problema para um usuário individual, mas pode ser um ponto de atenção
  se muitos usuários compartilharem o mesmo IP/NAT (ex.: uma rede
  corporativa) e o app crescer de escala. Fora de escopo tratar agora.
- `DownloadManager` tem comportamento inconsistente em algumas
  customizações de Android por fabricante (ex.: MIUI, alguns dispositivos
  Samsung antigos) — é o mecanismo padrão e mais usado pra esse tipo de
  updater sideload, mas não 100% garantido em todo aparelho.
- Introduz uma nova permissão sensível (`REQUEST_INSTALL_PACKAGES`), que
  pode gerar uma pergunta extra do Google Play Protect / do próprio
  Android ao instalar o APK atualizado — comportamento esperado e fora de
  controle do app.
