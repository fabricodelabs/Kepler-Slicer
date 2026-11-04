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
    PrinterDiscovery(Context c,Listener callback){manager=(NsdManager)c.getSystemService(Context.NSD_SERVICE);this.callback=callback;}
    synchronized void start(){if(active)return;active=true;for(String type:new String[]{"_octoprint._tcp.","_moonraker._tcp.","_prusa._tcp.","_prusalink._tcp.","_ultimaker._tcp.","_http._tcp."}){
        NsdManager.DiscoveryListener l=new NsdManager.DiscoveryListener(){public void onDiscoveryStarted(String t){}public void onDiscoveryStopped(String t){}public void onServiceLost(NsdServiceInfo s){}public void onStartDiscoveryFailed(String t,int code){callback.message("Обнаружение "+t+": код "+code);}public void onStopDiscoveryFailed(String t,int code){}public void onServiceFound(NsdServiceInfo service){synchronized(PrinterDiscovery.this){if(!active)return;String key=service.getServiceName()+"\n"+service.getServiceType();if(!seen.add(key))return;resolving.add(service);next();}}};listeners.add(l);try{manager.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,l);}catch(RuntimeException e){callback.message("DNS-SD недоступен для "+type);}
    }}
    private synchronized void next(){if(!active||resolverBusy||resolving.isEmpty())return;resolverBusy=true;NsdServiceInfo service=resolving.remove();try{manager.resolveService(service,new NsdManager.ResolveListener(){public void onResolveFailed(NsdServiceInfo s,int error){done();}public void onServiceResolved(NsdServiceInfo s){synchronized(PrinterDiscovery.this){if(active&&s.getHost()!=null&&s.getPort()>0){String host=s.getHost().getHostAddress();if(host.indexOf(':')>=0)host="["+host.replace("%","%25")+"]";callback.found(s.getServiceName(),"http://"+host+":"+s.getPort(),s.getServiceType());}}done();}private void done(){synchronized(PrinterDiscovery.this){resolverBusy=false;next();}}});}catch(RuntimeException e){resolverBusy=false;next();}}
    synchronized void stop(){active=false;for(NsdManager.DiscoveryListener l:listeners)try{manager.stopServiceDiscovery(l);}catch(RuntimeException ignored){}listeners.clear();resolving.clear();seen.clear();}
}
