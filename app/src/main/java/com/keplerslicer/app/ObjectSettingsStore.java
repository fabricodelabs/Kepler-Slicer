package com.keplerslicer.app;

import android.content.Context;
import android.content.SharedPreferences;
import com.keplerslicer.app.settings.PresetJson;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/** Typed process overrides for the one native object in an exact private file/plate scope. */
final class ObjectSettingsStore {
    private static final String PREFS="kepler_object_settings_v1";
    private static final Object LOCK=new Object();
    static final class Snapshot {
        final String modelFileName,scopeKey,fingerprint;
        final int plateIndex,nativeObjectCount;
        /** Detached inspection copy. Application always uses the frozen serialized values. */
        final JSONObject values;
        private final String frozen;
        Snapshot(String fileName,int plate,int count,JSONObject changes)throws IOException {
            modelFileName=fileName;plateIndex=plate;nativeObjectCount=count;scopeKey=fileName.isEmpty()?"":key(fileName,plate);values=validated(changes);frozen=values.toString();
            JSONObject identity=new JSONObject();try{identity.put("format","kepler-object-settings-v1");identity.put("file",fileName);identity.put("plate",plate);identity.put("values",values);}catch(Exception e){throw new IOException(e);}fingerprint=PresetJson.fingerprint(identity);
        }
        JSONObject changes()throws IOException {try{return new JSONObject(frozen);}catch(Exception e){throw new IOException(e);}}
        boolean isEmpty(){return frozen.equals("{}");}
        SettingsState.Snapshot applied(SettingsState.Snapshot profiles)throws IOException {if(isEmpty())return profiles;requireSingle(nativeObjectCount);return profiles.mergingProcess(changes());}
    }
    private static final class Scope {
        final String file;final int plate;
        Scope(String file,int plate){this.file=file;this.plate=plate;}
        boolean same(Scope other){return file.equals(other.file)&&plate==other.plate;}
    }
    private ObjectSettingsStore() { }

    static JSONObject load(Context context,String modelFileName,int plateIndex,int nativeObjectCount)throws IOException {
        requireSingle(nativeObjectCount);synchronized(LOCK){return read(context,key(modelFileName,plateIndex));}
    }
    static void save(Context context,String modelFileName,int plateIndex,int nativeObjectCount,JSONObject changes)throws IOException {
        requireSingle(nativeObjectCount);String id=key(modelFileName,plateIndex);JSONObject value=validated(changes);
        synchronized(LOCK){Scope current=scope(context);if(!current.file.equals(modelFileName)||current.plate!=plateIndex)throw new IOException("The selected model or plate changed; reopen object settings");SharedPreferences prefs=context.getSharedPreferences(PREFS,0);Map<String,?> previous=prefs.getAll();SharedPreferences.Editor edit=prefs.edit();
            // Native count is proved on the worker before this save. Imported model files
            // have immutable private names, and plate belongs to this exact hashed scope.
            if(value.length()==0)edit.remove(id).remove(countKey(id));else edit.putString(id,value.toString()).putInt(countKey(id),nativeObjectCount);
            if(!edit.commit()){rollback(prefs,id,previous);throw new IOException("Cannot save object settings");}
            if(!current.same(scope(context))){rollback(prefs,id,previous);throw new IOException("The selected model or plate changed; reopen object settings");}}
    }
    static void clear(Context context,String modelFileName,int plateIndex)throws IOException {
        String id=key(modelFileName,plateIndex);synchronized(LOCK){SharedPreferences prefs=context.getSharedPreferences(PREFS,0);Map<String,?> previous=prefs.getAll();if(!prefs.edit().remove(id).remove(countKey(id)).commit()){rollback(prefs,id,previous);throw new IOException("Cannot clear object settings");}}
    }
    static void clear(Context context,String modelFileName,int plateIndex,int nativeObjectCount)throws IOException {clear(context,modelFileName,plateIndex);}
    static Snapshot snapshot(Context context,String modelFileName,int plateIndex,int nativeObjectCount)throws IOException {
        requireSingle(nativeObjectCount);synchronized(LOCK){return new Snapshot(modelFileName,plateIndex,nativeObjectCount,read(context,key(modelFileName,plateIndex)));}
    }
    /**
     * Worker-thread UI entry: obtains the native object's count for the current model.
     * A file/plate change during loading retries instead of associating metadata with another file.
     */
    static Snapshot current(Context context)throws IOException {
        for(int attempt=0;attempt<3;attempt++){Scope before=scope(context);if(before.file.isEmpty())throw new IOException("Open a model before editing object settings");int count;
            if(before.file.toLowerCase(Locale.ROOT).endsWith(".stl"))count=1;
            else{CurrentModelStore model=CurrentModelStore.load(context);if(model==null)throw new IOException("Open a model before editing object settings");if(!before.file.equals(model.file.getName()))continue;count=objectCount(model.metadata);}
            if(!before.same(scope(context)))continue;return snapshot(context,before.file,before.plate,count);
        }throw new IOException("The selected model or plate changed while loading object settings");
    }
    /**
     * UI-safe result fingerprint path: never imports native metadata or a mesh.
     * A nonempty legacy record without a worker-proved count must be reopened and saved.
     */
    static Snapshot snapshot(Context context)throws IOException {
        for(int attempt=0;attempt<3;attempt++){Scope selected=scope(context);Snapshot result;
            if(selected.file.isEmpty())result=new Snapshot("",-1,0,new JSONObject());
            else synchronized(LOCK){String id=key(selected.file,selected.plate);Map<String,?> stored=context.getSharedPreferences(PREFS,0).getAll();JSONObject values=read(stored,id);Object count=stored.get(countKey(id));if(values.length()!=0&&(!(count instanceof Integer)||((Integer)count)!=1))throw new IOException("Saved object settings have no verified single-object count; reopen object settings and save them again");result=new Snapshot(selected.file,selected.plate,values.length()==0?0:1,values);}
            if(selected.same(scope(context)))return result;
        }throw new IOException("The selected model or plate changed while reading object settings");
    }
    static int objectCount(JSONObject metadata)throws IOException {
        if(metadata==null)throw new IOException("Native object metadata is unavailable");JSONArray objects=metadata.optJSONArray("objects");if(objects==null){JSONObject project=metadata.optJSONObject("project_data");if(project!=null)objects=project.optJSONArray("objects");}if(objects==null)throw new IOException("Native object metadata is unavailable");return objects.length();
    }
    static String key(String fileName,int plate)throws IOException {
        if(fileName==null||!fileName.startsWith("model-")||fileName.indexOf('/')>=0||fileName.indexOf('\\')>=0||fileName.indexOf('\0')>=0||fileName.indexOf('\n')>=0||fileName.indexOf('\r')>=0||plate< -1)throw new IOException("Invalid object settings scope");String lower=fileName.toLowerCase(Locale.ROOT);if(!lower.endsWith(".stl")&&!lower.endsWith(".obj")&&!lower.endsWith(".3mf")&&!lower.endsWith(".step")&&!lower.endsWith(".stp"))throw new IOException("Unsupported model format");return PresetJson.sha256("kepler-object-scope-v1\0"+fileName+"\0"+plate);
    }
    private static Scope scope(Context context)throws IOException {
        Map<String,?> selection=context.getSharedPreferences("current_model",0).getAll();Object file=selection.get("file"),plate=selection.get("plate");String name=file==null?"":String.valueOf(file);int index;try{index=Integer.parseInt(plate==null?"-1":String.valueOf(plate));}catch(NumberFormatException e){throw new IOException("Invalid selected plate",e);}if(!name.isEmpty())key(name,index);return new Scope(name,index);
    }
    private static JSONObject read(Context context,String id)throws IOException {return read(context.getSharedPreferences(PREFS,0).getAll(),id);}
    private static JSONObject read(Map<String,?> stored,String id)throws IOException {Object raw=stored.get(id);if(raw!=null&&!(raw instanceof String))throw new IOException("Cannot read stored object settings");String text=raw==null?"{}":(String)raw;try{return validated(new JSONObject(text));}catch(Exception e){if(e instanceof IOException)throw(IOException)e;throw new IOException("Cannot read stored object settings",e);}}
    private static String countKey(String id){return id+":native_object_count";}
    private static void rollback(SharedPreferences prefs,String id,Map<String,?> previous){SharedPreferences.Editor edit=prefs.edit();restore(edit,id,previous.get(id));restore(edit,countKey(id),previous.get(countKey(id)));edit.apply();}
    @SuppressWarnings("unchecked")
    private static void restore(SharedPreferences.Editor edit,String key,Object value){if(value==null)edit.remove(key);else if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof java.util.Set)edit.putStringSet(key,(java.util.Set<String>)value);else edit.remove(key);}
    private static JSONObject validated(JSONObject changes)throws IOException {
        if(changes==null)throw new IOException("Missing typed object settings");JSONObject copy=PresetJson.copy(changes);java.util.Iterator<String> keys=copy.keys();while(keys.hasNext()){String key=keys.next();if(!key.matches("[a-z][a-z0-9_]*")||reserved(key))throw new IOException("Object changes must contain process option keys, not preset identity fields");}return copy;
    }
    private static boolean reserved(String key){switch(key){case "name":case "type":case "from":case "instantiation":case "inherits":case "setting_id":case "print_settings_id":case "filament_settings_id":case "printer_settings_id":case "version":return true;default:return false;}}
    private static void requireSingle(int count)throws IOException {if(count!=1)throw new IOException(L.t("Object overrides currently require exactly one native object; multiple-object models are not supported by this path"));}
}
