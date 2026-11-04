package com.keplerslicer.app;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;

/** Host tests cover immutable saved options and filename routing; not OCCT execution. */
public final class StepImportPreferencesTest {
    private static int checks;
    private interface IoAction { void run() throws Exception; }
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    private static void reject(IoAction action,String name)throws Exception {try{action.run();}catch(IOException|IllegalArgumentException expected){checks++;return;}throw new AssertionError(name);}
    public static void main(String[] args)throws Exception {
        File root=new File(args[0]);root.mkdirs();
        StepImportPreferences.Options fine=new StepImportPreferences.Options(.003,.5,false);
        StepImportPreferences.Options coarse=new StepImportPreferences.Options(.5,1,true);
        check(fine.same(StepImportPreferences.Options.parse(fine.toJson().toString())),"fine typed JSON roundtrip");
        check(coarse.same(StepImportPreferences.Options.parse(coarse.toJson())),"coarse split JSON roundtrip");
        check(!fine.same(coarse),"tolerance and split identity");
        for(double invalid:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            reject(()->new StepImportPreferences.Options(invalid,.5,false),"invalid linear");
            reject(()->new StepImportPreferences.Options(.003,invalid,false),"invalid angular");
        }
        reject(()->new StepImportPreferences.Options(.003,Math.PI+.001,false),"angular beyond pi");
        check(new StepImportPreferences.Options(1000,Math.PI,false).linearMm==1000,"no arbitrary linear maximum");
        reject(()->StepImportPreferences.Options.parse(new JSONObject(fine.toJson().toString()).put("linear_mm","0.003")),"numeric string rejected");
        reject(()->StepImportPreferences.Options.parse(new JSONObject(fine.toJson().toString()).put("split",0)),"numeric boolean rejected");
        reject(()->StepImportPreferences.Options.parse(new JSONObject(fine.toJson().toString()).put("schema","future")),"unknown schema rejected");
        reject(()->StepImportPreferences.Options.parse(new JSONObject().put("linear_mm",.003)),"partial options rejected");
        check(StepImportPreferences.isStep(new File("MODEL.STEP")),"case insensitive STEP");
        check(StepImportPreferences.isStep(new File("part.stp")),"STP supported");
        check(!StepImportPreferences.isStep(new File("part.step.zip")),"compressed STEP is not plain STEP");
        check(!StepImportPreferences.isStep(null),"null file");
        check(ModelFilename.normalize("CAD.STP",null,"application/octet-stream").equals("CAD.stp"),"display STEP alias wins");
        check(ModelFilename.normalize("CAD",null,"model/step").equals("CAD.step"),"STEP MIME fallback");
        check(ModelFilename.normalize(null,"shape.step","application/octet-stream").endsWith(".step"),"URI fallback");
        check(ModelFilename.normalize("shape.stl",null,"model/step").equals("shape.stl"),"explicit display format priority");
        reject(()->ModelFilename.normalize("shape.zip",null,"application/step+zip"),"ZIP STEP not falsely registered");
        File source=new File(root,"original.step");byte[] original="ISO-10303-21;\nEND-ISO-10303-21;\n".getBytes(StandardCharsets.UTF_8);Files.write(source.toPath(),original);
        reject(()->StepImportPreferences.saved(source),"missing immutable options rejected");
        StepImportPreferences.saveOptions(source,fine);
        check(fine.same(StepImportPreferences.saved(source)),"saved exact options bound to original");
        check(java.util.Arrays.equals(original,Files.readAllBytes(source.toPath())),"saving options keeps original bytes");
        StepImportPreferences.saveOptions(source,coarse);
        check(coarse.same(StepImportPreferences.saved(source)),"atomic settings replacement");
        source.setLastModified(1);
        check(coarse.same(StepImportPreferences.saved(source)),"identity follows bytes rather than timestamp");
        Files.write(source.toPath(),"different-content".getBytes(StandardCharsets.UTF_8));
        reject(()->StepImportPreferences.saved(source),"mutated source rejected");
        Files.write(source.toPath(),original);
        check(coarse.same(StepImportPreferences.saved(source)),"unchanged bytes restore identity");
        File sidecar=StepImportPreferences.optionsFile(source);
        JSONObject bad=new JSONObject(new String(Files.readAllBytes(sidecar.toPath()),StandardCharsets.UTF_8));bad.put("source_sha256","0");Files.write(sidecar.toPath(),bad.toString().getBytes(StandardCharsets.UTF_8));
        reject(()->StepImportPreferences.saved(source),"corrupt source hash rejected");
        StepImportPreferences.saveOptions(source,fine);
        Thread.currentThread().interrupt();
        try{reject(()->StepImportPreferences.saved(source),"interrupted source verification rejected");}finally{Thread.interrupted();}
        check(fine.same(StepImportPreferences.saved(source)),"interruption keeps valid saved state");
        Files.write(sidecar.toPath(),new byte[20000]);
        reject(()->StepImportPreferences.saved(source),"oversized metadata rejected without source size cap");
        check(java.util.Arrays.equals(original,Files.readAllBytes(source.toPath())),"failed reads keep original bytes");
        reject(()->StepImportPreferences.saveOptions(new File(root,"missing.step"),fine),"missing source rejected");
        File nonStep=new File(root,"model.stl");Files.write(nonStep.toPath(),original);
        reject(()->StepImportPreferences.saveOptions(nonStep,fine),"wrong format rejected");
        File snapshot=new File(root,"frozen"),cache=new File(root,"derived-cache");snapshot.mkdirs();cache.mkdirs();
        File frozenSource=new File(snapshot,"model.step"),derived=new File(cache,"model.3mf"),target=new File(snapshot,"model.3mf");
        byte[] meshBytes="hash-binding-only; not a native mesh".getBytes(StandardCharsets.UTF_8);
        Files.write(frozenSource.toPath(),original);Files.write(derived.toPath(),meshBytes);Files.write(target.toPath(),meshBytes);
        JSONObject nativeMeta=new JSONObject().put("schema","kepler.step-import.v1").put("units","mm").put("triangle_count",4).put("linear_deflection_mm",fine.linearMm).put("angular_deflection_rad",fine.angularRad).put("split_compounds",fine.split);
        Files.write(new File(cache,"step-import.json").toPath(),nativeMeta.toString().getBytes(StandardCharsets.UTF_8));
        StepImportPreferences.copyVerifiedProvenance(frozenSource,fine,derived,target);
        byte[] proofBytes=Files.readAllBytes(new File(snapshot,"step-import.json").toPath());
        JSONObject proof=new JSONObject(new String(proofBytes,StandardCharsets.UTF_8));
        check(proof.getString("schema").equals("kepler.step-derivative.v1")&&proof.getString("original_file").equals("model.step"),"frozen print proof has dedicated schema/source path");
        check(proof.getLong("source_bytes")==original.length&&proof.getLong("derived_bytes")==meshBytes.length,"proof binds both file lengths");
        check(proof.getString("source_sha256").length()==64&&proof.getString("derived_sha256").length()==64,"proof binds both digests");
        Files.write(target.toPath(),new byte[]{1,2,3});
        reject(()->StepImportPreferences.copyVerifiedProvenance(frozenSource,fine,derived,target),"changed copied derivative rejected");
        check(java.util.Arrays.equals(proofBytes,Files.readAllBytes(new File(snapshot,"step-import.json").toPath())),"failed proof replacement keeps prior record");
        Files.write(target.toPath(),meshBytes);
        reject(()->StepImportPreferences.copyVerifiedProvenance(frozenSource,coarse,derived,target),"mismatched tessellation options rejected");
        reject(()->StepImportPreferences.copyVerifiedProvenance(source,fine,derived,target),"non-sibling source cannot acquire STEP snapshot proof");
        check(java.util.Arrays.equals(original,Files.readAllBytes(frozenSource.toPath())),"proof creation preserves original STEP");
        System.out.println("STEP Java checks: "+checks+" passed (no native tessellation simulated).");
    }
}
