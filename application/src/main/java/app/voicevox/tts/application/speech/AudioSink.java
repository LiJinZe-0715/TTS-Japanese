package app.voicevox.tts.application.speech;

/**
 * Where an utterance's audio goes: the framework's {@code SynthesisCallback}, reduced to the
 * calls {@link SpeechSynthesisService} makes.
 *
 * <p>Every utterance ends with exactly one {@link #done()} or {@link #error}: the framework
 * waits for it, and the caller's onDone/onError never fires without it.
 */
public interface AudioSink {

    /** Largest chunk {@link #write} accepts at once. */
    int maxBufferSize();

    /** Declares 16-bit PCM at this rate. False when the caller refuses it. */
    boolean start(int sampleRate, int channels);

    /** False when the caller refuses the buffer. */
    boolean write(byte[] buffer, int offset, int length);

    void done();

    void error(SpeechError error);
}
