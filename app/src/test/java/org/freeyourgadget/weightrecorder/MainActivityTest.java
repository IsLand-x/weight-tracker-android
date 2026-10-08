/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import android.app.Dialog;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import static org.robolectric.Shadows.shadowOf;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 28, application = android.app.Application.class)
public class MainActivityTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    @Before public void setup() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("org.freeyourgadget.weightrecorder.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
        Settings.prefs(RuntimeEnvironment.getApplication()).edit().clear().apply();
        WeightDatabase.get(RuntimeEnvironment.getApplication()).getWritableDatabase().delete("weight", null, null);
    }
    @After public void close() {
        if (controller != null) controller.pause().stop().destroy();
        WeightDatabase.get(RuntimeEnvironment.getApplication()).close();
        ReflectionHelpers.setStaticField(WeightDatabase.class, "instance", null);
    }
    private View findText(View view, String value) {
        if (view instanceof TextView && ((TextView) view).getText().toString().equals(value)) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = findText(((ViewGroup) view).getChildAt(i), value); if (found != null) return found;
        }
        return null;
    }
    private ListView findList(View view) {
        if (view instanceof ListView) return (ListView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            ListView found = findList(((ViewGroup) view).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    private void launch() { controller = Robolectric.buildActivity(MainActivity.class).setup().visible(); activity = controller.get(); }
    @Test public void recentShowsThreeAndMoreShowsEveryRecordNewestFirst() {
        WeightDatabase db = WeightDatabase.get(RuntimeEnvironment.getApplication());
        for (int i = 0; i < 5; i++) db.save(new ScaleProtocol.Measurement(System.currentTimeMillis() - (5 - i) * 86400000L, 70 + i, true), "scale", "");
        launch(); View root = activity.getWindow().getDecorView();
        assertNull(findText(root, "70.00 kg")); assertNull(findText(root, "71.00 kg"));
        for (int i = 72; i <= 74; i++) assertNotNull(findText(root, i + ".00 kg"));
        findText(root, "更多").performClick(); Dialog sheet = ShadowDialog.getLatestDialog();
        ListView list = findList(sheet.getWindow().getDecorView()); assertNotNull(list); assertEquals(5, list.getAdapter().getCount());
        assertTrue(list.getAdapter().getItem(0).toString().startsWith("74.00 kg"));
        assertTrue(list.getAdapter().getItem(4).toString().startsWith("70.00 kg"));
        list.performItemClick(list.getAdapter().getView(0, null, list), 0, 0);
        assertNotNull(findText(ShadowDialog.getLatestDialog().getWindow().getDecorView(), "74.00 kg"));
    }
    @Test public void settingsCancelDoesNotChangePolicyAndSavePersistsIndependentSwitches() {
        launch(); ReflectionHelpers.callInstanceMethod(activity, "showSettings"); shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        MaterialSwitch morning = (MaterialSwitch) findText(dialog.getWindow().getDecorView(), "上午记录调用 Webhook");
        assertTrue(morning.isChecked()); morning.setChecked(false); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(Settings.prefs(activity).getBoolean("morning_webhook", true));
        ReflectionHelpers.callInstanceMethod(activity, "showSettings"); shadowOf(Looper.getMainLooper()).idle();
        dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        ((MaterialSwitch) findText(dialog.getWindow().getDecorView(), "下午记录调用 Webhook")).setChecked(false);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(Settings.prefs(activity).getBoolean("afternoon_webhook", true));
        assertTrue(Settings.prefs(activity).getBoolean("morning_webhook", true));
    }
}
