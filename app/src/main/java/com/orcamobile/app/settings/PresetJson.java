package com.orcamobile.app.settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** JSON copies preserve unknown fields, arrays, null values and their original value types. */
public final class PresetJson {
    private PresetJson() { }
    public static JSONObject copy(JSONObject value) throws IOException {
        try { return new JSONObject(value.toString()); }
        catch (JSONException e) { throw new IOException("Invalid preset JSON", e); }
    }
    public static JSONObject read(InputStream stream) throws IOException {
        try { return new JSONObject(readText(stream, 64L * 1024 * 1024)); }
        catch (JSONException e) { throw new IOException("Expected a JSON object", e); }
    }
    static String readText(InputStream stream, long maximum) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
        while ((n = stream.read(buffer)) != -1) {
            if ((long)out.size() + n > maximum) throw new IOException("Configuration JSON is too large");
            out.write(buffer, 0, n);
        }
        String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
    public static JSONObject merge(JSONObject parent, JSONObject child) throws IOException {
        JSONObject result = copy(parent);
        try { for (Iterator<String> i = child.keys(); i.hasNext();) { String key = i.next(); result.put(key, child.get(key)); } }
        catch (JSONException e) { throw new IOException(e); }
        return copy(result); // Orca applies options atomically; arrays/objects are not deep-merged.
    }
    public static boolean equal(Object a, Object b) throws IOException { return canonical(a).equals(canonical(b)); }
    public static String fingerprint(Object value) throws IOException { return sha256(canonical(value)); }
    public static String sha256(String text) throws IOException {
        try { StringBuilder out = new StringBuilder(); for (byte b : MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return out.toString(); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }
    private static String canonical(Object value) throws IOException {
        try {
            if (value == null || value == JSONObject.NULL) return "null";
            if (value instanceof JSONObject) {
                JSONObject object = (JSONObject)value; List<String> keys = new ArrayList<>(); for (Iterator<String> i = object.keys(); i.hasNext();) keys.add(i.next()); Collections.sort(keys);
                StringBuilder out = new StringBuilder("{"); for (String key : keys) { if (out.length() > 1) out.append(','); out.append(JSONObject.quote(key)).append(':').append(canonical(object.get(key))); } return out.append('}').toString();
            }
            if (value instanceof JSONArray) { JSONArray array = (JSONArray)value; StringBuilder out = new StringBuilder("["); for (int i = 0; i < array.length(); i++) { if (i > 0) out.append(','); out.append(canonical(array.get(i))); } return out.append(']').toString(); }
            if (value instanceof Number) return JSONObject.numberToString((Number)value);
            if (value instanceof Boolean) return value.toString();
            return JSONObject.quote(String.valueOf(value));
        } catch (JSONException e) { throw new IOException(e); }
    }
}
