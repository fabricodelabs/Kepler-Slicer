package com.keplerslicer.app;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Audit SEC-02: 3MF containers are checked before the native ZIP reader. */
public class ArchiveGuardTest {
    private static File zip(String[] names, byte[][] bodies) throws IOException {
        File f = File.createTempFile("guard", ".3mf"); f.deleteOnExit();
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
            for (int i = 0; i < names.length; i++) { z.putNextEntry(new ZipEntry(names[i])); z.write(bodies[i]); z.closeEntry(); }
        }
        return f;
    }
    private static void rejected(File f, long max, String what) {
        try { ArchiveGuard.check(f, max); fail(what); } catch (IOException expected) { }
    }

    @Test public void ordinaryProjectPasses() throws IOException {
        File f = zip(new String[]{"[Content_Types].xml", "3D/3dmodel.model", "Metadata/project_settings.config"},
            new byte[][]{"<Types/>".getBytes(StandardCharsets.UTF_8), "<model><vertex x=\"1\"/></model>".getBytes(StandardCharsets.UTF_8), "{}".getBytes(StandardCharsets.UTF_8)});
        ArchiveGuard.check(f);
    }

    @Test public void decompressionBombIsRejected() throws IOException {
        // 64 MB of zeros deflate to about 64 KB: a ratio above 1000 once past the 16 MB floor.
        File f = File.createTempFile("bomb", ".3mf"); f.deleteOnExit();
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
            z.setLevel(9); z.putNextEntry(new ZipEntry("3D/3dmodel.model")); byte[] zeros = new byte[1 << 20];
            for (int i = 0; i < 64; i++) z.write(zeros); z.closeEntry();
        }
        assertTrue(f.length() < 1 << 20);
        rejected(f, ArchiveGuard.MAX_TOTAL, "a 64 MB zero-filled entry must be refused");
    }

    @Test public void totalInflatedSizeIsCapped() throws IOException {
        File f = zip(new String[]{"a.xml", "b.xml"}, new byte[][]{new byte[4000], new byte[4000]});
        rejected(f, 6000, "8000 inflated bytes exceed a 6000 byte cap");
    }

    @Test public void unsafeEntryNamesAreRejected() throws IOException {
        for (String bad : new String[]{"../evil.xml", "Metadata/../../evil", "/abs.xml", "dir\\win.xml"}) {
            File f = zip(new String[]{bad}, new byte[][]{"x".getBytes(StandardCharsets.UTF_8)});
            rejected(f, ArchiveGuard.MAX_TOTAL, "entry name " + bad);
        }
    }

    @Test public void notAZipIsRejected() throws IOException {
        File f = File.createTempFile("plain", ".3mf"); f.deleteOnExit();
        try (FileOutputStream o = new FileOutputStream(f)) { o.write("solid cube\nendsolid".getBytes(StandardCharsets.UTF_8)); }
        rejected(f, ArchiveGuard.MAX_TOTAL, "plain text is not a 3MF");
    }
}
