cmake_minimum_required(VERSION 3.22.1)

project("pocketai" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# llama.cpp source (cloned into this directory)
set(LLAMA_SRC ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)

# Our JNI wrapper
add_library(llama_jni SHARED llama_jni.cpp)

target_include_directories(llama_jni PRIVATE
    ${LLAMA_SRC}/include
    ${LLAMA_SRC}/ggml/include
)

target_link_libraries(llama_jni
    llama
    android
    log
)

# Rename output to libllama.so so System.loadLibrary("llama") works
set_target_properties(llama_jni PROPERTIES
    OUTPUT_NAME "llama"
    PREFIX "lib"
)
