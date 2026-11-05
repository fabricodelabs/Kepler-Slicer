package com.keplerslicer.app;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/** Synchronous print-host operations. Call on a worker thread.
 * Protocols follow pinned upstream Utils/OctoPrint.cpp and Moonraker.cpp.
 * Upload never starts a job; startUploaded is a separate explicit action.
 */
public final class PrinterHostClient {
    public static final class HostInfo {
        public final String name, state; public final boolean uploadByPut;
        public HostInfo(String name,String state,boolean uploadByPut) { this.name=name;this.state=state;this.uploadByPut=uploadByPut; }
    }
    public static final class Status {
        public final String state, rawJson;
        public Status(String state,String rawJson) { this.state=state;this.rawJson=rawJson; }
    }
    public static final class UploadResult {
        public final String remotePath, actionUrl, message; public final int httpStatus;
        public UploadResult(String remotePath,int httpStatus,String actionUrl,String message) {this.remotePath=remotePath;this.httpStatus=httpStatus;this.actionUrl=actionUrl;this.message=message;}
    }
    private final PrinterEndpoint endpoint;
    private PrinterDigestAuth.Challenge challenge;
    private String cnonce;
    private long nonceCount;
    public PrinterHostClient(PrinterEndpoint endpoint) { if(endpoint==null) throw new IllegalArgumentException(L.t("Не указан принтер."));this.endpoint=endpoint; }

    /** Verifies the expected service and, for PrusaLink, its PUT capability. */
    public HostInfo test(NetworkHttp.Cancellation cancel) throws IOException {
        if(LegacyPrinterHosts.supports(endpoint.type)) return new HostInfo(LegacyPrinterHosts.test(endpoint,cancel),"unknown",false);
        if(CloudPrinterHosts.supports(endpoint.type)) return CloudPrinterHosts.test(endpoint,cancel);
        switch(endpoint.type) {
            case OCTOPRINT: case PRUSALINK: case PRUSACONNECT: case SL1: {
                JSONObject version=json(get("api/version",cancel));
                if(!version.has("api")||version.isNull("api")) throw new IOException(L.t("Сервер не вернул версию API."));
                String text=version.optString("text","");
                if(endpoint.type==PrinterEndpoint.Type.OCTOPRINT && !text.isEmpty()&&!text.startsWith("OctoPrint")) throw new IOException(L.t("Сервер не похож на OctoPrint."));
                if(endpoint.type==PrinterEndpoint.Type.SL1 && !text.startsWith("Prusa SLA")) throw new IOException(L.t("Сервер не похож на Prusa SLA."));
                if(endpoint.type!=PrinterEndpoint.Type.OCTOPRINT && endpoint.type!=PrinterEndpoint.Type.SL1 && !(text.startsWith("PrusaLink")||text.startsWith("OctoPrint"))) throw new IOException(L.t("Сервер не похож на PrusaLink."));
                JSONObject capabilities=version.optJSONObject("capabilities");
                return new HostInfo(text.isEmpty()?endpoint.type.name():text,"unknown",capabilities!=null&&capabilities.optBoolean("upload-by-put",false));
            }
            case MOONRAKER: {
                JSONObject result=json(get("server/info",cancel)).optJSONObject("result");
                if(result==null||!result.has("klippy_state")||result.isNull("klippy_state")) throw new IOException(L.t("Ответ не похож на Moonraker."));
                return new HostInfo("Moonraker",result.optString("klippy_state","unknown"),false);
            }
            case BAMBU: case BAMBU_CLOUD: { JSONObject r = BambuLan.report(endpoint, cancel); return new HostInfo("Bambu Lab " + endpoint.username.trim(), r.optString("gcode_state", "unknown"), false); }
            default: throw new IOException(L.t("Этот тип сервера пока не поддерживается."));
        }
    }

    /** Reads server status only; legacy backends without a status API report unknown. */
    public Status status(NetworkHttp.Cancellation cancel) throws IOException {
        if(LegacyPrinterHosts.supports(endpoint.type)) { if(LegacyPrinterControl.supportsStatus(endpoint)) return LegacyPrinterControl.status(endpoint,cancel); return new Status("unknown",LegacyPrinterHosts.test(endpoint,cancel)); }
        if(CloudPrinterHosts.supports(endpoint.type)) return CloudPrinterHosts.status(endpoint,cancel);
        NetworkHttp.Response response;
        switch(endpoint.type) {
            case OCTOPRINT:
                response=send("GET",endpoint.url("api/printer"),new LinkedHashMap<>(),null,cancel,true);
                if(response.status==409) return new Status("disconnected",response.text());
                response.requireSuccess(); JSONObject octo=json(response); JSONObject state=octo.optJSONObject("state");
                return new Status(state==null?"unknown":state.optString("text","unknown"),response.text());
            case MOONRAKER:
                response=get("printer/objects/query?print_stats&virtual_sdcard",cancel);
                JSONObject root=json(response).optJSONObject("result"), objects=root==null?null:root.optJSONObject("status"), stats=objects==null?null:objects.optJSONObject("print_stats");
                return new Status(stats==null?"unknown":stats.optString("state","unknown"),response.text());
            case PRUSALINK:
                response=get("api/v1/status",cancel); JSONObject printer=json(response).optJSONObject("printer");
                return new Status(printer==null?"unknown":printer.optString("state","unknown"),response.text());
            case SL1:
                test(cancel); return new Status("unknown","");
            case PRUSACONNECT:
                test(cancel); return new Status("unknown","");
            case BAMBU: case BAMBU_CLOUD: { JSONObject r = BambuLan.report(endpoint, cancel); return new Status(r.optString("gcode_state", "unknown"), r.toString()); }
            default: throw new IOException(L.t("Статус для этого сервера пока не поддерживается."));
        }
    }

    /** Uploads only: no start/queue action is included in the request. */
    public UploadResult upload(File file,String remoteName,NetworkHttp.Cancellation cancel) throws IOException {
        if(file==null||!file.isFile()||!file.canRead()) throw new IOException(L.t("Файл G-code недоступен."));
        PrinterEndpoint.encodeComponent(remoteName); // validates one filename, not a path
        if(endpoint.type==PrinterEndpoint.Type.SL1 && !(remoteName.toLowerCase(java.util.Locale.ROOT).endsWith(".sl1") || remoteName.toLowerCase(java.util.Locale.ROOT).endsWith(".sl1s")))
            throw new IOException(L.t("Для Prusa SL1 нужен подготовленный SLA-файл .sl1 или .sl1s; FDM G-code не подходит."));
        if(LegacyPrinterHosts.supports(endpoint.type)) {
            String sentName=LegacyPrinterHosts.remoteName(endpoint.type,remoteName);
            LegacyPrinterHosts.upload(endpoint,file,remoteName,cancel);
            return new UploadResult(sentName,0,null,L.t("Файл отправлен; команда запуска не отправлялась.")+(sentName.equals(remoteName)?"":L.t(" Имя для передачи изменено; проверьте возможное совпадение на сервере.")));
        }
        if(CloudPrinterHosts.supports(endpoint.type)) return CloudPrinterHosts.upload(endpoint,file,remoteName,cancel);
        if(endpoint.type==PrinterEndpoint.Type.BAMBU||endpoint.type==PrinterEndpoint.Type.BAMBU_CLOUD) { String stored=BambuLan.upload(endpoint,file,remoteName,cancel); return new UploadResult(stored,0,null,L.t("Файл отправлен; команда запуска не отправлялась.")); }
        HostInfo info=test(cancel); NetworkHttp.Response response; String path;
        switch(endpoint.type) {
            case OCTOPRINT: {
                path="api/files/local"; Map<String,String> fields=new LinkedHashMap<>(); fields.put("print","false");fields.put("path","");
                response=send("POST",endpoint.url(path),new LinkedHashMap<>(),NetworkHttp.multipart(fields,"file",file,remoteName,"application/octet-stream"),cancel,false); break;
            }
            case MOONRAKER: {
                path="server/files/upload"; Map<String,String> fields=new LinkedHashMap<>(); fields.put("root",endpoint.storage);fields.put("print","false");
                response=send("POST",endpoint.url(path),new LinkedHashMap<>(),NetworkHttp.multipart(fields,"file",file,remoteName,"application/octet-stream"),cancel,false); break;
            }
            case PRUSALINK: case PRUSACONNECT: case SL1: {
                if(info.uploadByPut) {
                    path="api/v1/files/"+PrinterEndpoint.encodeComponent(endpoint.storage)+"/"+PrinterEndpoint.encodeComponent(remoteName);
                    Map<String,String> headers=new LinkedHashMap<>();headers.put("Content-Type","text/x.gcode");
                    // No Print-After-Upload or Overwrite header: device defaults apply.
                    response=send("PUT",endpoint.url(path),headers,NetworkHttp.file(file),cancel,false);
                } else {
                    path="api/files/"+PrinterEndpoint.encodeComponent(endpoint.storage);
                    Map<String,String> fields=new LinkedHashMap<>();fields.put("path","");
                    if(endpoint.type==PrinterEndpoint.Type.PRUSALINK || endpoint.type==PrinterEndpoint.Type.SL1) fields.put("print","false");
                    // PrusaConnect upload-only: neither to_print nor to_queue is sent.
                    response=send("POST",endpoint.url(path),new LinkedHashMap<>(),NetworkHttp.multipart(fields,"file",file,remoteName,"application/octet-stream"),cancel,false);
                }
                break;
            }
            default: throw new IOException(L.t("Загрузка для этого сервера пока не поддерживается."));
        }
        response.requireSuccess();
        String stored=remoteName, note=response.status==202?L.t("Файл принят сервером; обработка может продолжаться."):L.t("Файл отправлен; команда запуска не отправлялась.");
        if(endpoint.type==PrinterEndpoint.Type.OCTOPRINT) {
            stored=null;
            try {JSONObject files=new JSONObject(response.text()).optJSONObject("files"),local=files==null?null:files.optJSONObject("local");stored=confirmedRemotePath(local);}
            catch(JSONException ignored) { }
            if(stored==null)note+=L.t(" Сервер не подтвердил путь файла; запуск недоступен.");
        } else if(endpoint.type==PrinterEndpoint.Type.MOONRAKER) {
            stored=null;boolean startedOrQueued=false;
            try { JSONObject reply=new JSONObject(response.text()); JSONObject item=reply.optJSONObject("item");
                if(item!=null && "gcodes".equals(endpoint.storage) && "gcodes".equals(item.optString("root","")))stored=confirmedRemotePath(item);
                if(reply.optBoolean("print_started",false)||reply.optBoolean("print_queued",false)) {stored=null;startedOrQueued=true;note=L.t("Сервер сообщил о запуске или постановке в очередь, хотя запрос был без запуска. Повторный запуск отключён.");} }
            catch(JSONException ignored) { /* Older servers may omit their item envelope. */ }
            if(stored==null&&!startedOrQueued)note+=L.t(" Сервер не подтвердил путь в gcodes; запуск недоступен.");
        }
        return new UploadResult(stored,response.status,null,note);
    }

    /** Explicitly starts a previously uploaded file. The caller must obtain user confirmation first.
     * A failed or timed-out request is never retried: the printer may already have accepted it.
     * Protocol references: docs.octoprint.org/en/main/api/files.html and
     * moonraker.readthedocs.io/en/latest/external_api/printer/.
     */
    public void startUploaded(String remotePath, NetworkHttp.Cancellation cancel) throws IOException {
        String path=encodeRemotePath(remotePath);
        if(endpoint.type==PrinterEndpoint.Type.DUET) { if(remotePath.contains("/"))throw new IOException(L.t("Недопустимый путь файла на принтере.")); LegacyPrinterControl.startDuet(endpoint,remotePath,cancel); return; }
        if(endpoint.type==PrinterEndpoint.Type.BAMBU) { if(remotePath.contains("/"))throw new IOException(L.t("Недопустимый путь файла на принтере.")); BambuLan.startFile(endpoint,remotePath,cancel); return; }
        if(endpoint.type!=PrinterEndpoint.Type.OCTOPRINT && endpoint.type!=PrinterEndpoint.Type.MOONRAKER)
            throw new IOException(L.t("Запуск уже загруженного файла для этого сервера пока не поддерживается."));
        if(endpoint.type==PrinterEndpoint.Type.MOONRAKER && !"gcodes".equals(endpoint.storage))throw new IOException(L.t("Запуск доступен только для файлов в хранилище gcodes."));
        test(cancel); // read-only request also initializes Digest before any state-changing command
        NetworkHttp.Response response;
        if(endpoint.type==PrinterEndpoint.Type.OCTOPRINT) {
            Map<String,String> headers=new LinkedHashMap<>();headers.put("Content-Type","application/json");
            response=send("POST",endpoint.url("api/files/local/"+path),headers,
                    NetworkHttp.bytes("{\"command\":\"select\",\"print\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8)),cancel,false);
        } else {
            response=send("POST",endpoint.url("printer/print/start?filename="+path),new LinkedHashMap<>(),null,cancel,false);
        }
        response.requireSuccess();
        if(endpoint.type==PrinterEndpoint.Type.MOONRAKER && !"ok".equals(json(response).optString("result","")))
            throw new IOException(L.t("Moonraker не подтвердил запуск печати."));
    }
    // ---- Job control: explicit actions only, each confirmed by the user in the UI; never retried. ----
    public enum Job { PAUSE, RESUME, CANCEL }
    public static boolean supportsJobs(PrinterEndpoint.Type t) { return t==PrinterEndpoint.Type.OCTOPRINT||t==PrinterEndpoint.Type.MOONRAKER||t==PrinterEndpoint.Type.PRUSALINK||t==PrinterEndpoint.Type.BAMBU||t==PrinterEndpoint.Type.DUET||t==PrinterEndpoint.Type.REPETIER||t==PrinterEndpoint.Type.CREALITYPRINT||t==PrinterEndpoint.Type.ELEGOOLINK||t==PrinterEndpoint.Type.BAMBU_CLOUD; }
    public static boolean supportsMotion(PrinterEndpoint.Type t) { return t==PrinterEndpoint.Type.OCTOPRINT||t==PrinterEndpoint.Type.MOONRAKER||t==PrinterEndpoint.Type.BAMBU||t==PrinterEndpoint.Type.DUET||t==PrinterEndpoint.Type.REPETIER||t==PrinterEndpoint.Type.BAMBU_CLOUD; }
    public static boolean supportsLight(PrinterEndpoint.Type t) { return t==PrinterEndpoint.Type.BAMBU||t==PrinterEndpoint.Type.BAMBU_CLOUD||t==PrinterEndpoint.Type.CREALITYPRINT||t==PrinterEndpoint.Type.ELEGOOLINK; }
    public static boolean supportsChamber(PrinterEndpoint.Type t) { return t==PrinterEndpoint.Type.BAMBU||t==PrinterEndpoint.Type.BAMBU_CLOUD; }

    /** Pause, resume or cancel the running job (OctoPrint api/job, Moonraker printer/print, PrusaLink api/v1/job, Bambu MQTT). */
    public void job(Job job, NetworkHttp.Cancellation cancel) throws IOException {
        if(job==null)throw new IllegalArgumentException("job");
        switch(endpoint.type) {
            case BAMBU: case BAMBU_CLOUD: BambuLan.job(endpoint, job==Job.PAUSE?BambuLan.Job.PAUSE:job==Job.RESUME?BambuLan.Job.RESUME:BambuLan.Job.STOP, cancel); return;
            case OCTOPRINT: {
                test(cancel);
                String body=job==Job.CANCEL?"{\"command\":\"cancel\"}":"{\"command\":\"pause\",\"action\":\""+(job==Job.PAUSE?"pause":"resume")+"\"}";
                postJson("api/job",body,cancel); return;
            }
            case MOONRAKER: {
                test(cancel);
                NetworkHttp.Response r=send("POST",endpoint.url("printer/print/"+(job==Job.PAUSE?"pause":job==Job.RESUME?"resume":"cancel")),new LinkedHashMap<>(),null,cancel,false);
                r.requireSuccess(); return;
            }
            case PRUSALINK: {
                test(cancel);
                JSONObject current=json(get("api/v1/job",cancel)); if(!current.has("id"))throw new IOException(L.t("Принтер не сообщил о текущем задании."));
                long id=current.optLong("id",-1); if(id<0)throw new IOException(L.t("Принтер не сообщил о текущем задании."));
                String path="api/v1/job/"+id+(job==Job.CANCEL?"":job==Job.PAUSE?"/pause":"/resume");
                NetworkHttp.Response r=send(job==Job.CANCEL?"DELETE":"PUT",endpoint.url(path),new LinkedHashMap<>(),null,cancel,false); r.requireSuccess(); return;
            }
            case DUET: case REPETIER: case CREALITYPRINT: case ELEGOOLINK: LegacyPrinterControl.job(endpoint,job,cancel); return;
            default: throw new IOException(L.t("Управление печатью для этого сервера пока не поддерживается."));
        }
    }
    /** Speed mode 1 silent (50%), 2 standard (100%), 3 sport (124%), 4 ludicrous (166%), as on Bambu printers. */
    public void speed(int level, NetworkHttp.Cancellation cancel) throws IOException {
        if(level<1||level>4)throw new IllegalArgumentException("speed");
        if(endpoint.type==PrinterEndpoint.Type.BAMBU||endpoint.type==PrinterEndpoint.Type.BAMBU_CLOUD){BambuLan.speed(endpoint,level,cancel);return;}
        gcode("M220 S"+new int[]{50,100,124,166}[level-1]+"\n",cancel);
    }
    public void chamberLight(boolean on, NetworkHttp.Cancellation cancel) throws IOException {
        if(endpoint.type==PrinterEndpoint.Type.CREALITYPRINT||endpoint.type==PrinterEndpoint.Type.ELEGOOLINK){LegacyPrinterControl.light(endpoint,on,cancel);return;}
        if(endpoint.type!=PrinterEndpoint.Type.BAMBU&&endpoint.type!=PrinterEndpoint.Type.BAMBU_CLOUD)throw new IOException(L.t("Подсветка доступна не на всех принтерах."));
        BambuLan.chamberLight(endpoint,on,cancel);
    }
    public void auxFan(boolean on, NetworkHttp.Cancellation cancel) throws IOException {
        if(endpoint.type!=PrinterEndpoint.Type.BAMBU&&endpoint.type!=PrinterEndpoint.Type.BAMBU_CLOUD)throw new IOException(L.t("Вспомогательный вентилятор доступен только на Bambu Lab."));
        BambuLan.gcode(endpoint,"M106 P2 S"+(on?255:0)+"\n",cancel);
    }
    /** Relative move of one axis (mm, |d| <= 100) or homing (axis 'H'). The caller refuses this while printing. */
    public void move(char axis, double mm, NetworkHttp.Cancellation cancel) throws IOException {
        if(axis=='H'){ if(endpoint.type==PrinterEndpoint.Type.OCTOPRINT){test(cancel);postJson("api/printer/printhead","{\"command\":\"home\",\"axes\":[\"x\",\"y\",\"z\"]}",cancel);} else gcode("G28\n",cancel); return; }
        if("XYZ".indexOf(axis)<0||!Double.isFinite(mm)||Math.abs(mm)>100||mm==0)throw new IllegalArgumentException("move");
        String d=String.format(java.util.Locale.ROOT,"%.1f",mm);
        if(endpoint.type==PrinterEndpoint.Type.OCTOPRINT){test(cancel);postJson("api/printer/printhead","{\"command\":\"jog\",\""+Character.toLowerCase(axis)+"\":"+d+"}",cancel);return;}
        gcode("G91\nG1 "+axis+d+" F"+(axis=='Z'?600:3000)+"\nG90\n",cancel);
    }
    /** Sends fixed G-code lines built above (never user text). */
    private void gcode(String lines, NetworkHttp.Cancellation cancel) throws IOException {
        switch(endpoint.type){
            case BAMBU: case BAMBU_CLOUD: BambuLan.gcode(endpoint,lines,cancel); return;
            case OCTOPRINT: { test(cancel); org.json.JSONArray list=new org.json.JSONArray(); for(String l:lines.split("\n"))if(!l.isEmpty())list.put(l); postJson("api/printer/command","{\"commands\":"+list+"}",cancel); return; }
            case MOONRAKER: { test(cancel); NetworkHttp.Response r=send("POST",endpoint.url("printer/gcode/script?script="+java.net.URLEncoder.encode(lines.trim(),"UTF-8").replace("+","%20")),new LinkedHashMap<>(),null,cancel,false); r.requireSuccess(); return; }
            case DUET: case REPETIER: LegacyPrinterControl.gcode(endpoint,lines,cancel); return;
            default: throw new IOException(L.t("Команды G-code для этого сервера не поддерживаются."));
        }
    }
    private void postJson(String path,String body,NetworkHttp.Cancellation cancel) throws IOException {
        Map<String,String> headers=new LinkedHashMap<>();headers.put("Content-Type","application/json");
        send("POST",endpoint.url(path),headers,NetworkHttp.bytes(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)),cancel,false).requireSuccess();
    }

    private static String encodeRemotePath(String path) {
        if(path==null || path.isEmpty() || path.startsWith("/") || path.endsWith("/"))throw new IllegalArgumentException(L.t("Недопустимый путь файла на принтере."));
        StringBuilder out=new StringBuilder();for(String component:path.split("/",-1)){if(out.length()>0)out.append('/');out.append(PrinterEndpoint.encodeComponent(component));}return out.toString();
    }
    private static String confirmedRemotePath(JSONObject object) {
        if(object==null)return null;Object raw=object.opt("path");if(!(raw instanceof String))return null;
        String path=(String)raw;try{encodeRemotePath(path);return path;}catch(IllegalArgumentException bad){return null;}
    }
    private NetworkHttp.Response get(String path,NetworkHttp.Cancellation cancel) throws IOException {
        NetworkHttp.Response r=send("GET",endpoint.url(path),new LinkedHashMap<>(),null,cancel,true);r.requireSuccess();return r;
    }
    private static JSONObject json(NetworkHttp.Response response) throws IOException {
        try { return new JSONObject(response.text()); } catch(JSONException e) { throw new IOException(L.t("Сервер вернул некорректный JSON."),e); }
    }
    private NetworkHttp.Response send(String method,URI uri,Map<String,String> headers,NetworkHttp.Body body,NetworkHttp.Cancellation cancel,boolean safeRetry) throws IOException {
        Map<String,String> requestHeaders=new LinkedHashMap<>(headers);
        if(endpoint.auth==PrinterEndpoint.Auth.API_KEY) requestHeaders.put("X-Api-Key",endpoint.secret());
        else if(endpoint.auth==PrinterEndpoint.Auth.DIGEST) {
            if(endpoint.type!=PrinterEndpoint.Type.PRUSALINK&&endpoint.type!=PrinterEndpoint.Type.PRUSACONNECT&&endpoint.type!=PrinterEndpoint.Type.SL1) throw new IOException(L.t("Digest для этого протокола не поддерживается."));
            String auth=digestHeader(method,uri); if(auth!=null) requestHeaders.put("Authorization",auth);
            else if(body!=null) throw new IOException(L.t("Сначала проверьте авторизацию сервера."));
        } else if(endpoint.auth!=PrinterEndpoint.Auth.NONE) throw new IOException(L.t("Этот способ авторизации для сервера не поддерживается."));
        NetworkHttp.Response response=NetworkHttp.request(method,uri,requestHeaders,body,cancel);
        // Retry only read-only requests. Never replay an upload whose outcome could be uncertain.
        if(response.status==401&&endpoint.auth==PrinterEndpoint.Auth.DIGEST&&safeRetry&&body==null) {
            PrinterDigestAuth.Challenge next=digestChallenge(response);
            synchronized(this) { challenge=next;cnonce=PrinterDigestAuth.nonce();nonceCount=0; }
            requestHeaders.put("Authorization",digestHeader(method,uri));
            response=NetworkHttp.request(method,uri,requestHeaders,null,cancel);
        }
        return response;
    }
    private synchronized String digestHeader(String method,URI uri) throws IOException {
        if(challenge==null) return null;
        String path=uri.getRawPath(); if(path==null||path.isEmpty())path="/"; if(uri.getRawQuery()!=null)path+="?"+uri.getRawQuery();
        return PrinterDigestAuth.authorization(challenge,endpoint.username,endpoint.secret(),method,path,++nonceCount,cnonce);
    }
    private static PrinterDigestAuth.Challenge digestChallenge(NetworkHttp.Response response) throws IOException {
        IOException problem=null;
        for(Map.Entry<String,java.util.List<String>> header:response.headers.entrySet()) {
            if(header.getKey()==null||!header.getKey().equalsIgnoreCase("WWW-Authenticate")||header.getValue()==null) continue;
            for(String value:header.getValue()) {
                try { return PrinterDigestAuth.parse(value); } catch(IOException e) { problem=e; }
            }
        }
        throw problem==null?new IOException(L.t("Сервер не предложил Digest.")):problem;
    }
}
