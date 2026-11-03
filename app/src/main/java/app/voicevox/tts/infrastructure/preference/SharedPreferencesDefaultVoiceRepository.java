package app.voicevox.tts.infrastructure.preference;

import android.content.Context;
import android.content.SharedPreferences;

import app.voicevox.tts.domain.voice.DefaultVoiceRepository;

/**
 * The chosen default voice, stored in the app's own preferences.
 *
 * <p>The TTS service reads it and the settings screen writes it, in the same process (the
 * manifest declares no {@code android:process}). No copy of the value is kept here:
 * {@code getSharedPreferences} hands every caller in the process the same
 * {@link SharedPreferences}, whose in-memory map is updated synchronously by {@code apply()}.
 * A field cached here used to pin the engine to whatever it read at bind time, so a new
 * default only took effect once the engine process died.
 *
 * <p>No warm-up thread either: the platform implementation already starts parsing the file on
 * a background thread when the object is created, and every later read is a map lookup.
 */
public final class SharedPreferencesDefaultVoiceRepository implements DefaultVoiceRepository {
    private static final String STORE = "voicevox";
    private static final String KEY_STYLE = "default_style_id";

    private final SharedPreferences preferences;

    public SharedPreferencesDefaultVoiceRepository(Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    @Override
    public int styleId() {
        try {
            return preferences.getInt(KEY_STYLE, NONE);
        } catch (ClassCastException corrupt) {
            // A value of the wrong type is treated as "nothing chosen"; the default falls back.
            return NONE;
        }
    }

    @Override
    public void save(int styleId) {
        preferences.edit().putInt(KEY_STYLE, styleId).apply();
    }
}
