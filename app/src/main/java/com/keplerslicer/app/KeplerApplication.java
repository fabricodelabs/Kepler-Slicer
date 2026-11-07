package com.keplerslicer.app;
/** Application locale and foreground-only automatic workspace backups. */
public final class KeplerApplication extends android.app.Application {
    private static KeplerApplication instance;
    /** App-language string for static helpers and worker classes that have no Context of their own. */
    static android.content.Context get() { return instance; }
    static String str(int id, Object... args) { return args.length == 0 ? instance.getString(id) : instance.getString(id, args); }
    /** Tables for {@link L}: Russian source text to English, English source text to Russian. */
    private static void installMessages(android.content.Context c){try(java.io.InputStream in=c.getAssets().open("kepler_messages.json")){org.json.JSONObject j=new org.json.JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));L.install(map(j.optJSONObject("en")),map(j.optJSONObject("ru")));}catch(Exception ignored){}}
    private static java.util.Map<String,String> map(org.json.JSONObject o){java.util.HashMap<String,String> m=new java.util.HashMap<>();if(o!=null)for(java.util.Iterator<String> it=o.keys();it.hasNext();){String k=it.next();m.put(k,o.optString(k,k));}return m;}
    @Override public void onCreate(){super.onCreate();instance=this;installMessages(this);GeneralPreferences.install(this);OnlinePreferences.attach(this);
        // The interface mode fixes the orientation of every screen: tablet landscape only, mobile portrait only.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            public void onActivityCreated(android.app.Activity a, android.os.Bundle s) { InterfaceMode.applyOrientation(a); }
            public void onActivityResumed(android.app.Activity a) { InterfaceMode.applyOrientation(a); }
            public void onActivityStarted(android.app.Activity a) {} public void onActivityPaused(android.app.Activity a) {} public void onActivityStopped(android.app.Activity a) {}
            public void onActivitySaveInstanceState(android.app.Activity a, android.os.Bundle s) {} public void onActivityDestroyed(android.app.Activity a) {}
        });
        // iOS-style push/pop for every screen. On Android 14+ these also drive the predictive back gesture, so a
        // swipe back scrubs the pop animation with the finger and shows the previous screen underneath.
        if (android.os.Build.VERSION.SDK_INT >= 34) registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(android.app.Activity a, android.os.Bundle b) {
                a.overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, R.anim.kv_push_in, R.anim.kv_push_out);
                a.overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_CLOSE, R.anim.kv_pop_in, R.anim.kv_pop_out);
            }
            @Override public void onActivityStarted(android.app.Activity a) {}
            @Override public void onActivityResumed(android.app.Activity a) {}
            @Override public void onActivityPaused(android.app.Activity a) {}
            @Override public void onActivityStopped(android.app.Activity a) {}
            @Override public void onActivitySaveInstanceState(android.app.Activity a, android.os.Bundle b) {}
            @Override public void onActivityDestroyed(android.app.Activity a) {}
        });
        // Bambu printers connect through open-bamboo-networking; the agent starts on the first connection.
        BambuLan.transport = new BambuObnTransport(this);
        getSharedPreferences("bambu_lan_pins", MODE_PRIVATE).edit().clear().apply(); // certificates of the old Java client
        // Warm the settings schema and profile index so the Settings tab opens without a loading page.
        new Thread(()->{try{com.keplerslicer.app.settings.SettingsSchema.load(this);SettingsState.repository(this);}catch(Exception ignored){}},"settings-warmup").start();
        new Thread(()->{try{String requested=getSharedPreferences("app_preferences",0).getString("log_severity_level","info");String actual=NativePreferenceActions.setLogLevel(requested);getSharedPreferences("app_preferences",0).edit().putString("native_log_severity_actual",actual).apply();}catch(java.io.IOException ignored){getSharedPreferences("app_preferences",0).edit().remove("native_log_severity_actual").apply();}},"native-log-init").start();
    }
}
