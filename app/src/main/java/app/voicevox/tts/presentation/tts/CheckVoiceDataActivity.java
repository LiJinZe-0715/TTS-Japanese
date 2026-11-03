package app.voicevox.tts.presentation.tts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

import app.voicevox.tts.di.AppContainer;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Answers the system settings screen when it asks which languages this engine has installed.
 * Without it the engine shows up in the list but its language list is empty.
 *
 * <p>The result code is {@link TextToSpeech.Engine#CHECK_VOICE_DATA_PASS}, not
 * {@code RESULT_OK}: the settings screen compares against {@code 1} and only then reads
 * {@code EXTRA_AVAILABLE_VOICES}. {@code RESULT_OK} is {@code -1}, so a successful result
 * carrying the right extras was silently discarded and the language page stayed empty.
 *
 * <p>Availability is reported for real rather than assumed: the bundled model can be deleted
 * like any other pack, and claiming {@code jpn-JPN} with no pack left would only turn into a
 * failed utterance later. The bundled pack counts as soon as the app is installed, before its
 * copy out of the APK has run — the system asks this right after the user picks the engine,
 * which is usually before that copy has finished.
 */
public class CheckVoiceDataActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean canSpeak = !AppContainer.get(this).packs().installedFiles().isEmpty();
        Intent result = new Intent();
        result.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
                new ArrayList<>(canSpeak
                        ? Collections.singletonList("jpn-JPN")
                        : Collections.<String>emptyList()));
        result.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES,
                new ArrayList<>(canSpeak
                        ? Collections.<String>emptyList()
                        : Collections.singletonList("jpn-JPN")));
        // With nothing on disk the engine cannot emit a single sample, so the system is told
        // to offer the "install voice data" flow instead of pretending the locale is usable.
        setResult(canSpeak ? TextToSpeech.Engine.CHECK_VOICE_DATA_PASS
                : TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL, result);
        finish();
    }
}
