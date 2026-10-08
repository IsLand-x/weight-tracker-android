/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import android.Manifest;
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
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private TextView deviceInfo, deviceName, latest, latestDate, latestPeriod, delivery, delta;
    private Ui ui;
    private LinearLayout history;
    private Button connect;
    private WeightChartView chart;
    private int text, muted, primary;
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
        ui = new Ui(this); text = ui.text; muted = ui.muted; primary = ui.primary;
        LinearLayout screen = column();
        MaterialButton settings = ui.iconButton(R.drawable.ic_settings, "设置"); settings.setOnClickListener(v -> showSettings());
        screen.addView(ui.toolbar("体重记录", null, settings));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout root = column(); root.setPadding(dp(16), dp(8), dp(16), dp(16)); scroll.addView(root);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout summary = ui.card(root, 16); LinearLayout summaryHeading = row();
        TextView caption = label("最新体重", 13, muted);
        summaryHeading.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
        delta = label("", 12, muted); summaryHeading.addView(delta); summary.addView(summaryHeading);
        if (getResources().getConfiguration().fontScale > 1.3f) {
            summaryHeading.setOrientation(LinearLayout.VERTICAL); summaryHeading.setGravity(Gravity.START);
            caption.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout value = row(); value.setGravity(Gravity.BOTTOM); value.setBaselineAligned(true);
        latest = label("—", 54, text); latest.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); latest.setLetterSpacing(-.035f);
        latest.setMaxLines(1); latest.setMaxWidth(Math.max(dp(120), getResources().getDisplayMetrics().widthPixels - dp(104)));
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(latest, 28, 54, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        value.addView(latest); TextView kg = label("kg", 16, muted); kg.setPadding(dp(8), 0, 0, dp(8)); value.addView(kg); summary.addView(value);
        LinearLayout date = row(); latestDate = label("尚未记录", 13, muted);
        date.addView(latestDate, new LinearLayout.LayoutParams(0, -2, 1));
        latestPeriod = label("", 12, primary); date.addView(latestPeriod); summary.addView(date);
        delivery = label("连接体重秤后自动保存", 12, muted); delivery.setMaxLines(1);
        delivery.setEllipsize(android.text.TextUtils.TruncateAt.END); summary.addView(delivery); space(root, 12);

        LinearLayout trend = ui.card(root, 12); LinearLayout trendHeading = row();
        trendHeading.addView(ui.title("体重趋势", 16));
        MaterialButtonToggleGroup periods = new MaterialButtonToggleGroup(this); periods.setSingleSelection(true); periods.setSelectionRequired(true);
        int[] values = {7, 30, 365}; String[] titles = {"7 天", "30 天", "一年"};
        int initial = Settings.prefs(this).getInt("period", 7);
        if (initial != 7 && initial != 30 && initial != 365) initial = 7;
        for (int i = 0; i < 3; i++) {
            MaterialButton range = ui.segment(titles[i], 2001 + i, 12);
            periods.addView(range, new LinearLayout.LayoutParams(0, dp(48), 1));
            if (values[i] == initial) periods.check(range.getId());
        }
        LinearLayout.LayoutParams ranges = new LinearLayout.LayoutParams(0, dp(48), 1); ranges.setMarginStart(dp(12));
        if (getResources().getConfiguration().fontScale > 1.3f) {
            trendHeading.setOrientation(LinearLayout.VERTICAL); ranges.width = -1; ranges.weight = 0;
            ranges.setMarginStart(0); ranges.topMargin = dp(8);
        }
        trendHeading.addView(periods, ranges); trend.addView(trendHeading);
        chart = new WeightChartView(this, muted, color(R.color.wr_grid), primary, ui.afternoon);
        periods.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return; int period = values[id - 2001];
            Settings.prefs(this).edit().putInt("period", period).apply(); chart.setPeriod(period);
        }); chart.setPeriod(initial);
        LinearLayout legend = row(), periodLegend = row();
        periodLegend.addView(label("● 上午", 12, primary)); TextView afternoon = label("◆ 下午", 12, ui.afternoon);
        afternoon.setPadding(dp(14), dp(2), 0, dp(2)); periodLegend.addView(afternoon);
        LinearLayout.LayoutParams legendLabels = new LinearLayout.LayoutParams(0, -2, 1);
        legend.addView(periodLegend, legendLabels);
        MaterialButton browse = textButton("浏览历史"); browse.setTextSize(12); browse.setCheckable(true);
        browse.setIconResource(R.drawable.ic_history); browse.setIconSize(dp(16)); browse.setIconPadding(dp(4));
        browse.setOnClickListener(v -> {
            chart.setBrowseHistory(browse.isChecked()); browse.setText(browse.isChecked() ? "查看数据" : "浏览历史");
            browse.setContentDescription(browse.isChecked() ? "已开启历史浏览，点击切换为滑动查看数据" : "点击开启左右拖动浏览历史");
        });
        LinearLayout.LayoutParams historyMode = new LinearLayout.LayoutParams(-2, dp(48));
        if (getResources().getConfiguration().fontScale > 1.3f) {
            legend.setOrientation(LinearLayout.VERTICAL); legendLabels.width = -1; legendLabels.weight = 0;
            historyMode.gravity = Gravity.END;
        }
        legend.addView(browse, historyMode); trend.addView(legend);
        trend.addView(chart, new LinearLayout.LayoutParams(-1, dp(214))); space(root, 12);

        LinearLayout recent = ui.card(root, 12); LinearLayout recentHeading = row();
        recentHeading.addView(ui.title("最近记录", 16), new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton more = textButton("更多"); more.setOnClickListener(v -> showAllRecords()); recentHeading.addView(more);
        recent.addView(recentHeading); history = column(); recent.addView(history); space(root, 12);

        LinearLayout device = ui.card(root, 12); LinearLayout deviceRow = row();
        MaterialButton choose = ui.iconButton(R.drawable.ic_scale_notification, "选择或更换体重秤");
        choose.setOnClickListener(v -> beginScanWithPermissions()); deviceRow.addView(choose, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout deviceLabels = column(); deviceName = ui.title("体重秤", 14); deviceName.setSingleLine(true);
        deviceName.setEllipsize(android.text.TextUtils.TruncateAt.END); deviceLabels.addView(deviceName);
        deviceInfo = label("未连接", 12, muted); deviceInfo.setMaxLines(2); deviceInfo.setEllipsize(android.text.TextUtils.TruncateAt.END); deviceLabels.addView(deviceInfo);
        deviceRow.addView(deviceLabels, new LinearLayout.LayoutParams(0, -2, 1));
        connect = textButton("连接"); connect.setOnClickListener(v -> connectionAction()); deviceRow.addView(connect); device.addView(deviceRow);
        ui.install(this, screen); refresh();
    }
    private int color(int resource) { return ContextCompat.getColor(this, resource); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    private TextView label(String value, float size, int color) { return ui.label(value, size, color); }
    private MaterialButton textButton(String title) { return ui.textButton(title); }
    private void space(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private void refresh() {
        List<WeightDatabase.Record> records = WeightDatabase.get(this).all(); chart.setRecords(records);
        boolean tracking = Settings.prefs(this).getBoolean("tracking", false);
        String name = Settings.prefs(this).getString("name", "未选择体重秤");
        deviceName.setText(name); deviceInfo.setText(Settings.prefs(this).getString("connection", "点击图标选择体重秤"));
        String type = Settings.prefs(this).getString("type_label", ""); String model = Settings.prefs(this).getString("model", "");
        deviceName.setContentDescription(name + (type.isEmpty() ? "" : "，" + type) + (model.isEmpty() ? "" : "，型号：" + model));
        connect.setText(tracking ? "断开" : "连接"); history.removeAllViews();
        if (records.isEmpty()) {
            latest.setText("—"); latestDate.setText("尚未记录"); latestPeriod.setText(""); delta.setText("");
            delivery.setText("连接体重秤后自动保存"); delivery.setOnClickListener(null);
            history.addView(label("称重后，记录会自动显示在这里", 14, muted)); return;
        }
        WeightDatabase.Record last = records.get(records.size() - 1);
        latest.setText(String.format(Locale.getDefault(), "%.2f", last.weight)); latestDate.setText(Ui.date(last.timeMillis, "MM月dd日 HH:mm"));
        latestPeriod.setText(Settings.periods(this).label(last.timeMillis)); latestPeriod.setTextColor(Settings.periods(this).isMorning(last.timeMillis) ? primary : ui.afternoon);
        if (records.size() > 1) {
            double difference = last.weight - records.get(records.size() - 2).weight;
            delta.setText(String.format(Locale.getDefault(), "较上次 %+.2f kg", difference));
        } else delta.setText("首次记录");
        delivery.setText(Ui.recordState(last.state) + (last.message.isEmpty() || "screenshot-restore".equals(last.source) ? "" : " · " + last.message));
        delivery.setTextColor(last.state.equals("failed") ? color(R.color.wr_error) : muted);
        delivery.setOnClickListener(v -> showRecord(last));
        for (int i = records.size() - 1; i >= Math.max(0, records.size() - 3); i--) {
            WeightDatabase.Record r = records.get(i); LinearLayout item = ui.recordRow(r, true);
            item.setOnClickListener(v -> showRecord(r)); history.addView(item);
            if (i > Math.max(0, records.size() - 3)) ui.divider(history);
        }
    }
    private void showAllRecords() { startActivity(new Intent(this, RecordsActivity.class)); }
    private void showRecord(WeightDatabase.Record r) { RecordDetails.show(this, r, this::refresh); }
    private void showSettings() { startActivity(new Intent(this, SettingsActivity.class)); }
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
        stopScan(); handler.removeCallbacksAndMessages(null); super.onDestroy();
    }
}
