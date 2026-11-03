package com.orcamobile.app;

import android.content.Context;
import com.keplerslicer.engine.NativeModelIO;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded preview from Orca's model loader; the original project remains untouched on disk. */
final class ImportedModelPreview {
    final StlMesh mesh;
    final JSONObject metadata;
    private ImportedModelPreview(StlMesh m, JSONObject meta) { mesh=m; metadata=meta; }
    static ImportedModelPreview read(Context context,File file,int plate) throws IOException {
        File root=new File(context.getFilesDir(),"slicer-engine");
        if(!root.isDirectory() && !root.mkdirs()) throw new IOException("Не удалось подготовить каталог движка.");
        File resources=EngineResources.prepare(context);
        try {
            byte[] data=NativeModelIO.inspect(file.getAbsolutePath(),resources.getAbsolutePath(),root.getAbsolutePath(),plate,200000);
            if(data==null||data.length<4)throw new IOException("Движок не вернул геометрию модели.");
            ByteBuffer b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            int bytes=b.getInt(); if(bytes<2||bytes>b.remaining()||(b.remaining()-bytes)%36!=0)throw new IOException("Некорректный ответ движка.");
            byte[] json=new byte[bytes]; b.get(json); JSONObject meta=new JSONObject(new String(json,StandardCharsets.UTF_8));
            JSONArray min=meta.getJSONArray("bounds_min"), max=meta.getJSONArray("bounds_max");
            if(min.length()!=3||max.length()!=3)throw new IOException("Некорректные границы модели.");
            double[] low=new double[3],high=new double[3]; for(int i=0;i<3;i++){low[i]=min.getDouble(i);high[i]=max.getDouble(i);}
            float[] xyz=new float[b.remaining()/4]; for(int i=0;i<xyz.length;i++)xyz[i]=b.getFloat();
            long count=meta.getLong("triangle_count"); if(meta.getInt("preview_triangle_count")!=xyz.length/9)throw new IOException("Некорректная длина предпросмотра.");
            return new ImportedModelPreview(StlMesh.fromNative(xyz,low,high,count),meta);
        } catch(IOException e){throw e;} catch(Exception e){throw new IOException("Не удалось прочитать модель Orca: "+e.getMessage(),e);}
        catch(LinkageError e){throw new IOException("Движок для чтения модели недоступен.",e);}
    }
}
