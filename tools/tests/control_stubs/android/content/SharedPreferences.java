package android.content;
public interface SharedPreferences {
    boolean getBoolean(String key,boolean fallback);
    String getString(String key,String fallback);
    int getInt(String key,int fallback);
    Editor edit();
    interface Editor {Editor putBoolean(String key,boolean v);Editor putString(String key,String v);Editor putInt(String key,int v);Editor remove(String key);void apply();}
}
