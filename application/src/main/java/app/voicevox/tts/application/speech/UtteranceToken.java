package app.voicevox.tts.application.speech;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Which utterance is still allowed to push audio, expressed as a generation counter.
 *
 * <p>The framework calls {@code onSynthesizeText} on its single synthesis thread, but
 * {@code onStop} arrives on a binder thread while that thread is busy. The original code kept
 * a plain {@code volatile int} and used {@code ++} on it, a read-modify-write that a stop
 * racing a new utterance could lose, leaving the utterance it was meant to stop running.
 * {@link AtomicInteger#incrementAndGet()} makes every begin and cancel count, and each
 * utterance compares against the live counter at every chunk.
 */
final class UtteranceToken {
    private final AtomicInteger latest = new AtomicInteger();

    /** Starts a new utterance; every older one is now stale. */
    int begin() {
        return latest.incrementAndGet();
    }

    /** Invalidates every utterance started so far. */
    void cancel() {
        latest.incrementAndGet();
    }

    /** Whether the utterance that was handed {@code token} is allowed to keep going. */
    boolean isCancelled(int token) {
        return token != latest.get();
    }
}
