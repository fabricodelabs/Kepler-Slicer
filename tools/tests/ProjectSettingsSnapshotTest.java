package com.orcamobile.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Host tests for the detached native contract; no claim of executing JNI. */
public final class ProjectSettingsSnapshotTest {
    static int checks;
    static final String HASH="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    interface Edit { void run(JSONObject value) throws Exception; }
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void rejects(Edit edit,String label)throws Exception{
        JSONObject changed=fixture();edit.run(changed);
        try{ProjectSettingsSnapshot.parse(changed);throw new AssertionError("Accepted "+label);}
        catch(IOException expected){checks++;}
    }
    static JSONObject clone(JSONObject value)throws Exception{return new JSONObject(value.toString());}
    static JSONObject config()throws Exception{return new JSONObject().put("options",new JSONObject()).put("preset_values",new JSONObject());}
    static JSONObject action(boolean allowed,String error)throws Exception{return new JSONObject().put("can_load",allowed).put("error",error);}
    static void option(JSONObject config,String key,String type,Object value,boolean nullable,Object wire)throws Exception{
        config.getJSONObject("options").put(key,new JSONObject().put("type",type).put("nullable",nullable).put("serialized","native canonical "+key).put("value",value).put("preset_value",wire));
        config.getJSONObject("preset_values").put(key,wire);
    }
    static JSONObject richConfig()throws Exception{
        JSONObject c=config();
        option(c,"layer_height","coFloat",0.234567891234567,false,"0.234567891234567");
        option(c,"wall_loops","coInt",3,false,"3");
        option(c,"machine_start_gcode","coString","G28\n; Path \\server; \"quoted\"",false,"G28\n; Path \\server; \"quoted\"");
        option(c,"enable_support","coBool",true,false,"1");
        option(c,"sparse_infill_density","coPercent",15.5,false,"15.5%");
        option(c,"line_width","coFloatOrPercent",new JSONObject().put("value",120.25).put("percent",true),false,"120.25%");
        option(c,"point","coPoint",new JSONArray("[1.5,2.5]"),false,"1.5,2.5");
        option(c,"point3","coPoint3",new JSONArray("[1,2,3]"),false,"1,2,3");
        option(c,"pattern","coEnum","gyroid",false,"gyroid");
        option(c,"filament_diameter","coFloats",new JSONArray("[1.75,1.75]"),false,new JSONArray("[\"1.75\",\"1.75\"]"));
        option(c,"temperatures","coInts",new JSONArray("[210,220]"),false,new JSONArray("[\"210\",\"220\"]"));
        option(c,"ratios","coPercents",new JSONArray("[5,10.25]"),false,new JSONArray("[\"5%\",\"10.25%\"]"));
        option(c,"scripts","coStrings",new JSONArray().put("a;b").put("new\nline"),false,new JSONArray().put("a;b").put("new\nline"));
        option(c,"flags","coBools",new JSONArray("[true,false,null]"),true,new JSONArray("[\"1\",\"0\",\"nil\"]"));
        option(c,"enums","coEnums",new JSONArray("[\"one\",\"two\"]"),false,new JSONArray("[\"one\",\"two\"]"));
        option(c,"widths","coFloatsOrPercents",new JSONArray().put(new JSONObject().put("value",0.4).put("percent",false)).put(new JSONObject().put("value",100).put("percent",true)),false,new JSONArray("[\"0.4\",\"100%\"]"));
        option(c,"area","coPoints",new JSONArray("[[0,0],[200,0],[200,200]]"),false,new JSONArray("[\"0x0\",\"200x0\",\"200x200\"]"));
        option(c,"areas","coPointsGroups",new JSONArray("[[[0,0],[2,2]],[]]"),false,new JSONArray("[\"0x0,2x2\",\"\"]"));
        option(c,"groups","coIntsGroups",new JSONArray("[[1,2],[3]]"),false,new JSONArray("[\"1,2\",\"3\"]"));
        return c;
    }
    static JSONObject fixture()throws Exception{
        JSONObject validation=new JSONObject().put("embedded_printer",action(true,"")).put("selected_printer",action(false,"Incompatible nozzle"))
                .put("can_load",true).put("error","");
        String id=HASH+":object:0";
        JSONObject object=new JSONObject().put("id",id).put("index",0).put("name","Unicode куб")
                .put("config",config()).put("volumes",new JSONArray().put(new JSONObject().put("id",id+":volume:0").put("index",0).put("config",config())))
                .put("instances",new JSONArray().put(new JSONObject().put("id",id+":instance:0").put("index",0).put("loaded_id",42)))
                .put("layer_ranges",new JSONArray().put(new JSONObject().put("z_min",0.2).put("z_max",10).put("config",config())));
        return new JSONObject().put("schema",1).put("kind","kepler.project_settings").put("archive_sha256",HASH).put("selected_plate",0)
                .put("has_settings",true).put("printer_diff",true).put("printer_differences",new JSONArray().put("nozzle_diameter"))
                .put("selected_printer_id","Selected").put("embedded_printer_id","Embedded")
                .put("source_bytes_retained",true).put("geometry_only_changes_original",false)
                .put("global",richConfig()).put("plate",new JSONObject().put("index",0).put("config",config()))
                .put("effective",richConfig()).put("effective_selected_printer",config()).put("validation",validation)
                .put("objects",new JSONArray().put(object)).put("embedded_presets",new JSONArray())
                .put("embedded_sources",new JSONArray().put(new JSONObject().put("path","Metadata/process_settings_1.config").put("source_json",new JSONObject().put("setting_id","native-id"))));
    }
    static JSONObject scalar(JSONObject d,String key)throws Exception{return d.getJSONObject("global").getJSONObject("options").getJSONObject(key);}
    public static void main(String[] args)throws Exception{
        JSONObject source=fixture();ProjectSettingsSnapshot snapshot=ProjectSettingsSnapshot.parse(source.toString().getBytes(StandardCharsets.UTF_8));
        check(snapshot.archiveSha256.equals(HASH)&&snapshot.selectedPlate==0,"archive and plate");
        check(snapshot.hasSettings&&snapshot.printerDiff,"actual project flags");
        check(snapshot.canLoad(true)&&!snapshot.canLoad(false),"distinct printer actions");
        check(snapshot.validationError(false).equals("Incompatible nozzle"),"blocked reason retained");
        check(snapshot.globalValues().length()==19,"every native type represented");
        check(snapshot.globalValues().getString("machine_start_gcode").equals("G28\n; Path \\server; \"quoted\""),"macro Unicode/newline/escape values retained");
        check(snapshot.globalValues().getJSONArray("scripts").getString(0).equals("a;b"),"string vectors never split on semicolons");
        check(snapshot.json().getJSONObject("global").getJSONObject("options").getJSONObject("layer_height").getDouble("value")==0.234567891234567,"native double precision retained separately from wire value");
        check(snapshot.globalValues().getJSONArray("flags").getString(2).equals("nil"),"nullable native vector wire nil");
        check(snapshot.json().getJSONArray("embedded_sources").getJSONObject(0).getJSONObject("source_json").getString("setting_id").equals("native-id"),"source preset identity not invented by importer");
        check(snapshot.objectId(0).equals(HASH+":object:0"),"archive-scoped object identity");
        check(snapshot.objectValues(snapshot.objectId(0)).length()==0,"object scope is separate");
        check(snapshot.plateValues().length()==0&&snapshot.effectiveValues(false).length()==0,"plate/current printer config distinct");
        source.put("archive_sha256","tampered");check(snapshot.archiveSha256.equals(HASH),"input is detached");
        JSONObject copy=snapshot.effectiveValues(true);copy.put("layer_height","999");
        check(snapshot.effectiveValues(true).getString("layer_height").equals("0.234567891234567"),"returned values are detached");
        JSONObject raw=snapshot.json();raw.put("schema",99);check(snapshot.json().getInt("schema")==1,"returned snapshot is detached");
        snapshot.requireArchive(HASH,0);checks++;
        try{snapshot.requireArchive(HASH,1);throw new AssertionError("stale plate");}catch(IOException expected){checks++;}
        try{snapshot.requireArchive("wrong",0);throw new AssertionError("stale archive");}catch(IOException expected){checks++;}
        try{snapshot.objectValues("other:object:0");throw new AssertionError("foreign object");}catch(IOException expected){checks++;}
        try{snapshot.objectId(1);throw new AssertionError("out of bounds");}catch(IOException expected){checks++;}
        try{ProjectSettingsSnapshot.parse(new byte[]{(byte)0xc3,0x28});throw new AssertionError("malformed UTF8");}catch(IOException expected){checks++;}
        try{ProjectSettingsSnapshot.parse((byte[])null);throw new AssertionError("null payload");}catch(IOException expected){checks++;}
        rejects(d->d.put("schema",2),"unsupported version");
        rejects(d->d.put("schema","1"),"string version");
        rejects(d->d.put("archive_sha256",HASH.toUpperCase()),"noncanonical fingerprint");
        rejects(d->d.put("selected_plate",1.5),"fractional plate");
        rejects(d->d.put("has_settings","true"),"string bool");
        rejects(d->d.put("source_bytes_retained",false),"discarded source bytes");
        rejects(d->d.put("geometry_only_changes_original",true),"mutating original geometry mode");
        rejects(d->d.getJSONObject("plate").put("index",1),"plate scope mismatch");
        rejects(d->d.put("printer_diff",false),"inconsistent comparison");
        rejects(d->d.getJSONArray("printer_differences").put("nozzle_diameter"),"duplicate differences");
        rejects(d->scalar(d,"layer_height").put("value","NaN"),"non-numeric float");
        rejects(d->scalar(d,"wall_loops").put("value",3.2),"fractional int");
        rejects(d->scalar(d,"wall_loops").put("value",2147483648L),"overflowing int");
        rejects(d->scalar(d,"enable_support").put("value",1),"numeric bool");
        rejects(d->scalar(d,"line_width").getJSONObject("value").put("percent","true"),"invalid percent flag");
        rejects(d->scalar(d,"point").put("value",new JSONArray("[1,2,3]")),"incorrect point dimensions");
        rejects(d->scalar(d,"filament_diameter").put("value",new JSONArray("[1.75,null]")),"unexpected vector nil");
        rejects(d->scalar(d,"flags").put("nullable",false),"nil flag disabled");
        rejects(d->scalar(d,"layer_height").put("type","coNewNativeType"),"unknown type");
        rejects(d->scalar(d,"layer_height").put("preset_value","0.9"),"wire value mismatch");
        rejects(d->d.getJSONObject("global").getJSONObject("preset_values").put("unexpected","1"),"wire key count mismatch");
        rejects(d->scalar(d,"scripts").put("preset_value",new JSONArray().put(42)),"numeric preset vector");
        rejects(d->d.getJSONArray("objects").getJSONObject(0).put("id","wrong"),"foreign object identity");
        rejects(d->d.getJSONArray("objects").getJSONObject(0).getJSONArray("volumes").getJSONObject(0).put("index",1),"volume identity mismatch");
        rejects(d->d.getJSONArray("objects").getJSONObject(0).getJSONArray("instances").getJSONObject(0).put("loaded_id",-1),"negative source identity");
        rejects(d->d.getJSONArray("objects").getJSONObject(0).getJSONArray("layer_ranges").getJSONObject(0).put("z_min",20),"reversed layer range");
        rejects(d->d.getJSONObject("validation").getJSONObject("embedded_printer").put("error","failure"),"false success action");
        rejects(d->d.getJSONObject("validation").getJSONObject("selected_printer").put("error",""),"missing blocked reason");
        rejects(d->d.getJSONObject("validation").put("can_load",false),"root validation mismatch");
        System.out.println("ProjectSettingsSnapshot host checks: "+checks);
    }
}
