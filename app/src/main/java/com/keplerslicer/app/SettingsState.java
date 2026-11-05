package com.keplerslicer.app;

import android.content.Context;
import com.keplerslicer.app.settings.PresetRepository;
import com.keplerslicer.engine.ExperimentalSlicer;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Immutable profile snapshots for the existing engine JSON loader. No native configuration conversion. */
final class SettingsState {
    static final String PREFS="kepler_settings_v2";
    static final String[] KINDS={"printer","process","filament"};
    private static PresetRepository repository;
    /** Repository if it was already opened in this process, else null (never blocks on disk). */
    static synchronized PresetRepository peekRepository(){return repository;}
    static synchronized PresetRepository repository(Context context)throws IOException {
        if(repository==null)repository=new PresetRepository(context.getApplicationContext());return repository;
    }
    static String baseId(Context c,String kind)throws IOException {
        if(kind.equals("printer")){PrinterProfileCatalog.Profile p=PrinterProfileCatalog.loadSelection(c);if(p==null||p.imported)throw new IOException(L.t("Choose a bundled printer and nozzle first"));return PresetRepository.systemId(kind,p.vendor,p.name);}
        PrintProfileCatalog.Profile p=PrintProfileCatalog.loadSelection(c,kind);if(p==null)throw new IOException("Choose a "+kind+" preset first");return PresetRepository.systemId(kind,p.vendor,p.name);
    }
    static String activeId(Context c,String kind)throws IOException {
        String base=baseId(c,kind),id=c.getSharedPreferences(PREFS,0).getString(kind,base);
        if(id==null||id.equals(base))return base;
        PresetRepository.Preset ancestor=repository(c).systemAncestor(id);
        // A selection made by an existing picker supersedes a user preset of another base.
        return ancestor.id.equals(base)?id:base;
    }
    static void select(Context c,String id)throws IOException {
        PresetRepository repo=repository(c);PresetRepository.Preset p=repo.load(id),base=repo.systemAncestor(id);
        final PrinterProfileCatalog.Profile preparedPrinter;
        final PrinterProfileCatalog.Profile selectedPrinter;
        final PrintProfileCatalog.Profile selectedProcess;
        if(p.kind.equals("printer")){
            PrinterProfileCatalog.Profile selected=null;for(PrinterProfileCatalog.Profile row:PrinterProfileCatalog.load(c))if(row.path.equals(base.path)){selected=row;break;}
            if(selected==null)selected=PrinterProfileCatalog.systemProfile(base);
            selectedPrinter=selected;selectedProcess=null;preparedPrinter=null;
        }else{
            preparedPrinter=PrinterProfileCatalog.loadSelection(c);if(preparedPrinter==null||preparedPrinter.imported)throw new IOException(L.t("Choose a printer first"));
            PrintProfileCatalog.Profile selected=null;for(PrintProfileCatalog.Profile row:PrintProfileCatalog.load(c,p.kind))if(row.path.equals(base.path)){selected=row;break;}
            if(selected==null)throw new IOException("This template is not an instantiated preset");
            selectedPrinter=null;selectedProcess=selected;
        }
        // All checks and both writes share the selection lock; it is reentrant for the inner clear.
        ProcessOverridesStore.changeSelection(c,false,()->{
            PrinterProfileCatalog.Profile currentPrinter=PrinterProfileCatalog.loadSelection(c);
            if(preparedPrinter!=null&&!samePrinter(preparedPrinter,currentPrinter))throw new IOException("Printer selection changed; reopen the preset picker");
            String oldBase=currentBaseId(c,p.kind,currentPrinter),active=oldBase;
            if(oldBase!=null){String saved=c.getSharedPreferences(PREFS,0).getString(p.kind,oldBase);if(saved!=null&&!saved.equals(oldBase)){try{if(repo.systemAncestor(saved).id.equals(oldBase))active=saved;}catch(IOException ignored){/* stale user preset: fall back to selected system base */}}}
            if(id.equals(active))return;
            boolean clearLegacy=!base.id.equals(oldBase);
            android.content.SharedPreferences basePrefs=c.getSharedPreferences(p.kind.equals("printer")?StorageNames.PROFILE_SELECTION:StorageNames.PRINT_PROFILES,0),settings=c.getSharedPreferences(PREFS,0),legacy=c.getSharedPreferences("kepler_process_overrides_v1",0);
            String[] baseKeys=p.kind.equals("printer")?new String[]{"name","vendor","path","model","variant","imported"}:new String[]{p.kind+"_vendor",p.kind+"_name",p.kind+"_path",p.kind+"_printer"};
            Map<String,?> baseBefore=basePrefs.getAll(),settingsBefore=settings.getAll(),legacyBefore=legacy.getAll();
            try{ProcessOverridesStore.changeSelection(c,clearLegacy,()->{
                if(selectedPrinter!=null)PrinterProfileCatalog.saveSelectionChecked(c,selectedPrinter);else PrintProfileCatalog.saveSelection(c,selectedProcess,currentPrinter);
                saveSelection(c,p.kind,id);
            });}catch(IOException|RuntimeException failure){
                try{restoreKeys(basePrefs,baseBefore,baseKeys);}catch(IOException restore){failure.addSuppressed(restore);}
                try{restoreKeys(settings,settingsBefore,new String[]{p.kind});}catch(IOException restore){failure.addSuppressed(restore);}
                if(clearLegacy)try{restoreAll(legacy,legacyBefore);}catch(IOException restore){failure.addSuppressed(restore);}
                throw failure;
            }
        });
    }
    private static boolean samePrinter(PrinterProfileCatalog.Profile a,PrinterProfileCatalog.Profile b){return a!=null&&b!=null&&a.imported==b.imported&&Objects.equals(a.vendor,b.vendor)&&Objects.equals(a.name,b.name)&&Objects.equals(a.path,b.path);}
    private static String currentBaseId(Context c,String kind,PrinterProfileCatalog.Profile printer)throws IOException {
        if(printer==null||printer.imported)return null;
        if(kind.equals("printer"))return PresetRepository.systemId(kind,printer.vendor,printer.name);
        PrintProfileCatalog.Profile current=PrintProfileCatalog.loadSelection(c,kind);
        return current==null?null:PresetRepository.systemId(kind,current.vendor,current.name);
    }
    /** Restore only keys this transaction touched, leaving unrelated preferences intact. */
    static void restoreKeys(android.content.SharedPreferences prefs,Map<String,?> previous,String[] keys)throws IOException {
        android.content.SharedPreferences.Editor edit=prefs.edit();for(String key:keys)restoreValue(edit,key,previous.get(key));
        if(!edit.commit())throw new IOException("Could not restore previous preference values");
    }
    private static void restoreAll(android.content.SharedPreferences prefs,Map<String,?> previous)throws IOException {
        android.content.SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> entry:previous.entrySet())restoreValue(edit,entry.getKey(),entry.getValue());
        if(!edit.commit())throw new IOException("Could not restore previous legacy overrides");
    }
    private static void restoreValue(android.content.SharedPreferences.Editor edit,String key,Object value)throws IOException {
        if(value==null)edit.remove(key);else if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Set){Set<String> set=new HashSet<>();for(Object item:(Set<?>)value){if(!(item instanceof String))throw new IOException("Invalid stored preference set");set.add((String)item);}edit.putStringSet(key,set);}else throw new IOException("Unsupported stored preference type: "+key);
    }
    private static void saveSelection(Context c,String kind,String id)throws IOException {android.content.SharedPreferences prefs=c.getSharedPreferences(PREFS,0);Map<String,?> before=prefs.getAll();if(!prefs.edit().putString(kind,id).commit()){try{restoreKeys(prefs,before,new String[]{kind});}catch(IOException restore){IOException error=new IOException("Cannot save active preset; previous value could not be restored");error.addSuppressed(restore);throw error;}throw new IOException("Cannot save active preset");}}
    static final class Overlay {
        final String kind,path,name;
        // The exposed copy is for callers inspecting a snapshot. Installation and hashing
        // always use the serialized immutable copy, so a JSON mutation cannot poison a cache.
        final JSONObject effective;private final String frozen;
        Overlay(String kind,String path,String name,JSONObject values)throws IOException {requireOverlayPath(kind,path);if(name==null||name.isEmpty()||name.indexOf('\0')>=0)throw new IOException("Invalid preset name");this.kind=kind;this.path=path;this.name=name;effective=copy(values);frozen=effective.toString();}
        JSONObject values()throws IOException {try{return new JSONObject(frozen);}catch(Exception e){throw new IOException(e);}}
    }
    static final class Snapshot {
        final Map<String,Overlay> overlays;final String fingerprint;
        Snapshot(Map<String,Overlay> source)throws IOException {Map<String,Overlay> frozen=new LinkedHashMap<>();for(Map.Entry<String,Overlay> e:source.entrySet()){Overlay o=e.getValue();if(o==null||!e.getKey().equals(o.kind))throw new IOException("Overlay kind does not match its key");frozen.put(e.getKey(),new Overlay(o.kind,o.path,o.name,o.values()));}overlays=Collections.unmodifiableMap(frozen);JSONObject hash=new JSONObject();try{hash.put("format","kepler-settings-snapshot-v2");hash.put("revision",PresetRepository.REVISION);JSONObject profiles=new JSONObject();for(Map.Entry<String,Overlay> e:overlays.entrySet()){JSONObject row=new JSONObject();row.put("path",e.getValue().path);row.put("name",e.getValue().name);row.put("values",e.getValue().values());profiles.put(e.getKey(),row);}hash.put("profiles",profiles);}catch(Exception e){throw new IOException(e);}fingerprint=sha(canonical(hash));}
        Snapshot replacing(String kind,PresetRepository.Preset base,JSONObject effective)throws IOException {if(base==null||!base.system||!kind.equals(base.kind))throw new IOException("Expected the original system ancestor for this preset kind");Map<String,Overlay> next=new LinkedHashMap<>(overlays);next.put(kind,new Overlay(kind,base.path,base.name,effective));return new Snapshot(next);}
        Snapshot mergingProcess(JSONObject changes)throws IOException {if(changes.length()==0)return this;Overlay process=overlays.get("process");if(process==null)throw new IOException("Select a process preset before applying object settings");Map<String,Overlay> next=new LinkedHashMap<>(overlays);next.put("process",new Overlay(process.kind,process.path,process.name,com.keplerslicer.app.settings.PresetJson.merge(process.values(),changes)));return new Snapshot(next);}
        Set<String> vendors()throws IOException {Set<String> out=new LinkedHashSet<>();for(Overlay overlay:overlays.values())out.add(requireOverlayPath(overlay.kind,overlay.path));return Collections.unmodifiableSet(out);}
        /** Only private copies of original preset files are replaced, using the engine's original JSON representation. */
        void install(File data)throws IOException {
            File system=new File(data,"system").getCanonicalFile();
            for(Overlay overlay:overlays.values()){
                File file=new File(system,overlay.path).getCanonicalFile();
                if(!file.getPath().startsWith(system.getPath()+File.separator)||!file.isFile())throw new IOException("Private preset base is missing");
                JSONObject original=read(file),document=overlay.values();
                // Keep the selected upstream identity. Options and unknown fields are preserved.
                for(String key:new String[]{"name","type","from","instantiation","inherits","setting_id","print_settings_id","filament_settings_id","printer_settings_id"}){
                    try{if(original.has(key))document.put(key,original.get(key));else document.remove(key);}catch(Exception e){throw new IOException(e);}
                }
                try(FileOutputStream out=new FileOutputStream(file)){out.write(document.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
            }
        }
    }
    static Snapshot capture(Context c)throws IOException {
        // Existing selection writers acquire the process lock. Queries outside that lock
        // use a repeatable-read check, avoiding a new SettingsState->process lock order.
        for(int attempt=0;attempt<3;attempt++){Map<String,String> selected=selectedIds(c);Map<String,Overlay> out=new LinkedHashMap<>();for(Map.Entry<String,String> e:selected.entrySet()){PresetRepository.Preset base=repository(c).systemAncestor(e.getValue());out.put(e.getKey(),new Overlay(e.getKey(),base.path,base.name,repository(c).resolve(e.getValue())));}Snapshot first=new Snapshot(out);if(!selected.equals(selectedIds(c)))continue;Map<String,Overlay> confirmed=new LinkedHashMap<>();for(Map.Entry<String,String> e:selected.entrySet()){PresetRepository.Preset base=repository(c).systemAncestor(e.getValue());confirmed.put(e.getKey(),new Overlay(e.getKey(),base.path,base.name,repository(c).resolve(e.getValue())));}Snapshot second=new Snapshot(confirmed);if(first.fingerprint.equals(second.fingerprint)&&selected.equals(selectedIds(c)))return first;}
        throw new IOException("Profiles changed while preparing settings; retry the operation");
    }
    private static Map<String,String> selectedIds(Context c)throws IOException {Map<String,String> ids=new LinkedHashMap<>();for(String kind:KINDS){String current;try{current=baseId(c,kind);}catch(IOException absent){continue;}String selected=c.getSharedPreferences(PREFS,0).getString(kind,current);if(selected==null||selected.equals(current))ids.put(kind,current);else{PresetRepository.Preset base=repository(c).systemAncestor(selected);ids.put(kind,base.id.equals(current)?selected:current);}}return ids;}
    static String requireOverlayPath(String kind,String path)throws IOException {String folder=kind.equals("printer")?"machine":kind.equals("process")?"process":kind.equals("filament")?"filament":null;if(folder==null||path==null||path.indexOf('\\')>=0||path.indexOf('\0')>=0||path.indexOf(':')>=0||!path.endsWith(".json"))throw new IOException("Invalid preset overlay path");String[] parts=path.split("/",-1);if(parts.length<3||!parts[1].equals(folder))throw new IOException("Invalid preset overlay kind/path");for(String part:parts)if(part.isEmpty()||part.equals(".")||part.equals("..")||part.indexOf('\n')>=0||part.indexOf('\r')>=0)throw new IOException("Invalid preset overlay path");return parts[0];}
    static void validate(Context c,Snapshot snapshot)throws IOException {
        Overlay p=snapshot.overlays.get("printer"),q=snapshot.overlays.get("process"),f=snapshot.overlays.get("filament");
        if(p==null||q==null||f==null)throw new IOException("Select printer, process and filament before applying settings");
        String pv=requireOverlayPath(p.kind,p.path),qv=requireOverlayPath(q.kind,q.path),fv=requireOverlayPath(f.kind,f.path);
        CompatibilityResources r=CompatibilityResources.prepareTriple(c,pv,qv,fv,snapshot);
        // The same immutable names/paths used to install the overlay are validated.
        // Reading live selection here could validate another preset after a UI selection.
        try{ExperimentalSlicer.validateProcessSettings(pv,qv,fv,p.name,q.name,f.name,r.resources.getAbsolutePath(),r.data.getAbsolutePath(),"{}");}
        catch(Throwable e){throw new IOException(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(),e);}
    }
    static JSONObject copy(JSONObject value)throws IOException {try{return new JSONObject(value.toString());}catch(Exception e){throw new IOException(e);}}
    private static JSONObject read(File file)throws IOException {try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new JSONObject(out.toString("UTF-8"));}catch(org.json.JSONException e){throw new IOException(e);}}
    private static String canonical(Object value)throws IOException {
        if(value instanceof JSONObject){JSONObject o=(JSONObject)value;List<String> keys=new ArrayList<>();Iterator<String> it=o.keys();while(it.hasNext())keys.add(it.next());Collections.sort(keys);StringBuilder b=new StringBuilder("{");for(String k:keys){if(b.length()>1)b.append(',');b.append(JSONObject.quote(k)).append(':').append(canonical(o.opt(k)));}return b.append('}').toString();}
        if(value instanceof JSONArray){JSONArray a=(JSONArray)value;StringBuilder b=new StringBuilder("[");for(int i=0;i<a.length();i++){if(i>0)b.append(',');b.append(canonical(a.opt(i)));}return b.append(']').toString();}
        return value==null||value==JSONObject.NULL?"null":value instanceof String?JSONObject.quote((String)value):value.toString();
    }
    private static String sha(String value)throws IOException {try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}catch(Exception e){throw new IOException(e);}}
}
