package app.voicevox.tts.infrastructure.storage;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import app.voicevox.tts.application.port.VoicePackInstaller;
import app.voicevox.tts.domain.voice.PackIntegrity;
import app.voicevox.tts.domain.voice.VoiceCatalog;
import app.voicevox.tts.domain.voice.VoicePack;
import app.voicevox.tts.domain.voice.VoicePackRepository;
import app.voicevox.tts.infrastructure.net.VoicePackSource;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The voice packs and the dictionary, kept as files in the app's private directory. Implements
 * both the repository (which packs are installed) and the installer (how they get there).
 *
 * <p>The default voice model (0.vvm) and the Open JTalk dictionary ship inside the APK, so the
 * engine works the moment it is installed. Every .vvm is downloaded on demand into
 * {@code filesDir/voicevox/vvm/} and every one of them, the bundled model included, can be
 * deleted again from the settings screen.
 *
 * <p>Deleting the bundled model leaves a {@code .removed} marker next to it. Without one the
 * next launch would copy it back out of the APK, and the delete button would look broken.
 *
 * <p>Both kinds of resource have to end up on the filesystem: the core takes real paths, and
 * the dictionary is a directory of thousands of small files that cannot be read through the
 * asset manager.
 *
 * <p>Every write to one model file — copying it out of the APK, downloading it, deleting it —
 * happens under that file's own lock, and every write goes to a temporary file of its own.
 * The UI, the engine's warm-up and a recreated settings screen can all ask for the same file
 * at once; before, they shared one {@code .part} path and could interleave into a corrupt model.
 *
 * <p>One instance per process ({@code di.AppContainer}): the locks and the "one download at a
 * time" rule only hold if every screen and the engine share it.
 */
public final class FileVoicePackStore implements VoicePackRepository, VoicePackInstaller {
    private static final String TAG = "VoicePackStore";
    private static final String DICT_MARKER = ".ready";
    private static final String REMOVED = ".removed";
    private static final String PART = ".part";

    private final Context context;

    /** One monitor per model file name. */
    private final Map<String, Object> fileLocks = new ConcurrentHashMap<>();

    /** Serialises {@link #prepare}: the engine's warm-up and the settings screen both call it. */
    private final Object prepareLock = new Object();

    /** The download running in this process, or null. One at a time, for every screen. */
    private volatile String activeDownload;
    private volatile int activePercent;
    private final Object downloadLock = new Object();

    /** Whether the APK carries the bundled model; read once, it cannot change at runtime. */
    private volatile Boolean bundledAsset;

    public FileVoicePackStore(Context context) {
        // Application context: downloads outlive any single screen.
        this.context = context.getApplicationContext();
    }

    private Object lockFor(String file) {
        return fileLocks.computeIfAbsent(file, key -> new Object());
    }

    // ------------------------------------------------------------------ layout

    private File root() {
        return new File(context.getFilesDir(), "voicevox");
    }

    public File dictDir() {
        return new File(root(), "dict");
    }

    private File vvmDir() {
        return new File(root(), "vvm");
    }

    public File vvmFile(String file) {
        return new File(vvmDir(), file);
    }

    /** Set when the user deletes a model that ships in the APK, so it stays deleted. */
    private File removedMarker(String file) {
        return new File(vvmDir(), file + REMOVED);
    }

    // ------------------------------------------------------------------ repository

    /**
     * Whether the file can speak, now or after {@link #prepare} has run.
     *
     * <p>The bundled model counts from the moment the app is installed: it is in the APK, and
     * the 59 MB copy to the filesystem is an implementation detail. Asking the filesystem alone
     * made a fresh install report "not downloaded", offer a download of a file the APK already
     * carries, and fail the system's CHECK_TTS_DATA until the engine happened to be bound.
     */
    @Override
    public boolean isInstalled(String file) {
        if (isOnDisk(file)) return true;
        return VoiceCatalog.BUNDLED_FILE.equals(file) && isBundledAvailable();
    }

    /** Catalogue order, every file that can speak (see {@link #isInstalled(String)}). */
    @Override
    public List<String> installedFiles() {
        List<String> files = new ArrayList<>();
        for (VoicePack pack : VoiceCatalog.all()) {
            if (isInstalled(pack.file)) files.add(pack.file);
        }
        return files;
    }

    /** Bytes the packs take; a bundled model that is still only in the APK counts its size. */
    @Override
    public long installedBytes() {
        long total = 0;
        for (String file : installedFiles()) {
            File target = vvmFile(file);
            VoicePack pack = VoiceCatalog.byFile(file);
            total += target.exists() ? target.length() : (pack == null ? 0 : pack.bytes);
        }
        return total;
    }

    /**
     * Removes a model file, bundled or downloaded.
     *
     * <p>Only touches the filesystem: dropping the model out of memory first is a use case
     * decision, made in {@code VoicePackService.delete}.
     *
     * <p>For the bundled model a marker is left behind so {@link #prepare} does not copy it
     * back out of the APK on the next launch. It is written first: a copy that runs right after
     * the delete must already see it.
     */
    @Override
    public void delete(VoicePack pack) {
        String file = pack.file;
        synchronized (lockFor(file)) {
            if (VoiceCatalog.BUNDLED_FILE.equals(file)) {
                File marker = removedMarker(file);
                File dir = marker.getParentFile();
                if (dir != null && !dir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                }
                try (OutputStream out = new FileOutputStream(marker)) {
                    out.write('1');
                } catch (IOException e) {
                    Log.w(TAG, "cannot write " + marker, e);
                }
            }
            File target = vvmFile(file);
            //noinspection ResultOfMethodCallIgnored
            if (target.exists() && !target.delete()) {
                Log.w(TAG, "cannot delete " + target);
                return;
            }
        }
        Log.i(TAG, "deleted " + file);
    }

    /** On disk and exactly as long as the catalogue says. See {@link PackIntegrity}. */
    private boolean isOnDisk(String file) {
        File target = vvmFile(file);
        if (!target.exists()) return false;
        return PackIntegrity.isComplete(target.length(), VoiceCatalog.byFile(file));
    }

    /** The bundled model is in the APK and the user has not deleted it. */
    private boolean isBundledAvailable() {
        return hasBundledAsset() && !removedMarker(VoiceCatalog.BUNDLED_FILE).exists();
    }

    private boolean hasBundledAsset() {
        Boolean known = bundledAsset;
        if (known != null) return known;
        boolean found = false;
        try {
            String[] names = context.getAssets().list("vvm");
            found = names != null && Arrays.asList(names).contains(VoiceCatalog.BUNDLED_FILE);
        } catch (IOException e) {
            Log.w(TAG, "cannot list assets", e);
        }
        bundledAsset = found;
        return found;
    }

    // ------------------------------------------------------------------ installer: bundled data

    @Override
    public boolean isDictionaryReady() {
        return new File(dictDir(), DICT_MARKER).exists();
    }

    /**
     * Makes the bundled resources available on the filesystem. Never touches the network:
     * a missing download is reported by the caller rather than fetched behind its back.
     *
     * <p>Only the dictionary is required. A voice model is not: the user is free to delete
     * all of them, and failing here would take the whole engine down with them.
     *
     * <p>Called by the engine before the core starts and by the settings screen when it opens,
     * so a fresh install is unpacked whichever comes first.
     */
    @Override
    public boolean prepare() {
        synchronized (prepareLock) {
            try {
                File root = root();
                if (!root.exists() && !root.mkdirs()) return false;
                unpackDict();
                copyBundledVvm();
                return isDictionaryReady();
            } catch (IOException e) {
                Log.e(TAG, "prepare failed", e);
                return false;
            }
        }
    }

    /**
     * What the dictionary marker records: the install it was extracted from.
     *
     * <p>A bare "done" marker survived app updates, so a newer dictionary in a newer APK would
     * never have replaced the old one on disk. {@code lastUpdateTime} changes with every
     * install or update of the package, which is exactly when the asset may have changed.
     */
    private String installStamp() {
        try {
            return Long.toString(context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).lastUpdateTime);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return "1";
        }
    }

    private void unpackDict() throws IOException {
        File dict = dictDir();
        File marker = new File(dict, DICT_MARKER);
        String stamp = installStamp();
        if (marker.exists() && stamp.equals(readSmall(marker))) return;
        //noinspection ResultOfMethodCallIgnored
        marker.delete();
        if (!dict.exists() && !dict.mkdirs()) throw new IOException("cannot create " + dict);

        try (InputStream raw = context.getAssets().open("open_jtalk_dict.zip");
             ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                File target = resolveInside(dict, entry.getName());
                if (entry.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    target.mkdirs();
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("cannot create " + parent);
                }
                try (OutputStream out = new FileOutputStream(target)) {
                    VerifiedCopy.copy(zip, out, null, null, 0, null);
                }
            }
        }
        // Marker last: an interrupted extraction is retried instead of half-used.
        try (OutputStream out = new FileOutputStream(marker)) {
            out.write(stamp.getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static String readSmall(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64];
            int read = in.read(buffer);
            return read <= 0 ? "" : new String(buffer, 0, read, StandardCharsets.US_ASCII).trim();
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Maps a zip entry name onto a path that is guaranteed to stay under {@code base}.
     *
     * <p>Entry names are attacker-controlled in a general archive reader, and
     * {@code new File(base, "../../x")} happily escapes the directory — the classic zip-slip.
     * The dictionary comes from this project's own assets, so this is not the likely attack,
     * but it is cheap, it runs before anything is written, and it keeps a future change to a
     * downloaded dictionary from turning into a file-write primitive.
     */
    private static File resolveInside(File base, String entryName) throws IOException {
        String name = entryName.replace('\\', '/');
        while (name.startsWith("/")) name = name.substring(1);
        if (name.isEmpty() || name.contains("../") || name.equals("..")
                || name.startsWith("../")) {
            throw new IOException("unsafe zip entry: " + entryName);
        }
        File resolved = new File(base, name);
        String basePath = base.getCanonicalPath();
        String targetPath = resolved.getCanonicalPath();
        if (!targetPath.equals(basePath) && !targetPath.startsWith(basePath + File.separator)) {
            throw new IOException("zip entry escapes " + base + ": " + entryName);
        }
        return resolved;
    }

    private void copyBundledVvm() throws IOException {
        String file = VoiceCatalog.BUNDLED_FILE;
        synchronized (lockFor(file)) {
            if (!isBundledAvailable()) return;
            if (isOnDisk(file)) return;
            VoicePack pack = VoiceCatalog.bundled();
            File model = vvmFile(file);
            File temp = newTempFile(file);
            try {
                try (InputStream in = context.getAssets().open("vvm/" + file);
                     OutputStream out = new FileOutputStream(temp)) {
                    VerifiedCopy.copy(in, out, null, null, 0, pack == null ? null : pack.sha256);
                }
                commit(temp, model, pack);
            } finally {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
            Log.i(TAG, "bundled model ready: " + model.length() + " bytes");
        }
    }

    // ------------------------------------------------------------------ installer: downloads

    @Override
    public String activeDownload() {
        return activeDownload;
    }

    @Override
    public int activePercent() {
        return activePercent;
    }

    @Override
    public boolean awaitDownload(String file, long millis) throws InterruptedException {
        synchronized (downloadLock) {
            if (file.equals(activeDownload)) downloadLock.wait(millis);
            return file.equals(activeDownload);
        }
    }

    /**
     * Downloads one optional .vvm. Safe to call again after a partial failure.
     *
     * <p>One download runs per process at a time, whichever screen started it: the flag used
     * to live in the settings screen, so a rotated screen — a new Activity with a new
     * presenter — happily started a second copy of the same 60 MB file. A second request now
     * fails fast with a message instead of racing the first.
     */
    @Override
    public void download(VoicePack pack, Progress progress) throws IOException {
        if (pack == null || VoiceCatalog.byFile(pack.file) == null) {
            throw new IOException("未知的语音模型 " + (pack == null ? null : pack.file));
        }
        synchronized (downloadLock) {
            if (activeDownload != null) throw new IOException("正在下载 " + activeDownload + "，请稍候");
            activeDownload = pack.file;
            activePercent = 0;
        }
        try {
            synchronized (lockFor(pack.file)) {
                fetch(pack, progress);
            }
        } finally {
            synchronized (downloadLock) {
                activeDownload = null;
                downloadLock.notifyAll();
            }
        }
    }

    private void fetch(VoicePack pack, Progress progress) throws IOException {
        File target = vvmFile(pack.file);
        File part = newTempFile(pack.file);
        try {
            HttpURLConnection connection = VoicePackSource.open(pack);
            long total = connection.getContentLengthLong();
            if (total <= 0) total = pack.bytes;
            Progress tracking = (stage, done, all) -> {
                activePercent = (int) Math.min(100, done * 100 / Math.max(1, all));
                if (progress != null) progress.onProgress(stage, done, all);
            };
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 OutputStream out = new FileOutputStream(part)) {
                VerifiedCopy.copy(in, out, tracking, pack.characters(), total, pack.sha256);
            } finally {
                connection.disconnect();
            }
            commit(part, target, pack);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
        }
        // A fresh download undoes an earlier delete, including for the bundled model.
        //noinspection ResultOfMethodCallIgnored
        removedMarker(pack.file).delete();
        Log.i(TAG, "downloaded " + pack.file + " (" + target.length() + " bytes)");
    }

    /**
     * A temporary file no other writer uses, after clearing what an earlier crash left behind.
     * Callers hold the file's lock, so no live writer owns a leftover.
     */
    private File newTempFile(String file) throws IOException {
        File dir = vvmDir();
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File[] stale = dir.listFiles(
                (d, name) -> name.startsWith(file + ".") && name.endsWith(PART));
        if (stale != null) {
            //noinspection ResultOfMethodCallIgnored
            for (File leftover : stale) leftover.delete();
        }
        return File.createTempFile(file + ".", PART, dir);
    }

    /** Length check, then the atomic rename that makes the file visible to everyone else. */
    private static void commit(File temp, File target, VoicePack pack) throws IOException {
        if (!PackIntegrity.isComplete(temp.length(), pack)) {
            long expected = pack == null ? 0 : pack.bytes;
            throw new IOException(target.getName() + " 下载不完整：" + temp.length() + "/" + expected);
        }
        if (!temp.renameTo(target)) throw new IOException("cannot rename " + temp);
    }
}
