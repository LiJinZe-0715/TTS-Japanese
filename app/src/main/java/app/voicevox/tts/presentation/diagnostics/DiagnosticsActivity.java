package app.voicevox.tts.presentation.diagnostics;

import android.content.Intent;

import app.voicevox.tts.application.port.SpeechEngine;
import app.voicevox.tts.presentation.settings.SettingsPresenter;
import app.voicevox.tts.presentation.settings.view.SettingsActivity;
import app.voicevox.tts.presentation.settings.view.SettingsView;

/**
 * The settings screen with the adb hooks ({@code --es auto <name>}) switched on.
 *
 * <p>The hooks delete voice packs and start 60 MB downloads, so they must not be reachable from
 * the exported launcher Activity — which is why only this class ever creates
 * {@link DiagnosticHooks}. It is declared with
 * {@code android:permission="android.permission.DUMP"}: the adb shell holds that permission, while a third-party app cannot be granted it
 * (signature|privileged|development). The guard is enforced by the system when the Activity is
 * started, so nothing here has to work out who the caller was — the previous check compared
 * {@code Binder.getCallingPid()} with our own PID on the main thread, where it always returns
 * our own PID, and therefore let every app through.
 *
 * <pre>
 * adb shell am start -n app.voicevox.tts/.presentation.diagnostics.DiagnosticsActivity \
 *     --es auto diag
 * </pre>
 */
public class DiagnosticsActivity extends SettingsActivity {
    private DiagnosticHooks hooks;

    @Override
    protected void onScreenCreated(SettingsPresenter presenter, SpeechEngine engine,
                                   SettingsView view) {
        hooks = new DiagnosticHooks(this, presenter, engine, view);
        hooks.run(getIntent());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (hooks == null || !hooks.onActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onDestroy() {
        if (hooks != null) hooks.release();
        super.onDestroy();
    }
}
