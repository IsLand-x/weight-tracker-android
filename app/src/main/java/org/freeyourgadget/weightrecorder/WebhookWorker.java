/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class WebhookWorker extends Worker {
    public WebhookWorker(@NonNull Context c, @NonNull WorkerParameters parameters) { super(c, parameters); }
    public static void enqueue(Context c, long id) {
        WorkManager.getInstance(c).enqueueUniqueWork("weight-" + id, ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(WebhookWorker.class).setInputData(new Data.Builder().putLong("id", id).build())
                        .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build());
    }
    @NonNull @Override public Result doWork() {
        Context context = getApplicationContext(); WeightDatabase db = WeightDatabase.get(context);
        long id = getInputData().getLong("id", -1); WeightDatabase.Record record = db.find(id);
        if (record == null || record.state.equals("success") || record.state.equals("local") || record.state.equals("failed")) return Result.success();
        if (record.state.equals("sending")) {
            db.update(id, "failed", "上次发送中断，结果未知。请确认服务端记录后再重试。"); changed(context); return Result.success();
        }
        if (Settings.endpoint(context).isEmpty()) {
            db.update(id, "local", "Webhook 已关闭，仅保存在本机"); changed(context); return Result.success();
        }
        db.update(id, "sending", "正在发送"); changed(context);
        String message;
        try {
            message = WebhookTransport.send(record.endpoint, record.timeMillis, record.weight);
        } catch (Exception e) {
            // The server may already have processed a timed-out request. Never retry it silently.
            db.update(id, "failed", "发送未确认：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            changed(context); return Result.success();
        }
        db.update(id, "success", message);
        // Notification permission may be revoked during the HTTP request. Delivery still succeeded.
        try { Notifications.success(context, id, message); } catch (SecurityException ignored) {}
        changed(context); return Result.success();
    }
    static void changed(Context c) { c.sendBroadcast(new Intent(c.getPackageName() + ".CHANGED").setPackage(c.getPackageName())); }
}
