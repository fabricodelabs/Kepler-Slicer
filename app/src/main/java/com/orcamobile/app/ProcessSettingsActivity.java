package com.orcamobile.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import com.keplerslicer.engine.ExperimentalSlicer;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mobile editor backed by the resolved upstream process config and definitions. */
public final class ProcessSettingsActivity extends Activity {
    private Object backCallback;
    private int BG, INK, MUTED, SURFACE;
    private MdUi ui;
    private static final String[] PAGES={"Quality","Strength","Speed","Support","Multimaterial","Others"};
    private ProcessOverridesStore.Identity identity;
    private CompatibilityResources resources;
    private List<ProcessOption> options;
    private final Map<String, String> raw=new LinkedHashMap<>();
    private final Map<String, View> controls=new LinkedHashMap<>();
    private JSONObject russian=new JSONObject();
    private LinearLayout root, body, actions;
    private TextView status;
    private String selected="";
    private boolean saving;
    private Bundle restored;
    private int dp(float n) { return (int)(n*getResources().getDisplayMetrics().density+.5f); }
    private LinearLayout.LayoutParams lp(int w,int h) { return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)); }
    private TextView text(String s,int size,int color,boolean bold) { TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL)); v.setLineSpacing(dp(2),1); return v; }
    private GradientDrawable shape(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private void gap(LinearLayout parent,int h) { parent.addView(new View(this),lp(1,h)); }
    private TextView button(String label,boolean primary) { TextView t=text(label,14,primary?BG:INK,true); t.setGravity(Gravity.CENTER); t.setMinHeight(dp(48)); t.setPadding(dp(15),0,dp(15),0); t.setBackground(shape(primary?INK:SURFACE,100)); return t; }
    @Override public void onCreate(Bundle state) {
        MdUi.prepare(this); super.onCreate(state); if(android.os.Build.VERSION.SDK_INT>=33)backCallback=Api33.register(this); restored=state;
        ui=new MdUi(this); BG=ui.bg; INK=ui.ink; MUTED=ui.muted; SURFACE=ui.surface;
        if(state!=null) selected=state.getString("page","");
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        ui.bars(this,root);
        setContentView(root);
        TextView back=text("‹   Назад",15,INK,true); back.setPadding(dp(20),dp(12),dp(20),dp(8)); back.setMinHeight(dp(48)); back.setOnClickListener(v->back()); root.addView(back);
        TextView title=text("Параметры печати",26,INK,true); title.setPadding(dp(20),0,dp(20),dp(8)); root.addView(title);
        status=text("Загружаем настройки профиля Orca…",13,MUTED,false); status.setPadding(dp(20),0,dp(20),dp(10)); root.addView(status);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(false); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(20),dp(16),dp(20),dp(24)); scroll.addView(body);
        actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); actions.setPadding(dp(16),dp(8),dp(16),dp(12)); root.addView(actions,lp(-1,-2));
        ui.navigation(this,root,1);
        load();
    }
    private void load() {
        android.content.Context app=getApplicationContext();
        new Thread(()->{
            try {
                ProcessOverridesStore.Snapshot snap=ProcessOverridesStore.snapshotForSlice(app);
                ProcessOverridesStore.Identity id=snap.identity;
                CompatibilityResources prepared=CompatibilityResources.prepareTriple(app,id.printer.vendor,id.process.vendor,id.filament.vendor);
                byte[] bytes=ExperimentalSlicer.processSettingsMetadata(id.printer.vendor,id.process.vendor,id.filament.vendor,id.printer.name,id.process.name,id.filament.name,prepared.resources.getAbsolutePath(),prepared.data.getAbsolutePath());
                List<ProcessOption> parsed=ProcessOption.parseMetadata(new String(bytes,StandardCharsets.UTF_8));
                JSONObject translations=readTranslations(app);
                String stored=ProcessOverridesStore.load(app,id);
                runOnUiThread(()->{
                    if(isDestroyed()||isFinishing()) return;
                    try {
                        identity=id; resources=prepared; options=parsed; russian=translations;
                        JSONObject values=new JSONObject(stored);
                        for(java.util.Iterator<String> it=values.keys();it.hasNext();) { String key=it.next(); raw.put(key,String.valueOf(values.get(key))); }
                        if(restored!=null) { ArrayList<String> keys=restored.getStringArrayList("draft_keys"), vals=restored.getStringArrayList("draft_values"); if(keys!=null&&vals!=null&&keys.size()==vals.size()) for(int i=0;i<keys.size();i++) raw.put(keys.get(i),vals.get(i)); }
                        status.setText(id.processName+"  ·  "+id.printerName+"  ·  "+id.filamentName);
                        renderPage(); renderActions();
                    } catch(Exception e) { showError(e); }
                });
            } catch(Throwable e) { runOnUiThread(()->{ if(!isDestroyed()&&!isFinishing()) status.setText("Не удалось загрузить параметры: "+message(e)); }); }
        },"orca-process-settings").start();
    }
    private static JSONObject readTranslations(android.content.Context context) {
        try(InputStream in=context.getAssets().open("orca_ui_ru.json")) { ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[8192]; int n,total=0; while((n=in.read(b))!=-1) { total+=n; if(total>4*1024*1024) return new JSONObject(); out.write(b,0,n); } return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)); } catch(Exception ignored) { return new JSONObject(); }
    }
    private String tr(String source) { String s=russian.optString(source,source); return s.isEmpty()?source:s; }
    private String pageName(String s) { switch(s) { case "Quality":return "Качество";case "Strength":return "Прочность";case "Speed":return "Скорость";case "Support":return "Поддержки";case "Multimaterial":return "Мультиматериал";default:return "Прочее"; } }
    private void renderPage() {
        body.removeAllViews(); controls.clear();
        if(selected.isEmpty()) { renderLanding(); return; }
        body.addView(text(pageName(selected),22,INK,true)); gap(body,18);
        String lastSection=null; int count=0;
        for(ProcessOption o:options) {
            if(!selected.equals(o.category)) continue; count++;
            if(!o.section.equals(lastSection)) { if(lastSection!=null) gap(body,12); body.addView(text(tr(o.section),18,INK,true)); gap(body,12); lastSection=o.section; }
            String title=tr(o.label.isEmpty()?o.key:o.label); if(!o.unit.isEmpty() && !title.contains(o.unit)) title+="  ·  "+tr(o.unit);
            body.addView(text(title,14,INK,true));
            if(!o.tooltip.isEmpty()) { TextView hint=text(tr(o.tooltip),12,MUTED,false); body.addView(hint); }
            if(!o.editable) {
                String value=String.valueOf(o.value), shortValue=value.length()>240?value.substring(0,240)+"…":value;
                TextView readOnly=text(shortValue+"  ·  только просмотр",13,MUTED,false); readOnly.setPadding(0,dp(6),0,dp(4)); body.addView(readOnly);
                if(value.length()>240) readOnly.setOnClickListener(v->MdUi.alert(this).setTitle(titleFor(o)).setMessage(value).setPositiveButton("Закрыть",null).show());
            } else {
                String value=raw.containsKey(o.key)?raw.get(o.key):String.valueOf(o.value);
                View field=createControl(o,value); controls.put(o.key,field); body.addView(field,lp(-1,52));
            }
            gap(body,12);
        }
        if(count==0) body.addView(text("Нет параметров для выбранного профиля.",14,MUTED,false));
    }
    private void renderLanding() {
        String[] descriptions={"Высота слоя, ширина линий и шов", "Стенки, оболочка и заполнение", "Первый слой, печать и перемещения", "Поддержки, интерфейс и подложка", "Переходы, башня очистки и материалы", "Юбка, кайма, поверхность и другие параметры"};
        String[] glyphs={"◇","▧","◴","⌁","◫","⋯"};
        for(int i=0;i<PAGES.length;i++) {
            String page=PAGES[i];
            body.addView(ui.row(pageName(page),descriptions[i],glyphs[i],()->{selected=page;renderPage();renderActions();}));
            gap(body,10);
        }
    }
    private String titleFor(ProcessOption o) { return tr(o.label.isEmpty()?o.key:o.label); }
    private View createControl(ProcessOption o,String value) {
        if(o.type.equals("boolean")) { Switch s=new Switch(this); s.setText("Включено"); s.setTextColor(INK); s.setChecked(Boolean.parseBoolean(value)); s.setContentDescription(titleFor(o)); return s; }
        if(o.type.equals("enum")) { Spinner s=new Spinner(this); ArrayList<String> labels=new ArrayList<>(); int index=-1; for(int i=0;i<o.choices.size();i++) { ProcessOption.Choice c=o.choices.get(i); labels.add(tr(c.label)); if(c.value.equals(value)) index=i; } if(index<0) {index=labels.size();labels.add("Недопустимое значение");}
            ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels) {
                @Override public View getView(int position,View convert,android.view.ViewGroup parent) { TextView v=(TextView)super.getView(position,convert,parent); v.setTextColor(INK); return v; }
                @Override public View getDropDownView(int position,View convert,android.view.ViewGroup parent) { TextView v=(TextView)super.getDropDownView(position,convert,parent); v.setTextColor(INK); v.setBackgroundColor(SURFACE); return v; }
            }; s.setAdapter(adapter); s.setPopupBackgroundDrawable(shape(SURFACE,12)); s.setSelection(index); s.setContentDescription(titleFor(o)); return s; }
        EditText e=new EditText(this); e.setSingleLine(true); e.setTextSize(16); e.setTextColor(INK); e.setText(value); e.setContentDescription(titleFor(o));
        e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(MUTED));
        if(!o.type.equals("string")&&!o.type.equals("float_or_percent")) e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        return e;
    }
    private void capture() { if(options==null) return; for(ProcessOption o:options) { View view=controls.get(o.key); if(view==null) continue; String value; if(view instanceof Switch) value=Boolean.toString(((Switch)view).isChecked()); else if(view instanceof Spinner) { int i=((Spinner)view).getSelectedItemPosition(); value=i>=0&&i<o.choices.size()?o.choices.get(i).value:""; } else value=((EditText)view).getText().toString(); raw.put(o.key,value); } }
    private void renderActions() {
        actions.removeAllViews(); actions.setVisibility(selected.isEmpty()?View.GONE:View.VISIBLE);
        if(selected.isEmpty()) return;
        TextView reset=button("Сбросить всё",false), save=button("Сохранить",true);
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(48),1); a.setMargins(0,0,dp(8),0); actions.addView(reset,a); actions.addView(save,new LinearLayout.LayoutParams(0,dp(48),1));
        reset.setOnClickListener(v->{ if(saving)return; raw.clear(); renderPage(); });
        save.setOnClickListener(v->{ if(saving)return; try { capture(); Map<String,Object> changes=new LinkedHashMap<>(); for(ProcessOption o:options) if(o.editable&&raw.containsKey(o.key)) { String input=raw.get(o.key); if(input.equals(String.valueOf(o.value))) continue; Object value=o.parseInput(input); if(!same(o,value,o.value)) changes.put(o.key,value); } validateAndSave(ProcessOption.encodeOverrides(changes)); } catch(Exception e){showError(e);} });
    }
    private static boolean same(ProcessOption option,Object a,Object b) {
        if(a instanceof Number&&b instanceof Number) return Double.compare(((Number)a).doubleValue(),((Number)b).doubleValue())==0;
        if(option.type.equals("float_or_percent")) {
            String x=String.valueOf(a), y=String.valueOf(b); boolean xp=x.endsWith("%"), yp=y.endsWith("%");
            if(xp!=yp) return false;
            try { return Double.compare(Double.parseDouble(xp?x.substring(0,x.length()-1):x),Double.parseDouble(yp?y.substring(0,y.length()-1):y))==0; } catch(NumberFormatException ignored) { return false; }
        }
        return a.equals(b);
    }
    private void validateAndSave(String json) {
        saving=true; actions.setAlpha(.5f); status.setText("Проверяем параметры и совместимость Orca…");
        ProcessOverridesStore.Identity id=identity; CompatibilityResources r=resources; android.content.Context app=getApplicationContext();
        new Thread(()->{ try {
            ExperimentalSlicer.validateProcessSettings(id.printer.vendor,id.process.vendor,id.filament.vendor,id.printer.name,id.process.name,id.filament.name,r.resources.getAbsolutePath(),r.data.getAbsolutePath(),json);
            ProcessOverridesStore.save(app,id,json);
            runOnUiThread(()->{if(!isDestroyed()&&!isFinishing()){setResult(RESULT_OK);finish();}});
        } catch(Throwable e) {runOnUiThread(()->{if(!isDestroyed()&&!isFinishing()){saving=false;actions.setAlpha(1f);status.setText("Параметры не сохранены");showError(e);}});} },"orca-validate-process").start();
    }
    @Override protected void onSaveInstanceState(Bundle out) { capture(); out.putString("page",selected); out.putStringArrayList("draft_keys",new ArrayList<>(raw.keySet())); out.putStringArrayList("draft_values",new ArrayList<>(raw.values())); super.onSaveInstanceState(out); }
    private void back() { if(saving)return; if(!selected.isEmpty()&&options!=null) {capture();selected="";renderPage();renderActions();} else finish(); }
    @android.annotation.TargetApi(33) private static final class Api33 {static Object register(ProcessSettingsActivity a){android.window.OnBackInvokedCallback cb=a::back;a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,cb);return cb;}static void unregister(ProcessSettingsActivity a,Object cb){a.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback)cb);}}
    @Override protected void onDestroy(){if(android.os.Build.VERSION.SDK_INT>=33&&backCallback!=null)Api33.unregister(this,backCallback);super.onDestroy();}
    @android.annotation.SuppressLint("GestureBackNavigation") @Override public void onBackPressed() { back(); }
    private static String message(Throwable e) { return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(); }
    private void showError(Throwable e) { MdUi.alert(this).setTitle("Проверьте параметры").setMessage(message(e)).setPositiveButton("Понятно",null).show(); }
}
