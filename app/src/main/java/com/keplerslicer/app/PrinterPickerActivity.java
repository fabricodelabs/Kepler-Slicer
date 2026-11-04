package com.keplerslicer.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Real printer-model and nozzle variant picker backed by the bundled upstream catalog. */
public class PrinterPickerActivity extends Activity {
    private Object backCallback;
    private int INK, MUTED, BG, SURFACE, LINE, CARD;
    private final ExecutorService worker = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> images = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private List<Model> models;
    private final List<Model> visible = new ArrayList<>();
    private LinearLayout root, content;
    private Model chosenModel;
    private PrinterProfileCatalog.Profile chosenVariant;
    private String query = "", restoredModel, restoredVariant;
    private String error;
    private int detailScrollY;

    private static final class Model {
        final String vendor, name; String cover = "";
        final List<PrinterProfileCatalog.Profile> variants = new ArrayList<>();
        Model(String vendor, String name) { this.vendor = vendor; this.name = name; }
        String key() { return vendor + "\u0000" + name; }
    }
    @Override public void onCreate(Bundle state) {
        MdUi.prepare(this); super.onCreate(state); if(android.os.Build.VERSION.SDK_INT>=33)backCallback=Api33.register(this);
        MdUi palette = new MdUi(this);
        INK=palette.ink; MUTED=palette.muted; BG=palette.bg; SURFACE=palette.container; LINE=palette.line; CARD=palette.surface;
        if (state != null) { query = state.getString("query", ""); restoredModel = state.getString("model"); restoredVariant = state.getString("variant"); }
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(palette.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        render();
        worker.execute(() -> {
            try {
                List<PrinterProfileCatalog.Profile> profiles = PrinterProfileCatalog.load(getApplicationContext());
                HashMap<String,String> covers = readCovers();
                LinkedHashMap<String,Model> byKey = new LinkedHashMap<>();
                for (PrinterProfileCatalog.Profile p : profiles) {
                    if (p.model == null || p.model.trim().isEmpty()) continue;
                    String key = p.vendor + "\u0000" + p.model;
                    Model m = byKey.get(key); if (m == null) { m = new Model(p.vendor,p.model); m.cover = covers.getOrDefault(key, ""); byKey.put(key,m); }
                    m.variants.add(p);
                }
                List<Model> all = new ArrayList<>(byKey.values());
                Collections.sort(all, Comparator.comparing((Model m) -> m.vendor.toLowerCase(Locale.ROOT)).thenComparing(m -> m.name.toLowerCase(Locale.ROOT)));
                for (Model m : all) Collections.sort(m.variants, Comparator.comparing(p -> p.variant.isEmpty() ? "~" : p.variant));
                runOnUiThread(() -> {
                    if (isDestroyed()) return; models = all;
                    if (restoredModel != null) for (Model m : models) if (m.key().equals(restoredModel)) { chosenModel=m; break; }
                    PrinterProfileCatalog.Profile selection=PrinterProfileCatalog.loadSelection(this);
                    if (chosenModel != null) for (PrinterProfileCatalog.Profile p : chosenModel.variants) {
                        if (restoredVariant != null ? p.path.equals(restoredVariant) : selection != null && p.path.equals(selection.path)) chosenVariant=p;
                    }
                    render();
                });
            } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed()) { error=e.getMessage(); render(); } }); }
        });
    }
    private HashMap<String,String> readCovers() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = getAssets().open("kepler_printer_models.json")) { byte[] b=new byte[8192]; int n,total=0; while((n=in.read(b))!=-1) { total+=n; if(total>2*1024*1024) throw new java.io.IOException("Слишком большой индекс моделей"); out.write(b,0,n); } }
        JSONObject index = new JSONObject(new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
        if (!PrinterProfileCatalog.UPSTREAM_REVISION.equals(index.optString("revision"))) throw new java.io.IOException("Версии каталогов не совпадают");
        HashMap<String,String> result = new HashMap<>(); JSONArray rows=index.getJSONArray("models");
        for(int i=0;i<rows.length();i++) { JSONObject row=rows.getJSONObject(i); String filename=row.optString("cover", "");
            if (!filename.isEmpty() && !filename.matches("[0-9a-f]{64}\\.png")) throw new java.io.IOException("Некорректный индекс изображения");
            result.put(row.getString("vendor")+"\u0000"+row.getString("model"),filename);
        }
        return result;
    }
    @Override protected void onSaveInstanceState(Bundle out) { out.putString("query",query); if(chosenModel!=null) out.putString("model",chosenModel.key()); if(chosenVariant!=null) out.putString("variant",chosenVariant.path); super.onSaveInstanceState(out); }
    @Override protected void onDestroy() { worker.shutdownNow(); if(android.os.Build.VERSION.SDK_INT>=33&&backCallback!=null)Api33.unregister(this,backCallback); super.onDestroy(); }
    @android.annotation.TargetApi(33) private static final class Api33 {static Object register(PrinterPickerActivity a){android.window.OnBackInvokedCallback cb=a::handleBack;a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,cb);return cb;}static void unregister(PrinterPickerActivity a,Object cb){a.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback)cb);}}
    private void handleBack() { if(chosenModel!=null) { chosenModel=null; chosenVariant=null; render(); } else if(library) { library=false; render(); } else finish(); }
    @android.annotation.SuppressLint("GestureBackNavigation") @Override public void onBackPressed() { handleBack(); }
    private int dp(float n) { return (int)(n*getResources().getDisplayMetrics().density+.5f); }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private GradientDrawable outline(int color,int radius) { GradientDrawable d=shape(color,radius); d.setStroke(dp(1),LINE); return d; }
    private TextView text(String value,int size,int color,boolean bold) { TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setIncludeFontPadding(false); t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL)); return t; }
    private LinearLayout column() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private LinearLayout.LayoutParams lp(int w,int h) { return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)); }
    private void gap(LinearLayout v,int h) { v.addView(new View(this),lp(1,h)); }
    private void padding(View v,int l,int t,int r,int b) { v.setPadding(dp(l),dp(t),dp(r),dp(b)); }
    private boolean library;
    private KeplerScreen screen;
    private com.keplerslicer.app.glass.KeplerUi ui;
    private void render() {
        String title = chosenModel != null ? chosenModel.name : library ? getString(R.string.kv3_printer_library) : getString(R.string.kv3_printers);
        View plus = new com.keplerslicer.app.glass.KeplerUi(this).glassButton(R.drawable.kv3_plus, 44, 22, getString(R.string.kv3_add_printer), () -> { library = true; chosenModel = null; render(); });
        screen = new KeplerScreen(this, title, this::handleBack, library || chosenModel != null ? null : plus, 1); ui = screen.ui;
        root = screen.column; content = screen.content;
        if (models == null) { TextView state = ui.text(error == null ? "Загружаем каталог принтеров…" : "Не удалось загрузить каталог: " + error, 14, 500, com.keplerslicer.app.glass.KeplerUi.SECONDARY); content.addView(state); return; }
        if (chosenModel != null) renderDetails(); else if (library) renderList(); else renderPrinters();
    }

    /** Printers & profiles (Figma node 5:245): the active printer card with nozzle variants, process and actions. */
    private void renderPrinters() {
        final int white = com.keplerslicer.app.glass.KeplerUi.WHITE, secondary = com.keplerslicer.app.glass.KeplerUi.SECONDARY;
        PrinterProfileCatalog.Profile selection = PrinterProfileCatalog.loadSelection(this);
        Model current = null;
        if (selection != null) for (Model m : models) if (m.vendor.equals(selection.vendor) && m.name.equals(selection.model)) { current = m; break; }
        if (selection != null) {
            LinearLayout card = ui.column(); card.setBackground(ui.shape(com.keplerslicer.app.glass.KeplerUi.GLASS, 0x995EE0C8, 24));
            LinearLayout head = ui.row(); head.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12)); head.setMinimumHeight(ui.dp(72));
            head.addView(printerTile(current), ui.lp(48, 48));
            LinearLayout labels = ui.column(); LinearLayout.LayoutParams lpp = ui.weight(); lpp.leftMargin = ui.dp(12); lpp.rightMargin = ui.dp(12); head.addView(labels, lpp);
            labels.addView(ui.line(selection.model == null || selection.model.isEmpty() ? selection.name : selection.model, 16, 700, white));
            LinearLayout status = ui.row(); View dot = new View(this); dot.setBackground(ui.shape(0x99FFFFFF, 0, 4)); status.addView(dot, ui.lp(8, 8));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2); sp.leftMargin = ui.dp(6); status.addView(ui.line(EngineText.clean(selection.vendor) + " · " + getString(R.string.kv3_system_profile), 12, 500, secondary), sp);
            labels.addView(status, ui.below(2));
            head.addView(selectedBadge());
            card.addView(head);
            if (current != null && current.variants.size() > 0) {
                LinearLayout nozzle = ui.column(); nozzle.setPadding(ui.dp(14), 0, ui.dp(14), ui.dp(12));
                nozzle.addView(ui.line(getString(R.string.kv3_nozzle_caption), 12, 500, secondary));
                LinearLayout group = ui.row(); group.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4)); group.setBackground(ui.shape(com.keplerslicer.app.glass.KeplerUi.FIELD, 0, 16));
                android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false); hs.setFillViewport(true); hs.addView(group, new FrameLayout.LayoutParams(-1, -2));
                for (PrinterProfileCatalog.Profile p : current.variants) {
                    boolean on = p.path.equals(selection.path); String label = p.variant == null || p.variant.trim().isEmpty() ? "—" : p.variant;
                    TextView seg = ui.line(label, 13, 700, on ? com.keplerslicer.app.glass.KeplerUi.ON_ACTIVE : white); seg.setGravity(Gravity.CENTER); seg.setPadding(ui.dp(12), 0, ui.dp(12), 0); seg.setMinHeight(ui.dp(36));
                    if (on) seg.setBackground(ui.shape(white, 0, 12)); else { ui.pressable(seg, null, 12); final Model model = current; seg.setOnClickListener(v -> saveVariant(model, p, false)); }
                    seg.setContentDescription(getString(R.string.kv3_nozzle_caption) + " " + label + (on ? ", " + getString(R.string.glass_selected) : ""));
                    group.addView(seg, new LinearLayout.LayoutParams(0, ui.dp(36), 1)); ((LinearLayout.LayoutParams) seg.getLayoutParams()).width = 0; seg.setMinWidth(ui.dp(64));
                }
                if (current.variants.size() <= 5) { hs.removeView(group); nozzle.addView(group, ui.below(6)); } else nozzle.addView(hs, ui.below(6)); card.addView(nozzle);
            }
            card.addView(ui.hairline());
            PrintProfileCatalog.Profile process = PrintProfileCatalog.loadSelection(this, PrintProfileCatalog.PROCESS);
            card.addView(ui.listRow(0, process == null ? getString(R.string.kv3_choose_process) : process.name, getString(R.string.kv3_process_system), process == null ? null : selectedBadge(), () -> startActivity(new Intent(this, ProfilePickerActivity.class).putExtra(ProfilePickerActivity.EXTRA_KIND, PrintProfileCatalog.PROCESS))));
            card.addView(ui.hairline());
            card.addView(ui.listRow(0, getString(R.string.kv3_other_processes), getString(R.string.kv3_other_processes_detail), ui.image(R.drawable.kv3_chevron_right, 20, white), () -> startActivity(new Intent(this, ProfilePickerActivity.class).putExtra(ProfilePickerActivity.EXTRA_KIND, PrintProfileCatalog.PROCESS))));
            card.addView(ui.hairline());
            LinearLayout actions = ui.row(); actions.setPadding(ui.dp(14), ui.dp(13), ui.dp(14), ui.dp(14));
            actions.addView(ui.secondaryButton(R.drawable.kv3_sliders, getString(R.string.kv3_edit_printer), 44, 22, () -> startActivity(new Intent(this, SettingsActivity.class).putExtra("kepler_kind", "printer"))), ui.weight());
            LinearLayout.LayoutParams cp = ui.weight(); cp.leftMargin = ui.dp(8);
            actions.addView(ui.secondaryButton(R.drawable.kv3_wifi, getString(R.string.kv3_connection), 44, 22, () -> startActivity(new Intent(this, NetworkActivity.class))), cp);
            card.addView(actions);
            content.addView(card, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout lib = ui.card(24);
        lib.addView(ui.listRow(R.drawable.kv3_printer, getString(R.string.kv3_printer_library), getString(R.string.kv3_printer_library_detail), ui.image(R.drawable.kv3_chevron_right, 20, white), () -> { library = true; render(); }));
        content.addView(lib, ui.below(10));
    }
    private View printerTile(Model model) {
        FrameLayout tile = new FrameLayout(this); tile.setBackground(ui.shape(com.keplerslicer.app.glass.KeplerUi.FIELD, 0, 14));
        tile.addView(ui.image(R.drawable.kv3_printer, 24, com.keplerslicer.app.glass.KeplerUi.WHITE), new FrameLayout.LayoutParams(ui.dp(24), ui.dp(24), Gravity.CENTER)); return tile;
    }
    /** Mint "Selected" badge (node 5:270): 24dp mint circle with the dark check. */
    private View selectedBadge() {
        FrameLayout b = new FrameLayout(this); b.setBackground(ui.shape(com.keplerslicer.app.glass.KeplerUi.MINT, 0, 12));
        b.addView(ui.image(R.drawable.kv3_check, 16, 0xFF12302A), new FrameLayout.LayoutParams(ui.dp(16), ui.dp(16), Gravity.CENTER));
        b.setLayoutParams(ui.lp(24, 24)); b.setContentDescription(getString(R.string.glass_selected)); return b;
    }
    private void saveVariant(Model model, PrinterProfileCatalog.Profile variant, boolean close) {
        try {
            PrinterProfileCatalog.Profile previous = PrinterProfileCatalog.loadSelection(this);
            boolean changed = previous == null || previous.imported != variant.imported || !previous.path.equals(variant.path) || !previous.name.equals(variant.name) || !previous.vendor.equals(variant.vendor);
            if (changed) ProcessOverridesStore.changeSelection(this, () -> PrinterProfileCatalog.saveSelection(this, variant));
            setResult(RESULT_OK, new Intent());
            if (close) finish(); else render();
        } catch (Exception e) { screen.info("Не удалось сохранить профиль", e.getMessage()); }
    }

    private void renderList() {
        final int white = com.keplerslicer.app.glass.KeplerUi.WHITE, secondary = com.keplerslicer.app.glass.KeplerUi.SECONDARY;
        screen.body.removeView(screen.scroll);
        LinearLayout top = ui.column(); top.setPadding(0, ui.dp(8), 0, ui.dp(4)); top.addView(screen.takeTopBar());
        LinearLayout searchBox = ui.row(); searchBox.setBackground(ui.field(22)); searchBox.setPadding(ui.dp(14), 0, ui.dp(12), 0);
        searchBox.addView(ui.image(R.drawable.kv3_search, 20, secondary));
        EditText input = new EditText(this); input.setSingleLine(true); input.setTextSize(14); input.setTextColor(white); input.setHintTextColor(secondary); input.setHint("Поиск модели или производителя"); input.setBackgroundColor(Color.TRANSPARENT); input.setText(query);
        com.keplerslicer.app.glass.GlassTypography.apply(input, 500);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, ui.dp(48), 1); ip.leftMargin = ui.dp(8); searchBox.addView(input, ip); top.addView(searchBox, ui.lp(-1, 48));
        TextView counter = ui.caption(""); counter.setPadding(ui.dp(4), ui.dp(16), ui.dp(4), ui.dp(8)); top.addView(counter);
        LinearLayout wrap = ui.column();
        ListView list = new ListView(this); list.addHeaderView(top, null, false); list.setItemsCanFocus(true); list.setDivider(null); list.setClipToPadding(false); list.setSelector(new android.graphics.drawable.ColorDrawable(0)); list.setPadding(ui.dp(20), 0, ui.dp(20), ui.dp(16)); wrap.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        screen.body.addView(wrap, new FrameLayout.LayoutParams(-1, -1));
        BaseAdapter adapter = new BaseAdapter() {
            public int getCount() { return visible.size(); } public Object getItem(int pos) { return visible.get(pos); } public long getItemId(int pos) { return pos; }
            public View getView(int pos, View recycled, ViewGroup parent) {
                Model m = visible.get(pos); LinearLayout item = ui.row(); item.setBackground(ui.glass(24)); item.setPadding(ui.dp(10), ui.dp(10), ui.dp(14), ui.dp(10));
                FrameLayout thumb = coverView(m, 64); item.addView(thumb, ui.lp(64, 64));
                LinearLayout labels = ui.column(); LinearLayout.LayoutParams lp2 = ui.weight(); lp2.leftMargin = ui.dp(12); lp2.rightMargin = ui.dp(8);
                labels.addView(ui.caption(EngineText.clean(m.vendor)));
                TextView title = ui.text(m.name, 16, 700, white); title.setMaxLines(2); labels.addView(title, ui.below(2));
                labels.addView(ui.line(m.variants.size() + " проф.", 12, 500, secondary), ui.below(2)); item.addView(labels, lp2);
                item.addView(ui.image(R.drawable.kv3_chevron_right, 20, white));
                LinearLayout wrapper = ui.column(); wrapper.addView(item, ui.lp(-1, -2)); wrapper.addView(ui.spacer(10)); return wrapper;
            }
        }; list.setAdapter(adapter);
        Runnable filter = () -> { String q = query.trim().toLowerCase(Locale.ROOT); visible.clear(); for (Model m : models) if (q.isEmpty() || (m.vendor + " " + m.name).toLowerCase(Locale.ROOT).contains(q)) visible.add(m); counter.setText((visible.isEmpty() ? "Модели не найдены" : "Модели  ·  " + visible.size()).toUpperCase(Locale.getDefault())); adapter.notifyDataSetChanged(); };
        input.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s, int a, int c, int f) {} public void onTextChanged(CharSequence s, int a, int before, int count) { query = s.toString(); filter.run(); } public void afterTextChanged(Editable e) {} });
        list.setOnItemClickListener((p, v, position, id) -> { int pos = position - list.getHeaderViewsCount(); if (pos < 0 || pos >= visible.size()) return; chosenModel = visible.get(pos); chosenVariant = null; detailScrollY = 0; PrinterProfileCatalog.Profile saved = PrinterProfileCatalog.loadSelection(this); if (saved != null) for (PrinterProfileCatalog.Profile variant : chosenModel.variants) if (variant.path.equals(saved.path)) chosenVariant = variant; ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0); render(); });
        filter.run();
    }

    private void renderDetails() {
        final int white = com.keplerslicer.app.glass.KeplerUi.WHITE, secondary = com.keplerslicer.app.glass.KeplerUi.SECONDARY;
        ScrollView scroll = screen.scroll;
        content.addView(ui.caption(EngineText.clean(chosenModel.vendor)));
        content.addView(ui.text("Выберите вариант профиля для этой модели", 13, 500, secondary), ui.below(6));
        FrameLayout hero = coverView(chosenModel, 220); content.addView(hero, new LinearLayout.LayoutParams(-1, ui.dp(220))); ((LinearLayout.LayoutParams) hero.getLayoutParams()).topMargin = ui.dp(14);
        screen.section("Сопло и вариант", null, null);
        LinearLayout card = ui.card(24);
        for (int i = 0; i < chosenModel.variants.size(); i++) {
            PrinterProfileCatalog.Profile p = chosenModel.variants.get(i);
            String variant = p.variant == null || p.variant.trim().isEmpty() ? "Вариант не указан" : "Вариант  " + p.variant;
            boolean selected = chosenVariant != null && p.path.equals(chosenVariant.path);
            View trailing = selected ? selectedBadge() : null;
            LinearLayout row = ui.listRow(0, variant, p.name, trailing, () -> { chosenVariant = p; detailScrollY = scroll.getScrollY(); render(); });
            row.setContentDescription(variant + ", " + p.name + (selected ? ", выбрано" : ""));
            if (i > 0) card.addView(ui.hairline()); card.addView(row);
        }
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));
        TextView disclaimer = ui.text("Системный профиль сохраняется на устройстве и применяется при экспериментальной нарезке вместе с выбранными процессом и материалом.", 12, 500, secondary); disclaimer.setPadding(ui.dp(4), 0, ui.dp(4), 0);
        content.addView(disclaimer, ui.below(14));
        LinearLayout save = ui.primaryButton(R.drawable.kv3_check, "Сохранить выбор", () -> { if (chosenVariant != null) saveVariant(chosenModel, chosenVariant, true); });
        ui.setEnabled(save, chosenVariant != null);
        content.addView(save, ui.below(16));
        scroll.post(() -> scroll.scrollTo(0, detailScrollY));
    }
    private FrameLayout coverView(Model model,int size) {
        FrameLayout box=new FrameLayout(this); box.setBackground(shape(com.keplerslicer.app.glass.KeplerUi.FIELD,size>100?20:16)); box.setClipToOutline(true);
        TextView fallback=text("▧",size>100?48:26,com.keplerslicer.app.glass.KeplerUi.SECONDARY,false); fallback.setGravity(Gravity.CENTER); fallback.setContentDescription("Изображение модели отсутствует"); box.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
        ImageView image=new ImageView(this); image.setScaleType(ImageView.ScaleType.FIT_CENTER); image.setPadding(dp(8),dp(8),dp(8),dp(8)); image.setContentDescription("Изображение "+model.name+" из встроенного каталога");
        box.addView(image,new FrameLayout.LayoutParams(-1,-1));
        if(model.cover.isEmpty()) { image.setVisibility(View.GONE); return box; }
        image.setTag(model.cover); Bitmap cached=images.get(model.cover);
        if(cached!=null) { showCover(image,cached); fallback.setVisibility(View.GONE); return box; }
        final String filename=model.cover;
        worker.execute(()->{ try {
            BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true; try(InputStream in=getAssets().open("printer_covers/"+filename)){BitmapFactory.decodeStream(in,null,options);}
            int sample=1; while(Math.max(options.outWidth,options.outHeight)/sample>512) sample*=2;
            options.inJustDecodeBounds=false; options.inSampleSize=sample; Bitmap bitmap; try(InputStream in=getAssets().open("printer_covers/"+filename)){bitmap=BitmapFactory.decodeStream(in,null,options);}
            if(bitmap==null) return; images.put(filename,bitmap); runOnUiThread(()->{if(!isDestroyed() && filename.equals(image.getTag())){ showCover(image,bitmap); fallback.setVisibility(View.GONE); }});
        } catch(Exception ignored) { runOnUiThread(()->{if(!isDestroyed()) image.setVisibility(View.GONE);}); } });
        return box;
    }
    private static void showCover(ImageView image, Bitmap bitmap) {
        image.setImageBitmap(bitmap);
        ColorMatrix mono=new ColorMatrix(); mono.setSaturation(0);
        image.setColorFilter(new ColorMatrixColorFilter(mono));
    }
}
