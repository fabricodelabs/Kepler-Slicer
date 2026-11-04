package com.keplerslicer.app;

import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Converts ConfigBase::save()/serialize() to save_to_json()/vserialize() representation.
 * Source: upstream Config.cpp escape_string_cstyle, unescape_strings_cstyle, save_to_json;
 * Config.hpp ConfigOption*::serialize/vserialize. Unknown or malformed types return null.
 */
final class ProfileIniCodec {
    private static final String NUM = "[+-]?(?:(?:[0-9]+(?:\\.[0-9]*)?)|(?:\\.[0-9]+))(?:[eE][+-]?[0-9]+)?";
    private static final Pattern NUMBER = Pattern.compile(NUM), INTEGER = Pattern.compile("[+-]?[0-9]+");
    private static final Pattern POINT = Pattern.compile(NUM + "x" + NUM);
    private static final Pattern SCALAR_POINT = Pattern.compile(NUM + "," + NUM);
    private ProfileIniCodec() {}

    static Object convert(String key, String type, String raw) {
        if (type == null || raw == null) return null;
        switch (type) {
            case "coString":
                if ("bed_custom_texture".equals(key) || "bed_custom_model".equals(key)) return raw;
                return unescape(raw);
            case "coBool": return "0".equals(raw) || "1".equals(raw) ? raw : null;
            case "coInt": return INTEGER.matcher(raw).matches() ? raw : null;
            case "coFloat": return numeric(raw, false) ? raw : null;
            case "coPercent": return raw.endsWith("%") && numeric(raw.substring(0,raw.length()-1), false) ? raw : null;
            case "coFloatOrPercent": return numeric(raw, true) ? raw : null;
            case "coEnum": return raw.isEmpty() ? null : raw;
            case "coPoint": return SCALAR_POINT.matcher(raw).matches() ? raw : null;
            case "coStrings": {
                List<String> strings = unescapeStrings(raw);
                return strings == null ? null : array(strings);
            }
            case "coFloats": case "coInts": case "coBools": case "coPercents":
            case "coFloatsOrPercents": case "coEnums": case "coPoints":
            case "coPointsGroups": case "coIntsGroups":
                return vector(type, raw);
            default: return null;
        }
    }
    private static boolean numeric(String value, boolean allowPercent) {
        if (allowPercent && value.endsWith("%")) value = value.substring(0,value.length()-1);
        if (!NUMBER.matcher(value).matches()) return false;
        try { return Double.isFinite(Double.parseDouble(value)); } catch (NumberFormatException e) { return false; }
    }
    private static JSONArray vector(String type, String raw) {
        JSONArray result = new JSONArray();
        // One empty group and zero groups both serialize to the empty string.
        if (raw.isEmpty()) return "coPointsGroups".equals(type) || "coIntsGroups".equals(type) ? null : result;
        String separator = "coPointsGroups".equals(type) || "coIntsGroups".equals(type) ? "#" : ",";
        for (String item : raw.split(Pattern.quote(separator), -1)) {
            if (!validItem(type, item)) return null;
            result.put(item);
        }
        return result;
    }
    private static boolean validItem(String type, String item) {
        if ("nil".equals(item)) return !"coPoints".equals(type) && !"coPointsGroups".equals(type) && !"coIntsGroups".equals(type);
        switch (type) {
            case "coFloats": return numeric(item, false);
            case "coInts": return INTEGER.matcher(item).matches();
            case "coBools": return "0".equals(item) || "1".equals(item);
            case "coPercents": return item.endsWith("%") && numeric(item.substring(0,item.length()-1), false);
            case "coFloatsOrPercents": return numeric(item, true);
            case "coEnums": return !item.isEmpty();
            case "coPoints": return POINT.matcher(item).matches();
            case "coPointsGroups":
            case "coIntsGroups":
                if (item.isEmpty()) return true;
                for (String part : item.split(",", -1))
                    if ("coPointsGroups".equals(type) ? !POINT.matcher(part).matches() : !INTEGER.matcher(part).matches()) return false;
                return true;
            default: return false;
        }
    }
    private static JSONArray array(List<String> strings) {
        JSONArray result = new JSONArray();
        for (String s : strings) result.put(s);
        return result;
    }
    private static String unescape(String text) {
        StringBuilder result = new StringBuilder();
        for (int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if (c=='\\') {
                if (++i==text.length()) return null;
                c=text.charAt(i);
                if (c=='n') c='\n'; else if (c=='r') c='\r';
            }
            result.append(c);
        }
        return result.toString();
    }
    private static List<String> unescapeStrings(String text) {
        List<String> out = new ArrayList<>();
        if (text.isEmpty()) return out;
        int i=0, n=text.length();
        while (true) {
            while (i<n && (text.charAt(i)==' ' || text.charAt(i)=='\t')) i++;
            if (i==n) return out;
            StringBuilder item=new StringBuilder();
            if (text.charAt(i)=='"') {
                i++; boolean closed=false;
                while (i<n) {
                    char c=text.charAt(i++);
                    if (c=='"') { closed=true; break; }
                    if (c=='\\') {
                        if (i==n) return null;
                        c=text.charAt(i++); if (c=='n') c='\n'; else if (c=='r') c='\r';
                    }
                    item.append(c);
                }
                if (!closed) return null;
            } else {
                while (i<n && text.charAt(i)!=';') item.append(text.charAt(i++));
            }
            out.add(item.toString());
            if (i==n) return out;
            while (i<n && (text.charAt(i)==' ' || text.charAt(i)=='\t')) i++;
            if (i==n) return out;
            if (text.charAt(i++)!=';') return null;
            if (i==n) { out.add(""); return out; }
        }
    }
}
