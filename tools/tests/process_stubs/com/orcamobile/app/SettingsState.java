package com.orcamobile.app;
import android.content.Context;import java.io.IOException;import java.util.*;
final class SettingsState {
 static String profileFingerprint="profile-a";static boolean mismatch=false;
 static final class Overlay {final String name,path;Overlay(String name,String path){this.name=name;this.path=path;}}
 static final class Snapshot {final String fingerprint;final Map<String,Overlay> overlays;Snapshot(){fingerprint=profileFingerprint;overlays=new HashMap<>();OrcaProfileCatalog.Profile p=OrcaProfileCatalog.current;OrcaProcessFilamentCatalog.Profile q=OrcaProcessFilamentCatalog.process,f=OrcaProcessFilamentCatalog.filament;overlays.put("printer",new Overlay(p.name,p.path));overlays.put("process",new Overlay(mismatch?"Wrong":q.name,q.path));overlays.put("filament",new Overlay(f.name,f.path));}}
 static Snapshot capture(Context c){return new Snapshot();}
 static String requireOverlayPath(String kind,String path)throws IOException {String[] parts=path.split("/");if(parts.length<3)throw new IOException("bad path");return parts[0];}
}
