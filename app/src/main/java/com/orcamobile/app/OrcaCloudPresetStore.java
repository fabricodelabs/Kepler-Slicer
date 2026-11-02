package com.orcamobile.app;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Atomic, account-scoped mirror of cloud profile JSON. It is separate from the slicer presets. */
public final class OrcaCloudPresetStore {
    private static final Object LOCK=new Object();
    private final AtomicFile file;
    public OrcaCloudPresetStore(Context context,String accountId) throws IOException {
        if(accountId==null||accountId.isEmpty())throw new IllegalArgumentException("Account ID required");
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(accountId.getBytes(StandardCharsets.UTF_8));
            StringBuilder name=new StringBuilder();for(byte b:digest)name.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            File dir=new File(context.getFilesDir(),"orca-cloud-presets");
            if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create cloud store");
            file=new AtomicFile(new File(dir,name+".json"));
        } catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
    }
    private JSONObject read() throws IOException {
        try(FileInputStream in=file.openRead()) {
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[8192];int n,total=0;
            while((n=in.read(b))!=-1){total+=n;if(total>64*1024*1024)throw new IOException("Cloud store too large");out.write(b,0,n);}
            return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
        } catch(java.io.FileNotFoundException e){return new JSONObject();}
        catch(JSONException e){throw new IOException("Invalid cloud store",e);}
    }
    private void write(JSONObject state) throws IOException {
        FileOutputStream out=null;
        try {out=file.startWrite();out.write(state.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(IOException e){if(out!=null)file.failWrite(out);throw e;}
    }
    private static JSONObject getObject(JSONObject root,String key) throws JSONException {
        JSONObject found=root.optJSONObject(key);if(found!=null)return found;
        found=new JSONObject();root.put(key,found);return found;
    }
    private static JSONObject profile(OrcaCloudSync.Profile p) throws JSONException {
        return new JSONObject().put("id",p.id).put("name",p.name).put("updated_time",p.updatedTime).put("created_time",p.createdTime).put("content",p.content).put("dirty",false);
    }
    private static long newestRevision(JSONObject local,JSONObject conflict,long deleted){long value=deleted;if(local!=null){value=Math.max(value,local.optLong("updated_time",0));JSONObject baseline=local.optJSONObject("server_baseline");if(baseline!=null)value=Math.max(value,baseline.optLong("updated_time",0));}if(conflict!=null){value=Math.max(value,conflict.optLong("server_cursor",0));value=Math.max(value,conflict.optLong("response_revision",0));JSONObject server=conflict.optJSONObject("server");if(server!=null)value=Math.max(value,server.optLong("updated_time",0));}return value;}
    public long cursor() throws IOException {synchronized(LOCK){return read().optLong("cursor",0);}}
    public boolean hasSnapshot()throws IOException{synchronized(LOCK){JSONObject root=read();JSONObject profiles=root.optJSONObject("profiles");return root.optBoolean("snapshot_known",false)||root.optLong("cursor",0)>0||(profiles!=null&&profiles.length()>0);}}
    /** Data and cursor commit together. Dirty local copies are never overwritten by pull. */
    public void apply(OrcaCloudSync.Pull batch) throws IOException {
        if(batch==null||batch.notModified)return;
        synchronized(LOCK) {try {
            JSONObject root=read(),profiles=getObject(root,"profiles"),conflicts=getObject(root,"conflicts"),tombstones=getObject(root,"deleted_versions");
            if(batch.nextCursor<root.optLong("cursor",0))throw new IOException("Stale cloud snapshot cursor; newer local mirror retained");
            Set<String> seen=new HashSet<>();
            for(OrcaCloudSync.Profile p:batch.upserts) {
                seen.add(p.id);JSONObject local=profiles.optJSONObject(p.id);
                long deleted=tombstones.optLong(p.id,0);if(p.updatedTime<newestRevision(local,conflicts.optJSONObject(p.id),deleted)||(deleted>0&&p.updatedTime<=deleted))continue;
                tombstones.remove(p.id);
                if(local!=null&&local.optBoolean("dirty",false)) {
                    local.put("server_baseline",profile(p));
                    if(local.optLong("updated_time",0)!=p.updatedTime) conflicts.put(p.id,new JSONObject().put("deleted",false).put("server",profile(p)));else conflicts.remove(p.id);
                } else {profiles.put(p.id,profile(p));conflicts.remove(p.id);}
            }
            Set<String> removed=new HashSet<>(batch.deletes);
            if(batch.fullResync) {
                ArrayList<String> keys=new ArrayList<>();for(Iterator<String> i=profiles.keys();i.hasNext();)keys.add(i.next());
                for(String id:keys)if(!seen.contains(id))removed.add(id);
            }
            for(String id:removed) {
                JSONObject local=profiles.optJSONObject(id);
                long known=newestRevision(local,conflicts.optJSONObject(id),tombstones.optLong(id,0));if(batch.nextCursor<=0||batch.nextCursor<known)continue;
                tombstones.put(id,batch.nextCursor);
                if(local!=null&&local.optBoolean("dirty",false)) {
                    if(local.optLong("updated_time",0)!=0)conflicts.put(id,new JSONObject().put("deleted",true).put("server_cursor",batch.nextCursor));
                }
                else {profiles.remove(id);conflicts.remove(id);}
            }
            if(batch.nextCursor!=0||batch.fullResync)root.put("cursor",batch.nextCursor);root.put("snapshot_known",true);
            write(root);
        }catch(JSONException e){throw new IOException("Cannot update cloud store",e);}}
    }
    /** Export includes all JSON content and unresolved conflicts; no preset data is discarded. */
    public String exportJson() throws IOException {synchronized(LOCK){return read().toString();}}
    public JSONArray profiles() throws IOException {synchronized(LOCK){try {
        JSONObject p=getObject(read(),"profiles");JSONArray out=new JSONArray();
        for(Iterator<String> i=p.keys();i.hasNext();)out.put(p.getJSONObject(i.next()));return out;
    }catch(JSONException e){throw new IOException(e);}}}
    public int conflictCount() throws IOException {synchronized(LOCK){JSONObject c=read().optJSONObject("conflicts");return c==null?0:c.length();}}
    /** Called after a confirmed idempotent remote delete, without advancing the server cursor. */
    public void removeAcknowledged(String id)throws IOException{synchronized(LOCK){try{JSONObject root=read(),profiles=getObject(root,"profiles"),conflicts=getObject(root,"conflicts"),tombstones=getObject(root,"deleted_versions");long known=newestRevision(profiles.optJSONObject(id),conflicts.optJSONObject(id),tombstones.optLong(id,0));if(known>0)tombstones.put(id,known);profiles.remove(id);conflicts.remove(id);write(root);}catch(JSONException e){throw new IOException(e);}}}
    public void resetCursor()throws IOException{synchronized(LOCK){try{JSONObject root=read();root.put("cursor",0);write(root);}catch(JSONException e){throw new IOException(e);}}}
    public void upsertAcknowledged(String id,String name,JSONObject content,long updatedTime)throws IOException{synchronized(LOCK){try{JSONObject root=read(),profiles=getObject(root,"profiles"),conflicts=getObject(root,"conflicts"),tombstones=getObject(root,"deleted_versions");long deleted=tombstones.optLong(id,0);if(updatedTime<newestRevision(profiles.optJSONObject(id),conflicts.optJSONObject(id),deleted)||(deleted>0&&updatedTime<=deleted))throw new IOException("Stale upload acknowledgment; newer mirror retained");profiles.put(id,new JSONObject().put("id",id).put("name",name).put("content",new JSONObject(content.toString())).put("updated_time",updatedTime).put("dirty",false));conflicts.remove(id);tombstones.remove(id);write(root);}catch(JSONException e){throw new IOException(e);}}}
    /** Complete reviewed server versions, including the preserved server side of dirty mirror conflicts. */
    public JSONArray serverProfiles()throws IOException{synchronized(LOCK){try{JSONObject root=read(),profiles=getObject(root,"profiles"),conflicts=getObject(root,"conflicts");JSONObject server=new JSONObject(profiles.toString());for(Iterator<String> keys=profiles.keys();keys.hasNext();){String id=keys.next();JSONObject local=profiles.getJSONObject(id);if(local.optBoolean("dirty",false)){JSONObject baseline=local.optJSONObject("server_baseline");if(baseline==null||baseline.optJSONObject("content")==null)throw new IOException("Получите полный снимок сервера перед разбором конфликта.");server.put(id,baseline);}}for(Iterator<String> keys=conflicts.keys();keys.hasNext();){String id=keys.next();JSONObject conflict=conflicts.optJSONObject(id);if(conflict==null)throw new IOException("Invalid conflict record");if(conflict.optBoolean("needs_refresh",false))throw new IOException("Получите полную актуальную версию сервера перед разбором конфликта.");if(conflict.optBoolean("deleted",false))server.remove(id);else{JSONObject remote=conflict.optJSONObject("server");if(remote==null||remote.optJSONObject("content")==null)throw new IOException("Получите полный снимок сервера перед разбором конфликта.");server.put(id,remote);}}JSONArray out=new JSONArray();for(Iterator<String> keys=server.keys();keys.hasNext();)out.put(server.getJSONObject(keys.next()));return out;}catch(JSONException e){throw new IOException(e);}}}
    public JSONObject conflicts() throws IOException {synchronized(LOCK){try {JSONObject c=read().optJSONObject("conflicts");return c==null?new JSONObject():new JSONObject(c.toString());}catch(JSONException e){throw new IOException(e);}}}
    /** A destructive conflict resolution is explicit; unknown/incomplete server data is rejected. */
    public void acceptServer(String id) throws IOException {synchronized(LOCK){try {
        JSONObject root=read(),conflicts=getObject(root,"conflicts"),c=conflicts.optJSONObject(id);
        if(c==null)throw new IOException("Конфликт не найден.");
        JSONObject profiles=getObject(root,"profiles");
        JSONObject reviewed=c.optJSONObject("server");long revision=c.optBoolean("deleted",false)?c.optLong("server_cursor",0):reviewed==null?0:reviewed.optLong("updated_time",0);if(c.optBoolean("needs_refresh",false)||revision<newestRevision(profiles.optJSONObject(id),c,getObject(root,"deleted_versions").optLong(id,0)))throw new IOException("Обновите полную версию сервера перед принятием конфликта.");
        if(c.optBoolean("deleted",false))profiles.remove(id);
        else {
            JSONObject server=c.optJSONObject("server");
            if(server==null||server.optJSONObject("content")==null)throw new IOException("Обновите профиль из облака для получения полной версии сервера.");
            profiles.put(id,new JSONObject(server.toString()).put("dirty",false));
        }
        conflicts.remove(id);write(root);
    }catch(JSONException e){throw new IOException(e);}}}
    public void edit(String id,JSONObject content) throws IOException {synchronized(LOCK){try {
        JSONObject root=read(),p=getObject(root,"profiles").optJSONObject(id);
        if(p==null||content==null)throw new IOException("Профиль не найден.");
        if(!p.optBoolean("dirty",false)){JSONObject baseline=new JSONObject(p.toString());baseline.remove("server_baseline");p.put("server_baseline",baseline);}
        p.put("content",new JSONObject(content.toString())).put("dirty",true);write(root);
    }catch(JSONException e){throw new IOException("Не удалось сохранить профиль.",e);}}}
    public void markPushed(String id,String sentContent,long updatedTime) throws IOException {synchronized(LOCK){try {
        JSONObject root=read(),p=getObject(root,"profiles").optJSONObject(id);
        if(p==null)throw new IOException("Профиль изменился во время синхронизации.");
        if(updatedTime<newestRevision(p,getObject(root,"conflicts").optJSONObject(id),getObject(root,"deleted_versions").optLong(id,0)))throw new IOException("Stale upload acknowledgment; newer mirror retained");
        p.put("updated_time",updatedTime);
        if(p.optJSONObject("content")!=null&&p.optJSONObject("content").toString().equals(sentContent)){p.put("dirty",false);p.remove("server_baseline");}
        getObject(root,"conflicts").remove(id);write(root);
    }catch(JSONException e){throw new IOException(e);}}}
    public void recordConflict(String id,JSONObject server) throws IOException {synchronized(LOCK){try {
        JSONObject root=read(),conflicts=getObject(root,"conflicts"),existing=conflicts.optJSONObject(id),profile=server==null?null:server.optJSONObject("server_profile");long revision=profile==null?0:profile.optLong("updated_time",0);long known=newestRevision(getObject(root,"profiles").optJSONObject(id),existing,getObject(root,"deleted_versions").optLong(id,0));if(revision>0&&revision<known)return;JSONObject next=existing==null?new JSONObject():new JSONObject(existing.toString());next.put("response",server).put("needs_refresh",true);if(revision>0)next.put("response_revision",Math.max(revision,next.optLong("response_revision",0)));conflicts.put(id,next);write(root);
    }catch(JSONException e){throw new IOException(e);}}}
}
