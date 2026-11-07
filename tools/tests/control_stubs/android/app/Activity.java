package android.app;
public class Activity extends android.content.Context {
    public boolean finishing,destroyed,focus=true;
    public final java.util.List<android.content.Intent> started=new java.util.ArrayList<>();
    public boolean isFinishing(){return finishing;}
    public boolean isDestroyed(){return destroyed;}
    public boolean hasWindowFocus(){return focus;}
    public void startActivity(android.content.Intent intent){started.add(intent);}
    public android.content.Context getApplicationContext(){return this;}
}
