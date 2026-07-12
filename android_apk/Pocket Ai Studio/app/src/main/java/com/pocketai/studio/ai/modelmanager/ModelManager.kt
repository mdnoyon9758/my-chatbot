package com.pocketai.studio.ai.modelmanager

import android.content.Context
import com.pocketai.studio.domain.model.AiModel
import com.pocketai.studio.domain.model.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val modelsDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _downloadStatus = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStatus: StateFlow<Map<String, DownloadStatus>> = _downloadStatus.asStateFlow()

    private var activeDownloads = mutableMapOf<String, Job>()

    suspend fun getInstalledModels(): List<AiModel> = withContext(Dispatchers.IO) {
        val dir = modelsDir
        if (!dir.exists()) return@withContext emptyList()

        dir.listFiles { file -> file.extension == "gguf" }
            ?.map { file -> fileToModel(file) }
            ?.toList() ?: emptyList()
    }

    fun getAvailableModels(): List<AiModel> = listOf(
        model(
            id = "llama-3.2-1b",
            name = "Llama 3.2 1B",
            dev = "Meta",
            desc = "Fast and efficient for everyday tasks",
            sizeMb = 790,
            ram = "2 GB",
            device = "Most phones",
            url = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            file = "Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        ),
        model(
            id = "gemma-2-2b",
            name = "Gemma 2 2B",
            dev = "Google",
            desc = "Great balance of quality and speed",
            sizeMb = 1500,
            ram = "4 GB",
            device = "Most phones",
            url = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            file = "gemma-2-2b-it-Q4_K_M.gguf"
        ),
        model(
            id = "phi-3-mini",
            name = "Phi-3 Mini",
            dev = "Microsoft",
            desc = "Excellent for mobile devices",
            sizeMb = 2400,
            ram = "4 GB",
            device = "High-end phones",
            url = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
            file = "Phi-3-mini-4k-instruct-q4.gguf"
        ),
        model(
            id = "mistral-7b",
            name = "Mistral 7B",
            dev = "Mistral AI",
            desc = "Powerful model for complex tasks",
            sizeMb = 4100,
            ram = "8 GB",
            device = "Flagship phones/tablets",
            url = "https://huggingface.co/bartowski/Mistral-7B-Instruct-v0.3-GGUF/resolve/main/Mistral-7B-Instruct-v0.3-Q4_K_M.gguf",
            file = "Mistral-7B-Instruct-v0.3-Q4_K_M.gguf"
        )
    )

    fun downloadModel(model: AiModel): Job {
        val existing = activeDownloads[model.id]
        if (existing != null && existing.isActive) return existing

        val job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                _downloadStatus.update(model.id, DownloadStatus.DOWNLOADING)
                _downloadProgress.update(model.id, 0f)

                val targetFile = File(modelsDir, model.filename)
                if (targetFile.exists()) {
                    _downloadStatus.update(model.id, DownloadStatus.COMPLETED)
                    _downloadProgress.update(model.id, 1f)
                    return@launch
                }

                val request = Request.Builder().url(model.downloadUrl).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    _downloadStatus.update(model.id, DownloadStatus.FAILED)
                    return@launch
                }

                val body = response.body ?: run {
                    _downloadStatus.update(model.id, DownloadStatus.FAILED)
                    return@launch
                }

                val contentLength = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(targetFile)
                val buffer = ByteArray(8192)
                var bytesRead: Long = 0
                var read: Int

                while (inputStream.read(buffer).also { read = it } != -1) {
                    if (!isActive) {
                        outputStream.close()
                        inputStream.close()
                        targetFile.delete()
                        _downloadStatus.update(model.id, DownloadStatus.FAILED)
                        return@launch
                    }
                    outputStream.write(buffer, 0, read)
                    bytesRead += read
                    if (contentLength > 0) {
                        val progress = bytesRead.toFloat() / contentLength
                        _downloadProgress.update(model.id, progress)
                    }
                }

                outputStream.close()
                inputStream.close()

                _downloadStatus.update(model.id, DownloadStatus.VERIFYING)
                if (targetFile.exists() && targetFile.length() > 0) {
                    _downloadStatus.update(model.id, DownloadStatus.COMPLETED)
                    _downloadProgress.update(model.id, 1f)
                } else {
                    targetFile.delete()
                    _downloadStatus.update(model.id, DownloadStatus.FAILED)
                }
            } catch (e: CancellationException) {
                _downloadStatus.update(model.id, DownloadStatus.FAILED)
            } catch (e: Exception) {
                _downloadStatus.update(model.id, DownloadStatus.FAILED)
            } finally {
                activeDownloads.remove(model.id)
            }
        }

        activeDownloads[model.id] = job
        return job
    }

    fun cancelDownload(modelId: String) {
        activeDownloads[modelId]?.cancel()
        activeDownloads.remove(modelId)
        _downloadStatus.update(modelId, DownloadStatus.FAILED)
    }

    suspend fun deleteModel(model: AiModel): Boolean = withContext(Dispatchers.IO) {
        val file = model.filePath?.let { File(it) }
        file?.delete() ?: false
    }

    suspend fun deleteAllModels(): Int = withContext(Dispatchers.IO) {
        val dir = modelsDir
        if (!dir.exists()) return@withContext 0
        val files = dir.listFiles { f -> f.extension == "gguf" } ?: return@withContext 0
        var count = 0
        for (file in files) {
            if (file.delete()) count++
        }
        count
    }

    suspend fun importModel(sourcePath: String): Result<AiModel> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("File not found"))
            }
            if (sourceFile.extension.lowercase() != "gguf") {
                return@withContext Result.failure(Exception("Unsupported format. Only GGUF files are supported."))
            }
            val targetFile = File(modelsDir, sourceFile.name)
            if (targetFile.exists()) {
                return@withContext Result.failure(Exception("A model with this name already exists"))
            }
            sourceFile.copyTo(targetFile, overwrite = false)
            val model = fileToModel(targetFile)
            Result.success(model)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getModelsDirectory(): File = modelsDir

    fun getStorageInfo(): Pair<Long, Long> {
        val dir = modelsDir
        val total = dir.totalSpace
        val free = dir.freeSpace
        return Pair(total, free)
    }

    fun getModelsSize(): Long {
        val dir = modelsDir
        if (!dir.exists()) return 0
        return dir.walkTopDown()
            .filter { it.isFile && it.extension == "gguf" }
            .sumOf { it.length() }
    }

    private fun fileToModel(file: File): AiModel {
        val name = file.nameWithoutExtension
        return AiModel(
            id = name,
            name = formatModelName(name),
            developer = extractDeveloper(name),
            description = "Local GGUF model",
            fileSizeBytes = file.length(),
            fileSizeFormatted = formatSize(file.length()),
            requiredRam = estimateRam(file.length()),
            filename = file.name,
            isDownloaded = true,
            filePath = file.absolutePath
        )
    }

    private fun model(
        id: String, name: String, dev: String, desc: String,
        sizeMb: Long, ram: String, device: String, url: String, file: String
    ) = AiModel(
        id = id, name = name, developer = dev, description = desc,
        quantization = "Q4_K_M",
        fileSizeBytes = sizeMb * 1024 * 1024,
        fileSizeFormatted = "${sizeMb} MB",
        requiredRam = ram, recommendedDevice = device,
        downloadUrl = url, filename = file
    )

    private fun formatModelName(raw: String): String {
        return raw.replace("-", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private fun extractDeveloper(name: String): String = when {
        name.contains("llama", true) -> "Meta"
        name.contains("gemma", true) -> "Google"
        name.contains("phi", true) -> "Microsoft"
        name.contains("mistral", true) -> "Mistral AI"
        name.contains("qwen", true) -> "Alibaba"
        name.contains("deepseek", true) -> "DeepSeek"
        else -> "Community"
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "%.1f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
    }

    private fun estimateRam(fileSize: Long): String {
        val gb = fileSize.toDouble() / (1024 * 1024 * 1024)
        return when {
            gb < 1 -> "2 GB"
            gb < 2 -> "4 GB"
            gb < 4 -> "6 GB"
            else -> "8 GB"
        }
    }

    private fun <K, V> MutableStateFlow<Map<K, V>>.update(key: K, value: V) {
        this.value = this.value + (key to value)
    }
}