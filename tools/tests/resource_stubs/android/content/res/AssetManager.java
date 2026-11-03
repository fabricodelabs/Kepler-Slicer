package android.content.res;
import java.io.*;
public final class AssetManager {
    private final File zip;public AssetManager(File zip){this.zip=zip;}
    public InputStream open(String name)throws IOException {if(!"upstream_resources.zip".equals(name))throw new IOException("Unexpected asset request");return new FileInputStream(zip);}
}
