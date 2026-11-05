package com.keplerslicer.app.glass;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.keplerslicer.app.R;

/** Four-item Figma v3 navigation. The host owns destination changes. */
public final class GlassNavigation extends LinearLayout {
    public interface Listener {
        void selected(int item);
    }

    private static final int[] ICONS = {
        R.drawable.kepler_v3_nav_workspace,
        R.drawable.kepler_v3_nav_settings,
        R.drawable.kepler_v3_nav_export,
        R.drawable.kepler_v3_nav_preferences
    };
    private static final int[] SELECTED_ICONS = {
        R.drawable.kepler_v3_nav_workspace_selected,
        R.drawable.kepler_v3_nav_settings_selected,
        R.drawable.kepler_v3_nav_export_selected,
        R.drawable.kepler_v3_nav_preferences_selected
    };

    private final GlassTokens tokens;
    private final Listener listener;
    private final String[] labels;
    private final LinearLayout[] items = new LinearLayout[4];
    private final ImageView[] icons = new ImageView[4];
    private final TextView[] captions = new TextView[4];
    private int selected;
    private boolean reduced;
    private View backdropSource;
    private final android.graphics.Paint pillPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF pill = new android.graphics.RectF();
    private android.animation.ValueAnimator pillAnimator, liftAnimator;
    /** Liquid Glass droplet: 0 = resting white capsule, 1 = lifted clear lens (while moving or dragged). */
    private float lift, stretch;
    private final android.graphics.Paint rimPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF drawn = new android.graphics.RectF();
    private boolean dragging; private float downX, downY; private int dragTarget = -1;

    /** Blurs {@code source} (a sibling drawn under this floating bar) behind the bar on Android 12+. */
    public void setBackdrop(View source) { backdropSource = source; updateAppearance(); }

    public GlassNavigation(Context context, int selected, boolean reduced, Listener listener) {
        super(context);
        tokens = GlassTokens.get(context);
        pillPaint.setColor(Color.WHITE);
        this.listener = listener;
        this.selected = Math.max(0, Math.min(items.length - 1, selected));
        this.reduced = reduced;
        labels = new String[] {
            context.getString(R.string.glass_workspace),
            context.getString(R.string.glass_settings),
            context.getString(R.string.glass_export),
            context.getString(R.string.glass_preferences)
        };

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int padding = tokens.dp(tokens.navPad + tokens.navStrokeWidth);
        setPadding(padding, padding, padding, padding);
        setMinimumHeight(tokens.dp(tokens.navHeight));
        buildItems();
        updateAppearance();
        rimPaint.setStyle(android.graphics.Paint.Style.STROKE);
        rimPaint.setStrokeWidth(Math.max(1f, tokens.dp(1.5f)));
    }

    public void setReducedTransparency(boolean reduced) {
        this.reduced = reduced;
        updateAppearance();
    }

    public void setSelectedItem(int item) { setSelectedItem(item, true); }

    public void setSelectedItem(int item, boolean animate) {
        if (item < 0 || item >= items.length) return;
        int previous = selected;
        selected = item;
        updateAppearance();
        if (animate && previous != item && isLaidOut() && items[item].getWidth() > 0) slidePill();
        else { if (pillAnimator != null) pillAnimator.cancel(); placePill(); invalidate(); }
    }

    private void placePill() {
        View target = items[selected];
        pill.set(target.getLeft(), target.getTop(), target.getRight(), target.getBottom());
    }

    /** The droplet lifts into clear glass, flows to the selected item stretching with its speed, then settles. */
    private void slidePill() {
        if (pillAnimator != null) pillAnimator.cancel();
        final float fromL = pill.left, fromR = pill.right;
        final View target = items[selected];
        animateLift(1f, 140, 0);
        pillAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        pillAnimator.setDuration(420);
        pillAnimator.setInterpolator(new android.view.animation.PathInterpolator(0.32f, 1.18f, 0.36f, 1f));
        pillAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue(), f = a.getAnimatedFraction();
            pill.set(fromL + (target.getLeft() - fromL) * t, target.getTop(), fromR + (target.getRight() - fromR) * t, target.getBottom());
            stretch = (float) Math.sin(Math.PI * Math.min(1f, f * 1.15f)) * 0.22f;
            magnifyUnderPill(); invalidate();
        });
        pillAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { stretch = 0f; animateLift(0f, 260, 0); }
        });
        pillAnimator.start();
    }

    private void animateLift(float to, long duration, long delay) {
        if (liftAnimator != null) liftAnimator.cancel();
        liftAnimator = android.animation.ValueAnimator.ofFloat(lift, to);
        liftAnimator.setDuration(duration); liftAnimator.setStartDelay(delay);
        liftAnimator.setInterpolator(to > lift ? new android.view.animation.DecelerateInterpolator() : new android.view.animation.OvershootInterpolator(1.6f));
        liftAnimator.addUpdateListener(a -> { lift = Math.max(0f, (float) a.getAnimatedValue()); magnifyUnderPill(); updateItemColors(); invalidate(); });
        liftAnimator.start();
    }

    /** Items under the lifted lens grow a little, as if magnified by it. */
    private void magnifyUnderPill() {
        float cx = pill.centerX();
        for (LinearLayout item : items) {
            if (item == null || item.getWidth() == 0) continue;
            float d = Math.abs(item.getLeft() + item.getWidth() / 2f - cx) / item.getWidth();
            float k = 1f + 0.14f * lift * Math.max(0f, 1f - d);
            item.setScaleX(k); item.setScaleY(k);
        }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (!dragging && (pillAnimator == null || !pillAnimator.isRunning())) placePill();
    }

    @Override protected void dispatchDraw(android.graphics.Canvas canvas) {
        if (pill.width() > 0) {
            float grow = lift * tokens.dp(5), sx = pill.width() * stretch / 2f, sy = pill.height() * stretch * 0.18f;
            drawn.set(pill.left - grow - sx, pill.top - grow + sy, pill.right + grow + sx, pill.bottom + grow - sy);
            float radius = Math.min(drawn.height() / 2f, tokens.dp(tokens.navSelectedRadius) + grow);
            // Clear glass capsule (Liquid Glass): faint fill, top sheen and a bright rim; it brightens as it lifts.
            pillPaint.setShader(null);
            pillPaint.setColor(Color.argb(Math.round(34 + 26 * lift), 255, 255, 255));
            canvas.drawRoundRect(drawn, radius, radius, pillPaint);
            pillPaint.setShader(new android.graphics.LinearGradient(0, drawn.top, 0, drawn.bottom,
                Color.argb(Math.round(46 + 54 * lift), 255, 255, 255), Color.argb(0, 255, 255, 255), android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRoundRect(drawn, radius, radius, pillPaint);
            pillPaint.setShader(null);
            rimPaint.setShader(new android.graphics.LinearGradient(drawn.left, drawn.top, drawn.right, drawn.bottom,
                Color.argb(Math.round(150 + 90 * lift), 255, 255, 255), Color.argb(Math.round(36 + 40 * lift), 255, 255, 255), android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRoundRect(drawn, radius, radius, rimPaint);
        }
        super.dispatchDraw(canvas);
    }

    // ---- Drag the droplet along the bar (as on iOS 26): it follows the finger and drops on the nearest tab. ----
    @Override public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
        switch (e.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN: downX = e.getX(); downY = e.getY(); dragging = false; return false;
            case android.view.MotionEvent.ACTION_MOVE:
                float slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (Math.abs(e.getX() - downX) > slop && Math.abs(e.getX() - downX) > Math.abs(e.getY() - downY)) { startDrag(); return true; }
                return false;
            default: return false;
        }
    }

    @Override public boolean onTouchEvent(android.view.MotionEvent e) {
        if (!dragging) return super.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case android.view.MotionEvent.ACTION_MOVE: followFinger(e.getX()); return true;
            case android.view.MotionEvent.ACTION_UP: case android.view.MotionEvent.ACTION_CANCEL: endDrag(e.getActionMasked() == android.view.MotionEvent.ACTION_UP); return true;
            default: return true;
        }
    }

    private void startDrag() {
        dragging = true; getParent().requestDisallowInterceptTouchEvent(true);
        if (pillAnimator != null) pillAnimator.cancel();
        for (LinearLayout item : items) item.setPressed(false);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        animateLift(1f, 140, 0);
    }

    private void followFinger(float x) {
        float w = pill.width(), half = w / 2f;
        float min = items[0].getLeft(), max = items[items.length - 1].getRight();
        float cx = Math.max(min + half, Math.min(max - half, x));
        float prev = pill.centerX();
        pill.offset(cx - prev, 0);
        stretch = Math.min(0.2f, Math.abs(cx - prev) / Math.max(1f, w) * 1.2f);
        int nearest = nearestItem(cx);
        if (nearest != dragTarget) { if (dragTarget >= 0) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); dragTarget = nearest; }
        magnifyUnderPill(); invalidate();
    }

    private int nearestItem(float x) {
        int best = 0; float bestD = Float.MAX_VALUE;
        for (int i = 0; i < items.length; i++) { float d = Math.abs(items[i].getLeft() + items[i].getWidth() / 2f - x); if (d < bestD) { bestD = d; best = i; } }
        return best;
    }

    private void endDrag(boolean commit) {
        dragging = false; stretch = 0f;
        int target = commit ? nearestItem(pill.centerX()) : selected;
        dragTarget = -1;
        int previous = selected;
        selected = target; updateAppearance();
        slidePill();
        if (commit && target != previous && listener != null) { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); listener.selected(target); }
    }

    private void buildItems() {
        int itemHeight = tokens.dp(tokens.navHeight) - getPaddingTop() - getPaddingBottom();
        int minItemHeight = Math.max(itemHeight, tokens.dp(tokens.minTouch));
        for (int index = 0; index < items.length; index++) {
            final int target = index;
            LinearLayout item = new LinearLayout(getContext());
            item.setOrientation(VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setMinimumHeight(minItemHeight);
            item.setFocusable(true);
            item.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

            ImageView icon = new ImageView(getContext());
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int iconSize = tokens.dp(tokens.navIconSize);
            item.addView(icon, new LayoutParams(iconSize, iconSize));

            TextView caption = new TextView(getContext());
            caption.setText(labels[index]);
            caption.setTextSize(tokens.navLabelSize);
            caption.setSingleLine(true);
            caption.setEllipsize(TextUtils.TruncateAt.END);
            caption.setGravity(Gravity.CENTER);
            caption.setIncludeFontPadding(false);
            caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            GlassTypography.apply(caption, 600);
            // Reserve the reference line box while allowing larger native font metrics.
            caption.setMinimumHeight(Math.round(caption.getTextSize() * tokens.navLineHeight));
            LayoutParams captionParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            captionParams.topMargin = tokens.dp(tokens.navLabelGap);
            item.addView(caption, captionParams);

            item.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setSelected(host.isSelected());
                }
            });
            item.setOnClickListener(view -> {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                setSelectedItem(target);
                if (listener != null) listener.selected(target);
            });

            LayoutParams itemParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            if (index > 0) itemParams.setMarginStart(tokens.dp(tokens.navGap));
            addView(item, itemParams);
            items[index] = item;
            icons[index] = icon;
            captions[index] = caption;
        }
    }

    /** Update selection in place to retain item views and accessibility focus. */
    private void updateAppearance() {
        GradientDrawable bar = new GradientDrawable();
        // Frosted glass: Figma glass/fill over a blurred backdrop (Android 12+). Without blur the fill stays nearly
        // opaque so content scrolling under the floating bar does not fight with the labels.
        boolean blur = !reduced && backdropSource != null && Backdrop.supported();
        bar.setColor(reduced ? tokens.reduceTransparencyFill : blur ? (LiquidLens.supported() ? 0x704E535A : 0xB84E535A) : 0xE64E535A);
        bar.setCornerRadius(tokens.dp(tokens.navRadius));
        bar.setStroke(Math.max(1, tokens.dp(tokens.navStrokeWidth)), tokens.navStroke);
        setBackground(blur ? Backdrop.behind(this, backdropSource, tokens.dp(LiquidLens.supported() ? 10 : 24), tokens.dp(tokens.navRadius), bar) : bar);

        for (int index = 0; index < items.length; index++) {
            boolean active = index == selected;
            LinearLayout item = items[index];
            item.setBackground(null);
            item.setSelected(active);
            if (index == items.length - 1) updateItemColors();
            item.setContentDescription(active
                ? labels[index] + ", " + getContext().getString(R.string.glass_selected)
                : labels[index]);
        }
    }

    private void updateItemColors() {
        for (int index = 0; index < items.length; index++) {
            if (icons[index] == null) continue;
            boolean active = index == selected && !dragging;
            icons[index].setImageResource(ICONS[index]);
            icons[index].setImageAlpha(active ? 255 : 170);
            captions[index].setTextColor(active ? Color.WHITE : tokens.navInactiveText);
        }
    }
}
