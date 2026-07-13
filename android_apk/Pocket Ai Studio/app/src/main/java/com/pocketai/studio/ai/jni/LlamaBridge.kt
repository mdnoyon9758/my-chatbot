package com.pocketai.studio.ai.jni

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * JNI bridge to llama.cpp native library.
 * Uses separate locks for inference vs control operations to prevent deadlock.
 */
object LlamaBridge {

    @Volatile private var nativeLoaded = false
    @Volatile private var nativeContextPtr: Long = 0
    private val contextLock = ReentrantLock()
    // Separate lock for stop — does NOT conflict with inference lock
    private val stopLock = ReentrantLock()

    init {
        try {
            System.loadLibrary("llama_jni")
            nativeLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            nativeLoaded = false
        }
    }

    fun isNativeAvailable(): Boolean = nativeLoaded

    private external fun nativeCreateContext(modelPath: String, contextSize: Int, threads: Int, useGpu: Boolean): Long
    private external fun nativeEvaluate(contextPtr: Long, prompt: String, maxTokens: Int, temperature: Float, topP: Float): String
    private external fun nativeEvaluateStream(contextPtr: Long, prompt: String, maxTokens: Int, temperature: Float, topP: Float): Array<String>
    private external fun nativeStopEvaluate(contextPtr: Long)
    private external fun nativeFreeContext(contextPtr: Long)
    private external fun nativeGetTokenCount(contextPtr: Long): Int

    fun createContext(modelPath: String, contextSize: Int = 4096, threads: Int = 4, useGpu: Boolean = false): Result<Unit> = contextLock.withLock {
        try {
            if (!nativeLoaded) return@withLock Result.failure(Exception("Native library not loaded"))
            if (modelPath.isBlank()) return@withLock Result.failure(Exception("Model path is empty"))
            freeContextInternal()
            nativeContextPtr = nativeCreateContext(modelPath, contextSize, threads, useGpu)
            if (nativeContextPtr == 0L) Result.failure(Exception("Failed to create inference context"))
            else Result.success(Unit)
        } catch (e: Error) { Result.failure(Exception("Native error: ${e.message}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    fun evaluate(prompt: String, maxTokens: Int = 2048, temperature: Float = 0.7f, topP: Float = 0.9f): Result<String> {
        val ptr = nativeContextPtr
        if (ptr == 0L) return Result.failure(Exception("No active model context"))
        return try {
            val result = nativeEvaluate(ptr, prompt, maxTokens, temperature, topP)
            Result.success(result)
        } catch (e: Error) { Result.failure(Exception("Native error: ${e.message}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    fun evaluateStream(prompt: String, maxTokens: Int = 2048, temperature: Float = 0.7f, topP: Float = 0.9f): Result<List<String>> {
        val ptr = nativeContextPtr
        if (ptr == 0L) return Result.failure(Exception("No active model context"))
        return try {
            val tokens = nativeEvaluateStream(ptr, prompt, maxTokens, temperature, topP)
            Result.success(tokens.toList())
        } catch (e: Error) { Result.failure(Exception("Native error: ${e.message}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    fun stopEvaluate() {
        // Use separate lock — does NOT block on inference lock
        val ptr = nativeContextPtr
        if (ptr != 0L) {
            try { nativeStopEvaluate(ptr) } catch (_: Exception) { }
        }
    }

    fun freeContext() = contextLock.withLock { freeContextInternal() }

    private fun freeContextInternal() {
        if (nativeContextPtr != 0L) {
            try { nativeFreeContext(nativeContextPtr) } catch (_: Exception) { }
            nativeContextPtr = 0
        }
    }

    fun getTokenCount(): Int {
        val ptr = nativeContextPtr
        return if (ptr != 0L) {
            try { nativeGetTokenCount(ptr) } catch (_: Exception) { 0 }
        } else 0
    }

    fun isContextActive(): Boolean = nativeContextPtr != 0L
}
