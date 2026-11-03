package app.voicevox.tts.di;

import android.content.Context;

import app.voicevox.tts.application.port.AppLog;
import app.voicevox.tts.application.port.SamplePlayer;
import app.voicevox.tts.application.port.SpeechEngine;
import app.voicevox.tts.application.speech.SpeechSynthesisService;
import app.voicevox.tts.application.voice.VoicePackService;
import app.voicevox.tts.application.voice.VoiceSelectionService;
import app.voicevox.tts.domain.voice.DefaultVoiceRepository;
import app.voicevox.tts.infrastructure.engine.VoicevoxEngine;
import app.voicevox.tts.infrastructure.log.LogcatLog;
import app.voicevox.tts.infrastructure.playback.SystemTtsSamplePlayer;
import app.voicevox.tts.infrastructure.preference.SharedPreferencesDefaultVoiceRepository;
import app.voicevox.tts.infrastructure.storage.FileVoicePackStore;

/**
 * The composition root: the only place that pairs a port with its implementation.
 *
 * <p>One per process. The TTS service and the settings screen run in the same process and must
 * share the same store and engine — one download at a time, one native core, one LRU of
 * resident models — so every entry point asks {@link #get} instead of building its own.
 *
 * <p>Nothing above this class imports {@code infrastructure}: when a second backend shows up,
 * this file changes and the use cases and the screens do not.
 */
public final class AppContainer {
    /** Logcat tag shared by the TTS service and the settings screens. */
    public static final String LOG_TAG = "VoicevoxTts";

    private static AppContainer instance;

    private final Context context;
    private final SpeechEngine engine;
    private final VoicePackService packs;
    private final VoiceSelectionService voices;

    private AppContainer(Context context) {
        this.context = context;
        FileVoicePackStore store = new FileVoicePackStore(context);
        DefaultVoiceRepository defaults = new SharedPreferencesDefaultVoiceRepository(context);
        engine = new VoicevoxEngine(context, store);
        packs = new VoicePackService(store, store, engine);
        voices = new VoiceSelectionService(store, defaults);
    }

    public static synchronized AppContainer get(Context context) {
        if (instance == null) instance = new AppContainer(context.getApplicationContext());
        return instance;
    }

    public SpeechEngine engine() {
        return engine;
    }

    public VoicePackService packs() {
        return packs;
    }

    public VoiceSelectionService voices() {
        return voices;
    }

    /** One per bound TTS service: it owns that service's producer thread. */
    public SpeechSynthesisService newSpeechSynthesis() {
        return new SpeechSynthesisService(voices, engine, log(LOG_TAG));
    }

    /** One per settings screen: it owns that screen's binding to the engine. */
    public SamplePlayer newSamplePlayer() {
        return new SystemTtsSamplePlayer(context);
    }

    public AppLog log(String tag) {
        return new LogcatLog(tag);
    }
}
