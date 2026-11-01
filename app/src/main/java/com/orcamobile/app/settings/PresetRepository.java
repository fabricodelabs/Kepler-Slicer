package com.orcamobile.app.settings;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Local system/user preset repository. Call disk operations on a worker thread. */
public final class PresetRepository implements Closeable {
    public static final String PROCESS="process", FILAMENT="filament", PRINTER="printer";
    public static final String REVISION="6d2869c430284714f0a22570c38d3b359965a802", UPSTREAM_VERSION="02.08.01.55";
    private static final Object LOCK = new Object();
    private final File usersFile;
    private final SystemSource systemSource;
    private final Map<String, SystemRecord> systems = new LinkedHashMap<>();
    private final Map<String, List<SystemRecord>> systemNames = new LinkedHashMap<>();

    /** A descriptor returned by list has null source/effective. load supplies detached JSON copies. */
    public static final class Preset {
        public final String id,name,kind,vendor,parentId,path;
        public final boolean system;
        public final JSONObject source,effective;
        private Preset(String id,String name,String kind,String vendor,boolean system,String parentId,String path,JSONObject source,JSONObject effective) {
            this.id=id;this.name=name;this.kind=kind;this.vendor=vendor;this.system=system;this.parentId=parentId;this.path=path;this.source=source;this.effective=effective;
        }
    }
    public static final class SystemRecord {
        public final String kind,vendor,name,path,parent;
        public final boolean instantiated;
        public SystemRecord(String kind,String vendor,String name,String path,String parent,boolean instantiated) {
            requireKind(kind);this.kind=kind;this.vendor=vendor;this.name=name;this.path=path;this.parent=parent==null?"":parent;this.instantiated=instantiated;
        }
    }
    /** Testable source boundary; production reads untouched JSON entries in the original asset ZIP. */
    public interface SystemSource extends Closeable {
        List<SystemRecord> entries() throws IOException;
        JSONObject read(String path) throws IOException;
    }
    public static final class Draft {
        public final String baseId;
        private JSONObject initial,edited;
        private String baseline;
        private Draft(String baseId,JSONObject source) throws IOException {this.baseId=baseId;replace(source);}
        private void replace(JSONObject source) throws IOException {initial=PresetJson.copy(source);edited=PresetJson.copy(source);baseline=PresetJson.fingerprint(source);}
        public JSONObject source() throws IOException {return PresetJson.copy(edited);}
        public void set(String key,Object value) throws IOException {if(key==null||key.isEmpty())throw new IOException("Setting key is empty");try {edited.put(key,value==null?JSONObject.NULL:value);}catch(JSONException e){throw new IOException(e);}}
        public void remove(String key){edited.remove(key);}
        public boolean dirty() throws IOException {return !PresetJson.equal(initial,edited);}
    }
    public PresetRepository(Context context) throws IOException {
        this(new File(context.getApplicationContext().getFilesDir(),"kepler-presets-v1"),new AssetSource(context.getApplicationContext()));
    }
    public PresetRepository(File userDirectory,SystemSource source) throws IOException {
        if(!userDirectory.isDirectory()&&!userDirectory.mkdirs())throw new IOException("Cannot create preset directory");usersFile=new File(userDirectory,"presets.json");systemSource=source;
        try {for(SystemRecord record:source.entries()){String id=systemId(record.kind,record.vendor,record.name);if(systems.put(id,record)!=null)throw new IOException("Duplicate system preset: "+record.name);systemNames.computeIfAbsent(record.kind+"\0"+record.name,k->new ArrayList<>()).add(record);}}
        catch(IOException|RuntimeException e){try{source.close();}catch(IOException ignored){}throw e;}
    }
    public static String systemId(String kind,String vendor,String name) throws IOException {requireKind(kind);if(vendor==null||vendor.isEmpty()||name==null||name.isEmpty())throw new IOException("Preset identity is incomplete");return "system:"+kind+":"+PresetJson.sha256(vendor+"\0"+name);}
    public List<Preset> list(String kind) throws IOException {
        requireKind(kind);synchronized(LOCK){Map<String,JSONObject> users=readUsers();List<Preset> out=new ArrayList<>();for(Map.Entry<String,SystemRecord> e:systems.entrySet())if(e.getValue().kind.equals(kind)&&e.getValue().instantiated)out.add(descriptor(e.getKey(),users));for(String id:users.keySet())if(kind.equals(users.get(id).optString("kind")))out.add(descriptor(id,users));out.sort(Comparator.comparing((Preset p)->p.system).thenComparing(p->p.name,String.CASE_INSENSITIVE_ORDER).thenComparing(p->p.vendor));return Collections.unmodifiableList(out);}
    }
    public Preset load(String id) throws IOException {synchronized(LOCK){Map<String,JSONObject> users=readUsers();Preset d=descriptor(id,users);return new Preset(d.id,d.name,d.kind,d.vendor,d.system,parent(id,users),d.path,source(id,users),resolve(id,users,new HashSet<>()));}}
    public JSONObject resolve(String id) throws IOException {synchronized(LOCK){return resolve(id,readUsers(),new HashSet<>());}}
    /** Closest original system preset in the chain, suitable as an isolated native-loader base. */
    public Preset systemAncestor(String id) throws IOException {synchronized(LOCK){Map<String,JSONObject> users=readUsers();Set<String> seen=new HashSet<>();while(id!=null&&!id.isEmpty()){if(!seen.add(id))throw new IOException("Preset inheritance cycle");Preset p=descriptor(id,users);if(p.system)return load(id);id=parent(id,users);}throw new IOException("Preset has no bundled system ancestor");}}
    public Draft beginDraft(String id) throws IOException {return new Draft(id,load(id).source);}
    public void reset(Draft draft) throws IOException {draft.replace(load(draft.baseId).source);}
    public JSONObject resolveDraft(Draft draft) throws IOException {synchronized(LOCK){Map<String,JSONObject> users=readUsers();String parent=parent(draft.baseId,users);JSONObject base=parent==null?new JSONObject():resolve(parent,users,new HashSet<>());return PresetJson.merge(base,draft.edited);}}
    public Preset save(Draft draft) throws IOException {
        synchronized(LOCK){Map<String,JSONObject> users=readUsers();Preset old=descriptor(draft.baseId,users);if(old.system)throw new IOException("System presets are read-only; use Save as");if(!draft.baseline.equals(PresetJson.fingerprint(source(old.id,users))))throw new IOException("Preset changed since this draft was opened");JSONObject record=PresetJson.copy(users.get(old.id));put(record,"source",normalize(draft.edited,old.kind,old.name,parentName(old.parentId,users)));users.put(old.id,record);validateAll(users);writeUsers(users);Preset saved=load(old.id);draft.replace(saved.source);return saved;}
    }
    public Preset saveAs(Draft draft,String name) throws IOException {
        synchronized(LOCK){Map<String,JSONObject> users=readUsers();Preset old=descriptor(draft.baseId,users);return create(old.kind,old.vendor,name,old.id,draft.edited,users);}
    }
    public Preset saveUser(String kind,String vendor,String name,String parentId,JSONObject original) throws IOException {
        synchronized(LOCK){Map<String,JSONObject> users=readUsers();if(parentId==null||parentId.isEmpty()){String inherited=original.optString("inherits","");if(!inherited.isEmpty())parentId=findParent(kind,vendor,inherited,users);}return create(kind,vendor,name,parentId,original,users);}
    }
    public void deleteUser(String id) throws IOException {
        synchronized(LOCK){Map<String,JSONObject> users=readUsers();Preset p=descriptor(id,users);if(p.system)throw new IOException("System presets cannot be deleted");for(String child:users.keySet())if(id.equals(parent(child,users)))throw new IOException("This preset is inherited by another user preset");users.remove(id);writeUsers(users);}
    }
    public JSONObject exportPreset(String id) throws IOException {synchronized(LOCK){Map<String,JSONObject> users=readUsers();Preset p=descriptor(id,users);JSONObject result=normalize(source(id,users),p.kind,p.name,parentName(parent(id,users),users));if(!result.has("version"))put(result,"version",UPSTREAM_VERSION);String identity=p.kind.equals(PROCESS)?"print_settings_id":p.kind.equals(FILAMENT)?"filament_settings_id":"printer_settings_id";put(result,identity,p.kind.equals(FILAMENT)?new JSONArray().put(p.name):p.name);return result;}}
    /** Explicit Kepler exchange wrapper. This JSON object is not advertised as desktop Orca's ZIP format. */
    public JSONObject exportBundle(List<String> ids) throws IOException {synchronized(LOCK){JSONArray out=new JSONArray();for(String id:withUserAncestors(ids,readUsers())){Preset p=load(id);JSONObject entry=new JSONObject();put(entry,"kind",p.kind);put(entry,"vendor",p.vendor);put(entry,"source",exportPreset(id));out.put(entry);}JSONObject root=new JSONObject();put(root,"format","kepler-presets-v1");put(root,"upstream_revision",REVISION);put(root,"presets",out);return root;}}
    /** Plain ZIP of original-format preset JSON, accepted by upstream PresetBundle::import_presets. */
    public void exportOrcaZip(List<String> ids,OutputStream target) throws IOException {
        synchronized(LOCK){try(ZipOutputStream zip=new ZipOutputStream(target)){int n=0;for(String id:withUserAncestors(ids,readUsers())){Preset p=load(id);String path=(p.kind.equals(PRINTER)?"machine":p.kind)+"/"+String.format(java.util.Locale.ROOT,"%04d",++n)+"-"+safeFilename(p.name)+".json";zip.putNextEntry(new ZipEntry(path));zip.write(exportPreset(id).toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}}
    }
    public List<Preset> importBundle(JSONObject document) throws IOException {
        synchronized(LOCK){List<ImportRecord> pending=new ArrayList<>();if("kepler-presets-v1".equals(document.optString("format"))){JSONArray records=document.optJSONArray("presets");if(records==null)throw new IOException("Preset array is missing");for(int i=0;i<records.length();i++){JSONObject row=records.optJSONObject(i);if(row==null||row.optJSONObject("source")==null)throw new IOException("Invalid preset entry");pending.add(new ImportRecord(row.optString("kind"),row.optString("vendor"),row.optJSONObject("source")));}}else pending.add(new ImportRecord(kindForType(document.optString("type")),"",document));return importRecords(pending);}
    }
    /** Transactional import: one malformed/missing/cyclic parent prevents every write. No overwrite by name. */
    public List<Preset> importOrcaZip(File archive) throws IOException {
        List<ImportRecord> pending=new ArrayList<>();try(ZipFile zip=new ZipFile(archive)){Set<String> names=new HashSet<>();java.util.Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){ZipEntry entry=entries.nextElement();if(entry.isDirectory())continue;if(!names.add(entry.getName()))throw new IOException("Duplicate ZIP entry");String file=entry.getName().toLowerCase(java.util.Locale.ROOT);if(!file.endsWith(".json"))throw new IOException("This bundle contains companion files; only JSON-only preset ZIP import is supported. The original archive has not been changed");try(InputStream in=zip.getInputStream(entry)){JSONObject source=PresetJson.read(in);if(file.endsWith("bundle_structure.json")){JSONObject assets=source.optJSONObject("printer_asset");if(assets!=null&&assets.length()>0)throw new IOException("This bundle contains companion bed assets; importing those assets is not supported yet. The original archive has not been changed");continue;}if(file.endsWith("bundle_metadata.json"))continue;String type=source.optString("type");if(type.equals("process")||type.equals("filament")||type.equals("machine"))pending.add(new ImportRecord(kindForType(type),"",source));}}}if(pending.isEmpty())throw new IOException("No Orca preset JSON files in archive");synchronized(LOCK){return importRecords(pending);}
    }
    private static final class ImportRecord {final String kind,vendor;final JSONObject source;ImportRecord(String kind,String vendor,JSONObject source){this.kind=kind;this.vendor=vendor;this.source=source;}}
    private List<Preset> importRecords(List<ImportRecord> pending) throws IOException {
        Map<String,JSONObject> users=readUsers();List<String> created=new ArrayList<>();for(ImportRecord row:pending){requireKind(row.kind);String name=validName(row.source.optString("name"));String vendor=row.vendor==null?"":row.vendor;requireUnique(row.kind,vendor,name,users);String id="user:"+UUID.randomUUID();JSONObject record=new JSONObject();put(record,"kind",row.kind);put(record,"vendor",vendor);put(record,"source",PresetJson.copy(row.source));put(record,"parent_id","");users.put(id,record);created.add(id);}
        for(String id:created){JSONObject record=users.get(id),source=record.optJSONObject("source");String inherited=source.optString("inherits","");String parent=inherited.isEmpty()?null:findParent(record.optString("kind"),record.optString("vendor"),inherited,users);put(record,"parent_id",parent==null?"":parent);put(record,"source",normalize(source,record.optString("kind"),source.optString("name"),parentName(parent,users)));}
        validateAll(users);writeUsers(users);List<Preset> result=new ArrayList<>();for(String id:created)result.add(load(id));return Collections.unmodifiableList(result);
    }
    private Preset create(String kind,String vendor,String name,String parentId,JSONObject original,Map<String,JSONObject> users) throws IOException {
        requireKind(kind);name=validName(name);if(vendor==null)vendor="";requireUnique(kind,vendor,name,users);if(parentId!=null&&!parentId.isEmpty()&&!kind.equals(descriptor(parentId,users).kind))throw new IOException("Preset kinds cannot be mixed by inheritance");String id="user:"+UUID.randomUUID();JSONObject record=new JSONObject();put(record,"kind",kind);put(record,"vendor",vendor);put(record,"parent_id",parentId==null?"":parentId);put(record,"source",normalize(original,kind,name,parentName(parentId,users)));users.put(id,record);validateAll(users);writeUsers(users);return load(id);
    }
    private void requireUnique(String kind,String vendor,String name,Map<String,JSONObject> users) throws IOException {for(String id:users.keySet()){Preset p=descriptor(id,users);if(p.kind.equals(kind)&&p.name.equals(name))throw new IOException("A user preset with this name already exists");}for(SystemRecord p:systems.values())if(p.kind.equals(kind)&&p.name.equals(name))throw new IOException("A system preset already uses this name");}
    private static JSONObject normalize(JSONObject source,String kind,String name,String inherited) throws IOException {JSONObject copy=PresetJson.copy(source);put(copy,"type",kind.equals(PRINTER)?"machine":kind);put(copy,"name",name);put(copy,"from","user");put(copy,"instantiation","true");if(inherited==null||inherited.isEmpty())copy.remove("inherits");else put(copy,"inherits",inherited);return copy;}
    private String parentName(String id,Map<String,JSONObject> users) throws IOException {return id==null||id.isEmpty()?null:descriptor(id,users).name;}
    private Preset descriptor(String id,Map<String,JSONObject> users) throws IOException {
        SystemRecord system=systems.get(id);if(system!=null)return new Preset(id,system.name,system.kind,system.vendor,true,null,system.path,null,null);
        JSONObject user=users.get(id);if(user==null||user.optJSONObject("source")==null)throw new IOException("Preset not found: "+id);JSONObject source=user.optJSONObject("source");return new Preset(id,source.optString("name"),user.optString("kind"),user.optString("vendor"),false,emptyToNull(user.optString("parent_id")),"",null,null);
    }
    private JSONObject source(String id,Map<String,JSONObject> users) throws IOException {SystemRecord system=systems.get(id);if(system!=null)return systemSource.read(system.path);JSONObject row=users.get(id);if(row==null||row.optJSONObject("source")==null)throw new IOException("Preset not found");return PresetJson.copy(row.optJSONObject("source"));}
    private String parent(String id,Map<String,JSONObject> users) throws IOException {SystemRecord s=systems.get(id);if(s!=null)return s.parent.isEmpty()?null:findParent(s.kind,s.vendor,s.parent,users);JSONObject row=users.get(id);if(row==null)throw new IOException("Preset not found");return emptyToNull(row.optString("parent_id"));}
    private String findParent(String kind,String vendor,String name,Map<String,JSONObject> users) throws IOException {
        List<String> matches=new ArrayList<>();for(String id:users.keySet()){JSONObject row=users.get(id);if(kind.equals(row.optString("kind"))&&name.equals(row.optJSONObject("source").optString("name")))matches.add(id);}if(matches.size()==1)return matches.get(0);if(matches.size()>1)throw new IOException("Ambiguous user parent: "+name);
        List<SystemRecord> found=systemNames.get(kind+"\0"+name);if(found==null||found.isEmpty())throw new IOException("Missing parent preset: "+name);for(SystemRecord s:found)if(s.vendor.equals(vendor))return systemId(kind,s.vendor,s.name);if(kind.equals(FILAMENT))for(SystemRecord s:found)if(s.vendor.equals("OrcaFilamentLibrary"))return systemId(kind,s.vendor,s.name);if(found.size()==1)return systemId(kind,found.get(0).vendor,name);throw new IOException("Ambiguous system parent; vendor is required: "+name);
    }
    private JSONObject resolve(String id,Map<String,JSONObject> users,Set<String> active) throws IOException {
        if(active.size()>128||!active.add(id))throw new IOException("Preset inheritance cycle or excessive depth");try{JSONObject own=source(id,users);String parent=parent(id,users);return parent==null?own:PresetJson.merge(resolve(parent,users,active),own);}finally{active.remove(id);}
    }
    private void validateAll(Map<String,JSONObject> users) throws IOException {for(String id:users.keySet()){JSONObject row=users.get(id);requireKind(row.optString("kind"));validName(row.optJSONObject("source").optString("name"));String p=parent(id,users);if(p!=null&&!descriptor(id,users).kind.equals(descriptor(p,users).kind))throw new IOException("Preset kinds cannot be mixed by inheritance");resolve(id,users,new HashSet<>());}}
    private List<String> withUserAncestors(List<String> ids,Map<String,JSONObject> users) throws IOException {List<String> out=new ArrayList<>();Set<String> visiting=new HashSet<>();for(String id:ids)collectExport(id,users,visiting,out);return out;}
    private void collectExport(String id,Map<String,JSONObject> users,Set<String> visiting,List<String> out) throws IOException {if(out.contains(id))return;if(!visiting.add(id))throw new IOException("Preset inheritance cycle");String p=parent(id,users);if(p!=null&&!descriptor(p,users).system)collectExport(p,users,visiting,out);out.add(id);visiting.remove(id);}
    private Map<String,JSONObject> readUsers() throws IOException {
        Map<String,JSONObject> out=new LinkedHashMap<>();if(!usersFile.isFile())return out;try(InputStream in=new FileInputStream(usersFile)){JSONObject root=PresetJson.read(in);if(root.optInt("version")!=1||root.optJSONObject("profiles")==null)throw new IOException("Unsupported preset store");JSONObject records=root.optJSONObject("profiles");for(Iterator<String> i=records.keys();i.hasNext();){String id=i.next();if(!id.startsWith("user:")||records.optJSONObject(id)==null)throw new IOException("Invalid user preset record");out.put(id,records.optJSONObject(id));}return out;}
    }
    private void writeUsers(Map<String,JSONObject> users) throws IOException {JSONObject records=new JSONObject();for(Map.Entry<String,JSONObject> e:users.entrySet())put(records,e.getKey(),e.getValue());JSONObject root=new JSONObject();put(root,"version",1);put(root,"profiles",records);atomicWrite(usersFile,root.toString().getBytes(StandardCharsets.UTF_8));}
    private static void atomicWrite(File target,byte[] bytes) throws IOException {File staging=File.createTempFile(".preset-",".tmp",target.getParentFile());boolean committed=false;try{try(FileOutputStream out=new FileOutputStream(staging)){out.write(bytes);out.getFD().sync();}try{Files.move(staging.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(java.nio.file.AtomicMoveNotSupportedException e){Files.move(staging.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}committed=true;}finally{if(!committed)Files.deleteIfExists(staging.toPath());}}
    private static String emptyToNull(String s){return s==null||s.isEmpty()?null:s;}
    private static String validName(String name) throws IOException {if(name==null||name.trim().isEmpty()||name.length()>240||name.indexOf('/')>=0||name.indexOf('\\')>=0||name.indexOf('\0')>=0||name.equals(".")||name.equals(".."))throw new IOException("Invalid preset name");return name;}
    private static String safeFilename(String name){return name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_");}
    private static String kindForType(String type) throws IOException {if(type.equals("machine"))return PRINTER;if(type.equals(PROCESS)||type.equals(FILAMENT))return type;throw new IOException("Expected an Orca machine, process or filament preset");}
    private static void requireKind(String kind){if(!PROCESS.equals(kind)&&!FILAMENT.equals(kind)&&!PRINTER.equals(kind))throw new IllegalArgumentException("Unknown preset kind: "+kind);}
    private static void put(JSONObject object,String key,Object value) throws IOException {try{object.put(key,value);}catch(JSONException e){throw new IOException(e);}}
    @Override public void close() throws IOException {systemSource.close();}

    private static final class AssetSource implements SystemSource {
        private final ZipFile zip;private final List<SystemRecord> entries=new ArrayList<>();
        AssetSource(Context context) throws IOException {
            JSONObject index;try(InputStream in=context.getAssets().open("orca_preset_index.json")){index=PresetJson.read(in);}if(index.optInt("version")!=1||!REVISION.equals(index.optString("revision")))throw new IOException("Preset index revision mismatch");String hash=index.optString("archive_sha256");if(!hash.matches("[a-f0-9]{64}"))throw new IOException("Invalid preset archive digest");File archive=new File(context.getCacheDir(),"orca-preset-source-"+hash.substring(0,16)+".zip");if(!archive.isFile()||archive.length()!=index.optLong("archive_size")){File temp=File.createTempFile(".orca-preset-",".zip",context.getCacheDir());boolean moved=false;try{try(InputStream in=context.getAssets().open("orca_profiles.zip");FileOutputStream out=new FileOutputStream(temp)){byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);out.getFD().sync();}Files.move(temp.toPath(),archive.toPath(),StandardCopyOption.REPLACE_EXISTING);moved=true;}finally{if(!moved)Files.deleteIfExists(temp.toPath());}}
            zip=new ZipFile(archive);JSONArray rows=index.optJSONArray("profiles");if(rows==null)throw new IOException("Preset index entries missing");for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)throw new IOException("Invalid preset index row");entries.add(new SystemRecord(row.optString("kind"),row.optString("vendor"),row.optString("name"),row.optString("path"),row.optString("parent"),row.optBoolean("instantiated")));}
        }
        public List<SystemRecord> entries(){return entries;}
        public JSONObject read(String path) throws IOException {ZipEntry entry=zip.getEntry(path);if(entry==null||entry.isDirectory())throw new IOException("Preset JSON missing in original archive");try(InputStream in=zip.getInputStream(entry)){return PresetJson.read(in);}}
        public void close() throws IOException {zip.close();}
    }
}
