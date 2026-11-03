package app.voicevox.tts.application.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The cancellation counter behind {@code SpeechSynthesisService}.
 *
 * <p>The defect this guards against was a {@code volatile int} bumped with {@code ++}: under a
 * race two utterances received the same number and could no longer cancel each other. That is
 * invisible to a single-threaded test, so the interesting case here actually has threads.
 */
public class UtteranceTokenTest {

    @Test
    public void anIdleTokenCancelsNothing() {
        UtteranceToken token = new UtteranceToken();

        int generation = token.begin();

        assertFalse(token.isCancelled(generation));
    }

    @Test
    public void cancelInvalidatesTheUtterancesAlreadyRunning() {
        UtteranceToken token = new UtteranceToken();
        int first = token.begin();

        token.cancel();

        assertTrue(token.isCancelled(first));
    }

    @Test
    public void aNewUtteranceSupersedesThePreviousOne() {
        UtteranceToken token = new UtteranceToken();
        int first = token.begin();
        int second = token.begin();

        assertTrue(token.isCancelled(first));
        assertFalse(token.isCancelled(second));
    }

    @Test
    public void aStopAfterAHealthyUtteranceOnlyAffectsTheStaleOne() {
        UtteranceToken token = new UtteranceToken();
        int stale = token.begin();
        int current = token.begin();
        token.cancel();

        assertTrue(token.isCancelled(stale));
        assertTrue(token.isCancelled(current));
    }

    @Test
    public void concurrentUtterancesNeverShareAGeneration() throws Exception {
        UtteranceToken token = new UtteranceToken();
        int threads = 16;
        Set<Integer> generations = ConcurrentHashMap.newKeySet();
        AtomicInteger duplicates = new AtomicInteger();
        CyclicBarrier start = new CyclicBarrier(threads);
        Thread[] workers = new Thread[threads];

        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                try {
                    start.await();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                if (!generations.add(token.begin())) duplicates.incrementAndGet();
            });
            workers[i].start();
        }
        for (Thread worker : workers) worker.join();

        // Every begin() must be distinguishable; a shared number is exactly the old bug.
        assertEquals(0, duplicates.get());
        assertEquals(threads, generations.size());
    }

    @Test
    public void aCancelledGenerationStaysCancelledAsMoreArrive() {
        UtteranceToken token = new UtteranceToken();
        int doomed = token.begin();
        token.cancel();
        for (int i = 0; i < 100; i++) token.begin();

        assertTrue(token.isCancelled(doomed));
    }
}
