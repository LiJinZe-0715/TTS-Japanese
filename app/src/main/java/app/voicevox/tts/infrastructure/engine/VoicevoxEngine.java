package app.voicevox.tts.infrastructure.engine;

import android.content.Context;
import android.util.Log;

import app.voicevox.tts.application.port.SpeechEngine;
import app.voicevox.tts.domain.voice.VoiceCatalog;
import app.voicevox.tts.domain.voice.VoicePack;
import app.voicevox.tts.infrastructure.storage.FileVoicePackStore;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * VOICEVOX CORE behind the {@link SpeechEngine} port.
 *
 * <p>Initialisation (onnxruntime plus the dictionary) is expensive, so it happens once. Voice
 * models are a different matter: each .vvm costs roughly 230 MB of native heap, so they are
 * loaded on demand and the least recently used one is evicted once {@link #MAX_LOADED} is
 * reached. The system still sees every installed voice; a voice whose model is not resident
 * just pays a reload on its first utterance.
 *
 * <p>The native side serialises every call behind one mutex, so nothing here runs two core
 * calls in parallel. The Java monitors below exist to keep the bookkeeping consistent and to
 * keep the UI thread off that mutex, not to buy concurrency.
 *
 * <p>The core is one per process, so this class must be too: {@code di.AppContainer} creates
 * the only instance.
 */
public final class VoicevoxEngine implements SpeechEngine {
    private static final String TAG = "VoicevoxEngine";

    /** Two models is about 460 MB of native heap; more risks the low-memory killer. */
    private static final int MAX_LOADED = 2;

    private final Context context;
    private final FileVoicePackStore store;

    private final Object lock = new Object();
    private volatile boolean ready;
    private volatile String lastError = "";

    /** vvm file name -> model id, in load order; the eldest entry is evicted first. */
    private final LinkedHashMap<String, String> loaded = new LinkedHashMap<>();

    /**
     * Bumped by {@link #release()}, guarded by {@link #loaded}.
     *
     * <p>A load that was already inside the core when the core was torn down returns an id
     * that belongs to the dead synthesizer. Recording it would make the next core believe the
     * model is resident: {@link #ensureModel} would answer true, every synthesis with that
     * voice would fail, and the unload that should repair it fails too and puts the entry back.
     * A load only records its id when the epoch it started in is still current.
     */
    private int epoch;

    /**
     * Files currently being mapped by some thread, so a second caller waits rather than
     * mapping the same model twice. Guarded by its own monitor, never held across a native call.
     */
    private final Set<String> loading = new HashSet<>();

    public VoicevoxEngine(Context context, FileVoicePackStore store) {
        this.context = context.getApplicationContext();
        this.store = store;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public String lastError() {
        return lastError;
    }

    /**
     * Reads the resident-model list without touching the native mutex.
     *
     * <p>This is the accessor the UI must use: the settings screen repaints while a 230 MB
     * model is being mapped or a sentence is being synthesised, and those keep the core busy
     * for seconds. A copy taken under the map's own monitor cannot wait on them.
     */
    @Override
    public List<String> loadedFiles() {
        synchronized (loaded) {
            return new ArrayList<>(loaded.keySet());
        }
    }

    @Override
    public int loadedCount() {
        synchronized (loaded) {
            return loaded.size();
        }
    }

    @Override
    public boolean isLoaded(String file) {
        synchronized (loaded) {
            return loaded.containsKey(file);
        }
    }

    @Override
    public void warmUp() {
        ensureReady();
    }

    /** Blocking; safe from any thread. Brings up the core, without loading any voice model. */
    private boolean ensureReady() {
        if (ready) return true;
        if (!VoicevoxJni.load()) {
            lastError = VoicevoxJni.loadError();
            return false;
        }
        if (ready) return true;
        synchronized (lock) {
            if (ready) return true;
            try {
                if (!store.prepare()) {
                    lastError = "语音资源未就绪，请先打开 VOICEVOX TTS 完成下载";
                    return false;
                }
                String ortPath = context.getApplicationInfo().nativeLibraryDir
                        + "/libvoicevox_onnxruntime.so";
                int threads = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors()));
                long started = System.currentTimeMillis();
                String error = VoicevoxJni.nativeInit(ortPath,
                        store.dictDir().getAbsolutePath(), threads);
                if (error != null) {
                    lastError = error;
                    Log.e(TAG, "init failed: " + error);
                    return false;
                }
                lastError = "";
                ready = true;
                Log.i(TAG, "init took " + (System.currentTimeMillis() - started) + "ms");
                return true;
            } catch (Throwable t) {
                lastError = t.toString();
                Log.e(TAG, "init crashed", t);
                return false;
            }
        }
    }

    @Override
    public boolean ensureModel(int styleId) {
        VoicePack pack = VoiceCatalog.packOfStyle(styleId);
        if (pack == null) {
            lastError = "未知的 style id " + styleId;
            return false;
        }
        return ensureModel(pack.file);
    }

    /**
     * Makes the file's model resident, evicting the eldest first when already at capacity.
     *
     * <p>Capacity counts loads that are still in flight: two threads that each saw one free
     * slot would otherwise both load and leave three models resident. Eviction is retried in a
     * small loop, because another thread may have freed the victim already and "already gone"
     * is not a reason to fail.
     */
    private boolean ensureModel(String file) {
        File path = store.vvmFile(file);
        for (int attempt = 0; attempt < 4; attempt++) {
            String evict = null;
            synchronized (loading) {
                synchronized (loaded) {
                    if (loaded.containsKey(file)) {
                        // Touch so the LRU order reflects actual use.
                        String id = loaded.remove(file);
                        loaded.put(file, id);
                        return true;
                    }
                    if (!loading.contains(file)
                            && loaded.size() + loading.size() >= MAX_LOADED) {
                        if (!loaded.isEmpty()) evict = loaded.keySet().iterator().next();
                    }
                }
                if (evict == null && !loading.contains(file)
                        && loading.size() >= MAX_LOADED) {
                    // Every slot is taken by a load still in flight: wait for one to finish.
                    try {
                        loading.wait(30_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        lastError = "加载被中断";
                        return false;
                    }
                    continue;
                }
            }
            if (evict != null) {
                evict(evict);
                continue;
            }
            if (!ensureReady()) return false;
            if (!path.exists()) {
                lastError = file + " 尚未下载";
                return false;
            }
            // Two threads can reach this point for the same file. The second would map another
            // 230 MB and then overwrite the first's id, leaking a model that nothing can free.
            // The loser waits for the winner instead.
            int startedIn;
            synchronized (loading) {
                if (loading.contains(file)) {
                    try {
                        loading.wait(30_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        lastError = "加载被中断";
                        return false;
                    }
                    continue;
                }
                loading.add(file);
                synchronized (loaded) {
                    startedIn = epoch;
                }
            }
            try {
                long started = System.currentTimeMillis();
                String id = VoicevoxJni.nativeLoadModel(path.getAbsolutePath());
                if (id == null || id.startsWith("!")) {
                    lastError = id == null ? "加载失败" : id.substring(1);
                    Log.e(TAG, "load " + file + " failed: " + lastError);
                    return false;
                }
                synchronized (loaded) {
                    if (epoch != startedIn) {
                        // The core this id belongs to has been released; see `epoch`.
                        lastError = "加载期间引擎已释放";
                        return false;
                    }
                    loaded.put(file, id);
                }
                // A delete may have removed the file while it was being mapped. It found
                // nothing resident to drop, so the model would otherwise stay in memory for a
                // pack the user just deleted.
                if (!path.exists()) {
                    evict(file);
                    lastError = file + " 已被删除";
                    return false;
                }
                lastError = "";
                Log.i(TAG, "loaded " + file + " in "
                        + (System.currentTimeMillis() - started) + "ms");
                return true;
            } finally {
                synchronized (loading) {
                    loading.remove(file);
                    loading.notifyAll();
                }
            }
        }
        lastError = file + " 反复淘汰失败，无法腾出内存位置";
        Log.e(TAG, lastError);
        return false;
    }

    /**
     * Drops one model from memory.
     *
     * <p>The entry is only removed for good after the core accepted the unload: a failed one
     * puts it back, so the next eviction retries instead of forgetting a model that is still
     * holding 230 MB — unless the core was released in between, in which case the id is dead.
     */
    private void evict(String file) {
        String id;
        int claimedIn;
        synchronized (loaded) {
            // Claim the entry first: two threads freeing the same 230 MB model at once would
            // otherwise both see it loaded and the second unload would fail on a stale id.
            id = loaded.remove(file);
            claimedIn = epoch;
        }
        if (id == null) return;
        String error = VoicevoxJni.nativeUnloadModel(id);
        if (error != null) {
            Log.w(TAG, "unload " + file + " failed: " + error);
            synchronized (loaded) {
                if (epoch == claimedIn && !loaded.containsKey(file)) loaded.put(file, id);
            }
            return;
        }
        Log.i(TAG, "evicted " + file);
    }

    @Override
    public void unload(String file) {
        evict(file);
    }

    /**
     * Tears the core down.
     *
     * <p>Lock order is the same as everywhere else — {@code loading}, then {@code loaded}, then
     * {@code lock} — and none of them is held across another's native call. The epoch is
     * bumped before the core goes away, so a load that is still running finds out and drops
     * its id instead of recording it for the next core.
     */
    @Override
    public void release() {
        synchronized (loading) {
            synchronized (loaded) {
                loaded.clear();
                epoch++;
            }
            loading.notifyAll();
        }
        synchronized (lock) {
            if (!ready) return;
            ready = false;
            try {
                VoicevoxJni.nativeRelease();
            } catch (Throwable t) {
                Log.w(TAG, "release failed", t);
            }
        }
    }

    /**
     * Synthesises one sentence and returns a complete WAV byte array, or null on failure.
     *
     * @param pitch the client's pitch as a frequency ratio (1.0 = normal), i.e. what
     *              {@link android.speech.tts.TextToSpeech#setPitch} took. It is NOT the
     *              core's pitchScale: that field is 0.0 at normal pitch and only stays
     *              usable within roughly ±0.15, so a ratio of 1.0 fed in directly pushes
     *              the voice so far up that the vocoder collapses into a buzz.
     */
    @Override
    public byte[] synthesize(String text, int styleId, float speed, float pitch, float volume) {
        if (!ready || text == null || text.isEmpty()) return null;
        try {
            // Standard UTF-8; an unpaired surrogate becomes '?' rather than invalid bytes.
            byte[] query = VoicevoxJni.nativeAudioQuery(
                    text.getBytes(StandardCharsets.UTF_8), styleId);
            if (query == null) return null;
            JSONObject json = new JSONObject(new String(query, StandardCharsets.UTF_8));
            json.put("speedScale", clamp(speed, 0.5f, 2.0f));
            json.put("pitchScale", toPitchScale(pitch));
            json.put("volumeScale", clamp(volume, 0.0f, 2.0f));
            json.put("outputSamplingRate", 24000);
            json.put("outputStereo", false);
            return VoicevoxJni.nativeSynthesis(
                    json.toString().getBytes(StandardCharsets.UTF_8), styleId);
        } catch (Throwable t) {
            Log.e(TAG, "synthesis failed", t);
            return null;
        }
    }

    /** Ratio to the core's logarithmic offset, kept inside the range the vocoder survives. */
    private static float toPitchScale(float ratio) {
        if (ratio <= 0f || ratio == 1f) return 0f;
        float octaves = (float) (Math.log(ratio) / Math.log(2.0));
        return clamp(octaves * 0.15f, -0.15f, 0.15f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
