package android.content;
public class Context {
    public static final int MODE_PRIVATE=0;
    private static final Memory prefs=new Memory();
    public SharedPreferences getSharedPreferences(String name,int mode){return prefs;}
    static final class Memory implements SharedPreferences, SharedPreferences.Editor {
        final java.util.Map<String,Object> values=new java.util.HashMap<>();
        public boolean getBoolean(String key,boolean fallback){Object v=values.get(key);return v instanceof Boolean?(Boolean)v:fallback;}
        public String getString(String key,String fallback){Object v=values.get(key);return v instanceof String?(String)v:fallback;}
        public int getInt(String key,int fallback){Object v=values.get(key);return v instanceof Integer?(Integer)v:fallback;}
        public Editor edit(){return this;}
        public Editor putBoolean(String key,boolean v){values.put(key,v);return this;}
        public Editor putString(String key,String v){values.put(key,v);return this;}
        public Editor putInt(String key,int v){values.put(key,v);return this;}
        public Editor remove(String key){values.remove(key);return this;}
        public void apply(){}
    }
}
