package com.keplerslicer.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * Android control preferences. These affect only the supported single-model
 * workspace; multi-material flushing and clone arrangement require features
 * that the workspace/engine bridge does not yet expose.
 */
final class ControlPreferences {
    static final String PREFS="app_preferences";
    static final String CAMERA_STYLE="camera_style", ZOOM_CENTER="zoom_center",
            FREE_CAMERA="free_camera", SWAP_GESTURES="swap_gestures", AUTO_SLICE="auto_slice",
            AUTO_SLICE_DELAY="auto_slice_delay_seconds", UNSAVED_PROJECT="choice_unsaved_project",
            UNSAVED_PRESET="choice_unsaved_preset", SYNC_CHOICE="choice_printer_sync";
    static final String STYLE_DEFAULT="default", STYLE_TOUCHPAD="touchpad";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static Runnable pending;
    private static volatile long generation;
    private static boolean dirtyRequest;
    private static long dueTime;
    private ControlPreferences() {}
    private static SharedPreferences prefs(Context context){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    static boolean freeCamera(Context context){return prefs(context).getBoolean(FREE_CAMERA,false);}
    static boolean zoomToGesture(Context context){return prefs(context).getBoolean(ZOOM_CENTER,false);}
    static boolean swapGestures(Context context){return prefs(context).getBoolean(SWAP_GESTURES,false);}
    static boolean touchpad(Context context){return STYLE_TOUCHPAD.equals(prefs(context).getString(CAMERA_STYLE,STYLE_DEFAULT));}
    static boolean autoSlice(Context context){return prefs(context).getBoolean(AUTO_SLICE,false);}
    static boolean currentAutoRequest(Context context,long request){return request==generation&&autoSlice(context);}
    static int delaySeconds(Context context){return Math.max(0,Math.min(60,prefs(context).getInt(AUTO_SLICE_DELAY,0)));}
    /** Clear a remembered answer, never the corresponding project, profile or credentials. */
    static void clearRememberedChoice(Context context,String key){
        if(!UNSAVED_PROJECT.equals(key)&&!UNSAVED_PRESET.equals(key)&&!SYNC_CHOICE.equals(key))
            throw new IllegalArgumentException("Unknown remembered choice");
        prefs(context).edit().remove(key).apply();
    }
    /**
     * Call on the main thread after a saved slicing-related change, never while
     * a draft is still being edited. The job runs through the ordinary slicing
     * screen; it does not send G-code to a printer.
     */
    static void scheduleAutoSlice(Activity activity) {
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Expected main thread");
        final long request=++generation;
        if(pending!=null)MAIN.removeCallbacks(pending);
        pending=null;
        dirtyRequest=false;
        if(activity==null||!autoSlice(activity))return;
        dirtyRequest=true;dueTime=android.os.SystemClock.uptimeMillis()+delaySeconds(activity)*1000L;
        if(eligible(activity))arm(activity,request);
    }
    private static boolean eligible(Activity activity){return activity instanceof MainActivity||activity instanceof SettingsActivity;}
    /** Rebind a deferred change after navigating between eligible app screens. */
    static void onForeground(Activity activity) {
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Expected main thread");
        if(activity==null||!dirtyRequest||!eligible(activity))return;
        if(!autoSlice(activity)){cancelAutoSlice();return;}
        arm(activity,generation);
    }
    /** Call immediately when the user switches auto slicing off. */
    static void cancelAutoSlice() {
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Expected main thread");
        generation++;dirtyRequest=false;if(pending!=null)MAIN.removeCallbacks(pending);pending=null;
    }
    private static void arm(Activity activity,long request){
        if(pending!=null)MAIN.removeCallbacks(pending);
        WeakReference<Activity> ref=new WeakReference<>(activity);
        pending=new Runnable(){int focusWaits;public void run(){
            if(pending!=this||!dirtyRequest||request!=generation)return;
            Activity owner=ref.get();
            if(owner==null||owner.isFinishing()||owner.isDestroyed()){pending=null;return;}
            if(!autoSlice(owner)){cancelAutoSlice();return;}
            if(!owner.hasWindowFocus()){if(focusWaits++<20)MAIN.postDelayed(this,100);else pending=null;return;}
            if(owner instanceof SettingsActivity&&!((SettingsActivity)owner).autoSliceReady()){MAIN.postDelayed(this,1000);return;}
            if(SliceActivity.isSlicing()){MAIN.postDelayed(this,1000);return;}
            try {
                CurrentModelStore.selectionKey(owner);
                PrinterProfileCatalog.Profile printer=PrinterProfileCatalog.loadSelection(owner);
                if(printer==null||printer.imported||PrintProfileCatalog.loadSelection(owner,PrintProfileCatalog.PROCESS)==null||
                        PrintProfileCatalog.loadSelection(owner,PrintProfileCatalog.FILAMENT)==null){pending=null;dirtyRequest=false;return;}
            } catch(IOException error){pending=null;dirtyRequest=false;return;}
            pending=null;dirtyRequest=false;
            owner.startActivity(new Intent(owner,SliceActivity.class).putExtra(SliceActivity.EXTRA_AUTO_SLICE,true)
                    .putExtra(SliceActivity.EXTRA_AUTO_SLICE_REQUEST,request));
        }};
        MAIN.postDelayed(pending,Math.max(0,dueTime-android.os.SystemClock.uptimeMillis()));
    }
}
