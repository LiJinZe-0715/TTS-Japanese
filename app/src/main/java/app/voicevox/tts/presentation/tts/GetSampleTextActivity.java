package app.voicevox.tts.presentation.tts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

/**
 * Supplies the sample sentence the system settings screen offers for playback.
 *
 * The result code must be LANG_AVAILABLE (0), not RESULT_OK (-1): the settings screen
 * only reads EXTRA_SAMPLE_TEXT when the code equals LANG_AVAILABLE, and otherwise falls
 * back to its own English sample, which a Japanese-only engine cannot pronounce.
 */
public class GetSampleTextActivity extends Activity {
    private static final String SAMPLE =
            "こんにちは。これは音声合成のサンプルです。日本語の文章を読み上げています。";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent result = new Intent();
        result.putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, SAMPLE);
        setResult(TextToSpeech.LANG_AVAILABLE, result);
        finish();
    }
}
