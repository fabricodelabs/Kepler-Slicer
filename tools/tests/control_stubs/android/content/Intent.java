package android.content;
public class Intent {
    public final java.util.Map<String,Object> extras=new java.util.HashMap<>();
    public Intent(Context owner,Class<?> target){}
    public Intent putExtra(String key,boolean value){extras.put(key,value);return this;}
    public Intent putExtra(String key,long value){extras.put(key,value);return this;}
}
