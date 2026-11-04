package com.orcamobile.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.orcamobile.app.glass.KeplerUi;

/**
 * Device · printing (Figma node 4:336). Live printer telemetry, camera and job control are not
 * implemented in this build, so every live value shows an honest empty state and controls explain that.
 */
public final class DeviceActivity extends Activity {
    private KeplerScreen screen; private KeplerUi ui; private TextView status, jobPercent, jobDetail; private View statusDot, jobFill; private LinearLayout jobTrack; private final TextView[] tempValues = new TextView[3], tempTargets = new TextView[3];

    @Override public void onCreate(Bundle state) {
        MdUi.prepare(this); super.onCreate(state);
        View wifi = new KeplerUi(this).glassButton(R.drawable.kv3_wifi, 44, 22, getString(R.string.kv3_connections), () -> startActivity(new Intent(this, NetworkActivity.class)));
        OrcaProfileCatalog.Profile printer = OrcaProfileCatalog.loadSelection(this);
        String name = printer == null ? getString(R.string.kv3_choose_printer) : (printer.model == null || printer.model.isEmpty() ? printer.name : printer.model);
        screen = new KeplerScreen(this, name, this::finish, wifi, 0); ui = screen.ui;
        final int white = KeplerUi.WHITE, secondary = KeplerUi.SECONDARY;

        LinearLayout bar = (LinearLayout) screen.column.getChildAt(0); TextView title = (TextView) bar.getChildAt(1);
        LinearLayout.LayoutParams tp = (LinearLayout.LayoutParams) title.getLayoutParams(); bar.removeView(title);
        LinearLayout head = ui.column(); head.setGravity(Gravity.CENTER_HORIZONTAL); head.addView(title, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout st = ui.row(); st.setGravity(Gravity.CENTER); View dot = new View(this); dot.setBackground(ui.shape(0x99FFFFFF, 0, 4)); st.addView(dot, ui.lp(8, 8)); statusDot = dot;
        status = ui.line(getString(R.string.kv3_device_offline), 12, 500, secondary); LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2); sp.leftMargin = ui.dp(6); st.addView(status, sp);
        head.addView(st, new LinearLayout.LayoutParams(-1, -2)); bar.addView(head, 1, tp);
        LinearLayout content = screen.content;

        FrameLayout camera = new FrameLayout(this); camera.setBackground(ui.shape(0xFF15171B, KeplerUi.GLASS_STROKE, 24));
        LinearLayout center = ui.column(); center.setGravity(Gravity.CENTER);
        FrameLayout start = ui.glassButton(R.drawable.kv3_play, 56, 22, getString(R.string.kv3_start_live_view), this::toggleCamera); cameraCenter = center;
        center.addView(start, ui.lp(56, 56));
        TextView live = ui.line(getString(R.string.kv3_start_live_view), 13, 600, white); live.setGravity(Gravity.CENTER); center.addView(live, ui.below(8));
        cameraImage = new android.widget.ImageView(this); cameraImage.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP); cameraImage.setVisibility(View.GONE); cameraImage.setContentDescription(getString(R.string.kv3_camera)); camera.addView(cameraImage, new FrameLayout.LayoutParams(-1, -1)); camera.setClipToOutline(true);
        camera.addView(center, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout badge = ui.row(); badge.setPadding(ui.dp(12), 0, ui.dp(12), 0); badge.setMinimumHeight(ui.dp(30)); badge.setBackground(ui.shape(0x73000000, 0, 15));
        View rec = new View(this); rec.setBackground(ui.shape(0xFFFF6B6B, 0, 4)); badge.addView(rec, ui.lp(8, 8));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2); bp.leftMargin = ui.dp(6); badge.addView(ui.line(getString(R.string.kv3_camera), 12, 500, white), bp);
        FrameLayout.LayoutParams badgePos = new FrameLayout.LayoutParams(-2, ui.dp(30), Gravity.TOP | Gravity.START); badgePos.setMargins(ui.dp(11), ui.dp(11), 0, 0); camera.addView(badge, badgePos);
        FrameLayout full = new FrameLayout(this); full.addView(ui.image(R.drawable.kv3_fullscreen, 22, white), new FrameLayout.LayoutParams(ui.dp(22), ui.dp(22), Gravity.CENTER));
        ui.pressable(full, ui.shape(0x73000000, 0, 22), 22); full.setContentDescription(getString(R.string.kv3_fullscreen)); full.setOnClickListener(v -> { if (cameraImage.getVisibility() == View.VISIBLE) cameraImage.setScaleType(cameraImage.getScaleType() == android.widget.ImageView.ScaleType.CENTER_CROP ? android.widget.ImageView.ScaleType.FIT_CENTER : android.widget.ImageView.ScaleType.CENTER_CROP); else toggleCamera(); });
        FrameLayout.LayoutParams fullPos = new FrameLayout.LayoutParams(ui.dp(44), ui.dp(44), Gravity.TOP | Gravity.END); fullPos.setMargins(0, ui.dp(7), ui.dp(7), 0); camera.addView(full, fullPos);
        content.addView(camera, new LinearLayout.LayoutParams(-1, ui.dp(200)));

        LinearLayout job = ui.column(); job.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14)); job.setBackground(ui.glass(24));
        LinearLayout jobHead = ui.row();
        FrameLayout thumb = new FrameLayout(this); thumb.setBackground(ui.shape(KeplerUi.FIELD, 0, 14));
        thumb.addView(ui.image(R.drawable.kv3_model_thumb, 40, white), new FrameLayout.LayoutParams(ui.dp(40), ui.dp(40), Gravity.CENTER)); jobHead.addView(thumb, ui.lp(48, 48));
        LinearLayout labels = ui.column(); LinearLayout.LayoutParams lp = ui.weight(); lp.leftMargin = ui.dp(12); lp.rightMargin = ui.dp(12); jobHead.addView(labels, lp);
        String model = getSharedPreferences("last_slice_result", MODE_PRIVATE).getString("model", "");
        labels.addView(ui.line(model.isEmpty() ? getString(R.string.kv3_no_job) : model.replaceAll("(?i)\\.(stl|obj|3mf|step|stp)$", "") + ".gcode", 16, 700, white));
        jobDetail = ui.line(getString(R.string.kv3_no_job_detail), 12, 500, secondary); labels.addView(jobDetail, ui.below(2));
        jobPercent = ui.line("—", 20, 700, white); jobHead.addView(jobPercent);
        job.addView(jobHead);
        jobTrack = ui.row(); jobTrack.setBackground(ui.shape(0x3DFFFFFF, 0, 4)); jobFill = new View(this); jobFill.setBackground(ui.shape(KeplerUi.MINT, 0, 4)); jobTrack.addView(jobFill, new LinearLayout.LayoutParams(0, -1, 0)); jobTrack.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1)); job.addView(jobTrack, ui.below(14)); ((LinearLayout.LayoutParams) jobTrack.getLayoutParams()).height = ui.dp(8);
        LinearLayout actions = ui.row();
        LinearLayout pause = ui.primaryButton(R.drawable.kv3_pause, getString(R.string.kv3_pause), null); pause.setMinimumHeight(ui.dp(48)); ui.setEnabled(pause, false);
        ((TextView) pause.getChildAt(1)).setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15); actions.addView(pause, ui.weight());
        LinearLayout stop = ui.secondaryButton(R.drawable.kv3_stop, getString(R.string.kv3_stop), 48, 24, null); ui.setEnabled(stop, false);
        LinearLayout.LayoutParams stp = ui.weight(); stp.leftMargin = ui.dp(8); actions.addView(stop, stp);
        job.addView(actions, ui.below(14));
        content.addView(job, ui.below(10));

        screen.section(getString(R.string.kv3_temperatures), null, null);
        LinearLayout temps = ui.row(); temps.setGravity(Gravity.TOP);
        int[] tl = {R.string.kv3_nozzle_temp, R.string.kv3_bed_temp, R.string.kv3_chamber_temp};
        for (int i = 0; i < 3; i++) {
            LinearLayout box = ui.column(); box.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12)); box.setBackground(ui.glass(20));
            box.addView(ui.line(getString(tl[i]), 12, 500, secondary)); tempValues[i] = ui.line("—", 18, 700, white); box.addView(tempValues[i], ui.below(2)); tempTargets[i] = ui.line(getString(R.string.kv3_no_data), 12, 500, secondary); box.addView(tempTargets[i], ui.below(2));
            LinearLayout.LayoutParams p = ui.weight(); if (i > 0) p.leftMargin = ui.dp(8); temps.addView(box, p);
        }
        content.addView(temps, new LinearLayout.LayoutParams(-1, -2));

        screen.section(getString(R.string.kv3_filament_ams), null, null);
        LinearLayout ams = ui.row(); ams.setGravity(Gravity.TOP);
        OrcaProcessFilamentCatalog.Profile filament = OrcaProcessFilamentCatalog.loadSelection(this, OrcaProcessFilamentCatalog.FILAMENT);
        for (int i = 0; i < 4; i++) {
            boolean loaded = i == 0 && filament != null;
            LinearLayout slot = ui.column(); slot.setGravity(Gravity.CENTER); slot.setPadding(ui.dp(6), ui.dp(10), ui.dp(6), ui.dp(10));
            slot.setBackground(ui.shape(KeplerUi.GLASS, loaded ? KeplerUi.MINT : KeplerUi.GLASS_STROKE, 18));
            View c = new View(this); GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL);
            if (loaded) { g.setColor(0xFFE9EEF5); g.setStroke(Math.max(1, ui.dp(1)), 0x66FFFFFF); } else { g.setColor(0); g.setStroke(Math.max(1, ui.dp(1.5f)), 0xB3FFFFFF, ui.dp(3), ui.dp(3)); }
            c.setBackground(g); slot.addView(c, ui.lp(28, 28));
            String label = (i + 1) + " · " + (loaded ? shortType(filament.name) : getString(R.string.kv3_slot_empty));
            TextView t = ui.line(label, 12, 600, white); t.setGravity(Gravity.CENTER); slot.addView(t, ui.below(6));
            slot.setContentDescription(getString(R.string.kv3_slot, i + 1) + ", " + label);
            LinearLayout.LayoutParams p = ui.weight(); if (i > 0) p.leftMargin = ui.dp(8); ams.addView(slot, p);
        }
        content.addView(ams, new LinearLayout.LayoutParams(-1, -2));

        screen.section(getString(R.string.kv3_controls), null, null);
        LinearLayout list = ui.card(20);
        View speed = ui.listRow(0, getString(R.string.kv3_print_speed), null, selectField(getString(R.string.kv3_speed_standard)), () -> screen.unavailable(getString(R.string.kv3_print_speed)));
        list.addView(speed); list.addView(ui.hairline());
        list.addView(ui.listRow(0, getString(R.string.kv3_chamber_light), null, ui.switchArt(false), () -> screen.unavailable(getString(R.string.kv3_chamber_light)))); list.addView(ui.hairline());
        list.addView(ui.listRow(0, getString(R.string.kv3_aux_fan), null, ui.switchArt(false), () -> screen.unavailable(getString(R.string.kv3_aux_fan)))); list.addView(ui.hairline());
        list.addView(ui.listRow(0, getString(R.string.kv3_move_axes), null, ui.image(R.drawable.kv3_chevron_right, 20, white), () -> screen.unavailable(getString(R.string.kv3_move_axes))));
        content.addView(list, new LinearLayout.LayoutParams(-1, -2));

        refreshStatus();
    }

    private NetworkHttp.Cancellation cancellation;
    private android.widget.ImageView cameraImage; private View cameraCenter; private volatile Thread cameraThread; private volatile java.net.HttpURLConnection cameraConnection;
    @Override protected void onDestroy() { if (cancellation != null) cancellation.cancel(); stopCamera(); super.onDestroy(); }
    @Override protected void onPause() { stopCamera(); super.onPause(); }

    /** Live view: MJPEG stream of the selected OctoPrint/Moonraker host (webcam/?action=stream), read-only. */
    private void toggleCamera() {
        if (cameraThread != null) { stopCamera(); return; }
        Thread worker = new Thread(() -> {
            java.net.HttpURLConnection c = null;
            try {
                NetworkStore.Connection conn = NetworkStore.load(this).selected();
                if (conn == null) throw new java.io.IOException(getString(R.string.kv3_device_offline));
                if (conn.endpoint.type != PrinterEndpoint.Type.OCTOPRINT && conn.endpoint.type != PrinterEndpoint.Type.MOONRAKER) throw new java.io.IOException(getString(R.string.kv3_camera_unsupported, label(conn.endpoint.type)));
                java.net.URI uri = conn.endpoint.url("webcam/?action=stream"); NetworkPolicy.checkUri(this, uri);
                c = (java.net.HttpURLConnection) uri.toURL().openConnection(); cameraConnection = c; c.setConnectTimeout(8000); c.setReadTimeout(15000);
                if (c.getResponseCode() / 100 != 2) throw new java.io.IOException("HTTP " + c.getResponseCode());
                runOnUiThread(() -> { if (!isDestroyed()) { cameraImage.setVisibility(View.VISIBLE); cameraCenter.setVisibility(View.GONE); } });
                java.io.InputStream in = new java.io.BufferedInputStream(c.getInputStream(), 65536);
                java.io.ByteArrayOutputStream frame = new java.io.ByteArrayOutputStream(); int prev = -1, b; boolean inside = false;
                while (!Thread.currentThread().isInterrupted() && (b = in.read()) != -1) {
                    if (!inside) { if (prev == 0xFF && b == 0xD8) { inside = true; frame.reset(); frame.write(0xFF); frame.write(0xD8); } }
                    else { frame.write(b); if (prev == 0xFF && b == 0xD9) { inside = false; final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(frame.toByteArray(), 0, frame.size()); if (bmp != null) runOnUiThread(() -> { if (!isDestroyed() && cameraThread != null) cameraImage.setImageBitmap(bmp); }); }
                        else if (frame.size() > 8 * 1024 * 1024) { inside = false; frame.reset(); } }
                    prev = b;
                }
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) return;
                String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> { if (isDestroyed()) return; cameraThread = null; cameraImage.setVisibility(View.GONE); cameraCenter.setVisibility(View.VISIBLE); screen.info(getString(R.string.kv3_camera), m); });
            } finally { if (c != null) c.disconnect(); }
        }, "device-camera");
        cameraThread = worker; worker.start();
    }
    private void stopCamera() { Thread t = cameraThread; cameraThread = null; if (t != null) t.interrupt(); final java.net.HttpURLConnection open = cameraConnection; cameraConnection = null; if (open != null) new Thread(open::disconnect, "camera-close").start(); if (cameraImage != null) { cameraImage.setVisibility(View.GONE); cameraCenter.setVisibility(View.VISIBLE); } }

    /** Read-only status of the selected saved printer (no commands are sent). Offline/stealth modes are respected. */
    private void refreshStatus() {
        final NetworkHttp.Cancellation cancel = new NetworkHttp.Cancellation(); cancellation = cancel;
        new Thread(() -> {
            NetworkStore.Connection c = null; String text; boolean online = false; org.json.JSONObject raw = null; String state = null;
            try {
                c = NetworkStore.load(this).selected();
                if (c == null) text = getString(R.string.kv3_device_offline);
                else {
                    runOnUiThread(() -> { if (!isDestroyed()) status.setText(getString(R.string.kv3_device_checking)); });
                    NetworkPolicy.requireEndpoint(this, c.endpoint);
                    PrinterHostClient.Status s = new PrinterHostClient(c.endpoint).status(cancel);
                    state = s.state; online = !"disconnected".equalsIgnoreCase(s.state);
                    text = getString(R.string.kv3_device_online, label(c.endpoint.type), s.state);
                    try { if (s.rawJson != null && s.rawJson.trim().startsWith("{")) raw = new org.json.JSONObject(s.rawJson); } catch (org.json.JSONException ignored) { }
                }
            } catch (Exception e) { text = getString(R.string.kv3_device_unreachable, c == null ? "" : label(c.endpoint.type), e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
            final String value = text; final boolean ok = online; final org.json.JSONObject json = raw; final PrinterEndpoint.Type type = c == null ? null : c.endpoint.type; final String st = state;
            runOnUiThread(() -> { if (isDestroyed()) return; status.setText(value); statusDot.setBackground(ui.shape(ok ? KeplerUi.MINT : 0x99FFFFFF, 0, 4)); if (json != null && type != null) applyTelemetry(type, json, st); });
        }, "device-status").start();
    }
    private static String label(PrinterEndpoint.Type type) { String n = type.name().replace('_', ' '); return n.charAt(0) + n.substring(1).toLowerCase(java.util.Locale.ROOT); }

    /** OctoPrint /api/printer and PrusaLink /api/v1/status carry temperatures; PrusaLink and Moonraker carry job progress. */
    private void applyTelemetry(PrinterEndpoint.Type type, org.json.JSONObject json, String state) {
        double[][] t = new double[3][];
        if (type == PrinterEndpoint.Type.OCTOPRINT) {
            org.json.JSONObject temps = json.optJSONObject("temperature");
            if (temps != null) { String[] keys = {"tool0", "bed", "chamber"}; for (int i = 0; i < 3; i++) { org.json.JSONObject o = temps.optJSONObject(keys[i]); if (o != null) t[i] = new double[]{o.optDouble("actual", Double.NaN), o.optDouble("target", Double.NaN)}; } }
        } else if (type == PrinterEndpoint.Type.PRUSALINK) {
            org.json.JSONObject p = json.optJSONObject("printer");
            if (p != null) { t[0] = new double[]{p.optDouble("temp_nozzle", Double.NaN), p.optDouble("target_nozzle", Double.NaN)}; t[1] = new double[]{p.optDouble("temp_bed", Double.NaN), p.optDouble("target_bed", Double.NaN)}; }
            org.json.JSONObject job = json.optJSONObject("job");
            if (job != null && job.has("progress")) showProgress(job.optDouble("progress", Double.NaN) / 100.0, state, job.optLong("time_remaining", -1));
        } else if (type == PrinterEndpoint.Type.MOONRAKER) {
            org.json.JSONObject r = json.optJSONObject("result"), s = r == null ? null : r.optJSONObject("status"), sd = s == null ? null : s.optJSONObject("virtual_sdcard"), ps = s == null ? null : s.optJSONObject("print_stats");
            if (sd != null && sd.has("progress")) showProgress(sd.optDouble("progress", Double.NaN), state, -1);
            if (ps != null && !ps.optString("filename", "").isEmpty() && jobDetail != null) jobDetail.setText(ps.optString("filename"));
        }
        for (int i = 0; i < 3; i++) {
            if (t[i] == null || !Double.isFinite(t[i][0])) continue;
            tempValues[i].setText(String.format(java.util.Locale.getDefault(), "%.0f°", t[i][0]));
            tempTargets[i].setText(Double.isFinite(t[i][1]) && t[i][1] > 0 ? getString(R.string.kv3_target, String.format(java.util.Locale.getDefault(), "%.0f°", t[i][1])) : getString(R.string.kv3_no_target));
        }
    }
    private void showProgress(double fraction, String state, long secondsLeft) {
        if (!Double.isFinite(fraction)) return; fraction = Math.max(0, Math.min(1, fraction));
        jobPercent.setText(Math.round(fraction * 100) + "%");
        ((LinearLayout.LayoutParams) jobFill.getLayoutParams()).weight = (float) fraction; ((LinearLayout.LayoutParams) jobTrack.getChildAt(1).getLayoutParams()).weight = (float) (1 - fraction); jobTrack.requestLayout();
        String left = secondsLeft > 0 ? " · " + getString(R.string.kv3_time_left, secondsLeft / 3600, (secondsLeft % 3600) / 60) : "";
        jobDetail.setText((state == null ? "" : state) + left);
    }

    private View selectField(String value) {
        LinearLayout s = ui.row(); s.setPadding(ui.dp(12), 0, ui.dp(10), 0); s.setMinimumHeight(ui.dp(36)); s.setBackground(ui.field(12));
        s.addView(ui.line(value, 14, 600, KeplerUi.WHITE)); LinearLayout.LayoutParams p = ui.lp(16, 16); p.leftMargin = ui.dp(8); s.addView(ui.image(R.drawable.kv3_chevron_down, 16, KeplerUi.WHITE), p); return s;
    }
    private static String shortType(String name) { int at = name.indexOf(" @"); String n = at > 0 ? name.substring(0, at) : name; return n.length() > 12 ? n.substring(0, 12) + "…" : n; }
}
