package com.keplerslicer.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** AMS tray contents read from a Bambu Lab report (first AMS unit, four trays), remembered on the device. */
final class AmsTrays {
    static final class Tray {
        final String type; final int color; final boolean loaded, active;
        Tray(String type, int color, boolean loaded, boolean active) { this.type = type; this.color = color; this.loaded = loaded; this.active = active; }
    }
    private static final String PREFS = "kepler_v3_ui", KEY = "ams_trays";
    private AmsTrays() {}

    /** Parses print.ams from a Bambu report; null when the printer has no AMS. */
    static Tray[] parse(JSONObject print) {
        JSONObject ams = print == null ? null : print.optJSONObject("ams");
        JSONArray units = ams == null ? null : ams.optJSONArray("ams");
        JSONObject unit = units == null || units.length() == 0 ? null : units.optJSONObject(0);
        JSONArray trays = unit == null ? null : unit.optJSONArray("tray");
        if (trays == null) return null;
        int now = -1; try { now = Integer.parseInt(ams.optString("tray_now", "-1")); } catch (NumberFormatException ignored) { }
        Tray[] out = new Tray[4];
        for (int i = 0; i < 4; i++) {
            JSONObject t = null;
            for (int k = 0; k < trays.length(); k++) { JSONObject c = trays.optJSONObject(k); if (c != null && c.optString("id", String.valueOf(k)).equals(String.valueOf(i))) { t = c; break; } }
            String type = t == null ? "" : t.optString("tray_type", "");
            out[i] = new Tray(type, color(t == null ? "" : t.optString("tray_color", "")), !type.isEmpty(), now == i);
        }
        return out;
    }

    static void save(Context c, Tray[] trays) {
        if (trays == null) return;
        JSONArray a = new JSONArray();
        try { for (Tray t : trays) a.put(new JSONObject().put("type", t.type).put("color", t.color).put("loaded", t.loaded).put("active", t.active)); } catch (JSONException e) { return; }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply();
    }

    static Tray[] load(Context c) {
        try {
            JSONArray a = new JSONArray(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"));
            if (a.length() != 4) return null;
            Tray[] out = new Tray[4];
            for (int i = 0; i < 4; i++) { JSONObject t = a.getJSONObject(i); out[i] = new Tray(t.optString("type"), t.optInt("color"), t.optBoolean("loaded"), t.optBoolean("active")); }
            return out;
        } catch (JSONException e) { return null; }
    }

    /** "RRGGBBAA" (Bambu) to ARGB; unreadable colours become light grey. */
    private static int color(String rrggbbaa) {
        try {
            if (rrggbbaa.length() >= 6) {
                int rgb = (int) Long.parseLong(rrggbbaa.substring(0, 6), 16);
                int alpha = rrggbbaa.length() >= 8 ? Integer.parseInt(rrggbbaa.substring(6, 8), 16) : 255;
                return (Math.max(alpha, 0x40) << 24) | rgb;
            }
        } catch (NumberFormatException ignored) { }
        return 0xFFE9EEF5;
    }
}
