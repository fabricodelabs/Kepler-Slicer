package com.keplerslicer.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.text.InputType;

/** Kepler Slicer mobile interface. */
public class MainActivity extends Activity {
    private int INK = Color.rgb(27, 28, 27), MUTED = Color.rgb(105, 107, 104), BG = Color.rgb(250, 250, 247);
    private int SURFACE = Color.rgb(239, 240, 236), LINE = Color.rgb(218, 220, 215);
    private boolean darkMode = true, refreshAfterResume;
    private boolean compactLandscape;
    private AlertDialog activeTransformEditor;
    private LinearLayout root;
    private int selected = 0;
    private static final int OPEN_STL = 101;
    private static final int REQUEST_IMPORT = 105, REQUEST_MODEL_DETAILS=106;
    private static final int PICK_PRINTER = 102;
    private static final int PICK_PROCESS = 103, PICK_FILAMENT = 104;
    private StlMesh mesh;
    private StlView stlView;
    private String meshName;
    private String meshFileName;
    private org.json.JSONObject modelMetadata;
    private int modelProjectMode=-1;
    private ModelTransform modelTransform = ModelTransform.identity();
    private boolean previewReady;
    private boolean moveMode = true;
    private final java.util.concurrent.ExecutorService bedWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final android.os.Handler bedHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private String bedKey = "";
    private BedGeometry loadedBed;
    /** Bed shown before leaving the workspace: drawn at once on return (same printer) while it is re-read, so the
     *  scene scale, and with it the zoom, does not jump from the placeholder grid to the real bed. */
    private BedGeometry staleBed; private String staleBedKey;
    private int bedGeneration;
    private boolean bedPending;
    private volatile boolean visibleWorkspace = true;
    private TextView bedHint; private View workspaceSlice;
    private TextView prepareTab, layersTab, layerCaption, viewportMode;
    private LinearLayout layerPanel; private SeekBar layerSlider;
    private View viewportMove, viewportTransform; private StlView.MoveCallback viewportMoveCallback;
    private boolean layersMode, pendingLayerPreview; private int layerRequest; private Thread layerReader; private GcodeAnalysis layerAnalysis;
    private Uri pendingUri;
    private boolean loadingMesh;
    private int loadSequence;
    private CurrentModelStore pendingPrepared;

    @Override public void onCreate(Bundle state) {
        MdUi.prepare(this); super.onCreate(state);
        if (state != null) {selected = state.getInt("tab", 0);pendingLayerPreview=state.getBoolean("pending_layer_preview",false);tabletDraft=state.getBundle("tablet_settings_draft");}
        render();
        if (state != null && state.getString("pending_model_uri") != null) loadModelUri(Uri.parse(state.getString("pending_model_uri")));
        else if(GeneralPreferences.modelUri(getIntent())==null)restoreModel();
        dispatchNavigationIntent(getIntent());
        if (state == null && BrandSplash.wanted(this)) BrandSplash.attach(this); // cold start from the launcher
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); dispatchNavigationIntent(intent); }
    private void dispatchNavigationIntent(Intent intent) {
        if(intent==null)return;
        Uri external=GeneralPreferences.modelUri(intent);if(external!=null){intent.setData(null);intent.removeExtra(Intent.EXTRA_STREAM);intent.setClipData(null);loadStl(external);return;}
        if(intent.getBooleanExtra("general_start_home",false)){intent.removeExtra("general_start_home");chooseStl();return;}
        if(intent.getBooleanExtra("kepler_show_layers",false)){intent.removeExtra("kepler_show_layers");pendingLayerPreview=true;maybeOpenRequestedLayers();}
        if (!intent.hasExtra("kepler_nav")) return;
        int target = intent.getIntExtra("kepler_nav", 0); intent.removeExtra("kepler_nav");
        if (target == 1) com.keplerslicer.app.glass.KeplerUi.tab(this, new Intent(this, SettingsActivity.class), false);
        else if (target == 2) openExport();
        else if (target == 3) com.keplerslicer.app.glass.KeplerUi.tab(this, new Intent(this, PreferencesActivity.class), false);
        else render();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("pending_layer_preview",pendingLayerPreview);out.putInt("tab", selected);
        Bundle draft = tabletSettings != null ? tabletSettings.saveDraft() : tabletDraft; if (draft != null) out.putBundle("tablet_settings_draft", draft);
        if (pendingUri != null) out.putString("pending_model_uri", pendingUri.toString());
        super.onSaveInstanceState(out);
    }

    private float[] savedCamera; private String cameraModel;
    /** Camera and bed survive app restarts, so the workspace opens exactly as the user left it. */
    private WorkspaceMemory memory() { return new WorkspaceMemory(this); }
    private float[] persistedCamera() { return memory().camera(meshFileName); }
    private void persistCamera() { if (stlView != null) memory().saveCamera(meshFileName, stlView.camera()); }
    @Override protected void onPause() { persistCamera(); cancelLayerWork(); visibleWorkspace = false; refreshAfterResume = true; if (stlView != null) stlView.onPause(); super.onPause(); }
    @Override protected void onResume() {
        super.onResume();
        visibleWorkspace = true;
        SliceActivity.progress = sliceProgress; if (SliceActivity.isSlicing()) showSliceProgress(SliceActivity.status(), true, false);
        if (navBar != null) navBar.setSelectedItem(0, false);
        String storedFile=getSharedPreferences("current_model",0).getString("file","");
        if(!loadingMesh&&!storedFile.isEmpty()&&!storedFile.equals(meshFileName)){restoreModel();}
        if (refreshAfterResume) { if (loadedBed != null) { staleBed = loadedBed; staleBedKey = bedKey; } loadedBed = null; bedPending = false; ++bedGeneration; }
        if (refreshAfterResume && (activeTransformEditor == null || !activeTransformEditor.isShowing())) render();
        else {
            MdUi.prepare(this);
            if (refreshAfterResume && stlView != null && bedHint != null)
                loadSelectedBed(PrinterProfileCatalog.loadSelection(this), stlView, bedHint);
        }
        refreshAfterResume = false;
        if (stlView != null) stlView.onResume();
        maybeOpenRequestedLayers();
        openSetupIfNeeded();
    }
    @Override protected void onDestroy() { if (SliceActivity.progress == sliceProgress) SliceActivity.progress = null; if (tabletSettings != null) { tabletSettings.dispose(); tabletSettings = null; } if(pendingPrepared!=null){pendingPrepared.discard();pendingPrepared=null;}cancelLayerWork(); bedGeneration++; bedHandler.removeCallbacksAndMessages(null); bedWorker.shutdownNow(); super.onDestroy(); }

    private void chooseStl() {
        startActivityForResult(new Intent(this, ImportActivity.class), REQUEST_IMPORT);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if(request==REQUEST_MODEL_DETAILS){if(data!=null&&data.getBooleanExtra("paint_colour",false))pendingPaint="colour";restoreModel();return;}
        if (request == PICK_PRINTER || request == PICK_PROCESS || request == PICK_FILAMENT) { if (request == PICK_PRINTER) setupOpen = false; if (result == RESULT_OK) {render();ControlPreferences.scheduleAutoSlice(this);} return; }
        if ((request != OPEN_STL && request != REQUEST_IMPORT) || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        selected = 0; loadStl(uri);
    }
    private void loadStl(Uri uri) {
        if(getSharedPreferences("current_model",0).getString("file","").isEmpty()){loadModelUri(uri);return;}
        String choice=GeneralPreferences.prefs(this).getString(GeneralPreferences.CHOICE_PROJECT,"ask");
        if("replace".equals(choice)){loadModelUri(uri);return;}
        if("backup_replace".equals(choice)){backupThenLoad(uri);return;}
        android.widget.CheckBox remember=new android.widget.CheckBox(this);remember.setText(R.string.general_remember_choice);
        MdUi.alert(this).setTitle(R.string.general_replace_title).setMessage(R.string.general_replace_detail).setView(remember)
                .setPositiveButton(R.string.general_replace_backup,(d,w)->{if(remember.isChecked())GeneralPreferences.prefs(this).edit().putString(GeneralPreferences.CHOICE_PROJECT,"backup_replace").apply();backupThenLoad(uri);})
                .setNeutralButton(R.string.general_replace,(d,w)->{if(remember.isChecked())GeneralPreferences.prefs(this).edit().putString(GeneralPreferences.CHOICE_PROJECT,"replace").apply();loadModelUri(uri);})
                .setNegativeButton(R.string.general_project_cancel,null).show();
    }
    private void backupThenLoad(Uri uri){new Thread(()->{try{GeneralPreferences.backupNow(this);runOnUiThread(()->{if(!isDestroyed())loadModelUri(uri);});}catch(Exception e){runOnUiThread(()->{if(!isDestroyed())dialog(getString(R.string.general_replace_title),e.getMessage());});}},"backup-before-import").start();}
    private void loadModelUri(Uri uri) { loadModelUri(uri,false); }
    private void loadModelUri(Uri uri,boolean stepReviewed) {loadModelUri(uri,stepReviewed,null);}
    private void loadModelUri(Uri uri,boolean stepReviewed,String preferredName) {
        final int sequence = ++loadSequence;
        pendingUri = uri; loadingMesh = true; render();
        new Thread(() -> {
            try {
                String name = preferredName, mime = null;
                if(name==null)try (android.database.Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0);
                } catch (RuntimeException ignored) { }
                try { mime=getContentResolver().getType(uri); } catch(RuntimeException ignored) { }
                name=ModelFilename.normalize(name,uri.getLastPathSegment(),mime);
                if(!stepReviewed&&(name.endsWith(".step")||name.endsWith(".stp"))&&GeneralPreferences.prefs(this).getBoolean("show_step_import_settings",true)) {
                    runOnUiThread(()->{if(isDestroyed()||sequence!=loadSequence)return;StepImportPreferences.request(this,()->{if(!isDestroyed()&&sequence==loadSequence)loadModelUri(uri,true,preferredName);},()->{if(!isDestroyed()&&sequence==loadSequence){pendingUri=null;loadingMesh=false;render();if(mesh==null)restoreModel();}});});
                    return;
                }
                CurrentModelStore prepared = CurrentModelStore.prepare(this, uri, name);
                GeneralProjectPolicy policy=GeneralProjectPolicy.inspect(prepared.metadata);
                ProjectSettingsSnapshot projectSnapshot=null;EngineProfileSession projectSession=null;String projectProblem="";
                if(policy.project) {
                    try {
                        projectSession=EngineProfileSession.capture(this);
                        ProcessOverridesStore.Identity id=projectSession.id;
                        int plate=prepared.metadata.optInt("selected_plate",-1);
                        projectSnapshot=ProjectSettingsSnapshot.parse(com.keplerslicer.engine.ExperimentalSlicer.projectSettingsSnapshot(
                            id.printer.vendor,id.process.vendor,id.filament.vendor,id.printerName,id.processName,id.filamentName,
                            projectSession.resources.getAbsolutePath(),projectSession.data.getAbsolutePath(),projectSession.selection.overrides,"{}",prepared.file.getAbsolutePath(),plate));
                        prepared.metadata.put("project_settings_snapshot",projectSnapshot.json());
                    } catch(Exception|LinkageError unavailable) {projectProblem=unavailable.getMessage()==null?getString(R.string.native_project_snapshot_unavailable):unavailable.getMessage();}
                }
                final ProjectSettingsSnapshot checkedProject=projectSnapshot;final EngineProfileSession checkedSession=projectSession;final String checkedProblem=projectProblem;
                runOnUiThread(() -> {
                    if (isDestroyed() || sequence != loadSequence) { prepared.discard(); return; }
                    reviewPreparedImport(prepared,uri,sequence,policy,checkedProject,checkedSession,checkedProblem);
                });
            } catch (Exception e) {
                final String message = e.getMessage() == null ? getString(R.string.l10n_ws_file_read_failed) : e.getMessage();
                runOnUiThread(() -> {
                    if (isDestroyed() || sequence != loadSequence) return;
                    pendingUri = null; loadingMesh = false; render();
                    if (mesh == null) restoreModel();
                    dialog(getString(R.string.native_project_open_failed), message);
                });
            }
        }, "stl-loader").start();
    }

    private void reviewPreparedImport(CurrentModelStore prepared,Uri uri,int sequence,GeneralProjectPolicy policy,ProjectSettingsSnapshot snapshot,EngineProfileSession session,String problem){
        if(pendingPrepared!=null&&pendingPrepared!=prepared)pendingPrepared.discard();pendingPrepared=prepared;
        Runnable geometry=()->acceptProjectMode(prepared,uri,sequence,policy,snapshot,session,0,null);
        Runnable project=()->{String mode=GeneralPreferences.projectLoadBehaviour(this);boolean ask=policy.needsDecision(mode)||(snapshot!=null&&snapshot.hasSettings&&!"load_geometry".equals(mode));
            if(!policy.project){publishPreparedImport(prepared,uri,sequence,policy);return;}
            if("load_geometry".equals(mode)||(!ask&&!"load_all".equals(mode))){geometry.run();return;}
            Runnable full=()->chooseProjectPrinter(prepared,uri,sequence,policy,snapshot,session);
            if("load_all".equals(mode)&&snapshot!=null&&(snapshot.canLoad(true)||snapshot.canLoad(false))){full.run();return;}
            String reason=problem;if(snapshot!=null&&!snapshot.canLoad(true)&&!snapshot.canLoad(false))reason=snapshot.validationError(true);
            String message=getString(R.string.native_project_scope_detail)+(reason.isEmpty()?"":"\n\n"+getString(R.string.native_project_unavailable,reason));
            showProjectSheet(getString(R.string.native_project_title),message,new String[]{getString(R.string.native_project_load_full),getString(R.string.native_project_geometry)},
                new boolean[]{snapshot!=null&&(snapshot.canLoad(true)||snapshot.canLoad(false)),true},
                new Runnable[]{full,geometry},()->cancelPreparedImport(prepared,sequence),false,null);
        };
        if(policy.customCodePresent&&!GeneralPreferences.suppressModifiedGcodeWarning(this)){
            android.widget.CheckBox suppress=new android.widget.CheckBox(this);suppress.setText(R.string.phase2_preferences_gcode_warning);
            MdUi.alert(this).setTitle(R.string.general_modified_gcode_title).setMessage(R.string.general_modified_gcode_detail).setView(suppress).setPositiveButton(R.string.general_continue,(d,w)->{if(suppress.isChecked())GeneralPreferences.prefs(this).edit().putBoolean("no_warn_when_modified_gcodes",true).apply();project.run();}).setNegativeButton(R.string.general_project_cancel,(d,w)->cancelPreparedImport(prepared,sequence)).setOnCancelListener(d->cancelPreparedImport(prepared,sequence)).show();
        }else project.run();
    }
    private void chooseProjectPrinter(CurrentModelStore prepared,Uri uri,int sequence,GeneralProjectPolicy policy,ProjectSettingsSnapshot snapshot,EngineProfileSession session){
        if(snapshot==null){cancelPreparedImport(prepared,sequence);return;}
        if(!snapshot.printerDiff){acceptProjectMode(prepared,uri,sequence,policy,snapshot,session,snapshot.canLoad(true)?2:1,null);return;}
        String remembered=GeneralPreferences.prefs(this).getString(ControlPreferences.SYNC_CHOICE,"");
        if("selected_printer".equals(remembered)&&snapshot.canLoad(false)){acceptProjectMode(prepared,uri,sequence,policy,snapshot,session,1,null);return;}
        if("embedded_printer".equals(remembered)&&snapshot.canLoad(true)){acceptProjectMode(prepared,uri,sequence,policy,snapshot,session,2,null);return;}
        final com.keplerslicer.app.glass.GlassSwitch[] remember=new com.keplerslicer.app.glass.GlassSwitch[1];
        Runnable current=()->acceptProjectMode(prepared,uri,sequence,policy,snapshot,session,1,()->{if(remember[0]!=null&&remember[0].isChecked())GeneralPreferences.prefs(this).edit().putString(ControlPreferences.SYNC_CHOICE,"selected_printer").apply();});
        Runnable embedded=()->acceptProjectMode(prepared,uri,sequence,policy,snapshot,session,2,()->{if(remember[0]!=null&&remember[0].isChecked())GeneralPreferences.prefs(this).edit().putString(ControlPreferences.SYNC_CHOICE,"embedded_printer").apply();});
        String message=getString(R.string.native_project_sync_detail,snapshot.selectedPrinterId,snapshot.embeddedPrinterId);
        if(!snapshot.canLoad(false))message+="\n\n"+getString(R.string.native_project_unavailable,snapshot.validationError(false));
        if(!snapshot.canLoad(true))message+="\n\n"+getString(R.string.native_project_unavailable,snapshot.validationError(true));
        showProjectSheet(getString(R.string.native_project_sync_title),message,
            new String[]{getString(R.string.native_project_use_printer),getString(R.string.native_project_keep_printer)},
            new boolean[]{snapshot.canLoad(true),snapshot.canLoad(false)},new Runnable[]{embedded,current},
            ()->cancelPreparedImport(prepared,sequence),true,remember);
    }
    private void showProjectSheet(String title,String message,String[] captions,boolean[] enabled,Runnable[] actions,Runnable cancel,boolean rememberChoice,com.keplerslicer.app.glass.GlassSwitch[] rememberOut){
        com.keplerslicer.app.glass.SheetDialog dialog=new com.keplerslicer.app.glass.SheetDialog(this);
        com.keplerslicer.app.glass.GlassUi glass=new com.keplerslicer.app.glass.GlassUi(this,root,false);
        LinearLayout body=glass.vertical();glass.pad(body,20,20,20,20);body.addView(glass.heading(title));glass.space(body,12);body.addView(glass.text(message,14,400,glass.tokens.textPrimary));
        if(rememberChoice){glass.space(body,10);com.keplerslicer.app.glass.GlassSwitch toggle=new com.keplerslicer.app.glass.GlassSwitch(this);toggle.setContentDescription(getString(R.string.general_remember_choice));rememberOut[0]=toggle;LinearLayout row=glass.horizontal();row.addView(glass.text(getString(R.string.general_remember_choice),14,400,glass.tokens.textPrimary),new LinearLayout.LayoutParams(0,-2,1));row.addView(toggle);body.addView(row);}
        for(int i=0;i<captions.length;i++){final int index=i;glass.space(body,8);TextView action=glass.action(captions[i],i==0,()->{dialog.dismiss();actions[index].run();});action.setEnabled(enabled[i]);action.setAlpha(enabled[i]?1:.45f);body.addView(action);}
        glass.space(body,8);body.addView(glass.action(getString(R.string.general_project_cancel),false,()->{dialog.dismiss();cancel.run();}));dialog.setOnCancelListener(d->cancel.run());
        ScrollView scroll=new ScrollView(this);scroll.addView(body);dialog.setContentView(glass.surface(scroll,glass.tokens.cardRadius));dialog.show();
    }
    private void acceptProjectMode(CurrentModelStore prepared,Uri uri,int sequence,GeneralProjectPolicy policy,ProjectSettingsSnapshot snapshot,EngineProfileSession session,int mode,Runnable afterPublished){
        if(mode>0&&(snapshot==null||session==null||!snapshot.canLoad(mode==2))){cancelPreparedImport(prepared,sequence);return;}
        if(!policy.project){publishPreparedImport(prepared,uri,sequence,policy);return;}
        new Thread(()->{
            try {
                if(mode>0&&!session.stillCurrent(this))throw new java.io.IOException(getString(R.string.native_project_context_changed));
                prepared.setProjectMode(mode);
                if(snapshot!=null)prepared.metadata.put("project_settings_snapshot",snapshot.json());
                runOnUiThread(()->{if(isDestroyed()||sequence!=loadSequence){prepared.discard();return;}if(publishPreparedImport(prepared,uri,sequence,policy)&&afterPublished!=null)afterPublished.run();});
            } catch(Exception error){runOnUiThread(()->{if(isDestroyed()||sequence!=loadSequence){prepared.discard();return;}cancelPreparedImport(prepared,sequence);dialog(getString(R.string.native_project_title),error.getMessage());});}
        },"project-settings-commit").start();
    }
    private void cancelPreparedImport(CurrentModelStore prepared,int sequence){prepared.discard();if(pendingPrepared==prepared)pendingPrepared=null;if(sequence==loadSequence){pendingUri=null;loadingMesh=false;render();if(mesh==null)restoreModel();}}
    private boolean publishPreparedImport(CurrentModelStore prepared,Uri uri,int sequence,GeneralProjectPolicy policy){
        if(isDestroyed()||sequence!=loadSequence){prepared.discard();return false;}
        try{prepared.publish(this);if(pendingPrepared==prepared)pendingPrepared=null;RecentModels.remember(getApplicationContext(),uri,prepared.name,prepared.mesh);mesh=prepared.mesh;meshName=prepared.name;meshFileName=prepared.file.getName();modelTransform=prepared.transform;modelMetadata=prepared.metadata;modelProjectMode=prepared.projectMode;pendingUri=null;loadingMesh=false;render();
            if(prepared.file.getName().endsWith(".obj"))dialog(getString(R.string.l10n_ws_obj_materials),getString(R.string.l10n_ws_obj_note));
            else if(policy.settingsPresent)dialog(getString(R.string.native_project_title),getString(prepared.projectMode>0?R.string.native_project_loaded_full:R.string.native_project_loaded_geometry));
            else if(prepared.metadata!=null&&prepared.metadata.optBoolean("mapping_unresolved",false))dialog(getString(R.string.l10n_ws_plate_unknown),getString(R.string.l10n_ws_plate_mapping_full));
            else if(prepared.metadata!=null&&prepared.metadata.optInt("non_printable_instances",0)>0)dialog(getString(R.string.l10n_ws_objects_on_bed),getString(R.string.l10n_ws_unprintable));
            if(canSliceModel())ControlPreferences.scheduleAutoSlice(this);
            return true;
        }catch(java.io.IOException e){prepared.discard();pendingUri=null;loadingMesh=false;render();if(mesh==null)restoreModel();dialog(getString(R.string.l10n_ws_model_save_failed),e.getMessage());return false;}
    }

    private void restoreModel() {
        final int sequence = ++loadSequence;
        loadingMesh = true; render();
        new Thread(() -> {
            try {
                CurrentModelStore saved = CurrentModelStore.load(this);
                runOnUiThread(() -> {
                    if (isDestroyed() || sequence != loadSequence) return;
                    if (saved != null) { mesh = saved.mesh; meshName = saved.name; meshFileName = saved.file.getName(); modelTransform = saved.transform; modelMetadata=saved.metadata; modelProjectMode=saved.projectMode; }
                    loadingMesh = false; render();
                });
            } catch (Exception e) {
                final String message = e.getMessage() == null ? getString(R.string.l10n_ws_model_read_failed) : e.getMessage();
                runOnUiThread(() -> {
                    if (isDestroyed() || sequence != loadSequence) return;
                    loadingMesh = false; render(); dialog(getString(R.string.l10n_ws_restore_stl_failed), message);
                });
            }
        }, "stl-restore").start();
    }

    private int dp(float n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int color, float radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private GradientDrawable outline(int color, float radius, int stroke) {
        GradientDrawable d = shape(color, radius); d.setStroke(dp(1), stroke); return d;
    }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("kern"); t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); return t;
    }
    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private void pad(View v, int l, int t, int r, int b) { v.setPadding(dp(l), dp(t), dp(r), dp(b)); }
    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); }
    private void gap(LinearLayout l, int h) { View v = new View(this); l.addView(v, lp(1, h)); }
    private void click(View view, Runnable action) { view.setOnClickListener(v -> action.run()); view.setFocusable(true); }
    private void dialog(String title, String message) {
        MdUi.alert(this).setTitle(title).setMessage(message).setPositiveButton(R.string.kv3_ok, null).show();
    }

    private void updatePalette() {
        // Kepler Glass UI v3 is a single dark glass theme (Figma "Kepler v3").
        darkMode = true;
        BG = com.keplerslicer.app.glass.KeplerUi.BASE; SURFACE = com.keplerslicer.app.glass.KeplerUi.GLASS; LINE = com.keplerslicer.app.glass.KeplerUi.GLASS_STROKE;
        INK = com.keplerslicer.app.glass.KeplerUi.WHITE; MUTED = com.keplerslicer.app.glass.KeplerUi.SECONDARY;
        getWindow().setStatusBarColor(Color.rgb(74,79,89)); getWindow().setNavigationBarColor(Color.rgb(56,61,70));
        getWindow().getDecorView().setSystemUiVisibility(0);
    }

    private com.keplerslicer.app.glass.KeplerUi kui;
    private com.keplerslicer.app.glass.KeplerUi.ModeToolbar modeBar;
    private com.keplerslicer.app.glass.GlassNavigation navBar;
    private FrameLayout shellView, addButton;
    private LinearLayout panelHost, prepareGroup;
    private View toolsSheet, previewSheet;
    private TextView plateChip, toolTitle;
    private com.keplerslicer.app.glass.KeplerUi.AxisField[] axisFields;
    private LinearLayout[] toolTiles;
    private int activeTool;
    private boolean toolsMode;

    private void render() {
        refreshAfterResume = false; cancelLayerWork(); layersMode=false; layerAnalysis=null;
        if (stlView != null) { if (java.util.Objects.equals(cameraModel, meshFileName)) savedCamera = stlView.camera(); stlView.onPause(); stlView = null; }
        updatePalette(); selected = 0; toolsMode = false;
        android.content.res.Configuration config = getResources().getConfiguration();
        compactLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && config.screenWidthDp >= 500;
        tabletMode = InterfaceMode.tablet(this); renderedMode = tabletMode;
        kui = new com.keplerslicer.app.glass.KeplerUi(this);
        FrameLayout shell = new FrameLayout(this); shellView = shell; shell.setBackground(kui.backdrop());
        root = vertical(); shell.addView(root, new FrameLayout.LayoutParams(-1,-1));
        if (android.os.Build.VERSION.SDK_INT >= 35) shell.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            root.setPadding(safe.left, safe.top, safe.right, compactLandscape || tabletMode ? safe.bottom : 0); navBottomInset = safe.bottom; layoutFloatingNav(); return insets;
        });
        floatingNav = null;
        setContentView(shell);
        workspace();
        if (tabletMode && sidebar != null) {
            // Tools and the layer preview need a ready model, so a restored session starts on Prepare.
            String section = sidebar.section(); if ("tools".equals(section) || "preview".equals(section) || "objects".equals(section)) section = "prepare";
            sidebar.select(section, false); showTabletSection(section); applyTabletLayout();
        }
        navBar = new com.keplerslicer.app.glass.GlassNavigation(this, 0, false, index -> {
            if (index == 1) com.keplerslicer.app.glass.KeplerUi.tab(this, new Intent(this, SettingsActivity.class), false);
            else if (index == 2) { if (!openExport() && navBar != null) navBar.setSelectedItem(0, true); }
            else if (index == 3) com.keplerslicer.app.glass.KeplerUi.tab(this, new Intent(this, PreferencesActivity.class), false);
        });
        FrameLayout navShell = new FrameLayout(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int available = Math.max(0, View.MeasureSpec.getSize(widthSpec) - dp(32));
                if (navBar != null && navBar.getLayoutParams() != null) navBar.getLayoutParams().width = Math.min(available, dp(600));
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        navShell.addView(navBar, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        // Landscape: the tab bar goes to the bottom of the side column (added below) so the 3D view keeps the full height.
        if (tabletMode) { /* The sidebar rail replaces the tab bar. */ }
        else if (compactLandscape && landscapeSide != null) { LinearLayout.LayoutParams np = lp(-1, -2); np.bottomMargin = dp(8); landscapeSide.addView(navShell, np); }
        else {
            // Portrait: the tab bar floats over the 3D view as clear glass, like on the other tabs.
            floatingNav = navShell; navBar.setClearGlass(true);
            shell.addView(navShell, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            navShell.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> { if (b - t != ob - ot) layoutFloatingNav(); });
            layoutFloatingNav();
        }
    }

    // ---- Tablet workspace (Preferences → Interface → Display mode), arranged like desktop OrcaSlicer: tabs and the
    // slice/export/send actions on top, printer/material/settings on the left, tools above the 3D plate.
    // It shares every action with the mobile layout. ----
    private boolean tabletMode, renderedMode;
    private TabletSidebar sidebar;
    private boolean tabletPrepareScrolls;
    private View tabletToolColumn;
    private ScrollView tabletFloating; private View tabletToolBar;
    private View[] tabletToolButtons;
    private FrameLayout tabletViewport, tabletSliceSlot;
    private View restoreButton, tabletPrepareExtras, tabletObjects, tabletDevice, tabletHead;
    private final java.util.Map<String, TextView> tabletTabs = new java.util.HashMap<>();
    private ProcessSettingsPanel tabletSettings;
    private Bundle tabletDraft;
    private final TabletSidebar.Host tabletHost = new TabletSidebar.Host() {
        @Override public void openSection(String section) { openTabletSection(section); }
        @Override public void openSettings() { com.keplerslicer.app.glass.KeplerUi.tab(MainActivity.this, new Intent(MainActivity.this, SettingsActivity.class), false); }
        @Override public void openPreferences() { com.keplerslicer.app.glass.KeplerUi.tab(MainActivity.this, new Intent(MainActivity.this, PreferencesActivity.class), false); }
        @Override public void sidebarChanged() { applyTabletLayout(); }
    };

    /** Section chosen in the tab bar or the strip: tools and preview use the same workspace modes as the mobile toolbar. */
    private void openTabletSection(String section) {
        if ("tools".equals(section)) openTools();
        else if ("preview".equals(section) && lastResult() == null && !layersMode) { openSlice(); return; } // as on desktop: Preview slices the plate first
        else if ("preview".equals(section)) openLayerPreview();
        else if (layersMode || toolsMode) { showPreparation(); setToolsMode(false); }
        // A mode that could not open (no model yet, not sliced) leaves the panel on Prepare.
        String shown = "tools".equals(section) && !toolsMode || "preview".equals(section) && !layersMode ? "prepare" : section;
        String side = "tools".equals(shown) || "preview".equals(shown) ? "prepare" : shown; // tools and preview keep the Prepare sidebar
        if (sidebar != null && !side.equals(sidebar.section())) sidebar.select(side, false);
        showTabletSection(shown);
    }

    /** Workspace mode changed elsewhere (object card, slicing result): keep the panel in step. */
    private void tabletModeChanged(int mode) {
        if (sidebar == null) return;
        String current = sidebar.section();
        String next = mode == 1 ? "preview" : mode == 2 ? "tools" : "objects".equals(current) || "device".equals(current) ? current : "prepare";
        String side = mode == 0 ? next : "prepare";
        if (!side.equals(current)) sidebar.select(side, false);
        showTabletSection(next);
    }

    private void showTabletSection(String section) {
        boolean prepare = "prepare".equals(section) || "tools".equals(section) || "preview".equals(section); // same sidebar, as in OrcaSlicer
        if (sidebar != null) sidebar.showFill(prepare && !tabletPrepareScrolls);
        if (tabletPrepareScrolls && tabletPrepareExtras != null) tabletPrepareExtras.setVisibility(prepare ? View.VISIBLE : View.GONE);
        if (tabletObjects != null) tabletObjects.setVisibility("objects".equals(section) ? View.VISIBLE : View.GONE);
        if (tabletDevice != null) tabletDevice.setVisibility("device".equals(section) ? View.VISIBLE : View.GONE);
        if (tabletFloating != null) tabletFloating.setVisibility(toolsMode || layersMode || paint != null ? View.VISIBLE : View.GONE);
        // The layer slider takes the right side in Preview.
        if (tabletToolColumn != null) tabletToolColumn.setVisibility("preview".equals(section) ? View.GONE : View.VISIBLE);
        if (tabletToolBar != null) tabletToolBar.setVisibility("preview".equals(section) ? View.GONE : View.VISIBLE); // no object tools in Preview
        for (java.util.Map.Entry<String, TextView> e : tabletTabs.entrySet()) {
            boolean on = e.getKey().equals("tools".equals(section) ? "prepare" : section); // tools belong to Prepare, as in OrcaSlicer
            e.getValue().setTextColor(on ? com.keplerslicer.app.glass.KeplerUi.ON_ACTIVE : com.keplerslicer.app.glass.KeplerUi.WHITE);
            e.getValue().setBackground(on ? kui.shape(com.keplerslicer.app.glass.KeplerUi.MINT, 0, 12) : null);
            e.getValue().setSelected(on);
        }
    }

    /** Moves the 3D view next to the docked sidebar; the GL surface is resized, never recreated. */
    private void applyTabletLayout() {
        if (sidebar == null || tabletViewport == null) return;
        int docked = sidebar.dockedPx();
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) tabletViewport.getLayoutParams();
        if (p.leftMargin != docked) { p.leftMargin = docked; tabletViewport.setLayoutParams(p); }
        if (restoreButton != null) restoreButton.setVisibility(sidebar.state() == TabletLayout.State.HIDDEN ? View.VISIBLE : View.GONE);
        sidebar.setElevation(sidebar.overlay() ? dp(12) : 0);
    }

    /** Current window width in dp (split screen and freeform windows included). */
    private int windowWidthDp() { return Math.round(getWindowManager().getCurrentWindowMetrics().getBounds().width() / getResources().getDisplayMetrics().density); }

    /** Top bar: section tabs on the left, Slice / Export / Send on the right (OrcaSlicer's header). */
    private View buildTabletTopBar() {
        final com.keplerslicer.app.glass.KeplerUi ui = kui;
        LinearLayout bar = horizontal(); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setBackgroundColor(0xFF262A31); bar.setPadding(dp(8), 0, dp(12), 0);
        bar.addView(ui.glassButton(R.drawable.kv3_cog, 44, 22, getString(R.string.tablet_preferences), tabletHost::openPreferences), new LinearLayout.LayoutParams(dp(44), dp(44)));
        // No separate settings hub: process settings live in the Prepare sidebar, printer and filament editors open
        // from their blocks, as on desktop.
        android.widget.HorizontalScrollView tabsScroll = new android.widget.HorizontalScrollView(this); tabsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = horizontal(); tabs.setGravity(Gravity.CENTER_VERTICAL); tabsScroll.addView(tabs);
        // Workspaces of desktop OrcaSlicer; Objects is a block of the Prepare sidebar, not a workspace.
        String[] sections = {"prepare", "preview", "device"};
        int[] titles = {R.string.tablet_prepare, R.string.tablet_preview, R.string.tablet_device};
        int[] icons = {R.drawable.kv3_cube, R.drawable.kv3_layers, R.drawable.kv3_printer};
        tabletTabs.clear();
        for (int i = 0; i < sections.length; i++) {
            final String section = sections[i];
            TextView tab = ui.line(getString(titles[i]), 15, 700, com.keplerslicer.app.glass.KeplerUi.WHITE); tab.setGravity(Gravity.CENTER);
            if (windowWidthDp() < 900) { tab.setContentDescription(tab.getText()); tab.setText(""); } // narrow window: icon-only tabs
            tab.setCompoundDrawablesRelativeWithIntrinsicBounds(ui.icon(icons[i], com.keplerslicer.app.glass.KeplerUi.WHITE), null, null, null); tab.setCompoundDrawablePadding(dp(8));
            tab.setPadding(dp(14), 0, dp(14), 0); tab.setMinHeight(dp(44)); tab.setMinWidth(dp(48));
            tab.setOnClickListener(v -> { if ("device".equals(section)) { com.keplerslicer.app.glass.KeplerUi.tab(this, new Intent(this, DeviceActivity.class), false); return; } // Device is its own workspace
                if (sidebar != null) { if (sidebar.state() == TabletLayout.State.HIDDEN) sidebar.setState(TabletLayout.State.EXPANDED); sidebar.select(section, true); } });
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, dp(44)); tp.leftMargin = dp(i == 0 ? 12 : 4);
            tabs.addView(tab, tp); tabletTabs.put(section, tab);
        }
        bar.addView(tabsScroll, new LinearLayout.LayoutParams(0, -2, 1));
        tabletSliceSlot = new FrameLayout(this);
        bar.addView(tabletSliceSlot, new LinearLayout.LayoutParams(-2, dp(44)));
        return bar;
    }

    /** Slice, Export and Send in the top bar; the slice button keeps its own enabled state. */
    private void pinTabletActions(LinearLayout slice) {
        if (tabletSliceSlot == null) return;
        if (slice.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) slice.getParent()).removeView(slice);
        LinearLayout row = horizontal(); row.setGravity(Gravity.CENTER_VERTICAL);
        int w = windowWidthDp();
        if (w < 600) { for (int i = 0; i < slice.getChildCount(); i++) if (slice.getChildAt(i) instanceof TextView) slice.getChildAt(i).setVisibility(View.GONE); slice.setPadding(0, 0, 0, 0); } // phone: icon-only slice button
        row.addView(slice, new LinearLayout.LayoutParams(dp(w < 600 ? 52 : w < 1100 ? 150 : 180), dp(44)));
        boolean compact = windowWidthDp() < 1100;
        View export = compact ? kui.glassButton(R.drawable.kv3_upload, 44, 22, getString(R.string.tablet_export), () -> openExport()) : kui.secondaryButton(R.drawable.kv3_upload, getString(R.string.tablet_export), 44, 22, () -> openExport());
        View send = compact ? kui.glassButton(R.drawable.kv3_print, 44, 22, getString(R.string.tablet_send), () -> openExport()) : kui.secondaryButton(R.drawable.kv3_print, getString(R.string.tablet_send), 44, 22, () -> openExport());
        // Checking the last result reads the G-code file: off the main thread (it took seconds on a 21 MB result).
        export.setAlpha(.45f); send.setAlpha(.45f);
        bedWorker.execute(() -> { boolean sliced = lastResult() != null; runOnUiThread(() -> { if (isDestroyed()) return; export.setAlpha(sliced ? 1f : .45f); send.setAlpha(sliced ? 1f : .45f); }); });
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(compact ? dp(44) : -2, dp(44)); a.leftMargin = dp(8);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(compact ? dp(44) : -2, dp(44)); b.leftMargin = dp(8);
        row.addView(export, a); row.addView(send, b);
        tabletSliceSlot.addView(row, new FrameLayout.LayoutParams(-2, -1));
    }

    /**
     * Canvas of the tablet workspace, as in OrcaSlicer: object tools in a bar above the 3D area and the plate tools in a
     * column at the right side of the plate (delete, put on plate, arrange, lock, plate settings, move to the front).
     */
    private void addTabletCanvasBar(FrameLayout viewport) {
        if (addButton != null) addButton.setVisibility(View.GONE); // "+" lives in the tool bar
        final com.keplerslicer.app.glass.KeplerUi ui = kui;
        LinearLayout bar = horizontal(); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(6), dp(6), dp(6), dp(6));
        View toggle = ui.glassButton(R.drawable.kv3_sliders, 44, 22, getString(R.string.tablet_collapse), () -> { if (sidebar != null) { if (sidebar.state() == TabletLayout.State.HIDDEN) sidebar.restore(); else sidebar.toggleCollapsed(); } });
        bar.addView(toggle, new LinearLayout.LayoutParams(dp(44), dp(44)));
        View add = ui.glassButton(R.drawable.kv3_plus, 44, 22, getString(R.string.tablet_import), this::chooseStl);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(44), dp(44)); ap.leftMargin = dp(8); bar.addView(add, ap);
        View divider = new View(this); divider.setBackgroundColor(com.keplerslicer.app.glass.KeplerUi.DIVIDER);
        LinearLayout.LayoutParams dv = new LinearLayout.LayoutParams(Math.max(1, dp(1)), dp(28)); dv.setMargins(dp(10), 0, dp(10), 0); bar.addView(divider, dv);
        int[] icons = {R.drawable.kv3_move, R.drawable.kv3_refresh, R.drawable.kv3_scale, R.drawable.kv3_lay_on_face, R.drawable.kv3_cut, R.drawable.kv3_support_paint, R.drawable.kv3_seam_paint, R.drawable.kv3_fuzzy_skin, R.drawable.kv3_text, R.drawable.kv3_measure, R.drawable.kv3_assembly, R.drawable.kv3_brim_ears};
        int[] labels = {R.string.kv3_tool_move, R.string.kv3_tool_rotate, R.string.kv3_tool_scale, R.string.kv3_tool_lay_on_face, R.string.kv3_tool_cut, R.string.kv3_tool_support_paint, R.string.kv3_tool_seam_paint, R.string.kv3_tool_fuzzy_skin, R.string.kv3_tool_text, R.string.kv3_tool_measure, R.string.kv3_tool_assembly, R.string.kv3_tool_brim_ears};
        final View[] toolButtons = new View[icons.length]; final String[] toolNames = new String[icons.length]; final Runnable[] toolActions = new Runnable[icons.length];
        for (int i = 0; i < icons.length; i++) {
            final int index = i; toolNames[i] = getString(labels[i]);
            toolActions[i] = () -> {
                if (!toolsMode) openTools();
                if (toolsMode && toolTiles != null && index < toolTiles.length && toolTiles[index] != null) toolTiles[index].performClick();
            };
            View b = ui.glassButton(icons[i], 44, 22, toolNames[i], toolActions[i]); b.setTooltipText(toolNames[i]); toolButtons[i] = b;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(44), dp(44)); if (i > 0) p.leftMargin = dp(8); bar.addView(b, p);
        }
        // Tools that do not fit go to a "more" menu instead of being cut off at the right edge.
        final View more = ui.glassButton(R.drawable.kv3_more, 44, 22, getString(R.string.tablet_more_tools), null); more.setTooltipText(getString(R.string.tablet_more_tools));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(44), dp(44)); mp.leftMargin = dp(8); bar.addView(more, mp); more.setVisibility(View.GONE);
        more.setOnClickListener(v -> {
            java.util.List<Integer> hidden = new java.util.ArrayList<>(); for (int i = 0; i < toolButtons.length; i++) if (toolButtons[i].getVisibility() != View.VISIBLE) hidden.add(i);
            String[] names = new String[hidden.size()]; for (int i = 0; i < names.length; i++) names[i] = toolNames[hidden.get(i)];
            int[] menuIcons = new int[names.length]; for (int i = 0; i < names.length; i++) menuIcons[i] = icons[hidden.get(i)];
            dropdown(v, menuIcons, names, which -> toolActions[hidden.get(which)].run());
        });
        for (View t : new View[]{toggle, add}) t.setTooltipText(t.getContentDescription());
        tabletToolButtons = toolButtons;
        viewport.addOnLayoutChangeListener((v, l, t, r, btm, ol, ot, or, ob) -> {
            if (r - l == or - ol) return;
            int room = (r - l) - dp(132) - dp(88) - dp(12) - dp(44 + 8 + 44 + 21);   // chip, plate column, padding, toggle + add + divider
            int fit = Math.max(0, room / dp(52));
            boolean overflow = fit < toolButtons.length; if (overflow) fit = Math.max(0, (room - dp(52)) / dp(52));
            for (int i = 0; i < toolButtons.length; i++) toolButtons[i].setVisibility(i < fit ? View.VISIBLE : View.GONE);
            more.setVisibility(overflow ? View.VISIBLE : View.GONE);
        });
        // Scroller between the plate chip and the plate column; the bar is centred and scrolls when it does not fit.
        // The glass pill belongs to the scroller, so a bar that does not fit scrolls inside rounded ends.
        android.widget.HorizontalScrollView holder = new android.widget.HorizontalScrollView(this); holder.setHorizontalScrollBarEnabled(false);
        holder.setBackground(ui.shape(0xE62B2F36, 0x33FFFFFF, 28)); holder.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND); holder.setClipToOutline(true);
        holder.addView(bar);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START); hp.topMargin = dp(16); hp.leftMargin = dp(132); hp.rightMargin = dp(88);
        viewport.addView(holder, hp); tabletToolBar = holder;

        LinearLayout plate = vertical(); plate.setGravity(Gravity.CENTER_HORIZONTAL); plate.setPadding(dp(6), dp(6), dp(6), dp(6)); plate.setBackground(ui.shape(0xE62B2F36, 0x33FFFFFF, 26));
        int count = plateCount(); boolean locked = plateLocked();
        Object[][] actions = {
            {R.drawable.kv3_plate_delete, getString(R.string.plate_delete), (Runnable) this::deletePlate, count > 1 && !locked},
            {R.drawable.kv3_plate_drop, getString(R.string.plate_drop), (Runnable) () -> plateEdit(R.string.plate_drop, NativePreferenceActions.EDIT_DROP), !locked},
            {R.drawable.kv3_plate_arrange, getString(R.string.plate_arrange), (Runnable) () -> plateEdit(R.string.plate_arrange, NativePreferenceActions.EDIT_ARRANGE), !locked},
            {locked ? R.drawable.kv3_plate_lock : R.drawable.kv3_plate_unlock, getString(locked ? R.string.plate_unlock : R.string.plate_lock), (Runnable) this::togglePlateLock, true},
            {R.drawable.kv3_sliders, getString(R.string.plate_settings), (Runnable) this::plateSettings, true},
            {R.drawable.kv3_plate_first, getString(R.string.plate_first), (Runnable) this::movePlateFirst, count > 1 && selectedPlate() > 0 && !locked},
        };
        for (int i = 0; i < actions.length; i++) {
            View b = ui.glassButton((Integer) actions[i][0], 44, 22, (String) actions[i][1], (Runnable) actions[i][2]);
            boolean enabled = (Boolean) actions[i][3]; b.setEnabled(enabled); b.setAlpha(enabled ? 1f : .38f);
            if (i == 3 && locked) b.setBackground(ui.shape(0x405EE0C8, 0x995EE0C8, 22));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(44), dp(44)); if (i > 0) p.topMargin = dp(8); plate.addView(b, p);
        }
        ScrollView column = new ScrollView(this); column.setVerticalScrollBarEnabled(false); column.setFillViewport(true); column.setOutlineProvider(android.view.ViewOutlineProvider.BOUNDS); column.setClipToOutline(true);
        FrameLayout middle = new FrameLayout(this); middle.addView(plate, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL)); column.addView(middle);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -1, Gravity.END); cp.topMargin = dp(84); cp.bottomMargin = dp(16); cp.rightMargin = dp(16);
        viewport.addView(column, cp);
        tabletToolColumn = column;
        View b = ui.glassButton(R.drawable.kv3_chevron_right, 48, 24, getString(R.string.tablet_show), () -> { if (sidebar != null) sidebar.restore(); });
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START | Gravity.CENTER_VERTICAL); rp.leftMargin = dp(16);
        viewport.addView(b, rp); restoreButton = b;
    }

    private int plateCount() {
        org.json.JSONObject project = modelMetadata == null ? null : modelMetadata.optJSONObject("project_data");
        org.json.JSONArray plates = project == null ? null : project.optJSONArray("plates");
        return plates == null ? (mesh == null ? 0 : 1) : plates.length();
    }
    private int selectedPlate() { return modelMetadata == null ? 0 : Math.max(0, modelMetadata.optInt("selected_plate", 0)); }
    private String plateKey() { return (meshFileName == null ? "" : meshFileName) + "#" + selectedPlate(); }
    private android.content.SharedPreferences plates() { return getSharedPreferences("kepler_plates", MODE_PRIVATE); }
    /** A locked plate keeps its models where they are: no tools, no arranging (desktop OrcaSlicer behaviour). */
    // Plate tools exist only in the tablet workspace, so a lock set there does not affect the mobile interface.
    boolean plateLocked() { return tabletMode && meshFileName != null && plates().getBoolean("lock:" + plateKey(), false); }
    private void togglePlateLock() { plates().edit().putBoolean("lock:" + plateKey(), !plateLocked()).apply(); if (plateLocked() && toolsMode) { setToolsMode(false); showPreparation(); } render(); }
    String plateName() { return meshFileName == null ? "" : plates().getString("name:" + plateKey(), ""); }

    private void plateEdit(int title, int op) {
        if (mesh == null || loadingMesh) { dialog(getString(title), getString(R.string.kv3_tools_unavailable)); return; }
        if (plateLocked()) { dialog(getString(title), getString(R.string.plate_locked)); return; }
        runEdit(getString(title), op, 0, 0, null);
    }
    private void deletePlate() {
        final int count = plateCount();
        if (count < 2) { dialog(getString(R.string.plate_delete), getString(R.string.plate_single)); return; }
        if (plateLocked()) { dialog(getString(R.string.plate_delete), getString(R.string.plate_locked)); return; }
        MdUi.alert(this).setTitle(R.string.plate_delete).setMessage(getString(R.string.plate_delete_confirm, selectedPlate() + 1))
            .setNegativeButton(R.string.general_project_cancel, null)
            .setPositiveButton(R.string.plate_delete, (d, w) -> runEdit(getString(R.string.plate_delete), NativePreferenceActions.EDIT_PLATE_DELETE, count, 0, null)).show();
    }
    private void movePlateFirst() {
        final int count = plateCount();
        if (count < 2 || selectedPlate() == 0) { dialog(getString(R.string.plate_first), getString(R.string.plate_single)); return; }
        if (plateLocked()) { dialog(getString(R.string.plate_first), getString(R.string.plate_locked)); return; }
        runEdit(getString(R.string.plate_first), NativePreferenceActions.EDIT_PLATE_FIRST, count, 0, null);
    }

    /** Plate settings (OrcaSlicer: name, bed type, print sequence, vase mode, filament order) in the shared editor. */
    private void plateSettings() {
        final com.keplerslicer.app.glass.KeplerUi ui = kui;
        com.keplerslicer.app.glass.SheetDialog sheet = new com.keplerslicer.app.glass.SheetDialog(this);
        LinearLayout body = ui.column(); body.setPadding(dp(20), dp(20), dp(20), dp(16));
        body.addView(ui.text(getString(R.string.plate_settings), 20, 800, com.keplerslicer.app.glass.KeplerUi.WHITE));
        LinearLayout nameRow = ui.row(); nameRow.setBackground(ui.glass(20)); nameRow.setPadding(dp(16), dp(6), dp(14), dp(6)); nameRow.setMinimumHeight(dp(56));
        nameRow.addView(ui.text(getString(R.string.plate_name), 14, 500, com.keplerslicer.app.glass.KeplerUi.WHITE), new LinearLayout.LayoutParams(0, -2, 1));
        android.widget.EditText name = new android.widget.EditText(this); name.setSingleLine(true); name.setText(plateName()); name.setBackground(null); name.setPadding(0, 0, 0, 0);
        name.setTextSize(14); name.setTextColor(com.keplerslicer.app.glass.KeplerUi.WHITE); com.keplerslicer.app.glass.GlassTypography.apply(name, 600); name.setGravity(Gravity.CENTER_VERTICAL | Gravity.END); name.setContentDescription(getString(R.string.plate_name));
        LinearLayout pill = ui.row(); pill.setBackground(ui.field(12)); pill.setPadding(dp(10), 0, dp(10), 0); pill.addView(name, new LinearLayout.LayoutParams(dp(180), dp(40)));
        nameRow.addView(pill);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2); np.topMargin = dp(16); body.addView(nameRow, np);
        ProcessSettingsPanel editor = new ProcessSettingsPanel(this, () -> ControlPreferences.scheduleAutoSlice(this),
                new java.util.HashSet<>(java.util.Arrays.asList("curr_bed_type", "print_sequence", "spiral_mode", "first_layer_sequence_choice", "first_layer_print_sequence", "other_layers_sequence_choice", "other_layers_print_sequence")));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(420)); ep.topMargin = dp(12); body.addView(editor.view(), ep);
        View close = ui.secondaryButton(0, getString(R.string.glass_dismiss), 48, 24, sheet::dismiss);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(48)); cp.topMargin = dp(4); body.addView(close, cp);
        sheet.setOnDismissListener(d -> { String n = name.getText().toString().trim(); plates().edit().putString("name:" + plateKey(), n).apply(); editor.dispose(); if (viewportMode != null) viewportMode.setText(plateCaption()); });
        body.setBackground(ui.shape(0xFF323741, 0x33FFFFFF, 28)); sheet.setContentView(body);
        sheet.show();
    }


    /**
     * One block of the Prepare column (OrcaSlicer: Printer, Filament, Objects, Process): a compact 44dp header with an
     * optional collapse chevron and header icons, then a glass card. Collapsed state is remembered per block.
     */
    private LinearLayout tabletBlock(LinearLayout parent, int icon, String title, String collapseKey, View... headerIcons) {
        final com.keplerslicer.app.glass.KeplerUi ui = kui;
        LinearLayout head = horizontal(); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(dp(16), dp(2), dp(8), dp(2)); head.setMinimumHeight(dp(44));
        head.addView(ui.image(icon, 18, com.keplerslicer.app.glass.KeplerUi.WHITE), new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView t = ui.line(title, 15, 800, com.keplerslicer.app.glass.KeplerUi.WHITE); LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1); tp.leftMargin = dp(10); head.addView(t, tp);
        for (View v : headerIcons) { LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(40), dp(40)); ip.leftMargin = dp(6); ip.topMargin = dp(2); ip.bottomMargin = dp(2); head.addView(v, ip); }
        parent.addView(head, lp(-1, -2));
        LinearLayout body = ui.card(16); LinearLayout.LayoutParams bp = lp(-1, -2); bp.setMargins(dp(12), 0, dp(12), dp(4)); parent.addView(body, bp);
        if (collapseKey != null) {
            final android.content.SharedPreferences prefs = getSharedPreferences("app_preferences", MODE_PRIVATE);
            final String key = "tablet_block_" + collapseKey;
            final android.widget.ImageView chevron = ui.image(R.drawable.kv3_chevron_down, 18, com.keplerslicer.app.glass.KeplerUi.SECONDARY);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(18), dp(18)); cp.leftMargin = dp(8); head.addView(chevron, 2, cp);
            Runnable apply = () -> { boolean open = prefs.getBoolean(key, !"objects".equals(collapseKey)); body.setVisibility(open ? View.VISIBLE : View.GONE); chevron.setRotation(open ? 0 : -90);
                head.setContentDescription(title + ", " + getString(open ? R.string.tablet_collapse : R.string.tablet_expand)); };
            apply.run();
            kui.pressable(head, null, 12);
            head.setOnClickListener(v -> { prefs.edit().putBoolean(key, !prefs.getBoolean(key, !"objects".equals(collapseKey))).apply(); apply.run(); });
        }
        return body;
    }

    /**
     * One-line block (OrcaSlicer combo with its section caption): icon, caption over the chosen preset, chevron, then
     * header icons. Used for Printer, Filament and Process so the settings editor keeps most of the height.
     */
    private void tabletInline(LinearLayout parent, int icon, String caption, String value, Runnable action, View... icons) {
        final com.keplerslicer.app.glass.KeplerUi ui = kui;
        LinearLayout row = horizontal(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(8), dp(4), dp(6), dp(4));
        LinearLayout pick = horizontal(); pick.setGravity(Gravity.CENTER_VERTICAL); pick.setPadding(dp(8), dp(4), dp(8), dp(4)); pick.setMinimumHeight(dp(48));
        pick.addView(ui.image(icon, 18, com.keplerslicer.app.glass.KeplerUi.WHITE), new LinearLayout.LayoutParams(dp(18), dp(18)));
        LinearLayout labels = vertical(); LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, -2, 1); lp2.leftMargin = dp(12);
        labels.addView(ui.line(caption, 12, 600, com.keplerslicer.app.glass.KeplerUi.SECONDARY));
        TextView v = ui.line(value, 14, 700, com.keplerslicer.app.glass.KeplerUi.WHITE); v.setMaxLines(1); v.setEllipsize(android.text.TextUtils.TruncateAt.END); labels.addView(v);
        pick.addView(labels, lp2);
        pick.addView(ui.image(R.drawable.kv3_chevron_down, 18, com.keplerslicer.app.glass.KeplerUi.WHITE), new LinearLayout.LayoutParams(dp(18), dp(18)));
        ui.pressable(pick, null, 14); pick.setContentDescription(caption + ": " + value); pick.setOnClickListener(x -> action.run());
        row.addView(pick, new LinearLayout.LayoutParams(0, -2, 1));
        for (View b : icons) { LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(40), dp(40)); ip.leftMargin = dp(6); row.addView(b, ip); }
        row.setBackground(ui.glass(16));
        LinearLayout.LayoutParams p = lp(-1, -2); p.setMargins(dp(12), dp(4), dp(12), dp(4)); parent.addView(row, p);
    }

    private View tabletIcon(int icon, String description, Runnable action) { return kui.glassButton(icon, 40, 20, description, action); }

    /** Compact selector row (OrcaSlicer combo box): label, optional detail and a chevron, 48dp tall. */
    private View tabletSelector(String label, Runnable action) { return tabletSelector(label, null, action); }
    private View tabletSelector(String label, String detail, Runnable action) {
        LinearLayout row = horizontal(); row.setGravity(Gravity.CENTER_VERTICAL); row.setMinimumHeight(dp(48)); row.setPadding(dp(14), dp(4), dp(12), dp(4));
        LinearLayout labels = vertical();
        TextView t = kui.line(label, 14, 600, com.keplerslicer.app.glass.KeplerUi.WHITE); labels.addView(t);
        if (detail != null && !detail.isEmpty()) labels.addView(kui.line(detail, 12, 500, com.keplerslicer.app.glass.KeplerUi.SECONDARY));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(kui.image(R.drawable.kv3_chevron_down, 18, com.keplerslicer.app.glass.KeplerUi.WHITE), new LinearLayout.LayoutParams(dp(18), dp(18)));
        kui.pressable(row, null, 14); row.setContentDescription(label); row.setOnClickListener(v -> action.run()); return row;
    }

    /** Prepare column (Printer, Filament, Objects, Process with the embedded editor) and the Device block. */
    private void buildTabletSections(LinearLayout side) {
        final com.keplerslicer.app.glass.KeplerUi ui = kui; final int white = com.keplerslicer.app.glass.KeplerUi.WHITE;
        // Short windows (phones in landscape): the whole Prepare column scrolls and the editor gets a screen of height.
        int heightDp = Math.round(getWindowManager().getCurrentWindowMetrics().getBounds().height() / getResources().getDisplayMetrics().density);
        tabletPrepareScrolls = heightDp < 700;
        LinearLayout fill = tabletPrepareScrolls ? vertical() : sidebar.fill;
        if (tabletPrepareScrolls) side.addView(fill, lp(-1, -2));
        PrinterProfileCatalog.Profile printerProfile = PrinterProfileCatalog.loadSelection(this);
        // Printer, filament and process are one line each (caption over the chosen preset), as the desktop combos.
        String printerLabel = printerProfile == null ? getString(R.string.kv3_choose_printer) : (printerProfile.model == null || printerProfile.model.isEmpty() ? printerProfile.name : printerProfile.model);
        if (printerProfile != null && printerProfile.variant != null && !printerProfile.variant.isEmpty()) printerLabel += " · " + getString(R.string.kv3_nozzle, printerProfile.variant);
        if (modelProjectMode == 2) printerLabel = projectOptionCaption("printer_settings_id", getString(R.string.native_project_title));
        tabletInline(fill, R.drawable.kv3_printer, getString(R.string.phase2_settings_printer), printerLabel, modelProjectMode == 2 ? this::openSettingsPanel : this::openPrinterProfiles,
                tabletIcon(R.drawable.kv3_wifi, getString(R.string.tablet_connection), () -> startActivity(new Intent(this, DeviceActivity.class))),
                tabletIcon(R.drawable.kv3_sliders, getString(R.string.kv3_edit_printer), () -> com.keplerslicer.app.glass.KeplerUi.tab(this, new Intent(this, SettingsActivity.class).putExtra("kepler_kind", "printer"), false)));
        PrintProfileCatalog.Profile filament = PrintProfileCatalog.loadSelection(this, PrintProfileCatalog.FILAMENT);
        MaterialSlots.Slot[] slots = MaterialSlots.load(this);
        StringBuilder more = new StringBuilder();
        for (int i = 1; i < slots.length; i++) if (slots[i].filled()) more.append(" · ").append(i + 1).append(": ").append(EngineText.clean(slots[i].name));
        String filamentLabel = (filament == null ? getString(R.string.kv3_choose_material) : (more.length() > 0 ? "1: " : "") + filament.name) + more;
        tabletInline(fill, R.drawable.kv3_spool, getString(R.string.phase2_settings_filament), filamentLabel, () -> openPrintProfile(PrintProfileCatalog.FILAMENT, PICK_FILAMENT),
                tabletIcon(R.drawable.kv3_sliders, getString(R.string.phase2_settings_filament), () -> com.keplerslicer.app.glass.KeplerUi.tab(this, new Intent(this, SettingsActivity.class).putExtra("kepler_kind", "filament"), false)));
        // Objects of the plate (OrcaSlicer keeps the object list in the Prepare sidebar, not in a workspace tab).
        LinearLayout objects = tabletBlock(fill, R.drawable.kv3_assembly, getString(R.string.tablet_objects), "objects",
                tabletIcon(R.drawable.kv3_sliders, getString(R.string.tablet_object_details), this::openModelDetails));
        org.json.JSONArray items = modelMetadata == null ? null : modelMetadata.optJSONArray("objects");
        if (mesh == null || loadingMesh) objects.addView(tabletSelector(getString(R.string.tablet_no_objects), this::chooseStl));
        else if (items == null || items.length() == 0) objects.addView(tabletSelector(meshName, modelDetails(), modelMetadata == null ? this::openTools : this::openModelDetails));
        else for (int i = 0; i < items.length(); i++) {
            org.json.JSONObject o = items.optJSONObject(i); String name = o == null ? "" : o.optString("name", "");
            if (i > 0) objects.addView(ui.hairline());
            objects.addView(tabletSelector(name.isEmpty() ? getString(R.string.tablet_objects) + " " + (i + 1) : name, this::openModelDetails));
        }
        tabletObjects = null;
        // Process: preset and the embedded editor, which takes the rest of the height and scrolls by itself.
        PrintProfileCatalog.Profile process = PrintProfileCatalog.loadSelection(this, PrintProfileCatalog.PROCESS);
        tabletInline(fill, R.drawable.kv3_layers, getString(R.string.tablet_print_settings), process == null ? getString(R.string.kv3_choose_process) : process.name, () -> openPrintProfile(PrintProfileCatalog.PROCESS, PICK_PROCESS),
                tabletIcon(R.drawable.kv3_sliders, getString(R.string.kv3_process), () -> com.keplerslicer.app.glass.KeplerUi.tab(this, new Intent(this, SettingsActivity.class).putExtra("kepler_kind", "process"), false)));
        if (printerProfile != null && process != null && modelProjectMode <= 0) {
            // The editor outlives workspace rebuilds: same view, same scroll position and unsaved edits; it reloads
            // only when the printer, filament or process really changed.
            if (tabletSettings == null) {
                tabletSettings = new ProcessSettingsPanel(this, () -> ControlPreferences.scheduleAutoSlice(this));
                if (tabletDraft != null) { tabletSettings.restoreDraft(tabletDraft); tabletDraft = null; }
            } else tabletSettings.reloadIfStale();
            View editor = tabletSettings.view(); editor.setBackgroundColor(0);
            if (editor.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) editor.getParent()).removeView(editor);
            LinearLayout.LayoutParams ep = tabletPrepareScrolls ? new LinearLayout.LayoutParams(-1, dp(Math.max(320, heightDp - 140))) : new LinearLayout.LayoutParams(-1, 0, 1); ep.setMargins(0, dp(4), 0, 0);
            fill.addView(editor, ep);
        } else {
            if (tabletSettings != null) { tabletSettings.dispose(); tabletSettings = null; }
            if (!tabletPrepareScrolls) fill.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));
        }
        tabletPrepareExtras = fill;

        LinearLayout device = vertical();
        LinearLayout rows = ui.card(20);
        rows.addView(ui.listRow(R.drawable.kv3_wifi, getString(R.string.tablet_connection), getString(R.string.tablet_connection_detail), ui.image(R.drawable.kv3_chevron_right, 20, white), () -> startActivity(new Intent(this, DeviceActivity.class))));
        rows.addView(ui.hairline());
        rows.addView(ui.listRow(R.drawable.kv3_print, getString(R.string.tablet_send), getString(R.string.tablet_send_detail), ui.image(R.drawable.kv3_chevron_right, 20, white), () -> openExport()));
        rows.addView(ui.hairline());
        rows.addView(ui.listRow(R.drawable.kv3_printer, getString(R.string.tablet_network), getString(R.string.tablet_network_detail), ui.image(R.drawable.kv3_chevron_right, 20, white), () -> startActivity(new Intent(this, NetworkActivity.class))));
        device.addView(rows, tabletCardParams());
        side.addView(device, lp(-1, -2)); tabletDevice = device;
    }

    private LinearLayout.LayoutParams tabletCardParams() { LinearLayout.LayoutParams p = lp(-1, -2); p.setMargins(dp(12), 0, dp(12), dp(12)); return p; }

    /** Hardware keyboard: Ctrl+O import, Ctrl+Enter slice; tablet panel: Ctrl+B collapse, Ctrl+\ hide, Ctrl+1..5 sections. */
    @Override public boolean onKeyShortcut(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_O) { chooseStl(); return true; }
        if (keyCode == android.view.KeyEvent.KEYCODE_ENTER && workspaceSlice != null && workspaceSlice.isEnabled()) { openSlice(); return true; }
        if (sidebar != null) {
            if (keyCode == android.view.KeyEvent.KEYCODE_B) { if (sidebar.state() == TabletLayout.State.HIDDEN) sidebar.restore(); else sidebar.toggleCollapsed(); return true; }
            if (keyCode == android.view.KeyEvent.KEYCODE_BACKSLASH) { if (sidebar.state() == TabletLayout.State.HIDDEN) sidebar.restore(); else sidebar.setState(TabletLayout.State.HIDDEN); return true; }
            int index = keyCode - android.view.KeyEvent.KEYCODE_1;
            if (index >= 0 && index < TabletLayout.SECTIONS.length) { if (sidebar.state() == TabletLayout.State.HIDDEN) sidebar.setState(TabletLayout.State.EXPANDED); sidebar.select(TabletLayout.SECTIONS[index], true); return true; }
        }
        return super.onKeyShortcut(keyCode, event);
    }

    /** Side column of the landscape workspace; the tab bar is added to its bottom. */
    private LinearLayout landscapeSide;
    private View floatingNav; private int navBottomInset; private float sceneLift;
    /** Keeps the floating tab bar above the gesture bar and the bottom panels above the tab bar. */
    private void layoutFloatingNav() {
        if (floatingNav == null || !(floatingNav.getLayoutParams() instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) floatingNav.getLayoutParams();
        int lift = dp(24) + navBottomInset;
        if (p.bottomMargin != lift) { p.bottomMargin = lift; floatingNav.setLayoutParams(p); }
        int reserve = (floatingNav.getHeight() > 0 ? floatingNav.getHeight() : dp(72)) + lift + dp(10);
        if (panelHost != null && panelHost.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams hp = (FrameLayout.LayoutParams) panelHost.getLayoutParams();
            if (hp.bottomMargin != reserve) { hp.bottomMargin = reserve; panelHost.setLayoutParams(hp); }
        }
    }

    private String plateCaption() { String base = plateCaptionBase(), name = plateName(); return name.isEmpty() ? base : base + " · " + name; }
    private String plateCaptionBase() {
        org.json.JSONObject project = modelMetadata == null ? null : modelMetadata.optJSONObject("project_data");
        org.json.JSONArray plates = project == null ? null : project.optJSONArray("plates");
        int plate = modelMetadata == null ? 0 : Math.max(0, modelMetadata.optInt("selected_plate", 0));
        if (plates != null && plates.length() > 1) return getString(R.string.kv3_plate, Math.min(plate, plates.length() - 1) + 1, plates.length());
        return getString(R.string.kv3_plate_single, plate + 1);
    }

    private void workspace() {
        landscapeSide = null;
        sidebar = null; tabletViewport = null; tabletSliceSlot = null; restoreButton = null; tabletHead = null; tabletPrepareExtras = tabletObjects = tabletDevice = null;
        PrinterProfileCatalog.Profile printer = PrinterProfileCatalog.loadSelection(this);
        PrintProfileCatalog.Profile material = PrintProfileCatalog.loadSelection(this, PrintProfileCatalog.FILAMENT);
        final com.keplerslicer.app.glass.KeplerUi ui = kui;
        Runnable openPrinter = modelProjectMode==2 ? this::openSettingsPanel : this::openPrinterProfiles;
        com.keplerslicer.app.glass.KeplerUi.Header head = ui.header("", openPrinter, getString(R.string.kv3_connections),
                () -> startActivity(new Intent(this, DeviceActivity.class)));
        if (tabletMode) head.wifi.setVisibility(View.GONE); // Wi-Fi: the Device section has it
        TextView printerName = head.name;
        printerName.setText(printer == null ? getString(R.string.kv3_choose_printer) : (printer.model == null || printer.model.isEmpty() ? printer.name : printer.model));
        if (modelProjectMode==2) printerName.setText(projectOptionCaption("printer_settings_id", getString(R.string.native_project_title)));
        if (printer != null && printer.variant != null && !printer.variant.isEmpty()) {
            String nozzle = modelProjectMode==2 ? projectOptionCaption("nozzle_diameter", printer.variant) : printer.variant;
            ui.setNozzle(head, nozzle.matches("[0-9]+([.,][0-9]+)?") ? getString(R.string.kv3_nozzle, nozzle) : nozzle);
        }
        TextView materialLabel = head.material;
        materialLabel.setText(material == null ? getString(R.string.kv3_choose_material) : material.name);
        if (material != null && modelProjectMode <= 0) { PrintProfileCatalog.Profile process = PrintProfileCatalog.loadSelection(this, PrintProfileCatalog.PROCESS); java.util.regex.Matcher layer = process == null ? null : java.util.regex.Pattern.compile("^([0-9]+(?:[.,][0-9]+)?[ ]*mm)").matcher(process.name); if (layer != null && layer.find()) materialLabel.setText(material.name + " · " + layer.group(1)); }
        if (modelProjectMode>0) materialLabel.setText(projectOptionCaption("filament_settings_id", getString(R.string.phase2_settings_filament)));
        head.printer.setContentDescription(getString(R.string.kv3_printer_header, printerName.getText()));
        { View materialRow = (View) materialLabel.getParent(); materialRow.setMinimumHeight(dp(24)); com.keplerslicer.app.glass.TouchTarget.ensure(materialRow); materialRow.setContentDescription(getString(R.string.kv3_choose_material) + ": " + materialLabel.getText()); click(materialRow, () -> { if (modelProjectMode > 0) openSettingsPanel(); else openPrintProfile(PrintProfileCatalog.FILAMENT, PICK_FILAMENT); }); }
        if (modelProjectMode<=0) loadUserPresetNames(printerName, materialLabel, new TextView(this));
        LinearLayout.LayoutParams hp = lp(-1, -2); hp.setMargins(dp(20), dp(8), dp(20), 0);

        LinearLayout wideBody = null, side = null; FrameLayout tabletBody = null;
        if (tabletMode) {
            tabletBody = new FrameLayout(this);
            sidebar = new TabletSidebar(this, ui, tabletHost);
            side = sidebar.content;
            tabletHead = head.view;
            root.addView(buildTabletTopBar(), new LinearLayout.LayoutParams(-1, dp(56)));
            root.addView(tabletBody, new LinearLayout.LayoutParams(-1,0,1));
        } else if (compactLandscape) {
            wideBody = horizontal(); wideBody.setGravity(Gravity.TOP); side = vertical();
            LinearLayout.LayoutParams sh = lp(-1,-2); sh.setMargins(dp(16), dp(8), dp(4), 0); side.addView(head.view, sh);
            wideBody.addView(side, new LinearLayout.LayoutParams(Math.min(dp(380), getResources().getDisplayMetrics().widthPixels * 2 / 5), -1));
            root.addView(wideBody, new LinearLayout.LayoutParams(-1,0,1));
        } else root.addView(head.view, hp);

        FrameLayout viewport = new com.keplerslicer.app.glass.TouchTarget.Host(this); workspaceViewport = viewport;
        // Returning to the workspace rebuilds the 3D view: keep the camera where the user left it (same model only).
        if (stlView != null && java.util.Objects.equals(cameraModel, meshFileName)) savedCamera = stlView.camera();
        else if (!java.util.Objects.equals(cameraModel, meshFileName)) savedCamera = null;
        if (savedCamera == null || loadingMesh || mesh == null) { float[] stored = persistedCamera(); if (stored != null) savedCamera = stored; }
        StlView view = new StlView(this, mesh, projectBounds(modelMetadata)); sceneLift = 0; if (savedCamera != null) view.setCamera(savedCamera); cameraModel = meshFileName; stlView = view; view.setDarkMode(true); int[] slotColours = triangleSlotColours(mesh, modelMetadata); if (slotColours != null) view.setTriangleColors(slotColours);
        view.setBackdrop(kui.backdrop(), shellView);
        viewport.addView(view, new FrameLayout.LayoutParams(-1,-1));

        TextView chip = ui.chip(plateCaption(), false, this::choosePlate); plateChip = chip; viewportMode = chip;
        FrameLayout.LayoutParams chipPos = new FrameLayout.LayoutParams(-2,-2,Gravity.START|Gravity.TOP); chipPos.setMargins(dp(20), dp(18), dp(96), 0); viewport.addView(chip, chipPos);

        boolean hasModel = mesh != null && !loadingMesh; final boolean editable = hasModel && mesh.vertices.length>0 && transformSupported();
        modeBar = ui.modeToolbar(new String[]{getString(R.string.kv3_mode_prepare), getString(R.string.kv3_mode_preview), getString(R.string.kv3_mode_tools)},
                new Runnable[]{ () -> { showPreparation(); setToolsMode(false); }, this::openLayerPreview, this::openTools });
        FrameLayout.LayoutParams toolPosition = new FrameLayout.LayoutParams(-2,-2,Gravity.END|Gravity.TOP); toolPosition.setMargins(0, dp(16), dp(20), 0); viewport.addView(modeBar.view, toolPosition);
        if (tabletMode) { modeBar.view.setVisibility(View.GONE); modeBar.onSelect = this::tabletModeChanged; }
        View transform = modeBar.items[2]; transform.setEnabled(false); transform.setAlpha(.38f);

        FrameLayout add = ui.glassButton(R.drawable.kv3_plus, 56, 26, getString(R.string.kv3_add_model), this::chooseStl); addButton = add;
        FrameLayout.LayoutParams plus = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.END|Gravity.BOTTOM); plus.setMargins(0, 0, dp(20), dp(12)); viewport.addView(add, plus);

        TextView hint = ui.text(loadingMesh ? getString(R.string.kv3_reading_model) : mesh == null ? getString(R.string.kv3_no_model_detail) : mesh.vertices.length==0 ? getString(R.string.l10n_ws_plate_empty) : modelMetadata!=null && modelMetadata.optBoolean("mapping_unresolved",false) ? getString(R.string.l10n_ws_plate_unknown_all) : getString(R.string.l10n_ws_grid_loading), 10, 600, com.keplerslicer.app.glass.KeplerUi.SECONDARY);
        hint.setAlpha(tabletMode ? 0f : .6f); hint.setMaxLines(2); bedHint = hint; // tablet: no mobile caption over the plate
        FrameLayout.LayoutParams hintPosition = new FrameLayout.LayoutParams(-2,-2,Gravity.START|Gravity.BOTTOM); hintPosition.setMargins(dp(20), 0, dp(92), dp(18)); viewport.addView(hint, hintPosition);

        statusPill = ui.statusPill(com.keplerslicer.app.glass.KeplerUi.MINT, getString(R.string.kv3_sliced_plate, 1));
        statusPill.setBackground(ui.shape(0x2E5EE0C8, 0x805EE0C8, 18)); statusPill.setVisibility(View.GONE);
        viewport.addView(statusPill, new FrameLayout.LayoutParams(chipPos));
        layerRail = new com.keplerslicer.app.glass.LayerRail(this); layerRail.setContentDescription(getString(R.string.kv3_layer, 1)); layerRail.setVisibility(View.GONE);
        FrameLayout.LayoutParams railPos = new FrameLayout.LayoutParams(dp(44), dp(150), Gravity.END|Gravity.TOP); railPos.setMargins(0, dp(192), dp(20), 0); viewport.addView(layerRail, railPos);
        layerRail.setListener(p -> { if (layerSlider != null) layerSlider.setProgress(p); });
        layerPanel = buildPreviewSheet(); layerPanel.setVisibility(View.GONE);

        if (tabletMode) { tabletBody.addView(viewport, new FrameLayout.LayoutParams(-1,-1)); tabletBody.addView(sidebar, new FrameLayout.LayoutParams(-2,-1,Gravity.START)); tabletViewport = viewport; addTabletCanvasBar(viewport); }
        else if (compactLandscape) wideBody.addView(viewport, new LinearLayout.LayoutParams(0,-1,1));
        else root.addView(viewport, new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout host = vertical(); panelHost = host;
        if (tabletMode) {
            buildTabletSections(side);
            // Tool values, paint and layer preview panels float over the plate (desktop gizmo panels), not in the sidebar.
            ScrollView floating = new ScrollView(this); floating.setVerticalScrollBarEnabled(false); floating.addView(host); tabletFloating = floating;
            FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(dp(380), -2, Gravity.TOP | Gravity.START); fl.topMargin = dp(80); fl.leftMargin = dp(4); fl.bottomMargin = dp(16);
            viewport.addView(floating, fl);
        }
        else if (compactLandscape) { side.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1)); LinearLayout.LayoutParams hostLp = lp(-1,-2); hostLp.setMargins(dp(4), 0, dp(4), dp(8)); side.addView(host, hostLp); landscapeSide = side; }
        else {
            FrameLayout.LayoutParams hostLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM); hostLp.bottomMargin = dp(96) + navBottomInset;
            viewport.addView(host, hostLp);
            final FrameLayout.LayoutParams plusLp = plus, hintLp = hintPosition; final View addView = add, hintView = hint;
            host.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
                int above = (b - t) + ((FrameLayout.LayoutParams) v.getLayoutParams()).bottomMargin;
                if (plusLp.bottomMargin != above + dp(12)) { plusLp.bottomMargin = above + dp(12); addView.setLayoutParams(plusLp); }
                if (hintLp.bottomMargin != above + dp(18)) { hintLp.bottomMargin = above + dp(18); hintView.setLayoutParams(hintLp); }
                // A tall panel (tools, layer preview) covers the lower scene: lift the 3D view so the model stays in
                // the free area above it, without resizing its surface (resizing made the view flash black).
                StlView scene = stlView; if (scene != null) {
                    float lift = -Math.max(0, (b - t) - dp(150)) / 2f;
                    if (Math.abs(sceneLift - lift) > 1) {
                        com.keplerslicer.app.glass.Spring sp = com.keplerslicer.app.glass.Spring.smooth(0.4f);
                        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(sceneLift, lift); sceneLift = lift;
                        va.setDuration(sp.duration()); va.setInterpolator(sp); va.addUpdateListener(x -> { if (stlView == scene) scene.setSceneShift(-(float) x.getAnimatedValue()); }); va.start();
                    }
                }
            });
            layoutFloatingNav();
        }

        LinearLayout prepare = vertical(); prepareGroup = prepare;
        if (mesh != null && !loadingMesh) {
            com.keplerslicer.app.glass.KeplerUi.ObjectCard card = ui.objectCard(modelMetadata==null ? this::openTools : this::openModelDetails);
            card.name.setText(meshName); card.details.setText(modelDetails());
            card.view.setContentDescription(meshName + ". " + card.details.getText());
            if (needsProjectModeReview()) {
                card.details.setText(getString(R.string.native_project_legacy_mode));
                TextView review = ui.line(getString(R.string.native_project_review_existing), 12, 700, com.keplerslicer.app.glass.KeplerUi.WHITE);
                review.setMinHeight(dp(36)); review.setGravity(Gravity.CENTER_VERTICAL); card.labels.addView(review);
                click(review, () -> { String expected=meshFileName; if(expected==null||!expected.equals(getSharedPreferences("current_model",0).getString("file",""))){restoreModel();return;} loadModelUri(Uri.fromFile(new java.io.File(new java.io.File(getFilesDir(),"models"),expected)),false,meshName); });
            }
            LinearLayout.LayoutParams cardParams = lp(-1,-2); cardParams.setMargins(dp(compactLandscape || tabletMode ? 12 : 20), 0, dp(compactLandscape || tabletMode ? 12 : 20), dp(12));
            prepare.addView(card.view, cardParams);
        }
        LinearLayout slice = ui.primaryButton(R.drawable.kv3_play, getString(R.string.kv3_slice_plate), this::openSlice);
        workspaceSlice = slice;
        LinearLayout.LayoutParams sliceParams = lp(-1, compactLandscape ? 48 : 52); sliceParams.setMargins(dp(compactLandscape || tabletMode ? 12 : 20), 0, dp(compactLandscape || tabletMode ? 12 : 20), dp(12));
        prepare.addView(slice, sliceParams);
        host.addView(prepare, lp(-1,-2));
        if (tabletMode) pinTabletActions(slice);

        toolsSheet = buildToolsSheet(); toolsSheet.setVisibility(View.GONE);
        LinearLayout.LayoutParams sheetParams = lp(-1,-2); sheetParams.setMargins(dp(12), 0, dp(12), dp(8)); host.addView(toolsSheet, sheetParams);
        LinearLayout.LayoutParams previewParams = lp(-1,-2); previewParams.setMargins(dp(12), 0, dp(12), dp(8)); host.addView(layerPanel, previewParams);

        previewReady = mesh == null; ui.setEnabled(slice, false);
        View move = toolTiles[0];
        StlView.MoveCallback moved = next -> commitDrag(view, next, transform, move); viewportMove = move; viewportTransform = transform; viewportMoveCallback = moved;
        if (hasModel) view.setModelTransform(modelTransform, error -> {
            if (isDestroyed() || stlView != view) return;
            previewReady = error == null; transform.setEnabled(previewReady&&hasModel); transform.setAlpha(previewReady&&hasModel ? 1 : .38f);
            if (previewReady && pendingPaint != null) { String kind = pendingPaint; pendingPaint = null; setToolsMode(true); startPaint(kind); }
            view.setMoveMode(previewReady&&editable&&moveMode&&toolsMode&&activeTool==0, moved);
            boolean sliceable = previewReady&&canSliceModel()&&materialChosen(); slice.setEnabled(true); slice.setAlpha(sliceable ? 1f : .42f);
            refreshToolFields();
            if (error != null) {pendingLayerPreview=false;dialog(getString(R.string.l10n_ws_update_failed),error.getMessage());} else maybeOpenRequestedLayers();
        });
        loadSelectedBed(printer, view, hint);
        final android.content.Context appContext = getApplicationContext();
        bedWorker.execute(() -> { String plate = ""; try { com.keplerslicer.app.settings.PresetRepository repo = SettingsState.repository(appContext);
            plate = repo.load(SettingsState.activeId(appContext, "process")).effective.optString("curr_bed_type", ""); if (plate.isEmpty()) plate = repo.load(SettingsState.activeId(appContext, "printer")).effective.optString("default_bed_type", ""); } catch (Exception ignored) { }
            final String label = plate; runOnUiThread(() -> { if (!isDestroyed() && stlView == view) view.setPlateLabel(label); }); });
    }

    private String modelDetails() {
        String name = meshFileName == null ? (meshName == null ? "" : meshName) : meshFileName;
        int dot = name.lastIndexOf('.');
        String type = dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1).toUpperCase(java.util.Locale.ROOT) : "";
        if (modelMetadata != null && !modelMetadata.optString("format").isEmpty()) type = modelMetadata.optString("format").toUpperCase(java.util.Locale.ROOT);
        long bytes = meshFileName == null ? 0 : new java.io.File(new java.io.File(getFilesDir(), "models"), meshFileName).length();
        String size = bytes > 0 ? android.text.format.Formatter.formatShortFileSize(this, bytes) : "";
        String dims = mesh == null ? "" : String.format(java.util.Locale.getDefault(), "%.1f × %.1f × %.1f", mesh.size[0], mesh.size[1], mesh.size[2]);
        if (type.isEmpty() || size.isEmpty()) return (type.isEmpty() ? size : type) + (type.isEmpty() && size.isEmpty() ? "" : " · ") + dims + " " + getString(R.string.kv3_unit_mm);
        return getString(R.string.kv3_model_details, type, size, dims);
    }

    /** Tools sheet (Figma node 3:205): title + file, 4-column tool grid, coloured X/Y/Z fields. */
    private View buildToolsSheet() {
        final com.keplerslicer.app.glass.KeplerUi ui = kui;
        LinearLayout sheet = ui.sheet();
        LinearLayout title = ui.row(); title.setPadding(dp(4), 0, dp(4), 0);
        toolTitle = ui.line(getString(R.string.kv3_tool_move), 16, 700, com.keplerslicer.app.glass.KeplerUi.WHITE); title.addView(toolTitle, ui.weight());
        if (tabletMode) {
            // Desktop: the tool bar above the plate picks the tool; this floating panel only holds its values.
            FrameLayout close = ui.glassButton(R.drawable.kv3_plus, 36, 18, getString(R.string.glass_dismiss), () -> { setToolsMode(false); tabletModeChanged(0); });
            close.getChildAt(0).setRotation(45); close.setTooltipText(getString(R.string.glass_dismiss)); title.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));
        } else { TextView file = ui.line(meshName == null ? "" : meshName, 12, 500, com.keplerslicer.app.glass.KeplerUi.SECONDARY); file.setMaxWidth(dp(180)); title.addView(file); }
        sheet.addView(title, lp(-1,-2));
        int[] icons = {R.drawable.kv3_move, R.drawable.kv3_refresh, R.drawable.kv3_scale, R.drawable.kv3_lay_on_face, R.drawable.kv3_cut, R.drawable.kv3_support_paint, R.drawable.kv3_seam_paint, R.drawable.kv3_fuzzy_skin, R.drawable.kv3_text, R.drawable.kv3_measure, R.drawable.kv3_assembly, R.drawable.kv3_brim_ears};
        int[] labels = {R.string.kv3_tool_move, R.string.kv3_tool_rotate, R.string.kv3_tool_scale, R.string.kv3_tool_lay_on_face, R.string.kv3_tool_cut, R.string.kv3_tool_support_paint, R.string.kv3_tool_seam_paint, R.string.kv3_tool_fuzzy_skin, R.string.kv3_tool_text, R.string.kv3_tool_measure, R.string.kv3_tool_assembly, R.string.kv3_tool_brim_ears};
        toolTiles = new LinearLayout[icons.length];
        for (int i = 0; i < icons.length; i++) {
            final int index = i; final String label = getString(labels[i]);
            toolTiles[i] = ui.toolTile(icons[i], label, i == activeTool, () -> {
                if (index <= 2) selectTool(index);
                else if (index == 3) layOnFace();
                else if (index == 5) paintMenu(label, "support", "support_type", true);
                else if (index == 6) paintMenu(label, "seam", "seam_position", false);
                else if (index == 7) paintMenu(label, "fuzzy", "fuzzy_skin", false);
                else if (index == 9) measure();
                else if (index == 11) objectTool(label, "brim_type", false);
                else if (index == 4) cutTool(label);
                else if (index == 8) textTool(label);
                else if (index == 10) assemblyTool(label);
            });
        }
        if (!tabletMode) sheet.addView(ui.grid(toolTiles, 4), ui.below(10));
        LinearLayout fields = ui.row(); fields.setGravity(Gravity.TOP);
        String[] axes = {"X", "Y", "Z"}; int[] colors = {com.keplerslicer.app.glass.KeplerUi.AXIS_X, com.keplerslicer.app.glass.KeplerUi.AXIS_Y, com.keplerslicer.app.glass.KeplerUi.AXIS_Z};
        axisFields = new com.keplerslicer.app.glass.KeplerUi.AxisField[3];
        for (int i = 0; i < 3; i++) {
            final int axis = i;
            axisFields[i] = ui.axisField(axes[i], colors[i], "", axes[i]);
            axisFields[i].value.setOnEditorActionListener((v, action, event) -> { commitAxis(axis); return false; });
            axisFields[i].value.setOnFocusChangeListener((v, focused) -> { if (!focused) commitAxis(axis); });
            LinearLayout.LayoutParams p = ui.weight(); if (i > 0) p.leftMargin = dp(8); fields.addView(axisFields[i].view, p);
        }
        sheet.addView(fields, ui.below(10));
        return sheet;
    }

    private LinearLayout statusPill; private com.keplerslicer.app.glass.LayerRail layerRail;
    private TextView statTime, statFilament, statCost; private boolean playing; private int statsRequest;

    /** Slice result sheet (Figma node 3:382): colour modes, legend, stats, layer scrubber, Print / Export. */
    private LinearLayout buildPreviewSheet() {
        final com.keplerslicer.app.glass.KeplerUi ui = kui; final int white = com.keplerslicer.app.glass.KeplerUi.WHITE, secondary = com.keplerslicer.app.glass.KeplerUi.SECONDARY;
        LinearLayout sheet = ui.sheet();
        android.widget.HorizontalScrollView modes = new android.widget.HorizontalScrollView(this); modes.setHorizontalScrollBarEnabled(false); modes.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout chips = ui.row(); modes.addView(chips);
        int[] modeLabels = {R.string.kv3_color_feature, R.string.kv3_color_speed, R.string.kv3_color_flow, R.string.kv3_color_height, R.string.kv3_color_temperature};
        modeChips.clear();
        for (int i = 0; i < modeLabels.length; i++) {
            final int mode = i;
            TextView chip = ui.chip(getString(modeLabels[i]), i == colorMode, () -> setColorMode(mode));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(36)); if (i > 0) p.leftMargin = dp(8); chips.addView(chip, p); modeChips.add(chip);
        }
        sheet.addView(modes, lp(-1, -2));
        int[] legendColors = {0xFFFF9F43, 0xFFF5D06B, 0xFFFF7A6E, 0xFFB06CF0, 0xFF6FD0C4, 0xFF6F9CF0};
        int[] legendLabels = {R.string.kv3_legend_outer, R.string.kv3_legend_inner, R.string.kv3_legend_sparse, R.string.kv3_legend_solid, R.string.kv3_legend_support, R.string.kv3_legend_bridge};
        com.keplerslicer.app.glass.FlowRow legend = new com.keplerslicer.app.glass.FlowRow(this, dp(14), dp(6)); legendRow = legend;
        String[] labels = new String[legendLabels.length]; for (int k = 0; k < labels.length; k++) labels[k] = getString(legendLabels[k]);
        fillLegend(legendColors, labels);
        sheet.addView(legend, ui.below(10));
        LinearLayout stats = ui.row(); stats.setGravity(Gravity.TOP);
        int[] statLabels = {R.string.kv3_stat_time, R.string.kv3_stat_filament, R.string.kv3_stat_cost}; TextView[] values = new TextView[3];
        for (int i = 0; i < 3; i++) {
            LinearLayout box = ui.column(); box.setPadding(dp(12), dp(8), dp(12), dp(8)); box.setBackground(ui.field(16));
            box.addView(ui.line(getString(statLabels[i]), 12, 500, secondary));
            values[i] = ui.line("—", 15, 700, white); LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, -2); vp.topMargin = dp(1); box.addView(values[i], vp);
            LinearLayout.LayoutParams bp = ui.weight(); if (i > 0) bp.leftMargin = dp(8); stats.addView(box, bp);
        }
        statTime = values[0]; statFilament = values[1]; statCost = values[2];
        sheet.addView(stats, ui.below(10));
        LinearLayout scrubber = ui.row();
        FrameLayout play = new FrameLayout(this); ImageView playArt = ui.image(R.drawable.kv3_play, 18, white);
        play.addView(playArt, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER)); ui.pressable(play, null, 16);
        play.setContentDescription(getString(R.string.kv3_play_layers)); play.setOnClickListener(v -> togglePlayback(playArt));
        scrubber.addView(play, lp(32, 32));
        layerSlider = new SeekBar(this); layerSlider.setContentDescription(getString(R.string.l10n_ws_visible_layers));
        layerSlider.setProgressTintList(android.content.res.ColorStateList.valueOf(white));
        layerSlider.setThumbTintList(android.content.res.ColorStateList.valueOf(white));
        layerSlider.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x52FFFFFF));
        layerSlider.setPadding(dp(8), 0, dp(8), 0);
        scrubber.addView(layerSlider, new LinearLayout.LayoutParams(0, dp(32), 1));
        layerCaption = ui.line(getString(R.string.kv3_layer, 1), 12, 500, secondary);
        click(layerCaption, () -> { if (layerAnalysis!=null) dialog(getString(R.string.l10n_ws_about_layers),getString(R.string.l10n_ws_layer_note)+layerAnalysis.skippedUnknown+getString(R.string.l10n_ws_moves_unsupported)+layerAnalysis.unsupportedMoves+getString(R.string.l10n_ws_arcs_hidden)+layerAnalysis.arcMoves+"."); });
        scrubber.addView(layerCaption);
        sheet.addView(scrubber, ui.below(10));
        if (tabletMode) { scrubber.setVisibility(View.GONE); return sheet; } // tablet: the layer slider at the right and Export/Send on top, as on desktop
        LinearLayout actions = ui.row();
        LinearLayout print = ui.primaryButton(R.drawable.kv3_print, getString(R.string.kv3_print), () -> { Intent intent = new Intent(); intent.setClassName(this, getPackageName()+".NetworkActivity"); startActivity(intent); });
        print.setMinimumHeight(dp(48)); ui.pressable(print, ui.shape(white, 0, 24), 24);
        actions.addView(print, ui.weight());
        LinearLayout export = ui.secondaryButton(R.drawable.kv3_upload, getString(R.string.kv3_export), 48, 24, this::openExport);
        LinearLayout.LayoutParams ep = ui.weight(); ep.leftMargin = dp(8); actions.addView(export, ep);
        sheet.addView(actions, ui.below(10));
        return sheet;
    }

    private final java.util.List<TextView> modeChips = new java.util.ArrayList<>();
    private com.keplerslicer.app.glass.FlowRow legendRow; private int colorMode; private ToolpathGeometry layerGeometry; private int colorRequest;

    private void fillLegend(int[] colors, String[] labels) {
        if (legendRow == null) return; legendRow.removeAllViews();
        for (int k = 0; k < colors.length; k++) {
            LinearLayout item = kui.row();
            View sw = new View(this); sw.setBackground(kui.shape(colors[k], 0, 3)); item.addView(sw, lp(10, 10));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2); tp.leftMargin = dp(6); item.addView(kui.line(labels[k], 12, 500, com.keplerslicer.app.glass.KeplerUi.WHITE), tp);
            legendRow.addView(item, new ViewGroup.LayoutParams(-2, -2));
        }
    }
    /** Legend for the active colour mode: feature classes, or a five-step value scale with units. */
    private void updateLegend(GcodeAnalysis analysis) {
        if (colorMode == ToolpathGeometry.MODE_FEATURE || analysis == null) {
            int[] c = java.util.Arrays.copyOfRange(ToolpathGeometry.FEATURE_COLORS, 1, 7);
            fillLegend(c, new String[]{getString(R.string.kv3_legend_outer), getString(R.string.kv3_legend_inner), getString(R.string.kv3_legend_sparse), getString(R.string.kv3_legend_solid), getString(R.string.kv3_legend_support), getString(R.string.kv3_legend_bridge)});
            return;
        }
        double[] r = ToolpathGeometry.range(analysis, colorMode);
        String unit = colorMode == ToolpathGeometry.MODE_SPEED ? getString(R.string.kv3_unit_mm_s) : colorMode == ToolpathGeometry.MODE_FLOW ? getString(R.string.kv3_unit_mm_per_mm) : colorMode == ToolpathGeometry.MODE_TEMPERATURE ? "°C" : getString(R.string.kv3_unit_mm);
        if (!(r[1] >= r[0]) || !Double.isFinite(r[0])) { fillLegend(new int[]{ToolpathGeometry.FEATURE_COLORS[0]}, new String[]{getString(R.string.kv3_no_data)}); return; }
        int[] colors = new int[5]; String[] labels = new String[5];
        for (int i = 0; i < 5; i++) { double t = i / 4.0; colors[i] = ToolpathGeometry.ramp(t); double v = r[0] + (r[1] - r[0]) * t;
            labels[i] = (colorMode == ToolpathGeometry.MODE_FLOW ? String.format(java.util.Locale.getDefault(), "%.3f", v) : colorMode == ToolpathGeometry.MODE_HEIGHT ? String.format(java.util.Locale.getDefault(), "%.2f", v) : String.format(java.util.Locale.getDefault(), "%.0f", v)) + " " + unit; }
        fillLegend(colors, labels);
    }
    private void setColorMode(int mode) {
        colorMode = mode; for (int i = 0; i < modeChips.size(); i++) kui.setChipActive(modeChips.get(i), i == mode);
        final ToolpathGeometry geometry = layerGeometry; final GcodeAnalysis analysis = layerAnalysis; final StlView view = stlView; final int request = ++colorRequest;
        updateLegend(analysis);
        if (geometry == null || analysis == null || view == null) return;
        new Thread(() -> { try { java.nio.FloatBuffer c = ToolpathGeometry.colors(analysis, mode, geometry.vertices.capacity() / 3);
            runOnUiThread(() -> { if (isDestroyed() || request != colorRequest || stlView != view || layerGeometry != geometry) return; geometry.colors = c; view.requestRender(); }); } catch (Exception ignored) { } }, "toolpath-colors").start();
    }

    private void togglePlayback(ImageView art) {
        if (layerSlider == null) return;
        playing = !playing; art.setImageDrawable(kui.icon(playing ? R.drawable.kv3_pause : R.drawable.kv3_play, com.keplerslicer.app.glass.KeplerUi.WHITE));
        if (!playing) return;
        if (layerSlider.getProgress() >= layerSlider.getMax()) layerSlider.setProgress(0);
        final SeekBar bar = layerSlider;
        Runnable step = new Runnable() { @Override public void run() {
            if (!playing || bar != layerSlider || !layersMode || isDestroyed()) { playing = false; art.setImageDrawable(kui.icon(R.drawable.kv3_play, com.keplerslicer.app.glass.KeplerUi.WHITE)); return; }
            if (bar.getProgress() >= bar.getMax()) { playing = false; art.setImageDrawable(kui.icon(R.drawable.kv3_play, com.keplerslicer.app.glass.KeplerUi.WHITE)); return; }
            bar.setProgress(bar.getProgress() + 1); bar.postDelayed(this, 60);
        }};
        bar.postDelayed(step, 60);
    }

    /** Which bottom panel and overlays are visible for Prepare, Preview and Tools. */
    private void syncPanels() {
        boolean preview = layersMode && layerPanel != null && layerPanel.getVisibility() == View.VISIBLE;
        if (prepareGroup != null) prepareGroup.setVisibility(tabletMode || toolsMode || preview ? View.GONE : View.VISIBLE); // tablet: Prepare is the sidebar
        if (tabletFloating != null) tabletFloating.setVisibility(toolsMode || preview || paint != null || layersMode ? View.VISIBLE : View.GONE);
        markTabletTools();
        if (toolsSheet != null) toolsSheet.setVisibility(toolsMode && paint == null ? View.VISIBLE : View.GONE);
        if (paintPanel != null) paintPanel.setVisibility(paint != null ? View.VISIBLE : View.GONE);
        if (addButton != null) addButton.setVisibility(tabletMode || toolsMode || layersMode ? View.GONE : View.VISIBLE); // tablet: "+" is in the canvas toolbar
        if (statusPill != null) { statusPill.setVisibility(preview ? View.VISIBLE : View.GONE); int plate = modelMetadata == null ? 0 : Math.max(0, modelMetadata.optInt("selected_plate", 0)); ((TextView) statusPill.getChildAt(1)).setText(getString(R.string.kv3_sliced_plate, plate + 1)); }
        if (plateChip != null) plateChip.setVisibility(preview ? View.GONE : View.VISIBLE);
        if (layerRail != null) layerRail.setVisibility(preview ? View.VISIBLE : View.GONE);
        if (!preview) playing = false; else loadSliceStats();
    }

    /** Reads the engine's summary comments from the current result (time, filament weight, cost). */
    private void loadSliceStats() {
        final java.io.File file = lastResult(); final int request = ++statsRequest;
        if (file == null || statTime == null) return;
        new Thread(() -> {
            String time = null, grams = null, cost = null;
            try (java.io.RandomAccessFile in = new java.io.RandomAccessFile(file, "r")) {
                long start = Math.max(0, in.length() - 96 * 1024); in.seek(start); byte[] buffer = new byte[(int) (in.length() - start)]; in.readFully(buffer);
                byte[] head = new byte[(int) Math.min(4096, in.length())]; in.seek(0); in.readFully(head); // Bambu G-code keeps the time in its header
                for (String line : (new String(buffer, java.nio.charset.StandardCharsets.UTF_8) + "\n" + new String(head, java.nio.charset.StandardCharsets.UTF_8)).split("\n")) {
                    String l = line.trim().toLowerCase(java.util.Locale.ROOT); if (!l.startsWith(";")) continue;
                    String value = l.contains("=") ? l.substring(l.indexOf('=') + 1).trim() : l.contains(":") ? l.substring(l.lastIndexOf(':') + 1).trim() : "";
                    if (value.isEmpty()) continue;
                    if (time == null && (l.contains("estimated printing time") || l.contains("total estimated time"))) time = PrintTime.localize(this, value);
                    else if (grams == null && l.contains("filament") && l.contains("[g]") && value.matches("[0-9.,]+.*")) grams = value.split("[ ,]")[0] + " " + getString(R.string.kv3_unit_g);
                    else if (cost == null && l.contains("filament cost") && value.matches("[0-9.,]+.*")) cost = value.split("[ ,]")[0];
                }
            } catch (Exception ignored) { }
            final String t = time, g = grams, c = cost;
            runOnUiThread(() -> { if (isDestroyed() || request != statsRequest || statTime == null) return; statTime.setText(t == null ? "—" : t); statFilament.setText(g == null ? "—" : g); statCost.setText(c == null ? "—" : c); });
        }, "slice-stats").start();
    }

    /**
     * Object-scoped setting tools (Support / Seam / Fuzzy skin / Brim ears): the choices are the engine enum values
     * from the settings schema and are stored as per-object overrides that the engine applies when slicing.
     */
    private void objectTool(String title, String key, boolean support) {
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            try {
                com.keplerslicer.app.settings.SettingsSchema schema = com.keplerslicer.app.settings.SettingsSchema.load(app);
                com.keplerslicer.app.settings.SettingsSchema.Option option = null, enable = null;
                for (com.keplerslicer.app.settings.SettingsSchema.Option o : schema.options("process")) { if (key.equals(o.key)) option = o; if ("enable_support".equals(o.key)) enable = o; }
                if (option == null || option.enumValues.isEmpty() || support && enable == null) throw new java.io.IOException(getString(R.string.kv3_tool_option_missing, key));
                org.json.JSONObject changes = toolScopeValues(app);
                final java.util.List<String> values = new java.util.ArrayList<>(option.enumValues), labels = new java.util.ArrayList<>();
                for (int i = 0; i < values.size(); i++) labels.add(i < option.enumLabels.size() && !option.enumLabels.get(i).isEmpty() ? option.enumLabels.get(i) : values.get(i));
                if (support) { values.add(0, null); labels.add(0, getString(R.string.kv3_support_off)); }
                String current = changes.has(key) ? String.valueOf(changes.opt(key)) : null; int selected = -1;
                Object enabled = changes.opt("enable_support"); if (support && enabled != null && ("false".equals(String.valueOf(enabled)) || "0".equals(String.valueOf(enabled)))) selected = 0;
                else if (current != null) selected = values.indexOf(current);
                final int sel = selected; final com.keplerslicer.app.settings.SettingsSchema.Option opt = option, en = support ? enable : null; // only the support tool switches supports
                runOnUiThread(() -> { if (isDestroyed()) return;
                    com.keplerslicer.app.glass.GlassBottomSheet.choose(this, root, false, title, labels.toArray(new String[0]), sel, index -> applyObjectTool(title, key, opt, en, values.get(index)));
                });
            } catch (Exception e) { String m = EngineText.clean(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); runOnUiThread(() -> { if (!isDestroyed()) dialog(title, m); }); }
        }, "object-tool").start();
    }
    /**
     * Object tools edit the object's own settings when the model is one object; a project with several objects
     * gets the value for the whole plate (process overrides), since per-object values are not supported there yet.
     */
    private org.json.JSONObject toolScopeValues(android.content.Context app) throws Exception {
        org.json.JSONObject values;
        if (ObjectSettingsStore.currentObjectCount(app) == 1) values = ObjectSettingsStore.current(app).changes();
        else { String json = ProcessOverridesStore.load(app, ProcessOverridesStore.Identity.capture(app)); values = new org.json.JSONObject(json == null || json.isEmpty() ? "{}" : json); }
        // Earlier builds stored enable_support as text; the engine rejects that, so turn it back into a boolean.
        Object support = values.opt("enable_support");
        if (support instanceof String) values.put("enable_support", "true".equals(support) || "1".equals(support));
        return values;
    }
    private void applyObjectTool(String title, String key, com.keplerslicer.app.settings.SettingsSchema.Option option, com.keplerslicer.app.settings.SettingsSchema.Option enable, String value) {
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            try {
                boolean single = ObjectSettingsStore.currentObjectCount(app) == 1;
                org.json.JSONObject changes = toolScopeValues(app);
                if (enable != null) changes.put("enable_support", value != null); // the engine takes a real boolean
                if (value != null) changes.put(key, option.parseInput(value, changes.opt(key)));
                if (single) { ObjectSettingsStore.Snapshot scope = ObjectSettingsStore.current(app); ObjectSettingsStore.save(app, scope.modelFileName, scope.plateIndex, scope.nativeObjectCount, changes); }
                else { ProcessOverridesStore.Identity id = ProcessOverridesStore.Identity.capture(app); ProcessOverridesStore.save(app, id, changes.toString()); }
                final String done = single ? getString(R.string.kv3_tool_applied) : L.t("В проекте несколько объектов — настройка применена ко всей пластине (параметры процесса).");
                runOnUiThread(() -> { if (isDestroyed()) return; ControlPreferences.scheduleAutoSlice(this); com.keplerslicer.app.glass.GlassBottomSheet.info(this, root, false, title, done); });
            } catch (Exception e) { String m = EngineText.clean(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); runOnUiThread(() -> { if (!isDestroyed()) dialog(title, m); }); }
        }, "object-tool-save").start();
    }

    /** Lay on face: orient the largest planar face of the mesh downwards (keeps Z rotation, scale and offset). */
    /**
     * Lay on face, as on desktop: the user taps a face of the model and that face is turned down onto the plate.
     * The tapped preview triangle has the same index in the source mesh; its normal (with the current scale) gives
     * the X/Y rotation that points it straight down. Z rotation, scale and position are kept.
     */
    private void layOnFace() {
        if (mesh == null || mesh.vertices.length < 9 || !previewReady || !transformSupported() || stlView == null) { dialog(getString(R.string.kv3_tool_lay_on_face), getString(R.string.kv3_tools_unavailable)); return; }
        final StlView view = stlView;
        view.setMoveMode(false, viewportMoveCallback); view.setRotateGizmo(false);
        if (toolTitle != null) toolTitle.setText(L.t("Нажмите на грань модели"));
        view.setFacePick(triangle -> {
            view.setFacePick(null);
            final float[] v = mesh == null ? null : mesh.vertices; final ModelTransform t = modelTransform;
            if (v == null || triangle < 0 || triangle * 9 + 8 >= v.length) { selectTool(activeTool); return; }
            int i = triangle * 9;
            double ax = (v[i+3]-v[i])*t.sx, ay = (v[i+4]-v[i+1])*t.sy, az = (v[i+5]-v[i+2])*t.sz, bx = (v[i+6]-v[i])*t.sx, by = (v[i+7]-v[i+1])*t.sy, bz = (v[i+8]-v[i+2])*t.sz;
            double nx = ay*bz-az*by, ny = az*bx-ax*bz, nz = ax*by-ay*bx, len = Math.sqrt(nx*nx+ny*ny+nz*nz);
            if (!(len > 1e-12)) { selectTool(activeTool); return; }
            nx /= len; ny /= len; nz /= len;
            double rx = Math.atan2(ny, nz), r = Math.sqrt(ny*ny+nz*nz), ry = Math.atan2(nx, -r);
            selectTool(activeTool);
            applyTransform(new ModelTransform(Math.toDegrees(rx), Math.toDegrees(ry), t.rz, t.sx, t.sy, t.sz, t.offsetX, t.offsetY, t.offsetZ));
        });
        com.keplerslicer.app.glass.GlassBottomSheet.info(this, root, false, getString(R.string.kv3_tool_lay_on_face), L.t("Нажмите на грань модели, которой она должна лечь на стол."));
    }
    /** Measure: size of the transformed model in millimetres (bounding box, as placed on the plate). */
    private void measure() {
        if (mesh == null || mesh.vertices.length < 9) { dialog(getString(R.string.kv3_tool_measure), getString(R.string.kv3_no_model_detail)); return; }
        final float[] v = mesh.vertices; final double max = Math.max(mesh.size[0], Math.max(mesh.size[1], mesh.size[2])) / 1.6; final ModelTransform t = modelTransform;
        new Thread(() -> {
            double ax = Math.toRadians(t.rx), ay = Math.toRadians(t.ry), az = Math.toRadians(t.rz), cx = Math.cos(ax), sx = Math.sin(ax), cy = Math.cos(ay), sy = Math.sin(ay), cz = Math.cos(az), sz = Math.sin(az);
            double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (int i = 0; i + 2 < v.length; i += 3) {
                double x = v[i]*max*t.sx, y = v[i+1]*max*t.sy, z = v[i+2]*max*t.sz;
                double y1 = y*cx - z*sx, z1 = y*sx + z*cx; double x2 = x*cy + z1*sy, z2 = -x*sy + z1*cy; double x3 = x2*cz - y1*sz, y3 = x2*sz + y1*cz;
                double[] p = {x3, y3, z2}; for (int k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], p[k]); hi[k] = Math.max(hi[k], p[k]); }
            }
            double dx = hi[0]-lo[0], dy = hi[1]-lo[1], dz = hi[2]-lo[2];
            String text = getString(R.string.kv3_measure_result, fmt(dx), fmt(dy), fmt(dz), fmt(Math.sqrt(dx*dx+dy*dy+dz*dz))) + (mesh.previewSampled ? "\n" + getString(R.string.kv3_measure_sampled) : "");
            runOnUiThread(() -> { if (!isDestroyed()) com.keplerslicer.app.glass.GlassBottomSheet.info(this, root, false, getString(R.string.kv3_tool_measure), text); });
        }, "measure").start();
    }
    /** Preview colours from the material slot of each triangle (engine runs) and the project's slot colours. */
    private int[] triangleSlotColours(StlMesh m, org.json.JSONObject meta) {
        if (m == null || meta == null) return null;
        org.json.JSONArray runs = meta.optJSONArray("preview_slot_runs"), colours = meta.optJSONArray("slot_colours"), painted = meta.optJSONArray("preview_paint_colour");
        if (runs == null || runs.length() == 0) return null;
        boolean several = colours != null && colours.length() > 1, anyPaint = false;
        for (int i = 0; i < runs.length() && !several; i++) { org.json.JSONArray r = runs.optJSONArray(i); if (r != null && r.optInt(1, 1) != 1) several = true; }
        for (int i = 0; painted != null && i < painted.length() && !anyPaint; i++) { org.json.JSONArray r = painted.optJSONArray(i); if (r != null && r.optInt(1, 0) > 0) anyPaint = true; }
        if (!several && !anyPaint) return null; // single-material models keep the design's model colour
        MaterialSlots.Slot[] fallback = MaterialSlots.load(this);
        int triangles = m.vertices.length / 9; int[] out = new int[triangles];
        for (int i = 0; i < runs.length(); i++) {
            org.json.JSONArray r = runs.optJSONArray(i); if (r == null) continue;
            int from = Math.max(0, r.optInt(0, 0)), slot = Math.max(1, r.optInt(1, 1));
            org.json.JSONArray next = i + 1 < runs.length() ? runs.optJSONArray(i + 1) : null; int to = next == null ? triangles : Math.min(triangles, next.optInt(0, triangles));
            String hex = colours != null && slot - 1 < colours.length() ? colours.optString(slot - 1, "") : slot - 1 < fallback.length ? fallback[slot - 1].colour : "";
            int argb = hex.matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?") ? 0xFF000000 | Integer.parseInt(hex.substring(1, 7), 16) : 0xFF3D8FE0;
            for (int t = from; t < to; t++) out[t] = argb;
        }
        // Colour painted with the brush (mmu segmentation): the slot colour of the painted state, per triangle.
        for (int i = 0; painted != null && i < painted.length(); i++) {
            org.json.JSONArray r = painted.optJSONArray(i), next = i + 1 < painted.length() ? painted.optJSONArray(i + 1) : null; if (r == null) continue;
            int state = r.optInt(1, 0); if (state <= 0) continue;
            int from = Math.max(0, r.optInt(0, 0)), to = next == null ? triangles : Math.min(triangles, next.optInt(0, triangles));
            String hex = colours != null && state - 1 < colours.length() ? colours.optString(state - 1, "") : state - 1 < fallback.length ? fallback[state - 1].colour : "";
            int argb = hex.matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?") ? 0xFF000000 | Integer.parseInt(hex.substring(1, 7), 16) : 0xFF3D8FE0;
            for (int t = from; t < to; t++) out[t] = argb;
        }
        return out;
    }
    // ---- Paint brushes (support, seam, colour): whole source triangles under a sphere brush, applied by the engine ----
    private static final class PaintSession {
        final String kind; int state; float radius = 3f; boolean fill; final java.util.HashMap<Integer, Integer> strokes = new java.util.HashMap<>();
        /** Brush dabs for the engine: {hit preview triangle, state, radius, x, y, z, cx, cy, cz}; fills go to strokes (whole triangles). */
        final java.util.ArrayList<double[]> dabs = new java.util.ArrayList<>(); final java.util.HashMap<Integer, Integer> filled = new java.util.HashMap<>();
        final org.json.JSONArray map; PaintSession(String kind, int state, org.json.JSONArray map) { this.kind = kind; this.state = state; this.map = map; }
    }
    private PaintSession paint; private LinearLayout paintPanel; private String pendingPaint;
    private static final float[] RADII = {1f, 2f, 3f, 5f, 8f, 12f};
    private void paintMenu(String title, String kind, String key, boolean support) {
        String[] options = {getString(support ? R.string.kv3_paint_support_brush : "fuzzy".equals(kind) ? R.string.kv3_paint_fuzzy_brush : R.string.kv3_paint_seam_brush), getString(R.string.kv3_paint_object_setting)};
        com.keplerslicer.app.glass.GlassBottomSheet.choose(this, root, false, title, options, -1, which -> { if (which == 0) startPaint(kind); else objectTool(title, key, support); });
    }
    /** Source triangle of each preview triangle: [first preview, object, part, first source] runs (stride 1 only). */
    private org.json.JSONArray paintMap() {
        if (mesh == null || mesh.previewSampled) return null;
        if (modelMetadata == null) { try { return new org.json.JSONArray().put(new org.json.JSONArray().put(0).put(0).put(0).put(0)); } catch (Exception e) { return null; } }
        if (modelMetadata.optInt("preview_stride", 0) != 1) return null;
        return modelMetadata.optJSONArray("preview_source_map");
    }
    private void startPaint(String kind) {
        if (mesh == null || loadingMesh || !previewReady) { dialog(getString(R.string.kv3_paint_title), getString(R.string.kv3_no_model_detail)); return; }
        org.json.JSONArray map = paintMap();
        if (map == null) { dialog(getString(R.string.kv3_paint_title), getString(mesh.previewSampled ? R.string.kv3_paint_too_detailed : R.string.kv3_paint_reopen)); return; }
        if ("colour".equals(kind)) { MaterialSlots.Slot[] slots = MaterialSlots.load(this); int filled = 0; for (MaterialSlots.Slot s : slots) if (s.filled()) filled++; if (filled < 2) { dialog(getString(R.string.kv3_materials_title), getString(R.string.kv3_materials_need_slots)); return; } }
        paint = new PaintSession(kind, "colour".equals(kind) ? 2 : 1, map);
        if (stlView != null) { stlView.setMoveMode(false, viewportMoveCallback); stlView.setPaintMode(this::painted, paint.radius); }
        showPaintPanel(); repaint();
    }
    private void painted(int[] triangles, int hit, double[] point, double[] camera) {
        if (paint == null) return; for (int t : triangles) paint.strokes.put(t, paint.state);
        if (point == null || camera == null) { for (int t : triangles) paint.filled.put(t, paint.state); }
        else if (paint.dabs.size() < 20000) paint.dabs.add(new double[]{hit, paint.state, paint.radius, point[0], point[1], point[2], camera[0], camera[1], camera[2]});
        repaint();
    }
    private int paintColour(String kind, int state) {
        if (state <= 0) return 0;
        if ("support".equals(kind)) return state == 1 ? 0xFF3DDC84 : 0xFFFF5A5A;
        if ("fuzzy".equals(kind)) return 0xFFC58BFF;
        if ("seam".equals(kind)) return state == 1 ? 0xFFFFD27A : 0xFF8A8F98;
        MaterialSlots.Slot[] slots = MaterialSlots.load(this); java.util.List<Integer> filled = new java.util.ArrayList<>(); for (int i = 0; i < slots.length; i++) if (slots[i].filled()) filled.add(i);
        return state - 1 < filled.size() ? MaterialSlots.argb(slots[filled.get(state - 1)].colour) : 0xFF3D8FE0;
    }
    /** Base colours (slot colours or the model colour), stored paint of the active kind, then this session's strokes. */
    private void repaint() {
        if (stlView == null || mesh == null) return;
        int n = mesh.vertices.length / 9; int[] colours = triangleSlotColours(mesh, modelMetadata);
        if (colours == null) { colours = new int[n]; java.util.Arrays.fill(colours, 0xFF3D8FE0); }
        if (paint != null && modelMetadata != null) {
            org.json.JSONArray runs = modelMetadata.optJSONArray("preview_paint_" + paint.kind);
            for (int i = 0; runs != null && i < runs.length(); i++) {
                org.json.JSONArray r = runs.optJSONArray(i), next = i + 1 < runs.length() ? runs.optJSONArray(i + 1) : null; if (r == null) continue;
                int from = r.optInt(0), to = next == null ? n : Math.min(n, next.optInt(0)), c = paintColour(paint.kind, r.optInt(1));
                if (c != 0) for (int t = Math.max(0, from); t < to; t++) colours[t] = c;
            }
        }
        if (paint != null) for (java.util.Map.Entry<Integer, Integer> s : paint.strokes.entrySet()) { int t = s.getKey(); if (t >= 0 && t < n) { int c = paintColour(paint.kind, s.getValue()); colours[t] = c == 0 ? 0xFF3D8FE0 : c; } }
        stlView.setTriangleColors(colours);
    }
    private void showPaintPanel() {
        final com.keplerslicer.app.glass.KeplerUi ui = kui; if (paintPanel != null && paintPanel.getParent() instanceof ViewGroup) ((ViewGroup) paintPanel.getParent()).removeView(paintPanel);
        LinearLayout sheet = ui.sheet(); paintPanel = sheet;
        String kindTitle = getString("support".equals(paint.kind) ? R.string.kv3_paint_support : "seam".equals(paint.kind) ? R.string.kv3_paint_seam : "fuzzy".equals(paint.kind) ? R.string.kv3_paint_fuzzy : R.string.kv3_paint_colour);
        sheet.addView(ui.line(kindTitle, 16, 700, com.keplerslicer.app.glass.KeplerUi.WHITE));
        TextView hint = ui.line(getString(R.string.kv3_paint_hint) + ("support".equals(paint.kind) ? " " + getString(R.string.kv3_paint_support_note) : ""), 12, 500, com.keplerslicer.app.glass.KeplerUi.SECONDARY); hint.setSingleLine(false); hint.setMaxLines(5); hint.setEllipsize(null); sheet.addView(hint, ui.below(4));
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this); scroll.setHorizontalScrollBarEnabled(false); LinearLayout chips = ui.row(); scroll.addView(chips);
        java.util.List<String> labels = new java.util.ArrayList<>(); java.util.List<Integer> states = new java.util.ArrayList<>();
        if ("colour".equals(paint.kind)) { MaterialSlots.Slot[] slots = MaterialSlots.load(this); int k = 0; for (int i = 0; i < slots.length; i++) if (slots[i].filled()) { k++; labels.add(getString(R.string.kv3_slot, i + 1)); states.add(k); } }
        else if ("fuzzy".equals(paint.kind)) { labels.add(getString(R.string.kv3_paint_fuzzy_add)); states.add(1); }
        else { labels.add(getString("support".equals(paint.kind) ? R.string.kv3_paint_enforce : R.string.kv3_paint_seam_here)); states.add(1); labels.add(getString("support".equals(paint.kind) ? R.string.kv3_paint_block : R.string.kv3_paint_seam_block)); states.add(2); }
        labels.add(getString(R.string.kv3_paint_erase)); states.add(0);
        final TextView[] chipViews = new TextView[labels.size()];
        for (int i = 0; i < labels.size(); i++) { final int st = states.get(i); final int index = i;
            chipViews[i] = ui.chip(labels.get(i), st == paint.state, () -> { paint.state = st; for (int j = 0; j < chipViews.length; j++) ui.setChipActive(chipViews[j], j == index); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(36)); if (i > 0) p.leftMargin = dp(8); chips.addView(chipViews[i], p); }
        sheet.addView(scroll, ui.below(10));
        // Brush (sphere, splits triangles along its edge) or fill (one tap fills the connected surface up to 30 degrees).
        LinearLayout modes = ui.row(); final TextView[] modeChips = new TextView[2];
        modeChips[0] = ui.chip(getString(R.string.kv3_paint_mode_brush), !paint.fill, () -> { paint.fill = false; if (stlView != null) stlView.setPaintFill(false); ui.setChipActive(modeChips[0], true); ui.setChipActive(modeChips[1], false); });
        modeChips[1] = ui.chip(getString(R.string.kv3_paint_mode_fill), paint.fill, () -> { paint.fill = true; if (stlView != null) stlView.setPaintFill(true); ui.setChipActive(modeChips[0], false); ui.setChipActive(modeChips[1], true); });
        modes.addView(modeChips[0], new LinearLayout.LayoutParams(-2, dp(36))); LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-2, dp(36)); mp.leftMargin = dp(8); modes.addView(modeChips[1], mp);
        sheet.addView(modes, ui.below(8));
        LinearLayout radiusRow = ui.row(); final TextView radiusText = ui.line("", 14, 600, com.keplerslicer.app.glass.KeplerUi.WHITE);
        Runnable showRadius = () -> { radiusText.setText(getString(R.string.kv3_paint_radius, paint.radius == Math.round(paint.radius) ? String.valueOf(Math.round(paint.radius)) : String.valueOf(paint.radius))); if (stlView != null) stlView.setPaintMode(this::painted, paint.radius); };
        View minus = ui.glassButton(R.drawable.kv3_minus, 40, 18, getString(R.string.kv3_paint_smaller), () -> { paint.radius = step(paint.radius, -1); showRadius.run(); });
        View plus = ui.glassButton(R.drawable.kv3_plus, 40, 18, getString(R.string.kv3_paint_larger), () -> { paint.radius = step(paint.radius, 1); showRadius.run(); });
        radiusRow.addView(minus, lp(40, 40)); LinearLayout.LayoutParams rt = new LinearLayout.LayoutParams(0, -2, 1); rt.leftMargin = dp(12); radiusRow.addView(radiusText, rt); radiusRow.addView(plus, lp(40, 40));
        showRadius.run(); sheet.addView(radiusRow, ui.below(10));
        LinearLayout actions = ui.row();
        LinearLayout cancel = ui.secondaryButton(0, getString(R.string.general_project_cancel), 48, 24, this::endPaint); actions.addView(cancel, ui.weight());
        LinearLayout apply = ui.primaryButton(0, getString(R.string.kv3_paint_apply), this::applyPaint); LinearLayout.LayoutParams ap = ui.weight(); ap.leftMargin = dp(8); actions.addView(apply, ap);
        sheet.addView(actions, ui.below(12));
        ViewGroup host = toolsSheet == null ? null : (ViewGroup) toolsSheet.getParent();
        if (host != null) { LinearLayout.LayoutParams sp = lp(-1, -2); sp.setMargins(dp(12), 0, dp(12), dp(8)); host.addView(sheet, host.indexOfChild(toolsSheet) + 1, sp); }
        syncPanels();
    }
    private static org.json.JSONArray sourceRun(org.json.JSONArray map, int t) throws org.json.JSONException {
        org.json.JSONArray run = null; for (int i = 0; i < map.length(); i++) { org.json.JSONArray r = map.getJSONArray(i); if (r.getInt(0) <= t) run = r; else break; } return run;
    }
    private static float step(float r, int dir) { int i = 0; while (i < RADII.length - 1 && RADII[i] < r) i++; return RADII[Math.max(0, Math.min(RADII.length - 1, i + dir))]; }
    private void endPaint() {
        paint = null; if (stlView != null) { stlView.setPaintMode(null, 3f); stlView.setPaintFill(false); stlView.setTriangleColors(triangleSlotColours(mesh, modelMetadata)); stlView.setMoveMode(toolsMode && activeTool == 0 && previewReady && moveMode, viewportMoveCallback); }
        if (paintPanel != null && paintPanel.getParent() instanceof ViewGroup) ((ViewGroup) paintPanel.getParent()).removeView(paintPanel); paintPanel = null; syncPanels();
    }
    private void applyPaint() {
        final PaintSession s = paint; if (s == null) return; if (s.strokes.isEmpty()) { endPaint(); return; }
        try {
            // Group strokes by source part and state; preview runs map preview triangles to source triangles.
            java.util.TreeMap<String, java.util.TreeSet<Integer>> groups = new java.util.TreeMap<>();
            for (java.util.Map.Entry<Integer, Integer> e : s.filled.entrySet()) {
                org.json.JSONArray run = sourceRun(s.map, e.getKey()); if (run == null) continue;
                int source = run.getInt(3) + (e.getKey() - run.getInt(0)); String key = run.getInt(1) + "/" + run.getInt(2) + "/" + e.getValue();
                groups.computeIfAbsent(key, k -> new java.util.TreeSet<>()).add(source);
            }
            // Brush dabs, grouped by part, instance, state and radius; each keeps its hit source triangle.
            java.util.LinkedHashMap<String, org.json.JSONArray> dabs = new java.util.LinkedHashMap<>();
            for (double[] d : s.dabs) {
                int t = (int) d[0]; org.json.JSONArray run = sourceRun(s.map, t); if (run == null) continue;
                int source = run.getInt(3) + (t - run.getInt(0)), instance = run.length() > 4 ? run.getInt(4) : 0;
                String key = run.getInt(1) + "/" + run.getInt(2) + "/" + instance + "/" + (int) d[1] + "/" + d[2];
                dabs.computeIfAbsent(key, k -> new org.json.JSONArray()).put(new org.json.JSONObject().put("facet", source)
                    .put("point", new org.json.JSONArray().put(d[3]).put(d[4]).put(d[5])).put("camera", new org.json.JSONArray().put(d[6]).put(d[7]).put(d[8])));
            }
            MaterialSlots.Slot[] slots = MaterialSlots.load(this); int[] map = new int[slots.length];
            org.json.JSONObject request = MaterialSlots.request(slots, map); org.json.JSONArray items = new org.json.JSONArray();
            for (java.util.Map.Entry<String, java.util.TreeSet<Integer>> g : groups.entrySet()) {
                String[] k = g.getKey().split("/"); org.json.JSONArray tris = new org.json.JSONArray(); for (int t : g.getValue()) tris.put(t);
                items.put(new org.json.JSONObject().put("object", Integer.parseInt(k[0])).put("part", Integer.parseInt(k[1])).put("kind", s.kind).put("state", Integer.parseInt(k[2])).put("triangles", tris));
            }
            for (java.util.Map.Entry<String, org.json.JSONArray> g : dabs.entrySet()) {
                String[] k = g.getKey().split("/");
                items.put(new org.json.JSONObject().put("object", Integer.parseInt(k[0])).put("part", Integer.parseInt(k[1])).put("instance", Integer.parseInt(k[2])).put("kind", s.kind)
                    .put("state", Integer.parseInt(k[3])).put("radius", Double.parseDouble(k[4])).put("strokes", g.getValue()));
            }
            request.put("paint", items);
            endPaint(); runEdit(getString(R.string.kv3_paint_title), NativePreferenceActions.EDIT_MATERIALS, 0, 0, request.toString());
        } catch (org.json.JSONException e) { dialog(getString(R.string.kv3_edit_failed), String.valueOf(e.getMessage())); }
    }
    /** Plate chip: pick another plate of a multi-plate project (same engine path as Model details). */
    private void choosePlate() {
        org.json.JSONObject project = modelMetadata == null ? null : modelMetadata.optJSONObject("project_data");
        org.json.JSONArray plates = project == null ? null : project.optJSONArray("plates");
        if (plates == null || plates.length() < 2 || loadingMesh || meshFileName == null) return;
        final int[] indexes = new int[plates.length()]; String[] labels = new String[plates.length()]; int selected = modelMetadata.optInt("selected_plate", 0), current = -1;
        for (int i = 0; i < plates.length(); i++) { org.json.JSONObject plate = plates.optJSONObject(i); indexes[i] = plate == null ? i : plate.optInt("index", i);
            String name = plate == null ? "" : plate.optString("name", "").trim(); labels[i] = getString(R.string.kv3_plate_n, indexes[i] + 1) + (name.isEmpty() ? "" : " · " + name); if (indexes[i] == selected) current = i; }
        final String expected = meshFileName;
        com.keplerslicer.app.glass.GlassBottomSheet.choose(this, root, false, getString(R.string.l10n_md_plates), labels, current, which -> {
            loadingMesh = true; render();
            new Thread(() -> {
                try { ProjectPlates.select(getApplicationContext(), expected, indexes[which]); runOnUiThread(() -> { if (isDestroyed()) return; loadingMesh = false; restoreModel(); ControlPreferences.scheduleAutoSlice(this); }); }
                catch (Exception e) { final String m = EngineText.clean(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); runOnUiThread(() -> { if (isDestroyed()) return; loadingMesh = false; render(); dialog(getString(R.string.l10n_md_plates), m); }); }
            }, "choose-plate").start();
        });
    }
    private static String fmt(double mm) { return String.format(java.util.Locale.getDefault(), "%.2f", mm); }

    // ---- Cut / Text / Assembly: edited by the engine into a derived 3MF that replaces the current model ----
    private void cutTool(String title) {
        if (mesh == null || mesh.vertices.length < 9 || loadingMesh) { dialog(title, getString(R.string.kv3_no_model_detail)); return; }
        final float[] v = mesh.vertices; final double max = Math.max(mesh.size[0], Math.max(mesh.size[1], mesh.size[2])) / 1.6; final ModelTransform t = modelTransform;
        new Thread(() -> {
            // Height of the model as placed (same maths as Measure); the engine re-checks the plane against the real mesh.
            double ax = Math.toRadians(t.rx), ay = Math.toRadians(t.ry), cx = Math.cos(ax), sx = Math.sin(ax), cy = Math.cos(ay), sy = Math.sin(ay);
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (int i = 0; i + 2 < v.length; i += 3) {
                double x = v[i]*max*t.sx, y = v[i+1]*max*t.sy, z = v[i+2]*max*t.sz;
                double z1 = y*sx + z*cx, z2 = -x*sy + z1*cy; lo = Math.min(lo, z2); hi = Math.max(hi, z2);
            }
            final double height = Math.max(0, hi - lo);
            runOnUiThread(() -> { if (!isDestroyed()) showCutDialog(title, height); });
        }, "cut-height").start();
    }
    private void showCutDialog(String title, double height) {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20), dp(8), dp(20), 0);
        TextView hint = new com.keplerslicer.app.glass.KeplerUi(this).line(getString(R.string.kv3_cut_hint, fmt(height)), 13, 400, com.keplerslicer.app.glass.KeplerUi.SECONDARY); hint.setSingleLine(false); hint.setMaxLines(5); hint.setEllipsize(null); form.addView(hint);
        android.widget.EditText z = numberField(form, getString(R.string.kv3_cut_height), fmt(height / 2));
        android.widget.CheckBox upper = check(form, getString(R.string.kv3_cut_keep_upper)), lower = check(form, getString(R.string.kv3_cut_keep_lower));
        MdUi.alert(this).setTitle(title).setView(form).setNegativeButton(R.string.general_project_cancel, null)
            .setPositiveButton(R.string.kv3_cut_apply, (d, w) -> {
                Double at = number(z); if (at == null) { dialog(title, getString(R.string.kv3_edit_number)); return; }
                int keep = (upper.isChecked() ? 1 : 0) | (lower.isChecked() ? 2 : 0);
                runEdit(title, NativePreferenceActions.EDIT_CUT, at, keep, null);
            }).show();
    }
    private void textTool(String title) {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20), dp(8), dp(20), 0);
        android.widget.EditText value = new android.widget.EditText(this); value.setHint(R.string.kv3_text_value); value.setSingleLine(true);
        value.setTextColor(0xFFFFFFFF); value.setHintTextColor(0x99FFFFFF); value.setText("Kepler"); form.addView(value);
        android.widget.EditText h = numberField(form, getString(R.string.kv3_text_height), "10"), depth = numberField(form, getString(R.string.kv3_text_depth), "2");
        MdUi.alert(this).setTitle(title).setView(form).setNegativeButton(R.string.general_project_cancel, null)
            .setPositiveButton(R.string.kv3_text_apply, (d, w) -> {
                Double height = number(h), thick = number(depth); String text = value.getText().toString().trim();
                if (height == null || thick == null || text.isEmpty()) { dialog(title, getString(R.string.kv3_edit_number)); return; }
                runEdit(title, NativePreferenceActions.EDIT_TEXT, height, thick, text);
            }).show();
    }
    private void assemblyTool(String title) {
        if (mesh == null || loadingMesh) { dialog(title, getString(R.string.kv3_no_model_detail)); return; }
        MdUi.alert(this).setTitle(title).setMessage(R.string.kv3_assembly_detail).setNegativeButton(R.string.general_project_cancel, null)
            .setPositiveButton(R.string.kv3_assembly_apply, (d, w) -> runEdit(title, NativePreferenceActions.EDIT_ASSEMBLY, 0, 0, null)).show();
    }
    private void runEdit(String title, int op, double a, double b, String text) {
        if (loadingMesh) return;
        loadingMesh = true; render();
        new Thread(() -> {
            try {
                NativePreferenceActions.editModel(getApplicationContext(), op, a, b, text);
                runOnUiThread(() -> { if (isDestroyed()) return; loadingMesh = false; restoreModel(); ControlPreferences.scheduleAutoSlice(this); });
            } catch (Exception | LinkageError e) {
                final String m = EngineText.clean(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                runOnUiThread(() -> { if (isDestroyed()) return; loadingMesh = false; render(); dialog(getString(R.string.kv3_edit_failed), m); });
            }
        }, "native-model-edit").start();
    }
    private android.widget.EditText numberField(LinearLayout form, String label, String initial) {
        TextView caption = new com.keplerslicer.app.glass.KeplerUi(this).line(label, 12, 500, com.keplerslicer.app.glass.KeplerUi.SECONDARY); caption.setPadding(0, dp(12), 0, 0); form.addView(caption);
        android.widget.EditText field = new android.widget.EditText(this); field.setSingleLine(true); field.setText(initial);
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL); field.setTextColor(0xFFFFFFFF);
        form.addView(field); return field;
    }
    private android.widget.CheckBox check(LinearLayout form, String label) {
        android.widget.CheckBox box = new android.widget.CheckBox(this); box.setText(label); box.setChecked(true); box.setTextColor(0xFFFFFFFF); form.addView(box); return box;
    }
    private static Double number(android.widget.EditText field) {
        try { double v = Double.parseDouble(field.getText().toString().trim().replace(',', '.')); return Double.isFinite(v) ? v : null; } catch (NumberFormatException e) { return null; }
    }
    /** Applies a complete transform with the same save/rollback rules as the X/Y/Z fields. */
    private void applyTransform(ModelTransform next) {
        if (stlView == null || meshFileName == null || !previewReady || !transformSupported()) return;
        final StlView view = stlView; final String file = meshFileName; final ModelTransform old = modelTransform;
        dragReady(view, viewportTransform, viewportMove, false);
        view.setModelTransform(next, error -> {
            if (isDestroyed() || stlView != view) return;
            try { if (error != null) throw new IllegalArgumentException(error.getMessage()); CurrentModelStore.saveTransform(this, file, next); modelTransform = next; dragReady(view, viewportTransform, viewportMove, true); refreshToolFields(); ControlPreferences.scheduleAutoSlice(this); }
            catch (Exception e) { view.setModelTransform(old, ignored -> { if (!isDestroyed() && stlView == view) { dragReady(view, viewportTransform, viewportMove, true); refreshToolFields(); } }); dialog(getString(R.string.kv3_apply_failed), e.getMessage()); }
        });
    }

    /** Desktop drop-down menu under its button (tablet mode): icon and name per row, 48 dp tall for touch. */
    private void dropdown(View anchor, int[] menuIcons, String[] names, java.util.function.IntConsumer chosen) {
        LinearLayout list = vertical(); list.setPadding(dp(6), dp(6), dp(6), dp(6));
        list.setBackground(com.keplerslicer.app.glass.Desktop.surface(this, 12));
        final android.widget.PopupWindow popup = new android.widget.PopupWindow(list, -2, -2, true);
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            LinearLayout row = horizontal(); row.setGravity(Gravity.CENTER_VERTICAL); row.setMinimumHeight(dp(48)); row.setMinimumWidth(dp(220)); row.setPadding(dp(12), 0, dp(16), 0);
            row.addView(kui.image(menuIcons[i], 20, com.keplerslicer.app.glass.KeplerUi.WHITE), new LinearLayout.LayoutParams(dp(20), dp(20)));
            TextView t = kui.line(names[i], 14, 600, com.keplerslicer.app.glass.KeplerUi.WHITE); LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2); tp.leftMargin = dp(12); row.addView(t, tp);
            kui.pressable(row, null, 8); row.setContentDescription(names[i]);
            row.setOnClickListener(x -> { popup.dismiss(); chosen.accept(index); });
            list.addView(row, lp(-1, -2));
        }
        popup.setElevation(dp(8)); popup.setOutsideTouchable(true); popup.setAnimationStyle(0);
        list.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.showAsDropDown(anchor, anchor.getWidth() - list.getMeasuredWidth(), dp(8));
    }

    /** Tablet tool bar: the active tool is highlighted, as the selected gizmo on desktop. */
    private void markTabletTools() {
        if (tabletToolButtons == null) return;
        int[] icons = {R.drawable.kv3_move, R.drawable.kv3_refresh, R.drawable.kv3_scale};
        for (int i = 0; i < tabletToolButtons.length; i++) {
            if (!(tabletToolButtons[i] instanceof FrameLayout)) continue;
            boolean on = toolsMode && paint == null && i == activeTool && i < icons.length; FrameLayout b = (FrameLayout) tabletToolButtons[i];
            if (b.isSelected() == on) continue;
            b.setSelected(on);
            kui.pressable(b, on ? kui.shape(com.keplerslicer.app.glass.KeplerUi.MINT, 0, 22) : kui.glass(22), 22);
            if (i < icons.length && b.getChildAt(0) instanceof ImageView) ((ImageView) b.getChildAt(0)).setImageDrawable(kui.icon(icons[i], on ? com.keplerslicer.app.glass.KeplerUi.ON_ACTIVE : com.keplerslicer.app.glass.KeplerUi.WHITE));
        }
    }

    private void openTools() {
        if (plateLocked()) { dialog(getString(R.string.kv3_mode_tools), getString(R.string.plate_locked)); return; }
        boolean editable = previewReady && mesh != null && !loadingMesh && mesh.vertices.length > 0; // Move/Rotate/Scale check transformSupported() themselves
        if (!editable) { dialog(getString(R.string.kv3_mode_tools), getString(R.string.kv3_tools_unavailable)); return; }
        if (layersMode) showPreparation();
        setToolsMode(true);
    }
    private void setToolsMode(boolean on) {
        toolsMode = on; if (!on && stlView != null) stlView.setFacePick(null);
        if (modeBar != null) modeBar.select(on ? 2 : layersMode ? 1 : 0);
        syncPanels();
        if (bedHint != null && on) bedHint.setVisibility(View.GONE); else if (bedHint != null && !layersMode) bedHint.setVisibility(View.VISIBLE);
        if (stlView != null) { stlView.setMoveMode(on && activeTool == 0 && previewReady && moveMode, viewportMoveCallback); stlView.setRotateGizmo(on && activeTool == 1 && previewReady, viewportMoveCallback); }
        if (on) refreshToolFields();
    }
    private void selectTool(int index) {
        activeTool = index; if (index == 0) moveMode = true; if (stlView != null) stlView.setFacePick(null);
        int[] labels = {R.string.kv3_tool_move, R.string.kv3_tool_rotate, R.string.kv3_tool_scale};
        if (toolTitle != null) toolTitle.setText(labels[index]);
        if (toolTiles != null) for (int i = 0; i < toolTiles.length; i++) {
            boolean on = i == index; LinearLayout t = toolTiles[i];
            kui.pressable(t, on ? kui.shape(com.keplerslicer.app.glass.KeplerUi.WHITE, 0, 16) : kui.glass(16), 16); t.setSelected(on);
            ((ImageView) t.getChildAt(0)).setImageDrawable(kui.icon(new int[]{R.drawable.kv3_move, R.drawable.kv3_refresh, R.drawable.kv3_scale, R.drawable.kv3_lay_on_face, R.drawable.kv3_cut, R.drawable.kv3_support_paint, R.drawable.kv3_seam_paint, R.drawable.kv3_fuzzy_skin, R.drawable.kv3_text, R.drawable.kv3_measure, R.drawable.kv3_assembly, R.drawable.kv3_brim_ears}[i], on ? com.keplerslicer.app.glass.KeplerUi.ON_ACTIVE : com.keplerslicer.app.glass.KeplerUi.WHITE));
            ((TextView) t.getChildAt(1)).setTextColor(on ? com.keplerslicer.app.glass.KeplerUi.ON_ACTIVE : com.keplerslicer.app.glass.KeplerUi.WHITE);
        }
        if (stlView != null) { stlView.setMoveMode(toolsMode && index == 0 && previewReady, viewportMoveCallback); stlView.setRotateGizmo(toolsMode && index == 1 && previewReady, viewportMoveCallback); }
        refreshToolFields(); markTabletTools();
    }
    /** Values shown in the X/Y/Z fields for the active tool, from the stored model transform. */
    private double[] toolValues(ModelTransform t) {
        if (activeTool == 1) return new double[]{t.rx, t.ry, t.rz};
        if (activeTool == 2) return new double[]{t.sx * 100, t.sy * 100, t.sz * 100};
        return new double[]{t.offsetX, t.offsetY, t.offsetZ};
    }
    private void refreshToolFields() {
        if (axisFields == null) return;
        double[] v = toolValues(modelTransform);
        String unit = getString(activeTool == 1 ? R.string.kv3_unit_deg : activeTool == 2 ? R.string.kv3_unit_percent : R.string.kv3_unit_mm);
        boolean editable = previewReady && mesh != null && mesh.vertices.length > 0 && transformSupported();
        for (int i = 0; i < 3; i++) {
            EditText field = axisFields[i].value; if (!field.hasFocus()) field.setText(String.format(java.util.Locale.ROOT, "%.2f", v[i]));
            boolean enabled = editable;
            field.setEnabled(enabled); axisFields[i].view.setAlpha(enabled ? 1f : .5f);
            field.setContentDescription(getString(R.string.kv3_axis, new String[]{"X","Y","Z"}[i], unit));
        }
    }
    private void commitAxis(int axis) {
        if (axisFields == null || stlView == null || meshFileName == null || !previewReady || !transformSupported()) return;
        if (activeTool == 0 && axis == 2) return;
        double value;
        try { value = Double.parseDouble(axisFields[axis].value.getText().toString().trim().replace(',', '.')); }
        catch (NumberFormatException e) { refreshToolFields(); dialog(getString(R.string.kv3_apply_failed), getString(R.string.kv3_check_value)); return; }
        ModelTransform t = modelTransform; double[] v = toolValues(t);
        if (Math.abs(v[axis] - value) < 1e-9) return;
        v[axis] = value;
        final ModelTransform next;
        try {
            if (activeTool == 1) next = new ModelTransform(v[0], v[1], v[2], t.sx, t.sy, t.sz, t.offsetX, t.offsetY, t.offsetZ);
            else if (activeTool == 2) next = new ModelTransform(t.rx, t.ry, t.rz, v[0] / 100, v[1] / 100, v[2] / 100, t.offsetX, t.offsetY, t.offsetZ);
            else next = new ModelTransform(t.rx, t.ry, t.rz, t.sx, t.sy, t.sz, v[0], v[1], Math.min(0, v[2]));
        } catch (IllegalArgumentException e) { refreshToolFields(); dialog(getString(R.string.kv3_apply_failed), e.getMessage()); return; }
        final StlView view = stlView; final String file = meshFileName; final ModelTransform old = modelTransform;
        dragReady(view, viewportTransform, viewportMove, false);
        view.setModelTransform(next, error -> {
            if (isDestroyed() || stlView != view) return;
            try {
                if (error != null) throw new IllegalArgumentException(error.getMessage());
                CurrentModelStore.saveTransform(this, file, next); modelTransform = next;
                dragReady(view, viewportTransform, viewportMove, true); refreshToolFields(); ControlPreferences.scheduleAutoSlice(this);
            } catch (Exception e) {
                view.setModelTransform(old, ignored -> { if (!isDestroyed() && stlView == view) { dragReady(view, viewportTransform, viewportMove, true); refreshToolFields(); } });
                dialog(getString(R.string.kv3_apply_failed), e.getMessage());
            }
        });
    }

    private void openSettingsPanel(){startActivity(new Intent(this, SettingsActivity.class));}

    private void dragReady(StlView view,View transform,View move,boolean ready) {
        previewReady=ready;transform.setEnabled(ready);move.setEnabled(ready);transform.setAlpha(ready?1:.38f);move.setAlpha(ready?1:.38f);
        if(workspaceSlice!=null){boolean sliceable=ready&&canSliceModel()&&materialChosen();workspaceSlice.setEnabled(true);workspaceSlice.setAlpha(sliceable?1:.42f);}
        view.setMoveMode(ready&&moveMode&&toolsMode&&activeTool==0,value->commitDrag(view,value,transform,move));
        if(ready)refreshToolFields();
    }
    private void commitDrag(StlView view,ModelTransform next,View transform,View move) {
        if(stlView!=view||meshFileName==null||!previewReady){view.discardPendingMove();return;}
        String expected=meshFileName;ModelTransform old=modelTransform;
        try {
            CurrentModelStore.saveTransform(this,expected,next);modelTransform=next;dragReady(view,transform,move,false);
            view.setModelTransform(next,error->{
                if(isDestroyed()||stlView!=view)return;
                if(error==null){dragReady(view,transform,move,true);ControlPreferences.scheduleAutoSlice(this);return;}
                try{CurrentModelStore.saveTransform(this,expected,old);modelTransform=old;}
                catch(Exception rollback){view.discardPendingMove();dialog(getString(R.string.l10n_save_error),rollback.getMessage());return;}
                view.setModelTransform(old,restoreError->{if(isDestroyed()||stlView!=view)return;dragReady(view,transform,move,restoreError==null);});
                dialog(getString(R.string.l10n_ws_move_failed),error.getMessage());
            });
        }catch(Exception error){view.discardPendingMove();dialog(getString(R.string.l10n_ws_position_save_failed),error.getMessage());}
    }

    private FrameLayout toolButton(String icon, String label, Runnable action) {
        FrameLayout b = new FrameLayout(this); b.addView(new Icon(icon,INK),new FrameLayout.LayoutParams(dp(23),dp(23),Gravity.CENTER));
        b.setContentDescription(label); click(b,action); return b;
    }

    private void loadUserPresetNames(TextView printer, TextView filament, TextView process) {
        final android.content.Context app = getApplicationContext();
        bedWorker.execute(() -> {
            String[] kinds = {"printer", "filament", "process"};
            String[] names = new String[kinds.length];
            for (int i = 0; i < kinds.length; i++) try {
                com.keplerslicer.app.settings.PresetRepository.Preset preset =
                        SettingsState.repository(app).load(SettingsState.activeId(app, kinds[i]));
                if (!preset.system) names[i] = preset.name;
            } catch (java.io.IOException ignored) { /* Keep the already displayed system label. */ }
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (names[0] != null && printer.isAttachedToWindow()) printer.setText(names[0]);
                if (names[1] != null && filament.isAttachedToWindow()) filament.setText(names[1]);
                if (names[2] != null && process.isAttachedToWindow()) process.setText(names[2] + " ›");
            });
        });
    }

    private void loadSelectedBed(PrinterProfileCatalog.Profile printer, StlView view, TextView hint) {
        final org.json.JSONObject projectConfig=modelProjectMode==2?projectEffectiveConfig():null;
        final boolean embeddedBed=modelProjectMode==2;
        final String key = embeddedBed?"project-bed:"+meshFileName+":"+(modelMetadata==null?-1:modelMetadata.optInt("selected_plate",-1)):printer == null || printer.imported ? "" : printer.vendor + "\\n" + printer.name + "\\n" + printer.path;
        if (!key.equals(bedKey)) { bedKey = key; loadedBed = null; bedPending = false; ++bedGeneration; }
        if (key.isEmpty()) {if(pendingLayerPreview&&visibleWorkspace){pendingLayerPreview=false;dialog(getString(R.string.l10n_ws_layer_preview),getString(R.string.l10n_ws_bed_unavailable));}return;}
        if (loadedBed != null) {
            view.setBuildVolume(loadedBed, error -> {
                if (isDestroyed() || stlView != view || !key.equals(bedKey)) return;
                hint.setText(error == null ? bedDescription(loadedBed) : getString(R.string.l10n_ws_grid_unavailable));
                if(error==null)maybeOpenRequestedLayers();else if(pendingLayerPreview){pendingLayerPreview=false;dialog(getString(R.string.l10n_ws_layer_preview),getString(R.string.l10n_ws_bed_load_failed));}
            });
            return;
        }
        if (staleBed == null && loadedBed == null) {
            BedGeometry remembered = memory().bed(key); if (remembered != null) { staleBed = remembered; staleBedKey = key; }
        }
        if (staleBed != null && key.equals(staleBedKey)) view.setBuildVolume(staleBed, error -> { });
        if (bedPending || SliceActivity.isSlicing()) return;
        bedPending = true; final int generation = bedGeneration;
        final android.content.Context app = getApplicationContext();
        bedWorker.execute(() -> {
            try {
                if(embeddedBed&&projectConfig==null)throw new java.io.IOException(L.t("Embedded project bed settings are unavailable"));
                SettingsState.Snapshot snapshot = SettingsState.capture(app);
                CompatibilityResources paths = CompatibilityResources.prepareTriple(app, printer.vendor, "", null, snapshot);
                if (Thread.currentThread().isInterrupted() || !visibleWorkspace || SliceActivity.isSlicing()) {
                    runOnUiThread(() -> {
                        if (isDestroyed() || generation != bedGeneration) return;
                        bedPending = false;
                        if (visibleWorkspace && !SliceActivity.isSlicing() && stlView != null && bedHint != null)
                            loadSelectedBed(printer, stlView, bedHint);
                    }); return;
                }
                double[] raw = projectConfig!=null?projectBedPayload(projectConfig):com.keplerslicer.engine.ExperimentalSlicer.printerBuildVolume(printer.vendor, printer.name,
                        paths.resources.getAbsolutePath(), paths.data.getAbsolutePath());
                if (raw == null) {
                    runOnUiThread(() -> {
                        if (isDestroyed() || generation != bedGeneration) return;
                        bedPending = false;
                        if (visibleWorkspace && !SliceActivity.isSlicing()) bedHandler.postDelayed(() -> {
                            if (!isDestroyed() && visibleWorkspace && generation == bedGeneration && stlView != null && bedHint != null) loadSelectedBed(printer, stlView, bedHint);
                        }, 2000);
                    });
                    return;
                }
                BedGeometry result = new BedGeometry(raw);
                if (!snapshot.fingerprint.equals(SettingsState.capture(app).fingerprint)) {
                    runOnUiThread(() -> {
                        if (isDestroyed() || generation != bedGeneration) return;
                        bedPending = false;
                        if (visibleWorkspace && stlView != null && bedHint != null) loadSelectedBed(printer, stlView, bedHint);
                    });
                    return;
                }
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != bedGeneration) return;
                    loadedBed = result; bedPending = false; memory().saveBed(key, result);
                    if (stlView != null && bedHint != null) loadSelectedBed(printer, stlView, bedHint);
                });
            } catch (Exception | LinkageError error) {
                runOnUiThread(() -> { if (!isDestroyed() && generation == bedGeneration) { bedPending = false; if (stlView == view) {hint.setText(getString(R.string.l10n_ws_grid_unavailable));if(pendingLayerPreview){pendingLayerPreview=false;dialog(getString(R.string.l10n_ws_layer_preview),getString(R.string.l10n_ws_bed_load_failed));}} } });
            }
        });
    }
    private org.json.JSONObject projectEffectiveConfig(){org.json.JSONObject snapshot=modelMetadata==null?null:modelMetadata.optJSONObject("project_settings_snapshot");return snapshot==null?null:snapshot.optJSONObject(modelProjectMode==2?"effective":"effective_selected_printer");}
    private String projectOptionCaption(String key,String fallback){try{org.json.JSONObject config=projectEffectiveConfig();if(config==null)return fallback;Object value=config.getJSONObject("options").getJSONObject(key).get("value");if(value instanceof org.json.JSONArray){org.json.JSONArray array=(org.json.JSONArray)value;if(array.length()==0)return fallback;return array.get(0).toString()+(array.length()>1?" +"+(array.length()-1):"");}return value==org.json.JSONObject.NULL?fallback:value.toString();}catch(Exception ignored){return fallback;}}
    private static double[] projectBedPayload(org.json.JSONObject config)throws java.io.IOException {try{org.json.JSONObject options=config.getJSONObject("options");org.json.JSONArray area=options.getJSONObject("printable_area").getJSONArray("value");if(area.length()<3||area.length()>512)throw new java.io.IOException("Invalid embedded project bed outline");double[] payload=new double[1+area.length()*2];payload[0]=options.getJSONObject("printable_height").getDouble("value");for(int i=0;i<area.length();i++){org.json.JSONArray p=area.getJSONArray(i);if(p.length()!=2)throw new java.io.IOException("Invalid embedded project bed point");payload[1+i*2]=p.getDouble(0);payload[2+i*2]=p.getDouble(1);}return payload;}catch(org.json.JSONException e){throw new java.io.IOException("Embedded project bed is not available",e);}}

    private void loadPrinterCover(ImageView target, View fallback, PrinterProfileCatalog.Profile printer) {
        final String identity = printer.vendor + "\u0000" + printer.model; target.setTag(identity);
        new Thread(() -> {
            try {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                try (java.io.InputStream in = getAssets().open("kepler_printer_models.json")) {
                    byte[] buf=new byte[8192]; int n,total=0; while((n=in.read(buf))!=-1) { total+=n; if(total>2*1024*1024) return; out.write(buf,0,n); }
                }
                org.json.JSONObject index=new org.json.JSONObject(new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
                if (!PrinterProfileCatalog.UPSTREAM_REVISION.equals(index.optString("revision"))) return;
                org.json.JSONArray models=index.getJSONArray("models"); String filename="";
                for(int i=0;i<models.length();i++) { org.json.JSONObject m=models.getJSONObject(i); if(printer.vendor.equals(m.optString("vendor")) && printer.model.equals(m.optString("model"))) { filename=m.optString("cover",""); break; } }
                if(!filename.matches("[0-9a-f]{64}\\.png")) return;
                Bitmap bitmap; try(java.io.InputStream in=getAssets().open("printer_covers/"+filename)) { BitmapFactory.Options options=new BitmapFactory.Options(); options.inSampleSize=4; bitmap=BitmapFactory.decodeStream(in,null,options); }
                if(bitmap!=null) runOnUiThread(()->{ if(!isDestroyed() && identity.equals(target.getTag())) { target.setImageBitmap(bitmap); fallback.setVisibility(View.GONE); } });
            } catch(Exception ignored) { }
        },"printer-cover").start();
    }

    private java.io.File lastResult() {
        java.util.Map<String, ?> last = getSharedPreferences("last_slice_result", MODE_PRIVATE).getAll();
        String savedFingerprint = (String) last.get("job_fingerprint");
        try {
            if (savedFingerprint == null || savedFingerprint.isEmpty() || !savedFingerprint.equals(ProcessOverridesStore.currentResultFingerprint(this))) return null;
        } catch (Exception error) { return null; }
        String path = (String) last.get("path");
        if (path == null || path.isEmpty()) return null;
        try {
            java.io.File base = new java.io.File(getFilesDir(),"slicer-engine").getCanonicalFile(), file = new java.io.File(path).getCanonicalFile();
            return file.getPath().startsWith(base.getPath()+java.io.File.separator) && file.getName().equals("result.gcode") && file.isFile() && file.length()>0 ? file : null;
        } catch(java.io.IOException e) { return null; }
    }
    private void cancelLayerWork(){layerRequest++;Thread worker=layerReader;layerReader=null;if(worker!=null)worker.interrupt();}
    private void updatePreviewTabs(){if(modeBar!=null)modeBar.select(layersMode?1:toolsMode?2:0);syncPanels();}
    private void showPreparation(){cancelLayerWork();layersMode=false;layerAnalysis=null;StlView view=stlView;if(view!=null)view.clearToolpaths();if(layerPanel!=null)layerPanel.setVisibility(View.GONE);if(bedHint!=null)bedHint.setVisibility(View.VISIBLE);updatePreviewTabs();
        boolean editable=previewReady&&mesh!=null&&mesh.vertices.length>0&&transformSupported();boolean toolsOpen=previewReady&&mesh!=null&&mesh.vertices.length>0;if(viewportTransform!=null){viewportTransform.setEnabled(toolsOpen);viewportTransform.setAlpha(toolsOpen?1:.38f);} // Cut/Text/Assembly also work on 3MF modelsif(viewportMove!=null){viewportMove.setEnabled(editable);viewportMove.setAlpha(editable?1:.38f);}if(view!=null)view.setMoveMode(editable&&moveMode&&toolsMode&&activeTool==0,viewportMoveCallback);
        if(viewportMode!=null)viewportMode.setText(plateCaption());}
    private void maybeOpenRequestedLayers(){if(!pendingLayerPreview||!visibleWorkspace||isDestroyed()||isFinishing())return;if(loadingMesh)return;if(mesh==null||mesh.vertices.length==0){pendingLayerPreview=false;dialog(getString(R.string.l10n_ws_layer_preview),getString(R.string.l10n_ws_open_and_slice));return;}if(!previewReady||loadedBed==null)return;pendingLayerPreview=false;openLayerPreview();}
    private int layerPreviewWaits;
    private void openLayerPreview(){
        if(layersMode)return;if((stlView==null||loadingMesh||!previewReady||loadedBed==null)&&mesh!=null&&layerPreviewWaits++<40){if(stlView!=null)stlView.postDelayed(this::openLayerPreview,250);return;}layerPreviewWaits=0; // tablet: wait for the plate instead of an alert
if(stlView==null||mesh==null||loadingMesh||!previewReady){dialog(getString(R.string.l10n_ws_layer_preview),getString(R.string.l10n_ws_open_wait));return;}
        if(loadedBed==null){dialog(getString(R.string.l10n_ws_layer_preview),getString(R.string.l10n_ws_wait_bed));return;}
        cancelLayerWork();final int request=layerRequest;final StlView view=stlView;layersMode=true;setToolsMode(false);updatePreviewTabs();view.setMoveMode(false,null);view.setRotateGizmo(false);
        if(viewportTransform!=null){viewportTransform.setEnabled(false);viewportTransform.setAlpha(.38f);}if(viewportMove!=null){viewportMove.setEnabled(false);viewportMove.setAlpha(.38f);}if(viewportMode!=null)viewportMode.setText(getString(R.string.l10n_ws_reading_layers));
        layerReader=new Thread(()->{try{
            java.io.File file=lastResult();if(file==null)throw new java.io.IOException(getString(R.string.l10n_ws_reslice_first));long length=file.length(),modified=file.lastModified();
            GcodeAnalysis analysis=GcodeAnalysis.read(file);if(Thread.currentThread().isInterrupted())return;
            ToolpathGeometry geometry=ToolpathGeometry.build(analysis);if(Thread.currentThread().isInterrupted())return;geometry.colors=ToolpathGeometry.colors(analysis,colorMode,geometry.vertices.capacity()/3);
            java.io.File fresh=lastResult();if(fresh==null||!fresh.equals(file)||file.length()!=length||file.lastModified()!=modified)throw new java.io.IOException(getString(R.string.l10n_ws_changed_reslice));
            if(geometry.layerCount==0)throw new java.io.IOException(getString(R.string.l10n_ws_no_layers));
            runOnUiThread(()->{if(isDestroyed()||stlView!=view||request!=layerRequest||!layersMode)return;layerAnalysis=analysis;layerGeometry=geometry;updateLegend(analysis);int count=geometry.layerCount;
                view.setToolpaths(geometry,count,error->{if(isDestroyed()||stlView!=view||request!=layerRequest||!layersMode)return;if(error!=null){showPreparation();dialog(getString(R.string.l10n_ws_layer_preview),error.getMessage());return;}
                    if(viewportMode!=null)viewportMode.setText(plateCaption());if(bedHint!=null)bedHint.setVisibility(View.GONE);layerPanel.setVisibility(View.VISIBLE);layerSlider.setMax(count-1);if(layerRail!=null)layerRail.setMax(count-1);syncPanels();
                    layerSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){int visible=progress+1;view.setVisibleToolpathLayers(visible);updateLayerCaption(analysis,geometry,visible);}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});
                    layerSlider.setProgress(count-1);updateLayerCaption(analysis,geometry,count);
                });});
        }catch(Exception | OutOfMemoryError error){String message=error instanceof OutOfMemoryError?getString(R.string.l10n_ws_toolpath_memory):error.getMessage()==null?getString(R.string.l10n_ws_gcode_read_failed):error.getMessage();runOnUiThread(()->{if(isDestroyed()||stlView!=view||request!=layerRequest||!layersMode)return;showPreparation();dialog(getString(R.string.l10n_ws_layer_preview),message);});}},"workspace-toolpaths");layerReader.start();
    }
    private void updateLayerCaption(GcodeAnalysis analysis,ToolpathGeometry geometry,int visible){int index=visible-1;double z=index<analysis.layers.size()?analysis.layers.get(index).z:Double.NaN;String value=getString(R.string.l10n_ws_approx_layer)+visible+" / "+geometry.layerCount+(Double.isFinite(z)?String.format(java.util.Locale.getDefault(),L.t("  ·  Z %.3f мм"),z):getString(R.string.l10n_ws_z_unknown));if(geometry.skippedLayersWithoutZ>0||analysis.unsupportedMoves>0||analysis.skippedUnknown>0)value+=getString(R.string.l10n_ws_partial);layerCaption.setText(getString(R.string.kv3_layer,visible));layerCaption.setContentDescription(value);if(layerRail!=null){layerRail.setProgress(visible-1);layerRail.setContentDescription(value);}}
    private boolean openExport() {
        java.io.File file=lastResult(); if(file==null) { dialog(getString(R.string.l10n_export),getString(R.string.l10n_ws_reslice_first)); return false; }
        String name=getSharedPreferences("last_slice_result",MODE_PRIVATE).getString("model","kepler-result");
        if(name==null || name.trim().isEmpty()) name="kepler-result";
        name=name.replaceFirst("(?i)\\.(stl|3mf|obj|step|stp|amf)$","");
        com.keplerslicer.app.glass.KeplerUi.tab(this,new Intent(this,GcodeExportActivity.class).putExtra(GcodeExportActivity.EXTRA_RESULT_PATH,file.getAbsolutePath()).putExtra(GcodeExportActivity.EXTRA_FILE_NAME,name+".gcode"),false);
        return true;
    }

    private boolean setupOpen;
    /** First launch or an unfinished printer change: printer, filament and process must be chosen before the workspace. */
    private void openSetupIfNeeded() {
        if (setupOpen || SetupFlow.complete(this)) return;
        setupOpen = true;
        startActivityForResult(new Intent(this, PrinterPickerActivity.class).putExtra(SetupFlow.EXTRA, true), PICK_PRINTER);
    }
    private void openPrinterProfiles() {
        startActivityForResult(new Intent(this, PrinterPickerActivity.class), PICK_PRINTER);
    }
    private void openPrintProfile(String kind, int request) {
        startActivityForResult(new Intent(this, ProfilePickerActivity.class).putExtra(ProfilePickerActivity.EXTRA_KIND, kind), request);
    }
    private static boolean isProject(org.json.JSONObject metadata){return metadata!=null&&"3mf".equals(metadata.optString("format"));}
    private static double[] projectBounds(org.json.JSONObject metadata){
        if(!isProject(metadata))return null;try{org.json.JSONArray lo=metadata.getJSONArray("bounds_min"),hi=metadata.getJSONArray("bounds_max");if(lo.length()!=3||hi.length()!=3)throw new org.json.JSONException("bounds");double[] v=new double[6];for(int i=0;i<3;i++){v[i]=lo.getDouble(i);v[i+3]=hi.getDouble(i);if(!Double.isFinite(v[i])||!Double.isFinite(v[i+3])||v[i+3]<v[i])throw new org.json.JSONException("bounds");}return v;}catch(Exception e){return new double[0];}
    }
    private boolean canSliceModel(){if(mesh==null||mesh.vertices.length==0||modelMetadata!=null&&(modelMetadata.optBoolean("mapping_unresolved",false)||modelMetadata.has("printable_instances")&&modelMetadata.optInt("printable_instances",0)==0))return false;if(modelMetadata!=null&&"3mf".equals(modelMetadata.optString("format"))){if(modelProjectMode<0&&GeneralProjectPolicy.inspect(modelMetadata).settingsPresent)return false;if(modelProjectMode>0){org.json.JSONObject snapshot=modelMetadata.optJSONObject("project_settings_snapshot"),validation=snapshot==null?null:snapshot.optJSONObject("validation"),mode=validation==null?null:validation.optJSONObject(modelProjectMode==2?"embedded_printer":"selected_printer");if(mode==null||!mode.optBoolean("can_load",false))return false;}org.json.JSONObject project=modelMetadata.optJSONObject("project_data");if(project!=null&&(project.optBoolean("unknown_unit_requires_validation",false)||project.optBoolean("non_mm_transform_requires_validation",false)))return false;}return true;}
    private boolean needsProjectModeReview(){return modelMetadata!=null&&"3mf".equals(modelMetadata.optString("format"))&&modelProjectMode<0&&GeneralProjectPolicy.inspect(modelMetadata).settingsPresent;}
    private String bedDescription(BedGeometry bed){if(mesh!=null&&mesh.vertices.length==0)return getString(R.string.l10n_ws_plate_empty);if(modelMetadata!=null&&modelMetadata.optBoolean("mapping_unresolved",false))return getString(R.string.l10n_ws_plate_unknown_all);if(modelMetadata!=null&&modelMetadata.has("printable_instances")&&modelMetadata.optInt("printable_instances",0)==0)return getString(R.string.l10n_ws_no_objects);
        double[] b=projectBounds(modelMetadata);if(b!=null&&b.length==6&&bed!=null){double eps=.001;if(b[0]<bed.centerX-bed.width/2-eps||b[3]>bed.centerX+bed.width/2+eps||b[1]<bed.centerY-bed.depth/2-eps||b[4]>bed.centerY+bed.depth/2+eps||b[2]<-eps||b[5]>bed.height+eps)return getString(R.string.l10n_ws_3mf_outside);return getString(R.string.l10n_ws_3mf_hint);}return getString(R.string.l10n_ws_bed_profile);
    }
    private boolean transformSupported() {
        if(modelMetadata==null)return true;
        String format=modelMetadata.optString("format");if("3mf".equals(format))return !modelMetadata.optBoolean("mapping_unresolved",false);
        if("step".equals(format)){org.json.JSONObject provenance=modelMetadata.optJSONObject("step_import");return provenance!=null&&provenance.optInt("object_count",0)==1&&provenance.optInt("instance_count",0)==1;}
        if("obj".equals(format)){org.json.JSONArray objects=modelMetadata.optJSONArray("objects");if(objects==null||objects.length()!=1)return false;org.json.JSONObject object=objects.optJSONObject(0);org.json.JSONArray instances=object==null?null:object.optJSONArray("instances");return instances!=null&&instances.length()==1;}
        return false;
    }
    private void openModelDetails() { startActivityForResult(new Intent().setClassName(this,getPackageName()+".ModelDetailsActivity"),REQUEST_MODEL_DETAILS); }

    /** A 3MF project brings its own filaments; otherwise a filament profile must be selected. */
    private boolean materialChosen() { return modelProjectMode > 0 || PrintProfileCatalog.loadSelection(this, PrintProfileCatalog.FILAMENT) != null; }
    private void openSlice() {
        if (mesh == null || loadingMesh) { dialog(getString(R.string.l10n_ws_slicing), getString(R.string.l10n_ws_open_first)); return; }
        if(!canSliceModel()){dialog(getString(R.string.l10n_ws_slicing),needsProjectModeReview()?getString(R.string.native_project_legacy_mode):modelMetadata!=null&&modelMetadata.optBoolean("mapping_unresolved",false)?getString(R.string.l10n_ws_plate_mapping):getString(R.string.l10n_ws_check_project));return;}
        if (!previewReady) { dialog(getString(R.string.l10n_ws_model_updating), getString(R.string.l10n_ws_wait_preview)); return; }
        if (!materialChosen()) {
            // Slicing needs a filament profile; a printer connection is not required.
            MdUi.alert(this).setTitle(getString(R.string.l10n_ws_slicing)).setMessage(L.t("Выберите материал (профиль пластика) — без него нарезка невозможна. Подключение к принтеру для нарезки не нужно."))
                .setNegativeButton(R.string.l10n_cancel, null).setPositiveButton(getString(R.string.kv3_choose_material), (d, w) -> openPrintProfile(PrintProfileCatalog.FILAMENT, PICK_FILAMENT)).show();
            return;
        }
        // Slicing runs on the plate in both interfaces: progress over the canvas, then the layer preview opens.
        if (!SliceActivity.start(getApplicationContext(), -1)) showSliceProgress(SliceActivity.status(), true, false);
    }

    // ---- Tablet: slicing on the plate, as desktop OrcaSlicer (progress over the canvas, then Preview) ----
    private FrameLayout workspaceViewport;
    private LinearLayout sliceStrip; private TextView sliceStripText; private View sliceStripSpinner, sliceStripClose;
    private final SliceActivity.Progress sliceProgress = (text, working, success) -> { if (!isDestroyed()) showSliceProgress(text, working, success); };
    private void showSliceProgress(String text, boolean working, boolean success) {
        final FrameLayout host = tabletMode ? tabletViewport : workspaceViewport;
        if (host == null) return;
        if (sliceStrip == null || sliceStrip.getParent() != host) {
            LinearLayout strip = horizontal(); strip.setGravity(Gravity.CENTER_VERTICAL); strip.setPadding(dp(16), dp(10), dp(8), dp(10));
            strip.setBackground(com.keplerslicer.app.glass.Desktop.surface(this, 14)); strip.setElevation(dp(6));
            android.widget.ProgressBar spin = new android.widget.ProgressBar(this); spin.setIndeterminate(true); spin.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(com.keplerslicer.app.glass.KeplerUi.MINT));
            strip.addView(spin, new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView t = kui.line("", 14, 600, com.keplerslicer.app.glass.KeplerUi.WHITE); t.setMaxLines(3); t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1); tp.leftMargin = dp(12); tp.rightMargin = dp(8); strip.addView(t, tp);
            FrameLayout close = kui.glassButton(R.drawable.kv3_plus, 36, 18, getString(R.string.glass_dismiss), () -> { if (sliceStrip != null) sliceStrip.setVisibility(View.GONE); });
            close.getChildAt(0).setRotation(45); strip.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));
            FrameLayout.LayoutParams sp;
            if (tabletMode) { sp = new FrameLayout.LayoutParams(dp(520), -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL); sp.bottomMargin = dp(24); }
            else { sp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP); sp.setMargins(dp(16), dp(68), dp(16), 0); } // phone: under the plate chip, clear of the bottom panel
            host.addView(strip, sp);
            sliceStrip = strip; sliceStripText = t; sliceStripSpinner = spin; sliceStripClose = close;
        }
        sliceStrip.setVisibility(View.VISIBLE); sliceStrip.bringToFront();
        String shown = text == null ? "" : text.trim();
        if (!working && success) shown = getString(R.string.kv3_slice_done_short);
        else if (working) { int nl = shown.indexOf(10); if (nl > 0) shown = shown.substring(0, nl); } // first line only
        sliceStripText.setText(EngineText.clean(shown));
        sliceStripSpinner.setVisibility(working ? View.VISIBLE : View.GONE);
        sliceStripClose.setVisibility(working ? View.GONE : View.VISIBLE);
        if (!working && success) {
            sliceStrip.postDelayed(() -> { if (sliceStrip != null && !SliceActivity.isSlicing()) sliceStrip.setVisibility(View.GONE); }, 2500);
            if (layersMode) showPreparation();
            if (tabletMode) openTabletSection("preview"); // the result opens in Preview, as on desktop
            else openLayerPreview();
        }
    }

    private void editModelTransform() {
        if (mesh == null || stlView == null || meshFileName == null || !previewReady || !transformSupported()) return;
        final String currentFile = meshFileName;
        final ModelTransform old = modelTransform;
        final StlView view = stlView;
        String[] labels = {getString(R.string.l10n_ws_rotate_x), getString(R.string.l10n_ws_rotate_y), getString(R.string.l10n_ws_rotate_z), getString(R.string.l10n_ws_scale_x), getString(R.string.l10n_ws_scale_y), getString(R.string.l10n_ws_scale_z), getString(R.string.l10n_ws_offset_x), getString(R.string.l10n_ws_offset_y)};
        double[] values = {old.rx, old.ry, old.rz, old.sx, old.sy, old.sz, old.offsetX, old.offsetY};
        EditText[] fields = new EditText[8];
        LinearLayout inputs = vertical(); pad(inputs, 20, 4, 20, 8);
        TextView hint = text(getString(R.string.l10n_ws_offset_note), 13, MUTED, false);
        inputs.addView(hint); gap(inputs, 8);
        for (int i = 0; i < fields.length; i++) {
            inputs.addView(text(labels[i], 12, MUTED, true));
            EditText field = new EditText(this); fields[i] = field;
            field.setSingleLine(true); field.setText(Double.toString(values[i])); field.setTextColor(INK); field.setTextSize(16);
            field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
            field.setContentDescription(labels[i]); inputs.addView(field, lp(-1, 48)); gap(inputs, 8);
        }
        ScrollView scroller = new ScrollView(this); scroller.addView(inputs);
        AlertDialog editor = MdUi.alert(this).setTitle(getString(R.string.l10n_ws_transform)).setView(scroller)
                .setNegativeButton(getString(R.string.general_project_cancel), null).setNeutralButton(getString(R.string.l10n_reset), null).setPositiveButton(getString(R.string.l10n_apply), null).create();
        activeTransformEditor = editor;
        editor.setOnDismissListener(ignored -> { if (activeTransformEditor == editor) activeTransformEditor = null; });
        editor.setOnShowListener(ignored -> {
            editor.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                double[] defaults = {0, 0, 0, 1, 1, 1, 0, 0};
                for (int i = 0; i < fields.length; i++) fields[i].setText(Double.toString(defaults[i]));
            });
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    double[] n = new double[8];
                    for (int i = 0; i < n.length; i++) n[i] = Double.parseDouble(fields[i].getText().toString().trim().replace(',', '.'));
                    ModelTransform next = new ModelTransform(n[0], n[1], n[2], n[3], n[4], n[5], n[6], n[7], modelTransform.offsetZ);
                    editor.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    editor.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                    editor.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
                    editor.setCancelable(false);
                    view.setModelTransform(next, error -> {
                        if (isDestroyed() || stlView != view) return;
                        try {
                            if (error != null) throw new IllegalArgumentException(error.getMessage());
                            CurrentModelStore.saveTransform(this, currentFile, next);
                            modelTransform = next; editor.dismiss(); render();ControlPreferences.scheduleAutoSlice(this);
                        } catch (Exception e) {
                            view.setModelTransform(old, ignored2 -> { });
                            editor.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            editor.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                            editor.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                            editor.setCancelable(true);
                            dialog(getString(R.string.l10n_ws_apply_failed), e.getMessage());
                        }
                    });
                } catch (IllegalArgumentException e) {
                    dialog(getString(R.string.l10n_check_values), e instanceof NumberFormatException ? getString(R.string.kv3_edit_number) : e.getMessage());
                }
            });
        });
        editor.show();
    }
    private class Icon extends View {
        final String name; final Paint p = new Paint(3); final int color;
        Icon(String name, int color) { super(MainActivity.this); this.name = name; this.color = color; }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); c.save(); c.scale(getWidth()/24f, getHeight()/24f); p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.7f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            Path a = new Path();
            switch(name) {
                case "move": c.drawLine(12,3,12,21,p);c.drawLine(3,12,21,12,p);c.drawLine(12,3,9,6,p);c.drawLine(12,3,15,6,p);c.drawLine(12,21,9,18,p);c.drawLine(12,21,15,18,p);c.drawLine(3,12,6,9,p);c.drawLine(3,12,6,15,p);c.drawLine(21,12,18,9,p);c.drawLine(21,12,18,15,p);break;
                case "wifi": c.drawArc(new RectF(2,4,22,22),225,90,false,p);c.drawArc(new RectF(6,9,18,21),225,90,false,p);p.setStyle(Paint.Style.FILL);c.drawCircle(12,19,1.5f,p);break;
                case "add": c.drawLine(12,4,12,20,p); c.drawLine(4,12,20,12,p); break;
                case "cube": a.moveTo(12,3); a.lineTo(20,7.5f); a.lineTo(20,16.5f); a.lineTo(12,21); a.lineTo(4,16.5f); a.lineTo(4,7.5f); a.close(); a.moveTo(4,7.5f); a.lineTo(12,12); a.lineTo(20,7.5f); a.moveTo(12,12); a.lineTo(12,21); c.drawPath(a,p); break;
                case "layers": a.moveTo(12,3); a.lineTo(21,9); a.lineTo(12,15); a.lineTo(3,9); a.close(); a.moveTo(3,13); a.lineTo(12,19); a.lineTo(21,13); c.drawPath(a,p); break;
                case "export": a.moveTo(12,15); a.lineTo(12,3); a.moveTo(7,8); a.lineTo(12,3); a.lineTo(17,8); a.moveTo(4,15); a.lineTo(4,20); a.lineTo(20,20); a.lineTo(20,15); c.drawPath(a,p); break;
                case "prefs": c.drawCircle(12,12,3,p); c.drawCircle(12,12,8,p); c.drawLine(12,1,12,4,p); c.drawLine(12,20,12,23,p); c.drawLine(1,12,4,12,p); c.drawLine(20,12,23,12,p); c.drawLine(4,4,6,6,p); c.drawLine(18,18,20,20,p); c.drawLine(4,20,6,18,p); c.drawLine(18,6,20,4,p); break;
                case "home": a.moveTo(3,11); a.lineTo(12,4); a.lineTo(21,11); a.moveTo(6,10); a.lineTo(6,20); a.lineTo(18,20); a.lineTo(18,10); a.moveTo(10,20); a.lineTo(10,14); a.lineTo(14,14); a.lineTo(14,20); c.drawPath(a,p); break;
                case "folder": a.moveTo(3,7); a.lineTo(9,7); a.lineTo(11,9); a.lineTo(21,9); a.lineTo(21,19); a.lineTo(3,19); a.close(); c.drawPath(a,p); break;
                case "printer": c.drawRoundRect(new RectF(4,4,20,20),2,2,p); c.drawLine(7,8,17,8,p); c.drawLine(8,16,16,16,p); c.drawCircle(12,12,1,p); break;
                case "person": c.drawCircle(12,8,4,p); c.drawArc(new RectF(5,13,19,24),180,180,false,p); break;
                case "filament": c.drawCircle(12,12,8,p); c.drawCircle(12,12,3,p); c.drawLine(12,4,12,9,p); c.drawLine(19,16,15,14,p); c.drawLine(5,16,9,14,p); break;
                case "settings": case "tune": c.drawLine(4,7,20,7,p); c.drawLine(4,12,20,12,p); c.drawLine(4,17,20,17,p); p.setStyle(Paint.Style.FILL); c.drawCircle(9,7,2,p); c.drawCircle(16,12,2,p); c.drawCircle(10,17,2,p); break;
                default: c.drawCircle(12,12,9,p); c.drawLine(12,10,12,16,p); p.setStyle(Paint.Style.FILL); c.drawCircle(12,7,1,p); break;
            }
            c.restore();
        }
    }
}
