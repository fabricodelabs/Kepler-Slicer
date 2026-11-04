package com.keplerslicer.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.keplerslicer.engine.ExperimentalSlicer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.LinkedHashSet;
import java.util.UUID;

/** Experimental integration of upstream libslic3r. No printer connection or export. */
public final class SliceActivity extends Activity {
    static final String EXTRA_AUTO_SLICE="kepler_auto_slice";
    static final String EXTRA_AUTO_SLICE_REQUEST="kepler_auto_slice_request";
    private static final Object LOCK = new Object();
    private static final String RESULTS = "last_slice_result", RESULT_PATH = "path", RESULT_DETAILS = "details", RESULT_MODEL = "model", RESULT_OVERRIDES = "overrides", RESULT_FINGERPRINT = "job_fingerprint";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<SliceActivity> observer = new WeakReference<>(null);
    private static boolean working;
    static boolean isSlicing() { synchronized (LOCK) { return working; } }
    private static String status = KeplerApplication.str(R.string.l10n_sl_ready);
    private TextView statusView, startButton, previewButton, previewDetails, exportButton;
    private MdUi ui;
    private boolean pendingAutoSlice;
    private long pendingAutoRequest;
    private File shownResult;
    private boolean checkingResult;
    private int resultQueryGeneration;
    private TextView printerSummary, processSummary, filamentSummary, profileSummaryNote;
    private int profileQueryGeneration;
    private Thread profileReader;
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    @Override public void onCreate(Bundle state) {
        MdUi.prepare(this); super.onCreate(state);
        pendingAutoSlice=state==null&&getIntent()!=null&&getIntent().getBooleanExtra(EXTRA_AUTO_SLICE,false);
        pendingAutoRequest=getIntent()==null?-1:getIntent().getLongExtra(EXTRA_AUTO_SLICE_REQUEST,-1);
        if(getIntent()!=null){getIntent().removeExtra(EXTRA_AUTO_SLICE);getIntent().removeExtra(EXTRA_AUTO_SLICE_REQUEST);}
        ui = new MdUi(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); ui.bars(this, root);
        setContentView(root);
        ScrollView scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this); content.setPadding(dp(20), dp(8), dp(20), dp(32)); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content);
        com.keplerslicer.app.glass.KeplerUi kv = new com.keplerslicer.app.glass.KeplerUi(this); content.addView(kv.subHeader(getString(R.string.kv3_slice_title), getString(R.string.kv3_back), this::finish, null)); space(content, 16);
        // Title is in the Kepler v3 top bar above.
        content.addView(label(getString(R.string.l10n_sl_note), 14, false)); space(content, 24);
        content.addView(label(getString(R.string.l10n_sl_printer), 11, true)); printerSummary=label(getString(R.string.native_slice_profiles_loading),15,false);content.addView(printerSummary);space(content,14);
        content.addView(label(getString(R.string.l10n_sl_process), 11, true)); processSummary=label(getString(R.string.native_slice_profiles_loading),15,false);content.addView(processSummary);space(content,14);
        content.addView(label(getString(R.string.l10n_sl_material), 11, true)); filamentSummary=label(getString(R.string.native_slice_profiles_loading),15,false);content.addView(filamentSummary);space(content,12);
        profileSummaryNote=label("",12,false);content.addView(profileSummaryNote);space(content,24);
        statusView = label("", 14, false); statusView.setTextIsSelectable(true); content.addView(statusView); space(content, 24);
        startButton = label(getString(R.string.l10n_sl_slice_model), 15, true); startButton.setTextColor(ui.bg); startButton.setGravity(Gravity.CENTER); startButton.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable button = new GradientDrawable(); button.setColor(ui.ink); button.setCornerRadius(dp(100)); startButton.setBackground(button); content.addView(startButton);
        startButton.setOnClickListener(v -> startSlice(-1));
        space(content, 16);
        previewButton = label(getString(R.string.l10n_sl_view_last), 15, true); previewButton.setGravity(Gravity.CENTER); previewButton.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable outline = new GradientDrawable(); outline.setColor(ui.surface); outline.setStroke(dp(1), ui.line); outline.setCornerRadius(dp(100)); previewButton.setBackground(outline); content.addView(previewButton);
        previewButton.setOnClickListener(v -> withCurrentResult(result -> {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("kepler_show_layers",true));
        }));
        space(content, 12);
        exportButton = label(getString(R.string.l10n_sl_save_gcode), 15, true); exportButton.setGravity(Gravity.CENTER); exportButton.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable exportOutline = new GradientDrawable(); exportOutline.setColor(ui.surface); exportOutline.setStroke(dp(1), ui.line); exportOutline.setCornerRadius(dp(100)); exportButton.setBackground(exportOutline); content.addView(exportButton);
        exportButton.setOnClickListener(v -> withCurrentResult(result -> {
            String modelName = getSharedPreferences(RESULTS, MODE_PRIVATE).getString(RESULT_MODEL, "");
            if (modelName == null || modelName.trim().isEmpty()) modelName = "kepler-result";
            String lowerName=modelName.toLowerCase(java.util.Locale.ROOT);
            if (lowerName.endsWith(".stl") || lowerName.endsWith(".obj") || lowerName.endsWith(".3mf")) modelName = modelName.substring(0, modelName.length() - 4);
            startActivity(new Intent(this, GcodeExportActivity.class).putExtra(GcodeExportActivity.EXTRA_RESULT_PATH, result.getAbsolutePath())
                    .putExtra(GcodeExportActivity.EXTRA_FILE_NAME, modelName + ".gcode"));
        }));
        space(content, 8); previewDetails = label("", 12, false); content.addView(previewDetails);
    }
    @Override protected void onStart() { super.onStart(); synchronized (LOCK) { observer = new WeakReference<>(this); } requestResultRefresh();requestProfileSummary();if(pendingAutoSlice){pendingAutoSlice=false;if(ControlPreferences.currentAutoRequest(this,pendingAutoRequest))startSlice(pendingAutoRequest);} }
    @Override protected void onStop() { resultQueryGeneration++;profileQueryGeneration++;Thread reader=profileReader;profileReader=null;if(reader!=null)reader.interrupt();shownResult=null;synchronized (LOCK) { if (observer.get() == this) observer.clear(); } super.onStop(); }
    private TextView label(String value, int size, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(ui.ink); com.keplerslicer.app.glass.GlassTypography.apply(t, bold ? 700 : 500); t.setLineSpacing(dp(2), 1); return t; }
    private void space(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private void refresh() {
        boolean available = shownResult != null;
        synchronized (LOCK) {
            String visibleStatus = !working && !available && status.contains(getString(R.string.l10n_sl_done)) ? getString(R.string.l10n_sl_stale) : status;
            statusView.setText(visibleStatus); startButton.setEnabled(!working); startButton.setAlpha(working ? .45f : 1f);
        }
        previewButton.setVisibility(available ? View.VISIBLE : View.GONE); exportButton.setVisibility(available ? View.VISIBLE : View.GONE); previewDetails.setVisibility(available ? View.VISIBLE : View.GONE);
        previewButton.setEnabled(!checkingResult);exportButton.setEnabled(!checkingResult);
        if (available) previewDetails.setText("Последний сохранённый результат:\n" + getSharedPreferences(RESULTS, MODE_PRIVATE).getString(RESULT_DETAILS, ""));
    }
    private void requestResultRefresh() {
        final int generation=++resultQueryGeneration;
        shownResult=null;refresh();
        final android.content.Context context=getApplicationContext();
        new Thread(()->{
            File current=lastResult(context);
            MAIN.post(()->{if(!isFinishing()&&!isDestroyed()&&generation==resultQueryGeneration){shownResult=current;refresh();}});
        },"kepler-result-check").start();
    }
    /** Read the same effective project mode as slicing; never label it as a library preset. */
    private void requestProfileSummary() {
        final int generation=++profileQueryGeneration;
        Thread previous=profileReader;if(previous!=null)previous.interrupt();
        final android.content.Context context=getApplicationContext();
        String loading=getString(R.string.native_slice_profiles_loading);
        printerSummary.setText(loading);processSummary.setText(loading);filamentSummary.setText(loading);profileSummaryNote.setText("");
        profileReader=new Thread(()->{
            File temporary=null;CurrentModelStore.Snapshot model=null;
            try {
                final String expectedModel=CurrentModelStore.selectionKey(context);
                final EngineProfileSession session=EngineProfileSession.capture(context);
                String printer=session.id.profileSettings.overlays.get("printer").values().optString("name",session.id.printer.name);
                String process=session.id.profileSettings.overlays.get("process").values().optString("name",session.id.process.name);
                String filament=session.id.profileSettings.overlays.get("filament").values().optString("name",session.id.filament.name);
                String note="";
                int mode=CurrentModelStore.projectMode(context);
                if(mode==1||mode==2) {
                    temporary=new File(context.getCacheDir(),"slice-project-summary-"+UUID.randomUUID());
                    if(!temporary.mkdir())throw new IOException("Cannot prepare project summary");
                    model=CurrentModelStore.snapshotToDirectory(context,temporary);
                    if(model.projectMode!=mode||!expectedModel.equals(model.selectionKey))throw new IOException(context.getString(R.string.native_project_stale_values));
                    org.json.JSONObject overrides=new org.json.JSONObject(session.selection.overrides);
                    java.util.Iterator<String> objectKeys=session.id.objectSettings.changes().keys();
                    while(objectKeys.hasNext())overrides.remove(objectKeys.next());
                    ProjectSettingsSnapshot snapshot=ProjectSettingsSnapshot.parse(ExperimentalSlicer.projectSettingsSnapshot(
                        session.id.printer.vendor,session.id.process.vendor,session.id.filament.vendor,
                        session.id.printer.name,session.id.process.name,session.id.filament.name,
                        session.resources.getAbsolutePath(),session.data.getAbsolutePath(),overrides.toString(),model.materialEdits,
                        model.file.getAbsolutePath(),model.plateIndex));
                    boolean embedded=mode==2;
                    if(!snapshot.canLoad(embedded))throw new IOException(snapshot.validationError(embedded));
                    org.json.JSONObject actual=snapshot.effectiveValues(embedded);
                    printer=actual.optString("printer_settings_id","");
                    process=actual.optString("print_settings_id","");
                    if(printer.trim().isEmpty())printer=context.getString(R.string.native_slice_unnamed_printer);
                    if(process.trim().isEmpty())process=context.getString(R.string.native_slice_unnamed_process);
                    process+="\n"+layerHeightSummary(context,snapshot.json().getJSONObject(embedded?"effective":"effective_selected_printer"));
                    org.json.JSONArray materials=actual.optJSONArray("filament_settings_id");StringBuilder materialNames=new StringBuilder();
                    if(materials!=null)for(int i=0;i<materials.length();i++){if(i>0)materialNames.append("\n");String name=materials.optString(i,"");materialNames.append(name.trim().isEmpty()?context.getString(R.string.native_slice_unnamed_filament,i+1):name);}
                    filament=materialNames.length()==0?context.getString(R.string.native_slice_profiles_unavailable):materialNames.toString();
                    note=context.getString(R.string.native_project_slice_detail,context.getString(embedded?R.string.native_project_mode_embedded:R.string.native_project_mode_current))+"\n"+context.getString(R.string.native_slice_project_summary_note);
                } else {
                    byte[] bytes=ExperimentalSlicer.resolvedSettingsSnapshot(session.id.printer.vendor,session.id.process.vendor,session.id.filament.vendor,
                        session.id.printer.name,session.id.process.name,session.id.filament.name,
                        session.resources.getAbsolutePath(),session.data.getAbsolutePath(),session.selection.overrides);
                    if(bytes==null)throw new IOException("No resolved settings summary");
                    String json=java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                    process+="\n"+layerHeightSummary(context,new org.json.JSONObject(json));
                    note=context.getString(R.string.native_slice_selected_summary_note);
                }
                if(Thread.currentThread().isInterrupted()||!session.stillCurrent(context)||!expectedModel.equals(CurrentModelStore.selectionKey(context)))
                    throw new IOException(context.getString(R.string.native_project_stale_values));
                final String shownPrinter=printer,shownProcess=process,shownFilament=filament,shownNote=note;
                MAIN.post(()->{if(isFinishing()||isDestroyed()||generation!=profileQueryGeneration)return;printerSummary.setText(shownPrinter);processSummary.setText(shownProcess);filamentSummary.setText(shownFilament);profileSummaryNote.setText(shownNote);});
            } catch(Exception|LinkageError error) {
                final String message=context.getString(R.string.native_slice_summary_error,error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());
                MAIN.post(()->{if(isFinishing()||isDestroyed()||generation!=profileQueryGeneration)return;String unavailable=getString(R.string.native_slice_profiles_unavailable);printerSummary.setText(unavailable);processSummary.setText(unavailable);filamentSummary.setText(unavailable);profileSummaryNote.setText(message);});
            } finally {if(model!=null)model.file.delete();if(temporary!=null)temporary.delete();}
        },"kepler-slice-profile-summary");
        profileReader.start();
    }
    private static String layerHeightSummary(android.content.Context context,org.json.JSONObject config)throws IOException {
        try {
            org.json.JSONObject row=config.getJSONObject("options").getJSONObject("layer_height");Object raw=row.get("value");
            if(!"coFloat".equals(row.getString("type"))||!(raw instanceof Number))throw new IOException("Invalid effective layer height");
            double value=((Number)raw).doubleValue();if(!Double.isFinite(value)||value<=0)throw new IOException("Invalid effective layer height");
            return context.getString(R.string.native_slice_layer_height_value,java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString());
        }catch(org.json.JSONException error){throw new IOException("Missing effective layer height",error);}
    }
    private void withCurrentResult(java.util.function.Consumer<File> action) {
        if(checkingResult)return;
        checkingResult=true;refresh();
        final int generation=resultQueryGeneration;
        final android.content.Context context=getApplicationContext();
        new Thread(()->{
            File result=lastResult(context);
            MAIN.post(()->{
                if(isFinishing()||isDestroyed()||generation!=resultQueryGeneration){checkingResult=false;return;}
                checkingResult=false;shownResult=result;refresh();
                if(result==null)update(getString(R.string.l10n_sl_saved_unavailable));else action.accept(result);
            });
        },"kepler-result-action").start();
    }
    private static void update(String text) { synchronized (LOCK) { status = text; } MAIN.post(() -> { SliceActivity a; synchronized (LOCK) { a = observer.get(); } if (a != null && !a.isFinishing()) a.refresh(); }); }

    private void startSlice(long autoRequest) {
        synchronized (LOCK) { if (working) return; working = true; }
        refresh();
        final android.content.Context context = getApplicationContext();
        new Thread(() -> {
            String stage = getString(R.string.l10n_sl_phase_snapshot);
            File data = null;
            boolean success = false;
            try {
                if(autoRequest>=0&&!ControlPreferences.currentAutoRequest(context,autoRequest))throw new IOException(getString(R.string.l10n_sl_auto_cancelled));
                final ProcessOverridesStore.Snapshot selection=ProcessOverridesStore.snapshotForSlice(context);
                final boolean allowMixed=GeneralPreferences.prefs(context).getBoolean("allow_mixed_temperatures",false);
                final PrinterProfileCatalog.Profile printer=selection.identity.printer;
                final PrintProfileCatalog.Profile process=selection.identity.process;
                final PrintProfileCatalog.Profile filament=selection.identity.filament;
                stage=getString(R.string.l10n_sl_phase_model);
                update(getString(R.string.l10n_sl_checking));
                File root = new File(context.getFilesDir(), "slicer-engine"); ensureDirectory(root);
                data = new File(root, "run-" + UUID.randomUUID()); ensureDirectory(data);
                // Snapshot the bytes and transform together before the model can be changed.
                CurrentModelStore.Snapshot model = CurrentModelStore.snapshotToDirectory(context, data);
                ObjectSettingsStore.Snapshot objectSettings=selection.identity.objectSettings;
                String sourceFile=model.selectionKey.substring(0,model.selectionKey.indexOf('\n'));
                if(!objectSettings.modelFileName.equals(sourceFile)||objectSettings.plateIndex!=model.plateIndex)
                    throw new IOException(getString(R.string.l10n_sl_changed_prepare));
                File input = model.file;
                String jobFingerprint = ResultFingerprint.of(model.selectionKey, selection.identity.fingerprint, selection.overrides);
                if(autoRequest>=0&&hasSavedResult(context,jobFingerprint)&&ControlPreferences.currentAutoRequest(context,autoRequest)
                        &&jobFingerprint.equals(ProcessOverridesStore.currentResultFingerprint(context))){
                    update(getString(R.string.l10n_sl_already));return;
                }
                File resources = EngineResources.prepare(context, count -> update(getString(R.string.l10n_sl_preparing) + count + " файлов…"));
                File system = new File(data, "system"); ensureDirectory(system);
                LinkedHashSet<String> vendors = new LinkedHashSet<>();
                vendors.add(printer.vendor); vendors.add(process.vendor); vendors.add(filament.vendor); vendors.add("OrcaFilamentLibrary");
                long needed = 128L * 1024 * 1024;
                for (String vendor : vendors) {
                    if (vendor == null || vendor.isEmpty() || vendor.contains("/") || vendor.contains("\\") || vendor.equals(".") || vendor.equals("..")) throw new IOException("Неверное имя производителя профиля.");
                    File from = new File(resources, "profiles");
                    needed += treeSize(new File(from, vendor + ".json")) + treeSize(new File(from, vendor));
                }
                if (root.getUsableSpace() < needed) throw new IOException(getString(R.string.l10n_sl_no_space));
                for (String vendor : vendors) {
                    File from = new File(resources, "profiles");
                    copyTree(new File(from, vendor + ".json"), new File(system, vendor + ".json"));
                    copyTree(new File(from, vendor), new File(system, vendor));
                }
                objectSettings.applied(selection.identity.profileSettings).install(data);
                // Object values have higher priority than legacy global process overrides.
                org.json.JSONObject nativeOverrides=new org.json.JSONObject(selection.overrides);
                java.util.Iterator<String> objectKeys=objectSettings.changes().keys();
                while(objectKeys.hasNext())nativeOverrides.remove(objectKeys.next());
                if((autoRequest>=0&&!ControlPreferences.currentAutoRequest(context,autoRequest))||!jobFingerprint.equals(ProcessOverridesStore.currentResultFingerprint(context)))
                    throw new IOException(getString(R.string.l10n_sl_changed_before));
                stage = getString(R.string.l10n_sl_phase_engine);
                update(getString(R.string.l10n_sl_slicing));
                File job = new File(data, "job-" + UUID.randomUUID());
                ProjectSettingsSnapshot projectDetails=null;
                if(model.projectMode==1||model.projectMode==2)projectDetails=ProjectSettingsSnapshot.parse(ExperimentalSlicer.projectSettingsSnapshot(
                    printer.vendor,process.vendor,filament.vendor,printer.name,process.name,filament.name,
                    resources.getAbsolutePath(),data.getAbsolutePath(),nativeOverrides.toString(),model.materialEdits,input.getAbsolutePath(),model.plateIndex));
                if(allowMixed!=GeneralPreferences.prefs(context).getBoolean("allow_mixed_temperatures",false))throw new IOException("Material compatibility policy changed before slicing");
                String result = ExperimentalSlicer.sliceProjectWithSettings(printer.vendor, process.vendor, filament.vendor,
                        printer.name, process.name, filament.name, input.getAbsolutePath(), resources.getAbsolutePath(), data.getAbsolutePath(), job.getAbsolutePath(), model.transform.toNativeArguments(), nativeOverrides.toString(),model.materialEdits,model.plateIndex,model.projectMode,allowMixed);
                File output = result == null ? null : new File(result);
                if (output == null || !output.isFile() || output.length() == 0 || !output.getCanonicalFile().getParentFile().equals(job.getCanonicalFile())) throw new IOException(getString(R.string.l10n_sl_no_gcode));
                if((autoRequest>=0&&!ControlPreferences.currentAutoRequest(context,autoRequest))||!jobFingerprint.equals(ProcessOverridesStore.currentResultFingerprint(context)))
                    throw new IOException(getString(R.string.l10n_sl_changed_during));
                if(allowMixed!=GeneralPreferences.prefs(context).getBoolean("allow_mixed_temperatures",false))throw new IOException("Material compatibility policy changed during slicing; stale result was not saved");
                success = true;
                ModelTransform transform = model.transform;
                String printerDisplay=selection.identity.profileSettings.overlays.get("printer").values().optString("name",printer.name);
                String processDisplay=selection.identity.profileSettings.overlays.get("process").values().optString("name",process.name);
                String filamentDisplay=selection.identity.profileSettings.overlays.get("filament").values().optString("name",filament.name);
                if(projectDetails!=null){org.json.JSONObject actual=projectDetails.effectiveValues(model.projectMode==2);printerDisplay=model.projectMode==2?projectDetails.embeddedPrinterId:projectDetails.selectedPrinterId;processDisplay=actual.optString("print_settings_id",processDisplay);org.json.JSONArray ids=actual.optJSONArray("filament_settings_id");if(ids!=null&&ids.length()>0)filamentDisplay=ids.optString(0,filamentDisplay)+(ids.length()>1?" +"+(ids.length()-1):"");}
                String details = model.name + "\n" + printerDisplay + "\n" + processDisplay + "\n" + filamentDisplay +
                        String.format(java.util.Locale.getDefault(), "\nПоворот XYZ: %.2f°, %.2f°, %.2f°\nМасштаб XYZ: %.3f × %.3f × %.3f\nСмещение XY: %.2f, %.2f мм",
                                transform.rx, transform.ry, transform.rz, transform.sx, transform.sy, transform.sz, transform.offsetX, transform.offsetY);
                if(input.getName().endsWith(".3mf")&&model.projectMode>=0&&model.projectMode<=2)details+="\n"+context.getString(R.string.native_project_slice_detail,context.getString(model.projectMode==2?R.string.native_project_mode_embedded:model.projectMode==1?R.string.native_project_mode_current:R.string.native_project_mode_geometry));
                SharedPreferences prefs = context.getSharedPreferences(RESULTS, MODE_PRIVATE);
                String previousPath = prefs.getString(RESULT_PATH, ""), previousDetails = prefs.getString(RESULT_DETAILS, ""), previousModel = prefs.getString(RESULT_MODEL, ""), previousOverrides = prefs.getString(RESULT_OVERRIDES, "{}"), previousFingerprint = prefs.getString(RESULT_FINGERPRINT, "");
                boolean saved = prefs.edit().putString(RESULT_PATH, output.getCanonicalPath()).putString(RESULT_DETAILS, details).putString(RESULT_MODEL, model.name).putString(RESULT_OVERRIDES, selection.overrides).putString(RESULT_FINGERPRINT, jobFingerprint).commit();
                // A failed commit may still change the in-memory preference map.
                if (!saved) prefs.edit().putString(RESULT_PATH, previousPath).putString(RESULT_DETAILS, previousDetails).putString(RESULT_MODEL, previousModel).putString(RESULT_OVERRIDES, previousOverrides).putString(RESULT_FINGERPRINT, previousFingerprint).apply();
                update(getString(R.string.l10n_sl_last_run) + details + "\n\nНарезка завершена: " + output.length() + getString(R.string.l10n_sl_done_bytes) + (saved ? "" : "\nНе удалось сохранить ссылку на результат для просмотра и экспорта."));
            } catch (Throwable error) {
                String message = error.getMessage();
                String diagnostic = error instanceof LinkageError ? nativeDiagnostic(context) : "";
                update(getString(R.string.l10n_sl_last_stopped) + stage + ".\n" + error.getClass().getSimpleName() + (message == null ? "" : ": " + message) + diagnostic);
            } finally {
                if (data != null) {
                    if (success) { deleteTree(new File(data, "system")); File[] files=data.listFiles(); if(files!=null) for (File source : files) if (source.isFile() && source.getName().startsWith("model.")) source.delete(); }
                    else deleteTree(data);
                }
                synchronized (LOCK) { working = false; }
                MAIN.post(() -> { SliceActivity a; synchronized (LOCK) { a = observer.get(); } if (a != null && !a.isFinishing()) a.requestResultRefresh(); });
            }
        }, "kepler-slicing").start();
    }
    private static void ensureDirectory(File dir) throws IOException { if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException(KeplerApplication.str(R.string.l10n_sl_mkdir_failed) + dir.getName()); }
    private static File lastResult(android.content.Context context) {
        java.util.Map<String, ?> record = context.getSharedPreferences(RESULTS, MODE_PRIVATE).getAll();
        Object rawPath=record.get(RESULT_PATH);String path=rawPath instanceof String?(String)rawPath:null;
        if (path == null || path.isEmpty()) return null;
        try {
            Object rawSaved=record.get(RESULT_FINGERPRINT);String saved=rawSaved instanceof String?(String)rawSaved:null;
            if (saved == null || saved.isEmpty() || !saved.equals(ProcessOverridesStore.currentResultFingerprint(context))) return null;
            File root = new File(context.getFilesDir(), "slicer-engine").getCanonicalFile();
            File result = new File(path).getCanonicalFile();
            return result.getName().equals("result.gcode") && result.getPath().startsWith(root.getPath() + File.separator) && result.isFile() && result.length() > 0 ? result : null;
        } catch (IOException | RuntimeException ignored) { return null; }
    }
    /** Called on the slicing worker to prevent an unchanged auto-slice loop. */
    private static boolean hasSavedResult(android.content.Context context,String fingerprint) {
        SharedPreferences prefs=context.getSharedPreferences(RESULTS,MODE_PRIVATE);
        if(!fingerprint.equals(prefs.getString(RESULT_FINGERPRINT,"")))return false;
        try {
            String path=prefs.getString(RESULT_PATH,"");if(path==null||path.isEmpty())return false;
            File root=new File(context.getFilesDir(),"slicer-engine").getCanonicalFile(),result=new File(path).getCanonicalFile();
            return result.getName().equals("result.gcode")&&result.getPath().startsWith(root.getPath()+File.separator)&&result.isFile()&&result.length()>0;
        } catch(IOException error){return false;}
    }
    private static void copy(InputStream in, FileOutputStream out) throws IOException { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n); }
    private static String nativeDiagnostic(android.content.Context context) {
        android.content.pm.ApplicationInfo info = context.getApplicationInfo();
        boolean found = containsNativeLibrary(info.sourceDir);
        if (info.splitSourceDirs != null) for (String split : info.splitSourceDirs) found |= containsNativeLibrary(split);
        return "\nABI устройства: " + String.join(", ", Build.SUPPORTED_ABIS) +
                "\nАрхитектура процесса: " + System.getProperty("os.arch", "неизвестна") +
                "\nARM64-библиотека в установленном APK: " + (found ? "есть" : "нет") +
                "\nКаталог библиотек: " + info.nativeLibraryDir;
    }
    private static boolean containsNativeLibrary(String path) {
        if (path == null) return false;
        try (java.util.zip.ZipFile apk = new java.util.zip.ZipFile(path)) {
            return apk.getEntry("lib/arm64-v8a/libkepler_slicer_experimental.so") != null;
        } catch (IOException ignored) { return false; }
    }
    private static long treeSize(File file) throws IOException {
        if (file.isFile()) return file.length();
        if (!file.isDirectory()) throw new IOException(KeplerApplication.str(R.string.l10n_sl_resource_missing) + file.getName());
        File[] files = file.listFiles(); if (files == null) throw new IOException(KeplerApplication.str(R.string.l10n_sl_profiles_read_failed));
        long total = 0; for (File child : files) total += treeSize(child); return total;
    }
    private static void deleteTree(File file) {
        if (file.isDirectory()) { File[] files = file.listFiles(); if (files != null) for (File child : files) deleteTree(child); }
        file.delete();
    }
    private static void copyTree(File from, File to) throws IOException {
        if (from.isDirectory()) { ensureDirectory(to); File[] children = from.listFiles(); if (children == null) throw new IOException(KeplerApplication.str(R.string.l10n_sl_profiles_read_failed)); for (File child : children) copyTree(child, new File(to, child.getName())); }
        else if (from.isFile()) { try (InputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) { copy(in, out); } }
        else throw new IOException(KeplerApplication.str(R.string.l10n_sl_resource_missing) + from.getName());
    }
}
