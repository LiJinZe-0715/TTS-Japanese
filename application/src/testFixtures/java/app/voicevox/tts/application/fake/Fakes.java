package app.voicevox.tts.application.fake;

import app.voicevox.tts.application.port.SamplePlayer;
import app.voicevox.tts.application.port.SpeechEngine;
import app.voicevox.tts.application.port.VoicePackInstaller;
import app.voicevox.tts.domain.voice.DefaultVoiceRepository;
import app.voicevox.tts.domain.voice.VoiceCatalog;
import app.voicevox.tts.domain.voice.VoicePack;
import app.voicevox.tts.domain.voice.VoicePackRepository;
import app.voicevox.tts.domain.voice.VoiceStyle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Test doubles for the repositories and ports the use cases depend on.
 *
 * <p>They exist because that is the whole point of the ports: the download/delete/preview
 * branches can be walked on a plain JVM, with no device, no engine and no files. Everything a
 * test needs to observe is a public field.
 */
public final class Fakes {

    private Fakes() {
    }

    /**
     * An in-memory inventory and installer. "Installed" is just a set of file names; the two
     * ports share it because a download is what makes a pack installed.
     */
    public static final class Packs implements VoicePackRepository, VoicePackInstaller {
        public final Set<String> installed = new LinkedHashSet<>();
        public final List<String> downloaded = new ArrayList<>();
        public boolean dictionaryReady = true;
        public boolean deleteCalled;

        /** Thrown from {@link #download} instead of succeeding. */
        public IOException failDownloadWith;

        /** Runs inside {@link #download}, i.e. while the presenter still considers it busy. */
        public Runnable duringDownload;

        /** What {@link #activeDownload()} reports: a download some other screen started. */
        public String foreignDownload;
        public int foreignPercent;
        public int prepareCalls;

        /** Runs inside {@link #delete}, e.g. to record the order of calls across ports. */
        public Runnable duringDelete;

        @Override
        public boolean isInstalled(String file) {
            return installed.contains(file);
        }

        @Override
        public List<String> installedFiles() {
            List<String> files = new ArrayList<>();
            for (VoicePack pack : VoiceCatalog.all()) {
                if (installed.contains(pack.file)) files.add(pack.file);
            }
            return files;
        }

        @Override
        public long installedBytes() {
            long total = 0;
            for (VoicePack pack : VoiceCatalog.all()) {
                if (installed.contains(pack.file)) total += pack.bytes;
            }
            return total;
        }

        @Override
        public void delete(VoicePack pack) {
            deleteCalled = true;
            if (duringDelete != null) duringDelete.run();
            installed.remove(pack.file);
        }

        @Override
        public boolean isDictionaryReady() {
            return dictionaryReady;
        }

        @Override
        public boolean prepare() {
            prepareCalls++;
            return true;
        }

        @Override
        public void download(VoicePack pack, Progress progress) throws IOException {
            downloaded.add(pack.file);
            if (duringDownload != null) duringDownload.run();
            if (failDownloadWith != null) throw failDownloadWith;
            if (progress != null) {
                progress.onProgress("download", 0, pack.bytes);
                progress.onProgress("download", pack.bytes, pack.bytes);
            }
            installed.add(pack.file);
        }

        @Override
        public String activeDownload() {
            return foreignDownload;
        }

        @Override
        public int activePercent() {
            return foreignPercent;
        }

        /** Finishes the foreign download on the first wait, as if it completed meanwhile. */
        @Override
        public boolean awaitDownload(String file, long millis) {
            if (file.equals(foreignDownload)) {
                foreignDownload = null;
                installed.add(file);
            }
            return false;
        }
    }

    /** A player that flips between "playing this style" and "silent". */
    public static final class Player implements SamplePlayer {
        public Listener listener;
        public int playing = -1;
        public int stopCount;
        public int releaseCount;
        public final List<VoiceStyle> toggles = new ArrayList<>();

        @Override
        public void setListener(Listener listener) {
            this.listener = listener;
        }

        @Override
        public int playingStyleId() {
            return playing;
        }

        @Override
        public void toggle(VoiceStyle style) {
            toggles.add(style);
            playing = playing == style.id ? -1 : style.id;
        }

        @Override
        public void stop() {
            stopCount++;
            playing = -1;
        }

        @Override
        public void release() {
            releaseCount++;
        }
    }

    /**
     * An engine that is always ready. {@link #synthesize} returns {@link #audio} — by default a
     * tiny valid WAV — and records what it was asked to say.
     */
    public static final class Engine implements SpeechEngine {
        public boolean ready = true;
        public boolean modelAvailable = true;
        public int loaded;
        public List<String> loadedFiles = new ArrayList<>();
        public final List<String> unloaded = new ArrayList<>();
        public final List<String> spoken = new ArrayList<>();
        public final List<Integer> ensured = new ArrayList<>();
        public String error = "";
        public byte[] audio = wav(24000, 480);

        /** Runs inside {@link #unload}, e.g. to record the order of calls across ports. */
        public Runnable duringUnload;

        @Override
        public boolean isReady() {
            return ready;
        }

        @Override
        public void warmUp() {
        }

        @Override
        public boolean ensureModel(int styleId) {
            ensured.add(styleId);
            return modelAvailable;
        }

        @Override
        public byte[] synthesize(String text, int styleId, float speed, float pitch,
                                 float volume) {
            spoken.add(text);
            return audio;
        }

        @Override
        public boolean isLoaded(String file) {
            return loadedFiles.contains(file);
        }

        @Override
        public void unload(String file) {
            unloaded.add(file);
            if (duringUnload != null) duringUnload.run();
            loadedFiles.remove(file);
        }

        @Override
        public int loadedCount() {
            return loaded;
        }

        @Override
        public List<String> loadedFiles() {
            return loadedFiles;
        }

        @Override
        public String lastError() {
            return error;
        }

        @Override
        public void release() {
        }

        /** A canonical mono 16-bit PCM file with {@code payload} bytes of silence. */
        public static byte[] wav(int sampleRate, int payload) {
            byte[] bytes = new byte[44 + payload];
            put(bytes, 0, "RIFF");
            le32(bytes, 4, 36 + payload);
            put(bytes, 8, "WAVE");
            put(bytes, 12, "fmt ");
            le32(bytes, 16, 16);
            bytes[20] = 1;
            bytes[22] = 1;
            le32(bytes, 24, sampleRate);
            le32(bytes, 28, sampleRate * 2);
            bytes[32] = 2;
            bytes[34] = 16;
            put(bytes, 36, "data");
            le32(bytes, 40, payload);
            return bytes;
        }

        private static void put(byte[] bytes, int offset, String ascii) {
            for (int i = 0; i < 4; i++) bytes[offset + i] = (byte) ascii.charAt(i);
        }

        private static void le32(byte[] bytes, int offset, int value) {
            for (int i = 0; i < 4; i++) bytes[offset + i] = (byte) (value >>> (8 * i));
        }
    }

    /** The stored default character: one int, plus a counter so a test can see the write. */
    public static final class Defaults implements DefaultVoiceRepository {
        public int styleId = NONE;
        public int writes;

        @Override
        public int styleId() {
            return styleId;
        }

        @Override
        public void save(int styleId) {
            this.styleId = styleId;
            writes++;
        }
    }
}
