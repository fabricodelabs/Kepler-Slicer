package com.orcamobile.app;
/** Application locale and foreground-only automatic workspace backups. */
public final class KeplerApplication extends android.app.Application {
    @Override public void onCreate(){super.onCreate();GeneralPreferences.install(this);OnlinePreferences.attach(this);}
}
