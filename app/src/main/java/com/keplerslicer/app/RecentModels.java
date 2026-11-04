package com.keplerslicer.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** SAF history contains only URIs for which Android grants persistent read access. */
final class RecentModels {
    private static final Object LOCK=new Object();
    private static final java.util.concurrent.ExecutorService WRITER=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"recent-history"));
    static final class Entry { final Uri uri; final String name,thumbnail; final long time;
        Entry(Uri u,String n,String t,long tm){uri=u;name=n;thumbnail=t;time=tm;}
    }
    static List<Entry> load(Context c) {
        synchronized(LOCK) {
            List<Entry> result=loadAll(c);
            int limit=c.getSharedPreferences("app_preferences",0).getInt("recent_count",12);
            return new ArrayList<>(result.subList(0,Math.min(result.size(),Math.max(1,Math.min(30,limit)))));
        }
    }
    private static List<Entry> loadAll(Context c) {
            List<Entry> result=new ArrayList<>();
            try { JSONArray list=new JSONArray(c.getSharedPreferences("recent_models",0).getString("entries","[]"));
                for(int i=0;i<list.length();i++) { JSONObject o=list.getJSONObject(i); String thumb=o.optString("thumb","");
                    if(!thumb.matches("[a-f0-9]{64}\\.png")) thumb="";
                    result.add(new Entry(Uri.parse(o.getString("uri")),o.getString("name"),thumb,o.optLong("time")));
                }
            } catch(Exception ignored) { }
            return result;
    }
    /** Best effort; an unavailable history never invalidates a successfully imported model. */
    static void remember(Context context,Uri uri,String name,StlMesh mesh) {
        if(!GeneralPreferences.addModelToRecent(context,name))return;
        Context c=context.getApplicationContext();
        long selectedAt=System.currentTimeMillis();
        WRITER.execute(()->rememberNow(c,uri,name,mesh,selectedAt));
    }
    private static void rememberNow(Context c,Uri uri,String name,StlMesh mesh,long selectedAt) {
        try {
            if(!GeneralPreferences.addModelToRecent(c,name))return;
            if(!"content".equals(uri.getScheme())) return;
            boolean retained=false;
            for(android.content.UriPermission permission:c.getContentResolver().getPersistedUriPermissions())
                if(permission.isReadPermission()&&permission.getUri().equals(uri)){retained=true;break;}
            if(!retained)return;
            StringBuilder h=new StringBuilder(); for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(uri.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))) h.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            String thumb=h+".png"; File dir=new File(c.getFilesDir(),"recent-thumbnails");
            if(dir.isDirectory()||dir.mkdirs()) {
                File staged=File.createTempFile("thumb-",".png",dir);Bitmap bitmap=thumbnail(mesh);
                try {try(FileOutputStream out=new FileOutputStream(staged)){bitmap.compress(Bitmap.CompressFormat.PNG,90,out);out.getFD().sync();}
                    java.nio.file.Files.move(staged.toPath(),new File(dir,thumb).toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } finally {bitmap.recycle();staged.delete();}
            }
            synchronized(LOCK) {
                List<Entry> entries=loadAll(c); entries.removeIf(e->e.uri.equals(uri)); entries.add(0,new Entry(uri,name,thumb,selectedAt)); entries.sort((a,b)->Long.compare(b.time,a.time));
                JSONArray a=new JSONArray(); for(int i=0;i<Math.min(30,entries.size());i++) {Entry e=entries.get(i); a.put(new JSONObject().put("uri",e.uri.toString()).put("name",e.name).put("thumb",e.thumbnail).put("time",e.time));}
                c.getSharedPreferences("recent_models",0).edit().putString("entries",a.toString()).apply();
            }
        } catch(Exception ignored) { }
    }
    static File thumbnailFile(Context c,Entry e) { return e.thumbnail.isEmpty()?null:new File(new File(c.getFilesDir(),"recent-thumbnails"),e.thumbnail); }
    private static Bitmap thumbnail(StlMesh mesh) {
        Bitmap bitmap=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bitmap); c.drawColor(0xff272a26);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(0xffc5cac1); p.setStyle(Paint.Style.FILL); float[] v=mesh.vertices;
        // The mesh reader normalizes the original geometry. Project actual triangles, without editing the model.
        int stride=Math.max(1,(v.length/9)/3000); Path path=new Path();
        for(int i=0;i+8<v.length;i+=9*stride) {path.reset(); for(int j=0;j<3;j++) {float x=v[i+j*3],y=v[i+j*3+1],z=v[i+j*3+2]; float sx=160+(x*.82f-y*.57f)*67,sy=140+(x*.25f+y*.36f-z*.9f)*67; if(j==0)path.moveTo(sx,sy);else path.lineTo(sx,sy);}path.close();
            float ax=v[i+3]-v[i],ay=v[i+4]-v[i+1],az=v[i+5]-v[i+2],bx=v[i+6]-v[i],by=v[i+7]-v[i+1],bz=v[i+8]-v[i+2];
            float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx,n=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            int shade=n>0?(int)(120+90*Math.abs((nx*.3f+ny*.5f+nz*.8f)/n)):160; shade=Math.max(95,Math.min(220,shade)); p.setColor(android.graphics.Color.rgb(shade,shade,shade)); c.drawPath(path,p);
        } return bitmap;
    }
}
