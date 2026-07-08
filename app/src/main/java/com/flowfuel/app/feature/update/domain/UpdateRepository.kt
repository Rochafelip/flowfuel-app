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
