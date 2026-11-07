package com.keplerslicer.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TabletLayoutTest {
    @Test public void unknownValuesFallBackToDefaults() {
        assertEquals(TabletLayout.State.EXPANDED, TabletLayout.state(null));
        assertEquals(TabletLayout.State.EXPANDED, TabletLayout.state("wide"));
        assertEquals(TabletLayout.State.COLLAPSED, TabletLayout.state("collapsed"));
        assertEquals("hidden", TabletLayout.name(TabletLayout.State.HIDDEN));
        assertEquals("prepare", TabletLayout.section("nope"));
        assertEquals("device", TabletLayout.section("device"));
    }

    @Test public void widthIsClampedToTheWindow() {
        assertEquals(TabletLayout.DEFAULT_PANEL_DP, TabletLayout.clampWidth(0, 1280));
        assertEquals(TabletLayout.MAX_PANEL_DP, TabletLayout.clampWidth(900, 1280));
        assertEquals(TabletLayout.MIN_PANEL_DP, TabletLayout.clampWidth(100, 1280));
        // A phone: the panel never grows wider than the window allows.
        assertTrue(TabletLayout.clampWidth(560, 411) <= 411 - TabletLayout.HANDLE_DP);
    }

    @Test public void narrowWindowsFloatThePanel() {
        assertTrue(TabletLayout.overlay(411, 320));
        assertFalse(TabletLayout.overlay(1280, 360));
        assertEquals(0, TabletLayout.docked(TabletLayout.State.EXPANDED, 411, 320));
        assertEquals(360 + TabletLayout.HANDLE_DP, TabletLayout.docked(TabletLayout.State.EXPANDED, 1280, 360));
        assertEquals(TabletLayout.RAIL_DP, TabletLayout.docked(TabletLayout.State.COLLAPSED, 1280, 360));
        assertEquals(0, TabletLayout.docked(TabletLayout.State.HIDDEN, 1280, 360));
    }
}
