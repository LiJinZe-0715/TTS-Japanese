package app.voicevox.tts.presentation.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import app.voicevox.tts.di.AppContainer;

import java.util.List;

/**
 * The Android implementation of {@link ScreenHost}: one Activity, a few thin wrappers.
 *
 * <p>Nothing here decides anything — the strings, the order and the conditions all come from
 * the presenter. That is what keeps the presenter testable and this class boring.
 */
public final class AndroidScreenHost implements ScreenHost {
    private static final String TAG = AppContainer.LOG_TAG;
    private static final String TTS_SETTINGS = "com.android.settings.TTS_SETTINGS";

    private final Activity activity;

    public AndroidScreenHost(Activity activity) {
        this.activity = activity;
    }

    @Override
    public void post(Runnable action) {
        // Dropped once the Activity is gone. The download runs on its own thread and outlives
        // the screen — the user taps 下载 and leaves — so without this the callback posts into
        // a destroyed window and keeps the Activity reachable until the queue drains.
        if (activity.isDestroyed()) return;
        activity.runOnUiThread(() -> {
            if (!activity.isDestroyed()) action.run();
        });
    }

    @Override
    public void background(String threadName, Runnable work) {
        new Thread(work, threadName).start();
    }

    @Override
    public void confirm(String title, String message, String positiveLabel, Runnable onConfirm) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton(positiveLabel, (dialog, which) -> onConfirm.run())
                .show();
    }

    @Override
    public boolean openSystemSettings() {
        try {
            activity.startActivity(new Intent(TTS_SETTINGS));
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    @Override
    public boolean isRegisteredEngine() {
        Intent intent = new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        List<ResolveInfo> resolved = activity.getPackageManager().queryIntentServices(
                intent, PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_META_DATA);
        for (ResolveInfo info : resolved) {
            if (info.serviceInfo.packageName.equals(activity.getPackageName())) return true;
        }
        return false;
    }

    @Override
    public void log(String message, Throwable error) {
        Log.e(TAG, message, error);
    }
}
