package com.keplerslicer.app.settings;
import com.keplerslicer.app.L;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Immutable metadata generated from the pinned upstream sources; does not validate through the engine. */
public final class SettingsSchema {
    private static volatile SettingsSchema cached; private static volatile String cachedLanguage;
    private final Map<String, List<Option>> sections;

    private SettingsSchema(JSONObject document) throws JSONException, IOException { this(document, null); }
    private SettingsSchema(JSONObject document, JSONObject tr) throws JSONException, IOException {
        if (document.optInt("schema_version", -1) != 1) throw new IOException("Unsupported settings schema version");
        JSONObject source = document.getJSONObject("sections");
        Map<String, List<Option>> result = new LinkedHashMap<>();
        for (String kind : new String[]{"process", "filament", "printer"}) {
            JSONArray rows = source.getJSONArray(kind);
            List<Option> options = new ArrayList<>(rows.length());
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (int i = 0; i < rows.length(); i++) {
                Option option = new Option(rows.getJSONObject(i), tr);
                if (option.key.isEmpty() || !seen.add(option.key)) throw new IOException("Duplicate or empty schema key: " + option.key);
                options.add(option);
            }
            result.put(kind, Collections.unmodifiableList(options));
        }
        sections = Collections.unmodifiableMap(result);
    }

    private static volatile JSONObject translationCache; private static volatile String translationLocale;
    /** Russian UI strings for engine labels (assets/kepler_ui_ru.json), or null when the app language is not Russian. */
    static JSONObject translations(Context context) {
        String lang = java.util.Locale.getDefault().getLanguage(); // GeneralPreferences applies the app language as the default locale
        if (!"ru".equals(lang)) return null;
        JSONObject cachedTr = translationCache; if (cachedTr != null && lang.equals(translationLocale)) return cachedTr;
        try (InputStream in = context.getApplicationContext().getAssets().open(UI_RU); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[16384]; for (int n; (n = in.read(b)) != -1;) out.write(b, 0, n);
            cachedTr = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8)); translationCache = cachedTr; translationLocale = lang; return cachedTr;
        } catch (Exception e) { return null; }
    }
    static final String UI_RU = "kepler_ui_ru.json";
    private static String tr(JSONObject tr, String s) { if (tr == null || s == null || s.isEmpty()) return com.keplerslicer.app.EngineText.clean(s); String v = tr.optString(s, ""); if (v.isEmpty()) v = tr.optString(s.trim(), ""); return com.keplerslicer.app.EngineText.clean(v.isEmpty() ? s : v); }
    /** Display text for a page or other engine string in the current app language (page ids stay English). */
    public static String display(Context context, String s) { return tr(translations(context), s); }

    /** Call on a worker thread; the cached result and its option collections are immutable. */
    public static SettingsSchema load(Context context) throws IOException {
        String language = java.util.Locale.getDefault().getLanguage();
        SettingsSchema result = language.equals(cachedLanguage) ? cached : null;
        if (result == null) synchronized (SettingsSchema.class) {
            result = language.equals(cachedLanguage) ? cached : null;
            if (result == null) {
                try (InputStream input = context.getApplicationContext().getAssets().open("kepler_settings_runtime.json");
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[16384];
                    for (int n; (n = input.read(buffer)) != -1;) output.write(buffer, 0, n);
                    result = new SettingsSchema(new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8)), translations(context));
                    cached = result; cachedLanguage = language;
                } catch (JSONException ex) { throw new IOException("Invalid settings schema", ex); }
            }
        }
        return result;
    }

    public List<Option> options(String kind) {
        List<Option> value = sections.get(kind);
        if (value == null) throw new IllegalArgumentException("Unknown settings section: " + kind);
        return value;
    }

    /** Inventory of metadata, not a claim that every key was rendered or saved by a screen. */
    public JSONObject uiInventory() {
        try {
            JSONObject doc = new JSONObject(), groups = new JSONObject();
            for (Map.Entry<String,List<Option>> entry : sections.entrySet()) {
                JSONArray keys = new JSONArray();
                for (Option option : entry.getValue()) keys.put(option.key);
                groups.put(entry.getKey(), keys);
            }
            doc.put("scope", "schema_model_inventory_not_rendered_ui");
            doc.put("sections", groups);
            return doc;
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }

    private static String string(JSONObject obj, String name) {
        return obj.isNull(name) ? null : obj.optString(name, null);
    }
    private static Double number(JSONObject obj, String name) {
        if (obj.isNull(name)) return null;
        Object value = obj.opt(name);
        if (!(value instanceof Number)) return null;
        double result = ((Number)value).doubleValue();
        return Double.isFinite(result) ? result : null;
    }
    private static Object freeze(Object value) throws JSONException {
        if (value == null || value == JSONObject.NULL) return null;
        if (value instanceof JSONArray) {
            JSONArray src = (JSONArray)value; List<Object> out = new ArrayList<>();
            for (int i = 0; i < src.length(); i++) out.add(freeze(src.get(i)));
            return Collections.unmodifiableList(out);
        }
        if (value instanceof JSONObject) {
            JSONObject src = (JSONObject)value; Map<String,Object> out = new LinkedHashMap<>();
            java.util.Iterator<String> keys = src.keys();
            while (keys.hasNext()) { String key = keys.next(); out.put(key, freeze(src.get(key))); }
            return Collections.unmodifiableMap(out);
        }
        return value;
    }
    private static Object thaw(Object value) throws JSONException {
        if (value == null) return JSONObject.NULL;
        if (value instanceof List) {
            JSONArray array = new JSONArray();
            for (Object item : (List<?>)value) array.put(thaw(item));
            return array;
        }
        if (value instanceof Map) {
            JSONObject object = new JSONObject();
            for (Map.Entry<?,?> e : ((Map<?,?>)value).entrySet()) object.put((String)e.getKey(), thaw(e.getValue()));
            return object;
        }
        return value;
    }

    public static final class Option {
        public final String key, baseKey, label, tooltip, type, units, page, group, mode;
        public final Integer index;
        public final Double min, max;
        public final List<String> enumValues, enumLabels;
        public final List<String> sourceReferences;
        public final boolean enumComplete, modeKnown, pageKnown, defaultResolved;
        public final Boolean nullable;
        /** Nested default lists/maps are immutable. Use defaultJsonValue() for a fresh JSON value. */
        public final Object defaultValue;

        private Option(JSONObject row, JSONObject tr) throws JSONException {
            key = row.getString("key"); baseKey = string(row,"base_key");
            index = row.isNull("index") ? null : row.getInt("index");
            label = tr(tr, string(row,"label")); tooltip = tr(tr, string(row,"tooltip"));
            type = string(row,"type"); units = string(row,"units");
            min = number(row,"min"); max = number(row,"max");
            String originalMode = string(row,"mode"); modeKnown = originalMode != null;
            mode = modeKnown ? originalMode : "comExpert";
            String originalPage = string(row,"page"); pageKnown = originalPage != null;
            page = pageKnown ? originalPage : "Other options";
            String originalGroup = string(row,"group"); group = tr(tr, originalGroup == null ? "Other options" : originalGroup);
            JSONObject enums = row.optJSONObject("enums");
            List<String> values = new ArrayList<>(), labels = new ArrayList<>();
            boolean complete = enums != null && enums.optBoolean("values_complete", false);
            if (enums != null) {
                JSONArray vs = enums.optJSONArray("values"), ls = enums.optJSONArray("labels");
                if (vs != null) for (int i=0; i<vs.length(); i++) {
                    if (vs.isNull(i)) { complete = false; continue; }
                    String val = vs.optString(i); values.add(val);
                    labels.add(tr(tr, ls != null && i < ls.length() && !ls.isNull(i) ? ls.optString(i) : val));
                }
            }
            enumValues = Collections.unmodifiableList(values); enumLabels = Collections.unmodifiableList(labels);
            enumComplete = complete; nullable = row.isNull("nullable") ? null : row.optBoolean("nullable");
            List<String> sources = new ArrayList<>();
            JSONObject source = row.optJSONObject("source");
            if (source != null) {
                JSONArray definitions = source.optJSONArray("definition");
                if (definitions != null) for (int i = 0; i < definitions.length(); i++) addSource(sources, definitions.optJSONObject(i));
                addSource(sources, source.optJSONObject("tab"));
            }
            sourceReferences = Collections.unmodifiableList(sources);
            JSONObject def = row.optJSONObject("default");
            defaultResolved = def != null && def.optBoolean("resolved", false);
            defaultValue = defaultResolved ? freeze(def.opt("value")) : null;
        }

        public Object defaultJsonValue() {
            try { return thaw(defaultValue); } catch (JSONException ex) { throw new IllegalStateException(ex); }
        }
        private static void addSource(List<String> result, JSONObject source) {
            if (source == null) return;
            String file = string(source, "file");
            if (file != null) result.add(file + (source.optInt("line", 0) > 0 ? ":" + source.optInt("line") : ""));
        }
        public String displayValue(Object value) {
            if (value == null || value == JSONObject.NULL) return "";
            // Per-extruder vectors are shown as plain values ("ABS", "200, 210"), not as JSON.
            if (value instanceof org.json.JSONArray) {
                org.json.JSONArray array = (org.json.JSONArray) value; StringBuilder out = new StringBuilder();
                for (int i = 0; i < array.length(); i++) {
                    Object item = array.opt(i);
                    if (item instanceof org.json.JSONArray || item instanceof JSONObject) return value.toString();
                    if (i > 0) out.append(", ");
                    out.append(displayValue(item));
                }
                return out.toString();
            }
            String raw = value.toString();
            int index = enumValues.indexOf(raw);
            return index >= 0 ? enumLabels.get(index) : raw;
        }
        public boolean isBoolean() { return "coBool".equals(type); }
        public boolean isVector() {
            if (type == null) return false;
            switch (type) {
                case "coStrings": case "coFloats": case "coInts": case "coBools":
                case "coPercents": case "coFloatsOrPercents": case "coEnums":
                case "coPoints": case "coPointsGroups": case "coFloatsGroups":
                case "coIntsGroups": return true;
                default: return false;
            }
        }
        public boolean booleanValue(Object value) {
            if (value instanceof Boolean) return (Boolean)value;
            String text = value == null ? "" : value.toString();
            if ("1".equals(text) || "true".equalsIgnoreCase(text)) return true;
            if ("0".equals(text) || "false".equalsIgnoreCase(text)) return false;
            throw new IllegalArgumentException(L.t("Булево значение не задано или не распознано"));
        }
        /** Returns a new JSON value, preserving the engine's string storage for ordinary scalar inputs. */
        public Object parseInput(String input, Object current) {
            if (input == null) throw new IllegalArgumentException(L.t("Введите значение"));
            String text = input.trim();
            if (isVector() || current instanceof JSONArray || current instanceof List) {
                Object value = StrictJson.parse(text);
                if (!(value instanceof JSONArray)) throw new IllegalArgumentException(L.t("Нужен JSON-массив"));
                return value;
            }
            if (isBoolean()) {
                boolean value = booleanValue(text);
                if (current instanceof Boolean) return value;
                if (current instanceof Number) return value ? 1 : 0;
                return value ? "1" : "0";
            }
            if ("coInt".equals(type) || "coFloat".equals(type) || "coPercent".equals(type) || "coFloatOrPercent".equals(type)) {
                boolean percent = text.endsWith("%");
                if (percent && !("coPercent".equals(type) || "coFloatOrPercent".equals(type))) throw new IllegalArgumentException(L.t("Проценты здесь не поддерживаются"));
                String raw = (percent ? text.substring(0,text.length()-1).trim() : text).replace(',', '.');
                if (!NUMBER.matcher(raw).matches()) throw new IllegalArgumentException(L.t("Введите корректное число"));
                double value;
                try { value = Double.parseDouble(raw); } catch (NumberFormatException ex) { throw new IllegalArgumentException(L.t("Введите корректное число")); }
                if (!Double.isFinite(value)) throw new IllegalArgumentException(L.t("Число должно быть конечным"));
                if ("coInt".equals(type) && !INTEGER.matcher(raw).matches()) throw new IllegalArgumentException(L.t("Нужно целое число"));
                if (min != null && value < min) throw new IllegalArgumentException(L.t("Значение меньше минимума: ") + min);
                if (max != null && value > max) throw new IllegalArgumentException(L.t("Значение больше максимума: ") + max);
                if (!percent && current instanceof String && ((String) current).trim().endsWith("%")) percent = true;
                if (current instanceof Number && !percent) {
                    if ("coInt".equals(type)) { try { return Long.parseLong(raw); } catch (NumberFormatException ex) { throw new IllegalArgumentException(L.t("Целое число вне допустимого диапазона")); } }
                    return value;
                }
                return raw + (percent ? "%" : "");
            }
            if (enumComplete && !enumValues.isEmpty() && !enumValues.contains(text)) throw new IllegalArgumentException(L.t("Значение отсутствует в списке допустимых значений"));
            if (type == null && (current instanceof JSONObject || current instanceof Boolean || current instanceof Number)) {
                Object parsed = StrictJson.parse(text);
                if (current instanceof JSONObject && !(parsed instanceof JSONObject) || current instanceof Boolean && !(parsed instanceof Boolean) || current instanceof Number && !(parsed instanceof Number)) throw new IllegalArgumentException(L.t("Тип JSON должен сохраняться"));
                return parsed;
            }
            return input;
        }
    }

    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private static final Pattern INTEGER = Pattern.compile("[+-]?[0-9]+");

    /** RFC 8259 reader; rejects comments, unquoted keys, trailing data and non-finite numbers. */
    private static final class StrictJson {
        final String text; int at;
        StrictJson(String text) { this.text = text; }
        static Object parse(String text) {
            try { StrictJson p = new StrictJson(text); Object value = p.value(0); p.ws(); if (p.at != text.length()) throw p.error(); return value; }
            catch (JSONException | NumberFormatException ex) { throw new IllegalArgumentException(L.t("Некорректный JSON"), ex); }
        }
        IllegalArgumentException error() { return new IllegalArgumentException(L.t("Некорректный JSON, позиция ") + at); }
        void ws() { while (at < text.length() && " \t\r\n".indexOf(text.charAt(at)) >= 0) at++; }
        boolean eat(char ch) { ws(); if (at < text.length() && text.charAt(at) == ch) { at++; return true; } return false; }
        Object value(int depth) throws JSONException {
            if (depth > 128) throw new IllegalArgumentException(L.t("Слишком глубокий JSON"));
            ws(); if (at >= text.length()) throw error(); char c = text.charAt(at);
            if (c == '"') return quoted();
            if (c == '[') { at++; JSONArray a = new JSONArray(); if (eat(']')) return a; do { a.put(value(depth+1)); } while (eat(',')); if (!eat(']')) throw error(); return a; }
            if (c == '{') { at++; JSONObject o = new JSONObject(); java.util.HashSet<String> keys = new java.util.HashSet<>(); if (eat('}')) return o; do { ws(); if (at >= text.length() || text.charAt(at) != '"') throw error(); String k=quoted(); if (!keys.add(k) || !eat(':')) throw error(); o.put(k,value(depth+1)); } while (eat(',')); if (!eat('}')) throw error(); return o; }
            if (text.startsWith("true",at)) { at+=4; return true; }
            if (text.startsWith("false",at)) { at+=5; return false; }
            if (text.startsWith("null",at)) { at+=4; return JSONObject.NULL; }
            java.util.regex.Matcher m = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?").matcher(text); m.region(at,text.length());
            if (!m.lookingAt()) throw error(); String raw=m.group(); at=m.end();
            if (!Double.isFinite(Double.parseDouble(raw))) throw error(); return new BigDecimal(raw);
        }
        String quoted() {
            if (text.charAt(at++) != '"') throw error(); StringBuilder out = new StringBuilder();
            while (at < text.length()) {
                char c=text.charAt(at++); if (c=='"') return out.toString(); if (c < 0x20) throw error();
                if (c!='\\') { out.append(c); continue; }
                if (at>=text.length()) throw error(); c=text.charAt(at++);
                switch(c) {
                    case '"': case '\\': case '/': out.append(c); break;
                    case 'b': out.append('\b'); break; case 'f': out.append('\f'); break;
                    case 'n': out.append('\n'); break; case 'r': out.append('\r'); break; case 't': out.append('\t'); break;
                    case 'u': if (at+4>text.length()) throw error(); try { out.append((char)Integer.parseInt(text.substring(at,at+4),16)); } catch(NumberFormatException ex) { throw error(); } at+=4; break;
                    default: throw error();
                }
            }
            throw error();
        }
    }
}
