package com.orcamobile.app.settings;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Standalone JVM tests use the real org.json implementation, not Android's throwing SDK stub. */
public final class PresetRepositoryTest {
    private static int assertions;
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    private interface Action{void run() throws Exception;}
    private static void rejects(Action action,String message) throws Exception {try{action.run();throw new AssertionError(message);}catch(IOException expected){assertions++;}}
    private static final class Fixtures implements PresetRepository.SystemSource {
        final Map<String,JSONObject> json=new LinkedHashMap<>();final List<PresetRepository.SystemRecord> rows=new ArrayList<>();
        Fixtures() throws Exception {add("base","",new JSONObject("{\"type\":\"process\",\"name\":\"base\",\"layer_height\":\"0.2\",\"unknown\":{\"a\":[1,true,null]}}"),false);add("fine","base",new JSONObject("{\"type\":\"process\",\"name\":\"fine\",\"inherits\":\"base\",\"layer_height\":\"0.12\",\"custom\":[\"a\",\"b\"]}"),true);}
        void add(String name,String parent,JSONObject document,boolean instantiated){String path="Vendor/process/"+name+".json";rows.add(new PresetRepository.SystemRecord("process","Vendor",name,path,parent,instantiated));json.put(path,document);}
        public List<PresetRepository.SystemRecord> entries(){return rows;}
        public JSONObject read(String path) throws IOException {return PresetJson.copy(json.get(path));}
        public void close(){}
    }
    public static void main(String[] args) throws Exception {
        File root=new File(args[0]);Files.createDirectories(root.toPath());File assets=new File(args[1]);
        try(PresetRepository repo=new PresetRepository(new File(root,"repo"),new Fixtures())){
            String base=PresetRepository.systemId("process","Vendor","fine");
            check(repo.list("process").size()==1,"Hidden inheritance base should not clutter picker");
            check(repo.list("process").get(0).source==null,"List must remain a lightweight descriptor");
            JSONObject resolved=repo.resolve(base);check(resolved.getString("layer_height").equals("0.12"),"Child option overrides parent");check(resolved.getJSONObject("unknown").getJSONArray("a").isNull(2),"Unknown nested null preserved");
            PresetRepository.Draft draft=repo.beginDraft(base);check(!draft.dirty(),"New draft starts clean");draft.set("layer_height","0.16");check(draft.dirty(),"Edit marks dirty");check(repo.resolve(base).getString("layer_height").equals("0.12"),"Draft cannot mutate a system preset");rejects(()->repo.save(draft),"System preset write must be rejected");
            PresetRepository.Preset user=repo.saveAs(draft,"Custom Fine");check(user.source.getString("inherits").equals("fine"),"Save as inherits selected original");check(user.effective.getString("layer_height").equals("0.16"),"User change applies in inheritance");check(repo.systemAncestor(user.id).path.equals("Vendor/process/fine.json"),"Closest system ancestor is stable");
            PresetRepository.Draft edit=repo.beginDraft(user.id);edit.set("unknown_two",new JSONArray("[1,{\"q\":false}]"));repo.save(edit);check(!edit.dirty(),"Successful Save updates baseline");JSONObject expected=repo.exportPreset(user.id);check(expected.getString("version").equals(PresetRepository.UPSTREAM_VERSION),"Desktop-compatible semantic version added");check(expected.getString("print_settings_id").equals("Custom Fine"),"Desktop process identity supplied");
            PresetRepository.Draft stale=repo.beginDraft(user.id),other=repo.beginDraft(user.id);other.set("layer_height","0.17");repo.save(other);stale.set("layer_height","0.18");rejects(()->repo.save(stale),"Concurrent draft must not overwrite a newer save");repo.reset(stale);check(!stale.dirty(),"Reset discards unsaved state");
            PresetRepository.Draft childDraft=repo.beginDraft(user.id);childDraft.set("top_shell_layers","6");PresetRepository.Preset child=repo.saveAs(childDraft,"Child Fine");rejects(()->repo.deleteUser(user.id),"Inherited user parent cannot be deleted");
            JSONObject bundle=repo.exportBundle(Collections.singletonList(child.id));check(bundle.getJSONArray("presets").length()==2,"Export includes user ancestors");
            try(PresetRepository imported=new PresetRepository(new File(root,"import"),new Fixtures())){List<PresetRepository.Preset> got=imported.importBundle(bundle);check(got.size()==2,"Import is one transaction");PresetRepository.Preset importedChild=got.get(1);check(importedChild.effective.getString("top_shell_layers").equals("6"),"Child option survives exchange");check(PresetJson.equal(importedChild.effective.get("unknown_two"),new JSONArray("[1,{\"q\":false}]")),"Unknown arrays/objects survive exchange");}
            ByteArrayOutputStream zip=new ByteArrayOutputStream();repo.exportOrcaZip(Collections.singletonList(child.id),zip);File archive=new File(root,"presets.zip");Files.write(archive.toPath(),zip.toByteArray());try(PresetRepository imported=new PresetRepository(new File(root,"zipimport"),new Fixtures())){check(imported.importOrcaZip(archive).size()==2,"Orca plain ZIP preset exchange");}
            int before=repo.list("process").size();JSONObject invalid=new JSONObject("{\"format\":\"kepler-presets-v1\",\"presets\":[{\"kind\":\"process\",\"vendor\":\"Vendor\",\"source\":{\"name\":\"cycle-a\",\"type\":\"process\",\"inherits\":\"cycle-b\"}},{\"kind\":\"process\",\"vendor\":\"Vendor\",\"source\":{\"name\":\"cycle-b\",\"type\":\"process\",\"inherits\":\"cycle-a\"}}]}");rejects(()->repo.importBundle(invalid),"Cycle import must fail");check(repo.list("process").size()==before,"Failed cycle import must not write any profile");
            rejects(()->repo.saveUser("process","Vendor","Orphan",null,new JSONObject("{\"inherits\":\"missing\"}")),"Missing parent must fail");rejects(()->repo.deleteUser(base),"System delete must fail");
            repo.deleteUser(child.id);repo.deleteUser(user.id);check(repo.list("process").size()==1,"User deletion leaves system archive untouched");
        }
        testPartialCommitBaseline(root);
        testSaveAsIdentity(root);
        testProject(root);
        File official=new File(args.length>2?args[2]:"");
        if(official.isFile())testOfficialProject(root,official);
        try(PresetRepository real=new PresetRepository(new Context(new File(root,"real"),assets))){
            check(real.list("process").size()>3000,"Original process catalog available");String id=PresetRepository.systemId("printer","Prusa","Prusa CORE One 0.6 nozzle");JSONObject config=real.resolve(id);check(config.getJSONArray("nozzle_diameter").getString(0).equals("0.6"),"Real upstream machine inheritance resolves");check(real.load(id).path.equals("Prusa/machine/Prusa CORE One 0.6 nozzle.json"),"Original system path retained");
        }
        System.out.println("Preset repository: "+assertions+" assertions passed");
    }
    private static void testPartialCommitBaseline(File root) throws Exception {
        try(PresetRepository repo=new PresetRepository(new File(root,"partial-commit"),new Fixtures())){
            String system=PresetRepository.systemId("process","Vendor","fine");
            PresetRepository.Draft original=repo.beginDraft(system);original.set("layer_height","0.24");
            PresetRepository.Preset saved=repo.saveAs(original,"Partial Save As");
            // Simulate a later object-store failure: the new global preset already exists.
            PresetRepository.Draft rebased=repo.beginDraft(saved.id);
            check(rebased.baseId.equals(saved.id)&&!rebased.dirty(),"Partial Save As reloads the committed identity and baseline");
            rebased.set("top_shell_layers","5");repo.save(rebased);
            check(repo.load(saved.id).effective.getString("top_shell_layers").equals("5"),"Retry can save against the reloaded baseline");
            PresetRepository.Draft normal=repo.beginDraft(saved.id);normal.set("top_shell_layers","7");repo.save(normal);
            check(!normal.dirty(),"Ordinary committed save advances its draft baseline before later failure");
            PresetRepository.Draft retried=repo.beginDraft(saved.id);retried.set("top_shell_layers","8");repo.save(retried);
            check(repo.load(saved.id).effective.getString("top_shell_layers").equals("8"),"A second retry uses the new committed baseline");
        }
    }
    private static void testSaveAsIdentity(File root) throws Exception {
        Fixtures source=new Fixtures();JSONObject system=source.json.get("Vendor/process/fine.json");
        system.put("setting_id","system-fine-id");String original=PresetJson.fingerprint(system);
        try(PresetRepository repo=new PresetRepository(new File(root,"save-as-identity"),source)){
            String systemId=PresetRepository.systemId("process","Vendor","fine");PresetRepository.Draft draft=repo.beginDraft(systemId);
            PresetRepository.Preset first=repo.saveAs(draft,"New identity");
            check(!first.source.has("setting_id"),"System Save As must not copy its parent's setting_id into the new user source");
            check(!repo.exportPreset(first.id).has("setting_id"),"Export of a newly saved user must not publish its parent's identity");
            check(first.parentId.equals(systemId)&&first.source.getString("inherits").equals("fine"),"Clearing new identity keeps the exact system inheritance link");
            check(PresetJson.fingerprint(repo.load(systemId).source).equals(original),"Save As must leave original system JSON unchanged");
            check(draft.source().getString("setting_id").equals("system-fine-id"),"Save As must not mutate its source draft identity");
            PresetRepository.Draft firstEdit=repo.beginDraft(first.id);firstEdit.set("layer_height","0.19");repo.save(firstEdit);
            check(!repo.exportPreset(first.id).has("setting_id"),"Saving a new identity-free user does not restore its parent's ID");

            JSONObject opaque=new JSONObject("{\"nested\":[null,true,{\"text\":\"retained\"}]}");
            JSONObject incoming=new JSONObject().put("type","process").put("name","Existing identity").put("inherits","fine").put("setting_id","existing-user-id").put("SETTING_ID","upper-user-id").put("SeTtInG_Id","mixed-user-id").put("sett\u0131ng_id","opaque-unicode-key").put("base_id","imported-base-metadata").put("unknown_payload",opaque).put("layer_height","0.24");
            PresetRepository.Preset existing=repo.importBundle(incoming).get(0);
            check(existing.source.getString("setting_id").equals("existing-user-id"),"Import preserves the original explicit setting_id");
            check(existing.source.getString("SETTING_ID").equals("upper-user-id")&&existing.source.getString("SeTtInG_Id").equals("mixed-user-id"),"Import keeps original case variants of metadata unchanged");
            check(PresetJson.equal(existing.source.get("unknown_payload"),opaque),"Import retains unknown JSON beside identity metadata");
            PresetRepository.Draft existingEdit=repo.beginDraft(existing.id);existingEdit.set("layer_height","0.25");PresetRepository.Preset saved=repo.save(existingEdit);
            check(saved.id.equals(existing.id)&&saved.source.getString("setting_id").equals("existing-user-id"),"Save of an existing user retains its UUID and explicit identity");
            check(saved.source.getString("SETTING_ID").equals("upper-user-id")&&saved.source.getString("SeTtInG_Id").equals("mixed-user-id"),"Existing Save preserves the original identity key variants");
            check(repo.exportPreset(existing.id).getString("setting_id").equals("existing-user-id"),"Export does not silently rewrite existing saved identity");
            String savedSource=PresetJson.fingerprint(saved.source);PresetRepository.Draft copyDraft=repo.beginDraft(existing.id);
            PresetRepository.Preset copy=repo.saveAs(copyDraft,"Different identity");
            check(!copy.id.equals(existing.id)&&!copy.id.equals(first.id),"Save As assigns a distinct local user UUID");
            check(!copy.source.has("setting_id")&&!repo.exportPreset(copy.id).has("setting_id"),"User-to-new-name Save As clears copied setting_id in source and export");
            check(!copy.source.has("SETTING_ID")&&!copy.source.has("SeTtInG_Id")&&!repo.exportPreset(copy.id).has("SETTING_ID"),"Save As clears every ASCII-case setting_id alias recognized by Orca");
            check(copy.parentId.equals(existing.id)&&copy.source.getString("inherits").equals("Existing identity"),"User Save As keeps the selected parent link");
            check(PresetJson.equal(copy.source.get("unknown_payload"),opaque)&&copy.source.getString("base_id").equals("imported-base-metadata"),"Save As retains unknown/imported metadata except the deliberately cleared own setting_id");
            check(copy.source.getString("sett\u0131ng_id").equals("opaque-unicode-key"),"ASCII metadata clearing preserves an unknown Unicode lookalike key");
            check(PresetJson.fingerprint(repo.load(existing.id).source).equals(savedSource),"Creating a new name does not rewrite the existing user profile");
            check(copyDraft.source().getString("setting_id").equals("existing-user-id"),"Creating a new name does not rewrite the existing user draft");
            rejects(()->repo.saveAs(copyDraft,"Existing identity"),"Duplicate Save As still rejects the conflicting name");
            check(copyDraft.source().getString("setting_id").equals("existing-user-id"),"Failed Save As keeps its source draft identity intact");
        }
    }
    private static void testProject(File root) throws Exception {
        File original=new File(root,"original.3mf"),modified=new File(root,"modified.3mf");byte[] geometry=new byte[1_048_577];for(int i=0;i<geometry.length;i++)geometry[i]=(byte)(i*31);byte[] unknown="unknown entry\u0000payload".getBytes(StandardCharsets.UTF_8);
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(original.toPath()))){entry(zip,"3D/3dmodel.model",geometry);entry(zip,"unknown/opaque.bin",unknown);entry(zip,ProjectConfigArchive.PROJECT_CONFIG,"{\"type\":\"project\",\"name\":\"project_settings\",\"layer_height\":\"0.2\",\"custom\":{\"a\":[null,true]}}".getBytes(StandardCharsets.UTF_8));entry(zip,"Metadata/process_settings_1.config","{\"type\":\"process\",\"name\":\"embedded\"}".getBytes(StandardCharsets.UTF_8));}
        JSONObject config=ProjectConfigArchive.readProjectConfig(original);config.put("layer_height","0.16");ProjectConfigArchive.replaceProjectConfig(original,modified,config);check(ProjectConfigArchive.readProjectConfig(modified).getString("layer_height").equals("0.16"),"3MF project JSON replaced");check(PresetJson.equal(config,ProjectConfigArchive.readProjectConfig(modified)),"Unknown 3MF configuration keys preserved");check(ProjectConfigArchive.readEmbeddedPresets(modified).size()==1,"Embedded preset JSON retained");try(ZipFile zip=new ZipFile(modified)){check(Arrays.equals(zip.getInputStream(zip.getEntry("3D/3dmodel.model")).readAllBytes(),geometry),"3MF geometry payload byte-identical");check(Arrays.equals(zip.getInputStream(zip.getEntry("unknown/opaque.bin")).readAllBytes(),unknown),"Unknown ZIP payload byte-identical");}rejects(()->ProjectConfigArchive.replaceProjectConfig(original,original,config),"In-place mutation must be rejected");rejects(()->ProjectConfigArchive.replaceProjectConfig(original,modified,config),"Existing output must not be overwritten");
        testProjectIntegrity(root, original, config);
    }
    private static void testProjectIntegrity(File root, File original, JSONObject config) throws Exception {
        File invalid = new File(root, "invalid.3mf");
        File output = new File(root, "verified-output.3mf");
        byte[] originalBytes = Files.readAllBytes(original.toPath());

        // Change only central-directory fields. The DEFLATED payload still decompresses,
        // so an unchecked ZIP copy would quietly give it a new, valid checksum.
        Files.write(invalid.toPath(), corruptZipField(originalBytes, "3D/3dmodel.model", 16));
        rejects(() -> ProjectConfigArchive.replaceProjectConfig(invalid, output, config), "Corrupt geometry CRC must prevent export");
        check(!output.exists(), "A checksum failure must leave no output");
        Files.write(invalid.toPath(), corruptZipField(originalBytes, "3D/3dmodel.model", 24));
        rejects(() -> ProjectConfigArchive.replaceProjectConfig(invalid, output, config), "Incorrect geometry length must prevent export");
        Files.write(invalid.toPath(), corruptZipField(originalBytes, ProjectConfigArchive.PROJECT_CONFIG, 16));
        rejects(() -> ProjectConfigArchive.readProjectConfig(invalid), "Corrupt project settings CRC must prevent import");
        rejects(() -> ProjectConfigArchive.replaceProjectConfig(invalid, output, config), "Replacing project settings must still verify original entry");
        Files.write(invalid.toPath(), corruptZipField(originalBytes, "Metadata/process_settings_1.config", 16));
        rejects(() -> ProjectConfigArchive.readEmbeddedPresets(invalid), "Corrupt embedded preset CRC must prevent import");

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(invalid.toPath()))) {
            entry(zip, ProjectConfigArchive.PROJECT_CONFIG, "{}".getBytes(StandardCharsets.UTF_8));
            entry(zip, "3D/model.model", new byte[]{1});
            entry(zip, "3d/MODEL.model", new byte[]{2});
        }
        rejects(() -> ProjectConfigArchive.readProjectConfig(invalid), "Archive with case-alias paths must not be read");
        rejects(() -> ProjectConfigArchive.readEmbeddedPresets(invalid), "Case-alias paths must not be accepted by the preset reader");
        rejects(() -> ProjectConfigArchive.replaceProjectConfig(invalid, output, config), "Case-alias paths must not be exported");

        Thread.currentThread().interrupt();
        try {
            rejects(() -> ProjectConfigArchive.replaceProjectConfig(original, output, config), "Canceled export must fail");
        } finally {
            Thread.interrupted();
        }
        check(!output.exists(), "Rejected and canceled exports must leave no output");

        byte[] competingBytes = {42, 1, 3};
        JSONObject competingWriter = new JSONObject(config.toString()) {
            @Override public String toString() {
                try {
                    Files.write(output.toPath(), competingBytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return super.toString();
            }
        };
        rejects(() -> ProjectConfigArchive.replaceProjectConfig(original, output, competingWriter), "A destination claimed during export must be kept");
        check(Arrays.equals(Files.readAllBytes(output.toPath()), competingBytes), "A racing writer's output must remain byte-identical");
        check(Arrays.equals(Files.readAllBytes(original.toPath()), originalBytes), "Rejected exports must preserve the source");
        try (java.util.stream.Stream<java.nio.file.Path> entries = Files.list(root.toPath())) {
            check(entries.noneMatch(path -> path.getFileName().toString().startsWith(".project-config-")), "Project staging files must be removed");
        }
    }
    private static byte[] corruptZipField(byte[] original, String target, int fieldOffset) {
        byte[] copy = original.clone();
        ByteBuffer bytes = ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN);
        int end = copy.length - 22; // This small test fixture has no ZIP comment.
        if (bytes.getInt(end) != 0x06054b50) throw new AssertionError("Invalid test ZIP ending");
        int position = bytes.getInt(end + 16);
        while (position < end && bytes.getInt(position) == 0x02014b50) {
            int nameLength = Short.toUnsignedInt(bytes.getShort(position + 28));
            String name = new String(copy, position + 46, nameLength, StandardCharsets.UTF_8);
            if (name.equals(target)) {
                copy[position + fieldOffset] ^= 1;
                return copy;
            }
            position += 46 + nameLength + Short.toUnsignedInt(bytes.getShort(position + 30))
                    + Short.toUnsignedInt(bytes.getShort(position + 32));
        }
        throw new AssertionError("Missing test ZIP entry " + target);
    }
    private static void entry(ZipOutputStream zip,String name,byte[] bytes)throws Exception{zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();}
    private static void testOfficialProject(File root,File original) throws Exception {
        JSONObject config=ProjectConfigArchive.readProjectConfig(original);config.put("layer_height","0.16");File result=new File(root,"official-config-roundtrip.3mf");ProjectConfigArchive.replaceProjectConfig(original,result,config);check(ProjectConfigArchive.readProjectConfig(result).getString("layer_height").equals("0.16"),"Official Orca project config roundtrip");try(ZipFile in=new ZipFile(original);ZipFile out=new ZipFile(result)){java.util.Enumeration<? extends ZipEntry> entries=in.entries();int count=0;while(entries.hasMoreElements()){ZipEntry entry=entries.nextElement();if(entry.isDirectory()||entry.getName().equalsIgnoreCase(ProjectConfigArchive.PROJECT_CONFIG))continue;ZipEntry other=out.getEntry(entry.getName());check(other!=null,"Original entry retained: "+entry.getName());try(java.io.InputStream a=in.getInputStream(entry);java.io.InputStream b=out.getInputStream(other)){check(Arrays.equals(a.readAllBytes(),b.readAllBytes()),"Official project payload retained: "+entry.getName());}count++;}check(count>0,"Official fixture contains real payloads");}
    }
}
