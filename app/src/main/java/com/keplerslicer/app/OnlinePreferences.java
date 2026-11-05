package com.keplerslicer.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.keplerslicer.app.settings.PresetRepository;
import com.keplerslicer.app.settings.PresetJson;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Preferences with real consumers. All network/disk actions are worker-only unless stated. */
public final class OnlinePreferences {
    public static final String[] REGIONS={"Asia-Pacific","China","Europe","North America","Others"};
    public static final String[] LOG_LEVELS={"fatal","error","warning","info","debug","trace"};
    private static final String TOKEN=StorageNames.CLOUD_TOKEN;
    private static final Object SESSION_LOCK=new Object(),REFRESH_LOCK=new Object(),SYNC_LOCK=new Object();
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    private static volatile long lastForegroundAttempt;
    private static volatile NetworkHttp.Cancellation automaticRequest;
    private OnlinePreferences(){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("app_preferences",0);}
    public static void attach(Context c){NetworkPolicy.install(c);}
    public static void changed(Context c){attach(c);if(NetworkPolicy.offline(c)||prefs(c).getBoolean("stealth_mode",false))NetworkHttp.cancelActive();NetworkHttp.Cancellation automatic=automaticRequest;if(!prefs(c).getBoolean("sync_user_preset",false)&&automatic!=null)automatic.cancel();lastForegroundAttempt=0;foreground(c);}
    /** No network work is started unless the user explicitly enabled a network preference. */
    /** Profile cloud sync was removed; an old "sync on" choice is switched off. */
    public static void foreground(Context context){if(context!=null)prefs(context.getApplicationContext()).edit().putBoolean("sync_user_preset",false).apply();}
    public static int regionIndex(Context c){String value=prefs(c).getString("region",REGIONS[0]);for(int i=0;i<REGIONS.length;i++)if(REGIONS[i].equals(value))return i;return 0;}
    /** Bambu-specific region routing cannot be applied without the absent Android provider. */
    /** Region of the Bambu Lab account (China uses bambulab.cn, every other region bambulab.com). */
    public static void setRegion(Context c,int index)throws IOException{if(index<0||index>=REGIONS.length)throw new IOException("Unknown region");if(!prefs(c).edit().putString("region",REGIONS[index]).commit())throw new IOException(L.t("Не удалось сохранить регион."));}
    public static String region(Context c){return REGIONS[regionIndex(c)];}
    public static void setStealth(Context c,boolean enabled)throws IOException{if(!prefs(c).edit().putBoolean("stealth_mode",enabled).commit())throw new IOException(L.t("Режим не сохранён."));changed(c);}
    public static String connectionTest(Context c)throws IOException{NetworkPolicy.requireOnline(c);NetworkStore.Connection selected=NetworkStore.load(c).selected();if(selected!=null){NetworkPolicy.requireEndpoint(c,selected.endpoint);PrinterHostClient.HostInfo result=new PrinterHostClient(selected.endpoint).test(new NetworkHttp.Cancellation());return L.t("Связь с выбранным принтером подтверждена: ")+result.name+" · "+result.state;}NetworkPolicy.requireCloud(c);long begin=android.os.SystemClock.elapsedRealtime();NetworkHttp.Response r=NetworkHttp.request("GET",URI.create("https://api.github.com/repos/OrcaSlicer/OrcaSlicer"),java.util.Collections.emptyMap(),null,new NetworkHttp.Cancellation());r.requireSuccess();record(c,3,"network-test","https-success");return L.t("HTTPS к официальному репозиторию движка: HTTP ")+r.status+", "+(android.os.SystemClock.elapsedRealtime()-begin)+L.t(" мс. Выбранного принтера нет.");}
    /** Checks the official release channel; no desktop binary or incompatible preset pack is installed. */
    public static String checkUpdates(Context c)throws IOException{NetworkPolicy.requireCloud(c);NetworkHttp.Response r=NetworkHttp.request("GET",URI.create("https://api.github.com/repos/OrcaSlicer/OrcaSlicer/releases?per_page=30"),java.util.Collections.emptyMap(),null,new NetworkHttp.Cancellation(),8*1024*1024);r.requireSuccess();try{JSONObject chosen=OnlineRules.selectRelease(new JSONArray(r.text()),prefs(c).getBoolean("check_stable_update_only",true));if(chosen==null)throw new IOException(L.t("Нет релиза в выбранном канале."));String tag=chosen.optString("tag_name");prefs(c).edit().putString("online_release_tag",tag).putString("online_release_url",chosen.optString("html_url")).putLong("online_release_checked",System.currentTimeMillis()).apply();record(c,3,"release-check","completed");return L.t("Официальный релиз движка: ")+tag+(chosen.optBoolean("prerelease",false)?L.t(" · предварительный"):L.t(" · стабильный"))+L.t(". Это релиз настольного движка; Android APK автоматически не заменяется.");}catch(JSONException e){throw new IOException(L.t("Неверный ответ списка релизов."),e);}}
    public static int logLevel(Context c){String level=prefs(c).getString("log_severity_level","info");for(int i=0;i<LOG_LEVELS.length;i++)if(LOG_LEVELS[i].equals(level))return i;return 3;}
    /** Structured local diagnostics never include endpoints, credentials, account IDs or raw server bodies. */
    public static void record(Context c,int level,String event,String outcome){if(!OnlineRules.logEnabled(logLevel(c),level))return;try{File dir=new File(c.getFilesDir(),"diagnostics");synchronized(OnlinePreferences.class){if(!dir.isDirectory()&&!dir.mkdirs())return;File log=new File(dir,"events.jsonl");if(log.length()>1024*1024){File previous=new File(dir,"events.previous.jsonl");java.nio.file.Files.move(log.toPath(),previous.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}JSONObject row=new JSONObject().put("time",System.currentTimeMillis()).put("level",LOG_LEVELS[level]).put("event",event).put("outcome",outcome);try(java.io.FileOutputStream out=new java.io.FileOutputStream(log,true)){out.write((row.toString()+"\n").getBytes(StandardCharsets.UTF_8));}}}catch(Exception ignored){}}
    public static String diagnostics(Context c){SharedPreferences p=prefs(c);return "Android API "+android.os.Build.VERSION.SDK_INT+L.t("\nДвижок ")+PrinterProfileCatalog.UPSTREAM_REVISION+L.t("\nРегион: ")+REGIONS[regionIndex(c)]+L.t("\nОблако профилей: глобальный API api.orcaslicer.com\nСеть: ")+(NetworkPolicy.offline(c)?L.t("выключена"):p.getBoolean("stealth_mode",false)?L.t("только LAN"):L.t("включена"))+L.t("\nПоследняя синхронизация: ")+p.getLong("online_last_sync",0)+L.t("\nПоследняя проверка релиза: ")+p.getString("online_release_tag",L.t("не выполнялась"))+L.t("\nЖурнал Java: ")+LOG_LEVELS[logLevel(c)]+"\n"+bambuCapability();}
    public static String bambuCapability(){return L.t("Bambu network plug-in не установлен: в проекте нет Android-библиотеки и JNI-интерфейса Bambu. AMS, синхронизация филамента, проверка/разрешение abnormal storage, выбор версии и перезагрузка Bambu plug-in требуют этого провайдера. Встроенный HTTP-транспорт Kepler не выдаётся за Bambu plug-in.");}
    public static String reload(Context c)throws IOException{throw new IOException(bambuCapability());}
    public static String lastStatus(Context c){SharedPreferences p=prefs(c);String error=p.getString("online_last_error","");return error.isEmpty()?L.t("Последняя синхронизация: ")+p.getLong("online_last_sync",0):error;}
    private static String safeMessage(Throwable e){String m=e.getMessage();return m==null?e.getClass().getSimpleName():m;}
}
