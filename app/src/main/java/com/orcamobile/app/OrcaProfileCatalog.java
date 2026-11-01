package com.orcamobile.app;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Read-only catalog of upstream machine profiles. It does not resolve inheritance or slice. */
public final class OrcaProfileCatalog {
    public static final String UPSTREAM_REVISION = "6d2869c430284714f0a22570c38d3b359965a802";
    private static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    private static final String PREFERENCES = "orca_profile_selection";

    private OrcaProfileCatalog() { }

    public static final class Profile {
        public final String name, vendor, path, model, variant;
        public final boolean imported;

        private Profile(String name, String vendor, String path, String model, String variant, boolean imported) {
            this.name = name; this.vendor = vendor; this.path = path;
            this.model = model; this.variant = variant; this.imported = imported;
        }

        public String displayName() {
            return imported ? name + " · импортирован" : vendor + " · " + name;
        }
    }

    /** Load on a worker thread; the bundled catalog is generated from upstream machine JSON files. */
    public static List<Profile> load(Context context) throws IOException {
        try (InputStream in = context.getAssets().open("orca_machine_catalog.json")) {
            JSONObject root = new JSONObject(readUtf8(in));
            if (!UPSTREAM_REVISION.equals(root.optString("revision"))) throw new IOException("Неверная версия каталога профилей");
            JSONArray profiles = root.getJSONArray("profiles");
            List<Profile> result = new ArrayList<>(profiles.length());
            for (int i = 0; i < profiles.length(); i++) {
                JSONObject p = profiles.getJSONObject(i);
                result.add(new Profile(p.getString("name"), p.getString("vendor"), p.getString("path"),
                        p.optString("model", ""), p.optString("variant", ""), false));
            }
            return Collections.unmodifiableList(result);
        } catch (JSONException e) { throw new IOException("Не удалось прочитать каталог профилей", e); }
    }

    /** Read the original JSON unchanged. Parent profiles are not merged. */
    public static String readOriginal(Context context, Profile profile) throws IOException {
        if (profile.imported || profile.path.isEmpty()) throw new IOException("Для импортированного профиля нет записи в каталоге");
        try (ZipInputStream zip = new ZipInputStream(context.getAssets().open("orca_profiles.zip"))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().equals(profile.path)) return readUtf8(zip);
            }
        }
        throw new IOException("Профиль не найден в архиве");
    }

    /** Inspect a user-selected machine JSON without treating it as a fully resolved print configuration. */
    public static Profile inspectImported(Context context, Uri uri) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Файл недоступен");
            JSONObject obj = new JSONObject(readUtf8(in));
            String name = obj.optString("name", "").trim();
            if (!"machine".equals(obj.optString("type")) || name.isEmpty()) {
                throw new IOException("Ожидался JSON-профиль принтера OrcaSlicer с полями type=machine и name");
            }
            return new Profile(name, "", "", obj.optString("printer_model", ""), obj.optString("printer_variant", ""), true);
        } catch (JSONException e) { throw new IOException("Неверный JSON профиля", e); }
    }

    /** Save the visible selection only; imported file content is not retained or applied to slicing. */
    public static void saveSelection(Context context, Profile profile) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString("name", profile.name).putString("vendor", profile.vendor).putString("path", profile.path)
                .putString("model", profile.model).putString("variant", profile.variant)
                .putBoolean("imported", profile.imported).apply();
    }

    public static Profile loadSelection(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String name = prefs.getString("name", "");
        if (name == null || name.isEmpty()) return null;
        return new Profile(name, prefs.getString("vendor", ""), prefs.getString("path", ""),
                prefs.getString("model", ""), prefs.getString("variant", ""), prefs.getBoolean("imported", false));
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int n, total = 0;
        while ((n = input.read(buffer)) != -1) {
            total += n;
            if (total > MAX_JSON_BYTES) throw new IOException("Файл профиля слишком большой");
            out.write(buffer, 0, n);
        }
        String s = new String(out.toByteArray(), StandardCharsets.UTF_8);
        return s.startsWith("\uFEFF") ? s.substring(1) : s;
    }
}
