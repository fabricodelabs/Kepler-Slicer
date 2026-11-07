package com.keplerslicer.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.keplerslicer.app.glass.KeplerUi;

/**
 * Device · printing (Figma node 4:336). Live telemetry, camera and job control for the selected printer;
 * every command is an explicit, confirmed action sent once.
 */
public final class DeviceActivity extends Activity {
    private final GradientDrawable[] amsDot = new GradientDrawable[4]; private final TextView[] amsText = new TextView[4]; private final LinearLayout[] amsBox = new LinearLayout[4];
    private KeplerScreen screen; private KeplerUi ui; private TextView status, jobPercent, jobDetail; private View statusDot, jobFill; private LinearLayout jobTrack; private final TextView[] tempValues = new TextView[3], tempTargets = new TextView[3];

    @Override public void onCreate(Bundle state) {
        MdUi.prepare(this); super.onCreate(state);
        View wifi = new KeplerUi(this).glassButton(R.drawable.kv3_wifi, 44, 22, getString(R.string.kv3_connections), () -> startActivity(new Intent(this, NetworkActivity.class)));
        PrinterProfileCatalog.Profile printer = PrinterProfileCatalog.loadSelection(this);
        String name = printer == null ? getString(R.string.kv3_choose_printer) : (printer.model == null || printer.model.isEmpty() ? printer.name : printer.model);
        screen = new KeplerScreen(this, name, this::finish, wifi, 0); ui = screen.ui;
        final int white = KeplerUi.WHITE, secondary = KeplerUi.SECONDARY;

        LinearLayout bar = screen.topBar; TextView title = (TextView) bar.getChildAt(1);
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
        LinearLayout pause = ui.primaryButton(R.drawable.kv3_pause, getString(R.string.kv3_pause), this::pauseOrResume); pause.setMinimumHeight(ui.dp(48)); ui.setEnabled(pause, false); pauseButton = pause;
        ((TextView) pause.getChildAt(1)).setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15); actions.addView(pause, ui.weight());
        LinearLayout stop = ui.secondaryButton(R.drawable.kv3_stop, getString(R.string.kv3_stop), 48, 24, this::confirmStop); ui.setEnabled(stop, false); stopButton = stop;
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
        final MaterialSlots.Slot[] material = MaterialSlots.load(this);
        for (int i = 0; i < 4; i++) {
            boolean loaded = material[i].filled();
            LinearLayout slot = ui.column(); slot.setGravity(Gravity.CENTER); slot.setPadding(ui.dp(6), ui.dp(10), ui.dp(6), ui.dp(10));
            slot.setBackground(ui.shape(KeplerUi.GLASS, i == 0 && loaded ? KeplerUi.MINT : KeplerUi.GLASS_STROKE, 18));
            View c = new View(this); GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL);
            if (loaded) { g.setColor(MaterialSlots.argb(material[i].colour)); g.setStroke(Math.max(1, ui.dp(1)), 0x66FFFFFF); } else { g.setColor(0); g.setStroke(Math.max(1, ui.dp(1.5f)), 0xB3FFFFFF, ui.dp(3), ui.dp(3)); }
            c.setBackground(g); slot.addView(c, ui.lp(28, 28)); amsDot[i] = g; amsBox[i] = slot;
            String label = (i + 1) + " · " + (loaded ? shortType(EngineText.clean(material[i].name)) : getString(R.string.kv3_slot_empty));
            TextView t = ui.line(label, 12, 600, white); t.setGravity(Gravity.CENTER); slot.addView(t, ui.below(6)); amsText[i] = t;
            slot.setContentDescription(getString(R.string.kv3_slot, i + 1) + ", " + label);
            LinearLayout.LayoutParams p = ui.weight(); if (i > 0) p.leftMargin = ui.dp(8); ams.addView(slot, p);
        }
        content.addView(ams, new LinearLayout.LayoutParams(-1, -2));

        screen.section(getString(R.string.kv3_controls), null, null);
        LinearLayout list = ui.card(20);
        speedField = selectField(getString(R.string.kv3_speed_standard));
        View speed = ui.listRow(0, getString(R.string.kv3_print_speed), null, speedField, this::chooseSpeed);
        list.addView(speed); list.addView(ui.hairline());
        lightSwitch = new FrameLayout(this); lightSwitch.addView(ui.switchArt(false));
        list.addView(ui.listRow(0, getString(R.string.kv3_chamber_light), null, lightSwitch, () -> toggle(true))); list.addView(ui.hairline());
        fanSwitch = new FrameLayout(this); fanSwitch.addView(ui.switchArt(false));
        list.addView(ui.listRow(0, getString(R.string.kv3_aux_fan), null, fanSwitch, () -> toggle(false))); list.addView(ui.hairline());
        list.addView(ui.listRow(0, getString(R.string.kv3_move_axes), null, ui.image(R.drawable.kv3_chevron_right, 20, white), this::moveAxes));
        content.addView(list, new LinearLayout.LayoutParams(-1, -2));

        refreshStatus();
    }

    private NetworkHttp.Cancellation cancellation;
    /** The timer skips a poll while the previous one still waits for a slow printer, so its answer is shown. */
    private volatile boolean statusRunning;
    private android.widget.ImageView cameraImage; private View cameraCenter; private volatile Thread cameraThread; private volatile java.net.HttpURLConnection cameraConnection;
    @Override protected void onDestroy() { if (cancellation != null) cancellation.cancel(); stopCamera(); super.onDestroy(); }
    @Override protected void onPause() { stopCamera(); polling = false; refresher.removeCallbacksAndMessages(null); super.onPause(); }
    @Override protected void onResume() { super.onResume(); polling = true; refresher.postDelayed(this::poll, 8000); }
    private void poll() { if (!polling || isDestroyed()) return; if (!commandRunning && !statusRunning) refreshStatus(); refresher.postDelayed(this::poll, 8000); }

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
                String m = EngineText.clean(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                runOnUiThread(() -> { if (isDestroyed()) return; cameraThread = null; cameraImage.setVisibility(View.GONE); cameraCenter.setVisibility(View.VISIBLE); screen.info(getString(R.string.kv3_camera), m); });
            } finally { if (c != null) c.disconnect(); }
        }, "device-camera");
        cameraThread = worker; worker.start();
    }
    private void stopCamera() { Thread t = cameraThread; cameraThread = null; if (t != null) t.interrupt(); final java.net.HttpURLConnection open = cameraConnection; cameraConnection = null; if (open != null) new Thread(open::disconnect, "camera-close").start(); if (cameraImage != null) { cameraImage.setVisibility(View.GONE); cameraCenter.setVisibility(View.VISIBLE); } }

    /** Read-only status of the selected saved printer (no commands are sent). Offline/stealth modes are respected. */
    private void refreshStatus() {
        if (cancellation != null) cancellation.cancel(); // never two polls at once: the newer one wins
        final NetworkHttp.Cancellation cancel = new NetworkHttp.Cancellation(); cancellation = cancel;
        statusRunning = true;
        new Thread(() -> {
            NetworkStore.Connection c = null; String text; boolean online = false; org.json.JSONObject raw = null; String state = null;
            try {
                c = NetworkStore.load(this).selected();
                if (c == null) text = getString(R.string.kv3_device_offline);
                else {
                    runOnUiThread(() -> { if (!isDestroyed()) status.setText(getString(R.string.kv3_device_checking)); });
                    NetworkPolicy.requireEndpoint(this, c.endpoint);
                    PrinterHostClient.Status s = new PrinterHostClient(NetworkStore.fresh(this, c, cancel)).status(cancel);
                    state = s.state; online = !"disconnected".equalsIgnoreCase(s.state);
                    text = getString(R.string.kv3_device_online, label(c.endpoint.type), s.state);
                    try { if (s.rawJson != null && s.rawJson.trim().startsWith("{")) raw = new org.json.JSONObject(s.rawJson); } catch (org.json.JSONException ignored) { }
                }
            } catch (Exception e) { text = getString(R.string.kv3_device_unreachable, c == null ? "" : label(c.endpoint.type), e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
            final String value = text; final boolean ok = online; final org.json.JSONObject json = raw; final PrinterEndpoint.Type type = c == null ? null : c.endpoint.type; final String st = state;
            runOnUiThread(() -> { if (cancellation == cancel) statusRunning = false; if (isDestroyed() || cancellation != cancel) return; status.setText(value); statusDot.setBackground(ui.shape(ok ? KeplerUi.MINT : 0x99FFFFFF, 0, 4)); activeType = ok ? type : null; jobState = ok ? phase(st) : Phase.UNKNOWN; if (json != null && type != null) applyTelemetry(type, json, st); updateControls(); });
        }, "device-status").start();
    }
    private static String label(PrinterEndpoint.Type type) { String n = type.name().replace('_', ' '); return n.charAt(0) + n.substring(1).toLowerCase(java.util.Locale.ROOT); }

    /** OctoPrint /api/printer and PrusaLink /api/v1/status carry temperatures; PrusaLink and Moonraker carry job progress. */
    private void applyTelemetry(PrinterEndpoint.Type type, org.json.JSONObject json, String state) {
        double[][] t = new double[3][];
        org.json.JSONObject k = json.optJSONObject("kepler"); // Duet, Repetier, Creality, Elegoo: normalised by LegacyPrinterControl
        if (k != null) {
            String[] keys = {"nozzle", "bed", "chamber"};
            for (int i = 0; i < 3; i++) { org.json.JSONArray a = k.optJSONArray(keys[i]); if (a != null && a.length() == 2) t[i] = new double[]{a.optDouble(0, Double.NaN), a.optDouble(1, Double.NaN)}; }
            if (k.has("progress")) showProgress(k.optDouble("progress", Double.NaN), state, k.optLong("seconds_left", -1));
            if (!k.optString("file", "").isEmpty() && jobDetail != null && !k.has("progress")) jobDetail.setText(k.optString("file"));
        }
        if (type == PrinterEndpoint.Type.OCTOPRINT) {
            org.json.JSONObject temps = json.optJSONObject("temperature");
            if (temps != null) { String[] keys = {"tool0", "bed", "chamber"}; for (int i = 0; i < 3; i++) { org.json.JSONObject o = temps.optJSONObject(keys[i]); if (o != null) t[i] = new double[]{o.optDouble("actual", Double.NaN), o.optDouble("target", Double.NaN)}; } }
        } else if (type == PrinterEndpoint.Type.PRUSALINK) {
            org.json.JSONObject p = json.optJSONObject("printer");
            if (p != null) { t[0] = new double[]{p.optDouble("temp_nozzle", Double.NaN), p.optDouble("target_nozzle", Double.NaN)}; t[1] = new double[]{p.optDouble("temp_bed", Double.NaN), p.optDouble("target_bed", Double.NaN)}; }
            org.json.JSONObject job = json.optJSONObject("job");
            if (job != null && job.has("progress")) showProgress(job.optDouble("progress", Double.NaN) / 100.0, state, job.optLong("time_remaining", -1));
        } else if (type == PrinterEndpoint.Type.BAMBU || type == PrinterEndpoint.Type.BAMBU_CLOUD) {
            // Bambu LAN report: temperatures, progress, job name and the first AMS unit.
            t[0] = new double[]{json.optDouble("nozzle_temper", Double.NaN), json.optDouble("nozzle_target_temper", Double.NaN)};
            t[1] = new double[]{json.optDouble("bed_temper", Double.NaN), json.optDouble("bed_target_temper", Double.NaN)};
            if (json.has("chamber_temper")) t[2] = new double[]{json.optDouble("chamber_temper", Double.NaN), Double.NaN};
            if (json.has("mc_percent")) showProgress(json.optDouble("mc_percent", Double.NaN) / 100.0, state, json.has("mc_remaining_time") ? json.optLong("mc_remaining_time", -1) * 60 : -1);
            if (!json.optString("subtask_name", "").isEmpty() && jobDetail != null) jobDetail.setText(json.optString("subtask_name"));
            AmsTrays.Tray[] trays = AmsTrays.parse(json);
            if (trays != null) { AmsTrays.save(this, trays); showTrays(trays); }
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
    /** AMS slots from the printer: tray colour, material type and the active tray. */
    private void showTrays(AmsTrays.Tray[] trays) {
        for (int i = 0; i < 4 && i < trays.length; i++) {
            if (amsDot[i] == null || amsText[i] == null || amsBox[i] == null) continue;
            AmsTrays.Tray t = trays[i];
            if (t.loaded) { amsDot[i].setColor(t.color); amsDot[i].setStroke(Math.max(1, ui.dp(1)), 0x66FFFFFF); }
            else { amsDot[i].setColor(0); amsDot[i].setStroke(Math.max(1, ui.dp(1.5f)), 0xB3FFFFFF, ui.dp(3), ui.dp(3)); }
            String label = (i + 1) + " · " + (t.loaded ? t.type : getString(R.string.kv3_slot_empty));
            amsText[i].setText(label);
            amsBox[i].setBackground(ui.shape(KeplerUi.GLASS, t.active ? KeplerUi.MINT : KeplerUi.GLASS_STROKE, 18));
            amsBox[i].setContentDescription(getString(R.string.kv3_slot, i + 1) + ", " + label);
        }
    }
    private void showProgress(double fraction, String state, long secondsLeft) {
        if (!Double.isFinite(fraction)) return; fraction = Math.max(0, Math.min(1, fraction));
        jobPercent.setText(Math.round(fraction * 100) + "%");
        ((LinearLayout.LayoutParams) jobFill.getLayoutParams()).weight = (float) fraction; ((LinearLayout.LayoutParams) jobTrack.getChildAt(1).getLayoutParams()).weight = (float) (1 - fraction); jobTrack.requestLayout();
        String left = secondsLeft > 0 ? " · " + getString(R.string.kv3_time_left, secondsLeft / 3600, (secondsLeft % 3600) / 60) : "";
        jobDetail.setText((state == null ? "" : state) + left);
    }

    // ---- Job control and printer commands: each one is an explicit, confirmed action, sent once and never retried ----
    private enum Phase { PRINTING, PAUSED, IDLE, UNKNOWN }
    private final android.os.Handler refresher = new android.os.Handler(android.os.Looper.getMainLooper());
    private volatile boolean polling, commandRunning;
    private PrinterEndpoint.Type activeType; private Phase jobState = Phase.UNKNOWN;
    private LinearLayout pauseButton, stopButton; private View speedField; private FrameLayout lightSwitch, fanSwitch; private boolean lightOn, fanOn;

    private static Phase phase(String state) {
        String s = state == null ? "" : state.toLowerCase(java.util.Locale.ROOT);
        if (s.contains("paus")) return Phase.PAUSED;
        if (s.contains("print") || s.equals("running") || s.equals("prepare") || s.equals("busy")) return Phase.PRINTING;
        if (s.equals("idle") || s.equals("operational") || s.equals("ready") || s.equals("standby") || s.equals("finish") || s.equals("finished") || s.equals("complete") || s.equals("failed") || s.equals("cancelled") || s.equals("stopped")) return Phase.IDLE;
        return Phase.UNKNOWN;
    }
    private void updateControls() {
        boolean jobs = activeType != null && PrinterHostClient.supportsJobs(activeType), active = jobState == Phase.PRINTING || jobState == Phase.PAUSED;
        ui.setEnabled(pauseButton, jobs && active && !commandRunning); ui.setEnabled(stopButton, jobs && active && !commandRunning);
        ((TextView) pauseButton.getChildAt(1)).setText(jobState == Phase.PAUSED ? R.string.kv3_resume : R.string.kv3_pause);
        if (pauseButton.getChildAt(0) instanceof android.widget.ImageView) ((android.widget.ImageView) pauseButton.getChildAt(0)).setImageDrawable(ui.icon(jobState == Phase.PAUSED ? R.drawable.kv3_play : R.drawable.kv3_pause, KeplerUi.ON_ACTIVE));
    }
    private NetworkStore.Connection selectedOrExplain() {
        NetworkStore.Connection c; try { c = NetworkStore.load(this).selected(); } catch (java.io.IOException e) { c = null; }
        if (c == null || activeType == null || c.endpoint.type != activeType) { screen.info(getString(R.string.kv3_controls), getString(R.string.kv3_cmd_offline)); return null; }
        return c;
    }
    /** Runs one printer command on a worker; refreshes the status afterwards. */
    private void command(String title, CommandAction action) {
        final NetworkStore.Connection c = selectedOrExplain(); if (c == null || commandRunning) return;
        commandRunning = true; updateControls();
        final NetworkHttp.Cancellation cancel = new NetworkHttp.Cancellation();
        new Thread(() -> {
            String error = null;
            try { NetworkPolicy.requireEndpoint(this, c.endpoint); action.run(new PrinterHostClient(NetworkStore.fresh(this, c, cancel)), cancel); }
            catch (Exception e) { error = EngineText.clean(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
            final String m = error;
            runOnUiThread(() -> { if (isDestroyed()) return; commandRunning = false; updateControls(); if (m != null) screen.info(title, getString(R.string.kv3_cmd_failed, m)); refreshStatus(); });
        }, "device-command").start();
    }
    private interface CommandAction { void run(PrinterHostClient client, NetworkHttp.Cancellation cancel) throws Exception; }
    private void confirm(String title, String message, String action, Runnable yes) {
        MdUi.alert(this).setTitle(title).setMessage(message).setNegativeButton(R.string.general_project_cancel, null).setPositiveButton(action, (d, w) -> yes.run()).show();
    }
    private void pauseOrResume() {
        final boolean resume = jobState == Phase.PAUSED; final String title = getString(resume ? R.string.kv3_resume : R.string.kv3_pause);
        confirm(title, getString(resume ? R.string.kv3_cmd_resume_q : R.string.kv3_cmd_pause_q), title,
            () -> command(title, (client, cancel) -> client.job(resume ? PrinterHostClient.Job.RESUME : PrinterHostClient.Job.PAUSE, cancel)));
    }
    private void confirmStop() {
        final String title = getString(R.string.kv3_stop);
        confirm(title, getString(R.string.kv3_cmd_stop_q), getString(R.string.kv3_cmd_stop_yes), () -> command(title, (client, cancel) -> client.job(PrinterHostClient.Job.CANCEL, cancel)));
    }
    private void chooseSpeed() {
        if (selectedOrExplain() == null) return;
        if (!PrinterHostClient.supportsMotion(activeType)) { screen.info(getString(R.string.kv3_print_speed), getString(R.string.kv3_cmd_unsupported, label(activeType))); return; }
        final String[] modes = {getString(R.string.kv3_speed_silent), getString(R.string.kv3_speed_standard), getString(R.string.kv3_speed_sport), getString(R.string.kv3_speed_ludicrous)};
        com.keplerslicer.app.glass.GlassBottomSheet.choose(this, getWindow().getDecorView(), false, getString(R.string.kv3_print_speed), modes, -1, which -> command(getString(R.string.kv3_print_speed), (client, cancel) -> {
            client.speed(which + 1, cancel);
            runOnUiThread(() -> { if (speedField instanceof LinearLayout && ((LinearLayout) speedField).getChildAt(0) instanceof TextView) ((TextView) ((LinearLayout) speedField).getChildAt(0)).setText(modes[which]); });
        }));
    }
    private void toggle(boolean light) {
        if (selectedOrExplain() == null) return;
        final String title = getString(light ? R.string.kv3_chamber_light : R.string.kv3_aux_fan);
        if (light ? !PrinterHostClient.supportsLight(activeType) : !PrinterHostClient.supportsChamber(activeType)) { screen.info(title, getString(R.string.kv3_cmd_unsupported, label(activeType))); return; }
        final boolean next = !(light ? lightOn : fanOn);
        command(title, (client, cancel) -> {
            if (light) client.chamberLight(next, cancel); else client.auxFan(next, cancel);
            runOnUiThread(() -> { if (light) lightOn = next; else fanOn = next; FrameLayout box = light ? lightSwitch : fanSwitch; box.removeAllViews(); box.addView(ui.switchArt(next)); });
        });
    }
    /** Jog panel: ±1 / ±10 mm per axis and homing; refused while a job is running or paused. */
    private void moveAxes() {
        if (selectedOrExplain() == null) return;
        final String title = getString(R.string.kv3_move_axes);
        if (!PrinterHostClient.supportsMotion(activeType)) { screen.info(title, getString(R.string.kv3_cmd_unsupported, label(activeType))); return; }
        if (jobState != Phase.IDLE) { screen.info(title, getString(R.string.kv3_cmd_move_busy)); return; }
        LinearLayout panel = ui.column(); panel.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), 0);
        TextView hint = ui.line(getString(R.string.kv3_cmd_move_hint), 13, 400, KeplerUi.SECONDARY); hint.setSingleLine(false); hint.setMaxLines(4); hint.setEllipsize(null); panel.addView(hint);
        for (char axis : new char[]{'X', 'Y', 'Z'}) {
            LinearLayout row = ui.row(); row.setPadding(0, ui.dp(10), 0, 0);
            TextView name = ui.line(String.valueOf(axis), 16, 700, axis == 'X' ? KeplerUi.AXIS_X : axis == 'Y' ? KeplerUi.AXIS_Y : KeplerUi.AXIS_Z); row.addView(name, ui.lp(28, -2));
            for (double step : new double[]{-10, -1, 1, 10}) {
                TextView b = ui.line((step > 0 ? "+" : "") + (int) step, 14, 600, KeplerUi.WHITE); b.setGravity(Gravity.CENTER); b.setMinHeight(ui.dp(40)); b.setBackground(ui.field(12)); ui.pressable(b, b.getBackground(), 12);
                b.setContentDescription(axis + " " + (int) step + " " + getString(R.string.kv3_unit_mm));
                b.setOnClickListener(v -> command(title, (client, cancel) -> client.move(axis, step, cancel)));
                LinearLayout.LayoutParams p = ui.weight(); p.leftMargin = ui.dp(6); row.addView(b, p);
            }
            panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        MdUi.alert(this).setTitle(title).setView(panel).setNegativeButton(R.string.l10n_close, null)
            .setPositiveButton(R.string.kv3_cmd_home, (d, w) -> confirm(getString(R.string.kv3_cmd_home), getString(R.string.kv3_cmd_home_q), getString(R.string.kv3_cmd_home), () -> command(title, (client, cancel) -> client.move('H', 0, cancel)))).show();
    }

    private View selectField(String value) {
        LinearLayout s = ui.row(); s.setPadding(ui.dp(12), 0, ui.dp(10), 0); s.setMinimumHeight(ui.dp(36)); s.setBackground(ui.field(12));
        s.addView(ui.line(value, 14, 600, KeplerUi.WHITE)); LinearLayout.LayoutParams p = ui.lp(16, 16); p.leftMargin = ui.dp(8); s.addView(ui.image(R.drawable.kv3_chevron_down, 16, KeplerUi.WHITE), p); return s;
    }
    private static String shortType(String name) { int at = name.indexOf(" @"); String n = at > 0 ? name.substring(0, at) : name; return n.length() > 12 ? n.substring(0, 12) + "…" : n; }
}
