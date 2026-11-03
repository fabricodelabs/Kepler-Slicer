package android.content;
import android.content.res.AssetManager;
import java.io.File;
public final class Context {
    public static final int MODE_PRIVATE=0;
    private final File root;private final AssetManager assets;
    private final java.util.Map<String,MemoryPreferences> preferences=new java.util.HashMap<>();
    public Context(File root,File assetDirectory){this.root=root;assets=new AssetManager(assetDirectory);}
    public Context getApplicationContext(){return this;}
    public File getFilesDir(){File f=new File(root,"files");f.mkdirs();return f;}
    public File getCacheDir(){File f=new File(root,"cache");f.mkdirs();return f;}
    public AssetManager getAssets(){return assets;}
    public String getString(int id){return "test-resource-"+id;}
    public synchronized SharedPreferences getSharedPreferences(String name,int mode){return preferences.computeIfAbsent(name,k->new MemoryPreferences());}
    public synchronized void failNextCommit(String name){((MemoryPreferences)getSharedPreferences(name,0)).fail=true;}
    public ContentResolver getContentResolver(){return new ContentResolver();}
    private static final class MemoryPreferences implements SharedPreferences {
        final java.util.Map<String,Object> values=new java.util.LinkedHashMap<>();boolean fail;
        public synchronized java.util.Map<String,?> getAll(){return new java.util.LinkedHashMap<>(values);}
        public synchronized String getString(String key,String fallback){Object value=values.get(key);return value==null?fallback:(String)value;}
        public synchronized boolean getBoolean(String key,boolean fallback){Object value=values.get(key);return value==null?fallback:(Boolean)value;}
        public Editor edit(){return new Edit();}
        private final class Edit implements Editor {
            final java.util.Map<String,Object> updates=new java.util.LinkedHashMap<>();boolean clear;
            public Editor putString(String key,String value){updates.put(key,value);return this;}
            public Editor putBoolean(String key,boolean value){updates.put(key,value);return this;}
            public Editor putInt(String key,int value){updates.put(key,value);return this;}
            public Editor putLong(String key,long value){updates.put(key,value);return this;}
            public Editor putFloat(String key,float value){updates.put(key,value);return this;}
            public Editor putStringSet(String key,java.util.Set<String> value){updates.put(key,new java.util.HashSet<>(value));return this;}
            public Editor remove(String key){updates.put(key,null);return this;}
            public Editor clear(){clear=true;return this;}
            public boolean commit(){synchronized(MemoryPreferences.this){if(clear)values.clear();for(java.util.Map.Entry<String,Object> entry:updates.entrySet()){if(entry.getValue()==null)values.remove(entry.getKey());else values.put(entry.getKey(),entry.getValue());}boolean ok=!fail;fail=false;return ok;}}
            public void apply(){commit();}
        }
    }
}
