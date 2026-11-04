package com.keplerslicer.app;
import android.content.Context;
import java.io.*;
import java.nio.file.Files;
import org.json.JSONObject;
/** STEP native/cache boundary fake. Real CurrentModelStore owns all publication and snapshots. */
final class StepImportPreferences {
    static Options captured=new Options(.003,.5,false),lastPreview,lastDerived;
    static boolean failPreview;
    static int proofRequests;
    static File proofOriginal,proofDerived,proofTarget;
    static Options proofOptions;
    static final class Options {
        final double linearMm,angularRad;final boolean split;
        Options(double linear,double angular,boolean split){if(!Double.isFinite(linear)||linear<=0||!Double.isFinite(angular)||angular<=0||angular>Math.PI)throw new IllegalArgumentException();linearMm=linear;angularRad=angular;this.split=split;}
        JSONObject toJson(){try{return new JSONObject().put("schema","kepler.step-options.v1").put("linear_mm",linearMm).put("angular_rad",angularRad).put("split",split);}catch(Exception e){throw new AssertionError(e);}}
        static Options parse(JSONObject json)throws IOException{try{return new Options(json.getDouble("linear_mm"),json.getDouble("angular_rad"),json.getBoolean("split"));}catch(Exception e){throw new IOException(e);}}
        boolean same(Options other){return other!=null&&linearMm==other.linearMm&&angularRad==other.angularRad&&split==other.split;}
    }
    static boolean isStep(File file){String name=file.getName().toLowerCase(java.util.Locale.ROOT);return name.endsWith(".step")||name.endsWith(".stp");}
    static Options capture(Context c){return captured;}
    static void saveOptions(File original,Options options)throws IOException{Files.write(new File(original.getPath()+".step-settings.json").toPath(),options.toJson().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static Options saved(File source)throws IOException{try{return Options.parse(new JSONObject(Files.readString(new File(source.getPath()+".step-settings.json").toPath())));}catch(Exception e){throw new IOException(e);}}
    static ImportedModelPreview preview(Context c,File source,Options options)throws IOException{if(failPreview){failPreview=false;throw new IOException("Simulated OCCT failure");}lastPreview=options;return ImportedModelPreview.read(c,source,-1);}
    static File derive(Context c,File source,Options options)throws IOException{lastDerived=options;File result=File.createTempFile("fake-tessellation-",".3mf",c.getCacheDir());Files.write(result.toPath(),new byte[]{3,1,4,1,5});return result;}
    // Record that production CMS asks the real helper to bind source/options
    // and derivative. This fake does not claim to verify native provenance.
    static void copyVerifiedProvenance(File original,Options options,File derived,File target)throws IOException{proofRequests++;proofOriginal=original;proofOptions=options;proofDerived=derived;proofTarget=target;}
}
