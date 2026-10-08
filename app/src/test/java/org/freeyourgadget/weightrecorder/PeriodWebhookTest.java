/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import android.content.Context;
import java.util.Calendar;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 28, application = android.app.Application.class)
public class PeriodWebhookTest {
    private Context context;
    private WeightDatabase db;
    private long time(int hour) {
        Calendar c = Calendar.getInstance(); c.clear(); c.set(2026, 9, 9, hour, 0); return c.getTimeInMillis();
    }
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication(); Settings.prefs(context).edit().clear()
                .putString("endpoint", "https://example.com/hook").putBoolean("webhook_enabled", true)
                .putInt("morning_start", 360).putInt("morning_end", 720).apply();
        context.deleteDatabase("weight.db"); db = new WeightDatabase(context);
    }
    @After public void close() { db.close(); }
    @Test public void disabledPeriodSavesLocallyWithoutQueuingOrDuplicatePromotion() {
        Settings.prefs(context).edit().putBoolean("morning_webhook", false).apply();
        long morning = time(8), afternoon = time(18);
        ScaleProtocol.Measurement sample = new ScaleProtocol.Measurement(morning, 70, true);
        long id = db.save(sample, "scale", Settings.endpoint(context, morning));
        assertEquals("local", db.find(id).state); assertEquals("", db.find(id).endpoint);
        assertEquals(-1, db.save(sample, "scale", Settings.endpoint(context, morning)));
        long second = db.save(new ScaleProtocol.Measurement(afternoon, 71, true), "scale", Settings.endpoint(context, afternoon));
        assertEquals("pending", db.find(second).state);
        Settings.prefs(context).edit().putBoolean("morning_webhook", true).apply();
        assertEquals(-1, db.save(sample, "scale", Settings.endpoint(context, morning)));
        assertEquals("local", db.find(id).state);
    }
    @Test public void globalAndPeriodSwitchesAreIndependentAndBoundaryIsAfternoon() {
        Settings.prefs(context).edit().putBoolean("afternoon_webhook", false).apply();
        assertFalse(Settings.endpoint(context, time(8)).isEmpty());
        assertEquals("", Settings.endpoint(context, time(12))); assertEquals("", Settings.endpoint(context, time(23)));
        Settings.prefs(context).edit().putBoolean("webhook_enabled", false).apply();
        assertEquals("", Settings.endpoint(context, time(8)));
    }
    @Test public void queuedRecordPolicyReflectsChangedPeriodsAndSwitches() {
        long morning = time(8);
        assertFalse(Settings.endpoint(context, morning).isEmpty());
        Settings.prefs(context).edit().putBoolean("afternoon_webhook", false).putInt("morning_start", 9 * 60).apply();
        assertEquals("下午", Settings.periods(context).label(morning)); assertEquals("", Settings.endpoint(context, morning));
    }
}
