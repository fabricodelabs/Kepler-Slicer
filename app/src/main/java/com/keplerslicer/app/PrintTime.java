package com.keplerslicer.app;

import android.content.Context;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Localises the engine's G-code time summary ("1d 2h 8m 56s") into the app language. */
final class PrintTime {
    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([dhms])");
    private static final String[] SUMMARY = {"estimated printing time", "total estimated time"};
    private PrintTime() {}

    /**
     * The time from an engine summary comment (lower-cased line), or null. "; estimated printing time (normal mode) =
     * 1h 3m 2s" and Bambu's "; model printing time: …; total estimated time: 2h 13m 31s" count; settings in the
     * config block do not, e.g. Flashforge's "; file_start_gcode = ; estimated printing time … = {print_time_day}d …".
     */
    static String summaryTime(String line) {
        for (String phrase : SUMMARY) {
            int i = line.indexOf(phrase);
            if (i < 0 || line.substring(0, i).contains("=")) continue;
            String rest = line.substring(i + phrase.length());
            int eq = rest.indexOf('='), k = eq >= 0 ? eq : rest.indexOf(':');
            if (k < 0) continue;
            String v = rest.substring(k + 1).trim(); int semi = v.indexOf(';'); if (semi >= 0) v = v.substring(0, semi).trim();
            if (v.matches("(\\d+\\s*[dhms]\\s*)+")) return v;
        }
        return null;
    }

    static String localize(Context c, String engineTime) {
        if (engineTime == null) return null;
        String t = engineTime.trim();
        if (!t.matches("(\\d+\\s*[dhms]\\s*)+")) return t; // unknown format: show as written
        StringBuilder out = new StringBuilder(); Matcher m = PART.matcher(t);
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n == 0 && !(out.length() == 0 && m.hitEnd())) continue; // "0d 1h 3m 0s" -> "1 h 3 m"
            int id = m.group(2).equals("d") ? R.string.kv3_time_d : m.group(2).equals("h") ? R.string.kv3_time_h : m.group(2).equals("m") ? R.string.kv3_time_m : R.string.kv3_time_s;
            if (out.length() > 0) out.append(' ');
            out.append(c.getString(id, n));
        }
        return out.toString();
    }
}
