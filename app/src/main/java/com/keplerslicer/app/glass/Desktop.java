package com.keplerslicer.app.glass;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

/**
 * Tablet mode follows desktop OrcaSlicer: dialogs and menus are compact opaque windows (centred, or dropped from their
 * button) with actions on the right, never bottom sheets. Colours and type stay Kepler's.
 */
public final class Desktop {
    private Desktop() {}

    /** Tablet interface (Preferences → Interface → Display mode). */
    public static boolean is(Context c) {
        return "tablet".equals(c.getSharedPreferences("app_preferences", Context.MODE_PRIVATE).getString("interface_mode", "mobile"));
    }

    /** Opaque window surface of desktop dialogs and menus. */
    public static GradientDrawable surface(Context c, float radiusDp) {
        float d = c.getResources().getDisplayMetrics().density;
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF2E323A); g.setCornerRadius(radiusDp * d); g.setStroke(Math.max(1, Math.round(d)), 0x33FFFFFF);
        return g;
    }
}
