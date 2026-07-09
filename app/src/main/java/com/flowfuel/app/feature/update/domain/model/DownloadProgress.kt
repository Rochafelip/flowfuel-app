package com.flowfuel.app.feature.update.domain.model

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
) {
    /** null enquanto o DownloadManager ainda não sabe o tamanho total (bem no início do download). */
    val fraction: Float?
        get() = totalBytes.takeIf { it > 0 }?.let { bytesDownloaded.toFloat() / it }
}
