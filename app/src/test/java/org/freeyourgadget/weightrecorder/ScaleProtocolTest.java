package org.freeyourgadget.weightrecorder;
import org.junit.Test;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import static org.junit.Assert.*;

public class ScaleProtocolTest {
    private static final long NOW = 1791264000000L;
    private byte[] miPacket(int flags, int raw) {
        return new byte[]{(byte) flags, (byte) raw, (byte) (raw >> 8), (byte) 0xea, 7, 10, 6, 12, 0, 0};
    }
    @Test public void identifiesOnlySupportedScales() {
        assertEquals(ScaleProtocol.Kind.MI_WEIGHT, ScaleProtocol.identify("MI SCALE2", Collections.emptyList(), false));
        assertEquals(ScaleProtocol.Kind.MI_BODY, ScaleProtocol.identify("mibfs", Collections.emptyList(), false));
        assertEquals(ScaleProtocol.Kind.MI_BODY, ScaleProtocol.identify(null, Arrays.asList(ScaleProtocol.BODY_SERVICE), true));
        assertEquals(ScaleProtocol.Kind.STANDARD, ScaleProtocol.identify("Health scale", Arrays.asList(ScaleProtocol.WEIGHT_SERVICE), false));
        assertNull(ScaleProtocol.identify("Mi Band 7", Collections.emptyList(), true));
    }
    @Test public void decodesStableMiWeightAndRejectsUnstableOrRemovedWeight() {
        assertEquals(70.25, ScaleProtocol.decode(ScaleProtocol.Kind.MI_WEIGHT, miPacket(0x20, 14050), NOW).get(0).weightKg, .0001);
        assertTrue(ScaleProtocol.decode(ScaleProtocol.Kind.MI_WEIGHT, miPacket(0, 14050), NOW).isEmpty());
        assertTrue(ScaleProtocol.decode(ScaleProtocol.Kind.MI_WEIGHT, miPacket(0xa0, 14050), NOW).isEmpty());
    }
    @Test public void decodesTwoHistorySamplesAndCorrectWeightUnits() {
        byte[] first = miPacket(0x20, 14000), second = miPacket(0x30, 14000), packet = new byte[20];
        System.arraycopy(first, 0, packet, 0, 10); System.arraycopy(second, 0, packet, 10, 10);
        assertEquals(2, ScaleProtocol.decode(ScaleProtocol.Kind.MI_WEIGHT, packet, NOW).size());
        assertEquals(70.0, ScaleProtocol.xiaomiWeight(14000, 0x30), .0001);
        assertEquals(70.307, ScaleProtocol.decode(ScaleProtocol.Kind.MI_WEIGHT, miPacket(0x21, 15500), NOW).get(0).weightKg, .0001);
    }
    @Test public void decodesCompositionAndNormalizesInvalidClockInMilliseconds() {
        byte[] packet = {2, 0x20, (byte) 0xea, 7, 10, 6, 12, 0, 0, 0, 2, (byte) 0xb0, 0x36};
        ScaleProtocol.Measurement valid = ScaleProtocol.decode(ScaleProtocol.Kind.MI_BODY, packet, NOW).get(0);
        assertEquals(70.0, valid.weightKg, .0001); assertTrue(valid.timeMillis > 1700000000000L);
        packet[4] = 0; ScaleProtocol.Measurement fallback = ScaleProtocol.decode(ScaleProtocol.Kind.MI_BODY, packet, NOW).get(0);
        assertEquals(NOW, fallback.timeMillis); assertFalse(fallback.deviceTime);
    }
    @Test public void standardScaleAcceptsFinalWeightButRejectsTruncatedAndSentinelPackets() {
        assertEquals(70, ScaleProtocol.decode(ScaleProtocol.Kind.STANDARD, new byte[]{0, (byte) 0xb0, 0x36}, NOW).get(0).weightKg, .0001);
        assertTrue(ScaleProtocol.decode(ScaleProtocol.Kind.STANDARD, new byte[]{2, 1, 2}, NOW).isEmpty());
        assertTrue(ScaleProtocol.decode(ScaleProtocol.Kind.STANDARD, new byte[]{0, -1, -1}, NOW).isEmpty());
        assertTrue(ScaleProtocol.decode(ScaleProtocol.Kind.MI_BODY, new byte[]{0}, NOW).isEmpty());
        assertTrue(ScaleProtocol.decode(ScaleProtocol.Kind.MI_WEIGHT, miPacket(0x20, 100), NOW).isEmpty());
    }
    @Test public void calendarRangeIncludesFirstDayAndLeapYear() {
        Calendar c = Calendar.getInstance(); c.clear(); c.set(2024, Calendar.MARCH, 1, 12, 0, 0);
        long end = ChartWindow.dayEnd(c.getTimeInMillis());
        c.setTimeInMillis(ChartWindow.start(end, 7)); assertEquals(Calendar.FEBRUARY, c.get(Calendar.MONTH)); assertEquals(24, c.get(Calendar.DAY_OF_MONTH)); assertEquals(0, c.get(Calendar.HOUR_OF_DAY));
        c.setTimeInMillis(ChartWindow.start(end, 365)); assertEquals(2023, c.get(Calendar.YEAR)); assertEquals(Calendar.MARCH, c.get(Calendar.MONTH)); assertEquals(2, c.get(Calendar.DAY_OF_MONTH));
    }
    @Test public void staleLiveClockUsesActualWeighingTime() {
        ScaleProtocol.Measurement sample = new ScaleProtocol.Measurement(NOW - 86400000, 70, true);
        assertEquals(NOW, ScaleProtocol.liveTime(sample, NOW).timeMillis);
        assertFalse(ScaleProtocol.liveTime(sample, NOW).deviceTime);
        assertEquals(NOW - 1000, ScaleProtocol.liveTime(new ScaleProtocol.Measurement(NOW - 1000, 70, true), NOW).timeMillis);
    }
    @Test public void dragLimitStillIncludesTheEarliestWeighingDay() {
        Calendar c = Calendar.getInstance(); c.clear(); c.set(2024, Calendar.JANUARY, 1, 8, 0, 0);
        long first = c.getTimeInMillis(); c.set(Calendar.HOUR_OF_DAY, 0); long midnight = c.getTimeInMillis();
        for (int period : new int[]{7, 30, 365}) {
            assertEquals(midnight, ChartWindow.start(ChartWindow.endForFirstDay(first, period), period));
        }
    }
    @Test public void decodesLiveWeightFluctuationAndStability() {
        byte[] unstable = miPacket(0x00, 14000);
        ScaleProtocol.LiveReading live1 = ScaleProtocol.decodeLive(ScaleProtocol.Kind.MI_WEIGHT, unstable);
        assertNotNull(live1);
        assertEquals(70.0, live1.weightKg, .0001);
        assertFalse(live1.stable);

        byte[] stable = miPacket(0x20, 14000);
        ScaleProtocol.LiveReading live2 = ScaleProtocol.decodeLive(ScaleProtocol.Kind.MI_WEIGHT, stable);
        assertNotNull(live2);
        assertEquals(70.0, live2.weightKg, .0001);
        assertTrue(live2.stable);
    }
    @Test public void steppingOffDoesNotTriggerANewLiveWeighing() {
        assertNull(ScaleProtocol.decodeLive(ScaleProtocol.Kind.MI_WEIGHT, miPacket(0xa0, 14000)));
        byte[] body = new byte[13]; body[1] = (byte) 0xa0; body[11] = (byte) 0xb0; body[12] = 0x36;
        assertNull(ScaleProtocol.decodeLive(ScaleProtocol.Kind.MI_BODY, body));
    }
    @Test public void decodesAdvertisementServiceDataAndCattyUnit() {
        // Test 斤 unit: flags = 0x10, raw = 14000 -> 14000 / 100 * 0.5 = 70.0 kg
        assertEquals(70.0, ScaleProtocol.xiaomiWeight(14000, 0x10), .0001);

        // Build a simulated BLE scan record containing 0x181d service data (len 13: len=13, type=0x16, uuid=181d, 10 bytes payload)
        byte[] payload = miPacket(0x20, 14000);
        byte[] scanRecord = new byte[payload.length + 4];
        scanRecord[0] = (byte) (payload.length + 3); // length
        scanRecord[1] = 0x16; // Service Data 16-bit UUID
        scanRecord[2] = 0x1d; scanRecord[3] = 0x18; // 0x181D LE
        System.arraycopy(payload, 0, scanRecord, 4, payload.length);

        ScaleProtocol.LiveReading liveAd = ScaleProtocol.decodeAdvertisement(ScaleProtocol.Kind.MI_WEIGHT, scanRecord);
        assertNotNull(liveAd);
        assertEquals(70.0, liveAd.weightKg, .0001);
        assertTrue(liveAd.stable);

        assertEquals(1, ScaleProtocol.decodeAdvertisementSamples(ScaleProtocol.Kind.MI_WEIGHT, scanRecord, NOW).size());
    }
}
