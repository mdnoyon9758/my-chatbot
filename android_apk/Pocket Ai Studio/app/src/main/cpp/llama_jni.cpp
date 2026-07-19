#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <cstring>
#include <cstdlib>

#include "llama.h"

static inline jstring toJString(JNIEnv *env, const std::string &s) {
    return env->NewStringUTF(s.c_str());
}

static inline std::string fromJString(JNIEnv *env, jstring js) {
    if (!js) return "";
    const char *c = env->GetStringUTFChars(js, nullptr);
    std::string s(c);
    env->ReleaseStringUTFChars(js, c);
    return s;
}

struct InferenceContext {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    llama_sampler *sampler = nullptr;
    std::atomic<bool> stop_requested{false};
    std::atomic<int> token_count{0};
    int n_ctx = 4096;
    int n_threads = 4;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_pocketai_studio_ai_jni_LlamaBridge_nativeCreateContext(
        JNIEnv *env, jobject, jstring modelPath,
        jint contextSize, jint threads, jboolean useGpu) {

    std::string path = fromJString(env, modelPath);
    if (path.empty()) return 0;

    static bool backend_initialized = false;
    if (!backend_initialized) {
        llama_backend_init();
        backend_initialized = true;
    }

    auto *ictx = new InferenceContext();
    ictx->n_ctx = contextSize;
    ictx->n_threads = threads;

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = useGpu ? 99 : 0; // Offload all layers if GPU enabled

    ictx->model = llama_model_load_from_file(path.c_str(), model_params);
    if (!ictx->model) { delete ictx; return 0; }

    ictx->vocab = llama_model_get_vocab(ictx->model);
    if (!ictx->vocab) { llama_model_free(ictx->model); delete ictx; return 0; }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = (uint32_t)contextSize;
    ctx_params.n_batch = 512;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;

    ictx->ctx = llama_init_from_model(ictx->model, ctx_params);
    if (!ictx->ctx) { llama_model_free(ictx->model); delete ictx; return 0; }

    // Create sampler with default params
    auto smpl_params = llama_sampler_chain_default_params();
    ictx->sampler = llama_sampler_chain_init(smpl_params);
    if (ictx->sampler) {
        llama_sampler_chain_add(ictx->sampler, llama_sampler_init_temp(0.7f));
        llama_sampler_chain_add(ictx->sampler, llama_sampler_init_top_p(0.9f, 1));
    }

    return reinterpret_cast<jlong>(ictx);
}

JNIEXPORT jstring JNICALL
Java_com_pocketai_studio_ai_jni_LlamaBridge_nativeEvaluate(
        JNIEnv *env, jobject, jlong contextPtr, jstring prompt,
        jint maxTokens, jfloat temperature, jfloat topP) {

    auto *ictx = reinterpret_cast<InferenceContext *>(contextPtr);
    if (!ictx || !ictx->ctx || !ictx->vocab) {
        return env->NewStringUTF("[Error: invalid context]");
    }

    ictx->stop_requested = false;
    ictx->token_count = 0;

    std::string input = fromJString(env, prompt);
    if (input.empty()) return env->NewStringUTF("[Error: empty prompt]");

    // Tokenize
    int32_t n_tokens = -llama_tokenize(ictx->vocab, input.c_str(), (int32_t)input.size(),
                                        nullptr, 0, true, true);
    if (n_tokens <= 0) return env->NewStringUTF("[Error: failed to tokenize]");

    // Limit to context
    if (n_tokens > ictx->n_ctx - 100) n_tokens = ictx->n_ctx - 100;

    std::vector<llama_token> tokens(n_tokens);
    llama_tokenize(ictx->vocab, input.c_str(), (int32_t)input.size(),
                   tokens.data(), n_tokens, true, true);

    std::string result;

    // Evaluate prompt tokens one by one
    for (int32_t i = 0; i < n_tokens && !ictx->stop_requested; i++) {
        llama_batch batch = llama_batch_init(1, 0, 1);
        if (!batch.token) continue;

        batch.token[0] = tokens[i];
        batch.n_tokens = 1;
        batch.pos[0] = i;
        batch.seq_id[0][0] = 0;
        batch.n_seq_id[0] = 1;
        batch.logits[0] = (i == n_tokens - 1) ? 1 : 0;

        int ret = llama_decode(ictx->ctx, batch);
        llama_batch_free(batch);
        if (ret != 0) return env->NewStringUTF("[Error: decode failed]");
    }

    // Generate response tokens
    for (int g = 0; g < maxTokens && !ictx->stop_requested; g++) {
        llama_token tid = llama_sampler_sample(ictx->sampler, ictx->ctx, -1);
        if (llama_vocab_is_eog(ictx->vocab, tid)) break;

        char buf[256];
        int n = llama_token_to_piece(ictx->vocab, tid, buf, sizeof(buf), 0, true);
        if (n > 0) result.append(buf, n);

        ictx->token_count++;

        // Feed the generated token back
        llama_batch batch = llama_batch_init(1, 0, 1);
        if (!batch.token) break;

        batch.token[0] = tid;
        batch.n_tokens = 1;
        batch.pos[0] = n_tokens + g;
        batch.seq_id[0][0] = 0;
        batch.n_seq_id[0] = 1;
        batch.logits[0] = 1;

        int ret = llama_decode(ictx->ctx, batch);
        llama_batch_free(batch);
        if (ret != 0) break;
    }

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jobjectArray JNICALL
Java_com_pocketai_studio_ai_jni_LlamaBridge_nativeEvaluateStream(
        JNIEnv *env, jobject, jlong contextPtr, jstring prompt,
        jint maxTokens, jfloat temperature, jfloat topP) {

    auto *ictx = reinterpret_cast<InferenceContext *>(contextPtr);
    jclass strCls = env->FindClass("java/lang/String");

    if (!ictx || !ictx->ctx || !ictx->vocab) {
        return env->NewObjectArray(0, strCls, nullptr);
    }

    ictx->stop_requested = false;
    ictx->token_count = 0;

    std::string input = fromJString(env, prompt);
    if (input.empty()) return env->NewObjectArray(0, strCls, nullptr);

    // Tokenize
    int32_t n_tokens = -llama_tokenize(ictx->vocab, input.c_str(), (int32_t)input.size(),
                                        nullptr, 0, true, true);
    if (n_tokens <= 0) return env->NewObjectArray(0, strCls, nullptr);

    // Limit to context
    if (n_tokens > ictx->n_ctx - 100) n_tokens = ictx->n_ctx - 100;

    std::vector<llama_token> tokens(n_tokens);
    llama_tokenize(ictx->vocab, input.c_str(), (int32_t)input.size(),
                   tokens.data(), n_tokens, true, true);

    std::vector<std::string> tokenStrings;

    // Evaluate prompt tokens
    for (int32_t i = 0; i < n_tokens && !ictx->stop_requested; i++) {
        llama_batch batch = llama_batch_init(1, 0, 1);
        if (!batch.token) continue;

        batch.token[0] = tokens[i];
        batch.n_tokens = 1;
        batch.pos[0] = i;
        batch.seq_id[0][0] = 0;
        batch.n_seq_id[0] = 1;
        batch.logits[0] = (i == n_tokens - 1) ? 1 : 0;

        int ret = llama_decode(ictx->ctx, batch);
        llama_batch_free(batch);
        if (ret != 0) return env->NewObjectArray(0, strCls, nullptr);
    }

    // Generate response tokens
    for (int g = 0; g < maxTokens && !ictx->stop_requested; g++) {
        llama_token tid = llama_sampler_sample(ictx->sampler, ictx->ctx, -1);
        if (llama_vocab_is_eog(ictx->vocab, tid)) break;

        char buf[256];
        int n = llama_token_to_piece(ictx->vocab, tid, buf, sizeof(buf), 0, true);
        if (n > 0) tokenStrings.emplace_back(buf, n);

        ictx->token_count++;

        llama_batch batch = llama_batch_init(1, 0, 1);
        if (!batch.token) break;

        batch.token[0] = tid;
        batch.n_tokens = 1;
        batch.pos[0] = n_tokens + g;
        batch.seq_id[0][0] = 0;
        batch.n_seq_id[0] = 1;
        batch.logits[0] = 1;

        int ret = llama_decode(ictx->ctx, batch);
        llama_batch_free(batch);
        if (ret != 0) break;
    }

    // Build result array — delete each local ref to prevent overflow
    jsize count = (jsize)tokenStrings.size();
    jobjectArray arr = env->NewObjectArray(count, strCls, nullptr);
    if (!arr) return env->NewObjectArray(0, strCls, nullptr);

    for (jsize i = 0; i < count; i++) {
        jstring js = env->NewStringUTF(tokenStrings[i].c_str());
        env->SetObjectArrayElement(arr, i, js);
        env->DeleteLocalRef(js);
    }
    return arr;
}

JNIEXPORT void JNICALL
Java_com_pocketai_studio_ai_jni_LlamaBridge_nativeStopEvaluate(
        JNIEnv *, jobject, jlong contextPtr) {
    auto *ictx = reinterpret_cast<InferenceContext *>(contextPtr);
    if (ictx) ictx->stop_requested = true;
}

JNIEXPORT void JNICALL
Java_com_pocketai_studio_ai_jni_LlamaBridge_nativeFreeContext(
        JNIEnv *, jobject, jlong contextPtr) {
    auto *ictx = reinterpret_cast<InferenceContext *>(contextPtr);
    if (ictx) {
        if (ictx->sampler) llama_sampler_free(ictx->sampler);
        if (ictx->ctx) llama_free(ictx->ctx);
        if (ictx->model) llama_model_free(ictx->model);
        delete ictx;
    }
}

JNIEXPORT jint JNICALL
Java_com_pocketai_studio_ai_jni_LlamaBridge_nativeGetTokenCount(
        JNIEnv *, jobject, jlong contextPtr) {
    auto *ictx = reinterpret_cast<InferenceContext *>(contextPtr);
    return ictx ? ictx->token_count.load() : 0;
}

} // extern "C"
