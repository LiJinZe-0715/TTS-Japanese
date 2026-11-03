package app.voicevox.tts.application.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.voicevox.tts.application.fake.Fakes;
import app.voicevox.tts.domain.voice.VoiceCatalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The cross-port rule of deleting a pack: the engine lets go of the model before the file goes.
 * It used to live inside the file repository, out of sight of every test.
 */
public class VoicePackServiceTest {
    private Fakes.Packs packs;
    private Fakes.Engine engine;
    private VoicePackService service;

    @BeforeEach
    public void setUp() {
        packs = new Fakes.Packs();
        packs.installed.add("0.vvm");
        engine = new Fakes.Engine();
        service = new VoicePackService(packs, packs, engine);
    }

    @Test
    public void theModelIsUnloadedBeforeTheFileIsDeleted() {
        List<String> order = new ArrayList<>();
        engine.duringUnload = () -> order.add("unload");
        packs.duringDelete = () -> order.add("delete");

        service.delete(VoiceCatalog.bundled());

        assertEquals(List.of("unload", "delete"), order);
        assertFalse(packs.installed.contains("0.vvm"));
    }

    @Test
    public void aModelLoadedDuringTheDeleteIsUnloadedAgain() {
        // A load that finished between the first unload and the file removal.
        packs.duringDelete = () -> engine.loadedFiles.add("0.vvm");

        service.delete(VoiceCatalog.bundled());

        assertEquals(List.of("0.vvm", "0.vvm"), engine.unloaded);
        assertFalse(engine.isLoaded("0.vvm"));
    }

    @Test
    public void aModelThatStaysUnloadedIsUnloadedOnce() {
        service.delete(VoiceCatalog.bundled());

        assertEquals(List.of("0.vvm"), engine.unloaded);
    }

    @Test
    public void everyCataloguedPackIsListed() {
        assertEquals(25, service.packs().size());
        assertTrue(service.isInstalled(VoiceCatalog.bundled()));
        assertFalse(service.isInstalled("1.vvm"));
    }
}
