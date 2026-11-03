package com.orcamobile.app;

import android.content.Context;
import java.io.File;
import java.io.IOException;

/** Worker-only preparation of the pinned engine's immutable app-private resources. */
final class EngineResources {
    interface Progress { void update(int extractedFiles); }
    private EngineResources() {}
    static File prepare(Context context)throws IOException { return prepare(context,null); }
    static File prepare(Context context,Progress progress)throws IOException {
        Context app=context.getApplicationContext();
        // Context owns this root. Resolve Android's legitimate /data/user aliases
        // before enforcing the no-symlink rule inside our engine-owned subtree.
        File privateFiles=app.getFilesDir().toPath().toRealPath().toFile();
        return EngineResourceArchive.prepare(privateFiles,
            ()->app.getAssets().open("upstream_resources.zip"),progress==null?null:progress::update);
    }
}
