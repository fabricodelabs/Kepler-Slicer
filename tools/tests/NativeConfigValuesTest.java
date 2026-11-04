package com.keplerslicer.app;

import android.content.Context;
import com.keplerslicer.engine.ExperimentalSlicer;
import com.keplerslicer.app.settings.SettingsSchema;
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
    interface Action {void run()throws Exception;}
    static void rejects(Action action,String message)throws Exception {
        try{action.run();throw new AssertionError(message);}catch(java.io.IOException expected){checks++;}
    }
    public static void main(String[] args)throws Exception {
        Context context=new Context(new File(args[1]),new File(args[0]));
        SettingsSchema schema=SettingsSchema.load(context);
        ProcessOverridesStore.Identity id=new ProcessOverridesStore.Identity();
        JSONObject typed=NativeTypedConfigSnapshotTest.fixture();
        ExperimentalSlicer.resolvedMetadata=typed.toString().getBytes(StandardCharsets.UTF_8);
        CurrentModelStore.hasModel=false;
        rejects(()->CurrentModelStore.selectionKey(context),"Model-required API should reject empty workspace in the production-like stub");
        for(String kind:new String[]{"process","filament","printer"}) {
            NativeConfigValues.Result emptyWorkspace=NativeConfigValues.loadDetailed(context,schema,kind,id);
            check(emptyWorkspace.resolvedConfigurationAvailable&&emptyWorkspace.count(NativeConfigValues.Source.RESOLVED_TYPED)>0,"Preset settings resolve without an opened model: "+kind);
        }
        ExperimentalSlicer.resolvedHook=()->CurrentModelStore.hasModel=true;
        rejects(()->NativeConfigValues.loadDetailed(context,schema,"process",id),"Empty-to-model transition must invalidate the pending settings result");
        CurrentModelStore.hasModel=false;
        ExperimentalSlicer.resolvedHook=()->{CurrentModelStore.hasModel=true;CurrentModelStore.mode=2;CurrentModelStore.key="new-project";};
        rejects(()->NativeConfigValues.loadDetailed(context,schema,"process",id),"Empty-to-project transition must invalidate the pending settings result");
        CurrentModelStore.hasModel=false;CurrentModelStore.mode=0;CurrentModelStore.key="captured-project";ExperimentalSlicer.resolvedHook=null;
        CurrentModelStore.corruptSelection=true;
        rejects(()->NativeConfigValues.loadDetailed(context,schema,"process",id),"Malformed saved metadata must not be treated as an empty workspace");
        CurrentModelStore.corruptSelection=false;CurrentModelStore.hasModel=true;
        ExperimentalSlicer.resolvedCalls=0;
        ProcessOverridesStore.overrides="{\"layer_height\":\"0.31\"}";
        NativeConfigValues.Result precise=NativeConfigValues.loadDetailed(context,schema,"process",id);
        check(precise.resolvedConfigurationAvailable&&!precise.projectConfiguration,"Resolved typed config is a selected-profile value source");
        check(precise.values.getString("layer_height").equals("0.234567891234567"),"High precision uses direct typed data instead of rounded preset JSON");
        check(precise.sources.get("layer_height")==NativeConfigValues.Source.RESOLVED_TYPED,"Typed values have a distinct exact origin");
        check(precise.sources.get("initial_layer_print_height")==NativeConfigValues.Source.DEFINITION_DEFAULT,"Absent native options retain explicit definition provenance");
        check(precise.optionalDiagnostic==null,"Successful typed config has no legacy unavailable diagnostic");
        check(ExperimentalSlicer.lastResolvedSettings.equals(ProcessOverridesStore.overrides),"Captured process overrides reach the typed native entry point");
        check(ExperimentalSlicer.iniCalls==0&&ExperimentalSlicer.metadataCalls==0,"Resolved typed config invokes neither old metadata nor INI");
        NativeConfigValues.Result preciseFilament=NativeConfigValues.loadDetailed(context,schema,"filament",id);
        NativeConfigValues.Result precisePrinter=NativeConfigValues.loadDetailed(context,schema,"printer",id);
        check(preciseFilament.values.getJSONArray("filament_diameter").getString(0).equals("1.750000000000017")&&preciseFilament.sources.get("filament_diameter")==NativeConfigValues.Source.RESOLVED_TYPED,"Filament uses precise direct vector values");
        check(precisePrinter.values.getJSONArray("nozzle_diameter").getString(0).equals("0.600000000000017")&&precisePrinter.sources.get("nozzle_diameter")==NativeConfigValues.Source.RESOLVED_TYPED,"Printer uses precise direct vector values");
        check(!precise.values.has("filament_diameter")&&!preciseFilament.values.has("layer_height")&&!precisePrinter.values.has("layer_height"),"Each editor only receives its own schema scope");
        check(ExperimentalSlicer.resolvedCalls==3&&ExperimentalSlicer.iniCalls==0,"All three kinds use the same existing typed JNI contract");
        try{precise.sources.clear();throw new AssertionError("Typed provenance map is mutable");}catch(UnsupportedOperationException expected){checks++;}

        ExperimentalSlicer.resolvedHook=()->ProcessOverridesStore.stale=true;
        rejects(()->NativeConfigValues.loadDetailed(context,schema,"process",id),"Published typed values for a changed profile selection");
        ProcessOverridesStore.stale=false;ExperimentalSlicer.resolvedHook=()->CurrentModelStore.key="different-model";
        rejects(()->NativeConfigValues.loadDetailed(context,schema,"process",id),"Published values after model scope changed");
        CurrentModelStore.key="captured-project";
        ExperimentalSlicer.resolvedHook=()->ProcessOverridesStore.overrides="{\"layer_height\":\"0.32\"}";
        rejects(()->NativeConfigValues.loadDetailed(context,schema,"process",id),"Published values after saved overrides changed");
        ProcessOverridesStore.overrides="{}";
        ExperimentalSlicer.resolvedHook=()->Thread.currentThread().interrupt();
        try{NativeConfigValues.loadDetailed(context,schema,"process",id);throw new AssertionError("Ignored typed read cancellation");}
        catch(java.io.InterruptedIOException expected){check(Thread.currentThread().isInterrupted(),"Typed native cancellation preserves the interrupt state");}
        finally{Thread.interrupted();}
        ExperimentalSlicer.resolvedHook=null;
        ExperimentalSlicer.resolvedMetadata=new byte[]{(byte)0xc3,0x28};
        rejects(()->NativeConfigValues.loadDetailed(context,schema,"process",id),"Malformed available typed data was masked by defaults");
        ExperimentalSlicer.resolvedMetadata=null;
        rejects(()->NativeConfigValues.loadDetailed(context,schema,"process",id),"Null available typed data was masked by defaults");
        ExperimentalSlicer.resolvedMetadata=typed.toString().getBytes(StandardCharsets.UTF_8);
        ExperimentalSlicer.typedFailure=new IllegalStateException("Invalid selected native config");
        rejects(()->NativeConfigValues.loadDetailed(context,schema,"process",id),"Native config rejection was masked by legacy defaults");
        ExperimentalSlicer.typedFailure=null;
        JSONObject wrongType=NativeTypedConfigSnapshotTest.fixture();ProjectSettingsSnapshotTest.option(wrongType,"layer_height","coInt",3,false,"3");
        ExperimentalSlicer.resolvedMetadata=wrongType.toString().getBytes(StandardCharsets.UTF_8);
        rejects(()->NativeConfigValues.loadDetailed(context,schema,"process",id),"Native/schema type mismatch was masked by legacy defaults");
        check(ExperimentalSlicer.iniCalls==0&&ExperimentalSlicer.metadataCalls==0,"Invalid available typed responses never invoke an older decoder");

        // Preserve and separately classify the old display path only for an absent JNI symbol.
        ExperimentalSlicer.typedUnavailable=true;
        JSONArray rows=new JSONArray();
        row(rows,"layer_height",0.3);row(rows,"sparse_infill_density",15.5);
        row(rows,"enable_support",true);row(rows,"filename_format","prefix\nname");
        row(rows,"post_process","\"a;b\";script");row(rows,"line_width","120%");
        ExperimentalSlicer.metadata=metadata(rows);
        NativeConfigValues.Result process=NativeConfigValues.loadDetailed(context,schema,"process",id);
        check(process.resolvedConfigurationAvailable,"process metadata available");
        check(process.optionalDiagnostic.contains("API is unavailable"),"Missing typed JNI is distinguished from malformed available typed data");
        check(process.values.getString("layer_height").equals("0.3"),"float is an upstream JSON string");
        check(process.values.getString("sparse_infill_density").equals("15.5%"),"percent retains %");
        check(process.values.getString("enable_support").equals("1"),"bool uses upstream 1/0");
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

        ExperimentalSlicer.ini="# upstream settings\nnozzle_diameter = 0.6\n";
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

        ExperimentalSlicer.iniFails=false;ExperimentalSlicer.ini="# upstream settings\nfilament_diameter = 1.75\n";
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
        int previousIniCalls=ExperimentalSlicer.iniCalls,previousMetadataCalls=ExperimentalSlicer.metadataCalls,previousResolvedCalls=ExperimentalSlicer.resolvedCalls;
        NativeConfigValues.Result project=NativeConfigValues.loadDetailed(context,schema,"process",id);
        check(project.projectConfiguration&&project.resolvedConfigurationAvailable,"project effective config distinguished from preset fallback");
        check(project.values.getString("layer_height").equals("0.234567891234567"),"actual project wire precision retained");
        check(project.sources.get("layer_height")==NativeConfigValues.Source.PROJECT_CONFIGURATION,"project origin");
        check(!project.values.has("point")&&!project.values.has("filament_diameter"),"only selected schema scope exposed");
        check(ExperimentalSlicer.iniCalls==previousIniCalls&&ExperimentalSlicer.metadataCalls==previousMetadataCalls,"project never falls through to INI or selected process metadata");
        check(ExperimentalSlicer.resolvedCalls==previousResolvedCalls,"Project mode never substitutes a selected-preset typed snapshot");
        check(!CurrentModelStore.lastCopy.exists()&&!CurrentModelStore.lastCopy.getParentFile().exists(),"immutable project copy removed after success");
        ExperimentalSlicer.projectHook=()->ProcessOverridesStore.stale=true;
        rejects(()->NativeConfigValues.loadDetailed(context,schema,"process",id),"Project values published after captured profile changed");
        ProcessOverridesStore.stale=false;ExperimentalSlicer.projectHook=()->ProcessOverridesStore.overrides="{\"layer_height\":\"0.42\"}";
        rejects(()->NativeConfigValues.loadDetailed(context,schema,"process",id),"Project values published after overrides changed");
        ProcessOverridesStore.overrides="{}";ExperimentalSlicer.projectHook=null;
        check(!CurrentModelStore.lastCopy.exists()&&!CurrentModelStore.lastCopy.getParentFile().exists(),"immutable project copy removed after profile or override rejection");
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
