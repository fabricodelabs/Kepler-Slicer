package com.orcamobile.app;

import android.content.Context;
import com.orcamobile.app.settings.SettingsSchema;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Real Java decoder/converter tests; no JNI or Android runtime is executed. */
public final class NativeTypedConfigSnapshotTest {
    static int checks;
    interface Edit { void change(JSONObject document)throws Exception; }
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static byte[] bytes(JSONObject document){return document.toString().getBytes(StandardCharsets.UTF_8);}
    static JSONObject fixture()throws Exception {
        JSONObject document=ProjectSettingsSnapshotTest.richConfig();
        ProjectSettingsSnapshotTest.option(document,"layer_height","coFloat",0.234567891234567,false,"0.234568");
        ProjectSettingsSnapshotTest.option(document,"filament_diameter","coFloats",new JSONArray("[1.750000000000017,1.234567891234567]"),false,new JSONArray("[\"1.75\",\"1.23457\"]"));
        ProjectSettingsSnapshotTest.option(document,"sparse_infill_density","coPercent",15.12345678912345,false,"15.1235%");
        ProjectSettingsSnapshotTest.option(document,"line_width","coFloatOrPercent",new JSONObject().put("value",120.23456789123456).put("percent",true),false,"120.235%");
        ProjectSettingsSnapshotTest.option(document,"nozzle_diameter","coFloats",new JSONArray("[0.600000000000017]"),false,new JSONArray("[\"0.6\"]"));
        ProjectSettingsSnapshotTest.option(document,"nullable_scalar","coFloat",JSONObject.NULL,true,"nil");
        ProjectSettingsSnapshotTest.option(document,"nullable_floats","coFloats",new JSONArray("[null,0.1234567891234567]"),true,new JSONArray("[\"nil\",\"0.123457\"]"));
        ProjectSettingsSnapshotTest.option(document,"unicode","coString","Кеплер 🚀\n\t\\path; \"quoted\"",false,"Кеплер 🚀\n\t\\path; \"quoted\"");
        ProjectSettingsSnapshotTest.option(document,"nullable_ints","coInts",new JSONArray("[null,-2147483648,2147483647]"),true,new JSONArray("[\"nil\",\"-2147483648\",\"2147483647\"]"));
        ProjectSettingsSnapshotTest.option(document,"nullable_percents","coPercents",new JSONArray("[null,12.34567891234567]"),true,new JSONArray("[\"nil\",\"12.3457%\"]"));
        ProjectSettingsSnapshotTest.option(document,"nullable_widths","coFloatsOrPercents",new JSONArray().put(JSONObject.NULL).put(new JSONObject().put("value",0.1234567891234567).put("percent",false)),true,new JSONArray("[\"nil\",\"0.123457\"]"));
        ProjectSettingsSnapshotTest.option(document,"nullable_enums","coEnums",new JSONArray().put(JSONObject.NULL).put("second"),true,new JSONArray("[\"nil\",\"second\"]"));
        ProjectSettingsSnapshotTest.option(document,"precise_point","coPoint",new JSONArray("[1.234567891234567,2.345678912345678]"),false,"1.23457,2.34568");
        ProjectSettingsSnapshotTest.option(document,"precise_groups","coPointsGroups",new JSONArray("[[[1.234567891234567,2.345678912345678]]]"),false,new JSONArray("[\"1.23457x2.34568\"]"));
        return document;
    }
    static JSONObject row(JSONObject document,String key)throws Exception{return document.getJSONObject("options").getJSONObject(key);}
    static void reject(Edit edit,String description)throws Exception {
        JSONObject document=fixture();edit.change(document);
        try{NativeTypedConfigSnapshot.readAll(bytes(document));throw new AssertionError("Accepted " + description);}
        catch(IOException expected){checks++;}
    }
    static void rejectText(String text,String description)throws Exception {
        try{NativeTypedConfigSnapshot.readAll(text.getBytes(StandardCharsets.UTF_8));throw new AssertionError("Accepted " + description);}
        catch(IOException expected){checks++;}
    }
    public static void main(String[] args)throws Exception {
        JSONObject document=fixture();String original=document.toString();
        JSONObject values=NativeTypedConfigSnapshot.readAll(bytes(document));
        check(values.length()==document.getJSONObject("options").length(),"Every supported native option converts to editor values");
        check(values.getString("layer_height").equals("0.234567891234567"),"Direct float precision is retained despite rounded preset wire text");
        check(values.getJSONArray("filament_diameter").toString().equals("[\"1.750000000000017\",\"1.234567891234567\"]"),"Direct vector precision is retained");
        check(values.getString("sparse_infill_density").equals("15.12345678912345%"),"Precise percent retains its percent marker");
        check(values.getString("line_width").equals("120.23456789123456%"),"Float-or-percent object becomes precise editor text");
        check(values.getString("wall_loops").equals("3")&&values.getString("enable_support").equals("1"),"Integer and bool match existing Orca string representation");
        check(values.getString("point").equals("1.5,2.5"),"Scalar point uses commas");
        check(values.getString("point3").equals("1,2,3"),"Scalar point3 uses three comma-separated coordinates");
        check(values.getJSONArray("area").toString().equals("[\"0x0\",\"200x0\",\"200x200\"]"),"Point vector uses x-separated coordinates per Orca vserialize");
        check(values.getJSONArray("areas").toString().equals("[\"0x0,2x2\",\"\"]"),"Point groups retain separate string entries and empty groups");
        check(values.getJSONArray("groups").toString().equals("[\"1,2\",\"3\"]"),"Integer groups retain separate comma-separated entries");
        check(values.getJSONArray("temperatures").toString().equals("[\"210\",\"220\"]"),"Integer vector values remain string entries");
        check(values.getJSONArray("ratios").toString().equals("[\"5%\",\"10.25%\"]"),"Percent vector keeps percent on each entry");
        check(values.getJSONArray("widths").toString().equals("[\"0.4\",\"100%\"]"),"Float-or-percent vectors do not leak typed objects into the editor");
        check(values.getJSONArray("scripts").toString().equals("[\"a;b\",\"new\\nline\"]"),"String vectors preserve semicolons and decoded newlines");
        check(values.getString("pattern").equals("gyroid")&&values.getJSONArray("enums").getString(1).equals("two"),"Enum scalars and vectors remain strings");
        check(values.getString("nullable_scalar").equals("nil")&&values.getJSONArray("nullable_floats").getString(0).equals("nil"),"Supported nullable scalars and vector entries use literal nil");
        check(values.getJSONArray("nullable_floats").getString(1).equals("0.1234567891234567"),"Nullable vectors retain non-null precision");
        check(values.getJSONArray("flags").toString().equals("[\"1\",\"0\",\"nil\"]"),"Nullable bool vector is correctly represented");
        check(values.getString("unicode").equals("Кеплер 🚀\n\t\\path; \"quoted\""),"Unicode, supplementary code points and escaped macro text survive");
        check(values.getJSONArray("nullable_ints").toString().equals("[\"nil\",\"-2147483648\",\"2147483647\"]"),"Nullable integer vector retains native signed bounds");
        check(values.getJSONArray("nullable_percents").toString().equals("[\"nil\",\"12.34567891234567%\"]"),"Nullable percent vector preserves precise non-null values");
        check(values.getJSONArray("nullable_widths").toString().equals("[\"nil\",\"0.1234567891234567\"]"),"Nullable float-or-percent vector preserves absolute precision");
        check(values.getJSONArray("nullable_enums").toString().equals("[\"nil\",\"second\"]"),"Nullable enum vector uses explicit nil string");
        check(values.getString("precise_point").equals("1.234567891234567,2.345678912345678"),"Scalar points preserve each direct numeric coordinate");
        check(values.getJSONArray("precise_groups").getString(0).equals("1.234567891234567x2.345678912345678"),"Point groups preserve coordinate precision instead of rounded wire text");
        values.getJSONArray("filament_diameter").put(0,"mutated");
        check(NativeTypedConfigSnapshot.readAll(bytes(document)).getJSONArray("filament_diameter").getString(0).equals("1.750000000000017")&&document.toString().equals(original),"Returned editor values and caller source are detached");

        SettingsSchema schema=SettingsSchema.load(new Context(new File(args[1]),new File(args[0])));
        JSONObject process=NativeTypedConfigSnapshot.read(bytes(document),schema.options("process"));
        JSONObject filament=NativeTypedConfigSnapshot.read(bytes(document),schema.options("filament"));
        JSONObject printer=NativeTypedConfigSnapshot.read(bytes(document),schema.options("printer"));
        check(process.has("layer_height")&&!process.has("filament_diameter")&&!process.has("point"),"Process filters by its actual schema scope");
        check(filament.has("filament_diameter")&&!filament.has("layer_height"),"Filament filters by its actual schema scope");
        check(printer.has("nozzle_diameter")&&!printer.has("layer_height"),"Printer filters by its actual schema scope");
        JSONObject mismatched=fixture();ProjectSettingsSnapshotTest.option(mismatched,"layer_height","coInt",3,false,"3");
        try{NativeTypedConfigSnapshot.read(bytes(mismatched),schema.options("process"));throw new AssertionError("Accepted schema type mismatch");}catch(IOException expected){checks++;}

        reject(d->d.put("version",1),"invented or changed outer ABI version");
        reject(d->d.put("scope","process"),"invented or changed outer ABI scope");
        reject(d->d.remove("options"),"missing options object");
        reject(d->d.getJSONObject("preset_values").remove("layer_height"),"mismatched wire key count");
        reject(d->{d.getJSONObject("preset_values").remove("layer_height");d.getJSONObject("preset_values").put("other","0.2");},"same-size mismatched wire key set");
        reject(d->d.getJSONObject("preset_values").put("layer_height","0.1"),"row and top-level wire disagreement");
        reject(d->row(d,"layer_height").remove("serialized"),"missing row ABI field");
        reject(d->row(d,"layer_height").put("extra",true),"unknown row ABI field");
        reject(d->row(d,"layer_height").put("nullable","false"),"coerced nullable flag");
        reject(d->row(d,"layer_height").put("type","future_type"),"unknown native option type");
        reject(d->row(d,"layer_height").put("value","0.2"),"string instead of native numeric value");
        reject(d->row(d,"layer_height").put("value",JSONObject.NULL),"null non-nullable float");
        reject(d->row(d,"filament_diameter").put("value",JSONObject.NULL),"null entire native vector");
        reject(d->row(d,"filament_diameter").put("value",new JSONArray("[null]")),"null vector entry when non-nullable");
        reject(d->row(d,"wall_loops").put("value",3.5),"fractional native integer");
        reject(d->row(d,"wall_loops").put("value",2147483648L),"overflowing native integer");
        reject(d->row(d,"enable_support").put("value",1),"numeric native boolean");
        reject(d->row(d,"line_width").getJSONObject("value").put("percent","true"),"coerced float-or-percent flag");
        reject(d->row(d,"line_width").getJSONObject("value").put("extra",0),"unknown float-or-percent shape");
        reject(d->row(d,"point").put("value",new JSONArray("[1,2,3]")),"wrong point dimension");
        reject(d->row(d,"point3").put("value",new JSONArray("[1,2]")),"wrong point3 dimension");
        reject(d->row(d,"areas").put("value",new JSONArray("[[[0,null]]]")),"invalid coordinate inside groups");
        reject(d->row(d,"groups").put("value",new JSONArray("[[1,2.5]]")),"noninteger inside integer groups");
        reject(d->row(d,"flags").put("value",new JSONArray("[true,1]")),"coerced bool vector entry");
        reject(d->{row(d,"pattern").put("preset_value",42);d.getJSONObject("preset_values").put("pattern",42);},"non-string preset wire scalar");
        reject(d->{JSONArray wire=new JSONArray().put("0.2");row(d,"layer_height").put("preset_value",wire);d.getJSONObject("preset_values").put("layer_height",wire);},"array preset wire for a scalar");
        reject(d->{row(d,"filament_diameter").put("preset_value","1.75");d.getJSONObject("preset_values").put("filament_diameter","1.75");},"scalar preset wire for a vector");
        reject(d->{JSONArray wire=new JSONArray().put(new JSONArray().put("1.75"));row(d,"filament_diameter").put("preset_value",wire);d.getJSONObject("preset_values").put("filament_diameter",wire);},"nested preset wire entries unsupported by this ABI");
        rejectText("{\"options\":{},\"options\":{},\"preset_values\":{}}","duplicate JSON keys");
        rejectText("{options:{},preset_values:{}}","unquoted JSON keys");
        rejectText("{\"options\":{},\"preset_values\":{},}","trailing JSON comma");
        rejectText("{\"options\":{},\"preset_values\":{}} false","trailing JSON data");
        rejectText("/*comment*/{\"options\":{},\"preset_values\":{}}","JSON comments");
        rejectText("[ ]","nonobject JSON scope");
        rejectText(original.replace("0.234567891234567","1e999"),"overflowing JSON number");
        rejectText(original.replace("0.234567891234567","1e-999"),"underflowing native numeric value");
        rejectText(original.replace("Кеплер","\\uD800"),"unpaired Unicode surrogate");
        rejectText(original.replace("Кеплер","\\u+FFF"),"signed Unicode escape outside JSON grammar");
        try{NativeTypedConfigSnapshot.readAll(new byte[]{(byte)0xc3,0x28});throw new AssertionError("Accepted malformed UTF8");}catch(IOException expected){checks++;}
        try{NativeTypedConfigSnapshot.readAll(null);throw new AssertionError("Accepted missing JNI bytes");}catch(IOException expected){checks++;}
        Thread.currentThread().interrupt();
        try{NativeTypedConfigSnapshot.readAll(bytes(document));throw new AssertionError("Ignored cancellation");}
        catch(java.io.InterruptedIOException expected){check(Thread.currentThread().isInterrupted(),"Cancellation is propagated without clearing interrupt state");}
        finally{Thread.interrupted();}
        System.out.println("Native typed config snapshot: "+checks+" host checks passed (JNI not executed)");
    }
}
