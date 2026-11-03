package app.voicevox.tts.domain.voice;

/**
 * Domain rule: whether a voice pack on disk is whole enough to use.
 *
 * <p>A rule this small still gets its own type, because getting it wrong is not a cosmetic
 * bug: a pack that is accepted as complete loses its download button, so a truncated file
 * turns into an engine that silently fails every utterance and offers no way to repair it.
 *
 * <p>The rule is "exactly as long as the catalogue says". It once allowed 95%, which handed a
 * 59 MB fragment of a 60 MB model the "already downloaded" state, and later "at least as
 * long", which also accepted a file two concurrent downloads had written past its end. The
 * catalogue sizes come from the same release table as the digests the download is checked
 * against, so there is no stale-table case left to tolerate. This is the cheap check run on
 * every listing; the SHA-256 is verified once, while the file is written.
 */
public final class PackIntegrity {

    private PackIntegrity() {
    }

    /**
     * @param actual   bytes on disk
     * @param expected bytes the catalogue declares, or 0 when it does not know the file
     */
    public static boolean isComplete(long actual, long expected) {
        if (expected <= 0) return actual > 0;
        return actual == expected;
    }

    /** {@link #isComplete(long, long)} against the size the catalogue declares for the pack. */
    public static boolean isComplete(long actual, VoicePack pack) {
        return isComplete(actual, pack == null ? 0 : pack.bytes);
    }
}
