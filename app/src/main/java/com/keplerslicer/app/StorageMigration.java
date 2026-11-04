package com.keplerslicer.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.util.Map;
import java.util.Set;

/** One-time move of data saved by earlier builds under their old names (the only place those names remain). */
final class StorageMigration {
    private static final String[][] PREFS = {
            {"orca_print_profiles", StorageNames.PRINT_PROFILES},
            {"orca_profile_selection", StorageNames.PROFILE_SELECTION},
    };

    private StorageMigration() {}

    /** The old copy is cleared only after the new one is committed. */
    static void run(Context c) {
        for (String[] p : PREFS) movePrefs(c, p[0], p[1]);
        // The cloud session lives in the encrypted network secrets under a key name.
        try {
            String old = NetworkSecrets.get(c, "orca_cloud_token");
            if (!old.isEmpty() && NetworkSecrets.get(c, StorageNames.CLOUD_TOKEN).isEmpty()) {
                NetworkSecrets.put(c, StorageNames.CLOUD_TOKEN, old);
                NetworkSecrets.remove(c, "orca_cloud_token");
            }
        } catch (java.io.IOException ignored) { /* keep the old entry; the user can sign in again */ }
        File oldDir = new File(c.getFilesDir(), "orca-cloud-presets"), newDir = new File(c.getFilesDir(), StorageNames.CLOUD_PROFILES_DIR);
        if (oldDir.isDirectory() && !newDir.exists()) oldDir.renameTo(newDir);
        // Regenerable caches written by earlier builds.
        File[] cached = c.getCacheDir().listFiles();
        if (cached != null) for (File f : cached) if (f.getName().startsWith("orca-") || f.getName().startsWith(".orca-")) delete(f);
    }

    @SuppressWarnings("unchecked")
    private static void movePrefs(Context c, String from, String to) {
        SharedPreferences old = c.getSharedPreferences(from, 0), now = c.getSharedPreferences(to, 0);
        Map<String, ?> all = old.getAll();
        if (all.isEmpty() || !now.getAll().isEmpty()) { if (all.isEmpty()) c.deleteSharedPreferences(from); return; }
        SharedPreferences.Editor e = now.edit();
        for (Map.Entry<String, ?> en : all.entrySet()) {
            Object v = en.getValue(); String k = en.getKey();
            if (v instanceof String) e.putString(k, (String) v);
            else if (v instanceof Boolean) e.putBoolean(k, (Boolean) v);
            else if (v instanceof Integer) e.putInt(k, (Integer) v);
            else if (v instanceof Long) e.putLong(k, (Long) v);
            else if (v instanceof Float) e.putFloat(k, (Float) v);
            else if (v instanceof Set) e.putStringSet(k, (Set<String>) v);
        }
        if (e.commit() && old.edit().clear().commit()) c.deleteSharedPreferences(from);
    }

    private static void delete(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) delete(k);
        f.delete();
    }
}
