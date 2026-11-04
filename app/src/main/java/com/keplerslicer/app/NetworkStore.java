package com.keplerslicer.app;

import android.content.Context;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** One encrypted atomic record contains both the active selection and connection credentials. */
final class NetworkStore {
    private static final Object LOCK=new Object();private static final String KEY="printer_connections_v1";
    static final class Connection {
        final String id,name,printerIdentity,oauthSession;final PrinterEndpoint endpoint;
        Connection(String id,String name,String identity,PrinterEndpoint e){this(id,name,identity,e,"");}
        Connection(String id,String name,String identity,PrinterEndpoint e,String session){this.id=id;this.name=name;printerIdentity=identity;endpoint=e;oauthSession=session==null?"":session;}
    }
    static final class State {final List<Connection> connections;final String active;State(List<Connection> c,String a){connections=Collections.unmodifiableList(c);active=a;}Connection selected(){for(Connection c:connections)if(c.id.equals(active))return c;return null;}}
    static State load(Context c)throws IOException {synchronized(LOCK){try{String raw=NetworkSecrets.get(c,KEY);if(raw.isEmpty())return new State(new ArrayList<>(),"");JSONObject root=new JSONObject(raw);JSONArray a=root.getJSONArray("connections");if(a.length()>100)throw new IOException("Слишком много подключений.");List<Connection> rows=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject r=a.getJSONObject(i);Map<String,String> extras=new LinkedHashMap<>();JSONObject x=r.optJSONObject("extras");if(x!=null)for(Iterator<String> it=x.keys();it.hasNext();){String k=it.next();extras.put(k,x.getString(k));}PrinterEndpoint e=new PrinterEndpoint(PrinterEndpoint.Type.valueOf(r.getString("type")),r.getString("url"),PrinterEndpoint.Auth.valueOf(r.getString("auth")),r.optString("username"),r.optString("secret"),r.optString("storage"),extras);rows.add(new Connection(r.getString("id"),r.getString("name"),r.optString("printer_identity"),e,r.optString("oauth_session","")));}return new State(rows,root.optString("active"));}catch(IOException e){throw e;}catch(Exception e){throw new IOException("Не удалось прочитать подключения.",e);}}}
    static void save(Context c,Connection connection)throws IOException {synchronized(LOCK){State state=load(c);List<Connection> rows=new ArrayList<>(state.connections);rows.removeIf(r->r.id.equals(connection.id));if(rows.size()>=100)throw new IOException("Сохранено 100 подключений.");rows.add(connection);write(c,rows,connection.id);}}
    /** Update tokens and access credential together, without changing the selected printer. */
    static PrinterEndpoint updateOAuth(Context c,String id,String expectedAccess,String rawSession)throws IOException {synchronized(LOCK){
        try {JSONObject json=new JSONObject(rawSession);String access=json.optString("access_token"),refresh=json.optString("refresh_token");if(access.isEmpty()||refresh.isEmpty())throw new IOException("Сервер не вернул полную сессию SimplyPrint.");
            State state=load(c);List<Connection> rows=new ArrayList<>(state.connections);int index=-1;for(int i=0;i<rows.size();i++)if(rows.get(i).id.equals(id)){index=i;break;}if(index<0)throw new IOException("Подключение удалено.");
            Connection old=rows.get(index);PrinterEndpoint e=old.endpoint;if(e.type!=PrinterEndpoint.Type.SIMPLYPRINT||!e.secret().equals(expectedAccess))throw new IOException("Подключение изменилось; войдите снова.");
            PrinterEndpoint next=new PrinterEndpoint(e.type,e.baseUri.toASCIIString(),e.auth,e.username,access,e.storage,e.extras);rows.set(index,new Connection(old.id,old.name,old.printerIdentity,next,rawSession));write(c,rows,state.active);return next;
        }catch(JSONException|IllegalArgumentException x){throw new IOException("Некорректная сессия SimplyPrint.",x);}
    }}
    static void select(Context c,String id)throws IOException{synchronized(LOCK){State state=load(c);if(state.connections.stream().noneMatch(r->r.id.equals(id)))throw new IOException("Подключение не найдено.");write(c,state.connections,id);}}
    static void remove(Context c,String id)throws IOException{synchronized(LOCK){State state=load(c);List<Connection> rows=new ArrayList<>(state.connections);rows.removeIf(r->r.id.equals(id));write(c,rows,state.active.equals(id)?"":state.active);}}
    private static void write(Context c,List<Connection> connections,String active)throws IOException{try{JSONArray a=new JSONArray();for(Connection row:connections){PrinterEndpoint e=row.endpoint;JSONObject r=new JSONObject().put("id",row.id).put("name",row.name).put("printer_identity",row.printerIdentity).put("type",e.type.name()).put("url",e.baseUri.toASCIIString()).put("auth",e.auth.name()).put("username",e.username).put("secret",e.secret()).put("oauth_session",row.oauthSession).put("storage",e.storage).put("extras",new JSONObject(e.extras));a.put(r);}NetworkSecrets.put(c,KEY,new JSONObject().put("version",1).put("active",active).put("connections",a).toString());}catch(JSONException e){throw new IOException("Не удалось сохранить подключение.",e);}}
    static String currentPrinterIdentity(Context c){PrinterProfileCatalog.Profile p=PrinterProfileCatalog.loadSelection(c);return p==null?"":p.vendor+"\u0000"+p.model;}
}
