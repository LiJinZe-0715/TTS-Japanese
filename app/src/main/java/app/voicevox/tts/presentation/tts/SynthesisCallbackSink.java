package app.voicevox.tts.presentation.tts;

import android.media.AudioFormat;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.TextToSpeech;

import app.voicevox.tts.application.speech.AudioSink;
import app.voicevox.tts.application.speech.SpeechError;

/** The framework's {@link SynthesisCallback} as an {@link AudioSink}. */
final class SynthesisCallbackSink implements AudioSink {
    private final SynthesisCallback callback;

    SynthesisCallbackSink(SynthesisCallback callback) {
        this.callback = callback;
    }

    @Override
    public int maxBufferSize() {
        return callback.getMaxBufferSize();
    }

    @Override
    public boolean start(int sampleRate, int channels) {
        return callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, channels)
                == TextToSpeech.SUCCESS;
    }

    @Override
    public boolean write(byte[] buffer, int offset, int length) {
        return callback.audioAvailable(buffer, offset, length) == TextToSpeech.SUCCESS;
    }

    @Override
    public void done() {
        callback.done();
    }

    @Override
    public void error(SpeechError error) {
        callback.error(code(error));
    }

    private static int code(SpeechError error) {
        switch (error) {
            case INVALID_REQUEST:
                return TextToSpeech.ERROR_INVALID_REQUEST;
            case NOT_INSTALLED:
                return TextToSpeech.ERROR_NOT_INSTALLED_YET;
            case OUTPUT:
                return TextToSpeech.ERROR_OUTPUT;
            case STOPPED:
                return TextToSpeech.STOPPED;
            case SYNTHESIS:
            default:
                return TextToSpeech.ERROR_SYNTHESIS;
        }
    }
}
