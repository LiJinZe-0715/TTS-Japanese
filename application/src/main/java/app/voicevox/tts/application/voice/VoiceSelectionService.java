package app.voicevox.tts.application.voice;

import app.voicevox.tts.domain.voice.DefaultVoicePolicy;
import app.voicevox.tts.domain.voice.DefaultVoiceRepository;
import app.voicevox.tts.domain.voice.VoiceCatalog;
import app.voicevox.tts.domain.voice.VoicePack;
import app.voicevox.tts.domain.voice.VoicePackRepository;
import app.voicevox.tts.domain.voice.VoiceStyle;

import java.util.List;

/**
 * Application service: which voice speaks.
 *
 * <p>The TTS engine and the settings screen both answer "what can speak, and who speaks when
 * nobody says" — the engine to the system, the screen to the user. Both go through here, so
 * the voice the screen highlights is the voice the engine uses.
 */
public final class VoiceSelectionService {
    private final VoicePackRepository repository;
    private final DefaultVoiceRepository defaults;

    public VoiceSelectionService(VoicePackRepository repository, DefaultVoiceRepository defaults) {
        this.repository = repository;
        this.defaults = defaults;
    }

    /** One voice per character, for every pack that can speak. */
    public List<VoiceStyle> availableStyles() {
        return VoiceCatalog.defaultStylesOf(repository.installedFiles());
    }

    /** The same list, for a caller that has just read the installed files itself. */
    public List<VoiceStyle> availableStyles(List<String> installedFiles) {
        return VoiceCatalog.defaultStylesOf(installedFiles);
    }

    /** Whether the name is one the engine lists right now. */
    public boolean isAvailable(String voiceName) {
        VoiceStyle requested = VoiceCatalog.byVoiceName(voiceName);
        if (requested == null) return false;
        for (VoiceStyle style : availableStyles()) {
            if (style.id == requested.id) return true;
        }
        return false;
    }

    /** What speaks when the caller names no voice, or null when nothing can speak. */
    public VoiceStyle defaultStyle(List<VoiceStyle> available) {
        return DefaultVoicePolicy.resolve(defaults.styleId(), available);
    }

    /** {@link #defaultStyle(List)} against what is installed right now. */
    public VoiceStyle defaultStyle() {
        return defaultStyle(availableStyles());
    }

    /**
     * Resolves a voice name to a style whose pack can actually speak.
     *
     * <p>The framework keeps asking for whatever voice the user picked, even after that
     * model has been deleted, so an unavailable name falls back to the user's default
     * character, then to the first one available, instead of failing the utterance.
     *
     * <p>An empty name is the normal case for browsers — Chrome on Android never calls
     * {@code setVoice()}, it only sets the language — and is answered from the user's
     * "default character" setting, which is the only way to pick a character there.
     *
     * @param available what {@link #availableStyles()} returned, so it is scanned once
     * @return the style, or null when nothing can speak
     */
    public VoiceStyle resolve(String voiceName, List<VoiceStyle> available) {
        VoiceStyle requested = VoiceCatalog.byVoiceName(voiceName);
        if (requested != null) {
            VoicePack pack = VoiceCatalog.packOfStyle(requested.id);
            if (pack != null && repository.isInstalled(pack.file)) return requested;
        }
        return defaultStyle(available);
    }

    /** Remembers the character a caller lands on when it names none. */
    public void chooseDefault(VoiceStyle style) {
        defaults.save(style.id);
    }
}
