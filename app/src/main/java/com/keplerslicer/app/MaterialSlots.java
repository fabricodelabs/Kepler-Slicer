package com.keplerslicer.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Material slots 1..4 for multi-material printing. Slot 1 is the selected filament profile; slots 2..4 hold
 * extra system filament presets for the same printer profile. Colours are "#RRGGBB".
 */
final class MaterialSlots {
    static final int COUNT = 4;
    private static final String PREFS = "kepler_material_slots_v1";
    static final String[] PALETTE = {"#F2F2F2", "#1F1F1F", "#E53935", "#FB8C00", "#FDD835", "#43A047", "#1E88E5", "#8E24AA", "#808080", "#6D4C41"};

    static final class Slot {
        final String vendor, name, colour;
        Slot(String vendor, String name, String colour) { this.vendor = vendor; this.name = name; this.colour = colour; }
        boolean filled() { return !name.isEmpty(); }
    }
    private MaterialSlots() {}

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private static String scope(Context c) { PrinterProfileCatalog.Profile p = PrinterProfileCatalog.loadSelection(c); return p == null ? "" : p.path; }

    /** All four slots; slot 1 mirrors the selected filament and only stores its colour. */
    static Slot[] load(Context c) {
        Slot[] out = new Slot[COUNT];
        PrintProfileCatalog.Profile first = PrintProfileCatalog.loadSelection(c, PrintProfileCatalog.FILAMENT);
        JSONArray rows = rows(c);
        for (int i = 0; i < COUNT; i++) {
            JSONObject row = rows.optJSONObject(i);
            String colour = row == null ? "" : row.optString("colour", "");
            if (!validColour(colour)) colour = i == 0 ? "#F2F2F2" : PALETTE[(i + 1) % PALETTE.length];
            if (i == 0) out[i] = new Slot(first == null ? "" : first.vendor, first == null ? "" : first.name, colour);
            else out[i] = new Slot(row == null ? "" : row.optString("vendor", ""), row == null ? "" : row.optString("name", ""), colour);
        }
        return out;
    }

    static void set(Context c, int index, String vendor, String name) { put(c, index, vendor, name, null); }
    static void setColour(Context c, int index, String colour) { if (!validColour(colour)) throw new IllegalArgumentException("colour"); put(c, index, null, null, colour); }
    static void clear(Context c, int index) { put(c, index, "", "", null); }

    private static synchronized void put(Context c, int index, String vendor, String name, String colour) {
        if (index < 0 || index >= COUNT) throw new IllegalArgumentException("slot");
        JSONArray rows = rows(c);
        try {
            JSONObject row = rows.optJSONObject(index); if (row == null) row = new JSONObject();
            if (vendor != null && index > 0) row.put("vendor", vendor);
            if (name != null && index > 0) row.put("name", name);
            if (colour != null) row.put("colour", colour.toUpperCase(Locale.ROOT));
            while (rows.length() <= index) rows.put(new JSONObject());
            rows.put(index, row);
        } catch (JSONException e) { throw new IllegalStateException(e); }
        prefs(c).edit().putString("slots", rows.toString()).putString("printer", scope(c)).apply();
    }

    /** Stored rows, discarded when the printer profile changed (presets are printer-specific). */
    private static JSONArray rows(Context c) {
        SharedPreferences p = prefs(c);
        if (!scope(c).equals(p.getString("printer", ""))) return new JSONArray();
        try { return new JSONArray(p.getString("slots", "[]")); } catch (JSONException e) { return new JSONArray(); }
    }

    static boolean validColour(String c) { return c != null && c.matches("#[0-9A-Fa-f]{6}"); }
    static int argb(String colour) { return validColour(colour) ? 0xFF000000 | Integer.parseInt(colour.substring(1), 16) : 0xFFE9EEF5; }
    static String hex(int argb) { return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF); }

    /** Engine request: slot 1 colour plus the filled extra slots, numbered in order. Returns null if none is filled. */
    static JSONObject request(Slot[] slots, int[] map) throws JSONException {
        JSONObject r = new JSONObject().put("colour", slots[0].colour.toUpperCase(Locale.ROOT));
        JSONArray extra = new JSONArray();
        for (int i = 1; i < slots.length; i++) if (slots[i].filled()) { map[i] = extra.length() + 2; extra.put(new JSONObject().put("vendor", slots[i].vendor).put("name", slots[i].name).put("colour", slots[i].colour.toUpperCase(Locale.ROOT))); }
        map[0] = 1;
        return r.put("slots", extra);
    }
}
