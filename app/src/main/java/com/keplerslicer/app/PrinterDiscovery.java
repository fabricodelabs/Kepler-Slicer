package com.keplerslicer.app;
import android.content.Context;
import android.net.nsd.*;
import java.util.*;

/** Android DNS-SD discovers advertised services; it does not probe arbitrary network hosts. */
final class PrinterDiscovery {
    interface Listener{void found(String name,String url,String serviceType);void message(String message);}
    private final NsdManager manager;private final Listener callback;
    private final List<NsdManager.DiscoveryListener> listeners=new ArrayList<>();private final Set<String> seen=new HashSet<>();
    private final java.util.ArrayDeque<NsdServiceInfo> resolving=new java.util.ArrayDeque<>();private boolean active,resolverBusy;
    private final Context context;private Thread ssdp;private java.net.DatagramSocket ssdpBroadcast;private java.net.MulticastSocket ssdpMulticast;private android.net.wifi.WifiManager.MulticastLock multicastLock;
    PrinterDiscovery(Context c,Listener callback){manager=(NsdManager)c.getSystemService(Context.NSD_SERVICE);this.callback=callback;context=c.getApplicationContext();}
    /** Bambu Lab printers announce over SSDP (UDP 2021 broadcast, 239.255.255.250:1990), not DNS-SD. */
    private void startSsdp(){
        try{android.net.wifi.WifiManager wifi=(android.net.wifi.WifiManager)context.getSystemService(Context.WIFI_SERVICE);if(wifi!=null){multicastLock=wifi.createMulticastLock("kepler-bambu-ssdp");multicastLock.setReferenceCounted(false);multicastLock.acquire();}}catch(RuntimeException ignored){}
        ssdp=new Thread(()->{
            try{
                java.net.DatagramSocket b=new java.net.DatagramSocket(null);b.setReuseAddress(true);b.setBroadcast(true);b.bind(new java.net.InetSocketAddress(2021));b.setSoTimeout(400);ssdpBroadcast=b;
                java.net.MulticastSocket m;try{m=new java.net.MulticastSocket(null);m.setReuseAddress(true);m.bind(new java.net.InetSocketAddress(1990));m.joinGroup(java.net.InetAddress.getByName("239.255.255.250"));m.setSoTimeout(400);ssdpMulticast=m;}catch(java.io.IOException|RuntimeException e){m=null;}
                byte[] ask=BambuSsdp.search();
                ask(b,ask);
                byte[] buf=new byte[2048];long lastAsk=System.currentTimeMillis();
                while(active&&!Thread.currentThread().isInterrupted()){
                    for(java.net.DatagramSocket socket:new java.net.DatagramSocket[]{b,m}){
                        if(socket==null)continue;
                        java.net.DatagramPacket packet=new java.net.DatagramPacket(buf,buf.length);
                        try{socket.receive(packet);}catch(java.net.SocketTimeoutException timeout){continue;}
                        BambuSsdp.Device d=BambuSsdp.parse(packet.getData(),packet.getLength(),packet.getAddress()==null?null:packet.getAddress().getHostAddress());
                        if(d==null)continue;
                        boolean fresh;synchronized(PrinterDiscovery.this){fresh=active&&seen.add("bambu:"+d.serial);}
                        if(fresh)callback.found(d.name+(d.model.isEmpty()||d.name.equals(d.model)?"":" · "+d.model),d.ip,"_bambu:"+d.serial);
                    }
                    if(System.currentTimeMillis()-lastAsk>5000){lastAsk=System.currentTimeMillis();ask(b,ask);}
                }
            }catch(java.io.IOException e){if(active)callback.message(L.t("Поиск Bambu Lab недоступен: ")+e.getMessage());}
            finally{closeSsdp();}
        },"bambu-ssdp");ssdp.setDaemon(true);ssdp.start();
    }
    private static void ask(java.net.DatagramSocket b,byte[] ask){
        try{b.send(new java.net.DatagramPacket(ask,ask.length,java.net.InetAddress.getByName("239.255.255.250"),1990));}catch(java.io.IOException ignored){}
        try{b.send(new java.net.DatagramPacket(ask,ask.length,java.net.InetAddress.getByName("255.255.255.255"),2021));}catch(java.io.IOException ignored){}
    }
    private synchronized void closeSsdp(){if(ssdpBroadcast!=null){ssdpBroadcast.close();ssdpBroadcast=null;}if(ssdpMulticast!=null){ssdpMulticast.close();ssdpMulticast=null;}if(multicastLock!=null&&multicastLock.isHeld()){try{multicastLock.release();}catch(RuntimeException ignored){}}multicastLock=null;}
    synchronized void start(){if(active)return;active=true;startSsdp();for(String type:new String[]{"_octoprint._tcp.","_moonraker._tcp.","_prusa._tcp.","_prusalink._tcp.","_ultimaker._tcp.","_http._tcp."}){
        NsdManager.DiscoveryListener l=new NsdManager.DiscoveryListener(){public void onDiscoveryStarted(String t){}public void onDiscoveryStopped(String t){}public void onServiceLost(NsdServiceInfo s){}public void onStartDiscoveryFailed(String t,int code){callback.message(L.t("Обнаружение ")+t+L.t(": код ")+code);}public void onStopDiscoveryFailed(String t,int code){}public void onServiceFound(NsdServiceInfo service){synchronized(PrinterDiscovery.this){if(!active)return;if(service.getServiceType()!=null&&service.getServiceType().startsWith("_http.")&&BambuSsdp.isPaperPrinter(service.getServiceName()))return;String key=service.getServiceName()+"\n"+service.getServiceType();if(!seen.add(key))return;resolving.add(service);next();}}};listeners.add(l);try{manager.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,l);}catch(RuntimeException e){callback.message(L.t("DNS-SD недоступен для ")+type);}
    }}
    private synchronized void next(){if(!active||resolverBusy||resolving.isEmpty())return;resolverBusy=true;NsdServiceInfo service=resolving.remove();try{manager.resolveService(service,new NsdManager.ResolveListener(){public void onResolveFailed(NsdServiceInfo s,int error){done();}public void onServiceResolved(NsdServiceInfo s){synchronized(PrinterDiscovery.this){if(active&&s.getHost()!=null&&s.getPort()>0){String host=s.getHost().getHostAddress();if(host.indexOf(':')>=0)host="["+host.replace("%","%25")+"]";callback.found(s.getServiceName(),"http://"+host+":"+s.getPort(),s.getServiceType());}}done();}private void done(){synchronized(PrinterDiscovery.this){resolverBusy=false;next();}}});}catch(RuntimeException e){resolverBusy=false;next();}}
    synchronized void stop(){active=false;if(ssdp!=null){ssdp.interrupt();ssdp=null;}closeSsdp();for(NsdManager.DiscoveryListener l:listeners)try{manager.stopServiceDiscovery(l);}catch(RuntimeException ignored){}listeners.clear();resolving.clear();seen.clear();}
}
