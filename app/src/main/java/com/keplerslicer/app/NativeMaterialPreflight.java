package com.keplerslicer.app;

import android.content.Context;
import com.keplerslicer.engine.ExperimentalSlicer;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Validate a frozen backup overlay against its actual project before publication. */
final class NativeMaterialPreflight {
    private NativeMaterialPreflight() {}
    interface Publication { void run()throws IOException; }
    static final class Validated {
        private final Context app;private final EngineProfileSession session;
        private Validated(Context context,EngineProfileSession frozen){app=context;session=frozen;}
        void publish(Publication operation)throws IOException {
            ProcessOverridesStore.changeSelection(app,false,()->{
                interrupted();if(!session.stillCurrent(app))throw new IOException(app.getString(R.string.native_material_backup_changed));operation.run();
            });
        }
    }
    static Validated validate(Context context,File original,int plate,int mode,String materials)throws IOException {
        Context app=context.getApplicationContext();JSONObject expected=ProjectMaterialValidation.forRestore(original.getName(),mode,materials);
        if(expected.length()==0)return null;interrupted();
        final EngineProfileSession session;
        try{session=EngineProfileSession.capture(app);}catch(IOException unavailable){throw new IOException(app.getString(R.string.native_material_backup_profiles),unavailable);}
        ProcessOverridesStore.Identity id=session.id;
        try{
            byte[] bytes=ExperimentalSlicer.projectMaterialSettings(id.printer.vendor,id.process.vendor,id.filament.vendor,id.printerName,id.processName,id.filamentName,
                session.resources.getAbsolutePath(),session.data.getAbsolutePath(),session.selection.overrides,original.getAbsolutePath(),plate,mode,materials,-1,"",false);
            if(bytes==null)throw new IOException(app.getString(R.string.native_material_backup_invalid));
            JSONObject actual=new JSONObject(new String(bytes,StandardCharsets.UTF_8));JSONArray slots=actual.getJSONArray("slots"),matrix=actual.getJSONArray("flush_volumes_matrix"),colours=expected.getJSONArray("colours"),volumes=expected.getJSONArray("flush_volumes");
            if(actual.getInt("version")!=1||slots.length()!=colours.length()||matrix.length()!=volumes.length())throw new IOException(app.getString(R.string.native_material_backup_invalid));
            for(int i=0;i<slots.length();i++)if(!slots.getJSONObject(i).getString("color").equals(colours.getString(i)))throw new IOException(app.getString(R.string.native_material_backup_invalid));
            for(int i=0;i<matrix.length();i++)if(Double.compare(matrix.getDouble(i),volumes.getDouble(i))!=0)throw new IOException(app.getString(R.string.native_material_backup_invalid));
            interrupted();if(!session.stillCurrent(app))throw new IOException(app.getString(R.string.native_material_backup_changed));
            return new Validated(app,session);
        }catch(IOException e){throw e;}catch(Exception|LinkageError e){throw new IOException(app.getString(R.string.native_material_backup_invalid),e);}
    }
    private static void interrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Material backup validation canceled");}
}
