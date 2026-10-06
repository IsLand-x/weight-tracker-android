/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;
import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

final class Notifications {
    static void channels(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = c.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel("scale", "体重秤连接", NotificationManager.IMPORTANCE_LOW));
            manager.createNotificationChannel(new NotificationChannel("webhook", "体重记录结果", NotificationManager.IMPORTANCE_DEFAULT));
        }
    }
    private static NotificationCompat.Builder builder(Context c, String channel) {
        PendingIntent intent = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(c, channel).setSmallIcon(R.drawable.ic_scale_notification).setContentIntent(intent)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
    }
    static Notification connection(Context c, String text) {
        PendingIntent stop = PendingIntent.getService(c, 1, new Intent(c, ScaleService.class).setAction("stop"), PendingIntent.FLAG_IMMUTABLE);
        return builder(c, "scale").setContentTitle("体重秤已开启").setContentText(text).setOngoing(true)
                .addAction(0, "断开", stop).build();
    }
    static void success(Context c, long id, String message) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        c.getSystemService(NotificationManager.class).notify((int) (id % (Integer.MAX_VALUE - 100)) + 100,
                builder(c, "webhook").setContentTitle("体重记录成功").setContentText(message)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(message)).setAutoCancel(true).build());
    }
}
