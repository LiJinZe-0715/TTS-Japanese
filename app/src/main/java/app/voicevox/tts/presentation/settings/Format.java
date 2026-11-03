package app.voicevox.tts.presentation.settings;

import java.util.Locale;

/**
 * The handful of numbers the screen renders as text.
 *
 * <p>One copy only: the presenter writes "占用 178.4 MB" into a message and the view prints
 * the same figure on a card, so the formatting lives here instead of being duplicated in both.
 */
public final class Format {

    private Format() {
    }

    public static String megabytes(long bytes) {
        return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
    }
}
