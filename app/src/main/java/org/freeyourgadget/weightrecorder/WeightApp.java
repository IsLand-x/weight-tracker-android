/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;
import android.app.Application;

public class WeightApp extends Application {
    @Override public void onCreate() {
        super.onCreate(); Notifications.channels(this);
        for (WeightDatabase.Record record : WeightDatabase.get(this).all()) {
            if (record.state.equals("pending") || record.state.equals("sending")) WebhookWorker.enqueue(this, record.id);
        }
    }
}
