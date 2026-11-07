package com.keplerslicer.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class GcodeAnalysisTest {
    /** Bambu Lab G-code marks layers with "; CHANGE_LAYER" and features with "; FEATURE:" (OrcaSlicer for BBL printers). */
    @Test public void bambuMarkersGiveLayersAndFeatures() throws Exception {
        String gcode = "G90\nM83\n; CHANGE_LAYER\n; Z_HEIGHT: 0.2\n; FEATURE: Outer wall\nG1 X10 Y10 Z0.2 F3000\nG1 X20 Y10 E1 F1500\n"
            + "; CHANGE_LAYER\n; Z_HEIGHT: 0.4\n; FEATURE: Sparse infill\nG1 Z0.4\nG1 X20 Y20 E1\n";
        File f = File.createTempFile("bbl", ".gcode"); f.deleteOnExit();
        Files.write(f.toPath(), gcode.getBytes(StandardCharsets.US_ASCII));
        GcodeAnalysis a = GcodeAnalysis.read(f);
        assertEquals(2, a.layers.size());
        assertEquals(0.4, a.layers.get(1).z, 1e-9);
        assertEquals(GcodeAnalysis.OUTER_WALL, a.layers.get(0).types[0]);
        assertEquals(GcodeAnalysis.SPARSE_INFILL, a.layers.get(1).types[0]);
    }
}
