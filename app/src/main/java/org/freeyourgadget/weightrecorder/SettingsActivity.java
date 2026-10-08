/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {
    private Ui ui;
    private final int[] times = new int[2];
    private MaterialButton start, end;
    private TextView afternoon, timeError;
    private MaterialSwitch morningWebhook, afternoonWebhook, enabled;
    private TextInputLayout endpointField;
    private TextInputEditText endpoint;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); ui = new Ui(this);
        WeighingPeriods periods = Settings.periods(this);
        times[0] = state == null ? periods.morningStart : state.getInt("draft_start");
        times[1] = state == null ? periods.morningEnd : state.getInt("draft_end");
        LinearLayout screen = ui.column();
        MaterialButton save = ui.textButton("保存"); save.setOnClickListener(v -> save());
        screen.addView(ui.toolbar("设置", this::finish, save));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout content = ui.column(); content.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(24));
        scroll.addView(content); screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        content.addView(ui.label("称重时段", 13, ui.muted)); ui.space(content, 8);
        LinearLayout timeCard = ui.card(content, 16);
        timeCard.addView(ui.title("上午", 16)); ui.space(timeCard, 8);
        LinearLayout timeFields = ui.row();
        LinearLayout startField = ui.column(), endField = ui.column();
        startField.addView(ui.label("开始时间", 12, ui.muted)); endField.addView(ui.label("结束时间", 12, ui.muted));
        start = timeButton(); end = timeButton();
        startField.addView(start, new LinearLayout.LayoutParams(-1, ui.dp(52)));
        endField.addView(end, new LinearLayout.LayoutParams(-1, ui.dp(52)));
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1); left.setMarginEnd(ui.dp(12));
        timeFields.addView(startField, left); timeFields.addView(endField, new LinearLayout.LayoutParams(0, -2, 1)); timeCard.addView(timeFields);
        ui.space(timeCard, 12); ui.divider(timeCard); ui.space(timeCard, 12);
        timeCard.addView(ui.title("下午", 16)); afternoon = ui.label("", 14, ui.afternoon); timeCard.addView(afternoon);
        timeCard.addView(ui.label("自动使用上午以外的时间", 12, ui.muted));
        timeError = ui.label("开始和结束时间必须不同", 13, ui.color(R.color.wr_error)); timeCard.addView(timeError);
        start.setOnClickListener(v -> chooseTime(0)); end.setOnClickListener(v -> chooseTime(1)); updateTimes();
        ui.space(content, 8); content.addView(ui.label("使用手机本地时间，修改后已有记录会重新归类。", 12, ui.muted)); ui.space(content, 20);

        content.addView(ui.label("Webhook", 13, ui.muted)); ui.space(content, 8);
        LinearLayout webhook = ui.card(content, 16);
        enabled = toggle(webhook, "启用 Webhook", "webhook_enabled", false, state);
        ui.space(webhook, 8);
        endpointField = new TextInputLayout(this); endpointField.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        endpointField.setBoxCornerRadii(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12)); endpointField.setHint("Webhook 地址");
        endpoint = new TextInputEditText(endpointField.getContext()); endpoint.setSingleLine(true); endpoint.setTextSize(14);
        endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpoint.setText(state == null ? Settings.prefs(this).getString("endpoint", "") : state.getString("draft_endpoint"));
        endpointField.addView(endpoint); webhook.addView(endpointField); ui.space(webhook, 8);
        webhook.addView(ui.label("自动发送称重时间与体重，成功后显示返回消息。", 12, ui.muted));
        ui.space(webhook, 12); ui.divider(webhook); ui.space(webhook, 4);
        morningWebhook = toggle(webhook, "上午记录调用 Webhook", "morning_webhook", true, state);
        afternoonWebhook = toggle(webhook, "下午记录调用 Webhook", "afternoon_webhook", true, state);
        webhook.addView(ui.label("总开关和对应时段开关均开启时才会发送。", 12, ui.muted)); ui.space(content, 16);

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            LinearLayout notification = ui.card(content, 16);
            notification.addView(ui.title("结果通知", 16)); notification.addView(ui.label("允许通知后，可以在通知栏查看记录结果。", 13, ui.muted));
            MaterialButton allow = ui.textButton("允许结果通知");
            allow.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 20)); notification.addView(allow);
            ui.space(content, 16);
        }
        content.addView(ui.label("体重记录 " + BuildConfig.VERSION_NAME + "\n基于 Gadgetbridge · AGPLv3", 12, ui.muted));
        ui.install(this, screen);
    }
    private MaterialButton timeButton() {
        MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setAllCaps(false); b.setTextSize(20); b.setCornerRadius(ui.dp(12)); b.setInsetTop(0); b.setInsetBottom(0);
        b.setMinWidth(ui.dp(48)); b.setMinimumWidth(ui.dp(48)); return b;
    }
    private MaterialSwitch toggle(LinearLayout parent, String label, String key, boolean fallback, Bundle state) {
        MaterialSwitch v = new MaterialSwitch(this); v.setText(label); v.setTextSize(14); v.setMinHeight(ui.dp(48));
        v.setChecked(state == null ? Settings.prefs(this).getBoolean(key, fallback) : state.getBoolean("draft_" + key));
        parent.addView(v, new LinearLayout.LayoutParams(-1, -2)); return v;
    }
    private void chooseTime(int index) {
        new TimePickerDialog(this, (picker, hour, minute) -> { times[index] = hour * 60 + minute; updateTimes(); },
                times[index] / 60, times[index] % 60, true).show();
    }
    private void updateTimes() {
        start.setText(WeighingPeriods.time(times[0])); end.setText(WeighingPeriods.time(times[1]));
        start.setContentDescription("上午开始时间 " + WeighingPeriods.time(times[0]));
        end.setContentDescription("上午结束时间 " + WeighingPeriods.time(times[1]));
        boolean valid = times[0] != times[1]; timeError.setVisibility(valid ? View.GONE : View.VISIBLE);
        afternoon.setText(valid ? new WeighingPeriods(times[0], times[1]).afternoonRange() : "请先设置有效的上午时段");
    }
    private void save() {
        String url = endpoint.getText().toString().trim(); updateTimes();
        if (times[0] == times[1]) return;
        if ((!url.isEmpty() || enabled.isChecked()) && !WebhookContract.validUrl(url)) {
            endpointField.setError("请输入完整的 HTTP 或 HTTPS 地址"); endpoint.requestFocus(); return;
        }
        endpointField.setError(null);
        Settings.prefs(this).edit().putString("endpoint", url).putBoolean("webhook_enabled", enabled.isChecked())
                .putInt("morning_start", times[0]).putInt("morning_end", times[1])
                .putBoolean("morning_webhook", morningWebhook.isChecked()).putBoolean("afternoon_webhook", afternoonWebhook.isChecked()).apply();
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show(); finish();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putInt("draft_start", times[0]); state.putInt("draft_end", times[1]);
        state.putString("draft_endpoint", endpoint.getText().toString());
        state.putBoolean("draft_webhook_enabled", enabled.isChecked());
        state.putBoolean("draft_morning_webhook", morningWebhook.isChecked());
        state.putBoolean("draft_afternoon_webhook", afternoonWebhook.isChecked());
    }
}
