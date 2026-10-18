/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import java.util.Locale;

public class LiveWeighingActivity extends AppCompatActivity {
    public static volatile boolean isRunning = false;
    private Ui ui;
    private TextView weightDisplay, statusText, subHint;
    private ProgressBar progress;
    private MaterialButton finishBtn;
    private boolean isStable;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoFinish = this::finish;

    private final BroadcastReceiver liveReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            double weight = intent.getDoubleExtra("weight", 0);
            boolean stable = intent.getBooleanExtra("stable", false);
            updateWeighing(weight, stable);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new Ui(this);

        LinearLayout screen = ui.column();
        MaterialButton closeBtn = ui.iconButton(R.drawable.ic_back, "返回");
        closeBtn.setOnClickListener(v -> finish());
        screen.addView(ui.toolbar("实时测重", this::finish, null));

        LinearLayout root = ui.column();
        root.setPadding(ui.dp(20), ui.dp(32), ui.dp(20), ui.dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        screen.addView(root, new LinearLayout.LayoutParams(-1, -1));

        LinearLayout card = ui.card(root, 28);
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        statusText = ui.label("正在称重，请保持站立...", 16, ui.muted);
        statusText.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(statusText);
        ui.space(card, 24);

        LinearLayout valueRow = ui.row();
        valueRow.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        valueRow.setBaselineAligned(true);

        double initialWeight = getIntent().getDoubleExtra("initial_weight", 0);
        boolean initialStable = getIntent().getBooleanExtra("initial_stable", false);

        weightDisplay = ui.label(initialWeight > 0 ? String.format(Locale.getDefault(), "%.2f", initialWeight) : "0.00", 64, ui.text);
        weightDisplay.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        weightDisplay.setLetterSpacing(-.035f);
        valueRow.addView(weightDisplay);

        TextView kg = ui.label("kg", 20, ui.muted);
        kg.setPadding(ui.dp(8), 0, 0, ui.dp(10));
        valueRow.addView(kg);
        card.addView(valueRow);
        ui.space(card, 24);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        card.addView(progress, new LinearLayout.LayoutParams(ui.dp(180), ui.dp(6)));
        ui.space(card, 16);

        subHint = ui.label("数字波动中，稳定后将自动锁定记录", 13, ui.muted);
        subHint.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(subHint);

        ui.space(root, 32);

        finishBtn = ui.textButton("完成");
        finishBtn.setVisibility(View.GONE);
        finishBtn.setOnClickListener(v -> finish());
        root.addView(finishBtn, new LinearLayout.LayoutParams(-1, ui.dp(48)));

        ui.install(this, screen);

        if (initialWeight > 0) {
            updateWeighing(initialWeight, initialStable);
        }
    }

    private void updateWeighing(double weight, boolean stable) {
        weightDisplay.setText(String.format(Locale.getDefault(), "%.2f", weight));
        if (stable) {
            isStable = true;
            statusText.setText("体重已稳定锁定");
            statusText.setTextColor(ui.primary);
            weightDisplay.setTextColor(ui.primary);
            progress.setVisibility(View.GONE);
            subHint.setText("已记录该体重，可在首页及历史中查看");
            finishBtn.setVisibility(View.VISIBLE);
            handler.removeCallbacks(autoFinish);
            handler.postDelayed(autoFinish, 2500);
        } else {
            if (!isStable) {
                statusText.setText("正在称重，数值波动中...");
                statusText.setTextColor(ui.muted);
                progress.setVisibility(View.VISIBLE);
                subHint.setText("请保持平稳站立，正在测定最终数值");
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        isRunning = true;
        ContextCompat.registerReceiver(this, liveReceiver,
                new IntentFilter(getPackageName() + ".LIVE_WEIGHT"),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onStop() {
        isRunning = false;
        unregisterReceiver(liveReceiver);
        handler.removeCallbacksAndMessages(null);
        super.onStop();
    }
}
