package app.voicevox.tts.application.speech;

import app.voicevox.tts.application.port.AppLog;
import app.voicevox.tts.application.port.SpeechEngine;
import app.voicevox.tts.application.voice.VoiceSelectionService;
import app.voicevox.tts.domain.speech.TextChunker;
import app.voicevox.tts.domain.speech.WavAudio;
import app.voicevox.tts.domain.voice.VoiceStyle;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Application service: speaking one utterance — pick the voice, cut the text into sentences,
 * synthesise them one ahead of playback and stream the audio out.
 *
 * <p>This is everything the system TTS contract asks of the engine apart from the Android
 * types themselves; {@code presentation.tts.VoicevoxTtsService} only translates a
 * {@code SynthesisRequest} into a call here and a {@code SynthesisCallback} into an
 * {@link AudioSink}.
 *
 * <p>One instance per bound service: it owns the producer thread and the cancellation counter.
 */
public final class SpeechSynthesisService {

    /** At most this many sentences wait, already synthesised, ahead of the one playing. */
    private static final int LOOKAHEAD = 2;

    /** Sample rate declared for an utterance with nothing to pronounce. */
    private static final int SILENT_SAMPLE_RATE = 24000;

    /** Queue markers; compared by identity, never by content. */
    private static final byte[] END = new byte[0];
    private static final byte[] FAILED = new byte[0];

    private final VoiceSelectionService voices;
    private final SpeechEngine engine;
    private final AppLog log;

    /**
     * The generation of the utterance allowed to keep talking — see {@link UtteranceToken}.
     *
     * <p>{@code stop()} is process-wide by contract — {@code TextToSpeechService.onStop} backs
     * every client's {@code stop()} — so it intentionally cancels all in-flight utterances
     * rather than trying to guess which client asked. {@code onStop} arrives on a binder
     * thread while the synthesis thread is busy, which is why this is atomic.
     */
    private final UtteranceToken token = new UtteranceToken();

    /**
     * Synthesises the next sentence while the current one is being played.
     *
     * <p>The framework only buffers about half a second of unplayed audio, so pushing one
     * sentence and only then synthesising the next left an audible gap at every full stop.
     */
    private final ExecutorService producer =
            Executors.newSingleThreadExecutor(work -> new Thread(work, "voicevox-producer"));

    public SpeechSynthesisService(VoiceSelectionService voices, SpeechEngine engine, AppLog log) {
        this.voices = voices;
        this.engine = engine;
        this.log = log;
    }

    /**
     * Brings the core up front: onnxruntime plus the dictionary is under a second, and it
     * keeps the first utterance from paying for it. Voice models are left to the moment they
     * are actually needed — each one costs about 230 MB of native memory.
     *
     * <p>A failure here must not take the process down: an uncaught error on this thread would
     * kill the engine process and the engine would look dead to the system instead of merely
     * unready.
     */
    public void warmUpInBackground() {
        new Thread(() -> {
            try {
                engine.warmUp();
            } catch (Throwable t) {
                log.error("warmup failed", t);
            }
        }, "voicevox-warmup").start();
    }

    /** Invalidates every utterance that is in flight. */
    public void stop() {
        token.cancel();
    }

    public void shutdown() {
        token.cancel();
        producer.shutdownNow();
        engine.release();
    }

    /**
     * Speaks {@code text} into {@code sink}, blocking until it is played, stopped or failed.
     * Ends the sink with exactly one {@link AudioSink#done()} or {@link AudioSink#error}.
     *
     * @param voiceName what the caller asked for; empty for every browser
     * @param speed     rate multiplier, 1.0 = normal
     * @param pitch     frequency ratio, 1.0 = normal
     */
    public void speak(String text, String voiceName, float speed, float pitch, AudioSink sink) {
        final int generation = token.begin();
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            sink.error(SpeechError.INVALID_REQUEST);
            return;
        }

        final int styleId;
        try {
            VoiceStyle picked = voices.resolve(voiceName, voices.availableStyles());
            styleId = picked == null ? -1 : picked.id;

            // Inside the guard: ensureModel reaches JNI, and an exception on this thread would
            // leave the caller's sink without a done()/error().
            if (styleId < 0) {
                log.error("no voice pack available", null);
                sink.error(SpeechError.NOT_INSTALLED);
                return;
            }
            if (!engine.ensureModel(styleId)) {
                log.error("model not available: " + engine.lastError(), null);
                sink.error(SpeechError.SYNTHESIS);
                return;
            }
        } catch (Throwable t) {
            log.error("cannot prepare synthesis", t);
            sink.error(SpeechError.SYNTHESIS);
            return;
        }
        List<String> chunks = TextChunker.split(trimmed);
        log.info("synthesize style=" + styleId + " speed=" + speed
                + " chars=" + trimmed.length() + " chunks=" + chunks.size());

        // The producer runs one or two sentences ahead; this thread only pushes audio. The
        // native core serialises calls anyway, so this overlaps synthesis with playback, not
        // two syntheses with each other.
        BlockingQueue<byte[]> ready = new ArrayBlockingQueue<>(LOOKAHEAD);
        Future<?> production;
        try {
            production = producer.submit(() -> produce(chunks, styleId, speed, pitch,
                    generation, ready));
        } catch (Throwable t) {
            log.error("cannot start synthesis", t);
            sink.error(SpeechError.SYNTHESIS);
            return;
        }

        try {
            if (push(sink, ready, generation, production)) {
                sink.done();
            }
        } finally {
            production.cancel(true);
        }
    }

    /** Fills {@code ready} with one WAV per chunk, then {@link #END} — or {@link #FAILED}. */
    private void produce(List<String> chunks, int styleId, float speed, float pitch,
                         int generation, BlockingQueue<byte[]> ready) {
        try {
            for (String chunk : chunks) {
                if (token.isCancelled(generation)) return;
                // Only possible for a text with nothing to pronounce at all ("……"): the
                // chunker folds such pieces into their neighbour otherwise.
                if (!TextChunker.isSpeakable(chunk)) continue;
                byte[] wav = engine.synthesize(chunk, styleId, speed, pitch, 1.0f);
                if (wav == null) {
                    log.warn("no audio for chunk of " + chunk.length() + " chars", null);
                    ready.put(FAILED);
                    return;
                }
                ready.put(wav);
            }
            ready.put(END);
        } catch (InterruptedException stopped) {
            // The consumer gave up (stop, error, or a refused buffer): nothing left to do.
        } catch (Throwable t) {
            log.error("synthesis crashed", t);
            ready.offer(FAILED);
        }
    }

    /**
     * Streams what the producer delivers until {@link #END}.
     *
     * <p>Every exit has to end the sink: the framework waits for exactly one done()/error(),
     * and the caller's onDone/onError never fires without it. A dropped termination is what
     * leaves a caller stuck in "speaking" forever, so a stop, a failed start and a refused
     * buffer all report an error here; true means the caller still owes the done().
     */
    private boolean push(AudioSink sink, BlockingQueue<byte[]> ready, int generation,
                         Future<?> production) {
        boolean started = false;
        long pushStarted = 0;
        try {
            while (true) {
                if (token.isCancelled(generation)) {
                    sink.error(SpeechError.STOPPED);
                    return false;
                }
                byte[] wav = ready.poll(100, TimeUnit.MILLISECONDS);
                if (wav == null) {
                    // A producer that died without managing to queue FAILED would otherwise
                    // leave this loop waiting forever. END is queued before it finishes, so a
                    // finished producer with an empty queue means a lost failure.
                    if (production.isDone() && ready.isEmpty()) {
                        sink.error(SpeechError.SYNTHESIS);
                        return false;
                    }
                    continue;
                }
                if (wav == END) break;
                WavAudio audio = wav == FAILED ? null : WavAudio.parse(wav);
                if (audio == null) {
                    sink.error(SpeechError.SYNTHESIS);
                    return false;
                }
                if (!started) {
                    log.info("stream format: rate=" + audio.sampleRate
                            + " channels=" + audio.channels + " bytes=" + audio.length
                            + " offset=" + audio.offset);
                    if (!sink.start(audio.sampleRate, audio.channels)) {
                        sink.error(SpeechError.OUTPUT);
                        return false;
                    }
                    started = true;
                    pushStarted = System.currentTimeMillis();
                }
                int max = Math.max(1024, sink.maxBufferSize());
                for (int offset = 0; offset < audio.length; offset += max) {
                    if (token.isCancelled(generation)) {
                        sink.error(SpeechError.STOPPED);
                        return false;
                    }
                    int size = Math.min(max, audio.length - offset);
                    if (!sink.write(wav, audio.offset + offset, size)) {
                        sink.error(SpeechError.OUTPUT);
                        return false;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sink.error(SpeechError.STOPPED);
            return false;
        } catch (Throwable t) {
            log.error("playback crashed", t);
            sink.error(SpeechError.SYNTHESIS);
            return false;
        }

        if (!started) {
            // Nothing pronounceable at all, e.g. a lone "……": an empty but successful utterance.
            if (!sink.start(SILENT_SAMPLE_RATE, 1)) {
                sink.error(SpeechError.OUTPUT);
                return false;
            }
            return true;
        }
        // Wall time spent handing PCM to the sink tracks real playback time, so a figure far
        // above the clip length means the sample rate we declared was ignored.
        log.info("push finished in " + (System.currentTimeMillis() - pushStarted) + "ms");
        return true;
    }
}
