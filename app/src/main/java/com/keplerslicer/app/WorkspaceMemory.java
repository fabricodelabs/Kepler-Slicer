package com.keplerslicer.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * What the workspace remembers between launches: the camera for the open model and the last bed of the selected
 * printer, so the plate opens exactly as the user left it instead of re-fitting the view.
 */
final class WorkspaceMemory {
    private static final String PREFS = "kepler_v3_ui";
    private final SharedPreferences prefs;
    private final Context context;

    WorkspaceMemory(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Camera (yaw, pitch, zoom, panX, panY) stored for this model, or null. */
    float[] camera(String modelFileName) {
        // While the model is still being read, the stored model name tells which camera to keep.
        String model = modelFileName != null ? modelFileName : context.getSharedPreferences("current_model", 0).getString("file", null);
        if (model == null || !model.equals(prefs.getString("camera_model", null))) return null;
        double[] v = numbers(prefs.getString("camera", null));
        if (v == null || v.length != 5) return null;
        float[] c = new float[5];
        for (int i = 0; i < 5; i++) {
            if (!Double.isFinite(v[i])) return null;
            c[i] = (float) v[i];
        }
        return c;
    }

    void saveCamera(String modelFileName, float[] camera) {
        if (modelFileName == null || camera == null) return;
        double[] d = new double[camera.length];
        for (int i = 0; i < camera.length; i++) d[i] = camera[i];
        prefs.edit().putString("camera_model", modelFileName).putString("camera", join(d)).apply();
    }

    /** Last bed loaded for this printer key, or null. */
    BedGeometry bed(String key) {
        double[] raw = key.equals(prefs.getString("bed_key", null)) ? numbers(prefs.getString("bed_raw", null)) : null;
        if (raw == null) return null;
        try { return new BedGeometry(raw); } catch (IllegalArgumentException ignored) { return null; }
    }

    void saveBed(String key, BedGeometry bed) {
        prefs.edit().putFloat("bed_center_x", (float) bed.centerX).putFloat("bed_center_y", (float) bed.centerY)
            .putString("bed_key", key).putString("bed_raw", join(bed.source)).apply();
    }

    static String join(double[] v) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < v.length; i++) { if (i > 0) b.append(','); b.append(v[i]); }
        return b.toString();
    }

    static double[] numbers(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            String[] p = s.split(",");
            double[] v = new double[p.length];
            for (int i = 0; i < p.length; i++) v[i] = Double.parseDouble(p[i]);
            return v;
        } catch (NumberFormatException e) { return null; }
    }
}
