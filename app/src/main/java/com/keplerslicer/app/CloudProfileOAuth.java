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
            Map<String,String> q=new HashMap<>();String raw=target.getRawQuery();
            if(raw!=null)for(String pair:raw.split("&")){String[] kv=pair.split("=",2);String k=URLDecoder.decode(kv[0],"UTF-8"),v=kv.length==2?URLDecoder.decode(kv[1],"UTF-8"):"";if(q.put(k,v)!=null)throw new IOException("Duplicate callback parameter");}
            if(!MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),q.getOrDefault("state","").getBytes(StandardCharsets.UTF_8)))throw new IOException("OAuth state mismatch");
            String code=q.get("code");if(code==null||code.isEmpty())throw new IOException("OAuth callback lacks code");
            byte[] response="HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nConnection: close\r\n\r\nYou may return to Kepler Slicer.".getBytes(StandardCharsets.UTF_8);client.getOutputStream().write(response);
            consumed=true;close();
            try {
                String body=new JSONObject().put("auth_code",code).put("code_verifier",verifier).toString();
                CloudProfileSync.Response result=transport.post("auth/v1/token?grant_type=pkce",body);
                if(result.status<200||result.status>=300)throw new IOException("Cloud profile login HTTP "+result.status);
                JSONObject session=new JSONObject(result.body);if(session.optString("access_token").isEmpty()||session.optString("refresh_token").isEmpty()||session.optJSONObject("user")==null||session.optJSONObject("user").optString("id").isEmpty())throw new IOException("Incomplete cloud profile session");
                return session;
            }catch(JSONException ex){throw new IOException("Invalid cloud profile session",ex);}
        } catch(IllegalArgumentException ex) {throw new IOException("Invalid OAuth callback",ex);}
    }
    @Override public void close() throws IOException {listener.close();}
}
