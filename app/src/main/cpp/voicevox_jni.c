// JNI glue between the Android TextToSpeechService and the VOICEVOX C API.
// Everything the core hands back must be released with its own free functions.

#include <jni.h>
#include <android/log.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define VOICEVOX_LOAD_ONNXRUNTIME
#include "voicevox_core.h"

#define TAG "VoicevoxJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static const struct VoicevoxOnnxruntime *g_ort = NULL;
static struct OpenJtalkRc *g_ojt = NULL;
static struct VoicevoxSynthesizer *g_syn = NULL;

/*
 * Serialises every call into the core, loads included. Java therefore gains nothing by
 * calling one of these off its own locks: a synthesis still waits for a model load here.
 */
static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;

/* For file paths only: they live under the app's files dir and are plain ASCII. */
static char *dup_jstring(JNIEnv *env, jstring value) {
    if (value == NULL) return NULL;
    const char *chars = (*env)->GetStringUTFChars(env, value, NULL);
    if (chars == NULL) return NULL;
    char *copy = strdup(chars);
    (*env)->ReleaseStringUTFChars(env, value, chars);
    return copy;
}

/*
 * Copies a Java byte[] holding standard UTF-8 into a NUL-terminated C string.
 *
 * Text and JSON cross the bridge as bytes, not as jstring: GetStringUTFChars hands out
 * *modified* UTF-8, which writes every character outside the BMP (emoji, CJK Extension B
 * kanji such as U+20BB7) as two 3-byte surrogates. That is not valid UTF-8, so the core
 * rejected the whole sentence with VOICEVOX_RESULT_INVALID_UTF8_INPUT_ERROR. Java encodes
 * with StandardCharsets.UTF_8 instead.
 */
static char *dup_utf8(JNIEnv *env, jbyteArray bytes) {
    if (bytes == NULL) return NULL;
    jsize length = (*env)->GetArrayLength(env, bytes);
    char *copy = malloc((size_t) length + 1);
    if (copy == NULL) return NULL;
    (*env)->GetByteArrayRegion(env, bytes, 0, length, (jbyte *) copy);
    copy[length] = '\0';
    return copy;
}

/* The reverse direction: a C string as a byte[] that Java decodes as UTF-8. */
static jbyteArray new_utf8(JNIEnv *env, const char *text) {
    jsize length = (jsize) strlen(text);
    jbyteArray result = (*env)->NewByteArray(env, length);
    if (result != NULL) {
        (*env)->SetByteArrayRegion(env, result, 0, length, (const jbyte *) text);
    }
    return result;
}

/*
 * nativeInit's error string. Only used there: that method signals success with NULL, so the
 * text needs no marker. Every other method must use new_failure, or a failure returned as a
 * plain string is indistinguishable from a successful result.
 */
static jstring new_error(JNIEnv *env, VoicevoxResultCode code) {
    return (*env)->NewStringUTF(env, voicevox_error_result_to_message(code));
}

/**
 * A failure message in the form Java checks for: everything the JVM side treats as an error
 * starts with '!', so "did unload succeed?" is the same test on every native method. Without
 * the marker a failed unload reads as success — the message is a non-NULL string.
 */
static jstring new_failure(JNIEnv *env, VoicevoxResultCode code) {
    char message[128];
    snprintf(message, sizeof(message), "!%s", voicevox_error_result_to_message(code));
    return (*env)->NewStringUTF(env, message);
}

JNIEXPORT jstring JNICALL
Java_app_voicevox_tts_infrastructure_engine_VoicevoxJni_nativeInit(JNIEnv *env, jclass cls, jstring ortPath,
                                              jstring dictDir, jint threads) {
    pthread_mutex_lock(&g_lock);
    if (g_syn != NULL) {
        pthread_mutex_unlock(&g_lock);
        return NULL;
    }

    char *ort_path = dup_jstring(env, ortPath);
    char *dict_dir = dup_jstring(env, dictDir);
    jstring error = NULL;
    VoicevoxResultCode code;

    struct VoicevoxLoadOnnxruntimeOptions load_options =
            voicevox_make_default_load_onnxruntime_options();
    load_options.filename = ort_path;
    code = voicevox_onnxruntime_load_once(load_options, &g_ort);
    if (code != VOICEVOX_RESULT_OK) {
        LOGE("onnxruntime load failed: %s", voicevox_error_result_to_message(code));
        error = new_error(env, code);
        goto done;
    }

    code = voicevox_open_jtalk_rc_new(dict_dir, &g_ojt);
    if (code != VOICEVOX_RESULT_OK) {
        LOGE("open jtalk init failed: %s", voicevox_error_result_to_message(code));
        error = new_error(env, code);
        goto done;
    }

    struct VoicevoxInitializeOptions init_options = voicevox_make_default_initialize_options();
    init_options.acceleration_mode = VOICEVOX_ACCELERATION_MODE_CPU;
    init_options.cpu_num_threads = (uint16_t) (threads > 0 ? threads : 0);
    code = voicevox_synthesizer_new(g_ort, g_ojt, init_options, &g_syn);
    if (code != VOICEVOX_RESULT_OK) {
        LOGE("synthesizer init failed: %s", voicevox_error_result_to_message(code));
        error = new_error(env, code);
        goto done;
    }

    LOGI("core ready");

    done:
    // A half-built synthesizer must not be left behind: the next attempt would
    // see g_syn != NULL and report success without a usable core.
    if (error != NULL) {
        if (g_syn != NULL) {
            voicevox_synthesizer_delete(g_syn);
            g_syn = NULL;
        }
        if (g_ojt != NULL) {
            voicevox_open_jtalk_rc_delete(g_ojt);
            g_ojt = NULL;
        }
    }
    free(ort_path);
    free(dict_dir);
    pthread_mutex_unlock(&g_lock);
    return error;
}

/** Loads one VVM and returns its 16-byte id as 32 hex chars, or a message starting with '!'. */
JNIEXPORT jstring JNICALL
Java_app_voicevox_tts_infrastructure_engine_VoicevoxJni_nativeLoadModel(JNIEnv *env, jclass cls, jstring path) {
    pthread_mutex_lock(&g_lock);
    jstring result = NULL;
    char *model_path = dup_jstring(env, path);
    if (g_syn == NULL || model_path == NULL) {
        pthread_mutex_unlock(&g_lock);
        jstring failure = (*env)->NewStringUTF(env, model_path == NULL
                ? "!out of memory" : "!core not initialised");
        free(model_path);
        return failure;
    }

    struct VoicevoxVoiceModelFile *model = NULL;
    VoicevoxResultCode code = voicevox_voice_model_file_open(model_path, &model);
    if (code != VOICEVOX_RESULT_OK) {
        LOGE("voice model open failed: %s", voicevox_error_result_to_message(code));
        result = new_failure(env, code);
        goto done;
    }

    uint8_t id[16];
    voicevox_voice_model_file_id(model, &id);

    struct VoicevoxLoadVoiceModelOptions options =
            voicevox_make_default_load_voice_model_options();
    code = voicevox_synthesizer_load_voice_model(g_syn, model, options);
    if (code != VOICEVOX_RESULT_OK) {
        LOGE("voice model load failed: %s", voicevox_error_result_to_message(code));
        result = new_failure(env, code);
        goto done;
    }

    char hex[33];
    for (int i = 0; i < 16; i++) {
        snprintf(hex + i * 2, 3, "%02x", id[i]);
    }
    result = (*env)->NewStringUTF(env, hex);
    LOGI("model loaded: %s", hex);

    done:
    if (model != NULL) voicevox_voice_model_file_delete(model);
    free(model_path);
    pthread_mutex_unlock(&g_lock);
    return result;
}

/** Frees the model behind a 32-char hex id. Returns NULL on success, else a '!'-prefixed message. */
JNIEXPORT jstring JNICALL
Java_app_voicevox_tts_infrastructure_engine_VoicevoxJni_nativeUnloadModel(JNIEnv *env, jclass cls, jstring hexId) {
    pthread_mutex_lock(&g_lock);
    jstring result = NULL;
    char *hex = dup_jstring(env, hexId);
    if (g_syn == NULL || hex == NULL || strlen(hex) != 32) {
        pthread_mutex_unlock(&g_lock);
        free(hex);
        return (*env)->NewStringUTF(env, "!bad model id");
    }

    uint8_t id[16];
    for (int i = 0; i < 16; i++) {
        unsigned int byte = 0;
        if (sscanf(hex + i * 2, "%2x", &byte) != 1) {
            free(hex);
            pthread_mutex_unlock(&g_lock);
            return (*env)->NewStringUTF(env, "!bad model id");
        }
        id[i] = (uint8_t) byte;
    }

    VoicevoxResultCode code = voicevox_synthesizer_unload_voice_model(g_syn, &id);
    if (code != VOICEVOX_RESULT_OK) {
        LOGE("voice model unload failed: %s", voicevox_error_result_to_message(code));
        result = new_failure(env, code);
    } else {
        LOGI("model unloaded: %s", hex);
    }
    free(hex);
    pthread_mutex_unlock(&g_lock);
    return result;
}

/** UTF-8 text in, AudioQuery JSON as UTF-8 bytes out, or NULL on failure. */
JNIEXPORT jbyteArray JNICALL
Java_app_voicevox_tts_infrastructure_engine_VoicevoxJni_nativeAudioQuery(JNIEnv *env, jclass cls,
                                                    jbyteArray text, jint styleId) {
    pthread_mutex_lock(&g_lock);
    jbyteArray result = NULL;
    char *text_utf8 = dup_utf8(env, text);
    if (g_syn != NULL && text_utf8 != NULL) {
        char *json = NULL;
        VoicevoxResultCode code = voicevox_synthesizer_create_audio_query(
                g_syn, text_utf8, (VoicevoxStyleId) styleId, &json);
        if (code == VOICEVOX_RESULT_OK && json != NULL) {
            result = new_utf8(env, json);
            voicevox_string_free(json);
        } else {
            LOGE("audio query failed: %s", voicevox_error_result_to_message(code));
        }
    }
    free(text_utf8);
    pthread_mutex_unlock(&g_lock);
    return result;
}

/** AudioQuery JSON as UTF-8 bytes in, a complete WAV out, or NULL on failure. */
JNIEXPORT jbyteArray JNICALL
Java_app_voicevox_tts_infrastructure_engine_VoicevoxJni_nativeSynthesis(JNIEnv *env, jclass cls,
                                                   jbyteArray queryJson, jint styleId) {
    pthread_mutex_lock(&g_lock);
    jbyteArray result = NULL;
    char *json = dup_utf8(env, queryJson);
    if (g_syn != NULL && json != NULL) {
        uintptr_t length = 0;
        uint8_t *wav = NULL;
        struct VoicevoxSynthesisOptions options = voicevox_make_default_synthesis_options();
        VoicevoxResultCode code = voicevox_synthesizer_synthesis(
                g_syn, json, (VoicevoxStyleId) styleId, options, &length, &wav);
        if (code == VOICEVOX_RESULT_OK && wav != NULL) {
            result = (*env)->NewByteArray(env, (jsize) length);
            if (result != NULL) {
                (*env)->SetByteArrayRegion(env, result, 0, (jsize) length, (const jbyte *) wav);
            }
            voicevox_bytes_free(wav);
        } else {
            LOGE("synthesis failed: %s", voicevox_error_result_to_message(code));
        }
    }
    free(json);
    pthread_mutex_unlock(&g_lock);
    return result;
}

JNIEXPORT void JNICALL
Java_app_voicevox_tts_infrastructure_engine_VoicevoxJni_nativeRelease(JNIEnv *env, jclass cls) {
    pthread_mutex_lock(&g_lock);
    if (g_syn != NULL) {
        voicevox_synthesizer_delete(g_syn);
        g_syn = NULL;
    }
    if (g_ojt != NULL) {
        voicevox_open_jtalk_rc_delete(g_ojt);
        g_ojt = NULL;
    }
    pthread_mutex_unlock(&g_lock);
}
