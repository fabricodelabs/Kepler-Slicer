package com.keplerslicer.app;

import android.content.Context;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Localises the engine's G-code time summary ("1d 2h 8m 56s") into the app language. */
final class PrintTime {
    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([dhms])");
    private PrintTime() {}

    static String localize(Context c, String engineTime) {
        if (engineTime == null) return null;
        String t = engineTime.trim();
        if (!t.matches("(\\d+\\s*[dhms]\\s*)+")) return t; // unknown format: show as written
        StringBuilder out = new StringBuilder(); Matcher m = PART.matcher(t);
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            int id = m.group(2).equals("d") ? R.string.kv3_time_d : m.group(2).equals("h") ? R.string.kv3_time_h : m.group(2).equals("m") ? R.string.kv3_time_m : R.string.kv3_time_s;
            if (out.length() > 0) out.append(' ');
            out.append(c.getString(id, n));
        }
        return out.toString();
    }
}
