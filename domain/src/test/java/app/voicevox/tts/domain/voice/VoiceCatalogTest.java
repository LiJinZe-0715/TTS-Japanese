package app.voicevox.tts.domain.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

/**
 * The catalogue is hand-written data, so the tests guard the shape of it: one file per
 * release asset, one voice per character, and a style id that always resolves back to the
 * file that has to be loaded before it can speak.
 */
public class VoiceCatalogTest {

    @Test
    public void everyAssetOfThePinnedReleaseIsListed() {
        assertEquals(25, VoiceCatalog.all().size());
        for (int i = 0; i < 25; i++) {
            assertTrue(VoiceCatalog.byFile(i + ".vvm") != null, "missing " + i + ".vvm");
        }
    }

    @Test
    public void bundledIsTheOneShippedInsideTheApk() {
        assertEquals("0.vvm", VoiceCatalog.bundled().file);
        assertSame(VoiceCatalog.byFile("0.vvm"), VoiceCatalog.bundled());
    }

    @Test
    public void defaultStylesPicksOneVoicePerCharacter() {
        List<VoiceStyle> styles = VoiceCatalog.byFile("0.vvm").defaultStyles();

        assertEquals(4, styles.size());
        assertEquals("四国めたん", styles.get(0).character);
        assertEquals("ノーマル", styles.get(0).name);
        assertEquals(2, styles.get(0).id);
        assertEquals("ずんだもん", styles.get(1).character);
        assertEquals(3, styles.get(1).id);
    }

    @Test
    public void aPackWithOneCharacterKeepsOneChip() {
        List<VoiceStyle> styles = VoiceCatalog.byFile("1.vvm").defaultStyles();

        assertEquals(1, styles.size());
        assertEquals("冥鳴ひまり", styles.get(0).character);
        assertEquals(14, styles.get(0).id);
    }

    @Test
    public void charactersJoinsEachNameOnceInFileOrder() {
        assertEquals("四国めたん / ずんだもん / 春日部つむぎ / 雨晴はう",
                VoiceCatalog.byFile("0.vvm").characters());
    }

    @Test
    public void styleIdResolvesToTheFileThatMustBeLoaded() {
        assertEquals("0.vvm", VoiceCatalog.packOfStyle(2).file);
        assertEquals("1.vvm", VoiceCatalog.packOfStyle(14).file);
        assertEquals("15.vvm", VoiceCatalog.packOfStyle(46).file);
        assertNull(VoiceCatalog.packOfStyle(9999));
    }

    @Test
    public void styleIdsAreUniqueAcrossTheCatalogue() {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (VoicePack vvm : VoiceCatalog.all()) {
            for (VoiceStyle style : vvm.styles) {
                assertTrue(seen.add(style.id), "duplicate style id " + style.id);
            }
        }
    }

    @Test
    public void thePublishedNameRoundTripsBackToTheStyle() {
        VoiceStyle style = VoiceCatalog.byStyleId(14);
        String published = style.displayName(VoiceNames.JAPANESE);
        assertEquals("冥鳴ひまり / ノーマル", published);
        assertSame(style, VoiceCatalog.byVoiceName(published));
    }

    @Test
    public void everyPackCarriesADistinctPublishedDigest() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (VoicePack vvm : VoiceCatalog.all()) {
            assertTrue(vvm.sha256 != null && vvm.sha256.matches("[0-9a-f]{64}"),
                    vvm.file + " has no SHA-256");
            assertTrue(seen.add(vvm.sha256), vvm.file + " shares a digest with another pack");
        }
        // The bundled pack's digest is the one scripts/fetch-assets.mjs verifies at build time.
        assertEquals("ecd35374d4182cd883cba5040376f7f888cc6ba248b1c2f4cea07cdb34bb1318",
                VoiceCatalog.bundled().sha256);
    }

    // ------------------------------------------------------------------ translated names

    @Test
    public void thePhoneLanguagePicksTheRendering() {
        assertEquals(VoiceNames.CHINESE, VoiceNames.language(Locale.SIMPLIFIED_CHINESE));
        assertEquals(VoiceNames.CHINESE, VoiceNames.language(Locale.TRADITIONAL_CHINESE));
        assertEquals(VoiceNames.ENGLISH, VoiceNames.language(Locale.US));
        // A Japanese phone reads the published names.
        assertEquals(VoiceNames.JAPANESE, VoiceNames.language(Locale.JAPAN));
        // Anything else falls back to English.
        assertEquals(VoiceNames.ENGLISH, VoiceNames.language(Locale.FRANCE));
        assertEquals(VoiceNames.ENGLISH, VoiceNames.language(null));
    }

    @Test
    public void aTranslatedNameReadsBackAsTheSameStyle() {
        VoiceStyle style = VoiceCatalog.byStyleId(14);

        assertEquals("冥鸣日鞠 / 标准", style.displayName(VoiceNames.CHINESE));
        assertSame(style, VoiceCatalog.byVoiceName(style.displayName(VoiceNames.CHINESE)));

        assertEquals("Meimei Himari / Normal", style.displayName(VoiceNames.ENGLISH));
        assertSame(style, VoiceCatalog.byVoiceName(style.displayName(VoiceNames.ENGLISH)));
    }

    @Test
    public void everyCharacterAndStyleInTheCatalogueIsTranslated() {
        for (VoicePack vvm : VoiceCatalog.all()) {
            for (VoiceStyle style : vvm.styles) {
                assertTrue(VoiceNames.knownCharacters().contains(style.character),
                        "no translations for " + style.character);
                assertTrue(VoiceNames.knownStyles().contains(style.name),
                        "no translations for " + style.name);

                // A missing entry falls back to the published name, so kana in a translation
                // means the table has a hole rather than that the name is Japanese by design.
                assertFalse(hasKana(style.characterName(VoiceNames.CHINESE)),
                        "untranslated character " + style.character);
                assertFalse(hasKana(style.styleName(VoiceNames.CHINESE)),
                        "untranslated style " + style.name);
                assertFalse(hasCjk(style.characterName(VoiceNames.ENGLISH)),
                        "untranslated character " + style.character);
                assertFalse(hasCjk(style.styleName(VoiceNames.ENGLISH)),
                        "untranslated style " + style.name);
            }
        }
    }

    @Test
    public void charactersAreListedInTheRequestedLanguage() {
        assertEquals("四国玫碳 / 毛豆泥 / 春日部紬 / 雨晴哈乌",
                VoiceCatalog.byFile("0.vvm").characters(VoiceNames.CHINESE));
        assertEquals("Shikoku Metan / Zundamon / Kasukabe Tsumugi / Amehare Hau",
                VoiceCatalog.byFile("0.vvm").characters(VoiceNames.ENGLISH));
    }

    private static boolean hasKana(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x3040 && c <= 0x30FF) return true;
        }
        return false;
    }

    private static boolean hasCjk(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x3000 && c <= 0x9FFF) return true;
            if (c >= 0xFF00 && c <= 0xFFEF) return true;
        }
        return false;
    }

    @Test
    public void unknownIdsAndNamesAreNull() {
        assertNull(VoiceCatalog.byStyleId(9999));
        assertNull(VoiceCatalog.byVoiceName("存在しない / ノーマル"));
        assertNull(VoiceCatalog.byFile("missing.vvm"));
    }

    @Test
    public void defaultStylesOfListsOneVoicePerCharacterOfTheGivenPacks() {
        List<VoiceStyle> styles =
                VoiceCatalog.defaultStylesOf(List.of("0.vvm", "1.vvm", "nope.vvm"));

        assertEquals(5, styles.size());
        assertEquals(2, styles.get(0).id);
        assertEquals(14, styles.get(4).id);
    }
}
