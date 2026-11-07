package com.keplerslicer.app.glass;

import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Grows the touch area of a small control to at least 48dp without changing how it looks. Several controls may share
 * one parent, so the parent gets a single delegate that forwards to each of them.
 */
public final class TouchTarget {
    private TouchTarget() {}

    public static void ensure(View view) {
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> v.post(() -> attach(v)));
    }

    private static void attach(View view) {
        if (!(view.getParent() instanceof ViewGroup) || !view.isAttachedToWindow()) return;
        ViewGroup parent = (ViewGroup) view.getParent();
        int min = Math.round(48 * view.getResources().getDisplayMetrics().density);
        Rect hit = new Rect();
        view.getHitRect(hit);
        if (hit.width() >= min && hit.height() >= min) return;
        int dx = Math.max(0, min - hit.width()) / 2 + 1, dy = Math.max(0, min - hit.height()) / 2 + 1;
        hit.inset(-dx, -dy);
        Composite composite = parent.getTouchDelegate() instanceof Composite ? (Composite) parent.getTouchDelegate() : new Composite(parent);
        composite.put(view, hit);
        parent.setTouchDelegate(composite);
    }

    /**
     * Container whose full-size child (the 3D view) would otherwise take every touch: taps in the grown area of a
     * small control go to that control first.
     */
    public static class Host extends android.widget.FrameLayout {
        private boolean routing;
        public Host(android.content.Context c) { super(c); }
        @Override public boolean dispatchTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) routing = getTouchDelegate() instanceof Composite && ((Composite) getTouchDelegate()).claims(e.getX(), e.getY());
            if (routing) {
                boolean handled = getTouchDelegate().onTouchEvent(e);
                int a = e.getActionMasked(); if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) routing = false;
                return handled;
            }
            return super.dispatchTouchEvent(e);
        }
    }

    private static final class Composite extends TouchDelegate {
        private final List<View> views = new ArrayList<>();
        private final List<TouchDelegate> delegates = new ArrayList<>();
        private TouchDelegate active;

        Composite(View parent) { super(new Rect(), parent); }

        private final List<Rect> grown = new ArrayList<>();

        /** True when the point is in a grown area but outside the control itself (inside, normal dispatch works). */
        boolean claims(float x, float y) {
            for (int i = 0; i < views.size(); i++) {
                View v = views.get(i); if (!v.isShown() || !v.isEnabled()) continue;
                Rect own = new Rect(); v.getHitRect(own);
                if (grown.get(i).contains((int) x, (int) y) && !own.contains((int) x, (int) y)) return true;
            }
            return false;
        }

        void put(View view, Rect bounds) {
            int i = views.indexOf(view);
            TouchDelegate d = new TouchDelegate(new Rect(bounds), view);
            if (i >= 0) { delegates.set(i, d); grown.set(i, new Rect(bounds)); } else { views.add(view); delegates.add(d); grown.add(new Rect(bounds)); }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                active = null;
                for (int i = 0; i < delegates.size(); i++) {
                    View v = views.get(i);
                    if (!v.isShown() || !v.isEnabled()) continue;
                    if (delegates.get(i).onTouchEvent(event)) { active = delegates.get(i); return true; }
                }
                return false;
            }
            if (active == null) return false;
            boolean handled = active.onTouchEvent(event);
            int a = event.getActionMasked();
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) active = null;
            return handled;
        }
    }
}
