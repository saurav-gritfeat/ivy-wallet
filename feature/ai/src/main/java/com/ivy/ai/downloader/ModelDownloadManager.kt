package com.ivy.ai.downloader

import com.ivy.ai.engine.MediaPipeLlmEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadableModel(
    val id: String,
    val name: String,
    val description: String,
    val sizeText: String,
    val fileName: String,
    val downloadUrl: String
)

data class DownloadProgress(
    val modelId: String? = null,
    val isDownloading: Boolean = false,
    val progressPercent: Float = 0f,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val errorMessage: String? = null
)

@Singleton
class ModelDownloadManager @Inject constructor(
    private val mediaPipeLlmEngine: MediaPipeLlmEngine
) {
    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    val availableCatalog = listOf(
        DownloadableModel(
            id = "qwen2.5_0.5b",
            name = "Qwen 2.5 (0.5B Instruct - Int4)",
            description = "Ultra-fast & lightweight. Perfect for bank SMS parsing with low RAM usage (~350MB).",
            sizeText = "345 MB",
            fileName = "qwen2.5-0.5b-it-gpu-int4.bin",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/qwen2.5-0.5b-it-gpu-int4.bin"
        ),
        DownloadableModel(
            id = "gemma_2b",
            name = "Gemma 2B (IT - Int4 MediaPipe)",
            description = "Official Google open-weights SLM with strong financial reasoning (~1.3GB).",
            sizeText = "1.35 GB",
            fileName = "gemma-2b-it-cpu-int4.bin",
            downloadUrl = "https://huggingface.co/google/gemma-2b-it-cpu-int4/resolve/main/gemma-2b-it-cpu-int4.bin"
        ),
        DownloadableModel(
            id = "smollm_360m",
            name = "SmolLM (360M Instruct - Int4)",
            description = "Super small footprint for fast on-device categorization (~190MB).",
            sizeText = "190 MB",
            fileName = "smollm-360m-instruct-int4.bin",
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM-360M-Instruct/resolve/main/smollm-360m-instruct-int4.bin"
        )
    )

    fun isModelDownloaded(model: DownloadableModel): Boolean {
        val targetFile = File(mediaPipeLlmEngine.persistentDownloadsDir, model.fileName)
        return targetFile.exists() && targetFile.length() > 1024 * 1024
    }

    suspend fun downloadModel(model: DownloadableModel): Result<File> = withContext(Dispatchers.IO) {
        val targetDir = mediaPipeLlmEngine.persistentDownloadsDir
        val targetFile = File(targetDir, model.fileName)
        val tempFile = File(targetDir, "${model.fileName}.downloading")

        _downloadProgress.value = DownloadProgress(
            modelId = model.id,
            isDownloading = true,
            progressPercent = 0f,
            bytesDownloaded = 0,
            totalBytes = 0
        )

        try {
            var url = URL(model.downloadUrl)
            var connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15000
            connection.readTimeout = 30000

            var responseCode = connection.responseCode
            // Follow up to 3 redirects (e.g. huggingface resolve -> cdn)
            var redirectCount = 0
            while ((responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == 307 || responseCode == 308) && redirectCount < 3) {
                val newUrl = connection.getHeaderField("Location")
                url = URL(newUrl)
                connection = url.openConnection() as HttpURLConnection
                responseCode = connection.responseCode
                redirectCount++
            }

            if (responseCode !in 200..299) {
                throw IllegalStateException("Server returned HTTP $responseCode: ${connection.responseMessage}")
            }

            val contentLength = connection.contentLengthLong

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead: Long = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val percent = if (contentLength > 0) {
                            (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        _downloadProgress.value = DownloadProgress(
                            modelId = model.id,
                            isDownloading = true,
                            progressPercent = percent,
                            bytesDownloaded = totalRead,
                            totalBytes = contentLength
                        )
                    }
                }
            }

            // Rename temp to final
            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)

            _downloadProgress.value = DownloadProgress(
                modelId = model.id,
                isDownloading = false,
                progressPercent = 1f,
                bytesDownloaded = targetFile.length(),
                totalBytes = targetFile.length()
            )

            Timber.d("Model downloaded successfully to: ${targetFile.absolutePath}")
            Result.success(targetFile)
        } catch (e: Throwable) {
            Timber.e(e, "Model download failed for: ${model.name}")
            if (tempFile.exists()) tempFile.delete()
            _downloadProgress.value = DownloadProgress(
                modelId = model.id,
                isDownloading = false,
                errorMessage = e.message ?: "Download failed"
            )
            Result.failure(e)
        }
    }
}
