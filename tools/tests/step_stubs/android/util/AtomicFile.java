package android.util;
import java.io.*;
import java.nio.file.*;
/** Host file-only stand-in; Android AtomicFile behavior is verified on device separately. */
public final class AtomicFile {
    private final File file,partial;
    public AtomicFile(File f){file=f;partial=new File(f.getPath()+".new");}
    public FileInputStream openRead()throws IOException{return new FileInputStream(file);}
    public FileOutputStream startWrite()throws IOException{return new FileOutputStream(partial);}
    public void finishWrite(FileOutputStream out)throws IOException{out.getFD().sync();out.close();Files.move(partial.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
    public void failWrite(FileOutputStream out){try{out.close();}catch(IOException ignored){}partial.delete();}
}
