package com.keplerslicer.app;
import android.content.Context;
import org.json.JSONObject;
import java.io.File;
final class CurrentModelStore {
    static int loads;static JSONObject nextMetadata;
    final File file;final JSONObject metadata;
    private CurrentModelStore(File file,JSONObject metadata){this.file=file;this.metadata=metadata;}
    static CurrentModelStore load(Context context){loads++;String name=context.getSharedPreferences("current_model",0).getString("file","");return name.isEmpty()?null:new CurrentModelStore(new File(new File(context.getFilesDir(),"models"),name),nextMetadata);}
}
