package com.keplerslicer.app.glass;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

/**
 * Liquid Glass touch response: the glass lights up from within around the finger and flexes (swells a little,
 * springs back on release) instead of a flat ripple.
 */
public final class LiquidPress {
    private LiquidPress() {}

    /** Background with the inner glow over {@code resting}; also installs the flex animation on {@code v}. */
    public static Drawable apply(View v, Drawable resting, float radiusPx) {
        Glow glow = new Glow(radiusPx);
        v.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, orr, ob) -> {
            if (r - l != orr - ol || b - t != ob - ot) view.setStateListAnimator(flex(view, r - l));
        });
        v.setStateListAnimator(flex(v, v.getWidth()));
        return resting == null ? glow : new LayerDrawable(new Drawable[]{resting, glow});
    }

    /** Small controls swell more, wide rows barely (a full-width row growing 6% would look like a jump). */
    private static StateListAnimator flex(View v, int widthPx) {
        float density = v.getResources().getDisplayMetrics().density;
        float grow = widthPx <= 0 ? 1.04f : 1f + Math.max(0.012f, Math.min(0.07f, 6f * density / widthPx * 2f));
        StateListAnimator s = new StateListAnimator();
        ObjectAnimator down = ObjectAnimator.ofPropertyValuesHolder(v, PropertyValuesHolder.ofFloat(View.SCALE_X, grow), PropertyValuesHolder.ofFloat(View.SCALE_Y, grow));
        down.setDuration(160); down.setInterpolator(new PathInterpolator(0.2f, 0.9f, 0.3f, 1f));
        ObjectAnimator up = ObjectAnimator.ofPropertyValuesHolder(v, PropertyValuesHolder.ofFloat(View.SCALE_X, 1f), PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f));
        up.setDuration(420); up.setInterpolator(new OvershootInterpolator(2.4f));
        s.addState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled}, down);
        s.addState(new int[0], up);
        return s;
    }

    /** Radial light around the touch point, clipped to the rounded shape, fading in on press and out on release. */
    private static final class Glow extends Drawable {
        private final float radius; private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path(); private final RectF r = new RectF();
        private float hx = -1, hy = -1, level; private boolean pressed; private ValueAnimator anim;

        Glow(float radius) { this.radius = radius; rim.setStyle(Paint.Style.STROKE); }

        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] state) {
            boolean p = false; for (int s : state) if (s == android.R.attr.state_pressed) p = true;
            if (p == pressed) return false;
            pressed = p; fade(p ? 1f : 0f, p ? 140 : 380); return true;
        }
        @Override public void setHotspot(float x, float y) { hx = x; hy = y; invalidateSelf(); }

        private void fade(float to, long ms) {
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(level, to); anim.setDuration(ms);
            anim.addUpdateListener(a -> { level = (float) a.getAnimatedValue(); invalidateSelf(); });
            anim.start();
        }

        @Override public void draw(Canvas c) {
            if (level <= 0.001f) return;
            android.graphics.Rect b = getBounds(); if (b.isEmpty()) return;
            float x = hx < 0 ? b.exactCenterX() : hx, y = hy < 0 ? b.exactCenterY() : hy;
            float reach = Math.max(b.width(), b.height()) * 0.9f;
            paint.setShader(new RadialGradient(x, y, reach, new int[]{alpha(0x55, level), alpha(0x1A, level), 0}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            float rr = Math.min(radius, Math.min(b.width(), b.height()) / 2f);
            r.set(b); clip.reset(); clip.addRoundRect(r, rr, rr, Path.Direction.CW);
            int save = c.save(); c.clipPath(clip); c.drawRect(b, paint); c.restoreToCount(save);
            float sw = Math.max(1f, b.height() / 40f); rim.setStrokeWidth(sw); rim.setColor(alpha(0x66, level));
            r.inset(sw / 2f, sw / 2f); c.drawRoundRect(r, rr, rr, rim);
        }
        private static int alpha(int a, float k) { return (Math.round(a * k) << 24) | 0xFFFFFF; }
        @Override public void getOutline(Outline o) { o.setRoundRect(getBounds(), radius); }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
