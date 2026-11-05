package com.keplerslicer.app;

import android.content.Context;
import android.content.SharedPreferences;
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

/** Discovery index generated from upstream manifests; compatibility remains an slicing engine decision. */
public final class PrintProfileCatalog {
    public static final String PROCESS = "process", FILAMENT = "filament";
    private static final String PREFS = StorageNames.PRINT_PROFILES;
    private PrintProfileCatalog() { }

    public static final class Profile {
        public final String kind, vendor, name, path;
        public final List<String> explicitPrinters;
        Profile(String kind, String vendor, String name, String path, List<String> printers) {
            this.kind = kind; this.vendor = vendor; this.name = name; this.path = path;
            this.explicitPrinters = Collections.unmodifiableList(printers);
        }
    }
    private static void requireKind(String kind) {
        if (!PROCESS.equals(kind) && !FILAMENT.equals(kind)) throw new IllegalArgumentException("Unknown profile type");
    }

    /** Call from a worker thread. Includes only instantiated profiles referenced by upstream manifests. */
    public static List<Profile> load(Context context, String kind) throws IOException {
        requireKind(kind);
        try (InputStream in = context.getAssets().open("kepler_print_profile_catalog.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] bytes = new byte[8192]; int n, size = 0;
            while ((n = in.read(bytes)) != -1) {
                size += n; if (size > 8 * 1024 * 1024) throw new IOException(L.t("Каталог слишком большой"));
                out.write(bytes, 0, n);
            }
            JSONObject root = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            if (!PrinterProfileCatalog.UPSTREAM_REVISION.equals(root.optString("revision"))) throw new IOException(L.t("Версии профилей не совпадают"));
            JSONArray rows = root.getJSONArray("profiles"); List<Profile> result = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (!kind.equals(row.getString("kind"))) continue;
                JSONArray names = row.getJSONArray("printers"); List<String> printers = new ArrayList<>(names.length());
                for (int j = 0; j < names.length(); j++) printers.add(names.getString(j));
                result.add(new Profile(kind, row.getString("vendor"), row.getString("name"), row.getString("path"), printers));
            }
            return Collections.unmodifiableList(result);
        } catch (JSONException e) { throw new IOException(L.t("Не удалось прочитать профили"), e); }
    }

    /** Bind selection to exact printer profile so a changed nozzle cannot silently reuse stale settings. */
    public static void saveSelection(Context context, Profile profile, PrinterProfileCatalog.Profile printer) throws IOException {
        if (profile == null || printer == null || printer.imported || printer.path.isEmpty()) throw new IOException(L.t("Сначала выберите системный профиль принтера"));
        requireKind(profile.kind);
        SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String[] keys={profile.kind+"_vendor",profile.kind+"_name",profile.kind+"_path",profile.kind+"_printer"};
        java.util.Map<String,?> before=prefs.getAll();
        SharedPreferences.Editor edit=prefs.edit();
        edit.putString(keys[0],profile.vendor).putString(keys[1],profile.name).putString(keys[2],profile.path).putString(keys[3],printer.path);
        if(!edit.commit()){
            try{SettingsState.restoreKeys(prefs,before,keys);}catch(IOException restore){IOException failure=new IOException(L.t("Не удалось сохранить профиль; восстановление прежнего выбора не подтверждено"));failure.addSuppressed(restore);throw failure;}
            throw new IOException(L.t("Не удалось сохранить профиль"));
        }
    }

    public static Profile loadSelection(Context context, String kind) {
        requireKind(kind);
        PrinterProfileCatalog.Profile printer = PrinterProfileCatalog.loadSelection(context);
        if (printer == null || printer.imported) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String name = prefs.getString(kind + "_name", ""), vendor = prefs.getString(kind + "_vendor", ""),
                path = prefs.getString(kind + "_path", ""), selectedPrinter = prefs.getString(kind + "_printer", "");
        if (name == null || name.isEmpty() || vendor == null || vendor.isEmpty() || path == null || path.isEmpty()
                || !printer.path.equals(selectedPrinter)) return null;
        return new Profile(kind, vendor, name, path, Collections.emptyList());
    }
}
