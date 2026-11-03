package com.orcamobile.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Looper;
import android.text.InputType;
import android.util.AtomicFile;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.keplerslicer.engine.ExperimentalSlicer;
import com.orcamobile.app.glass.GlassSwitch;
import com.orcamobile.app.glass.GlassUi;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** STEP originals are immutable; the cached 3MF is only their fixed tessellation. */
final class StepImportPreferences {
    private static final String SCHEMA="kepler.step-options.v1";
    private static final String CACHE_SCHEMA="kepler.step-cache.v1";
    private static final String CONVERTER="kepler.step-import.v1";
    private static final String LINEAR="step_linear_deflection_mm",ANGULAR="step_angular_deflection_rad",SPLIT="step_split_compounds";
    private static final int METADATA_LIMIT=16384;
    private StepImportPreferences() {}

    static final class Options {
        final double linearMm,angularRad;
        final boolean split;
        Options(double linear,double angular,boolean splitCompounds) {
            if(!Double.isFinite(linear)||linear<=0)throw new IllegalArgumentException("STEP linear deflection must be finite and positive");
            if(!Double.isFinite(angular)||angular<=0||angular>Math.PI)throw new IllegalArgumentException("STEP angular deflection must be between 0 and pi radians");
            linearMm=linear;angularRad=angular;split=splitCompounds;
        }
        JSONObject toJson() {
            try{return new JSONObject().put("schema",SCHEMA).put("linear_mm",linearMm).put("angular_rad",angularRad).put("split",split);}
            catch(org.json.JSONException impossible){throw new IllegalStateException(impossible);}
        }
        static Options parse(String text)throws IOException {
            try{return parse(new JSONObject(text));}catch(IOException e){throw e;}catch(Exception e){throw new IOException("Invalid saved STEP options",e);}
        }
        static Options parse(JSONObject object)throws IOException {
            try{
                if(object==null||!SCHEMA.equals(object.getString("schema"))||!(object.get("linear_mm") instanceof Number)||!(object.get("angular_rad") instanceof Number)||!(object.get("split") instanceof Boolean))throw new IOException("Invalid saved STEP options");
                return new Options(object.getDouble("linear_mm"),object.getDouble("angular_rad"),object.getBoolean("split"));
            }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Invalid saved STEP options",e);}
        }
        boolean same(Options other){return other!=null&&Double.compare(linearMm,other.linearMm)==0&&Double.compare(angularRad,other.angularRad)==0&&split==other.split;}
    }

    static boolean isStep(File file){if(file==null)return false;String name=file.getName().toLowerCase(Locale.ROOT);return name.endsWith(".step")||name.endsWith(".stp");}
    static Options capture(Context context){
        try{return new Options(Double.parseDouble(GeneralPreferences.prefs(context).getString(LINEAR,"0.003")),Double.parseDouble(GeneralPreferences.prefs(context).getString(ANGULAR,"0.5")),GeneralPreferences.prefs(context).getBoolean(SPLIT,false));}
        catch(RuntimeException invalid){return new Options(.003,.5,false);}
    }
    static File optionsFile(File original){return new File(original.getParentFile(),original.getName()+".step-settings.json");}
    static synchronized void saveOptions(File immutableOriginal,Options options)throws IOException {
        if(options==null)throw new IOException("Missing STEP options");
        requireOriginal(immutableOriginal);
        final String before=sha256(immutableOriginal);
        try{
            JSONObject record=new JSONObject().put("schema","kepler.step-source.v1").put("source_sha256",before).put("source_bytes",immutableOriginal.length()).put("options",options.toJson());
            if(!before.equals(sha256(immutableOriginal)))throw new IOException("STEP source changed while saving its options");
            writeJson(optionsFile(immutableOriginal),record);
        }catch(org.json.JSONException e){throw new IOException("Cannot save STEP options",e);}
    }
    static synchronized Options saved(File original)throws IOException {
        requireOriginal(original);
        try{
            JSONObject record=readJson(optionsFile(original));
            if(!"kepler.step-source.v1".equals(record.getString("schema"))||record.getLong("source_bytes")!=original.length()||!record.getString("source_sha256").equals(sha256(original)))throw new IOException("Saved STEP options do not match the original file");
            return Options.parse(record.getJSONObject("options"));
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Missing or invalid saved STEP options",e);}
    }

    /** Worker only. Never derives from a sampled preview, nor changes the source. */
    static synchronized File derive(Context context,File immutableSource,Options options)throws IOException {
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("STEP tessellation requires a worker thread");
        if(options==null)throw new IOException("Missing STEP options");
        requireOriginal(immutableSource);interrupted();
        Context app=context.getApplicationContext();
        final String sourceHash=sha256(immutableSource);
        final String key=sha256((CONVERTER+"\n"+installIdentity(app)+"\n"+sourceHash+"\n"+options.toJson()).getBytes(StandardCharsets.UTF_8));
        final File data=new File(app.getCacheDir(),"step-engine/data");mkdir(data);
        final File index=new File(data,key+".json");
        File cached=null;
        try{cached=readCache(index,data,sourceHash,options);}
        catch(IOException stale){interrupted(); /* Rebuild an invalid private cache; original remains unchanged. */}
        if(cached!=null){if(!sourceHash.equals(sha256(immutableSource)))throw new IOException("STEP source changed during cache verification");return cached;}
        final File job=new File(data,"step-"+UUID.randomUUID());
        boolean complete=false;
        try{
            File engineRoot=new File(app.getFilesDir(),"slicer-engine");mkdir(engineRoot);
            File resources=new File(engineRoot,"resources-6d2869c4");SliceActivity.prepareResources(app,resources);
            interrupted();
            String returned=ExperimentalSlicer.prepareStep(immutableSource.getAbsolutePath(),resources.getAbsolutePath(),data.getAbsolutePath(),job.getAbsolutePath(),options.linearMm,options.angularRad,options.split);
            if(returned==null)throw new IOException(app.getString(R.string.step_import_no_result));
            File derived=new File(returned).getCanonicalFile();
            if(!job.getCanonicalFile().equals(derived.getParentFile())||!derived.getName().equals("model.3mf")||!derived.isFile()||derived.length()==0)throw new IOException(app.getString(R.string.step_import_no_result));
            verifyNativeOptions(new File(job,"step-import.json"),options);
            String derivedHash=sha256(derived);interrupted();
            if(!sourceHash.equals(sha256(immutableSource)))throw new IOException(app.getString(R.string.step_import_source_changed));
            JSONObject record=new JSONObject().put("schema",CACHE_SCHEMA).put("source_sha256",sourceHash).put("options",options.toJson()).put("job",job.getName()).put("derived_sha256",derivedHash).put("derived_bytes",derived.length());
            writeJson(index,record);complete=true;return derived;
        }catch(IOException e){throw e;}catch(LinkageError|RuntimeException e){throw new IOException(app.getString(R.string.step_import_engine_failed,message(e)),e);}
        catch(Exception e){throw new IOException(app.getString(R.string.step_import_engine_failed,message(e)),e);}
        finally{if(!complete)deleteOwnedJob(job,data);}
    }

    static ImportedModelPreview preview(Context context,File source,Options options)throws IOException {
        File derived=derive(context,source,options);
        ImportedModelPreview preview=ImportedModelPreview.read(context,derived,-1);
        try{
            preview.metadata.put("format","step").put("source_format","step").put("derived_format","3mf").put("step_options",options.toJson()).put("step_import",readJson(new File(derived.getParentFile(),"step-import.json")));
            return preview;
        }catch(org.json.JSONException e){throw new IOException("Cannot describe STEP preview",e);}
    }

    /** Bind a copied print mesh to its immutable source; invoked before publishing the frozen job. */
    static synchronized void copyVerifiedProvenance(File original,Options options,File derived,File target)throws IOException {
        requireOriginal(original);if(options==null)throw new IOException("Missing STEP snapshot options");
        File parent=target.getCanonicalFile().getParentFile();
        if(parent==null||!parent.equals(original.getCanonicalFile().getParentFile())||!target.getName().equals("model.3mf")||!(original.getName().equals("model.step")||original.getName().equals("model.stp")))throw new IOException("STEP print proof requires a frozen private snapshot directory");
        JSONObject nativeProof=verifyNativeOptions(new File(derived.getParentFile(),"step-import.json"),options);
        String originalHash=sha256(original),derivedHash=sha256(derived),copiedHash=sha256(target);
        if(!derivedHash.equals(copiedHash))throw new IOException("STEP print mesh changed while copying");
        try{
            JSONObject proof=new JSONObject().put("schema","kepler.step-derivative.v1").put("original_file",original.getName()).put("source_sha256",originalHash).put("source_bytes",original.length()).put("derived_sha256",copiedHash).put("derived_bytes",target.length()).put("options",options.toJson()).put("native",nativeProof);
            if(!originalHash.equals(sha256(original))||!derivedHash.equals(sha256(derived)))throw new IOException("STEP snapshot changed during proof creation");
            writeJson(new File(parent,"step-import.json"),proof);
        }catch(org.json.JSONException e){throw new IOException("Cannot bind STEP print mesh to its source",e);}
    }

    static void request(Activity activity,Runnable accepted){request(activity,accepted,()->{});}
    static void request(Activity activity,Runnable accepted,Runnable canceled){
        if(!GeneralPreferences.prefs(activity).getBoolean("show_step_import_settings",true)){accepted.run();return;}
        final Options options=capture(activity);
        final AtomicBoolean completed=new AtomicBoolean();
        final Dialog dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        GlassUi ui=new GlassUi(activity,activity.getWindow().getDecorView(),false);
        LinearLayout body=ui.vertical();ui.pad(body,20,20,20,20);body.addView(ui.heading(activity.getString(R.string.step_import_title)));ui.space(body,10);
        body.addView(ui.text(activity.getString(R.string.step_import_detail),14,400,ui.tokens.textPrimary));ui.space(body,10);
        EditText linear=ui.edit(Double.toString(options.linearMm),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED,false);
        EditText angular=ui.edit(Double.toString(options.angularRad),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED,false);
        linear.setSelectAllOnFocus(true);angular.setSelectAllOnFocus(true);
        linear.setContentDescription(activity.getString(R.string.step_import_linear));angular.setContentDescription(activity.getString(R.string.step_import_angular));
        body.addView(ui.stackedRow(activity.getString(R.string.step_import_linear),linear));body.addView(ui.stackedRow(activity.getString(R.string.step_import_angular),angular));
        GlassSwitch split=new GlassSwitch(activity);split.setChecked(options.split);split.setContentDescription(activity.getString(R.string.step_import_split));body.addView(ui.row(activity.getString(R.string.step_import_split),split,null));ui.space(body,10);
        body.addView(ui.action(activity.getString(R.string.step_import_continue),true,()->{
            final double linearValue,angularValue;
            try{linearValue=decimal(linear.getText().toString());if(!Double.isFinite(linearValue)||linearValue<=0)throw new IllegalArgumentException();linear.setError(null);}
            catch(IllegalArgumentException invalid){linear.setError(activity.getString(R.string.step_import_linear_error));return;}
            try{angularValue=decimal(angular.getText().toString());if(!Double.isFinite(angularValue)||angularValue<=0||angularValue>Math.PI)throw new IllegalArgumentException();angular.setError(null);}
            catch(IllegalArgumentException invalid){angular.setError(activity.getString(R.string.step_import_angular_error));return;}
            final Options entered=new Options(linearValue,angularValue,split.isChecked());
            if(!GeneralPreferences.prefs(activity).edit().putString(LINEAR,Double.toString(entered.linearMm)).putString(ANGULAR,Double.toString(entered.angularRad)).putBoolean(SPLIT,entered.split).commit()){linear.setError(activity.getString(R.string.step_import_save_error));return;}
            if(completed.compareAndSet(false,true)){dialog.dismiss();accepted.run();}
        }));ui.space(body,6);body.addView(ui.action(activity.getString(R.string.step_import_cancel),false,dialog::dismiss));
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.addView(body);
        dialog.setContentView(ui.surface(scroll,ui.tokens.cardRadius));
        dialog.setOnDismissListener(ignored->{if(completed.compareAndSet(false,true))canceled.run();});
        dialog.show();if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));dialog.getWindow().setGravity(Gravity.BOTTOM);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);dialog.getWindow().setLayout(-1,-2);}
    }

    private static File readCache(File index,File data,String sourceHash,Options options)throws IOException {
        if(!index.isFile()&&!new File(index.getPath()+".bak").isFile())return null;
        try{
            JSONObject record=readJson(index);
            if(!CACHE_SCHEMA.equals(record.getString("schema"))||!sourceHash.equals(record.getString("source_sha256"))||!options.same(Options.parse(record.getJSONObject("options"))))throw new IOException("Stale STEP cache metadata");
            String name=record.getString("job");if(!name.matches("step-[a-fA-F0-9-]+"))throw new IOException("Invalid STEP cache job");
            File job=new File(data,name).getCanonicalFile();if(!data.getCanonicalFile().equals(job.getParentFile()))throw new IOException("Invalid STEP cache path");
            File derived=new File(job,"model.3mf").getCanonicalFile();
            if(!job.equals(derived.getParentFile())||!derived.isFile()||derived.length()<=0||derived.length()!=record.getLong("derived_bytes")||!record.getString("derived_sha256").equals(sha256(derived)))throw new IOException("Invalid cached STEP geometry");
            verifyNativeOptions(new File(job,"step-import.json"),options);return derived;
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Invalid STEP cache",e);}
    }
    private static JSONObject verifyNativeOptions(File file,Options options)throws IOException {
        try{
            JSONObject record=readJson(file);
            if(!CONVERTER.equals(record.getString("schema"))||!"mm".equals(record.getString("units"))||record.getLong("triangle_count")<=0||Double.compare(options.linearMm,record.getDouble("linear_deflection_mm"))!=0||Double.compare(options.angularRad,record.getDouble("angular_deflection_rad"))!=0||options.split!=record.getBoolean("split_compounds"))throw new IOException("STEP tessellation provenance does not match saved options");
            return record;
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Missing STEP tessellation provenance",e);}
    }
    private static String installIdentity(Context context)throws IOException {
        try{return context.getPackageName()+":"+context.getPackageManager().getPackageInfo(context.getPackageName(),0).lastUpdateTime;}
        catch(Exception e){throw new IOException("Cannot identify installed STEP engine",e);}
    }
    private static void requireOriginal(File file)throws IOException {if(!isStep(file)||!file.isFile()||file.length()==0)throw new IOException("Missing immutable STEP source");}
    private static void mkdir(File directory)throws IOException {if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot prepare private STEP directory");}
    private static void interrupted()throws InterruptedIOException {if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("STEP import canceled");}
    private static double decimal(String text){return Double.parseDouble(text.trim().replace(',','.'));}
    private static String message(Throwable e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private static String sha256(File source)throws IOException {
        try{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(source)){byte[] bytes=new byte[65536];int count;while((count=in.read(bytes))!=-1){interrupted();digest.update(bytes,0,count);}}return hex(digest.digest());}
        catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
    }
    private static String sha256(byte[] bytes)throws IOException {try{return hex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte value:bytes)out.append(String.format(Locale.ROOT,"%02x",value&255));return out.toString();}
    private static JSONObject readJson(File file)throws IOException {
        try(InputStream in=new AtomicFile(file).openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] bytes=new byte[2048];int count;while((count=in.read(bytes))!=-1){if(out.size()>METADATA_LIMIT-count)throw new IOException("STEP metadata is too large");out.write(bytes,0,count);}return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Invalid STEP metadata",e);}
    }
    private static void writeJson(File file,JSONObject value)throws IOException {
        AtomicFile atomic=new AtomicFile(file);FileOutputStream out=null;
        try{out=atomic.startWrite();out.write(value.toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);out=null;}
        finally{if(out!=null)atomic.failWrite(out);}
    }
    private static void deleteOwnedJob(File job,File data){
        try{if(!data.getCanonicalFile().equals(job.getCanonicalFile().getParentFile())||!job.getName().startsWith("step-"))return;deleteTree(job);}catch(IOException ignored){}
    }
    private static void deleteTree(File file){if(Files.isSymbolicLink(file.toPath())){file.delete();return;}if(file.isDirectory()){File[] entries=file.listFiles();if(entries!=null)for(File child:entries)deleteTree(child);}file.delete();}
}
