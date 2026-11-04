package com.keplerslicer.app;
import org.json.JSONArray;

public final class ProfileIniCodecTest {
    static void same(Object a, Object b) {
        if (!java.util.Objects.equals(a,b)) throw new AssertionError("Expected " + b + ", got " + a);
    }
    public static void main(String[] args) {
        same(ProfileIniCodec.convert("start_gcode","coString","G1 X1\\nM117 \\\"Hello\\\"\\r\\\\a"), "G1 X1\nM117 \"Hello\"\r\\a");
        JSONArray strings=(JSONArray)ProfileIniCodec.convert("post_process","coStrings","simple;\"with space\";\"semi;colon\";\"quote\\\"x\";\"line\\n2\";");
        same(strings.toString(), "[\"simple\",\"with space\",\"semi;colon\",\"quote\\\"x\",\"line\\n2\",\"\"]");
        same(((JSONArray)ProfileIniCodec.convert("x","coStrings","")).length(),0);
        same(((JSONArray)ProfileIniCodec.convert("x","coStrings","\"\"")).length(),1);
        same(ProfileIniCodec.convert("x","coStrings","\"incomplete"),null);
        same(ProfileIniCodec.convert("x","coFloat","1.23457e-06"),"1.23457e-06");
        same(ProfileIniCodec.convert("x","coFloat","nan"),null);
        same(ProfileIniCodec.convert("x","coPercent","15%"),"15%");
        same(ProfileIniCodec.convert("x","coFloatOrPercent","30%"),"30%");
        same(ProfileIniCodec.convert("x","coFloats","1.2,nil,-3e-2").toString(),"[\"1.2\",\"nil\",\"-3e-2\"]");
        same(ProfileIniCodec.convert("x","coPercents","10%,nil,30%").toString(),"[\"10%\",\"nil\",\"30%\"]");
        same(ProfileIniCodec.convert("x","coPoint","12.5,-4"),"12.5,-4");
        same(ProfileIniCodec.convert("x","coPoints","0x0,250x220").toString(),"[\"0x0\",\"250x220\"]");
        same(ProfileIniCodec.convert("x","coPointsGroups","0x0,1x1#2x3").toString(),"[\"0x0,1x1\",\"2x3\"]");
        same(ProfileIniCodec.convert("x","coPointsGroups",""),null);
        same(ProfileIniCodec.convert("bed_custom_model","coString","C:\\\\a"),"C:\\\\a");
        same(ProfileIniCodec.convert("x",null,"5"),null);
        System.out.println("upstream INI codec checks passed");
    }
}
