package com.orcamobile.app.settings;

import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Configuration-only 3MF transport; never parses or rewrites model geometry. */
public final class ProjectConfigArchive {
    public static final String PROJECT_CONFIG="Metadata/project_settings.config";
    private ProjectConfigArchive() { }
    /** The exact Orca JSON object, including unrecognized keys and original value types. */
    public static JSONObject readProjectConfig(File archive) throws IOException {
        try(ZipFile zip=new ZipFile(archive)){ZipEntry entry=uniqueProjectEntry(zip);try(InputStream in=zip.getInputStream(entry)){return PresetJson.read(in);}}
    }
    /** Original embedded preset JSON; these are not implicitly selected/applied to slicing. */
    public static List<JSONObject> readEmbeddedPresets(File archive) throws IOException {
        List<JSONObject> out=new ArrayList<>();try(ZipFile zip=new ZipFile(archive)){Enumeration<? extends ZipEntry> entries=zip.entries();Set<String> seen=new HashSet<>();while(entries.hasMoreElements()){ZipEntry entry=entries.nextElement();String name=entry.getName().toLowerCase(Locale.ROOT);if(!seen.add(name))throw new IOException("Duplicate 3MF entry");if(!entry.isDirectory()&&(name.startsWith("metadata/process_settings_")||name.startsWith("metadata/filament_settings_")||name.startsWith("metadata/machine_settings_"))&&name.endsWith(".config")){try(InputStream in=zip.getInputStream(entry)){out.add(PresetJson.read(in));}}}}return Collections.unmodifiableList(out);
    }
    /**
     * Writes a new archive. Every other entry's uncompressed payload is streamed unchanged.
     * ZIP compression bytes may change. Geometry, plates, thumbnails and unknown entries remain.
     * A matching existing project_settings.config is required: this does not manufacture a project.
     */
    public static void replaceProjectConfig(File source,File destination,JSONObject config) throws IOException {
        if(config==null||(config.has("type")&&!"project".equals(config.optString("type"))))throw new IOException("Expected Orca project configuration JSON");
        if(source.getCanonicalFile().equals(destination.getCanonicalFile()))throw new IOException("Choose a new 3MF output file");if(destination.exists())throw new IOException("Output file already exists");File parent=destination.getAbsoluteFile().getParentFile();if(parent==null||(!parent.isDirectory()&&!parent.mkdirs()))throw new IOException("Cannot create output directory");File staged=File.createTempFile(".project-config-",".3mf",parent);boolean committed=false;
        try{try(ZipFile zip=new ZipFile(source);ZipOutputStream out=new ZipOutputStream(new FileOutputStream(staged))){ZipEntry project=uniqueProjectEntry(zip);Set<String> names=new HashSet<>();Enumeration<? extends ZipEntry> entries=zip.entries();byte[] buffer=new byte[32768];while(entries.hasMoreElements()){ZipEntry original=entries.nextElement();if(!names.add(original.getName()))throw new IOException("Duplicate 3MF ZIP entry");boolean replace=original.getName().equals(project.getName());ZipEntry next=new ZipEntry(original.getName());if(original.getTime()>=0)next.setTime(original.getTime());if(original.getComment()!=null)next.setComment(original.getComment());if(original.getExtra()!=null)next.setExtra(withoutZip64Sizes(original.getExtra()));if(!replace&&original.getMethod()==ZipEntry.STORED){next.setMethod(ZipEntry.STORED);next.setSize(original.getSize());next.setCrc(original.getCrc());}out.putNextEntry(next);if(replace)out.write(config.toString().getBytes(StandardCharsets.UTF_8));else if(!original.isDirectory()){try(InputStream in=zip.getInputStream(original)){int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}}out.closeEntry();}if(zip.getComment()!=null)out.setComment(zip.getComment());}try{Files.move(staged.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE);}catch(java.nio.file.AtomicMoveNotSupportedException e){Files.move(staged.toPath(),destination.toPath());}committed=true;}
        finally{if(!committed)Files.deleteIfExists(staged.toPath());}
    }
    private static ZipEntry uniqueProjectEntry(ZipFile zip) throws IOException {ZipEntry found=null;Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){ZipEntry entry=entries.nextElement();if(PROJECT_CONFIG.equalsIgnoreCase(entry.getName())){if(found!=null||entry.isDirectory())throw new IOException("Duplicate or invalid project config entry");found=entry;}}if(found==null)throw new IOException("This 3MF has no Orca project_settings.config");return found;}
    // ZIP64 size fields describe the old compression stream. The writer regenerates these
    // from the streamed payload; copying them into a recompressed entry can corrupt sizes.
    private static byte[] withoutZip64Sizes(byte[] extra) throws IOException {java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();int offset=0;while(offset+4<=extra.length){int id=(extra[offset]&255)|((extra[offset+1]&255)<<8),length=(extra[offset+2]&255)|((extra[offset+3]&255)<<8);if(offset+4+length>extra.length)throw new IOException("Malformed ZIP extra field");if(id!=1)out.write(extra,offset,length+4);offset+=length+4;}if(offset!=extra.length)throw new IOException("Malformed ZIP extra field");return out.toByteArray();}
}
