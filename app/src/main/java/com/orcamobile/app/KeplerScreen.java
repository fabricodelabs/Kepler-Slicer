package com.orcamobile.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.orcamobile.app.glass.GlassNavigation;
import com.orcamobile.app.glass.KeplerUi;

/**
 * Kepler Glass UI v3 sub-screen shell (Import, Printers, Filament, Connection, Export, Device):
 * glow backdrop, 48dp top bar at x20 (44dp glass back, 18/700 centred title, 44dp action),
 * scroll content px20 from y112, an optional floating primary button 16dp above the 68dp nav.
 */
final class KeplerScreen {
    final Activity activity; final KeplerUi ui; final FrameLayout root; final LinearLayout column, content; final ScrollView scroll;
    final FrameLayout body; final GlassNavigation nav; private View floating;

    KeplerScreen(Activity activity, String title, Runnable back, View trailing, int navIndex) {
        this.activity = activity; ui = new KeplerUi(activity);
        root = new FrameLayout(activity); root.setBackground(ui.backdrop());
        activity.getWindow().setStatusBarColor(Color.rgb(74, 79, 89)); activity.getWindow().setNavigationBarColor(Color.rgb(56, 61, 70));
        activity.getWindow().getDecorView().setSystemUiVisibility(0);
        activity.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        column = ui.column(); root.addView(column, new FrameLayout.LayoutParams(-1, -1));
        if (android.os.Build.VERSION.SDK_INT >= 35) root.setOnApplyWindowInsetsListener((v, in) -> {
            android.graphics.Insets safe = in.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout() | android.view.WindowInsets.Type.ime());
            column.setPadding(safe.left, safe.top, safe.right, safe.bottom); return in;
        });
        // Without a back action the screen is a top-level tab (Export 6:588): 24/800 title on the left.
        LinearLayout bar = back == null ? (trailing == null ? ui.titleBar(title) : ui.titleBar(title, trailing)) : ui.subHeader(title, activity.getString(R.string.kv3_back), back, trailing);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.setMargins(ui.dp(20), ui.dp(8), ui.dp(20), 0); column.addView(bar, bp);
        body = new FrameLayout(activity); column.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        scroll = new ScrollView(activity); scroll.setVerticalScrollBarEnabled(false); scroll.setClipToPadding(false);
        content = ui.column(); content.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(16)); scroll.addView(content);
        body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        if (navIndex >= 0) {
            nav = new GlassNavigation(activity, navIndex, false, index -> {
                if (index == navIndex) return;
                Intent intent = new Intent(activity, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                if (index != 0) intent.putExtra("kepler_nav", index);
                activity.startActivity(intent); activity.finish();
            });
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2); np.setMargins(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(24)); column.addView(nav, np);
        } else nav = null;
        activity.setContentView(root);
    }

    /** Floating primary action (Import "Choose file", node 4:311): 52dp, 16dp above the navigation. */
    void floating(View button) {
        if (floating != null) body.removeView(floating);
        floating = button; FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM); p.setMargins(ui.dp(20), 0, ui.dp(20), ui.dp(8));
        body.addView(button, p); content.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(84));
    }

    /** Section header (Kepler/Section: 12/700 uppercase, tracking 0.72, pt20 pb8 px4) with optional trailing action. */
    LinearLayout section(String title, String action, Runnable onAction) {
        LinearLayout row = ui.row(); row.setPadding(ui.dp(4), ui.dp(20), ui.dp(4), ui.dp(8));
        row.addView(ui.caption(title), ui.weight());
        if (action != null) { android.widget.TextView a = ui.line(action, 12, 600, KeplerUi.WHITE); a.setMinHeight(ui.dp(32)); a.setGravity(Gravity.CENTER_VERTICAL); ui.pressable(a, null, 8); a.setOnClickListener(v -> onAction.run()); row.addView(a); }
        content.addView(row, new LinearLayout.LayoutParams(-1, -2)); return row;
    }

    void info(String title, String message) { com.orcamobile.app.glass.GlassBottomSheet.info(activity, root, false, title, message); }
    void unavailable(String feature) { info(feature, activity.getString(R.string.kv3_unavailable_detail)); }
}
