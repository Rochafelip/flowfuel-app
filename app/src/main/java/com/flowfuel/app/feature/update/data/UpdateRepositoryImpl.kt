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
import com.flowfuel.app.feature.update.domain.model.DownloadProgress
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

    override fun downloadProgress(downloadId: Long): DownloadProgress? {
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        return cursor.use {
            if (!it.moveToFirst()) {
                null
            } else {
                val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                DownloadProgress(bytesDownloaded = downloaded, totalBytes = total)
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
