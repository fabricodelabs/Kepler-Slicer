package android.net;
public final class Uri {public final String path;public Uri(String path){this.path=path;}public static Uri fromFile(java.io.File file){return new Uri(file.getAbsolutePath());}}
