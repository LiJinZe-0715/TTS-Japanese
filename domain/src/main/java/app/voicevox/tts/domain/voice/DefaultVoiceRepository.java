package app.voicevox.tts.domain.voice;

/**
 * Repository: which character the engine speaks with when the caller does not name one.
 *
 * <p>This exists because some callers cannot choose. Chrome on Android builds its voice list by
 * walking {@code Locale.getAvailableLocales()} and asking {@code isLanguageAvailable()}, never
 * by reading {@code TextToSpeech#getVoices()}, and it speaks with {@code setLanguage()} alone —
 * never {@code setVoice()}. Every page therefore arrives with an empty voice name, and the only
 * way a user can pick a character there is a setting like this one.
 *
 * <p>It only stores the choice. Which style actually speaks is {@link DefaultVoicePolicy}'s
 * decision, because the stored pick may point at a pack that has since been deleted.
 */
public interface DefaultVoiceRepository {

    int NONE = -1;

    /** The style the user chose, or {@link #NONE} when nothing has been chosen yet. */
    int styleId();

    /** Stores the choice; the engine reads it on the next utterance, no restart involved. */
    void save(int styleId);
}
