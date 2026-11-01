package com.orcamobile.app;
import android.content.Context;
import java.io.IOException;
final class ProcessOverridesStore {
    interface SelectionWrite{void commit()throws IOException;}
    static Runnable beforeLock;static int clears;
    static synchronized void changeSelection(Context context,SelectionWrite operation)throws IOException {changeSelection(context,true,operation);}
    static synchronized void changeSelection(Context context,boolean clear,SelectionWrite operation)throws IOException {if(beforeLock!=null){Runnable r=beforeLock;beforeLock=null;r.run();}if(clear){clears++;if(!context.getSharedPreferences("kepler_process_overrides_v1",0).edit().clear().commit())throw new IOException("Cannot clear legacy");}operation.commit();}
}
