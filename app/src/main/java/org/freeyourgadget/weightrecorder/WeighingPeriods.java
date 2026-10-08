/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import java.util.Calendar;
import java.util.Locale;

/** Daily local-time ranges. Start is inclusive, end is exclusive. */
final class WeighingPeriods {
    static final int DEFAULT_START = 0;
    static final int DEFAULT_END = 12 * 60;
    final int morningStart, morningEnd;

    WeighingPeriods(int start, int end) {
        if (start < 0 || start >= 1440 || end < 0 || end >= 1440 || start == end)
            throw new IllegalArgumentException("上午开始和结束时间必须不同");
        morningStart = start; morningEnd = end;
    }

    boolean isMorning(long timeMillis) {
        Calendar local = Calendar.getInstance(); local.setTimeInMillis(timeMillis);
        return isMorningMinute(local.get(Calendar.HOUR_OF_DAY) * 60 + local.get(Calendar.MINUTE));
    }

    boolean isMorningMinute(int minute) {
        return morningStart < morningEnd ? minute >= morningStart && minute < morningEnd
                : minute >= morningStart || minute < morningEnd;
    }

    String label(long timeMillis) { return isMorning(timeMillis) ? "上午" : "下午"; }
    static String time(int minute) { return String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60); }
    String morningRange() { return range(morningStart, morningEnd); }
    String afternoonRange() { return range(morningEnd, morningStart); }
    private static String range(int start, int end) { return time(start) + " — " + time(end) + (start > end ? "（跨午夜）" : ""); }
}
