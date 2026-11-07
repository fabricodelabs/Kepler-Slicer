package com.keplerslicer.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.keplerslicer.app.glass.KeplerUi;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Import model (Figma node 4:156): Android document providers and real, persistently accessible model history. */
public final class ImportActivity extends Activity {
    private static final int PICK_FILE = 401, RECENT_PREVIEW = 6;
    private static final String[] FORMATS = {null, "STL", "3MF", "OBJ", "STEP", "AMF"};
    private KeplerScreen screen; private KeplerUi ui; private LinearLayout recent; private boolean pickerOpen, showAll;
    private final List<TextView> formatChips = new ArrayList<>(); private String format; private List<RecentModels.Entry> entries = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        MdUi.prepare(this); super.onCreate(state); pickerOpen = state != null && state.getBoolean("picker");
        if (state != null) { format = state.getString("format"); showAll = state.getBoolean("all"); }
        KeplerUi kui = new KeplerUi(this);
        View search = kui.glassButton(R.drawable.kv3_search, 44, 22, getString(R.string.kv3_search), this::chooseFile);
        screen = new KeplerScreen(this, getString(R.string.kv3_import_title), this::finish, search, 0); ui = screen.ui;
        LinearLayout content = screen.content;

        HorizontalScrollView formatScroll = new HorizontalScrollView(this); formatScroll.setHorizontalScrollBarEnabled(false); formatScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout formats = ui.row(); formatScroll.addView(formats);
        for (int i = 0; i < FORMATS.length; i++) {
            final String value = FORMATS[i]; String label = value == null ? getString(R.string.kv3_all_formats) : value;
            TextView chip = ui.chip(label, eq(value, format), () -> { format = value; for (int k = 0; k < formatChips.size(); k++) ui.setChipActive(formatChips.get(k), eq(FORMATS[k], format)); renderRecent(); });
            if (value != null) chip.setContentDescription(getString(R.string.native_import_format_description, value));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, ui.dp(36)); if (i > 0) p.leftMargin = ui.dp(8); formats.addView(chip, p); formatChips.add(chip);
        }
        content.addView(formatScroll, new LinearLayout.LayoutParams(-1, -2));

        screen.section(getString(R.string.kv3_open_from), null, null);
        // "Choose file" below opens any storage (Files, Downloads, Google Drive); the tiles add a model link and generated shapes.
        int[] icons = {R.drawable.kv3_link, R.drawable.kv3_cube};
        String[] labels = {getString(R.string.kv3_source_url), getString(R.string.kv3_source_shapes)};
        View[] tiles = new View[icons.length];
        for (int i = 0; i < icons.length; i++) {
            final int index = i;
            tiles[i] = sourceTile(icons[i], labels[i], () -> { if (index == 0) fromUrl(); else shapes(); });
        }
        content.addView(ui.grid(tiles, 2), new LinearLayout.LayoutParams(-1, -2));

        screen.section(getString(R.string.kv3_recent_models), getString(R.string.kv3_view_all), () -> { showAll = !showAll; renderRecent(); });
        recent = ui.column(); content.addView(recent, new LinearLayout.LayoutParams(-1, -2));
        content.addView(ui.spacer(14));
        LinearLayout info = ui.row(); info.setGravity(Gravity.TOP); info.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12)); info.setBackground(ui.glass(20));
        info.addView(ui.image(R.drawable.kv3_info, 20, KeplerUi.WHITE));
        TextView note = ui.text(getString(R.string.kv3_import_note) + "\n" + getString(R.string.native_import_formats), 14, 500, KeplerUi.WHITE);
        LinearLayout.LayoutParams np = ui.weight(); np.leftMargin = ui.dp(10); info.addView(note, np);
        content.addView(info, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout choose = ui.primaryButton(R.drawable.kv3_folder, getString(R.string.kv3_choose_file), this::chooseFile);
        ((TextView) choose.getChildAt(1)).setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        screen.floating(choose);
        loadRecent();
    }

    private static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }

    /** Source tile (node 4:187): glass r20, 88dp tall, 26dp icon, 12/500 label, gap 8. */
    private View sourceTile(int icon, String label, Runnable action) {
        LinearLayout tile = ui.column(); tile.setGravity(Gravity.CENTER); tile.setMinimumHeight(ui.dp(88)); tile.setPadding(ui.dp(6), ui.dp(10), ui.dp(6), ui.dp(10));
        ui.pressable(tile, ui.glass(20), 20); tile.addView(ui.image(icon, 26, KeplerUi.WHITE));
        TextView t = ui.line(label, 12, 500, KeplerUi.WHITE); t.setGravity(Gravity.CENTER); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = ui.dp(8); tile.addView(t, p);
        tile.setContentDescription(label); tile.setOnClickListener(v -> action.run()); return tile;
    }

    private void loadRecent() {
        recent.removeAllViews(); TextView loading = ui.text(getString(R.string.kv3_loading_history), 13, 500, KeplerUi.SECONDARY); recent.addView(loading);
        new Thread(() -> { List<RecentModels.Entry> loaded = RecentModels.load(this);
            runOnUiThread(() -> { if (isDestroyed()) return; entries = loaded; renderRecent(); });
        }, "recent-models").start();
    }

    private void renderRecent() {
        if (recent == null) return; recent.removeAllViews();
        List<RecentModels.Entry> shown = new ArrayList<>();
        for (RecentModels.Entry e : entries) if (format == null || format.equals(formatLabel(e.name))) shown.add(e);
        if (shown.isEmpty()) { TextView empty = ui.text(getString(R.string.kv3_recent_empty), 14, 500, KeplerUi.SECONDARY); empty.setPadding(ui.dp(4), 0, ui.dp(4), 0); recent.addView(empty); return; }
        int count = showAll ? shown.size() : Math.min(RECENT_PREVIEW, shown.size());
        View[] tiles = new View[count];
        for (int i = 0; i < count; i++) tiles[i] = recentTile(shown.get(i));
        recent.addView(ui.grid(tiles, 3), new LinearLayout.LayoutParams(-1, -2));
    }

    /** Recent model (node 4:217): glass r20 pt6 px6 pb10, 88dp field thumb r15, 12/700 name, 12/500 size, more16. */
    private View recentTile(RecentModels.Entry e) {
        LinearLayout tile = ui.column(); tile.setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(10)); ui.pressable(tile, ui.glass(20), 20);
        FrameLayout thumb = new FrameLayout(this); thumb.setBackground(ui.shape(KeplerUi.FIELD, 0, 15)); thumb.setClipToOutline(true);
        File file = RecentModels.thumbnailFile(this, e); Bitmap bitmap = file == null ? null : BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap != null) { ImageView image = new ImageView(this); image.setImageBitmap(bitmap); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); thumb.addView(image, new FrameLayout.LayoutParams(-1, -1)); }
        else { String f = formatLabel(e.name); int art = "3MF".equals(f) ? R.drawable.kv3_thumb_cylinder : "STL".equals(f) ? R.drawable.kv3_thumb_benchy : R.drawable.kv3_thumb_box; float size = art == R.drawable.kv3_thumb_benchy ? 64 : 52;
            thumb.addView(ui.image(art, size, KeplerUi.WHITE), new FrameLayout.LayoutParams(ui.dp(size), ui.dp(size), Gravity.CENTER)); }
        tile.addView(thumb, new LinearLayout.LayoutParams(-1, ui.dp(88)));
        LinearLayout labels = ui.row(); labels.setGravity(Gravity.TOP); labels.setPadding(ui.dp(4), 0, 0, 0);
        LinearLayout col = ui.column(); TextView name = ui.line(e.name, 12, 700, KeplerUi.WHITE); col.addView(name);
        TextView size = ui.line(formatLabel(e.name), 12, 500, KeplerUi.SECONDARY); col.addView(size); labels.addView(col, ui.weight());
        LinearLayout.LayoutParams mp = ui.lp(16, 16); mp.leftMargin = ui.dp(2); labels.addView(ui.image(R.drawable.kv3_more, 16, KeplerUi.WHITE), mp);
        tile.addView(labels, ui.below(8));
        tile.setContentDescription(e.name); tile.setOnClickListener(v -> returnUri(e.uri));
        new Thread(() -> { long bytes = -1; try (android.database.Cursor c = getContentResolver().query(e.uri, new String[]{OpenableColumns.SIZE}, null, null, null)) { if (c != null && c.moveToFirst() && !c.isNull(0)) bytes = c.getLong(0); } catch (RuntimeException ignored) { }
            final long b = bytes; if (b > 0) runOnUiThread(() -> { if (!isDestroyed() && size.isAttachedToWindow()) size.setText(android.text.format.Formatter.formatShortFileSize(this, b)); }); }, "recent-size").start();
        return tile;
    }

    /** From URL: download an STL/3MF/OBJ/STEP file over HTTP(S) into private cache, respecting offline and stealth modes. */
    private void fromUrl() {
        android.app.Dialog dialog = new android.app.Dialog(this); dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout body = ui.column(); body.setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(20)); body.setBackground(ui.shape(0xF22E323A, KeplerUi.GLASS_STROKE, 28));
        body.addView(ui.text(getString(R.string.kv3_source_url), 18, 700, KeplerUi.WHITE));
        TextView hint = ui.text(getString(R.string.kv3_url_hint), 13, 500, KeplerUi.SECONDARY); body.addView(hint, ui.below(6));
        android.widget.EditText field = new android.widget.EditText(this); field.setSingleLine(true); field.setTextColor(KeplerUi.WHITE); field.setHintTextColor(KeplerUi.SECONDARY); field.setHint("https://");
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); field.setBackground(ui.field(12)); field.setPadding(ui.dp(12), 0, ui.dp(12), 0); field.setMinHeight(ui.dp(44));
        com.keplerslicer.app.glass.GlassTypography.apply(field, 500); body.addView(field, ui.below(12));
        TextView error = ui.text("", 12, 500, 0xFFFF8A80); error.setVisibility(View.GONE); body.addView(error, ui.below(8));
        LinearLayout actions = ui.row();
        LinearLayout cancel = ui.secondaryButton(0, getString(R.string.general_project_cancel), 48, 24, dialog::dismiss); actions.addView(cancel, ui.weight());
        final LinearLayout[] go = new LinearLayout[1];
        go[0] = ui.primaryButton(R.drawable.kv3_link, getString(R.string.kv3_download), () -> {
            String text = field.getText().toString().trim(); ui.setEnabled(go[0], false); error.setVisibility(View.GONE);
            new Thread(() -> { try {
                    java.net.URI page = new java.net.URI(text);
                    String explain = ModelLinks.unsupportedPage(page); if (explain != null) throw new java.io.IOException(explain);
                    String printables = ModelLinks.printablesId(page);
                    if (printables != null) {
                        java.util.List<ModelLinks.FileRef> files = ModelLinks.printablesFiles(printables, new NetworkHttp.Cancellation());
                        runOnUiThread(() -> { if (isDestroyed()) return; ui.setEnabled(go[0], true); dialog.dismiss(); pickPrintables(files); });
                        return;
                    }
                    File file = download(text); runOnUiThread(() -> { if (isDestroyed()) return; dialog.dismiss(); returnUri(ShareProvider.uriFor(this, file)); }); }
                catch (Exception e) { String m = EngineText.clean(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); runOnUiThread(() -> { if (isDestroyed()) return; ui.setEnabled(go[0], true); error.setText(m); error.setVisibility(View.VISIBLE); }); } }, "url-import").start();
        });
        go[0].setMinimumHeight(ui.dp(48)); LinearLayout.LayoutParams gp = ui.weight(); gp.leftMargin = ui.dp(8); actions.addView(go[0], gp);
        body.addView(actions, ui.below(16));
        dialog.setContentView(body); if (dialog.getWindow() != null) { dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0)); dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - ui.dp(32), ui.dp(420)), -2); }
        dialog.show();
    }
    /** One file goes straight in; several are offered in a sheet. */
    private void pickPrintables(java.util.List<ModelLinks.FileRef> files) {
        if (files.size() == 1) { fetchPrintables(files.get(0)); return; }
        String[] names = new String[files.size()];
        for (int i = 0; i < names.length; i++) { ModelLinks.FileRef f = files.get(i); names[i] = f.name + (f.size > 0 ? " · " + android.text.format.Formatter.formatShortFileSize(this, f.size) : ""); }
        com.keplerslicer.app.glass.GlassBottomSheet.choose(this, screen.root, false, L.t("Файл модели"), names, -1, i -> fetchPrintables(files.get(i)));
    }
    private void fetchPrintables(ModelLinks.FileRef f) {
        screen.info(L.t("Загрузка"), L.t("Скачиваем ") + f.name + "…");
        new Thread(() -> { try { File file = download(ModelLinks.printablesLink(f.url, new NetworkHttp.Cancellation())); runOnUiThread(() -> { if (!isDestroyed()) returnUri(ShareProvider.uriFor(this, file)); }); }
            catch (Exception e) { String m = EngineText.clean(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); runOnUiThread(() -> { if (!isDestroyed()) screen.info(L.t("Загрузка"), m); }); } }, "printables-import").start();
    }
    private File download(String text) throws Exception {
        java.net.URI uri = new java.net.URI(text);
        if (uri.getScheme() == null || !(uri.getScheme().equalsIgnoreCase("https") || uri.getScheme().equalsIgnoreCase("http")) || uri.getHost() == null) throw new java.io.IOException(getString(R.string.kv3_url_invalid));
        java.net.HttpURLConnection c = null;
        for (int hop = 0; ; hop++) {
            // Redirects are followed by hand so each target passes the same network policy as the typed link.
            NetworkPolicy.checkUri(this, uri);
            c = (java.net.HttpURLConnection) uri.toURL().openConnection(); c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "KeplerSlicer"); // file CDNs (e.g. Printables) refuse the default Dalvik agent
            int code = c.getResponseCode();
            if (code < 300 || code >= 400) break;
            String location = c.getHeaderField("Location"); c.disconnect();
            if (location == null || hop >= 5) throw new java.io.IOException(L.t("Слишком много перенаправлений."));
            uri = uri.resolve(location);
            if (uri.getScheme() == null || !(uri.getScheme().equalsIgnoreCase("https") || uri.getScheme().equalsIgnoreCase("http"))) throw new java.io.IOException(getString(R.string.kv3_url_invalid));
        }
        try {
            int code = c.getResponseCode(); if (code < 200 || code >= 300) throw new java.io.IOException(L.t("Сервер не отдал файл (HTTP ") + code + ").");
            String type = c.getContentType() == null ? "" : c.getContentType().toLowerCase(Locale.ROOT);
            if (type.startsWith("text/html")) throw new java.io.IOException(L.t("Ссылка ведёт на веб-страницу, а не на файл модели. Нужна прямая ссылка на .stl/.3mf/.obj/.step или страница Printables."));
            String name = null, disposition = c.getHeaderField("Content-Disposition");
            if (disposition != null) { java.util.regex.Matcher m = java.util.regex.Pattern.compile("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(disposition); if (m.find()) name = java.net.URLDecoder.decode(m.group(1), "UTF-8"); }
            if (name == null) { String path = c.getURL().getPath(); name = path == null ? "" : path.substring(path.lastIndexOf('/') + 1); name = java.net.URLDecoder.decode(name, "UTF-8"); }
            name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_"); String lower = name.toLowerCase(Locale.ROOT);
            if (!(lower.endsWith(".stl") || lower.endsWith(".3mf") || lower.endsWith(".obj") || lower.endsWith(".step") || lower.endsWith(".stp"))) throw new java.io.IOException(getString(R.string.kv3_url_format));
            long limit = 512L * 1024 * 1024; if (c.getContentLengthLong() > limit) throw new java.io.IOException(getString(R.string.kv3_url_too_large));
            File dir = new File(getCacheDir(), "url-import"); if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("cache");
            File[] old = dir.listFiles(); if (old != null) for (File f : old) f.delete();
            File out = new File(dir, name.length() > 120 ? name.substring(name.length() - 120) : name); long total = 0;
            try (java.io.InputStream in = c.getInputStream(); java.io.OutputStream o = new java.io.FileOutputStream(out)) { byte[] b = new byte[65536]; int r; while ((r = in.read(b)) != -1) { total += r; if (total > limit) throw new java.io.IOException(getString(R.string.kv3_url_too_large)); o.write(b, 0, r); } }
            if (total == 0) throw new java.io.IOException(getString(R.string.kv3_url_empty));
            return out;
        } finally { c.disconnect(); }
    }

    /** Shapes: generate a simple watertight primitive (20 mm) as binary STL and open it like any other model. */
    private void shapes() {
        String[] names = {getString(R.string.kv3_shape_cube), getString(R.string.kv3_shape_cylinder), getString(R.string.kv3_shape_cone), getString(R.string.kv3_shape_sphere)};
        com.keplerslicer.app.glass.GlassBottomSheet.choose(this, screen.root, false, getString(R.string.kv3_source_shapes), names, -1, index -> {
            try { File f = PrimitiveShapes.write(this, index, new String[]{"cube", "cylinder", "cone", "sphere"}[index]); returnUri(ShareProvider.uriFor(this, f)); }
            catch (java.io.IOException e) { screen.info(getString(R.string.kv3_source_shapes), e.getMessage()); }
        });
    }

    private void chooseFile() { chooseFile(null); }
    private void chooseFile(Uri start) {
        if (pickerOpen) return; Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"model/stl", "application/sla", "model/x.stl-binary", "model/x.stl-ascii", "application/vnd.ms-pki.stl", "model/obj", "text/obj", "text/plain", "model/3mf", "application/vnd.ms-3mfdocument", "application/x-3mf", "model/step", "application/step", "application/x-step", "model/x-step", "application/octet-stream"});
        Uri initial = start != null ? start : GeneralPreferences.initialDocumentUri(this); if (initial != null) i.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, initial);
        try { pickerOpen = true; startActivityForResult(i, PICK_FILE); } catch (android.content.ActivityNotFoundException e) { pickerOpen = false; screen.info(getString(R.string.kv3_choose_file), L.t("На устройстве нет приложения для выбора документов.")); }
    }
    private static String formatLabel(String name) { String lower = name == null ? "" : name.toLowerCase(Locale.ROOT); return lower.endsWith(".step") || lower.endsWith(".stp") ? "STEP" : lower.endsWith(".3mf") ? "3MF" : lower.endsWith(".obj") ? "OBJ" : lower.endsWith(".amf") ? "AMF" : "STL"; }
    @Override protected void onActivityResult(int request, int result, Intent data) { super.onActivityResult(request, result, data); if (request != PICK_FILE) return; pickerOpen = false; if (result == RESULT_OK && data != null && data.getData() != null) {
        if ((data.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0 && (data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) try { getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) { }
        returnUri(data.getData());
    } }
    private void returnUri(Uri uri) { setResult(RESULT_OK, new Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); finish(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("picker", pickerOpen); state.putString("format", format); state.putBoolean("all", showAll); super.onSaveInstanceState(state); }
}
