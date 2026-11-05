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
        Glow glow = new Glow(radiusPx, v.getResources().getDisplayMetrics().density, v, resting);
        v.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, orr, ob) -> {
            if (r - l != orr - ol || b - t != ob - ot) view.setStateListAnimator(flex(view, r - l));
        });
        v.setStateListAnimator(flex(v, v.getWidth()));
        // Coming back to a screen must never show a press that was in flight when it was left.
        v.getViewTreeObserver().addOnWindowFocusChangeListener(focus -> { if (focus && !v.isPressed()) { glow.settle(); v.setScaleX(1f); v.setScaleY(1f); } });
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { glow.settle(); unclip(view); }
            @Override public void onViewDetachedFromWindow(View view) { glow.settle(); view.setScaleX(1f); view.setScaleY(1f); }
        });
        return resting == null ? glow : new LayerDrawable(new Drawable[]{resting, glow});
    }

    /** The swelling control must not be cut by its row or card: lift clipping on the two nearest non-scrolling parents. */
    private static void unclip(View v) {
        android.view.ViewParent p = v.getParent();
        for (int i = 0; i < 2 && p instanceof android.view.ViewGroup; i++, p = p.getParent()) {
            if (p instanceof android.widget.ScrollView || p instanceof android.widget.HorizontalScrollView || p instanceof android.widget.AbsListView) break;
            ((android.view.ViewGroup) p).setClipChildren(false);
            ((android.view.ViewGroup) p).setClipToPadding(false);
        }
    }

    /** Inside a container that clips to its rounded outline (cards, segmented controls) a swell would be cut. */
    private static boolean insideOutlineClip(View v) {
        android.view.ViewParent p = v.getParent();
        for (int i = 0; i < 4 && p instanceof View; i++, p = p.getParent()) if (((View) p).getClipToOutline()) return true;
        // A segment or a button that fills its own capsule track (segmented control, toolbar) would grow over its
        // neighbours and the track edge: it only lights up.
        if (v.getParent() instanceof View) {
            View parent = (View) v.getParent();
            int inner = parent.getHeight() - parent.getPaddingTop() - parent.getPaddingBottom();
            if (parent.getBackground() != null && v.getHeight() > 0 && v.getHeight() >= inner - 2) return true;
        }
        return false;
    }

    /** Buttons and chips swell; wide rows and cards only light up (scaling them would push past the screen edge). */
    private static StateListAnimator flex(View v, int widthPx) {
        float density = v.getResources().getDisplayMetrics().density;
        if (widthPx > 140 * density || insideOutlineClip(v)) return null;
        float grow = widthPx <= 0 ? 1.05f : 1f + Math.max(0.03f, Math.min(0.08f, 8f * density / widthPx));
        StateListAnimator s = new StateListAnimator();
        ObjectAnimator down = ObjectAnimator.ofPropertyValuesHolder(v, PropertyValuesHolder.ofFloat(View.SCALE_X, grow), PropertyValuesHolder.ofFloat(View.SCALE_Y, grow));
        Spring press = Spring.smooth(0.22f), release = Spring.bouncy(0.45f);
        down.setDuration(press.duration()); down.setInterpolator(press);
        ObjectAnimator up = ObjectAnimator.ofPropertyValuesHolder(v, PropertyValuesHolder.ofFloat(View.SCALE_X, 1f), PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f));
        up.setDuration(release.duration()); up.setInterpolator(release);
        s.addState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled}, down);
        s.addState(new int[0], up);
        return s;
    }

    /** Radial light around the touch point, clipped to the rounded shape, fading in on press and out on release. */
    private static final class Glow extends Drawable {
        private final float radius; private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path(); private final RectF r = new RectF();
        private float hx = -1, hy = -1, level; private boolean pressed; private ValueAnimator anim;

        private final float density; private final View view; private final Drawable resting; private final Outline shape = new Outline();
        Glow(float radius, float density, View view, Drawable resting) { this.radius = radius; this.density = density; this.view = view; this.resting = resting; rim.setStyle(Paint.Style.STROKE); }

        /**
         * Where the light goes: a control with its own glass shape lights up exactly inside that shape; a list cell
         * inside a card gets an inset highlight with corners concentric to the card; a bare row uses its radius.
         */
        private float shape(android.graphics.Rect b) {
            r.set(b);
            if (resting != null) {
                resting.setBounds(b); shape.setEmpty(); resting.getOutline(shape);
                float rr = shape.getRadius();
                return rr >= 0 ? rr : radius;
            }
            boolean inCard = view.getParent() instanceof View && ((View) view.getParent()).getBackground() != null;
            if (inCard && b.width() > 140 * density) { r.inset(4 * density, 4 * density); return Math.min(14 * density, Math.min(r.width(), r.height()) / 2f); }
            if (b.width() <= 140 * density && b.height() <= 72 * density) return Math.min(b.width(), b.height()) / 2f;
            return Math.min(radius, Math.min(b.width(), b.height()) / 2f);
        }

        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] state) {
            boolean p = false; for (int s : state) if (s == android.R.attr.state_pressed) p = true;
            if (p == pressed) return false;
            pressed = p; fade(p ? 1f : 0f, p ? Spring.smooth(0.18f) : Spring.smooth(0.4f)); return true;
        }
        @Override public void setHotspot(float x, float y) { hx = x; hy = y; invalidateSelf(); }
        @Override public void jumpToCurrentState() { settle(); }
        void settle() { if (anim != null) anim.cancel(); level = pressed ? 1f : 0f; invalidateSelf(); }

        private void fade(float to, Spring spring) {
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(level, to); anim.setDuration(spring.duration()); anim.setInterpolator(spring);
            anim.addUpdateListener(a -> { level = (float) a.getAnimatedValue(); invalidateSelf(); });
            anim.start();
        }

        @Override public void draw(Canvas c) {
            if (level <= 0.001f) return;
            android.graphics.Rect b = getBounds(); if (b.isEmpty()) return;
            float x = hx < 0 ? b.exactCenterX() : hx, y = hy < 0 ? b.exactCenterY() : hy;
            float reach = Math.max(b.width(), b.height()) * 0.9f;
            float rr = shape(b);
            clip.reset(); clip.addRoundRect(r, rr, rr, Path.Direction.CW);
            int save = c.save(); c.clipPath(clip);
            paint.setShader(null); paint.setColor(alpha(0x1C, level)); c.drawRect(b, paint);
            paint.setShader(new RadialGradient(x, y, reach, new int[]{alpha(0x40, level), alpha(0x12, level), 0}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(b, paint);
            c.restoreToCount(save);
        }
        private static int alpha(int a, float k) { return (Math.round(a * k) << 24) | 0xFFFFFF; }
        @Override public void getOutline(Outline o) { android.graphics.Rect b = getBounds(); float rr = shape(b); o.setRoundRect(Math.round(r.left), Math.round(r.top), Math.round(r.right), Math.round(r.bottom), rr); }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
