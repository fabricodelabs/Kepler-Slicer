package com.orcamobile.app;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
/** Deterministic scheduler test: fake Android lifecycle and fake slice boundary, no JNI. */
public final class ControlPreferencesTest {
    static int checks;
    static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    public static void main(String[] args) {
        Activity a=new MainActivity();
        SharedPreferences p=a.getSharedPreferences(ControlPreferences.PREFS,0);
        ControlPreferences.scheduleAutoSlice(a);Handler.advance(3000);
        check(a.started.isEmpty(),"disabled preference does not start");
        p.edit().putBoolean(ControlPreferences.AUTO_SLICE,true).putInt(ControlPreferences.AUTO_SLICE_DELAY,2).apply();
        ControlPreferences.scheduleAutoSlice(a);Handler.advance(1999);check(a.started.isEmpty(),"debounce waits");
        Handler.advance(1);check(a.started.size()==1,"one job starts");
        long first=(Long)a.started.get(0).extras.get(SliceActivity.EXTRA_AUTO_SLICE_REQUEST);
        check(ControlPreferences.currentAutoRequest(a,first),"request token current");
        Handler.advance(5000);check(a.started.size()==1,"completed request does not loop");
        ControlPreferences.scheduleAutoSlice(a);ControlPreferences.scheduleAutoSlice(a);
        check(!ControlPreferences.currentAutoRequest(a,first),"new request invalidates old");
        Handler.advance(2000);check(a.started.size()==2,"superseded timer does not start twice");
        p.edit().putInt(ControlPreferences.AUTO_SLICE_DELAY,0).apply();
        SliceActivity.working=true;ControlPreferences.scheduleAutoSlice(a);Handler.advance(2000);
        check(a.started.size()==2,"working native job prevents parallel launch");
        SliceActivity.working=false;Handler.advance(1000);check(a.started.size()==3,"latest request starts after existing job");
        ControlPreferences.scheduleAutoSlice(a);p.edit().putBoolean(ControlPreferences.AUTO_SLICE,false).apply();
        Handler.advance(10);check(a.started.size()==3,"disabling revokes queued job");
        p.edit().putBoolean(ControlPreferences.AUTO_SLICE,true).apply();
        CurrentModelStore.present=false;ControlPreferences.scheduleAutoSlice(a);Handler.advance(1);
        check(a.started.size()==3,"missing model does not launch");CurrentModelStore.present=true;
        a.focus=false;ControlPreferences.scheduleAutoSlice(a);Handler.advance(500);a.focus=true;Handler.advance(100);
        check(a.started.size()==4,"waits briefly for returning foreground focus");
        a.focus=false;ControlPreferences.scheduleAutoSlice(a);Handler.advance(2500);
        check(a.started.size()==4,"background activity does not start slicing");
        a.focus=true;ControlPreferences.onForeground(a);Handler.advance(0);
        check(a.started.size()==5,"pending saved change transfers back to foreground");
        ControlPreferences.scheduleAutoSlice(a);ControlPreferences.cancelAutoSlice();ControlPreferences.onForeground(a);Handler.advance(3000);
        check(a.started.size()==5,"explicit cancel cannot resurrect request");
        Activity preferences=new Activity();ControlPreferences.scheduleAutoSlice(preferences);Handler.advance(5000);
        check(preferences.started.isEmpty()&&a.started.size()==5,"non-workspace activity does not start slicing");
        ControlPreferences.onForeground(a);Handler.advance(0);
        check(a.started.size()==6,"deferred request starts after returning to workspace");
        SettingsActivity settings=new SettingsActivity();settings.focus=false;
        ControlPreferences.scheduleAutoSlice(settings);settings.finishing=true;Handler.advance(0);
        ControlPreferences.onForeground(a);Handler.advance(0);
        check(a.started.size()==7,"saved settings change transfers from finishing settings screen");
        ControlPreferences.scheduleAutoSlice(a);
        p.edit().putBoolean(ControlPreferences.AUTO_SLICE,false).apply();ControlPreferences.scheduleAutoSlice(a);
        p.edit().putBoolean(ControlPreferences.AUTO_SLICE,true).apply();ControlPreferences.onForeground(a);Handler.advance(3000);
        check(a.started.size()==7,"off callback invalidates pending even if immediately re-enabled");
        SettingsActivity editing=new SettingsActivity();editing.ready=false;
        ControlPreferences.scheduleAutoSlice(editing);Handler.advance(5000);
        check(editing.started.isEmpty(),"pending slicing does not interrupt a loading or dirty settings editor");
        editing.ready=true;Handler.advance(1000);
        check(editing.started.size()==1,"saved settings can finish before deferred slicing resumes");
        p.edit().putString(ControlPreferences.UNSAVED_PROJECT,"replace").putString(ControlPreferences.UNSAVED_PRESET,"save").putString("project_data","keep").apply();
        ControlPreferences.clearRememberedChoice(a,ControlPreferences.UNSAVED_PROJECT);
        check(p.getString(ControlPreferences.UNSAVED_PROJECT,"absent").equals("absent"),"only remembered project answer cleared");
        check(p.getString(ControlPreferences.UNSAVED_PRESET,"absent").equals("save")&&p.getString("project_data","").equals("keep"),"profile and project data remain");
        try{ControlPreferences.clearRememberedChoice(a,"project_data");throw new AssertionError("accepted unrelated key");}
        catch(IllegalArgumentException expected){checks++;}
        System.out.println("Control scheduler host tests passed: "+checks+" checks (JNI not executed)");
    }
}
