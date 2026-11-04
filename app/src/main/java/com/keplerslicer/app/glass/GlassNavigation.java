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

    /** Blurs {@code source} (a sibling drawn under this floating bar) behind the bar on Android 12+. */
    public void setBackdrop(View source) { backdropSource = source; updateAppearance(); }

    public GlassNavigation(Context context, int selected, boolean reduced, Listener listener) {
        super(context);
        tokens = GlassTokens.get(context);
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
    }

    public void setReducedTransparency(boolean reduced) {
        this.reduced = reduced;
        updateAppearance();
    }

    public void setSelectedItem(int item) {
        if (item < 0 || item >= items.length) return;
        selected = item;
        updateAppearance();
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
        bar.setColor(reduced ? tokens.reduceTransparencyFill : blur ? 0xB84E535A : 0xE64E535A);
        bar.setCornerRadius(tokens.dp(tokens.navRadius));
        bar.setStroke(Math.max(1, tokens.dp(tokens.navStrokeWidth)), tokens.navStroke);
        setBackground(blur ? Backdrop.behind(this, backdropSource, tokens.dp(24), tokens.dp(tokens.navRadius), bar) : bar);

        for (int index = 0; index < items.length; index++) {
            boolean active = index == selected;
            LinearLayout item = items[index];
            GradientDrawable pill = null;
            if (active) {
                pill = new GradientDrawable();
                pill.setColor(Color.WHITE);
                pill.setCornerRadius(tokens.dp(tokens.navSelectedRadius));
            }
            item.setBackground(pill);
            icons[index].setImageResource(active ? SELECTED_ICONS[index] : ICONS[index]);
            captions[index].setTextColor(active ? tokens.navActiveText : tokens.navInactiveText);
            item.setSelected(active);
            item.setContentDescription(active
                ? labels[index] + ", " + getContext().getString(R.string.glass_selected)
                : labels[index]);
        }
    }
}
