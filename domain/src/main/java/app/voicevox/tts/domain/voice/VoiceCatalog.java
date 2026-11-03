package app.voicevox.tts.domain.voice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every talk voice model VOICEVOX publishes, and which styles each .vvm file contains.
 *
 * <p>Generated from the voicevox_vvm {@value #RELEASE} release table (sizes and digests from
 * the GitHub release API). This is the single source of truth for the download list, for the
 * voices exposed to the system, and for the mapping from a style id back to the pack that has
 * to be loaded before that style can speak.
 *
 * <p>Where the packs are downloaded from is not part of the model: the release tag is, because
 * the sizes and digests below belong to it, but the URL is built by the infrastructure.
 */
public final class VoiceCatalog {
    /** The voicevox_vvm release this table was generated from. */
    public static final String RELEASE = "0.17.0";

    /** The pack shipped inside the APK, so the engine works before anything is downloaded. */
    public static final String BUNDLED_FILE = "0.vvm";

    private static final Map<String, VoicePack> BY_FILE = new LinkedHashMap<>();

    /**
     * Every name a voice can be asked for by, in every language it is listed in.
     *
     * <p>The framework stores whatever name it was handed and hands it back later, possibly
     * after the phone's language changed, so the Japanese, Chinese and English renderings all
     * resolve to the same style.
     */
    private static final Map<String, VoiceStyle> BY_VOICE_NAME = new HashMap<>();

    static {
        add("0.vvm", 59308488L, "ecd35374d4182cd883cba5040376f7f888cc6ba248b1c2f4cea07cdb34bb1318", "四国めたん|ノーマル|2", "四国めたん|あまあま|0", "四国めたん|ツンツン|6", "四国めたん|セクシー|4", "ずんだもん|ノーマル|3", "ずんだもん|あまあま|1", "ずんだもん|ツンツン|7", "ずんだもん|セクシー|5", "春日部つむぎ|ノーマル|8", "雨晴はう|ノーマル|10");
        add("1.vvm", 60571189L, "e16ffb6fc3e13e1be4ebc3831cc39df019cbac96b28bbed7653a4bb51c4d7d5d", "冥鳴ひまり|ノーマル|14");
        add("2.vvm", 59308683L, "2eb9a67e3bce09a7f209eb21a56926fa94476352b6ce874c9acd8f465871a4f4", "九州そら|ノーマル|16", "九州そら|あまあま|15", "九州そら|ツンツン|18", "九州そら|セクシー|17");
        add("3.vvm", 63346737L, "54aeea4aa51c56e87acc1d406fafc2bd980414c36ebceae0830340b83791d3d7", "波音リツ|ノーマル|9", "波音リツ|クイーン|65", "中国うさぎ|ノーマル|61", "中国うさぎ|おどろき|62", "中国うさぎ|こわがり|63", "中国うさぎ|へろへろ|64");
        add("4.vvm", 59304904L, "716d2900f6b93ba22119af0e236c32fdbf6fea81c519b5d4830afcb2db349522", "玄野武宏|ノーマル|11", "剣崎雌雄|ノーマル|21");
        add("5.vvm", 57611312L, "67451cdbc3e473dba4d09c878903a656be9e7030cb5901155e5a51634ed8b318", "四国めたん|ささやき|36", "四国めたん|ヒソヒソ|37", "ずんだもん|ささやき|22", "ずんだもん|ヒソヒソ|38", "九州そら|ささやき|19");
        add("6.vvm", 59304854L, "975d29f225cf605b0e75a0bb66f3f952821b16bbfa49a3a9b96a7688bf21abbe", "No.7|ノーマル|29", "No.7|アナウンス|30", "No.7|読み聞かせ|31");
        add("7.vvm", 58326212L, "d8e3bc97610805901fb7647226a233fe15056f30d1f91d270895c60198625569", "後鬼|人間ver.|27", "後鬼|ぬいぐるみver.|28");
        add("8.vvm", 59304782L, "0625c497f1f22be87d03281676c0292d834e262338e70ccaf83af7f34aa83b99", "WhiteCUL|ノーマル|23", "WhiteCUL|たのしい|24", "WhiteCUL|かなしい|25", "WhiteCUL|びえーん|26");
        add("9.vvm", 58328935L, "3dca586c0aa553772419bdc090045f9ea73bd9ed82b88b7dd5737bdd6eddee29", "白上虎太郎|ふつう|12", "白上虎太郎|わーい|32", "白上虎太郎|びくびく|33", "白上虎太郎|おこ|34", "白上虎太郎|びえーん|35");
        add("10.vvm", 58327087L, "dea2104b05391fdc37d8c27b4104569d5bff3a52bcb9e4077bb2b1f951c7524a", "玄野武宏|喜び|39", "玄野武宏|ツンギレ|40", "玄野武宏|悲しみ|41", "ちび式じい|ノーマル|42");
        add("11.vvm", 61687324L, "3fb0c0f12ec37fda568020509d6e63a0a6b5d6edf3c2330508c6bf4e3eeb8b82", "櫻歌ミコ|ノーマル|43", "櫻歌ミコ|第二形態|44", "櫻歌ミコ|ロリ|45", "ナースロボ＿タイプＴ|ノーマル|47", "ナースロボ＿タイプＴ|楽々|48", "ナースロボ＿タイプＴ|恐怖|49", "ナースロボ＿タイプＴ|内緒話|50");
        add("12.vvm", 59305189L, "894464a6cbf59bdf930b8406097f44d2f25bb762db5bc71c9e9c31e1f904c715", "†聖騎士 紅桜†|ノーマル|51", "雀松朱司|ノーマル|52", "麒ヶ島宗麟|ノーマル|53");
        add("13.vvm", 61686018L, "df381d94df92992c4170017d2f1ca386f7a5c3cb559e1bd0578f899910529e29", "春歌ナナ|ノーマル|54", "猫使アル|ノーマル|55", "猫使アル|おちつき|56", "猫使アル|うきうき|57", "猫使ビィ|ノーマル|58", "猫使ビィ|おちつき|59", "猫使ビィ|人見知り|60");
        add("14.vvm", 66281498L, "63cbabeb29ee9a4837a2462c77fc5cecd380abb5bdbcda4c40b3c34f8f2f9d38", "栗田まろん|ノーマル|67", "あいえるたん|ノーマル|68", "満別花丸|ノーマル|69", "満別花丸|元気|70", "満別花丸|ささやき|71", "満別花丸|ぶりっ子|72", "満別花丸|ボーイ|73", "琴詠ニア|ノーマル|74");
        add("15.vvm", 67265793L, "c29b0c0f8e332fcedc2a5de4b4426feb75fc9f587c314da99af8dbc964287221", "ずんだもん|ヘロヘロ|75", "ずんだもん|なみだめ|76", "青山龍星|ノーマル|13", "青山龍星|熱血|81", "青山龍星|不機嫌|82", "青山龍星|喜び|83", "青山龍星|しっとり|84", "青山龍星|かなしみ|85", "青山龍星|囁き|86", "もち子さん|ノーマル|20", "もち子さん|セクシー／あん子|66", "もち子さん|泣き|77", "もち子さん|怒り|78", "もち子さん|喜び|79", "もち子さん|のんびり|80", "小夜/SAYO|ノーマル|46");
        add("16.vvm", 59303880L, "545a2365dbb3f457d2ee9e24b45313968aa4c8351d513f3a79d9922a17569cab", "後鬼|人間（怒り）ver.|87", "後鬼|鬼ver.|88");
        add("17.vvm", 59304258L, "eb96b6c71fa80f905a86ec7f221f10fbf31f84c0675f919a25d198d8561e3f9a", "Voidoll|ノーマル|89");
        add("18.vvm", 62519486L, "33e5177ebbe1ec5829769c1017f1294641dbf22c43cd2af4896ae0738a14ed13", "ぞん子|ノーマル|90", "ぞん子|低血圧|91", "ぞん子|覚醒|92", "ぞん子|実況風|93", "中部つるぎ|ノーマル|94", "中部つるぎ|怒り|95", "中部つるぎ|ヒソヒソ|96", "中部つるぎ|おどおど|97", "中部つるぎ|絶望と敗北|98");
        add("19.vvm", 60021787L, "b9e1c5ddd5b418a6f1d6377d7d00df32c682b580ac9d326675ba4e0b7bb4fc3d", "離途|ノーマル|99", "離途|シリアス|101", "黒沢冴白|ノーマル|100");
        add("20.vvm", 60535324L, "fcfa6438d71df86c2013a8f285224fb96b632c4f07486d9855474f3df9a50a09", "ユーレイちゃん|ノーマル|102", "ユーレイちゃん|甘々|103", "ユーレイちゃん|哀しみ|104", "ユーレイちゃん|ささやき|105", "ユーレイちゃん|ツクモちゃん|106");
        add("21.vvm", 63195138L, "f6b937e953409a65f3d7609335a4dfd19fdd9458be27d988bdd8296034adfaf6", "猫使アル|つよつよ|110", "猫使アル|へろへろ|111", "猫使ビィ|つよつよ|112", "東北ずん子|ノーマル|107", "東北きりたん|ノーマル|108", "東北イタコ|ノーマル|109");
        add("22.vvm", 63409684L, "1c580f93caaa89f517d2431ee82519eab39c389d1bfb8791035f5c5af71eb17d", "あんこもん|ノーマル|113", "あんこもん|つよつよ|114", "あんこもん|よわよわ|115", "あんこもん|けだるげ|116");
        add("23.vvm", 60022581L, "4e09d14a73e5c3e6ec237cf37aae1ce44464e0d4b8a36e7580cb7b475f494dda", "あんこもん|ささやき|117");
        add("24.vvm", 60024319L, "3e62f5ce0ac345ed74c0e3bc9051cd99baaed37389e33f64195c9e0e49f34594", "夜語トバリ|ノーマル|118", "夜語トバリ|明るい|119", "夜語トバリ|哀しみ|120", "夜語トバリ|呆れ|121", "暁記ミタマ|ノーマル|122", "暁記ミタマ|怒り|123", "暁記ミタマ|哀しみ|124", "暁記ミタマ|ささやき|125", "里石ユカ|つぼみ|126");

        indexVoiceNames();
    }

    private VoiceCatalog() {
    }

    /** Fills the reverse index once, after the table above is complete. */
    private static void indexVoiceNames() {
        for (VoicePack pack : BY_FILE.values()) {
            for (VoiceStyle style : pack.defaultStyles()) {
                for (String language : VoiceNames.languages()) {
                    BY_VOICE_NAME.put(style.displayName(language), style);
                }
            }
        }
    }

    private static void add(String file, long bytes, String sha256, String... entries) {
        List<VoiceStyle> styles = new ArrayList<>(entries.length);
        for (String entry : entries) {
            String[] parts = entry.split("\\|");
            styles.add(new VoiceStyle(parts[0], parts[1], Integer.parseInt(parts[2])));
        }
        BY_FILE.put(file, new VoicePack(file, bytes, sha256, styles));
    }

    public static List<VoicePack> all() {
        return new ArrayList<>(BY_FILE.values());
    }

    public static VoicePack byFile(String file) {
        return BY_FILE.get(file);
    }

    /**
     * Resolves a voice name back to the style the system asked for.
     *
     * <p>Accepts the published Japanese name and every translation of it, because the name the
     * system hands back is whichever rendering it was shown when the user picked the voice.
     */
    public static VoiceStyle byVoiceName(String voiceName) {
        return voiceName == null ? null : BY_VOICE_NAME.get(voiceName);
    }

    public static VoiceStyle byStyleId(int id) {
        for (VoicePack pack : BY_FILE.values()) {
            for (VoiceStyle style : pack.styles) {
                if (style.id == id) return style;
            }
        }
        return null;
    }

    /** Which pack has to be loaded before this style id can be synthesised. */
    public static VoicePack packOfStyle(int id) {
        for (VoicePack pack : BY_FILE.values()) {
            for (VoiceStyle style : pack.styles) {
                if (style.id == id) return pack;
            }
        }
        return null;
    }

    /** The pack bundled in the APK. */
    public static VoicePack bundled() {
        return BY_FILE.get(BUNDLED_FILE);
    }

    /**
     * One style per character, for the given packs, in catalogue order. This is the voice list
     * the engine shows the system and the choices the default-voice picker offers.
     */
    public static List<VoiceStyle> defaultStylesOf(List<String> files) {
        List<VoiceStyle> styles = new ArrayList<>();
        for (String file : files) {
            VoicePack pack = BY_FILE.get(file);
            if (pack != null) styles.addAll(pack.defaultStyles());
        }
        return styles;
    }
}
