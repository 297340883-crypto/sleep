package com.nightpulse.sleepscope;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Ui {
    public static final int BG = 0xff071624, CARD = 0xff10283a, CARD2 = 0xff142f43;
    public static final int MINT = 0xff7de5c6, WHITE = 0xfff3f8ff, MUTED = 0xff9cb3c3;
    public static final int AMBER = 0xfff5c77d, BLUE = 0xff86beff;
    private Ui() { }

    public static int dp(Context c, float n) { return (int) (n * c.getResources().getDisplayMetrics().density + .5f); }

    public static GradientDrawable bg(int color, int radius, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radius));
        return d;
    }

    public static GradientDrawable border(int color, int stroke, int radius, Context c) {
        GradientDrawable d = bg(color, radius, c);
        d.setStroke(dp(c, 1), stroke);
        return d;
    }

    public static TextView text(Context c, String str, int size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(str);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setFontFeatureSettings("kern");
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setIncludeFontPadding(true);
        return t;
    }

    public static TextView button(Context c, String str, boolean solid) {
        TextView b = text(c, str, 17, solid ? BG : WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(bg(solid ? MINT : CARD2, 18, c));
        b.setMinHeight(dp(c, 56));
        b.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    public static LinearLayout col(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    public static LinearLayout row(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }

    public static LinearLayout card(Context c) {
        LinearLayout l = col(c);
        l.setPadding(dp(c, 20), dp(c, 20), dp(c, 20), dp(c, 20));
        l.setBackground(bg(CARD, 20, c));
        return l;
    }

    public static ScrollView page(Activity a, LinearLayout outer) {
        a.getWindow().setStatusBarColor(BG);
        a.getWindow().setNavigationBarColor(BG);
        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(BG);
        outer.setPadding(dp(a, 22), dp(a, 24), dp(a, 22), dp(a, 35));
        scroll.addView(outer);
        a.setContentView(scroll);
        return scroll;
    }

    public static void spaceHorizontal(LinearLayout parent, Context c, int width) {
        View v = new View(c);
        parent.addView(v, new LinearLayout.LayoutParams(dp(c, width), 1));
    }

    public static void space(LinearLayout parent, Context c, int height) {
        View v = new View(c);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(c, height)));
    }

    public static LinearLayout.LayoutParams lp(Context c, int mt) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(c, mt);
        return p;
    }

    public static String clock(long timestamp) {
        return new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date(timestamp));
    }

    public static String date(long timestamp) {
        return new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(new Date(timestamp));
    }

    public static String dateTime(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(timestamp));
    }

    public static String duration(long ms) {
        long totalMin = Math.max(0, ms) / 60000;
        if (totalMin == 0) return Math.max(0, ms) / 1000 + "秒";
        return totalMin / 60 > 0 ? (totalMin / 60 + "小时" + totalMin % 60 + "分") : totalMin + "分钟";
    }

    public static String timer(long ms) {
        long seconds = Math.max(0, ms) / 1000;
        return String.format(Locale.CHINA, "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60);
    }
}
