package app.voicevox.tts.domain.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The sentence splitter in front of the core.
 *
 * <p>Every piece it emits becomes one native synthesis, so a bad cut is audible: a lone "！"
 * costs a round trip, a word cut in half gets the wrong accent, and a cut inside a surrogate
 * pair turns the character into an encoding error.
 */
public class TextChunkerTest {

    @Test
    public void cutsAtSentenceEnds() {
        assertEquals(List.of("こんにちは。", "元気ですか？", "はい"),
                TextChunker.split("こんにちは。元気ですか？はい"));
    }

    @Test
    public void foldsPunctuationOnlyPiecesIntoTheirPredecessor() {
        assertEquals(List.of("本当？！"), TextChunker.split("本当？！"));
        assertEquals(List.of("すごい!!!"), TextChunker.split("すごい!!!"));
    }

    @Test
    public void dropsBlankLines() {
        assertEquals(List.of("一行目\n\n", "二行目"), TextChunker.split("一行目\n\n二行目"));
    }

    @Test
    public void keepsATextWithNothingToPronounceAsOnePiece() {
        // The service skips it and reports an empty, successful utterance.
        List<String> pieces = TextChunker.split("……");
        assertEquals(List.of("……"), pieces);
        assertFalse(TextChunker.isSpeakable(pieces.get(0)));
    }

    @Test
    public void anOverLongSentenceIsCutAtTheLastSoftBreak() {
        String head = "あ".repeat(70) + "、";
        String tail = "い".repeat(60);
        List<String> pieces = TextChunker.split(head + tail);

        assertEquals(head, pieces.get(0));
        assertEquals(tail, pieces.get(1));
    }

    @Test
    public void anOverLongSentenceWithoutBreaksIsCutAtTheLimit() {
        List<String> pieces = TextChunker.split("あ".repeat(250));

        assertEquals(3, pieces.size());
        assertEquals(TextChunker.MAX, pieces.get(0).length());
    }

    @Test
    public void neverCutsBetweenTheHalvesOfASurrogatePair() {
        // 99 BMP characters, then 𠮷 (two chars): the 100-char limit lands inside the pair.
        String text = "あ".repeat(99) + "𠮷" + "野家";
        for (String piece : TextChunker.split(text)) {
            assertFalse(Character.isLowSurrogate(piece.charAt(0)), "piece starts mid-pair");
            assertFalse(Character.isHighSurrogate(piece.charAt(piece.length() - 1)),
                    "piece ends mid-pair");
        }
        assertEquals(text, String.join("", TextChunker.split(text)));
    }

    @Test
    public void digitsCountAsTextButALoneEmojiDoesNot() {
        assertTrue(TextChunker.isSpeakable("2026年"));
        assertTrue(TextChunker.isSpeakable("了解"));
        assertFalse(TextChunker.isSpeakable("👍"));
    }
}
