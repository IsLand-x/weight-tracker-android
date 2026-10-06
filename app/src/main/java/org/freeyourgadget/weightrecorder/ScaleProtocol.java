/* SPDX-License-Identifier: AGPL-3.0-or-later
 * Xiaomi decoding adapted from Gadgetbridge WeightMeasurement and MiCompositionScaleDeviceSupport.
 * Copyright (C) 2019-2024 Andreas Shimokawa, Arjan Schrijver, Damien Gaignon,
 * Daniel Dakhno, Jean-François Greffier, José Rebelo, Petr Vaněk, Severin von Wnuck-Lipinski.
 */
package org.freeyourgadget.weightrecorder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ScaleProtocol {
    public static final UUID WEIGHT_SERVICE = uuid("181d");
    public static final UUID BODY_SERVICE = uuid("181b");
    public static final UUID WEIGHT = uuid("2a9d");
    public static final UUID BODY = uuid("2a9c");
    public static final UUID CURRENT_TIME = uuid("2a2b");
    public static final UUID HISTORY = UUID.fromString("00002a2f-0000-3512-2118-0009af100700");
    public static final UUID CCCD = uuid("2902");
    public enum Kind {
        MI_WEIGHT("小米体重秤（华米）"), MI_BODY("小米体脂秤（华米）"), STANDARD("标准蓝牙体重秤");
        public final String label;
        Kind(String label) { this.label = label; }
    }
    public static final class Measurement {
        public final long timeMillis;
        public final double weightKg;
        public final boolean deviceTime;
        Measurement(long time, double weight, boolean deviceTime) {
            this.timeMillis = time; this.weightKg = weight; this.deviceTime = deviceTime;
        }
    }
    private ScaleProtocol() {}
    private static UUID uuid(String value) { return UUID.fromString("0000" + value + "-0000-1000-8000-00805f9b34fb"); }
    public static Kind identify(String name, List<UUID> services, boolean huamiManufacturer) {
        String n = name == null ? "" : name.toUpperCase(Locale.ROOT).trim();
        if (n.equals("MIBFS") || n.equals("MIBCS") || (huamiManufacturer && services.contains(BODY_SERVICE))) return Kind.MI_BODY;
        if (n.equals("MI SCALE") || n.equals("MI SCALE2")) return Kind.MI_WEIGHT;
        return services.contains(WEIGHT_SERVICE) ? Kind.STANDARD : null;
    }
    public static List<Measurement> decode(Kind kind, byte[] bytes, long now) {
        if (bytes == null) return Collections.emptyList();
        List<Measurement> result = new ArrayList<>();
        if (kind == Kind.MI_BODY) {
            if (bytes.length < 13 || !stable(bytes[1] & 255)) return result;
            add(result, date(bytes, 2), xiaomiWeight(u16(bytes, 11), bytes[1] & 255), now);
        } else if (kind == Kind.MI_WEIGHT) {
            for (int i = 0; i + 10 <= bytes.length; i += 10) {
                int flags = bytes[i] & 255;
                if (stable(flags)) add(result, date(bytes, i + 3), xiaomiWeight(u16(bytes, i + 1), flags), now);
            }
        } else {
            for (int i = 0; i + 3 <= bytes.length;) {
                int flags = bytes[i] & 255;
                int length = 3 + ((flags & 2) != 0 ? 7 : 0) + ((flags & 4) != 0 ? 1 : 0) + ((flags & 8) != 0 ? 4 : 0);
                if (i + length > bytes.length) break;
                int raw = u16(bytes, i + 1);
                if (raw != 65535) add(result, (flags & 2) != 0 ? date(bytes, i + 3) : 0,
                        raw * ((flags & 1) != 0 ? 0.01 * 0.45359237 : 0.005), now);
                i += length;
            }
        }
        return result;
    }
    private static boolean stable(int flags) { return (flags & 0x20) != 0 && (flags & 0x80) == 0; }
    static double xiaomiWeight(int raw, int flags) {
        return (flags & 1) != 0 ? raw / 100.0 * 0.45359237 : raw / 200.0;
    }
    private static int u16(byte[] bytes, int offset) { return (bytes[offset] & 255) | ((bytes[offset + 1] & 255) << 8); }
    private static long date(byte[] bytes, int offset) {
        try {
            Calendar c = Calendar.getInstance();
            c.clear(); c.setLenient(false);
            c.set(u16(bytes, offset), (bytes[offset + 2] & 255) - 1, bytes[offset + 3] & 255,
                    bytes[offset + 4] & 255, bytes[offset + 5] & 255, bytes[offset + 6] & 255);
            return c.getTimeInMillis();
        } catch (IllegalArgumentException e) { return 0; }
    }
    private static void add(List<Measurement> result, long time, double kg, long now) {
        if (!Double.isFinite(kg) || kg < 10 || kg > 300) return;
        boolean validTime = time >= 1420070400000L && time <= now + 86400000L;
        result.add(new Measurement(validTime ? time : now, Math.round(kg * 1000) / 1000.0, validTime));
    }
    public static Measurement liveTime(Measurement sample, long now) {
        return Math.abs(sample.timeMillis - now) > 600000
                ? new Measurement(now, sample.weightKg, false) : sample;
    }
    public static byte[] currentTime() {
        Calendar c = Calendar.getInstance(); int year = c.get(Calendar.YEAR);
        return new byte[]{(byte) year, (byte) (year >> 8), (byte) (c.get(Calendar.MONTH) + 1),
                (byte) c.get(Calendar.DAY_OF_MONTH), (byte) c.get(Calendar.HOUR_OF_DAY),
                (byte) c.get(Calendar.MINUTE), (byte) c.get(Calendar.SECOND), 0, 0, 1};
    }
}
