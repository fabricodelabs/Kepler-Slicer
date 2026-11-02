package com.orcamobile.app;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import com.keplerslicer.engine.ExperimentalSlicer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/** Embeddable process editor. All engine calls and profile I/O run on workers. */
final class ProcessSettingsPanel {
    private static final Object SAVE_LOCK=new Object();
    private static java.util.concurrent.CountDownLatch pendingSave;
    private static final String[] CATEGORIES={"Quality","Strength","Speed","Support","Multimaterial","Others"};
    private static final String[] NAMES={"Качество","Прочность","Скорость","Поддержки","Мультиматериал","Прочее"};
    private final Activity activity;
    private final Runnable onSaved;
    private final MdUi ui;
    private final LinearLayout root, tabs, body, actions;
    private final TextView status;
    private final Map<String,String> raw=new LinkedHashMap<>();
    private final Map<String,View> controls=new LinkedHashMap<>();
    private ProcessOverridesStore.Identity identity;
    private CompatibilityResources resources;
    private List<ProcessOption> options;
    private JSONObject translations=new JSONObject();
    private Bundle pendingDraft;
    private String selected="Quality";
    private int generation;
    private boolean disposed, saving, rebuilding;
    private String statusDetail;

    ProcessSettingsPanel(Activity activity, Runnable onSaved) {
        this.activity=activity;this.onSaved=onSaved;ui=new MdUi(activity);
        root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(ui.bg);
        status=text("Загружаем настройки профиля Orca…",12,ui.muted,false);status.setPadding(dp(16),dp(6),dp(16),dp(6));status.setMaxLines(2);status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setOnClickListener(v->{if(statusDetail!=null)MdUi.alert(activity).setTitle("Проверьте параметры").setMessage(statusDetail).setPositiveButton("Закрыть",null).show();});root.addView(status);
        HorizontalScrollView strip=new HorizontalScrollView(activity);strip.setHorizontalScrollBarEnabled(false);
        tabs=new LinearLayout(activity);tabs.setOrientation(LinearLayout.HORIZONTAL);tabs.setPadding(dp(12),0,dp(12),dp(4));strip.addView(tabs);root.addView(strip);
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(8),dp(16),dp(8));scroll.addView(body);
        actions=new LinearLayout(activity);actions.setPadding(dp(16),dp(6),dp(16),dp(8));root.addView(actions);
        reload();
    }
    View view(){return root;}
    /** Call after any printer/process/filament change. Pending drafts apply only to the same fingerprint. */
    void reload() {
        if(disposed)return;
        if(options!=null && identity!=null) pendingDraft=saveDraft();
        final int token=++generation;saving=false;statusDetail=null;options=null;identity=null;resources=null;controls.clear();raw.clear();
        tabs.removeAllViews();body.removeAllViews();actions.removeAllViews();status.setText("Загружаем настройки профиля Orca…");
        final android.content.Context app=activity.getApplicationContext();
        new Thread(()->{try {
            java.util.concurrent.CountDownLatch wait; synchronized(SAVE_LOCK){wait=pendingSave;} if(wait!=null)wait.await();
            ProcessOverridesStore.Snapshot snap=ProcessOverridesStore.snapshotForSlice(app);ProcessOverridesStore.Identity id=snap.identity;
            CompatibilityResources r=CompatibilityResources.prepareTriple(app,id.printer.vendor,id.process.vendor,id.filament.vendor);
            byte[] bytes=ExperimentalSlicer.processSettingsMetadata(id.printer.vendor,id.process.vendor,id.filament.vendor,id.printer.name,id.process.name,id.filament.name,r.resources.getAbsolutePath(),r.data.getAbsolutePath());
            List<ProcessOption> parsed=ProcessOption.parseMetadata(new String(bytes,StandardCharsets.UTF_8));JSONObject ru=translations(app);JSONObject stored=new JSONObject(snap.overrides);
            activity.runOnUiThread(()->{if(stale(token))return;try {
                identity=id;resources=r;options=parsed;translations=ru;raw.clear();
                for(java.util.Iterator<String> it=stored.keys();it.hasNext();){String k=it.next();raw.put(k,String.valueOf(stored.get(k)));}
                applyPending();render();
            }catch(Exception e){error(e);}});
        }catch(Throwable e){activity.runOnUiThread(()->{if(!stale(token))error(e);});}},"orca-panel-metadata").start();
    }
    Bundle saveDraft() {
        if(identity==null) return pendingDraft==null?new Bundle():new Bundle(pendingDraft);
        capture();Bundle b=new Bundle();b.putString("fingerprint",identity.fingerprint);b.putString("category",selected);
        b.putStringArrayList("keys",new ArrayList<>(raw.keySet()));b.putStringArrayList("values",new ArrayList<>(raw.values()));return b;
    }
    void restoreDraft(Bundle draft) {if(disposed||draft==null)return;pendingDraft=new Bundle(draft);if(options!=null){capture();applyPending();render();}}
    void dispose(){disposed=true;generation++;controls.clear();}
    private boolean stale(int token){return disposed||token!=generation||activity.isFinishing()||activity.isDestroyed();}
    private void applyPending(){
        Bundle b=pendingDraft;pendingDraft=null;if(b==null||identity==null||!identity.fingerprint.equals(b.getString("fingerprint")))return;
        ArrayList<String> keys=b.getStringArrayList("keys"),values=b.getStringArrayList("values");
        if(keys!=null&&values!=null&&keys.size()==values.size())for(int i=0;i<keys.size();i++)if(keys.get(i)!=null&&values.get(i)!=null)raw.put(keys.get(i),values.get(i));
        String category=b.getString("category",selected);for(String s:CATEGORIES)if(s.equals(category)){selected=category;break;}
    }
    private void render(){if(options==null)return;rebuilding=true;renderTabs();renderBody();renderActions();rebuilding=false;updateStatus(null);}
    private void renderTabs(){tabs.removeAllViews();for(int i=0;i<CATEGORIES.length;i++){final String page=CATEGORIES[i];TextView t=text(NAMES[i],13,page.equals(selected)?ui.bg:ui.ink,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(14),dp(9),dp(14),dp(9));t.setBackground(ui.shape(page.equals(selected)?ui.ink:ui.surface,100));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(0,0,dp(6),0);tabs.addView(t,p);t.setOnClickListener(v->{if(saving)return;capture();selected=page;render();});}}
    private void renderBody(){body.removeAllViews();controls.clear();String section=null;int n=0;
        for(ProcessOption o:options){if(!selected.equals(o.category))continue;n++;
            if(!o.section.equals(section)){section=o.section;TextView h=text(tr(section),16,ui.ink,true);h.setPadding(0,dp(8),0,dp(8));body.addView(h);}
            String label=tr(o.label.isEmpty()?o.key:o.label);if(!o.unit.isEmpty()&&!label.contains(o.unit))label+=" · "+tr(o.unit);
            TextView title=text(label,14,ui.ink,true);body.addView(title);
            if(!o.tooltip.isEmpty()){String description=tr(o.tooltip);TextView hint=text(description.length()>140?description.substring(0,140)+"…":description,12,ui.muted,false);body.addView(hint);if(description.length()>140)hint.setOnClickListener(v->MdUi.alert(activity).setMessage(description).setPositiveButton("Закрыть",null).show());}
            if(o.editable){String value=raw.containsKey(o.key)?raw.get(o.key):String.valueOf(o.value);View field=control(o,value);controls.put(o.key,field);body.addView(field,new LinearLayout.LayoutParams(-1,dp(48)));}
            else {String value=String.valueOf(o.value);TextView r=text((value.length()>100?value.substring(0,100)+"…":value)+" · только просмотр",12,ui.muted,false);r.setPadding(0,dp(4),0,dp(4));body.addView(r);if(value.length()>100)r.setOnClickListener(v->MdUi.alert(activity).setTitle(tr(o.label)).setMessage(value).setPositiveButton("Закрыть",null).show());}
            View space=new View(activity);body.addView(space,new LinearLayout.LayoutParams(1,dp(10)));
        }if(n==0)body.addView(text("Нет параметров для выбранного профиля.",13,ui.muted,false));
    }
    private View control(ProcessOption o,String value){
        if(o.type.equals("boolean")){Switch s=new Switch(activity);s.setText("Включено");s.setTextColor(ui.ink);s.setChecked(Boolean.parseBoolean(value));s.setContentDescription(tr(o.label));s.setOnCheckedChangeListener((v,b)->updateStatus(null));return s;}
        if(o.type.equals("enum")){Spinner s=new Spinner(activity);ArrayList<String> labels=new ArrayList<>();int index=-1;for(int i=0;i<o.choices.size();i++){ProcessOption.Choice c=o.choices.get(i);labels.add(tr(c.label));if(c.value.equals(value))index=i;}if(index<0){index=labels.size();labels.add("Недопустимое значение");}
            ArrayAdapter<String> a=new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,labels){@Override public View getView(int p,View c,android.view.ViewGroup g){TextView v=(TextView)super.getView(p,c,g);v.setTextColor(ui.ink);return v;}@Override public View getDropDownView(int p,View c,android.view.ViewGroup g){TextView v=(TextView)super.getDropDownView(p,c,g);v.setTextColor(ui.ink);v.setBackgroundColor(ui.surface);return v;}};
            s.setAdapter(a);s.setSelection(index);s.setPopupBackgroundDrawable(ui.shape(ui.surface,12));s.setContentDescription(tr(o.label));s.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){updateStatus(null);}public void onNothingSelected(android.widget.AdapterView<?> p){updateStatus(null);}});return s;}
        EditText e=new EditText(activity);e.setSingleLine(true);e.setTextSize(15);e.setTextColor(ui.ink);e.setContentDescription(tr(o.label));e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.muted));
        if(!o.type.equals("string")&&!o.type.equals("float_or_percent"))e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setText(value);e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){updateStatus(null);}public void afterTextChanged(Editable e){}});return e;
    }
    private void capture(){if(options==null||rebuilding)return;for(ProcessOption o:options){View v=controls.get(o.key);if(v==null)continue;String value;if(v instanceof Switch)value=Boolean.toString(((Switch)v).isChecked());else if(v instanceof Spinner){int i=((Spinner)v).getSelectedItemPosition();value=i>=0&&i<o.choices.size()?o.choices.get(i).value:"";}else value=((EditText)v).getText().toString();raw.put(o.key,value);}}
    private void renderActions(){actions.removeAllViews();TextView reset=button("Сбросить",false),save=button("Сохранить",true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(0,0,dp(8),0);actions.addView(reset,p);actions.addView(save,new LinearLayout.LayoutParams(0,dp(44),1));
        reset.setOnClickListener(v->{if(!saving){raw.clear();render();}});save.setOnClickListener(v->{if(saving)return;try{capture();Map<String,Object> changes=changes();validateAndSave(ProcessOption.encodeOverrides(changes));}catch(Exception e){error(e);}});actions.setAlpha(saving?.5f:1f);}
    private Map<String,Object> changes(){Map<String,Object> changes=new LinkedHashMap<>();for(ProcessOption o:options)if(o.editable&&raw.containsKey(o.key)){String input=raw.get(o.key);if(input.equals(String.valueOf(o.value)))continue;try{Object value=o.parseInput(input);if(!same(o,value,o.value))changes.put(o.key,value);}catch(IllegalArgumentException e){throw new IllegalArgumentException(tr(o.label.isEmpty()?o.key:o.label)+": "+e.getMessage(),e);}}return changes;}
    private static boolean same(ProcessOption o,Object a,Object b){if(a instanceof Number&&b instanceof Number)return Double.compare(((Number)a).doubleValue(),((Number)b).doubleValue())==0;if(o.type.equals("float_or_percent")){String x=String.valueOf(a),y=String.valueOf(b);boolean xp=x.endsWith("%"),yp=y.endsWith("%");if(xp!=yp)return false;try{return Double.compare(Double.parseDouble(xp?x.substring(0,x.length()-1):x),Double.parseDouble(yp?y.substring(0,y.length()-1):y))==0;}catch(NumberFormatException e){return false;}}return a.equals(b);}
    private void validateAndSave(String json){
        final java.util.concurrent.CountDownLatch operation;
        synchronized(SAVE_LOCK){if(pendingSave!=null){error(new IllegalStateException("Дождитесь завершения предыдущего сохранения."));return;}operation=new java.util.concurrent.CountDownLatch(1);pendingSave=operation;}
        saving=true;actions.setAlpha(.5f);setInputsEnabled(false);statusDetail=null;status.setText("Проверяем параметры и совместимость Orca…");
        final int token=generation;ProcessOverridesStore.Identity id=identity;CompatibilityResources r=resources;android.content.Context app=activity.getApplicationContext();
        new Thread(()->{try{ExperimentalSlicer.validateProcessSettings(id.printer.vendor,id.process.vendor,id.filament.vendor,id.printer.name,id.process.name,id.filament.name,r.resources.getAbsolutePath(),r.data.getAbsolutePath(),json);ProcessOverridesStore.save(app,id,json);activity.runOnUiThread(()->{if(stale(token))return;saving=false;actions.setAlpha(1f);setInputsEnabled(true);updateStatus("Сохранено");if(onSaved!=null)onSaved.run();});}
            catch(Throwable e){activity.runOnUiThread(()->{if(stale(token))return;saving=false;actions.setAlpha(1f);setInputsEnabled(true);error(e);});}
            finally{synchronized(SAVE_LOCK){if(pendingSave==operation)pendingSave=null;}operation.countDown();}},"orca-panel-save").start();
    }
    private void setInputsEnabled(boolean enabled){for(View v:controls.values())v.setEnabled(enabled);}
    private void updateStatus(String note){if(rebuilding||options==null)return;statusDetail=null;capture();int count=0;for(ProcessOption o:options)if(o.editable&&raw.containsKey(o.key)){try{Object v=o.parseInput(raw.get(o.key));if(!same(o,v,o.value))count++;}catch(Exception e){count++;}}status.setText((note==null?"":note+" · ")+"Изменено параметров: "+count);}
    private void error(Throwable e){String m=e.getMessage();statusDetail=m==null?e.getClass().getSimpleName():m;status.setText("Ошибка: "+statusDetail);}
    private int dp(float n){return (int)(activity.getResources().getDisplayMetrics().density*n+.5f);}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(activity);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setTypeface(android.graphics.Typeface.create("sans-serif",bold?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL));return t;}
    private TextView button(String label,boolean primary){TextView t=text(label,14,primary?ui.bg:ui.ink,true);t.setGravity(Gravity.CENTER);t.setBackground(ui.shape(primary?ui.ink:ui.surface,100));return t;}
    private String tr(String s){String t=translations.optString(s,s);return t.isEmpty()?s:t;}
    private static JSONObject translations(android.content.Context c){try(InputStream in=c.getAssets().open("orca_ui_ru.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>4*1024*1024)return new JSONObject();out.write(b,0,n);}return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}catch(Exception ignored){return new JSONObject();}}
}
