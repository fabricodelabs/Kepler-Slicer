package android.content;
import java.io.File;
public final class Context {
    private final File root;
    public Context(File root){this.root=root;root.mkdirs();}
    public File getFilesDir(){return root;}
    public String getString(int resource){return "No workspace backup is available";}
}
