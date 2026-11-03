package app.voicevox.tts.application.port;

import app.voicevox.tts.domain.voice.VoicePack;

import java.io.IOException;

/**
 * Port: getting voice data onto the phone — unpacking what ships in the APK and downloading
 * the rest.
 *
 * <p>Kept apart from {@code domain.voice.VoicePackRepository} because it is not about which
 * packs exist but about the transfer: one process-wide download at a time, its progress, and
 * the dictionary every pack needs.
 */
public interface VoicePackInstaller {

    /** Download progress, in bytes. Called from a worker thread. */
    interface Progress {
        void onProgress(String stage, long done, long total);
    }

    /** False when the Open JTalk dictionary is missing, i.e. the install is broken. */
    boolean isDictionaryReady();

    /**
     * Unpacks what ships inside the app (dictionary, bundled pack) onto the filesystem.
     * Blocking and idempotent; the caller runs it off the UI thread.
     *
     * @return false when the dictionary could not be made ready, i.e. the install is broken
     */
    boolean prepare();

    /**
     * Blocking; the caller runs this off the UI thread and reports progress itself.
     * At most one download runs per process: a second one fails with an IOException.
     */
    void download(VoicePack pack, Progress progress) throws IOException;

    /**
     * The pack this process is downloading right now — possibly started by a screen that no
     * longer exists, e.g. before a rotation — or null.
     */
    String activeDownload();

    /** Progress of {@link #activeDownload()}, 0..100. */
    int activePercent();

    /**
     * Waits up to {@code millis} for the download of {@code file} to end.
     *
     * @return true while it is still running
     */
    boolean awaitDownload(String file, long millis) throws InterruptedException;
}
