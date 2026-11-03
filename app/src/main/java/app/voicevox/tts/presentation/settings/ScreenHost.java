package app.voicevox.tts.presentation.settings;

/**
 * The platform services the settings presenter needs, reduced to the things it actually
 * calls.
 *
 * <p>Without this the presenter would hold an {@code Activity}, and every use case would need
 * a device to run — {@code AlertDialog} and {@code runOnUiThread} throw on a bare JVM. Behind
 * this port the download/delete/preview branches are ordinary code that a fake host drives
 * synchronously. {@link AndroidScreenHost} is the real implementation.
 */
public interface ScreenHost {

    /** Runs on the UI thread. A test host runs the action inline. */
    void post(Runnable action);

    /** Runs blocking work off the UI thread. A test host runs the work inline. */
    void background(String threadName, Runnable work);

    /** Two-button confirmation: cancel, or run {@code onConfirm}. */
    void confirm(String title, String message, String positiveLabel, Runnable onConfirm);

    /** Opens 设置 → 无障碍 → 文字转语音. Returns false when nothing handles it. */
    boolean openSystemSettings();

    /** Whether the package manager lists this app as a TTS engine. */
    boolean isRegisteredEngine();

    /**
     * Records a diagnostic that is not meant for the user.
     *
     * <p>This is the only thing besides {@link #post}/{@link #background} that would otherwise
     * have pulled {@code android.util.Log} into the presenter, which would have made the
     * "plain JVM" claim false: on a bare JVM that call returns a default instead of writing
     * anything, so failures were logged nowhere while looking logged.
     */
    void log(String message, Throwable error);
}
