/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;
import java.util.Calendar;

public final class ChartWindow {
    public static long endForFirstDay(long first, int period) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(dayEnd(first));
        if (period == 365) { c.add(Calendar.YEAR, 1); c.add(Calendar.DAY_OF_MONTH, -1); }
        else c.add(Calendar.DAY_OF_MONTH, period - 1);
        return c.getTimeInMillis();
    }
    private ChartWindow() {}
    public static long dayEnd(long time) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999);
        return c.getTimeInMillis();
    }
    public static long start(long end, int period) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(end);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        if (period == 365) { c.add(Calendar.YEAR, -1); c.add(Calendar.DAY_OF_YEAR, 1); }
        else c.add(Calendar.DAY_OF_YEAR, 1 - period);
        return c.getTimeInMillis();
    }
}
