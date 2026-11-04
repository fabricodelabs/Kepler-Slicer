package com.keplerslicer.app;
import android.content.Context;
import java.io.*;
import java.nio.file.Files;
/** Explicit native/profile boundary fake; production CMS and structural checks remain real. */
final class NativeMaterialPreflight {
    interface Publication{void run()throws IOException;}
    interface Hook{void run()throws IOException;}
    static int checks,publications,lastMode,lastPlate;
    static boolean rejectValidation,rejectPublication;
    static Hook nextValidation;
    static String lastMaterials;
    static byte[] lastOriginal;
    static final class Validated{
        void publish(Publication operation)throws IOException{publications++;if(rejectPublication){rejectPublication=false;throw new IOException("Simulated changed native profile context");}operation.run();}
    }
    static Validated validate(Context c,File original,int plate,int mode,String materials)throws IOException{
        if(ProjectMaterialValidation.forRestore(original.getName(),mode,materials).length()==0)return null;
        checks++;lastMode=mode;lastPlate=plate;lastMaterials=materials;lastOriginal=Files.readAllBytes(original.toPath());
        Hook hook=nextValidation;nextValidation=null;if(hook!=null)hook.run();
        if(rejectValidation){rejectValidation=false;throw new IOException("Simulated mismatch with native project slot/nozzle dimensions");}
        return new Validated();
    }
}
