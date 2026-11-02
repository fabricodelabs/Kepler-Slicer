package com.orcamobile.app;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Original model bytes and plate/placement recovery. Preset library and credentials are never overwritten. */
final class GeneralBackupArchive {
    private static final Object LOCK=new Object();
    private GeneralBackupArchive(){}
    static void backup(Context context)throws IOException {synchronized(LOCK){
        File directory=directory(context),staged=File.createTempFile("backup-",".zip",directory);boolean committed=false;
        try(CurrentModelStore.BackupInput source=CurrentModelStore.openBackup(context)){
            String extension=extension(source.fileName);JSONObject meta=new JSONObject();
            try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(staged))){
                MessageDigest hash=digest();zip.putNextEntry(new ZipEntry("model"+extension));copy(source.input,zip,hash);zip.closeEntry();
                try{meta.put("format","kepler-model-backup-v1");meta.put("scope","original_model_plate_transform");meta.put("name",source.name);meta.put("extension",extension);meta.put("plate",source.plate);meta.put("transform",source.transform.encode());meta.put("model_sha256",hex(hash.digest()));meta.put("saved_at",System.currentTimeMillis());}catch(Exception e){throw new IOException("Cannot encode workspace backup",e);}
                zip.putNextEntry(new ZipEntry("workspace.json"));zip.write(meta.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            }
            if(!source.selectionKey.equals(CurrentModelStore.selectionKey(context)))throw new IOException("Workspace changed during backup; retry");
            try(FileOutputStream sync=new FileOutputStream(staged,true)){sync.getFD().sync();}
            try{Files.move(staged.toPath(),new File(directory,"workspace.zip").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(java.nio.file.AtomicMoveNotSupportedException e){Files.move(staged.toPath(),new File(directory,"workspace.zip").toPath(),StandardCopyOption.REPLACE_EXISTING);}committed=true;
        }finally{if(!committed)staged.delete();}
    }}
    static void restore(Context context)throws IOException {synchronized(LOCK){
        CurrentModelStore.RestoreSelection expected=CurrentModelStore.captureRestoreSelection(context);
        File directory=directory(context),archive=new File(directory,"workspace.zip");if(!archive.isFile())throw new IOException(context.getString(R.string.general_backup_missing));File extracted=null;
        try(ZipFile zip=new ZipFile(archive)){
            Set<String> seen=new HashSet<>();Enumeration<? extends ZipEntry> all=zip.entries();while(all.hasMoreElements()){String name=all.nextElement().getName();if(!seen.add(name)||!(name.equals("workspace.json")||name.equals("model.stl")||name.equals("model.obj")||name.equals("model.3mf")))throw new IOException("Invalid workspace backup entries");}
            if(seen.size()!=2||!seen.contains("workspace.json"))throw new IOException("Incomplete workspace backup");
            JSONObject meta;try(InputStream in=zip.getInputStream(zip.getEntry("workspace.json"))){meta=new JSONObject(readMetadata(in));}catch(Exception e){throw new IOException("Invalid workspace backup metadata",e);}
            if(!"kepler-model-backup-v1".equals(meta.optString("format")))throw new IOException("Unsupported workspace backup format");String extension=extension("model"+meta.optString("extension"));ZipEntry model=zip.getEntry("model"+extension);if(model==null||model.isDirectory())throw new IOException("Workspace backup model is missing");
            String name=meta.optString("name","Model"+extension);int plate=meta.optInt("plate",-2);if(plate< -1||(!extension.equals(".3mf")&&plate!=-1))throw new IOException("Invalid backup plate");ModelTransform transform;try{transform=ModelTransform.decode(meta.getString("transform"));}catch(Exception e){throw new IOException("Invalid backup placement",e);}
            extracted=File.createTempFile("restore-",extension,directory);MessageDigest hash=digest();try(InputStream in=zip.getInputStream(model);OutputStream out=new FileOutputStream(extracted)){copy(in,out,hash);}
            if(!hex(hash.digest()).equals(meta.optString("model_sha256")))throw new IOException("Workspace backup model checksum does not match");
            CurrentModelStore.restoreBackup(context,extracted,name,transform,plate,expected);
        }finally{if(extracted!=null)extracted.delete();}
    }}
    private static File directory(Context c)throws IOException{File dir=new File(c.getFilesDir(),"workspace-backup");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot prepare workspace backup directory");return dir;}
    private static String extension(String name)throws IOException{for(String ext:new String[]{".stl",".obj",".3mf"})if(name.toLowerCase(Locale.ROOT).endsWith(ext))return ext;throw new IOException("Unsupported backup model format");}
    private static MessageDigest digest()throws IOException{try{return MessageDigest.getInstance("SHA-256");}catch(Exception e){throw new IOException(e);}}
    private static String hex(byte[] hash){StringBuilder text=new StringBuilder();for(byte b:hash)text.append(String.format(Locale.ROOT,"%02x",b&255));return text.toString();}
    private static void copy(InputStream in,OutputStream out,MessageDigest hash)throws IOException{byte[] data=new byte[65536];int n;while((n=in.read(data))!=-1){if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Backup operation canceled");out.write(data,0,n);hash.update(data,0,n);}}
    private static String readMetadata(InputStream in)throws IOException{ByteArrayOutputStream text=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(text.size()+n>1_048_576)throw new IOException("Backup metadata is too large");text.write(b,0,n);}return text.toString("UTF-8");}
}
