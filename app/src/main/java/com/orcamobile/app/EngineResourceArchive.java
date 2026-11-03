package com.orcamobile.app;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.*;

/** Production ZIP/cache transaction, independent of Android so failure paths can be tested. */
final class EngineResourceArchive {
    interface Source { InputStream open()throws IOException; }
    interface Progress { void update(int extractedFiles); }
    static final String DIRECTORY="resources-6d2869c4";
    private static final String HEADER="kepler-engine-resources-v1",REVISION="6d2869c430284714f0a22570c38d3b359965a802";
    private static final long LIMIT=512L*1024*1024;
    private static final int ENTRIES=50000;
    private static final ReentrantLock LOCK=new ReentrantLock();
    private static final List<String> REQUIRED=Collections.unmodifiableList(Arrays.asList(
        "info/legacy_compatible_printers.json","flush/flush_data_standard.txt",
        "flush/flush_data_dual_standard.txt","flush/flush_data_dual_highflow.txt"));
    private EngineResourceArchive() {}

    static File prepare(File filesDirectory,Source source,Progress progress)throws IOException {
        try{LOCK.lockInterruptibly();}catch(InterruptedException e){Thread.currentThread().interrupt();throw canceled();}
        try{
            interrupted();
            Path files=filesDirectory.toPath().toAbsolutePath().normalize();
            noLinks(files);if(!Files.isDirectory(files,LinkOption.NOFOLLOW_LINKS))throw new IOException("Private files directory is unavailable");
            Path parent=files.resolve("slicer-engine");directory(parent);
            Path target=parent.resolve(DIRECTORY);noLinks(target);
            cleanOrphanStaging(parent);
            if(valid(target)){interrupted();return target.toFile();}
            Path staging=Files.createTempDirectory(parent,"resources-staging-");
            Path previous=null;boolean published=false;IOException failure=null;
            try{
                Path archive=staging.resolve("packaged.zip"),tree=staging.resolve("tree");directory(tree);
                // A seekable copy makes ZipFile verify the central directory as well as local entries.
                // These bounds apply only to the packaged resource asset, never imported model files.
                try(InputStream in=source.open();FileOutputStream out=new FileOutputStream(archive.toFile())){
                    byte[] bytes=new byte[65536];long total=0;int n;
                    while((n=in.read(bytes))!=-1){interrupted();total+=n;if(total>LIMIT)throw new IOException("Packaged resources are too large");out.write(bytes,0,n);}
                    out.getFD().sync();
                }
                List<Record> records=extract(archive,tree,progress);interrupted();
                writeMarker(tree,records);interrupted();
                if(!valid(tree))throw new IOException("Prepared resource manifest did not validate");
                interrupted();
                if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){
                    previous=parent.resolve("resources-replaced-"+UUID.randomUUID());move(target,previous);
                }
                try{interrupted();move(tree,target);published=true;}
                catch(IOException error){if(previous!=null){try{move(previous,target);previous=null;}catch(IOException rollback){error.addSuppressed(rollback);}}throw error;}
                return target.toFile();
            }catch(IOException e){failure=e;throw e;}
            finally{
                IOException cleanup=null;
                try{deleteOwned(staging,parent,"resources-staging-");}catch(IOException e){cleanup=e;}
                // A failed rollback is retained for recovery/diagnosis instead of deleting the old tree.
                if(published&&previous!=null)try{deleteOwned(previous,parent,"resources-replaced-");}catch(IOException e){if(cleanup==null)cleanup=e;else cleanup.addSuppressed(e);}
                if(cleanup!=null){if(failure!=null)failure.addSuppressed(cleanup);else throw cleanup;}
            }
        }finally{LOCK.unlock();}
    }

    private static List<Record> extract(Path archive,Path tree,Progress progress)throws IOException {
        List<Record> records=new ArrayList<>();Set<String> paths=new HashSet<>();Set<Path> directories=new HashSet<>();directories.add(tree);long total=0;int count=0;
        try(ZipFile zip=new ZipFile(archive.toFile())){
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){
                interrupted();ZipEntry entry=entries.nextElement();if(++count>ENTRIES)throw new IOException("Too many resource entries");
                String name=entry.getName();if(entry.isDirectory()&&name.endsWith("/"))name=name.substring(0,name.length()-1);
                Path destination=entryPath(tree,name);if(!paths.add(name))throw new IOException("Duplicate resource entry");
                if(entry.isDirectory()){ownedDirectory(destination,tree,directories,true);continue;}
                if(entry.getSize()<0||entry.getCrc()<0||entry.getSize()>LIMIT-total)throw new IOException("Invalid resource entry size");
                ownedDirectory(destination.getParent(),tree,directories,true);CRC32 crc=new CRC32();long size=0;
                try(InputStream in=zip.getInputStream(entry);FileOutputStream out=new FileOutputStream(destination.toFile())){
                    byte[] bytes=new byte[65536];int n;
                    while((n=in.read(bytes))!=-1){interrupted();size+=n;total+=n;if(total>LIMIT||size>entry.getSize())throw new IOException("Resources exceed their declared size");out.write(bytes,0,n);crc.update(bytes,0,n);}
                }
                if(size!=entry.getSize()||crc.getValue()!=entry.getCrc())throw new IOException("Resource CRC or size does not match");
                records.add(new Record(name,size,crc.getValue()));
                if(progress!=null&&records.size()%500==0)progress.update(records.size());
            }
        }
        Set<String> nonempty=new HashSet<>();for(Record record:records)if(record.size>0)nonempty.add(record.name);
        if(!nonempty.containsAll(REQUIRED))throw new IOException("Required engine resources are missing");
        if(progress!=null)progress.update(records.size());interrupted();return records;
    }

    private static void writeMarker(Path tree,List<Record> records)throws IOException {
        try(FileOutputStream file=new FileOutputStream(tree.resolve(".complete").toFile());BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(file,StandardCharsets.UTF_8))){
            writer.write(HEADER);writer.newLine();writer.write(REVISION);writer.newLine();writer.write(Integer.toString(records.size()));writer.newLine();
            Base64.Encoder encoder=Base64.getUrlEncoder().withoutPadding();
            for(Record record:records){interrupted();writer.write(encoder.encodeToString(record.name.getBytes(StandardCharsets.UTF_8))+"\t"+record.size+"\t"+record.crc);writer.newLine();}
            writer.flush();file.getFD().sync();
        }
    }

    /** Fast path validates version, all recorded paths/sizes and required files, not content hashes. */
    private static boolean valid(Path tree)throws IOException {
        noLinks(tree);Path marker=tree.resolve(".complete");
        // readAttributes distinguishes missing/corrupt cache state from denied access;
        // Files.isRegularFile would silently turn permission errors into cache misses.
        BasicFileAttributes treeAttributes,markerAttributes;
        try{treeAttributes=Files.readAttributes(tree,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);if(!treeAttributes.isDirectory())return false;
            markerAttributes=Files.readAttributes(marker,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        }catch(NoSuchFileException e){return false;}
        if(markerAttributes.isSymbolicLink())throw new IOException("Symbolic link in engine resource manifest");
        if(!markerAttributes.isRegularFile()||markerAttributes.size()>8L*1024*1024)return false;
        noLinks(marker);
        try(BufferedReader reader=Files.newBufferedReader(marker,StandardCharsets.UTF_8)){
            if(!HEADER.equals(reader.readLine())||!REVISION.equals(reader.readLine()))return false;
            int count=Integer.parseInt(reader.readLine());if(count<REQUIRED.size()||count>ENTRIES)return false;
            Set<String> names=new HashSet<>(),nonempty=new HashSet<>();Set<Path> directories=new HashSet<>();directories.add(tree);long total=0;
            for(int i=0;i<count;i++){
                interrupted();String line=reader.readLine();if(line==null||line.length()>16384)return false;String[] fields=line.split("\t",-1);if(fields.length!=3)return false;
                String name=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Base64.getUrlDecoder().decode(fields[0]))).toString();long size=Long.parseLong(fields[1]),crc=Long.parseLong(fields[2]);
                if(size<0||size>LIMIT-total||crc<0||crc>0xffffffffL||!names.add(name))return false;total+=size;
                Path file=entryPath(tree,name);ownedDirectory(file.getParent(),tree,directories,false);
                BasicFileAttributes attributes;
                try{attributes=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}catch(NoSuchFileException e){return false;}
                if(attributes.isSymbolicLink())throw new IOException("Symbolic link in cached engine resources");
                if(!attributes.isRegularFile()||attributes.size()!=size)return false;if(size>0)nonempty.add(name);
            }
            return reader.readLine()==null&&nonempty.containsAll(REQUIRED);
        }catch(InterruptedIOException e){throw e;}
        catch(NoSuchFileException|CharacterCodingException|CorruptCacheException|ResourcePathException|IllegalArgumentException e){return false;}
    }

    private static Path entryPath(Path root,String name)throws IOException {
        if(name==null||name.isEmpty()||name.length()>4096||name.indexOf('\\')>=0||name.indexOf(':')>=0||name.startsWith("/")||name.equals(".complete"))throw new ResourcePathException();
        for(int i=0;i<name.length();i++)if(name.charAt(i)<32||name.charAt(i)==127)throw new ResourcePathException();
        for(String part:name.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new ResourcePathException();
        Path path=root.resolve(name).normalize();if(!path.startsWith(root)||path.equals(root))throw new ResourcePathException();return path;
    }
    private static void directory(Path path)throws IOException {noLinks(path);Files.createDirectories(path);noLinks(path);if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Resource directory is unavailable");}
    /** Cache only directory metadata within this call, never across preparations. */
    private static void ownedDirectory(Path path,Path root,Set<Path> checked,boolean create)throws IOException {
        interrupted();if(!path.startsWith(root))throw new IOException("Resource directory escaped its owned tree");if(checked.contains(path))return;
        ownedDirectory(path.getParent(),root,checked,create);
        if(create&&!Files.exists(path,LinkOption.NOFOLLOW_LINKS))Files.createDirectory(path);
        BasicFileAttributes attributes=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(attributes.isSymbolicLink())throw new IOException("Symbolic link in engine resource directory");
        if(!attributes.isDirectory()){if(!create)throw new CorruptCacheException();throw new IOException("Invalid directory in engine resources");}checked.add(path);
    }
    private static void noLinks(Path path)throws IOException {for(Path at=path;at!=null;at=at.getParent())if(Files.isSymbolicLink(at))throw new IOException("Symbolic links are not allowed in engine resources");}
    private static void move(Path from,Path to)throws IOException {try{Files.move(from,to,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(from,to);}}
    /** Same lock as extraction: no live preparation can own these stale direct children. */
    private static void cleanOrphanStaging(Path parent)throws IOException {
        try(DirectoryStream<Path> children=Files.newDirectoryStream(parent,"resources-staging-*")){
            for(Path child:children){interrupted();if(child.getFileName().toString().matches("resources-staging-[0-9]+"))deleteOwned(child,parent,"resources-staging-",true);}
        }
    }
    private static void deleteOwned(Path root,Path parent,String prefix)throws IOException {
        deleteOwned(root,parent,prefix,false);
    }
    private static void deleteOwned(Path root,Path parent,String prefix,boolean interruptible)throws IOException {
        if(!root.toAbsolutePath().normalize().getParent().equals(parent)||!root.getFileName().toString().startsWith(prefix))throw new IOException("Refusing to clean an unowned resource path");
        noLinks(parent);if(!Files.exists(root,LinkOption.NOFOLLOW_LINKS))return;
        Files.walkFileTree(root,new SimpleFileVisitor<Path>(){
            @Override public FileVisitResult preVisitDirectory(Path directory,BasicFileAttributes attributes)throws IOException {if(interruptible)interrupted();return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attributes)throws IOException {if(interruptible)interrupted();Files.delete(file);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult postVisitDirectory(Path directory,IOException error)throws IOException {if(interruptible)interrupted();if(error!=null)throw error;Files.delete(directory);return FileVisitResult.CONTINUE;}
        });
    }
    private static void interrupted()throws InterruptedIOException {if(Thread.currentThread().isInterrupted())throw canceled();}
    private static InterruptedIOException canceled(){return new InterruptedIOException("Engine resource preparation canceled");}
    private static final class CorruptCacheException extends IOException {CorruptCacheException(){super("Ordinary file used as a cached resource directory");}}
    private static final class ResourcePathException extends IOException {ResourcePathException(){super("Invalid resource path");}}
    private static final class Record {final String name;final long size,crc;Record(String n,long s,long c){name=n;size=s;crc=c;}}
}
