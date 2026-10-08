/* SPDX-License-Identifier: AGPL-3.0-or-later
 * Xiaomi UUIDs, time sync and history handshake adapted from Gadgetbridge MiSmartScaleDeviceSupport
 * (Copyright (C) 2024 Severin von Wnuck-Lipinski) under AGPL-3.0-or-later.
 */
package org.freeyourgadget.weightrecorder;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ScaleService extends Service {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private BluetoothLeScanner scanner;
    private boolean stopped, scanning, busy;
    private ScaleProtocol.Kind kind;
    private String address;
    private final ArrayDeque<Operation> operations = new ArrayDeque<>();
    private interface Operation { boolean start(); }
    private final Runnable operationTimeout = () -> {
        if (busy) disconnect("体重秤响应超时，等待再次唤醒");
    };
    private final Runnable scanTimeout = () -> {
        stopScan(); status("未发现体重秤，请踩一下唤醒");
        if (!stopped) handler.postDelayed(this::scan, 3000);
    };
    @Override public void onCreate() { super.onCreate(); Notifications.channels(this); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "stop".equals(intent.getAction())) {
            Settings.prefs(this).edit().putBoolean("tracking", false).apply(); stopSelf(); return START_NOT_STICKY;
        }
        String selectedAddress = Settings.prefs(this).getString("address", "");
        if (address != null && !address.equals(selectedAddress)) {
            handler.removeCallbacksAndMessages(null); stopScan();
            busy = false; operations.clear();
            if (gatt != null) { try { gatt.close(); } catch (SecurityException ignored) {} gatt = null; }
        }
        address = selectedAddress;
        if (address.isEmpty() || !Settings.prefs(this).getBoolean("tracking", false)) { stopSelf(); return START_NOT_STICKY; }
        try { kind = ScaleProtocol.Kind.valueOf(Settings.prefs(this).getString("kind", "MI_BODY")); }
        catch (IllegalArgumentException e) { stopSelf(); return START_NOT_STICKY; }
        try { startForeground(1, Notifications.connection(this, "等待体重秤唤醒")); }
        catch (SecurityException | IllegalStateException e) { stopSelf(); return START_NOT_STICKY; }
        if (gatt == null && !scanning) scan();
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    private void status(String text) {
        Settings.prefs(this).edit().putString("connection", text).apply();
        if (!stopped) getSystemService(android.app.NotificationManager.class).notify(1, Notifications.connection(this, text));
        WebhookWorker.changed(this);
    }
    private void scan() {
        if (stopped || scanning || gatt != null) return;
        try {
            BluetoothAdapter adapter = getSystemService(BluetoothManager.class).getAdapter();
            if (adapter == null || !adapter.isEnabled()) { status("请开启蓝牙"); handler.postDelayed(this::scan, 10000); return; }
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) { handler.postDelayed(this::scan, 10000); return; }
            scanning = true; status("等待体重秤唤醒");
            scanner.startScan(Collections.singletonList(new ScanFilter.Builder().setDeviceAddress(address).build()),
                    new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
            handler.postDelayed(scanTimeout, 20000);
        } catch (SecurityException | IllegalArgumentException | IllegalStateException e) { status("无法扫描，请检查蓝牙权限"); stopSelf(); }
    }
    private void stopScan() {
        handler.removeCallbacks(scanTimeout);
        if (scanning && scanner != null) { try { scanner.stopScan(scanCallback); } catch (SecurityException ignored) {} }
        scanning = false;
    }
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            handler.post(() -> {
                if (stopped || gatt != null || !scanning) return;
                stopScan(); status("正在连接体重秤");
                try { gatt = result.getDevice().connectGatt(ScaleService.this, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE); }
                catch (SecurityException e) { status("连接权限不足"); stopSelf(); }
                handler.postDelayed(operationTimeout, 15000); busy = true;
            });
        }
        @Override public void onScanFailed(int errorCode) { handler.post(() -> { stopScan(); status("扫描失败，请重新连接（" + errorCode + "）"); stopSelf(); }); }
    };
    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt connection, int result, int state) {
            handler.post(() -> {
                if (connection != gatt || stopped) return;
                if (result == BluetoothGatt.GATT_SUCCESS && state == BluetoothProfile.STATE_CONNECTED) {
                    try { if (!gatt.discoverServices()) disconnect("发现服务失败"); }
                    catch (SecurityException e) { disconnect("蓝牙权限不足"); }
                } else if (state == BluetoothProfile.STATE_DISCONNECTED || result != BluetoothGatt.GATT_SUCCESS) {
                    disconnect("体重秤休眠，等待再次唤醒");
                }
            });
        }
        @Override public void onServicesDiscovered(BluetoothGatt connection, int result) {
            handler.post(() -> {
                if (connection != gatt || stopped) return;
                handler.removeCallbacks(operationTimeout); busy = false;
                if (result != BluetoothGatt.GATT_SUCCESS) { disconnect("发现服务失败"); return; }
                BluetoothGattCharacteristic measure = characteristic(kind == ScaleProtocol.Kind.MI_BODY ? ScaleProtocol.BODY : ScaleProtocol.WEIGHT);
                if (measure == null) { status("该设备没有可读取的体重服务"); stopSelf(); return; }
                busy = true;
                if (kind == ScaleProtocol.Kind.MI_WEIGHT) write(ScaleProtocol.CURRENT_TIME, ScaleProtocol.currentTime());
                subscribe(measure);
                if (kind == ScaleProtocol.Kind.MI_WEIGHT && characteristic(ScaleProtocol.HISTORY) != null) {
                    subscribe(characteristic(ScaleProtocol.HISTORY));
                    write(ScaleProtocol.HISTORY, new byte[]{1, 0, 0, 0, 1});
                    write(ScaleProtocol.HISTORY, new byte[]{2});
                }
                BluetoothGattCharacteristic model = characteristic(UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb"));
                if (model != null && (model.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0) enqueue(() -> {
                    try { return gatt.readCharacteristic(model); }
                    catch (SecurityException e) { return false; }
                });
                status("已连接，等待稳定体重"); busy = false; next();
            });
        }
        @Override public void onDescriptorWrite(BluetoothGatt connection, BluetoothGattDescriptor descriptor, int status) { completed(connection, status); }
        @Override public void onCharacteristicWrite(BluetoothGatt connection, BluetoothGattCharacteristic characteristic, int status) { completed(connection, status); }
        @Override public void onCharacteristicRead(BluetoothGatt connection, BluetoothGattCharacteristic characteristic, int status) {
            handleRead(connection, characteristic, characteristic.getValue(), status);
        }
        @Override public void onCharacteristicRead(BluetoothGatt connection, BluetoothGattCharacteristic characteristic, byte[] value, int status) {
            handleRead(connection, characteristic, value, status);
        }
        @Override public void onCharacteristicChanged(BluetoothGatt connection, BluetoothGattCharacteristic characteristic) { received(connection, characteristic.getUuid(), characteristic.getValue()); }
        @Override public void onCharacteristicChanged(BluetoothGatt connection, BluetoothGattCharacteristic characteristic, byte[] value) { received(connection, characteristic.getUuid(), value); }
    };
    private void handleRead(BluetoothGatt connection, BluetoothGattCharacteristic characteristic, byte[] value, int result) {
        handler.post(() -> {
            if (connection != gatt || stopped) return;
            if (result == BluetoothGatt.GATT_SUCCESS && value != null) {
                String model = new String(value, StandardCharsets.UTF_8).replace("\u0000", "").trim();
                Settings.prefs(this).edit().putString("model", model).apply(); WebhookWorker.changed(this);
            }
            complete();
        });
    }
    private void completed(BluetoothGatt connection, int result) {
        handler.post(() -> {
            if (connection != gatt || stopped) return;
            if (result != BluetoothGatt.GATT_SUCCESS) { disconnect("蓝牙操作失败（" + result + "），等待重连"); return; }
            complete();
        });
    }
    private void received(BluetoothGatt connection, UUID characteristic, byte[] data) {
        byte[] bytes = data == null ? null : data.clone();
        handler.post(() -> {
            if (connection != gatt || stopped || bytes == null) return;
            if (characteristic.equals(ScaleProtocol.HISTORY) && bytes.length == 1 && bytes[0] == 3) {
                write(ScaleProtocol.HISTORY, new byte[]{3}); write(ScaleProtocol.HISTORY, new byte[]{4, 0, 0, 0, 1}); return;
            }
            if (!characteristic.equals(ScaleProtocol.HISTORY) && !characteristic.equals(ScaleProtocol.WEIGHT) && !characteristic.equals(ScaleProtocol.BODY)) return;
            boolean historical = characteristic.equals(ScaleProtocol.HISTORY);
            List<ScaleProtocol.Measurement> samples = ScaleProtocol.decode(kind, bytes, System.currentTimeMillis());
            for (ScaleProtocol.Measurement decoded : samples) {
                ScaleProtocol.Measurement sample = historical ? decoded : ScaleProtocol.liveTime(decoded, System.currentTimeMillis());
                String endpoint = historical ? "" : Settings.endpoint(this, sample.timeMillis);
                long id = WeightDatabase.get(this).save(sample, address, endpoint, historical);
                if (id >= 0 && !endpoint.isEmpty()) WebhookWorker.enqueue(this, id);
            }
            if (!samples.isEmpty()) { status("体重已保存 · " + (historical ? "历史数据" : "本次称重")); WebhookWorker.changed(this); }
        });
    }
    private BluetoothGattCharacteristic characteristic(UUID uuid) {
        if (gatt == null) return null;
        for (BluetoothGattService service : gatt.getServices()) { BluetoothGattCharacteristic c = service.getCharacteristic(uuid); if (c != null) return c; }
        return null;
    }
    private void subscribe(BluetoothGattCharacteristic c) {
        enqueue(() -> {
            try {
                BluetoothGattDescriptor descriptor = c.getDescriptor(ScaleProtocol.CCCD);
                if (descriptor == null || !gatt.setCharacteristicNotification(c, true)) return false;
                byte[] value = (c.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                        ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                if (Build.VERSION.SDK_INT >= 33) return gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS;
                descriptor.setValue(value); return gatt.writeDescriptor(descriptor);
            } catch (SecurityException e) { return false; }
        });
    }
    private void write(UUID uuid, byte[] value) {
        BluetoothGattCharacteristic c = characteristic(uuid); if (c == null) return;
        enqueue(() -> {
            try {
                if (Build.VERSION.SDK_INT >= 33) return gatt.writeCharacteristic(c, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS;
                c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT); c.setValue(value); return gatt.writeCharacteristic(c);
            } catch (SecurityException e) { return false; }
        });
    }
    private void enqueue(Operation operation) { operations.add(operation); next(); }
    private void next() {
        if (busy || stopped || gatt == null || operations.isEmpty()) return;
        busy = true;
        try {
            if (!operations.remove().start()) { disconnect("体重秤操作未完成，请重新唤醒"); return; }
            handler.postDelayed(operationTimeout, 15000);
        } catch (SecurityException e) { disconnect("蓝牙权限不足"); }
    }
    private void complete() { handler.removeCallbacks(operationTimeout); busy = false; next(); }
    private void disconnect(String text) {
        handler.removeCallbacks(operationTimeout); busy = false; operations.clear();
        if (gatt != null) { try { gatt.close(); } catch (SecurityException ignored) {} gatt = null; }
        if (!stopped) { status(text); handler.postDelayed(this::scan, 3000); }
    }
    @Override public void onDestroy() {
        stopped = true; handler.removeCallbacksAndMessages(null); stopScan(); disconnect("");
        Settings.prefs(this).edit().putBoolean("tracking", false).putString("connection", "未连接").apply();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        WebhookWorker.changed(this); super.onDestroy();
    }
}
