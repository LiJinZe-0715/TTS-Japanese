package app.voicevox.tts.presentation.settings.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import app.voicevox.tts.domain.voice.VoiceCatalog;
import app.voicevox.tts.domain.voice.VoicePack;
import app.voicevox.tts.domain.voice.VoiceStyle;
import app.voicevox.tts.domain.voice.VoiceNames;
import app.voicevox.tts.presentation.settings.Format;
import app.voicevox.tts.presentation.settings.SettingsScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One voice pack as a card: avatar, character name, file/size/state, a download-or-delete
 * pill, a progress bar and one preview chip per character.
 *
 * <p>It owns its own chips, so the page never has to keep a global style-id lookup table —
 * {@link #applyPlaying} walks the cards and each one highlights what it holds. Every decision
 * is still the presenter's: taps leave through {@link SettingsScreen.Callbacks}.
 */
final class PackCard {
    private final Context context;
    private final VoicePack pack;
    private final boolean installed;
    private final SettingsScreen.Callbacks callbacks;
    private final String language;

    private final LinearLayout view;
    private final TextView action;
    private final ProgressBar progress;
    private final String haystack;
    private final List<Chip> chips = new ArrayList<>();

    PackCard(Context context, VoicePack pack, boolean installed,
             SettingsScreen.Callbacks callbacks, String language) {
        this.context = context;
        this.pack = pack;
        this.installed = installed;
        this.callbacks = callbacks;
        this.language = language;
        // Every name a user might type, in every language the list can be shown in: someone who
        // knows the character as "ずんだもん" and someone who knows "毛豆泥" both find the pack.
        // (The old version appended the style list, whose toString is an object hash.)
        StringBuilder hay = new StringBuilder();
        for (String lang : VoiceNames.languages()) {
            hay.append(pack.characters(lang)).append(' ');
        }
        hay.append(pack.file);
        for (VoiceStyle style : pack.styles) {
            for (String lang : VoiceNames.languages()) {
                hay.append(' ').append(style.displayName(lang));
            }
        }
        this.haystack = hay.toString().toLowerCase(Locale.ROOT);

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(13), dp(14), dp(12));
        GradientDrawable background = Palette.rounded(context, Palette.SURFACE, 18);
        background.setStroke(dp(1), installed ? 0x1F5B6CF9 : 0x0F000000);
        box.setBackground(background);
        box.setElevation(dp(1.5f));
        LinearLayout.LayoutParams boxParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        boxParams.bottomMargin = dp(10);
        box.setLayoutParams(boxParams);
        this.view = box;

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(head);

        head.addView(Palette.avatar(context, pack.characters(language)));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        columnParams.leftMargin = dp(12);
        column.setLayoutParams(columnParams);
        head.addView(column);

        TextView name = new TextView(context);
        name.setText(pack.characters(language));
        name.setTextSize(15);
        name.setTypeface(Palette.MEDIUM);
        name.setTextColor(Palette.INK);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(name);

        column.addView(buildMeta());

        action = new TextView(context);
        action.setTextSize(12);
        action.setTypeface(Palette.MEDIUM);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(14), dp(8), dp(14), dp(8));
        head.addView(action);
        bindAction();

        progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(Palette.ACCENT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0x14000000));
        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        progressParams.topMargin = dp(10);
        progress.setLayoutParams(progressParams);
        progress.setVisibility(View.GONE);
        box.addView(progress);

        box.addView(buildChipStrip());
    }

    /** "0.vvm · 57.4 MB · 已下载" — the state is coloured so the list scans at a glance. */
    private TextView buildMeta() {
        String base = pack.file + " · " + Format.megabytes(pack.bytes);
        String state = installed ? ("0.vvm".equals(pack.file) ? "内置" : "已下载") : "未下载";
        SpannableString line = new SpannableString(base + " · " + state);
        int start = base.length() + 3;
        line.setSpan(new ForegroundColorSpan(installed ? Palette.GOOD : Palette.INK_FAINT),
                start, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        line.setSpan(new StyleSpan(Typeface.BOLD), start, line.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        TextView meta = new TextView(context);
        meta.setText(line);
        meta.setTextSize(12);
        meta.setTextColor(Palette.INK_DIM);
        meta.setPadding(0, dp(3), 0, 0);
        return meta;
    }

    private View buildChipStrip() {
        HorizontalScrollView strip = new HorizontalScrollView(context);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setClipToPadding(false);
        strip.setPadding(0, dp(10), 0, 0);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        strip.addView(row);

        List<VoiceStyle> styles = pack.defaultStyles();
        // A one-character pack reads better with the style on the chip; a pack with several
        // characters needs the name, or the chips are indistinguishable.
        boolean solo = styles.size() == 1;
        for (VoiceStyle style : styles) {
            String label = solo ? style.styleName(language) : style.characterName(language);
            Chip chip = new Chip(style, label);
            chips.add(chip);
            row.addView(chip.view);
        }
        return strip;
    }

    View view() {
        return view;
    }

    boolean matches(String needle) {
        return needle.isEmpty() || haystack.contains(needle);
    }

    void setVisible(boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** Download when missing, delete when present — and never hide the preview chips. */
    private void bindAction() {
        action.setClickable(true);
        if (installed) {
            action.setText("删除");
            action.setTextColor(Palette.DANGER);
            action.setBackground(Palette.pill(context, Palette.DANGER_SOFT, 11));
            action.setOnClickListener(v -> {
                if (callbacks != null) callbacks.onDeleteRequested(pack);
            });
        } else {
            action.setText("下载");
            action.setTextColor(Color.WHITE);
            action.setBackground(Palette.pill(context, Palette.ACCENT, 11));
            action.setOnClickListener(v -> {
                if (callbacks != null) callbacks.onDownloadRequested(pack);
            });
        }
    }

    void setDownloading(boolean active, int percent) {
        if (active) {
            progress.setVisibility(View.VISIBLE);
            progress.setProgress(percent);
            action.setText(percent + "%");
            action.setTextColor(Palette.ACCENT_DEEP);
            action.setBackground(Palette.pill(context, Palette.ACCENT_SOFT, 11));
            // Inert while the download runs: a second tap would only be rejected by the
            // presenter, so do not invite it.
            action.setOnClickListener(null);
            action.setClickable(false);
        } else {
            progress.setVisibility(View.GONE);
            action.setClickable(true);
            bindAction();
        }
    }

    /** {@code styleId} is the one currently speaking, or negative for silence. */
    void applyPlaying(int styleId) {
        for (Chip chip : chips) {
            chip.apply(chip.style.id == styleId);
        }
    }

    /** One character inside the pack. Always tappable: a missing pack downloads on the way. */
    private final class Chip {
        final VoiceStyle style;
        final String label;
        final TextView view;

        Chip(VoiceStyle style, String label) {
            this.style = style;
            this.label = label;
            this.view = new TextView(context);
            view.setTextSize(12);
            view.setGravity(Gravity.CENTER);
            view.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            params.rightMargin = dp(7);
            view.setLayoutParams(params);
            view.setOnClickListener(v -> {
                if (callbacks != null) {
                    callbacks.onPreviewRequested(VoiceCatalog.packOfStyle(style.id), style);
                }
            });
            apply(false);
        }

        void apply(boolean playing) {
            if (playing) {
                view.setText("■ " + label);
                view.setTextColor(Color.WHITE);
                view.setTypeface(Palette.MEDIUM);
                view.setBackground(Palette.pill(context, Palette.ACCENT, 100));
            } else if (installed) {
                view.setText("▶ " + label);
                view.setTextColor(Palette.ACCENT_DEEP);
                view.setTypeface(Palette.MEDIUM);
                view.setBackground(Palette.pill(context, Palette.ACCENT_SOFT, 100));
            } else {
                view.setText("↓ " + label);
                view.setTextColor(Palette.INK_FAINT);
                view.setTypeface(Typeface.DEFAULT);
                view.setBackground(Palette.pill(context, Palette.CHIP_OFF_BG, 100));
            }
        }
    }

    private int dp(float value) {
        return Palette.dp(context, value);
    }
}
