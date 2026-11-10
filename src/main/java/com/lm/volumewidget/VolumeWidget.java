package com.lm.volumewidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

public class VolumeWidget extends AppWidgetProvider {
    static final String ACT = "com.lm.volumewidget.ACT";
    static final int DOWN = 0, UP = 1, PRESET = 2, CYCLE = 3, MUTE = 4;
    /** Values of the "level" pref: how the current volume is drawn. */
    static final int LEVEL_BAR = 0, LEVEL_FILL = 1, LEVEL_NONE = 2;
    /** Default for the "height" pref: the tallest the widget draws itself, in dp. */
    static final int DEF_H = 52;
    /** Value of the "accent" pref selecting the user's own gradient (hue1, hue2, sat). */
    static final int CUSTOM = 7;

    static final int[] STREAMS = {AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_ALARM};
    static final String[] NAMES = {"Media", "Ring", "Notif", "Alarm"};
    /** Pref keys enabling each stream on the widget (media is always enabled). */
    static final String[] KEYS = {"s_media", "s_ring", "s_notif", "s_alarm"};

    /** Accent gradients (start, end), chosen by the "accent" pref. */
    static final int[][] GRADS = {
            {0xFF2F80ED, 0xFF56CCF2}, {0xFF7F00FF, 0xFFE100FF}, {0xFFFF5E62, 0xFFFF9966},
            {0xFF11998E, 0xFF38EF7D}, {0xFFF12711, 0xFFF5AF19}, {0xFFEC008C, 0xFFFC6767},
            {0xFF8E9EAB, 0xFFEEF2F3}};

    /** Gradient (start, end) for accent index i; CUSTOM is built from the hue/sat prefs. */
    static int[] grad(SharedPreferences p, int i) {
        if (i < GRADS.length) return GRADS[i];
        float sat = p.getInt("sat", 80) / 100f;
        return new int[]{Color.HSVToColor(new float[]{p.getInt("hue1", 280), sat, 1}),
                Color.HSVToColor(new float[]{p.getInt("hue2", 190), sat, 1})};
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    static boolean enabled(SharedPreferences p, int i) {
        return i == 0 || p.getBoolean(KEYS[i], false);
    }

    static int minVol(AudioManager am, int stream) {
        return Build.VERSION.SDK_INT >= 28 ? am.getStreamMinVolume(stream) : 0;
    }

    /** Preset media volume as a stream index; defaults to 40% of max. */
    static int presetIndex(AudioManager am, SharedPreferences p) {
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int i = p.getInt("presetIdx", -1);
        return i < 0 ? Math.round(max * 0.4f) : Math.min(i, max);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (ACT.equals(i.getAction())) {
            handle(c, i.getIntExtra("op", -1));
            updateAll(c);
        } else {
            super.onReceive(c, i);
        }
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        updateAll(c);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        updateAll(c);
    }

    static void handle(Context c, int op) {
        SharedPreferences p = prefs(c);
        AudioManager am = c.getSystemService(AudioManager.class);
        int cur = p.getInt("cur", 0);
        if (!enabled(p, cur)) cur = 0;
        int s = STREAMS[cur];
        try {
            switch (op) {
                case DOWN:
                case UP: {
                    int step = p.getInt("step", 1) * (op == UP ? 1 : -1);
                    int v = am.getStreamVolume(s) + step;
                    v = Math.max(minVol(am, s), Math.min(am.getStreamMaxVolume(s), v));
                    am.setStreamVolume(s, v, 0);
                    break;
                }
                case PRESET:
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, presetIndex(am, p), 0);
                    break;
                case CYCLE: {
                    int n = cur;
                    do n = (n + 1) % STREAMS.length; while (!enabled(p, n));
                    p.edit().putInt("cur", n).apply();
                    break;
                }
                case MUTE:
                    am.adjustStreamVolume(s, AudioManager.ADJUST_TOGGLE_MUTE, 0);
                    break;
            }
        } catch (SecurityException e) {
            // Ring/notification changes are blocked while Do Not Disturb is on.
        }
    }

    static PendingIntent pi(Context c, int op) {
        Intent i = new Intent(c, VolumeWidget.class).setAction(ACT).putExtra("op", op);
        return PendingIntent.getBroadcast(c, op, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (int id : m.getAppWidgetIds(new ComponentName(c, VolumeWidget.class))) {
            Bundle o = m.getAppWidgetOptions(id);
            int w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            int h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
            m.updateAppWidget(id, build(c, w > 0 ? w : 300, h > 0 ? h : DEF_H));
        }
    }

    /**
     * A rounded bar of w x h px painted with base, with the left frac of it overpainted by
     * fill and an outline drawn with stroke (fill and stroke may be null). round is 0..1,
     * where 1 gives fully semicircular ends.
     */
    static Bitmap pill(int w, int h, float round, Paint base, Paint fill, float frac,
                       Paint stroke) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        RectF r = new RectF(0, 0, w, h);
        float rad = h / 2f * round;
        cv.drawRoundRect(r, rad, rad, base);
        if (fill != null && frac > 0) {
            int layer = cv.saveLayer(r, null);
            // Rounded leading edge; never narrower than its two corners so it stays a clean shape.
            cv.drawRoundRect(0, 0, Math.max(w * frac, 2 * rad), h, rad, rad, fill);
            Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
            mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            cv.drawRoundRect(r, rad, rad, mask);
            cv.restoreToCount(layer);
        }
        if (stroke != null) {
            float half = stroke.getStrokeWidth() / 2;
            r.inset(half, half);
            float sr = Math.max(rad - half, 0);
            cv.drawRoundRect(r, sr, sr, stroke);
        }
        return b;
    }

    static Paint solid(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        return p;
    }

    static Paint gradient(int[] colors, int width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, width, 0, colors[0], colors[1],
                Shader.TileMode.CLAMP));
        return p;
    }

    /** Builds the widget views for a widget of the given size in dp. */
    static RemoteViews build(Context c, int wDp, int hDp) {
        SharedPreferences p = prefs(c);
        AudioManager am = c.getSystemService(AudioManager.class);
        int cur = p.getInt("cur", 0);
        if (!enabled(p, cur)) cur = 0;
        int s = STREAMS[cur];
        int max = am.getStreamMaxVolume(s), v = am.getStreamVolume(s);
        float frac = (float) v / max;

        boolean light = p.getBoolean("light", false);
        boolean bgGrad = p.getBoolean("bgGrad", false);
        boolean discrete = p.getBoolean("discrete", false);
        boolean showLabel = p.getBoolean("label", true);
        boolean showPct = p.getBoolean("pct", true);
        boolean showPreset = p.getBoolean("presetOn", true);
        int level = p.getInt("level", LEVEL_BAR);
        int[] grad = grad(p, p.getInt("accent", 0));
        float round = p.getInt("round", 100) / 100f;
        int font = p.getInt("font", 13);
        int fg = light && !bgGrad ? 0xFF202124 : 0xFFFFFFFF;
        int alpha = p.getInt("alpha", 85) * 255 / 100;

        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
        // Bitmaps are scaled down for very wide widgets to keep the RemoteViews small.
        float d = Math.min(c.getResources().getDisplayMetrics().density, 1000f / wDp);
        if (Build.VERSION.SDK_INT >= 31) {
            hDp = Math.min(hDp, p.getInt("height", DEF_H));
            rv.setViewLayoutHeight(R.id.box, hDp, TypedValue.COMPLEX_UNIT_DIP);
        }
        int w = Math.round(wDp * d), h = Math.round(hDp * d);

        // On a gradient background the level is drawn as a translucent white overlay.
        Paint base = bgGrad ? gradient(grad, w) : solid(light ? 0xFFFFFFFF : 0xFF1B1C20);
        base.setAlpha(alpha);
        Paint fill = bgGrad ? solid(0x59FFFFFF) : gradient(grad, w);
        Paint stroke = null;
        int outline = p.getInt("outline", 0);
        if (outline > 0) {
            stroke = bgGrad ? solid(0xB3FFFFFF) : gradient(grad, w);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(outline * d);
        }
        rv.setImageViewBitmap(R.id.bg,
                pill(w, h, round, base, level == LEVEL_FILL ? fill : null, frac, stroke));

        if (level == LEVEL_BAR) {
            // The bar is stretched to its slot, so render it at roughly the slot's size.
            int barDp = wDp - 92 - ((showLabel ? 50 : 0) + (showPct ? 42 : 0)
                    + (showPreset ? 62 : 0)) * font / 13;
            int bw = Math.round(Math.max(barDp, 24) * d), bh = Math.round(8 * d);
            Paint barFill = bgGrad ? solid(0xFFFFFFFF) : gradient(grad, bw);
            rv.setImageViewBitmap(R.id.bar,
                    pill(bw, bh, round, solid(fg & 0x38FFFFFF), barFill, frac, null));
        }
        // Keep the bar's slot (so the buttons stay spread out) even when it isn't drawn.
        rv.setViewVisibility(R.id.bar, level == LEVEL_BAR ? View.VISIBLE : View.INVISIBLE);

        rv.setTextViewText(R.id.label, NAMES[cur]);
        rv.setViewVisibility(R.id.label, vis(showLabel));
        rv.setTextViewText(R.id.pct, discrete ? String.valueOf(v) : Math.round(100 * frac) + "%");
        rv.setViewVisibility(R.id.pct, vis(showPct));
        int pre = presetIndex(am, p), musicMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        rv.setTextViewText(R.id.preset,
                discrete ? String.valueOf(pre) : Math.round(100f * pre / musicMax) + "%");
        rv.setViewVisibility(R.id.preset, vis(showPreset));

        for (int id : new int[]{R.id.label, R.id.minus, R.id.pct, R.id.plus, R.id.preset}) {
            rv.setTextColor(id, fg);
            boolean button = id == R.id.minus || id == R.id.plus;
            rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, font + (button ? 11 : 0));
        }

        rv.setOnClickPendingIntent(R.id.minus, pi(c, DOWN));
        rv.setOnClickPendingIntent(R.id.plus, pi(c, UP));
        rv.setOnClickPendingIntent(R.id.preset, pi(c, PRESET));
        rv.setOnClickPendingIntent(R.id.label, pi(c, CYCLE));
        rv.setOnClickPendingIntent(R.id.pct, pi(c, MUTE));
        return rv;
    }

    static int vis(boolean on) {
        return on ? View.VISIBLE : View.GONE;
    }
}
