package com.orcamobile.app.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.View;

/**
 * Vertical layer slider (Figma Preview node 3:377): 44x150 glass r22, 4dp track,
 * white fill below the handle, 36x28 white r14 handle with the 12sp/800 layer number.
 */
public final class LayerRail extends View {
    public interface Listener { void changed(int progress); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float d;
    private int max = 1, progress;
    private Listener listener;

    public LayerRail(Context context) {
        super(context); d = context.getResources().getDisplayMetrics().density;
        text.setTypeface(GlassTypography.typeface(context, 800)); text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 12, context.getResources().getDisplayMetrics()));
        text.setColor(KeplerUi.ON_ACTIVE); setFocusable(true); setClickable(true);
    }
    public void setListener(Listener l) { listener = l; }
    public void setMax(int value) { max = Math.max(1, value); progress = Math.min(progress, max); invalidate(); }
    public void setProgress(int value) { progress = Math.max(0, Math.min(max, value)); setContentDescription(String.valueOf(progress + 1)); invalidate(); }
    public int getProgress() { return progress; }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(resolveSize(Math.round(44 * d), w), resolveSize(Math.round(150 * d), h)); }
    private float top() { return 13 * d; }
    private float bottom() { return getHeight() - 15 * d; }
    private float handleY() { return bottom() - (bottom() - top()) * progress / (float) max; }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), cx = w / 2f;
        paint.setStyle(Paint.Style.FILL); paint.setColor(KeplerUi.GLASS); rect.set(0, 0, w, h); c.drawRoundRect(rect, 22 * d, 22 * d, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(1, d)); paint.setColor(KeplerUi.GLASS_STROKE); rect.inset(d / 2, d / 2); c.drawRoundRect(rect, 22 * d, 22 * d, paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(0x52FFFFFF); rect.set(cx - 2 * d, top(), cx + 2 * d, bottom()); c.drawRoundRect(rect, 2 * d, 2 * d, paint);
        float y = handleY();
        paint.setColor(KeplerUi.WHITE); rect.set(cx - 2 * d, y, cx + 2 * d, bottom()); c.drawRoundRect(rect, 2 * d, 2 * d, paint);
        float hy = Math.max(14 * d, Math.min(h - 14 * d, y)); rect.set(cx - 18 * d, hy - 14 * d, cx + 18 * d, hy + 14 * d); c.drawRoundRect(rect, 14 * d, 14 * d, paint);
        Paint.FontMetrics m = text.getFontMetrics(); c.drawText(String.valueOf(progress + 1), cx, hy - (m.ascent + m.descent) / 2f, text);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            float f = (bottom() - e.getY()) / Math.max(1, bottom() - top());
            int next = Math.round(Math.max(0, Math.min(1, f)) * max);
            if (next != progress) { setProgress(next); if (listener != null) listener.changed(next); }
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) performClick();
        return super.onTouchEvent(e);
    }
    @Override public boolean performClick() { super.performClick(); return true; }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info); info.setClassName(android.widget.SeekBar.class.getName());
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0, max, progress));
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD); info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }
    @Override public boolean performAccessibilityAction(int action, android.os.Bundle args) {
        int step = action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 1 : action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD ? -1 : 0;
        if (step == 0) return super.performAccessibilityAction(action, args);
        setProgress(progress + step); if (listener != null) listener.changed(progress); sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED); return true;
    }
}
