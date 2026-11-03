package app.voicevox.tts.application.voice;

import app.voicevox.tts.application.port.SpeechEngine;
import app.voicevox.tts.application.port.VoicePackInstaller;
import app.voicevox.tts.domain.voice.VoiceCatalog;
import app.voicevox.tts.domain.voice.VoicePack;
import app.voicevox.tts.domain.voice.VoicePackRepository;

import java.io.IOException;
import java.util.List;

/**
 * Application service: managing the voice packs on this phone — listing, installing and
 * removing them.
 *
 * <p>Most of it is a straight read of the repository or the installer. What it adds is the one
 * rule that spans both sides of the app, in {@link #delete}: a model the engine still has
 * mapped must not lose its file underneath it.
 */
public final class VoicePackService {
    private final VoicePackRepository repository;
    private final VoicePackInstaller installer;
    private final SpeechEngine engine;

    public VoicePackService(VoicePackRepository repository, VoicePackInstaller installer,
                            SpeechEngine engine) {
        this.repository = repository;
        this.installer = installer;
        this.engine = engine;
    }

    /** Every pack the catalogue knows about, installed or not. */
    public List<VoicePack> packs() {
        return VoiceCatalog.all();
    }

    public boolean isInstalled(VoicePack pack) {
        return repository.isInstalled(pack);
    }

    public boolean isInstalled(String file) {
        return repository.isInstalled(file);
    }

    public List<String> installedFiles() {
        return repository.installedFiles();
    }

    public long installedBytes() {
        return repository.installedBytes();
    }

    public boolean isDictionaryReady() {
        return installer.isDictionaryReady();
    }

    /** See {@link VoicePackInstaller#prepare()}. */
    public boolean prepare() {
        return installer.prepare();
    }

    /** See {@link VoicePackInstaller#download}. */
    public void download(VoicePack pack, VoicePackInstaller.Progress progress) throws IOException {
        installer.download(pack, progress);
    }

    public String activeDownload() {
        return installer.activeDownload();
    }

    public int activePercent() {
        return installer.activePercent();
    }

    public boolean awaitDownload(String file, long millis) throws InterruptedException {
        return installer.awaitDownload(file, millis);
    }

    /**
     * Removes a pack, dropping its model out of memory first. Blocking: unloading a resident
     * model waits for the synthesis or load the engine is running, which can take seconds.
     *
     * <p>The core keeps the model mapped, and a file that disappears under a live model is how
     * a delete turns into a crash. A load that was already running when the first unload looked
     * finds the file gone and drops the model itself; the second check catches one that
     * finished in between.
     */
    public void delete(VoicePack pack) {
        engine.unload(pack.file);
        repository.delete(pack);
        if (engine.isLoaded(pack.file)) engine.unload(pack.file);
    }
}
