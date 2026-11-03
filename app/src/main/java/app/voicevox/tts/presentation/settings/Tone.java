package app.voicevox.tts.presentation.settings;

/** The tone of a status message, i.e. how it should be coloured on screen. */
public enum Tone {
    /** Neutral progress information, e.g. "正在下载". */
    INFO,
    /** Something worked, e.g. "下载完成". */
    OK,
    /** Worked, but the user should know about a side effect. */
    WARN,
    /** Something failed. */
    ERROR,
    /** Plain informational text, no judgement. */
    NEUTRAL
}
