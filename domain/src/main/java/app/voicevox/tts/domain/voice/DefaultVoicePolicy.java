package app.voicevox.tts.domain.voice;

import java.util.List;

/**
 * Domain rule: the style to speak with when the caller names no voice — which is every
 * browser.
 *
 * <p>The user's pick wins only while its model is still on disk: a preference pointing at a
 * pack that has since been deleted must not turn into silence, so it falls back to the first
 * character that can actually speak. The engine and the settings screen both go through this,
 * so the chip the screen highlights is the voice the engine will use.
 */
public final class DefaultVoicePolicy {

    private DefaultVoicePolicy() {
    }

    /**
     * @param chosenId  what {@link DefaultVoiceRepository} holds, or
     *                  {@link DefaultVoiceRepository#NONE}
     * @param available styles whose model is installed, in the order the engine lists them
     * @return the style to use, or {@code null} when nothing can speak at all
     */
    public static VoiceStyle resolve(int chosenId, List<VoiceStyle> available) {
        VoiceStyle chosen = VoiceCatalog.byStyleId(chosenId);
        if (chosen != null) {
            for (VoiceStyle style : available) {
                if (style.id == chosen.id) return chosen;
            }
        }
        return available.isEmpty() ? null : available.get(0);
    }
}
