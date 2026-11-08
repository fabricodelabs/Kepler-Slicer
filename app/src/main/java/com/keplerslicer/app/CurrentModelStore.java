package com.keplerslicer.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Private copy of the most recently opened model, suitable for a later native import. */
final class CurrentModelStore {
    private static final String PREFS = "current_model";
    private static final String FILE = "file";
    private static final String NAME = "name";
    private static final String TRANSFORM = "transform";
    private static final String PLATE = "plate";
    private static final Object LOCK = new Object();
    final File file;
    final String name;
    final StlMesh mesh;
    final ModelTransform transform;
    final org.json.JSONObject metadata;
    int projectMode = -1;
    private String preparedMaterialEdits="{}";
    void setProjectMode(int mode){if(mode< -1||mode>2)throw new IllegalArgumentException("Unknown project mode");projectMode=mode;}
    static int projectMode(Context c)throws IOException {synchronized(LOCK){return readProjectMode(c.getSharedPreferences(PREFS,0));}}
    private static int readProjectMode(SharedPreferences p)throws IOException {int mode=p.getInt("project_mode",-1);if(mode< -1||mode>2)throw new IOException("Invalid project loading mode");return mode;}
    private static String readMaterialEdits(SharedPreferences p)throws IOException {String raw=p.getString("material_edits","{}");try{new org.json.JSONObject(raw);return raw;}catch(Exception e){throw new IOException("Invalid saved material edits",e);}}
    static String materialEdits(Context c)throws IOException {synchronized(LOCK){return readMaterialEdits(c.getSharedPreferences(PREFS,0));}}
    private static String selectionKey(String file,ModelTransform t,int plate,int mode,String edits){String token="";if(!"{}".equals(edits))try{byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(edits.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder hash=new StringBuilder();for(byte b:bytes)hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));token="material-edits:"+hash+"\n";}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}return file+"\n"+t.encode()+"\n"+(mode<0?"":"project-mode:"+mode+"\n")+token+plate;}
    static void saveMaterialEdits(Context c,String expectedSelectionKey,String edits)throws IOException {synchronized(LOCK){if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Material edit canceled");if(!expectedSelectionKey.equals(selectionKey(c)))throw new IOException("Model or project settings changed");try{new org.json.JSONObject(edits);}catch(Exception e){throw new IOException("Invalid material settings",e);}SharedPreferences p=c.getSharedPreferences(PREFS,0);String old=readMaterialEdits(p);if(!p.edit().putString("material_edits",edits).commit()){p.edit().putString("material_edits",old).apply();throw new IOException("Cannot save project material settings");}}}

    private CurrentModelStore(File file, String name, StlMesh mesh, ModelTransform transform, org.json.JSONObject metadata) {
        this.file = file; this.name = name; this.mesh = mesh; this.transform = transform; this.metadata=metadata;
    }

    /** Validate the exact bytes saved for native processing before changing the current model. */
    static final long MAX_IMPORT_BYTES = 2L * 1024 * 1024 * 1024;
    static CurrentModelStore prepare(Context context, Uri uri, String name) throws IOException {
        return prepare(context,uri,name,null);
    }
    private static CurrentModelStore prepare(Context context,Uri uri,String name,StepImportPreferences.Options suppliedStepOptions)throws IOException {
        File dir = directory(context);
        String extension=extension(name);
        File staged = File.createTempFile("model-", extension, dir);
        boolean valid = false;
        try {
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(staged)) {
                if (in == null) throw new IOException(L.t("Не удалось открыть файл."));
                byte[] bytes = new byte[65536]; int n; long copied = 0;
                while ((n = in.read(bytes)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException(L.t("Импорт STL отменён."));
                    // A provider can stream without end; models larger than this cannot be previewed anyway (audit SEC-06).
                    if ((copied += n) > MAX_IMPORT_BYTES) throw new IOException(L.t("Файл модели слишком большой."));
                    out.write(bytes, 0, n);
                }
                out.getFD().sync();
            }
            if(extension.equals(".3mf"))ArchiveGuard.check(staged); // before the native ZIP reader (audit SEC-02)
            StlMesh mesh; org.json.JSONObject metadata=null;
            try { if(extension.equals(".stl")) { try(InputStream in=new FileInputStream(staged)) { mesh=StlMesh.read(in,staged.length()); } }
                  else { ImportedModelPreview preview;
                      if(StepImportPreferences.isStep(staged)){StepImportPreferences.Options options=suppliedStepOptions==null?StepImportPreferences.capture(context):suppliedStepOptions;preview=StepImportPreferences.preview(context,staged,options);StepImportPreferences.saveOptions(staged,options);}
                      else preview=ImportedModelPreview.read(context,staged,-1);
                      mesh=preview.mesh;metadata=preview.metadata; } }
            catch (OutOfMemoryError e) { throw new IOException(L.t("Недостаточно оперативной памяти для просмотра STL."),e); }
            valid = true;
            CurrentModelStore result=new CurrentModelStore(staged,name,mesh,ModelTransform.identity(),metadata);if(StepImportPreferences.isStep(staged))result.setProjectMode(0);return result;
        } finally {
            if (!valid) {staged.delete();new File(staged.getPath()+".step-settings.json").delete();}
        }
    }

    static CurrentModelStore load(Context context) throws IOException {
      synchronized (LOCK) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String fileName = prefs.getString(FILE, "");
        if (fileName == null || fileName.isEmpty()) return null;
        // Never treat an arbitrary preference as a path outside the private model directory.
        if (!validName(fileName))
            throw new IOException(L.t("Недопустимое имя сохранённой модели."));
        File file = new File(directory(context), fileName);
        if (!file.isFile()) throw new IOException(L.t("Сохранённая модель недоступна."));
        try {
            StlMesh mesh; org.json.JSONObject metadata=null;
            if(fileName.endsWith(".stl")) { try(InputStream in=new FileInputStream(file)) { mesh=StlMesh.read(in,file.length()); } }
            else { ImportedModelPreview preview=StepImportPreferences.isStep(file)?StepImportPreferences.preview(context,file,StepImportPreferences.saved(file)):ImportedModelPreview.read(context,file,readPlate(prefs));mesh=preview.mesh;metadata=preview.metadata; }
            File snapshotFile=new File(file.getPath()+".project-snapshot.json");
            if(metadata!=null&&snapshotFile.isFile())try{org.json.JSONObject snapshot=new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(snapshotFile.toPath()),java.nio.charset.StandardCharsets.UTF_8));if(snapshot.optInt("selected_plate",-2)==metadata.optInt("selected_plate",-1))metadata.put("project_settings_snapshot",snapshot);}catch(Exception ignored){}
            CurrentModelStore result=new CurrentModelStore(file,prefs.getString(NAME,L.t("Модель")),mesh,readTransform(prefs),metadata);result.setProjectMode(readProjectMode(prefs));return result;
        } catch (OutOfMemoryError e) { throw new IOException(L.t("Недостаточно оперативной памяти для просмотра STL."),e); }
      }
    }

    /** Called only for the latest completed import; preserves the previous selection on failure. */
    void publish(Context context) throws IOException {
        publish(context,null,-1,null);
    }
    private void publish(Context context,ModelTransform restoredTransform,int restoredPlate,RestoreSelection expected) throws IOException {
      synchronized (LOCK) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if(expected!=null){
            if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Workspace restore canceled");
            if(!expected.matches(prefs.getAll()))throw new IOException("Workspace changed while restoring the backup; current model was kept");
        }
        String previous = prefs.getString(FILE, "");
        String previousName = prefs.getString(NAME, "");
        String previousTransform = prefs.getString(TRANSFORM, "");
        String previousPlate = prefs.getString(PLATE, "-1");
        int previousMode=readProjectMode(prefs);
        String previousMaterials=readMaterialEdits(prefs);
        if(metadata!=null&&metadata.optJSONObject("project_settings_snapshot")!=null){try(FileOutputStream output=new FileOutputStream(file.getPath()+".project-snapshot.json")){output.write(metadata.optJSONObject("project_settings_snapshot").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));output.getFD().sync();}}
        SharedPreferences.Editor edit=prefs.edit().putString(FILE,file.getName()).putString(NAME,name).putInt("project_mode",projectMode).putString("material_edits",preparedMaterialEdits);
        if(restoredTransform==null)edit.remove(TRANSFORM).remove(PLATE);else edit.putString(TRANSFORM,restoredTransform.encode()).putString(PLATE,Integer.toString(restoredPlate));
        if (!edit.commit()) {
            // Android updates the in-memory preferences even if the disk write fails.
            prefs.edit().putString(FILE, previous).putString(NAME, previousName).putString(TRANSFORM, previousTransform).putString(PLATE,previousPlate).putInt("project_mode",previousMode).putString("material_edits",previousMaterials).apply();
            throw new IOException(L.t("Не удалось сохранить выбор модели."));
        }
        if (validName(previous) && !previous.equals(file.getName())) {new File(directory(context), previous).delete();new File(directory(context),previous+".step-settings.json").delete();new File(directory(context),previous+".project-snapshot.json").delete();}
      }
    }

    /** Open an immutable original under the selection lock; large backup copies happen after unlocking. */
    static final class BackupInput implements java.io.Closeable {
        final String fileName,name,selectionKey,materialEdits;final int plate,projectMode;final StepImportPreferences.Options stepOptions;final ModelTransform transform;final FileInputStream input;
        BackupInput(String fileName,String name,ModelTransform transform,int plate,int mode,String edits,StepImportPreferences.Options options,FileInputStream input){this.fileName=fileName;this.name=name;this.transform=transform;this.plate=plate;projectMode=mode;materialEdits=edits;stepOptions=options;this.input=input;selectionKey=CurrentModelStore.selectionKey(fileName,transform,plate,mode,edits);}
        public void close()throws IOException{input.close();}
    }
    static BackupInput openBackup(Context context)throws IOException {synchronized(LOCK){SharedPreferences prefs=context.getSharedPreferences(PREFS,0);String filename=prefs.getString(FILE,"");if(!validName(filename))throw new IOException("Open a model before creating a backup");File source=new File(directory(context),filename);ModelTransform transform=readTransform(prefs);int plate=readPlate(prefs);return new BackupInput(filename,prefs.getString(NAME,"Model"),transform,plate,readProjectMode(prefs),readMaterialEdits(prefs),StepImportPreferences.isStep(source)?StepImportPreferences.saved(source):null,new FileInputStream(source));}}
    /** Raw preference identity also represents an empty or broken current selection without native loading. */
    static final class RestoreSelection {
        private final Object file,transform,plate,mode,materials;
        private RestoreSelection(java.util.Map<String,?> values){file=values.get(FILE);transform=values.get(TRANSFORM);plate=values.get(PLATE);mode=values.get("project_mode");materials=values.get("material_edits");}
        private boolean matches(java.util.Map<String,?> values){return java.util.Objects.equals(file,values.get(FILE))&&java.util.Objects.equals(transform,values.get(TRANSFORM))&&java.util.Objects.equals(plate,values.get(PLATE))&&java.util.Objects.equals(mode,values.get("project_mode"))&&java.util.Objects.equals(materials,values.get("material_edits"));}
    }
    static RestoreSelection captureRestoreSelection(Context context)throws IOException {synchronized(LOCK){if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Workspace restore canceled");return new RestoreSelection(context.getSharedPreferences(PREFS,0).getAll());}}
    /** Worker-only recovery; final guard and publication are atomic with every model/placement/plate writer. */
    static void restoreBackup(Context context,File source,String name,ModelTransform transform,int plate,RestoreSelection expected)throws IOException {
        restoreBackup(context,source,name,transform,plate,-1,null,expected);
    }
    static void restoreBackup(Context context,File source,String name,ModelTransform transform,int plate,int projectMode,StepImportPreferences.Options stepOptions,RestoreSelection expected)throws IOException {
        restoreBackup(context,source,name,transform,plate,projectMode,"{}",stepOptions,expected);
    }
    static void restoreBackup(Context context,File source,String name,ModelTransform transform,int plate,int projectMode,String materials,StepImportPreferences.Options stepOptions,RestoreSelection expected)throws IOException {
        if(expected==null)throw new IOException("Missing workspace restore identity");
        ProjectMaterialValidation.forRestore(name,projectMode,materials);
        CurrentModelStore prepared=prepare(context,Uri.fromFile(source),name,stepOptions);boolean published=false;
        try{if(plate>=0){if(!prepared.file.getName().endsWith(".3mf"))throw new IOException("A model without plates cannot restore a selected plate");ImportedModelPreview preview=ImportedModelPreview.read(context,prepared.file,plate);prepared=new CurrentModelStore(prepared.file,name,preview.mesh,transform,preview.metadata);}prepared.setProjectMode(projectMode);prepared.preparedMaterialEdits=materials;
            NativeMaterialPreflight.Validated checked=NativeMaterialPreflight.validate(context,prepared.file,plate,projectMode,materials);CurrentModelStore candidate=prepared;
            if(checked==null)candidate.publish(context,transform,plate,expected);else checked.publish(()->candidate.publish(context,transform,plate,expected));published=true;
        }finally{if(!published)prepared.discard();}
    }
    /** Caller holds the profile-selection lock; publication also checks the captured workspace. */
    static void replaceDerived(Context context,File derived,String displayName,RestoreSelection expected)throws IOException {replaceDerived(context,derived,displayName,expected,0,null);}
    /** projectMode 1 keeps the derived project's own material slots and settings with the selected printer (needs its checked snapshot). */
    static void replaceDerived(Context context,File derived,String displayName,RestoreSelection expected,int projectMode,org.json.JSONObject projectSnapshot)throws IOException {replaceDerived(context,derived,displayName,expected,projectMode,projectSnapshot,-1);}
    /** Same, opening the given plate of a multi-plate result (plate actions of the tablet workspace). */
    static void replaceDerived(Context context,File derived,String displayName,RestoreSelection expected,int projectMode,org.json.JSONObject projectSnapshot,int plate)throws IOException {
        if(projectMode!=0&&projectMode!=1||projectMode==1&&projectSnapshot==null)throw new IllegalArgumentException("Unsupported project mode");
        CurrentModelStore prepared=prepare(context,Uri.fromFile(derived),displayName);boolean published=false;
        try{if(projectSnapshot!=null){if(prepared.metadata==null)throw new IOException("Project metadata is missing");try{prepared.metadata.put("project_settings_snapshot",projectSnapshot);}catch(org.json.JSONException e){throw new IOException(e);}}
            prepared.setProjectMode(projectMode);prepared.publish(context,ModelTransform.identity(),plate,expected);published=true;}finally{if(!published)prepared.discard();}
    }

    static void saveTransform(Context context, String expectedFile, ModelTransform transform) throws IOException {
      synchronized (LOCK) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!expectedFile.equals(prefs.getString(FILE, ""))) throw new IOException(L.t("Модель изменилась. Откройте настройки снова."));
        String previous = prefs.getString(TRANSFORM, "");
        if (!prefs.edit().putString(TRANSFORM, transform.encode()).commit()) {
            prefs.edit().putString(TRANSFORM, previous).apply();
            throw new IOException(L.t("Не удалось сохранить преобразование модели."));
        }
      }
    }

    static final class Snapshot {
        final String name;
        final ModelTransform transform;
        final String selectionKey;
        final File file;
        final int plateIndex;
        final int projectMode;
        final String materialEdits;
        Snapshot(String name, ModelTransform transform, String selectionKey,File file,int plateIndex,int projectMode,String edits) { this.name = name; this.transform = transform; this.selectionKey = selectionKey;this.file=file;this.plateIndex=plateIndex;this.projectMode=projectMode;materialEdits=edits; }
    }

    /** Settings can resolve presets without a model, but must still reject a changed/broken workspace. */
    static String settingsSelectionKey(Context context) throws IOException {
      synchronized (LOCK) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            String fileName = prefs.getString(FILE, "");
            if (fileName != null && fileName.isEmpty()) {
                // Validate stored metadata explicitly; an IOException is never interpreted as "empty".
                readTransform(prefs);
                int plate=readPlate(prefs),mode=readProjectMode(prefs);
                String materials=readMaterialEdits(prefs);
                if (plate!=-1 || mode>0 || new org.json.JSONObject(materials).length()!=0)
                    throw new IOException("Saved project metadata has no selected model");
                return "\0kepler-no-model-v1";
            }
            return selectionKey(context);
        } catch (Exception invalid) {
            if (invalid instanceof IOException) throw (IOException)invalid;
            throw new IOException("Invalid saved model selection",invalid);
        }
      }
    }

    /** Model identity and transform are read under the same publication lock. */
    static String selectionKey(Context context) throws IOException {
      synchronized (LOCK) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String fileName = prefs.getString(FILE, "");
        if (!validName(fileName) || !new File(directory(context), fileName).isFile())
            throw new IOException(L.t("Сначала откройте STL-модель."));
        return selectionKey(fileName,readTransform(prefs),readPlate(prefs),readProjectMode(prefs),readMaterialEdits(prefs));
      }
    }

    /** Copy the current file and matching transform under the same lock as model publication and editing. */
    static Snapshot snapshot(Context context, File destination) throws IOException {
      synchronized (LOCK) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String fileName = prefs.getString(FILE, "");
        if (!validName(fileName)) throw new IOException(L.t("Сначала откройте модель."));
        File file = new File(directory(context), fileName);
        if (!file.isFile()) throw new IOException(L.t("Сохранённая модель недоступна."));
        ModelTransform transform = readTransform(prefs);
        if (destination.exists()) throw new IOException(L.t("Файл задания уже существует."));
        File parent=destination.getParentFile();
        if (parent != null && parent.getUsableSpace() > 0 && file.length() > parent.getUsableSpace())
            throw new IOException(L.t("Недостаточно свободного места для копии модели."));
        try (InputStream in = new FileInputStream(file); FileOutputStream out = new FileOutputStream(destination)) {
            byte[] bytes = new byte[65536]; int n;
            while ((n = in.read(bytes)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new IOException(L.t("Подготовка STL отменена."));
                out.write(bytes, 0, n);
            }
        } catch (IOException e) { destination.delete(); throw e; }
        int plate=readPlate(prefs);
        int mode=readProjectMode(prefs);String edits=readMaterialEdits(prefs);return new Snapshot(prefs.getString(NAME,L.t("Модель")),transform,selectionKey(fileName,transform,plate,mode,edits),destination,plate,mode,edits);
      }
    }

    /** Preserve the format suffix while selecting and copying under the publication lock. */
    static Snapshot snapshotToDirectory(Context context,File targetDirectory) throws IOException {
      Snapshot result;StepImportPreferences.Options step=null;
      synchronized(LOCK) {
        String filename=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(FILE,"");
        if(!validName(filename)) throw new IOException(L.t("Сначала откройте модель."));
        if(StepImportPreferences.isStep(new File(filename)))step=StepImportPreferences.saved(new File(directory(context),filename));
        result=snapshot(context,new File(targetDirectory,"model"+extension(filename)));
      }
      if(step==null)return result;
      File derived=StepImportPreferences.derive(context,result.file,step),target=new File(targetDirectory,"model.3mf");
      try(InputStream in=new FileInputStream(derived);FileOutputStream out=new FileOutputStream(target)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("STEP snapshot canceled");out.write(buffer,0,n);}out.getFD().sync();}
      StepImportPreferences.copyVerifiedProvenance(result.file,step,derived,target);
      // Mode 3 is internal to a frozen STEP derivative, never a persisted 3MF import mode.
      return new Snapshot(result.name,result.transform,result.selectionKey,target,-1,3,result.materialEdits);
    }
    private static boolean validName(String name) { if(name==null||!name.startsWith("model-")||name.indexOf('/')>=0||name.indexOf('\\')>=0)return false; try {extension(name);return true;}catch(IOException e){return false;} }
    private static String extension(String name) throws IOException {
        if(name!=null) {String n=name.toLowerCase(java.util.Locale.ROOT);for(String ext:new String[]{".stl",".obj",".3mf",".step",".stp"})if(n.endsWith(ext))return ext;}
        throw new IOException(L.t("Выберите файл STL, OBJ или 3MF с подходящим расширением."));
    }
    private static int readPlate(SharedPreferences prefs) throws IOException {
        try { int plate=Integer.parseInt(prefs.getString(PLATE,"-1")); if(plate < -1)throw new NumberFormatException();return plate; }
        catch(RuntimeException e) { throw new IOException(L.t("Не удалось прочитать выбранный стол проекта."),e); }
    }
    /** Validate the new plate before changing the selection or its fingerprint. */
    static CurrentModelStore selectPlate(Context context,String expectedFile,int plate) throws IOException {
      synchronized(LOCK) {
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String filename=p.getString(FILE,""); if(!validName(filename)||!filename.equals(expectedFile)||!filename.endsWith(".3mf")||plate<0)throw new IOException(L.t("Проект изменился или выбран недопустимый стол."));
        File file=new File(directory(context),filename); ImportedModelPreview preview=ImportedModelPreview.read(context,file,plate);
        String previous=p.getString(PLATE,"-1"); if(!p.edit().putString(PLATE,Integer.toString(plate)).commit()){p.edit().putString(PLATE,previous).apply();throw new IOException(L.t("Не удалось сохранить выбранный стол."));}
        CurrentModelStore selected=new CurrentModelStore(file,p.getString(NAME,L.t("Модель")),preview.mesh,readTransform(p),preview.metadata);selected.setProjectMode(readProjectMode(p));return selected;
      }
    }

    /** Stores a project settings snapshot for the current file (see ProjectPlates). */
    static void saveProjectSnapshot(Context context,String expectedFile,org.json.JSONObject snapshot)throws IOException{
      synchronized(LOCK){
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);String filename=p.getString(FILE,"");
        if(!validName(filename)||!filename.equals(expectedFile))throw new IOException(L.t("Проект изменился или выбран недопустимый стол."));
        File file=new File(directory(context),filename);
        try(FileOutputStream output=new FileOutputStream(file.getPath()+".project-snapshot.json")){output.write(snapshot.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));output.getFD().sync();}
      }
    }
    static int storedProjectMode(Context context)throws IOException{return readProjectMode(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE));}
    private static ModelTransform readTransform(SharedPreferences prefs) throws IOException {
        try { return ModelTransform.decode(prefs.getString(TRANSFORM, "")); }
        catch (IllegalArgumentException e) { throw new IOException(L.t("Не удалось прочитать сохранённые настройки модели."), e); }
    }

    void discard() { file.delete();new File(file.getPath()+".step-settings.json").delete();new File(file.getPath()+".project-snapshot.json").delete(); }

    private static File directory(Context context) throws IOException {
        File dir = new File(context.getFilesDir(), "models");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException(L.t("Не удалось создать хранилище моделей."));
        return dir;
    }
}
