package com.orcamobile.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Draft overrides, bound to one exact printer, process, filament and upstream revision. */
final class ProcessOverridesStore {
    private static final String PREFS = "kepler_process_overrides_v1", ID = "identity", JSON = "values";
    // All profile-selection writers and slice snapshot readers must use this same lock.
    private static final Object LOCK = new Object();
    interface SelectionWrite { void commit() throws IOException; }
    static final class Identity {
        final String fingerprint, printerName, processName, filamentName;
        final OrcaProfileCatalog.Profile printer;
        final OrcaProcessFilamentCatalog.Profile process, filament;
        private Identity(String fingerprint, OrcaProfileCatalog.Profile printer, OrcaProcessFilamentCatalog.Profile process,
                         OrcaProcessFilamentCatalog.Profile filament) {
            this.fingerprint = fingerprint; this.printer = printer; this.process = process; this.filament = filament;
            this.printerName = printer.name; this.processName = process.name; this.filamentName = filament.name;
        }
        static Identity capture(Context context) throws IOException {
            OrcaProfileCatalog.Profile p = OrcaProfileCatalog.loadSelection(context);
            OrcaProcessFilamentCatalog.Profile process = OrcaProcessFilamentCatalog.loadSelection(context, OrcaProcessFilamentCatalog.PROCESS);
            OrcaProcessFilamentCatalog.Profile filament = OrcaProcessFilamentCatalog.loadSelection(context, OrcaProcessFilamentCatalog.FILAMENT);
            if (p == null || p.imported || process == null || filament == null) throw new IOException("Сначала выберите системные профили принтера, процесса и материала.");
            String[] fields = {OrcaProfileCatalog.UPSTREAM_REVISION, p.vendor, p.name, p.path,
                    process.vendor, process.name, process.path, filament.vendor, filament.name, filament.path};
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                for (String field : fields) {
                    if (field == null || field.isEmpty()) throw new IOException("Выбран неполный профиль.");
                    byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                    int n = bytes.length;
                    digest.update((byte)(n >>> 24)); digest.update((byte)(n >>> 16)); digest.update((byte)(n >>> 8)); digest.update((byte)n); digest.update(bytes);
                }
                StringBuilder result = new StringBuilder(64);
                for (byte b : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
                return new Identity(result.toString(), p, process, filament);
            } catch (NoSuchAlgorithmException e) { throw new IOException("Не удалось проверить выбранные профили.", e); }
        }
    }

    /** Snapshot identity and overrides together; call under the same lock as selection writers. */
    static String load(Context context, Identity expected) throws IOException {
        synchronized (LOCK) {
            if (!Identity.capture(context).fingerprint.equals(expected.fingerprint)) throw new IOException("Выбор профилей изменился. Откройте параметры снова.");
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String id = p.getString(ID, "");
            if (!expected.fingerprint.equals(id)) {
                if (id != null && !id.isEmpty() && !p.edit().clear().commit()) throw new IOException("Не удалось сбросить параметры для другого профиля.");
                return "{}";
            }
            return validateStoredJson(p.getString(JSON, "{}"));
        }
    }
    static final class Snapshot {
        final Identity identity; final String overrides;
        Snapshot(Identity identity, String overrides) { this.identity = identity; this.overrides = overrides; }
    }
    static Snapshot snapshotForSlice(Context context) throws IOException {
        synchronized (LOCK) {
            Identity id = Identity.capture(context);
            return new Snapshot(id, load(context, id));
        }
    }
    /** Both locks are acquired in process→model order; selection writers use the same locks. */
    static String currentResultFingerprint(Context context) throws IOException {
        synchronized (LOCK) {
            Snapshot snapshot = snapshotForSlice(context);
            return ResultFingerprint.of(CurrentModelStore.selectionKey(context), snapshot.identity.fingerprint, snapshot.overrides);
        }
    }
    static void save(Context context, Identity expected, String overrides) throws IOException {
        synchronized (LOCK) {
            if (!Identity.capture(context).fingerprint.equals(expected.fingerprint)) throw new IOException("Выбор профилей изменился. Откройте параметры снова.");
            String validated = validateStoredJson(overrides);
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String oldId = p.getString(ID, ""), oldJson = p.getString(JSON, "{}");
            if (!p.edit().putString(ID, expected.fingerprint).putString(JSON, validated).commit()) {
                p.edit().putString(ID, oldId).putString(JSON, oldJson).apply();
                throw new IOException("Не удалось сохранить параметры процесса.");
            }
        }
    }
    /**
     * Integrate in EVERY printer/nozzle/process/filament selection path BEFORE
     * changing selection; call only when the selection actually changes.
     * Clearing first is crash-safe. A failed selection may discard the draft.
     * Result snapshots must also obtain this LOCK through load() while taking
     * the matching profile selection; otherwise a mixed snapshot is possible.
     */
    static void changeSelection(Context context, SelectionWrite operation) throws IOException {
        synchronized (LOCK) {
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (!p.edit().clear().commit()) throw new IOException("Не удалось сбросить параметры перед сменой профиля.");
            operation.commit();
        }
    }
    private static String validateStoredJson(String text) throws IOException {
        if (text == null || text.length() > 65536) throw new IOException("Параметры слишком велики.");
        try { return new JSONObject(text).toString(); }
        catch (JSONException e) { throw new IOException("Не удалось прочитать параметры процесса.", e); }
    }
}
