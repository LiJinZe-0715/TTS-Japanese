package app.voicevox.tts.application.port;

import java.util.List;

/**
 * Port: the speech engine that turns text into audio.
 *
 * <p>The use cases speak through this, so neither they nor the Android TTS contract ever see a
 * JNI signature. {@code infrastructure.engine.VoicevoxEngine} is the implementation that talks
 * to VOICEVOX CORE.
 */
public interface SpeechEngine {

    boolean isReady();

    /** Brings the core up without loading a voice model. Blocking; call from a worker. */
    void warmUp();

    /** Makes sure the model behind this style id is resident, evicting the eldest if needed. */
    boolean ensureModel(int styleId);

    /**
     * Synthesises one sentence and returns a complete WAV byte array, or null on failure.
     *
     * @param speed  rate multiplier, 1.0 = normal
     * @param pitch  frequency ratio, 1.0 = normal
     * @param volume volume multiplier, 1.0 = normal
     */
    byte[] synthesize(String text, int styleId, float speed, float pitch, float volume);

    /** Whether the pack's model is resident, i.e. whether unloading it has anything to do. */
    boolean isLoaded(String file);

    /**
     * Drops the pack's model from memory, e.g. before its file is deleted. Blocks until the
     * engine is free: a synthesis or a load in progress can take seconds.
     */
    void unload(String file);

    int loadedCount();

    List<String> loadedFiles();

    String lastError();

    void release();
}
