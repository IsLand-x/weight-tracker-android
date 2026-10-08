/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import android.Manifest;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private TextView deviceInfo, latest, latestDate, delivery;
    private LinearLayout history;
    private BottomSheetDialog historyDialog;
    private ArrayAdapter<String> historyAdapter;
    private List<WeightDatabase.Record> historyRecords = new ArrayList<>();
    private TextView historyTitle;
    private Button connect;
    private WeightChartView chart;
    private int text, muted, background, primary, surface, container;
    private boolean registered, scanAfterPermission;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothLeScanner scanner;
    private AlertDialog scanDialog;
    private ArrayAdapter<String> scanAdapter;
    private TextView scanStatus;
    private final Map<String, Candidate> candidates = new LinkedHashMap<>();
    private boolean scanning;
    private final Runnable discoveryTimeout = () -> {
        stopScan();
        if (scanDialog != null && scanDialog.isShowing()) scanStatus.setText(candidates.isEmpty()
                ? "未发现支持的秤，请唤醒后重新扫描" : "点击上方体重秤连接");
    };
    private final BroadcastReceiver changed = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) { refresh(); }
    };
    private static final class Candidate { String address, name; ScaleProtocol.Kind kind; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        text = color(R.color.wr_text); muted = color(R.color.wr_muted); background = color(R.color.wr_background);
        primary = color(R.color.wr_primary); surface = color(R.color.wr_surface); container = color(R.color.wr_primary_container);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(background);
        LinearLayout root = column(); root.setPadding(dp(20), dp(12), dp(20), dp(24)); scroll.addView(root);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            scroll.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!dark);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(!dark);
        LinearLayout heading = row(); TextView title = label("体重记录", 26, text); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
        MaterialButton settings = textButton(""); settings.setIconResource(R.drawable.ic_settings); settings.setIconSize(dp(24));
        settings.setIconTint(ColorStateList.valueOf(text)); settings.setIconPadding(0); settings.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        settings.setPadding(0, 0, 0, 0); settings.setContentDescription("设置"); settings.setOnClickListener(v -> showSettings());
        heading.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48))); root.addView(heading);
        root.addView(label("记录今天，看到变化", 13, muted)); space(root, 20);

        LinearLayout summary = card(root, container, 24);
        summary.addView(label("最近一次称重", 14, muted));
        LinearLayout value = row(); value.setGravity(Gravity.BOTTOM); value.setBaselineAligned(true);
        latest = label("—", 58, text); latest.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); latest.setLetterSpacing(-.04f);
        value.addView(latest); TextView kg = label("kg", 20, muted); kg.setPadding(dp(10), 0, 0, dp(12)); value.addView(kg); summary.addView(value);
        latestDate = label("尚未记录", 13, muted); summary.addView(latestDate); space(summary, 8);
        delivery = label("连接体重秤后，称重会自动保存", 13, primary); delivery.setTextIsSelectable(true); summary.addView(delivery); space(root, 16);

        LinearLayout trend = card(root, surface, 16); trend.addView(label("体重趋势", 18, text));
        LinearLayout legend = row();
        legend.addView(label("● 上午", 13, primary), new LinearLayout.LayoutParams(0, -2, 1));
        legend.addView(label("◆ 下午", 13, color(R.color.wr_afternoon))); trend.addView(legend);
        chart = new WeightChartView(this, muted, color(R.color.wr_grid), primary, color(R.color.wr_afternoon));
        trend.addView(chart, new LinearLayout.LayoutParams(-1, dp(260)));
        MaterialButtonToggleGroup periods = new MaterialButtonToggleGroup(this); periods.setSingleSelection(true); periods.setSelectionRequired(true);
        int[] values = {7, 30, 365}; String[] titles = {"近 7 天", "近 30 天", "近一年"};
        int initial = Settings.prefs(this).getInt("period", 7);
        for (int i = 0; i < 3; i++) {
            MaterialButton range = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            range.setText(titles[i]); range.setAllCaps(false); range.setTextSize(13); range.setId(2001 + i);
            range.setMinWidth(dp(48)); range.setMinimumWidth(dp(48)); range.setPadding(dp(8), 0, dp(8), 0); range.setInsetTop(0); range.setInsetBottom(0);
            periods.addView(range, new LinearLayout.LayoutParams(0, dp(48), 1));
            if (values[i] == initial) periods.check(range.getId());
        }
        periods.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return; int period = values[id - 2001];
            Settings.prefs(this).edit().putInt("period", period).apply(); chart.setPeriod(period);
        });
        chart.setPeriod(initial); trend.addView(periods); space(trend, 8);
        MaterialSwitch browse = new MaterialSwitch(this); browse.setText("浏览更早的记录"); browse.setMinHeight(dp(48));
        browse.setOnCheckedChangeListener((button, checked) -> chart.setBrowseHistory(checked)); trend.addView(browse);
        trend.addView(label("点击或滑动查看数据 · 开启历史浏览后左右拖动切换日期", 11, muted)); space(root, 16);
        LinearLayout device = card(root, surface, 20); LinearLayout deviceHeading = row();
        deviceHeading.addView(label("我的体重秤", 16, text), new LinearLayout.LayoutParams(0, dp(48), 1));
        MaterialButton choose = textButton("选择 / 更换"); choose.setOnClickListener(v -> beginScanWithPermissions()); deviceHeading.addView(choose); device.addView(deviceHeading);
        deviceInfo = label("未连接体重秤", 13, muted); device.addView(deviceInfo); space(device, 12);
        connect = button("连接体重秤"); connect.setOnClickListener(v -> connectionAction()); device.addView(connect, new LinearLayout.LayoutParams(-1, dp(48))); space(root, 16);
        LinearLayout recent = card(root, surface, 20); LinearLayout recentHeading = row();
        recentHeading.addView(label("最近记录", 18, text), new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton more = textButton("更多"); more.setOnClickListener(v -> showAllRecords()); recentHeading.addView(more);
        recent.addView(recentHeading); history = column(); recent.addView(history);
        setContentView(scroll); refresh();
    }
    private int color(int resource) { return ContextCompat.getColor(this, resource); }
    private LinearLayout card(LinearLayout parent, int color, int padding) {
        MaterialCardView card = new MaterialCardView(this); card.setRadius(dp(24)); card.setCardElevation(0);
        card.setStrokeWidth(0); card.setCardBackgroundColor(color);
        LinearLayout content = column(); content.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        card.addView(content); parent.addView(card, new LinearLayout.LayoutParams(-1, -2)); return content;
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    private TextView label(String value, float size, int color) {
        TextView label = new TextView(this); label.setText(value); label.setTextSize(size); label.setTextColor(color); label.setPadding(0, dp(4), 0, dp(4)); return label;
    }
    private MaterialButton button(String title) {
        MaterialButton b = new MaterialButton(this); b.setText(title); b.setAllCaps(false); b.setMinHeight(dp(48));
        b.setInsetTop(0); b.setInsetBottom(0); b.setCornerRadius(dp(24)); b.setElevation(0); return b;
    }
    private MaterialButton textButton(String title) {
        MaterialButton b = new MaterialButton(this, null, R.attr.wrTextButtonStyle);
        b.setText(title); b.setAllCaps(false); b.setMinHeight(dp(48)); b.setMinimumWidth(dp(48)); b.setMinWidth(dp(48));
        b.setInsetTop(0); b.setInsetBottom(0); b.setCornerRadius(dp(24)); return b;
    }
    private void space(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private String formatDate(long millis) { return new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(new Date(millis)); }
    private String recordState(String state) {
        switch (state) {
            case "pending": return "等待发送";
            case "sending": return "正在发送";
            case "success": return "已记录";
            case "failed": return "发送未确认";
            default: return "仅本机";
        }
    }
    private void refresh() {
        List<WeightDatabase.Record> records = WeightDatabase.get(this).all(); chart.setRecords(records);
        refreshAllRecords(records);
        boolean tracking = Settings.prefs(this).getBoolean("tracking", false);
        String name = Settings.prefs(this).getString("name", "未选择体重秤");
        String type = Settings.prefs(this).getString("type_label", ""); String model = Settings.prefs(this).getString("model", "");
        deviceInfo.setText(name + (type.isEmpty() ? "" : " · " + type) + (model.isEmpty() ? "" : "\n型号：" + model)
                + "\n" + Settings.prefs(this).getString("connection", "未连接"));
        connect.setText(tracking ? "断开" : "连接体重秤"); history.removeAllViews();
        if (records.isEmpty()) { history.addView(label("称重后，记录会自动显示在这里", 14, muted)); return; }
        WeightDatabase.Record last = records.get(records.size() - 1); latest.setText(String.format(Locale.getDefault(), "%.2f", last.weight)); latestDate.setText(formatDate(last.timeMillis));
        delivery.setText(recordState(last.state) + (last.message.isEmpty() ? "" : " · " + last.message));
        delivery.setTextColor(last.state.equals("failed") ? color(R.color.wr_error) : last.state.equals("success") ? primary : muted);
        delivery.setOnClickListener(v -> showRecord(last));
        for (int i = records.size() - 1; i >= Math.max(0, records.size() - 3); i--) {
            WeightDatabase.Record r = records.get(i); LinearLayout item = row(); item.setPadding(0, dp(8), 0, dp(8));
            item.setMinimumHeight(dp(64));
            item.addView(label(formatDate(r.timeMillis) + " · " + Settings.periods(this).label(r.timeMillis) + "\n" + recordState(r.state), 13, muted), new LinearLayout.LayoutParams(0, -2, 1));
            item.addView(label(String.format(Locale.getDefault(), "%.2f kg", r.weight), 19, text)); item.setOnClickListener(v -> showRecord(r));
            item.setFocusable(true); item.setContentDescription(formatDate(r.timeMillis) + "，" + r.weight + "公斤，" + recordState(r.state)); history.addView(item);
        }
    }
    private void showAllRecords() {
        if (historyDialog != null && historyDialog.isShowing()) return;
        historyDialog = new BottomSheetDialog(this, R.style.ThemeOverlay_WeightRecorder_BottomSheetDialog);
        historyDialog.setDismissWithAnimation(false);
        LinearLayout content = column(); content.setPadding(dp(20), dp(8), dp(20), 0);
        LinearLayout heading = row(); historyTitle = label("全部记录", 20, text);
        heading.addView(historyTitle, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton close = textButton("关闭"); close.setOnClickListener(v -> historyDialog.dismiss()); heading.addView(close); content.addView(heading);
        ListView list = new ListView(this);
        historyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(historyAdapter); TextView empty = label("暂无体重记录", 14, muted); content.addView(empty); list.setEmptyView(empty);
        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener((parent, view, position, id) -> showRecord(historyRecords.get(position)));
        historyDialog.setContentView(content);
        historyDialog.setOnDismissListener(d -> { historyAdapter = null; historyTitle = null; historyRecords = new ArrayList<>(); });
        historyDialog.show();
        int availableHeight = findViewById(android.R.id.content).getHeight();
        int sheetHeight = Math.round(availableHeight * .7f);
        View sheet = historyDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.getLayoutParams().height = sheetHeight; sheet.requestLayout();
            historyDialog.getBehavior().setPeekHeight(sheetHeight);
            historyDialog.getBehavior().setSkipCollapsed(true);
            historyDialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        }
        refreshAllRecords(WeightDatabase.get(this).all());
    }
    private void refreshAllRecords(List<WeightDatabase.Record> records) {
        if (historyAdapter == null) return;
        historyRecords = new ArrayList<>(records); java.util.Collections.reverse(historyRecords);
        historyTitle.setText("全部记录（" + records.size() + "）"); historyAdapter.clear();
        for (WeightDatabase.Record r : historyRecords) historyAdapter.add(String.format(Locale.getDefault(), "%.2f kg · %s\n%s · %s",
                r.weight, Settings.periods(this).label(r.timeMillis), formatDate(r.timeMillis), recordState(r.state)));
        historyAdapter.notifyDataSetChanged();
    }
    private void showRecord(WeightDatabase.Record r) {
        String message = formatDate(r.timeMillis) + " · " + Settings.periods(this).label(r.timeMillis) + "\n" + recordState(r.state) + "\n" + r.message;
        if (r.state.equals("failed")) message += "\n\n若请求超时，服务端可能已收到。请先确认当日记录，再重试。";
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this).setTitle(String.format(Locale.getDefault(), "%.2f kg", r.weight)).setMessage(message).setNegativeButton("关闭", null);
        if ((r.state.equals("failed") || r.state.equals("local")) && !Settings.endpoint(this, r.timeMillis).isEmpty()) {
            builder.setPositiveButton(r.state.equals("failed") ? "重试发送" : "发送这条记录", (d, w) -> {
                if (WeightDatabase.get(this).prepareRetry(r.id, Settings.endpoint(this, r.timeMillis))) { WebhookWorker.enqueue(this, r.id); refresh(); }
            });
        }
        builder.show();
    }
    private void showSettings() {
        LinearLayout content = column(); content.setPadding(dp(24), dp(8), dp(24), dp(8));
        WeighingPeriods saved = Settings.periods(this); int[] times = {saved.morningStart, saved.morningEnd};
        content.addView(label("上午称重时段", 16, text));
        LinearLayout timeButtons = row(); MaterialButton start = textButton("开始 " + WeighingPeriods.time(times[0]));
        MaterialButton end = textButton("结束 " + WeighingPeriods.time(times[1]));
        timeButtons.addView(start, new LinearLayout.LayoutParams(0, dp(48), 1)); timeButtons.addView(end, new LinearLayout.LayoutParams(0, dp(48), 1)); content.addView(timeButtons);
        content.addView(label("下午称重时段", 16, text)); TextView afternoon = label(saved.afternoonRange() + " · 自动使用剩余时间", 13, muted); content.addView(afternoon);
        TextView timeError = label("", 12, color(R.color.wr_error)); timeError.setVisibility(View.GONE); content.addView(timeError);
        start.setOnClickListener(v -> choosePeriodTime(times, 0, start, end, afternoon, timeError));
        end.setOnClickListener(v -> choosePeriodTime(times, 1, start, end, afternoon, timeError));
        content.addView(label("开始时间包含在上午，结束时间归入下午；使用手机本地时间。修改时段会重新归类已有记录。", 12, muted));
        MaterialSwitch morningWebhook = periodWebhookSwitch(content, "上午记录调用 Webhook", "morning_webhook");
        MaterialSwitch afternoonWebhook = periodWebhookSwitch(content, "下午记录调用 Webhook", "afternoon_webhook"); space(content, 12);
        MaterialSwitch enabled = new MaterialSwitch(this); enabled.setText("启用 Webhook"); enabled.setMinHeight(dp(48)); enabled.setChecked(Settings.prefs(this).getBoolean("webhook_enabled", false)); content.addView(enabled); space(content, 16);
        TextInputLayout field = new TextInputLayout(this); field.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE); field.setHint("Webhook 地址");
        EditText endpoint = new TextInputEditText(field.getContext()); endpoint.setSingleLine(true); endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpoint.setText(Settings.prefs(this).getString("endpoint", "")); field.addView(endpoint); content.addView(field); space(content, 12);
        content.addView(label("称重后发送时间戳和公斤数；成功后展示接口返回的消息。", 13, muted));
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Button notification = button("允许结果通知"); notification.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 20)); content.addView(notification);
            content.addView(label("未允许通知时，结果仍会显示在首页。", 12, muted));
        }
        content.addView(label("基于 Gadgetbridge · AGPLv3\n仅支持小米/华米及标准蓝牙体重秤", 12, muted));
        ScrollView settingsScroll = new ScrollView(this); settingsScroll.addView(content);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("设置").setView(settingsScroll).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String url = endpoint.getText().toString().trim();
            if (times[0] == times[1]) { timeError.setText("开始和结束时间必须不同"); timeError.setVisibility(View.VISIBLE); return; }
            if ((!url.isEmpty() || enabled.isChecked()) && !WebhookContract.validUrl(url)) { field.setError("请输入完整的 HTTP 或 HTTPS 地址"); return; }
            Settings.prefs(this).edit().putString("endpoint", url).putBoolean("webhook_enabled", enabled.isChecked())
                    .putInt("morning_start", times[0]).putInt("morning_end", times[1])
                    .putBoolean("morning_webhook", morningWebhook.isChecked()).putBoolean("afternoon_webhook", afternoonWebhook.isChecked()).apply();
            dialog.dismiss(); refresh();
            if (enabled.isChecked() && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 20);
        })); dialog.show();
    }
    private MaterialSwitch periodWebhookSwitch(LinearLayout content, String title, String key) {
        MaterialSwitch toggle = new MaterialSwitch(this); toggle.setText(title); toggle.setMinHeight(dp(48));
        toggle.setChecked(Settings.prefs(this).getBoolean(key, true)); content.addView(toggle); return toggle;
    }
    private void choosePeriodTime(int[] times, int index, MaterialButton start, MaterialButton end, TextView afternoon, TextView error) {
        new TimePickerDialog(this, (picker, hour, minute) -> {
            times[index] = hour * 60 + minute;
            start.setText("开始 " + WeighingPeriods.time(times[0])); end.setText("结束 " + WeighingPeriods.time(times[1]));
            error.setVisibility(times[0] == times[1] ? View.VISIBLE : View.GONE); error.setText("开始和结束时间必须不同");
            afternoon.setText(times[0] == times[1] ? "请先设置有效的上午时段" : new WeighingPeriods(times[0], times[1]).afternoonRange() + " · 自动使用剩余时间");
        }, times[index] / 60, times[index] % 60, true).show();
    }
    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private void permissions(boolean scan) {
        scanAfterPermission = scan;
        requestPermissions(Build.VERSION.SDK_INT >= 31 ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}
                : new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 10);
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 10) { if (hasBluetoothPermissions()) { if (scanAfterPermission) beginScanWithPermissions(); else startTracking(); } else Toast.makeText(this, "连接体重秤需要蓝牙权限", Toast.LENGTH_LONG).show(); }
    }
    private boolean bluetoothReady() {
        BluetoothAdapter adapter = getSystemService(BluetoothManager.class).getAdapter();
        if (adapter == null) { Toast.makeText(this, "此设备不支持蓝牙", Toast.LENGTH_LONG).show(); return false; }
        try {
            if (!adapter.isEnabled()) { startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); Toast.makeText(this, "开启蓝牙后，再点击连接", Toast.LENGTH_SHORT).show(); return false; }
        } catch (SecurityException e) { Toast.makeText(this, "请允许蓝牙权限后再连接", Toast.LENGTH_LONG).show(); return false; }
        return true;
    }
    private void connectionAction() {
        if (Settings.prefs(this).getBoolean("tracking", false)) { stopService(new Intent(this, ScaleService.class)); Settings.prefs(this).edit().putBoolean("tracking", false).apply(); refresh(); }
        else if (Settings.prefs(this).getString("address", "").isEmpty()) beginScanWithPermissions(); else startTracking();
    }
    private void startTracking() {
        if (!hasBluetoothPermissions()) { permissions(false); return; }
        if (!bluetoothReady()) return;
        Settings.prefs(this).edit().putBoolean("tracking", true).apply();
        ContextCompat.startForegroundService(this, new Intent(this, ScaleService.class));
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 20);
        refresh();
    }
    private void beginScanWithPermissions() {
        if (!hasBluetoothPermissions()) { permissions(true); return; }
        if (!bluetoothReady()) return;
        stopScan(); candidates.clear();
        LinearLayout content = column(); content.setPadding(dp(20), dp(6), dp(20), dp(6)); scanStatus = label("请踩一下体重秤，保持屏幕亮起", 14, muted); content.addView(scanStatus);
        ListView devices = new ListView(this); scanAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>()); devices.setAdapter(scanAdapter); content.addView(devices, new LinearLayout.LayoutParams(-1, dp(240)));
        scanDialog = new MaterialAlertDialogBuilder(this).setTitle("选择体重秤").setView(content).setNegativeButton("关闭", null).create();
        scanDialog.setOnDismissListener(d -> stopScan());
        devices.setOnItemClickListener((parent, view, position, id) -> {
            Candidate candidate = new ArrayList<>(candidates.values()).get(position);
            Settings.prefs(this).edit().putString("address", candidate.address).putString("name", candidate.name).putString("kind", candidate.kind.name())
                    .putString("type_label", candidate.kind.label).putString("model", "").putBoolean("tracking", false).apply();
            scanDialog.dismiss(); startTracking();
        }); scanDialog.show();
        scanner = getSystemService(BluetoothManager.class).getAdapter().getBluetoothLeScanner();
        if (scanner == null) { scanStatus.setText("无法扫描，请开启蓝牙"); return; }
        try { scanning = true; scanner.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), discovery); }
        catch (SecurityException | IllegalStateException e) { scanning = false; scanStatus.setText("无法扫描，请检查权限和蓝牙"); }
        handler.postDelayed(discoveryTimeout, 20000);
    }
    private final ScanCallback discovery = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            handler.post(() -> {
                if (!scanning || scanAdapter == null) return;
                ScanRecord record = result.getScanRecord(); if (record == null) return;
                List<UUID> services = new ArrayList<>(); if (record.getServiceUuids() != null) for (ParcelUuid uuid : record.getServiceUuids()) services.add(uuid.getUuid());
                String name = record.getDeviceName(); if (name == null) { try { name = result.getDevice().getName(); } catch (SecurityException ignored) {} }
                ScaleProtocol.Kind kind = ScaleProtocol.identify(name, services, record.getManufacturerSpecificData(0x0157) != null); if (kind == null) return;
                Candidate candidate = new Candidate(); candidate.address = result.getDevice().getAddress(); candidate.name = name == null ? "未命名体重秤" : name; candidate.kind = kind;
                candidates.put(candidate.address, candidate); scanAdapter.clear();
                for (Candidate c : candidates.values()) scanAdapter.add(c.name + " · " + c.kind.label + "\n" + c.address); scanAdapter.notifyDataSetChanged();
            });
        }
        @Override public void onScanFailed(int code) { handler.post(() -> { scanning = false; if (scanStatus != null) scanStatus.setText("扫描失败（" + code + "），请稍后再试"); }); }
    };
    private void stopScan() {
        handler.removeCallbacks(discoveryTimeout);
        if (scanning && scanner != null) { try { scanner.stopScan(discovery); } catch (SecurityException ignored) {} }
        scanning = false;
    }
    @Override protected void onStart() {
        super.onStart(); ContextCompat.registerReceiver(this, changed, new IntentFilter(getPackageName() + ".CHANGED"), ContextCompat.RECEIVER_NOT_EXPORTED); registered = true; refresh();
    }
    @Override protected void onStop() {
        stopScan(); if (registered) { unregisterReceiver(changed); registered = false; } super.onStop();
    }
    @Override protected void onDestroy() {
        if (historyDialog != null) historyDialog.dismiss();
        stopScan(); handler.removeCallbacksAndMessages(null); super.onDestroy();
    }
}
