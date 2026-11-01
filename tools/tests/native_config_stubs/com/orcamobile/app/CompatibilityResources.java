package com.orcamobile.app;
import android.content.Context;
import java.io.File;
import java.io.IOException;
final class CompatibilityResources {
    final File resources,data;
    private CompatibilityResources(File root) {resources=new File(root,"resources");data=new File(root,"data");resources.mkdirs();data.mkdirs();}
    static CompatibilityResources prepareTriple(Context context,String printer,String process,String filament,SettingsState.Snapshot snapshot) throws IOException {
        if (!"printer_vendor".equals(printer)||!"process_vendor".equals(process)||!"filament_vendor".equals(filament)||snapshot==null)
            throw new IOException("Incorrect identity");
        return new CompatibilityResources(context.getCacheDir());
    }
}
