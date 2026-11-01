package com.orcamobile.app;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** HTTP Digest challenge response for PrusaLink's API (RFC 7616 / legacy RFC 2069). */
final class PrinterDigestAuth {
    static final class Challenge {
        final String realm, nonce, opaque, algorithm, qop;
        final boolean algorithmAdvertised;
        final Charset charset;
        Challenge(Map<String,String> p) throws IOException {
            realm = required(p,"realm"); nonce = required(p,"nonce"); opaque = p.get("opaque");
            algorithmAdvertised=p.containsKey("algorithm"); algorithm = algorithmAdvertised ? p.get("algorithm") : "MD5";
            String alg=algorithm.toUpperCase(Locale.ROOT);
            if (!alg.equals("MD5") && !alg.equals("MD5-SESS") && !alg.equals("SHA-256") && !alg.equals("SHA-256-SESS")) throw new IOException("Неподдерживаемый алгоритм Digest.");
            String offered=p.get("qop"); String selected=null;
            if(offered!=null) { for(String item:offered.split(",")) if(item.trim().equalsIgnoreCase("auth")) selected="auth"; if(selected==null) throw new IOException("Сервер не поддерживает Digest qop=auth."); }
            qop=selected;
            String cs=p.get("charset");
            if(cs!=null&&!cs.equalsIgnoreCase("UTF-8")) throw new IOException("Неподдерживаемая кодировка Digest.");
            charset=cs==null?StandardCharsets.ISO_8859_1:StandardCharsets.UTF_8;
        }
        private static String required(Map<String,String> p,String key) throws IOException { String s=p.get(key); if(s==null||s.isEmpty()) throw new IOException("Неполный запрос Digest: "+key); return s; }
    }
    static Challenge parse(String header) throws IOException {
        if(header==null||header.length()>8192||header.indexOf('\r')>=0||header.indexOf('\n')>=0) throw new IOException("Отсутствует корректный вызов Digest.");
        int start=findDigest(header); if(start<0) throw new IOException("Сервер не предложил Digest.");
        int n=start+6; Map<String,String> p=new LinkedHashMap<>();
        while(n<header.length()) {
            while(n<header.length()&&(Character.isWhitespace(header.charAt(n))||header.charAt(n)==',')) n++;
            if(n>=header.length()) break;
            int begin=n; while(n<header.length()&&(Character.isLetterOrDigit(header.charAt(n))||header.charAt(n)=='-'||header.charAt(n)=='_')) n++;
            if(begin==n) throw new IOException("Некорректный параметр Digest.");
            String key=header.substring(begin,n).toLowerCase(Locale.ROOT);
            while(n<header.length()&&Character.isWhitespace(header.charAt(n))) n++;
            if(n>=header.length()||header.charAt(n)!='=') break; // next authentication scheme
            n++; while(n<header.length()&&Character.isWhitespace(header.charAt(n))) n++;
            String value;
            if(n<header.length()&&header.charAt(n)=='"') {
                n++; StringBuilder b=new StringBuilder(); boolean closed=false;
                while(n<header.length()) { char c=header.charAt(n++); if(c=='"'){closed=true;break;} if(c=='\\'){if(n>=header.length()) throw new IOException("Некорректное экранирование Digest.");c=header.charAt(n++);} if(c<0x20||c==0x7f) throw new IOException("Недопустимый символ Digest."); b.append(c); }
                if(!closed) throw new IOException("Незакрытая строка Digest."); value=b.toString();
            } else { begin=n; while(n<header.length()&&header.charAt(n)!=','&&!Character.isWhitespace(header.charAt(n))) n++; value=header.substring(begin,n); }
            if(p.put(key,value)!=null) throw new IOException("Повторный параметр Digest.");
        }
        return new Challenge(p);
    }
    private static int findDigest(String header) {
        boolean quoted=false, escaped=false;
        for(int i=0;i+6<header.length();i++) {
            char c=header.charAt(i); if(escaped){escaped=false;continue;} if(quoted&&c=='\\'){escaped=true;continue;} if(c=='"'){quoted=!quoted;continue;}
            if(!quoted&&(i==0||header.charAt(i-1)==','||Character.isWhitespace(header.charAt(i-1)))&&header.regionMatches(true,i,"Digest",0,6)&&Character.isWhitespace(header.charAt(i+6))) return i;
        }
        return -1;
    }
    static String nonce() { byte[] bytes=new byte[16]; new SecureRandom().nextBytes(bytes); return hex(bytes); }
    static String authorization(Challenge c,String user,String password,String method,String uri,long nonceCount,String cnonce) throws IOException {
        if(user==null||password==null||method==null||uri==null||cnonce==null||nonceCount<1||nonceCount>0xffffffffL) throw new IOException("Неверные параметры Digest.");
        check(user); check(password); check(method); check(uri); check(cnonce);
        String upper=c.algorithm.toUpperCase(Locale.ROOT); String hash=upper.startsWith("SHA-256")?"SHA-256":"MD5";
        String a1=digest(hash,user+":"+c.realm+":"+password,c.charset);
        if(upper.endsWith("-SESS")) a1=digest(hash,a1+":"+c.nonce+":"+cnonce,StandardCharsets.ISO_8859_1);
        String a2=digest(hash,method+":"+uri,StandardCharsets.UTF_8), nc=String.format(Locale.ROOT,"%08x",nonceCount);
        String response=c.qop==null?digest(hash,a1+":"+c.nonce+":"+a2,StandardCharsets.ISO_8859_1):digest(hash,a1+":"+c.nonce+":"+nc+":"+cnonce+":"+c.qop+":"+a2,StandardCharsets.ISO_8859_1);
        StringBuilder out=new StringBuilder("Digest username=\"").append(quote(user)).append("\", realm=\"").append(quote(c.realm)).append("\", nonce=\"").append(quote(c.nonce)).append("\", uri=\"").append(quote(uri)).append("\", response=\"").append(response).append('"');
        if(c.algorithmAdvertised) out.append(", algorithm=").append(c.algorithm);
        if(c.opaque!=null) out.append(", opaque=\"").append(quote(c.opaque)).append('"');
        if(c.qop!=null) out.append(", qop=auth, nc=").append(nc).append(", cnonce=\"").append(quote(cnonce)).append('"');
        else if(upper.endsWith("-SESS")) out.append(", cnonce=\"").append(quote(cnonce)).append('"');
        return out.toString();
    }
    private static void check(String s) throws IOException { for(int i=0;i<s.length();i++) if(s.charAt(i)<0x20||s.charAt(i)==0x7f) throw new IOException("Недопустимый символ Digest."); }
    private static String quote(String s) throws IOException { check(s); return s.replace("\\","\\\\").replace("\"","\\\""); }
    private static String digest(String algorithm,String text,Charset charset) throws IOException {
        try {
            ByteBuffer bytes=charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
            MessageDigest md=MessageDigest.getInstance(algorithm); md.update(bytes); return hex(md.digest());
        } catch(CharacterCodingException|NoSuchAlgorithmException e) { throw new IOException("Не удалось вычислить Digest.",e); }
    }
    private static String hex(byte[] bytes) { char[] table="0123456789abcdef".toCharArray(), result=new char[bytes.length*2]; for(int i=0;i<bytes.length;i++){int n=bytes[i]&255;result[i*2]=table[n>>>4];result[i*2+1]=table[n&15];}return new String(result); }
}
