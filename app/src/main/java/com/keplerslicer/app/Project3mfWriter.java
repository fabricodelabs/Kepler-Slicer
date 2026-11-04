package com.keplerslicer.app;

import android.content.Context;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Export "Project 3MF" (Figma 6:612). A 3MF source is copied unchanged (plates, objects and settings).
 * An STL source is written as a core-spec 3MF with the Tools transform baked into the vertices and the
 * object placed on the plate like the slicer does; profile settings are not embedded.
 */
final class Project3mfWriter {
    private Project3mfWriter() {}

    static String write(Context context, OutputStream out, File project, File stl) throws IOException {
        if (project != null) {
            long n = 0; try (InputStream in = new FileInputStream(project)) { byte[] b = new byte[65536]; int r; while ((r = in.read(b)) != -1) { out.write(b, 0, r); n += r; } }
            return context.getString(R.string.kv3_3mf_copied, android.text.format.Formatter.formatShortFileSize(context, n));
        }
        if (stl == null) throw new IOException(context.getString(R.string.kv3_3mf_unavailable));
        ModelTransform t = ModelTransform.decode(context.getSharedPreferences("current_model", Context.MODE_PRIVATE).getString("transform", ""));
        android.content.SharedPreferences ui = context.getSharedPreferences("kepler_v3_ui", Context.MODE_PRIVATE);
        double bedX = ui.getFloat("bed_center_x", 0), bedY = ui.getFloat("bed_center_y", 0);
        float[] v = readStl(stl);
        double ax = Math.toRadians(t.rx), ay = Math.toRadians(t.ry), az = Math.toRadians(t.rz), cx = Math.cos(ax), sx = Math.sin(ax), cy = Math.cos(ay), sy = Math.sin(ay), cz = Math.cos(az), sz = Math.sin(az);
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i < v.length; i += 3) {
            double x = v[i] * t.sx, y = v[i + 1] * t.sy, z = v[i + 2] * t.sz;
            double y1 = y * cx - z * sx, z1 = y * sx + z * cx, x2 = x * cy + z1 * sy, z2 = -x * sy + z1 * cy, x3 = x2 * cz - y1 * sz, y3 = x2 * sz + y1 * cz;
            v[i] = (float) x3; v[i + 1] = (float) y3; v[i + 2] = (float) z2;
            lo[0] = Math.min(lo[0], x3); hi[0] = Math.max(hi[0], x3); lo[1] = Math.min(lo[1], y3); hi[1] = Math.max(hi[1], y3); lo[2] = Math.min(lo[2], z2); hi[2] = Math.max(hi[2], z2);
        }
        double dx = bedX + t.offsetX - (lo[0] + hi[0]) / 2, dy = bedY + t.offsetY - (lo[1] + hi[1]) / 2, dz = -lo[2];
        ZipOutputStream zip = new ZipOutputStream(out);
        entry(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"model\" ContentType=\"application/vnd.ms-package.3dmanufacturing-3dmodel+xml\"/></Types>\n");
        entry(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Target=\"/3D/3dmodel.model\" Id=\"rel0\" Type=\"http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel\"/></Relationships>\n");
        zip.putNextEntry(new ZipEntry("3D/3dmodel.model"));
        Writer w = new java.io.BufferedWriter(new java.io.OutputStreamWriter(zip, StandardCharsets.UTF_8), 1 << 16);
        String name = stl.getName().replaceAll("[<>&\"']", "_");
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<model unit=\"millimeter\" xml:lang=\"en-US\" xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\">\n");
        w.write("<metadata name=\"Application\">Kepler Slicer</metadata>\n<resources><object id=\"1\" type=\"model\" name=\"" + name + "\"><mesh><vertices>\n");
        for (int i = 0; i < v.length; i += 3) w.write(String.format(Locale.ROOT, "<vertex x=\"%.5f\" y=\"%.5f\" z=\"%.5f\"/>\n", v[i] + dx, v[i + 1] + dy, v[i + 2] + dz));
        w.write("</vertices><triangles>\n");
        for (int i = 0; i < v.length / 3; i += 3) w.write("<triangle v1=\"" + i + "\" v2=\"" + (i + 1) + "\" v3=\"" + (i + 2) + "\"/>\n");
        w.write("</triangles></mesh></object></resources>\n<build><item objectid=\"1\"/></build>\n</model>\n");
        w.flush(); zip.closeEntry(); zip.finish();
        return context.getString(R.string.kv3_3mf_written, v.length / 9);
    }

    private static void entry(ZipOutputStream zip, String name, String text) throws IOException { zip.putNextEntry(new ZipEntry(name)); zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }

    /** Full-resolution STL reader (binary or ASCII), millimetres, 9 floats per triangle. */
    static float[] readStl(File file) throws IOException {
        long length = file.length(); if (length < 84) throw new IOException("STL слишком короткий.");
        long count; try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            byte[] head = new byte[84]; in.readFully(head);
            count = (head[80] & 0xFFL) | (head[81] & 0xFFL) << 8 | (head[82] & 0xFFL) << 16 | (head[83] & 0xFFL) << 24;
            if (84 + count * 50 == length) {
                if (count > 20_000_000) throw new IOException("Слишком много треугольников.");
                float[] v = new float[(int) count * 9]; byte[] rec = new byte[50]; java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(rec).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                for (int t = 0; t < count; t++) { in.readFully(rec); for (int k = 0; k < 9; k++) v[t * 9 + k] = bb.getFloat(12 + k * 4); }
                return v;
            }
        }
        java.util.ArrayList<Float> list = new java.util.ArrayList<>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new FileInputStream(file), StandardCharsets.US_ASCII))) {
            String line; while ((line = r.readLine()) != null) { line = line.trim(); if (!line.startsWith("vertex")) continue; String[] p = line.split("\\s+"); if (p.length < 4) continue; for (int k = 1; k <= 3; k++) list.add(Float.parseFloat(p[k])); }
        }
        if (list.isEmpty() || list.size() % 9 != 0) throw new IOException("Не удалось прочитать STL.");
        float[] v = new float[list.size()]; for (int i = 0; i < v.length; i++) v[i] = list.get(i); return v;
    }
}
