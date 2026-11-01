package com.orcamobile.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** UI model for metadata produced by the upstream ConfigOptionDef bridge. */
final class ProcessOption {
    static final class Choice {
        final String value, label;
        Choice(String value, String label) { this.value = value; this.label = label; }
    }
    final String key, type, label, category, section, tooltip, unit;
    final boolean editable;
    final Object value, defaultValue;
    final Double min, max, maxLiteral;
    final List<Choice> choices;
    private ProcessOption(JSONObject row) throws JSONException {
        key = row.getString("key"); type = row.getString("type"); label = row.getString("label");
        category = row.getString("category"); section = row.getString("section"); tooltip = row.getString("tooltip"); unit = row.getString("unit"); editable = row.getBoolean("editable");
        value = row.get("value"); defaultValue = row.get("default");
        min = row.isNull("min") ? null : row.getDouble("min"); max = row.isNull("max") ? null : row.getDouble("max"); maxLiteral = row.isNull("max_literal") ? null : row.getDouble("max_literal");
        ArrayList<Choice> list = new ArrayList<>(); JSONArray a = row.getJSONArray("choices");
        for (int i = 0; i < a.length(); i++) { JSONObject c = a.getJSONObject(i); list.add(new Choice(c.getString("value"), c.getString("label"))); }
        choices = Collections.unmodifiableList(list);
        if (!type.equals("float") && !type.equals("percent") && !type.equals("integer") && !type.equals("boolean") && !type.equals("enum") && !type.equals("float_or_percent") && !type.equals("string") && !type.equals("readonly"))
            throw new JSONException("Unsupported option type " + type);
    }
    static List<ProcessOption> parseMetadata(String source) throws JSONException {
        JSONObject document = new JSONObject(source);
        if (document.getInt("version") != 2 || !document.getString("scope").equals("process_options")) throw new JSONException("Unsupported metadata version");
        JSONArray a = document.getJSONArray("options"); if (a.length() > 4096) throw new JSONException("Too many options");
        Map<String, ProcessOption> result = new LinkedHashMap<>();
        for (int i = 0; i < a.length(); i++) {
            ProcessOption option = new ProcessOption(a.getJSONObject(i));
            if (result.put(option.key, option) != null) throw new JSONException("Duplicate option key");
        }
        return Collections.unmodifiableList(new ArrayList<>(result.values()));
    }
    /** Parse a field/switch/enum input into a correctly typed JSON value. Native validates again. */
    Object parseInput(String text) {
        if (!editable) throw new IllegalArgumentException("Этот параметр доступен только для просмотра.");
        if (type.equals("string")) {
            if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4096 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\0') >= 0)
                throw new IllegalArgumentException("Текст слишком длинный или содержит недопустимые символы.");
            return text;
        }
        String input = text.trim();
        if (type.equals("boolean")) {
            if (input.equals("true")) return Boolean.TRUE;
            if (input.equals("false")) return Boolean.FALSE;
            throw new IllegalArgumentException("Ожидается включено или выключено.");
        }
        if (type.equals("enum")) {
            for (Choice choice : choices) if (choice.value.equals(input)) return input;
            throw new IllegalArgumentException("Выберите допустимое значение.");
        }
        boolean suffix = input.endsWith("%");
        if ((type.equals("percent") || type.equals("float_or_percent")) && suffix) input = input.substring(0, input.length() - 1).trim();
        input = input.replace(',', '.');
        try {
            if (type.equals("integer")) {
                long number = Long.parseLong(input); checkRange(number); return number;
            }
            double number = Double.parseDouble(input); checkRange(number);
            if (type.equals("float_or_percent") && !suffix && maxLiteral != null && number > maxLiteral)
                throw new IllegalArgumentException("Абсолютное значение превышает допустимый предел; для процента добавьте %.");
            return type.equals("float_or_percent") ? Double.toString(number) + (suffix ? "%" : "") : number;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Введите корректное число.", e); }
    }
    private void checkRange(double number) {
        if (!Double.isFinite(number) || (min != null && number < min) || (max != null && number > max))
            throw new IllegalArgumentException("Значение вне допустимого диапазона.");
    }
    static String encodeOverrides(Map<String, Object> changes) throws JSONException {
        JSONObject object = new JSONObject();
        for (Map.Entry<String, Object> e : changes.entrySet()) object.put(e.getKey(), e.getValue());
        return object.toString();
    }
}
