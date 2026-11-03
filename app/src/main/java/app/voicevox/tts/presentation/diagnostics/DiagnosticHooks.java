package app.voicevox.tts.presentation.diagnostics;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;

import app.voicevox.tts.application.port.SpeechEngine;
import app.voicevox.tts.di.AppContainer;
import app.voicevox.tts.domain.voice.VoiceCatalog;
import app.voicevox.tts.domain.voice.VoicePack;
import app.voicevox.tts.domain.voice.VoiceStyle;
import app.voicevox.tts.presentation.settings.SettingsPresenter;
import app.voicevox.tts.presentation.settings.Tone;
import app.voicevox.tts.presentation.settings.view.SettingsView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The adb-driven entry points, deliberately outside the product path.
 *
 * <p>They exist because this device has no touch injection: a broken synthesis has to be told
 * apart from broken playback from a shell alone. Everything here is reached with
 * {@code am start -n app.voicevox.tts/.presentation.diagnostics.DiagnosticsActivity
 * --es auto <name>} and shares the same presenter, engine and view the UI uses, so a hook exercises the code a
 * tap would.
 *
 * <p>Only {@link DiagnosticsActivity} creates this class, and the system lets nobody without
 * the DUMP permission — in practice, only the adb shell — start that one. These hooks delete
 * voice packs and start downloads; any installed app could otherwise drive them with one
 * startActivity.
 */
final class DiagnosticHooks {
    private static final String TAG = AppContainer.LOG_TAG;
    private static final int REQUEST_CHECK_VOICE_DATA = 1001;
    private static final String PREVIEW_TEXT = "こんにちは。これは音声合成のサンプルです。";

    private final Activity host;
    private final SettingsPresenter presenter;
    private final SpeechEngine engine;
    private final SettingsView view;
    private TextToSpeech probe;

    DiagnosticHooks(Activity host, SettingsPresenter presenter, SpeechEngine engine,
               SettingsView view) {
        this.host = host;
        this.presenter = presenter;
        this.engine = engine;
        this.view = view;
    }

    /** Dispatches on {@code --es auto <name>}; a no-op for a launch without it. */
    void run(Intent intent) {
        String auto = intent == null ? null : intent.getStringExtra("auto");
        if (auto == null) return;
        switch (auto) {
            case "dump":
                view.showMessage("正在导出合成音频…", Tone.INFO);
                new Thread(this::dumpSample, "voicevox-dump").start();
                break;
            case "speak":
                view.showMessage("正在通过系统 TTS 通道朗读…", Tone.INFO);
                speakSample();
                break;
            case "download": {
                String file = intent.getStringExtra("file");
                VoicePack pack = VoiceCatalog.byFile(file);
                if (pack == null) {
                    view.showMessage("未知的文件：" + file, Tone.ERROR);
                } else {
                    presenter.download(pack, null);
                }
                break;
            }
            case "speakvoice":
                speakWith(intent.getStringExtra("voice"));
                break;
            case "delete": {
                String file = intent.getStringExtra("file");
                VoicePack pack = VoiceCatalog.byFile(file);
                if (pack == null) {
                    view.showMessage("未知的文件：" + file, Tone.ERROR);
                } else {
                    presenter.delete(pack);
                }
                break;
            }
            case "voices":
                listVoices(intent.getStringExtra("expect"));
                break;
            case "engines":
                listEngines();
                break;
            case "diag":
                diagnose();
                break;
            case "bottom":
                view.scrollToBottom();
                break;
            case "lru":
                new Thread(this::exerciseLru, "voicevox-lru").start();
                break;
            default:
                break;
        }
    }

    /** True when the result belongs to the diagnostics handshake. */
    boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CHECK_VOICE_DATA) return false;
        StringBuilder out = new StringBuilder("CHECK_TTS_DATA: result=")
                .append(resultCode).append('\n');
        if (data == null) {
            out.append("  data=null（系统设置会因此禁用语言列表）\n");
        } else {
            ArrayList<String> available = data.getStringArrayListExtra(
                    TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES);
            out.append("  availableVoices=").append(available).append('\n');
        }
        view.appendDiagnostics(out.toString());
        Log.i(TAG, "diag check result=" + resultCode + " voices="
                + (data == null ? "data=null" : data.getStringArrayListExtra(
                        TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES)));
        return true;
    }

    void release() {
        if (probe != null) {
            probe.shutdown();
            probe = null;
        }
    }

    // ------------------------------------------------------------------ hooks

    /** Walks three models in one process to prove the eviction policy works. */
    private void exerciseLru() {
        int[] ids = {2, 14, 16};
        for (int id : ids) {
            long started = System.currentTimeMillis();
            boolean ok = engine.ensureModel(id);
            VoiceStyle style = VoiceCatalog.byStyleId(id);
            Log.i(TAG, "lru step id=" + id + " ok=" + ok
                    + " took=" + (System.currentTimeMillis() - started) + "ms"
                    + " loaded=" + engine.loadedFiles()
                    + " character=" + (style == null ? "?" : style.character));
            byte[] wav = ok ? engine.synthesize("テスト", id, 1.0f, 1.0f, 1.0f) : null;
            Log.i(TAG, "lru audio id=" + id + " bytes=" + (wav == null ? -1 : wav.length));
        }
    }

    /** Prints the voice list exactly as a third-party app sees it after binding to us. */
    private void listVoices(String expect) {
        withProbe(tts -> {
            Set<Voice> voices = tts.getVoices();
            Log.i(TAG, "voices count=" + (voices == null ? -1 : voices.size())
                    + " expect=" + expect);
            if (voices != null) {
                List<String> names = new ArrayList<>();
                for (Voice voice : voices) names.add(voice.getName());
                java.util.Collections.sort(names);
                for (String name : names) Log.i(TAG, "voice: " + name);
                Log.i(TAG, "voices contains expect=" + names.contains(expect));
            }
        });
    }

    /**
     * Answers "why does my app see this engine but the browser does not".
     *
     * <p>Three different APIs are in play. {@code queryIntentServices} lists every engine that
     * is installed — that is what a TTS tester app and the system settings picker show.
     * {@code new TextToSpeech(ctx, cb)} binds to the <em>default</em> engine only, and that is
     * what Chrome's Web Speech bridge uses. {@code new TextToSpeech(ctx, cb, pkg)} pins one
     * engine, which is what every call site inside this app does — so the preview here keeps
     * working even when this engine is not the default.
     */
    private void listEngines() {
        Intent intent = new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE);
        List<ResolveInfo> resolved = host.getPackageManager()
                .queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY);
        String defaultEngine = Settings.Secure.getString(host.getContentResolver(),
                "tts_default_synth");

        StringBuilder out = new StringBuilder("已安装的 TTS 引擎：\n");
        for (ResolveInfo info : resolved) {
            boolean self = info.serviceInfo.packageName.equals(host.getPackageName());
            out.append(self ? "  ▸ " : "    ").append(info.serviceInfo.packageName)
                    .append(self ? "   ← 本引擎" : "").append('\n');
        }
        out.append("默认引擎：").append(defaultEngine == null ? "未设置" : defaultEngine)
                .append('\n');
        out.append(host.getPackageName().equals(defaultEngine)
                ? "浏览器（Web Speech）会用到本引擎"
                : "浏览器只用默认引擎，看不到本引擎");
        view.showDiagnostics(out.toString());
        // The block is below the fold on a phone, so the same lines go to the log.
        Log.i(TAG, "engines " + out.toString().replace('\n', '|'));
    }

    /** Speaks the sample with an explicit voice, to exercise a specific model. */
    private void speakWith(String voiceName) {
        withProbe(tts -> {
            Set<Voice> voices = tts.getVoices();
            boolean found = false;
            if (voices != null) {
                for (Voice voice : voices) {
                    if (voice.getName().equals(voiceName)) {
                        tts.setVoice(voice);
                        found = true;
                    }
                }
            }
            Log.i(TAG, "speakWith found=" + found
                    + " current=" + (tts.getVoice() == null ? "null" : tts.getVoice().getName()));
            tts.speak(PREVIEW_TEXT, TextToSpeech.QUEUE_FLUSH, null, "auto-voice");
        });
    }

    /** Drives the real playback path (framework -> our TextToSpeechService) without a tap. */
    private void speakSample() {
        withProbe(tts -> {
            tts.setLanguage(Locale.JAPANESE);
            tts.speak("こんにちは。これは音声合成のサンプルです。日本語の文章を読み上げています。",
                    TextToSpeech.QUEUE_FLUSH, null, "auto-speak");
            view.showMessage("朗读中…", Tone.INFO);
        });
    }

    /** Writes one utterance plus its header bytes to the log and to a file for inspection. */
    private void dumpSample() {
        final String text = "こんにちは。これは音声合成のサンプルです。日本語の文章を読み上げています。";
        VoiceStyle style = VoiceCatalog.bundled().defaultStyles().get(0);
        if (!engine.ensureModel(style.id)) {
            Log.e(TAG, "dump: model unavailable " + engine.lastError());
            return;
        }
        long started = System.currentTimeMillis();
        byte[] wav = engine.synthesize(text, style.id, 1.0f, 1.0f, 1.0f);
        long took = System.currentTimeMillis() - started;
        if (wav == null) {
            Log.e(TAG, "dump synthesis returned null");
            host.runOnUiThread(() -> view.showMessage("导出失败：合成返回 null", Tone.ERROR));
            return;
        }
        StringBuilder header = new StringBuilder();
        for (int i = 0; i < Math.min(64, wav.length); i++) {
            header.append(String.format("%02x", wav[i]));
        }
        Log.i(TAG, "dump bytes=" + wav.length + " ms=" + took + " header=" + header);

        File out = new File(host.getExternalFilesDir(null), "sample.wav");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(wav);
        } catch (Throwable t) {
            Log.e(TAG, "dump write failed", t);
            return;
        }
        Log.i(TAG, "dump wrote " + out.getAbsolutePath() + " size=" + out.length());
        final long size = wav.length;
        host.runOnUiThread(() -> view.showMessage("已导出 " + size + " 字节", Tone.OK));
    }

    /**
     * Binds a TextToSpeech to this very engine and asks it the same questions the system
     * settings screen asks, then replays the CHECK_TTS_DATA handshake.
     */
    private void diagnose() {
        final StringBuilder out = new StringBuilder();
        view.showDiagnostics("诊断中…");
        // The listener runs after the constructor returns, so the field is read through a
        // holder instead of referring to a local that is still being initialised.
        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = new TextToSpeech(host.getApplicationContext(), initStatus -> {
            final TextToSpeech tts = holder[0];
            out.append("引擎初始化: ")
                    .append(initStatus == TextToSpeech.SUCCESS ? "成功" : "失败(" + initStatus + ")")
                    .append('\n');
            if (initStatus == TextToSpeech.SUCCESS) {
                Set<Voice> voices = tts.getVoices();
                out.append("voice 数量: ")
                        .append(voices == null ? "null" : voices.size()).append('\n');
                if (voices != null) {
                    for (Voice voice : voices) {
                        out.append("  · ").append(voice.getName())
                                .append(' ').append(voice.getLocale()).append('\n');
                    }
                }
                out.append("isLanguageAvailable(ja_JP): ")
                        .append(tts.isLanguageAvailable(Locale.JAPANESE)).append('\n');
                out.append("setLanguage(ja_JP): ")
                        .append(tts.setLanguage(Locale.JAPANESE)).append('\n');
            }
            view.showDiagnostics(out.toString());
            // The block sits below the voice list, so on a phone it is usually off-screen.
            // Logging the same lines keeps the check usable from adb alone.
            Log.i(TAG, "diag " + out.toString().replace('\n', '|'));
            tts.shutdown();
            try {
                host.startActivityForResult(
                        new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
                                .setPackage(host.getPackageName()),
                        REQUEST_CHECK_VOICE_DATA);
            } catch (ActivityNotFoundException e) {
                view.appendDiagnostics("CHECK_TTS_DATA: 找不到处理该 action 的 Activity\n");
            }
        }, host.getPackageName());
    }

    /**
     * Binds a throwaway client to our own engine and hands it to {@code action}.
     *
     * <p>The four diagnostic hooks that need a raw TextToSpeech all did this by hand before; one
     * copy keeps the "release the old one first" rule in a single place.
     */
    private void withProbe(Consumer<TextToSpeech> action) {
        release();
        // Hand the action the client that just bound, not whatever `probe` holds by then: a
        // second hook may already have replaced it with one that is still binding.
        final TextToSpeech[] created = new TextToSpeech[1];
        created[0] = new TextToSpeech(host.getApplicationContext(), code -> {
            if (probe != created[0]) return;
            if (code != TextToSpeech.SUCCESS) {
                Log.e(TAG, "probe bind failed " + code);
                return;
            }
            action.accept(created[0]);
        }, host.getPackageName());
        probe = created[0];
    }
}
