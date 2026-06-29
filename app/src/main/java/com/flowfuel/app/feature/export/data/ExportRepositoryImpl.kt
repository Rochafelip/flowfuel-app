package com.flowfuel.app.feature.export.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.network.ProblemDetails
import com.flowfuel.app.feature.export.data.remote.ExportApi
import com.flowfuel.app.feature.export.domain.ExportFormat
import com.flowfuel.app.feature.export.domain.ExportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.Response
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val problemJson = Json { ignoreUnknownKeys = true; isLenient = true }

@Singleton
class ExportRepositoryImpl @Inject constructor(
    private val api: ExportApi,
    @ApplicationContext private val context: Context,
) : ExportRepository {

    override suspend fun exportRefuels(
        vehicleId: Int,
        format: ExportFormat,
        startDate: String?,
        endDate: String?,
    ): AppResult<Uri> = runExport(format) {
        api.exportRefuels(vehicleId, format.value, startDate, endDate)
    }

    override suspend fun exportEvents(
        vehicleId: Int,
        format: ExportFormat,
        type: String?,
        startDate: String?,
        endDate: String?,
    ): AppResult<Uri> = runExport(format) {
        api.exportEvents(vehicleId, format.value, type, startDate, endDate)
    }

    private suspend fun runExport(
        format: ExportFormat,
        call: suspend () -> Response<ResponseBody>,
    ): AppResult<Uri> = try {
        val response = call()
        if (!response.isSuccessful) {
            mapErrorResponse(response)
        } else {
            val body = response.body()
                ?: return AppResult.Failure(AppError.Unknown())
            val filename = extractFilename(
                response.headers()["Content-Disposition"],
                format,
            )
            val uri = saveFile(body, filename)
            AppResult.Success(uri)
        }
    } catch (e: IOException) {
        Timber.w(e, "export network failure")
        AppResult.Failure(AppError.Network)
    } catch (e: Throwable) {
        Timber.e(e, "export unexpected failure")
        AppResult.Failure(AppError.Unknown(e))
    }

    private fun mapErrorResponse(response: Response<*>): AppResult.Failure {
        if (response.code() == 429) {
            val retryAfter = response.headers()["Retry-After"]?.toIntOrNull()
            return AppResult.Failure(AppError.RateLimited(retryAfter))
        }
        val errorBody = response.errorBody()?.string()
        val problem = errorBody?.let {
            runCatching { problemJson.decodeFromString(ProblemDetails.serializer(), it) }.getOrNull()
        }
        val code = problem?.code ?: "HTTP_${response.code()}"
        return AppResult.Failure(AppError.Api(code, problem?.detail ?: problem?.title))
    }

    private fun saveFile(body: ResponseBody, filename: String): Uri {
        val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val file = File(dir, filename)
        file.outputStream().use { out -> body.byteStream().copyTo(out) }
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    private fun extractFilename(contentDisposition: String?, format: ExportFormat): String {
        if (contentDisposition != null) {
            val match = Regex("""filename[*]?=["']?([^"';\s]+)["']?""")
                .find(contentDisposition)
            if (match != null) return match.groupValues[1]
        }
        return "flowfuel-export.${format.value}"
    }
}
