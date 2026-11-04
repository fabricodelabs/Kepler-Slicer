package com.keplerslicer.app;
/** Application locale and foreground-only automatic workspace backups. */
public final class KeplerApplication extends android.app.Application {
    @Override public void onCreate(){super.onCreate();StorageMigration.run(this);GeneralPreferences.install(this);OnlinePreferences.attach(this);
        new Thread(()->{try{String requested=getSharedPreferences("app_preferences",0).getString("log_severity_level","info");String actual=NativePreferenceActions.setLogLevel(requested);getSharedPreferences("app_preferences",0).edit().putString("native_log_severity_actual",actual).apply();}catch(java.io.IOException ignored){getSharedPreferences("app_preferences",0).edit().remove("native_log_severity_actual").apply();}},"native-log-init").start();
    }
}
