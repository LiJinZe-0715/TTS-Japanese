package app.voicevox.tts.infrastructure.playback;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import app.voicevox.tts.application.port.SamplePlayer;
import app.voicevox.tts.domain.voice.VoiceCatalog;
import app.voicevox.tts.domain.voice.VoiceNames;
import app.voicevox.tts.domain.voice.VoiceStyle;

import java.util.Locale;
import java.util.Set;

/**
 * Plays a sample through the system TTS framework bound to this very engine.
 *
 * <p>This is the class that owns the awkward part — a {@link TextToSpeech} client whose voice
 * list is rebuilt from disk by the engine, so it has to be dropped whenever a pack is
 * installed or removed. Keeping it here means the presenter only sees "play this character"
 * and "it started / finished / failed".
 */
public final class SystemTtsSamplePlayer implements SamplePlayer {
    private static final String PREVIEW_TEXT = "こんにちは。これは音声合成のサンプルです。";

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());

    private Listener listener;
    private TextToSpeech tts;
    private boolean ready;
    private int playing = -1;

    public SystemTtsSamplePlayer(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public int playingStyleId() {
        return playing;
    }

    @Override
    public void toggle(VoiceStyle style) {
        if (playing == style.id) {
            stop();
            return;
        }
        playing = style.id;
        if (tts != null && ready) {
            playOn(style);
            return;
        }
        release();
        // The listener runs after the constructor returns, so the instance is read through a
        // holder. It is compared with the field because a client this class already replaced
        // can still report in: its callback used to mark the *new*, still-binding client ready
        // and speak on it.
        final TextToSpeech[] created = new TextToSpeech[1];
        created[0] = new TextToSpeech(context, code -> {
            if (tts != created[0]) return;
            // The user may have tapped another chip while the engine was binding.
            if (playing != style.id) return;
            if (code != TextToSpeech.SUCCESS) {
                fail(style, "引擎初始化错误 " + code);
                return;
            }
            ready = true;
            playOn(style);
        }, context.getPackageName());
        tts = created[0];
    }

    @Override
    public void stop() {
        playing = -1;
        if (tts != null) {
            try {
                tts.stop();
            } catch (Throwable ignored) {
                // A stop on a dead binder is not worth reporting.
            }
        }
    }

    @Override
    public void release() {
        ready = false;
        // Reset the flag too: the card highlight is driven by playingStyleId(), and a preview
        // that was released mid-sentence used to keep its chip lit with nothing playing.
        playing = -1;
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Throwable ignored) {
                // Shutdown after the engine process died is expected.
            }
            tts = null;
        }
    }

    /**
     * Selects the character and starts playback.
     *
     * <p>Every call in here crosses Binder to the engine service. It shares this process, but
     * the binding can still die with it, and then {@code setLanguage},
     * {@code getVoices}, {@code setVoice} and {@code speak} each throw
     * {@code DeadObjectException} — on this class's caller, which is the UI thread. The whole
     * sequence is therefore one guard: a preview that cannot start reports a failure on the
     * status line instead of taking the settings screen down with it.
     */
    private void playOn(VoiceStyle style) {
        if (tts == null) return;
        try {
            tts.setLanguage(Locale.JAPANESE);

            Voice target = null;
            Set<Voice> voices = tts.getVoices();
            if (voices != null) {
                for (Voice voice : voices) {
                    // By style id, not by name: the engine lists voices under localized names
                    // ("毛豆泥 / 标准"), and comparing against the Japanese one would never match.
                    VoiceStyle listed = VoiceCatalog.byVoiceName(voice.getName());
                    if (listed != null && listed.id == style.id) {
                        target = voice;
                        break;
                    }
                }
            }
            if (target == null) {
                fail(style, "引擎里没有「" + style.displayName(VoiceNames.language()) + "」");
                return;
            }

            tts.setVoice(target);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    main.post(() -> {
                        if (playing == style.id && listener != null) listener.onStarted(style);
                    });
                }

                @Override
                public void onDone(String utteranceId) {
                    main.post(() -> {
                        if (playing != style.id) return;
                        playing = -1;
                        if (listener != null) listener.onFinished(style);
                    });
                }

                /**
                 * Still abstract in the framework, so it has to exist even though the
                 * two-argument overload is the one the engine actually calls on API 21+.
                 * Forwarding keeps a single handler instead of two copies.
                 */
                @Override
                @SuppressWarnings("deprecation")
                public void onError(String utteranceId) {
                    onError(utteranceId, TextToSpeech.ERROR);
                }

                @Override
                public void onError(String utteranceId, int errorCode) {
                    main.post(() -> {
                        if (playing != style.id) return;
                        fail(style, "朗读中断（" + errorCode + "）");
                    });
                }
            });

            int result = tts.speak(PREVIEW_TEXT, TextToSpeech.QUEUE_FLUSH, null,
                    "preview-" + style.id);
            if (result != TextToSpeech.SUCCESS) {
                fail(style, "无法开始朗读（" + result + "）");
            }
        } catch (Throwable t) {
            fail(style, t.toString());
        }
    }

    private void fail(VoiceStyle style, String detail) {
        release();
        if (listener != null) listener.onFailure(style, detail);
    }
}
