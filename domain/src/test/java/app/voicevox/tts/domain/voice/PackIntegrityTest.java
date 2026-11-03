package app.voicevox.tts.domain.voice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The rule that decides whether a downloaded pack counts as installed.
 *
 * <p>The case worth pinning is the truncated one: those bytes used to pass because the
 * threshold was 95%, which hid the download button on a broken pack and left no way back.
 */
public class PackIntegrityTest {

    @Test
    public void anExactDownloadIsComplete() {
        assertTrue(PackIntegrity.isComplete(60_000_000L, 60_000_000L));
    }

    @Test
    public void aTruncatedDownloadIsNotComplete() {
        assertFalse(PackIntegrity.isComplete(59_000_000L, 60_000_000L),
                "the old 95% rule accepted this");
        assertFalse(PackIntegrity.isComplete(57_000_000L, 60_000_000L));
        assertFalse(PackIntegrity.isComplete(1L, 60_000_000L));
        assertFalse(PackIntegrity.isComplete(0L, 60_000_000L));
    }

    @Test
    public void oneByteShortIsStillRetried() {
        assertFalse(PackIntegrity.isComplete(59_999_999L, 60_000_000L));
    }

    @Test
    public void anOversizedFileIsRejected() {
        // What two writers interleaving into one file produce; the table and the digests come
        // from the same release, so there is no stale size to tolerate any more.
        assertFalse(PackIntegrity.isComplete(61_000_000L, 60_000_000L));
        assertFalse(PackIntegrity.isComplete(60_000_001L, 60_000_000L));
    }

    @Test
    public void anUnknownExpectedSizeFallsBackToNotEmpty() {
        // The catalogue may not know a file; an empty file is never usable, any content is.
        assertFalse(PackIntegrity.isComplete(0L, 0L));
        assertTrue(PackIntegrity.isComplete(1L, 0L));
    }

    @Test
    public void everyCatalogueEntryDeclaresAPositiveSize() {
        for (VoicePack pack : VoiceCatalog.all()) {
            assertTrue(pack.bytes > 0, pack.file + " declares no size");
        }
    }
}
