package com.keplerslicer.app;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Streaming HTTP transport shared by the original upstream host protocol ports. */
final class NetworkHttp {
    interface RequestPolicy { void check(URI uri)throws IOException; }
    private static volatile RequestPolicy requestPolicy;
    static void setRequestPolicy(RequestPolicy policy){requestPolicy=policy;}
    private static final Set<Cancellation> active=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static void cancelActive(){for(Cancellation token:active)token.cancel();}
    interface ProgressListener { void uploaded(long sent,long total); }
    static final class Cancellation {
        private volatile boolean cancelled;
        private Closeable closer;
        private final ProgressListener listener;
        Cancellation(){this(null);}
        Cancellation(ProgressListener listener){this.listener=listener;}
        synchronized void cancel(){cancelled=true;if(closer!=null)try{closer.close();}catch(IOException ignored){}}
        boolean isCancelled(){return cancelled;}
        void check() throws InterruptedIOException {if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Операция отменена.");}
        synchronized void setCloser(Closeable value)throws IOException{check();closer=value;}
        synchronized void clearCloser(Closeable value){if(closer==value)closer=null;}
        void progress(long sent,long total){if(listener!=null)listener.uploaded(sent,total);}
    }
    interface Body {
        long length();
        void write(OutputStream out,Cancellation cancel)throws IOException;
        default String contentType(){return "application/octet-stream";}
    }
    static final class Response {
        final int status; final byte[] body; final Map<String,List<String>> headers;
        Response(int status,byte[] body,Map<String,List<String>> headers){this.status=status;this.body=body;this.headers=headers;}
        String header(String name){for(Map.Entry<String,List<String>> h:headers.entrySet())if(h.getKey()!=null&&h.getKey().equalsIgnoreCase(name)&&h.getValue()!=null&&!h.getValue().isEmpty())return h.getValue().get(0);return "";}
        String text(){return new String(body,StandardCharsets.UTF_8);}
        void requireSuccess()throws IOException{if(status<200||status>=300)throw new IOException("Сервер вернул HTTP "+status+".");}
    }
    static Response request(String method,URI uri,Map<String,String> headers,Body body,Cancellation cancel)throws IOException{return request(method,uri,headers,body,cancel,2*1024*1024);}
    static Response request(String method,URI uri,Map<String,String> headers,Body body,Cancellation cancel,int responseLimit)throws IOException{
        if(cancel==null)cancel=new Cancellation();cancel.check();
        if(uri==null||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getRawFragment()!=null||(!"http".equalsIgnoreCase(uri.getScheme())&&!"https".equalsIgnoreCase(uri.getScheme())))throw new IOException("Нужен корректный HTTP(S)-адрес без пароля в URL.");
        RequestPolicy policy=requestPolicy;if(policy!=null)policy.check(uri);
        if(!Arrays.asList("GET","POST","PUT","DELETE","HEAD","OPTIONS","PATCH","PROPFIND","MKCOL").contains(method))throw new IOException("Неизвестный метод HTTP.");
        if(responseLimit<0||responseLimit>32*1024*1024)throw new IOException("Некорректный предел ответа.");
        HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(8000);connection.setReadTimeout(20000);connection.setUseCaches(false);connection.setRequestMethod(method);
        connection.setRequestProperty("Accept","application/json, */*");connection.setRequestProperty("Accept-Encoding","identity");connection.setRequestProperty("User-Agent","KeplerSlicer-Android/1.0");
        if(headers!=null)for(Map.Entry<String,String> h:headers.entrySet()){
            if(h.getKey()==null||!h.getKey().matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")||h.getValue()==null||h.getValue().indexOf('\r')>=0||h.getValue().indexOf('\n')>=0)throw new IOException("Недопустимый заголовок HTTP.");
            connection.setRequestProperty(h.getKey(),h.getValue());
        }
        final Closeable close=connection::disconnect;
        cancel.setCloser(close);active.add(cancel);
        try{
            cancel.check();RequestPolicy latest=requestPolicy;if(latest!=null)latest.check(uri);
            if(body!=null){
                connection.setDoOutput(true);if(connection.getRequestProperty("Content-Type")==null)connection.setRequestProperty("Content-Type",body.contentType());
                long total=body.length();if(total>=0)connection.setFixedLengthStreamingMode(total);else connection.setChunkedStreamingMode(65536);
                final Cancellation token=cancel;
                try(OutputStream output=connection.getOutputStream()){
                    OutputStream counting=new FilterOutputStream(output){long sent;@Override public void write(int b)throws IOException{token.check();out.write(b);token.progress(++sent,total);}@Override public void write(byte[] b,int offset,int length)throws IOException{token.check();out.write(b,offset,length);sent+=length;token.progress(sent,total);}};
                    body.write(counting,cancel);counting.flush();
                }
            }
            cancel.check();latest=requestPolicy;if(latest!=null)latest.check(uri);cancel.check();int code=connection.getResponseCode();
            Map<String,List<String>> copy=new LinkedHashMap<>();for(Map.Entry<String,List<String>> entry:connection.getHeaderFields().entrySet())if(entry.getKey()!=null&&entry.getValue()!=null)copy.put(entry.getKey(),Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            InputStream stream=code>=400?connection.getErrorStream():connection.getInputStream();ByteArrayOutputStream data=new ByteArrayOutputStream();
            if(stream!=null)try(InputStream in=stream){byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1){cancel.check();if(data.size()>(responseLimit-n))throw new IOException("Ответ сервера превышает допустимый размер.");data.write(buffer,0,n);}}
            cancel.check();return new Response(code,data.toByteArray(),Collections.unmodifiableMap(copy));
        }catch(IOException e){if(cancel.isCancelled())throw new InterruptedIOException("Операция отменена.");throw e;}finally{active.remove(cancel);cancel.clearCloser(close);connection.disconnect();}
    }
    static Body bytes(byte[] value){final byte[] copy=value.clone();return new Body(){public long length(){return copy.length;}public void write(OutputStream out,Cancellation c)throws IOException{c.check();out.write(copy);}};}
    static Body file(File file)throws IOException{return fileSlice(file,0,file.length());}
    static Body fileSlice(File file,long offset,long length)throws IOException{
        if(file==null||!file.isFile()||offset<0||length<0||offset>file.length()||length>file.length()-offset)throw new IOException("Файл или диапазон передачи недоступен.");
        final long expected=file.length();return new Body(){public long length(){return length;}public void write(OutputStream out,Cancellation c)throws IOException{
            if(file.length()!=expected)throw new IOException("Файл изменился перед передачей.");
            try(RandomAccessFile input=new RandomAccessFile(file,"r")){input.seek(offset);byte[] buffer=new byte[65536];long left=length;while(left>0){c.check();int n=input.read(buffer,0,(int)Math.min(left,buffer.length));if(n<0)throw new EOFException("Файл изменился во время передачи.");out.write(buffer,0,n);left-=n;}}
        }};
    }
    static Body multipart(Map<String,String> fields,String fieldName,File file,String filename,String mime)throws IOException{
        final String boundary="Kepler"+UUID.randomUUID().toString().replace("-","");ByteArrayOutputStream before=new ByteArrayOutputStream();
        if(fields!=null)for(Map.Entry<String,String> field:fields.entrySet()){String name=quoted(field.getKey());String value=field.getValue();if(value==null)throw new IOException("Пустое поле формы.");before.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n").getBytes(StandardCharsets.UTF_8));}
        before.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+quoted(fieldName)+"\"; filename=\""+quoted(filename)+"\"\r\nContent-Type: "+(mime==null?"application/octet-stream":safeMime(mime))+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        final byte[] prefix=before.toByteArray(),suffix=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);final Body payload=file(file);
        return new Body(){public long length(){return prefix.length+payload.length()+suffix.length;}public String contentType(){return "multipart/form-data; boundary="+boundary;}public void write(OutputStream out,Cancellation c)throws IOException{c.check();out.write(prefix);payload.write(out,c);c.check();out.write(suffix);}};
    }
    private static String safeMime(String value)throws IOException{if(!value.matches("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+"))throw new IOException("Недопустимый тип файла.");return value;}
    private static String quoted(String value)throws IOException{if(value==null||value.length()>1024||value.indexOf('\r')>=0||value.indexOf('\n')>=0||value.indexOf('\0')>=0)throw new IOException("Некорректное имя файла или поля.");return value.replace("\\","\\\\").replace("\"","\\\"");}
}
