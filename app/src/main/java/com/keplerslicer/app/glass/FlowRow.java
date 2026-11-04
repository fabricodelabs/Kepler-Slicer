package com.keplerslicer.app.glass;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Wrapping row (Figma flex-wrap) with fixed horizontal and vertical gaps; respects RTL. */
public final class FlowRow extends ViewGroup {
    private final int gapX, gapY;
    public FlowRow(Context context, int gapXPx, int gapYPx) { super(context); gapX = gapXPx; gapY = gapYPx; }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int max = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        if (MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED) max = Integer.MAX_VALUE;
        int x = 0, y = 0, line = 0, widest = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i); if (c.getVisibility() == GONE) continue;
            c.measure(MeasureSpec.makeMeasureSpec(Math.max(0, max), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int w = c.getMeasuredWidth(), h = c.getMeasuredHeight();
            if (x > 0 && x + gapX + w > max) { y += line + gapY; x = 0; line = 0; }
            x += (x > 0 ? gapX : 0) + w; line = Math.max(line, h); widest = Math.max(widest, x);
        }
        setMeasuredDimension(resolveSize(widest + getPaddingLeft() + getPaddingRight(), widthSpec), resolveSize(y + line + getPaddingTop() + getPaddingBottom(), heightSpec));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int max = r - l - getPaddingLeft() - getPaddingRight(); boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int x = 0, y = getPaddingTop(), line = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i); if (c.getVisibility() == GONE) continue;
            int w = c.getMeasuredWidth(), h = c.getMeasuredHeight();
            if (x > 0 && x + gapX + w > max) { y += line + gapY; x = 0; line = 0; }
            if (x > 0) x += gapX;
            int left = rtl ? r - l - getPaddingRight() - x - w : getPaddingLeft() + x;
            c.layout(left, y, left + w, y + h); x += w; line = Math.max(line, h);
        }
    }
}
