package com.pocketai.studio.ai.engine

import android.content.Context
import com.pocketai.studio.ai.jni.LlamaBridge
import com.pocketai.studio.domain.model.InferenceConfig
import com.pocketai.studio.domain.model.PerformanceMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile private var isLoaded = false
    @Volatile private var currentModelPath: String? = null
    @Volatile private var currentModelName: String? = null
    @Volatile private var _tokenCount: Int = 0
    @Volatile private var stopRequested = false
    private val inferenceMutex = Mutex()

    val tokenCount: Int get() = _tokenCount

    fun isModelLoaded(): Boolean = isLoaded
    fun getCurrentModelPath(): String? = currentModelPath
    fun getCurrentModelName(): String? = currentModelName
    fun isNativeAvailable(): Boolean = LlamaBridge.isNativeAvailable()

    suspend fun loadModel(modelPath: String, config: InferenceConfig = InferenceConfig()): Result<Unit> {
        return try {
            if (modelPath.isBlank()) {
                return Result.failure(ModelException("Model path is empty", ModelError.FILE_NOT_FOUND))
            }
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                return Result.failure(ModelException("Model file not found: $modelPath", ModelError.FILE_NOT_FOUND))
            }
            if (!modelFile.canRead()) {
                return Result.failure(ModelException("Cannot read model file", ModelError.PERMISSION_DENIED))
            }
            if (modelFile.length() == 0L) {
                return Result.failure(ModelException("Model file is empty", ModelError.CORRUPTED_MODEL))
            }

            // Wait for any ongoing inference to finish before unloading
            if (inferenceMutex.isLocked) {
                inferenceMutex.lock()
                inferenceMutex.unlock()
            }

            unloadModelInternal()

            if (LlamaBridge.isNativeAvailable()) {
                val result = LlamaBridge.createContext(
                    modelPath = modelPath,
                    contextSize = config.contextSize,
                    threads = config.threads,
                    useGpu = config.useGpu
                )
                result.getOrThrow()
            } else {
                return Result.failure(ModelException("Native library not available", ModelError.NATIVE_ERROR))
            }

            currentModelPath = modelPath
            currentModelName = File(modelPath).nameWithoutExtension
            isLoaded = true
            _tokenCount = 0
            Result.success(Unit)
        } catch (e: UnsatisfiedLinkError) {
            Result.failure(ModelException("Native library not available", ModelError.NATIVE_ERROR))
        } catch (e: OutOfMemoryError) {
            unloadModelInternal()
            Result.failure(ModelException("Device does not have enough RAM", ModelError.OUT_OF_MEMORY))
        } catch (e: Exception) {
            unloadModelInternal()
            Result.failure(ModelException(e.message ?: "Unknown error", ModelError.LOAD_FAILED))
        }
    }

    fun generate(
        prompt: String,
        systemPrompt: String = "",
        config: InferenceConfig = InferenceConfig()
    ): Flow<String> = flow {
        if (!isLoaded || currentModelPath == null) {
            emit("[Error: No model loaded. Please select a model first.]")
            return@flow
        }

        // Try to acquire mutex — if already locked, another generation is in progress
        val locked = inferenceMutex.tryLock()
        if (!locked) {
            emit("[Error: Already generating. Please wait or stop the current generation.]")
            return@flow
        }

        try {
            _tokenCount = 0
            stopRequested = false

            // Check state again after acquiring lock
            if (!isLoaded || currentModelPath == null) {
                emit("[Error: Model was unloaded.]")
                return@flow
            }

            val fullPrompt = buildPrompt(prompt, systemPrompt)
            val effectiveConfig = applyPerformanceMode(config)

            if (LlamaBridge.isNativeAvailable() && LlamaBridge.isContextActive()) {
                val tokensResult = LlamaBridge.evaluateStream(
                    prompt = fullPrompt,
                    maxTokens = effectiveConfig.maxTokens,
                    temperature = effectiveConfig.temperature,
                    topP = effectiveConfig.topP
                )
                if (tokensResult.isSuccess) {
                    for (token in tokensResult.getOrThrow()) {
                        if (stopRequested) break
                        emit(token)
                        _tokenCount++
                    }
                } else {
                    emit("[Error: ${tokensResult.exceptionOrNull()?.message}]")
                }
            } else {
                emit("[Error: Native context not available. Please reload the model.]")
            }
        } catch (e: Exception) {
            emit("[Error: ${e.message}]")
        } finally {
            inferenceMutex.unlock()
        }
    }.flowOn(Dispatchers.IO)

    private fun applyPerformanceMode(config: InferenceConfig): InferenceConfig {
        return when (config.performanceMode) {
            PerformanceMode.FAST -> config.copy(temperature = 0.9f, topP = 0.95f, maxTokens = 1024)
            PerformanceMode.BALANCED -> config
            PerformanceMode.HIGH_QUALITY -> config.copy(temperature = 0.3f, topP = 0.85f, maxTokens = 4096)
        }
    }

    fun stopGeneration() {
        stopRequested = true
        if (LlamaBridge.isNativeAvailable()) {
            LlamaBridge.stopEvaluate()
        }
    }

    fun unloadModel() {
        stopGeneration()
        unloadModelInternal()
    }

    private fun unloadModelInternal() {
        if (LlamaBridge.isNativeAvailable()) {
            LlamaBridge.freeContext()
        }
        isLoaded = false
        currentModelPath = null
        currentModelName = null
        _tokenCount = 0
    }

    private fun buildPrompt(userPrompt: String, systemPrompt: String): String {
        return buildString {
            if (systemPrompt.isNotBlank()) {
                append("<|system|>\n$systemPrompt\n</s>\n")
            }
            append("<|user|>\n$userPrompt\n</s>\n<|assistant|>\n")
        }
    }
}

enum class ModelError {
    FILE_NOT_FOUND, PERMISSION_DENIED, CORRUPTED_MODEL,
    OUT_OF_MEMORY, NATIVE_ERROR, LOAD_FAILED, UNSUPPORTED_FORMAT
}

class ModelException(message: String, val error: ModelError) : Exception(message)
