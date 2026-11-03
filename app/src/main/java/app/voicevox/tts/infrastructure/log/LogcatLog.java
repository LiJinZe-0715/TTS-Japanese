package app.voicevox.tts.infrastructure.log;

import android.util.Log;

import app.voicevox.tts.application.port.AppLog;

/** {@link AppLog} onto logcat, under one tag. */
public final class LogcatLog implements AppLog {
    private final String tag;

    public LogcatLog(String tag) {
        this.tag = tag;
    }

    @Override
    public void info(String message) {
        Log.i(tag, message);
    }

    @Override
    public void warn(String message, Throwable error) {
        Log.w(tag, message, error);
    }

    @Override
    public void error(String message, Throwable error) {
        Log.e(tag, message, error);
    }
}
