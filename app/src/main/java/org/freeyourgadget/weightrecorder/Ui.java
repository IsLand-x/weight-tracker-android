/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Ui {
    final Context context;
    final int text, muted, primary, afternoon, surface, background, line, tint;
    Ui(Context context) {
        this.context = context;
        text = color(R.color.wr_text); muted = color(R.color.wr_muted); primary = color(R.color.wr_primary);
        afternoon = color(R.color.wr_afternoon); surface = color(R.color.wr_surface);
        background = color(R.color.wr_background); line = color(R.color.wr_grid); tint = color(R.color.wr_primary_container);
    }
    int color(int id) { return ContextCompat.getColor(context, id); }
    int dp(float n) { return Math.round(n * context.getResources().getDisplayMetrics().density); }
    LinearLayout column() { LinearLayout v = new LinearLayout(context); v.setOrientation(LinearLayout.VERTICAL); return v; }
    LinearLayout row() { LinearLayout v = new LinearLayout(context); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    TextView label(String value, float size, int color) {
        TextView v = new AppCompatTextView(context); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setIncludeFontPadding(false); v.setPadding(0, dp(2), 0, dp(2)); return v;
    }
    TextView title(String value, float size) {
        TextView v = label(value, size, text); v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return v;
    }
    MaterialButton textButton(String value) {
        MaterialButton b = new MaterialButton(context, null, R.attr.wrTextButtonStyle);
        b.setText(value); b.setTextSize(14); b.setAllCaps(false); b.setMinHeight(dp(48));
        b.setMinWidth(dp(48)); b.setMinimumWidth(dp(48)); b.setInsetTop(0); b.setInsetBottom(0);
        b.setCornerRadius(dp(12)); b.setPadding(dp(12), 0, dp(12), 0); return b;
    }
    MaterialButton iconButton(int icon, String description) {
        MaterialButton b = textButton(""); b.setIconResource(icon); b.setIconSize(dp(22));
        b.setIconTint(ColorStateList.valueOf(text)); b.setIconPadding(0);
        b.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START); b.setPadding(0, 0, 0, 0);
        b.setContentDescription(description); return b;
    }
    MaterialButton segment(String value, int id, int size) {
        MaterialButton button = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setId(id); button.setText(value); button.setTextSize(size); button.setAllCaps(false);
        button.setMinWidth(dp(48)); button.setMinimumWidth(dp(48)); button.setInsetTop(0); button.setInsetBottom(0);
        button.setCornerRadius(dp(12)); button.setPadding(dp(4), 0, dp(4), 0); button.setStrokeWidth(0);
        int[][] states = {new int[]{android.R.attr.state_checked}, new int[]{}};
        button.setBackgroundTintList(new ColorStateList(states, new int[]{primary, background}));
        button.setTextColor(new ColorStateList(states, new int[]{color(R.color.wr_on_primary), muted})); return button;
    }
    LinearLayout toolbar(String title, Runnable back, View action) {
        LinearLayout bar = row(); bar.setPadding(dp(8), dp(4), dp(8), dp(4));
        if (back != null) {
            MaterialButton button = iconButton(R.drawable.ic_back, "返回"); button.setOnClickListener(v -> back.run());
            bar.addView(button, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        TextView name = title(title, back == null ? 22 : 20); name.setPadding(dp(back == null ? 8 : 4), 0, 0, 0);
        bar.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) bar.addView(action, new LinearLayout.LayoutParams(-2, dp(48)));
        return bar;
    }
    LinearLayout card(LinearLayout parent, int padding) {
        MaterialCardView card = new MaterialCardView(context); card.setRadius(dp(18));
        card.setCardElevation(0); card.setStrokeWidth(0); card.setCardBackgroundColor(surface);
        LinearLayout content = column(); content.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        card.addView(content); parent.addView(card, new LinearLayout.LayoutParams(-1, -2)); return content;
    }
    void space(LinearLayout parent, int height) { parent.addView(new View(context), new LinearLayout.LayoutParams(1, dp(height))); }
    void divider(LinearLayout parent) {
        View v = new View(context); v.setBackgroundColor(line); parent.addView(v, new LinearLayout.LayoutParams(-1, dp(1)));
    }
    void clickable(View view, int radius) {
        GradientDrawable mask = new GradientDrawable(); mask.setColor(surface); mask.setCornerRadius(dp(radius));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf((primary & 0x00ffffff) | 0x18000000), mask, null));
        view.setFocusable(true);
    }
    void groupedBackground(View view, boolean first, boolean last) {
        float top = first ? dp(12) : 0, bottom = last ? dp(12) : 0;
        GradientDrawable shape = new GradientDrawable(); shape.setColor(surface);
        shape.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        view.setBackground(new RippleDrawable(ColorStateList.valueOf((primary & 0x00ffffff) | 0x18000000), shape, null));
    }
    static String date(long millis, String pattern) {
        return new SimpleDateFormat(pattern, Locale.SIMPLIFIED_CHINESE).format(new Date(millis));
    }
    static String recordState(String state) {
        switch (state) {
            case "pending": return "等待发送";
            case "sending": return "正在发送";
            case "success": return "已记录";
            case "failed": return "发送未确认";
            default: return "仅本机";
        }
    }
    TextView weight(double weight, int size) {
        TextView v = title("", size); bindWeight(v, weight); return v;
    }
    private void bindWeight(TextView view, double weight) {
        String value = String.format(Locale.getDefault(), "%.2f kg", weight);
        SpannableString styled = new SpannableString(value); int unit = value.length() - 2;
        styled.setSpan(new RelativeSizeSpan(.65f), unit, value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new ForegroundColorSpan(muted), unit, value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(styled);
    }
    LinearLayout recordRow(WeightDatabase.Record record, boolean showDate) {
        LinearLayout row = row(); row.setPadding(dp(4), dp(10), dp(4), dp(10)); row.setMinimumHeight(dp(60));
        LinearLayout details = column();
        TextView time = label("", 14, text); details.addView(time);
        LinearLayout meta = row();
        TextView period = label("", 12, primary);
        meta.addView(period); TextView state = label("", 12, muted);
        meta.addView(state); details.addView(meta);
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1)); TextView weight = weight(record.weight, 21); row.addView(weight);
        clickable(row, 10); row.setTag(new RecordHolder(time, period, state, weight)); bindRecordRow(row, record, showDate);
        return row;
    }
    void bindRecordRow(LinearLayout row, WeightDatabase.Record record, boolean showDate) {
        RecordHolder holder = (RecordHolder) row.getTag();
        holder.time.setText(date(record.timeMillis, showDate ? "MM月dd日 HH:mm" : "HH:mm"));
        boolean morning = Settings.periods(context).isMorning(record.timeMillis);
        holder.period.setText(morning ? "上午" : "下午"); holder.period.setTextColor(morning ? primary : afternoon);
        holder.state.setText("  ·  " + recordState(record.state));
        holder.state.setTextColor(record.state.equals("failed") ? color(R.color.wr_error) : muted);
        bindWeight(holder.weight, record.weight);
        row.setContentDescription(date(record.timeMillis, "yyyy.MM.dd HH:mm") + "，" + record.weight + "公斤，"
                + Settings.periods(context).label(record.timeMillis) + "，" + recordState(record.state));
    }
    private static final class RecordHolder {
        final TextView time, period, state, weight;
        RecordHolder(TextView time, TextView period, TextView state, TextView weight) {
            this.time = time; this.period = period; this.state = state; this.weight = weight;
        }
    }
    void install(AppCompatActivity activity, View root) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
        activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= 29) activity.getWindow().setNavigationBarContrastEnforced(false);
        root.setBackgroundColor(background);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard)); return insets;
        });
        boolean dark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView()).setAppearanceLightStatusBars(!dark);
        WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView()).setAppearanceLightNavigationBars(!dark);
        activity.setContentView(root);
    }
}
