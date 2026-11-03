package app.voicevox.tts.presentation.settings;

import app.voicevox.tts.application.port.SamplePlayer;
import app.voicevox.tts.application.port.SpeechEngine;
import app.voicevox.tts.application.voice.VoicePackService;
import app.voicevox.tts.application.voice.VoiceSelectionService;
import app.voicevox.tts.domain.voice.VoiceCatalog;
import app.voicevox.tts.domain.voice.VoiceNames;
import app.voicevox.tts.domain.voice.VoicePack;
import app.voicevox.tts.domain.voice.VoiceStyle;

import java.util.HashSet;
import java.util.List;

/**
 * The settings screen's presenter.
 *
 * <p>It decides <em>whether</em> something may happen on this screen — previewing a pack that
 * is not on disk turns into a download, deleting asks for confirmation, two downloads never run
 * at once — and it owns every user-facing string. The work itself is the application services';
 * the platform is behind {@link ScreenHost} and the widgets behind {@link SettingsScreen}, so
 * no {@code android.*} type reaches this class and its branches are plain JVM tests.
 */
public final class SettingsPresenter implements SettingsScreen.Callbacks, SamplePlayer.Listener {

    private final ScreenHost host;
    private final VoicePackService packs;
    private final VoiceSelectionService voices;
    private final SpeechEngine engine;
    private final SamplePlayer player;
    private final SettingsScreen view;

    /**
     * What character and style names are called on screen.
     *
     * <p>{@code null} means "ask the phone, every time", which is what production uses: the
     * names the engine lists change with the system language, so a copy pinned at construction
     * could disagree with them after the user switches language. A test passes a language
     * explicitly instead, so no assertion depends on the locale of the machine running it.
     */
    private final String pinnedLanguage;

    /**
     * The pack currently downloading, or null.
     *
     * <p>Volatile because it is written on the download thread and read on the UI thread; a
     * plain field happened to work only because the view repaint carries the write along.
     */
    private volatile String busyFile;

    /** Last percent shown for {@link #busyFile}, so a repaint that rebuilds the cards keeps it. */
    private int busyPercent;

    /** The pack being deleted on a worker thread, or null. UI thread only. */
    private String deletingFile;

    /**
     * True until the first unpack of the bundled resources has finished. Until then a missing
     * dictionary is expected, not a broken install, and the screen says so.
     */
    private volatile boolean preparing;

    public SettingsPresenter(ScreenHost host, VoicePackService packs,
                             VoiceSelectionService voices, SpeechEngine engine,
                             SamplePlayer player, SettingsScreen view) {
        this(host, packs, voices, engine, player, view, null);
    }

    public SettingsPresenter(ScreenHost host, VoicePackService packs,
                             VoiceSelectionService voices, SpeechEngine engine,
                             SamplePlayer player, SettingsScreen view, String language) {
        this.host = host;
        this.packs = packs;
        this.voices = voices;
        this.engine = engine;
        this.player = player;
        this.view = view;
        this.pinnedLanguage = language;
    }

    /** The language the phone wants right now, or the one a test pinned. */
    private String language() {
        return pinnedLanguage != null ? pinnedLanguage : VoiceNames.language();
    }

    /**
     * First paint, then the unpack a fresh install needs.
     *
     * <p>The dictionary and the bundled pack used to reach the filesystem only when the TTS
     * service happened to be bound, so opening the app right after install reported a missing
     * dictionary and offered to download a pack the APK already carries.
     */
    public void start() {
        player.setListener(this);
        preparing = true;
        refresh();
        host.background("voicevox-prepare", () -> {
            boolean ok;
            try {
                ok = packs.prepare();
            } catch (Throwable t) {
                host.log("prepare failed", t);
                ok = false;
            }
            if (!ok) host.log("prepare failed", null);
            host.post(() -> {
                preparing = false;
                refresh();
            });
        });
        adoptRunningDownload();
    }

    public void stop() {
        player.release();
    }

    /** Re-reads the disk and repaints. Called on resume, after downloads and after deletes. */
    public void refresh() {
        List<VoicePack> all = packs.packs();
        List<String> installed = packs.installedFiles();
        List<VoiceStyle> available = voices.availableStyles(installed);
        view.renderPacks(all, new HashSet<>(installed), player.playingStyleId());
        // The cards were just rebuilt in their idle state; the one downloading must not look
        // tappable until its next progress tick.
        String busy = busyFile;
        if (busy != null) view.setDownloadProgress(busy, true, busyPercent);
        view.renderDefaultVoice(available, effectiveDefault(available));
        view.setSummary(installed.size() + " / " + all.size(), summaryDetail(installed));
        view.setEngineInfo(describeEngines());
    }

    // ------------------------------------------------------------------ view callbacks

    @Override
    public void onOpenSystemSettings() {
        if (!host.openSystemSettings()) {
            view.showMessage("无法打开系统设置，请手动进入 设置 → 无障碍 → 文字转语音。",
                    Tone.WARN);
        }
    }

    @Override
    public void onDownloadRequested(VoicePack pack) {
        download(pack, null);
    }

    @Override
    public void onDeleteRequested(VoicePack pack) {
        confirmDelete(pack);
    }

    @Override
    public void onPreviewRequested(VoicePack pack, VoiceStyle style) {
        previewStyle(pack, style);
    }

    /**
     * Remembers the character a caller lands on when it names none.
     *
     * <p>Repainting from the same rule the engine uses ({@link VoiceSelectionService}) keeps the
     * highlight honest: if the pick is not on disk, the chip that lights up is the one that will
     * actually speak.
     */
    @Override
    public void onDefaultVoiceRequested(VoiceStyle style) {
        if (style == null) return;
        voices.chooseDefault(style);
        List<VoiceStyle> available = voices.availableStyles(packs.installedFiles());
        view.renderDefaultVoice(available, effectiveDefault(available));
        view.showMessage("已把「" + style.displayName(language()) + "」设为默认角色。\n"
                + "网页朗读和没指定角色的应用会用它，浏览器不用重启。", Tone.OK);
    }

    // ------------------------------------------------------------------ player callbacks

    @Override
    public void onStarted(VoiceStyle style) {
        view.updatePlaying(player.playingStyleId());
        view.showMessage("正在试听：" + style.displayName(language()), Tone.OK);
    }

    @Override
    public void onFinished(VoiceStyle style) {
        view.updatePlaying(player.playingStyleId());
        view.showMessage("试听结束：" + style.displayName(language()), Tone.NEUTRAL);
    }

    @Override
    public void onFailure(VoiceStyle style, String detail) {
        view.updatePlaying(player.playingStyleId());
        view.showMessage("试听失败：" + style.displayName(language()) + "（" + detail + "）", Tone.ERROR);
    }

    // ------------------------------------------------------------------ use cases

    /** Plays a character's sample, downloading the pack first when it is not on disk. */
    private void previewStyle(VoicePack pack, VoiceStyle style) {
        if (pack == null) return;
        if (!packs.isInstalled(pack)) {
            view.showMessage("「" + style.characterName(language()) + "」还没下载，先下载再试听…", Tone.INFO);
            download(pack, style);
            return;
        }
        player.toggle(style);
        int playing = player.playingStyleId();
        view.updatePlaying(playing);
        if (playing < 0) {
            view.showMessage("已停止试听", Tone.NEUTRAL);
        } else {
            view.showMessage("正在准备「" + style.displayName(language()) + "」，首次试听需要几秒加载模型…",
                    Tone.INFO);
        }
    }

    /**
     * Downloads one pack. Blocking work happens on its own thread; every UI touch goes back
     * through the view, and progress is reported per card rather than as one global bar.
     */
    public void download(VoicePack pack, VoiceStyle autoplay) {
        String running = busyFile != null ? busyFile : packs.activeDownload();
        if (running != null) {
            view.showMessage("正在下载 " + running + "，请稍候…", Tone.INFO);
            return;
        }
        busyFile = pack.file;
        busyPercent = 0;
        view.setDownloadProgress(pack.file, true, 0);
        view.showMessage("开始下载 " + pack.characters(language()), Tone.INFO);

        host.background("voicevox-download", () -> {
            try {
                packs.download(pack, (stage, done, total) -> {
                    // Clamped: `total` is the server's Content-Length, and a CDN that reports
                    // less than it sends would otherwise paint "137%" on the button.
                    int percent = (int) Math.min(100, done * 100 / Math.max(1, total));
                    host.post(() -> {
                        busyPercent = percent;
                        view.setDownloadProgress(pack.file, true, percent);
                    });
                });
                // Cleared on the UI thread, after the repaint that re-enables the card: the
                // window between here and the completion callback must not look idle, or a tap
                // in it would start a second download of the same 60 MB file.
                host.post(() -> {
                    busyFile = null;
                    // The engine's voice list is built from disk, so the client binding has to
                    // be dropped for the new character to show up in it.
                    player.stop();
                    player.release();
                    refresh();
                    view.showMessage(pack.characters(language()) + " 下载完成，正在试听…", Tone.OK);
                    VoiceStyle first = autoplay != null ? autoplay
                            : pack.defaultStyles().get(0);
                    previewStyle(pack, first);
                });
            } catch (Throwable t) {
                host.log("download failed", t);
                String message = t.getMessage() == null ? t.toString() : t.getMessage();
                host.post(() -> {
                    busyFile = null;
                    view.setDownloadProgress(pack.file, false, 0);
                    view.showMessage("下载失败：" + message + "\n可再次点击重试", Tone.ERROR);
                });
            }
        });
    }

    /**
     * Picks up a download this process is already running for a screen that no longer exists
     * — the Activity before a rotation, a language change, or a second copy of this screen.
     * Without it the new screen showed the pack as idle and offered to download it again.
     */
    private void adoptRunningDownload() {
        String file = packs.activeDownload();
        if (file == null || busyFile != null) return;
        busyFile = file;
        busyPercent = packs.activePercent();
        view.setDownloadProgress(file, true, busyPercent);
        view.showMessage("正在下载 " + file + "…", Tone.INFO);
        host.background("voicevox-download-watch", () -> {
            try {
                while (packs.awaitDownload(file, 500)) {
                    int percent = packs.activePercent();
                    host.post(() -> {
                        busyPercent = percent;
                        view.setDownloadProgress(file, true, percent);
                    });
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            host.post(() -> {
                busyFile = null;
                player.release();
                refresh();
                if (packs.isInstalled(file)) {
                    view.showMessage(file + " 下载完成", Tone.OK);
                } else {
                    view.showMessage(file + " 没有下载完成，可再次点击重试", Tone.WARN);
                }
            });
        });
    }

    private void confirmDelete(VoicePack pack) {
        host.confirm("删除「" + pack.characters(language()) + "」？",
                "删除后这个声音包暂时无法试听和朗读，占用空间 "
                        + Format.megabytes(pack.bytes) + " 会被释放。\n"
                        + "随时点「下载」就能重新装回来。",
                "删除",
                () -> delete(pack));
    }

    /**
     * Deletes without asking; the confirmation lives in {@link #confirmDelete}.
     *
     * <p>The removal itself runs on a worker: unloading a resident model waits for whatever the
     * engine is synthesising or loading, which can take seconds, and this used to run on the
     * UI thread straight from the dialog button.
     */
    public void delete(VoicePack pack) {
        if (deletingFile != null) {
            view.showMessage("正在删除，请稍候…", Tone.INFO);
            return;
        }
        if (pack.file.equals(busyFile)) {
            view.showMessage("「" + pack.characters(language()) + "」正在下载，完成后再删除", Tone.INFO);
            return;
        }
        if (player.playingStyleId() >= 0) {
            VoicePack playingPack = VoiceCatalog.packOfStyle(player.playingStyleId());
            // The pack that is speaking right now is about to lose its file.
            if (playingPack != null && playingPack.file.equals(pack.file)) {
                player.stop();
                player.release();
            }
        }
        deletingFile = pack.file;
        view.showMessage("正在删除「" + pack.characters(language()) + "」…", Tone.INFO);
        host.background("voicevox-delete", () -> {
            Throwable failure = null;
            try {
                packs.delete(pack);
            } catch (Throwable t) {
                host.log("delete failed", t);
                failure = t;
            }
            Throwable error = failure;
            host.post(() -> {
                deletingFile = null;
                refresh();
                if (error != null) {
                    view.showMessage("删除失败：" + error, Tone.ERROR);
                } else if (packs.installedFiles().isEmpty()) {
                    view.showMessage("「" + pack.characters(language()) + "」已删除。现在没有任何声音包，朗读会没有声音；"
                            + "点任意角色的「▶」即可边下载边试听。", Tone.WARN);
                } else {
                    view.showMessage("「" + pack.characters(language()) + "」已删除，随时可以重新下载", Tone.OK);
                }
            });
        });
    }

    // ------------------------------------------------------------------ default voice

    /** What the engine will speak with right now, or -1 when nothing is installed. */
    private int effectiveDefault(List<VoiceStyle> available) {
        VoiceStyle style = voices.defaultStyle(available);
        return style == null ? -1 : style.id;
    }

    // ------------------------------------------------------------------ status text

    private String summaryDetail(List<String> installed) {
        StringBuilder sub = new StringBuilder();
        sub.append("占用 ").append(Format.megabytes(packs.installedBytes()));
        if (engine.isReady()) {
            sub.append(" · 内存驻留 ").append(engine.loadedCount()).append(" 个");
        }
        if (installed.isEmpty()) {
            sub.append(" · 尚无声音，朗读会静音");
        }
        return sub.toString();
    }

    private String describeEngines() {
        StringBuilder text = new StringBuilder();
        if (!packs.isDictionaryReady()) {
            text.append(preparing ? "正在解包内置词典和声音…" : "词典缺失，请重新安装应用");
        } else if (host.isRegisteredEngine()) {
            text.append("系统已识别本引擎，可在 设置 → 无障碍 → 文字转语音 中选用");
        } else {
            text.append("系统未识别本引擎，请检查安装是否完整");
        }
        if (engine.isReady()) {
            text.append("\n核心已加载");
            if (!engine.loadedFiles().isEmpty()) {
                text.append(" · 内存中：").append(String.join("、", engine.loadedFiles()));
            }
        }
        if (!engine.lastError().isEmpty()) {
            text.append("\n错误：").append(engine.lastError());
        }
        return text.toString();
    }
}
