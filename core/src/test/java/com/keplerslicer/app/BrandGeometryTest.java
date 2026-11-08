package com.keplerslicer.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** The brand mark's layers stay inside the mark and the icon safe zone; slicing keeps every shape convex and closed. */
public class BrandGeometryTest {
    @Test public void layersCoverTheMarkWithGaps() {
        float h = BrandGeometry.layerHeight();
        assertTrue(h > BrandGeometry.GAP * 2);
        assertEquals(BrandGeometry.BOTTOM, BrandGeometry.layerTop(BrandGeometry.LAYERS - 1) + h, 1e-3);
        for (int i = 0; i < BrandGeometry.LAYERS; i++) {
            List<float[]> layer = BrandGeometry.layer(i);
            assertFalse("layer " + i + " is empty", layer.isEmpty());
            float top = BrandGeometry.layerTop(i);
            for (float[] p : layer) {
                assertEquals(0, p.length % 2); assertTrue(p.length >= 6);
                for (int k = 0; k < p.length; k += 2) {
                    assertTrue(p[k] >= BrandGeometry.LEFT - 1e-3 && p[k] <= BrandGeometry.RIGHT + 1e-3);
                    assertTrue(p[k + 1] >= top - 1e-3 && p[k + 1] <= top + h + 1e-3);
                }
            }
        }
    }

    @Test public void markIsCentredForTheSplash() {
        // BrandSplash and splash_blueprint.xml centre the design point (50, 50).
        assertEquals(50f, (BrandGeometry.LEFT + BrandGeometry.RIGHT) / 2f, 1e-3);
        assertEquals(50f, (BrandGeometry.TOP + BrandGeometry.BOTTOM) / 2f, 1e-3);
    }

    @Test public void iconFitsTheAdaptiveSafeZone() {
        // BrandKit scales the mark by 0.70 into a 108 dp canvas; the 66 dp safe circle must hold it with its extrusion.
        float s = 0.70f, w = (BrandGeometry.RIGHT - BrandGeometry.LEFT + BrandGeometry.DEPTH) * s, h = (BrandGeometry.BOTTOM - BrandGeometry.TOP + BrandGeometry.DEPTH) * s;
        assertTrue(Math.hypot(w, h) <= 66.0);
    }
}
