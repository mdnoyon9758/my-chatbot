package com.pocketai.studio.ai.jni

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * JNI bridge to llama.cpp native library.
 * Thread-safe: all native calls are serialized via a ReentrantLock.
 */
object LlamaBridge {

    private var nativeLoaded = false
    private var nativeContextPtr: Long = 0
    private val lock = ReentrantLock()

    init {
        try {
            System.loadLibrary("llama_jni")
            nativeLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            nativeLoaded = false
        }
    }

    fun isNativeAvailable(): Boolean = nativeLoaded

    // Native methods
    private external fun nativeCreateContext(
        modelPath: String,
        contextSize: Int,
        threads: Int,
        useGpu: Boolean
    ): Long

    private external fun nativeEvaluate(
        contextPtr: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float
    ): String

    private external fun nativeEvaluateStream(
        contextPtr: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float
    ): Array<String>

    private external fun nativeStopEvaluate(contextPtr: Long)

    private external fun nativeFreeContext(contextPtr: Long)

    private external fun nativeGetTokenCount(contextPtr: Long): Int

    fun createContext(
        modelPath: String,
        contextSize: Int = 4096,
        threads: Int = 4,
        useGpu: Boolean = false
    ): Result<Unit> = lock.withLock {
        try {
            if (!nativeLoaded) {
                return@withLock Result.failure(Exception("Native library not loaded"))
            }
            freeContextLocked()
            nativeContextPtr = nativeCreateContext(modelPath, contextSize, threads, useGpu)
            if (nativeContextPtr == 0L) {
                Result.failure(Exception("Failed to create inference context"))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun evaluate(prompt: String, maxTokens: Int = 2048, temperature: Float = 0.7f, topP: Float = 0.9f): Result<String> = lock.withLock {
        try {
            if (nativeContextPtr == 0L) {
                return@withLock Result.failure(Exception("No active model context"))
            }
            val result = nativeEvaluate(nativeContextPtr, prompt, maxTokens, temperature, topP)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun evaluateStream(
        prompt: String,
        maxTokens: Int = 2048,
        temperature: Float = 0.7f,
        topP: Float = 0.9f
    ): Result<List<String>> = lock.withLock {
        try {
            if (nativeContextPtr == 0L) {
                return@withLock Result.failure(Exception("No active model context"))
            }
            val tokens = nativeEvaluateStream(nativeContextPtr, prompt, maxTokens, temperature, topP)
            Result.success(tokens.toList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun stopEvaluate() = lock.withLock {
        if (nativeContextPtr != 0L) {
            try {
                nativeStopEvaluate(nativeContextPtr)
            } catch (_: Exception) { }
        }
    }

    fun freeContext() = lock.withLock {
        freeContextLocked()
    }

    private fun freeContextLocked() {
        if (nativeContextPtr != 0L) {
            try {
                nativeFreeContext(nativeContextPtr)
            } catch (_: Exception) { }
            nativeContextPtr = 0
        }
    }

    fun getTokenCount(): Int = lock.withLock {
        if (nativeContextPtr != 0L) {
            try { nativeGetTokenCount(nativeContextPtr) } catch (_: Exception) { 0 }
        } else 0
    }

    fun isContextActive(): Boolean = lock.withLock { nativeContextPtr != 0L }
}
