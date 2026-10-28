package com.lerneng.b1cards;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Małe pomocniki do budowania interfejsu w kodzie (bez układów XML). */
final class Ui {
    static final int BG = 0xFFFFFFFF;
    static final int INK = 0xFF111827;
    static final int MUTED = 0xFF6B7280;
    static final int ACCENT = 0xFF2563EB;
    static final int ACCENT_SOFT = 0xFFEFF6FF;
    static final int ACCENT_LINE = 0xFFBFDBFE;
    static final int LINE = 0xFFE5E7EB;
    static final int GRAY_SOFT = 0xFFF3F4F6;
    static final int GRAY_MID = 0xFFD1D5DB;
    static final int DANGER = 0xFFDC2626;
    static final int DANGER_SOFT = 0xFFFEF2F2;
    static final int DANGER_LINE = 0xFFFECACA;
    static final int GREEN = 0xFF16A34A;
    static final int GREEN_SOFT = 0xFFF0FDF4;
    static final int GREEN_LINE = 0xFFBBF7D0;
    static final int ORANGE = 0xFFFF9600;
    static final int ORANGE_DARK = 0xFFC2410C;
    static final int ORANGE_SOFT = 0xFFFFF7ED;
    static final int ORANGE_LINE = 0xFFFED7AA;

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /**
     * Polska liczba mnoga: 1 słowo, 2–4 słowa, 5+ słów (z wyjątkiem 12–14, 112–114 itd.).
     */
    static String plural(int n, String one, String few, String many) {
        if (n == 1) {
            return one;
        }
        int m10 = n % 10;
        int m100 = n % 100;
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) {
            return few;
        }
        return many;
    }

    /** „5 słów”, „1 słowo”, „2 słowa”. */
    static String words(int n) {
        return n + " " + plural(n, "słowo", "słowa", "słów");
    }

    /** Białe tło, ciemne ikony paska stanu. */
    static void setupWindow(Activity a) {
        a.getWindow().setStatusBarColor(Color.WHITE);
        a.getWindow().setNavigationBarColor(Color.WHITE);
        a.getWindow().getDecorView().setBackgroundColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            a.getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    static GradientDrawable shape(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) {
            g.setStroke(dp(c, 1), stroke);
        }
        return g;
    }

    static GradientDrawable circle(Context c, int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        if (stroke != 0) {
            g.setStroke(dp(c, 2), stroke);
        }
        return g;
    }

    static Drawable ripple(Context c, int fill, int stroke, float radiusDp) {
        return new RippleDrawable(
                ColorStateList.valueOf(0x22000000),
                shape(c, fill, stroke, radiusDp),
                shape(c, 0xFFFFFFFF, 0, radiusDp));
    }

    static Button button(Context c, String label, int fill, int stroke, int textColor) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(textColor);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setBackground(ripple(c, fill, stroke, 14));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        return b;
    }

    static LinearLayout.LayoutParams lp(Context c, int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    /** Wiersz-nagłówek: strzałka „wstecz” + tytuł. Z prawej można dodać własne elementy. */
    static LinearLayout header(final Activity a, String title) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text(a, "←", 26, INK, true);
        back.setPadding(dp(a, 8), dp(a, 4), dp(a, 16), dp(a, 4));
        back.setOnClickListener(v -> a.finish());
        row.addView(back);

        TextView t = text(a, title, 20, INK, true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        return row;
    }
}
