#!/bin/bash
# Pocket AI Studio — Build Script
# Builds the APK and copies it to the releases folder.
#
# Usage:
#   ./build.sh              # Build debug APK
#   ./build.sh release      # Build release APK
#   ./build.sh clean        # Clean build (full recompilation)
#   ./build.sh cache        # Show build cache status

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/Pocket Ai Studio"
RELEASES_DIR="$(dirname "$SCRIPT_DIR")/releases"
CACHE_DIR="$SCRIPT_DIR/build_cache"
VERSION="1.0.0"

export ANDROID_HOME=/home/codespace/android-sdk
export JAVA_HOME=/usr/local/sdkman/candidates/java/17.0.13-ms

show_cache() {
    echo "=== Build Cache Status ==="
    echo ""
    if [ -d "$CACHE_DIR" ]; then
        echo "Cache location: $CACHE_DIR"
        echo ""
        for ABI in arm64-v8a x86_64; do
            if [ -d "$CACHE_DIR/$ABI" ]; then
                echo "[$ABI]"
                ls -lh "$CACHE_DIR/$ABI/" 2>/dev/null | grep -v "^total"
                echo ""
            fi
        done
    else
        echo "No build cache found."
    fi
    
    echo "=== Gradle CMake Cache ==="
    CXX_DIR="$PROJECT_DIR/app/.cxx"
    if [ -d "$CXX_DIR" ]; then
        echo "Location: $CXX_DIR"
        du -sh "$CXX_DIR" 2>/dev/null
    else
        echo "No CMake cache found (will compile from source on next build)."
    fi
}

save_cache() {
    echo "Saving build cache..."
    CXX_BUILD="$PROJECT_DIR/app/build/intermediates/cxx/Release"
    
    # Find the build hash directory
    BUILD_HASH=$(ls "$CXX_BUILD" 2>/dev/null | head -1)
    if [ -z "$BUILD_HASH" ]; then
        echo "No CMake build found to cache."
        return
    fi
    
    BUILD_OBJ="$CXX_BUILD/$BUILD_HASH/obj"
    
    for ABI in arm64-v8a x86_64; do
        mkdir -p "$CACHE_DIR/$ABI"
        for lib in libllama.so libggml.so libggml-base.so libggml-cpu.so libomp.so; do
            if [ -f "$BUILD_OBJ/$ABI/$lib" ]; then
                cp "$BUILD_OBJ/$ABI/$lib" "$CACHE_DIR/$ABI/"
                echo "  Cached $ABI/$lib"
            fi
        done
    done
}

copy_apk() {
    local build_type=$1
    local src="$PROJECT_DIR/app/build/outputs/apk/$build_type"
    local filename="PocketAiStudio-v${VERSION}-${build_type}.apk"
    
    mkdir -p "$RELEASES_DIR"
    
    if [ -f "$src/app-${build_type}.apk" ]; then
        cp "$src/app-${build_type}.apk" "$RELEASES_DIR/$filename"
        echo "APK copied to: $RELEASES_DIR/$filename"
        ls -lh "$RELEASES_DIR/$filename"
    else
        echo "ERROR: APK not found at $src"
        exit 1
    fi
}

do_clean() {
    echo "Cleaning build..."
    cd "$PROJECT_DIR"
    ./gradlew clean --no-daemon
    echo "Clean complete."
}

do_build() {
    local build_type=${1:-debug}
    echo "Building $build_type APK..."
    cd "$PROJECT_DIR"
    ./gradlew assemble${build_type^} --no-daemon
    save_cache
    copy_apk "$build_type"
    echo ""
    echo "Build complete!"
}

case "${1:-debug}" in
    debug)
        do_build debug
        ;;
    release)
        do_build release
        ;;
    clean)
        do_clean
        ;;
    cache)
        show_cache
        ;;
    *)
        echo "Usage: $0 [debug|release|clean|cache]"
        exit 1
        ;;
esac
