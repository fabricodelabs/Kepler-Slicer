package com.orcamobile.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

/** Saves an immutable, app-private slicing result through Android's document picker. */
public final class GcodeExportActivity extends Activity {
    public static final String EXTRA_RESULT_PATH = "result_path";
    public static final String EXTRA_FILE_NAME = "file_name";
    private static final int CREATE_DOCUMENT = 301;
    private TextView status; private View save;
    private MdUi ui;
    private File source;
    private ExportTask task;
    private boolean pickerOpen;

    private android.widget.EditText fileName;
    private KeplerScreen screen;

    /** Export (Figma node 6:588): slice summary, export type, G-code options, destinations and more exports. */
    @Override public void onCreate(Bundle state) {
        MdUi.prepare(this); super.onCreate(state);
        ui = new MdUi(this);
        Object retained = getLastNonConfigurationInstance();
        task = retained instanceof ExportTask ? (ExportTask)retained : null;
        pickerOpen = state != null && state.getBoolean("picker");
        com.orcamobile.app.glass.KeplerUi k0 = new com.orcamobile.app.glass.KeplerUi(this);
        View history = k0.glassButton(R.drawable.kv3_clock, 44, 22, getString(R.string.kv3_export_history), () -> screen.unavailable(getString(R.string.kv3_export_history)));
        screen = new KeplerScreen(this, getString(R.string.kv3_export), null, history, 2);
        final com.orcamobile.app.glass.KeplerUi k = screen.ui; final int white = com.orcamobile.app.glass.KeplerUi.WHITE, secondary = com.orcamobile.app.glass.KeplerUi.SECONDARY;
        LinearLayout content = screen.content;

        LinearLayout summary = k.row(); summary.setPadding(k.dp(14), k.dp(14), k.dp(16), k.dp(14)); summary.setBackground(k.glass(24));
        android.widget.FrameLayout thumb = new android.widget.FrameLayout(this); thumb.setBackground(k.shape(com.orcamobile.app.glass.KeplerUi.FIELD, 0, 16));
        thumb.addView(k.image(R.drawable.kv3_thumb_sliced, 44, white), new android.widget.FrameLayout.LayoutParams(k.dp(44), k.dp(44), Gravity.CENTER)); summary.addView(thumb, k.lp(52, 52));
        LinearLayout labels = k.column(); LinearLayout.LayoutParams lpp = k.weight(); lpp.leftMargin = k.dp(12); lpp.rightMargin = k.dp(12); summary.addView(labels, lpp);
        java.util.Map<String, ?> record=getSharedPreferences("last_slice_result",MODE_PRIVATE).getAll();
        String requested=getIntent().getStringExtra(EXTRA_RESULT_PATH);
        String details=requested!=null&&requested.equals(record.get("path"))?String.valueOf(record.get("details")):"";
        labels.addView(k.line(getString(R.string.kv3_plate_sliced, 1), 16, 700, white));
        TextView stats = k.text(details.isEmpty() ? "" : details.split("\n")[0], 12, 500, secondary); stats.setMaxLines(2); labels.addView(stats, k.below(2));
        View dot = new View(this); dot.setBackground(k.shape(com.orcamobile.app.glass.KeplerUi.MINT, 0, 4)); summary.addView(dot, k.lp(8, 8));
        content.addView(summary, new LinearLayout.LayoutParams(-1, -2));

        screen.section(getString(R.string.kv3_what_to_export), null, null);
        LinearLayout type = k.row(); type.setPadding(k.dp(4), k.dp(4), k.dp(4), k.dp(4)); type.setBackground(k.glass(22));
        String[] types = {"G-code", getString(R.string.kv3_project_3mf), "STL"};
        for (int i = 0; i < types.length; i++) {
            final String label = types[i]; TextView seg = k.line(label, 13, 700, i == 0 ? com.orcamobile.app.glass.KeplerUi.ON_ACTIVE : white); seg.setGravity(Gravity.CENTER); seg.setMinHeight(k.dp(40));
            final int index = i; if (i == 0) { seg.setBackground(k.shape(white, 0, 18)); seg.setSelected(true); } else { k.pressable(seg, null, 18); seg.setOnClickListener(v -> { if (index == 2) chooseExtra(CREATE_STL); else screen.unavailable(label); }); }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, k.dp(40), 1); if (i > 0) p.leftMargin = k.dp(2); type.addView(seg, p);
        }
        content.addView(type, new LinearLayout.LayoutParams(-1, -2));

        screen.section("G-code", null, null);
        LinearLayout list = k.card(20);
        list.addView(switchRow(k, getString(R.string.kv3_binary_gcode), getString(R.string.kv3_binary_gcode_detail), false));
        list.addView(k.hairline()); list.addView(switchRow(k, getString(R.string.kv3_embed_thumbnails), null, true));
        list.addView(k.hairline()); list.addView(switchRow(k, getString(R.string.kv3_all_plates), null, false));
        list.addView(k.hairline());
        TextView flavorValue = k.line(getString(R.string.kv3_from_printer), 14, 600, white);
        LinearLayout select = k.row(); select.setPadding(k.dp(12), 0, k.dp(10), 0); select.setMinimumHeight(k.dp(36)); select.setBackground(k.field(12));
        select.addView(flavorValue); LinearLayout.LayoutParams cp = k.lp(16, 16); cp.leftMargin = k.dp(8); select.addView(k.image(R.drawable.kv3_chevron_down, 16, white), cp);
        LinearLayout flavor = k.listRow(0, getString(R.string.kv3_gcode_flavor), getString(R.string.kv3_from_printer_profile), select, () -> startActivity(new Intent(this, SettingsActivity.class).putExtra("kepler_kind", "printer")));
        list.addView(flavor);
        list.addView(k.hairline());
        LinearLayout nameRow = k.column(); nameRow.setPadding(k.dp(16), k.dp(12), k.dp(14), k.dp(12));
        nameRow.addView(k.text(getString(R.string.kv3_file_name), 14, 500, white));
        fileName = new android.widget.EditText(this); fileName.setSingleLine(true); fileName.setTextSize(13); fileName.setTextColor(white); fileName.setBackground(k.field(12)); fileName.setPadding(k.dp(12), 0, k.dp(12), 0);
        com.orcamobile.app.glass.GlassTypography.apply(fileName, 500); fileName.setContentDescription(getString(R.string.kv3_file_name));
        String initialName = getIntent().getStringExtra(EXTRA_FILE_NAME); fileName.setText(initialName == null || initialName.trim().isEmpty() ? "kepler-result.gcode" : initialName);
        nameRow.addView(fileName, k.below(8)); ((LinearLayout.LayoutParams) fileName.getLayoutParams()).height = k.dp(40);
        list.addView(nameRow);
        content.addView(list, new LinearLayout.LayoutParams(-1, -2));

        OrcaProfileCatalog.Profile printer = OrcaProfileCatalog.loadSelection(this);
        String printerName = printer == null ? getString(R.string.kv3_printer_generic) : (printer.model == null || printer.model.isEmpty() ? printer.name : printer.model);
        LinearLayout send = k.primaryButton(R.drawable.kv3_print, getString(R.string.kv3_send_to, printerName), () -> startActivity(new Intent(this, NetworkActivity.class)));
        ((TextView) send.getChildAt(1)).setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        content.addView(send, k.below(14));
        LinearLayout pair = k.row();
        LinearLayout files = k.secondaryButton(R.drawable.kv3_folder, getString(R.string.kv3_save_to_files), 48, 24, this::chooseDestination); save = files;
        pair.addView(files, k.weight());
        LinearLayout share = k.secondaryButton(R.drawable.kv3_share, getString(R.string.kv3_share), 48, 24, this::share);
        LinearLayout.LayoutParams sp = k.weight(); sp.leftMargin = k.dp(8); pair.addView(share, sp);
        content.addView(pair, k.below(8));
        status = k.text("", 13, 500, secondary); status.setTextIsSelectable(true); status.setPadding(k.dp(4), 0, k.dp(4), 0); content.addView(status, k.below(10));

        screen.section(getString(R.string.kv3_more_exports), null, null);
        LinearLayout more = k.card(20);
        more.addView(k.listRow(R.drawable.kv3_cube, getString(R.string.kv3_plate_stl), getString(R.string.kv3_plate_stl_detail), k.image(R.drawable.kv3_chevron_right, 18, white), () -> chooseExtra(CREATE_STL)));
        more.addView(k.hairline());
        more.addView(k.listRow(R.drawable.kv3_layers, getString(R.string.kv3_toolpaths_obj), null, k.image(R.drawable.kv3_chevron_right, 18, white), () -> chooseExtra(CREATE_OBJ)));
        more.addView(k.hairline());
        more.addView(k.listRow(R.drawable.kv3_sliders, getString(R.string.kv3_preset_bundle), getString(R.string.kv3_preset_bundle_detail), k.image(R.drawable.kv3_chevron_right, 18, white), () -> startActivity(new Intent(this, SettingsTransferActivity.class))));
        content.addView(more, new LinearLayout.LayoutParams(-1, -2));
        TextView note = k.text("Экспериментальная сборка: результат ещё не проверен для печати.", 12, 500, secondary); note.setPadding(k.dp(4), 0, k.dp(4), 0); content.addView(note, k.below(14));

        try {
            String path = getIntent().getStringExtra(EXTRA_RESULT_PATH);
            if (path == null) throw new IOException("Не указан результат нарезки.");
            source = new File(path).getCanonicalFile();
            File base = new File(getFilesDir(), "slicer-engine").getCanonicalFile();
            if (!source.getPath().startsWith(base.getPath()+File.separator) || !source.getName().equals("result.gcode") || !source.isFile() || source.length() == 0)
                throw new IOException("Результат нарезки недоступен.");
            status.setText("Размер файла: " + android.text.format.Formatter.formatShortFileSize(this, source.length()) + ".");
            loadSummary(source, stats);
        } catch (IOException e) { status.setText(e.getMessage()); source = null; }
        new Thread(() -> { String flavorName = ""; try { flavorName = SettingsState.repository(this).load(SettingsState.activeId(this, "printer")).effective.optString("gcode_flavor", ""); } catch (Exception ignored) { }
            final String value = flavorName; if (!value.isEmpty()) runOnUiThread(() -> { if (!isDestroyed()) flavorValue.setText(value); }); }, "export-flavor").start();
        if (task != null) { task.observer = new WeakReference<>(this); refreshTask(); }
        else if (state != null && state.getBoolean("copying")) status.setText("Экспорт был прерван. Проверьте файл в выбранной папке и повторите сохранение при необходимости.");
        refreshEnabled();
    }

    private static final int CREATE_STL = 302, CREATE_OBJ = 303;
    private String baseName() { String n = fileName == null ? "kepler-result" : fileName.getText().toString().trim(); n = n.replaceAll("(?i)\\.gcode$", "").replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_"); return n.isEmpty() ? "kepler-result" : n.length() > 140 ? n.substring(0, 140) : n; }
    /** The model mesh kept by CurrentModelStore; only STL meshes can be saved as-is. */
    private File modelMesh() { String name = getSharedPreferences("current_model", MODE_PRIVATE).getString("file", ""); if (name.isEmpty() || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".stl")) return null; File f = new File(new File(getFilesDir(), "models"), name); return f.isFile() && f.length() > 0 ? f : null; }

    /** Export the plate mesh (STL) or the G-code toolpaths (OBJ polylines) through the document picker. */
    private void chooseExtra(int request) {
        if (pickerOpen) return;
        if (request == CREATE_STL && modelMesh() == null) { screen.info(getString(R.string.kv3_plate_stl), getString(R.string.kv3_stl_unavailable)); return; }
        if (request == CREATE_OBJ && source == null) { screen.info(getString(R.string.kv3_toolpaths_obj), status.getText().toString()); return; }
        String name = baseName() + (request == CREATE_STL ? ".stl" : "-toolpaths.obj");
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(request == CREATE_STL ? "model/stl" : "model/obj").putExtra(Intent.EXTRA_TITLE, name);
        try { pickerOpen = true; refreshEnabled(); startActivityForResult(intent, request); } catch (android.content.ActivityNotFoundException e) { pickerOpen = false; refreshEnabled(); status.setText("На устройстве нет приложения для сохранения документов."); }
    }
    private void writeExtra(int request, Uri destination) {
        final File mesh = modelMesh(), gcode = source; final android.content.Context app = getApplicationContext();
        status.setText(getString(R.string.kv3_exporting));
        new Thread(() -> {
            String message;
            try (OutputStream out = app.getContentResolver().openOutputStream(destination, "wt")) {
                if (out == null) throw new IOException("Не удалось открыть место сохранения.");
                if (request == CREATE_STL) {
                    if (mesh == null) throw new IOException(getString(R.string.kv3_stl_unavailable));
                    long n = 0; try (FileInputStream in = new FileInputStream(mesh)) { byte[] b = new byte[65536]; int r; while ((r = in.read(b)) != -1) { out.write(b, 0, r); n += r; } }
                    message = getString(R.string.kv3_saved_bytes, android.text.format.Formatter.formatShortFileSize(app, n));
                } else {
                    if (gcode == null) throw new IOException("Результат нарезки недоступен.");
                    GcodeAnalysis analysis = GcodeAnalysis.read(gcode); long vertices = 0;
                    java.io.Writer w = new java.io.BufferedWriter(new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8));
                    w.write("# Kepler Slicer toolpaths (extrusion only, millimetres)\n");
                    int index = 0;
                    for (GcodeAnalysis.Layer layer : analysis.layers) {
                        if (!Double.isFinite(layer.z)) continue; w.write("o layer_" + (++index) + "\n");
                        String z = String.format(java.util.Locale.ROOT, "%.4f", layer.z);
                        for (int i = 0; i + 3 < layer.size; i += 4) {
                            w.write(String.format(java.util.Locale.ROOT, "v %.4f %.4f %s\nv %.4f %.4f %s\nl %d %d\n", layer.segments[i], layer.segments[i + 1], z, layer.segments[i + 2], layer.segments[i + 3], z, vertices + 1, vertices + 2));
                            vertices += 2;
                        }
                    }
                    w.flush();
                    message = getString(R.string.kv3_saved_obj, index, vertices / 2);
                }
            } catch (Exception | OutOfMemoryError e) { message = "Не удалось сохранить файл: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
            final String text = message; runOnUiThread(() -> { if (!isDestroyed()) status.setText(text); });
        }, "export-extra").start();
    }
    /** Share the G-code through the read-only ShareProvider (a private copy under cache/share). */
    private void share() {
        if (source == null) { screen.info(getString(R.string.kv3_share), status.getText().toString()); return; }
        final File from = source; final String name = baseName() + ".gcode";
        new Thread(() -> {
            try {
                File dir = ShareProvider.directory(this); File[] old = dir.listFiles(); if (old != null) for (File f : old) f.delete();
                File copy = new File(dir, name);
                try (FileInputStream in = new FileInputStream(from); OutputStream out = new java.io.FileOutputStream(copy)) { byte[] b = new byte[65536]; int r; while ((r = in.read(b)) != -1) out.write(b, 0, r); }
                Uri uri = ShareProvider.uriFor(this, copy);
                runOnUiThread(() -> { if (isDestroyed()) return;
                    Intent send = new Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    send.setClipData(android.content.ClipData.newRawUri(name, uri));
                    try { startActivity(Intent.createChooser(send, getString(R.string.kv3_share)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); } catch (android.content.ActivityNotFoundException e) { status.setText("Нет приложений для отправки файла."); } });
            } catch (IOException e) { runOnUiThread(() -> { if (!isDestroyed()) status.setText(e.getMessage()); }); }
        }, "export-share").start();
    }

    /** Settings row with the original 46x28 switch art; options without a backend explain that on tap. */
    private View switchRow(com.orcamobile.app.glass.KeplerUi k, String title, String subtitle, boolean on) {
        ImageView sw = k.switchArt(on);
        LinearLayout r = k.listRow(0, title, subtitle, sw, () -> screen.unavailable(title));
        ((TextView) ((LinearLayout) r.getChildAt(0)).getChildAt(0)).setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        r.setMinimumHeight(k.dp(52)); return r;
    }

    /** "2 h 14 m · 18.4 g · 250 layers" from Orca's summary comments. */
    private void loadSummary(File file, TextView target) {
        new Thread(() -> {
            String time = null, grams = null; int layers = 0;
            try (java.io.RandomAccessFile in = new java.io.RandomAccessFile(file, "r")) {
                long start = Math.max(0, in.length() - 96 * 1024); in.seek(start); byte[] buffer = new byte[(int) (in.length() - start)]; in.readFully(buffer);
                for (String line : new String(buffer, java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                    String l = line.trim().toLowerCase(java.util.Locale.ROOT); if (!l.startsWith(";")) continue;
                    String value = l.contains("=") ? l.substring(l.indexOf('=') + 1).trim() : l.contains(":") ? l.substring(l.lastIndexOf(':') + 1).trim() : "";
                    if (time == null && l.contains("estimated printing time") && !value.isEmpty()) time = value;
                    else if (grams == null && l.contains("filament") && l.contains("[g]") && value.matches("[0-9.,]+.*")) grams = value.split("[ ,]")[0] + " " + getString(R.string.kv3_unit_g);
                    else if (layers == 0 && (l.contains("total layer number") || l.contains("total layers count")) && value.matches("[0-9]+.*")) layers = Integer.parseInt(value.replaceAll("[^0-9].*", ""));
                }
            } catch (Exception ignored) { }
            StringBuilder b = new StringBuilder(); if (time != null) b.append(time); if (grams != null) b.append(b.length() > 0 ? " · " : "").append(grams);
            if (layers > 0) b.append(b.length() > 0 ? " · " : "").append(getResources().getQuantityString(R.plurals.kv3_layers, layers, layers));
            final String text = b.toString();
            if (!text.isEmpty()) runOnUiThread(() -> { if (!isDestroyed()) target.setText(text); });
        }, "export-summary").start();
    }

    private void chooseDestination() {
        if (source == null || pickerOpen || task != null && task.running) return;
        String name = fileName == null ? getIntent().getStringExtra(EXTRA_FILE_NAME) : fileName.getText().toString().trim();
        if (name == null || name.trim().isEmpty()) name = "kepler-result.gcode";
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (name.length() > 150) name = name.substring(0,150);
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".gcode")) name += ".gcode";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,name);
        android.net.Uri initial=GeneralPreferences.initialDocumentUri(this);
        if(initial!=null)try{
            android.net.Uri directory=android.provider.DocumentsContract.buildDocumentUriUsingTree(initial,android.provider.DocumentsContract.getTreeDocumentId(initial));
            intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,directory);
        }catch(IllegalArgumentException ignored){/* A provider may no longer expose the persisted tree. */}
        try { pickerOpen = true; refreshEnabled(); startActivityForResult(intent,CREATE_DOCUMENT); }
        catch (android.content.ActivityNotFoundException e) { pickerOpen = false; status.setText("На устройстве нет приложения для сохранения документов."); refreshEnabled(); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request == CREATE_STL || request == CREATE_OBJ) { pickerOpen = false; refreshEnabled(); if (result == RESULT_OK && data != null && data.getData() != null) writeExtra(request, data.getData()); return; }
        if (request != CREATE_DOCUMENT) return;
        pickerOpen = false;
        if (result != RESULT_OK || data == null || data.getData() == null) { refreshEnabled(); return; }
        if (source == null) { status.setText("Исходный результат нарезки недоступен."); refreshEnabled(); return; }
        task = new ExportTask(getApplicationContext(),source,data.getData(),this); task.start(); refreshTask();
    }
    private void refreshEnabled() { boolean enabled = source != null && !pickerOpen && (task == null || !task.running); save.setEnabled(enabled); save.setAlpha(enabled?1f:.45f); }
    private void refreshTask() { if (task != null) status.setText(task.message); refreshEnabled(); }
    @Override public Object onRetainNonConfigurationInstance() { return task; }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("picker",pickerOpen); state.putBoolean("copying",task != null && task.running); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() { if (task != null && task.observer.get() == this) task.observer.clear(); super.onDestroy(); }
    private int dp(int value) { return (int)(value*getResources().getDisplayMetrics().density+.5f); }
    private TextView text(String value,int size,boolean bold) { return ui.text(value,size,bold); }
    private void gap(LinearLayout parent,int value) { parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(value))); }

    private static final class ExportTask extends Thread {
        final android.content.Context context; final File source; final Uri destination;
        volatile WeakReference<GcodeExportActivity> observer; volatile boolean running = true; volatile String message = "Сохраняем G-code…";
        ExportTask(android.content.Context context,File source,Uri destination,GcodeExportActivity activity) { super("gcode-export"); this.context=context; this.source=source; this.destination=destination; observer=new WeakReference<>(activity); }
        @Override public void run() {
            try {
                long length = source.length(), copied = 0;
                try (FileInputStream in = new FileInputStream(source); OutputStream out = context.getContentResolver().openOutputStream(destination,"wt")) {
                    if (out == null) throw new IOException("Не удалось открыть место сохранения.");
                    byte[] buffer = new byte[65536]; int n;
                    while ((n=in.read(buffer)) != -1) { out.write(buffer,0,n); copied+=n; }
                    out.flush();
                    if (copied != length || source.length() != length) throw new IOException("Размер исходного файла изменился.");
                }
                message = "G-code сохранён: " + copied + " байт.";
            } catch (Exception error) { message = "Не удалось сохранить файл полностью: " + (error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()) + " Повторите экспорт при необходимости."; }
            finally { running=false; new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> { GcodeExportActivity a=observer.get(); if (a!=null && !a.isDestroyed()) a.refreshTask(); }); }
        }
    }
}
