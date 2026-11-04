package com.keplerslicer.app;
import org.json.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;

public final class OnlineRulesTest {
    private static int checks;
    private static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
    private static JSONObject release(String tag,boolean preview,boolean draft,String url)throws Exception{return new JSONObject().put("tag_name",tag).put("prerelease",preview).put("draft",draft).put("html_url",url);}
    public static void main(String[] args)throws Exception{
        for(String host:new String[]{"localhost","printer","printer.local","127.0.0.1","10.1.2.3","172.16.0.1","172.31.255.255","192.168.0.2","169.254.1.1","::1","[::1]","fe80::1","fd12::2","fc00::3"})check(OnlineRules.lanHost(host),"LAN host "+host);
        for(String host:new String[]{"api.orcaslicer.com","api.bambulab.com","example.com","127.0.0.1.evil.test","192.168.1.1.evil.test","172.15.0.1","172.32.0.1","192.169.0.1","8.8.8.8","2001:4860:4860::8888","10.2.3.999","10.2.3","","evil..local"})check(!OnlineRules.lanHost(host),"Public/invalid host "+host);
        JSONArray list=new JSONArray().put(release("draft",false,true,"https://github.com/OrcaSlicer/OrcaSlicer/releases/draft")).put(release("beta",true,false,"https://github.com/OrcaSlicer/OrcaSlicer/releases/beta")).put(release("stable",false,false,"https://github.com/OrcaSlicer/OrcaSlicer/releases/stable"));
        check(OnlineRules.selectRelease(list,true).getString("tag_name").equals("stable"),"Stable channel filters draft and prerelease");check(OnlineRules.selectRelease(list,false).getString("tag_name").equals("beta"),"Preview channel accepts prerelease");check(OnlineRules.selectRelease(new JSONArray().put(release("foreign",false,false,"https://evil.test/releases/v1")),false)==null,"Foreign release links do not become update destinations");check(OnlineRules.logEnabled(2,1)&&!OnlineRules.logEnabled(2,3),"Log threshold affects actual event inclusion");check(!OnlineRules.logEnabled(-1,0)&&!OnlineRules.logEnabled(6,0),"Invalid log thresholds fail closed");
        check(OnlineRules.privateAddresses(new InetAddress[]{InetAddress.getByAddress(new byte[]{10,1,2,3}),InetAddress.getByName("fd00::1")}),"All-private DNS answers are allowed");check(!OnlineRules.privateAddresses(new InetAddress[]{InetAddress.getByAddress(new byte[]{10,1,2,3}),InetAddress.getByAddress(new byte[]{8,8,8,8})}),"A LAN-looking name with any public DNS answer is blocked");check(!OnlineRules.privateAddresses(new InetAddress[0]),"An unresolved LAN name is not authorized");
        AtomicInteger requests=new AtomicInteger();HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",exchange->{requests.incrementAndGet();byte[] body="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
        try{URI uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/");NetworkHttp.setRequestPolicy(value->{throw new java.io.IOException("offline fixture");});try{NetworkHttp.request("GET",uri,Collections.emptyMap(),null,new NetworkHttp.Cancellation());throw new AssertionError("Offline policy must block transport");}catch(java.io.IOException expected){checks++;}check(requests.get()==0,"Blocked policy sends no HTTP request");NetworkHttp.setRequestPolicy(value->{if(!OnlineRules.lanHost(value.getHost()))throw new java.io.IOException("stealth fixture");});check(NetworkHttp.request("GET",uri,Collections.emptyMap(),null,new NetworkHttp.Cancellation()).status==200,"Stealth fixture permits local HTTP");check(requests.get()==1,"One explicit LAN request reached loopback");}finally{NetworkHttp.setRequestPolicy(null);server.stop(0);}
        System.out.println("Online policy/release/transport: "+checks+" checks passed; only loopback HTTP used");
    }
}
