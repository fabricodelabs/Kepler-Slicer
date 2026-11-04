package com.keplerslicer.app.settings;

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
        synchronized(LOCK){Map<String,JSONObject> users=readUsers();Preset old=descriptor(draft.baseId,users);JSONObject fresh=PresetJson.copy(draft.edited);
            // Like desktop Save As, a new profile must not reuse the source's own
            // setting_id. the engine recognizes this metadata key without ASCII case.
            // Keep unknown Unicode keys, imported metadata and existing saves intact.
            for(Iterator<String> keys=fresh.keys();keys.hasNext();)if(keys.next().matches("[sS][eE][tT][tT][iI][nN][gG]_[iI][dD]"))keys.remove();
            return create(old.kind,old.vendor,name,old.id,fresh,users);}
    }
    public Preset saveUser(String kind,String vendor,String name,String parentId,JSONObject original) throws IOException {
        synchronized(LOCK){Map<String,JSONObject> users=readUsers();if(parentId==null||parentId.isEmpty()){String inherited=original.optString("inherits","");if(!inherited.isEmpty())parentId=findParent(kind,vendor,inherited,users);}return create(kind,vendor,name,parentId,original,users);}
    }
    public void deleteUser(String id) throws IOException {
        synchronized(LOCK){Map<String,JSONObject> users=readUsers();Preset p=descriptor(id,users);if(p.system)throw new IOException("System presets cannot be deleted");for(String child:users.keySet())if(id.equals(parent(child,users)))throw new IOException("This preset is inherited by another user preset");JSONArray deleted=readCloudDeletes();JSONObject cloud=users.get(id).optJSONObject("cloud_sync");if(cloud!=null&&!cloud.optString("account").isEmpty())deleted.put(PresetJson.copy(cloud));users.remove(id);writeUsers(users,deleted);}
    }

    /** A detached upload snapshot. Cloud bookkeeping never becomes an exported ConfigOption. */
    public JSONArray cloudPending(String account) throws IOException {
        synchronized(LOCK){String scope=cloudAccount(account);JSONArray out=new JSONArray();Map<String,JSONObject> users=readUsers();
            for(String id:users.keySet()){JSONObject record=users.get(id),meta=record.optJSONObject("cloud_sync");if(meta!=null&&!scope.equals(meta.optString("account")))continue;String hash=PresetJson.fingerprint(record.optJSONObject("source"));if(meta!=null&&hash.equals(meta.optString("baseline")))continue;String parentId=parent(id,users);JSONObject parentRecord=parentId==null?null:users.get(parentId),parentCloud=parentRecord==null?null:parentRecord.optJSONObject("cloud_sync");if(parentCloud!=null&&!scope.equals(parentCloud.optString("account")))throw new IOException("A user ancestor belongs to another cloud account");JSONObject row=new JSONObject();put(row,"local_id",id);put(row,"id",meta==null?cloudId(id.substring(5)):cloudId(meta.optString("id")));put(row,"source_hash",hash);put(row,"updated_time",meta==null?0:meta.optLong("updated_time",0));put(row,"content",exportPreset(id));out.put(row);}return out;}
    }
    /** Associates only the exact sent revision; an edit during upload remains dirty. */
    public void cloudPushed(String account,String localId,String sentHash,long updatedTime) throws IOException {
        if(updatedTime<=0)throw new IOException("Invalid cloud update time");synchronized(LOCK){String scope=cloudAccount(account);Map<String,JSONObject> users=readUsers();JSONObject record=users.get(localId);if(record==null){String uploadedId=cloudId(localId.substring(5));JSONArray pending=readCloudDeletes();boolean found=false;for(int n=0;n<pending.length();n++){JSONObject tombstone=pending.optJSONObject(n);if(tombstone!=null&&scope.equals(tombstone.optString("account"))&&uploadedId.equals(tombstone.optString("id"))){put(tombstone,"updated_time",Math.max(updatedTime,tombstone.optLong("updated_time",0)));found=true;}}if(!found){JSONObject tombstone=new JSONObject();put(tombstone,"account",scope);put(tombstone,"id",uploadedId);put(tombstone,"updated_time",updatedTime);pending.put(tombstone);}writeUsers(users,pending);throw new IOException("Preset was deleted during upload. A durable remote deletion is queued; it will not be re-imported.");}JSONObject old=record.optJSONObject("cloud_sync");if(old!=null&&!scope.equals(old.optString("account")))throw new IOException("Preset belongs to another cloud account");if(old!=null&&updatedTime<old.optLong("updated_time",0))throw new IOException("Stale cloud acknowledgment; newer local revision retained");JSONObject meta=new JSONObject();put(meta,"account",scope);put(meta,"id",old==null?cloudId(localId.substring(5)):cloudId(old.optString("id")));put(meta,"baseline",sentHash);put(meta,"updated_time",updatedTime);put(record,"cloud_sync",meta);writeUsers(users);}
    }
    /** Durable before POST: deletion during even an ambiguous first upload must queue remote cleanup. */
    public void reserveCloudUpload(String account,String localId,String expectedHash)throws IOException{synchronized(LOCK){String scope=cloudAccount(account);Map<String,JSONObject> users=readUsers();JSONObject record=users.get(localId);if(record==null||!PresetJson.fingerprint(record.optJSONObject("source")).equals(expectedHash))throw new IOException("Preset changed before upload; retry with a new snapshot");JSONObject meta=record.optJSONObject("cloud_sync");if(meta!=null&&!scope.equals(meta.optString("account")))throw new IOException("Preset belongs to another cloud account");if(meta==null){meta=new JSONObject();put(meta,"account",scope);put(meta,"id",cloudId(localId.substring(5)));put(meta,"baseline","");put(meta,"updated_time",0);put(record,"cloud_sync",meta);}put(meta,"upload_pending",true);writeUsers(users);}}
    /** Atomically applies a full account mirror. Dirty/name/parent conflicts leave every local profile intact. */
    public int mergeCloud(String account,JSONArray remote) throws IOException {
        if(remote==null)throw new IOException("Cloud snapshot is missing");
        synchronized(LOCK){String scope=cloudAccount(account);Map<String,JSONObject> users=readUsers();Set<String> seen=new HashSet<>(),changed=new HashSet<>(),removed=new HashSet<>(),tombstones=new HashSet<>();JSONArray deleted=readCloudDeletes();for(int n=0;n<deleted.length();n++){JSONObject d=deleted.optJSONObject(n);if(d!=null&&scope.equals(d.optString("account")))tombstones.add(d.optString("id"));}
            for(int n=0;n<remote.length();n++){JSONObject item=remote.optJSONObject(n);if(item==null||item.optJSONObject("content")==null)throw new IOException("Invalid cloud profile");String remoteId=cloudId(item.optString("id"));if(!seen.add(remoteId))throw new IOException("Duplicate cloud profile ID");if(tombstones.contains(remoteId))continue;JSONObject incoming=cloudContent(item.optJSONObject("content"),item.optString("name"),remoteId);String kind=kindForType(incoming.optString("type")),name=validName(incoming.optString("name"));long time=item.optLong("updated_time",0);if(time<=0)throw new IOException("Cloud profile lacks an update time");String id="user:"+remoteId;JSONObject record=users.get(id),meta=record==null?null:record.optJSONObject("cloud_sync");
                if(record!=null){if(meta!=null&&!scope.equals(meta.optString("account")))throw new IOException("Cloud ID belongs to another account");if(meta==null){if(!PresetJson.equal(exportPreset(id),incoming))throw new IOException("Cloud conflict: "+name+". Local and server versions are retained.");}else{boolean dirty=!PresetJson.fingerprint(record.optJSONObject("source")).equals(meta.optString("baseline"));if(time<=meta.optLong("updated_time",0))continue;if(dirty&&!PresetJson.equal(exportPreset(id),incoming))throw new IOException("Cloud conflict: "+name+". Local and server versions are retained.");}}
                for(String other:users.keySet())if(!other.equals(id)&&kind.equals(users.get(other).optString("kind"))&&name.equals(users.get(other).optJSONObject("source").optString("name")))throw new IOException("Cloud profile name conflicts with a local preset: "+name);for(SystemRecord system:systems.values())if(kind.equals(system.kind)&&name.equals(system.name))throw new IOException("Cloud profile cannot replace a system preset: "+name);
                if(record==null)record=new JSONObject();put(record,"kind",kind);put(record,"vendor",record.optString("vendor",incoming.optString("vendor","")));put(record,"source",normalize(incoming,kind,name,incoming.optString("inherits")));put(record,"parent_id","");JSONObject next=new JSONObject();put(next,"account",scope);put(next,"id",remoteId);put(next,"updated_time",time);put(record,"cloud_sync",next);users.put(id,record);changed.add(id);
            }
            for(String id:new ArrayList<>(users.keySet())){JSONObject record=users.get(id),meta=record.optJSONObject("cloud_sync");if(meta!=null&&scope.equals(meta.optString("account"))&&!seen.contains(meta.optString("id"))){if(meta.optBoolean("upload_pending",false)&&meta.optLong("updated_time",0)==0)continue;if(!PresetJson.fingerprint(record.optJSONObject("source")).equals(meta.optString("baseline")))throw new IOException("Cloud deletion conflicts with local changes: "+record.optJSONObject("source").optString("name"));users.remove(id);removed.add(id);}}
            for(String id:changed){JSONObject record=users.get(id),source=record.optJSONObject("source");String inherited=source.optString("inherits","");String parent=inherited.isEmpty()?null:findParent(record.optString("kind"),record.optString("vendor"),inherited,users);put(record,"parent_id",parent==null?"":parent);if(parent!=null&&record.optString("vendor").isEmpty())put(record,"vendor",descriptor(parent,users).vendor);put(record.optJSONObject("cloud_sync"),"baseline",PresetJson.fingerprint(source));}
            validateAll(users);writeUsers(users);return changed.size()+removed.size();}
    }
    public JSONArray cloudDeletes(String account) throws IOException {synchronized(LOCK){String scope=cloudAccount(account);JSONArray out=new JSONArray(),all=readCloudDeletes();for(int n=0;n<all.length();n++){JSONObject item=all.optJSONObject(n);if(item!=null&&scope.equals(item.optString("account")))out.put(PresetJson.copy(item));}return out;}}
    public void cloudDeleted(String account,String id) throws IOException {synchronized(LOCK){String scope=cloudAccount(account);JSONArray kept=new JSONArray(),all=readCloudDeletes();for(int n=0;n<all.length();n++){JSONObject item=all.optJSONObject(n);if(item==null)throw new IOException("Invalid cloud deletion record");if(!scope.equals(item.optString("account"))||!id.equals(item.optString("id")))kept.put(item);}writeUsers(readUsers(),kept);}}
    /** Clears local sync associations only; no remote deletion is scheduled. */
    public void clearCloudAssociations() throws IOException {synchronized(LOCK){Map<String,JSONObject> users=readUsers();for(JSONObject user:users.values())user.remove("cloud_sync");writeUsers(users,new JSONArray());}}
    /** Detached, reviewable conflicts for mapped IDs; unrelated name collisions require manual renaming. */
    public JSONArray cloudReviews(String account,JSONArray remote)throws IOException{synchronized(LOCK){String scope=cloudAccount(account);Map<String,JSONObject> users=readUsers(),server=new LinkedHashMap<>();for(int n=0;n<remote.length();n++){JSONObject r=remote.optJSONObject(n);if(r==null||r.optJSONObject("content")==null)throw new IOException("Invalid server snapshot");String id=cloudId(r.optString("id"));if(server.put(id,r)!=null)throw new IOException("Duplicate server profile");}JSONArray reviews=new JSONArray();for(String localId:users.keySet()){JSONObject local=users.get(localId),meta=local.optJSONObject("cloud_sync");if(meta==null||!scope.equals(meta.optString("account")))continue;String id=cloudId(meta.optString("id"));JSONObject r=server.get(id);boolean dirty=!PresetJson.fingerprint(local.optJSONObject("source")).equals(meta.optString("baseline"));if(r!=null&&(!dirty||r.optLong("updated_time",0)<=meta.optLong("updated_time",0)))continue;if(r==null&&!dirty)continue;JSONObject review=new JSONObject();put(review,"local_id",localId);put(review,"id",id);put(review,"local_hash",PresetJson.fingerprint(local.optJSONObject("source")));put(review,"local_cloud_revision",meta.optLong("updated_time",0));put(review,"local_source",source(localId,users));put(review,"upload_content",exportPreset(localId));put(review,"deleted",r==null);put(review,"server_time",r==null?0:r.optLong("updated_time",0));if(r!=null)put(review,"server_source",cloudContent(r.optJSONObject("content"),r.optString("name"),id));reviews.put(review);}return reviews;}}
    public void confirmCloudReview(String account,String localId,String expectedHash)throws IOException{synchronized(LOCK){String scope=cloudAccount(account);Map<String,JSONObject> users=readUsers();JSONObject local=users.get(localId),meta=local==null?null:local.optJSONObject("cloud_sync");if(meta==null||!scope.equals(meta.optString("account"))||!PresetJson.fingerprint(local.optJSONObject("source")).equals(expectedHash))throw new IOException("The local profile changed after review. Reopen the conflict.");}}
    /** One explicitly reviewed server choice. An optional duplicate preserves the full local effective config. */
    public String acceptCloudReview(String account,JSONObject review,String duplicateName)throws IOException{synchronized(LOCK){String localId=review.optString("local_id");confirmCloudReview(account,localId,review.optString("local_hash"));String scope=cloudAccount(account),remoteId=cloudId(review.optString("id"));Map<String,JSONObject> users=readUsers();JSONObject local=users.get(localId),meta=local.optJSONObject("cloud_sync");if(!remoteId.equals(meta.optString("id")))throw new IOException("Cloud identity changed after review");if(review.optLong("local_cloud_revision",-1)!=meta.optLong("updated_time",0))throw new IOException("Cloud revision changed after review. Reopen the conflict.");String duplicateId="";
        if(duplicateName!=null){String kind=local.optString("kind"),vendor=local.optString("vendor");duplicateName=validName(duplicateName);requireUnique(kind,vendor,duplicateName,users);String ancestor=localId;Set<String> ancestors=new HashSet<>();while(!systems.containsKey(ancestor)){if(!ancestors.add(ancestor))throw new IOException("Inheritance cycle");ancestor=parent(ancestor,users);if(ancestor==null)throw new IOException("No trusted system ancestor for duplicate");}JSONObject copy=resolve(localId,users,new HashSet<>());for(Iterator<String> keys=copy.keys();keys.hasNext();)if(keys.next().matches("[sS][eE][tT][tT][iI][nN][gG]_[iI][dD]"))keys.remove();JSONObject record=new JSONObject();duplicateId="user:"+UUID.randomUUID();put(record,"kind",kind);put(record,"vendor",vendor);put(record,"parent_id",ancestor);put(record,"source",normalize(copy,kind,duplicateName,descriptor(ancestor,users).name));users.put(duplicateId,record);}
        if(review.optBoolean("deleted",false)){for(String id:users.keySet())if(!id.equals(localId)&&localId.equals(parent(id,users)))throw new IOException("This server-deleted preset still has local children. Resolve or reparent them first.");users.remove(localId);}else{JSONObject incoming=review.optJSONObject("server_source");long time=review.optLong("server_time",0);if(incoming==null||time<=0)throw new IOException("Incomplete reviewed server profile");incoming=cloudContent(incoming,"",remoteId);String kind=kindForType(incoming.optString("type")),name=validName(incoming.optString("name"));if(!kind.equals(local.optString("kind")))throw new IOException("Cloud preset kind changed");for(String id:users.keySet())if(!id.equals(localId)&&kind.equals(users.get(id).optString("kind"))&&name.equals(users.get(id).optJSONObject("source").optString("name")))throw new IOException("Server name conflicts with another local profile");for(SystemRecord system:systems.values())if(kind.equals(system.kind)&&name.equals(system.name))throw new IOException("Server cannot replace a system profile");String inherited=incoming.optString("inherits","");String parent=inherited.isEmpty()?null:findParent(kind,local.optString("vendor"),inherited,users);JSONObject accepted=normalize(incoming,kind,name,parentName(parent,users));put(local,"source",accepted);put(local,"parent_id",parent==null?"":parent);put(meta,"account",scope);put(meta,"updated_time",time);put(meta,"baseline",PresetJson.fingerprint(accepted));}
        validateAll(users);writeUsers(users);return duplicateId;}}
    /** Upstream sync accepts content.name, then the envelope name, then the profile UUID. */
    private static JSONObject cloudContent(JSONObject content,String envelopeName,String id)throws IOException {
        JSONObject incoming=PresetJson.copy(content);
        if(incoming.optString("name","").isEmpty())put(incoming,"name",envelopeName==null||envelopeName.isEmpty()?id:envelopeName);
        return incoming;
    }
    private static String cloudAccount(String account) throws IOException {if(account==null||account.isEmpty()||account.length()>1024)throw new IOException("Cloud account ID required");return PresetJson.sha256(account);}
    private static String cloudId(String id) throws IOException {try{String canonical=UUID.fromString(id).toString();if(!canonical.equalsIgnoreCase(id))throw new IllegalArgumentException();return canonical;}catch(IllegalArgumentException e){throw new IOException("Invalid cloud profile UUID",e);}}
    public JSONObject exportPreset(String id) throws IOException {synchronized(LOCK){Map<String,JSONObject> users=readUsers();Preset p=descriptor(id,users);JSONObject result=normalize(source(id,users),p.kind,p.name,parentName(parent(id,users),users));if(!result.has("version"))put(result,"version",UPSTREAM_VERSION);String identity=p.kind.equals(PROCESS)?"print_settings_id":p.kind.equals(FILAMENT)?"filament_settings_id":"printer_settings_id";put(result,identity,p.kind.equals(FILAMENT)?new JSONArray().put(p.name):p.name);return result;}}
    /** Explicit Kepler exchange wrapper. This JSON object is not advertised as the desktop slicer's ZIP format. */
    public JSONObject exportBundle(List<String> ids) throws IOException {synchronized(LOCK){JSONArray out=new JSONArray();for(String id:withUserAncestors(ids,readUsers())){Preset p=load(id);JSONObject entry=new JSONObject();put(entry,"kind",p.kind);put(entry,"vendor",p.vendor);put(entry,"source",exportPreset(id));out.put(entry);}JSONObject root=new JSONObject();put(root,"format","kepler-presets-v1");put(root,"upstream_revision",REVISION);put(root,"presets",out);return root;}}
    /** Plain ZIP of original-format preset JSON, accepted by upstream PresetBundle::import_presets. */
    public void exportBundleZip(List<String> ids,OutputStream target) throws IOException {
        synchronized(LOCK){try(ZipOutputStream zip=new ZipOutputStream(target)){int n=0;for(String id:withUserAncestors(ids,readUsers())){Preset p=load(id);String path=(p.kind.equals(PRINTER)?"machine":p.kind)+"/"+String.format(java.util.Locale.ROOT,"%04d",++n)+"-"+safeFilename(p.name)+".json";zip.putNextEntry(new ZipEntry(path));zip.write(exportPreset(id).toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}}
    }
    public List<Preset> importBundle(JSONObject document) throws IOException {
        synchronized(LOCK){List<ImportRecord> pending=new ArrayList<>();if("kepler-presets-v1".equals(document.optString("format"))){JSONArray records=document.optJSONArray("presets");if(records==null)throw new IOException("Preset array is missing");for(int i=0;i<records.length();i++){JSONObject row=records.optJSONObject(i);if(row==null||row.optJSONObject("source")==null)throw new IOException("Invalid preset entry");pending.add(new ImportRecord(row.optString("kind"),row.optString("vendor"),row.optJSONObject("source")));}}else pending.add(new ImportRecord(kindForType(document.optString("type")),"",document));return importRecords(pending);}
    }
    /** Transactional import: one malformed/missing/cyclic parent prevents every write. No overwrite by name. */
    public List<Preset> importBundleZip(File archive) throws IOException {
        List<ImportRecord> pending=new ArrayList<>();try(ZipFile zip=new ZipFile(archive)){Set<String> names=new HashSet<>();java.util.Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){ZipEntry entry=entries.nextElement();if(entry.isDirectory())continue;if(!names.add(entry.getName()))throw new IOException("Duplicate ZIP entry");String file=entry.getName().toLowerCase(java.util.Locale.ROOT);if(!file.endsWith(".json"))throw new IOException("This bundle contains companion files; only JSON-only preset ZIP import is supported. The original archive has not been changed");try(InputStream in=zip.getInputStream(entry)){JSONObject source=PresetJson.read(in);if(file.endsWith("bundle_structure.json")){JSONObject assets=source.optJSONObject("printer_asset");if(assets!=null&&assets.length()>0)throw new IOException("This bundle contains companion bed assets; importing those assets is not supported yet. The original archive has not been changed");continue;}if(file.endsWith("bundle_metadata.json"))continue;String type=source.optString("type");if(type.equals("process")||type.equals("filament")||type.equals("machine"))pending.add(new ImportRecord(kindForType(type),"",source));}}}if(pending.isEmpty())throw new IOException("No preset JSON files in archive");synchronized(LOCK){return importRecords(pending);}
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
    private JSONArray readCloudDeletes() throws IOException {if(!usersFile.isFile())return new JSONArray();try(InputStream in=new FileInputStream(usersFile)){JSONObject root=PresetJson.read(in);JSONArray rows=root.optJSONArray("cloud_deleted");if(root.has("cloud_deleted")&&rows==null)throw new IOException("Invalid cloud deletion queue");try{return rows==null?new JSONArray():new JSONArray(rows.toString());}catch(JSONException e){throw new IOException(e);}}}
    private void writeUsers(Map<String,JSONObject> users) throws IOException {writeUsers(users,readCloudDeletes());}
    private void writeUsers(Map<String,JSONObject> users,JSONArray deleted) throws IOException {JSONObject records=new JSONObject();for(Map.Entry<String,JSONObject> e:users.entrySet())put(records,e.getKey(),e.getValue());JSONObject root=new JSONObject();put(root,"version",1);put(root,"profiles",records);if(deleted.length()>0)put(root,"cloud_deleted",deleted);atomicWrite(usersFile,root.toString().getBytes(StandardCharsets.UTF_8));}
    private static void atomicWrite(File target,byte[] bytes) throws IOException {File staging=File.createTempFile(".preset-",".tmp",target.getParentFile());boolean committed=false;try{try(FileOutputStream out=new FileOutputStream(staging)){out.write(bytes);out.getFD().sync();}try{Files.move(staging.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(java.nio.file.AtomicMoveNotSupportedException e){Files.move(staging.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}committed=true;}finally{if(!committed)Files.deleteIfExists(staging.toPath());}}
    private static String emptyToNull(String s){return s==null||s.isEmpty()?null:s;}
    private static String validName(String name) throws IOException {if(name==null||name.trim().isEmpty()||name.length()>240||name.indexOf('/')>=0||name.indexOf('\\')>=0||name.indexOf('\0')>=0||name.equals(".")||name.equals(".."))throw new IOException("Invalid preset name");return name;}
    private static String safeFilename(String name){return name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_");}
    private static String kindForType(String type) throws IOException {if(type.equals("machine"))return PRINTER;if(type.equals(PROCESS)||type.equals(FILAMENT))return type;throw new IOException("Expected a printer, process or filament preset");}
    private static void requireKind(String kind){if(!PROCESS.equals(kind)&&!FILAMENT.equals(kind)&&!PRINTER.equals(kind))throw new IllegalArgumentException("Unknown preset kind: "+kind);}
    private static void put(JSONObject object,String key,Object value) throws IOException {try{object.put(key,value);}catch(JSONException e){throw new IOException(e);}}
    @Override public void close() throws IOException {systemSource.close();}

    private static final class AssetSource implements SystemSource {
        private final ZipFile zip;private final List<SystemRecord> entries=new ArrayList<>();
        AssetSource(Context context) throws IOException {
            JSONObject index;try(InputStream in=context.getAssets().open("kepler_preset_index.json")){index=PresetJson.read(in);}if(index.optInt("version")!=1||!REVISION.equals(index.optString("revision")))throw new IOException("Preset index revision mismatch");String hash=index.optString("archive_sha256");if(!hash.matches("[a-f0-9]{64}"))throw new IOException("Invalid preset archive digest");File archive=new File(context.getCacheDir(),"kepler-preset-source-"+hash.substring(0,16)+".zip");if(!archive.isFile()||archive.length()!=index.optLong("archive_size")){File temp=File.createTempFile(".kepler-preset-",".zip",context.getCacheDir());boolean moved=false;try{try(InputStream in=context.getAssets().open("kepler_profiles.zip");FileOutputStream out=new FileOutputStream(temp)){byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);out.getFD().sync();}Files.move(temp.toPath(),archive.toPath(),StandardCopyOption.REPLACE_EXISTING);moved=true;}finally{if(!moved)Files.deleteIfExists(temp.toPath());}}
            zip=new ZipFile(archive);JSONArray rows=index.optJSONArray("profiles");if(rows==null)throw new IOException("Preset index entries missing");for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)throw new IOException("Invalid preset index row");entries.add(new SystemRecord(row.optString("kind"),row.optString("vendor"),row.optString("name"),row.optString("path"),row.optString("parent"),row.optBoolean("instantiated")));}
        }
        public List<SystemRecord> entries(){return entries;}
        public JSONObject read(String path) throws IOException {ZipEntry entry=zip.getEntry(path);if(entry==null||entry.isDirectory())throw new IOException("Preset JSON missing in original archive");try(InputStream in=zip.getInputStream(entry)){return PresetJson.read(in);}}
        public void close() throws IOException {zip.close();}
    }
}
