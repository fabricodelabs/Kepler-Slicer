package com.keplerslicer.app.glass;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;

/**
 * Backdrop blur for glass surfaces (Figma "glass" = blur + fill + stroke).
 * Android 12+: the sibling content under a floating surface is re-recorded into a blurred RenderNode and drawn
 * beneath the surface's own fill. Older versions keep the plain fill. A SurfaceView (the GL viewport) cannot be
 * captured; there the fill alone shows, as before.
 */
public final class Backdrop {
    private Backdrop() {}

    public static boolean supported() { return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S; }

    /**
     * Returns {@code fill} drawn over a blurred copy of {@code source}, or {@code fill} alone when blur is unavailable.
     * {@code glass} must not be inside {@code source}. Safe to call again whenever the fill is rebuilt.
     */
    public static Drawable behind(View glass, View source, float blurRadiusPx, float cornerRadiusPx, Drawable fill) {
        if (!supported() || glass == null || source == null || isDescendant(glass, source)) return fill;
        BlurDrawable blur = new BlurDrawable(glass, source, blurRadiusPx, cornerRadiusPx);
        if (glass.getTag(com.keplerslicer.app.R.id.kv3_backdrop) == null) { glass.setTag(com.keplerslicer.app.R.id.kv3_backdrop, Boolean.TRUE); listen(glass); }
        return fill == null ? blur : new LayerDrawable(new Drawable[]{blur, fill});
    }

    private static void listen(View glass) {
        // Redraw when what lies underneath moves; content that changes without layout or scroll catches up on the next one.
        ViewTreeObserver.OnScrollChangedListener scroll = glass::invalidate;
        ViewTreeObserver.OnGlobalLayoutListener layout = glass::invalidate;
        glass.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { ViewTreeObserver o = v.getViewTreeObserver(); o.addOnScrollChangedListener(scroll); o.addOnGlobalLayoutListener(layout); }
            @Override public void onViewDetachedFromWindow(View v) { ViewTreeObserver o = v.getViewTreeObserver(); o.removeOnScrollChangedListener(scroll); o.removeOnGlobalLayoutListener(layout); }
        });
        if (glass.isAttachedToWindow()) { ViewTreeObserver o = glass.getViewTreeObserver(); o.addOnScrollChangedListener(scroll); o.addOnGlobalLayoutListener(layout); }
    }

    /** Blurs the activity behind a dialog window (Android 12+, when the device enables cross-window blur). */
    public static void blurBehind(Window window, int radiusPx) {
        if (!supported() || window == null) return;
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        WindowManager.LayoutParams p = window.getAttributes(); p.setBlurBehindRadius(radiusPx); window.setAttributes(p);
    }

    private static boolean isDescendant(View v, View ancestor) {
        for (Object p = v; p instanceof View; p = ((View) p).getParent()) if (p == ancestor) return true;
        return false;
    }

    private static final class BlurDrawable extends Drawable {
        private final View glass, source; private final float corner; private final RenderNode node = new RenderNode("kepler-backdrop");
        private final int[] g = new int[2], s = new int[2]; private final Path clip = new Path(); private final RectF r = new RectF();
        private boolean drawing;

        BlurDrawable(View glass, View source, float radius, float corner) {
            this.glass = glass; this.source = source; this.corner = corner;
            node.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
        }

        @Override public void draw(Canvas canvas) {
            int w = glass.getWidth(), h = glass.getHeight();
            if (drawing || w <= 0 || h <= 0 || !canvas.isHardwareAccelerated() || source.getWidth() <= 0) return;
            drawing = true;
            try {
                glass.getLocationInWindow(g); source.getLocationInWindow(s);
                node.setPosition(0, 0, w, h);
                Canvas rec = node.beginRecording(w, h);
                try { record(rec); } finally { node.endRecording(); }
                r.set(0, 0, w, h); clip.reset(); clip.addRoundRect(r, corner, corner, Path.Direction.CW);
                int save = canvas.save(); canvas.clipPath(clip); canvas.drawRenderNode(node); canvas.restoreToCount(save);
            } finally { drawing = false; }
        }

        /** Window background, then every sibling of the glass in its parent (the glass itself is skipped). */
        private void record(Canvas rec) {
            int[] c = new int[2];
            Drawable windowBg = glass.getRootView().getBackground();
            if (windowBg != null) { int save = rec.save(); rec.translate(-g[0], -g[1]); windowBg.draw(rec); rec.restoreToCount(save); }
            if (!(glass.getParent() instanceof android.view.ViewGroup)) { int save = rec.save(); rec.translate(s[0] - g[0], s[1] - g[1]); source.draw(rec); rec.restoreToCount(save); return; }
            android.view.ViewGroup host = (android.view.ViewGroup) glass.getParent();
            for (int i = 0; i < host.getChildCount(); i++) {
                View child = host.getChildAt(i);
                if (child == glass || child.getVisibility() != View.VISIBLE || child.getWidth() <= 0) continue;
                child.getLocationInWindow(c);
                int save = rec.save(); rec.translate(c[0] - g[0], c[1] - g[1]); child.draw(rec); rec.restoreToCount(save);
            }
        }

        @Override public void getOutline(Outline outline) { outline.setRoundRect(getBounds(), corner); }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
