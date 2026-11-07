package com.keplerslicer.app;

import android.app.Activity;
import android.app.Application;
import android.app.LocaleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.LocaleList;
import android.provider.DocumentsContract;
import org.json.JSONObject;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Android consumers for General preferences. File/backup operations run off the UI thread. */
final class GeneralPreferences {
    static final String PREFS="app_preferences";
    static final String CHOICE_PROJECT="choice_unsaved_project";
    private static final ScheduledExecutorService BACKUPS=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"workspace-backup"));
    private static ScheduledFuture<?> backupTask;
    private static int foreground;
    private GeneralPreferences(){}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,0);}
    static String language(Context c){String s=prefs(c).getString("language","");return "en".equals(s)||"ru".equals(s)?s:"";}
    static String defaultPage(Context c){return "home".equals(prefs(c).getString("default_page","prepare"))?"home":"prepare";}
    /** Android keeps one active workspace because its private model store is shared. */
    static boolean singleInstance(Context c){return true;}
    static boolean showSplash(Context c){return prefs(c).getBoolean("show_splash_screen",true);}
    static String projectLoadBehaviour(Context c){String s=prefs(c).getString("project_load_behaviour","ask_when_relevant");for(String allowed:new String[]{"load_all","ask_when_relevant","always_ask","load_geometry"})if(allowed.equals(s))return s;return "ask_when_relevant";}
    static boolean addModelToRecent(Context c,String name){String n=name==null?"":name.toLowerCase(Locale.ROOT);return !(n.endsWith(".stl")||n.endsWith(".step")||n.endsWith(".stp"))||prefs(c).getBoolean("recent_models",true);}
    static boolean suppressModifiedGcodeWarning(Context c){return prefs(c).getBoolean("no_warn_when_modified_gcodes",false);}
    static String filamentGroup(Context c,String name,JSONObject effective,String vendor){
        String mode=prefs(c).getString("group_filament_presets","all");
        if("none".equals(mode))return "";
        if("vendor".equals(mode)){String from=effective==null?"":scalar(effective.opt("filament_vendor"));return from.isEmpty()?(vendor==null?"":vendor):from;}
        if("type".equals(mode))return effective==null?"":scalar(effective.opt("filament_type"));
        return c.getString(R.string.general_all_user_filaments);
    }
    private static String scalar(Object value){if(value instanceof org.json.JSONArray)return ((org.json.JSONArray)value).optString(0,"");return value instanceof String?(String)value:"";}

    static void setLanguage(Activity activity,String tag){
        if(!tag.isEmpty()&&!tag.equals("en")&&!tag.equals("ru"))throw new IllegalArgumentException("Unsupported application language");
        prefs(activity).edit().putString("language",tag).apply();
        if(android.os.Build.VERSION.SDK_INT>=33)Api33.setLanguage(activity,tag);
        else {applyLanguage(activity.getApplicationContext());applyLanguage(activity);activity.recreate();}
    }
    static void applyLanguage(Context context){
        if(android.os.Build.VERSION.SDK_INT>=33)return;
        String tag=language(context);Locale locale=tag.isEmpty()?android.content.res.Resources.getSystem().getConfiguration().getLocales().get(0):Locale.forLanguageTag(tag);
        Locale.setDefault(locale);Configuration config=new Configuration(context.getResources().getConfiguration());config.setLocales(new LocaleList(locale));context.getResources().updateConfiguration(config,context.getResources().getDisplayMetrics());
    }
    @android.annotation.TargetApi(33) private static final class Api33 {static void setLanguage(Context c,String tag){LocaleManager manager=c.getSystemService(LocaleManager.class);if(manager!=null)manager.setApplicationLocales(LocaleList.forLanguageTags(tag));}}

    /** Persisted document-tree grant: a starting directory, never a raw filesystem permission. */
    static void chooseDownloadFolder(Activity a,int request){a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION),request);}
    static void acceptDownloadFolder(Context c,Intent result)throws IOException {
        Uri uri=result==null?null:result.getData();if(uri==null||!"content".equals(uri.getScheme())||!DocumentsContract.isTreeUri(uri))throw new IOException(c.getString(R.string.general_folder_invalid));
        int grant=result.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if((grant&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0)throw new IOException(c.getString(R.string.general_folder_read_only));
        try{if((grant&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0)c.getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);else c.getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(SecurityException e){throw new IOException(c.getString(R.string.general_folder_permission),e);}
        if(!prefs(c).edit().putString("download_tree_uri",uri.toString()).commit())throw new IOException(c.getString(R.string.general_folder_not_saved));
    }
    static Uri initialDocumentUri(Context c){
        String raw=prefs(c).getString("download_tree_uri","");if(raw==null||raw.isEmpty())return null;Uri uri=Uri.parse(raw);
        for(android.content.UriPermission p:c.getContentResolver().getPersistedUriPermissions())if(p.getUri().equals(uri)&&p.isWritePermission())return uri;
        return null;
    }
    static String downloadFolderLabel(Context c){Uri uri=initialDocumentUri(c);if(uri==null)return c.getString(R.string.general_folder_system);try{return DocumentsContract.getTreeDocumentId(uri);}catch(Exception e){return c.getString(R.string.general_folder_selected);}}
    static Intent associationPickerIntent(String format){
        String[] mime=format.equals("stl")?new String[]{"model/stl","application/sla","application/vnd.ms-pki.stl","application/octet-stream"}:format.equals("obj")?new String[]{"model/obj","text/plain","application/octet-stream"}:new String[]{"model/3mf","application/vnd.ms-3mfdocument","application/octet-stream"};
        return new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    }
    static void openAssociatedUri(Activity a,Intent result)throws IOException {openAssociatedUri(a,result,"");}
    static void openAssociatedUri(Activity a,Intent result,String expectedFormat)throws IOException {if(result==null||result.getData()==null)return;Uri uri=result.getData();String display=null,mime=null;
        try(android.database.Cursor cursor=a.getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst()&&!cursor.isNull(0))display=cursor.getString(0);}catch(RuntimeException ignored){}
        try{mime=a.getContentResolver().getType(uri);}catch(RuntimeException ignored){}
        String normalized=ModelFilename.normalize(display,uri.getLastPathSegment(),mime);if(expectedFormat!=null&&!expectedFormat.isEmpty()&&!normalized.toLowerCase(Locale.ROOT).endsWith("."+expectedFormat.toLowerCase(Locale.ROOT)))throw new IOException(a.getString(R.string.general_association_wrong_type,expectedFormat.toUpperCase(Locale.ROOT)));
        if((result.getFlags()&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0)try{a.getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
        a.startActivity(new Intent(a,LaunchActivity.class).setAction(Intent.ACTION_VIEW).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
    static Uri modelUri(Intent intent){if(intent==null)return null;Uri uri=intent.getData();if(uri==null&&Intent.ACTION_SEND.equals(intent.getAction())){try{Object stream=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(stream instanceof Uri)uri=(Uri)stream;}catch(RuntimeException ignored){}}
        if(uri==null&&intent.getClipData()!=null&&intent.getClipData().getItemCount()>0)uri=intent.getClipData().getItemAt(0).getUri();
        return uri!=null&&("content".equals(uri.getScheme())||"file".equals(uri.getScheme()))?uri:null;}

    static int backupInterval(Context c){Object raw=prefs(c).getAll().get("backup_interval");int seconds=10;try{seconds=raw==null?10:Integer.parseInt(String.valueOf(raw));}catch(NumberFormatException ignored){}return Math.max(5,Math.min(86400,seconds));}
    static String backupStatus(Context c){String error=prefs(c).getString("general_backup_error","");if(error!=null&&!error.isEmpty())return c.getString(R.string.general_backup_error,error);long time=prefs(c).getLong("general_backup_time",0);return time==0?c.getString(R.string.general_backup_none):c.getString(R.string.general_backup_status,android.text.format.DateFormat.getMediumDateFormat(c).format(new java.util.Date(time)),android.text.format.DateFormat.getTimeFormat(c).format(new java.util.Date(time)));}
    static void backupNow(Context c)throws IOException {GeneralBackupArchive.backup(c);prefs(c).edit().putLong("general_backup_time",System.currentTimeMillis()).remove("general_backup_error").apply();}
    static void restoreBackup(Context c)throws IOException {GeneralBackupArchive.restore(c);}
    static synchronized void refreshBackupSchedule(Context c){if(backupTask!=null){backupTask.cancel(false);backupTask=null;}if(foreground==0||!prefs(c).getBoolean("backup_switch",false))return;Context app=c.getApplicationContext();int seconds=backupInterval(c);backupTask=BACKUPS.scheduleWithFixedDelay(()->{try{backupNow(app);}catch(IOException e){prefs(app).edit().putString("general_backup_error",e.getMessage()).apply();}},seconds,seconds,TimeUnit.SECONDS);}
    static void install(Application application){applyLanguage(application);application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
        public void onActivityCreated(Activity a,Bundle state){applyLanguage(a);}public void onActivityStarted(Activity a){synchronized(GeneralPreferences.class){foreground++;if(foreground==1)refreshBackupSchedule(application);}}
        public void onActivityStopped(Activity a){synchronized(GeneralPreferences.class){foreground=Math.max(0,foreground-1);if(foreground==0)refreshBackupSchedule(application);}}
        public void onActivityResumed(Activity a){OnlinePreferences.foreground(a);ControlPreferences.onForeground(a);}public void onActivityPaused(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
    });}
}
