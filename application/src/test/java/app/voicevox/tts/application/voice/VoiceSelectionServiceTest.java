package app.voicevox.tts.application.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.voicevox.tts.application.fake.Fakes;
import app.voicevox.tts.domain.voice.VoiceCatalog;
import app.voicevox.tts.domain.voice.VoiceNames;
import app.voicevox.tts.domain.voice.VoiceStyle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Which voice the engine speaks with, for a named voice, a stale one and none at all. */
public class VoiceSelectionServiceTest {
    private Fakes.Packs packs;
    private Fakes.Defaults defaults;
    private VoiceSelectionService voices;

    @BeforeEach
    public void setUp() {
        packs = new Fakes.Packs();
        packs.installed.add("0.vvm");
        defaults = new Fakes.Defaults();
        voices = new VoiceSelectionService(packs, defaults);
    }

    @Test
    public void onlyInstalledPacksAreListedOneVoicePerCharacter() {
        assertEquals(4, voices.availableStyles().size());

        packs.installed.add("1.vvm");
        assertEquals(5, voices.availableStyles().size());
    }

    @Test
    public void anInstalledVoiceIsUsedAsAsked() {
        VoiceStyle zundamon = VoiceCatalog.byStyleId(3);

        assertSame(zundamon, voices.resolve(zundamon.displayName(VoiceNames.ENGLISH),
                voices.availableStyles()));
    }

    @Test
    public void aVoiceWhosePackIsGoneFallsBackToTheDefault() {
        defaults.styleId = 3;

        String himari = VoiceCatalog.byStyleId(14).displayName(VoiceNames.JAPANESE);

        VoiceStyle picked = voices.resolve(himari, voices.availableStyles());

        assertEquals(3, picked.id);
    }

    @Test
    public void anEmptyNameSpeaksWithTheChosenDefault() {
        voices.chooseDefault(VoiceCatalog.byStyleId(3));

        assertEquals(3, voices.resolve("", voices.availableStyles()).id);
        assertEquals(1, defaults.writes);
    }

    @Test
    public void nothingInstalledMeansNothingSpeaks() {
        packs.installed.clear();

        assertNull(voices.resolve("", voices.availableStyles()));
        assertNull(voices.defaultStyle());
    }

    @Test
    public void validityFollowsWhatIsInstalled() {
        assertTrue(voices.isAvailable(VoiceCatalog.byStyleId(2).displayName(VoiceNames.JAPANESE)));
        assertFalse(voices.isAvailable(
                VoiceCatalog.byStyleId(14).displayName(VoiceNames.JAPANESE)));
        assertFalse(voices.isAvailable("nobody / ノーマル"));
    }
}
