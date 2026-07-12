# Pocket AI Studio

A fully offline Android AI assistant that runs GGUF language models locally on-device via llama.cpp. No internet required for inference.

## Features

- **AI Chat** — Real-time on-device inference with streaming tokens, stop generation, multiple chat sessions
- **Model Manager** — Download GGUF models from HuggingFace (Llama 3.2, Gemma 2, Phi-3, Mistral), load/unload/delete
- **Text Tools** — 7 AI text transforms: Summarize, Rewrite, Grammar, Translate, Expand, Shorten, Bullet Points
- **OCR** — Extract text from images via ML Kit (offline)
- **PDF Assistant** — Import PDFs and ask questions about their content
- **Settings** — Theme (light/dark), font size, context size (512-16384), threads (1-16), GPU toggle, performance mode

## Project Structure

```
my-chatbot/
├── README.md                          # This file
├── releases/                          # Final APK output (all versions stored here)
│   └── PocketAiStudio-v1.0.0-debug.apk
├── android_apk/
│   ├── Pocket Ai Studio/              # Android project source
│   │   ├── app/
│   │   │   ├── build.gradle.kts       # App build config (plugins, deps, native build)
│   │   │   ├── src/main/
│   │   │   │   ├── cpp/               # Native C++ code
│   │   │   │   │   ├── CMakeLists.txt # CMake build config
│   │   │   │   │   ├── llama_jni.cpp  # JNI bridge (6 native methods)
│   │   │   │   │   └── llama.cpp/     # llama.cpp source (cloned, compiled by Gradle)
│   │   │   │   ├── java/              # Kotlin source (36 files)
│   │   │   │   ├── res/               # Android resources
│   │   │   │   └── AndroidManifest.xml
│   │   │   └── build/                 # Build intermediates (auto-generated)
│   │   │       └── outputs/apk/debug/ # Debug APK output
│   │   ├── build.gradle.kts           # Root build config
│   │   ├── settings.gradle.kts        # Project settings
│   │   ├── local.properties           # SDK path
│   │   ├── gradle/                    # Gradle wrapper
│   │   └── gradlew                    # Gradle wrapper script
│   ├── build_cache/                   # Pre-compiled native libraries (reusable)
│   │   ├── arm64-v8a/                 # ARM 64-bit native libs
│   │   └── x86_64/                    # x86 64-bit native libs
│   └── setup_android_cli.sh           # SDK setup script
└── .mimocode/                         # MiMoCode session data
```

## Build Cache

The `android_apk/build_cache/` folder contains pre-compiled native libraries from llama.cpp. These are saved after each successful build to avoid recompilation (which takes ~6 minutes).

### Cached Libraries

| Library | Purpose | arm64-v8a | x86_64 |
|---|---|---|---|
| `libllama.so` | llama.cpp inference engine + JNI bridge | 3.3 MB | 3.3 MB |
| `libggml-base.so` | Tensor computation base | 1.2 MB | 1.2 MB |
| `libggml-cpu.so` | CPU backend | 784 KB | 4.2 MB |
| `libggml.so` | Backend loader | 123 KB | 584 KB |
| `libomp.so` | OpenMP threading | 939 KB | 1.2 MB |

### How Build Cache Works

1. **First build** — Gradle compiles llama.cpp from source via CMake (~6 min). Compiled `.so` files are cached in `app/.cxx/`.
2. **Subsequent builds** — Gradle detects no source changes and skips CMake compilation. Kotlin code compiles in ~1 minute.
3. **Cache backup** — The `build_cache/` folder holds copies of the compiled `.so` files as a backup. If `.cxx/` is deleted, you can restore from here.
4. **Clean rebuild** — Run `./gradlew clean` then `assembleDebug` to force full recompilation.

## Building

### Prerequisites
- JDK 17 (installed via sdkman)
- Android SDK at `/home/codespace/android-sdk/` (API 35, NDK 27, CMake 3.22.1)
- Gradle 8.9 (bundled via wrapper)

### Build Command

```bash
cd android_apk/Pocket\ Ai\ Studio

# Set environment
export ANDROID_HOME=/home/codespace/android-sdk
export JAVA_HOME=/usr/local/sdkman/candidates/java/17.0.13-ms

# Build debug APK
./gradlew assembleDebug --no-daemon

# Build release APK (requires signing key)
./gradlew assembleRelease --no-daemon
```

### Output Locations

| Output | Location |
|---|---|
| Debug APK | `android_apk/Pocket Ai Studio/app/build/outputs/apk/debug/app-debug.apk` |
| Release APK | `android_apk/Pocket Ai Studio/app/build/outputs/apk/release/app-release.apk` |
| Final APK (all versions) | `releases/PocketAiStudio-v{VERSION}-{BUILD_TYPE}.apk` |

### Copy APK to Releases

```bash
cp app/build/outputs/apk/debug/app-debug.apk \
   ../../releases/PocketAiStudio-v1.0.0-debug.apk
```

## Supported ABIs

| ABI | Status | Device Type |
|---|---|---|
| `arm64-v8a` | Supported | Modern phones/tablets (2016+) |
| `x86_64` | Supported | Emulators, Chromebooks |
| `armeabi-v7a` | Not supported | Older 32-bit ARM devices |

## Architecture

- **Clean Architecture**: Domain (pure Kotlin) → Data (Room + DataStore) → UI (Compose + MVVM)
- **AI Stack**: `UI → AiEngine → LlamaBridge (JNI) → libllama.so (llama.cpp native)`
- **Dependencies**: Hilt (DI), Room (database), DataStore (settings), Compose (UI)
- **Native Build**: llama.cpp compiled via Gradle CMake integration, no pre-built binaries

## Key Files

| File | Purpose |
|---|---|
| `app/src/main/cpp/llama_jni.cpp` | JNI bridge — implements 6 native methods for LlamaBridge.kt |
| `app/src/main/cpp/CMakeLists.txt` | CMake config — builds llama.cpp + JNI wrapper into libllama.so |
| `app/src/main/java/.../ai/jni/LlamaBridge.kt` | Kotlin JNI declarations |
| `app/src/main/java/.../ai/engine/AiEngine.kt` | AI inference engine (model loading, text generation) |
| `app/src/main/java/.../ai/modelmanager/ModelManager.kt` | Model file lifecycle management |
| `app/build.gradle.kts` | Build config (dependencies, cmake args, ABI filters) |
