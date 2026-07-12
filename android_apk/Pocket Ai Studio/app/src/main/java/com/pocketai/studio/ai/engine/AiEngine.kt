package com.pocketai.studio.ai.engine

import android.content.Context
import com.pocketai.studio.ai.jni.LlamaBridge
import com.pocketai.studio.domain.model.InferenceConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var isLoaded = false
    private var currentModelPath: String? = null
    private var currentModelName: String? = null
    private var generationJob: Job? = null
    private var _tokenCount: Int = 0

    val tokenCount: Int get() = _tokenCount

    fun isModelLoaded(): Boolean = isLoaded
    fun getCurrentModelPath(): String? = currentModelPath
    fun getCurrentModelName(): String? = currentModelName
    fun isNativeAvailable(): Boolean = LlamaBridge.isNativeAvailable()

    suspend fun loadModel(modelPath: String, config: InferenceConfig = InferenceConfig()): Result<Unit> {
        return try {
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                return Result.failure(ModelException("Model file not found", ModelError.FILE_NOT_FOUND))
            }
            if (!modelFile.canRead()) {
                return Result.failure(ModelException("Cannot read model file", ModelError.PERMISSION_DENIED))
            }
            if (modelFile.length() == 0L) {
                return Result.failure(ModelException("Model file is empty", ModelError.CORRUPTED_MODEL))
            }

            unloadModel()

            if (LlamaBridge.isNativeAvailable()) {
                val result = LlamaBridge.createContext(
                    modelPath = modelPath,
                    contextSize = config.contextSize,
                    threads = config.threads,
                    useGpu = config.useGpu
                )
                result.getOrThrow()
            }

            currentModelPath = modelPath
            currentModelName = File(modelPath).nameWithoutExtension
            isLoaded = true
            _tokenCount = 0
            Result.success(Unit)
        } catch (e: UnsatisfiedLinkError) {
            Result.failure(ModelException("Native library not available", ModelError.NATIVE_ERROR))
        } catch (e: OutOfMemoryError) {
            unloadModel()
            Result.failure(ModelException("Device does not have enough RAM", ModelError.OUT_OF_MEMORY))
        } catch (e: Exception) {
            unloadModel()
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

        _tokenCount = 0
        val fullPrompt = buildPrompt(prompt, systemPrompt)

        if (LlamaBridge.isNativeAvailable() && LlamaBridge.isContextActive()) {
            val tokensResult = LlamaBridge.evaluateStream(
                prompt = fullPrompt,
                maxTokens = config.maxTokens,
                temperature = config.temperature,
                topP = config.topP
            )
            if (tokensResult.isSuccess) {
                for (token in tokensResult.getOrThrow()) {
                    emit(token)
                    _tokenCount++
                }
            } else {
                emit("[Error: ${tokensResult.exceptionOrNull()?.message}]")
            }
        } else {
            emit("[AI Engine: Native library not available. " +
                 "Please ensure llama.cpp is compiled for your device architecture.]")
        }
    }.flowOn(Dispatchers.IO)

    fun stopGeneration() {
        generationJob?.cancel()
        if (LlamaBridge.isNativeAvailable()) {
            LlamaBridge.stopEvaluate()
        }
    }

    fun unloadModel() {
        stopGeneration()
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

class ModelException(
    message: String,
    val error: ModelError
) : Exception(message)