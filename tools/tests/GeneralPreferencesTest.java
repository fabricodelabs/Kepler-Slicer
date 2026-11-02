package com.orcamobile.app;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Host tests exercise actual archive and project-policy production code, with an explicit model/JNI fake. */
public final class GeneralPreferencesTest {
    static int assertions;
    static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    interface Action{void run()throws Exception;}
    static void rejects(Action action,String message)throws Exception{try{action.run();throw new AssertionError(message);}catch(IOException expected){assertions++;}}
    public static void main(String[] args)throws Exception{
        File root=new File(args[0]);root.mkdirs();Context context=new Context(root);
        byte[] model=new byte[2_000_123];new Random(42).nextBytes(model);File original=new File(root,"model-immutable.3mf");Files.write(original.toPath(),model);
        CurrentModelStore.current=original;CurrentModelStore.name="Original project.3mf";CurrentModelStore.plate=2;CurrentModelStore.transform=new ModelTransform(15,20,30,1.1,1.2,1.3,25,-18);
        GeneralBackupArchive.backup(context);File archive=new File(root,"workspace-backup/workspace.zip");check(archive.isFile(),"A real atomic backup archive is produced");
        GeneralBackupArchive.restore(context);check(Arrays.equals(model,CurrentModelStore.restoredBytes),"Recovery retains exact original bytes beyond 2MB");check(CurrentModelStore.restoredPlate==2,"Selected plate survives recovery");check(CurrentModelStore.restoredTransform.encode().equals(CurrentModelStore.transform.encode()),"Placement survives recovery without rounding");check(CurrentModelStore.restoredName.equals("Original project.3mf"),"Display name survives recovery");
        byte[] previous=Files.readAllBytes(archive.toPath());CurrentModelStore.changed=true;rejects(()->GeneralBackupArchive.backup(context),"A changing workspace must not publish an incoherent backup");check(Arrays.equals(previous,Files.readAllBytes(archive.toPath())),"Failed backup retains previous recovery archive");CurrentModelStore.changed=false;
        Map<String,byte[]> entries=readEntries(archive);entries.get("model.3mf")[17]^=1;writeEntries(archive,entries);int before=CurrentModelStore.restores;rejects(()->GeneralBackupArchive.restore(context),"Corrupted bytes must fail checksum before native restore");check(CurrentModelStore.restores==before,"Checksum failure never publishes a replacement model");
        entries=readEntriesBytes(previous);entries.put("../outside",new byte[]{1});writeEntries(archive,entries);rejects(()->GeneralBackupArchive.restore(context),"Unexpected/path-traversal archive entries are refused");check(!new File(root,"outside").exists(),"Archive paths cannot write outside private staging");
        Files.write(archive.toPath(),previous);Thread.currentThread().interrupt();rejects(()->GeneralBackupArchive.backup(context),"Interrupted backup must fail");Thread.interrupted();check(Arrays.equals(previous,Files.readAllBytes(archive.toPath())),"Interruption leaves last successful backup usable");
        check(Arrays.stream(archive.getParentFile().listFiles()).noneMatch(f->!f.getName().equals("workspace.zip")),"Failed and successful jobs leave no partial backup/restore files");
        GeneralProjectPolicy empty=GeneralProjectPolicy.inspect(new JSONObject("{\"format\":\"3mf\",\"project_data\":{\"project_config\":{},\"embedded_presets\":[],\"plates\":[]}}"));check(empty.project&&!empty.settingsPresent&&!empty.customCodePresent,"Empty geometry-only 3MF is not called configured or modified G-code");check(!empty.needsDecision("ask_when_relevant")&&empty.needsDecision("always_ask"),"Always Ask and Ask When Relevant have distinct consumers");
        JSONObject configured=new JSONObject("{\"format\":\"3mf\",\"project_data\":{\"project_config\":{\"layer_height\":\"0.24\",\"machine_start_gcode\":\"G28\"}}}");String untouched=configured.toString();GeneralProjectPolicy policy=GeneralProjectPolicy.inspect(configured);check(policy.settingsPresent&&policy.customCodePresent,"Embedded settings and custom/unverified G-code are detected");check(policy.needsDecision("load_all")&&policy.needsDecision("ask_when_relevant")&&!policy.needsDecision("load_geometry"),"Configured Load All requires explicit fallback, Geometry Only keeps its deliberate choice");check(configured.toString().equals(untouched),"Import policy never strips or rewrites project configuration");
        check(GeneralProjectPolicy.inspect(new JSONObject("{\"format\":\"3mf\",\"project_data\":{\"plates\":[{\"config\":{\"layer_height\":\"0.18\"}}]}} ")).settingsPresent,"Plate config is included in relevant-project detection");
        check(GeneralProjectPolicy.inspect(new JSONObject("{\"format\":\"3mf\",\"project_data\":{\"plate_custom_gcode\":[{\"items\":[{\"extra\":\"M600\"}]}]}} ")).customCodePresent,"Custom plate G-code warning is consumed");
        check(!GeneralProjectPolicy.inspect(new JSONObject("{\"format\":\"obj\",\"project_config\":{\"x\":1}} ")).needsDecision("always_ask"),"Project load preference does not prompt for ordinary OBJ");
        System.out.println("General backup/project policy: "+assertions+" assertions passed (native/JNI boundary stub)");
    }
    static Map<String,byte[]> readEntries(File file)throws Exception{return readEntriesBytes(Files.readAllBytes(file.toPath()));}
    static Map<String,byte[]> readEntriesBytes(byte[] data)throws Exception{Map<String,byte[]> entries=new LinkedHashMap<>();try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(data))){ZipEntry e;while((e=zip.getNextEntry())!=null)entries.put(e.getName(),zip.readAllBytes());}return entries;}
    static void writeEntries(File file,Map<String,byte[]> entries)throws Exception{try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(file.toPath()))){for(Map.Entry<String,byte[]> e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}}}
}
