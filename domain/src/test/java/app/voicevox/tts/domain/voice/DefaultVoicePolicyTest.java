package app.voicevox.tts.domain.voice;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import java.util.List;

/** The voice a caller lands on when it names none. */
public class DefaultVoicePolicyTest {

    @Test
    public void theChosenStyleWinsWhileItsModelIsInstalled() {
        List<VoiceStyle> available = VoiceCatalog.byFile("0.vvm").defaultStyles();

        assertSame(VoiceCatalog.byStyleId(3), DefaultVoicePolicy.resolve(3, available));
    }

    @Test
    public void anUninstalledChoiceFallsBackToTheFirstOneThatCanSpeak() {
        List<VoiceStyle> available = VoiceCatalog.byFile("0.vvm").defaultStyles();

        assertSame(available.get(0), DefaultVoicePolicy.resolve(14, available));
        assertSame(available.get(0),
                DefaultVoicePolicy.resolve(DefaultVoiceRepository.NONE, available));
    }

    @Test
    public void nothingInstalledMeansNoDefaultStyle() {
        assertNull(DefaultVoicePolicy.resolve(DefaultVoiceRepository.NONE, List.of()));
    }
}
