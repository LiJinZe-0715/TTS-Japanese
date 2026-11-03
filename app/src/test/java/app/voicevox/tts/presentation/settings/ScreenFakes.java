package app.voicevox.tts.presentation.settings;

import app.voicevox.tts.domain.voice.VoicePack;
import app.voicevox.tts.domain.voice.VoiceStyle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test doubles for the presenter's two platform-facing collaborators: the screen and the host.
 * The use-case side is faked in {@code application.fake.Fakes}.
 */
final class ScreenFakes {

    private ScreenFakes() {
    }
    /** A screen that records everything it was asked to show. */
    static final class Screen implements SettingsScreen {
        public final List<String> messages = new ArrayList<>();
        public final List<Tone> tones = new ArrayList<>();
        public final List<String> progressCalls = new ArrayList<>();
        public final Map<String, Integer> progressPercent = new LinkedHashMap<>();

        public List<VoicePack> renderedPacks = new ArrayList<>();
        public Set<String> renderedInstalled = new LinkedHashSet<>();
        public int renderedPlaying = -1;

        public String summary;
        public String summaryDetail;
        public String engineInfo;

        /** What {@link #renderDefaultVoice} last reported. */
        public List<VoiceStyle> defaultChoices = new ArrayList<>();
        public int defaultEffective = -2;

        /** What {@link #updatePlaying} last reported. */
        public int playing = -1;

        public String lastMessage() {
            return messages.isEmpty() ? null : messages.get(messages.size() - 1);
        }

        public Tone lastTone() {
            return tones.isEmpty() ? null : tones.get(tones.size() - 1);
        }

        public boolean said(String fragment) {
            for (String message : messages) {
                if (message.contains(fragment)) return true;
            }
            return false;
        }

        @Override
        public void renderPacks(List<VoicePack> packs, Set<String> installedFiles,
                                int playingStyleId) {
            renderedPacks = packs;
            renderedInstalled = installedFiles;
            renderedPlaying = playingStyleId;
            playing = playingStyleId;
        }

        @Override
        public void updatePlaying(int styleId) {
            playing = styleId;
        }

        @Override
        public void renderDefaultVoice(List<VoiceStyle> choices, int effectiveStyleId) {
            defaultChoices = choices;
            defaultEffective = effectiveStyleId;
        }

        @Override
        public void setSummary(String headline, String detail) {
            summary = headline;
            summaryDetail = detail;
        }

        @Override
        public void setEngineInfo(String text) {
            engineInfo = text;
        }

        @Override
        public void setDownloadProgress(String file, boolean active, int percent) {
            progressCalls.add(file + ":" + active + ":" + percent);
            progressPercent.put(file, percent);
        }

        @Override
        public void showMessage(String text, Tone tone) {
            messages.add(text);
            tones.add(tone);
        }
    }

    /** A host that runs everything inline, so a test is synchronous. */
    static final class Host implements ScreenHost {
        public boolean systemSettingsAvailable = true;
        public boolean registered = true;

        /** When false, {@link #confirm} only records the prompt, as if the user cancelled. */
        public boolean autoConfirm = true;

        public int confirmations;
        public String confirmTitle;
        public String confirmMessage;

        @Override
        public void post(Runnable action) {
            action.run();
        }

        /**
         * When true, {@link #background} queues the work instead of running it, so a test can
         * look at the screen while it is "still running" and then {@link #runDeferred}.
         */
        public boolean deferBackground;
        public final List<Runnable> deferred = new ArrayList<>();

        @Override
        public void background(String threadName, Runnable work) {
            if (deferBackground) {
                deferred.add(work);
            } else {
                work.run();
            }
        }

        /** Runs the queued background work, including anything it queues in turn. */
        public void runDeferred() {
            while (!deferred.isEmpty()) deferred.remove(0).run();
        }

        @Override
        public void confirm(String title, String message, String positiveLabel,
                            Runnable onConfirm) {
            confirmations++;
            confirmTitle = title;
            confirmMessage = message;
            if (autoConfirm) onConfirm.run();
        }

        @Override
        public boolean openSystemSettings() {
            return systemSettingsAvailable;
        }

        @Override
        public boolean isRegisteredEngine() {
            return registered;
        }

        @Override
        public void log(String message, Throwable error) {
        }
    }
}
