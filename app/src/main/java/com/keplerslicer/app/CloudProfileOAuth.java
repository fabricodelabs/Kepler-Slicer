package com.keplerslicer.app;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** External-browser OAuth with upstream's localhost redirect and S256 code exchange. */
public final class CloudProfileOAuth implements Closeable {
    public static final String AUTH_BASE="https://auth.orcaslicer.com";
    public static final String PUBLISHABLE_KEY="sb_publishable_lvVe_whOi80SU9BPSxM1kA_tbt9AbR_";
    public interface TokenTransport { CloudProfileSync.Response post(String relativePath,String json) throws IOException; }
    private final String verifier,state,challenge;
    private final ServerSocket listener;
    private boolean consumed;
    public CloudProfileOAuth() throws IOException {
        SecureRandom random=new SecureRandom();byte[] a=new byte[32],b=new byte[32];random.nextBytes(a);random.nextBytes(b);
        verifier=Base64.getUrlEncoder().withoutPadding().encodeToString(a);state=Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        try {challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IOException(ex);}
        listener=new ServerSocket();listener.setReuseAddress(false);listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),41172));listener.setSoTimeout(180000);
    }
    private static String enc(String s) {try{return URLEncoder.encode(s,"UTF-8");}catch(java.io.UnsupportedEncodingException ex){throw new AssertionError(ex);}}
    /** Only providers supported by the upstream login page are offered. User signs in in browser. */
    public String authorizationUrl(String provider) {
        if(!"google".equals(provider)&&!"github".equals(provider)&&!"apple".equals(provider))throw new IllegalArgumentException("Unsupported provider");
        return AUTH_BASE+"/auth/v1/authorize?provider="+enc(provider)+"&redirect_to="+enc("http://localhost:41172/callback")+"&code_challenge="+enc(challenge)+"&code_challenge_method=S256&state="+enc(state);
    }
    /** Blocks on localhost; call from background thread. Strict state protects the token exchange. */
    public JSONObject awaitAndExchange(TokenTransport transport) throws IOException {
        if(consumed)throw new IOException("Login session already used");
        try(Socket client=listener.accept()) {
            client.setSoTimeout(10000);InputStream in=client.getInputStream();ByteArrayOutputStream request=new ByteArrayOutputStream();
            int prev=-1,c;while((c=in.read())!=-1){if(request.size()>=8192)throw new IOException("Callback too long");request.write(c);if(prev=='\r'&&c=='\n')break;prev=c;}
            String line=request.toString("UTF-8").trim();String[] parts=line.split(" ");
            if(parts.length!=3 || !"GET".equals(parts[0]))throw new IOException("Invalid OAuth callback");
            URI target=URI.create(parts[1]);if(target.isAbsolute()||!"/callback".equals(target.getPath()))throw new IOException("Unexpected OAuth callback");
            String code=codeFrom(target.getRawQuery());
            // The page sends the user straight back to the app (the browser asks before leaving).
            String back="intent://cloud-login#Intent;scheme=keplerslicer;package=com.keplerslicer.app;end";
            byte[] response=("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n\r\n<!doctype html><meta name=viewport content='width=device-width'><meta http-equiv=refresh content='0;url="+back+"'>"
                +"<body style='font-family:sans-serif;background:#2E323A;color:#fff;padding:32px'><h2>Kepler Slicer</h2><p>\u0412\u0445\u043e\u0434 \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d. \u0412\u0435\u0440\u043d\u0438\u0442\u0435\u0441\u044c \u0432 \u043f\u0440\u0438\u043b\u043e\u0436\u0435\u043d\u0438\u0435.</p><p><a style='color:#5EE0C8' href='"+back+"'>\u041e\u0442\u043a\u0440\u044b\u0442\u044c Kepler Slicer</a></p></body>").getBytes(StandardCharsets.UTF_8);
            client.getOutputStream().write(response);
            return exchange(code,transport);
        } catch(IllegalArgumentException ex) {throw new IOException("Invalid OAuth callback",ex);}
    }

    /** True once a callback (from the socket or a pasted address) has been used. */
    public synchronized boolean isConsumed(){return consumed;}

    /**
     * Fallback when the phone froze the app while the browser was in front and the browser shows an error for
     * http://localhost:41172/callback?...: the user copies that address and the app finishes the same exchange.
     */
    public JSONObject exchangeFromUrl(String address,TokenTransport transport) throws IOException {
        if(address==null)throw new IOException("Empty address");
        try {
            URI target=URI.create(address.trim());
            String host=target.getHost()==null?"":target.getHost();
            if(!("localhost".equalsIgnoreCase(host)||"127.0.0.1".equals(host))||target.getPort()!=41172||!"/callback".equals(target.getPath()))throw new IOException(L.t("Это не адрес возврата входа. Скопируйте адрес страницы, начинающийся с http://localhost:41172/callback."));
            String code=codeFrom(target.getRawQuery());
            return exchange(code,transport);
        } catch(IllegalArgumentException ex){throw new IOException(L.t("Не удалось разобрать адрес."),ex);}
    }

    private String codeFrom(String raw) throws IOException {
        Map<String,String> q=new HashMap<>();
        if(raw!=null)for(String pair:raw.split("&")){String[] kv=pair.split("=",2);String k=URLDecoder.decode(kv[0],"UTF-8"),v=kv.length==2?URLDecoder.decode(kv[1],"UTF-8"):"";if(q.put(k,v)!=null)throw new IOException("Duplicate callback parameter");}
        String error=q.get("error_description")!=null?q.get("error_description"):q.get("error");
        if(error!=null&&!error.isEmpty())throw new IOException(L.t("Сервер входа вернул ошибку: ")+error);
        if(!MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),q.getOrDefault("state","").getBytes(StandardCharsets.UTF_8)))throw new IOException(L.t("Адрес относится к другой попытке входа. Начните вход заново."));
        String code=q.get("code");if(code==null||code.isEmpty())throw new IOException("OAuth callback lacks code");
        return code;
    }

    private JSONObject exchange(String code,TokenTransport transport) throws IOException {
        synchronized(this){if(consumed)throw new IOException("Login session already used");consumed=true;}
        close();
        {
            try {
                String body=new JSONObject().put("auth_code",code).put("code_verifier",verifier).toString();
                CloudProfileSync.Response result=transport.post("auth/v1/token?grant_type=pkce",body);
                if(result.status<200||result.status>=300){String why="";try{JSONObject e=new JSONObject(result.body);why=e.optString("msg",e.optString("error_description",e.optString("error","")));}catch(JSONException ignored){}throw new IOException(L.t("Сервер облака отклонил вход (HTTP ")+result.status+")"+(why.isEmpty()?"":": "+why)+L.t(". Начните вход заново."));}
                JSONObject session=new JSONObject(result.body);if(session.optString("access_token").isEmpty()||session.optString("refresh_token").isEmpty()||session.optJSONObject("user")==null||session.optJSONObject("user").optString("id").isEmpty())throw new IOException("Incomplete cloud profile session");
                return session;
            }catch(JSONException ex){throw new IOException("Invalid cloud profile session",ex);}
        }
    }
    @Override public void close() throws IOException {listener.close();}
}
