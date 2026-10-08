/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import android.content.Context;
import android.view.MotionEvent;
import java.util.Arrays;
import java.util.Calendar;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 28, application = android.app.Application.class)
public class WeightChartViewTest {
    private void touch(WeightChartView view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, 0, action, x, y, 0); view.onTouchEvent(event); event.recycle();
    }
    @Test public void slideUpdatesSelectionWithoutChangingDateWindowAndKeepsItAcrossRefresh() {
        Context context = RuntimeEnvironment.getApplication(); Settings.prefs(context).edit().clear().apply();
        WeightChartView view = new WeightChartView(context, 0, 0, 0, 0); view.layout(0, 0, 400, 260);
        WeightDatabase.Record first = new WeightDatabase.Record(), second = new WeightDatabase.Record();
        Calendar c = Calendar.getInstance(); c.set(Calendar.HOUR_OF_DAY, 8); c.set(Calendar.MINUTE, 0);
        first.id = 1; first.timeMillis = c.getTimeInMillis(); first.weight = 70;
        c.set(Calendar.HOUR_OF_DAY, 18); second.id = 2; second.timeMillis = c.getTimeInMillis(); second.weight = 72;
        view.setRecords(Arrays.asList(first, second));
        float density = context.getResources().getDisplayMetrics().density;
        long end = ChartWindow.dayEnd(System.currentTimeMillis()), start = ChartWindow.start(end, 7);
        float left = 47 * density, width = 400 - 63 * density;
        float x1 = left + (float) ((first.timeMillis - start) / (double) (end - start)) * width;
        float x2 = left + (float) ((second.timeMillis - start) / (double) (end - start)) * width;
        // Points on the same day are close horizontally; y must distinguish them.
        float top = 34 * density, bottom = 260 - 64 * density;
        float y1 = bottom - (bottom - top) / 6, y2 = top + (bottom - top) / 6;
        touch(view, MotionEvent.ACTION_DOWN, x1, y1);
        assertTrue(view.getContentDescription().toString().contains("70.0公斤"));
        touch(view, MotionEvent.ACTION_MOVE, x2 + 30, y2);
        assertTrue(view.getContentDescription().toString().contains("72.0公斤"));
        touch(view, MotionEvent.ACTION_UP, x2, y2);
        assertTrue(view.getContentDescription().toString().contains("72.0公斤"));
        WeightDatabase.Record refreshed = new WeightDatabase.Record(); refreshed.id = 2; refreshed.timeMillis = second.timeMillis; refreshed.weight = 72;
        view.setRecords(Arrays.asList(first, refreshed));
        assertTrue(view.getContentDescription().toString().contains("72.0公斤"));
        view.setPeriod(30); assertFalse(view.getContentDescription().toString().contains("72.0公斤"));
    }
}
