package com.keplerslicer.app;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.keplerslicer.engine.ExperimentalSlicer;
import com.keplerslicer.app.glass.KeplerUi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Filament library (Figma node 5:406) and the matching process library. Selects exact upstream profile
 * identities; the engine validates final compatibility.
 */
public class ProfilePickerActivity extends Activity {
    public static final String EXTRA_KIND = "profile_kind";
    private static final String[] TYPES = {"PLA", "PETG", "ABS", "ASA", "TPU", "PA", "PC"};
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Object> rows = new ArrayList<>();
    private List<PrintProfileCatalog.Profile> all;
    private PrinterProfileCatalog.Profile printer;
    private PrintProfileCatalog.Profile selectedProcess, current;
    private String kind, query = "", type;
    private boolean showAll, favouritesOnly;
    private boolean calculating = true;
    private String compatibilityError;
    private Map<String,String> compatibility = new HashMap<>();
    private final Set<String> favourites = new HashSet<>();
    private int generation;
    private TextView status, compatibleChip, favouriteChip; private EditText search;
    private final List<TextView> typeChips = new ArrayList<>();
    private BaseAdapter adapter;
    private KeplerScreen screen; private KeplerUi ui; private LinearLayout index; private ListView listView;
    /** Alphabet index (Figma 5:406): one 11/600 letter per vendor initial; tap jumps to that vendor group. */
    private void updateIndex() {
        if (index == null) return; index.removeAllViews(); java.util.LinkedHashMap<Character, Integer> first = new java.util.LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) if (rows.get(i) instanceof Header) { String v = EngineText.clean(((Header) rows.get(i)).vendor); if (v.isEmpty()) continue; char c = Character.toUpperCase(v.charAt(0)); if (!first.containsKey(c)) first.put(c, i); }
        index.setVisibility(first.size() > 1 ? View.VISIBLE : View.GONE);
        for (java.util.Map.Entry<Character, Integer> e : first.entrySet()) { TextView t = ui.line(String.valueOf(e.getKey()), 11, 600, KeplerUi.WHITE); t.setGravity(Gravity.CENTER); t.setMinHeight(ui.dp(20)); t.setContentDescription(String.valueOf(e.getKey())); final int pos = e.getValue(); t.setOnClickListener(v -> listView.setSelectionFromTop(pos + listView.getHeaderViewsCount(), 0)); index.addView(t, new LinearLayout.LayoutParams(-1, -2)); }
    }

    private static final class Header { final String vendor; final int count; Header(String v, int c) { vendor = v; count = c; } }
    private static final class Row { final PrintProfileCatalog.Profile profile; final boolean first, last; Row(PrintProfileCatalog.Profile p, boolean f, boolean l) { profile = p; first = f; last = l; } }

    @Override public void onCreate(Bundle state) {
        MdUi.prepare(this); super.onCreate(state);
        kind = getIntent().getStringExtra(EXTRA_KIND);
        if (!PrintProfileCatalog.PROCESS.equals(kind) && !PrintProfileCatalog.FILAMENT.equals(kind)) { finish(); return; }
        if (state != null) { query = state.getString("query", ""); showAll = state.getBoolean("showAll"); type = state.getString("type"); favouritesOnly = state.getBoolean("fav"); }
        favourites.addAll(getSharedPreferences("kepler_v3_ui", MODE_PRIVATE).getStringSet("favourites_" + kind, new HashSet<>()));
        printer = PrinterProfileCatalog.loadSelection(this);
        selectedProcess = PrintProfileCatalog.FILAMENT.equals(kind) ? PrintProfileCatalog.loadSelection(this, PrintProfileCatalog.PROCESS) : null;
        current = PrintProfileCatalog.loadSelection(this, kind);
        boolean filament = PrintProfileCatalog.FILAMENT.equals(kind);
        KeplerUi kui = new KeplerUi(this);
        View plus = kui.glassButton(R.drawable.kv3_plus, 44, 22, getString(R.string.kv3_new_preset), () -> screen.unavailable(getString(R.string.kv3_new_preset)));
        screen = new KeplerScreen(this, getString(filament ? R.string.kv3_filament : R.string.kv3_process), this::finish, plus, 1); ui = screen.ui;
        if (printer == null || printer.imported || printer.path.isEmpty()) { screen.content.addView(ui.text("Сначала выберите системный профиль принтера и сопла.", 14, 500, KeplerUi.SECONDARY)); return; }

        screen.body.removeView(screen.scroll);
        LinearLayout top = ui.column(); top.setPadding(0, ui.dp(8), 0, 0);
        if (filament) {
            LinearLayout head = ui.row(); head.setPadding(ui.dp(4), ui.dp(12), ui.dp(4), ui.dp(8));
            head.addView(ui.caption(getString(R.string.kv3_loaded)), ui.weight());
            TextView sync = ui.line(getString(R.string.kv3_sync_printer), 12, 600, KeplerUi.WHITE); sync.setMinHeight(ui.dp(32)); sync.setGravity(Gravity.CENTER_VERTICAL); ui.pressable(sync, null, 8);
            sync.setOnClickListener(v -> screen.unavailable(getString(R.string.kv3_sync_printer))); head.addView(sync);
            top.addView(head, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout slots = ui.row();
            for (int i = 0; i < 4; i++) {
                boolean loaded = i == 0 && current != null;
                View slot = slotCard(i + 1, loaded ? shortName(current.name) : getString(R.string.kv3_slot_empty), loaded);
                LinearLayout.LayoutParams p = ui.weight(); if (i > 0) p.leftMargin = ui.dp(8); slots.addView(slot, p);
            }
            top.addView(slots, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout box = ui.row(); box.setBackground(ui.glass(24)); box.setPadding(ui.dp(16), 0, ui.dp(12), 0);
        box.addView(ui.image(R.drawable.kv3_search, 20, KeplerUi.WHITE));
        search = new EditText(this); search.setSingleLine(true); search.setTextSize(15); search.setTextColor(KeplerUi.WHITE); search.setHintTextColor(KeplerUi.SECONDARY);
        search.setHint(getString(R.string.kv3_search_profiles)); search.setBackground(null); search.setText(query); com.keplerslicer.app.glass.GlassTypography.apply(search, 500);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, ui.dp(48), 1); sp.leftMargin = ui.dp(10); box.addView(search, sp);
        top.addView(box, filament ? ui.below(14) : ui.below(12)); ((LinearLayout.LayoutParams) box.getLayoutParams()).height = ui.dp(48);

        HorizontalScrollView s1 = new HorizontalScrollView(this); s1.setHorizontalScrollBarEnabled(false); s1.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout r1 = ui.row(); s1.addView(r1);
        TextView allChip = ui.chip(getString(R.string.kv3_filter_all), type == null && !favouritesOnly, null); typeChips.add(allChip);
        allChip.setOnClickListener(v -> { type = null; favouritesOnly = false; updateChips(); refilter(); }); ui.pressable(allChip, allChip.getBackground(), 18);
        r1.addView(allChip, new LinearLayout.LayoutParams(-2, ui.dp(36)));
        favouriteChip = ui.chip(getString(R.string.kv3_filter_favourites), favouritesOnly, () -> { favouritesOnly = !favouritesOnly; if (favouritesOnly) type = null; updateChips(); refilter(); });
        addChip(r1, favouriteChip);
        if (filament) for (String t : TYPES) { TextView c = ui.chip(t, t.equals(type), () -> { type = t.equals(type) ? null : t; favouritesOnly = false; updateChips(); refilter(); }); c.setTag(t); typeChips.add(c); addChip(r1, c); }
        top.addView(s1, ui.below(12));
        HorizontalScrollView s2 = new HorizontalScrollView(this); s2.setHorizontalScrollBarEnabled(false); s2.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout r2 = ui.row(); s2.addView(r2);
        String printerLabel = printer.model == null || printer.model.isEmpty() ? printer.name : printer.model;
        compatibleChip = ui.chip(getString(R.string.kv3_filter_compatible, printerLabel), !showAll, () -> { showAll = !showAll; updateChips(); refilter(); });
        r2.addView(compatibleChip, new LinearLayout.LayoutParams(-2, ui.dp(36)));
        addChip(r2, ui.chip(getString(R.string.kv3_filter_my_presets), false, () -> screen.unavailable(getString(R.string.kv3_filter_my_presets))));
        addChip(r2, ui.chip(getString(R.string.kv3_filter_vendors), false, () -> screen.unavailable(getString(R.string.kv3_filter_vendors))));
        top.addView(s2, ui.below(8));
        status = ui.text("Загружаем профили…", 12, 500, KeplerUi.SECONDARY); status.setPadding(ui.dp(4), ui.dp(10), ui.dp(4), 0); top.addView(status);

        top.addView(screen.takeTopBar(), 0); LinearLayout wrap = ui.column();
        ListView list = new ListView(this); list.setDivider(null); list.setClipToPadding(false); list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.addHeaderView(top, null, false); list.setItemsCanFocus(true); list.setPadding(ui.dp(20), 0, ui.dp(28), ui.dp(16)); FrameLayout listFrame = new FrameLayout(this); listFrame.addView(list, new FrameLayout.LayoutParams(-1, -1)); listView = list;
        index = ui.column(); index.setGravity(Gravity.CENTER); FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(ui.dp(24), -2, Gravity.END | Gravity.CENTER_VERTICAL); ip.rightMargin = ui.dp(2); listFrame.addView(index, ip);
        wrap.addView(listFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        screen.body.addView(wrap, new FrameLayout.LayoutParams(-1, -1));
        adapter = new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int i) { return rows.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public int getViewTypeCount() { return 2; }
            @Override public int getItemViewType(int i) { return rows.get(i) instanceof Header ? 0 : 1; }
            @Override public boolean isEnabled(int i) { return rows.get(i) instanceof Row; }
            @Override public View getView(int i, View ignored, ViewGroup parent) { Object o = rows.get(i); return o instanceof Header ? header((Header) o) : profileRow((Row) o); }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> { int at = position - list.getHeaderViewsCount(); if (at < 0 || at >= rows.size()) return; Object o = rows.get(at); if (o instanceof Row) choose(((Row) o).profile); });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { query = s.toString(); refilter(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        final int token = ++generation;
        final android.content.Context context = getApplicationContext();
        worker.execute(() -> {
            try {
                List<PrintProfileCatalog.Profile> loaded = PrintProfileCatalog.load(context, kind);
                runOnUiThread(() -> { if (!isDestroyed() && token == generation) { all = loaded; search.setHint(getString(R.string.kv3_search_count, loaded.size())); refilter(); } });
                String processVendor = selectedProcess == null ? "" : selectedProcess.vendor;
                String processName = selectedProcess == null ? "" : selectedProcess.name;
                CompatibilityResources paths = CompatibilityResources.prepare(context, printer.vendor, processVendor);
                String[] result = ExperimentalSlicer.listSystemCompatibility(printer.vendor, printer.name, kind,
                        processVendor, processName, paths.resources.getAbsolutePath(), paths.data.getAbsolutePath());
                if (result == null || result.length % 3 != 0) throw new java.io.IOException("Неверный ответ движка совместимости.");
                Map<String,String> checked = new HashMap<>();
                for (int i = 0; i < result.length; i += 3) {
                    if (result[i] == null || result[i+1] == null || result[i+2] == null ||
                            !(result[i+2].equals("yes") || result[i+2].equals("no") || result[i+2].equals("unknown") || result[i+2].equals("printer_only")))
                        throw new java.io.IOException("Неверный ответ движка совместимости.");
                    checked.put(profileKey(result[i], result[i+1]), result[i+2]);
                }
                runOnUiThread(() -> {
                    if (isDestroyed() || token != generation) return;
                    PrinterProfileCatalog.Profile currentPrinter = PrinterProfileCatalog.loadSelection(this);
                    PrintProfileCatalog.Profile currentProcess = PrintProfileCatalog.FILAMENT.equals(kind)
                            ? PrintProfileCatalog.loadSelection(this, PrintProfileCatalog.PROCESS) : null;
                    if (currentPrinter == null || !printer.path.equals(currentPrinter.path) || !sameProfile(selectedProcess, currentProcess)) {
                        calculating = false; compatibilityError = "Выбор принтера или процесса изменился. Откройте экран заново."; showAll = true; updateChips(); refilter(); return;
                    }
                    compatibility = checked; calculating = false; refilter();
                });
            } catch (Exception | LinkageError e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> {
                    if (!isDestroyed() && token == generation) {
                        calculating = false; compatibilityError = message; showAll = true; updateChips();
                        if (all == null) status.setText("Не удалось загрузить профили: " + message); else refilter();
                    }
                });
            }
        });
    }

    private void addChip(LinearLayout row, TextView chip) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, ui.dp(36)); p.leftMargin = ui.dp(8); row.addView(chip, p); }
    private void updateChips() {
        for (TextView c : typeChips) { Object t = c.getTag(); ui.setChipActive(c, t == null ? type == null && !favouritesOnly : t.equals(type)); }
        if (favouriteChip != null) ui.setChipActive(favouriteChip, favouritesOnly);
        if (compatibleChip != null) ui.setChipActive(compatibleChip, !showAll);
    }

    /** AMS slot (node 5:406 "Loaded"): glass r18, 22dp colour circle, "n" + 11/600 name; the loaded slot has a mint stroke. */
    private View slotCard(int number, String name, boolean loaded) {
        LinearLayout c = ui.column(); c.setGravity(Gravity.CENTER); c.setPadding(ui.dp(4), ui.dp(10), ui.dp(4), ui.dp(10)); c.setMinimumHeight(ui.dp(80));
        c.setBackground(ui.shape(KeplerUi.GLASS, loaded ? 0xB35EE0C8 : KeplerUi.GLASS_STROKE, 18));
        View circle = new View(this); GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL);
        if (loaded) { g.setColor(0xFFE9EEF5); g.setStroke(Math.max(1, ui.dp(1)), 0x66FFFFFF); } else { g.setColor(0); g.setStroke(Math.max(1, ui.dp(1.5f)), 0xB3FFFFFF, ui.dp(3), ui.dp(3)); }
        circle.setBackground(g); c.addView(circle, ui.lp(24, 24));
        TextView n = ui.line(String.valueOf(number), 11, 600, KeplerUi.WHITE); n.setGravity(Gravity.CENTER); c.addView(n, ui.below(6));
        TextView t = ui.line(name, 11, 600, KeplerUi.WHITE); t.setGravity(Gravity.CENTER); c.addView(t, new LinearLayout.LayoutParams(-1, -2));
        c.setContentDescription(getString(R.string.kv3_slot, number) + ", " + name);
        if (!loaded) ui.pressable(c, c.getBackground(), 18); if (!loaded) c.setOnClickListener(v -> screen.unavailable(getString(R.string.kv3_slot, number)));
        return c;
    }
    private static String shortName(String name) { int at = name.indexOf(" @"); return at > 0 ? name.substring(0, at) : name; }
    private static String typeOf(String name) { String upper = name.toUpperCase(Locale.ROOT); for (String t : new String[]{"PETG", "PLA", "ABS", "ASA", "TPU", "PCTG", "PA", "PC", "PVA", "HIPS", "PET"}) if (upper.matches(".*(^|[^A-Z])" + t + "([^A-Z]|$).*")) return t; return ""; }

    private View header(Header h) {
        LinearLayout r = ui.row(); r.setPadding(ui.dp(4), ui.dp(20), ui.dp(4), ui.dp(8));
        r.addView(ui.caption(EngineText.clean(h.vendor)), ui.weight()); r.addView(ui.line(String.valueOf(h.count), 12, 700, KeplerUi.SECONDARY)); return r;
    }
    /** Profile row (node 5:406 list): 26dp swatch, 15/600 name + mint slot tag, 12/500 detail, star toggle; rows join into one r24 card. */
    private View profileRow(Row row) {
        PrintProfileCatalog.Profile p = row.profile; boolean filament = PrintProfileCatalog.FILAMENT.equals(kind);
        boolean selected = current != null && current.path.equals(p.path) && current.vendor.equals(p.vendor) && current.name.equals(p.name);
        LinearLayout r = ui.row(); r.setPadding(ui.dp(14), ui.dp(10), ui.dp(8), ui.dp(10)); r.setMinimumHeight(ui.dp(58));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(KeplerUi.GLASS); bg.setStroke(Math.max(1, ui.dp(1)), KeplerUi.GLASS_STROKE);
        float rad = ui.dp(24), top = row.first ? rad : 0, bottom = row.last ? rad : 0; bg.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        r.setBackground(bg);
        if (filament) { View sw = new View(this); GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(selected ? 0xFFE9EEF5 : 0xFF9DA3AD); g.setStroke(Math.max(1, ui.dp(1)), 0x66FFFFFF); sw.setBackground(g); r.addView(sw, ui.lp(26, 26)); }
        else { FrameLayout tile = new FrameLayout(this); tile.setBackground(ui.shape(KeplerUi.GLASS, 0, 10)); tile.addView(ui.image(R.drawable.kv3_layers, 18, KeplerUi.WHITE), new FrameLayout.LayoutParams(ui.dp(18), ui.dp(18), Gravity.CENTER)); r.addView(tile, ui.lp(32, 32)); }
        LinearLayout col = ui.column(); LinearLayout.LayoutParams cp = ui.weight(); cp.leftMargin = ui.dp(14); cp.rightMargin = ui.dp(6); r.addView(col, cp);
        LinearLayout titleRow = ui.row(); TextView name = ui.line(shortName(p.name), 15, 600, KeplerUi.WHITE); name.setMaxWidth(ui.dp(selected ? 190 : 250)); titleRow.addView(name, new LinearLayout.LayoutParams(-2, -2));
        if (selected) { TextView tag = ui.line(filament ? getString(R.string.kv3_slot, 1) : getString(R.string.glass_selected), 11, 600, KeplerUi.MINT); tag.setPadding(ui.dp(7), ui.dp(2), ui.dp(7), ui.dp(2)); tag.setBackground(ui.shape(0x2E5EE0C8, 0, 6)); LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2); tp.leftMargin = ui.dp(8); titleRow.addView(tag, tp); }
        col.addView(titleRow);
        String t = typeOf(p.name); String detail = (t.isEmpty() ? "" : t + " · ") + compatibilityLabel(compatibility.get(profileKey(p.vendor, p.name)));
        TextView sub = ui.line(detail, 12, 500, KeplerUi.SECONDARY); col.addView(sub, ui.below(2));
        String key = profileKey(p.vendor, p.name); boolean fav = favourites.contains(key);
        FrameLayout star = new FrameLayout(this); ImageView art = ui.image(fav ? R.drawable.kv3_star_filled : R.drawable.kv3_star, 22, KeplerUi.WHITE);
        star.addView(art, new FrameLayout.LayoutParams(ui.dp(22), ui.dp(22), Gravity.CENTER)); ui.pressable(star, null, 22);
        star.setContentDescription(getString(R.string.kv3_filter_favourites)); star.setFocusable(true);
        star.setOnClickListener(v -> { if (favourites.contains(key)) favourites.remove(key); else favourites.add(key); getSharedPreferences("kepler_v3_ui", MODE_PRIVATE).edit().putStringSet("favourites_" + kind, new HashSet<>(favourites)).apply(); refilter(); });
        r.addView(star, ui.lp(44, 44));
        r.setContentDescription(p.name + ", " + p.vendor + ", " + detail);
        // The list lets header fields take focus, so rows with a focusable star handle their own tap.
        r.setOnClickListener(v -> choose(p));
        LinearLayout wrapper = ui.column(); wrapper.addView(r, new LinearLayout.LayoutParams(-1, -2));
        if (!row.last) { View d = new View(this); d.setBackgroundColor(KeplerUi.DIVIDER); wrapper.addView(d, new LinearLayout.LayoutParams(-1, Math.max(1, ui.dp(1)))); }
        return wrapper;
    }

    private void choose(PrintProfileCatalog.Profile next) {
        try {
            PrintProfileCatalog.Profile previous = PrintProfileCatalog.loadSelection(this, kind);
            boolean changed = previous == null || !previous.path.equals(next.path) || !previous.vendor.equals(next.vendor) || !previous.name.equals(next.name);
            if (changed) ProcessOverridesStore.changeSelection(this, () -> {
                PrinterProfileCatalog.Profile currentPrinter = PrinterProfileCatalog.loadSelection(this);
                if (currentPrinter == null || !currentPrinter.path.equals(printer.path)) throw new java.io.IOException("Профиль принтера изменился. Откройте выбор снова.");
                PrintProfileCatalog.saveSelection(this, next, printer);
            });
            setResult(RESULT_OK); finish();
        } catch (Exception e) { screen.info("Не удалось сохранить профиль", e.getMessage()); }
    }

    private static String profileKey(String vendor, String name) { return vendor + "\u0000" + name; }
    private static boolean sameProfile(PrintProfileCatalog.Profile a, PrintProfileCatalog.Profile b) {
        return a == null ? b == null : b != null && a.vendor.equals(b.vendor) && a.name.equals(b.name);
    }
    private String compatibilityLabel(String value) {
        if (value == null) return calculating ? "проверяется" : "не проверен";
        switch (value) {
            case "yes": return "совместим";
            case "no": return "несовместим";
            case "printer_only": return "совместим с принтером; выберите процесс";
            default: return "ошибка проверки условия";
        }
    }
    private void refilter() {
        if (status == null || all == null) return;
        String q = query.trim().toLowerCase(Locale.ROOT); List<PrintProfileCatalog.Profile> visible = new ArrayList<>();
        for (PrintProfileCatalog.Profile p : all) {
            String state = compatibility.get(profileKey(p.vendor, p.name));
            if (!showAll && !("yes".equals(state) || "printer_only".equals(state))) continue;
            if (!q.isEmpty() && !(p.name + " " + p.vendor).toLowerCase(Locale.ROOT).contains(q)) continue;
            if (type != null && !type.equals(typeOf(p.name))) continue;
            if (favouritesOnly && !favourites.contains(profileKey(p.vendor, p.name))) continue;
            visible.add(p);
        }
        visible.sort(Comparator.<PrintProfileCatalog.Profile, String>comparing(p -> EngineText.clean(p.vendor).toLowerCase(Locale.ROOT))
                .thenComparingInt(p -> "yes".equals(compatibility.get(profileKey(p.vendor, p.name))) ? 0 : 1)
                .thenComparing(p -> p.name.toLowerCase(Locale.ROOT)));
        rows.clear();
        for (int i = 0; i < visible.size(); ) {
            String vendor = visible.get(i).vendor; int j = i; while (j < visible.size() && visible.get(j).vendor.equals(vendor)) j++;
            rows.add(new Header(vendor, j - i));
            for (int k = i; k < j; k++) rows.add(new Row(visible.get(k), k == i, k == j - 1));
            i = j;
        }
        if (calculating) status.setText("Проверяем совместимость профилей…");
        else if (compatibilityError != null) status.setText("Не удалось проверить совместимость: " + compatibilityError);
        else status.setText(visible.isEmpty() ? "Ничего не найдено" : "");
        status.setVisibility(status.getText().length() == 0 ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged(); updateIndex();
    }
    @Override protected void onSaveInstanceState(Bundle out) { out.putString("query", query); out.putBoolean("showAll", showAll); out.putString("type", type); out.putBoolean("fav", favouritesOnly); super.onSaveInstanceState(out); }
    @Override protected void onDestroy() { generation++; worker.shutdownNow(); super.onDestroy(); }
}
