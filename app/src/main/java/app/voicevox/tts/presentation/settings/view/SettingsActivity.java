package app.voicevox.tts.presentation.settings.view;

import android.app.Activity;
import android.os.Bundle;

import app.voicevox.tts.application.port.SpeechEngine;
import app.voicevox.tts.di.AppContainer;
import app.voicevox.tts.presentation.settings.AndroidScreenHost;
import app.voicevox.tts.presentation.settings.SettingsPresenter;

/**
 * The settings screen: the launcher entry, and where the system's "engine settings" and
 * "install voice data" buttons land.
 *
 * <p>It only wires the screen together — the services from {@link AppContainer}, the widgets
 * from {@link SettingsView}, the decisions in {@link SettingsPresenter} — and forwards the
 * lifecycle.
 */
public class SettingsActivity extends Activity {
    private SettingsPresenter presenter;

    /** onCreate already painted once; the first onResume must not repaint the same state. */
    private boolean freshlyCreated;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        AppContainer container = AppContainer.get(this);
        SettingsView view = new SettingsView(this);
        presenter = new SettingsPresenter(new AndroidScreenHost(this), container.packs(),
                container.voices(), container.engine(), container.newSamplePlayer(), view);
        view.bind(presenter);
        setContentView(view.root());
        presenter.start();
        freshlyCreated = true;

        onScreenCreated(presenter, container.engine(), view);
    }

    /**
     * Called once the screen is painted. The exported screen does nothing more;
     * {@code presentation.diagnostics.DiagnosticsActivity} attaches its adb hooks here.
     */
    protected void onScreenCreated(SettingsPresenter presenter, SpeechEngine engine,
                                   SettingsView view) {
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (freshlyCreated) {
            freshlyCreated = false;
            return;
        }
        // Files may have changed while the system settings screen was in front.
        if (presenter != null) presenter.refresh();
    }

    @Override
    protected void onDestroy() {
        if (presenter != null) presenter.stop();
        super.onDestroy();
    }
}
