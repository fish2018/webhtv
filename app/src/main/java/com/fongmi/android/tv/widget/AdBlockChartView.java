package com.fongmi.android.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Lightweight horizontal bar chart used by the ad-block statistics dialog. */
public class AdBlockChartView extends View {

    private static final int BAR_COLOR = Color.rgb(33, 150, 243);
    private static final int TRACK_COLOR = Color.argb(28, 0, 0, 0);
    private static final int TEXT_COLOR = Color.rgb(60, 64, 67);

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Entry> entries = Collections.emptyList();

    public AdBlockChartView(Context context) {
        this(context, null);
    }

    public AdBlockChartView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AdBlockChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        barPaint.setColor(BAR_COLOR);
        trackPaint.setColor(TRACK_COLOR);
        textPaint.setColor(TEXT_COLOR);
        textPaint.setTextSize(dp(13));
    }

    public void setEntries(List<Entry> entries) {
        this.entries = entries == null ? Collections.emptyList() : new ArrayList<>(entries);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int rows = Math.max(1, entries.size());
        int desiredHeight = getPaddingTop() + getPaddingBottom() + rows * dp(48);
        setMeasuredDimension(resolveSize(dp(280), widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (entries.isEmpty()) return;
        long maximum = 1;
        for (Entry entry : entries) maximum = Math.max(maximum, entry.value);
        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float rowHeight = dp(48);
        float barTopOffset = dp(24);
        float barHeight = dp(12);
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            float top = getPaddingTop() + index * rowHeight;
            String value = String.valueOf(entry.value);
            canvas.drawText(entry.label, left, top + dp(15), textPaint);
            float valueWidth = textPaint.measureText(value);
            canvas.drawText(value, right - valueWidth, top + dp(15), textPaint);
            float barTop = top + barTopOffset;
            canvas.drawRoundRect(left, barTop, right, barTop + barHeight, barHeight / 2, barHeight / 2, trackPaint);
            float fraction = entry.value / (float) maximum;
            canvas.drawRoundRect(left, barTop, left + (right - left) * fraction, barTop + barHeight,
                    barHeight / 2, barHeight / 2, barPaint);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public static final class Entry {
        public final String label;
        public final long value;

        public Entry(String label, long value) {
            this.label = label == null ? "" : label;
            this.value = Math.max(0, value);
        }
    }
}
