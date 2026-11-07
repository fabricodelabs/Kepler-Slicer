package com.keplerslicer.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.keplerslicer.app.glass.KeplerUi;

/**
 * Left panel of the tablet workspace, laid out like the sidebar of desktop OrcaSlicer: expanded it shows the section
 * content (Prepare keeps printer, material and the settings editor in a fixed column; other sections scroll);
 * collapsed it shrinks to an icon strip; hidden leaves the whole width to the 3D plate. It only arranges views:
 * every action belongs to the workspace, so the mobile and tablet interfaces share the same logic.
 */
final class TabletSidebar extends LinearLayout {
    interface Host {
        void openSection(String section);
        void openSettings();
        void openPreferences();
        /** State or width changed: the workspace moves the 3D scene next to the sidebar. */
        void sidebarChanged();
    }

    private static final int[] ICONS = {R.drawable.kv3_cube, R.drawable.kv3_assembly, R.drawable.kv3_object_tools, R.drawable.kv3_layers, R.drawable.kv3_printer};
    static final int[] TITLES = {R.string.tablet_prepare, R.string.tablet_objects, R.string.tablet_tools, R.string.tablet_preview, R.string.tablet_device};

    /** Scrolling content (objects, tools, preview, device) and the fixed Prepare column. */
    final LinearLayout content, fill;
    private final KeplerUi ui;
    private final Host host;
    private final LinearLayout rail, panel;
    private final View handle;
    private final View[] railItems = new View[ICONS.length];
    private final TextView title;
    private final ScrollView scroll;
    private TabletLayout.State state;
    private String section;
    private int panelDp;
    private TabletLayout.State lastShown = TabletLayout.State.EXPANDED;

    TabletSidebar(Context context, KeplerUi ui, Host host) {
        super(context);
        this.ui = ui; this.host = host;
        setOrientation(HORIZONTAL);
        // Two states, as the desktop sidebar: expanded or hidden (the old icon strip duplicated the top tabs).
        state = InterfaceMode.state(context) == TabletLayout.State.EXPANDED ? TabletLayout.State.EXPANDED : TabletLayout.State.HIDDEN;
        section = InterfaceMode.section(context);
        panelDp = InterfaceMode.width(context);

        // Collapsed strip: one icon per section, then settings, preferences and "expand".
        rail = ui.column(); rail.setGravity(Gravity.CENTER_HORIZONTAL); rail.setPadding(0, ui.dp(8), 0, ui.dp(8));
        rail.setBackgroundColor(0xFF2B2F36); // solid: settings and numbers stay legible over the 3D scene
        for (int i = 0; i < ICONS.length; i++) {
            final String s = TabletLayout.SECTIONS[i];
            View item = railItem(ICONS[i], context.getString(TITLES[i]), () -> select(s, true));
            railItems[i] = item; rail.addView(item, railParams());
        }
        rail.addView(new View(context), new LayoutParams(1, 0, 1));
        rail.addView(railItem(R.drawable.kv3_sliders, context.getString(R.string.tablet_settings), host::openSettings), railParams());
        rail.addView(railItem(R.drawable.kv3_cog, context.getString(R.string.tablet_preferences), host::openPreferences), railParams());
        View expand = iconButton(R.drawable.kv3_back, context.getString(R.string.tablet_expand), () -> setState(TabletLayout.State.EXPANDED));
        expand.setRotation(180);
        rail.addView(expand, railParams());
        rail.addView(iconButton(R.drawable.kv3_fullscreen, context.getString(R.string.tablet_hide), () -> setState(TabletLayout.State.HIDDEN)), railParams());
        addView(rail, new LayoutParams(ui.dp(TabletLayout.RAIL_DP), -1));

        panel = ui.column(); panel.setBackgroundColor(0xFF323741); panel.setClipChildren(true);
        LinearLayout head = ui.row(); head.setPadding(ui.dp(16), ui.dp(2), ui.dp(6), 0);
        title = ui.text("", 16, 800, KeplerUi.WHITE);
        head.addView(title, new LayoutParams(0, -2, 1));
        head.addView(iconButton(R.drawable.kv3_back, context.getString(R.string.tablet_hide), () -> setState(TabletLayout.State.HIDDEN)), new LayoutParams(ui.dp(48), ui.dp(48)));
        panel.addView(head, new LayoutParams(-1, -2));
        fill = ui.column(); fill.setVisibility(GONE);
        panel.addView(fill, new LayoutParams(-1, 0, 1));
        scroll = new ScrollView(context); scroll.setFillViewport(true); scroll.setClipChildren(true);
        // Glass cards draw through render nodes; clip to the outline so scrolled content never covers the header.
        scroll.setOutlineProvider(android.view.ViewOutlineProvider.BOUNDS); scroll.setClipToOutline(true);
        fill.setOutlineProvider(android.view.ViewOutlineProvider.BOUNDS); fill.setClipToOutline(true);
        content = ui.column(); content.setPadding(0, 0, 0, ui.dp(12));
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(scroll, new LayoutParams(-1, 0, 1));
        addView(panel, new LayoutParams(ui.dp(panelDp), -1));

        handle = buildHandle(context);
        addView(handle, new LayoutParams(ui.dp(TabletLayout.HANDLE_DP), -1));
        apply(false);
    }

    private LayoutParams railParams() { LayoutParams p = new LayoutParams(ui.dp(60), ui.dp(56)); p.topMargin = ui.dp(4); return p; }

    private View railItem(int icon, String label, Runnable action) {
        LinearLayout item = ui.column(); item.setGravity(Gravity.CENTER);
        item.addView(ui.image(icon, 22, KeplerUi.WHITE), new LayoutParams(ui.dp(22), ui.dp(22)));
        TextView caption = ui.line(label, 10, 600, KeplerUi.SECONDARY); caption.setGravity(Gravity.CENTER); caption.setMaxLines(1);
        LayoutParams cp = new LayoutParams(-1, -2); cp.topMargin = ui.dp(3); item.addView(caption, cp);
        ui.pressable(item, null, 14); item.setContentDescription(label); item.setOnClickListener(v -> action.run());
        return item;
    }

    private View iconButton(int icon, String label, Runnable action) {
        FrameLayout f = new FrameLayout(getContext());
        ImageView art = new ImageView(getContext()); art.setImageResource(icon); art.setColorFilter(KeplerUi.WHITE);
        f.addView(art, new FrameLayout.LayoutParams(ui.dp(22), ui.dp(22), Gravity.CENTER));
        ui.pressable(f, null, 14); f.setContentDescription(label); f.setOnClickListener(v -> action.run());
        return f;
    }

    /** Right edge of the panel: drag to resize. It takes the touch, so the 3D view never sees the drag. */
    private View buildHandle(Context context) {
        FrameLayout h = new FrameLayout(context);
        View grip = new View(context); grip.setBackground(ui.shape(0x66FFFFFF, 0, 2));
        h.addView(grip, new FrameLayout.LayoutParams(ui.dp(4), ui.dp(40), Gravity.CENTER));
        h.setContentDescription(context.getString(R.string.tablet_resize));
        final float density = getResources().getDisplayMetrics().density;
        final int slop = ViewConfiguration.get(context).getScaledTouchSlop();
        h.setOnTouchListener(new OnTouchListener() {
            float startX; int startDp; boolean dragging;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: startX = e.getRawX(); startDp = panelDp; dragging = false; v.getParent().requestDisallowInterceptTouchEvent(true); return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!dragging && Math.abs(e.getRawX() - startX) < slop) return true;
                        dragging = true;
                        setPanelDp(TabletLayout.clampWidth(Math.round(startDp + (e.getRawX() - startX) / density), windowDp()));
                        return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                        if (dragging) { InterfaceMode.width(getContext(), panelDp); host.sidebarChanged(); }
                        return true;
                }
                return false;
            }
        });
        return h;
    }

    int windowDp() {
        android.graphics.Rect b = ((android.app.Activity) getContext()).getWindowManager().getCurrentWindowMetrics().getBounds();
        return Math.round(b.width() / getResources().getDisplayMetrics().density);
    }

    private void setPanelDp(int dp) {
        panelDp = dp;
        LayoutParams p = (LayoutParams) panel.getLayoutParams(); p.width = ui.dp(dp); panel.setLayoutParams(p);
    }

    String section() { return section; }
    TabletLayout.State state() { return state; }
    /** Width the 3D scene leaves to the sidebar, in pixels. */
    int dockedPx() { return ui.dp(TabletLayout.docked(state, windowDp(), panelDp)); }
    boolean overlay() { return state == TabletLayout.State.EXPANDED && TabletLayout.overlay(windowDp(), panelDp); }

    /** Prepare uses the fixed column (its settings editor scrolls by itself); other sections scroll as a whole. */
    void showFill(boolean fixed) { fill.setVisibility(fixed ? VISIBLE : GONE); scroll.setVisibility(fixed ? GONE : VISIBLE); }

    /** Highlights a section; {@code open} also asks the workspace to show it. */
    void select(String next, boolean open) {
        section = TabletLayout.section(next);
        InterfaceMode.section(getContext(), section);
        for (int i = 0; i < railItems.length; i++) {
            boolean on = TabletLayout.SECTIONS[i].equals(section);
            railItems[i].setBackground(on ? ui.shape(0x33FFFFFF, 0x66FFFFFF, 14) : null);
            railItems[i].setSelected(on);
        }
        for (int i = 0; i < TabletLayout.SECTIONS.length; i++) if (TabletLayout.SECTIONS[i].equals(section)) title.setText(TITLES[i]);
        if (open) {
            if (state != TabletLayout.State.EXPANDED) setState(TabletLayout.State.EXPANDED);
            host.openSection(section);
            scroll.scrollTo(0, 0);
        }
    }

    /** Brings a hidden panel back the way it was before hiding (expanded or collapsed). */
    void restore() { setState(TabletLayout.State.EXPANDED); }

    void toggleCollapsed() { setState(state == TabletLayout.State.EXPANDED ? TabletLayout.State.HIDDEN : TabletLayout.State.EXPANDED); }

    void setState(TabletLayout.State next) {
        if (next == TabletLayout.State.COLLAPSED) next = TabletLayout.State.HIDDEN;
        if (next == state) return;
        if (state != TabletLayout.State.HIDDEN) lastShown = state;
        state = next; InterfaceMode.state(getContext(), next);
        apply(true);
        host.sidebarChanged();
    }

    private void apply(boolean animate) {
        panelDp = TabletLayout.clampWidth(panelDp, windowDp());
        LayoutParams pp = (LayoutParams) panel.getLayoutParams(); pp.width = ui.dp(panelDp); panel.setLayoutParams(pp);
        boolean expanded = state == TabletLayout.State.EXPANDED;
        setVisibility(state == TabletLayout.State.HIDDEN ? GONE : VISIBLE);
        rail.setVisibility(state == TabletLayout.State.COLLAPSED ? VISIBLE : GONE);
        panel.setVisibility(expanded ? VISIBLE : GONE);
        handle.setVisibility(expanded ? VISIBLE : GONE);
        if (animate && expanded && ValueAnimator.areAnimatorsEnabled()) {
            panel.setAlpha(0f); panel.setTranslationX(-ui.dp(24));
            panel.animate().alpha(1f).translationX(0).setDuration(220).start();
        }
    }
}
