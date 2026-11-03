package app.voicevox.tts.domain.voice;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Character and style names in the languages the voice list is actually read in.
 *
 * <p>The catalogue is published in Japanese, and a name like "四国めたん / ノーマル" tells a
 * Chinese or English reader nothing — it is the single thing every other app shows when it
 * lists this engine's voices. So each published name gets a Chinese and an English rendering
 * here, and the phone's language decides which one is shown: the published Japanese on a
 * Japanese phone, Chinese on a Chinese phone, English for everything else (an unrecognised
 * locale is not left with Japanese it may not read).
 *
 * <p>The renderings are only ever labels. A style is identified by its id, and
 * {@link VoiceCatalog#byVoiceName} accepts every rendering, so a phone that switches language
 * does not lose the user's pick.
 *
 * <p>The table is data, and the tests check that it covers the whole catalogue.
 */
public final class VoiceNames {
    /** The published language: the names exactly as VOICEVOX lists them. */
    public static final String JAPANESE = "ja";
    public static final String CHINESE = "zh";
    public static final String ENGLISH = "en";

    /** Japanese name to {Chinese, English}. */
    private static final Map<String, String[]> CHARACTERS = new HashMap<>();
    private static final Map<String, String[]> STYLES = new HashMap<>();

    static {
        character("四国めたん", "四国玫碳", "Shikoku Metan");
        character("ずんだもん", "毛豆泥", "Zundamon");
        character("春日部つむぎ", "春日部紬", "Kasukabe Tsumugi");
        character("雨晴はう", "雨晴哈乌", "Amehare Hau");
        character("冥鳴ひまり", "冥鸣日鞠", "Meimei Himari");
        character("九州そら", "九州空", "Kyushu Sora");
        character("波音リツ", "波音律", "Haon Ritsu");
        character("中国うさぎ", "中国兔", "Chugoku Usagi");
        character("玄野武宏", "玄野武宏", "Kurono Takehiro");
        character("剣崎雌雄", "剑崎雌雄", "Kenzaki Mesuo");
        character("No.7", "No.7", "No.7");
        character("後鬼", "后鬼", "Goki");
        character("WhiteCUL", "WhiteCUL", "WhiteCUL");
        character("白上虎太郎", "白上虎太郎", "Shirakami Kotaro");
        character("ちび式じい", "小式爷爷", "Chibishiki Jii");
        character("櫻歌ミコ", "樱歌米可", "Sakurauta Miko");
        character("ナースロボ＿タイプＴ", "护士机器人T型", "Nurse Robo Type T");
        character("†聖騎士 紅桜†", "†圣骑士 红樱†", "†Holy Knight Benizakura†");
        character("雀松朱司", "雀松朱司", "Wakamatsu Akashi");
        character("麒ヶ島宗麟", "麒岛宗麟", "Kigashima Sourin");
        character("春歌ナナ", "春歌娜娜", "Haruka Nana");
        character("猫使アル", "猫使阿尔", "Nekotsuka Aru");
        character("猫使ビィ", "猫使碧", "Nekotsuka Bii");
        character("栗田まろん", "栗田玛珑", "Kurita Maron");
        character("あいえるたん", "艾尔坦", "Aierutan");
        character("満別花丸", "满别花丸", "Manbetsu Hanamaru");
        character("琴詠ニア", "琴咏妮亚", "Kotoyomi Nia");
        character("青山龍星", "青山龙星", "Aoyama Ryusei");
        character("もち子さん", "饼子小姐", "Mochiko-san");
        character("小夜/SAYO", "小夜", "Sayo");
        character("Voidoll", "Voidoll", "Voidoll");
        character("ぞん子", "尊子", "Zonko");
        character("中部つるぎ", "中部剑", "Chubu Tsurugi");
        character("離途", "离途", "Rito");
        character("黒沢冴白", "黑泽冴白", "Kurosawa Kohaku");
        character("ユーレイちゃん", "幽灵酱", "Yurei-chan");
        character("東北ずん子", "东北毛豆子", "Tohoku Zunko");
        character("東北きりたん", "东北切蒲英", "Tohoku Kiritan");
        character("東北イタコ", "东北伊塔子", "Tohoku Itako");
        character("あんこもん", "红豆泥", "Ankomon");
        character("夜語トバリ", "夜语托巴里", "Yogatari Tobari");
        character("暁記ミタマ", "晓记御魂", "Akatsuki Mitama");
        character("里石ユカ", "里石由香", "Satoishi Yuka");

        style("ノーマル", "标准", "Normal");
        style("あまあま", "甜美", "Sweet");
        style("ツンツン", "傲娇", "Tsundere");
        style("セクシー", "性感", "Sexy");
        style("クイーン", "女王", "Queen");
        style("おどろき", "惊讶", "Surprised");
        style("こわがり", "胆小", "Timid");
        style("へろへろ", "有气无力", "Worn Out");
        style("ささやき", "低语", "Whisper");
        style("ヒソヒソ", "耳语", "Whispering");
        style("アナウンス", "播报", "Announcement");
        style("読み聞かせ", "讲故事", "Storytelling");
        style("人間ver.", "人类版", "Human ver.");
        style("ぬいぐるみver.", "玩偶版", "Plushie ver.");
        style("たのしい", "开心", "Happy");
        style("かなしい", "难过", "Sad");
        style("びえーん", "大哭", "Wailing");
        style("ふつう", "平常", "Normal");
        style("わーい", "欢呼", "Yay");
        style("びくびく", "怯生生", "Nervous");
        style("おこ", "生气", "Angry");
        style("喜び", "喜悦", "Joy");
        style("ツンギレ", "傲娇发火", "Tsundere Angry");
        style("悲しみ", "悲伤", "Sorrow");
        style("第二形態", "第二形态", "Second Form");
        style("ロリ", "萝莉", "Loli");
        style("楽々", "轻松", "Relaxed");
        style("恐怖", "恐惧", "Fear");
        style("内緒話", "悄悄话", "Secret");
        style("おちつき", "沉稳", "Calm");
        style("うきうき", "雀跃", "Cheerful");
        style("人見知り", "怕生", "Shy");
        style("元気", "活力", "Energetic");
        style("ぶりっ子", "撒娇", "Coy");
        style("ボーイ", "少年", "Boy");
        style("ヘロヘロ", "虚弱", "Exhausted");
        style("なみだめ", "泪眼", "Teary");
        style("熱血", "热血", "Passionate");
        style("不機嫌", "不高兴", "Grumpy");
        style("しっとり", "温婉", "Gentle");
        style("かなしみ", "哀伤", "Sadness");
        style("囁き", "轻语", "Soft Whisper");
        style("セクシー／あん子", "性感／红豆子", "Sexy / Anko");
        style("泣き", "哭泣", "Crying");
        style("怒り", "愤怒", "Angry");
        style("のんびり", "悠闲", "Easygoing");
        style("人間（怒り）ver.", "人类（愤怒）版", "Human (Angry) ver.");
        style("鬼ver.", "鬼版", "Oni ver.");
        style("低血圧", "低血压", "Low Blood Pressure");
        style("覚醒", "觉醒", "Awakened");
        style("実況風", "实况解说风", "Commentary");
        style("おどおど", "慌张", "Flustered");
        style("絶望と敗北", "绝望与败北", "Despair & Defeat");
        style("シリアス", "严肃", "Serious");
        style("甘々", "甜腻", "Sugary");
        style("哀しみ", "悲哀", "Grief");
        style("ツクモちゃん", "九十九酱", "Tsukumo-chan");
        style("つよつよ", "超强", "Super Strong");
        style("よわよわ", "弱弱", "Weak");
        style("けだるげ", "慵懒", "Languid");
        style("明るい", "开朗", "Cheerful");
        style("呆れ", "无奈", "Exasperated");
        style("つぼみ", "花蕾", "Bud");
    }

    private VoiceNames() {
    }

    private static void character(String japanese, String chinese, String english) {
        CHARACTERS.put(japanese, new String[]{chinese, english});
    }

    private static void style(String japanese, String chinese, String english) {
        STYLES.put(japanese, new String[]{chinese, english});
    }

    /**
     * The language a voice list is shown in.
     *
     * <p>The published Japanese for a Japanese phone — this is a Japanese engine, and its
     * primary readers know the characters by those names — Chinese for a Chinese phone, and
     * English for everything else, including any language this table has no words for, so a
     * phone in a third language still gets something readable.
     */
    public static String language(Locale locale) {
        if (locale == null) return ENGLISH;
        if (JAPANESE.equals(locale.getLanguage())) return JAPANESE;
        if (CHINESE.equals(locale.getLanguage())) return CHINESE;
        return ENGLISH;
    }

    /** {@link #language(Locale)} for the phone this process is running on. */
    public static String language() {
        return language(Locale.getDefault());
    }

    /** Every rendering of a name; a stored voice name may come from any of them. */
    public static String[] languages() {
        return new String[]{JAPANESE, CHINESE, ENGLISH};
    }

    /** A character's name, or the published Japanese one when the table has no entry. */
    public static String character(String japanese, String language) {
        return pick(CHARACTERS, japanese, language);
    }

    /** A style's name, or the published Japanese one when the table has no entry. */
    public static String style(String japanese, String language) {
        return pick(STYLES, japanese, language);
    }

    private static String pick(Map<String, String[]> table, String japanese, String language) {
        if (JAPANESE.equals(language)) return japanese;
        String[] names = table.get(japanese);
        if (names == null) return japanese;
        return CHINESE.equals(language) ? names[0] : names[1];
    }

    /** The Japanese names this table covers; the tests use it to prove nothing is missing. */
    public static Set<String> knownCharacters() {
        return Collections.unmodifiableSet(CHARACTERS.keySet());
    }

    /** The Japanese style labels this table covers. */
    public static Set<String> knownStyles() {
        return Collections.unmodifiableSet(STYLES.keySet());
    }
}
