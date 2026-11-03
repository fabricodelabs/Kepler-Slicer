package com.orcamobile.app;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Iterator;
/** Conservative preview policy. It never applies, strips or executes embedded project configuration. */
final class GeneralProjectPolicy {
    final boolean project,settingsPresent,customCodePresent;
    private GeneralProjectPolicy(boolean p,boolean settings,boolean code){project=p;settingsPresent=settings;customCodePresent=code;}
    boolean needsDecision(String mode){return project&&("always_ask".equals(mode)||(settingsPresent&&!"load_geometry".equals(mode)));}
    static GeneralProjectPolicy inspect(JSONObject metadata){
        if(metadata!=null&&"step".equals(metadata.optString("source_format")))return new GeneralProjectPolicy(false,false,false);
        if(metadata==null||!"3mf".equals(metadata.optString("format")))return new GeneralProjectPolicy(false,false,false);
        JSONObject data=metadata.optJSONObject("project_data");if(data==null)data=metadata;
        boolean settings=nonempty(data.optJSONObject("project_config"))||length(data.optJSONArray("embedded_presets"))>0||length(data.optJSONArray("embedded_files"))>0||hasScopedSettings(data);
        JSONArray plates=data.optJSONArray("plates");if(plates!=null)for(int i=0;i<plates.length();i++){JSONObject plate=plates.optJSONObject(i);if(plate!=null&&nonempty(plate.optJSONObject("config")))settings=true;}
        return new GeneralProjectPolicy(true,settings,hasGcode(data));
    }
    private static int length(JSONArray a){return a==null?0:a.length();}
    private static boolean nonempty(JSONObject o){return o!=null&&o.length()>0;}
    private static boolean hasScopedSettings(Object node){
        if(node instanceof JSONObject){JSONObject o=(JSONObject)node;Iterator<String> keys=o.keys();while(keys.hasNext()){String key=keys.next();Object value=o.opt(key);if(key.equals("config")&&value instanceof JSONObject&&((JSONObject)value).length()>0)return true;if(key.equals("layer_height_profile")&&value instanceof JSONArray&&((JSONArray)value).length()>0)return true;if(hasScopedSettings(value))return true;}}
        else if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++)if(hasScopedSettings(a.opt(i)))return true;}return false;
    }
    private static boolean hasGcode(Object value){
        if(value instanceof JSONObject){JSONObject object=(JSONObject)value;Iterator<String> keys=object.keys();while(keys.hasNext()){String key=keys.next();Object item=object.opt(key);if((key.endsWith("_gcode")||key.equals("gcode"))&&item instanceof String&&!((String)item).trim().isEmpty())return true;
            if(key.equals("plate_custom_gcode")&&item instanceof JSONArray){JSONArray a=(JSONArray)item;for(int i=0;i<a.length();i++){JSONObject plate=a.optJSONObject(i);if(plate!=null&&length(plate.optJSONArray("items"))>0)return true;}}
            if(hasGcode(item))return true;}
        }else if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)if(hasGcode(a.opt(i)))return true;}return false;
    }
}
