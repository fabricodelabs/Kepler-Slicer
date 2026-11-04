package com.keplerslicer.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Cloud profile profile synchronization protocol at upstream commit 6d2869c.
 * Network I/O is supplied by the app's authenticated transport; call off the UI thread.
 * This layer does not decide conflict resolution or update a local preset store. */
public final class CloudProfileSync {
    public interface Transport {
        Response request(String method, String relativePath, String jsonBody) throws IOException;
    }
    public static final class Response {
        public final int status;
        public final String body;
        public Response(int status, String body) { this.status=status; this.body=body == null ? "" : body; }
    }
    public static final class Profile {
        public final String id, name;
        public final long updatedTime, createdTime;
        public final JSONObject content;
        Profile(JSONObject json) throws JSONException, IOException {
            id=validId(json.getString("id"));
            updatedTime=json.optLong("updated_time",0); createdTime=json.optLong("created_time",0);
            content=json.getJSONObject("content");
            String n=content.optString("name","");if(n.isEmpty())n=json.optString("name","");name=n.isEmpty()?id:n;
        }
    }
    public static final class Pull {
        public final long nextCursor;
        public final List<Profile> upserts;
        public final List<String> deletes;
        public final boolean fullResync, notModified;
        Pull(long cursor, List<Profile> profiles, List<String> removed, boolean full, boolean notModified) {
            nextCursor=cursor; upserts=Collections.unmodifiableList(profiles);
            deletes=Collections.unmodifiableList(removed); fullResync=full; this.notModified=notModified;
        }
    }
    public static final class Push {
        public final int status, conflictCode;
        public final long updatedTime;
        public final JSONObject conflict;
        Push(int status, long updatedTime, int conflictCode, JSONObject conflict) {
            this.status=status; this.updatedTime=updatedTime; this.conflictCode=conflictCode; this.conflict=conflict;
        }
        public boolean success() { return status==200 && updatedTime!=0; }
        public boolean isConflict() { return status==409; }
    }
    private final Transport transport;
    public CloudProfileSync(Transport transport) {
        if (transport==null) throw new IllegalArgumentException("Transport required"); this.transport=transport;
    }
    /** Caller must atomically apply the entire batch before persisting nextCursor. */
    public Pull pull(long cursor) throws IOException {
        if (cursor<0) throw new IllegalArgumentException("Invalid cursor");
        boolean full=cursor==0;
        Response r=transport.request("GET", "api/v1/sync/pull"+(cursor==0?"":"?cursor="+cursor), null);
        if (r.status==410 && cursor!=0) { full=true; r=transport.request("GET", "api/v1/sync/pull", null); }
        if (r.status==304) {
            if(full && cursor!=0)throw new IOException("The profile cloud returned 304 after an expired cursor");
            return new Pull(cursor, new ArrayList<>(), new ArrayList<>(), full, true);
        }
        if (r.status!=200) throw new IOException("Cloud profile pull HTTP "+r.status);
        try {
            JSONObject json=new JSONObject(r.body); long next=json.optLong("next_cursor",0);
            if (next<0) throw new IOException("Invalid sync cursor");
            List<Profile> upserts=new ArrayList<>(); List<String> deletes=new ArrayList<>();
            if ((json.has("upserts")&&json.optJSONArray("upserts")==null) || (json.has("deletes")&&json.optJSONArray("deletes")==null) || (full&&!json.has("upserts"))) throw new IOException("Invalid sync change lists");
            JSONArray arr=json.optJSONArray("upserts");
            if (arr!=null) for (int i=0;i<arr.length();i++) upserts.add(new Profile(arr.getJSONObject(i)));
            arr=json.optJSONArray("deletes");
            if (arr!=null) for (int i=0;i<arr.length();i++) deletes.add(validId(arr.getString(i)));
            return new Pull(next,upserts,deletes,full,false);
        } catch (JSONException e) { throw new IOException("Invalid Cloud profile pull response",e); }
    }
    /** A force write must be explicitly selected by the caller after a conflict. */
    public Push push(String id, String name, JSONObject content, String originalUpdatedTime, boolean force) throws IOException {
        validId(id); if (name==null || name.isEmpty() || content==null) throw new IllegalArgumentException("Invalid profile");
        try {
            JSONObject body=new JSONObject().put("id",id).put("name",name).put("content",content);
            if (originalUpdatedTime!=null && !originalUpdatedTime.isEmpty()) body.put("original_updated_time",originalUpdatedTime);
            String encoded=body.toString();
            if (encoded.getBytes(StandardCharsets.UTF_8).length>1024*1024) throw new IOException("Profile exceeds the cloud's 1 MiB limit");
            Response r=transport.request("POST",force?"api/v1/sync/force-push":"api/v1/sync/push",encoded);
            if (r.status==409) {
                JSONObject conflict; try { conflict=new JSONObject(r.body); } catch (JSONException invalid) { conflict=new JSONObject(); }
                return new Push(409,0,conflict.optInt("code",0),conflict);
            }
            if (r.status!=200) throw new IOException("Cloud profile push HTTP "+r.status);
            long updated=new JSONObject(r.body).optLong("updated_time",0);
            if (updated==0) throw new IOException("Cloud profile response lacks updated_time");
            return new Push(200,updated,0,null);
        } catch (JSONException e) { throw new IOException("Invalid Cloud profile push response",e); }
    }
    public void delete(String id) throws IOException {
        validId(id);
        Response r=transport.request("DELETE","api/v1/sync/delete?resource=profiles&id="+id,null);
        if (r.status==200 || r.status==204) return;
        if (r.status==404) try { if ("failed".equals(new JSONObject(r.body).optString("status"))) return; } catch (JSONException ignored) { }
        throw new IOException("Cloud profile delete HTTP "+r.status);
    }
    private static String validId(String value) throws IOException {
        try {
            if (value==null || !UUID.fromString(value).toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid cloud profile UUID",ex); }
    }
}
