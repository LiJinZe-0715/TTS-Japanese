package app.voicevox.tts.presentation.settings.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Palette and the handful of drawing primitives the screen is assembled from.
 *
 * <p>Everything visual that more than one view needs lives here, so the page skeleton and the
 * cards cannot drift apart on colour or corner radius.
 */
public final class Palette {
    public static final int BG = 0xFFF4F6FA;
    public static final int SURFACE = 0xFFFFFFFF;
    public static final int INK = 0xFF171A21;
    public static final int INK_DIM = 0xFF6B7280;
    public static final int INK_FAINT = 0xFF9BA3B0;
    public static final int ACCENT = 0xFF5B6CF9;
    public static final int ACCENT_DEEP = 0xFF4453E0;
    public static final int ACCENT_SOFT = 0xFFEEF0FE;
    public static final int DANGER = 0xFFD93B3B;
    public static final int DANGER_SOFT = 0xFFFDECEC;
    public static final int GOOD = 0xFF0F9D6B;
    public static final int GOOD_SOFT = 0xFFE7F7EF;
    public static final int CHIP_OFF_BG = 0xFFF1F3F7;

    public static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    public static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);

    private static final int[] AVATARS = {
            0xFF6C7BFF, 0xFF9B6BFF, 0xFF2FA8BC, 0xFFEF7F3C,
            0xFFDD5B8A, 0xFF3E9E62, 0xFF5C6BC0, 0xFFC79A2A,
    };

    private Palette() {
    }

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable rounded(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    /** A filled pill with the standard ripple on top, used for every tappable control. */
    public static RippleDrawable pill(Context context, int color, float radiusDp) {
        GradientDrawable shape = rounded(context, color, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(0x22000000), shape, null);
    }

    /** A round badge holding the first glyph of a character name. */
    public static TextView avatar(Context context, String name) {
        TextView view = new TextView(context);
        view.setText(name.isEmpty() ? "?" : new String(Character.toChars(name.codePointAt(0))));
        view.setTextSize(15);
        view.setTypeface(MEDIUM);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(AVATARS[(name.hashCode() & 0x7FFFFFFF) % AVATARS.length]);
        view.setBackground(shape);
        int size = dp(context, 38);
        view.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return view;
    }
}
