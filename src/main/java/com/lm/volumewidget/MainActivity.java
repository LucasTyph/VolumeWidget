package com.lm.volumewidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.function.IntFunction;

/** Settings screen; every change is saved and pushed to the widgets immediately. */
public class MainActivity extends Activity {
    static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    SharedPreferences p;
    LinearLayout page, card;
    FrameLayout preview;
    Runnable repaintSwatches;
    float d;

    int dp(float v) {
        return Math.round(v * d);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = VolumeWidget.prefs(this);
        d = getResources().getDisplayMetrics().density;
        AudioManager am = getSystemService(AudioManager.class);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        setContentView(root);

        // Fixed header: title and a live preview of the widget.
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(16), dp(12), dp(16), dp(8));
        root.addView(head);
        TextView title = text("Volume Widget", 26, true);
        title.setPadding(dp(4), 0, 0, dp(10));
        head.addView(title);
        preview = new FrameLayout(this);
        preview.setBackground(round(dp(24), 0xFF1D2B64, 0xFF614385));
        preview.setPadding(dp(14), dp(22), dp(14), dp(22));
        head.addView(preview, new LinearLayout.LayoutParams(MATCH, WRAP));

        ScrollView sv = new ScrollView(this);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), 0, dp(16), dp(24));
        sv.addView(page);
        root.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1));

        card("Preset button");
        toggle("Show preset button", "presetOn", true);
        seek("Preset media volume", "presetIdx", VolumeWidget.presetIndex(am, p), 0, max, 1,
                v -> v + " of " + max + "  (" + Math.round(100f * v / max) + "%)", null);
        button("Apply preset now", false, v -> {
            VolumeWidget.handle(this, VolumeWidget.PRESET);
            changed();
        });

        card("Volume");
        seek("Steps per tap of + / −", "step", 1, 1, 5, 1, String::valueOf, null);
        toggle("Show step number instead of %", "discrete", false);
        note("Extra volumes — tap the label on the widget to switch");
        toggle("Ring", "s_ring", false);
        toggle("Notification", "s_notif", false);
        toggle("Alarm", "s_alarm", false);

        card("Colours");
        swatches();
        // Moving a custom-colour slider switches to the custom gradient (the last swatch).
        Runnable custom = () -> {
            p.edit().putInt("accent", VolumeWidget.CUSTOM).apply();
            repaintSwatches.run();
        };
        hue("Custom gradient start", "hue1", 280, custom);
        hue("Custom gradient end", "hue2", 190, custom);
        seek("Custom colour intensity", "sat", 80, 10, 100, 5, v -> v + "%", custom);
        toggle("Gradient background", "bgGrad", false);
        toggle("Light theme", "light", false);
        seek("Background opacity", "alpha", 85, 0, 100, 5, v -> v + "%", null);

        card("Shape and text");
        seek("Height", "height", VolumeWidget.DEF_H, 36, 80, 4, v -> v + " dp", null);
        seek("Corner roundness", "round", 100, 0, 100, 5, v -> v + "%", null);
        seek("Outline thickness", "outline", 0, 0, 6, 1, v -> v == 0 ? "off" : v + " dp", null);
        seek("Text size", "font", 13, 10, 20, 1, v -> v + " sp", null);
        choice("Volume level shown as", "level", VolumeWidget.LEVEL_BAR, "Bar", "Fill", "None");
        toggle("Show volume name", "label", true);
        toggle("Show volume number (tap it to mute)", "pct", true);

        card = page;
        View pin = button("Add widget to home screen", true, v -> {
            AppWidgetManager m = AppWidgetManager.getInstance(this);
            if (m.isRequestPinAppWidgetSupported())
                m.requestPinAppWidget(new ComponentName(this, VolumeWidget.class), null, null);
            else
                Toast.makeText(this, "Long-press the home screen and pick Widgets instead",
                        Toast.LENGTH_LONG).show();
        });
        ((LinearLayout.LayoutParams) pin.getLayoutParams()).topMargin = dp(16);
    }

    /** Saves are already applied; redraw the home screen widgets and the preview. */
    void changed() {
        VolumeWidget.updateAll(this);
        int wDp = Math.round(getResources().getDisplayMetrics().widthPixels / d) - 60;
        int hDp = p.getInt("height", VolumeWidget.DEF_H);
        preview.removeAllViews();
        preview.addView(VolumeWidget.build(this, wDp, hDp).apply(this, preview),
                new FrameLayout.LayoutParams(MATCH, dp(hDp)));
    }

    GradientDrawable round(int radius, int... colors) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                colors.length > 1 ? colors : new int[]{colors[0], colors[0]});
        g.setCornerRadius(radius);
        return g;
    }

    TextView text(String s, float sp, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(sp);
        if (bold) tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return tv;
    }

    /** Starts a new rounded section; following rows are added to it. */
    void card(String title) {
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(dp(22), getColor(R.color.card)));
        card.setPadding(dp(18), dp(14), dp(18), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(12);
        page.addView(card, lp);
        TextView tv = text(title, 14, true);
        tv.setTextColor(getColor(R.color.accent));
        tv.setPadding(0, 0, 0, dp(4));
        card.addView(tv);
    }

    TextView note(String s) {
        TextView tv = text(s, 13, false);
        tv.setAlpha(0.7f);
        tv.setPadding(0, dp(12), 0, dp(4));
        card.addView(tv);
        return tv;
    }

    void toggle(String text, String key, boolean def) {
        Switch sw = new Switch(this);
        sw.setText(text);
        sw.setTextSize(15);
        sw.setMinHeight(dp(46));
        sw.setChecked(p.getBoolean(key, def));
        sw.setOnCheckedChangeListener((v, on) -> {
            p.edit().putBoolean(key, on).apply();
            changed();
        });
        card.addView(sw, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    /**
     * Slider over [min, max] in increments of inc; fmt renders the value shown. onUser, if
     * not null, runs when the user (not the code) moves the slider.
     */
    SeekBar seek(String text, String key, int def, int min, int max, int inc,
                 IntFunction<String> fmt, Runnable onUser) {
        TextView tv = note("");
        SeekBar sb = new SeekBar(this);
        sb.setMax((max - min) / inc);
        int val = p.getInt(key, def);
        sb.setProgress((val - min) / inc);
        tv.setText(text + ":  " + fmt.apply(val));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean user) {
                int v = min + progress * inc;
                tv.setText(text + ":  " + fmt.apply(v));
                p.edit().putInt(key, v).apply();
                if (user && onUser != null) onUser.run();
                changed();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        sb.setPadding(dp(10), dp(8), dp(10), dp(8));
        card.addView(sb, new LinearLayout.LayoutParams(MATCH, WRAP));
        return sb;
    }

    /** Hue slider (0-360) drawn as a rainbow track. */
    void hue(String text, String key, int def, Runnable onUser) {
        SeekBar sb = seek(text, key, def, 0, 360, 5, v -> v + "°", onUser);
        int[] rainbow = new int[7];
        for (int i = 0; i < 7; i++)
            rainbow[i] = android.graphics.Color.HSVToColor(new float[]{i * 60, 0.8f, 1});
        GradientDrawable g = round(dp(4), rainbow);
        sb.setProgressDrawable(new android.graphics.drawable.InsetDrawable(g, 0, dp(7), 0, dp(7)));
    }

    View button(String text, boolean primary, View.OnClickListener l) {
        TextView tv = text(text, 15, true);
        tv.setGravity(Gravity.CENTER);
        if (primary) {
            tv.setTextColor(0xFFFFFFFF);
            tv.setBackground(round(dp(26), 0xFF7F00FF, 0xFF2F80ED));
        } else {
            tv.setBackground(round(dp(22), getColor(R.color.chip)));
        }
        tv.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, dp(primary ? 52 : 44));
        lp.topMargin = dp(8);
        card.addView(tv, lp);
        return tv;
    }

    /** Row of equal cells; select(i) is called on tap and paint(cell, i, selected) styles them. */
    interface CellPainter {
        void paint(TextView cell, int index, boolean selected);
    }

    Runnable picker(String key, int def, int count, int heightDp, CellPainter painter) {
        LinearLayout row = new LinearLayout(this);
        TextView[] cells = new TextView[count];
        Runnable repaint = () -> {
            for (int i = 0; i < count; i++) painter.paint(cells[i], i, i == p.getInt(key, def));
        };
        for (int i = 0; i < count; i++) {
            int index = i;
            TextView cell = cells[i] = text("", 14, true);
            cell.setGravity(Gravity.CENTER);
            cell.setOnClickListener(v -> {
                p.edit().putInt(key, index).apply();
                repaint.run();
                changed();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(heightDp), 1);
            lp.setMargins(dp(3), dp(4), dp(3), dp(4));
            row.addView(cell, lp);
        }
        repaint.run();
        card.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
        return repaint;
    }

    void choice(String text, String key, int def, String... opts) {
        note(text);
        ColorStateList normal = new TextView(this).getTextColors();
        picker(key, def, opts.length, 38, (cell, i, sel) -> {
            cell.setText(opts[i]);
            cell.setBackground(round(dp(19), getColor(sel ? R.color.accent : R.color.chip)));
            if (sel) cell.setTextColor(0xFFFFFFFF);
            else cell.setTextColor(normal);
        });
    }

    void swatches() {
        ColorStateList ring = new TextView(this).getTextColors();
        repaintSwatches = picker("accent", 0, VolumeWidget.CUSTOM + 1, 34, (cell, i, sel) -> {
            GradientDrawable g = round(dp(17), VolumeWidget.grad(p, i));
            cell.setText(i == VolumeWidget.CUSTOM ? "✎" : "");
            cell.setTextColor(0xFFFFFFFF);
            if (sel) g.setStroke(dp(3), ring);
            cell.setBackground(g);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        changed();
    }
}
