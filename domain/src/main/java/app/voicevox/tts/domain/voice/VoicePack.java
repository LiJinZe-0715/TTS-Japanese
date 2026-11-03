package app.voicevox.tts.domain.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Value object: one published .vvm voice model, which may hold several characters.
 *
 * <p>It describes the release asset, not a file on this phone: whether it is installed is the
 * {@link VoicePackRepository}'s business.
 */
public final class VoicePack {
    /** The release asset's file name, e.g. "0.vvm". Also the pack's identity. */
    public final String file;

    /** Exact size of the release asset. See {@link PackIntegrity}. */
    public final long bytes;

    /**
     * Lower-case hex SHA-256 of the release asset, as GitHub publishes it
     * ({@code assets[].digest}). A download is accepted only when it hashes to this:
     * the size alone let a same-length but corrupt file pass as installed for good.
     */
    public final String sha256;

    public final List<VoiceStyle> styles;

    VoicePack(String file, long bytes, String sha256, List<VoiceStyle> styles) {
        this.file = file;
        this.bytes = bytes;
        this.sha256 = sha256;
        this.styles = Collections.unmodifiableList(styles);
    }

    /** The first style of each character, i.e. one voice per character. */
    public List<VoiceStyle> defaultStyles() {
        Map<String, VoiceStyle> first = new LinkedHashMap<>();
        for (VoiceStyle style : styles) {
            if (!first.containsKey(style.character)) first.put(style.character, style);
        }
        return new ArrayList<>(first.values());
    }

    /** Character names in file order, for the download list. */
    public String characters() {
        return characters(VoiceNames.JAPANESE);
    }

    /** The same list, named for a reader of {@code language}. */
    public String characters(String language) {
        Map<String, Boolean> names = new LinkedHashMap<>();
        for (VoiceStyle style : styles) names.put(style.characterName(language), Boolean.TRUE);
        return String.join(" / ", new ArrayList<>(names.keySet()));
    }
}
