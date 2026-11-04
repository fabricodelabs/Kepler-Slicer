package com.keplerslicer.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.net.URI;
import java.util.UUID;

/** Cloud print-host protocols from the pinned upstream engine 6d2869c. All methods are synchronous.
 * Credentials and actual HTTP transport belong to the caller; never log requests. */
public final class CloudPrinterHosts {
    public static boolean supports(PrinterEndpoint.Type type) { return type==PrinterEndpoint.Type.OBICO || type==PrinterEndpoint.Type.SIMPLYPRINT || type==PrinterEndpoint.Type.THREE_D_PRINTER_OS; }
    public static PrinterHostClient.HostInfo test(PrinterEndpoint e,NetworkHttp.Cancellation cancel) throws IOException {
        CloudPrinterHosts client=client(e,cancel);
        switch(e.type) {
            case OBICO: client.testObico(); return new PrinterHostClient.HostInfo("Obico","unknown",false);
            case SIMPLYPRINT: ok(client.transport.get(Server.SIMPLYPRINT_API,"oauth2/TokenInfo"));return new PrinterHostClient.HostInfo("SimplyPrint","unknown",false);
            case THREE_D_PRINTER_OS: if(!client.printerOsSession(e.secret()).optBoolean("result",false))throw new IOException("Сессия 3DPrinterOS недействительна.");return new PrinterHostClient.HostInfo("3DPrinterOS","unknown",false);
            default: throw new IOException("Неизвестный облачный сервис.");
        }
    }
    public static PrinterHostClient.Status status(PrinterEndpoint e,NetworkHttp.Cancellation cancel) throws IOException {test(e,cancel);return new PrinterHostClient.Status("unknown","");}
    static JSONArray listObicoPrinters(PrinterEndpoint e,NetworkHttp.Cancellation cancel)throws IOException {
        if(e.type!=PrinterEndpoint.Type.OBICO)throw new IOException("Требуется подключение Obico.");return client(e,cancel).obicoPrinters();
    }
    static JSONObject listPrinterOsProjects(PrinterEndpoint e,NetworkHttp.Cancellation cancel)throws IOException {
        if(e.type!=PrinterEndpoint.Type.THREE_D_PRINTER_OS)throw new IOException("Требуется подключение 3DPrinterOS.");return client(e,cancel).printerOsProjects(e.secret());
    }
    static JSONObject listPrinterOsTypes(PrinterEndpoint e,String query,NetworkHttp.Cancellation cancel)throws IOException {
        if(e.type!=PrinterEndpoint.Type.THREE_D_PRINTER_OS)throw new IOException("Требуется подключение 3DPrinterOS.");return client(e,cancel).printerOsTypes(e.secret(),query);
    }
    public static PrinterHostClient.UploadResult upload(PrinterEndpoint e,File file,String name,NetworkHttp.Cancellation cancel) throws IOException {
        CloudPrinterHosts client=client(e,cancel);
        switch(e.type) {
            case OBICO: client.uploadObico(file,name,e.extras.get("printer_id"),false);return new PrinterHostClient.UploadResult(name,0,null,"Файл отправлен в Obico; команда запуска не отправлялась.");
            case SIMPLYPRINT: String url=client.uploadSimplyPrint(file,name);return new PrinterHostClient.UploadResult(name,0,url,"Файл загружен временно; завершите импорт в SimplyPrint.");
            case THREE_D_PRINTER_OS:
                if(!client.printerOsSession(e.secret()).optBoolean("result",false))throw new IOException("Сессия 3DPrinterOS недействительна.");
                String id=client.uploadPrinterOs(file,name,e.secret(),e.extras.get("project_id"),e.extras.get("project_name"));
                String type=e.extras.get("printer_type"),gtype=e.extras.get("gcode_type");
                if(type!=null&&!type.isEmpty()&&gtype!=null&&!gtype.isEmpty()) {
                    JSONObject result=client.updatePrinterOs(e.secret(),id,type,gtype);
                    if(!result.optBoolean("result",false))return new PrinterHostClient.UploadResult(id,0,null,"Файл загружен, но тип принтера не применён; проверьте 3DPrinterOS.");
                }
                return new PrinterHostClient.UploadResult(id,0,null,"Файл загружен в 3DPrinterOS; команда запуска не отправлялась.");
            default: throw new IOException("Неизвестный облачный сервис.");
        }
    }
    private static CloudPrinterHosts client(PrinterEndpoint endpoint,NetworkHttp.Cancellation cancel) throws IOException {
        if(!supports(endpoint.type))throw new IOException("Неизвестный облачный сервис.");
        if(endpoint.secret().isEmpty())throw new IOException("Для облачного сервиса нужен токен или сессия.");
        if(endpoint.type==PrinterEndpoint.Type.THREE_D_PRINTER_OS && !"https".equalsIgnoreCase(endpoint.baseUri.getScheme()) && !"127.0.0.1".equals(endpoint.baseUri.getHost()) && !"localhost".equalsIgnoreCase(endpoint.baseUri.getHost()))throw new IOException("Для 3DPrinterOS нужен HTTPS.");
        return new CloudPrinterHosts(new Transport(){
            URI uri(Server server,String path) {
                if(server==Server.HOST)return endpoint.url(path);
                return URI.create((server==Server.SIMPLYPRINT_API?"https://api.simplyprint.io/":"https://simplyprint.io/")+path);
            }
            Map<String,String> headers(){Map<String,String> h=new LinkedHashMap<>();if(endpoint.type!=PrinterEndpoint.Type.THREE_D_PRINTER_OS)h.put("Authorization","Bearer "+endpoint.secret());return h;}
            Response convert(NetworkHttp.Response r){return new Response(r.status,r.text());}
            public boolean cleanupAllowed(){return cancel==null||!cancel.isCancelled();}
            public Response get(Server server,String path)throws IOException {return convert(NetworkHttp.request("GET",uri(server,path),headers(),null,cancel));}
            public Response form(Server server,String path,Map<String,String> fields,FilePart part)throws IOException {return convert(NetworkHttp.request("POST",uri(server,path),headers(),multipart(fields,part),cancel));}
            public Response formUrlEncoded(Server server,String path,Map<String,String> fields)throws IOException {
                StringBuilder b=new StringBuilder();for(Map.Entry<String,String> f:fields.entrySet()){if(b.length()>0)b.append('&');b.append(query(f.getKey())).append('=').append(query(f.getValue()));}
                Map<String,String> h=headers();h.put("Content-Type","application/x-www-form-urlencoded");return convert(NetworkHttp.request("POST",uri(server,path),h,NetworkHttp.bytes(b.toString().getBytes(StandardCharsets.UTF_8)),cancel));
            }
        });
    }
    private static NetworkHttp.Body multipart(Map<String,String> fields,FilePart part) throws IOException {
        String boundary="Kepler"+UUID.randomUUID().toString().replace("-","");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        for(Map.Entry<String,String> e:fields.entrySet()) {
            if(!e.getKey().matches("[A-Za-z0-9_]+")||e.getValue()==null)throw new IOException("Недопустимое поле формы.");
            out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+e.getKey()+"\"\r\n\r\n"+e.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
        }
        if(part!=null)out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+part.name.replace("\\","\\\\").replace("\"","\\\"")+"\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] prefix=out.toByteArray(),suffix=((part==null?"":"\r\n")+"--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
        NetworkHttp.Body payload=part==null?null:NetworkHttp.fileSlice(part.file,part.offset,part.length);
        return new NetworkHttp.Body(){public long length(){return prefix.length+(payload==null?0:payload.length())+suffix.length;}public String contentType(){return "multipart/form-data; boundary="+boundary;}public void write(java.io.OutputStream stream,NetworkHttp.Cancellation cancel)throws IOException{cancel.check();stream.write(prefix);if(payload!=null)payload.write(stream,cancel);cancel.check();stream.write(suffix);}};
    }
    public enum Server { HOST, SIMPLYPRINT_HOME, SIMPLYPRINT_API }
    public static final class Response {
        public final int status; public final String body;
        public Response(int status,String body) { this.status=status; this.body=body==null?"":body; }
    }
    public static final class FilePart {
        public final File file; public final String name; public final long offset, length;
        FilePart(File file,String name,long offset,long length) { this.file=file; this.name=name; this.offset=offset; this.length=length; }
    }
    /** Implementations must supply HTTPS, authentication, bounded responses, cancellation,
     * and streaming multipart file ranges; no redirects to a different origin with credentials. */
    public interface Transport {
        default boolean cleanupAllowed(){return true;}
        Response get(Server server,String path) throws IOException;
        Response form(Server server,String path,Map<String,String> fields,FilePart file) throws IOException;
        Response formUrlEncoded(Server server,String path,Map<String,String> fields) throws IOException;
    }
    private final Transport transport;
    public CloudPrinterHosts(Transport transport) { if(transport==null)throw new IllegalArgumentException(); this.transport=transport; }
    private static Map<String,String> fields(String... pairs) {
        Map<String,String> result=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2)result.put(pairs[i],pairs[i+1]); return result;
    }
    private static String query(String value) { try{return URLEncoder.encode(value,"UTF-8");}catch(java.io.UnsupportedEncodingException e){throw new AssertionError(e);} }
    private static void filename(String name) {
        if(name==null || name.isEmpty() || name.equals(".") || name.equals("..") || name.indexOf('/')>=0 || name.indexOf('\\')>=0 || name.indexOf('\r')>=0 || name.indexOf('\n')>=0 || name.indexOf('\0')>=0) throw new IllegalArgumentException("Invalid filename");
    }
    private static void file(File file) throws IOException { if(file==null || !file.isFile() || !file.canRead())throw new IOException("Cannot read G-code"); }
    private static Response ok(Response response) throws IOException {
        if(response.status<200 || response.status>=300)throw new IOException("Cloud host HTTP "+response.status); return response;
    }
    private static JSONObject object(Response response) throws IOException {
        try {return new JSONObject(ok(response).body);}catch(JSONException e){throw new IOException("Invalid cloud host response",e);}
    }
    /** Obico expects Authorization: Bearer token on every request. */
    public void testObico() throws IOException { ok(transport.get(Server.HOST,"api/v1/version/")); }
    public JSONArray obicoPrinters() throws IOException {
        Response r=ok(transport.get(Server.HOST,"api/v1/printers/"));
        try{return new JSONArray(r.body);}catch(JSONException e){throw new IOException("Invalid Obico printer list",e);}
    }
    public void uploadObico(File source,String remoteName,String printerId,boolean start) throws IOException {
        file(source);filename(remoteName);if(printerId==null || printerId.isEmpty())throw new IllegalArgumentException("Printer ID required");
        testObico(); ok(transport.form(Server.HOST,"api/v1/g_code_files/",fields("print",start?"true":"false","path","","printer_id",printerId,"filename",remoteName),new FilePart(source,remoteName,0,source.length())));
    }
    /** SimplyPrint requires a valid OAuth Bearer token. Result is a panel URL for user import. */
    public String uploadSimplyPrint(File source,String remoteName) throws IOException {
        file(source); filename(remoteName); String chunkId=null, deleteToken=null;
        try {
            if(source.length()>100_000_000L) {
                final long size=source.length(), chunkSize=99_000_000L, count=(size+chunkSize-1)/chunkSize;
                for(long i=0;i<count;i++) {
                    String path="0/files/ChunkReceive?i="+i+"&temp=true";
                    if(i==0) path+="&filename="+query(remoteName)+"&chunks="+count+"&totalsize="+size;
                    else path+="&id="+query(chunkId);
                    long offset=i*chunkSize; JSONObject response=object(transport.form(Server.SIMPLYPRINT_API,path,fields(),new FilePart(source,remoteName,offset,Math.min(chunkSize,size-offset))));
                    if(i==0) { chunkId=String.valueOf(response.get("id")); deleteToken=response.getString("delete_token"); if(chunkId.isEmpty()||deleteToken.isEmpty())throw new IOException("SimplyPrint chunk credentials missing"); }
                }
            }
            Response r=chunkId==null ? transport.form(Server.SIMPLYPRINT_HOME,"api/files/TempUpload",fields(),new FilePart(source,remoteName,0,source.length())) : transport.form(Server.SIMPLYPRINT_HOME,"api/files/TempUpload",fields("chunkId",chunkId),null);
            String uuid=object(r).getString("uuid"); if(uuid.isEmpty())throw new IOException("SimplyPrint upload UUID missing");
            return "https://simplyprint.io/panel?import="+query("tmp:"+uuid)+"&filename="+query(remoteName);
        } catch(JSONException e) {
            if(chunkId!=null && deleteToken!=null && transport.cleanupAllowed()) try { transport.get(Server.SIMPLYPRINT_API,"0/files/ChunkReceive?id="+query(chunkId)+"&temp=true&delete="+query(deleteToken)); } catch(IOException ignored) { }
            throw new IOException("Invalid SimplyPrint response",e);
        }
        catch(IOException | RuntimeException e) {
            if(chunkId!=null && deleteToken!=null && transport.cleanupAllowed()) try { transport.get(Server.SIMPLYPRINT_API,"0/files/ChunkReceive?id="+query(chunkId)+"&temp=true&delete="+query(deleteToken)); } catch(IOException ignored) { }
            throw e;
        }
    }
    /** 3DPrinterOS session comes from its token-login flow, not an API key. */
    public JSONObject printerOsSession(String session) throws IOException { return object(transport.formUrlEncoded(Server.HOST,"apiglobal/check_session",fields("session",session))); }
    public JSONObject printerOsProjects(String session) throws IOException { return object(transport.formUrlEncoded(Server.HOST,"apiglobal/get_projects",fields("session",session))); }
    public JSONObject printerOsTypes(String session,String search) throws IOException {
        Map<String,String> f=fields("session",session); if(search!=null && !search.isEmpty()){f.put("description",search);f.put("software_version","OrcaSlicer");}
        return object(transport.formUrlEncoded(Server.HOST,"apiglobal/get_printer_types",f));
    }
    public String uploadPrinterOs(File source,String remoteName,String session,String projectId,String newProject) throws IOException {
        file(source);filename(remoteName);if(session==null||session.isEmpty())throw new IllegalArgumentException("Session required");
        Map<String,String> f=fields("session",session,"upload_type_id","7","upload_soft_name","OrcaSlicer","zip","false");
        if(projectId!=null&&!projectId.isEmpty())f.put("project_id",projectId);
        else if(newProject!=null&&!newProject.isEmpty()){f.put("project_name",newProject);f.put("project_color","grey");}
        JSONObject j=object(transport.form(Server.HOST,"apiglobal/upload",f,new FilePart(source,remoteName,0,source.length())));
        if(!j.optBoolean("result",false))throw new IOException("3DPrinterOS upload failed");
        JSONObject message=j.optJSONObject("message"); if(message==null||message.optString("file_id").isEmpty())throw new IOException("3DPrinterOS file ID missing");
        return message.optString("file_id");
    }
    public JSONObject updatePrinterOs(String session,String id,String printerType,String gcodeType) throws IOException {
        if(id==null||id.isEmpty())throw new IllegalArgumentException("File ID required");
        return object(transport.formUrlEncoded(Server.HOST,"apiglobal/file_update",fields("session",session,"updates["+id+"][ptype]",printerType,"updates["+id+"][gtype]",gcodeType,"updates["+id+"][zip]","false")));
    }
}
