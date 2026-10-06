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
}
