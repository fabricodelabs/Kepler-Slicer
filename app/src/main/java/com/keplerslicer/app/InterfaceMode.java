package com.keplerslicer.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Interface preferences kept with the other app preferences. The mode is chosen only by the user (Preferences →
 * Interface → Display mode); the default is the mobile interface on every device.
 */
final class InterfaceMode {
    static final String KEY_MODE = "interface_mode", KEY_STATE = "tablet_sidebar_state",
            KEY_WIDTH = "tablet_sidebar_width_dp", KEY_SECTION = "tablet_sidebar_section";
    static final String MOBILE = "mobile", TABLET = "tablet";

    private InterfaceMode() {}

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("app_preferences", Context.MODE_PRIVATE); }

    static boolean tablet(Context c) { return TABLET.equals(prefs(c).getString(KEY_MODE, MOBILE)); }

    /** Tablet mode runs in landscape only, the mobile interface in portrait only (either way up). */
    static void applyOrientation(android.app.Activity a) {
        int want = tablet(a) ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
        if (a.getRequestedOrientation() != want) a.setRequestedOrientation(want);
    }

    static TabletLayout.State state(Context c) { return TabletLayout.state(prefs(c).getString(KEY_STATE, null)); }
    static void state(Context c, TabletLayout.State s) { prefs(c).edit().putString(KEY_STATE, TabletLayout.name(s)).apply(); }

    static int width(Context c) { return prefs(c).getInt(KEY_WIDTH, TabletLayout.DEFAULT_PANEL_DP); }
    static void width(Context c, int dp) { prefs(c).edit().putInt(KEY_WIDTH, dp).apply(); }

    static String section(Context c) { return TabletLayout.section(prefs(c).getString(KEY_SECTION, null)); }
    static void section(Context c, String s) { prefs(c).edit().putString(KEY_SECTION, TabletLayout.section(s)).apply(); }
}
