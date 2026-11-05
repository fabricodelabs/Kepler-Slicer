package com.keplerslicer.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** Pure policy and release selection, independently testable without Android or network I/O. */
public final class OnlineRules {
    private OnlineRules(){}
    public static boolean lanHost(String value){
        if(value==null)return false;String h=value.toLowerCase(Locale.ROOT);if(h.startsWith("[")&&h.endsWith("]"))h=h.substring(1,h.length()-1);
        if(h.equals("localhost")||h.equals("::1"))return true;
        if(h.endsWith(".local"))return h.matches("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+local");
        if(h.indexOf(':')>=0)return h.startsWith("fe80:")||h.startsWith("fc")||h.startsWith("fd");
        if(!h.contains("."))return h.matches("[a-z0-9-]+");
        String[] parts=h.split("\\.");if(parts.length!=4)return false;int[] n=new int[4];try{for(int i=0;i<4;i++){if(!parts[i].matches("[0-9]{1,3}"))return false;n[i]=Integer.parseInt(parts[i]);if(n[i]>255)return false;}}catch(NumberFormatException e){return false;}
        return n[0]==10||n[0]==127||(n[0]==192&&n[1]==168)||(n[0]==172&&n[1]>=16&&n[1]<=31)||(n[0]==169&&n[1]==254);
    }
    public static JSONObject selectRelease(JSONArray releases,boolean stableOnly){for(int n=0;n<releases.length();n++){JSONObject release=releases.optJSONObject(n);if(release==null||release.optBoolean("draft",false)||(stableOnly&&release.optBoolean("prerelease",false)))continue;String tag=release.optString("tag_name"),url=release.optString("html_url");if(!tag.isEmpty()&&url.startsWith("https://github.com/OrcaSlicer/OrcaSlicer/releases/"))return release;}return null;}
    public static boolean logEnabled(int configured,int event){return configured>=0&&configured<=5&&event>=0&&event<=configured;}
    /** All resolved candidates must be private, so a LAN-looking name cannot authorize a public target. */
    public static boolean privateAddresses(java.net.InetAddress[] addresses){if(addresses==null||addresses.length==0)return false;for(java.net.InetAddress address:addresses){if(address==null)return false;byte[] bytes=address.getAddress();boolean uniqueV6=bytes.length==16&&((bytes[0]&0xfe)==0xfc);if(!address.isLoopbackAddress()&&!address.isSiteLocalAddress()&&!address.isLinkLocalAddress()&&!uniqueV6)return false;}return true;}
}
