# Design: Redesign do diálogo de atualização

## Contexto

`docs/superpowers/specs/2026-07-08-auto-update-design.md` implementou a
checagem automática de atualização e o diálogo `UpdateAvailableDialog`
(já em produção desde a v1.3.0). Ao testar o fluxo ponta a ponta em
dispositivo real, dois problemas de UX ficaram evidentes:

1. O texto de "release notes" (`UpdateInfo.releaseNotes`, vindo direto do
   campo `body` da release do GitHub) é markdown gerado automaticamente
   pelo `generate_release_notes: true` do `release.yml` — algo como
   `**Full Changelog**: https://github.com/.../compare/v1.3.0...v1.3.1`.
   Sem parsing, isso aparece como texto cru (asteriscos literais, URL
   solta e não clicável) dentro do `Text` do `FFDialog`. Esse texto é
   pensado pra página de Releases do GitHub (público técnico), não pra um
   usuário final do app.
2. O diálogo usa componentes genéricos do Material (`FFDialog`/
   `AlertDialog` puro) sem nenhuma identidade visual do FlowFuel, e o
   estado "Baixando" é visualmente inconsistente com os outros dois (usa
   `AlertDialog` cru em vez de `FFDialog`), além de mostrar só um spinner
   indeterminado mesmo o `DownloadManager` já expondo bytes
   baixados/total.

Este documento cobre a correção desses dois pontos — sem mudar a lógica
de checagem/versão/dispensa já existente.

## Objetivo

Redesenhar `UpdateAvailableDialog` para: (a) nunca mostrar texto de
changelog bruto — trocar por uma frase curta e amigável; (b) ter visual
consistente e com identidade do FlowFuel nos 3 estados possíveis
(Disponível → Baixando → Pronto para instalar); (c) mostrar progresso
real (%) durante o download em vez do spinner indeterminado.

## Escopo

### Dentro

- Remoção de `UpdateInfo.releaseNotes` e de tudo que o alimenta
  (`GithubReleaseDto.body`, mapeamento em `UpdateRepositoryImpl
  .checkForUpdate()`). O corpo da release do GitHub deixa de ser
  buscado/propagado para o app.
- Novo texto fixo no estado "Disponível": *"A versão {versionLabel} do
  FlowFuel já está disponível, com melhorias e correções."* — sem link
  para changelog externo (decisão explícita: não linkar para a página do
  GitHub a partir do diálogo).
- `UpdateRepository.downloadProgress(downloadId: Long): DownloadProgress?`
  — nova função, reaproveitando a query ao `DownloadManager` já usada em
  `isDownloadComplete`, lendo `COLUMN_BYTES_DOWNLOADED_SO_FAR` e
  `COLUMN_TOTAL_SIZE_BYTES`.
- `UpdateViewModel` faz polling desse progresso (a cada 300ms) enquanto o
  estado é `Downloading`, atualizando `UpdateUiState.Downloading
  .progress`. Job cancelado ao sair do estado `Downloading` por qualquer
  motivo (sucesso, falha, ou `onHideDownloadProgress`).
- Um único composable de diálogo customizado (substitui o uso direto de
  `FFDialog`/`AlertDialog` cru neste arquivo) usado nos 3 estados, com
  ícone de marca (`R.drawable.ic_splash`, o mesmo já usado em
  `LoginScreen`), tipografia/spacing do `FFTheme`, e
  `LinearProgressIndicator` com texto de progresso (`"42% · 3,2 MB de
  8,4 MB"`) no estado "Baixando".
- Atualização dos testes existentes que hoje dependem de `releaseNotes`
  (`UpdateRepositoryImplTest`, `UpdateViewModelTest`) e testes novos para
  `downloadProgress`/polling.

### Fora (não mexer nesta rodada)

- Qualquer mudança na lógica de checagem de versão, dispensa
  (`UpdatePrefsStore`), permissão de instalação ou no fluxo de
  download/instalação em si — só o que é exibido e como.
- Link "Ver o que mudou" / changelog técnico no diálogo — decisão
  explícita de não incluir (usuário optou por tirar essa opção).
- Mudança no pipeline de release (`generate_release_notes: true`
  continua gerando o corpo da release normalmente — ele só deixa de ser
  *lido pelo app*; a página do GitHub continua útil pra quem for lá
  manualmente).
- Testes de UI/Compose para `UpdateAvailableDialog` — o projeto não tem
  esse tipo de teste hoje para diálogos equivalentes (`FFDialog` e afins
  também não têm), então não introduzimos o padrão aqui.
- Notificação de progresso nativa do `DownloadManager` (a barra que
  aparece na notification shade do Android) — fora do controle do app,
  continua como está.

## Arquitetura

### `UpdateInfo` (domínio) — remove campo

```kotlin
data class UpdateInfo(
    val tag: String,
    val versionLabel: String,
    val downloadUrl: String,
)
```

`GithubReleaseDto` mantém `body` fora do parsing? Não — remove o campo
`body` do DTO também (`@SerialName("body")`), já que nada mais o usa.

### `DownloadProgress` (domínio) — novo

`feature/update/domain/model/DownloadProgress.kt`:

```kotlin
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
) {
    /** null enquanto o DownloadManager ainda não sabe o tamanho total (bem no início). */
    val fraction: Float?
        get() = totalBytes.takeIf { it > 0 }?.let { bytesDownloaded.toFloat() / it }
}
```

### `UpdateRepository` / `UpdateRepositoryImpl`

Nova função na interface:

```kotlin
/** Progresso atual do download; null se o downloadId não for encontrado. */
fun downloadProgress(downloadId: Long): DownloadProgress?
```

Implementação em `UpdateRepositoryImpl`, mesmo padrão de
`isDownloadComplete` (query com `DownloadManager.Query()
.setFilterById(downloadId)`, cursor `use { }`):

```kotlin
override fun downloadProgress(downloadId: Long): DownloadProgress? {
    val downloadManager = context.getSystemService(DownloadManager::class.java)
    val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
    return cursor.use {
        if (!it.moveToFirst()) return@use null
        val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        DownloadProgress(downloaded, total)
    }
}
```

### `UpdateUiState` — `Downloading` ganha `progress`

```kotlin
data class Downloading(val info: UpdateInfo, val progress: Float? = null) : UpdateUiState
```

### `UpdateViewModel` — polling de progresso

Em `startDownload(info)`, além de registrar o `BroadcastReceiver` já
existente, lança um `Job` de polling:

```kotlin
private var progressJob: Job? = null

private fun startDownload(info: UpdateInfo) {
    _state.value = UpdateUiState.Downloading(info)
    val downloadId = updateRepository.enqueueDownload(info)
    registerDownloadReceiver(downloadId)
    downloadingInfo = info
    progressJob = viewModelScope.launch {
        while (isActive) {
            val progress = updateRepository.downloadProgress(downloadId)
            _state.update { s ->
                (s as? UpdateUiState.Downloading)?.copy(progress = progress?.fraction) ?: s
            }
            delay(300)
        }
    }
}
```

`progressJob?.cancel()` é chamado junto com `unregisterDownloadReceiver()`
(mesmo ponto de saída do estado `Downloading`: `onDownloadFinished` e
`onCleared`), pra não deixar o polling rodando depois que o download
termina ou o usuário sai do estado.

### `UpdateAvailableDialog` — visual novo

Estrutura comum aos 3 estados via um composable privado
`UpdateDialogShell` (ícone + título + conteúdo + botões), usado por cada
`when` branch:

```kotlin
@Composable
private fun UpdateDialogShell(
    title: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_splash),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(title) },
        text = { Column(content = content) },
        confirmButton = confirmButton,
        dismissButton = dismissButton,
    )
}
```

- **Available**: texto fixo (ver Objetivo), botões "Depois"/"Atualizar"
  (mantidos).
- **Downloading**: `LinearProgressIndicator` — determinado
  (`progress = { fraction }`) quando `state.progress != null`,
  indeterminado enquanto for `null`; texto abaixo formatado como
  `"{pct}% · {baixado} de {total}"` quando determinado, ou só
  `"Baixando FlowFuel {versionLabel}..."` quando indeterminado. Só botão
  "Ocultar" (mantido).
- **ReadyToInstall**: texto fixo atual, botões "Depois"/"Instalar"
  (mantidos).

Formatação de bytes (`"3,2 MB"`) via uma função privada pequena no
próprio arquivo (`formatBytes(bytes: Long): String`, divide por 1024²,
uma casa decimal, vírgula como separador decimal pt-BR) — não introduz
dependência nova nem util compartilhado, já que é usado só aqui.

## Testes

- **`UpdateRepositoryImplTest`**: remove os asserts de `releaseNotes`;
  adiciona casos para `downloadProgress` — downloadId existente retorna
  `DownloadProgress` com os bytes corretos; downloadId inexistente
  retorna `null`.
- **`UpdateViewModelTest`**: remove `releaseNotes` das fixtures; adiciona
  caso validando que `Downloading.progress` é atualizado ao longo do
  tempo (usar `TestDispatcher` com `advanceTimeBy`/`runCurrent` pra
  simular os ticks do polling sem `delay` real).
- **`DownloadProgress`**: teste unitário puro pra `fraction` — `null`
  quando `totalBytes == 0`; valor correto quando `totalBytes > 0`.

## Riscos identificados

- Polling a cada 300ms consulta o `DownloadManager` via `ContentResolver`
  — custo desprezível (é a mesma query leve já usada em
  `isDownloadComplete`, só que repetida), mas se o download for muito
  rápido (rede local/Wi-Fi rápido) o usuário pode nunca ver o estado
  "Baixando" com progresso — comportamento aceitável, o dialog já lida
  com isso hoje (o BroadcastReceiver de conclusão sempre chega).
- Perder o campo `releaseNotes`/`body` significa que, se no futuro
  quisermos reintroduzir changelog no app, será necessário adicionar de
  volta o campo no DTO e no domínio — custo baixo, não é uma decisão
  difícil de reverter.
