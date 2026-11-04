package com.keplerslicer.app;

import android.content.Context;
import android.util.Log;
import com.keplerslicer.engine.ExperimentalSlicer;
import com.keplerslicer.app.settings.SettingsSchema;
import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;

/** Background-only resolved native values, with explicitly classified older-ABI fallback. */
final class NativeConfigValues {
    private static final String TAG = "NativeConfigValues";
    private NativeConfigValues() {}
    enum Source { RESOLVED_TYPED, PROCESS_METADATA, PROCESS_METADATA_SERIALIZED, VALIDATED_INI, DEFINITION_DEFAULT, PROJECT_CONFIGURATION }
    static final class Result {
        final JSONObject values;
        final Map<String, Source> sources;
        final boolean resolvedConfigurationAvailable;
        final String optionalDiagnostic;
        final boolean projectConfiguration;
        Result(JSONObject values, Map<String, Source> sources, boolean available, String diagnostic) {
            this(values,sources,available,diagnostic,false);
        }
        Result(JSONObject values, Map<String, Source> sources, boolean available, String diagnostic,boolean project) {
            this.values = values;
            this.sources = Collections.unmodifiableMap(new HashMap<>(sources));
            this.resolvedConfigurationAvailable = available;
            this.optionalDiagnostic = diagnostic;
            this.projectConfiguration = project;
        }
        int count(Source source) { int result=0; for(Source value:sources.values()) if(value==source)result++; return result; }
    }

    /**
     * Resolves the captured saved profile selection and overrides through the current
     * typed JNI contract. Direct values retain their precision. Only a missing older
     * JNI symbol permits the legacy display path: its stream-serialized values may
     * round and retain their approximation provenance. Invalid available typed data
     * is rejected. Display fallbacks never become unchanged persisted overrides.
     */
    static JSONObject load(Context context, SettingsSchema schema, String kind,
                           ProcessOverridesStore.Identity id) throws IOException {
        return loadDetailed(context,schema,kind,id).values;
    }

    /** Values are display-only: a DEFINITION_DEFAULT is not the selected preset's effective value. */
    static Result loadDetailed(Context context, SettingsSchema schema, String kind,
                               ProcessOverridesStore.Identity id) throws IOException {
        if (context == null || schema == null || id == null) throw new IOException("Не выбраны профили для чтения настроек.");
        if(CurrentModelStore.projectMode(context)>0)return projectValues(context,schema,kind,id);
        List<SettingsSchema.Option> options = schema.options(kind);
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Загрузка отменена.");
        String modelKey = CurrentModelStore.settingsSelectionKey(context), overrides = ProcessOverridesStore.load(context,id);
        CompatibilityResources prepared = CompatibilityResources.prepareTriple(context,id.printer.vendor,
                id.process.vendor,id.filament.vendor,id.profileSettings);
        byte[] bytes;
        try {
            bytes = ExperimentalSlicer.resolvedSettingsSnapshot(id.printer.vendor,id.process.vendor,id.filament.vendor,
                    id.printerName,id.processName,id.filamentName,prepared.resources.getAbsolutePath(),
                    prepared.data.getAbsolutePath(),overrides);
        } catch (UnsatisfiedLinkError unavailable) {
            // Only a missing older JNI entry point permits the old, explicitly marked display path.
            requireCurrent(context,id,modelKey,overrides);
            Result legacy = legacyValues(context,schema,kind,id);
            requireCurrent(context,id,modelKey,overrides);
            String diagnostic = "Native typed settings API is unavailable";
            if (legacy.optionalDiagnostic != null) diagnostic += ": " + legacy.optionalDiagnostic;
            return new Result(legacy.values,legacy.sources,legacy.resolvedConfigurationAvailable,diagnostic);
        } catch (RuntimeException | LinkageError failure) {
            requireCurrent(context,id,modelKey,overrides);
            throw new IOException("Could not resolve native typed settings: " +
                    (failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage()),failure);
        }
        JSONObject result = NativeTypedConfigSnapshot.read(bytes,options);
        requireCurrent(context,id,modelKey,overrides);
        if (result.length()==0) throw new IOException("Native typed settings contain no recognized options for " + kind);
        Map<String,Source> sources = new HashMap<>();
        for (Iterator<String> keys=result.keys();keys.hasNext();) sources.put(keys.next(),Source.RESOLVED_TYPED);
        addDefinitionDefaults(result,sources,options);
        return new Result(result,sources,true,null);
    }

    private static void requireCurrent(Context context,ProcessOverridesStore.Identity id,String modelKey,String overrides)throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Загрузка отменена.");
        if (!modelKey.equals(CurrentModelStore.settingsSelectionKey(context)) || !overrides.equals(ProcessOverridesStore.load(context,id)))
            throw new IOException(context.getString(R.string.native_project_stale_values));
    }

    private static Result legacyValues(Context context, SettingsSchema schema, String kind,
                                       ProcessOverridesStore.Identity id) throws IOException {
        // Validate the section before treating errors in the optional native fallback as non-fatal.
        List<SettingsSchema.Option> options = schema.options(kind);
        JSONObject result = new JSONObject(); Map<String,Source> sources = new HashMap<>();
        String diagnostic = null; boolean resolved = false;
        try {
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Загрузка отменена.");
            CompatibilityResources prepared = CompatibilityResources.prepareTriple(context, id.printer.vendor,
                    id.process.vendor, id.filament.vendor, id.profileSettings);
            if ("process".equals(kind)) {
                byte[] bytes = ExperimentalSlicer.processSettingsMetadata(id.printer.vendor,id.process.vendor,id.filament.vendor,
                        id.printerName,id.processName,id.filamentName,prepared.resources.getAbsolutePath(),prepared.data.getAbsolutePath());
                if (bytes == null) throw new IOException("Движок не вернул настройки процесса.");
                result = readProcessMetadata(bytes, options);
                if (result.length()==0) throw new IOException("Движок не вернул распознанных параметров процесса.");
                resolved = true;
                for (SettingsSchema.Option option:options) if(result.has(option.key))
                    sources.put(option.key,option.isVector() || "coFloatOrPercent".equals(option.type) || "coPoint".equals(option.type)
                            ? Source.PROCESS_METADATA_SERIALIZED : Source.PROCESS_METADATA);
            } else {
                result = resolveIni(prepared, schema, kind, id);
                if (result.length()==0) throw new IOException("Нет распознанных параметров в файле движка.");
                resolved = true;
                for (java.util.Iterator<String> keys=result.keys();keys.hasNext();) sources.put(keys.next(),Source.VALIDATED_INI);
            }
        } catch (java.io.InterruptedIOException e) {
            throw e;
        } catch (IOException | RuntimeException | LinkageError e) {
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Загрузка отменена.");
            diagnostic = e.getMessage()==null ? e.getClass().getSimpleName() : e.getMessage();
            Log.i(TAG, "Optional resolved defaults unavailable; using known definition defaults");
        }
        addDefinitionDefaults(result,sources,options);
        return new Result(result,sources,resolved,diagnostic);
    }

    private static void addDefinitionDefaults(JSONObject result,Map<String,Source> sources,List<SettingsSchema.Option> options)throws IOException {
        for (SettingsSchema.Option option:options) {
            if (result.has(option.key) || !option.defaultResolved) continue;
            Object value = definitionValue(option);
            if (value == null) continue;
            try { result.put(option.key,value); sources.put(option.key,Source.DEFINITION_DEFAULT); }
            catch (JSONException e) { throw new IOException("Некорректное значение определения движка.",e); }
        }
    }

    private static Result projectValues(Context context,SettingsSchema schema,String kind,ProcessOverridesStore.Identity id)throws IOException {
        File working=new File(context.getCacheDir(),"project-settings-"+UUID.randomUUID());
        if(!working.mkdir())throw new IOException("Cannot prepare an immutable project settings snapshot");
        CurrentModelStore.Snapshot model=null;
        try {
            model=CurrentModelStore.snapshotToDirectory(context,working);
            if(model.projectMode<=0||!model.file.getName().endsWith(".3mf"))throw new IOException(context.getString(R.string.native_project_stale_values));
            CompatibilityResources prepared=CompatibilityResources.prepareTriple(context,id.printer.vendor,id.process.vendor,id.filament.vendor,id.profileSettings);
            String overrides=ProcessOverridesStore.load(context,id);
            ProjectSettingsSnapshot snapshot=ProjectSettingsSnapshot.parse(ExperimentalSlicer.projectSettingsSnapshot(
                id.printer.vendor,id.process.vendor,id.filament.vendor,id.printerName,id.processName,id.filamentName,
                prepared.resources.getAbsolutePath(),prepared.data.getAbsolutePath(),overrides,model.materialEdits,model.file.getAbsolutePath(),model.plateIndex));
            boolean embedded=model.projectMode==2;
            if(!snapshot.canLoad(embedded))throw new IOException(snapshot.validationError(embedded));
            requireCurrent(context,id,model.selectionKey,overrides);
            JSONObject all=snapshot.effectiveValues(embedded),filtered=new JSONObject();Map<String,Source> origins=new HashMap<>();
            for(SettingsSchema.Option option:schema.options(kind))if(all.has(option.key)){filtered.put(option.key,all.get(option.key));origins.put(option.key,Source.PROJECT_CONFIGURATION);}
            return new Result(filtered,origins,true,context.getString(R.string.native_project_effective_note),true);
        } catch(IOException e){throw e;} catch(Exception|LinkageError e){throw new IOException("Could not read native project settings: "+e.getMessage(),e);}
        finally {if(model!=null)model.file.delete();working.delete();}
    }

    private static JSONObject resolveIni(CompatibilityResources prepared, SettingsSchema schema, String kind,
                                         ProcessOverridesStore.Identity id) throws IOException {
        File job = new File(prepared.data, "settings-values-" + UUID.randomUUID());
        if (job.exists()) throw new IOException("Не удалось создать отдельное задание для чтения профилей.");
        try {
            String filename;
            try {
                filename = ExperimentalSlicer.resolveSystemProfiles(id.printer.vendor, id.process.vendor, id.filament.vendor,
                        id.printerName, id.processName, id.filamentName,
                        prepared.resources.getAbsolutePath(), prepared.data.getAbsolutePath(), job.getAbsolutePath());
            } catch (RuntimeException | LinkageError e) {
                throw new IOException("Не удалось прочитать настройки движка: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
            }
            File expected = new File(job, "resolved.ini");
            if (filename == null || !expected.getCanonicalFile().equals(new File(filename).getCanonicalFile()) || !expected.isFile())
                throw new IOException("Движок не создал ожидаемый файл настроек.");
            return read(expected, schema, kind);
        } finally {
            // JNI creates only this private job and resolved.ini. Never recursively traverse elsewhere.
            new File(job, "resolved.ini").delete();
            job.delete();
        }
    }

    static JSONObject readProcessMetadata(byte[] bytes, List<SettingsSchema.Option> options) throws IOException {
        Map<String,SettingsSchema.Option> indexed = new HashMap<>();
        for(SettingsSchema.Option option:options) indexed.put(option.key,option);
        try {
            String json=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            JSONObject document=new JSONObject(json), result=new JSONObject();
            if(document.getInt("version")!=2 || !"process_options".equals(document.getString("scope"))) throw new IOException("Неизвестный формат настроек движка.");
            JSONArray rows=document.getJSONArray("options");
            if(rows.length()>4096)throw new IOException("Неизвестный размер настроек движка.");
            HashSet<String> seen=new HashSet<>();
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i);String key=row.getString("key");
                if(!seen.add(key))throw new IOException("Повторяющийся ключ настроек движка.");
                SettingsSchema.Option option=indexed.get(key);if(option==null)continue;
                Object value=metadataValue(option,row.opt("value"));
                if(value!=null)result.put(key,value);
            }
            return result;
        } catch (JSONException | java.nio.charset.CharacterCodingException ex) {
            throw new IOException("Некорректные данные настроек движка.",ex);
        }
    }
    private static Object metadataValue(SettingsSchema.Option option,Object value) {
        if(value==null || value==JSONObject.NULL || option.type==null)return null;
        switch(option.type) {
            case "coString": return value instanceof String ? value : null; // already unescaped by option_value()
            case "coBool": return value instanceof Boolean ? ((Boolean)value ? "1":"0") : null;
            case "coFloat": case "coInt": return value instanceof Number && Double.isFinite(((Number)value).doubleValue()) ? value.toString() : null;
            case "coPercent": return value instanceof Number && Double.isFinite(((Number)value).doubleValue()) ? value.toString()+"%" : null;
            case "coFloatOrPercent": case "coEnum": return value instanceof String ? value : null;
            default: return value instanceof String ? ProfileIniCodec.convert(option.key,option.type,(String)value) : null;
        }
    }
    private static Object definitionValue(SettingsSchema.Option option) {
        Object value=option.defaultValue;
        if(value==null || option.type==null)return null;
        switch(option.type) {
            case "coString": case "coEnum": return value instanceof String ? value : null;
            case "coBool": return value instanceof Boolean ? ((Boolean)value ? "1":"0") : null;
            case "coInt": case "coFloat": return value instanceof Number && Double.isFinite(((Number)value).doubleValue()) ? value.toString() : null;
            case "coPercent": return value instanceof Number && Double.isFinite(((Number)value).doubleValue()) ? value.toString()+"%" : null;
            case "coFloatOrPercent": return value instanceof String ? value : null;
            case "coStrings": case "coFloats": case "coInts": case "coBools": case "coPercents":
                if(!(value instanceof List))return null;
                JSONArray result=new JSONArray();
                for(Object item:(List<?>)value) {
                    if("coStrings".equals(option.type)) { if(!(item instanceof String))return null; result.put(item); }
                    else if("coBools".equals(option.type)) { if(!(item instanceof Boolean))return null; result.put((Boolean)item?"1":"0"); }
                    else { if(!(item instanceof Number)||!Double.isFinite(((Number)item).doubleValue()))return null;
                        result.put(item.toString()+("coPercents".equals(option.type)?"%":"")); }
                }
                return result;
            default:return null;
        }
    }

    static JSONObject read(File file, SettingsSchema schema, String kind) throws IOException {
        Map<String,SettingsSchema.Option> options = new HashMap<>();
        for (SettingsSchema.Option option : schema.options(kind)) options.put(option.key, option);
        JSONObject result = new JSONObject();
        HashSet<String> seen = new HashSet<>();
        int omitted = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file),
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                int separator = line.indexOf(" = ");
                if (separator < 1) throw new IOException("Повреждён файл настроек движка.");
                String key = line.substring(0, separator);
                if (!seen.add(key)) throw new IOException("Повторяющийся ключ в файле настроек движка.");
                SettingsSchema.Option option = options.get(key);
                if (option == null) continue;
                Object value = ProfileIniCodec.convert(key, option.type, line.substring(separator + 3));
                if (value == null) { omitted++; continue; }
                try { result.put(key, value); }
                catch (JSONException e) { throw new IOException("Некорректное значение настроек движка.", e); }
            }
        }
        if (omitted > 0) Log.i(TAG, "Omitted " + omitted + " values with unsupported or ambiguous INI representation");
        return result;
    }
}
