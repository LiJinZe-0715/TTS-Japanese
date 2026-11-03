package app.voicevox.tts.application.speech;

/**
 * Why an utterance ended without being spoken. The Android adapter maps each one onto the
 * framework's {@code TextToSpeech.ERROR_*} code.
 */
public enum SpeechError {
    /** Nothing to say: empty or blank text. */
    INVALID_REQUEST,
    /** Every pack is deleted, so no voice can speak. */
    NOT_INSTALLED,
    /** The engine could not load the model or produce audio. */
    SYNTHESIS,
    /** The caller refused the audio we handed it. */
    OUTPUT,
    /** A stop, or a newer utterance, cancelled this one. */
    STOPPED
}
