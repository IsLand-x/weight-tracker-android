/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import java.util.Locale;

public class LiveWeighingActivity extends AppCompatActivity {
    private Ui ui;
    private TextView weightDisplay, statusText, subHint;
    private WeightGaugeView gauge;
    private boolean isStable;
    private double currentWeight;
    private int maximum;
    private final BroadcastReceiver liveReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            updateWeighing(intent.getDoubleExtra("weight", 0), intent.getBooleanExtra("stable", false), true);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ui = new Ui(this);
        maximum = Settings.prefs(this).getInt("gauge_maximum", 110);
        if (maximum != 100 && maximum != 110 && maximum != 200) maximum = 110;
        LinearLayout screen = ui.column();
        screen.addView(ui.toolbar("实时称重", this::finish, null));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout root = ui.column();
        root.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL); scroll.addView(root);
        LinearLayout card = ui.card(root, 20); card.setGravity(Gravity.CENTER_HORIZONTAL);
        statusText = ui.label("正在称重，请保持平稳站立", 16, ui.muted);
        statusText.setGravity(Gravity.CENTER); card.addView(statusText); ui.space(card, 8);
        gauge = new WeightGaugeView(this); gauge.setMaximum(maximum);
        card.addView(gauge, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout valueRow = ui.row(); valueRow.setGravity(Gravity.CENTER);
        weightDisplay = ui.label("0.00", 48, ui.text);
        weightDisplay.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        valueRow.addView(weightDisplay);
        TextView kg = ui.label("kg", 18, ui.muted); kg.setPadding(ui.dp(8), ui.dp(20), 0, 0);
        valueRow.addView(kg); card.addView(valueRow);
        gauge.setOnDisplayWeight(weight -> {
            weightDisplay.setText(String.format(Locale.getDefault(), "%.2f", weight));
            weightDisplay.setContentDescription(String.format(Locale.getDefault(), "体重 %.2f 千克", weight));
        });
        ui.space(card, 12);
        subHint = ui.label("指针与数字实时变化，稳定后自动锁定", 13, ui.muted);
        subHint.setGravity(Gravity.CENTER); card.addView(subHint);
        ui.space(root, 24);
        TextView rangeLabel = ui.label("仪表盘最大量程", 13, ui.muted); root.addView(rangeLabel);
        ui.space(root, 8);
        MaterialButtonToggleGroup ranges = new MaterialButtonToggleGroup(this);
        ranges.setSingleSelection(true); ranges.setSelectionRequired(true);
        int[] choices = {100, 110, 200};
        for (int choice : choices) {
            MaterialButton button = ui.segment(choice + " kg", View.generateViewId(), 14);
            button.setTag(choice);
            ranges.addView(button, new LinearLayout.LayoutParams(0, ui.dp(48), 1));
            if (choice == maximum) ranges.check(button.getId());
        }
        ranges.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            maximum = (int) group.findViewById(id).getTag();
            Settings.prefs(this).edit().putInt("gauge_maximum", maximum).apply();
            gauge.setMaximum(maximum); updateHint();
        });
        root.addView(ranges, new LinearLayout.LayoutParams(-1, -2)); ui.space(root, 24);
        MaterialButton finish = ui.textButton("完成"); finish.setOnClickListener(v -> finish());
        root.addView(finish, new LinearLayout.LayoutParams(-1, ui.dp(48)));
        ui.install(this, screen);
        currentWeight = state == null ? getIntent().getDoubleExtra("initial_weight", 0) : state.getDouble("weight");
        boolean stable = state == null ? getIntent().getBooleanExtra("initial_stable", false) : state.getBoolean("stable");
        updateWeighing(currentWeight, stable, false);
    }
    private void updateWeighing(double weight, boolean stable, boolean animate) {
        if (!Double.isFinite(weight) || weight < 3 || weight > 300 || isStable) return;
        currentWeight = weight; isStable = stable;
        gauge.setWeight(weight, stable, animate);
        statusText.setText(stable ? "体重已稳定锁定" : "正在称重，请保持平稳站立");
        statusText.setTextColor(stable ? ui.primary : ui.muted);
        weightDisplay.setTextColor(stable ? ui.primary : ui.text);
        updateHint();
    }
    private void updateHint() {
        subHint.setText(currentWeight > maximum ? "体重超出当前量程，请切换更大量程" : isStable
                ? "测量已完成，点击完成返回查看记录" : "指针与数字实时变化，稳定后自动锁定");
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putDouble("weight", currentWeight); state.putBoolean("stable", isStable);
        super.onSaveInstanceState(state);
    }
    @Override protected void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(this, liveReceiver, new IntentFilter(getPackageName() + ".LIVE_WEIGHT"),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        if (getApplication() instanceof WeightApp) {
            LiveWeighingLauncher launcher = ((WeightApp) getApplication()).liveWeighing;
            if (launcher != null && launcher.latest != null) {
                updateWeighing(launcher.latest.weightKg, launcher.latest.stable, false);
            }
        }
    }
    @Override protected void onStop() {
        unregisterReceiver(liveReceiver); super.onStop();
    }
}
