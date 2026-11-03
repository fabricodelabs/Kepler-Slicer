package android.content;
public interface SharedPreferences {
    java.util.Map<String,?> getAll();
    String getString(String key,String fallback);
    boolean getBoolean(String key,boolean fallback);
    default int getInt(String key,int fallback){Object value=getAll().get(key);return value==null?fallback:(Integer)value;}
    Editor edit();
    interface Editor {
        Editor putString(String key,String value);
        Editor putBoolean(String key,boolean value);
        Editor putInt(String key,int value);
        Editor putLong(String key,long value);
        Editor putFloat(String key,float value);
        Editor putStringSet(String key,java.util.Set<String> value);
        Editor remove(String key);
        Editor clear();
        boolean commit();
        void apply();
    }
}
