package com.keplerslicer.app;

import android.content.Context;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Import "Shapes" (Figma 4:209): 20 mm watertight primitives written as binary STL in private cache. */
final class PrimitiveShapes {
    private static final double SIZE = 20, R = 10;
    private static final int SEGMENTS = 64, RINGS = 32;
    private PrimitiveShapes() {}

    static File write(Context context, int kind, String name) throws IOException {
        List<double[]> t = new ArrayList<>();
        switch (kind) {
            case 0: cube(t); break;
            case 1: revolve(t, new double[][]{{0, 0}, {R, 0}, {R, SIZE}, {0, SIZE}}); break;
            case 2: revolve(t, new double[][]{{0, 0}, {R, 0}, {0, SIZE}}); break;
            default: { double[][] p = new double[RINGS + 1][]; for (int i = 0; i <= RINGS; i++) { double a = -Math.PI / 2 + Math.PI * i / RINGS; p[i] = new double[]{i == 0 || i == RINGS ? 0 : R * Math.cos(a), R + R * Math.sin(a)}; } revolve(t, p); }
        }
        File dir = new File(context.getCacheDir(), "shapes"); if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException(L.t("Не удалось подготовить фигуру."));
        File out = new File(dir, name + ".stl");
        try (OutputStream o = new BufferedOutputStream(new FileOutputStream(out))) {
            byte[] header = new byte[80]; byte[] label = ("Kepler Slicer " + name).getBytes(java.nio.charset.StandardCharsets.US_ASCII); System.arraycopy(label, 0, header, 0, Math.min(80, label.length)); o.write(header);
            ByteBuffer b = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN); b.putInt(t.size()); o.write(b.array(), 0, 4);
            for (double[] v : t) {
                b.clear(); double ux = v[3] - v[0], uy = v[4] - v[1], uz = v[5] - v[2], wx = v[6] - v[0], wy = v[7] - v[1], wz = v[8] - v[2];
                double nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx, len = Math.sqrt(nx * nx + ny * ny + nz * nz); if (len > 0) { nx /= len; ny /= len; nz /= len; }
                b.putFloat((float) nx).putFloat((float) ny).putFloat((float) nz); for (double c : v) b.putFloat((float) c); b.putShort((short) 0); o.write(b.array(), 0, 50);
            }
        }
        return out;
    }
    private static void tri(List<double[]> t, double[] a, double[] b, double[] c) { t.add(new double[]{a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]}); }
    private static void cube(List<double[]> t) {
        double s = SIZE;
        double[][] p = {{0, 0, 0}, {s, 0, 0}, {s, s, 0}, {0, s, 0}, {0, 0, s}, {s, 0, s}, {s, s, s}, {0, s, s}};
        int[][] f = {{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 1, 5, 4}, {1, 2, 6, 5}, {2, 3, 7, 6}, {3, 0, 4, 7}};
        for (int[] q : f) { tri(t, p[q[0]], p[q[1]], p[q[2]]); tri(t, p[q[0]], p[q[2]], p[q[3]]); }
    }
    /** Revolves a (radius, z) profile ordered from bottom to top around Z; zero-radius points become poles. */
    private static void revolve(List<double[]> t, double[][] profile) {
        for (int k = 0; k + 1 < profile.length; k++) {
            double r0 = profile[k][0], z0 = profile[k][1], r1 = profile[k + 1][0], z1 = profile[k + 1][1];
            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = 2 * Math.PI * i / SEGMENTS, a1 = 2 * Math.PI * (i + 1) / SEGMENTS;
                double[] p00 = {R + r0 * Math.cos(a0), R + r0 * Math.sin(a0), z0}, p01 = {R + r0 * Math.cos(a1), R + r0 * Math.sin(a1), z0};
                double[] p10 = {R + r1 * Math.cos(a0), R + r1 * Math.sin(a0), z1}, p11 = {R + r1 * Math.cos(a1), R + r1 * Math.sin(a1), z1};
                if (r0 > 0) tri(t, p00, p01, r1 > 0 ? p11 : p10);
                if (r1 > 0) tri(t, p00, p11, p10);
            }
        }
    }
}
