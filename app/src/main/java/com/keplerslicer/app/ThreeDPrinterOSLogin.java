package com.keplerslicer.app;
import java.io.*;import java.net.URI;import java.util.*;import org.json.*;
final class ThreeDPrinterOSLogin {
 static final class Pending {final String token;final URI browserUri;Pending(String t,URI u){token=t;browserUri=u;}}
 static final class Session {final String session,email;Session(String s,String e){session=s;email=e;}}
 static Pending generate(PrinterEndpoint e,NetworkHttp.Cancellation c)throws IOException{check(e);Map<String,String> f=new LinkedHashMap<>();f.put("app_type","plugin");f.put("app_name","OrcaSlicer");JSONObject j=post(e,"apiglobal/generate_login_token",f,c);if(!j.optBoolean("result",false))throw new IOException("3DPrinterOS отклонил запрос входа.");String t=j.optString("message");try{if(t.isEmpty()||t.length()>1024)throw new IllegalArgumentException();return new Pending(t,e.url("noauth/apiglobal_login_with_token/"+PrinterEndpoint.encodeComponent(t)));}catch(IllegalArgumentException x){throw new IOException("Некорректный токен входа 3DPrinterOS.",x);}}
 static Session poll(PrinterEndpoint e,String token,NetworkHttp.Cancellation c)throws IOException{return poll(e,token,c,180_000,1_500);}
 // Each result:true without a session is pending. The bounded deadline allows
 // a human to complete browser login without busy-polling the service.
 static Session poll(PrinterEndpoint e,String token,NetworkHttp.Cancellation c,long timeoutMs,long intervalMs)throws IOException{
  check(e);if(token==null||token.isEmpty()||token.length()>1024)throw new IOException("Отсутствует токен входа.");
  if(timeoutMs<=0||intervalMs<=0)throw new IllegalArgumentException("Invalid login polling interval");if(c==null)c=new NetworkHttp.Cancellation();
  final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMs);
  while(System.nanoTime()<deadline){c.check();JSONObject j=post(e,"apiglobal/login_with_token",Collections.singletonMap("token",token),c);
   if(!j.optBoolean("result",false))throw new IOException("3DPrinterOS отклонил вход.");JSONObject m=j.optJSONObject("message");
   if(m!=null){String s=m.optString("session"),email=m.optString("email");if(!s.isEmpty()&&!email.isEmpty())return new Session(s,email);if(!s.isEmpty())throw new IOException("3DPrinterOS не вернул адрес аккаунта.");}
   long wake=Math.min(deadline,System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(intervalMs));
   while(System.nanoTime()<wake){c.check();long remaining=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(wake-System.nanoTime());try{Thread.sleep(Math.max(1,Math.min(100,remaining)));}catch(InterruptedException x){Thread.currentThread().interrupt();throw new InterruptedIOException("Вход отменён.");}}
  }
  throw new IOException("Время подтверждения 3DPrinterOS истекло.");
 }
 private static JSONObject post(PrinterEndpoint e,String path,Map<String,String> f,NetworkHttp.Cancellation c)throws IOException{NetworkHttp.Response r=NetworkHttp.request("POST",e.url(path),Collections.singletonMap("Content-Type","application/x-www-form-urlencoded"),NetworkHttp.bytes(SimplyPrintOAuth.form(f)),c);r.requireSuccess();try{return new JSONObject(r.text());}catch(JSONException x){throw new IOException("Некорректный ответ 3DPrinterOS.",x);}}
 private static void check(PrinterEndpoint e)throws IOException{if(e==null||e.type!=PrinterEndpoint.Type.THREE_D_PRINTER_OS)throw new IOException("Неверный тип 3DPrinterOS.");}
}
