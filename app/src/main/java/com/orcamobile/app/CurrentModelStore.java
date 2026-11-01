package com.orcamobile.app;

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

    private CurrentModelStore(File file, String name, StlMesh mesh, ModelTransform transform, org.json.JSONObject metadata) {
        this.file = file; this.name = name; this.mesh = mesh; this.transform = transform; this.metadata=metadata;
    }

    /** Validate the exact bytes saved for native processing before changing the current model. */
    static CurrentModelStore prepare(Context context, Uri uri, String name) throws IOException {
        File dir = directory(context);
        String extension=extension(name);
        File staged = File.createTempFile("model-", extension, dir);
        boolean valid = false;
        try {
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(staged)) {
                if (in == null) throw new IOException("Не удалось открыть файл.");
                byte[] bytes = new byte[65536]; int n;
                while ((n = in.read(bytes)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Импорт STL отменён.");
                    out.write(bytes, 0, n);
                }
                out.getFD().sync();
            }
            StlMesh mesh; org.json.JSONObject metadata=null;
            try { if(extension.equals(".stl")) { try(InputStream in=new FileInputStream(staged)) { mesh=StlMesh.read(in,staged.length()); } }
                  else { ImportedModelPreview preview=ImportedModelPreview.read(context,staged,-1);mesh=preview.mesh;metadata=preview.metadata; } }
            catch (OutOfMemoryError e) { throw new IOException("Недостаточно оперативной памяти для просмотра STL.",e); }
            valid = true;
            return new CurrentModelStore(staged, name, mesh, ModelTransform.identity(), metadata);
        } finally {
            if (!valid) staged.delete();
        }
    }

    static CurrentModelStore load(Context context) throws IOException {
      synchronized (LOCK) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String fileName = prefs.getString(FILE, "");
        if (fileName == null || fileName.isEmpty()) return null;
        // Never treat an arbitrary preference as a path outside the private model directory.
        if (!validName(fileName))
            throw new IOException("Недопустимое имя сохранённой модели.");
        File file = new File(directory(context), fileName);
        if (!file.isFile()) throw new IOException("Сохранённая модель недоступна.");
        try {
            StlMesh mesh; org.json.JSONObject metadata=null;
            if(fileName.endsWith(".stl")) { try(InputStream in=new FileInputStream(file)) { mesh=StlMesh.read(in,file.length()); } }
            else { ImportedModelPreview preview=ImportedModelPreview.read(context,file,readPlate(prefs));mesh=preview.mesh;metadata=preview.metadata; }
            return new CurrentModelStore(file, prefs.getString(NAME, "Модель"), mesh, readTransform(prefs),metadata);
        } catch (OutOfMemoryError e) { throw new IOException("Недостаточно оперативной памяти для просмотра STL.",e); }
      }
    }

    /** Called only for the latest completed import; preserves the previous selection on failure. */
    void publish(Context context) throws IOException {
      synchronized (LOCK) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String previous = prefs.getString(FILE, "");
        String previousName = prefs.getString(NAME, "");
        String previousTransform = prefs.getString(TRANSFORM, "");
        String previousPlate = prefs.getString(PLATE, "-1");
        if (!prefs.edit().putString(FILE, file.getName()).putString(NAME, name).remove(TRANSFORM).remove(PLATE).commit()) {
            // Android updates the in-memory preferences even if the disk write fails.
            prefs.edit().putString(FILE, previous).putString(NAME, previousName).putString(TRANSFORM, previousTransform).putString(PLATE,previousPlate).apply();
            throw new IOException("Не удалось сохранить выбор модели.");
        }
        if (validName(previous) && !previous.equals(file.getName()))
            new File(directory(context), previous).delete();
      }
    }

    static void saveTransform(Context context, String expectedFile, ModelTransform transform) throws IOException {
      synchronized (LOCK) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!expectedFile.equals(prefs.getString(FILE, ""))) throw new IOException("Модель изменилась. Откройте настройки снова.");
        String previous = prefs.getString(TRANSFORM, "");
        if (!prefs.edit().putString(TRANSFORM, transform.encode()).commit()) {
            prefs.edit().putString(TRANSFORM, previous).apply();
            throw new IOException("Не удалось сохранить преобразование модели.");
        }
      }
    }

    static final class Snapshot {
        final String name;
        final ModelTransform transform;
        final String selectionKey;
        final File file;
        final int plateIndex;
        Snapshot(String name, ModelTransform transform, String selectionKey,File file,int plateIndex) { this.name = name; this.transform = transform; this.selectionKey = selectionKey;this.file=file;this.plateIndex=plateIndex; }
    }

    /** Model identity and transform are read under the same publication lock. */
    static String selectionKey(Context context) throws IOException {
      synchronized (LOCK) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String fileName = prefs.getString(FILE, "");
        if (!validName(fileName) || !new File(directory(context), fileName).isFile())
            throw new IOException("Сначала откройте STL-модель.");
        return fileName + "\n" + readTransform(prefs).encode() + "\n" + readPlate(prefs);
      }
    }

    /** Copy the current file and matching transform under the same lock as model publication and editing. */
    static Snapshot snapshot(Context context, File destination) throws IOException {
      synchronized (LOCK) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String fileName = prefs.getString(FILE, "");
        if (!validName(fileName)) throw new IOException("Сначала откройте модель.");
        File file = new File(directory(context), fileName);
        if (!file.isFile()) throw new IOException("Сохранённая модель недоступна.");
        ModelTransform transform = readTransform(prefs);
        if (destination.exists()) throw new IOException("Файл задания уже существует.");
        File parent=destination.getParentFile();
        if (parent != null && parent.getUsableSpace() > 0 && file.length() > parent.getUsableSpace())
            throw new IOException("Недостаточно свободного места для копии модели.");
        try (InputStream in = new FileInputStream(file); FileOutputStream out = new FileOutputStream(destination)) {
            byte[] bytes = new byte[65536]; int n;
            while ((n = in.read(bytes)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Подготовка STL отменена.");
                out.write(bytes, 0, n);
            }
        } catch (IOException e) { destination.delete(); throw e; }
        int plate=readPlate(prefs);
        return new Snapshot(prefs.getString(NAME, "Модель"), transform, fileName + "\n" + transform.encode()+"\n"+plate,destination,plate);
      }
    }

    /** Preserve the format suffix while selecting and copying under the publication lock. */
    static Snapshot snapshotToDirectory(Context context,File targetDirectory) throws IOException {
      synchronized(LOCK) {
        String filename=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(FILE,"");
        if(!validName(filename)) throw new IOException("Сначала откройте модель.");
        return snapshot(context,new File(targetDirectory,"model"+extension(filename)));
      }
    }
    private static boolean validName(String name) { if(name==null||!name.startsWith("model-")||name.indexOf('/')>=0||name.indexOf('\\')>=0)return false; try {extension(name);return true;}catch(IOException e){return false;} }
    private static String extension(String name) throws IOException {
        if(name!=null) {String n=name.toLowerCase(java.util.Locale.ROOT);for(String ext:new String[]{".stl",".obj",".3mf"})if(n.endsWith(ext))return ext;}
        throw new IOException("Выберите файл STL, OBJ или 3MF с подходящим расширением.");
    }
    private static int readPlate(SharedPreferences prefs) throws IOException {
        try { int plate=Integer.parseInt(prefs.getString(PLATE,"-1")); if(plate < -1)throw new NumberFormatException();return plate; }
        catch(RuntimeException e) { throw new IOException("Не удалось прочитать выбранный стол проекта.",e); }
    }
    /** Validate the new plate before changing the selection or its fingerprint. */
    static CurrentModelStore selectPlate(Context context,String expectedFile,int plate) throws IOException {
      synchronized(LOCK) {
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String filename=p.getString(FILE,""); if(!validName(filename)||!filename.equals(expectedFile)||!filename.endsWith(".3mf")||plate<0)throw new IOException("Проект изменился или выбран недопустимый стол.");
        File file=new File(directory(context),filename); ImportedModelPreview preview=ImportedModelPreview.read(context,file,plate);
        String previous=p.getString(PLATE,"-1"); if(!p.edit().putString(PLATE,Integer.toString(plate)).commit()){p.edit().putString(PLATE,previous).apply();throw new IOException("Не удалось сохранить выбранный стол.");}
        return new CurrentModelStore(file,p.getString(NAME,"Модель"),preview.mesh,readTransform(p),preview.metadata);
      }
    }

    private static ModelTransform readTransform(SharedPreferences prefs) throws IOException {
        try { return ModelTransform.decode(prefs.getString(TRANSFORM, "")); }
        catch (IllegalArgumentException e) { throw new IOException("Не удалось прочитать сохранённые настройки модели.", e); }
    }

    void discard() { file.delete(); }

    private static File directory(Context context) throws IOException {
        File dir = new File(context.getFilesDir(), "models");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Не удалось создать хранилище моделей.");
        return dir;
    }
}
