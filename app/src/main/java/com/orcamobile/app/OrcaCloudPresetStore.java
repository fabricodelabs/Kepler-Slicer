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
    public long cursor() throws IOException {synchronized(LOCK){return read().optLong("cursor",0);}}
    /** Data and cursor commit together. Dirty local copies are never overwritten by pull. */
    public void apply(OrcaCloudSync.Pull batch) throws IOException {
        if(batch==null||batch.notModified)return;
        synchronized(LOCK) {try {
            JSONObject root=read(),profiles=getObject(root,"profiles"),conflicts=getObject(root,"conflicts");
            Set<String> seen=new HashSet<>();
            for(OrcaCloudSync.Profile p:batch.upserts) {
                seen.add(p.id);JSONObject local=profiles.optJSONObject(p.id);
                if(local!=null&&local.optBoolean("dirty",false)) {
                    if(local.optLong("updated_time",0)!=p.updatedTime) conflicts.put(p.id,new JSONObject().put("deleted",false).put("server",profile(p)));
                } else {profiles.put(p.id,profile(p));conflicts.remove(p.id);}
            }
            Set<String> removed=new HashSet<>(batch.deletes);
            if(batch.fullResync) {
                ArrayList<String> keys=new ArrayList<>();for(Iterator<String> i=profiles.keys();i.hasNext();)keys.add(i.next());
                for(String id:keys)if(!seen.contains(id))removed.add(id);
            }
            for(String id:removed) {
                JSONObject local=profiles.optJSONObject(id);
                if(local!=null&&local.optBoolean("dirty",false)) {
                    if(local.optLong("updated_time",0)!=0)conflicts.put(id,new JSONObject().put("deleted",true));
                }
                else {profiles.remove(id);conflicts.remove(id);}
            }
            if(batch.nextCursor!=0||batch.fullResync)root.put("cursor",batch.nextCursor);
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
    public JSONObject conflicts() throws IOException {synchronized(LOCK){try {JSONObject c=read().optJSONObject("conflicts");return c==null?new JSONObject():new JSONObject(c.toString());}catch(JSONException e){throw new IOException(e);}}}
    /** A destructive conflict resolution is explicit; unknown/incomplete server data is rejected. */
    public void acceptServer(String id) throws IOException {synchronized(LOCK){try {
        JSONObject root=read(),conflicts=getObject(root,"conflicts"),c=conflicts.optJSONObject(id);
        if(c==null)throw new IOException("Конфликт не найден.");
        JSONObject profiles=getObject(root,"profiles");
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
        p.put("content",new JSONObject(content.toString())).put("dirty",true);write(root);
    }catch(JSONException e){throw new IOException("Не удалось сохранить профиль.",e);}}}
    public void markPushed(String id,String sentContent,long updatedTime) throws IOException {synchronized(LOCK){try {
        JSONObject root=read(),p=getObject(root,"profiles").optJSONObject(id);
        if(p==null)throw new IOException("Профиль изменился во время синхронизации.");
        p.put("updated_time",updatedTime);
        if(p.optJSONObject("content")!=null&&p.optJSONObject("content").toString().equals(sentContent))p.put("dirty",false);
        getObject(root,"conflicts").remove(id);write(root);
    }catch(JSONException e){throw new IOException(e);}}}
    public void recordConflict(String id,JSONObject server) throws IOException {synchronized(LOCK){try {
        JSONObject root=read();getObject(root,"conflicts").put(id,new JSONObject().put("response",server));write(root);
    }catch(JSONException e){throw new IOException(e);}}}
}
