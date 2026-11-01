package com.orcamobile.app;
import java.io.IOException;
import java.util.Locale;
/** Preserve supported model type when an Android provider omits or truncates display names. */
final class ModelFilename {
    static String normalize(String displayName,String uriName,String mime) throws IOException {
        String name=displayName==null?"":displayName.trim();
        String extension=extension(name);
        if(extension==null)extension=extension(uriName);
        if(extension==null)extension=fromMime(mime);
        if(extension==null)throw new IOException("Не удалось определить формат файла. Выберите STL, OBJ или 3MF с расширением в имени.");
        if(name.isEmpty())name="Модель";
        String own=extension(name);if(own!=null)name=name.substring(0,name.length()-own.length());
        if(name.length()>196){int end=196;if(Character.isHighSurrogate(name.charAt(end-1))&&Character.isLowSurrogate(name.charAt(end)))end--;name=name.substring(0,end);}
        if(name.trim().isEmpty())name="Модель";
        return name+extension;
    }
    private static String extension(String name){if(name==null)return null;String n=name.toLowerCase(Locale.ROOT);for(String ext:new String[]{".stl",".obj",".3mf"})if(n.endsWith(ext))return ext;return null;}
    private static String fromMime(String mime){if(mime==null)return null;switch(mime.toLowerCase(Locale.ROOT)) {
        case "model/stl":case "application/sla":case "model/x.stl-binary":return ".stl";
        case "model/obj":case "text/obj":return ".obj";
        case "model/3mf":case "application/vnd.ms-3mfdocument":case "application/x-3mf":return ".3mf";
        default:return null;
    }}
}
