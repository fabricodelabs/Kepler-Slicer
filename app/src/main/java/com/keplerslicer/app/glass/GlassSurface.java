package com.keplerslicer.app.glass;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;

/** Surface fill/stroke and an optional, isolated blur layer for the static workspace background. */
public final class GlassSurface {
    private GlassSurface(){}
    public static Drawable of(Context context,float radiusDp,boolean reduceTransparency){
        if(!Float.isFinite(radiusDp)||radiusDp<0)throw new IllegalArgumentException("Invalid surface radius");
        GlassTokens t=GlassTokens.get(context);GradientDrawable d=new GradientDrawable();d.setColor(reduceTransparency?t.reduceTransparencyFill:t.glassFill);d.setStroke(Math.max(1,t.dp(1)),t.glassStroke);d.setCornerRadius(t.dp(radiusDp));return d;
    }
    public static Drawable of(Context context,float radiusDp){return of(context,radiusDp,false);}
    /**
     * Add as a non-interactive sibling behind the tinted surface and all text.
     * On API 31+ this blurs a correctly positioned copy of GlassBackground,
     * not arbitrary dynamic content behind the view. Older Android, reduced
     * transparency and software rendering deliberately show the fill only.
     */
    public static View backdrop(Context context,View backgroundAnchor,float radiusDp,boolean reduceTransparency){
        if(backgroundAnchor==null||!Float.isFinite(radiusDp)||radiusDp<0)throw new IllegalArgumentException("Invalid backdrop anchor or radius");
        return new Backdrop(context,backgroundAnchor,radiusDp,reduceTransparency);
    }
    private static final class Backdrop extends View {
        private final View anchor;private final GlassBackground background;private final int[] anchorXY=new int[2],myXY=new int[2];private final boolean enabled;private final float corner;
        private int lastX=Integer.MIN_VALUE,lastY,lastAnchorX,lastAnchorY,lastW,lastH;
        private final ViewTreeObserver.OnPreDrawListener preDraw=this::beforeDraw;
        private boolean beforeDraw(){if(enabled){getLocationOnScreen(myXY);anchor.getLocationOnScreen(anchorXY);if(myXY[0]!=lastX||myXY[1]!=lastY||anchorXY[0]!=lastAnchorX||anchorXY[1]!=lastAnchorY||anchor.getWidth()!=lastW||anchor.getHeight()!=lastH){lastX=myXY[0];lastY=myXY[1];lastAnchorX=anchorXY[0];lastAnchorY=anchorXY[1];lastW=anchor.getWidth();lastH=anchor.getHeight();invalidate();}}return true;}
        Backdrop(Context c,View anchor,float radiusDp,boolean reduce){super(c);this.anchor=anchor;background=GlassBackground.create(c);GlassTokens t=GlassTokens.get(c);corner=t.dp(radiusDp);enabled=!reduce&&Build.VERSION.SDK_INT>=31;setWillNotDraw(!enabled);setClickable(false);setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,Outline o){o.setRoundRect(0,0,v.getWidth(),v.getHeight(),corner);}});setClipToOutline(true);if(enabled)enableBlur(this,t.dp(t.blurRadius));}
        @TargetApi(31) private static void enableBlur(View view,float radius){view.setRenderEffect(RenderEffect.createBlurEffect(radius,radius,Shader.TileMode.CLAMP));}
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(enabled)getViewTreeObserver().addOnPreDrawListener(preDraw);}
        @Override protected void onDetachedFromWindow(){if(enabled&&getViewTreeObserver().isAlive())getViewTreeObserver().removeOnPreDrawListener(preDraw);super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas canvas){if(!enabled||!isHardwareAccelerated()||anchor.getWidth()<=0||anchor.getHeight()<=0)return;getLocationOnScreen(myXY);anchor.getLocationOnScreen(anchorXY);int save=canvas.save();canvas.translate(anchorXY[0]-myXY[0],anchorXY[1]-myXY[1]);background.setBounds(0,0,anchor.getWidth(),anchor.getHeight());background.draw(canvas);canvas.restoreToCount(save);}
    }
}
