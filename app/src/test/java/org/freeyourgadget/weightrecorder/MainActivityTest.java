/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
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
import org.robolectric.util.ReflectionHelpers;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 28, application = android.app.Application.class)
public class MainActivityTest {
    private final List<ActivityController<? extends AppCompatActivity>> controllers = new ArrayList<>();
    @Before public void setup() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("org.freeyourgadget.weightrecorder.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
        Settings.prefs(RuntimeEnvironment.getApplication()).edit().clear().apply();
        WeightDatabase.get(RuntimeEnvironment.getApplication()).getWritableDatabase().delete("weight", null, null);
    }
    @After public void close() {
        for (ActivityController<?> controller : controllers) controller.pause().stop().destroy();
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
    private View findDescription(View view, String value) {
        if (value.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = findDescription(((ViewGroup) view).getChildAt(i), value); if (found != null) return found;
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
    private <T extends AppCompatActivity> T launch(Class<T> type) {
        ActivityController<T> controller = Robolectric.buildActivity(type).setup().visible(); controllers.add(controller);
        shadowOf(Looper.getMainLooper()).idle(); return controller.get();
    }
    private List<Integer> recordPositions(ListView list) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < list.getAdapter().getCount(); i++) if (list.getAdapter().isEnabled(i)) result.add(i);
        return result;
    }
    @Test public void recentShowsThreeAndMoreOpensSeparatePageWithEveryRecordNewestFirst() {
        WeightDatabase db = WeightDatabase.get(RuntimeEnvironment.getApplication());
        for (int i = 0; i < 5; i++) db.save(new ScaleProtocol.Measurement(System.currentTimeMillis() - (5 - i) * 86400000L, 70 + i, true), "scale", "");
        MainActivity activity = launch(MainActivity.class); View root = activity.getWindow().getDecorView();
        assertNull(findText(root, "70.00 kg")); assertNull(findText(root, "71.00 kg"));
        for (int i = 72; i <= 74; i++) assertNotNull(findText(root, i + ".00 kg"));
        findText(root, "更多").performClick(); Intent intent = shadowOf(activity).getNextStartedActivity();
        assertEquals(RecordsActivity.class.getName(), intent.getComponent().getClassName());
        RecordsActivity records = launch(RecordsActivity.class); ListView list = findList(records.getWindow().getDecorView());
        List<Integer> positions = recordPositions(list); assertEquals(5, positions.size());
        assertNotNull(findText(list.getAdapter().getView(positions.get(0), null, list), "74.00 kg"));
        assertNotNull(findText(list.getAdapter().getView(positions.get(4), null, list), "70.00 kg"));
        list.performItemClick(list.getAdapter().getView(positions.get(0), null, list), positions.get(0), 0);
        assertNotNull(findText(ShadowDialog.getLatestDialog().getWindow().getDecorView(), "74.00 kg"));
    }
    @Test public void settingsBackDoesNotChangePolicyAndSavePersistsIndependentSwitches() {
        MainActivity main = launch(MainActivity.class); findDescription(main.getWindow().getDecorView(), "设置").performClick();
        assertEquals(SettingsActivity.class.getName(), shadowOf(main).getNextStartedActivity().getComponent().getClassName());
        SettingsActivity settings = launch(SettingsActivity.class); View root = settings.getWindow().getDecorView();
        ((MaterialSwitch) findText(root, "上午记录调用 Webhook")).setChecked(false);
        findDescription(root, "返回").performClick(); assertTrue(settings.isFinishing());
        assertTrue(Settings.prefs(main).getBoolean("morning_webhook", true));
        SettingsActivity second = launch(SettingsActivity.class); root = second.getWindow().getDecorView();
        ((MaterialSwitch) findText(root, "下午记录调用 Webhook")).setChecked(false); findText(root, "保存").performClick();
        assertTrue(second.isFinishing()); assertFalse(Settings.prefs(main).getBoolean("afternoon_webhook", true));
        assertTrue(Settings.prefs(main).getBoolean("morning_webhook", true));
    }
    @Test public void invalidTimeRangeCannotBeSaved() {
        SettingsActivity activity = launch(SettingsActivity.class); View root = activity.getWindow().getDecorView();
        findText(root, "12:00").performClick(); TimePickerDialog picker = (TimePickerDialog) ShadowDialog.getLatestDialog();
        picker.updateTime(0, 0); picker.getButton(TimePickerDialog.BUTTON_POSITIVE).performClick();
        findText(root, "保存").performClick(); assertFalse(activity.isFinishing());
        assertEquals(WeighingPeriods.DEFAULT_END, Settings.periods(activity).morningEnd);
        assertEquals(View.VISIBLE, findText(root, "开始和结束时间必须不同").getVisibility());
    }
    @Test public void settingsDraftSurvivesRecreationWithoutSaving() {
        SettingsActivity activity = launch(SettingsActivity.class); View root = activity.getWindow().getDecorView();
        ((MaterialSwitch) findText(root, "上午记录调用 Webhook")).setChecked(false);
        Bundle state = new Bundle(); ActivityController<? extends AppCompatActivity> original = controllers.remove(controllers.size() - 1);
        original.saveInstanceState(state).pause().stop().destroy();
        ActivityController<SettingsActivity> recreated = Robolectric.buildActivity(SettingsActivity.class).create(state).start().resume().visible();
        controllers.add(recreated); shadowOf(Looper.getMainLooper()).idle();
        assertFalse(((MaterialSwitch) findText(recreated.get().getWindow().getDecorView(), "上午记录调用 Webhook")).isChecked());
        assertTrue(Settings.prefs(activity).getBoolean("morning_webhook", true));
    }
    @Test public void recordPageFiltersAndRefreshesAfterNewWeightWithoutDuplicating() {
        Calendar date = Calendar.getInstance(); date.set(Calendar.HOUR_OF_DAY, 8); date.set(Calendar.MINUTE, 0);
        WeightDatabase db = WeightDatabase.get(RuntimeEnvironment.getApplication());
        db.save(new ScaleProtocol.Measurement(date.getTimeInMillis(), 70, true), "scale", "");
        date.set(Calendar.HOUR_OF_DAY, 18); db.save(new ScaleProtocol.Measurement(date.getTimeInMillis(), 71, true), "scale", "");
        RecordsActivity activity = launch(RecordsActivity.class); View root = activity.getWindow().getDecorView(); ListView list = findList(root);
        assertEquals(2, recordPositions(list).size()); findText(root, "下午").performClick();
        assertEquals(1, recordPositions(list).size()); int position = recordPositions(list).get(0);
        assertNotNull(findText(list.getAdapter().getView(position, null, list), "71.00 kg"));
        date.set(Calendar.HOUR_OF_DAY, 19); db.save(new ScaleProtocol.Measurement(date.getTimeInMillis(), 72, true), "scale", "");
        activity.sendBroadcast(new Intent(activity.getPackageName() + ".CHANGED").setPackage(activity.getPackageName()));
        shadowOf(Looper.getMainLooper()).idle(); assertEquals(2, recordPositions(list).size());
        findText(root, "全部").performClick(); assertEquals(3, recordPositions(list).size());
    }
}
