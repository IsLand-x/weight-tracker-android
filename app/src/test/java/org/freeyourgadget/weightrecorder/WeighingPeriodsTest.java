/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Test;
import static org.junit.Assert.*;

public class WeighingPeriodsTest {
    @Test public void startIsInclusiveAndEndIsExclusiveIncludingSeconds() {
        WeighingPeriods periods = new WeighingPeriods(6 * 60, 12 * 60);
        assertFalse(periods.isMorningMinute(359)); assertTrue(periods.isMorningMinute(360));
        assertTrue(periods.isMorningMinute(719)); assertFalse(periods.isMorningMinute(720));
        Calendar c = Calendar.getInstance(); c.set(Calendar.HOUR_OF_DAY, 11); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59);
        assertTrue(periods.isMorning(c.getTimeInMillis())); c.set(Calendar.HOUR_OF_DAY, 12); c.set(Calendar.MINUTE, 0);
        assertFalse(periods.isMorning(c.getTimeInMillis()));
    }
    @Test public void afternoonIsAlwaysExactComplementEvenAcrossMidnight() {
        for (int[] range : new int[][]{{360, 720}, {1320, 360}, {0, 720}}) {
            WeighingPeriods morning = new WeighingPeriods(range[0], range[1]);
            WeighingPeriods afternoon = new WeighingPeriods(range[1], range[0]);
            for (int minute = 0; minute < 1440; minute++) assertNotEquals(morning.isMorningMinute(minute), afternoon.isMorningMinute(minute));
        }
    }
    @Test public void classificationUsesPhoneTimezoneRatherThanUtc() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")); utc.clear(); utc.set(2026, 9, 9, 0, 0, 0);
            assertTrue(new WeighingPeriods(360, 720).isMorning(utc.getTimeInMillis()));
            utc.set(Calendar.HOUR_OF_DAY, 4); assertFalse(new WeighingPeriods(360, 720).isMorning(utc.getTimeInMillis()));
        } finally { TimeZone.setDefault(original); }
    }
    @Test(expected = IllegalArgumentException.class) public void equalTimesAreRejected() { new WeighingPeriods(360, 360); }
    @Test(expected = IllegalArgumentException.class) public void invalidMinutesAreRejected() { new WeighingPeriods(-1, 360); }
}
