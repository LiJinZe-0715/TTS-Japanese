package app.voicevox.tts.application.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.voicevox.tts.application.fake.Fakes;
import app.voicevox.tts.application.port.AppLog;
import app.voicevox.tts.application.voice.VoiceSelectionService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The streaming pipeline the TTS service used to keep inside its Android callbacks, where no
 * test could reach it. The rule every case checks: an utterance ends with exactly one done()
 * or error().
 */
public class SpeechSynthesisServiceTest {
    private Fakes.Packs packs;
    private Fakes.Engine engine;
    private SpeechSynthesisService service;
    private Sink sink;

    @BeforeEach
    public void setUp() {
        packs = new Fakes.Packs();
        packs.installed.add("0.vvm");
        engine = new Fakes.Engine();
        service = new SpeechSynthesisService(
                new VoiceSelectionService(packs, new Fakes.Defaults()), engine, AppLog.NONE);
        sink = new Sink();
    }

    @AfterEach
    public void tearDown() {
        service.shutdown();
    }

    @Test
    public void eachSentenceIsSynthesisedAndStreamed() {
        service.speak("こんにちは。元気？", "", 1f, 1f, sink);

        assertEquals(List.of("こんにちは。", "元気？"), engine.spoken);
        assertEquals(List.of("start:24000:1", "write:480", "write:480", "done"), sink.calls);
    }

    @Test
    public void anEmptyNameSpeaksWithTheFirstInstalledCharacter() {
        service.speak("テスト", "", 1f, 1f, sink);

        assertEquals(List.of(2), engine.ensured);
    }

    @Test
    public void blankTextIsAnInvalidRequest() {
        service.speak("   ", "", 1f, 1f, sink);

        assertEquals(List.of("error:INVALID_REQUEST"), sink.calls);
    }

    @Test
    public void noPackLeftMeansNotInstalled() {
        packs.installed.clear();

        service.speak("テスト", "", 1f, 1f, sink);

        assertEquals(List.of("error:NOT_INSTALLED"), sink.calls);
    }

    @Test
    public void aModelThatCannotLoadIsASynthesisError() {
        engine.modelAvailable = false;

        service.speak("テスト", "", 1f, 1f, sink);

        assertEquals(List.of("error:SYNTHESIS"), sink.calls);
    }

    @Test
    public void noAudioFromTheEngineIsASynthesisError() {
        engine.audio = null;

        service.speak("テスト", "", 1f, 1f, sink);

        assertEquals(List.of("error:SYNTHESIS"), sink.calls);
    }

    @Test
    public void aRefusedFormatIsAnOutputError() {
        sink.acceptStart = false;

        service.speak("テスト", "", 1f, 1f, sink);

        assertEquals(List.of("start:24000:1", "error:OUTPUT"), sink.calls);
    }

    @Test
    public void nothingPronounceableIsAnEmptySuccess() {
        service.speak("……", "", 1f, 1f, sink);

        assertTrue(engine.spoken.isEmpty());
        assertEquals(List.of("start:24000:1", "done"), sink.calls);
    }

    @Test
    public void aStopWhileStreamingEndsTheUtteranceAsStopped() {
        sink.onWrite = () -> service.stop();

        service.speak("一。二。三。", "", 1f, 1f, sink);

        assertEquals("error:STOPPED", sink.calls.get(sink.calls.size() - 1));
        assertEquals(1, sink.terminations());
    }

    /** Records every call, in order. */
    private static final class Sink implements AudioSink {
        final List<String> calls = new ArrayList<>();
        boolean acceptStart = true;
        Runnable onWrite;

        @Override
        public int maxBufferSize() {
            return 4096;
        }

        @Override
        public boolean start(int sampleRate, int channels) {
            calls.add("start:" + sampleRate + ":" + channels);
            return acceptStart;
        }

        @Override
        public boolean write(byte[] buffer, int offset, int length) {
            calls.add("write:" + length);
            if (onWrite != null) onWrite.run();
            return true;
        }

        @Override
        public void done() {
            calls.add("done");
        }

        @Override
        public void error(SpeechError error) {
            calls.add("error:" + error);
        }

        long terminations() {
            return calls.stream().filter(c -> c.equals("done") || c.startsWith("error")).count();
        }
    }
}
