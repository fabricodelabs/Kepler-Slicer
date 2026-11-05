package com.keplerslicer.app.glass;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.keplerslicer.app.R;

/**
 * Kepler Slicer Glass UI v3 primitives shared by every screen. Values come from the Figma variables
 * (glass/fill, glass/stroke, field/fill, field/stroke, glass/divider, surface/active, text/*).
 * The caller owns data, state and callbacks.
 */
public final class KeplerUi {
    public static final int WHITE = 0xFFFFFFFF, SECONDARY = 0xE0FFFFFF, ON_ACTIVE = 0xFF16181C, BASE = 0xFF383D46;
    public static final int GLASS = 0x1CFFFFFF, GLASS_STROKE = 0x33FFFFFF, FIELD = 0x38000000, FIELD_STROKE = 0x2EFFFFFF, DIVIDER = 0x1FFFFFFF;
    public static final int MINT = 0xFF5EE0C8, AXIS_X = 0xFFFF8A80, AXIS_Y = 0xFF8BE28B, AXIS_Z = 0xFF8AB4FF, WARN = 0xFFFFD27A;

    private final Context context;
    private final float density;

    public KeplerUi(Context context) { this.context = context; density = context.getResources().getDisplayMetrics().density; }
    public Context context() { return context; }
    public int dp(float v) { return Math.round(v * density); }

    /**
     * Figma nav bar floats over the page (x16, bottom 24) instead of occupying its own band: the nav is moved
     * into {@code host} (a FrameLayout over {@code content}) and every scrolling list/scroll view inside content
     * gets bottom padding equal to the nav's footprint with clipToPadding off, so rows scroll underneath it.
     */
    public static void floatNavigation(android.widget.FrameLayout host, View content, View nav, int sideMarginPx, int bottomMarginPx) {
        if (nav.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) nav.getParent()).removeView(nav);
        android.widget.FrameLayout.LayoutParams p = new android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        p.setMargins(sideMarginPx, 0, sideMarginPx, bottomMarginPx); host.addView(nav, p);
        if (nav instanceof GlassNavigation) ((GlassNavigation) nav).setBackdrop(content);
        final int extra = Math.round(12 * host.getResources().getDisplayMetrics().density);
        host.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (nav.getHeight() <= 0) return;
            int[] navXY = new int[2]; nav.getLocationInWindow(navXY);
            padScrollables(content, navXY[1] - extra, nav.getHeight() + bottomMarginPx + extra);
        });
    }
    /**
     * Edge-to-edge host with a floating tab bar: content runs under the gesture bar (no hard cut above it) and the
     * floating bar is lifted by the gesture-bar height instead.
     */
    public static void edgeToEdge(android.widget.FrameLayout host, int navBottomPx) {
        if (android.os.Build.VERSION.SDK_INT < 35) return;
        host.setOnApplyWindowInsetsListener((v, in) -> {
            android.graphics.Insets bars = in.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            android.graphics.Insets ime = in.getInsets(android.view.WindowInsets.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, ime.bottom);
            for (int i = 0; i < host.getChildCount(); i++) {
                View child = host.getChildAt(i);
                if (child instanceof GlassNavigation && child.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                    android.view.ViewGroup.MarginLayoutParams p = (android.view.ViewGroup.MarginLayoutParams) child.getLayoutParams();
                    int want = navBottomPx + (ime.bottom > 0 ? 0 : bars.bottom);
                    if (p.bottomMargin != want) { p.bottomMargin = want; child.setLayoutParams(p); }
                }
            }
            return in;
        });
        host.requestApplyInsets();
    }

    /**
     * A Spinner that opens the glass bottom sheet instead of the platform drop-down (whose translucent popup
     * overlapped the form). Selection, listeners and getSelectedItemPosition() keep working as before.
     */
    public static void sheetSpinner(android.widget.Spinner spinner, String title) {
        spinner.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() != android.view.MotionEvent.ACTION_UP) return true;
            android.widget.SpinnerAdapter a = spinner.getAdapter(); if (a == null) return true;
            String[] values = new String[a.getCount()];
            for (int i = 0; i < values.length; i++) values[i] = String.valueOf(a.getItem(i));
            GlassBottomSheet.choose(spinner.getContext(), spinner, false, title, values, spinner.getSelectedItemPosition(), spinner::setSelection);
            return true;
        });
        spinner.setPopupBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF2E323A)); // opaque fallback (keyboard/TalkBack)
    }

    /**
     * In-screen navigation (a section opened inside the same screen, or back out of it) animated like a screen
     * push/pop: {@code rebuild} replaces the content view; the old content, captured first, slides away under or
     * over it. dir &gt; 0 pushes deeper, dir &lt; 0 goes back, 0 just rebuilds.
     */
    public static void navigate(android.app.Activity a, int dir, Runnable rebuild) {
        android.graphics.Bitmap shot = dir == 0 ? null : snapshotContent(a);
        rebuild.run();
        if (shot != null) slideFrom(a, shot, dir);
    }

    /** Picture of the current screen content (null when not laid out yet). */
    public static android.graphics.Bitmap snapshotContent(android.app.Activity a) {
        android.view.ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return null;
        View old = content.getChildAt(content.getChildCount() - 1);
        if (old.getWidth() <= 0 || old.getHeight() <= 0) return null;
        try {
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(old.getWidth(), old.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(bmp);
            android.graphics.drawable.Drawable windowBg = a.getWindow().getDecorView().getBackground();
            if (windowBg != null) windowBg.draw(c);
            // The tab bar stays put during in-screen navigation, so it is left out of the picture.
            GlassNavigation nav = findNav(old); int vis = nav == null ? 0 : nav.getVisibility();
            if (nav != null) nav.setVisibility(View.INVISIBLE);
            try { old.draw(c); } finally { if (nav != null) nav.setVisibility(vis); }
            return bmp;
        } catch (RuntimeException | OutOfMemoryError e) { return null; }
    }

    private static GlassNavigation findNav(View v) {
        if (v instanceof GlassNavigation) return (GlassNavigation) v;
        if (v instanceof android.view.ViewGroup) { android.view.ViewGroup g = (android.view.ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { GlassNavigation n = findNav(g.getChildAt(i)); if (n != null) return n; } }
        return null;
    }

    /** Animates the new content in against a picture of the old one (iOS push/pop with parallax and dimming). */
    public static void slideFrom(android.app.Activity a, android.graphics.Bitmap shot, int dir) {
        android.widget.FrameLayout content = a.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) { shot.recycle(); return; }
        final View now = content.getChildAt(content.getChildCount() - 1);
        final android.widget.ImageView old = new android.widget.ImageView(a);
        old.setImageBitmap(shot); old.setScaleType(android.widget.ImageView.ScaleType.MATRIX);
        old.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        final boolean push = dir > 0;
        if (push) content.addView(old, 0, new android.widget.FrameLayout.LayoutParams(-1, -1));
        else content.addView(old, new android.widget.FrameLayout.LayoutParams(-1, -1));
        final View moving = push ? now : old, under = push ? old : now;
        final float w = content.getWidth() > 0 ? content.getWidth() : a.getResources().getDisplayMetrics().widthPixels;
        moving.setElevation(12 * a.getResources().getDisplayMetrics().density);
        if (push) { now.setTranslationX(w); } else { now.setTranslationX(-0.3f * w); }
        // Keep the tab bar fixed: counter-move it with its screen, and when the old picture slides over the new screen,
        // draw the bar again on top through the overlay.
        final GlassNavigation nav = findNav(now);
        final android.graphics.drawable.Drawable navOnTop = nav == null || push ? null : new android.graphics.drawable.Drawable() {
            final int[] at = new int[2], base = new int[2];
            @Override public void draw(android.graphics.Canvas c) { nav.getLocationInWindow(at); content.getLocationInWindow(base); int save = c.save(); c.translate(at[0] - base[0], at[1] - base[1]); nav.draw(c); c.restoreToCount(save); }
            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(android.graphics.ColorFilter cf) {}
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
        if (navOnTop != null) { navOnTop.setBounds(0, 0, content.getWidth(), content.getHeight()); content.getOverlay().add(navOnTop); }
        if (nav != null) nav.setTranslationX(-now.getTranslationX());
        Spring spring = Spring.smooth(0.45f);
        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(spring.duration()); anim.setInterpolator(spring);
        anim.addUpdateListener(v -> {
            float t = (float) v.getAnimatedValue();
            if (push) { now.setTranslationX(w * (1 - t)); old.setTranslationX(-0.3f * w * t); old.setAlpha(1f - 0.15f * t); }
            else { old.setTranslationX(w * t); now.setTranslationX(-0.3f * w * (1 - t)); }
            if (nav != null) { nav.setTranslationX(-now.getTranslationX()); }
            if (navOnTop != null) content.invalidate();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator x) {
                if (navOnTop != null) content.getOverlay().remove(navOnTop);
                if (nav != null) nav.setTranslationX(0f);
                content.removeView(old); shot.recycle();
                now.setTranslationX(0f); now.setAlpha(1f); moving.setElevation(0f); under.setElevation(0f);
            }
        });
        anim.start();
    }

    /** Switches between the four top-level tabs in place, like a tab bar: no slide between the screens. */
    public static void tab(android.app.Activity from, android.content.Intent intent, boolean finish) {
        // Tabs dissolve into each other (iOS 26 tab bar): the new screen fades in over the old one, no slide.
        from.startActivity(intent);
        if (finish) from.finish();
        from.overridePendingTransition(com.keplerslicer.app.R.anim.kv_tab_in, com.keplerslicer.app.R.anim.kv_tab_hold);
    }
    private static void padScrollables(View v, int navTop, int fallback) {
        if (v.getVisibility() == View.GONE) return;
        if (v instanceof android.widget.ScrollView || v instanceof android.widget.AbsListView) {
            int[] xy = new int[2]; v.getLocationInWindow(xy); int bottom = xy[1] + v.getHeight();
            int need = Math.max(0, bottom - navTop); if (need == 0 && bottom == xy[1]) need = fallback;
            Object done = v.getTag(R.id.kv3_nav_pad); int base = done instanceof int[] ? ((int[]) done)[0] : v.getPaddingBottom();
            if (!(done instanceof int[]) || ((int[]) done)[1] != need) {
                v.setTag(R.id.kv3_nav_pad, new int[]{base, need});
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), base + need);
                if (v instanceof android.view.ViewGroup) ((android.view.ViewGroup) v).setClipToPadding(false);
            }
            return;
        }
        if (v instanceof android.view.ViewGroup) { android.view.ViewGroup g = (android.view.ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) padScrollables(g.getChildAt(i), navTop, fallback); }
    }

    /** Page background: #383d46 with the three original Figma glows. */
    public Drawable backdrop() { return SettingsHubUi.backdrop(context); }

    public TextView text(CharSequence value, float sp, int weight, int color) {
        TextView t = new TextView(context); t.setText(value); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); t.setTextColor(color);
        GlassTypography.apply(t, weight); t.setIncludeFontPadding(false); t.setLineSpacing(0, 1.3f); return t;
    }
    public TextView line(CharSequence value, float sp, int weight, int color) {
        TextView t = text(value, sp, weight, color); t.setSingleLine(true); t.setEllipsize(TextUtils.TruncateAt.END); return t;
    }

    public GradientDrawable shape(int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke); return g;
    }
    public GradientDrawable glass(float radiusDp) { return shape(GLASS, GLASS_STROKE, radiusDp); }
    public GradientDrawable field(float radiusDp) { return shape(FIELD, FIELD_STROKE, radiusDp); }

    /** Ripple feedback over the given resting background; mask follows the radius. */
    public void pressable(View v, Drawable resting, float radiusDp) {
        v.setBackground(LiquidPress.apply(v, resting, dp(radiusDp)));
        v.setClickable(true); v.setFocusable(true);
    }

    public Drawable icon(int res, int color) {
        Drawable d = context.getDrawable(res).mutate(); if (color != WHITE) d.setColorFilter(color, PorterDuff.Mode.SRC_IN); return d;
    }
    public ImageView image(int res, float sizeDp, int color) {
        ImageView i = new ImageView(context); i.setImageDrawable(icon(res, color)); i.setScaleType(ImageView.ScaleType.FIT_CENTER);
        i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); i.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))); return i;
    }

    public LinearLayout row() { LinearLayout l = new LinearLayout(context); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    public LinearLayout column() { LinearLayout l = new LinearLayout(context); l.setOrientation(LinearLayout.VERTICAL); return l; }
    public LinearLayout.LayoutParams lp(int wDp, int hDp) { return new LinearLayout.LayoutParams(wDp < 0 ? wDp : dp(wDp), hDp < 0 ? hDp : dp(hDp)); }
    public LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1); }
    public View spacer(int hDp) { View v = new View(context); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(hDp))); return v; }
    public View hairline() { View v = new View(context); v.setBackgroundColor(DIVIDER); v.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)))); return v; }

    /** Circular glass button, e.g. the 44dp header Wi-Fi button (22dp icon) or the 56dp add button (26dp icon). */
    public FrameLayout glassButton(int iconRes, float sizeDp, float iconDp, String description, Runnable action) {
        FrameLayout f = new FrameLayout(context);
        f.addView(image(iconRes, iconDp, WHITE), new FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER));
        pressable(f, glass(sizeDp / 2), sizeDp / 2); f.setContentDescription(description);
        if (action != null) f.setOnClickListener(v -> action.run()); f.setMinimumWidth(dp(sizeDp)); f.setMinimumHeight(dp(sizeDp)); return f;
    }
    /** Adds the 8dp mint status dot used on the Wi-Fi button (frame x28 y8 of 44). */
    public View statusDot(FrameLayout button) {
        View dot = new View(context); dot.setBackground(shape(MINT, 0, 4));
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(8), dp(8), Gravity.TOP | Gravity.END); p.topMargin = dp(8); p.setMarginEnd(dp(8));
        button.addView(dot, p); return dot;
    }

    /** Chip: h36 r18 px14, 13sp/600. Active chips are white with on-active text. */
    public TextView chip(String label, boolean active, Runnable action) {
        TextView t = line(label, 13, 600, active ? ON_ACTIVE : WHITE); t.setGravity(Gravity.CENTER); t.setPadding(dp(14), 0, dp(14), 0);
        t.setMinHeight(dp(36)); t.setMinimumHeight(dp(36));
        if (action != null) { pressable(t, active ? shape(WHITE, 0, 18) : glass(18), 18); t.setOnClickListener(v -> action.run()); }
        else t.setBackground(active ? shape(WHITE, 0, 18) : glass(18));
        t.setSelected(active); return t;
    }
    public void setChipActive(TextView chip, boolean active) {
        chip.setTextColor(active ? ON_ACTIVE : WHITE); chip.setSelected(active);
        Drawable resting = active ? shape(WHITE, 0, 18) : glass(18);
        if (chip.isClickable()) pressable(chip, resting, 18); else chip.setBackground(resting);
    }

    /** Printer header (node 3:38): 52dp glass thumb r16 + name 17/700, nozzle badge, chevron 18, swatch + 12/500 subtitle. */
    public static final class Header {
        public final LinearLayout view, printer; public final TextView name, nozzle, material; public final View swatch; public final FrameLayout wifi;
        Header(LinearLayout view, LinearLayout printer, TextView name, TextView nozzle, View swatch, TextView material, FrameLayout wifi) {
            this.view = view; this.printer = printer; this.name = name; this.nozzle = nozzle; this.swatch = swatch; this.material = material; this.wifi = wifi;
        }
    }
    public Header header(String printerDescription, Runnable printer, String wifiDescription, Runnable wifi) {
        LinearLayout bar = row(); bar.setMinimumHeight(dp(52));
        LinearLayout left = row(); pressable(left, null, 16); left.setContentDescription(printerDescription); left.setOnClickListener(v -> printer.run());
        FrameLayout thumb = new FrameLayout(context); thumb.setBackground(glass(16));
        thumb.addView(image(R.drawable.kv3_printer, 26, WHITE), new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        left.addView(thumb, lp(52, 52));
        LinearLayout info = column(); LinearLayout.LayoutParams ip = weight(); ip.leftMargin = dp(12); left.addView(info, ip);
        LinearLayout top = row(); info.addView(top);
        TextView name = line("", 17, 700, WHITE); top.addView(name, new LinearLayout.LayoutParams(-2, -2, 1));
        ((LinearLayout.LayoutParams) name.getLayoutParams()).weight = 0; name.setMaxWidth(dp(190));
        TextView nozzle = line("", 11, 600, WHITE); nozzle.setGravity(Gravity.CENTER); nozzle.setPadding(dp(6), 0, dp(6), 0);
        GradientDrawable badge = new GradientDrawable(); badge.setCornerRadius(dp(6)); badge.setStroke(Math.round(1.5f * density), WHITE); nozzle.setBackground(badge);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, dp(20)); np.leftMargin = dp(6); top.addView(nozzle, np); nozzle.setVisibility(View.GONE);
        LinearLayout.LayoutParams cp = lp(18, 18); cp.leftMargin = dp(6); top.addView(image(R.drawable.kv3_chevron_down, 18, WHITE), cp);
        LinearLayout bottom = row(); LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.topMargin = dp(2); info.addView(bottom, bp);
        View swatch = new View(context); swatch.setBackground(shape(0xFFE9EEF5, 0, 3)); bottom.addView(swatch, lp(10, 10));
        TextView material = line("", 12, 500, SECONDARY); LinearLayout.LayoutParams mp = weight(); mp.leftMargin = dp(6); bottom.addView(material, mp);
        bar.addView(left, weight());
        FrameLayout wifiButton = glassButton(R.drawable.kv3_wifi, 44, 22, wifiDescription, wifi);
        LinearLayout.LayoutParams wp = lp(44, 44); wp.leftMargin = dp(12); bar.addView(wifiButton, wp);
        return new Header(bar, left, name, nozzle, swatch, material, wifiButton);
    }
    public void setNozzle(Header h, String value) { h.nozzle.setText(value); h.nozzle.setVisibility(value == null || value.isEmpty() ? View.GONE : View.VISIBLE); }

    /** Mode toolbar (node 3:59): glass r28 p5 gap4; 44dp r22 items; white active item; 28x1 divider before Tools. */
    public static final class ModeToolbar {
        public final LinearLayout view; public final FrameLayout[] items; final int[] icons; final KeplerUi ui;
        ModeToolbar(KeplerUi ui, LinearLayout view, FrameLayout[] items, int[] icons) { this.ui = ui; this.view = view; this.items = items; this.icons = icons; }
        public void select(int index) {
            for (int i = 0; i < items.length; i++) {
                boolean on = i == index; FrameLayout f = items[i]; f.setSelected(on);
                ((ImageView) f.getChildAt(0)).setImageDrawable(ui.icon(icons[i], on ? ON_ACTIVE : WHITE));
                ui.pressable(f, on ? ui.shape(WHITE, 0, 22) : null, 22);
            }
        }
    }
    public ModeToolbar modeToolbar(String[] descriptions, Runnable[] actions) {
        int[] icons = {R.drawable.kv3_cube, R.drawable.kv3_layers, R.drawable.kv3_object_tools};
        LinearLayout bar = column(); bar.setGravity(Gravity.CENTER_HORIZONTAL); bar.setPadding(dp(5), dp(5), dp(5), dp(5)); bar.setBackground(glass(28));
        FrameLayout[] items = new FrameLayout[3];
        for (int i = 0; i < 3; i++) {
            if (i == 2) { View d = new View(context); d.setBackgroundColor(DIVIDER); LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(dp(28), Math.max(1, dp(1))); dp1.topMargin = dp(4); bar.addView(d, dp1); }
            FrameLayout f = new FrameLayout(context); ImageView art = image(icons[i], 22, WHITE);
            f.addView(art, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)); f.setContentDescription(descriptions[i]);
            final Runnable a = actions[i]; f.setOnClickListener(v -> { if (a != null) a.run(); });
            LinearLayout.LayoutParams p = lp(44, 44); if (i > 0) p.topMargin = dp(4); bar.addView(f, p); items[i] = f;
        }
        ModeToolbar t = new ModeToolbar(this, bar, items, icons); t.select(0); return t;
    }

    /** Object card (node 3:97): glass r24 h68, pl8 pr14 gap12, 52dp field thumb r16, 16/700 + 12/500, chevron 20. */
    public static final class ObjectCard { public final LinearLayout view; public final TextView name, details; public final LinearLayout labels; ObjectCard(LinearLayout v, TextView n, TextView d, LinearLayout l) { view = v; name = n; details = d; labels = l; } }
    public ObjectCard objectCard(Runnable action) {
        LinearLayout card = row(); card.setPadding(dp(8), dp(8), dp(14), dp(8)); card.setMinimumHeight(dp(68)); pressable(card, glass(24), 24);
        if (action != null) card.setOnClickListener(v -> action.run());
        FrameLayout thumb = new FrameLayout(context); thumb.setBackground(shape(FIELD, 0, 16));
        thumb.addView(image(R.drawable.kv3_model_thumb, 44, WHITE), new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER)); card.addView(thumb, lp(52, 52));
        LinearLayout labels = column(); LinearLayout.LayoutParams lpp = weight(); lpp.leftMargin = dp(12); lpp.rightMargin = dp(12); card.addView(labels, lpp);
        TextView name = line("", 16, 700, WHITE); labels.addView(name);
        TextView details = line("", 12, 500, SECONDARY); LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(-1, -2); dp2.topMargin = dp(2); labels.addView(details, dp2);
        card.addView(image(R.drawable.kv3_chevron_right, 20, WHITE));
        return new ObjectCard(card, name, details, labels);
    }

    /** Primary action (node 3:114): white r26 h52, 18dp icon + 16/700 on-active label, gap 8. */
    public LinearLayout primaryButton(int iconRes, String label, Runnable action) {
        LinearLayout b = row(); b.setGravity(Gravity.CENTER); b.setMinimumHeight(dp(52)); b.setPadding(dp(16), dp(8), dp(16), dp(8));
        pressable(b, shape(WHITE, 0, 26), 26);
        if (iconRes != 0) b.addView(image(iconRes, 18, ON_ACTIVE));
        TextView t = line(label, 16, 700, ON_ACTIVE); LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2); if (iconRes != 0) tp.leftMargin = dp(8); b.addView(t, tp);
        b.setContentDescription(label); if (action != null) b.setOnClickListener(v -> action.run()); return b;
    }
    /** Secondary glass action with the same geometry (node 3:436 style). */
    public LinearLayout secondaryButton(int iconRes, String label, float heightDp, float radiusDp, Runnable action) {
        LinearLayout b = row(); b.setGravity(Gravity.CENTER); b.setMinimumHeight(dp(heightDp)); b.setPadding(dp(12), dp(6), dp(12), dp(6));
        pressable(b, glass(radiusDp), radiusDp);
        if (iconRes != 0) b.addView(image(iconRes, 18, WHITE));
        TextView t = line(label, 15, 700, WHITE); LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2); if (iconRes != 0) tp.leftMargin = dp(8); b.addView(t, tp);
        b.setContentDescription(label); if (action != null) b.setOnClickListener(v -> action.run()); return b;
    }
    public void setEnabled(View v, boolean enabled) { v.setEnabled(enabled); v.setAlpha(enabled ? 1f : .42f); }

    /** Sheet (node 3:205): glass r28, padding 14, children spaced 10. */
    public LinearLayout sheet() { LinearLayout s = column(); s.setPadding(dp(14), dp(14), dp(14), dp(14)); s.setBackground(glass(28)); return s; }
    public LinearLayout.LayoutParams below(int topDp) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(topDp); return p; }

    /** Tool tile (node 3:210): 76x64 r16, 22dp icon, 11/600 label; white when active. */
    public LinearLayout toolTile(int iconRes, String label, boolean active, Runnable action) {
        LinearLayout t = column(); t.setGravity(Gravity.CENTER); t.setMinimumHeight(dp(64)); t.setPadding(dp(2), dp(6), dp(2), dp(6));
        pressable(t, active ? shape(WHITE, 0, 16) : glass(16), 16); t.setSelected(active);
        t.addView(image(iconRes, 22, active ? ON_ACTIVE : WHITE));
        TextView l = text(label, 11, 600, active ? ON_ACTIVE : WHITE); l.setGravity(Gravity.CENTER); l.setMaxLines(2); l.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(4); t.addView(l, p);
        t.setContentDescription(label); if (action != null) t.setOnClickListener(v -> action.run()); return t;
    }
    /** Lays tiles out four per row with 8dp gaps, matching the 4-column Tool grid at 366dp. */
    public LinearLayout grid(View[] tiles, int columns) {
        LinearLayout g = column(); LinearLayout current = null;
        for (int i = 0; i < tiles.length; i++) {
            if (i % columns == 0) { current = row(); current.setGravity(Gravity.TOP); g.addView(current, i == 0 ? new LinearLayout.LayoutParams(-1, -2) : below(8)); }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); if (i % columns > 0) p.leftMargin = dp(8); current.addView(tiles[i], p);
        }
        int rest = tiles.length % columns; if (rest > 0 && current != null) for (int i = rest; i < columns; i++) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 1, 1); p.leftMargin = dp(8); current.addView(new View(context), p); }
        return g;
    }

    /** Axis field (node 3:270): field r14 h44 px10, 12/800 coloured axis letter, 14/600 value. */
    public static final class AxisField { public final LinearLayout view; public final EditText value; AxisField(LinearLayout v, EditText e) { view = v; value = e; } }
    public AxisField axisField(String axis, int color, String value, String description) {
        LinearLayout f = row(); f.setPadding(dp(10), 0, dp(10), 0); f.setMinimumHeight(dp(44)); f.setBackground(field(14));
        f.addView(text(axis, 12, 800, color));
        EditText e = new EditText(context); e.setBackground(null); e.setPadding(0, 0, 0, 0); e.setSingleLine(true); e.setText(value);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14); e.setTextColor(WHITE); GlassTypography.apply(e, 600); e.setIncludeFontPadding(false);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE); e.setContentDescription(description); e.setMinHeight(dp(44));
        LinearLayout.LayoutParams p = weight(); p.leftMargin = dp(6); f.addView(e, p); return new AxisField(f, e);
    }

    /** Page title row (Settings 6:368): 24/800 title + trailing 44dp actions, min height 48. */
    public LinearLayout titleBar(String title, View... actions) {
        LinearLayout bar = row(); bar.setMinimumHeight(dp(48));
        TextView t = text(title, 24, 800, WHITE); t.setMaxLines(2); if (android.os.Build.VERSION.SDK_INT >= 28) t.setAccessibilityHeading(true);
        bar.addView(t, weight());
        for (View a : actions) { LinearLayout.LayoutParams p = lp(44, 44); p.leftMargin = dp(12); bar.addView(a, p); }
        return bar;
    }
    /** Back + centred title + trailing action header used by sub-screens (Printers, Filament, Connection, Import, Export). */
    public LinearLayout subHeader(String title, String backDescription, Runnable back, View trailing) {
        LinearLayout bar = row(); bar.setMinimumHeight(dp(48));
        bar.addView(glassButton(R.drawable.kv3_back, 44, 22, backDescription, back), lp(44, 44));
        TextView t = line(title, 18, 700, WHITE); t.setGravity(Gravity.CENTER); if (android.os.Build.VERSION.SDK_INT >= 28) t.setAccessibilityHeading(true);
        LinearLayout.LayoutParams tp = weight(); tp.leftMargin = dp(12); tp.rightMargin = dp(12); bar.addView(t, tp);
        if (trailing != null) bar.addView(trailing, lp(44, 44)); else bar.addView(new View(context), lp(44, 44));
        return bar;
    }

    /** Segmented scope (Process 7:493): field r16 p3, selected white r13 h32, 13/700 captions. */
    public LinearLayout segmented(String[] labels, int selected, Runnable[] actions) {
        LinearLayout row = row(); row.setPadding(dp(3), dp(3), dp(3), dp(3)); row.setBackground(shape(FIELD, 0, 16));
        for (int i = 0; i < labels.length; i++) {
            boolean on = i == selected; TextView t = line(labels[i], 13, 700, on ? ON_ACTIVE : WHITE); t.setGravity(Gravity.CENTER); t.setPadding(dp(12), 0, dp(12), 0); t.setMinHeight(dp(32));
            if (on) t.setBackground(shape(WHITE, 0, 13)); else pressable(t, null, 13);
            final Runnable a = actions[i]; t.setSelected(on); t.setOnClickListener(v -> { if (a != null) a.run(); });
            row.addView(t, new LinearLayout.LayoutParams(-2, dp(32)));
        }
        return row;
    }
    /** White header pill (Process "Save" 7:489): h44 r22 px16, 15/700 on-active. */
    public TextView pill(String label, Runnable action) {
        TextView t = line(label, 15, 700, ON_ACTIVE); t.setGravity(Gravity.CENTER); t.setPadding(dp(16), 0, dp(16), 0); t.setMinHeight(dp(44));
        pressable(t, shape(WHITE, 0, 22), 22); t.setOnClickListener(v -> action.run()); return t;
    }

    /** Section caption: 12/700 secondary, uppercase tracking like the Figma group labels. */
    public TextView caption(String label) {
        TextView t = text(label.toUpperCase(java.util.Locale.getDefault()), 12, 700, SECONDARY); t.setLetterSpacing(.06f);
        if (android.os.Build.VERSION.SDK_INT >= 28) t.setAccessibilityHeading(true); return t;
    }
    public LinearLayout card(float radiusDp) { LinearLayout c = column(); c.setBackground(glass(radiusDp)); return c; }

    /** List row inside a card: optional 32dp tile icon, title 15/600, optional subtitle 12/500, trailing view. */
    public LinearLayout listRow(int iconRes, String title, String subtitle, View trailing, Runnable action) {
        LinearLayout r = row(); r.setPadding(dp(16), dp(10), dp(14), dp(10)); r.setMinimumHeight(dp(56));
        if (iconRes != 0) { FrameLayout tile = new FrameLayout(context); tile.setBackground(shape(GLASS, 0, 10)); tile.addView(image(iconRes, 18, WHITE), new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER)); r.addView(tile, lp(32, 32)); }
        LinearLayout col = column(); LinearLayout.LayoutParams cp = weight(); cp.leftMargin = iconRes != 0 ? dp(12) : 0; cp.rightMargin = dp(10); r.addView(col, cp);
        TextView t = text(title, 15, 600, WHITE); t.setMaxLines(2); t.setEllipsize(TextUtils.TruncateAt.END); col.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) { TextView s = text(subtitle, 12, 500, SECONDARY); s.setMaxLines(3); s.setEllipsize(TextUtils.TruncateAt.END); LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2); sp.topMargin = dp(2); col.addView(s, sp); }
        if (trailing != null) r.addView(trailing);
        if (action != null) { pressable(r, null, 0); r.setOnClickListener(v -> action.run()); }
        return r;
    }
    /** Switch art (46x28, original mint/gray states) as an accessible checkable row control. */
    public ImageView switchArt(boolean on) { ImageView s = image(on ? R.drawable.kv3_switch_on : R.drawable.kv3_switch_off, 0, WHITE); s.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(28))); return s; }
    public void setSwitch(ImageView s, boolean on) { s.setImageResource(on ? R.drawable.kv3_switch_on : R.drawable.kv3_switch_off); }

    /** Small status pill (Preview 3:337): glass r18 h36, 8dp dot + 13/600 label. */
    public LinearLayout statusPill(int dotColor, String label) {
        LinearLayout p = row(); p.setPadding(dp(15), 0, dp(15), 0); p.setMinimumHeight(dp(36)); p.setBackground(glass(18));
        View dot = new View(context); dot.setBackground(shape(dotColor, 0, 4)); p.addView(dot, lp(8, 8));
        TextView t = line(label, 13, 600, WHITE); LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2); tp.leftMargin = dp(6); p.addView(t, tp);
        p.setContentDescription(label); return p;
    }
}
