package app.voicevox.tts.presentation.settings.view;

import android.app.Activity;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import app.voicevox.tts.domain.voice.VoicePack;
import app.voicevox.tts.domain.voice.VoiceStyle;
import app.voicevox.tts.domain.voice.VoiceNames;
import app.voicevox.tts.presentation.settings.SettingsScreen;
import app.voicevox.tts.presentation.settings.Tone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The settings screen's view layer: builds the widget tree and renders whatever state the
 * presenter hands it.
 *
 * <p>It holds no business rules. It does not read files, does not know whether a download is
 * legal, and never starts one by itself — a tap is forwarded through {@link Callbacks} and
 * comes back as a call to {@link #renderPacks} or {@link #showMessage}. The cards themselves
 * live in {@link PackCard}; this class is only the page skeleton around them.
 */
public final class SettingsView implements SettingsScreen {
    private final Activity host;

    /**
     * What characters and styles are called on screen: Chinese on a Chinese phone, English
     * elsewhere.
     *
     * <p>Read per call rather than pinned in a field at construction. The engine already reads
     * {@code VoiceNames.language()} on every utterance, so a cached copy here could disagree
     * with the names the voice list is built from after the user changes the system language
     * while the screen stays alive — the same phone would show two different names for one
     * character.
     */
    private static String language() {
        return VoiceNames.language();
    }

    private final LinearLayout listContainer;
    private final TextView messageView;
    private final ScrollView scroller;
    private final TextView diag;
    private LinearLayout defaultRow;
    private TextView defaultHint;
    private TextView heroCount;
    private TextView heroSub;
    private TextView engineLine;
    private EditText search;

    private final List<PackCard> cards = new ArrayList<>();
    private final Map<String, PackCard> cardsByFile = new LinkedHashMap<>();

    private Callbacks callbacks;

    public SettingsView(Activity host) {
        this.host = host;

        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Palette.BG);
        root.setPadding(dp(18), dp(10), dp(18), dp(28));
        // targetSdk 35+ means edge-to-edge: the window extends under the status and navigation
        // bars and the display cutout, and adjustResize no longer shrinks it for the keyboard.
        // Every side is reserved by hand, the bottom for whichever of the navigation bar and
        // the IME is taller, or the end of the list sits under the keyboard while searching.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int[] bars = systemBarInsets(insets);
            view.setPadding(bars[2] + dp(18), bars[0] + dp(10), bars[3] + dp(18),
                    bars[1] + dp(28));
            return insets;
        });

        root.addView(buildHeader());
        root.addView(buildHero());

        messageView = new TextView(host);
        messageView.setTextSize(12.5f);
        messageView.setLineSpacing(dp(3), 1f);
        messageView.setPadding(dp(12), dp(9), dp(12), dp(9));
        messageView.setVisibility(View.GONE);
        LinearLayout.LayoutParams messageParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(10);
        messageView.setLayoutParams(messageParams);
        root.addView(messageView);

        root.addView(buildDefaultVoice());

        root.addView(buildSectionHeader());
        root.addView(buildSearch());

        listContainer = new LinearLayout(host);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        listParams.topMargin = dp(12);
        listContainer.setLayoutParams(listParams);
        root.addView(listContainer);

        root.addView(buildAbout());

        diag = new TextView(host);
        diag.setTextSize(11);
        diag.setTextColor(Palette.INK_DIM);
        diag.setPadding(0, dp(16), 0, 0);
        diag.setVisibility(View.GONE);
        root.addView(diag);

        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(root);
        scroller = scroll;
    }

    /** Wired after construction: the presenter needs the view, and the view needs its sink. */
    public void bind(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public View root() {
        return scroller;
    }

    // ------------------------------------------------------------------ rendering

    /** Rebuilds the pack list. Cheap enough at 25 cards, and it keeps state in one place. */
    @Override
    public void renderPacks(List<VoicePack> packs, Set<String> installedFiles,
                            int playingStyleId) {
        listContainer.removeAllViews();
        cards.clear();
        cardsByFile.clear();
        // Resolved once per repaint: every card in this pass then names a character the same
        // way, which is the consistency the old fixed field was there to provide — without
        // pinning the choice for the lifetime of the Activity.
        String language = language();
        for (VoicePack pack : packs) {
            PackCard card = new PackCard(host, pack, installedFiles.contains(pack.file), callbacks,
                    language);
            cards.add(card);
            cardsByFile.put(pack.file, card);
            listContainer.addView(card.view());
        }
        Editable text = search.getText();
        applyFilter(text == null ? "" : text.toString());
        updatePlaying(playingStyleId);
    }

    /** Only the highlight changes when playback starts or stops; the cards stay as they are. */
    @Override
    public void updatePlaying(int styleId) {
        for (PackCard card : cards) card.applyPlaying(styleId);
    }

    /**
     * One chip per character that can actually speak, with the one in effect filled in.
     *
     * <p>This is the only control a browser user has: Chrome never exposes the engine's voices
     * to a page, so a site cannot offer a character picker no matter what it renders.
     */
    @Override
    public void renderDefaultVoice(List<VoiceStyle> choices, int effectiveStyleId) {
        defaultRow.removeAllViews();
        if (choices.isEmpty()) {
            defaultHint.setText("还没有可用的声音包，下载后就能选");
            return;
        }
        defaultHint.setText("网页朗读、以及没有指定角色的应用，都会用这个角色");
        String language = language();
        for (VoiceStyle style : choices) {
            defaultRow.addView(defaultChip(style, style.id == effectiveStyleId, language));
        }
    }

    private View defaultChip(VoiceStyle style, boolean selected, String language) {
        TextView chip = new TextView(host);
        chip.setText(style.displayName(language));
        chip.setTextSize(12);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), dp(8), dp(12), dp(8));
        chip.setTextColor(selected ? 0xFFFFFFFF : Palette.ACCENT_DEEP);
        chip.setTypeface(selected ? Palette.BOLD : Palette.MEDIUM);
        chip.setBackground(
                Palette.pill(host, selected ? Palette.ACCENT : Palette.ACCENT_SOFT, 100));
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(7);
        chip.setLayoutParams(params);
        chip.setOnClickListener(view -> {
            if (callbacks != null) callbacks.onDefaultVoiceRequested(style);
        });
        return chip;
    }

    @Override
    public void setSummary(String headline, String detail) {
        heroCount.setText(headline);
        heroSub.setText(detail);
    }

    @Override
    public void setEngineInfo(String text) {
        engineLine.setText(text);
    }

    @Override
    public void setDownloadProgress(String file, boolean active, int percent) {
        PackCard card = cardsByFile.get(file);
        if (card != null) card.setDownloading(active, percent);
    }

    @Override
    public void showMessage(String text, Tone tone) {
        messageView.setVisibility(View.VISIBLE);
        messageView.setText(text);
        switch (tone) {
            case OK:
                messageView.setBackground(Palette.rounded(host, Palette.GOOD_SOFT, 12));
                messageView.setTextColor(Palette.GOOD);
                break;
            case WARN:
                messageView.setBackground(Palette.rounded(host, 0xFFFFF4E5, 12));
                messageView.setTextColor(0xFFB4690E);
                break;
            case ERROR:
                messageView.setBackground(Palette.rounded(host, Palette.DANGER_SOFT, 12));
                messageView.setTextColor(Palette.DANGER);
                break;
            case INFO:
                messageView.setBackground(Palette.rounded(host, Palette.ACCENT_SOFT, 12));
                messageView.setTextColor(Palette.ACCENT_DEEP);
                break;
            default:
                messageView.setBackground(Palette.rounded(host, 0xFFEFF1F5, 12));
                messageView.setTextColor(Palette.INK_DIM);
                break;
        }
    }

    // ------------------------------------------------------------------ diagnostics-only extras

    /** Not part of {@link SettingsScreen}: only the adb diagnostics block needs it. */
    public void showDiagnostics(String text) {
        diag.setVisibility(View.VISIBLE);
        diag.setText(text);
    }

    public void appendDiagnostics(String text) {
        diag.setVisibility(View.VISIBLE);
        diag.setText(diag.getText() + text);
    }

    public void scrollToBottom() {
        scroller.post(() -> scroller.fullScroll(ScrollView.FOCUS_DOWN));
    }

    // ------------------------------------------------------------------ chrome

    private View buildHeader() {
        LinearLayout header = new LinearLayout(host);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, dp(6), 0, dp(14));

        TextView title = new TextView(host);
        // Latin only: this HyperOS build drops CJK glyphs from a large TextView, and the
        // Chinese copy lives in the line below, which renders normally at 13sp.
        title.setText("VOICEVOX TTS");
        title.setTextSize(24);
        title.setTypeface(Palette.BOLD);
        title.setTextColor(Palette.INK);
        title.setLetterSpacing(0.02f);
        header.addView(title);

        TextView subtitle = new TextView(host);
        subtitle.setText("系统级日语语音引擎 · 任何应用都能调用");
        subtitle.setTextSize(13);
        subtitle.setTextColor(Palette.INK_DIM);
        subtitle.setPadding(0, dp(5), 0, 0);
        header.addView(subtitle);
        return header;
    }

    private View buildHero() {
        FrameLayout hero = new FrameLayout(host);
        GradientDrawable background =
                new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[]{0xFF6E7CFF, 0xFF9B6BFF});
        background.setCornerRadius(dp(22));
        hero.setBackground(background);
        hero.setClipToOutline(true);
        hero.setElevation(dp(2));

        View blob = new View(host);
        GradientDrawable blobShape = new GradientDrawable();
        blobShape.setShape(GradientDrawable.OVAL);
        blobShape.setColor(0x1FFFFFFF);
        blob.setBackground(blobShape);
        FrameLayout.LayoutParams blobParams = new FrameLayout.LayoutParams(dp(130), dp(130));
        blobParams.gravity = Gravity.END | Gravity.TOP;
        blobParams.rightMargin = -dp(34);
        blobParams.topMargin = -dp(46);
        hero.addView(blob, blobParams);

        LinearLayout column = new LinearLayout(host);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(18), dp(16), dp(18), dp(16));

        TextView caption = new TextView(host);
        caption.setText("已安装声音包");
        caption.setTextSize(12);
        caption.setTextColor(0xCCFFFFFF);
        column.addView(caption);

        heroCount = new TextView(host);
        heroCount.setTextSize(26);
        heroCount.setTypeface(Palette.BOLD);
        heroCount.setTextColor(0xFFFFFFFF);
        heroCount.setPadding(0, dp(2), 0, 0);
        column.addView(heroCount);

        heroSub = new TextView(host);
        heroSub.setTextSize(12);
        heroSub.setTextColor(0xD9FFFFFF);
        heroSub.setPadding(0, dp(4), 0, 0);
        column.addView(heroSub);

        TextView open = new TextView(host);
        open.setText("打开系统语音设置");
        open.setTextSize(13);
        open.setTypeface(Palette.MEDIUM);
        open.setGravity(Gravity.CENTER);
        open.setTextColor(Palette.ACCENT_DEEP);
        open.setBackground(Palette.pill(host, Palette.SURFACE, 12));
        open.setPadding(0, dp(11), 0, dp(11));
        LinearLayout.LayoutParams openParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        openParams.topMargin = dp(14);
        open.setLayoutParams(openParams);
        open.setOnClickListener(view -> {
            if (callbacks != null) callbacks.onOpenSystemSettings();
        });
        column.addView(open);

        hero.addView(column);
        return hero;
    }

    private View buildDefaultVoice() {
        LinearLayout section = new LinearLayout(host);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, dp(20), 0, 0);

        LinearLayout titleRow = new LinearLayout(host);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(host);
        title.setText("默认角色");
        title.setTextSize(15);
        title.setTypeface(Palette.MEDIUM);
        title.setTextColor(Palette.INK);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(title);

        TextView note = new TextView(host);
        note.setText("浏览器用它朗读");
        note.setTextSize(12);
        note.setTextColor(Palette.INK_FAINT);
        titleRow.addView(note);
        section.addView(titleRow);

        defaultHint = new TextView(host);
        defaultHint.setTextSize(12);
        defaultHint.setTextColor(Palette.INK_DIM);
        defaultHint.setPadding(0, dp(5), 0, 0);
        section.addView(defaultHint);

        HorizontalScrollView strip = new HorizontalScrollView(host);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setClipToPadding(false);
        strip.setPadding(0, dp(9), 0, 0);

        defaultRow = new LinearLayout(host);
        defaultRow.setOrientation(LinearLayout.HORIZONTAL);
        strip.addView(defaultRow);
        section.addView(strip);
        return section;
    }

    private View buildSectionHeader() {
        LinearLayout header = new LinearLayout(host);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(20), 0, 0);

        TextView title = new TextView(host);
        title.setText("声音包");
        title.setTextSize(15);
        title.setTypeface(Palette.MEDIUM);
        title.setTextColor(Palette.INK);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);

        TextView count = new TextView(host);
        count.setText("每个约 57 MB");
        count.setTextSize(12);
        count.setTextColor(Palette.INK_FAINT);
        header.addView(count);
        return header;
    }

    private View buildSearch() {
        EditText field = new EditText(host);
        field.setHint("搜索角色，中英日名字都能搜");
        field.setTextSize(13);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setPadding(dp(14), dp(10), dp(14), dp(10));
        field.setBackground(Palette.rounded(host, Palette.SURFACE, 14));
        field.setTextColor(Palette.INK);
        field.setHintTextColor(Palette.INK_FAINT);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        field.setLayoutParams(params);
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter(s.toString());
            }
        });
        search = field;
        return field;
    }

    private View buildAbout() {
        LinearLayout about = new LinearLayout(host);
        about.setOrientation(LinearLayout.VERTICAL);
        about.setPadding(0, dp(22), 0, 0);

        engineLine = new TextView(host);
        engineLine.setTextSize(12);
        engineLine.setTextColor(Palette.INK_DIM);
        engineLine.setLineSpacing(dp(2), 1f);
        about.addView(engineLine);

        TextView credits = new TextView(host);
        credits.setText("音声：VOICEVOX CORE · 使用各角色音声前请遵守其利用规约");
        credits.setTextSize(11);
        credits.setTextColor(Palette.INK_FAINT);
        credits.setPadding(0, dp(6), 0, 0);
        about.addView(credits);
        return about;
    }

    private void applyFilter(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        for (PackCard card : cards) card.setVisible(card.matches(needle));
    }

    // ------------------------------------------------------------------ drawing helpers

    private int dp(float value) {
        return Palette.dp(host, value);
    }

    /**
     * System-bar, cutout and keyboard insets in pixels, as {@code {top, bottom, left, right}}.
     *
     * <p>{@code WindowInsets.Type} only arrived in API 30; before that the sole way to ask is
     * the pair of getters deprecated in the same release. minSdk is 26, so the fallback has to
     * stay — it lives in its own method so the modern path never links against it on a device
     * that cannot resolve it.
     */
    private static int[] systemBarInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return modernInsets(insets);
        return legacyInsets(insets);
    }

    private static int[] modernInsets(WindowInsets insets) {
        Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                | WindowInsets.Type.displayCutout());
        Insets ime = insets.getInsets(WindowInsets.Type.ime());
        return new int[]{bars.top, Math.max(bars.bottom, ime.bottom), bars.left, bars.right};
    }

    @SuppressWarnings("deprecation")
    private static int[] legacyInsets(WindowInsets insets) {
        // Before API 30 the system-window insets already include the keyboard.
        return new int[]{insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetBottom(),
                insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetRight()};
    }
}
