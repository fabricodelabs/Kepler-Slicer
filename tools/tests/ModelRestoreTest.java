package com.orcamobile.app;
import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;

/** Runs actual CurrentModelStore publication, capture and restore code; geometry/JNI alone is faked. */
public final class ModelRestoreTest {
    static int assertions;
    static void check(boolean yes,String message){assertions++;if(!yes)throw new AssertionError(message);}
    interface Action{void run()throws Exception;}
    static void rejects(Action action,String message)throws Exception{try{action.run();throw new AssertionError(message);}catch(IOException expected){assertions++;}}
    static File input(File root,String name,int marker)throws Exception{File file=new File(root,name);byte[] data=new byte[65537];Arrays.fill(data,(byte)marker);Files.write(file.toPath(),data);return file;}
    public static void main(String[] args)throws Exception{
        File root=new File(args[0]);root.mkdirs();Context c=new Context(new File(root,"active"),new File(root,"assets"));
        File first=input(root,"first.stl",1),next=input(root,"next.stl",2),backup=input(root,"backup.3mf",3),project=input(root,"project.3mf",4);
        CurrentModelStore.prepare(c,Uri.fromFile(first),"First.stl").publish(c);
        CurrentModelStore.RestoreSelection expected=CurrentModelStore.captureRestoreSelection(c);
        ImportedModelPreview.nextRead=()->CurrentModelStore.prepare(c,Uri.fromFile(next),"Newer.stl").publish(c);
        rejects(()->CurrentModelStore.restoreBackup(c,backup,"Backup.3mf",ModelTransform.identity(),-1,expected),"Native preparation may not replace a model published since restore started");
        CurrentModelStore newer=CurrentModelStore.load(c);check(newer.name.equals("Newer.stl"),"Latest selected model name is retained");check(newer.file.isFile()&&Arrays.equals(Files.readAllBytes(next.toPath()),Files.readAllBytes(newer.file.toPath())),"Late restore never deletes or replaces newer model bytes");
        check(new File(c.getFilesDir(),"models").listFiles().length==1,"Rejected native-prepared restore cleans its staged model only");

        CurrentModelStore.RestoreSelection placement=CurrentModelStore.captureRestoreSelection(c);ModelTransform moved=new ModelTransform(0,0,0,1,1,1,37,-12);
        ImportedModelPreview.nextRead=()->CurrentModelStore.saveTransform(c,newer.file.getName(),moved);
        rejects(()->CurrentModelStore.restoreBackup(c,backup,"Backup.3mf",ModelTransform.identity(),-1,placement),"Placement changes during native preparation reject restore");
        check(CurrentModelStore.load(c).transform.encode().equals(moved.encode()),"Latest placement is kept after rejected restore");

        CurrentModelStore.prepare(c,Uri.fromFile(project),"Project.3mf").publish(c);String selected=c.getSharedPreferences("current_model",0).getString("file","");
        CurrentModelStore.RestoreSelection plate=CurrentModelStore.captureRestoreSelection(c);ImportedModelPreview.nextRead=()->CurrentModelStore.selectPlate(c,selected,2);
        rejects(()->CurrentModelStore.restoreBackup(c,backup,"Backup.3mf",ModelTransform.identity(),-1,plate),"Plate changes during native preparation reject restore");
        check(c.getSharedPreferences("current_model",0).getString("plate","-1").equals("2"),"Latest selected plate is retained");

        CurrentModelStore.RestoreSelection cancel=CurrentModelStore.captureRestoreSelection(c);ImportedModelPreview.nextRead=()->Thread.currentThread().interrupt();
        rejects(()->CurrentModelStore.restoreBackup(c,backup,"Backup.3mf",ModelTransform.identity(),-1,cancel),"Cancellation during native preparation is checked before publication");Thread.interrupted();
        check(c.getSharedPreferences("current_model",0).getString("file","").equals(selected),"Interrupted restore keeps the current model");
        check(c.getSharedPreferences("current_model",0).getString("plate","-1").equals("2"),"Interrupted restore keeps the current plate");

        Context empty=new Context(new File(root,"empty"),new File(root,"assets"));CurrentModelStore.RestoreSelection absent=CurrentModelStore.captureRestoreSelection(empty);
        CurrentModelStore.restoreBackup(empty,backup,"Recovered.3mf",moved,0,absent);CurrentModelStore restored=CurrentModelStore.load(empty);
        check(restored.name.equals("Recovered.3mf")&&restored.file.isFile(),"A nullable/raw empty selection can restore successfully");
        check(restored.transform.encode().equals(moved.encode())&&empty.getSharedPreferences("current_model",0).getString("plate","").equals("0"),"Successful empty-workspace recovery restores plate and transform");
        System.out.println("Current model restore: "+assertions+" assertions passed (geometry/JNI boundary stub)");
    }
}
