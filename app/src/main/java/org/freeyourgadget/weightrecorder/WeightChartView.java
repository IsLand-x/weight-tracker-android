/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class WeightChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final List<WeightDatabase.Record> visible = new ArrayList<>();
    private final Date dateValue = new Date();
    private final SimpleDateFormat rangeFormat = new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("MM.dd", Locale.getDefault());
    private final SimpleDateFormat monthFormat = new SimpleDateFormat("yy.MM", Locale.getDefault());
    private final SimpleDateFormat detailFormat = new SimpleDateFormat("MM.dd HH:mm", Locale.getDefault());
    private List<WeightDatabase.Record> records = new ArrayList<>();
    private int period = 7;
    private long end = ChartWindow.dayEnd(System.currentTimeMillis());
    private long gestureEnd;
    private float downX, downY;
    private boolean dragging;
    private WeightDatabase.Record selected;
    private final int accent;
    private final int text, grid;
    public WeightChartView(Context context, int text, int grid, int accent) {
        super(context); this.text = text; this.grid = grid; this.accent = accent; setFocusable(true);
        setContentDescription("体重趋势图，左右拖动查看历史，点击数据点查看体重");
    }
    public void setRecords(List<WeightDatabase.Record> values) { records = values; invalidate(); }
    public void setPeriod(int value) { period = value; end = ChartWindow.dayEnd(System.currentTimeMillis()); selected = null; invalidate(); }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private String format(SimpleDateFormat formatter, long time) { dateValue.setTime(time); return formatter.format(dateValue); }
    private void label(Canvas canvas, String value, float x, float y, int color, float size) {
        paint.setColor(color); paint.setTextSize(dp(size)); paint.setStyle(Paint.Style.FILL); canvas.drawText(value, x, y, paint);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = dp(47), right = getWidth() - dp(16), top = dp(40), bottom = getHeight() - dp(50);
        if (right <= left || bottom <= top) return;
        long start = ChartWindow.start(end, period); double span = Math.max(1, end - start);
        label(canvas, format(rangeFormat, start) + " — " + format(rangeFormat, end), dp(4), dp(19), text, 13);
        visible.clear();
        for (WeightDatabase.Record r : records) if (r.timeMillis >= start && r.timeMillis <= end) visible.add(r);
        if (visible.isEmpty()) {
            label(canvas, records.isEmpty() ? "连接体重秤，开始记录" : "这段时间暂无记录", left, (top + bottom) / 2, text, 15); return;
        }
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (WeightDatabase.Record r : visible) { min = Math.min(min, r.weight); max = Math.max(max, r.weight); }
        double pad = Math.max(0.5, (max - min) * .18); min -= pad; max += pad;
        for (int i = 0; i < 4; i++) {
            float y = top + (bottom - top) * i / 3;
            paint.setColor(grid); paint.setStrokeWidth(dp(1)); canvas.drawLine(left, y, right, y, paint);
            label(canvas, String.format(Locale.getDefault(), "%.1f", max - (max - min) * i / 3), 0, y + dp(4), text, 11);
        }
        path.rewind(); boolean first = true;
        for (WeightDatabase.Record r : visible) {
            float x = left + (float) ((r.timeMillis - start) / span) * (right - left);
            float y = bottom - (float) ((r.weight - min) / (max - min)) * (bottom - top);
            if (first) { path.moveTo(x, y); first = false; } else path.lineTo(x, y);
        }
        paint.setColor(accent); paint.setStrokeWidth(dp(2.5f)); paint.setStyle(Paint.Style.STROKE); canvas.drawPath(path, paint); paint.setStyle(Paint.Style.FILL);
        for (WeightDatabase.Record r : visible) {
            float x = left + (float) ((r.timeMillis - start) / span) * (right - left);
            float y = bottom - (float) ((r.weight - min) / (max - min)) * (bottom - top);
            if (visible.size() <= 50 || r == selected) { paint.setColor(accent); canvas.drawCircle(x, y, dp(r == selected ? 5 : 3), paint); }
        }
        SimpleDateFormat tick = period == 365 ? monthFormat : dayFormat;
        for (int i = 0; i < 3; i++) {
            String value = format(tick, start + (long) (span * i / 2));
            float x = left + (right - left) * i / 2;
            paint.setTextSize(dp(11));
            label(canvas, value, x - (i == 0 ? 0 : i == 2 ? paint.measureText(value) : paint.measureText(value) / 2), bottom + dp(19), text, 11);
        }
        WeightDatabase.Record highlighted = selected != null && visible.contains(selected) ? selected : null;
        if (highlighted != null) label(canvas, String.format(Locale.getDefault(), "%.2f kg · %s", highlighted.weight,
                format(detailFormat, highlighted.timeMillis)), left, bottom + dp(43), accent, 12);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX(); downY = event.getY(); gestureEnd = end; dragging = false; return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX, dy = event.getY() - downY;
                if (!dragging && Math.abs(dx) > ViewConfiguration.get(getContext()).getScaledTouchSlop() && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true; getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging && !records.isEmpty()) {
                    long span = gestureEnd - ChartWindow.start(gestureEnd, period);
                    long earliest = Math.min(ChartWindow.dayEnd(System.currentTimeMillis()), ChartWindow.endForFirstDay(records.get(0).timeMillis, period));
                    end = Math.max(earliest, Math.min(ChartWindow.dayEnd(System.currentTimeMillis()), gestureEnd - (long) (dx / Math.max(dp(30), getWidth() - dp(63)) * span)));
                    selected = null; invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    long start = ChartWindow.start(end, period);
                    long target = start + (long) ((event.getX() - dp(47)) / Math.max(dp(30), getWidth() - dp(63)) * (end - start));
                    selected = null; long distance = Long.MAX_VALUE;
                    for (WeightDatabase.Record r : records) if (r.timeMillis >= start && r.timeMillis <= end && Math.abs(r.timeMillis - target) < distance) {
                        selected = r; distance = Math.abs(r.timeMillis - target);
                    }
                    invalidate(); performClick();
                }
                getParent().requestDisallowInterceptTouchEvent(false); return true;
            case MotionEvent.ACTION_CANCEL: getParent().requestDisallowInterceptTouchEvent(false); return true;
            default: return true;
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
