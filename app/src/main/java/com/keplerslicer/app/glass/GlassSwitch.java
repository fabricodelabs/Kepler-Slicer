package com.keplerslicer.app.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CompoundButton;
import android.widget.Switch;

/**
 * Figma v3 Switch component (node 2:228): 46x28 track r14; on = #5EE0C8 track with a #12302A knob,
 * off = white 24% track with a white knob; knob r10. Drawn inside a minimum 44dp interactive target.
 */
public final class GlassSwitch extends CompoundButton {
    private static final int ON_TRACK = 0xFF5EE0C8, ON_KNOB = 0xFF12302A, OFF_TRACK = 0x3DFFFFFF, OFF_KNOB = 0xFFFFFFFF;
    private final GlassTokens tokens; private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public GlassSwitch(Context context) { super(context); tokens = GlassTokens.get(context); setButtonDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT)); setClickable(true); setFocusable(true); setMinWidth(tokens.dp(tokens.minTouch)); setMinHeight(tokens.dp(tokens.minTouch)); }
    // The transparent button drawable is not stateful: custom-painted thumb needs its own redraw.
    @Override public void setChecked(boolean checked) { super.setChecked(checked); invalidate(); }
    @Override protected void onMeasure(int width, int height) { int w = Math.max(tokens.dp(tokens.minTouch), tokens.dp(46)), h = Math.max(tokens.dp(tokens.minTouch), tokens.dp(28)); setMeasuredDimension(resolveSize(w, width), resolveSize(h, height)); }
    @Override protected void onDraw(Canvas canvas) {
        float w = tokens.dp(46), h = tokens.dp(28), r = h / 2f, left = (getWidth() - w) / 2f, top = (getHeight() - h) / 2f;
        boolean on = isChecked(); int alpha = isEnabled() ? 255 : 100;
        paint.setColor(on ? ON_TRACK : OFF_TRACK); paint.setAlpha(Math.round(android.graphics.Color.alpha(on ? ON_TRACK : OFF_TRACK) * alpha / 255f)); canvas.drawRoundRect(left, top, left + w, top + h, r, r, paint);
        float knob = tokens.dp(10); boolean atRight = on ^ (getLayoutDirection() == LAYOUT_DIRECTION_RTL);
        float x = atRight ? left + w - tokens.dp(14) : left + tokens.dp(14);
        paint.setColor(on ? ON_KNOB : OFF_KNOB); paint.setAlpha(alpha); canvas.drawCircle(x, top + h / 2, knob, paint);
    }
    @Override public boolean performClick() { boolean ok = super.performClick(); if (ok) performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); return ok; }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) { super.onInitializeAccessibilityNodeInfo(info); info.setClassName(Switch.class.getName()); info.setCheckable(true); info.setChecked(isChecked()); }
}
