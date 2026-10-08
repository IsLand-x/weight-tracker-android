/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Locale;

final class RecordDetails {
    static void show(AppCompatActivity activity, WeightDatabase.Record record, Runnable refreshed) {
        String message = Ui.date(record.timeMillis, "yyyy.MM.dd HH:mm") + " · " + Settings.periods(activity).label(record.timeMillis)
                + "\n" + Ui.recordState(record.state);
        if (!record.message.isEmpty()) message += "\n\n" + record.message;
        if (record.state.equals("failed")) message += "\n\n若请求超时，服务端可能已收到。请先确认当日记录，再重试。";
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(String.format(Locale.getDefault(), "%.2f kg", record.weight)).setMessage(message).setNegativeButton("关闭", null);
        if ((record.state.equals("failed") || record.state.equals("local")) && !Settings.endpoint(activity, record.timeMillis).isEmpty()) {
            builder.setPositiveButton(record.state.equals("failed") ? "重试发送" : "发送这条记录", (d, w) -> {
                if (WeightDatabase.get(activity).prepareRetry(record.id, Settings.endpoint(activity, record.timeMillis))) {
                    WebhookWorker.enqueue(activity, record.id); refreshed.run();
                }
            });
        }
        builder.show();
    }
}
