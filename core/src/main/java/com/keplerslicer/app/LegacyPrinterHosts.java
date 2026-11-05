package com.keplerslicer.app;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Local host protocols ported from the upstream desktop slicer. Ordinary upload never starts printing. */
final class LegacyPrinterHosts {
    private LegacyPrinterHosts() {}
    static boolean supports(PrinterEndpoint.Type t) { return EnumSet.of(PrinterEndpoint.Type.DUET,PrinterEndpoint.Type.ULTIMAKER,PrinterEndpoint.Type.FLASHAIR,PrinterEndpoint.Type.ASTROBOX,PrinterEndpoint.Type.REPETIER,PrinterEndpoint.Type.MKS,PrinterEndpoint.Type.ESP3D,PrinterEndpoint.Type.CREALITYPRINT,PrinterEndpoint.Type.FLASHFORGE,PrinterEndpoint.Type.ELEGOOLINK).contains(t); }
    static String remoteName(PrinterEndpoint.Type type,String name) {
        switch(type) {case ESP3D:return LegacyHostFiles.short83(name);case CREALITYPRINT:return name.replace(' ','_');case FLASHFORGE:return LegacyHostFiles.flashforgeName(name);default:return name;}
    }
    static String test(PrinterEndpoint e, NetworkHttp.Cancellation c) throws IOException {
        c=token(c);
        switch(e.type) {
        case DUET: {boolean rrf=duetConnect(e,c);if(rrf)try{get(e,"rr_disconnect",null,c);}catch(IOException ignored){}return rrf?"Duet RRF":"Duet DSF";}
        case ULTIMAKER: { String v=jsonString(get(e,"api/v1/system/variant",null,c).text()); if(!v.startsWith("Ultimaker 3")&&!v.equals("Ultimaker S5"))throw new IOException(L.t("Это не поддерживаемый UltiMaker.")); NetworkHttp.Response verified=digestRequest(e,"GET",e.url("api/v1/auth/verify"),null,c);String result=verified.text().trim();if(!result.equals("\"ok\"")&&!"ok".equals(json(verified).optString("message")))throw new IOException(L.t("UltiMaker не подтвердил авторизацию.")); return v; }
        case FLASHAIR: if(!get(e,"command.cgi?op=118",null,c).text().trim().startsWith("1"))throw new IOException(L.t("FlashAir не подтвердил доступ.")); return "FlashAir";
        case ASTROBOX: { JSONObject j=json(get(e,"api/version",api(e),c)); if(!j.has("api")||(j.has("text")&&!j.optString("text").startsWith("AstroBox")))throw new IOException(L.t("AstroBox не подтвердил API.")); return "AstroBox"; }
        case REPETIER: { JSONObject j=json(get(e,"printer/info",api(e),c)); if((j.has("software")&&!"Repetier-Server".equals(j.optString("software")))||(!j.has("software")&&j.has("name")&&!j.optString("name").startsWith("Repetier")))throw new IOException(L.t("Это не Repetier Server.")); return "Repetier Server"; }
        case MKS: try(LegacyTcpConsole tcp=console(e,8080,c)){tcp.command("M105");} return "MKS";
        case ESP3D: get(e,"command?plain=M105",null,c); return "ESP3D";
        case CREALITYPRINT: get(e,"info",bearer(e),c); return "Creality Print";
        case FLASHFORGE: if(flashHttp(e)){ JSONObject j=json(request("POST",flashUri(e,"detail"),null,NetworkHttp.bytes(flashCredentials(e).toString().getBytes(StandardCharsets.UTF_8)),c,"application/json")); checkCode(j); } else {try(LegacyTcpConsole tcp=console(e,8899,c)){tcp.command("~M601 S1");}} return "Flashforge";
        case ELEGOOLINK: { int model=elegoo(e); if(model==1){if(!request("GET",e.baseUri,null,null,c,null).text().toUpperCase(Locale.ROOT).contains("ELEGOO"))throw new IOException(L.t("Elegoo не подтвердил доступ."));} else if(model==2){ JSONObject j=cc2(e,"GET","system/info?X-Token="+query(elegooToken(e)),null,null,c); if(j.optJSONObject("system_info")==null||j.optJSONObject("system_info").optString("sn").isEmpty())throw new IOException(L.t("Elegoo не вернул серийный номер.")); } else {JSONObject j=json(get(e,"api/version",api(e),c)); if(!j.has("api"))throw new IOException(L.t("Принтер не подтвердил API."));} return "ElegooLink"; }
        default: throw new IOException(L.t("Неизвестный протокол принтера."));
        }
    }
    static void upload(PrinterEndpoint e, File f, String name, NetworkHttp.Cancellation c) throws IOException {
        c=token(c); if(f==null||!f.isFile()||f.length()<=0)throw new IOException(L.t("G-code недоступен или пуст.")); PrinterEndpoint.encodeComponent(name);
        switch(e.type) {
        case ULTIMAKER: throw new IOException(L.t("UltiMaker сразу запускает печать при передаче. Требуется отдельное подтверждение запуска."));
        case DUET: {boolean rrf=duetConnect(e,c); if(rrf){try{JSONObject j=json(request("POST",e.url("rr_upload?name="+query("0:/gcodes/"+name)+"&time="+query(timestamp())),null,NetworkHttp.file(f),c,null));if(j.optInt("err",-1)!=0)throw new IOException(L.t("Duet отклонил файл."));}finally{try{get(e,"rr_disconnect",null,c);}catch(IOException ignored){}}}else {NetworkHttp.Response r=request("PUT",e.url("machine/file/gcodes/"+PrinterEndpoint.encodeComponent(name)),null,NetworkHttp.file(f),c,null);if(r.status!=201)throw new IOException(L.t("Duet DSF не подтвердил сохранение файла (HTTP ")+r.status+").");}return;}
        case FLASHAIR: flashAirSuccess(get(e,"upload.cgi?WRITEPROTECT=ON&FTIME="+LegacyHostFiles.fatTimestamp(System.currentTimeMillis()),null,c));flashAirSuccess(get(e,"upload.cgi?UPDIR="+query("/"),null,c));flashAirSuccess(request("POST",e.url("upload.cgi"),null,NetworkHttp.multipart(null,"file",f,name,"application/octet-stream"),c,null));return;
        case ASTROBOX: octoUpload(e,f,name,c); return;
        case REPETIER: {Map<String,String> fields=fields("a","upload");String group=e.extras.get("group");if(group!=null&&!group.isEmpty())fields.put("group",group);request("POST",e.url("printer/model/"+PrinterEndpoint.encodeComponent(e.storage)),api(e),NetworkHttp.multipart(fields,"filename",f,name,"application/octet-stream"),c,null);return;}
        case MKS: {JSONObject j=json(request("POST",e.url("upload?X-Filename="+query(name)),null,NetworkHttp.file(f),c,null));if(errorCode(j)!=0)throw new IOException(L.t("MKS отклонил файл."));return;}
        case ESP3D: request("POST",e.url("upload_serial"),null,NetworkHttp.multipart(null,"file",f,LegacyHostFiles.short83(name),"application/octet-stream"),c,null);return;
        case CREALITYPRINT: {Map<String,String> fields=fields("path","");request("POST",e.url("upload/"+PrinterEndpoint.encodeComponent(name.replace(' ','_'))),bearer(e),NetworkHttp.multipart(fields,"file",f,name.replace(' ','_'),"application/octet-stream"),c,null);return;}
        case FLASHFORGE: if(flashHttp(e)){Map<String,String> h=fields("serialNumber",serial(e));h.put("checkCode",e.secret());h.put("fileSize",Long.toString(f.length()));for(String k:new String[]{"printNow","levelingBeforePrint","flowCalibration","firstLayerInspection","timeLapseVideo","useMatlStation"})h.put(k,"false");h.put("gcodeToolCnt","1");h.put("materialMappings","W10=");checkCode(json(request("POST",flashUri(e,"uploadGcode"),h,NetworkHttp.multipart(null,"gcodeFile",f,LegacyHostFiles.flashforgeName(name),"application/octet-stream"),c,null)));}else flashTcpUpload(e,f,name,c);return;
        case ELEGOOLINK: {int m=elegoo(e);if(m==0){octoUpload(e,f,name,c);return;}elegooUpload(e,f,name,m,c);return;}
        default:throw new IOException(L.t("Неизвестный протокол принтера."));
        }
    }
    /** This endpoint is explicitly a start-print operation, never called by upload(). */
    static void uploadAndStart(PrinterEndpoint e,File file,String name,NetworkHttp.Cancellation c)throws IOException {
        if(e.type!=PrinterEndpoint.Type.ULTIMAKER)throw new IOException(L.t("Протокол не поддерживает этот способ запуска."));c=token(c);PrinterEndpoint.encodeComponent(name);File copy=LegacyHostFiles.griffinCopy(file,c);try{JSONObject reply=json(digestRequest(e,"POST",e.url("api/v1/print_job"),NetworkHttp.multipart(fields("jobname",name),"file",copy,name,"application/octet-stream"),c));if(errorCode(reply)!=0)throw new IOException(L.t("UltiMaker отклонил задание. Проверьте состояние принтера перед повтором."));}finally{copy.delete();}
    }
    private static int errorCode(JSONObject object)throws IOException {
        if(!object.has("err"))return 0;
        Object value=object.opt("err");
        if(!(value instanceof Number) && !(value instanceof String))throw new IOException(L.t("Сервер вернул некорректный код ошибки."));
        try {String s=value.toString();if(!s.matches("-?[0-9]+"))throw new NumberFormatException();return Integer.parseInt(s);}
        catch(NumberFormatException bad){throw new IOException(L.t("Сервер вернул некорректный код ошибки."),bad);}
    }
    /** Initiates printer-side authorization; user must approve on the UltiMaker panel. */
    static String[] requestUltiMakerPairing(PrinterEndpoint e,NetworkHttp.Cancellation c)throws IOException {
        if(e.type!=PrinterEndpoint.Type.ULTIMAKER)throw new IOException(L.t("Неверный протокол сопряжения."));
        String boundary="Kepler"+UUID.randomUUID().toString().replace("-","");StringBuilder form=new StringBuilder();
        for(String key:new String[]{"application","user","OrcaSlicer"})form.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(key).append("\"\r\n\r\nOrcaSlicer\r\n");form.append("--").append(boundary).append("--\r\n");
        byte[] bytes=form.toString().getBytes(StandardCharsets.UTF_8);JSONObject j=json(request("POST",e.url("api/v1/auth/request"),null,NetworkHttp.bytes(bytes),token(c),"multipart/form-data; boundary="+boundary));
        String id=j.optString("id"),key=j.optString("key");if(id.isEmpty()||key.isEmpty())throw new IOException(L.t("UltiMaker не вернул учётные данные."));return new String[]{id,key};
    }
    /** Call after requesting pairing, while waiting for approval on the printer. */
    static String ultiMakerPairingStatus(PrinterEndpoint e,NetworkHttp.Cancellation c)throws IOException {
        if(e.type!=PrinterEndpoint.Type.ULTIMAKER||e.username.isEmpty())throw new IOException(L.t("Не указан ID UltiMaker."));
        URI uri=e.url("api/v1/auth/check/"+PrinterEndpoint.encodeComponent(e.username));NetworkHttp.Response r=NetworkHttp.request("GET",uri,null,null,token(c));if(r.status==401)r=digestRequest(e,"GET",uri,null,token(c));else r.requireSuccess();
        String state=json(r).optString("message");if(state.equals("unknown"))return "waiting";if(state.equals("authorized")||state.equals("unauthorized"))return state;throw new IOException(L.t("Неизвестный ответ сопряжения UltiMaker."));
    }
    private static boolean duetConnect(PrinterEndpoint e,NetworkHttp.Cancellation c)throws IOException {
        NetworkHttp.Response r;
        try{r=NetworkHttp.request("GET",e.url("rr_connect?password="+query(e.secret().isEmpty()?"reprap":e.secret())+"&time="+query(timestamp())),null,null,c);}catch(InterruptedIOException x){throw x;}catch(IOException unavailable){get(e,"machine/status",null,c);return false;}
        if(r.status==404||r.status==405){get(e,"machine/status",null,c);return false;}r.requireSuccess();JSONObject j=json(r);if(j.optInt("err",-1)!=0)throw new IOException(L.t("Duet отклонил подключение: проверьте пароль."));return true;
    }
    private static void flashTcpUpload(PrinterEndpoint e,File f,String name,NetworkHttp.Cancellation c)throws IOException {
        long size=f.length();String file=LegacyHostFiles.flashforgeName(name);String fw=e.extras.get("firmware");if(fw!=null&&!fw.isEmpty()&&!fw.equals("legacy")&&!fw.equals("klipper"))throw new IOException(L.t("Неизвестная прошивка Flashforge."));
        try(LegacyTcpConsole s=console(e,8899,c)){s.command("~M601 S1");s.command("~M115");s.command("klipper".equals(fw)?"~M640":"~M650");s.command("~M119");}
        try(LegacyTcpConsole s=console(e,8899,c)){s.command("~M28 "+size+" 0:/user/"+file);s.file(f,size);}
        for(int i=0;i<30;i++){c.check();try{Thread.sleep(100);}catch(InterruptedException x){Thread.currentThread().interrupt();throw new InterruptedIOException(L.t("Операция отменена."));}}
        try(LegacyTcpConsole s=console(e,8899,c)){s.command("~M29");}
    }
    private static int elegoo(PrinterEndpoint e) {String model=e.extras.get("model");if(model==null||!model.startsWith("Elegoo Centauri"))return 0;return model.trim().endsWith("2")?2:1;}
    private static String elegooToken(PrinterEndpoint e){return e.secret().isEmpty()?"123456":e.secret();}
    private static JSONObject cc2(PrinterEndpoint e,String method,String path,Map<String,String> headers,NetworkHttp.Body body,NetworkHttp.Cancellation c)throws IOException {Map<String,String> h=headers==null?new LinkedHashMap<>():headers;h.put("X-Token",elegooToken(e));JSONObject j=json(request(method,e.url(path),h,body,c,null));if(j.optInt("error_code",-1)!=0)throw new IOException(L.t("Elegoo Centauri 2 отклонил запрос."));return j;}
    private static void elegooUpload(PrinterEndpoint e,File f,String name,int model,NetworkHttp.Cancellation c)throws IOException {
        long total=f.length();String md5=LegacyHostFiles.md5(f,c),uuid=UUID.randomUUID().toString();for(long pos=0;pos<total;){c.check();long len=Math.min(1048576,total-pos);if(model==1){Map<String,String> fields=fields("Check","1");fields.put("S-File-MD5",md5);fields.put("Offset",Long.toString(pos));fields.put("Uuid",uuid);fields.put("TotalSize",Long.toString(total));JSONObject j=json(request("POST",e.url("uploadFile/upload"),null,multipartSlice(fields,f,name,pos,len),c,null));if(!"000000".equals(j.optString("code")))throw new IOException(L.t("Elegoo отклонил часть файла."));}else{Map<String,String> h=fields("Content-Range","bytes "+pos+"-"+(pos+len-1)+"/"+total);h.put("X-File-Name",name);h.put("X-File-MD5",md5);cc2(e,"PUT","upload",h,NetworkHttp.fileSlice(f,pos,len),c);}pos+=len;c.progress(pos,total);}
    }
    private static NetworkHttp.Body multipartSlice(Map<String,String> fields,File file,String name,long offset,long length)throws IOException {
        String b="Kepler"+UUID.randomUUID().toString().replace("-","");ByteArrayOutputStream bytes=new ByteArrayOutputStream();for(Map.Entry<String,String> x:fields.entrySet())bytes.write(("--"+b+"\r\nContent-Disposition: form-data; name=\""+x.getKey()+"\"\r\n\r\n"+x.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));String q=name.replace("\\","\\\\").replace("\"","\\\"");bytes.write(("--"+b+"\r\nContent-Disposition: form-data; name=\"File\"; filename=\""+q+"\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));final byte[] pre=bytes.toByteArray(),post=("\r\n--"+b+"--\r\n").getBytes(StandardCharsets.UTF_8);final NetworkHttp.Body part=NetworkHttp.fileSlice(file,offset,length);return new NetworkHttp.Body(){public long length(){return pre.length+length+post.length;}public String contentType(){return "multipart/form-data; boundary="+b;}public void write(OutputStream out,NetworkHttp.Cancellation c)throws IOException{out.write(pre);part.write(out,c);out.write(post);}};
    }
    private static void octoUpload(PrinterEndpoint e,File f,String name,NetworkHttp.Cancellation c)throws IOException {Map<String,String> form=fields("print","false");form.put("path","");request("POST",e.url("api/files/local"),api(e),NetworkHttp.multipart(form,"file",f,name,"application/octet-stream"),c,null);}
    private static NetworkHttp.Response digestRequest(PrinterEndpoint e,String method,URI uri,NetworkHttp.Body body,NetworkHttp.Cancellation c)throws IOException {
        if(e.username.isEmpty()||e.secret().isEmpty())throw new IOException(L.t("Для UltiMaker нужны имя и пароль после сопряжения."));NetworkHttp.Response challenge=NetworkHttp.request("GET",e.url("api/v1/auth/verify"),null,null,c);if(challenge.status!=401)throw new IOException(L.t("UltiMaker не предоставил Digest challenge."));PrinterDigestAuth.Challenge parsed=PrinterDigestAuth.parse(challenge.header("WWW-Authenticate"));String target=uri.getRawPath()+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery());Map<String,String> h=fields("Authorization",PrinterDigestAuth.authorization(parsed,e.username,e.secret(),method,target,1,PrinterDigestAuth.nonce()));return request(method,uri,h,body,c,null);
    }
    private static void flashAirSuccess(NetworkHttp.Response r)throws IOException {if(!r.text().toUpperCase(Locale.ROOT).contains("SUCCESS"))throw new IOException(L.t("FlashAir не подтвердил запись."));}
    private static void checkCode(JSONObject j)throws IOException {if((j.has("code")&&j.optInt("code",-1)!=0)||(j.has("err")&&j.optInt("err",-1)!=0))throw new IOException(L.t("Flashforge отклонил запрос."));}
    private static JSONObject flashCredentials(PrinterEndpoint e)throws IOException {try{return new JSONObject().put("serialNumber",serial(e)).put("checkCode",e.secret());}catch(JSONException x){throw new IOException(L.t("Недопустимые параметры Flashforge."),x);}}
    private static String serial(PrinterEndpoint e)throws IOException {String s=e.extras.get("serial");if(s==null||s.trim().isEmpty()||e.secret().isEmpty())throw new IOException(L.t("Для Flashforge нужны серийный номер и код проверки."));return s;}
    private static boolean flashHttp(PrinterEndpoint e)throws IOException {String m=e.extras.get("flashforge_mode");if(m==null||m.isEmpty())return e.extras.containsKey("serial");if(m.equals("http"))return true;if(m.equals("tcp"))return false;throw new IOException(L.t("Неизвестный режим Flashforge."));}
    private static URI flashUri(PrinterEndpoint e,String path)throws IOException {try{int port=port(e,"http_port",8898);return new URI(e.baseUri.getScheme(),null,e.baseUri.getHost(),port,"/"+path,null,null);}catch(URISyntaxException x){throw new IOException(L.t("Неверный адрес Flashforge."),x);}}
    private static LegacyTcpConsole console(PrinterEndpoint e,int fallback,NetworkHttp.Cancellation c)throws IOException {if("https".equalsIgnoreCase(e.baseUri.getScheme()))throw new IOException(L.t("Этот протокол TCP не поддерживает TLS; укажите HTTP-адрес только для доверенной сети."));return new LegacyTcpConsole(e.baseUri.getHost(),port(e,"control_port",fallback),c);}
    private static int port(PrinterEndpoint e,String key,int fallback)throws IOException {String v=e.extras.get(key);if(v==null||v.isEmpty())return fallback;try{int p=Integer.parseInt(v);if(p<1||p>65535)throw new NumberFormatException();return p;}catch(NumberFormatException x){throw new IOException(L.t("Некорректный порт принтера."));}}
    private static NetworkHttp.Cancellation token(NetworkHttp.Cancellation c){return c==null?new NetworkHttp.Cancellation():c;}
    private static NetworkHttp.Response get(PrinterEndpoint e,String path,Map<String,String> h,NetworkHttp.Cancellation c)throws IOException{return request("GET",e.url(path),h,null,c,null);}
    private static NetworkHttp.Response request(String method,URI uri,Map<String,String> headers,NetworkHttp.Body body,NetworkHttp.Cancellation c,String contentType)throws IOException {if(contentType!=null){headers=headers==null?new LinkedHashMap<>():new LinkedHashMap<>(headers);headers.put("Content-Type",contentType);}NetworkHttp.Response r=NetworkHttp.request(method,uri,headers,body,c);r.requireSuccess();return r;}
    private static String jsonString(String body)throws IOException {try{Object value=new JSONTokener(body).nextValue();if(value instanceof String)return (String)value;throw new IOException(L.t("Принтер вернул некорректную строку JSON."));}catch(JSONException x){throw new IOException(L.t("Принтер вернул некорректный JSON."),x);}}
    private static JSONObject json(NetworkHttp.Response r)throws IOException {try{return new JSONObject(r.text());}catch(JSONException x){throw new IOException(L.t("Принтер вернул некорректный JSON."),x);}}
    private static Map<String,String> fields(String key,String value){Map<String,String> m=new LinkedHashMap<>();m.put(key,value);return m;}
    private static Map<String,String> api(PrinterEndpoint e){return e.secret().isEmpty()?new LinkedHashMap<>():fields("X-Api-Key",e.secret());}
    private static Map<String,String> bearer(PrinterEndpoint e)throws IOException {if(e.secret().isEmpty())throw new IOException(L.t("Для Creality Print нужен токен."));return fields("Authorization","Bearer "+e.secret());}
    private static String query(String s){StringBuilder b=new StringBuilder();char[] h="0123456789ABCDEF".toCharArray();for(byte x:s.getBytes(StandardCharsets.UTF_8)){int v=x&255;if((v>='a'&&v<='z')||(v>='A'&&v<='Z')||(v>='0'&&v<='9')||v=='-'||v=='_'||v=='.'||v=='~')b.append((char)v);else b.append('%').append(h[v>>>4]).append(h[v&15]);}return b.toString();}
    private static String timestamp(){return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.ROOT).format(new Date());}
}
