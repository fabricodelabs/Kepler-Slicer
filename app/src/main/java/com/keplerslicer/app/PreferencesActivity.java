package com.keplerslicer.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;
import com.keplerslicer.app.glass.GlassBackground;
import com.keplerslicer.app.glass.GlassBottomSheet;
import com.keplerslicer.app.glass.GlassGalleryActivity;
import com.keplerslicer.app.glass.GlassIcon;
import com.keplerslicer.app.glass.GlassNavigation;
import com.keplerslicer.app.glass.GlassSurface;
import com.keplerslicer.app.glass.GlassSwitch;
import com.keplerslicer.app.glass.GlassUi;
import java.util.Locale;

/** Actual Android preferences. Unsupported desktop options are clearly identified. */
public final class PreferencesActivity extends Activity {
    private SharedPreferences prefs;
    private FrameLayout root;
    private GlassUi ui;
    private LinearLayout content;
    private ScrollView bodyScroll;
    private int renderedSection=-1,restoreScroll;
    private int section;
    private boolean firstResume=true;
    private Object backCallback;
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean busy,dead;
    private static final int FOLDER=710,STL=711,THREE_MF=712,OBJ=713;
    private static final int[] SECTIONS={R.string.phase2_preferences_general,R.string.phase2_preferences_control,R.string.phase2_preferences_online,R.string.phase2_preferences_associate,R.string.phase2_preferences_developer};
    @Override public void onCreate(Bundle state){super.onCreate(state);prefs=getSharedPreferences("app_preferences",MODE_PRIVATE);if(state!=null){section=Math.max(0,Math.min(4,state.getInt("section",0)));restoreScroll=Math.max(0,state.getInt("scroll",0));}if(android.os.Build.VERSION.SDK_INT>=33)backCallback=Api33.register(this);render();}
    @Override protected void onDestroy(){dead=true;worker.shutdownNow();if(android.os.Build.VERSION.SDK_INT>=33&&backCallback!=null)Api33.unregister(this,backCallback);super.onDestroy();}
    @android.annotation.TargetApi(33) private static final class Api33 {
        static Object register(PreferencesActivity a){android.window.OnBackInvokedCallback callback=a::finish;a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback);return callback;}
        static void unregister(PreferencesActivity a,Object callback){a.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback)callback);}
    }
    @Override protected void onResume(){super.onResume();if(firstResume)firstResume=false;else render();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("section",section);state.putInt("scroll",bodyScroll==null?0:bodyScroll.getScrollY());super.onSaveInstanceState(state);}
    private String s(int id){return getString(id);}
    private void render(){
        final int scrollY=renderedSection==section&&bodyScroll!=null?bodyScroll.getScrollY():restoreScroll;restoreScroll=0;renderedSection=section;
        root=new FrameLayout(this);root.setBackground(com.keplerslicer.app.glass.SettingsHubUi.backdrop(this));setContentView(root);
        getWindow().setStatusBarColor(Color.rgb(74,79,89));getWindow().setNavigationBarColor(Color.rgb(74,79,89));getWindow().getDecorView().setSystemUiVisibility(0);
        if(android.os.Build.VERSION.SDK_INT>=35)root.setOnApplyWindowInsetsListener((v,in)->{android.graphics.Insets safe=in.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());v.setPadding(safe.left,safe.top,safe.right,safe.bottom);return in;});
        ui=new GlassUi(this,root,false);LinearLayout page=ui.vertical();ui.pad(page,20,8,20,0);LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);root.addView(column,new FrameLayout.LayoutParams(-1,-1));column.addView(page,new LinearLayout.LayoutParams(-1,0,1));
        com.keplerslicer.app.glass.KeplerUi kv=new com.keplerslicer.app.glass.KeplerUi(this);page.addView(kv.titleBar(s(R.string.phase2_preferences_title),kv.glassButton(R.drawable.kv3_search,44,22,getString(R.string.kv3_search),()->message(getString(R.string.kv3_search),getString(R.string.kv3_unavailable_detail)))),new LinearLayout.LayoutParams(-1,-2));FrameLayout bar=new FrameLayout(this);
        TextView title=ui.heading(s(R.string.phase2_preferences_title));title.setGravity(Gravity.CENTER);bar.addView(title,new FrameLayout.LayoutParams(-2,-1,Gravity.CENTER));title.setVisibility(View.GONE);
        ImageView back=new ImageView(this);back.setImageDrawable(GlassIcon.create(this,"back",ui.tokens.textPrimary));back.setBackground(GlassSurface.of(this,ui.tokens.pillRadiusMax,false));back.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));back.setContentDescription(s(R.string.phase2_preferences_back));back.setFocusable(true);back.setOnClickListener(v->finish());back.setVisibility(View.GONE);bar.addView(back,new FrameLayout.LayoutParams(ui.dp(44),ui.dp(44),Gravity.START|Gravity.CENTER_VERTICAL));
        ui.space(page,12);HorizontalScrollView strip=new HorizontalScrollView(this);strip.setHorizontalScrollBarEnabled(false);LinearLayout chips=ui.horizontal();for(int i=0;i<SECTIONS.length;i++){final int target=i;View chip=ui.chip(s(SECTIONS[i]),section==i,()->{section=target;render();});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);if(i>0)cp.leftMargin=ui.dp(6);chips.addView(chip,cp);}strip.addView(chips);page.addView(strip);TextView sectionTitle=kv.text(s(SECTIONS[section]),22,800,com.keplerslicer.app.glass.KeplerUi.WHITE);sectionTitle.setPadding(ui.dp(4),ui.dp(14),ui.dp(4),ui.dp(2));if(android.os.Build.VERSION.SDK_INT>=28)sectionTitle.setAccessibilityHeading(true);page.addView(sectionTitle);final int active=section;strip.post(()->{View selected=chips.getChildAt(active);if(selected!=null)strip.smoothScrollTo(Math.max(0,selected.getRight()-strip.getWidth()),0);});
        ScrollView scroll=new ScrollView(this);bodyScroll=scroll;scroll.setVerticalScrollBarEnabled(false);content=ui.vertical();ui.pad(content,0,0,0,16);scroll.addView(content);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));scroll.post(()->scroll.scrollTo(0,scrollY));
        {java.util.List<View> head=new java.util.ArrayList<>();for(int i=0;i<page.getChildCount();i++){View c=page.getChildAt(i);if(c!=scroll)head.add(c);}for(int i=0;i<head.size();i++){page.removeView(head.get(i));content.addView(head.get(i),i);}} // header scrolls with the content (owner request)
        switch(section){case 0:general();break;case 1:control();break;case 2:online();break;case 3:associate();break;default:developer();break;}
        GlassNavigation nav=new GlassNavigation(this,3,false,index->{if(index!=3)startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("kepler_nav",index));});
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);
        int navInset=ui.dp(ui.tokens.navInset);
        np.setMargins(navInset,ui.dp(8),navInset,ui.dp(ui.tokens.navBottom));
        column.addView(nav,np);com.keplerslicer.app.glass.KeplerUi.floatNavigation(root,column,nav,navInset,ui.dp(ui.tokens.navBottom));
    }
    private void heading(int title){TextView v=ui.section(s(title));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(ui.dp(4),ui.dp(12),ui.dp(4),ui.dp(6));content.addView(v,p);}
    private LinearLayout card(){return ui.vertical();}
    private void add(LinearLayout card,View row){if(card.getChildCount()>0){FrameLayout previous=(FrameLayout)card.getChildAt(card.getChildCount()-1);View line=new View(this);line.setBackgroundColor(ui.tokens.rowDivider);line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);previous.addView(line,new FrameLayout.LayoutParams(-1,Math.max(1,ui.dp(1)),Gravity.BOTTOM));}FrameLayout frame=new FrameLayout(this);frame.addView(row,new FrameLayout.LayoutParams(-1,-2));card.addView(frame,new LinearLayout.LayoutParams(-1,-2));}
    private void show(LinearLayout card){content.addView(ui.surface(card,ui.tokens.cardRadius),new LinearLayout.LayoutParams(-1,-2));}
    private View dependency(int title,int reason){return ui.row(s(title),ui.value(s(R.string.pref_requires_support)),()->message(s(title),s(reason)));}
    private View bambu(int title){return ui.row(s(title),ui.value(s(R.string.pref_bambu_missing)),()->message(s(title),s(R.string.pref_bambu_detail)));}
    private void message(String title,String text){GlassBottomSheet.info(this,root,false,title,text);}
    private View action(int title,Runnable action){return ui.row(s(title),ui.value("›"),action);}
    private View seconds(int title,int value,Runnable action){return ui.row(s(title),ui.value(getString(R.string.pref_seconds_value,value)+"  ›"),action);}
    private View info(int title,String value){return ui.row(s(title),ui.value(value),null);}
    private View bool(int title,String key,boolean fallback){return bool(title,key,fallback,()->{});}
    private View bool(int title,String key,boolean fallback,Runnable changed){GlassSwitch control=new GlassSwitch(this);control.setContentDescription(s(title));control.setChecked(prefs.getBoolean(key,fallback));control.setOnCheckedChangeListener((button,checked)->{prefs.edit().putBoolean(key,checked).apply();changed.run();});return ui.row(s(title),control,null);}
    private View choice(int title,String display,String[] choices,int selected,GlassBottomSheet.Selection callback){return ui.row(s(title),ui.select(display),()->GlassBottomSheet.choose(this,root,false,s(title),choices,selected,index->{callback.chosen(index);render();}));}
    private View floatChoice(int title,String key,float fallback,float[] numbers,String[] choices){float value=prefs.getFloat(key,fallback);int chosen=0;float distance=Float.MAX_VALUE;for(int i=0;i<numbers.length;i++){float d=Math.abs(numbers[i]-value);if(d<distance){chosen=i;distance=d;}}String current=String.format(Locale.ROOT,"%.2f×",value);return choice(title,current,choices,chosen,index->prefs.edit().putFloat(key,numbers[index]).apply());}
    private View stringChoice(int title,String key,String fallback,String[] keys,String[] labels){String value=prefs.getString(key,fallback);int selected=0;for(int i=0;i<keys.length;i++)if(keys[i].equals(value))selected=i;return choice(title,labels[selected],labels,selected,i->prefs.edit().putString(key,keys[i]).apply());}
    private String[] strings(int... ids){String[] a=new String[ids.length];for(int i=0;i<ids.length;i++)a[i]=s(ids[i]);return a;}
    private void general(){
        heading(R.string.phase2_preferences_settings);LinearLayout a=card();String[] langs={s(R.string.pref_system_language),"English","Русский"};String lang=GeneralPreferences.language(this);int selected="en".equals(lang)?1:"ru".equals(lang)?2:0;add(a,choice(R.string.phase2_preferences_language,langs[selected],langs,selected,i->GeneralPreferences.setLanguage(this,new String[]{"","en","ru"}[i])));add(a,info(R.string.phase2_preferences_units,"mm"));
        add(a,stringChoice(R.string.phase2_preferences_home,"default_page","prepare",new String[]{"prepare","home"},strings(R.string.pref_workspace,R.string.pref_home)));
        boolean dark=!"light".equals(prefs.getString("theme","dark"));add(a,choice(R.string.phase2_preferences_theme,s(dark?R.string.phase2_preferences_dark:R.string.phase2_preferences_light),strings(R.string.phase2_preferences_dark,R.string.phase2_preferences_light),dark?0:1,i->prefs.edit().putString("theme",i==0?"dark":"light").apply()));
        add(a,ui.row(s(R.string.phase2_preferences_single),ui.value(s(R.string.pref_android_managed)),()->message(s(R.string.phase2_preferences_single),s(R.string.pref_single_detail))));add(a,bool(R.string.phase2_preferences_splash,"show_splash_screen",true));add(a,ui.row(s(R.string.phase2_preferences_downloads),ui.value(GeneralPreferences.downloadFolderLabel(this)+"  ›"),()->GeneralPreferences.chooseDownloadFolder(this,FOLDER)));show(a);
        heading(R.string.phase2_preferences_project);LinearLayout p=card();add(p,stringChoice(R.string.phase2_preferences_load,"project_load_behaviour","ask_when_relevant",new String[]{"load_all","ask_when_relevant","always_ask","load_geometry"},strings(R.string.pref_load_all,R.string.pref_load_relevant,R.string.pref_load_ask,R.string.pref_load_geometry)));
        int[] limits={6,12,18,30};String[] labels={"6","12","18","30"};int count=prefs.getInt("recent_count",12),which=1;for(int i=0;i<limits.length;i++)if(limits[i]==count)which=i;add(p,choice(R.string.phase2_preferences_recent,Integer.toString(count),labels,which,i->prefs.edit().putInt("recent_count",limits[i]).apply()));add(p,bool(R.string.phase2_preferences_recent_add,"recent_models",true));add(p,bool(R.string.phase2_preferences_gcode_warning,"no_warn_when_modified_gcodes",false));add(p,bool(R.string.phase2_preferences_step,"show_step_import_settings",true));add(p,bool(R.string.phase2_preferences_backup,"backup_switch",false,()->GeneralPreferences.refreshBackupSchedule(this)));add(p,seconds(R.string.pref_backup_interval,GeneralPreferences.backupInterval(this),()->number(R.string.pref_backup_interval,GeneralPreferences.backupInterval(this),5,86400,value->{prefs.edit().putInt("backup_interval",value).apply();GeneralPreferences.refreshBackupSchedule(this);})));add(p,action(R.string.pref_backup_actions,this::backupActions));show(p);note(s(R.string.pref_load_detail));
        heading(R.string.phase2_preferences_preset);LinearLayout t=card();add(t,info(R.string.phase2_preferences_remember_printer,"✓"));add(t,stringChoice(R.string.phase2_preferences_group_presets,"group_filament_presets","all",new String[]{"all","none","type","vendor"},strings(R.string.pref_group_all,R.string.pref_group_none,R.string.pref_group_type,R.string.pref_group_vendor)));show(t);
    }
    private void control(){
        heading(R.string.phase2_preferences_behaviour);LinearLayout b=card();add(b,bool(R.string.phase2_preferences_flush,"auto_calculate_flushing_volumes",true));add(b,bool(R.string.phase2_preferences_arrange,"arrange_on_clone",true));show(b);
        heading(R.string.phase2_preferences_slicing);LinearLayout c=card();add(c,bool(R.string.phase2_preferences_auto_slice,ControlPreferences.AUTO_SLICE,false,()->ControlPreferences.scheduleAutoSlice(this)));add(c,seconds(R.string.pref_auto_slice_delay,ControlPreferences.delaySeconds(this),()->number(R.string.pref_auto_slice_delay,prefs.getInt(ControlPreferences.AUTO_SLICE_DELAY,0),0,60,value->{prefs.edit().putInt(ControlPreferences.AUTO_SLICE_DELAY,value).apply();ControlPreferences.scheduleAutoSlice(this);})));add(c,mixedTemperatures());show(c);
        heading(R.string.phase2_preferences_camera);LinearLayout v=card();add(v,stringChoice(R.string.phase2_preferences_camera_style,ControlPreferences.CAMERA_STYLE,"default",new String[]{"default","touchpad"},strings(R.string.pref_camera_default,R.string.pref_camera_touchpad)));add(v,floatChoice(R.string.phase2_preferences_orbit,"orbit_speed",.75f,new float[]{.25f,.5f,.75f,1f,1.5f,2f},new String[]{"0.25×","0.5×","0.75×","1×","1.5×","2×"}));add(v,floatChoice(R.string.phase2_preferences_move,"move_speed",.6f,new float[]{.25f,.5f,.6f,.75f,1f},new String[]{"0.25×","0.5×","0.6×","0.75×","1×"}));add(v,bool(R.string.phase2_preferences_zoom_center,ControlPreferences.ZOOM_CENTER,false));add(v,bool(R.string.phase2_preferences_free_camera,ControlPreferences.FREE_CAMERA,false));add(v,bool(R.string.phase2_preferences_swap,ControlPreferences.SWAP_GESTURES,false));add(v,bool(R.string.phase2_preferences_reverse_zoom,"reverse_zoom",false));show(v);
        heading(R.string.phase2_preferences_clear_choice);LinearLayout clear=card();add(clear,action(R.string.phase2_preferences_clear_projects,()->clearChoice(ControlPreferences.UNSAVED_PROJECT)));add(clear,action(R.string.phase2_preferences_clear_presets,()->clearChoice(ControlPreferences.UNSAVED_PRESET)));add(clear,action(R.string.phase2_preferences_clear_sync,()->clearChoice(ControlPreferences.SYNC_CHOICE)));show(clear);note(s(R.string.pref_clear_detail));
    }
    private void online(){
        heading(R.string.phase2_preferences_connection);LinearLayout c=card();add(c,bool(R.string.phase2_preferences_offline,"network_offline",false,()->OnlinePreferences.changed(this)));add(c,action(R.string.phase2_preferences_network,()->startActivity(new Intent(this,NetworkActivity.class))));add(c,action(R.string.phase2_preferences_cloud,()->startActivity(new Intent(this,CloudSyncActivity.class))));add(c,bambu(R.string.phase2_preferences_region));add(c,bool(R.string.phase2_preferences_stealth,"stealth_mode",false,()->OnlinePreferences.changed(this)));add(c,action(R.string.phase2_preferences_test,()->work(R.string.phase2_preferences_test,()->OnlinePreferences.connectionTest(this))));show(c);note(s(R.string.pref_stealth_detail));
        heading(R.string.phase2_preferences_sync);LinearLayout p=card();add(p,bool(R.string.phase2_preferences_stable,"check_stable_update_only",true));add(p,action(R.string.pref_check_updates,()->work(R.string.pref_check_updates,()->OnlinePreferences.checkUpdates(this))));add(p,ui.row(s(R.string.phase2_preferences_auto_sync),ui.value(s(prefs.getBoolean("sync_user_preset",false)?R.string.pref_on:R.string.pref_off)),this::autoSync));add(p,action(R.string.pref_sync_now,()->work(R.string.pref_sync_now,()->OnlinePreferences.syncNow(this))));add(p,dependency(R.string.phase2_preferences_update_profiles,R.string.pref_system_profiles_detail));add(p,info(R.string.phase2_preferences_encryption,s(R.string.phase2_preferences_encryption_on)));add(p,bambu(R.string.phase2_preferences_filament_sync));show(p);
        heading(R.string.phase2_preferences_plugin);LinearLayout n=card();add(n,bambu(R.string.phase2_preferences_plugin_enable));add(n,bambu(R.string.phase2_preferences_plugin_version));show(n);
    }
    private void associate(){heading(R.string.phase2_preferences_associate);note(s(R.string.pref_associate_detail));LinearLayout c=card();add(c,action(R.string.phase2_preferences_associate_stl,()->startActivityForResult(GeneralPreferences.associationPickerIntent("stl"),STL)));add(c,action(R.string.phase2_preferences_associate_3mf,()->startActivityForResult(GeneralPreferences.associationPickerIntent("3mf"),THREE_MF)));add(c,action(R.string.phase2_preferences_associate_obj,()->startActivityForResult(GeneralPreferences.associationPickerIntent("obj"),OBJ)));show(c);}
    private void developer(){heading(R.string.phase2_preferences_settings);LinearLayout c=card();add(c,bool(R.string.phase2_preferences_developer_mode,"developer_mode",false,this::render));add(c,bambu(R.string.phase2_preferences_ams));show(c);heading(R.string.phase2_preferences_storage);LinearLayout storage=card();add(storage,bambu(R.string.phase2_preferences_storage_abnormal));String[] levels=OnlinePreferences.LOG_LEVELS;int level=OnlinePreferences.logLevel(this);add(storage,choice(R.string.phase2_preferences_log,levels[level],levels,level,i->changeNativeLogLevel(levels[i])));String actual=prefs.getString("native_log_severity_actual","");if(actual!=null&&!actual.isEmpty())add(storage,info(R.string.native_preferences_actual_log,actual));show(storage);heading(R.string.phase2_preferences_plugin);LinearLayout p=card();add(p,bambu(R.string.phase2_preferences_reload));show(p);if(prefs.getBoolean("developer_mode",false)){heading(R.string.phase2_preferences_developer);LinearLayout d=card();add(d,action(R.string.phase2_preferences_gallery,()->startActivity(new Intent(this,GlassGalleryActivity.class))));add(d,action(R.string.pref_diagnostics,()->work(R.string.pref_diagnostics,()->OnlinePreferences.diagnostics(this))));add(d,action(R.string.native_preferences_test,()->work(R.string.native_preferences_test,()->NativePreferenceActions.runSelfTest(this))));add(d,info(R.string.phase2_preferences_engine,PrinterProfileCatalog.UPSTREAM_REVISION));add(d,info(R.string.phase2_preferences_platform,"Android "+android.os.Build.VERSION.RELEASE+" / API "+android.os.Build.VERSION.SDK_INT));show(d);}note(s(R.string.native_preferences_log_detail));}
    private View mixedTemperatures(){
        GlassSwitch control=new GlassSwitch(this);control.setContentDescription(s(R.string.phase2_preferences_temperature));control.setChecked(prefs.getBoolean("allow_mixed_temperatures",false));
        control.setOnCheckedChangeListener((button,checked)->{
            if(!checked){prefs.edit().putBoolean("allow_mixed_temperatures",false).apply();ControlPreferences.scheduleAutoSlice(this);return;}
            // Rebuild from the stored false value before opening the warning,
            // so dismissing the sheet never leaves an enabled-looking switch.
            render();Dialog dialog=new Dialog(this);dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            LinearLayout body=ui.vertical();ui.pad(body,20,20,20,20);body.addView(ui.heading(s(R.string.phase2_preferences_temperature)));ui.space(body,10);
            body.addView(ui.text(s(R.string.native_preferences_mixed_warning),14,400,ui.tokens.textPrimary));ui.space(body,14);
            body.addView(ui.action(s(R.string.native_preferences_enable_mixed),true,()->{prefs.edit().putBoolean("allow_mixed_temperatures",true).apply();dialog.dismiss();ControlPreferences.scheduleAutoSlice(this);render();}));ui.space(body,6);body.addView(ui.action(s(R.string.pref_cancel),false,dialog::dismiss));
            dialog.setContentView(ui.surface(body,ui.tokens.cardRadius));dialog.show();if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));dialog.getWindow().setGravity(Gravity.BOTTOM);dialog.getWindow().setLayout(-1,-2);}
        });return ui.row(s(R.string.phase2_preferences_temperature),control,null);
    }
    private void changeNativeLogLevel(String requested){work(R.string.phase2_preferences_log,()->{
        String previous=prefs.getString("log_severity_level","info"),previousActual=prefs.getString("native_log_severity_actual","");
        String actual=NativePreferenceActions.setLogLevel(requested);
        if(!prefs.edit().putString("log_severity_level",requested).putString("native_log_severity_actual",actual).commit()){
            prefs.edit().putString("log_severity_level",previous).putString("native_log_severity_actual",previousActual).apply();
            try{NativePreferenceActions.setLogLevel(previous);}catch(java.io.IOException ignored){}
            throw new java.io.IOException(s(R.string.native_preferences_log_save_failed));
        }
        return getString(R.string.native_preferences_log_applied,requested,actual);
    });}
    private void note(String text){ui.space(content,8);content.addView(ui.text(text,12,400,ui.tokens.textSecondary));ui.space(content,8);}
    private void clearChoice(String key){ControlPreferences.clearRememberedChoice(this,key);Toast.makeText(this,R.string.pref_choice_cleared,Toast.LENGTH_SHORT).show();}
    private interface Operation {String run()throws Exception;}
    private void work(int title,Operation operation){if(busy){Toast.makeText(this,R.string.pref_working,Toast.LENGTH_SHORT).show();return;}busy=true;Toast.makeText(this,R.string.pref_working,Toast.LENGTH_SHORT).show();worker.execute(()->{try{String result=operation.run();runOnUiThread(()->{busy=false;if(!dead){render();message(s(title),result);}});}catch(Exception|LinkageError e){runOnUiThread(()->{busy=false;if(!dead)message(s(title),e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());});}});}
    private interface IntegerResult {void accept(int value);}
    private void number(int title,int current,int min,int max,IntegerResult result){Dialog d=new Dialog(this);d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);LinearLayout body=ui.vertical();ui.pad(body,20,20,20,20);body.addView(ui.heading(s(title)));EditText input=ui.edit(Integer.toString(current),android.text.InputType.TYPE_CLASS_NUMBER,false);input.setContentDescription(s(title));body.addView(input);body.addView(ui.text(getString(R.string.pref_seconds_range,min,max),12,400,ui.tokens.textSecondary));ui.space(body,12);body.addView(ui.action(s(R.string.pref_save),true,()->{try{int value=Integer.parseInt(input.getText().toString().trim());if(value<min||value>max)throw new NumberFormatException();result.accept(value);d.dismiss();render();}catch(NumberFormatException invalid){input.setError(getString(R.string.pref_seconds_range,min,max));}}));ui.space(body,6);body.addView(ui.action(s(R.string.pref_cancel),false,d::dismiss));d.setContentView(ui.surface(body,ui.tokens.cardRadius));d.show();if(d.getWindow()!=null){d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));d.getWindow().setGravity(Gravity.BOTTOM);d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);d.getWindow().setLayout(-1,-2);}}
    private void backupActions(){GlassBottomSheet.choose(this,root,false,GeneralPreferences.backupStatus(this),strings(R.string.pref_backup_now,R.string.pref_restore_backup),-1,i->{if(i==0)work(R.string.pref_backup_now,()->{GeneralPreferences.backupNow(this);return GeneralPreferences.backupStatus(this);});else GlassBottomSheet.choose(this,root,false,s(R.string.pref_restore_question),strings(R.string.pref_cancel,R.string.pref_restore_backup),0,j->{if(j==1)work(R.string.pref_restore_backup,()->{GeneralPreferences.restoreBackup(this);return s(R.string.pref_restored);});});});}
    private void autoSync(){boolean enabled=prefs.getBoolean("sync_user_preset",false);GlassBottomSheet.choose(this,root,false,s(R.string.pref_sync_consent),strings(R.string.pref_off,R.string.pref_sync_enable),enabled?1:0,i->{prefs.edit().putBoolean("sync_user_preset",i==1).apply();OnlinePreferences.changed(this);render();});}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;try{if(request==FOLDER){GeneralPreferences.acceptDownloadFolder(this,data);render();}else if(request>=STL&&request<=OBJ)GeneralPreferences.openAssociatedUri(this,data,request==STL?"stl":request==THREE_MF?"3mf":"obj");}catch(Exception e){message(s(R.string.phase2_preferences_title),e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}}
}
