package com.orcamobile.app;

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

/** Real printer-model and nozzle variant picker backed by the bundled upstream Orca catalog. */
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
    private OrcaProfileCatalog.Profile chosenVariant;
    private String query = "", restoredModel, restoredVariant;
    private String error;
    private int detailScrollY;

    private static final class Model {
        final String vendor, name; String cover = "";
        final List<OrcaProfileCatalog.Profile> variants = new ArrayList<>();
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
                List<OrcaProfileCatalog.Profile> profiles = OrcaProfileCatalog.load(getApplicationContext());
                HashMap<String,String> covers = readCovers();
                LinkedHashMap<String,Model> byKey = new LinkedHashMap<>();
                for (OrcaProfileCatalog.Profile p : profiles) {
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
                    OrcaProfileCatalog.Profile selection=OrcaProfileCatalog.loadSelection(this);
                    if (chosenModel != null) for (OrcaProfileCatalog.Profile p : chosenModel.variants) {
                        if (restoredVariant != null ? p.path.equals(restoredVariant) : selection != null && p.path.equals(selection.path)) chosenVariant=p;
                    }
                    render();
                });
            } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed()) { error=e.getMessage(); render(); } }); }
        });
    }
    private HashMap<String,String> readCovers() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = getAssets().open("orca_machine_models.json")) { byte[] b=new byte[8192]; int n,total=0; while((n=in.read(b))!=-1) { total+=n; if(total>2*1024*1024) throw new java.io.IOException("Слишком большой индекс моделей"); out.write(b,0,n); } }
        JSONObject index = new JSONObject(new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
        if (!OrcaProfileCatalog.UPSTREAM_REVISION.equals(index.optString("revision"))) throw new java.io.IOException("Версии каталогов не совпадают");
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
    private void handleBack() { if(chosenModel!=null) { chosenModel=null; chosenVariant=null; render(); } else finish(); }
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
    private void render() {
        root=column(); root.setBackgroundColor(BG);
        FrameLayout shell=new FrameLayout(this); shell.setBackgroundColor(BG);
        if(android.os.Build.VERSION.SDK_INT>=35) shell.setOnApplyWindowInsetsListener((v,insets)->{ android.graphics.Insets s=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout()); v.setPadding(s.left,s.top,s.right,s.bottom); return insets; });
        FrameLayout.LayoutParams panel=new FrameLayout.LayoutParams(-1,-1,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        if(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density>640) panel.width=dp(600);
        shell.addView(root,panel); setContentView(shell);
        LinearLayout bar=row(); padding(bar,20,12,20,8);
        TextView back=text("‹",32,INK,false); back.setGravity(Gravity.CENTER); back.setContentDescription("Назад"); bar.addView(back,lp(42,48)); back.setOnClickListener(v->onBackPressed());
        LinearLayout heading=column(); padding(heading,8,0,0,0);
        TextView name=text("KEPLER SLICER",11,INK,true); name.setLetterSpacing(.13f); heading.addView(name); gap(heading,4); heading.addView(text("НАСТРОЙКА ПРИНТЕРА",9,MUTED,true));
        bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1)); root.addView(bar);
        if(models==null) { TextView state=text(error==null ? "Загружаем каталог OrcaSlicer…" : "Не удалось загрузить каталог: "+error,14,MUTED,false); padding(state,24,28,24,0); root.addView(state); return; }
        if(chosenModel==null) renderList(); else renderDetails();
    }
    private void renderList() {
        LinearLayout top=column(); padding(top,24,20,24,8); root.addView(top);
        top.addView(text("Выберите принтер",27,INK,true)); gap(top,8);
        top.addView(text("Модели и варианты сопел из каталога OrcaSlicer",13,MUTED,false)); gap(top,20);
        LinearLayout searchBox=row(); searchBox.setBackground(outline(CARD,24)); padding(searchBox,16,0,12,0);
        TextView searchIcon=text("⌕",24,MUTED,false); searchBox.addView(searchIcon,lp(30,48));
        EditText input=new EditText(this); input.setSingleLine(true); input.setTextSize(14); input.setTextColor(INK); input.setHintTextColor(MUTED); input.setHint("Поиск модели или производителя"); input.setBackgroundColor(Color.TRANSPARENT); input.setText(query);
        searchBox.addView(input,new LinearLayout.LayoutParams(0,dp(48),1)); top.addView(searchBox,lp(-1,50)); gap(top,18);
        TextView counter=text("",12,MUTED,true); top.addView(counter); gap(top,4);
        ListView list=new ListView(this); list.setDivider(null); list.setClipToPadding(false); list.setPadding(dp(24),0,dp(24),dp(16)); root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        BaseAdapter adapter=new BaseAdapter() {
            public int getCount(){return visible.size();} public Object getItem(int pos){return visible.get(pos);} public long getItemId(int pos){return pos;}
            public View getView(int pos, View recycled, ViewGroup parent){
                Model m=visible.get(pos); LinearLayout item=row(); item.setBackground(outline(CARD,20)); padding(item,10,10,12,10);
                FrameLayout thumb=coverView(m,72); item.addView(thumb,lp(72,72));
                LinearLayout labels=column(); padding(labels,14,0,4,0); labels.addView(text(m.vendor.toUpperCase(Locale.ROOT),10,MUTED,true)); gap(labels,5);
                TextView title=text(m.name,15,INK,true); title.setMaxLines(2); labels.addView(title); gap(labels,5); labels.addView(text(m.variants.size()+" проф.",11,MUTED,false)); item.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
                item.addView(text("›",26,MUTED,false)); LinearLayout wrapper=column(); wrapper.addView(item,lp(-1,-2)); gap(wrapper,8); return wrapper;
            }
        }; list.setAdapter(adapter);
        Runnable filter=()->{ String q=query.trim().toLowerCase(Locale.ROOT); visible.clear(); for(Model m:models) if(q.isEmpty() || (m.vendor+" "+m.name).toLowerCase(Locale.ROOT).contains(q)) visible.add(m); counter.setText(visible.isEmpty()?"Модели не найдены":"МОДЕЛИ  ·  "+visible.size()); adapter.notifyDataSetChanged(); };
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){} public void onTextChanged(CharSequence s,int a,int before,int count){ query=s.toString();filter.run();} public void afterTextChanged(Editable e){} });
        list.setOnItemClickListener((p,v,pos,id)->{ chosenModel=visible.get(pos); chosenVariant=null; detailScrollY=0; OrcaProfileCatalog.Profile saved=OrcaProfileCatalog.loadSelection(this); if(saved!=null) for(OrcaProfileCatalog.Profile variant:chosenModel.variants) if(variant.path.equals(saved.path)) chosenVariant=variant; ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0); render(); });
        filter.run();
    }
    private void renderDetails() {
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=column(); padding(content,24,16,24,24); scroll.addView(content);
        TextView vendor=text(chosenModel.vendor.toUpperCase(Locale.ROOT),11,MUTED,true); vendor.setLetterSpacing(.08f); content.addView(vendor); gap(content,8);
        content.addView(text(chosenModel.name,27,INK,true)); gap(content,8); content.addView(text("Выберите вариант профиля для этой модели",13,MUTED,false)); gap(content,20);
        FrameLayout hero=coverView(chosenModel,240); content.addView(hero,lp(-1,240)); gap(content,22);
        content.addView(text("Сопло и вариант",18,INK,true)); gap(content,7);
        content.addView(text("Названия вариантов взяты из профилей OrcaSlicer.",12,MUTED,false)); gap(content,12);
        for(OrcaProfileCatalog.Profile p:chosenModel.variants) {
            LinearLayout option=row(); option.setBackground(outline(CARD,18)); padding(option,16,13,16,13);
            LinearLayout labels=column(); String variant=p.variant==null || p.variant.trim().isEmpty()?"Вариант не указан":"Вариант  " + p.variant;
            labels.addView(text(variant,15,INK,true)); gap(labels,5); labels.addView(text(p.name,11,MUTED,false)); option.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            boolean selected=chosenVariant!=null && p.path.equals(chosenVariant.path); TextView radio=text(selected?"●":"○",24,INK,false); radio.setContentDescription(selected?"Выбрано":"Не выбрано"); option.addView(radio);
            option.setContentDescription(variant+", "+p.name+(selected?", выбрано":"")); option.setOnClickListener(v->{chosenVariant=p; detailScrollY=scroll.getScrollY(); render();}); content.addView(option,lp(-1,-2)); gap(content,8);
        }
        gap(content,8); TextView disclaimer=text("Системный профиль сохраняется на устройстве и применяется при экспериментальной нарезке вместе с выбранными процессом и материалом.",12,MUTED,false); disclaimer.setLineSpacing(dp(3),1); content.addView(disclaimer); gap(content,16);
        TextView save=text("Сохранить выбор",14,chosenVariant==null?MUTED:BG,true); save.setGravity(Gravity.CENTER); save.setBackground(shape(chosenVariant==null?LINE:INK,24)); save.setEnabled(chosenVariant!=null); content.addView(save,lp(-1,50));
        save.setOnClickListener(v->{try {
            OrcaProfileCatalog.Profile previous=OrcaProfileCatalog.loadSelection(this);
            boolean changed=previous==null || previous.imported!=chosenVariant.imported || !previous.path.equals(chosenVariant.path) || !previous.name.equals(chosenVariant.name) || !previous.vendor.equals(chosenVariant.vendor);
            if(changed) ProcessOverridesStore.changeSelection(this,()->OrcaProfileCatalog.saveSelection(this,chosenVariant));
            setResult(RESULT_OK,new Intent()); finish();
        } catch(Exception e) { new android.app.AlertDialog.Builder(this).setTitle("Не удалось сохранить профиль").setMessage(e.getMessage()).setPositiveButton("Понятно",null).show(); }});
        scroll.post(()->scroll.scrollTo(0,detailScrollY));
    }
    private FrameLayout coverView(Model model,int size) {
        FrameLayout box=new FrameLayout(this); box.setBackground(shape(SURFACE,20)); box.setClipToOutline(true);
        TextView fallback=text("▧",size>100?48:26,MUTED,false); fallback.setGravity(Gravity.CENTER); fallback.setContentDescription("Изображение модели отсутствует"); box.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
        ImageView image=new ImageView(this); image.setScaleType(ImageView.ScaleType.FIT_CENTER); image.setPadding(dp(8),dp(8),dp(8),dp(8)); image.setContentDescription("Изображение "+model.name+" из OrcaSlicer");
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
