package com.orcamobile.app;

import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Structural validation shared by backup restore and the native preflight boundary. */
final class ProjectMaterialValidation {
    private ProjectMaterialValidation() {}
    static JSONObject validate(String text)throws IOException {
        try{
            JSONObject edits=new JSONObject(text);if(edits.length()==0)return edits;
            if(edits.length()!=2||!edits.has("colours")||!edits.has("flush_volumes"))throw new IOException("Material settings require colours and flush_volumes only");
            Iterator<String> keys=edits.keys();while(keys.hasNext()){String key=keys.next();if(!key.equals("colours")&&!key.equals("flush_volumes"))throw new IOException("Unknown material setting");}
            JSONArray colours=edits.getJSONArray("colours"),volumes=edits.getJSONArray("flush_volumes");int slots=colours.length();long square=(long)slots*slots;
            if(slots==0||square>Integer.MAX_VALUE||volumes.length()==0||volumes.length()%square!=0)throw new IOException("Invalid material matrix dimensions");
            for(int i=0;i<slots;i++){Object colour=colours.get(i);if(!(colour instanceof String)||!((String)colour).matches("#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?"))throw new IOException("Invalid material colour");}
            for(int i=0;i<volumes.length();i++){Object value=volumes.get(i);if(!(value instanceof Number))throw new IOException("Flushing volume must be numeric");double volume=((Number)value).doubleValue();if(!Double.isFinite(volume)||volume<0||volume>20000)throw new IOException("Flushing volume is outside the native range");}
            return edits;
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Invalid material settings",e);}
    }
    static JSONObject forRestore(String name,int mode,String text)throws IOException {
        if(mode< -1||mode>2)throw new IOException("Invalid restored project mode");
        boolean project=name!=null&&name.toLowerCase(Locale.ROOT).endsWith(".3mf");
        if(mode>0&&!project)throw new IOException("Full-project mode requires a 3MF source");
        JSONObject edits=validate(text);
        if(edits.length()>0&&(!project||mode<1))throw new IOException("Material settings require a configured 3MF project");
        return edits;
    }
}
