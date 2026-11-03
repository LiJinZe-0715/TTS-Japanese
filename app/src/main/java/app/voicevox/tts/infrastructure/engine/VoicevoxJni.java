package app.voicevox.tts.infrastructure.engine;

import android.util.Log;

/**
 * The JNI bridge to VOICEVOX CORE ({@code voicevox_jni.c}), and nothing else.
 *
 * <p>The native symbols are named after this class ({@code
 * Java_app_voicevox_tts_infrastructure_engine_VoicevoxJni_*}): moving or renaming it means
 * renaming them too, or every call fails with {@code UnsatisfiedLinkError} at runtime.
 *
 * <p>The native side keeps one synthesizer per process and serialises every call behind one
 * mutex. {@link VoicevoxEngine} holds the bookkeeping around it.
 */
final class VoicevoxJni {
    private static final String TAG = "VoicevoxJni";
    private static final Object LOCK = new Object();

    /**
     * Set by the first call that needs JNI.
     *
     * <p>It has to happen here rather than in a static initialiser: a {@code loadLibrary}
     * failure thrown from {@code <clinit>} cannot be caught by any caller — it escapes as
     * {@code ExceptionInInitializerError} before the method body runs — so the guard in
     * {@link VoicevoxEngine#warmUp} would never see it and the TTS service or the settings
     * screen would die on a missing {@code libc++_shared.so} instead of reporting an unready
     * engine.
     */
    private static volatile boolean loaded;
    private static volatile String loadError;

    private VoicevoxJni() {
    }

    /**
     * Loads the bridge once. Safe from any thread; failure is remembered, not thrown: it is
     * permanent for the lifetime of the process, so repeating the attempt would only repeat
     * the throw.
     */
    static boolean load() {
        if (loaded) return true;
        synchronized (LOCK) {
            if (loaded) return true;
            if (loadError != null) return false;
            try {
                System.loadLibrary("voicevoxjni");
                loaded = true;
                return true;
            } catch (Throwable t) {
                loadError = t.toString();
                Log.e(TAG, "cannot load libvoicevoxjni", t);
                return false;
            }
        }
    }

    /** Why {@link #load()} failed, or null. */
    static String loadError() {
        return loadError;
    }

    static native String nativeInit(String ortPath, String dictDir, int threads);

    static native String nativeLoadModel(String path);

    static native String nativeUnloadModel(String modelId);

    /** UTF-8 text in, UTF-8 JSON out: see {@code dup_utf8} in voicevox_jni.c for why not String. */
    static native byte[] nativeAudioQuery(byte[] text, int styleId);

    static native byte[] nativeSynthesis(byte[] queryJson, int styleId);

    static native void nativeRelease();
}
