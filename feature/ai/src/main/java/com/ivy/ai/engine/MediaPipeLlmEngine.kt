package com.ivy.ai.engine

import android.content.Context
import android.os.Environment
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.ivy.ai.model.BankParsedTransaction
import com.ivy.base.model.TransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPipeLlmEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var llmInference: LlmInference? = null
    private var activeModelPath: String? = null

    // 1. Persistent Shared Storage (Survives app uninstalls & reinstalls)
    val persistentDownloadsDir: File
        get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IvyWallet/models").apply {
            if (!exists()) mkdirs()
        }

    // 2. Persistent Documents Storage
    val persistentDocumentsDir: File
        get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "IvyWallet/models").apply {
            if (!exists()) mkdirs()
        }

    // 3. External App-specific Storage (survives updates)
    val externalAppDir: File
        get() = (context.getExternalFilesDir("models") ?: File(context.filesDir, "models")).apply {
            if (!exists()) mkdirs()
        }

    // 4. Internal App Storage fallback
    val internalAppDir: File
        get() = File(context.filesDir, "models").apply {
            if (!exists()) mkdirs()
        }

    /**
     * Checks all safe persistent storage locations for SLM model files (.bin, .task, .gguf)
     */
    fun findAvailableModels(): List<File> {
        val modelDirectories = listOf(
            persistentDownloadsDir,
            persistentDocumentsDir,
            externalAppDir,
            internalAppDir
        )

        val foundFiles = mutableListOf<File>()
        for (dir in modelDirectories) {
            val files = dir.listFiles { file ->
                file.isFile && (file.name.endsWith(".bin") || file.name.endsWith(".task") || file.name.endsWith(".gguf"))
            }
            if (files != null) {
                foundFiles.addAll(files)
            }
        }
        return foundFiles.distinctBy { it.absolutePath }
    }

    fun isModelLoaded(): Boolean = llmInference != null

    fun getLoadedModelName(): String? {
        return activeModelPath?.let { File(it).name }
    }

    fun getActiveModelPath(): String? = activeModelPath

    /**
     * Initializes Google MediaPipe LLM Inference with on-device model weights
     */
    suspend fun loadModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Model file does not exist at: $modelPath"))
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .setTemperature(0.2f)
                .setTopK(40)
                .build()

            llmInference?.close()
            llmInference = LlmInference.createFromOptions(context, options)
            activeModelPath = modelPath
            Timber.d("MediaPipe LLM loaded successfully from: $modelPath")
            Result.success(Unit)
        } catch (e: Throwable) {
            Timber.e(e, "Failed to load MediaPipe LLM model")
            Result.failure(e)
        }
    }

    /**
     * Executes on-device LLM generation for the given prompt
     */
    suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.Default) {
        val inference = llmInference
            ?: return@withContext Result.failure(IllegalStateException("No SLM model is loaded in memory."))

        try {
            val response = inference.generateResponse(prompt)
            Result.success(response)
        } catch (e: Throwable) {
            Timber.e(e, "Error generating response from on-device SLM")
            Result.failure(e)
        }
    }

    /**
     * Parses raw LLM JSON response into structured BankParsedTransaction
     */
    fun parseLlmJsonOutput(llmOutput: String, rawInputMessage: String): BankParsedTransaction? {
        return try {
            val cleanedJson = llmOutput
                .replace(Regex("""```json\s*"""), "")
                .replace(Regex("""```\s*"""), "")
                .trim()

            val startIdx = cleanedJson.indexOf('{')
            val endIdx = cleanedJson.lastIndexOf('}')
            if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) return null

            val jsonObject = JSONObject(cleanedJson.substring(startIdx, endIdx + 1))
            val amount = jsonObject.optDouble("amount", 0.0)
            val currency = jsonObject.optString("currency", "USD")
            val typeStr = jsonObject.optString("type", "EXPENSE").uppercase()
            val type = if (typeStr == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
            val merchant = jsonObject.optString("merchant", "Bank Transaction")
            val category = jsonObject.optString("category", "General")
            val account = jsonObject.optString("account", null)

            BankParsedTransaction(
                amount = amount,
                currency = currency,
                type = type,
                merchant = merchant,
                categoryName = category,
                accountIdentifier = account,
                rawText = rawInputMessage,
                confidence = 0.98f
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse LLM JSON output: $llmOutput")
            null
        }
    }

    fun release() {
        try {
            llmInference?.close()
            llmInference = null
            activeModelPath = null
        } catch (e: Exception) {
            Timber.e(e, "Error releasing LLM inference")
        }
    }
}
