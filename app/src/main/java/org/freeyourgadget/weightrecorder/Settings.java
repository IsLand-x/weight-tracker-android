/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;
import android.content.Context;
import android.content.SharedPreferences;
final class Settings {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    static String endpoint(Context c) { return prefs(c).getBoolean("webhook_enabled", false) ? prefs(c).getString("endpoint", "") : ""; }
    static WeighingPeriods periods(Context c) {
        return new WeighingPeriods(prefs(c).getInt("morning_start", WeighingPeriods.DEFAULT_START),
                prefs(c).getInt("morning_end", WeighingPeriods.DEFAULT_END));
    }
    static String endpoint(Context c, long timeMillis) {
        String key = periods(c).isMorning(timeMillis) ? "morning_webhook" : "afternoon_webhook";
        return prefs(c).getBoolean(key, true) ? endpoint(c) : "";
    }
}
