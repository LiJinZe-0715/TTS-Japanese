package app.voicevox.tts.presentation.tts;

import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.speech.tts.Voice;

import app.voicevox.tts.application.speech.SpeechSynthesisService;
import app.voicevox.tts.application.voice.VoicePackService;
import app.voicevox.tts.application.voice.VoiceSelectionService;
import app.voicevox.tts.di.AppContainer;
import app.voicevox.tts.domain.voice.VoiceNames;
import app.voicevox.tts.domain.voice.VoiceStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A system-wide Japanese TTS engine backed by VOICEVOX CORE.
 *
 * <p>Any app that asks Android for a TTS engine can pick this one; one style per character is
 * exposed as a system voice so callers can choose a character the same way they would choose
 * a Google voice.
 *
 * <p>This class only speaks the framework's language: it answers the locale and voice queries
 * with Android constants and hands each utterance to {@link SpeechSynthesisService}, which owns
 * the voice choice, the sentence pipeline and cancellation.
 */
public class VoicevoxTtsService extends TextToSpeechService {

    // Volatile because onCreate() runs on the main thread while every override below runs on
    // a binder thread: without it a freshly bound service can read a null field, throw from
    // onGetVoices(), and be reported to the caller as an engine with no voices.
    private volatile VoicePackService packs;
    private volatile VoiceSelectionService voices;
    private volatile SpeechSynthesisService speech;

    @Override
    public void onCreate() {
        // Wire everything up before super.onCreate(): the framework calls onLoadLanguage() from
        // inside it to load the default locale, and that reads packs.
        AppContainer container = AppContainer.get(this);
        packs = container.packs();
        voices = container.voices();
        speech = container.newSpeechSynthesis();
        super.onCreate();
        speech.warmUpInBackground();
    }

    @Override
    public void onDestroy() {
        speech.shutdown();
        super.onDestroy();
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        if (!isJapanese(lang)) return TextToSpeech.LANG_NOT_SUPPORTED;
        // Japanese is supported, but with every pack deleted there is nothing to speak with:
        // say so instead of promising a locale that turns into a failed utterance later.
        if (packs.installedFiles().isEmpty()) return TextToSpeech.LANG_MISSING_DATA;
        // The engine has exactly one locale, ja_JP. Answering with the country variant is
        // strictly more informative than LANG_AVAILABLE, and callers that probe for a usable
        // locale before speaking accept only the COUNTRY answers.
        if ("JP".equalsIgnoreCase(country) || "JPN".equalsIgnoreCase(country)) {
            return TextToSpeech.LANG_COUNTRY_AVAILABLE;
        }
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected String[] onGetLanguage() {
        return new String[]{"jpn", "JPN", ""};
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        return onIsLanguageAvailable(lang, country, variant);
    }

    /**
     * One voice per character, for every pack that can speak.
     *
     * <p>"Can speak" includes the bundled pack while its copy out of the APK is still running:
     * callers ask for the voice list the moment they bind — Chrome within milliseconds — and
     * must not be told the engine has none. A bundled pack the user deleted is not listed.
     */
    @Override
    public List<Voice> onGetVoices() {
        List<Voice> list = new ArrayList<>();
        for (VoiceStyle style : voices.availableStyles()) {
            // The name is the only thing a caller ever sees of a voice, so it is the localized
            // one. Reading it back through VoiceCatalog.byVoiceName() accepts any rendering, so
            // this stays stable even when the phone's language changes after the caller stored it.
            list.add(new Voice(style.displayName(language()), Locale.JAPAN, Voice.QUALITY_HIGH,
                    Voice.LATENCY_NORMAL, false, Collections.emptySet()));
        }
        return list;
    }

    @Override
    public int onIsValidVoiceName(String voiceName) {
        return voices.isAvailable(voiceName) ? TextToSpeech.SUCCESS : TextToSpeech.ERROR;
    }

    @Override
    public String onGetDefaultVoiceNameFor(String lang, String country, String variant) {
        if (!isJapanese(lang)) return "";
        // Same rule as an empty voice name: the setting, then the first character available.
        VoiceStyle style = voices.defaultStyle();
        return style == null ? "" : style.displayName(language());
    }

    @Override
    public int onLoadVoice(String voiceName) {
        // Nothing to do: the model behind the voice is loaded on demand at synthesis time,
        // so that switching voices does not pay for a 230 MB load the caller may never use.
        return TextToSpeech.SUCCESS;
    }

    @Override
    protected void onStop() {
        // Invalidates every synthesis that was already in flight.
        speech.stop();
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        CharSequence text = request.getCharSequenceText();
        speech.speak(text == null ? "" : text.toString(), request.getVoiceName(),
                request.getSpeechRate() / 100f, request.getPitch() / 100f,
                new SynthesisCallbackSink(callback));
    }

    private static boolean isJapanese(String lang) {
        return "jpn".equalsIgnoreCase(lang) || "ja".equalsIgnoreCase(lang);
    }

    /**
     * The language the voice list is shown in, read per call rather than cached in a field,
     * so a phone whose language changed while the engine process stayed alive lists the new
     * language without a restart.
     */
    private static String language() {
        return VoiceNames.language();
    }
}
