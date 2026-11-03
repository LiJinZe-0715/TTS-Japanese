package app.voicevox.tts.presentation.settings;

import app.voicevox.tts.domain.voice.VoicePack;
import app.voicevox.tts.domain.voice.VoiceStyle;

import java.util.List;
import java.util.Set;

/**
 * What the presenter is allowed to do to the screen.
 *
 * <p>No {@code android.*} import: the presenter talks to this, so its branches can be driven
 * by a fake screen on a plain JVM. {@code view.SettingsView} is the Android implementation; the
 * extras only the diagnostics screen uses (diagnostics block, scroll to bottom) stay on the
 * concrete class and are not part of the product contract.
 */
public interface SettingsScreen {

    /** Everything the screen cannot decide on its own. */
    interface Callbacks {
        void onOpenSystemSettings();

        void onDownloadRequested(VoicePack pack);

        void onDeleteRequested(VoicePack pack);

        void onPreviewRequested(VoicePack pack, VoiceStyle style);

        void onDefaultVoiceRequested(VoiceStyle style);
    }

    void renderPacks(List<VoicePack> packs, Set<String> installedFiles, int playingStyleId);

    /**
     * The characters a caller can land on when it names none, and which one is in effect.
     *
     * @param choices one style per character whose model is on disk
     * @param effectiveStyleId the style the engine will actually speak with, or -1
     */
    void renderDefaultVoice(List<VoiceStyle> choices, int effectiveStyleId);

    /** Only the highlight changes when playback starts or stops; the cards stay as they are. */
    void updatePlaying(int styleId);

    void setSummary(String headline, String detail);

    void setEngineInfo(String text);

    void setDownloadProgress(String file, boolean active, int percent);

    void showMessage(String text, Tone tone);
}
