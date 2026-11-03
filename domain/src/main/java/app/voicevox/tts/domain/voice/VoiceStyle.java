package app.voicevox.tts.domain.voice;

/**
 * Value object: one character speaking in one style, identified by the style id the core
 * understands.
 */
public final class VoiceStyle {
    /** The character's published (Japanese) name, e.g. "ずんだもん". */
    public final String character;

    /** The style's published (Japanese) label, e.g. "ノーマル". */
    public final String name;

    /** What the core calls this style. Unique across the whole catalogue. */
    public final int id;

    VoiceStyle(String character, String name, int id) {
        this.character = character;
        this.name = name;
        this.id = id;
    }

    /** The character's name as a reader of {@code language} sees it. */
    public String characterName(String language) {
        return VoiceNames.character(character, language);
    }

    /** The style's label as a reader of {@code language} sees it. */
    public String styleName(String language) {
        return VoiceNames.style(name, language);
    }

    /**
     * What the system voice picker shows: "四国めたん / ノーマル", "四国玫碳 / 标准",
     * "Shikoku Metan / Normal". {@link VoiceCatalog#byVoiceName} reads any of them back.
     */
    public String displayName(String language) {
        return characterName(language) + " / " + styleName(language);
    }
}
