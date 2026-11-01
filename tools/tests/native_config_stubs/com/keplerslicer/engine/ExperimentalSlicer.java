package com.keplerslicer.engine;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
public final class ExperimentalSlicer {
    public static byte[] metadata;
    public static String ini="";
    public static boolean metadataFails,iniFails;
    public static int metadataCalls,iniCalls;
    public static File lastJob;
    private static void verify(String pv,String qv,String fv,String pn,String qn,String fn) {
        if (!"printer_vendor".equals(pv)||!"process_vendor".equals(qv)||!"filament_vendor".equals(fv)||
            !"printer_name".equals(pn)||!"process_name".equals(qn)||!"filament_name".equals(fn))
            throw new IllegalStateException("Incorrect captured identity");
    }
    public static byte[] processSettingsMetadata(String pv,String qv,String fv,String pn,String qn,String fn,String resources,String data) {
        verify(pv,qv,fv,pn,qn,fn);metadataCalls++;
        if(metadataFails)throw new IllegalStateException("metadata unavailable");
        return metadata;
    }
    public static String resolveSystemProfiles(String pv,String qv,String fv,String pn,String qn,String fn,String resources,String data,String job) {
        verify(pv,qv,fv,pn,qn,fn);iniCalls++;
        try {
            lastJob=new File(job);
            if (!lastJob.getParentFile().getCanonicalFile().equals(new File(data).getCanonicalFile())||!lastJob.mkdir())
                throw new IllegalStateException("Invalid private job");
            if(iniFails)throw new IllegalStateException("Resolved INI did not round-trip exactly; it must not be sliced");
            File out=new File(lastJob,"resolved.ini");
            Files.write(out.toPath(),ini.getBytes(StandardCharsets.UTF_8));
            return out.getAbsolutePath();
        } catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
