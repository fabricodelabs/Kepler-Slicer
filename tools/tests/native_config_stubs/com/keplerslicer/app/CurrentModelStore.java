package com.keplerslicer.app;
import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
final class CurrentModelStore {
    static int mode;
    static String key="captured-project";
    static boolean hasModel=true,corruptSelection;
    static File source,lastCopy;
    static int projectMode(Context context){return mode;}
    static String selectionKey(Context context)throws IOException {
        if(!hasModel)throw new IOException("Сначала откройте STL-модель.");
        if(corruptSelection)throw new IOException("Invalid selected model metadata");return key;
    }
    static String settingsSelectionKey(Context context)throws IOException {
        if(corruptSelection)throw new IOException("Invalid selected model metadata");
        if(!hasModel){if(mode>0)throw new IOException("Project metadata without model");return "\0kepler-no-model-v1";}
        return selectionKey(context);
    }
    static final class Snapshot {
        final int projectMode,plateIndex=0;
        final String selectionKey,materialEdits="{}";
        final File file;
        Snapshot(File f){file=f;projectMode=mode;selectionKey=key;}
    }
    static Snapshot snapshotToDirectory(Context context,File directory)throws IOException {
        lastCopy=new File(directory,"model.3mf");Files.copy(source.toPath(),lastCopy.toPath());return new Snapshot(lastCopy);
    }
}
