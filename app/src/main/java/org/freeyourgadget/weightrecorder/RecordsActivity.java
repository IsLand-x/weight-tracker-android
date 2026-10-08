/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecordsActivity extends AppCompatActivity {
    private Ui ui;
    private TextView count;
    private RecordAdapter adapter;
    private int filter;
    private boolean registered;
    private final BroadcastReceiver changed = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); ui = new Ui(this); filter = state == null ? 0 : state.getInt("filter");
        LinearLayout screen = ui.column(); screen.addView(ui.toolbar("全部记录", this::finish, null));
        LinearLayout header = ui.column(); header.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(12));
        MaterialButtonToggleGroup filters = new MaterialButtonToggleGroup(this); filters.setSingleSelection(true); filters.setSelectionRequired(true);
        String[] labels = {"全部", "上午", "下午"};
        for (int i = 0; i < labels.length; i++) {
            MaterialButton button = ui.segment(labels[i], 2100 + i, 14);
            filters.addView(button, new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        }
        filters.check(2100 + filter); header.addView(filters); ui.space(header, 10);
        count = ui.label("", 13, ui.muted); header.addView(count); screen.addView(header);
        ListView list = new ListView(this); list.setDivider(null); list.setClipToPadding(false);
        list.setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(16)); adapter = new RecordAdapter(); list.setAdapter(adapter);
        TextView empty = ui.label("暂无体重记录\n称重后，记录会自动显示在这里", 14, ui.muted);
        empty.setGravity(android.view.Gravity.CENTER); empty.setPadding(ui.dp(24), ui.dp(32), ui.dp(24), ui.dp(32));
        screen.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
        screen.addView(list, new LinearLayout.LayoutParams(-1, 0, 1)); list.setEmptyView(empty);
        list.setOnItemClickListener((parent, view, position, id) -> {
            WeightDatabase.Record r = adapter.getItem(position).record;
            if (r != null) RecordDetails.show(this, r, this::refresh);
        });
        filters.addOnButtonCheckedListener((group, id, checked) -> { if (checked) { filter = id - 2100; refresh(); list.setSelection(0); } });
        ui.install(this, screen);
    }
    private void refresh() {
        List<WeightDatabase.Record> records = new ArrayList<>(WeightDatabase.get(this).all()); Collections.reverse(records);
        adapter.rows.clear(); String previousDay = ""; int visible = 0;
        WeighingPeriods periods = Settings.periods(this);
        for (WeightDatabase.Record r : records) {
            if (filter == 1 && !periods.isMorning(r.timeMillis) || filter == 2 && periods.isMorning(r.timeMillis)) continue;
            String day = Ui.date(r.timeMillis, "yyyy年MM月dd日 EEEE");
            if (!day.equals(previousDay)) { adapter.rows.add(new RecordRow(day, null)); previousDay = day; }
            adapter.rows.add(new RecordRow(day, r)); visible++;
        }
        count.setText("共 " + visible + " 条" + (filter == 1 ? "上午" : filter == 2 ? "下午" : "") + "记录"); adapter.notifyDataSetChanged();
    }
    static final class RecordRow {
        final String day;
        final WeightDatabase.Record record;
        RecordRow(String day, WeightDatabase.Record record) { this.day = day; this.record = record; }
    }
    private final class RecordAdapter extends BaseAdapter {
        final List<RecordRow> rows = new ArrayList<>();
        @Override public int getCount() { return rows.size(); }
        @Override public RecordRow getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) { return getItem(position).record == null ? 0 : 1; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int position) { return getItem(position).record != null; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            RecordRow entry = getItem(position);
            if (entry.record == null) {
                TextView date = convertView instanceof TextView ? (TextView) convertView : ui.label("", 12, ui.muted);
                date.setText(entry.day); date.setPadding(ui.dp(4), ui.dp(16), ui.dp(4), ui.dp(10)); return date;
            }
            LinearLayout item, row;
            if (convertView instanceof LinearLayout) {
                item = (LinearLayout) convertView; row = (LinearLayout) item.getTag(); ui.bindRecordRow(row, entry.record, false);
            } else {
                item = ui.column(); row = ui.recordRow(entry.record, false); row.setBackground(null);
                row.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12)); item.addView(row); item.setTag(row);
                View separator = new View(RecordsActivity.this); separator.setBackgroundColor(ui.line);
                LinearLayout.LayoutParams edge = new LinearLayout.LayoutParams(-1, ui.dp(1));
                edge.setMarginStart(ui.dp(14)); edge.setMarginEnd(ui.dp(14)); item.addView(separator, edge);
            }
            boolean first = position == 0 || getItem(position - 1).record == null;
            boolean last = position == getCount() - 1 || getItem(position + 1).record == null;
            item.getChildAt(1).setVisibility(last ? View.GONE : View.VISIBLE); ui.groupedBackground(item, first, last);
            // ListView handles taps and recycling; descendants must not intercept the row.
            row.setFocusable(false); item.setFocusable(false); item.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            item.setContentDescription(row.getContentDescription()); return item;
        }
    }
    @Override protected void onStart() {
        super.onStart(); ContextCompat.registerReceiver(this, changed, new IntentFilter(getPackageName() + ".CHANGED"), ContextCompat.RECEIVER_NOT_EXPORTED);
        registered = true; refresh();
    }
    @Override protected void onStop() { if (registered) { unregisterReceiver(changed); registered = false; } super.onStop(); }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putInt("filter", filter); }
}
