/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.core.content.ContextCompat;

/** Opens a weighing session from any foreground screen, once per measurement. */
final class LiveWeighingLauncher extends BroadcastReceiver implements Application.ActivityLifecycleCallbacks {
    private Activity foreground;
    private boolean launched, previousStable;
    private long lastReading;
    ScaleProtocol.LiveReading latest;

    void register(Application application) {
        application.registerActivityLifecycleCallbacks(this);
        ContextCompat.registerReceiver(application, this,
                new IntentFilter(application.getPackageName() + ".LIVE_WEIGHT"), ContextCompat.RECEIVER_NOT_EXPORTED);
    }
    @Override public void onReceive(Context context, Intent intent) {
        double weight = intent.getDoubleExtra("weight", 0);
        boolean stable = intent.getBooleanExtra("stable", false);
        if (!Double.isFinite(weight) || weight < 3 || weight > 300) return;
        long now = SystemClock.elapsedRealtime();
        // A fresh unstable reading after a completed measurement, or a long silence, starts a new session.
        if (now - lastReading > 15000 || (previousStable && !stable)) launched = false;
        previousStable = stable; lastReading = now;
        latest = new ScaleProtocol.LiveReading(weight, stable);
        if (foreground == null) { Notifications.weighing(context, weight, stable); return; }
        if (foreground instanceof LiveWeighingActivity) { launched = true; return; }
        if (launched || foreground == null || foreground.isFinishing()) return;
        launched = true;
        foreground.startActivity(new Intent(foreground, LiveWeighingActivity.class)
                .putExtra("initial_weight", weight).putExtra("initial_stable", stable));
    }
    @Override public void onActivityResumed(Activity activity) { foreground = activity; }
    @Override public void onActivityPaused(Activity activity) { if (foreground == activity) foreground = null; }
    @Override public void onActivityDestroyed(Activity activity) { if (foreground == activity) foreground = null; }
    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
}
