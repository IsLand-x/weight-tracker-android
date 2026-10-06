/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;
import android.content.Context;
import android.content.SharedPreferences;
final class Settings {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    static String endpoint(Context c) { return prefs(c).getBoolean("webhook_enabled", false) ? prefs(c).getString("endpoint", "") : ""; }
}
