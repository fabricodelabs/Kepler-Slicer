package com.keplerslicer.app;
import android.content.Context;
import java.io.*;
import org.json.JSONObject;
/** Deterministic change/cancel hook while the real store is in its native preparation phase. */
final class ImportedModelPreview {
    interface NativeHook{void run()throws IOException;}
    static NativeHook nextRead;
    final StlMesh mesh=new StlMesh();final JSONObject metadata=new JSONObject();
    static ImportedModelPreview read(Context c,File f,int plate)throws IOException {NativeHook hook=nextRead;nextRead=null;if(hook!=null)hook.run();return new ImportedModelPreview();}
}
