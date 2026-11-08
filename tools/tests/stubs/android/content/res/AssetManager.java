package android.content.res;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
public final class AssetManager {
    private final File root;
    public AssetManager(File root){this.root=root;}
    /** Like the device: app assets plus the install-time asset pack (engine_assets) next to the app module. */
    public InputStream open(String path) throws IOException{
        File f=new File(root,path);
        if(!f.isFile()){File project=up(root,4);if(project!=null){File pack=new File(project,"engine_assets/src/main/assets/"+path);if(pack.isFile())f=pack;}}
        return new FileInputStream(f);
    }
    private static File up(File f,int n){for(int i=0;i<n&&f!=null;i++)f=f.getAbsoluteFile().getParentFile();return f;}
}
