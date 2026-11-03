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
        final String fingerprint, legacyFingerprint, priorProfileFingerprint, printerName, processName, filamentName;
        final OrcaProfileCatalog.Profile printer;
        final OrcaProcessFilamentCatalog.Profile process, filament;
        final SettingsState.Snapshot profileSettings;
        final ObjectSettingsStore.Snapshot objectSettings;
        private Identity(String fingerprint, String legacyFingerprint, String priorProfileFingerprint, OrcaProfileCatalog.Profile printer, OrcaProcessFilamentCatalog.Profile process,
                         OrcaProcessFilamentCatalog.Profile filament, SettingsState.Snapshot profileSettings, ObjectSettingsStore.Snapshot objectSettings) {
            this.fingerprint = fingerprint; this.legacyFingerprint = legacyFingerprint; this.priorProfileFingerprint = priorProfileFingerprint; this.printer = printer; this.process = process; this.filament = filament;
            this.printerName = printer.name; this.processName = process.name; this.filamentName = filament.name;
            this.profileSettings=profileSettings;
            this.objectSettings=objectSettings;
        }
        static Identity capture(Context context) throws IOException {
            OrcaProfileCatalog.Profile p = OrcaProfileCatalog.loadSelection(context);
            OrcaProcessFilamentCatalog.Profile process = OrcaProcessFilamentCatalog.loadSelection(context, OrcaProcessFilamentCatalog.PROCESS);
            OrcaProcessFilamentCatalog.Profile filament = OrcaProcessFilamentCatalog.loadSelection(context, OrcaProcessFilamentCatalog.FILAMENT);
            if (p == null || p.imported || process == null || filament == null) throw new IOException("Сначала выберите системные профили принтера, процесса и материала.");
            SettingsState.Snapshot profileSettings=SettingsState.capture(context);
            requireMatchingOverlay(profileSettings,"printer",p.vendor,p.name,p.path);
            requireMatchingOverlay(profileSettings,"process",process.vendor,process.name,process.path);
            requireMatchingOverlay(profileSettings,"filament",filament.vendor,filament.name,filament.path);
            ObjectSettingsStore.Snapshot objectSettings=ObjectSettingsStore.snapshot(context);
            String[] triple={OrcaProfileCatalog.UPSTREAM_REVISION,p.vendor,p.name,p.path,
                    process.vendor,process.name,process.path,filament.vendor,filament.name,filament.path};
            String legacy=hash(triple);
            String[] prior=java.util.Arrays.copyOf(triple,triple.length+1);prior[triple.length]=profileSettings.fingerprint;
            String[] full=java.util.Arrays.copyOf(prior,prior.length+2);full[prior.length]=objectSettings.fingerprint;full[prior.length+1]="mixed-temperatures:"+context.getSharedPreferences("app_preferences",0).getBoolean("allow_mixed_temperatures",false);
            return new Identity(hash(full),legacy,hash(prior),p,process,filament,profileSettings,objectSettings);
        }
        private static void requireMatchingOverlay(SettingsState.Snapshot snapshot,String kind,String vendor,String name,String path)throws IOException {
            SettingsState.Overlay overlay=snapshot.overlays.get(kind);
            if(overlay==null||!name.equals(overlay.name)||!path.equals(overlay.path)||!vendor.equals(SettingsState.requireOverlayPath(kind,overlay.path)))
                throw new IOException("Профили изменились при подготовке снимка. Повторите операцию.");
        }
        private static String hash(String[] fields)throws IOException {
            try {
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                for(String field:fields){if(field==null||field.isEmpty())throw new IOException("Выбран неполный профиль.");byte[] bytes=field.getBytes(StandardCharsets.UTF_8);int n=bytes.length;digest.update((byte)(n>>>24));digest.update((byte)(n>>>16));digest.update((byte)(n>>>8));digest.update((byte)n);digest.update(bytes);}
                StringBuilder result=new StringBuilder(64);for(byte b:digest.digest())result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return result.toString();
            }catch(NoSuchAlgorithmException e){throw new IOException("Не удалось проверить выбранные профили.",e);}

        }
    }

    /** Snapshot identity and overrides together; call under the same lock as selection writers. */
    static String load(Context context, Identity expected) throws IOException {
        synchronized (LOCK) {
            if (!Identity.capture(context).fingerprint.equals(expected.fingerprint)) throw new IOException("Выбор профилей изменился. Откройте параметры снова.");
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String id = p.getString(ID, "");
            boolean same=expected.legacyFingerprint.equals(id);
            boolean migrate=expected.priorProfileFingerprint.equals(id)||expected.fingerprint.equals(id);
            if (!same&&!migrate) return "{}";
            String stored=validateStoredJson(p.getString(JSON, "{}"));
            if(migrate&&!p.edit().putString(ID,expected.legacyFingerprint).commit()){
                p.edit().putString(ID,id).apply();throw new IOException("Не удалось перенести сохранённые параметры процесса.");
            }
            return stored;
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
            for(int attempt=0;attempt<3;attempt++){
                Snapshot snapshot=snapshotForSlice(context);String modelKey=CurrentModelStore.selectionKey(context);
                ObjectSettingsStore.Snapshot object=snapshot.identity.objectSettings;
                if(modelKey.startsWith(object.modelFileName+"\n")&&modelKey.endsWith("\n"+object.plateIndex)&&modelKey.equals(CurrentModelStore.selectionKey(context)))
                    return ResultFingerprint.of(modelKey,snapshot.identity.fingerprint,snapshot.overrides);
            }
            throw new IOException("Модель или стол изменились при проверке результата. Повторите операцию.");
        }
    }
    static void save(Context context, Identity expected, String overrides) throws IOException {
        synchronized (LOCK) {
            if (!Identity.capture(context).fingerprint.equals(expected.fingerprint)) throw new IOException("Выбор профилей изменился. Откройте параметры снова.");
            String validated = validateStoredJson(overrides);
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String oldId = p.getString(ID, ""), oldJson = p.getString(JSON, "{}");
            if (!p.edit().putString(ID, expected.legacyFingerprint).putString(JSON, validated).commit()) {
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
        changeSelection(context,true,operation);
    }
    static void changeSelection(Context context, boolean clearLegacy, SelectionWrite operation) throws IOException {
        synchronized (LOCK) {
            if(clearLegacy){SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
                if(!p.edit().clear().commit())throw new IOException("Не удалось сбросить параметры перед сменой профиля.");}
            operation.commit();
        }
    }
    private static String validateStoredJson(String text) throws IOException {
        if (text == null || text.length() > 65536) throw new IOException("Параметры слишком велики.");
        try { return new JSONObject(text).toString(); }
        catch (JSONException e) { throw new IOException("Не удалось прочитать параметры процесса.", e); }
    }
}
