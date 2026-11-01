package com.orcamobile.app;

import android.app.Activity;
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
import com.orcamobile.app.glass.GlassBackground;
import com.orcamobile.app.glass.GlassBottomSheet;
import com.orcamobile.app.glass.GlassGalleryActivity;
import com.orcamobile.app.glass.GlassIcon;
import com.orcamobile.app.glass.GlassNavigation;
import com.orcamobile.app.glass.GlassSurface;
import com.orcamobile.app.glass.GlassSwitch;
import com.orcamobile.app.glass.GlassUi;
import java.util.Locale;

/** Actual Android preferences. Unsupported desktop options are clearly identified. */
public final class PreferencesActivity extends Activity {
    private SharedPreferences prefs;
    private FrameLayout root;
    private GlassUi ui;
    private LinearLayout content;
    private int section;
    private boolean firstResume=true;
    private Object backCallback;
    private static final int[] SECTIONS={R.string.phase2_preferences_general,R.string.phase2_preferences_control,R.string.phase2_preferences_online,R.string.phase2_preferences_associate,R.string.phase2_preferences_developer};
    @Override public void onCreate(Bundle state){super.onCreate(state);prefs=getSharedPreferences("app_preferences",MODE_PRIVATE);if(state!=null)section=Math.max(0,Math.min(4,state.getInt("section",0)));if(android.os.Build.VERSION.SDK_INT>=33)backCallback=Api33.register(this);render();}
    @Override protected void onDestroy(){if(android.os.Build.VERSION.SDK_INT>=33&&backCallback!=null)Api33.unregister(this,backCallback);super.onDestroy();}
    @android.annotation.TargetApi(33) private static final class Api33 {
        static Object register(PreferencesActivity a){android.window.OnBackInvokedCallback callback=a::finish;a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback);return callback;}
        static void unregister(PreferencesActivity a,Object callback){a.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback)callback);}
    }
    @Override protected void onResume(){super.onResume();if(firstResume)firstResume=false;else render();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("section",section);super.onSaveInstanceState(state);}
    private String s(int id){return getString(id);}
    private void render(){
        root=new FrameLayout(this);root.setBackground(GlassBackground.create(this));setContentView(root);
        getWindow().setStatusBarColor(Color.rgb(74,79,89));getWindow().setNavigationBarColor(Color.rgb(74,79,89));getWindow().getDecorView().setSystemUiVisibility(0);
        if(android.os.Build.VERSION.SDK_INT>=35)root.setOnApplyWindowInsetsListener((v,in)->{android.graphics.Insets safe=in.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());v.setPadding(safe.left,safe.top,safe.right,safe.bottom);return in;});
        ui=new GlassUi(this,root,false);LinearLayout page=ui.vertical();ui.pad(page,20,28,20,0);root.addView(page,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout bar=new FrameLayout(this);page.addView(bar,new LinearLayout.LayoutParams(-1,ui.dp(44)));
        TextView title=ui.heading(s(R.string.phase2_preferences_title));title.setGravity(Gravity.CENTER);bar.addView(title,new FrameLayout.LayoutParams(-2,-1,Gravity.CENTER));
        ImageView back=new ImageView(this);back.setImageDrawable(GlassIcon.create(this,"back",ui.tokens.textPrimary));back.setBackground(GlassSurface.of(this,ui.tokens.pillRadiusMax,false));back.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));back.setContentDescription(s(R.string.phase2_preferences_back));back.setFocusable(true);back.setOnClickListener(v->finish());bar.addView(back,new FrameLayout.LayoutParams(ui.dp(44),ui.dp(44),Gravity.START|Gravity.CENTER_VERTICAL));
        ui.space(page,12);HorizontalScrollView strip=new HorizontalScrollView(this);strip.setHorizontalScrollBarEnabled(false);LinearLayout chips=ui.horizontal();for(int i=0;i<SECTIONS.length;i++){final int target=i;View chip=ui.chip(s(SECTIONS[i]),section==i,()->{section=target;render();});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);if(i>0)cp.leftMargin=ui.dp(6);chips.addView(chip,cp);}strip.addView(chips);page.addView(strip);final int active=section;strip.post(()->{View selected=chips.getChildAt(active);if(selected!=null)strip.smoothScrollTo(Math.max(0,selected.getRight()-strip.getWidth()),0);});
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);content=ui.vertical();ui.pad(content,0,0,0,110);scroll.addView(content);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        switch(section){case 0:general();break;case 1:control();break;case 2:online();break;case 3:associate();break;default:developer();break;}
        GlassNavigation nav=new GlassNavigation(this,3,false,index->{if(index!=3)startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("kepler_nav",index));});FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-1,ui.dp(ui.tokens.navHeight),Gravity.BOTTOM);np.setMargins(ui.dp(20),0,ui.dp(20),ui.dp(24));root.addView(nav,np);
    }
    private void heading(int title){TextView v=ui.section(s(title));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(ui.dp(4),ui.dp(12),ui.dp(4),ui.dp(6));content.addView(v,p);}
    private LinearLayout card(){return ui.vertical();}
    private void add(LinearLayout card,View row){if(card.getChildCount()>0){FrameLayout previous=(FrameLayout)card.getChildAt(card.getChildCount()-1);View line=new View(this);line.setBackgroundColor(ui.tokens.rowDivider);line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);previous.addView(line,new FrameLayout.LayoutParams(-1,Math.max(1,ui.dp(1)),Gravity.BOTTOM));}FrameLayout frame=new FrameLayout(this);frame.addView(row,new FrameLayout.LayoutParams(-1,-2));card.addView(frame,new LinearLayout.LayoutParams(-1,-2));}
    private void show(LinearLayout card){content.addView(ui.surface(card,ui.tokens.cardRadius),new LinearLayout.LayoutParams(-1,-2));}
    private View unavailable(int title){View row=ui.row(s(title),ui.value(s(R.string.phase2_preferences_unavailable)),()->GlassBottomSheet.info(this,root,false,s(title),s(R.string.phase2_preferences_reason)));row.setAlpha(.72f);return row;}
    private View action(int title,Runnable action){return ui.row(s(title),ui.value("›"),action);}
    private View info(int title,String value){return ui.row(s(title),ui.value(value),null);}
    private View bool(int title,String key,boolean fallback){GlassSwitch control=new GlassSwitch(this);control.setContentDescription(s(title));control.setChecked(prefs.getBoolean(key,fallback));control.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean(key,checked).apply());return ui.row(s(title),control,null);}
    private View choice(int title,String display,String[] choices,int selected,GlassBottomSheet.Selection callback){return ui.row(s(title),ui.value(display+"  ›"),()->GlassBottomSheet.choose(this,root,false,s(title),choices,selected,index->{callback.chosen(index);render();}));}
    private View floatChoice(int title,String key,float fallback,float[] numbers,String[] choices){float value=prefs.getFloat(key,fallback);int chosen=0;float distance=Float.MAX_VALUE;for(int i=0;i<numbers.length;i++){float d=Math.abs(numbers[i]-value);if(d<distance){chosen=i;distance=d;}}String current=String.format(Locale.ROOT,"%.2f×",value);return choice(title,current,choices,chosen,index->prefs.edit().putFloat(key,numbers[index]).apply());}
    private void general(){
        heading(R.string.phase2_preferences_settings);LinearLayout a=card();add(a,unavailable(R.string.phase2_preferences_language));add(a,info(R.string.phase2_preferences_units,"mm"));add(a,unavailable(R.string.phase2_preferences_home));boolean dark=!"light".equals(prefs.getString("theme","dark"));add(a,choice(R.string.phase2_preferences_theme,s(dark?R.string.phase2_preferences_dark:R.string.phase2_preferences_light),new String[]{s(R.string.phase2_preferences_dark),s(R.string.phase2_preferences_light)},dark?0:1,i->prefs.edit().putString("theme",i==0?"dark":"light").apply()));add(a,unavailable(R.string.phase2_preferences_single));add(a,unavailable(R.string.phase2_preferences_splash));add(a,unavailable(R.string.phase2_preferences_downloads));show(a);
        heading(R.string.phase2_preferences_project);LinearLayout p=card();add(p,unavailable(R.string.phase2_preferences_load));int[] limits={6,12,18,30};String[] labels={"6","12","18","30"};int count=prefs.getInt("recent_count",12),which=1;for(int i=0;i<limits.length;i++)if(limits[i]==count)which=i;add(p,choice(R.string.phase2_preferences_recent,Integer.toString(count),labels,which,i->prefs.edit().putInt("recent_count",limits[i]).apply()));add(p,unavailable(R.string.phase2_preferences_recent_add));add(p,unavailable(R.string.phase2_preferences_gcode_warning));add(p,unavailable(R.string.phase2_preferences_step));add(p,unavailable(R.string.phase2_preferences_backup));show(p);
        heading(R.string.phase2_preferences_preset);LinearLayout t=card();add(t,info(R.string.phase2_preferences_remember_printer,"✓"));add(t,unavailable(R.string.phase2_preferences_group_presets));show(t);
    }
    private void control(){
        heading(R.string.phase2_preferences_behaviour);LinearLayout b=card();add(b,unavailable(R.string.phase2_preferences_flush));add(b,unavailable(R.string.phase2_preferences_arrange));show(b);
        heading(R.string.phase2_preferences_slicing);LinearLayout c=card();add(c,unavailable(R.string.phase2_preferences_auto_slice));add(c,unavailable(R.string.phase2_preferences_temperature));show(c);
        heading(R.string.phase2_preferences_camera);LinearLayout v=card();add(v,unavailable(R.string.phase2_preferences_camera_style));add(v,floatChoice(R.string.phase2_preferences_orbit,"orbit_speed",.75f,new float[]{.25f,.5f,.75f,1f,1.5f,2f},new String[]{"0.25×","0.5×","0.75×","1×","1.5×","2×"}));add(v,floatChoice(R.string.phase2_preferences_move,"move_speed",.6f,new float[]{.25f,.5f,.6f,.75f,1f},new String[]{"0.25×","0.5×","0.6×","0.75×","1×"}));add(v,unavailable(R.string.phase2_preferences_zoom_center));add(v,unavailable(R.string.phase2_preferences_free_camera));add(v,unavailable(R.string.phase2_preferences_swap));add(v,bool(R.string.phase2_preferences_reverse_zoom,"reverse_zoom",false));show(v);
        heading(R.string.phase2_preferences_clear_choice);LinearLayout clear=card();add(clear,unavailable(R.string.phase2_preferences_clear_projects));add(clear,unavailable(R.string.phase2_preferences_clear_presets));add(clear,unavailable(R.string.phase2_preferences_clear_sync));show(clear);
    }
    private void online(){
        heading(R.string.phase2_preferences_connection);LinearLayout c=card();add(c,bool(R.string.phase2_preferences_offline,"network_offline",false));add(c,action(R.string.phase2_preferences_network,()->startActivity(new Intent(this,NetworkActivity.class))));add(c,action(R.string.phase2_preferences_cloud,()->startActivity(new Intent(this,CloudSyncActivity.class))));add(c,unavailable(R.string.phase2_preferences_region));add(c,unavailable(R.string.phase2_preferences_stealth));add(c,unavailable(R.string.phase2_preferences_test));show(c);ui.space(content,6);content.addView(ui.text(s(R.string.phase2_preferences_offline_detail),12,400,ui.tokens.textSecondary));
        heading(R.string.phase2_preferences_sync);LinearLayout p=card();add(p,unavailable(R.string.phase2_preferences_stable));add(p,unavailable(R.string.phase2_preferences_auto_sync));add(p,unavailable(R.string.phase2_preferences_update_profiles));add(p,info(R.string.phase2_preferences_encryption,s(R.string.phase2_preferences_encryption_on)));add(p,unavailable(R.string.phase2_preferences_filament_sync));show(p);
        heading(R.string.phase2_preferences_plugin);LinearLayout n=card();add(n,unavailable(R.string.phase2_preferences_plugin_enable));add(n,unavailable(R.string.phase2_preferences_plugin_version));show(n);
    }
    private void associate(){heading(R.string.phase2_preferences_associate);TextView explanation=ui.text(s(R.string.phase2_preferences_associate_info),14,400,ui.tokens.textSecondary);content.addView(explanation);ui.space(content,8);LinearLayout c=card();add(c,unavailable(R.string.phase2_preferences_associate_stl));add(c,unavailable(R.string.phase2_preferences_associate_3mf));add(c,unavailable(R.string.phase2_preferences_associate_obj));show(c);}
    private void developer(){heading(R.string.phase2_preferences_settings);LinearLayout c=card();add(c,unavailable(R.string.phase2_preferences_developer_mode));add(c,unavailable(R.string.phase2_preferences_ams));show(c);heading(R.string.phase2_preferences_storage);LinearLayout storage=card();add(storage,unavailable(R.string.phase2_preferences_storage_abnormal));add(storage,unavailable(R.string.phase2_preferences_log));show(storage);heading(R.string.phase2_preferences_plugin);LinearLayout p=card();add(p,unavailable(R.string.phase2_preferences_reload));show(p);heading(R.string.phase2_preferences_developer);LinearLayout d=card();add(d,action(R.string.phase2_preferences_gallery,()->startActivity(new Intent(this,GlassGalleryActivity.class))));add(d,info(R.string.phase2_preferences_engine,OrcaProfileCatalog.UPSTREAM_REVISION));add(d,info(R.string.phase2_preferences_platform,"Android "+android.os.Build.VERSION.RELEASE+" / API "+android.os.Build.VERSION.SDK_INT));show(d);ui.space(content,12);content.addView(ui.text(s(R.string.phase2_preferences_transfer_state),12,400,ui.tokens.textSecondary));}
}
