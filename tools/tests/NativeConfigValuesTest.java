package com.orcamobile.app;

import android.content.Context;
import com.keplerslicer.engine.ExperimentalSlicer;
import com.orcamobile.app.settings.SettingsSchema;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;

/** Host contract tests: native bridge and Android Context are explicit fakes, not JNI tests. */
public final class NativeConfigValuesTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void row(JSONArray rows,String key,Object value)throws Exception{rows.put(new JSONObject().put("key",key).put("value",value));}
    static byte[] metadata(JSONArray rows)throws Exception{
        return new JSONObject().put("version",2).put("scope","process_options").put("options",rows)
                .toString().getBytes(StandardCharsets.UTF_8);
    }
    static void noJob() {check(ExperimentalSlicer.lastJob!=null&&!ExperimentalSlicer.lastJob.exists(),"private job was removed");}
    public static void main(String[] args)throws Exception {
        Context context=new Context(new File(args[1]),new File(args[0]));
        SettingsSchema schema=SettingsSchema.load(context);
        ProcessOverridesStore.Identity id=new ProcessOverridesStore.Identity();
        JSONArray rows=new JSONArray();
        row(rows,"layer_height",0.3);row(rows,"sparse_infill_density",15.5);
        row(rows,"enable_support",true);row(rows,"filename_format","prefix\nname");
        row(rows,"post_process","\"a;b\";script");row(rows,"line_width","120%");
        ExperimentalSlicer.metadata=metadata(rows);
        NativeConfigValues.Result process=NativeConfigValues.loadDetailed(context,schema,"process",id);
        check(process.resolvedConfigurationAvailable,"process metadata available");
        check(process.values.getString("layer_height").equals("0.3"),"float is an Orca JSON string");
        check(process.values.getString("sparse_infill_density").equals("15.5%"),"percent retains %");
        check(process.values.getString("enable_support").equals("1"),"bool uses Orca 1/0");
        check(process.values.getString("filename_format").equals("prefix\nname"),"already decoded macro preserved");
        check(process.values.getJSONArray("post_process").toString().equals("[\"a;b\",\"script\"]"),"string-vector serialization");
        check(process.sources.get("layer_height")==NativeConfigValues.Source.PROCESS_METADATA,"direct origin");
        check(process.sources.get("post_process")==NativeConfigValues.Source.PROCESS_METADATA_SERIALIZED,"serialized vector origin");
        check(process.sources.get("line_width")==NativeConfigValues.Source.PROCESS_METADATA_SERIALIZED,"serialized percent origin");
        check(process.sources.get("initial_layer_print_height")==NativeConfigValues.Source.DEFINITION_DEFAULT,"definition origin");
        check(process.count(NativeConfigValues.Source.PROCESS_METADATA)>0,"source counts");
        check(ExperimentalSlicer.iniCalls==0,"process metadata does not use INI");
        try {process.sources.clear();throw new AssertionError("mutable source map");}catch(UnsupportedOperationException expected){}

        ExperimentalSlicer.metadataFails=true;
        NativeConfigValues.Result processFallback=NativeConfigValues.loadDetailed(context,schema,"process",id);
        check(!processFallback.resolvedConfigurationAvailable,"failed metadata is not effective configuration");
        check(processFallback.optionalDiagnostic.contains("metadata unavailable"),"optional diagnostic retained");
        check(processFallback.sources.get("initial_layer_print_height")==NativeConfigValues.Source.DEFINITION_DEFAULT,"fallback source");
        check(!processFallback.values.has("layer_height"),"unresolved definition default stays absent");
        ExperimentalSlicer.metadataFails=false;

        ExperimentalSlicer.ini="# Orca settings\nnozzle_diameter = 0.6\n";
        NativeConfigValues.Result printer=NativeConfigValues.loadDetailed(context,schema,"printer",id);
        check(printer.resolvedConfigurationAvailable,"validated INI reported");
        check(printer.values.getJSONArray("nozzle_diameter").getString(0).equals("0.6"),"numeric vector in INI");
        check(printer.sources.get("nozzle_diameter")==NativeConfigValues.Source.VALIDATED_INI,"INI origin");
        noJob();

        ExperimentalSlicer.iniFails=true;
        NativeConfigValues.Result failed=NativeConfigValues.loadDetailed(context,schema,"printer",id);
        check(!failed.resolvedConfigurationAvailable,"failed round-trip is not an effective config");
        check(failed.optionalDiagnostic.contains("round-trip exactly"),"INI failure retained only as diagnostic");
        check(failed.sources.get("machine_max_speed_x")==NativeConfigValues.Source.DEFINITION_DEFAULT,"axis definition fallback source");
        check(failed.values.getJSONArray("machine_max_speed_x").toString().equals("[\"500\",\"200\"]"),"axis default uses string array");
        check(failed.count(NativeConfigValues.Source.VALIDATED_INI)==0,"no values claim an INI origin on failure");
        noJob();

        ExperimentalSlicer.iniFails=false;ExperimentalSlicer.ini="# Orca settings\nfilament_diameter = 1.75\n";
        NativeConfigValues.Result filament=NativeConfigValues.loadDetailed(context,schema,"filament",id);
        check(filament.values.getJSONArray("filament_diameter").getString(0).equals("1.75"),"filament value");
        check(filament.sources.get("filament_diameter")==NativeConfigValues.Source.VALIDATED_INI,"filament origin");
        noJob();

        JSONArray bad=new JSONArray();row(bad,"enable_support","maybe");
        check(!NativeConfigValues.readProcessMetadata(metadata(bad),schema.options("process")).has("enable_support"),"malformed bool omitted");
        JSONArray duplicates=new JSONArray();row(duplicates,"layer_height",1);row(duplicates,"layer_height",2);
        try { NativeConfigValues.readProcessMetadata(metadata(duplicates),schema.options("process"));throw new AssertionError("duplicate accepted"); }
        catch(java.io.IOException expected){checks++;}
        ExperimentalSlicer.metadata=new byte[]{(byte)0xc3,0x28};
        NativeConfigValues.Result malformed=NativeConfigValues.loadDetailed(context,schema,"process",id);
        check(!malformed.resolvedConfigurationAvailable && malformed.optionalDiagnostic!=null,"bad UTF-8 safely falls back");
        check(!malformed.projectConfiguration,"legacy values are not project values");

        // The bridge response is an explicit fake. Exercise the real consumer,
        // immutable model-copy cleanup, scope filtering and fail-closed behavior.
        CurrentModelStore.source=new File(context.getCacheDir(),"native-config-source.3mf");
        java.nio.file.Files.write(CurrentModelStore.source.toPath(),new byte[]{1,2,3});
        CurrentModelStore.mode=2;
        ExperimentalSlicer.projectMetadata=ProjectSettingsSnapshotTest.fixture().toString().getBytes(StandardCharsets.UTF_8);
        int previousIniCalls=ExperimentalSlicer.iniCalls,previousMetadataCalls=ExperimentalSlicer.metadataCalls;
        NativeConfigValues.Result project=NativeConfigValues.loadDetailed(context,schema,"process",id);
        check(project.projectConfiguration&&project.resolvedConfigurationAvailable,"project effective config distinguished from preset fallback");
        check(project.values.getString("layer_height").equals("0.234567891234567"),"actual project wire precision retained");
        check(project.sources.get("layer_height")==NativeConfigValues.Source.PROJECT_CONFIGURATION,"project origin");
        check(!project.values.has("point")&&!project.values.has("filament_diameter"),"only selected schema scope exposed");
        check(ExperimentalSlicer.iniCalls==previousIniCalls&&ExperimentalSlicer.metadataCalls==previousMetadataCalls,"project never falls through to INI or selected process metadata");
        check(!CurrentModelStore.lastCopy.exists()&&!CurrentModelStore.lastCopy.getParentFile().exists(),"immutable project copy removed after success");
        CurrentModelStore.mode=1;
        try{NativeConfigValues.loadDetailed(context,schema,"process",id);throw new AssertionError("incompatible current-printer config accepted");}
        catch(java.io.IOException expected){check(expected.getMessage().contains("Incompatible nozzle"),"native project rejection not masked as defaults");}
        check(!CurrentModelStore.lastCopy.exists()&&!CurrentModelStore.lastCopy.getParentFile().exists(),"immutable project copy removed after native rejection");
        CurrentModelStore.mode=2;
        ExperimentalSlicer.projectHook=()->CurrentModelStore.key="changed-project";
        try{NativeConfigValues.loadDetailed(context,schema,"process",id);throw new AssertionError("stale project display accepted");}
        catch(java.io.IOException expected){checks++;}
        ExperimentalSlicer.projectHook=null;CurrentModelStore.key="captured-project";
        ExperimentalSlicer.projectMetadata=new byte[]{(byte)0xc3,0x28};
        try{NativeConfigValues.loadDetailed(context,schema,"process",id);throw new AssertionError("bad project UTF-8 accepted");}
        catch(java.io.IOException expected){checks++;}
        check(ExperimentalSlicer.iniCalls==previousIniCalls&&ExperimentalSlicer.metadataCalls==previousMetadataCalls,"project errors never claim saved profile defaults");
        check(!CurrentModelStore.lastCopy.exists()&&!CurrentModelStore.lastCopy.getParentFile().exists(),"immutable project copy removed after malformed response");
        CurrentModelStore.mode=0;CurrentModelStore.source.delete();
        System.out.println("Native config values host tests passed: "+checks+" checks (JNI not executed)");
    }
}
