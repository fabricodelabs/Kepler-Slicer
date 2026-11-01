package com.orcamobile.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.keplerslicer.engine.ExperimentalSlicer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Selects exact upstream profile identities. The engine validates final compatibility. */
public class ProfilePickerActivity extends Activity {
    public static final String EXTRA_KIND = "profile_kind";
    private int BG, INK, MUTED, CARD, CONTAINER;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<OrcaProcessFilamentCatalog.Profile> visible = new ArrayList<>();
    private List<OrcaProcessFilamentCatalog.Profile> all;
    private OrcaProfileCatalog.Profile printer;
    private OrcaProcessFilamentCatalog.Profile selectedProcess;
    private String kind, query = "";
    private boolean showAll;
    private boolean calculating = true;
    private String compatibilityError;
    private Map<String,String> compatibility = new HashMap<>();
    private int generation;
    private TextView status, filter;
    private BaseAdapter adapter;
    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); }
    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); return v;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private void pad(View v, int l, int t, int r, int b) { v.setPadding(dp(l),dp(t),dp(r),dp(b)); }

    @Override public void onCreate(Bundle state) {
        MdUi.prepare(this); super.onCreate(state);
        MdUi palette = new MdUi(this);
        BG=palette.bg; INK=palette.ink; MUTED=palette.muted; CARD=palette.surface; CONTAINER=palette.container;
        kind = getIntent().getStringExtra(EXTRA_KIND);
        if (!OrcaProcessFilamentCatalog.PROCESS.equals(kind) && !OrcaProcessFilamentCatalog.FILAMENT.equals(kind)) { finish(); return; }
        if (state != null) { query = state.getString("query", ""); showAll = state.getBoolean("showAll"); }
        printer = OrcaProfileCatalog.loadSelection(this);
        selectedProcess = OrcaProcessFilamentCatalog.FILAMENT.equals(kind)
                ? OrcaProcessFilamentCatalog.loadSelection(this, OrcaProcessFilamentCatalog.PROCESS) : null;
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(palette.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        if (android.os.Build.VERSION.SDK_INT >= 35) root.setOnApplyWindowInsetsListener((v,insets) -> {
            android.graphics.Insets s = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            v.setPadding(s.left,s.top,s.right,s.bottom); return insets;
        });
        setContentView(root);
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); pad(bar,20,12,20,8);
        TextView back = text("‹",32,INK,false); back.setGravity(Gravity.CENTER); back.setContentDescription("Назад"); back.setOnClickListener(v -> finish()); bar.addView(back,lp(42,48));
        TextView title = text(OrcaProcessFilamentCatalog.PROCESS.equals(kind) ? "Профиль печати" : "Профиль материала", 21, INK, true);
        pad(title,8,0,0,0); bar.addView(title,new LinearLayout.LayoutParams(0,-2,1)); root.addView(bar);
        if (printer == null || printer.imported || printer.path.isEmpty()) {
            TextView msg = text("Сначала выберите системный профиль принтера и сопла.",14,MUTED,false); pad(msg,24,20,24,0); root.addView(msg); return;
        }
        LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.VERTICAL); pad(top,24,8,24,8); root.addView(top);
        top.addView(text(printer.name,13,INK,true));
        TextView explanation = text("Движок Orca проверяет производителя принтера, выбранного процесса и библиотеку материалов. Остальные профили общего каталога не проверены.",12,MUTED,false);
        pad(explanation,0,6,0,12); top.addView(explanation);
        EditText search = new EditText(this); search.setSingleLine(true); search.setTextSize(15); search.setTextColor(INK);
        search.setHintTextColor(MUTED); search.setHint("Поиск профиля"); search.setBackground(shape(CARD,22));
        pad(search,16,0,16,0); search.setText(query); top.addView(search,lp(-1,50));
        filter = text("",13,INK,true); filter.setGravity(Gravity.CENTER); filter.setBackground(shape(CONTAINER,100));
        filter.setMinHeight(dp(48)); pad(filter,12,8,12,8);
        LinearLayout.LayoutParams filterLp = lp(-1,-2); filterLp.topMargin = dp(12); top.addView(filter,filterLp);
        filter.setOnClickListener(v -> { showAll = !showAll; refilter(); });
        status = text("Загружаем профили…",12,MUTED,false); pad(status,0,12,0,4); top.addView(status);
        ListView list = new ListView(this); list.setDivider(null); list.setClipToPadding(false); list.setPadding(dp(24),0,dp(24),dp(16));
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        adapter = new BaseAdapter() {
            @Override public int getCount() { return visible.size(); }
            @Override public Object getItem(int i) { return visible.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View ignored, ViewGroup parent) {
                OrcaProcessFilamentCatalog.Profile p = visible.get(i);
                LinearLayout wrapper = new LinearLayout(ProfilePickerActivity.this); wrapper.setOrientation(LinearLayout.VERTICAL);
                LinearLayout row = new LinearLayout(ProfilePickerActivity.this); row.setOrientation(LinearLayout.VERTICAL);
                row.setBackground(shape(CARD,18)); pad(row,16,12,16,12);
                TextView name = text(p.name,15,INK,true); name.setMaxLines(2); row.addView(name);
                String hint = p.vendor + " · " + compatibilityLabel(compatibility.get(profileKey(p.vendor,p.name)));
                TextView subtitle = text(hint,11,MUTED,false); pad(subtitle,0,4,0,0); row.addView(subtitle);
                wrapper.addView(row,lp(-1,-2)); View gap = new View(ProfilePickerActivity.this); wrapper.addView(gap,lp(1,8)); return wrapper;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            try {
                OrcaProcessFilamentCatalog.Profile next=visible.get(position), previous=OrcaProcessFilamentCatalog.loadSelection(this,kind);
                boolean changed=previous==null || !previous.path.equals(next.path) || !previous.vendor.equals(next.vendor) || !previous.name.equals(next.name);
                if(changed) ProcessOverridesStore.changeSelection(this,()->{
                    OrcaProfileCatalog.Profile current=OrcaProfileCatalog.loadSelection(this);
                    if(current==null || !current.path.equals(printer.path)) throw new java.io.IOException("Профиль принтера изменился. Откройте выбор снова.");
                    OrcaProcessFilamentCatalog.saveSelection(this,next,printer);
                });
                setResult(RESULT_OK); finish();
            }
            catch (Exception e) { new android.app.AlertDialog.Builder(this).setTitle("Не удалось сохранить профиль").setMessage(e.getMessage()).setPositiveButton("Понятно",null).show(); }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { query = s.toString(); refilter(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        final int token = ++generation;
        final android.content.Context context = getApplicationContext();
        worker.execute(() -> {
            try {
                List<OrcaProcessFilamentCatalog.Profile> loaded = OrcaProcessFilamentCatalog.load(context,kind);
                runOnUiThread(() -> { if (!isDestroyed() && token == generation) { all = loaded; refilter(); } });
                String processVendor = selectedProcess == null ? "" : selectedProcess.vendor;
                String processName = selectedProcess == null ? "" : selectedProcess.name;
                CompatibilityResources paths = CompatibilityResources.prepare(context, printer.vendor, processVendor);
                String[] rows = ExperimentalSlicer.listSystemCompatibility(printer.vendor, printer.name, kind,
                        processVendor, processName, paths.resources.getAbsolutePath(), paths.data.getAbsolutePath());
                if (rows == null || rows.length % 3 != 0) throw new java.io.IOException("Неверный ответ движка совместимости.");
                Map<String,String> checked = new HashMap<>();
                for (int i=0; i<rows.length; i+=3) {
                    if (rows[i] == null || rows[i+1] == null || rows[i+2] == null ||
                            !(rows[i+2].equals("yes") || rows[i+2].equals("no") || rows[i+2].equals("unknown") || rows[i+2].equals("printer_only")))
                        throw new java.io.IOException("Неверный ответ движка совместимости.");
                    checked.put(profileKey(rows[i], rows[i+1]), rows[i+2]);
                }
                runOnUiThread(() -> {
                    if (isDestroyed() || token != generation) return;
                    OrcaProfileCatalog.Profile current = OrcaProfileCatalog.loadSelection(this);
                    OrcaProcessFilamentCatalog.Profile currentProcess = OrcaProcessFilamentCatalog.FILAMENT.equals(kind)
                            ? OrcaProcessFilamentCatalog.loadSelection(this, OrcaProcessFilamentCatalog.PROCESS) : null;
                    if (current == null || !printer.path.equals(current.path) || !sameProfile(selectedProcess,currentProcess)) {
                        calculating = false; compatibilityError = "Выбор принтера или процесса изменился. Откройте экран заново."; showAll = true; refilter(); return;
                    }
                    compatibility = checked; calculating = false; refilter();
                });
            } catch (Exception | LinkageError e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> {
                    if (!isDestroyed() && token == generation) {
                        calculating = false; compatibilityError = message; showAll = true;
                        if (all == null) status.setText("Не удалось загрузить профили: " + message); else refilter();
                    }
                });
            }
        });
    }
    private static String profileKey(String vendor, String name) { return vendor + "\u0000" + name; }
    private static boolean sameProfile(OrcaProcessFilamentCatalog.Profile a, OrcaProcessFilamentCatalog.Profile b) {
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
        if (filter == null || all == null) return;
        filter.setText(showAll ? "Весь каталог  ·  Показать проверенные" : "Проверенные  ·  Показать весь каталог");
        String q = query.trim().toLowerCase(Locale.ROOT); visible.clear();
        for (OrcaProcessFilamentCatalog.Profile p : all) {
            String state = compatibility.get(profileKey(p.vendor,p.name));
            if (!showAll && !("yes".equals(state) || "printer_only".equals(state))) continue;
            if (!q.isEmpty() && !(p.name + " " + p.vendor).toLowerCase(Locale.ROOT).contains(q)) continue;
            visible.add(p);
        }
        visible.sort(Comparator.<OrcaProcessFilamentCatalog.Profile>comparingInt(p -> "yes".equals(compatibility.get(profileKey(p.vendor,p.name))) ? 0 : 1)
                .thenComparing(p -> p.name.toLowerCase(Locale.ROOT)).thenComparing(p -> p.vendor));
        if (calculating) status.setText("Проверяем совместимость в OrcaSlicer…");
        else if (compatibilityError != null) status.setText("Не удалось проверить совместимость: " + compatibilityError);
        else status.setText(visible.isEmpty() ? "Ничего не найдено" : "ПРОФИЛИ  ·  " + visible.size());
        adapter.notifyDataSetChanged();
    }
    @Override protected void onSaveInstanceState(Bundle out) { out.putString("query",query); out.putBoolean("showAll",showAll); super.onSaveInstanceState(out); }
    @Override protected void onDestroy() { generation++; worker.shutdownNow(); super.onDestroy(); }
}
