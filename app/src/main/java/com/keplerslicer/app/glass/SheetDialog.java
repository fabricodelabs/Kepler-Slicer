package com.keplerslicer.app.glass;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import com.keplerslicer.app.R;

import java.util.List;

/**
 * Bottom sheet for every modal editor in the app.
 *
 * <p>The window covers the whole screen from the first frame and never changes size or position; the sheet is a view
 * pinned to the bottom of it. System bars and the keyboard are applied as padding, and the keyboard is followed frame
 * by frame, so a sheet with a text field neither flies in from the side nor jumps when the keyboard opens.
 * Callers use it like a {@link android.app.Dialog}: {@code setContentView(view)}, {@code show()}, {@code dismiss()}.
 */
public class SheetDialog extends android.app.Dialog {
    private static final int SCRIM = 0x73000000;
    private final Root root;
    private final View scrim;
    private View sheet;
    private boolean closing;
    /** Tablet mode: a centred, opaque desktop dialog that fades in, instead of a bottom sheet. */
    private final boolean desktop;

    public SheetDialog(Context context) {
        super(context, R.style.KeplerSheetDialog);
        root = new Root(context);
        desktop = Desktop.is(context);
        scrim = new View(context);
        scrim.setBackgroundColor(desktop ? 0x59000000 : SCRIM);
        scrim.setAlpha(0f);
        scrim.setOnClickListener(v -> { if (outsideCancels) cancel(); });
        scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
    }

    private boolean outsideCancels = true;

    @Override public void setCanceledOnTouchOutside(boolean cancel) { super.setCanceledOnTouchOutside(cancel); outsideCancels = cancel; }

    /** Preferred sheet height in pixels; 0 (default) wraps the content. Always capped to the free space. */
    public SheetDialog preferredHeight(int px) { root.preferred = Math.max(0, px); root.requestLayout(); return this; }

    /** Largest share of the free height the sheet may take (default 0.92). */
    public SheetDialog maxFraction(float f) { root.fraction = Math.max(.3f, Math.min(1f, f)); root.requestLayout(); return this; }

    @Override public void setContentView(View view) {
        if (sheet != null) root.removeView(sheet);
        sheet = view;
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        // Swallow taps on the sheet so they never reach the scrim behind it.
        view.setClickable(true);
        if (desktop) { view.setBackground(Desktop.surface(getContext(), 14)); view.setClipToOutline(true); }
        root.addView(view, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        super.setContentView(root);
    }

    @Override public void setContentView(int layoutResId) { setContentView(getLayoutInflater().inflate(layoutResId, root, false)); }

    @Override public void show() {
        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(-1, -1);
            w.setDecorFitsSystemWindows(false);
            // Dialog windows are fitted inside the system bars by default; the sheet handles them itself.
            WindowManager.LayoutParams a = w.getAttributes(); a.setFitInsetsTypes(0); a.setFitInsetsSides(0); a.setFitInsetsIgnoringVisibility(true); a.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS; w.setAttributes(a);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                    | (w.getAttributes().softInputMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE));
            w.setWindowAnimations(0);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        closing = false;
        if (sheet != null) sheet.setVisibility(View.INVISIBLE);
        super.show();
        root.post(this::enter);
    }

    private boolean animate() { return ValueAnimator.areAnimatorsEnabled(); }

    private void enter() {
        if (sheet == null) return;
        if (desktop) {
            sheet.setVisibility(View.VISIBLE);
            if (!animate()) { scrim.setAlpha(1f); return; }
            sheet.setAlpha(0f); sheet.animate().alpha(1f).setDuration(120).start(); scrim.animate().alpha(1f).setDuration(120).start();
            return;
        }
        int h = Math.max(sheet.getHeight(), 1);
        sheet.setTranslationY(h);
        sheet.setVisibility(View.VISIBLE);
        if (!animate()) { sheet.setTranslationY(0); scrim.setAlpha(1f); return; }
        Interpolator in = AnimationUtils.loadInterpolator(getContext(), R.interpolator.kv_ios);
        sheet.animate().translationY(0).setDuration(380).setInterpolator(in).start();
        scrim.animate().alpha(1f).setDuration(240).start();
    }

    @Override public void dismiss() {
        if (closing) return;
        if (sheet == null || !isShowing() || !sheet.isAttachedToWindow() || !animate() || ownerGone()) { dismissNow(); return; }
        closing = true;
        Interpolator out = AnimationUtils.loadInterpolator(getContext(), R.interpolator.kv_ios_out);
        hideKeyboard();
        if (desktop) {
            sheet.animate().alpha(0f).setDuration(90).withEndAction(() -> { closing = false; dismissNow(); }).start();
            scrim.animate().alpha(0f).setDuration(90).start();
            return;
        }
        sheet.animate().translationY(sheet.getHeight() + root.getPaddingBottom()).setDuration(240).setInterpolator(out)
                .withEndAction(() -> { closing = false; dismissNow(); }).start();
        scrim.animate().alpha(0f).setDuration(220).start();
    }

    /** The activity may be recreated (rotation, interface mode) while the sheet slides out: its window is gone then. */
    private boolean ownerGone() {
        android.content.Context c = getContext();
        while (c instanceof android.content.ContextWrapper && !(c instanceof android.app.Activity)) c = ((android.content.ContextWrapper) c).getBaseContext();
        return c instanceof android.app.Activity && (((android.app.Activity) c).isFinishing() || ((android.app.Activity) c).isDestroyed());
    }

    private void dismissNow() {
        if (!isShowing()) return;
        Window w = getWindow();
        if (w == null || !w.getDecorView().isAttachedToWindow()) return;
        try { super.dismiss(); } catch (IllegalArgumentException detached) { /* window already removed with its activity */ }
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) return;
        android.view.inputmethod.InputMethodManager imm = getContext().getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    /** Full-screen container: pads for bars, cutout and keyboard, and caps the sheet to the free space. */
    private final class Root extends FrameLayout {
        int preferred;
        float fraction = .92f;
        private boolean imeAnimating;
        private Insets bars = Insets.NONE;

        Root(Context c) {
            super(c);
            setClipChildren(false);
            setOnApplyWindowInsetsListener((v, insets) -> {
                bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                if (!imeAnimating) applyBottom(insets.getInsets(WindowInsets.Type.ime()).bottom);
                return WindowInsets.CONSUMED;
            });
            setWindowInsetsAnimationCallback(new WindowInsetsAnimation.Callback(WindowInsetsAnimation.Callback.DISPATCH_MODE_STOP) {
                @Override public void onPrepare(WindowInsetsAnimation a) { if ((a.getTypeMask() & WindowInsets.Type.ime()) != 0) imeAnimating = true; }
                @Override public WindowInsets onProgress(WindowInsets insets, List<WindowInsetsAnimation> running) {
                    applyBottom(insets.getInsets(WindowInsets.Type.ime()).bottom);
                    return insets;
                }
                @Override public void onEnd(WindowInsetsAnimation a) {
                    if ((a.getTypeMask() & WindowInsets.Type.ime()) == 0) return;
                    imeAnimating = false;
                    WindowInsets now = getRootWindowInsets();
                    if (now != null) applyBottom(now.getInsets(WindowInsets.Type.ime()).bottom);
                }
            });
        }

        private int sheetBasePadding = -1, sheetExtra = -1;

        /** The view that paints the sheet card: the sheet itself, or its last child with a background (glass surfaces). */
        private View backgroundHolder() {
            if (sheet == null) return null;
            if (sheet.getBackground() == null && sheet instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) sheet;
                for (int i = g.getChildCount() - 1; i >= 0; i--) if (g.getChildAt(i).getBackground() != null) return g.getChildAt(i);
            }
            return sheet;
        }

        /** Above the keyboard the sheet sits on it; otherwise its background runs under the gesture bar. */
        private void applyBottom(int ime) {
            boolean keyboard = ime > bars.bottom;
            int bottom = keyboard ? ime : 0, extra = keyboard ? 0 : bars.bottom;
            if (desktop) { bottom = Math.max(ime, bars.bottom); extra = 0; } // a centred window keeps clear of the bars
            if (getPaddingLeft() != bars.left || getPaddingTop() != bars.top || getPaddingRight() != bars.right || getPaddingBottom() != bottom)
                setPadding(bars.left, bars.top, bars.right, bottom);
            View card = backgroundHolder();
            if (card != null && extra != sheetExtra) {
                if (sheetBasePadding < 0) sheetBasePadding = card.getPaddingBottom();
                sheetExtra = extra;
                card.setPadding(card.getPaddingLeft(), card.getPaddingTop(), card.getPaddingRight(), sheetBasePadding + extra);
            }
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(width, height);
            int innerW = Math.max(1, width - getPaddingLeft() - getPaddingRight());
            int innerH = Math.max(1, height - getPaddingTop() - getPaddingBottom());
            float d = getResources().getDisplayMetrics().density;
            // Wide screens and landscape: a centred sheet instead of a stretched strip.
            int sheetW = Math.min(innerW, Math.round((desktop ? 520 : 640) * d));
            int max = Math.max(1, Math.round(innerH * fraction));
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child == scrim) {
                    child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                } else {
                    int hs = preferred > 0 && !desktop ? MeasureSpec.makeMeasureSpec(Math.min(preferred, max), MeasureSpec.EXACTLY) // desktop windows wrap their content
                            : MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST);
                    child.measure(MeasureSpec.makeMeasureSpec(sheetW, MeasureSpec.EXACTLY), hs);
                }
            }
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l, height = b - t;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child == scrim) { child.layout(0, 0, width, height); continue; }
                int cw = child.getMeasuredWidth(), ch = child.getMeasuredHeight();
                int left = getPaddingLeft() + (width - getPaddingLeft() - getPaddingRight() - cw) / 2;
                int bottom = height - getPaddingBottom();
                if (desktop) bottom = getPaddingTop() + (height - getPaddingTop() - getPaddingBottom() + ch) / 2;
                child.layout(left, bottom - ch, left + cw, bottom);
            }
        }
    }
}
