package com.orcamobile.app;
import android.content.Context;
import java.io.IOException;
/** Offline mode gates explicit operations; no printer or cloud request runs at app startup. */
final class NetworkPolicy {
    static boolean offline(Context c){return c.getSharedPreferences("app_preferences",0).getBoolean("network_offline",false);}
    static void requireOnline(Context c)throws IOException{if(offline(c))throw new IOException("Включён режим без сети. Измените режим на экране подключения.");}
}
