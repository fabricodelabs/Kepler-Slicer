package com.orcamobile.app;

import com.orcamobile.app.settings.SettingsSchema;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checked current JNI config-scope ABI; direct values, never rounded preset_values. */
final class NativeTypedConfigSnapshot {
    private NativeTypedConfigSnapshot() { }

    static JSONObject read(byte[] utf8, List<SettingsSchema.Option> section) throws IOException {
        if (section == null) throw new IOException("Settings schema section is missing");
        return readScope(utf8,section);
    }
    static JSONObject readAll(byte[] utf8) throws IOException { return readScope(utf8,null); }
    private static JSONObject readScope(byte[] utf8, List<SettingsSchema.Option> section) throws IOException {
        if (utf8 == null) throw new IOException("Native typed settings were not returned");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(utf8)).toString();
            Object parsed = StrictJson.read(text);
            if (!(parsed instanceof JSONObject)) throw new IOException("Native typed settings must be an object");
            JSONObject document = (JSONObject)parsed;
            requireKeys(document, "options", "preset_values");
            JSONObject options = document.getJSONObject("options"), wireValues = document.getJSONObject("preset_values");
            if (options.length() != wireValues.length() || options.length() > 4096)
                throw new IOException("Native typed settings key count mismatch");
            Map<String,SettingsSchema.Option> schema = new HashMap<>();
            if (section != null) for (SettingsSchema.Option option : section) schema.put(option.key,option);
            JSONObject values = new JSONObject();
            Iterator<String> keys = options.keys();
            while (keys.hasNext()) {
                interrupted();
                String key = keys.next();
                if (key.isEmpty() || !wireValues.has(key)) throw new IOException("Native typed settings key mismatch");
                JSONObject row = options.getJSONObject(key);
                requireKeys(row,"type","nullable","serialized","value","preset_value");
                String type = string(row.get("type"));
                boolean nullable = bool(row.get("nullable"));
                string(row.get("serialized"));
                Object wire = row.get("preset_value");
                validateWire(wire,type);
                if (!sameWire(wire,wireValues.get(key))) throw new IOException("Native preset value mismatch: " + key);
                Object value = convert(type,row.get("value"),nullable);
                SettingsSchema.Option option = schema.get(key);
                if (section == null) { values.put(key,value); continue; }
                if (option == null) continue;
                if (option.type != null && !option.type.equals(type)) throw new IOException("Native settings type differs from schema: " + key);
                if (option.nullable != null && option.nullable.booleanValue() != nullable)
                    throw new IOException("Native settings nullability differs from schema: " + key);
                values.put(key,value);
            }
            return values;
        } catch (JSONException | java.nio.charset.CharacterCodingException e) {
            throw new IOException("Invalid native typed settings",e);
        }
    }

    private static Object convert(String type,Object value,boolean nullable) throws IOException,JSONException {
        interrupted();
        if (value == JSONObject.NULL) {
            if (!nullable) throw new IOException("Unexpected null native setting");
            // Native vectors are arrays even when all their entries are nil.
            if (vectorType(type) != null) throw new IOException("Native setting vector is not an array");
            requireType(type);
            return "nil";
        }
        switch (type) {
            case "coFloat": return numeric(value,false);
            case "coInt": return numeric(value,true);
            case "coPercent": return numeric(value,false) + "%";
            case "coBool": return bool(value) ? "1" : "0";
            case "coString": case "coEnum": return string(value);
            case "coFloatOrPercent": {
                if (!(value instanceof JSONObject)) throw new IOException("Invalid native absolute/percent setting");
                JSONObject fraction = (JSONObject)value;
                requireKeys(fraction,"value","percent");
                return numeric(fraction.get("value"),false) + (bool(fraction.get("percent")) ? "%" : "");
            }
            case "coPoint": return point(value,2,",");
            case "coPoint3": return point(value,3,",");
            case "coPointsGroups": return groups(value,true,nullable);
            case "coIntsGroups": return groups(value,false,nullable);
            default: {
                String elementType = vectorType(type);
                if (elementType == null) throw new IOException("Unsupported native setting type: " + type);
                if (!(value instanceof JSONArray)) throw new IOException("Native setting vector is not an array");
                JSONArray array = (JSONArray)value, result = new JSONArray();
                for (int i=0;i<array.length();i++) {
                    Object item = array.get(i);
                    if ("coPoint".equals(elementType) && item != JSONObject.NULL) result.put(point(item,2,"x"));
                    else result.put(convert(elementType,item,nullable));
                }
                return result;
            }
        }
    }
    private static String vectorType(String type) {
        switch (type) {
            case "coFloats": return "coFloat"; case "coInts": return "coInt";
            case "coPercents": return "coPercent"; case "coStrings": return "coString";
            case "coBools": return "coBool"; case "coEnums": return "coEnum";
            case "coFloatsOrPercents": return "coFloatOrPercent"; case "coPoints": return "coPoint";
            case "coPointsGroups": return "coPoints"; case "coIntsGroups": return "coInts";
            default:return null;
        }
    }
    private static void requireType(String type) throws IOException {
        switch (type) {
            case "coFloat": case "coInt": case "coPercent": case "coBool": case "coString": case "coEnum":
            case "coFloatOrPercent": case "coPoint": case "coPoint3": return;
            default:throw new IOException("Unsupported native setting type: " + type);
        }
    }
    private static JSONArray groups(Object value,boolean points,boolean nullable) throws IOException,JSONException {
        if (!(value instanceof JSONArray)) throw new IOException("Invalid native setting groups");
        JSONArray groups = (JSONArray)value, result = new JSONArray();
        for (int i=0;i<groups.length();i++) {
            Object group = groups.get(i);
            if (group == JSONObject.NULL && nullable) { result.put("nil"); continue; }
            if (!(group instanceof JSONArray)) throw new IOException("Invalid native setting group");
            JSONArray items = (JSONArray)group; StringBuilder encoded = new StringBuilder();
            for (int j=0;j<items.length();j++) {
                if (j>0) encoded.append(',');
                encoded.append(points ? point(items.get(j),2,"x") : numeric(items.get(j),true));
            }
            result.put(encoded.toString());
        }
        return result;
    }
    private static String point(Object value,int dimensions,String separator) throws IOException,JSONException {
        if (!(value instanceof JSONArray) || ((JSONArray)value).length()!=dimensions)
            throw new IOException("Invalid native setting point");
        JSONArray point = (JSONArray)value; StringBuilder result = new StringBuilder();
        for (int i=0;i<dimensions;i++) {
            if (i>0) result.append(separator);
            result.append(numeric(point.get(i),false));
        }
        return result.toString();
    }
    private static String numeric(Object value,boolean integer) throws IOException {
        if (!(value instanceof Number)) throw new IOException("Invalid native numeric setting");
        double number = ((Number)value).doubleValue();
        if (!Double.isFinite(number)) throw new IOException("Non-finite native numeric setting");
        BigDecimal decimal;
        try { decimal = new BigDecimal(value.toString()); }
        catch (NumberFormatException e) { throw new IOException("Invalid native numeric setting",e); }
        if (number == 0 && decimal.signum()!=0) throw new IOException("Native numeric setting underflows double");
        if (integer) {
            try { return Integer.toString(decimal.intValueExact()); }
            catch (ArithmeticException e) { throw new IOException("Native integer setting is outside its range",e); }
        }
        return decimal.stripTrailingZeros().toPlainString();
    }
    private static String string(Object value) throws IOException {
        if (!(value instanceof String)) throw new IOException("Invalid native setting text");
        return (String)value;
    }
    private static boolean bool(Object value) throws IOException {
        if (!(value instanceof Boolean)) throw new IOException("Invalid native setting boolean");
        return (Boolean)value;
    }
    private static void requireKeys(JSONObject object,String... expected) throws IOException {
        if (object.length()!=expected.length) throw new IOException("Unknown native settings ABI shape");
        for (String key:expected) if (!object.has(key)) throw new IOException("Missing native settings field: " + key);
    }
    private static void validateWire(Object value,String type) throws IOException,JSONException {
        if (vectorType(type)==null) {
            if (!(value instanceof String)) throw new IOException("Invalid native scalar preset wire value");
            return;
        }
        if (!(value instanceof JSONArray)) throw new IOException("Invalid native vector preset wire value");
        JSONArray array=(JSONArray)value;
        for (int i=0;i<array.length();i++) if (!(array.get(i) instanceof String))
            throw new IOException("Invalid native vector preset wire entry");
    }
    private static boolean sameWire(Object a,Object b) throws JSONException {
        if (a instanceof String) return b instanceof String && a.equals(b);
        if (!(a instanceof JSONArray) || !(b instanceof JSONArray)) return false;
        JSONArray left=(JSONArray)a,right=(JSONArray)b;
        if (left.length()!=right.length()) return false;
        for (int i=0;i<left.length();i++) if (!sameWire(left.get(i),right.get(i))) return false;
        return true;
    }
    private static void interrupted() throws java.io.InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Reading native settings was canceled");
    }

    /** org.json is lenient on Android; reject duplicate keys and non-JSON syntax consistently. */
    private static final class StrictJson {
        private static final Pattern NUMBER=Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
        final String text;int at;
        StrictJson(String text){this.text=text;}
        static Object read(String text)throws IOException,JSONException {
            StrictJson parser=new StrictJson(text);Object value=parser.value(0);parser.ws();
            if(parser.at!=text.length())throw parser.error();return value;
        }
        IOException error(){return new IOException("Invalid native settings JSON at " + at);}
        void ws(){while(at<text.length()&&" \t\r\n".indexOf(text.charAt(at))>=0)at++;}
        boolean eat(char ch){ws();if(at<text.length()&&text.charAt(at)==ch){at++;return true;}return false;}
        Object value(int depth)throws IOException,JSONException {
            interrupted();if(depth>32)throw error();ws();if(at>=text.length())throw error();char ch=text.charAt(at);
            if(ch=='"')return quoted();
            if(ch=='{') {
                at++;JSONObject result=new JSONObject();Set<String> keys=new HashSet<>();if(eat('}'))return result;
                do{ws();if(at>=text.length()||text.charAt(at)!='"')throw error();String key=quoted();
                    if(!keys.add(key)||!eat(':'))throw error();result.put(key,value(depth+1));
                }while(eat(','));if(!eat('}'))throw error();return result;
            }
            if(ch=='[') {
                at++;JSONArray result=new JSONArray();if(eat(']'))return result;
                do{result.put(value(depth+1));}while(eat(','));if(!eat(']'))throw error();return result;
            }
            if(text.startsWith("true",at)){at+=4;return true;}
            if(text.startsWith("false",at)){at+=5;return false;}
            if(text.startsWith("null",at)){at+=4;return JSONObject.NULL;}
            Matcher matcher=NUMBER.matcher(text);matcher.region(at,text.length());if(!matcher.lookingAt())throw error();
            String raw=matcher.group();at=matcher.end();
            try{BigDecimal number=new BigDecimal(raw);if(!Double.isFinite(number.doubleValue()))throw error();return number;}
            catch(NumberFormatException e){throw error();}
        }
        String quoted()throws IOException {
            if(text.charAt(at++)!='"')throw error();StringBuilder value=new StringBuilder();
            while(at<text.length()) {
                char ch=text.charAt(at++);
                if(ch=='"') {
                    for(int i=0;i<value.length();i++)if(Character.isHighSurrogate(value.charAt(i))) {
                        if(++i>=value.length()||!Character.isLowSurrogate(value.charAt(i)))throw error();
                    }else if(Character.isLowSurrogate(value.charAt(i)))throw error();
                    return value.toString();
                }
                if(ch<0x20)throw error();if(ch!='\\'){value.append(ch);continue;}
                if(at>=text.length())throw error();ch=text.charAt(at++);
                switch(ch) {
                    case '"':case '\\':case '/':value.append(ch);break;
                    case 'b':value.append('\b');break;case 'f':value.append('\f');break;
                    case 'n':value.append('\n');break;case 'r':value.append('\r');break;case 't':value.append('\t');break;
                    case 'u':if(at+4>text.length())throw error();
                        for(int i=at;i<at+4;i++)if("0123456789abcdefABCDEF".indexOf(text.charAt(i))<0)throw error();
                        value.append((char)Integer.parseInt(text.substring(at,at+4),16));at+=4;break;
                    default:throw error();
                }
            }
            throw error();
        }
    }
}
