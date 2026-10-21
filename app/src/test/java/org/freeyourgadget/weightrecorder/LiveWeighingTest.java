/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class LiveWeighingTest {
    @Before public void setup() {
        Settings.prefs(RuntimeEnvironment.getApplication()).edit().clear().apply();
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
                "org.freeyourgadget.weightrecorder.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
    }
    private View text(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = text(((ViewGroup) root).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private Intent reading(double weight, boolean stable) {
        return new Intent(RuntimeEnvironment.getApplication().getPackageName() + ".LIVE_WEIGHT")
                .setPackage(RuntimeEnvironment.getApplication().getPackageName())
                .putExtra("weight", weight).putExtra("stable", stable);
    }
    @Test public void liveValuesAnimateTogetherAndStableResultStaysVisible() {
        Intent intent = new Intent().putExtra("initial_weight", 65.0);
        try (ActivityController<LiveWeighingActivity> controller = Robolectric.buildActivity(LiveWeighingActivity.class, intent).setup().visible()) {
            LiveWeighingActivity activity = controller.get();
            WeightGaugeView gauge = ReflectionHelpers.getField(activity, "gauge");
            assertEquals(110, gauge.getMaximum()); assertEquals(65, gauge.getDisplayedWeight(), .001);
            activity.sendBroadcast(reading(72.35, false));
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            assertEquals(72.35, gauge.getDisplayedWeight(), .001);
            assertNotNull(text(activity.getWindow().getDecorView(), "72.35"));
            activity.sendBroadcast(reading(72.4, true));
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            activity.sendBroadcast(reading(10, false));
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4));
            assertEquals(72.4, gauge.getDisplayedWeight(), .001);
            assertNotNull(text(activity.getWindow().getDecorView(), "体重已稳定锁定"));
            assertFalse(activity.isFinishing());
        }
    }
    @Test public void rangePersistsAndOverflowKeepsActualWeightAfterRecreation() {
        Intent intent = new Intent().putExtra("initial_weight", 120.5).putExtra("initial_stable", true);
        ActivityController<LiveWeighingActivity> controller = Robolectric.buildActivity(LiveWeighingActivity.class, intent).setup().visible();
        LiveWeighingActivity activity = controller.get(); View root = activity.getWindow().getDecorView();
        text(root, "100 kg").performClick();
        assertEquals(100, Settings.prefs(activity).getInt("gauge_maximum", 0));
        assertNotNull(text(root, "体重超出当前量程，请切换更大量程"));
        assertNotNull(text(root, "120.50"));
        text(root, "200 kg").performClick();
        assertNotNull(text(root, "测量已完成，点击完成返回查看记录"));
        Bundle saved = new Bundle(); controller.saveInstanceState(saved).pause().stop().destroy();
        try (ActivityController<LiveWeighingActivity> restored = Robolectric.buildActivity(LiveWeighingActivity.class, intent)
                .create(saved).start().resume().visible()) {
            WeightGaugeView gauge = ReflectionHelpers.getField(restored.get(), "gauge");
            assertEquals(200, gauge.getMaximum()); assertEquals(120.5, gauge.getDisplayedWeight(), .001);
            assertNotNull(text(restored.get().getWindow().getDecorView(), "体重已稳定锁定"));
        }
    }
    @Test public void launcherOpensFromAnyForegroundScreenWithoutRepeatingCompletedReading() {
        LiveWeighingLauncher launcher = new LiveWeighingLauncher();
        try (ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class).setup().visible()) {
            AppCompatActivity activity = controller.get(); launcher.onActivityResumed(activity);
            launcher.onReceive(activity, reading(Double.NaN, false));
            assertNull(shadowOf(activity).getNextStartedActivity());
            launcher.onReceive(activity, reading(70.25, false));
            Intent launched = shadowOf(activity).getNextStartedActivity();
            assertEquals(LiveWeighingActivity.class.getName(), launched.getComponent().getClassName());
            assertEquals(70.25, launched.getDoubleExtra("initial_weight", 0), .001);
            launcher.onReceive(activity, reading(70.3, true));
            launcher.onReceive(activity, reading(70.3, true));
            assertNull(shadowOf(activity).getNextStartedActivity());
            launcher.onReceive(activity, reading(71.0, false));
            assertNotNull(shadowOf(activity).getNextStartedActivity());
            launcher.onActivityPaused(activity);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16));
            launcher.onReceive(activity, reading(75, true));
            assertNull(shadowOf(activity).getNextStartedActivity());
        }
    }
}
