package app.voicevox.tts.domain.speech;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts an utterance into the pieces the engine synthesises one at a time, so audio starts
 * flowing after the first sentence instead of after the whole paragraph.
 *
 * <p>Three rules the old in-service splitter broke:
 * <ul>
 *   <li>A piece with nothing to pronounce — a lone "！", a blank line — is not sent to the core
 *       on its own. It costs an inference round trip at best and an empty-query error at
 *       worst; it is folded into the piece before it instead.</li>
 *   <li>An over-long sentence is cut at the last soft break (、，, space…) rather than blindly
 *       at a fixed length, which split words and wrecked the accent at the seam.</li>
 *   <li>No cut ever falls between the two halves of a surrogate pair: either half alone is not
 *       encodable as UTF-8 and used to turn an emoji into garbage.</li>
 * </ul>
 *
 * <p>Pure Java, so every boundary case is a plain JVM test.
 */
public final class TextChunker {
    /** Ends a sentence: always a cut. */
    private static final String HARD = "。！？!?；;\n";

    /** A pause inside a sentence: where an over-long one is cut. */
    private static final String SOFT = "、，,：:…‥ 　";

    /** Characters per piece before a cut is forced. */
    public static final int MAX = 100;

    private TextChunker() {
    }

    public static List<String> split(String text) {
        List<String> pieces = new ArrayList<>();
        if (text == null) return pieces;
        StringBuilder current = new StringBuilder();
        int lastSoft = -1;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int width = Character.charCount(codePoint);
            current.appendCodePoint(codePoint);
            i += width;
            if (HARD.indexOf(codePoint) >= 0) {
                emit(pieces, current.toString());
                current.setLength(0);
                lastSoft = -1;
                continue;
            }
            if (SOFT.indexOf(codePoint) >= 0) lastSoft = current.length();
            if (current.length() >= MAX) {
                // Cut after the last soft break when there is one, else here. `current` only
                // ever grows by whole code points, so either position is a code point boundary.
                int cut = lastSoft > 0 ? lastSoft : current.length();
                emit(pieces, current.substring(0, cut));
                current.delete(0, cut);
                lastSoft = -1;
            }
        }
        if (current.length() > 0) emit(pieces, current.toString());
        return pieces;
    }

    /** Appends a piece, folding one with nothing to pronounce into its predecessor. */
    private static void emit(List<String> pieces, String piece) {
        if (!isSpeakable(piece) && !pieces.isEmpty()) {
            int last = pieces.size() - 1;
            pieces.set(last, pieces.get(last) + piece);
            return;
        }
        if (piece.trim().isEmpty()) return;
        pieces.add(piece);
    }

    /** Whether the text holds anything a reader would pronounce: a letter or a digit. */
    public static boolean isSpeakable(String text) {
        return text.codePoints().anyMatch(Character::isLetterOrDigit);
    }
}
