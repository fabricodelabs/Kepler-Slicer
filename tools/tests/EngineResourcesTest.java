package com.orcamobile.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;

/** Exercises production extraction, cache validation and transactional failures with actual ZIP bytes. */
public final class EngineResourcesTest {
    private static int checks;
    private static Path suite;
    private interface Action { void run()throws Exception; }
    private static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    private static void rejects(Action action,String name)throws Exception {try{action.run();throw new AssertionError(name);}catch(IOException expected){checks++;}}
    private static Path fresh(String name)throws IOException {return Files.createDirectories(suite.resolve(name));}
    private static Path target(Path files){return files.resolve("slicer-engine").resolve(EngineResourceArchive.DIRECTORY);}
    private static EngineResourceArchive.Source source(byte[] zip){return ()->new ByteArrayInputStream(zip);}
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    private static Map<String,byte[]> entries(){
        Map<String,byte[]> e=new LinkedHashMap<>();e.put("info/legacy_compatible_printers.json",bytes("{\"version\":1}"));
        e.put("flush/flush_data_standard.txt",bytes("CRC_DATA_MARKER_123"));e.put("flush/flush_data_dual_standard.txt",bytes("dual standard\n"));
        e.put("flush/flush_data_dual_highflow.txt",bytes("dual highflow\n"));e.put("images/preview.svg",bytes("<svg/>"));return e;
    }
    private static byte[] zip(Map<String,byte[]> entries)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream out=new ZipOutputStream(bytes)){
            for(Map.Entry<String,byte[]> row:entries.entrySet()){
                ZipEntry e=new ZipEntry(row.getKey());CRC32 crc=new CRC32();crc.update(row.getValue());e.setMethod(ZipEntry.STORED);e.setSize(row.getValue().length);e.setCompressedSize(row.getValue().length);e.setCrc(crc.getValue());out.putNextEntry(e);out.write(row.getValue());out.closeEntry();
            }
        }return bytes.toByteArray();
    }
    private static void cleanStaging(Path files,String name)throws IOException {
        Path parent=files.resolve("slicer-engine");if(!Files.isDirectory(parent))return;
        try(java.util.stream.Stream<Path> list=Files.list(parent)){check(list.noneMatch(p->p.getFileName().toString().startsWith("resources-staging-")),name);}
    }
    private static void keepsOld(Path files,byte[] broken,String name)throws Exception {
        Path old=target(files);Files.createDirectories(old);Files.write(old.resolve("old.txt"),bytes("keep me"));Files.write(old.resolve(".complete"),new byte[0]);
        rejects(()->EngineResourceArchive.prepare(files.toFile(),source(broken),null),name);
        check(Arrays.equals(Files.readAllBytes(old.resolve("old.txt")),bytes("keep me")),name+": old bytes retained");
        check(Files.size(old.resolve(".complete"))==0,name+": old marker retained");cleanStaging(files,name+": staging removed");
    }
    public static void main(String[] args)throws Exception {
        suite=Files.createDirectories(Paths.get(args[0]));Map<String,byte[]> expected=entries();byte[] valid=zip(expected);
        Path first=fresh("success");AtomicInteger progress=new AtomicInteger();File result=EngineResourceArchive.prepare(first.toFile(),source(valid),progress::set);
        check(result.toPath().equals(target(first)),"Service owns the exact private versioned path");
        for(Map.Entry<String,byte[]> row:expected.entrySet())check(Arrays.equals(Files.readAllBytes(result.toPath().resolve(row.getKey())),row.getValue()),"Exact extracted bytes: "+row.getKey());
        check(progress.get()==expected.size(),"Progress reports extracted file count");
        check(Files.readAllLines(result.toPath().resolve(".complete")).get(0).equals("kepler-engine-resources-v1"),"Published cache has a versioned manifest");
        EngineResourceArchive.prepare(first.toFile(),()->{throw new IOException("Cache hit must not open the asset");},null);check(true,"Valid cache does not reopen ZIP");cleanStaging(first,"Successful staging removed");

        Files.delete(result.toPath().resolve("images/preview.svg"));AtomicInteger reopened=new AtomicInteger();EngineResourceArchive.prepare(first.toFile(),()->{reopened.incrementAndGet();return new ByteArrayInputStream(valid);},null);
        check(reopened.get()==1&&Files.isRegularFile(target(first).resolve("images/preview.svg")),"Missing cached file rebuilds from the real ZIP");
        Files.write(target(first).resolve("flush/flush_data_standard.txt"),bytes("short"));EngineResourceArchive.prepare(first.toFile(),source(valid),null);
        check(Arrays.equals(Files.readAllBytes(target(first).resolve("flush/flush_data_standard.txt")),expected.get("flush/flush_data_standard.txt")),"Wrong cached size rebuilds");
        Files.delete(target(first).resolve("images/preview.svg"));Files.delete(target(first).resolve("images"));EngineResourceArchive.prepare(first.toFile(),source(valid),null);
        check(Files.isRegularFile(target(first).resolve("images/preview.svg")),"Missing entire cached directory is repaired from ZIP");cleanStaging(first,"Missing directory repair staging removed");

        keepsOld(fresh("truncated"),Arrays.copyOf(valid,valid.length-22),"Truncated central directory rejected");
        byte[] crc=valid.clone(),needle=bytes("CRC_DATA_MARKER_123");int at=indexOf(crc,needle,0);check(at>=0,"CRC fixture payload found");crc[at]^=1;keepsOld(fresh("crc"),crc,"Stored payload CRC mismatch rejected");
        Path invalid=fresh("invalid-zip");rejects(()->EngineResourceArchive.prepare(invalid.toFile(),source(bytes("not a ZIP")),null),"Non-ZIP rejected");check(!Files.exists(target(invalid)),"Invalid archive never publishes final directory");cleanStaging(invalid,"Invalid ZIP staging removed");
        Path empty=fresh("missing-required");rejects(()->EngineResourceArchive.prepare(empty.toFile(),source(zip(Collections.singletonMap("images/a.svg",bytes("x")))),null),"ZIP without required Orca datasets rejected");check(!Files.exists(target(empty)),"Missing required resources never publish");

        String[] badNames={"../outside.txt","/absolute.txt","C:/absolute.txt","C:relative.txt","nested\\escape.txt","nested/../escape.txt","nested//escape.txt",".complete"};
        for(int i=0;i<badNames.length;i++){Map<String,byte[]> bad=entries();bad.put(badNames[i],bytes("escape"));Path files=fresh("bad-path-"+i);rejects(()->EngineResourceArchive.prepare(files.toFile(),source(zip(bad)),null),"Unsafe resource path rejected: "+badNames[i]);check(!Files.exists(target(files)),"Unsafe path not published: "+i);cleanStaging(files,"Unsafe path staging removed: "+i);}
        check(!Files.exists(suite.resolve("outside.txt")),"ZIP traversal never creates outside output");
        Map<String,byte[]> duplicates=entries();duplicates.put("extras/aa.txt",bytes("a"));duplicates.put("extras/bb.txt",bytes("b"));byte[] duplicate=zip(duplicates);replaceAll(duplicate,bytes("extras/bb.txt"),bytes("extras/aa.txt"));
        Path dup=fresh("duplicates");rejects(()->EngineResourceArchive.prepare(dup.toFile(),source(duplicate),null),"Duplicate resource paths rejected");cleanStaging(dup,"Duplicate staging removed");

        Path pre=fresh("pre-cancel");Thread.currentThread().interrupt();try{EngineResourceArchive.prepare(pre.toFile(),source(valid),null);throw new AssertionError("Pre-cancel must fail");}catch(InterruptedIOException expectedCancel){check(Thread.currentThread().isInterrupted(),"Pre-cancel preserves interrupt flag");}finally{Thread.interrupted();}check(!Files.exists(target(pre)),"Pre-cancel publishes nothing");
        Path copyCancel=fresh("copy-cancel");try{EngineResourceArchive.prepare(copyCancel.toFile(),()->new ByteArrayInputStream(valid){@Override public synchronized int read(byte[] b,int o,int l){int n=super.read(b,o,l);Thread.currentThread().interrupt();return n;}},null);throw new AssertionError("Copy cancellation must fail");}catch(InterruptedIOException expectedCancel){check(true,"Cancellation during ZIP copy stops preparation");}finally{Thread.interrupted();}check(!Files.exists(target(copyCancel)),"Copy cancellation exposes no final tree");cleanStaging(copyCancel,"Copy cancellation staging removed");
        Path extraction=fresh("extract-cancel");Path old=target(extraction);Files.createDirectories(old);Files.write(old.resolve("old.txt"),bytes("previous"));
        try{EngineResourceArchive.prepare(extraction.toFile(),source(valid),count->Thread.currentThread().interrupt());throw new AssertionError("Extraction cancellation must fail");}catch(InterruptedIOException expectedCancel){check(true,"Cancellation after extraction prevents publication");}finally{Thread.interrupted();}
        check(Files.readString(old.resolve("old.txt")).equals("previous"),"Cancellation preserves old tree");check(!Files.exists(old.resolve(".complete")),"Cancellation does not mark old cache complete");cleanStaging(extraction,"Extraction cancellation staging removed");

        Path io=fresh("source-io");rejects(()->EngineResourceArchive.prepare(io.toFile(),()->new InputStream(){int count;public int read()throws IOException{if(count++>20)throw new IOException("Injected asset failure");return 0;}},null),"Asset read failure propagated");check(!Files.exists(target(io)),"Source I/O failure publishes nothing");cleanStaging(io,"Source I/O failure staging removed");
        lockCancellation(valid);
        Path legacy=fresh("legacy");Files.createDirectories(target(legacy));Files.write(target(legacy).resolve(".complete"),new byte[0]);Files.write(target(legacy).resolve("stale.txt"),bytes("old"));EngineResourceArchive.prepare(legacy.toFile(),source(valid),null);
        check(!Files.exists(target(legacy).resolve("stale.txt")),"Legacy empty marker receives a clean validated replacement");check(Files.size(target(legacy).resolve(".complete"))>0,"Legacy marker upgraded");
        Path wrapper=fresh("android-wrapper"),child=Files.createDirectories(wrapper.resolve("alias-child")),asset=suite.resolve("wrapper-resources.zip");Files.write(asset,valid);
        File wrapped=EngineResources.prepare(new android.content.Context(child.resolve("..").toFile(),asset.toFile()));
        check(wrapped.toPath().equals(target(wrapper.toRealPath())),"Android wrapper resolves the trusted Context files-root alias before private-path validation");
        symlinkSafety(valid);
        if(args.length>1){
            Path packaged=Paths.get(args[1]),files=fresh("packaged-asset");AtomicInteger count=new AtomicInteger();
            long extractStart=System.nanoTime();EngineResourceArchive.prepare(files.toFile(),()->Files.newInputStream(packaged),count::set);
            long extractNs=System.nanoTime()-extractStart;
            check(count.get()>10000,"Actual packaged Orca resource tree extracted with CRC and required datasets");
            long cacheStart=System.nanoTime();EngineResourceArchive.prepare(files.toFile(),()->{throw new IOException("Packaged cache must not reopen asset");},null);long cacheNs=System.nanoTime()-cacheStart;
            check(true,"Actual packaged resource manifest takes the cache fast path");cleanStaging(files,"Actual packaged staging removed");
            System.out.printf(Locale.ROOT,"PACKAGED files=%d extract=%.3fs cache=%.3fs%n",count.get(),extractNs/1e9,cacheNs/1e9);
        }
        System.out.println("PASS "+checks+" engine resource checks (production extractor; Android boundary adapted; no JNI)");
    }
    private static void lockCancellation(byte[] valid)throws Exception {
        Path producer=fresh("lock-owner"),waiting=fresh("lock-waiter");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),started=new CountDownLatch(1);AtomicReference<Throwable> producerError=new AtomicReference<>(),waiterError=new AtomicReference<>();
        Thread owner=new Thread(()->{try{EngineResourceArchive.prepare(producer.toFile(),()->{entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IOException("Test release timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException();}return new ByteArrayInputStream(valid);},null);}catch(Throwable t){producerError.set(t);}},"resource-test-lock-owner");
        Thread waiter=new Thread(()->{started.countDown();try{EngineResourceArchive.prepare(waiting.toFile(),source(valid),null);}catch(Throwable t){waiterError.set(t);}},"resource-test-lock-waiter");
        owner.start();try{check(entered.await(5,TimeUnit.SECONDS),"First worker owns preparation lock");waiter.start();check(started.await(5,TimeUnit.SECONDS),"Second worker attempts preparation");waiter.interrupt();waiter.join(2000);check(!waiter.isAlive()&&waiterError.get() instanceof InterruptedIOException,"Canceled lock waiter exits before owner releases lock");check(!Files.exists(target(waiting)),"Canceled lock waiter creates no resources");}finally{release.countDown();owner.join(5000);if(waiter.isAlive()){waiter.interrupt();waiter.join(2000);}}
        check(!owner.isAlive()&&producerError.get()==null,"Lock owner can finish normally after waiter cancellation");EngineResourceArchive.prepare(waiting.toFile(),source(valid),null);check(Files.exists(target(waiting).resolve(".complete")),"Canceled lock acquisition does not leak the lock");
    }
    private static void symlinkSafety(byte[] valid)throws Exception {
        Path files=fresh("symlink"),outside=fresh("outside-directory"),link=files.resolve("slicer-engine");Files.write(outside.resolve("keep.txt"),bytes("safe"));
        try{Files.createSymbolicLink(link,outside);}catch(UnsupportedOperationException|IOException|SecurityException unavailable){System.out.println("SKIP symlink fixture: OS does not permit creating symbolic links");return;}
        rejects(()->EngineResourceArchive.prepare(files.toFile(),source(valid),null),"Symlink private engine root rejected");check(Files.readString(outside.resolve("keep.txt")).equals("safe"),"Rejected symlink target remains unchanged");check(!Files.exists(outside.resolve(EngineResourceArchive.DIRECTORY)),"No resource output follows private-root symlink");
    }
    private static int indexOf(byte[] data,byte[] needle,int from){outer:for(int i=from;i<=data.length-needle.length;i++){for(int j=0;j<needle.length;j++)if(data[i+j]!=needle[j])continue outer;return i;}return -1;}
    private static void replaceAll(byte[] data,byte[] from,byte[] to){for(int at=0;(at=indexOf(data,from,at))>=0;at+=to.length)System.arraycopy(to,0,data,at,to.length);}
}
