package com.keplerslicer.app;
import org.json.JSONObject;
import java.io.IOException;
/** Explicit STEP option boundary for the actual archive tests; no OCCT/JNI claim. */
final class StepImportPreferences {
    static final class Options {
        final double linearMm,angularRad;final boolean split;
        Options(double linear,double angular,boolean split){if(!Double.isFinite(linear)||linear<=0||!Double.isFinite(angular)||angular<=0||angular>Math.PI)throw new IllegalArgumentException();linearMm=linear;angularRad=angular;this.split=split;}
        JSONObject toJson(){try{return new JSONObject().put("schema","kepler.step-options.v1").put("linear_mm",linearMm).put("angular_rad",angularRad).put("split",split);}catch(Exception e){throw new AssertionError(e);}}
        static Options parse(JSONObject json)throws IOException{try{if(!"kepler.step-options.v1".equals(json.getString("schema"))||!(json.get("linear_mm") instanceof Number)||!(json.get("angular_rad") instanceof Number)||!(json.get("split") instanceof Boolean))throw new IOException("Invalid options");return new Options(json.getDouble("linear_mm"),json.getDouble("angular_rad"),json.getBoolean("split"));}catch(Exception e){throw new IOException("Invalid options",e);}}
        boolean same(Options other){return other!=null&&linearMm==other.linearMm&&angularRad==other.angularRad&&split==other.split;}
    }
}
