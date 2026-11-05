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
        new Thread(()->{try{String requested=getSharedPreferences("app_preferences",0).getString("log_severity_level","info");String actual=NativePreferenceActions.setLogLevel(requested);getSharedPreferences("app_preferences",0).edit().putString("native_log_severity_actual",actual).apply();}catch(java.io.IOException ignored){getSharedPreferences("app_preferences",0).edit().remove("native_log_severity_actual").apply();}},"native-log-init").start();
    }
}
