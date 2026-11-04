package com.keplerslicer.app;

import java.util.regex.Pattern;

/** Display-only rewording of text that comes from the bundled engine and its upstream data (messages, tooltips). */
public final class EngineText {
    private static final Pattern SLICER = Pattern.compile("Orca ?Slicer");
    private static final Pattern WORD = Pattern.compile("\bOrca\b");
    private EngineText() {}

    /** Never use the result as a key or a value that is saved or sent; it is for the screen only. */
    public static String clean(String s) {
        if (s == null || s.indexOf("Orca") < 0) return s;
        s = s.replace("OrcaFilamentLibrary", "Kepler Filament Library");
        s = SLICER.matcher(s).replaceAll("Kepler Slicer");
        return WORD.matcher(s).replaceAll("Kepler");
    }

    public static CharSequence clean(CharSequence s) { return s instanceof String ? clean((String) s) : s; }
}
