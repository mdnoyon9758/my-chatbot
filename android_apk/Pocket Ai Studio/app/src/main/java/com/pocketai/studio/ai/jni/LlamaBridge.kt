package com.pocketai.studio.ai.jni

/**
 * JNI bridge to llama.cpp native library.
 * This provides the interface to the compiled llama.cpp shared library
 * for GGUF model inference on Android.
 *
 * The native library (libllama.so) is loaded at runtime.
 * All inference runs on background threads via coroutines.
 */
object LlamaBridge {

    private var nativeLoaded = false
    private var nativeContextPtr: Long = 0

    init {
        try {
            System.loadLibrary("llama")
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
    ): Result<Unit> {
        return try {
            if (!nativeLoaded) {
                return Result.failure(Exception("Native library not loaded"))
            }
            freeContext()
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

    fun evaluate(prompt: String, maxTokens: Int = 2048, temperature: Float = 0.7f, topP: Float = 0.9f): Result<String> {
        return try {
            if (nativeContextPtr == 0L) {
                return Result.failure(Exception("No active model context"))
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
    ): Result<List<String>> {
        return try {
            if (nativeContextPtr == 0L) {
                return Result.failure(Exception("No active model context"))
            }
            val tokens = nativeEvaluateStream(nativeContextPtr, prompt, maxTokens, temperature, topP)
            Result.success(tokens.toList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun stopEvaluate() {
        if (nativeContextPtr != 0L) {
            try {
                nativeStopEvaluate(nativeContextPtr)
            } catch (_: Exception) { }
        }
    }

    fun freeContext() {
        if (nativeContextPtr != 0L) {
            try {
                nativeFreeContext(nativeContextPtr)
            } catch (_: Exception) { }
            nativeContextPtr = 0
        }
    }

    fun getTokenCount(): Int {
        return if (nativeContextPtr != 0L) {
            try { nativeGetTokenCount(nativeContextPtr) } catch (_: Exception) { 0 }
        } else 0
    }

    fun isContextActive(): Boolean = nativeContextPtr != 0L
}