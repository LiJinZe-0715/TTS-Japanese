package app.voicevox.tts.domain.voice;

import java.util.List;

/**
 * Repository: the voice packs installed on this phone.
 *
 * <p>It answers "which packs can speak" and removes one. It says nothing about files, HTTP or
 * the APK, and nothing about the engine either: dropping a model out of memory before its
 * file goes away is a use case ({@code application.voice.VoicePackService#delete}), not
 * something a repository decides behind its caller's back.
 */
public interface VoicePackRepository {

    /**
     * Whether the pack can speak. The bundled pack counts from install time, before its copy
     * out of the APK has run.
     */
    boolean isInstalled(String file);

    default boolean isInstalled(VoicePack pack) {
        return pack != null && isInstalled(pack.file);
    }

    /** Every installed pack, in catalogue order. */
    List<String> installedFiles();

    /** Bytes the installed packs take; a bundled pack still only in the APK counts its size. */
    long installedBytes();

    /** Removes the pack from storage. Blocking. */
    void delete(VoicePack pack);
}
