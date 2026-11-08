package com.keplerslicer.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.keplerslicer.app.glass.GlassNavigation;
import com.keplerslicer.app.glass.KeplerUi;

/**
 * Kepler Glass UI v3 sub-screen shell (Import, Printers, Filament, Connection, Export, Device):
 * glow backdrop, 48dp top bar at x20 (44dp glass back, 18/700 centred title, 44dp action),
 * scroll content px20 from y112, an optional floating primary button 16dp above the 68dp nav.
 */
final class KeplerScreen {
    final Activity activity; final KeplerUi ui; final FrameLayout root; final LinearLayout column, content; final ScrollView scroll;
    final FrameLayout body; final GlassNavigation nav; private View floating; final LinearLayout topBar;
    /** Detaches the top bar so list screens can place it inside their own scrolling header. */
    LinearLayout takeTopBar() { if (topBar.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) topBar.getParent()).removeView(topBar); return topBar; }

    private View navBar() { return nav; }
    KeplerScreen(Activity activity, String title, Runnable back, View trailing, int navIndex) {
        this.activity = activity; ui = new KeplerUi(activity);
        root = new FrameLayout(activity); root.setBackground(ui.backdrop());
        activity.getWindow().setStatusBarColor(Color.rgb(74, 79, 89)); activity.getWindow().setNavigationBarColor(Color.rgb(56, 61, 70));
        activity.getWindow().getDecorView().setSystemUiVisibility(0);
        activity.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        column = ui.column(); root.addView(column, new FrameLayout.LayoutParams(-1, -1));
        if (android.os.Build.VERSION.SDK_INT >= 35) root.setOnApplyWindowInsetsListener((v, in) -> {
            android.graphics.Insets safe = in.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout() | android.view.WindowInsets.Type.ime());
            android.graphics.Insets ime = in.getInsets(android.view.WindowInsets.Type.ime());
            column.setPadding(safe.left, safe.top, safe.right, navIndex >= 0 ? ime.bottom : safe.bottom);
            View bar = navBar(); // same lift above the gesture bar as the other tabs
            if (bar != null && bar.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                android.graphics.Insets sys = in.getInsets(android.view.WindowInsets.Type.systemBars());
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) bar.getLayoutParams();
                int want = ui.dp(24) + (ime.bottom > 0 ? 0 : sys.bottom);
                if (p.bottomMargin != want) { p.bottomMargin = want; bar.setLayoutParams(p); }
            }
            return in;
        });
        // Without a back action the screen is a top-level tab (Export 6:588): 24/800 title on the left.
        if (back == null && com.keplerslicer.app.glass.Desktop.windowed(activity)) back = activity::finish; // tablet window: always closable
        LinearLayout bar = back == null ? (trailing == null ? ui.titleBar(title) : ui.titleBar(title, trailing)) : ui.subHeader(title, activity.getString(R.string.kv3_back), back, trailing);
        topBar = bar; // scrolls away with the content instead of being pinned (owner request)
        body = new FrameLayout(activity); column.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        scroll = new ScrollView(activity); scroll.setVerticalScrollBarEnabled(false); scroll.setClipToPadding(false);
        content = ui.column(); content.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(16)); scroll.addView(content);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.bottomMargin = ui.dp(12); content.addView(bar, bp);
        body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        if (navIndex >= 0) {
            nav = new GlassNavigation(activity, navIndex, false, index -> {
                if (index == navIndex) return;
                Intent intent = new Intent(activity, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                if (index != 0) intent.putExtra("kepler_nav", index);
                com.keplerslicer.app.glass.KeplerUi.tab(activity, intent, true);
            });
            KeplerUi.floatNavigation(root, column, nav, ui.dp(16), ui.dp(24));
            // Scrolling content ends above the floating tab bar (the last button used to sit under it on short screens).
            View.OnLayoutChangeListener clear = (v, l, t, r, b, ol, ot, orr, ob) -> applyNavClearance();
            nav.addOnLayoutChangeListener(clear); body.addOnLayoutChangeListener(clear);
        } else nav = null;
        activity.setContentView(root);
    }

    private final java.util.List<View> navClear = new java.util.ArrayList<>();
    /** Gives a scrolling view (a ListView of a list screen) the same bottom clearance as the content. */
    void clearNav(View scrolling) { navClear.add(scrolling); applyNavClearance(); }
    private void applyNavClearance() {
        if (nav == null || nav.getHeight() == 0 || body.getHeight() == 0) return;
        int[] n = new int[2], bd = new int[2]; nav.getLocationInWindow(n); body.getLocationInWindow(bd);
        int covered = Math.max(0, bd[1] + body.getHeight() - n[1]) + ui.dp(16);
        if (floating == null && content.getPaddingBottom() != covered) content.setPadding(content.getPaddingLeft(), content.getPaddingTop(), content.getPaddingRight(), covered);
        for (View s : navClear) if (s.getPaddingBottom() != covered) s.setPadding(s.getPaddingLeft(), s.getPaddingTop(), s.getPaddingRight(), covered);
    }

    /** Floating primary action (Import "Choose file", node 4:311): 52dp, 16dp above the navigation. */
    void floating(View button) {
        if (floating != null) body.removeView(floating);
        floating = button; FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM); p.setMargins(ui.dp(20), 0, ui.dp(20), nav == null ? ui.dp(16) : ui.dp(24 + 68 + 16));
        body.addView(button, p); content.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(84));
        if (nav != null) {
            // Keep 12 dp of air above the floating tab bar, wherever the gesture-bar inset puts it.
            View.OnLayoutChangeListener follow = (v, l, t, r, b, ol, ot, orr, ob) -> {
                if (floating != button || nav.getHeight() == 0 || body.getHeight() == 0) return;
                int[] n = new int[2], bd = new int[2]; nav.getLocationInWindow(n); body.getLocationInWindow(bd);
                int want = bd[1] + body.getHeight() - n[1] + ui.dp(12);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) button.getLayoutParams();
                if (want > 0 && lp.bottomMargin != want) { lp.bottomMargin = want; button.setLayoutParams(lp); content.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), want + button.getHeight() + ui.dp(12)); }
            };
            nav.addOnLayoutChangeListener(follow); body.addOnLayoutChangeListener(follow);
        }
    }

    /** Section header (Kepler/Section: 12/700 uppercase, tracking 0.72, pt20 pb8 px4) with optional trailing action. */
    LinearLayout section(String title, String action, Runnable onAction) {
        LinearLayout row = ui.row(); row.setPadding(ui.dp(4), ui.dp(20), ui.dp(4), ui.dp(8));
        row.addView(ui.caption(title), ui.weight());
        if (action != null) { android.widget.TextView a = ui.line(action, 12, 600, KeplerUi.WHITE); a.setMinHeight(ui.dp(32)); a.setGravity(Gravity.CENTER_VERTICAL); ui.pressable(a, null, 8); a.setOnClickListener(v -> onAction.run()); row.addView(a); }
        content.addView(row, new LinearLayout.LayoutParams(-1, -2)); return row;
    }

    void info(String title, String message) { com.keplerslicer.app.glass.GlassBottomSheet.info(activity, root, false, title, message); }
    void unavailable(String feature) { info(feature, activity.getString(R.string.kv3_unavailable_detail)); }
}
