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
    private final SimpleDateFormat detailFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault());
    private List<WeightDatabase.Record> records = new ArrayList<>();
    private int period = 7;
    private long end = ChartWindow.dayEnd(System.currentTimeMillis());
    private long gestureEnd;
    private float downX, downY;
    private boolean dragging, browseHistory;
    private WeightDatabase.Record selected;
    private final int morningColor, afternoonColor, text, grid;

    public WeightChartView(Context context, int text, int grid, int morningColor, int afternoonColor) {
        super(context); this.text = text; this.grid = grid;
        this.morningColor = morningColor; this.afternoonColor = afternoonColor; setFocusable(true);
        updateDescription();
    }
    public void setRecords(List<WeightDatabase.Record> values) {
        records = values;
        if (selected != null) {
            long id = selected.id; selected = null;
            for (WeightDatabase.Record r : values) if (r.id == id) { selected = r; break; }
        }
        updateDescription(); invalidate();
    }
    public void setPeriod(int value) {
        period = value; end = ChartWindow.dayEnd(System.currentTimeMillis()); selected = null;
        updateDescription(); invalidate();
    }
    public void setBrowseHistory(boolean value) { browseHistory = value; updateDescription(); }
    private void updateDescription() {
        setContentDescription(selected == null ? "上午和下午体重趋势图，" + (browseHistory ? "左右拖动查看历史，点击查看数据" : "点击或滑动查看数据")
                : "体重趋势，" + Settings.periods(getContext()).label(selected.timeMillis) + "，" + format(detailFormat, selected.timeMillis) + "，" + selected.weight + "公斤");
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private String format(SimpleDateFormat formatter, long time) { dateValue.setTime(time); return formatter.format(dateValue); }
    private void label(Canvas canvas, String value, float x, float y, int color, float size) {
        paint.setColor(color); paint.setTextSize(dp(size)); paint.setStyle(Paint.Style.FILL); canvas.drawText(value, x, y, paint);
    }
    private float left() { return dp(47); }
    private float right() { return getWidth() - dp(16); }
    private float top() { return dp(40); }
    private float bottom() { return getHeight() - dp(70); }
    private List<WeightDatabase.Record> windowRecords() {
        long start = ChartWindow.start(end, period); List<WeightDatabase.Record> result = new ArrayList<>();
        for (WeightDatabase.Record r : records) if (r.timeMillis >= start && r.timeMillis <= end) result.add(r);
        return result;
    }
    private double[] bounds(List<WeightDatabase.Record> values) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (WeightDatabase.Record r : values) { min = Math.min(min, r.weight); max = Math.max(max, r.weight); }
        double pad = Math.max(0.5, (max - min) * .18); return new double[]{min - pad, max + pad};
    }
    private float x(WeightDatabase.Record r) {
        long start = ChartWindow.start(end, period);
        return left() + (float) ((r.timeMillis - start) / (double) Math.max(1, end - start)) * (right() - left());
    }
    private float y(WeightDatabase.Record r, double[] bounds) {
        return bottom() - (float) ((r.weight - bounds[0]) / (bounds[1] - bounds[0])) * (bottom() - top());
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (right() <= left() || bottom() <= top()) return;
        long start = ChartWindow.start(end, period); double span = Math.max(1, end - start);
        label(canvas, format(rangeFormat, start) + " — " + format(rangeFormat, end), dp(4), dp(19), text, 13);
        visible.clear(); visible.addAll(windowRecords());
        if (visible.isEmpty()) {
            label(canvas, records.isEmpty() ? "连接体重秤，开始记录" : "这段时间暂无记录", left(), (top() + bottom()) / 2, text, 15); return;
        }
        double[] bounds = bounds(visible); WeighingPeriods periods = Settings.periods(getContext());
        for (int i = 0; i < 4; i++) {
            float y = top() + (bottom() - top()) * i / 3;
            paint.setColor(grid); paint.setStrokeWidth(dp(1)); canvas.drawLine(left(), y, right(), y, paint);
            label(canvas, String.format(Locale.getDefault(), "%.1f", bounds[1] - (bounds[1] - bounds[0]) * i / 3), 0, y + dp(4), text, 11);
        }
        // Separate paths ensure readings in different periods are never connected.
        for (boolean morning : new boolean[]{true, false}) {
            path.rewind(); boolean first = true;
            for (WeightDatabase.Record r : visible) {
                if (periods.isMorning(r.timeMillis) != morning) continue;
                if (first) { path.moveTo(x(r), y(r, bounds)); first = false; } else path.lineTo(x(r), y(r, bounds));
            }
            paint.setColor(morning ? morningColor : afternoonColor); paint.setStrokeWidth(dp(2.5f));
            paint.setStyle(Paint.Style.STROKE); canvas.drawPath(path, paint); paint.setStyle(Paint.Style.FILL);
        }
        for (WeightDatabase.Record r : visible) {
            boolean morning = periods.isMorning(r.timeMillis);
            if (visible.size() <= 50 || r == selected) {
                float x = x(r), y = y(r, bounds), radius = dp(r == selected ? 5 : 3);
                paint.setColor(morning ? morningColor : afternoonColor);
                if (morning) canvas.drawCircle(x, y, radius, paint);
                else {
                    path.rewind(); path.moveTo(x, y - radius); path.lineTo(x + radius, y);
                    path.lineTo(x, y + radius); path.lineTo(x - radius, y); path.close(); canvas.drawPath(path, paint);
                }
            }
        }
        SimpleDateFormat tick = period == 365 ? monthFormat : dayFormat;
        for (int i = 0; i < 3; i++) {
            String value = format(tick, start + (long) (span * i / 2)); float x = left() + (right() - left()) * i / 2;
            paint.setTextSize(dp(11));
            label(canvas, value, x - (i == 0 ? 0 : i == 2 ? paint.measureText(value) : paint.measureText(value) / 2), bottom() + dp(19), text, 11);
        }
        if (selected != null && visible.contains(selected)) {
            float x = x(selected); int color = periods.isMorning(selected.timeMillis) ? morningColor : afternoonColor;
            paint.setColor(color); paint.setStrokeWidth(dp(1)); canvas.drawLine(x, top(), x, bottom(), paint);
            label(canvas, String.format(Locale.getDefault(), "%s · %.2f kg", periods.label(selected.timeMillis), selected.weight), left(), bottom() + dp(42), color, 13);
            label(canvas, format(detailFormat, selected.timeMillis), left(), bottom() + dp(61), text, 12);
        } else label(canvas, "点击或滑动查看称重时间和体重", left(), bottom() + dp(45), text, 12);
    }
    private void selectAt(float touchX, float touchY) {
        List<WeightDatabase.Record> values = windowRecords(); selected = null;
        if (!values.isEmpty() && right() > left() && bottom() > top()) {
            double[] bounds = bounds(values); double best = Double.MAX_VALUE;
            for (WeightDatabase.Record r : values) {
                double dx = x(r) - touchX, dy = y(r, bounds) - touchY, distance = dx * dx + dy * dy;
                if (distance < best) { selected = r; best = distance; }
            }
        }
        updateDescription(); invalidate();
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX(); downY = event.getY(); gestureEnd = end; dragging = false;
                if (!browseHistory) selectAt(downX, downY);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX, dy = event.getY() - downY;
                if (!browseHistory) selectAt(event.getX(), event.getY());
                if (!dragging && Math.abs(dx) > ViewConfiguration.get(getContext()).getScaledTouchSlop() && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true; if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    if (browseHistory && !records.isEmpty()) {
                        long span = gestureEnd - ChartWindow.start(gestureEnd, period);
                        long earliest = Math.min(ChartWindow.dayEnd(System.currentTimeMillis()), ChartWindow.endForFirstDay(records.get(0).timeMillis, period));
                        end = Math.max(earliest, Math.min(ChartWindow.dayEnd(System.currentTimeMillis()), gestureEnd - (long) (dx / Math.max(dp(30), right() - left()) * span)));
                        selected = null; updateDescription(); invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging || !browseHistory) { selectAt(event.getX(), event.getY()); performClick(); }
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default: return true;
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
