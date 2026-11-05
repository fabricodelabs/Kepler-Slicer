package com.keplerslicer.app;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * Messages of protocol, model and storage helpers. They are written once in the source (mostly Russian, some
 * English) so the same classes also run in the host tests without Android; the app installs the tables from
 * assets/kepler_messages.json at start and the current language picks one per call. Without tables the source
 * text is returned unchanged.
 */
public final class L {
    private static volatile Map<String, String> toEnglish = Collections.emptyMap(), toRussian = Collections.emptyMap();
    private L() {}
    static void install(Map<String, String> english, Map<String, String> russian) {
        toEnglish = english == null ? Collections.<String, String>emptyMap() : Collections.unmodifiableMap(english);
        toRussian = russian == null ? Collections.<String, String>emptyMap() : Collections.unmodifiableMap(russian);
    }
    public static String t(String source) {
        String v = ("ru".equals(Locale.getDefault().getLanguage()) ? toRussian : toEnglish).get(source);
        return v == null ? source : v;
    }
}
