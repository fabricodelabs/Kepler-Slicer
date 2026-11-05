package com.keplerslicer.app;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Group names for profile lists: printers and processes by printer maker, filaments by the plastic maker.
 * Filament profiles live in printer-vendor libraries, so the plastic maker comes from the profile name
 * ("Polymaker PolyLite PLA @Qidi Q2" belongs to Polymaker).
 */
final class ProfileGroups {
    private ProfileGroups() { }

    private static final Map<String, String> PRINTER = new HashMap<>();
    private static final Map<String, String> BRAND = new HashMap<>();
    private static final String[] MATERIALS = {"PLA", "PETG", "PET", "ABS", "ASA", "TPU", "TPE", "PA", "PA6", "PA12", "PAHT", "PC", "PVA", "HIPS", "PP", "PE", "PPS", "PEEK", "PEI", "PVB", "BVOH", "SBS", "CPE", "NYLON", "SILK", "WOOD", "SUPPORT", "PCTG", "PETG-CF", "PLA-CF", "PA-CF"};
    static {
        String[][] printers = {{"bbl", "Bambu Lab"}, {"orcafilamentlibrary", "Kepler Filament Library"}, {"qidi", "QIDI"}, {"creality", "Creality"},
            {"elegoo", "Elegoo"}, {"anycubic", "Anycubic"}, {"prusa", "Prusa"}, {"voron", "Voron"}, {"flashforge", "Flashforge"}, {"sovol", "Sovol"},
            {"snapmaker", "Snapmaker"}, {"artillery", "Artillery"}, {"ratrig", "RatRig"}, {"custom", "Custom"}};
        for (String[] p : printers) PRINTER.put(p[0], p[1]);
        String[][] brands = {{"bambu", "Bambu Lab"}, {"bbl", "Bambu Lab"}, {"generic", ""}, {"qidi", "QIDI"}, {"esun", "eSUN"}, {"sunlu", "SUNLU"},
            {"polymaker", "Polymaker"}, {"elegoo", "Elegoo"}, {"overture", "Overture"}, {"prusament", "Prusament"}, {"creality", "Creality"},
            {"anycubic", "Anycubic"}, {"aliz", "AliZ"}, {"fiberon", "Fiberon"}, {"panchroma", "Panchroma"}, {"flashforge", "Flashforge"},
            {"sovol", "Sovol"}, {"snapmaker", "Snapmaker"}, {"kingroon", "Kingroon"}, {"eryone", "Eryone"}, {"jayo", "JAYO"}, {"kexcelled", "Kexcelled"},
            {"devil", "Devil Design"}, {"fillamentum", "Fillamentum"}, {"fiberlogy", "Fiberlogy"}, {"colorfabb", "colorFabb"}, {"3dxtech", "3DXTech"}};
        for (String[] b : brands) BRAND.put(b[0], b[1]);
    }

    /** Printer maker for a printer or process profile vendor folder. */
    static String printerMaker(String vendor) {
        if (vendor == null || vendor.isEmpty()) return "";
        String known = PRINTER.get(vendor.toLowerCase(Locale.ROOT));
        return known != null ? known : EngineText.clean(vendor);
    }

    /** Plastic maker for a filament profile; "" for generic and unbranded profiles. */
    static String filamentBrand(String name) {
        if (name == null) return "";
        String base = name;
        int at = base.indexOf('@'); if (at >= 0) base = base.substring(0, at);
        base = base.trim();
        if (base.isEmpty()) return "";
        String first = base.split("[\\s_]+")[0];
        String key = first.toLowerCase(Locale.ROOT);
        String known = BRAND.get(key);
        if (known != null) return known;
        String upper = first.toUpperCase(Locale.ROOT);
        for (String m : MATERIALS) if (upper.equals(m) || upper.startsWith(m + "-") || upper.startsWith(m + "+")) return "";
        if (first.length() < 2 || Character.isDigit(first.charAt(0))) return "";
        return first;
    }

    /** Heading for unbranded filaments. */
    static String genericLabel() { return L.t("Универсальные"); }
}
