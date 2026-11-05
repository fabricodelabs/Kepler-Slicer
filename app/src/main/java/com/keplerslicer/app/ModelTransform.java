package com.keplerslicer.app;

/** Immutable object-space transform; view gestures remain separate camera controls.
 * Angles are degrees about STL X/Y/Z; scales act before Rz * Ry * Rx.
 * XY offsets are millimetres relative to the printable-area bounding-box center.
 */
final class ModelTransform {
    private static final double MAX_OFFSET_MM = 10_000.0;
    final double rx, ry, rz, sx, sy, sz, offsetX, offsetY;

    ModelTransform(double rx, double ry, double rz, double sx, double sy, double sz, double offsetX, double offsetY) {
        double[] values = { rx, ry, rz, sx, sy, sz, offsetX, offsetY };
        for (double v : values) if (!Double.isFinite(v)) throw new IllegalArgumentException(L.t("Параметры преобразования должны быть конечными."));
        if (sx < .001 || sy < .001 || sz < .001 || sx > 100 || sy > 100 || sz > 100)
            throw new IllegalArgumentException(L.t("Масштаб по каждой оси должен быть от 0,001 до 100."));
        if (Math.abs(offsetX) > MAX_OFFSET_MM || Math.abs(offsetY) > MAX_OFFSET_MM)
            throw new IllegalArgumentException(L.t("Смещение не должно превышать 10 000 мм."));
        this.rx = Math.IEEEremainder(rx, 360); this.ry = Math.IEEEremainder(ry, 360); this.rz = Math.IEEEremainder(rz, 360);
        this.sx = sx; this.sy = sy; this.sz = sz; this.offsetX = offsetX; this.offsetY = offsetY;
    }
    static ModelTransform identity() { return new ModelTransform(0, 0, 0, 1, 1, 1, 0, 0); }

    String encode() {
        return rx + "," + ry + "," + rz + "," + sx + "," + sy + "," + sz + "," + offsetX + "," + offsetY;
    }
    static ModelTransform decode(String value) {
        if (value == null || value.isEmpty()) return identity();
        String[] fields = value.split(",", -1);
        if (fields.length != 8) throw new IllegalArgumentException(L.t("Не удалось прочитать преобразование модели."));
        try {
            double[] v = new double[8];
            for (int i = 0; i < 8; i++) v[i] = Double.parseDouble(fields[i]);
            return new ModelTransform(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]);
        } catch (NumberFormatException e) { throw new IllegalArgumentException(L.t("Не удалось прочитать преобразование модели."), e); }
    }

    /** Stable JNI payload: radians XYZ, positive scales XYZ, XY offset in mm. */
    double[] toNativeArguments() {
        return new double[] {Math.toRadians(rx), Math.toRadians(ry), Math.toRadians(rz), sx, sy, sz, offsetX, offsetY};
    }

    /**
     * Takes StlMesh.vertices and its original size; returns a new normalized buffer.
     * The transformed XY bounding box is centered like the engine, then moved by the
     * supplied millimetre offsets. Z is visually centered for the camera; native
     * placement instead drops the transformed mesh to the bed. This preview does
     * not show a plate and cannot establish whether the object fits.
     * Run off the UI thread for large meshes.
     */
    float[] previewVertices(float[] vertices, double[] size) {
        if (vertices == null || vertices.length == 0 || vertices.length % 9 != 0 || size == null || size.length != 3)
            throw new IllegalArgumentException(L.t("Некорректная геометрия модели."));
        double maxSize = Math.max(size[0], Math.max(size[1], size[2]));
        if (!Double.isFinite(maxSize) || maxSize <= 0) throw new IllegalArgumentException(L.t("Модель не имеет размеров."));
        double ax = Math.toRadians(rx), ay = Math.toRadians(ry), az = Math.toRadians(rz);
        double cx = Math.cos(ax), sxn = Math.sin(ax), cy = Math.cos(ay), syn = Math.sin(ay), cz = Math.cos(az), szn = Math.sin(az);
        float[] result = new float[vertices.length];
        double[] lo = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] hi = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (int i = 0; i < vertices.length; i += 3) {
            // Scale, rotate around X, then Y, then Z; matches the engine's assemble_transform.
            double x = vertices[i] * sx, y = vertices[i+1] * sy, z = vertices[i+2] * sz;
            double y1 = cx*y - sxn*z, z1 = sxn*y + cx*z;
            double x2 = cy*x + syn*z1, z2 = -syn*x + cy*z1;
            double x3 = cz*x2 - szn*y1, y3 = szn*x2 + cz*y1;
            double[] v = {x3, y3, z2};
            for (int k = 0; k < 3; ++k) {
                if (!Double.isFinite(v[k])) throw new IllegalArgumentException(L.t("Некорректная геометрия после преобразования."));
                lo[k] = Math.min(lo[k], v[k]); hi[k] = Math.max(hi[k], v[k]);
                result[i+k] = (float)v[k];
            }
        }
        for (int axis = 0; axis < 3; axis++) {
            double extentMm = (hi[axis] - lo[axis]) * maxSize / 1.6;
            if (!Double.isFinite(extentMm) || extentMm > 10_000)
                throw new IllegalArgumentException(L.t("Размер преобразованной модели не должен превышать 10 000 мм по каждой оси."));
        }
        double dx = offsetX * 1.6 / maxSize - (lo[0] + hi[0]) * .5;
        double dy = offsetY * 1.6 / maxSize - (lo[1] + hi[1]) * .5;
        double dz = -(lo[2] + hi[2]) * .5;
        for (int i = 0; i < result.length; i += 3) {
            result[i] += (float)dx; result[i+1] += (float)dy; result[i+2] += (float)dz;
            if (!Float.isFinite(result[i]) || !Float.isFinite(result[i+1]) || !Float.isFinite(result[i+2]))
                throw new IllegalArgumentException(L.t("Смещение слишком велико для отображения этой модели."));
        }
        return result;
    }
}
