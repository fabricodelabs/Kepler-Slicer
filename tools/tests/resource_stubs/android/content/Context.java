package android.content;
import java.io.File;
import android.content.res.AssetManager;
/** Only the Android boundary is adapted; archive validation/publication are production code. */
public final class Context {
    private final File files;private final AssetManager assets;
    public Context(File files,File resourceZip){this.files=files;assets=new AssetManager(resourceZip);}
    public Context getApplicationContext(){return this;}
    public File getFilesDir(){return files;}
    public AssetManager getAssets(){return assets;}
}
