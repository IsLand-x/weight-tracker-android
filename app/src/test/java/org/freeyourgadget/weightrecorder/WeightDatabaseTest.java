package org.freeyourgadget.weightrecorder;
import android.content.Context;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 28, application = android.app.Application.class)
public class WeightDatabaseTest {
    WeightDatabase db;
    @Before public void setup() { Context context = RuntimeEnvironment.getApplication(); context.deleteDatabase("weight.db"); db = new WeightDatabase(context); }
    @After public void close() { db.close(); }
    @Test public void duplicateDoesNotCreateSecondWebhookAndSameWeightNextDayIsAllowed() {
        long time = 1791264000000L; ScaleProtocol.Measurement sample = new ScaleProtocol.Measurement(time, 70, true);
        long id = db.save(sample, "scale", "https://example.com/hook"); assertTrue(id > 0);
        assertEquals(-1, db.save(sample, "scale", "https://example.com/hook"));
        assertTrue(db.save(new ScaleProtocol.Measurement(time + 86400000, 70, true), "scale", "https://example.com/hook") > 0);
        assertEquals(2, db.all().size()); assertEquals("pending", db.find(id).state);
    }
    @Test public void repeatedLivePacketWithoutClockIsFilteredOnlyForTenSeconds() {
        long time = 1791264000000L;
        assertTrue(db.save(new ScaleProtocol.Measurement(time, 70, false), "scale", "") > 0);
        assertEquals(-1, db.save(new ScaleProtocol.Measurement(time + 3000, 70, false), "scale", ""));
        assertTrue(db.save(new ScaleProtocol.Measurement(time + 20000, 70, false), "scale", "") > 0);
    }
    @Test public void successCannotBeRetriedAndFailedUsesCurrentEndpoint() {
        long id = db.save(new ScaleProtocol.Measurement(1791264000000L, 70, true), "scale", "https://example.com/old");
        db.update(id, "success", "已记录"); assertFalse(db.prepareRetry(id, "https://example.com/new"));
        db.update(id, "failed", "timeout"); assertTrue(db.prepareRetry(id, "https://example.com/new"));
        assertEquals("pending", db.find(id).state); assertEquals("https://example.com/new", db.find(id).endpoint);
    }
    @Test public void historyArrivingBeforeLivePacketDoesNotSuppressOrDuplicateWebhook() {
        ScaleProtocol.Measurement sample = new ScaleProtocol.Measurement(1791264000000L, 70, true);
        long id = db.save(sample, "scale", "", true);
        assertEquals("local", db.find(id).state);
        assertEquals(id, db.save(sample, "scale", "https://example.com/hook", false));
        assertEquals("pending", db.find(id).state);
        assertEquals(-1, db.save(sample, "scale", "https://example.com/hook", false));
        assertEquals(-1, db.save(sample, "scale", "", true));
        assertEquals(1, db.all().size());
    }
}
