package app.voicevox.tts.application.port;

/**
 * Port: diagnostics that are not meant for the user.
 *
 * <p>Without it the use cases would call {@code android.util.Log} and stop compiling on a plain
 * JVM. {@code infrastructure.log.LogcatLog} is the implementation; a test passes {@link #NONE}.
 */
public interface AppLog {

    AppLog NONE = new AppLog() {
        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message, Throwable error) {
        }

        @Override
        public void error(String message, Throwable error) {
        }
    };

    void info(String message);

    /** {@code error} may be null. */
    void warn(String message, Throwable error);

    /** {@code error} may be null. */
    void error(String message, Throwable error);
}
