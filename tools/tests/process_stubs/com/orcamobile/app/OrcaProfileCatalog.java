package com.orcamobile.app;
import android.content.Context;
final class OrcaProfileCatalog {
    static final String UPSTREAM_REVISION="revision";
    static Profile current=new Profile("VP","Printer","VP/machine/p.json",false);
    static Profile loadSelection(Context c){return current;}
    static final class Profile {final String vendor,name,path;final boolean imported;Profile(String vendor,String name,String path,boolean imported){this.vendor=vendor;this.name=name;this.path=path;this.imported=imported;}}
}
