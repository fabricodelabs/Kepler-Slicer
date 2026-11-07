package com.keplerslicer.app;

import java.util.Locale;

/**
 * Rules of the tablet workspace sidebar, free of Android so they are unit-tested. The window size only adapts the
 * layout the user chose; it never switches between the mobile and tablet interfaces.
 */
public final class TabletLayout {
    private TabletLayout() {}

    public enum State { EXPANDED, COLLAPSED, HIDDEN }

    public static final String[] SECTIONS = {"prepare", "objects", "tools", "preview", "device"};
    public static final int RAIL_DP = 72, HANDLE_DP = 16, MIN_PANEL_DP = 280, MAX_PANEL_DP = 560, DEFAULT_PANEL_DP = 360;
    /** Narrowest 3D area kept beside a docked panel; below it the panel floats over the scene. */
    public static final int MIN_SCENE_DP = 360;

    public static State state(String value) {
        if (value == null) return State.EXPANDED;
        try { return State.valueOf(value.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return State.EXPANDED; }
    }

    public static String name(State state) { return state.name().toLowerCase(Locale.ROOT); }

    public static String section(String value) {
        for (String s : SECTIONS) if (s.equals(value)) return s;
        return SECTIONS[0];
    }

    /** Largest panel width that still leaves room for the rail and a usable margin in this window. */
    public static int maxWidth(int windowDp) {
        return Math.max(MIN_PANEL_DP, Math.min(MAX_PANEL_DP, windowDp - HANDLE_DP - 24));
    }

    public static int clampWidth(int requestedDp, int windowDp) {
        int width = requestedDp <= 0 ? DEFAULT_PANEL_DP : requestedDp;
        return Math.max(MIN_PANEL_DP, Math.min(maxWidth(windowDp), width));
    }

    /** True when an expanded panel of this width would leave too little 3D area: it then floats over the scene. */
    public static boolean overlay(int windowDp, int panelDp) {
        return windowDp - panelDp - HANDLE_DP < MIN_SCENE_DP;
    }

    /** Width of the scene's left inset for a state: what the sidebar occupies when docked. */
    public static int docked(State state, int windowDp, int panelDp) {
        if (state == State.HIDDEN) return 0;
        if (state == State.COLLAPSED) return RAIL_DP;
        // Expanded: the panel replaces the icon strip; on narrow windows it floats over the scene.
        return overlay(windowDp, panelDp) ? 0 : panelDp + HANDLE_DP;
    }
}
