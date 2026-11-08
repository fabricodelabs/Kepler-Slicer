package com.keplerslicer.app;

import java.util.ArrayList;
import java.util.List;

/**
 * The Kepler Slicer mark: a "K" built from three convex shapes and cut into horizontal print layers, with a small
 * extrusion behind each layer. One source for the launcher icon, the splash animation and the brand files
 * (tools/branding/BrandKit.java); coordinates are in a 100 x 100 design grid, y down.
 */
public final class BrandGeometry {
    private BrandGeometry() {}

    /** Stem, upper arm and lower leg before slicing (convex polygons, x/y pairs). */
    public static final float[][] SHAPES = {
        {21f, 18f, 35f, 18f, 35f, 82f, 21f, 82f},
        {35f, 50f, 61f, 18f, 77.7f, 18f, 35f, 70.6f},
        {50f, 47f, 79f, 82f, 62.1f, 82f, 40f, 55.3f},
    };
    public static final float TOP = 18f, BOTTOM = 82f, LEFT = 21f, RIGHT = 79f;
    /** Print layers, the gap between them, the extrusion offset and the light edge on top of each layer. */
    public static final int LAYERS = 5;
    public static final float GAP = 2.6f, DEPTH = 2.4f, EDGE = 1.1f;

    /** Brand colours (ARGB). Mint is the app accent (KeplerUi.MINT). */
    public static final int MINT = 0xFF5EE0C8, FACE_TOP = 0xFF92F7E2, FACE_BOTTOM = 0xFF2DB3A5, SIDE = 0xFF145C58,
            EDGE_LIGHT = 0x99E6FFF9, GRAPHITE_TOP = 0xFF2B3038, GRAPHITE_BOTTOM = 0xFF14171C, INK = 0xFF16181C,
            LIGHT_FACE = 0xFF119282, LIGHT_SIDE = 0xFF0B5C53;

    public static float layerHeight() { return (BOTTOM - TOP - GAP * (LAYERS - 1)) / LAYERS; }
    public static float layerTop(int i) { return TOP + i * (layerHeight() + GAP); }

    /** Convex polygons of one layer (0 = top), each shape clipped to the layer's band. */
    public static List<float[]> layer(int i) { float t = layerTop(i); return band(t, t + layerHeight()); }

    /** Thin light edge along the top of a layer. */
    public static List<float[]> edge(int i) { float t = layerTop(i); return band(t, t + EDGE); }

    /** All shapes clipped to the horizontal band [top, bottom]. */
    public static List<float[]> band(float top, float bottom) {
        List<float[]> out = new ArrayList<>();
        for (float[] s : SHAPES) {
            float[] c = clip(clip(s, top, true), bottom, false);
            if (c.length >= 6) out.add(c);
        }
        return out;
    }

    /** Sutherland-Hodgman against one horizontal line: keep y >= line (below) or y <= line (above). */
    private static float[] clip(float[] p, float line, boolean keepBelow) {
        List<Float> out = new ArrayList<>(); int n = p.length / 2;
        for (int k = 0; k < n; k++) {
            float ax = p[2 * k], ay = p[2 * k + 1], bx = p[2 * ((k + 1) % n)], by = p[2 * ((k + 1) % n) + 1];
            boolean ain = keepBelow ? ay >= line : ay <= line, bin = keepBelow ? by >= line : by <= line;
            if (ain) { out.add(ax); out.add(ay); }
            if (ain != bin) { float t = (line - ay) / (by - ay); out.add(ax + t * (bx - ax)); out.add(line); }
        }
        float[] r = new float[out.size()]; for (int k = 0; k < r.length; k++) r[k] = out.get(k); return r;
    }
}
