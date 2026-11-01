package com.orcamobile.app;
import android.content.Context;
final class OrcaProcessFilamentCatalog {
    static final String PROCESS="process",FILAMENT="filament";
    static Profile process=new Profile("VQ","Process","VQ/process/q.json"),filament=new Profile("VF","Filament","VF/filament/f.json");
    static Profile loadSelection(Context c,String kind){return PROCESS.equals(kind)?process:filament;}
    static final class Profile {final String vendor,name,path;Profile(String vendor,String name,String path){this.vendor=vendor;this.name=name;this.path=path;}}
}
