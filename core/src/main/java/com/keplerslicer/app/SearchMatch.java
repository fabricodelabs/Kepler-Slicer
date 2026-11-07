package com.keplerslicer.app;

import java.util.Locale;

/** Search used by the printer, profile and settings lists: every word of the query must appear, in any order. */
public final class SearchMatch {
    private SearchMatch() {}

    static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replace('ё', 'е').replace('_', ' ').replace('-', ' ');
    }

    public static boolean matches(String query, String... fields) {
        String q = normalize(query).trim();
        if (q.isEmpty()) return true;
        StringBuilder hay = new StringBuilder();
        for (String f : fields) hay.append(' ').append(normalize(f));
        String h = hay.toString();
        for (String word : q.split("\s+")) if (!word.isEmpty() && !h.contains(word)) return false;
        return true;
    }
}
