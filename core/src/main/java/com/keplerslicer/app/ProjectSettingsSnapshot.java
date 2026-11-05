package com.keplerslicer.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Detached, checked native 3MF settings. Never replaces the original archive. */
final class ProjectSettingsSnapshot {
    final String archiveSha256, selectedPrinterId, embeddedPrinterId;
    final int selectedPlate;
    final boolean hasSettings, printerDiff;
    private final JSONObject document;

    private ProjectSettingsSnapshot(JSONObject source) throws IOException, JSONException {
        document = copy(source);
        if (integer(document, "schema", 1, 1) != 1 || !"kepler.project_settings".equals(string(document, "kind")))
            throw new IOException("Unknown project settings snapshot version");
        archiveSha256 = string(document, "archive_sha256");
        if (!archiveSha256.matches("[0-9a-f]{64}")) throw new IOException("Invalid project archive fingerprint");
        selectedPlate = integer(document, "selected_plate", -1, Integer.MAX_VALUE);
        hasSettings = bool(document, "has_settings");
        printerDiff = bool(document, "printer_diff");
        selectedPrinterId = string(document, "selected_printer_id");
        embeddedPrinterId = string(document, "embedded_printer_id");
        if (!bool(document, "source_bytes_retained") || bool(document, "geometry_only_changes_original"))
            throw new IOException("Project settings must retain the original archive");
        JSONArray differences = document.getJSONArray("printer_differences");
        Set<String> differentKeys = new HashSet<>();
        for (int i = 0; i < differences.length(); i++) {
            Object key = differences.get(i);
            if (!(key instanceof String) || ((String) key).isEmpty() || !differentKeys.add((String) key))
                throw new IOException("Invalid project printer differences");
        }
        if (printerDiff != (differences.length() > 0)) throw new IOException("Inconsistent printer comparison");
        validateConfig(document.getJSONObject("global"));
        JSONObject plate = document.getJSONObject("plate");
        if (integer(plate, "index", -1, Integer.MAX_VALUE) != selectedPlate) throw new IOException("Project plate mismatch");
        validateConfig(plate.getJSONObject("config"));
        validateConfig(document.getJSONObject("effective"));
        validateConfig(document.getJSONObject("effective_selected_printer"));
        JSONObject validation = document.getJSONObject("validation");
        validateAction(validation.getJSONObject("embedded_printer"));
        validateAction(validation.getJSONObject("selected_printer"));
        bool(validation, "can_load"); string(validation, "error");
        if (bool(validation, "can_load") != bool(validation.getJSONObject("embedded_printer"), "can_load")
                || !string(validation, "error").equals(string(validation.getJSONObject("embedded_printer"), "error")))
            throw new IOException("Inconsistent project import validation");
        JSONArray objects = document.getJSONArray("objects");
        for (int oi = 0; oi < objects.length(); oi++) {
            JSONObject object = objects.getJSONObject(oi);
            String objectId = archiveSha256 + ":object:" + oi;
            requireIdentity(object, oi, objectId); string(object, "name");
            validateConfig(object.getJSONObject("config"));
            JSONArray volumes = object.getJSONArray("volumes");
            for (int vi = 0; vi < volumes.length(); vi++) {
                JSONObject volume = volumes.getJSONObject(vi);
                requireIdentity(volume, vi, objectId + ":volume:" + vi);
                validateConfig(volume.getJSONObject("config"));
            }
            JSONArray instances = object.getJSONArray("instances");
            for (int ii = 0; ii < instances.length(); ii++) {
                JSONObject instance = instances.getJSONObject(ii);
                requireIdentity(instance, ii, objectId + ":instance:" + ii);
                nonnegativeInteger(instance.get("loaded_id"));
            }
            JSONArray ranges = object.getJSONArray("layer_ranges");
            for (int ri = 0; ri < ranges.length(); ri++) {
                JSONObject range = ranges.getJSONObject(ri);
                if (number(range.get("z_min")) > number(range.get("z_max"))) throw new IOException("Invalid project layer range");
                validateConfig(range.getJSONObject("config"));
            }
        }
        JSONArray presets = document.getJSONArray("embedded_presets");
        for (int i = 0; i < presets.length(); i++) {
            JSONObject preset = presets.getJSONObject(i);
            string(preset, "name"); string(preset, "type"); string(preset, "setting_id");
            string(preset, "filament_id"); string(preset, "base_id");
            validateConfig(preset.getJSONObject("config"));
        }
    }

    static ProjectSettingsSnapshot parse(byte[] utf8) throws IOException {
        if (utf8 == null) throw new IOException("Native project settings were not returned");
        try {
            String encoded = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(utf8)).toString();
            return new ProjectSettingsSnapshot(new JSONObject(encoded));
        } catch (JSONException | java.nio.charset.CharacterCodingException e) {
            throw new IOException("Invalid native project settings", e);
        }
    }

    static ProjectSettingsSnapshot parse(JSONObject source) throws IOException {
        if (source == null) throw new IOException("Project settings are missing");
        try { return new ProjectSettingsSnapshot(source); }
        catch (JSONException e) { throw new IOException("Invalid native project settings", e); }
    }

    boolean canLoad(boolean embeddedPrinter) {
        return document.optJSONObject("validation").optJSONObject(embeddedPrinter ? "embedded_printer" : "selected_printer").optBoolean("can_load", false);
    }
    String validationError(boolean embeddedPrinter) {
        return document.optJSONObject("validation").optJSONObject(embeddedPrinter ? "embedded_printer" : "selected_printer").optString("error");
    }
    JSONObject effectiveValues(boolean embeddedPrinter) throws IOException {
        return values(document.optJSONObject(embeddedPrinter ? "effective" : "effective_selected_printer"));
    }
    JSONObject globalValues() throws IOException { return values(document.optJSONObject("global")); }
    JSONObject plateValues() throws IOException { return values(document.optJSONObject("plate").optJSONObject("config")); }
    JSONObject objectValues(String objectId) throws IOException {
        JSONArray objects = document.optJSONArray("objects");
        for (int i = 0; i < objects.length(); i++) {
            JSONObject object = objects.optJSONObject(i);
            if (objectId.equals(object.optString("id"))) return values(object.optJSONObject("config"));
        }
        throw new IOException("Project object does not belong to this archive");
    }
    String objectId(int index) throws IOException {
        JSONArray objects = document.optJSONArray("objects");
        if (index < 0 || index >= objects.length()) throw new IOException("Project object index is outside the snapshot");
        return objects.optJSONObject(index).optString("id");
    }
    JSONObject json() throws IOException {
        try { return copy(document); }
        catch (JSONException e) { throw new IOException("Cannot copy native project settings", e); }
    }
    void requireArchive(String sha256, int plate) throws IOException {
        if (!archiveSha256.equals(sha256) || selectedPlate != plate)
            throw new IOException("Project settings no longer match the selected model and plate");
    }
    private static JSONObject values(JSONObject scope) throws IOException {
        try { return copy(scope.getJSONObject("preset_values")); }
        catch (JSONException e) { throw new IOException("Invalid project config scope", e); }
    }
    private static void validateConfig(JSONObject config) throws IOException, JSONException {
        JSONObject options = config.getJSONObject("options"), values = config.getJSONObject("preset_values");
        if (options.length() != values.length()) throw new IOException("Project config key count mismatch");
        Iterator<String> keys = options.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.isEmpty() || !values.has(key)) throw new IOException("Project config key mismatch");
            JSONObject option = options.getJSONObject(key);
            String type = string(option, "type");
            boolean nullable = bool(option, "nullable"); string(option, "serialized");
            validateTypedValue(type, option.get("value"), nullable);
            Object wire = option.get("preset_value");
            validateWireValue(wire, 0);
            if (!wire.toString().equals(values.get(key).toString())) throw new IOException("Project preset value mismatch");
        }
    }
    private static void validateTypedValue(String type, Object value, boolean nullable) throws IOException, JSONException {
        if (value == JSONObject.NULL) {
            if (!nullable) throw new IOException("Unexpected null project option");
            return;
        }
        switch (type) {
            case "coFloat": case "coPercent": number(value); return;
            case "coInt": signedInteger(value); return;
            case "coBool": if (!(value instanceof Boolean)) throw new IOException("Invalid project boolean"); return;
            case "coString": case "coEnum": if (!(value instanceof String)) throw new IOException("Invalid project string/enum"); return;
            case "coFloatOrPercent": fraction(value); return;
            case "coPoint": point(value, 2); return;
            case "coPoint3": point(value, 3); return;
            case "coFloats": vector(value, "coFloat", nullable); return;
            case "coInts": vector(value, "coInt", nullable); return;
            case "coPercents": vector(value, "coPercent", nullable); return;
            case "coStrings": vector(value, "coString", nullable); return;
            case "coBools": vector(value, "coBool", nullable); return;
            case "coEnums": vector(value, "coEnum", nullable); return;
            case "coFloatsOrPercents": vector(value, "coFloatOrPercent", nullable); return;
            case "coPoints": vector(value, "coPoint", nullable); return;
            case "coPointsGroups": vector(value, "coPoints", nullable); return;
            case "coIntsGroups": vector(value, "coInts", nullable); return;
            default: throw new IOException("Unknown native project option type: " + type);
        }
    }
    private static void validateWireValue(Object value, int depth) throws IOException, JSONException {
        if (value instanceof String) return;
        if (!(value instanceof JSONArray) || depth >= 2) throw new IOException("Invalid native preset JSON value");
        JSONArray array = (JSONArray) value;
        for (int i = 0; i < array.length(); i++) validateWireValue(array.get(i), depth + 1);
    }
    private static void vector(Object value, String itemType, boolean nullable) throws IOException, JSONException {
        if (!(value instanceof JSONArray)) throw new IOException("Invalid project option vector");
        JSONArray items = (JSONArray) value;
        for (int i = 0; i < items.length(); i++) validateTypedValue(itemType, items.get(i), nullable);
    }
    private static void fraction(Object value) throws IOException, JSONException {
        if (!(value instanceof JSONObject)) throw new IOException("Invalid project absolute/percent option");
        JSONObject fraction = (JSONObject) value;
        if (fraction.length() != 2) throw new IOException("Invalid project absolute/percent keys");
        number(fraction.get("value")); bool(fraction, "percent");
    }
    private static void point(Object value, int dimension) throws IOException, JSONException {
        if (!(value instanceof JSONArray) || ((JSONArray) value).length() != dimension) throw new IOException("Invalid project point");
        for (int i = 0; i < dimension; i++) number(((JSONArray) value).get(i));
    }
    private static void requireIdentity(JSONObject object, int index, String id) throws IOException, JSONException {
        if (integer(object, "index", 0, Integer.MAX_VALUE) != index || !id.equals(string(object, "id")))
            throw new IOException("Invalid project object identity");
    }
    private static void validateAction(JSONObject action) throws IOException, JSONException {
        boolean canLoad = bool(action, "can_load"); String error = string(action, "error");
        if (canLoad == !error.isEmpty()) throw new IOException("Invalid project import action");
    }
    private static double number(Object value) throws IOException {
        if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())) throw new IOException("Invalid project numeric value");
        return ((Number) value).doubleValue();
    }
    private static void signedInteger(Object value) throws IOException {
        double number = number(value);
        if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw new IOException("Invalid project integer");
    }
    private static void nonnegativeInteger(Object value) throws IOException {
        double number = number(value);
        if (number != Math.rint(number) || number < 0 || number > 9007199254740991d) throw new IOException("Invalid native project source identity");
    }
    private static int integer(JSONObject value, String key, int minimum, int maximum) throws IOException, JSONException {
        double number = number(value.get(key));
        if (number != Math.rint(number) || number < minimum || number > maximum) throw new IOException("Invalid project index/version");
        return (int) number;
    }
    private static String string(JSONObject object, String key) throws IOException, JSONException {
        Object value = object.get(key);
        if (!(value instanceof String)) throw new IOException("Invalid project text value");
        return (String) value;
    }
    private static boolean bool(JSONObject object, String key) throws IOException, JSONException {
        Object value = object.get(key);
        if (!(value instanceof Boolean)) throw new IOException("Invalid project boolean value");
        return (Boolean) value;
    }
    private static JSONObject copy(JSONObject object) throws JSONException { return new JSONObject(object.toString()); }
}
