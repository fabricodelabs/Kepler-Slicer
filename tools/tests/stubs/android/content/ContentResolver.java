package android.content;
public final class ContentResolver {
    public java.io.InputStream openInputStream(android.net.Uri uri)throws java.io.IOException {return new java.io.FileInputStream(uri.path);}
}
