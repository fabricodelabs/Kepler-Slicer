package com.orcamobile.app;
import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
final class CurrentModelStore {
    static int mode;
    static String key="captured-project";
    static File source,lastCopy;
    static int projectMode(Context context){return mode;}
    static String selectionKey(Context context){return key;}
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
