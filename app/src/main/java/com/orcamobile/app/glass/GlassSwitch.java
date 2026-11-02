package com.orcamobile.app.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.view.SoundEffectConstants;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CompoundButton;
import android.widget.Switch;

/** 42×24 token switch in a minimum 44dp interactive target. */
public final class GlassSwitch extends CompoundButton {
    private final GlassTokens tokens; private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public GlassSwitch(Context context){super(context);tokens=GlassTokens.get(context);setButtonDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));setClickable(true);setFocusable(true);setMinWidth(tokens.dp(tokens.minTouch));setMinHeight(tokens.dp(tokens.minTouch));}
    // The transparent button drawable is not stateful: custom-painted thumb needs its own redraw.
    @Override public void setChecked(boolean checked){super.setChecked(checked);invalidate();}
    @Override protected void onMeasure(int width,int height){int w=Math.max(tokens.dp(tokens.minTouch),tokens.dp(tokens.toggleWidth)),h=Math.max(tokens.dp(tokens.minTouch),tokens.dp(tokens.toggleHeight));setMeasuredDimension(resolveSize(w,width),resolveSize(h,height));}
    @Override protected void onDraw(Canvas canvas){float w=tokens.dp(tokens.toggleWidth),h=tokens.dp(tokens.toggleHeight),r=h/2f,left=(getWidth()-w)/2f,top=(getHeight()-h)/2f;
        paint.setColor(isEnabled()?(isChecked()?tokens.toggleOn:tokens.toggleOff):android.graphics.Color.argb(100,255,255,255));canvas.drawRoundRect(left,top,left+w,top+h,r,r,paint);
        float knob=tokens.dp(tokens.toggleKnob),pad=(h-knob)/2f;boolean atRight=isChecked()^(getLayoutDirection()==LAYOUT_DIRECTION_RTL);float x=atRight?left+w-pad-knob/2:left+pad+knob/2;
        paint.setColor(isEnabled()?android.graphics.Color.WHITE:0xffb6b7b8);canvas.drawCircle(x,top+h/2,knob/2,paint);
    }
    @Override public boolean performClick(){boolean ok=super.performClick();if(ok)performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);return ok;}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(Switch.class.getName());info.setCheckable(true);info.setChecked(isChecked());}
}
