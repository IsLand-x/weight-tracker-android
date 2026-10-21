/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import java.util.Locale;

/** A 240 degree dial. The numeric readouts and needle share the same animation frame. */
public final class WeightGaugeView extends View {
    interface OnDisplayWeight { void changed(double weight); }
    private final Ui ui;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private OnDisplayWeight listener;
    private double displayedWeight;
    private int maximum = 110;
    private boolean stable;

    public WeightGaugeView(Context context) {
        super(context);
        ui = new Ui(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void setOnDisplayWeight(OnDisplayWeight listener) { this.listener = listener; }
    void setMaximum(int maximum) { this.maximum = maximum; invalidate(); }
    int getMaximum() { return maximum; }
    double getDisplayedWeight() { return displayedWeight; }
    void setWeight(double weight, boolean stable, boolean animate) {
        if (!Double.isFinite(weight) || weight < 0) return;
        this.stable = stable;
        if (animator != null) animator.cancel();
        if (!animate || Math.abs(displayedWeight - weight) < .005) {
            display(weight);
            return;
        }
        animator = ValueAnimator.ofFloat((float) displayedWeight, (float) weight);
        animator.setDuration(280);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> display((float) value.getAnimatedValue()));
        animator.start();
    }
    private void display(double weight) {
        displayedWeight = weight;
        if (listener != null) listener.changed(weight);
        invalidate();
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = resolveSize(ui.dp(300), widthSpec);
        setMeasuredDimension(width, resolveSize(Math.round(width * .9f), heightSpec));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() * .51f;
        float radius = Math.min(getWidth() * .44f, getHeight() * .48f);
        RectF arc = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(ui.dp(6)); paint.setColor(ui.line);
        canvas.drawArc(arc, 150, 240, false, paint);
        float fraction = (float) Math.max(0, Math.min(1, displayedWeight / maximum));
        paint.setColor(ui.primary);
        if (fraction > 0) canvas.drawArc(arc, 150, fraction * 240, false, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        int divisions = maximum == 110 ? 55 : 50;
        for (int i = 0; i <= divisions; i++) {
            double angle = Math.toRadians(150 + 240f * i / divisions);
            boolean major = i % 5 == 0;
            float outer = radius - ui.dp(10), inner = outer - ui.dp(major ? 12 : 6);
            paint.setColor(major ? ui.muted : ui.line); paint.setStrokeWidth(ui.dp(major ? 2 : 1));
            canvas.drawLine(cx + (float) Math.cos(angle) * inner, cy + (float) Math.sin(angle) * inner,
                    cx + (float) Math.cos(angle) * outer, cy + (float) Math.sin(angle) * outer, paint);
            if (major) {
                paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(ui.dp(11)); paint.setTypeface(Typeface.DEFAULT);
                float labelRadius = inner - ui.dp(14);
                String label = Integer.toString(Math.round(maximum * (float) i / divisions));
                canvas.drawText(label, cx + (float) Math.cos(angle) * labelRadius,
                        cy + (float) Math.sin(angle) * labelRadius - (paint.ascent() + paint.descent()) / 2, paint);
                paint.setStyle(Paint.Style.STROKE);
            }
        }
        double angle = Math.toRadians(150 + fraction * 240);
        paint.setColor(ui.primary); paint.setStrokeWidth(ui.dp(3)); paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(cx - (float) Math.cos(angle) * ui.dp(10), cy - (float) Math.sin(angle) * ui.dp(10),
                cx + (float) Math.cos(angle) * (radius - ui.dp(43)),
                cy + (float) Math.sin(angle) * (radius - ui.dp(43)), paint);
        paint.setStyle(Paint.Style.FILL); canvas.drawCircle(cx, cy, ui.dp(7), paint);
        paint.setColor(ui.surface); canvas.drawCircle(cx, cy, ui.dp(3), paint);
        paint.setColor(stable ? ui.primary : ui.text); paint.setTextSize(ui.dp(28));
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        canvas.drawText(String.format(Locale.getDefault(), "%.2f", displayedWeight), cx, cy + radius * .58f, paint);
        paint.setColor(ui.muted); paint.setTextSize(ui.dp(12)); paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText("kg", cx, cy + radius * .58f + ui.dp(20), paint);
    }
    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
