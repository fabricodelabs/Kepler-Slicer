package com.keplerslicer.app;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** File transformations used by the upstream local printer hosts. */
final class LegacyHostFiles {
    private LegacyHostFiles() { }
    static String md5(File file, NetworkHttp.Cancellation cancel) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5"); byte[] bytes = new byte[32768];
            try (FileInputStream in = new FileInputStream(file)) {
                int n; while ((n = in.read(bytes)) != -1) { cancel.check(); if(n>0) md.update(bytes,0,n); }
            }
            char[] hex = "0123456789abcdef".toCharArray(), out = new char[32]; byte[] sum=md.digest();
            for(int i=0;i<sum.length;i++){int b=sum[i]&255;out[i*2]=hex[b>>>4];out[i*2+1]=hex[b&15];}
            return new String(out);
        } catch (NoSuchAlgorithmException impossible) { throw new IOException(L.t("Не удалось вычислить контрольную сумму."),impossible); }
    }
    static String short83(String filename) {
        PrinterEndpoint.encodeComponent(filename);
        int p=filename.lastIndexOf('.'); String stem=p>0?filename.substring(0,p):filename, extension=p>0?filename.substring(p+1):"";
        String cleanStem=ascii83(stem),cleanExt=ascii83(extension);
        boolean changed=!cleanStem.equals(stem)||!cleanExt.equals(extension)||cleanStem.length()>8||cleanExt.length()>3;
        if(cleanStem.isEmpty())throw new IllegalArgumentException(L.t("Неверное имя файла ESP3D."));
        if(changed) {
            try {
                byte[] digest=MessageDigest.getInstance("SHA-256").digest(filename.getBytes(StandardCharsets.UTF_8));
                char[] hex="0123456789abcdef".toCharArray();
                StringBuilder h=new StringBuilder();for(byte b:digest){h.append(hex[(b&255)>>>4]).append(hex[b&15]);if(h.length()>=5)break;}
                String prefix=(cleanStem+"__").substring(0,2);
                cleanStem=prefix+"_"+h.substring(0,5);
            }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        }
        if(cleanExt.length()>3)cleanExt=cleanExt.substring(0,3);
        return cleanExt.isEmpty()?cleanStem:cleanStem+"."+cleanExt;
    }
    private static String ascii83(String input) {
        StringBuilder out=new StringBuilder();input.codePoints().forEach(c->out.append((c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='_'||c=='-'?(char)c:'_'));return out.toString();
    }
    static String flashforgeName(String filename) {
        PrinterEndpoint.encodeComponent(filename); StringBuilder out=new StringBuilder();
        for(int i=0;i<filename.length();i++) { char c=filename.charAt(i); out.append((c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='.'||c=='_'||c=='-'?c:'_'); }
        if(out.length()==0 || out.toString().equals(".") || out.toString().equals("..")) throw new IllegalArgumentException(L.t("Неверное имя файла Flashforge."));
        return out.toString();
    }
    static String fatTimestamp(long epochMillis) {
        java.util.Calendar c=java.util.Calendar.getInstance(); c.setTimeInMillis(epochMillis);
        int year=Math.max(1980,Math.min(2107,c.get(java.util.Calendar.YEAR)));
        long n=((long)(year-1980)<<25)|((long)(c.get(java.util.Calendar.MONTH)+1)<<21)|((long)c.get(java.util.Calendar.DAY_OF_MONTH)<<16)|
                ((long)c.get(java.util.Calendar.HOUR_OF_DAY)<<11)|((long)c.get(java.util.Calendar.MINUTE)<<5)|(c.get(java.util.Calendar.SECOND)/2);
        return "0x"+Long.toHexString(n);
    }
    private static final Pattern TIME=Pattern.compile("^; estimated printing time \\(normal mode\\) = (?:(\\d+)h )?(\\d+)m (\\d+)s.*$");
    /** Upstream rewrites Griffin headers; preserve the original file, use a private temporary copy. */
    static File griffinCopy(File source, NetworkHttp.Cancellation cancel) throws IOException {
        if(source==null||!source.isFile()||source.length()==0) throw new IOException(L.t("Файл задания недоступен."));
        long seconds=0;
        try(BufferedReader in=reader(source)) {
            String line; while((line=in.readLine())!=null) { cancel.check(); Matcher m=TIME.matcher(line); if(m.matches()) {
                try {long h=m.group(1)==null?0:Long.parseLong(m.group(1)), min=Long.parseLong(m.group(2)), sec=Long.parseLong(m.group(3)); seconds=Math.addExact(Math.addExact(Math.multiplyExact(h,3600),Math.multiplyExact(min,60)),sec);}
                catch(ArithmeticException|NumberFormatException e){throw new IOException(L.t("Некорректное время печати."),e);} break; }
            }
        }
        File result=File.createTempFile("kepler-griffin-", ".gcode",source.getParentFile()); boolean ok=false, header=false;
        try(BufferedReader in=reader(source); BufferedWriter out=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(result),StandardCharsets.UTF_8))) {
            String line; while((line=in.readLine())!=null) { cancel.check();
                if(!header) {if(!line.startsWith(";START_OF_HEADER"))continue; header=true;}
                if(line.startsWith("; generated by the engineSlicer")) line=line.replace(':','-');
                if(line.startsWith(";PRINT.TIME:")) line=";PRINT.TIME:"+seconds;
                out.write(line); out.newLine();
            }
            if(!header)throw new IOException(L.t("В G-code отсутствует заголовок Griffin UltiMaker."));
            ok=true; return result;
        } finally { if(!ok) result.delete(); }
    }
    private static BufferedReader reader(File file) throws IOException {return new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8));}
}
