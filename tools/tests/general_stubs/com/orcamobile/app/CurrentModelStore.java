package com.orcamobile.app;
import android.content.Context;
import java.io.*;
/** Explicit native/model boundary fake: archive tests do not execute JNI or Android. */
final class CurrentModelStore {
    static File current;static ModelTransform transform;static int plate,projectMode=-1;static String materials="{}",restoredMaterials;static StepImportPreferences.Options stepOptions;static String name;static boolean changed;static int restores;static byte[] restoredBytes;static String restoredName;static ModelTransform restoredTransform;static int restoredPlate,restoredMode;static StepImportPreferences.Options restoredStepOptions;
    static String rawKey(){return current.getName()+"\n"+transform.encode()+"\n"+(projectMode<0?"":"project-mode:"+projectMode+"\n")+materials+"\n"+plate;}
    static String selectionKey(Context c){return changed?"changed":rawKey();}
    static final class BackupInput implements Closeable {final String fileName,name,selectionKey,materialEdits;final ModelTransform transform;final int plate,projectMode;final StepImportPreferences.Options stepOptions;final FileInputStream input;
        BackupInput()throws IOException{fileName=current.getName();name=CurrentModelStore.name;transform=CurrentModelStore.transform;plate=CurrentModelStore.plate;projectMode=CurrentModelStore.projectMode;materialEdits=materials;stepOptions=CurrentModelStore.stepOptions;selectionKey=rawKey();input=new FileInputStream(current);}public void close()throws IOException{input.close();}}
    static BackupInput openBackup(Context c)throws IOException{return new BackupInput();}
    static final class RestoreSelection {final String key;RestoreSelection(String key){this.key=key;}}
    static RestoreSelection captureRestoreSelection(Context c){return new RestoreSelection(selectionKey(c));}
    static void restoreBackup(Context c,File file,String name,ModelTransform transform,int plate,int mode,String materialEdits,StepImportPreferences.Options options,RestoreSelection expected)throws IOException{if(Thread.currentThread().isInterrupted()||!expected.key.equals(selectionKey(c)))throw new IOException("Workspace changed");restoredBytes=java.nio.file.Files.readAllBytes(file.toPath());restoredName=name;restoredTransform=transform;restoredPlate=plate;restoredMode=mode;restoredMaterials=materialEdits;restoredStepOptions=options;restores++;}
}
