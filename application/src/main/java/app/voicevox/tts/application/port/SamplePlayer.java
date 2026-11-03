package app.voicevox.tts.application.port;

import app.voicevox.tts.domain.voice.VoiceStyle;

/**
 * Port: playing a short sample of one character.
 *
 * <p>Deliberately free of user-facing copy — it reports what happened ({@link Listener}), and
 * the presenter decides how to phrase it. That keeps every string in one place and lets the
 * implementation be swapped for a fake in tests.
 */
public interface SamplePlayer {

    /** All callbacks arrive on the main thread. */
    interface Listener {
        /** Audio actually started flowing. */
        void onStarted(VoiceStyle style);

        /** The sample played to the end. */
        void onFinished(VoiceStyle style);

        /** Playback failed; {@code detail} is for diagnostics, not for the user. */
        void onFailure(VoiceStyle style, String detail);
    }

    void setListener(Listener listener);

    /** The style id currently playing, or -1. */
    int playingStyleId();

    /** Plays the style, or stops it when it is the one already playing. */
    void toggle(VoiceStyle style);

    void stop();

    /** Drops the binding; the next sample rebinds and sees the current file list. */
    void release();
}
