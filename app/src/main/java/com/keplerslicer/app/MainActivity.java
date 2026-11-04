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
        if (state != null) {selected = state.getInt("tab", 0);pendingLayerPreview=state.getBoolean("pending_layer_preview",false);}
        render();
        if (state != null && state.getString("pending_model_uri") != null) loadModelUri(Uri.parse(state.getString("pending_model_uri")));
        else if(GeneralPreferences.modelUri(getIntent())==null)restoreModel();
        dispatchNavigationIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); dispatchNavigationIntent(intent); }
    private void dispatchNavigationIntent(Intent intent) {
        if(intent==null)return;
        Uri external=GeneralPreferences.modelUri(intent);if(external!=null){intent.setData(null);intent.removeExtra(Intent.EXTRA_STREAM);intent.setClipData(null);loadStl(external);return;}
        if(intent.getBooleanExtra("general_start_home",false)){intent.removeExtra("general_start_home");chooseStl();return;}
        if(intent.getBooleanExtra("kepler_show_layers",false)){intent.removeExtra("kepler_show_layers");pendingLayerPreview=true;maybeOpenRequestedLayers();}
        if (!intent.hasExtra("kepler_nav")) return;
        int target = intent.getIntExtra("kepler_nav", 0); intent.removeExtra("kepler_nav");
        if (target == 1) openSettingsPanel();
        else if (target == 2) openExport();
        else if (target == 3) startActivity(new Intent(this, PreferencesActivity.class));
        else render();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("pending_layer_preview",pendingLayerPreview);out.putInt("tab", selected);
        if (pendingUri != null) out.putString("pending_model_uri", pendingUri.toString());
        super.onSaveInstanceState(out);
    }

    @Override protected void onPause() { cancelLayerWork(); visibleWorkspace = false; refreshAfterResume = true; if (stlView != null) stlView.onPause(); super.onPause(); }
    @Override protected void onResume() {
        super.onResume();
        visibleWorkspace = true;
        String storedFile=getSharedPreferences("current_model",0).getString("file","");
        if(!loadingMesh&&!storedFile.isEmpty()&&!storedFile.equals(meshFileName)){restoreModel();}
        if (refreshAfterResume) { loadedBed = null; bedPending = false; ++bedGeneration; }
        if (refreshAfterResume && (activeTransformEditor == null || !activeTransformEditor.isShowing())) render();
        else {
            MdUi.prepare(this);
            if (refreshAfterResume && stlView != null && bedHint != null)
                loadSelectedBed(PrinterProfileCatalog.loadSelection(this), stlView, bedHint);
        }
        refreshAfterResume = false;
        if (stlView != null) stlView.onResume();
        maybeOpenRequestedLayers();
    }
    @Override protected void onDestroy() { if(pendingPrepared!=null){pendingPrepared.discard();pendingPrepared=null;}cancelLayerWork(); bedGeneration++; bedHandler.removeCallbacksAndMessages(null); bedWorker.shutdownNow(); super.onDestroy(); }

    private void chooseStl() {
        startActivityForResult(new Intent(this, ImportActivity.class), REQUEST_IMPORT);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if(request==REQUEST_MODEL_DETAILS){restoreModel();return;}
        if (request == PICK_PRINTER || request == PICK_PROCESS || request == PICK_FILAMENT) { if (result == RESULT_OK) {render();ControlPreferences.scheduleAutoSlice(this);} return; }
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
                final String message = e.getMessage() == null ? "Не удалось прочитать файл." : e.getMessage();
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
        android.app.Dialog dialog=new android.app.Dialog(this);dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        com.keplerslicer.app.glass.GlassUi glass=new com.keplerslicer.app.glass.GlassUi(this,root,false);
        LinearLayout body=glass.vertical();glass.pad(body,20,20,20,20);body.addView(glass.heading(title));glass.space(body,12);body.addView(glass.text(message,14,400,glass.tokens.textPrimary));
        if(rememberChoice){glass.space(body,10);com.keplerslicer.app.glass.GlassSwitch toggle=new com.keplerslicer.app.glass.GlassSwitch(this);toggle.setContentDescription(getString(R.string.general_remember_choice));rememberOut[0]=toggle;LinearLayout row=glass.horizontal();row.addView(glass.text(getString(R.string.general_remember_choice),14,400,glass.tokens.textPrimary),new LinearLayout.LayoutParams(0,-2,1));row.addView(toggle);body.addView(row);}
        for(int i=0;i<captions.length;i++){final int index=i;glass.space(body,8);TextView action=glass.action(captions[i],i==0,()->{dialog.dismiss();actions[index].run();});action.setEnabled(enabled[i]);action.setAlpha(enabled[i]?1:.45f);body.addView(action);}
        glass.space(body,8);body.addView(glass.action(getString(R.string.general_project_cancel),false,()->{dialog.dismiss();cancel.run();}));dialog.setOnCancelListener(d->cancel.run());
        ScrollView scroll=new ScrollView(this);scroll.addView(body);dialog.setContentView(glass.surface(scroll,glass.tokens.cardRadius));dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));body.measure(View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));dialog.getWindow().setGravity(Gravity.BOTTOM);dialog.getWindow().setLayout(-1,Math.min(body.getMeasuredHeight(),(int)(getResources().getDisplayMetrics().heightPixels*.85f)));}
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
            if(prepared.file.getName().endsWith(".obj"))dialog("Материалы OBJ","Загружена геометрия OBJ. Внешние файлы материалов .mtl и текстуры пока не импортируются; цвета и связанные настройки могут отсутствовать.");
            else if(policy.settingsPresent)dialog(getString(R.string.native_project_title),getString(prepared.projectMode>0?R.string.native_project_loaded_full:R.string.native_project_loaded_geometry));
            else if(prepared.metadata!=null&&prepared.metadata.optBoolean("mapping_unresolved",false))dialog("Пластина не определена","Нельзя определить соответствие объектов пластине. Исходный проект сохранён; нарезка недоступна.");
            else if(prepared.metadata!=null&&prepared.metadata.optInt("non_printable_instances",0)>0)dialog("Объекты на столе","Непечатаемые экземпляры видны в предпросмотре, но не попадут в нарезку.");
            if(canSliceModel())ControlPreferences.scheduleAutoSlice(this);
            return true;
        }catch(java.io.IOException e){prepared.discard();pendingUri=null;loadingMesh=false;render();if(mesh==null)restoreModel();dialog("Не удалось сохранить модель",e.getMessage());return false;}
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
                final String message = e.getMessage() == null ? "Не удалось прочитать модель." : e.getMessage();
                runOnUiThread(() -> {
                    if (isDestroyed() || sequence != loadSequence) return;
                    loadingMesh = false; render(); dialog("Не удалось восстановить STL", message);
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
        if (stlView != null) { stlView.onPause(); stlView = null; }
        updatePalette(); selected = 0; toolsMode = false;
        android.content.res.Configuration config = getResources().getConfiguration();
        compactLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && config.screenWidthDp >= 500;
        kui = new com.keplerslicer.app.glass.KeplerUi(this);
        FrameLayout shell = new FrameLayout(this); shellView = shell; shell.setBackground(kui.backdrop());
        root = vertical(); shell.addView(root, new FrameLayout.LayoutParams(-1,-1));
        if (android.os.Build.VERSION.SDK_INT >= 35) shell.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            root.setPadding(safe.left, safe.top, safe.right, safe.bottom); return insets;
        });
        setContentView(shell);
        workspace();
        navBar = new com.keplerslicer.app.glass.GlassNavigation(this, 0, false, index -> {
            if (index == 1) openSettingsPanel(); else if (index == 2) openExport(); else if (index == 3) startActivity(new Intent(this, PreferencesActivity.class));
            if (navBar != null) navBar.post(() -> { if (navBar != null) navBar.setSelectedItem(0); });
        });
        FrameLayout navShell = new FrameLayout(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int available = Math.max(0, View.MeasureSpec.getSize(widthSpec) - dp(32));
                if (navBar != null && navBar.getLayoutParams() != null) navBar.getLayoutParams().width = Math.min(available, dp(600));
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        navShell.addView(navBar, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        LinearLayout.LayoutParams np = lp(-1, -2); np.bottomMargin = dp(compactLandscape ? 8 : 24); root.addView(navShell, np);
    }

    private String plateCaption() {
        org.json.JSONObject project = modelMetadata == null ? null : modelMetadata.optJSONObject("project_data");
        org.json.JSONArray plates = project == null ? null : project.optJSONArray("plates");
        int plate = modelMetadata == null ? 0 : Math.max(0, modelMetadata.optInt("selected_plate", 0));
        if (plates != null && plates.length() > 1) return getString(R.string.kv3_plate, Math.min(plate, plates.length() - 1) + 1, plates.length());
        return getString(R.string.kv3_plate_single, plate + 1);
    }

    private void workspace() {
        PrinterProfileCatalog.Profile printer = PrinterProfileCatalog.loadSelection(this);
        PrintProfileCatalog.Profile material = PrintProfileCatalog.loadSelection(this, PrintProfileCatalog.FILAMENT);
        final com.keplerslicer.app.glass.KeplerUi ui = kui;
        Runnable openPrinter = modelProjectMode==2 ? this::openSettingsPanel : this::openPrinterProfiles;
        com.keplerslicer.app.glass.KeplerUi.Header head = ui.header("", openPrinter, getString(R.string.kv3_connections),
                () -> startActivity(new Intent(this, DeviceActivity.class)));
        TextView printerName = head.name;
        printerName.setText(printer == null ? getString(R.string.kv3_choose_printer) : (printer.model == null || printer.model.isEmpty() ? printer.name : printer.model));
        if (modelProjectMode==2) printerName.setText(projectOptionCaption("printer_settings_id", getString(R.string.native_project_title)));
        if (printer != null && printer.variant != null && !printer.variant.isEmpty()) {
            String nozzle = modelProjectMode==2 ? projectOptionCaption("nozzle_diameter", printer.variant) : printer.variant;
            ui.setNozzle(head, nozzle.matches("[0-9]+([.,][0-9]+)?") ? getString(R.string.kv3_nozzle, nozzle) : nozzle);
        }
        TextView materialLabel = head.material;
        materialLabel.setText(material == null ? getString(R.string.kv3_choose_material) : material.name);
        if (modelProjectMode>0) materialLabel.setText(projectOptionCaption("filament_settings_id", getString(R.string.phase2_settings_filament)));
        head.printer.setContentDescription(getString(R.string.kv3_printer_header, printerName.getText()));
        { View materialRow = (View) materialLabel.getParent(); materialRow.setMinimumHeight(dp(24)); materialRow.setContentDescription(getString(R.string.kv3_choose_material) + ": " + materialLabel.getText()); click(materialRow, () -> { if (modelProjectMode > 0) openSettingsPanel(); else openPrintProfile(PrintProfileCatalog.FILAMENT, PICK_FILAMENT); }); }
        if (modelProjectMode<=0) loadUserPresetNames(printerName, materialLabel, new TextView(this));
        LinearLayout.LayoutParams hp = lp(-1, -2); hp.setMargins(dp(20), dp(8), dp(20), 0);

        LinearLayout wideBody = null, side = null;
        if (compactLandscape) {
            wideBody = horizontal(); wideBody.setGravity(Gravity.TOP); side = vertical();
            LinearLayout.LayoutParams sh = lp(-1,-2); sh.setMargins(dp(16), dp(8), dp(4), 0); side.addView(head.view, sh);
            wideBody.addView(side, new LinearLayout.LayoutParams(Math.min(dp(380), getResources().getDisplayMetrics().widthPixels * 2 / 5), -1));
            root.addView(wideBody, new LinearLayout.LayoutParams(-1,0,1));
        } else root.addView(head.view, hp);

        FrameLayout viewport = new FrameLayout(this);
        StlView view = new StlView(this, mesh, projectBounds(modelMetadata)); stlView = view; view.setDarkMode(true);
        view.setBackdrop(kui.backdrop(), shellView);
        viewport.addView(view, new FrameLayout.LayoutParams(-1,-1));

        TextView chip = ui.chip(plateCaption(), false, null); plateChip = chip; viewportMode = chip;
        FrameLayout.LayoutParams chipPos = new FrameLayout.LayoutParams(-2,-2,Gravity.START|Gravity.TOP); chipPos.setMargins(dp(20), dp(18), dp(96), 0); viewport.addView(chip, chipPos);

        boolean hasModel = mesh != null && !loadingMesh; final boolean editable = hasModel && mesh.vertices.length>0 && transformSupported();
        modeBar = ui.modeToolbar(new String[]{getString(R.string.kv3_mode_prepare), getString(R.string.kv3_mode_preview), getString(R.string.kv3_mode_tools)},
                new Runnable[]{ () -> { showPreparation(); setToolsMode(false); }, this::openLayerPreview, this::openTools });
        FrameLayout.LayoutParams toolPosition = new FrameLayout.LayoutParams(-2,-2,Gravity.END|Gravity.TOP); toolPosition.setMargins(0, dp(16), dp(20), 0); viewport.addView(modeBar.view, toolPosition);
        View transform = modeBar.items[2]; transform.setEnabled(false); transform.setAlpha(.38f);

        FrameLayout add = ui.glassButton(R.drawable.kv3_plus, 56, 26, getString(R.string.kv3_add_model), this::chooseStl); addButton = add;
        FrameLayout.LayoutParams plus = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.END|Gravity.BOTTOM); plus.setMargins(0, 0, dp(20), dp(12)); viewport.addView(add, plus);

        TextView hint = ui.text(loadingMesh ? getString(R.string.kv3_reading_model) : mesh == null ? getString(R.string.kv3_no_model_detail) : mesh.vertices.length==0 ? "Пластина пуста" : modelMetadata!=null && modelMetadata.optBoolean("mapping_unresolved",false) ? "Пластина не определена · все объекты проекта" : "Условная сетка · стол загружается", 10, 600, com.keplerslicer.app.glass.KeplerUi.SECONDARY);
        hint.setAlpha(.6f); hint.setMaxLines(2); bedHint = hint;
        FrameLayout.LayoutParams hintPosition = new FrameLayout.LayoutParams(-2,-2,Gravity.START|Gravity.BOTTOM); hintPosition.setMargins(dp(20), 0, dp(92), dp(18)); viewport.addView(hint, hintPosition);

        statusPill = ui.statusPill(com.keplerslicer.app.glass.KeplerUi.MINT, getString(R.string.kv3_sliced_plate, 1));
        statusPill.setBackground(ui.shape(0x2E5EE0C8, 0x805EE0C8, 18)); statusPill.setVisibility(View.GONE);
        viewport.addView(statusPill, new FrameLayout.LayoutParams(chipPos));
        layerRail = new com.keplerslicer.app.glass.LayerRail(this); layerRail.setContentDescription(getString(R.string.kv3_layer, 1)); layerRail.setVisibility(View.GONE);
        FrameLayout.LayoutParams railPos = new FrameLayout.LayoutParams(dp(44), dp(150), Gravity.END|Gravity.TOP); railPos.setMargins(0, dp(192), dp(20), 0); viewport.addView(layerRail, railPos);
        layerRail.setListener(p -> { if (layerSlider != null) layerSlider.setProgress(p); });
        layerPanel = buildPreviewSheet(); layerPanel.setVisibility(View.GONE);

        if (compactLandscape) wideBody.addView(viewport, new LinearLayout.LayoutParams(0,-1,1));
        else root.addView(viewport, new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout host = vertical(); panelHost = host;
        if (compactLandscape) { side.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1)); LinearLayout.LayoutParams hostLp = lp(-1,-2); hostLp.setMargins(dp(4), 0, dp(4), dp(8)); side.addView(host, hostLp); }
        else root.addView(host, lp(-1, -2));

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
            LinearLayout.LayoutParams cardParams = lp(-1,-2); cardParams.setMargins(dp(compactLandscape ? 12 : 20), 0, dp(compactLandscape ? 12 : 20), dp(12));
            prepare.addView(card.view, cardParams);
        }
        LinearLayout slice = ui.primaryButton(R.drawable.kv3_play, getString(R.string.kv3_slice_plate), this::openSlice);
        workspaceSlice = slice;
        LinearLayout.LayoutParams sliceParams = lp(-1, compactLandscape ? 48 : 52); sliceParams.setMargins(dp(compactLandscape ? 12 : 20), 0, dp(compactLandscape ? 12 : 20), dp(12));
        prepare.addView(slice, sliceParams);
        host.addView(prepare, lp(-1,-2));

        toolsSheet = buildToolsSheet(); toolsSheet.setVisibility(View.GONE);
        LinearLayout.LayoutParams sheetParams = lp(-1,-2); sheetParams.setMargins(dp(12), 0, dp(12), dp(8)); host.addView(toolsSheet, sheetParams);
        LinearLayout.LayoutParams previewParams = lp(-1,-2); previewParams.setMargins(dp(12), 0, dp(12), dp(8)); host.addView(layerPanel, previewParams);

        previewReady = mesh == null; ui.setEnabled(slice, false);
        View move = toolTiles[0];
        StlView.MoveCallback moved = next -> commitDrag(view, next, transform, move); viewportMove = move; viewportTransform = transform; viewportMoveCallback = moved;
        if (hasModel) view.setModelTransform(modelTransform, error -> {
            if (isDestroyed() || stlView != view) return;
            previewReady = error == null; transform.setEnabled(previewReady&&hasModel); transform.setAlpha(previewReady&&hasModel ? 1 : .38f);
            view.setMoveMode(previewReady&&editable&&moveMode&&toolsMode&&activeTool==0, moved);
            boolean sliceable = previewReady&&canSliceModel(); ui.setEnabled(slice, sliceable);
            refreshToolFields();
            if (error != null) {pendingLayerPreview=false;dialog("Не удалось обновить модель",error.getMessage());} else maybeOpenRequestedLayers();
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
        TextView file = ui.line(meshName == null ? "" : meshName, 12, 500, com.keplerslicer.app.glass.KeplerUi.SECONDARY); file.setMaxWidth(dp(180)); title.addView(file);
        sheet.addView(title, lp(-1,-2));
        int[] icons = {R.drawable.kv3_move, R.drawable.kv3_refresh, R.drawable.kv3_scale, R.drawable.kv3_lay_on_face, R.drawable.kv3_cut, R.drawable.kv3_support_paint, R.drawable.kv3_seam_paint, R.drawable.kv3_fuzzy_skin, R.drawable.kv3_text, R.drawable.kv3_measure, R.drawable.kv3_assembly, R.drawable.kv3_brim_ears};
        int[] labels = {R.string.kv3_tool_move, R.string.kv3_tool_rotate, R.string.kv3_tool_scale, R.string.kv3_tool_lay_on_face, R.string.kv3_tool_cut, R.string.kv3_tool_support_paint, R.string.kv3_tool_seam_paint, R.string.kv3_tool_fuzzy_skin, R.string.kv3_tool_text, R.string.kv3_tool_measure, R.string.kv3_tool_assembly, R.string.kv3_tool_brim_ears};
        toolTiles = new LinearLayout[icons.length];
        for (int i = 0; i < icons.length; i++) {
            final int index = i; final String label = getString(labels[i]);
            toolTiles[i] = ui.toolTile(icons[i], label, i == activeTool, () -> {
                if (index <= 2) selectTool(index);
                else if (index == 3) layOnFace();
                else if (index == 5) objectTool(label, "support_type", true);
                else if (index == 6) objectTool(label, "seam_position", false);
                else if (index == 7) objectTool(label, "fuzzy_skin", false);
                else if (index == 9) measure();
                else if (index == 11) objectTool(label, "brim_type", false);
                else if (index == 4) cutTool(label);
                else if (index == 8) textTool(label);
                else if (index == 10) assemblyTool(label);
            });
        }
        sheet.addView(ui.grid(toolTiles, 4), ui.below(10));
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
        layerSlider = new SeekBar(this); layerSlider.setContentDescription("Количество видимых слоёв");
        layerSlider.setProgressTintList(android.content.res.ColorStateList.valueOf(white));
        layerSlider.setThumbTintList(android.content.res.ColorStateList.valueOf(white));
        layerSlider.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x52FFFFFF));
        layerSlider.setPadding(dp(8), 0, dp(8), 0);
        scrubber.addView(layerSlider, new LinearLayout.LayoutParams(0, dp(32), 1));
        layerCaption = ui.line(getString(R.string.kv3_layer, 1), 12, 500, secondary);
        click(layerCaption, () -> { if (layerAnalysis!=null) dialog("О просмотре слоёв","Приблизительный просмотр линий экструзии и поддерживаемых дуг на высоте слоя. Холостые перемещения, толщина линий и изменение Z внутри сегмента не воспроизводятся. Пропущенных движений с неизвестной позицией: "+layerAnalysis.skippedUnknown+"; неподдерживаемых движений: "+layerAnalysis.unsupportedMoves+"; неотображённых дуг: "+layerAnalysis.arcMoves+"."); });
        scrubber.addView(layerCaption);
        sheet.addView(scrubber, ui.below(10));
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
        if (prepareGroup != null) prepareGroup.setVisibility(toolsMode || preview ? View.GONE : View.VISIBLE);
        if (toolsSheet != null) toolsSheet.setVisibility(toolsMode ? View.VISIBLE : View.GONE);
        if (addButton != null) addButton.setVisibility(toolsMode || layersMode ? View.GONE : View.VISIBLE);
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
                for (String line : new String(buffer, java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                    String l = line.trim().toLowerCase(java.util.Locale.ROOT); if (!l.startsWith(";")) continue;
                    String value = l.contains("=") ? l.substring(l.indexOf('=') + 1).trim() : l.contains(":") ? l.substring(l.lastIndexOf(':') + 1).trim() : "";
                    if (value.isEmpty()) continue;
                    if (time == null && l.contains("estimated printing time")) time = value;
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
                ObjectSettingsStore.Snapshot scope = ObjectSettingsStore.current(app); org.json.JSONObject changes = scope.changes();
                final java.util.List<String> values = new java.util.ArrayList<>(option.enumValues), labels = new java.util.ArrayList<>();
                for (int i = 0; i < values.size(); i++) labels.add(i < option.enumLabels.size() && !option.enumLabels.get(i).isEmpty() ? option.enumLabels.get(i) : values.get(i));
                if (support) { values.add(0, null); labels.add(0, getString(R.string.kv3_support_off)); }
                String current = changes.has(key) ? String.valueOf(changes.opt(key)) : null; int selected = -1;
                Object enabled = changes.opt("enable_support"); if (support && enabled != null && ("false".equals(String.valueOf(enabled)) || "0".equals(String.valueOf(enabled)))) selected = 0;
                else if (current != null) selected = values.indexOf(current);
                final int sel = selected; final com.keplerslicer.app.settings.SettingsSchema.Option opt = option, en = enable;
                runOnUiThread(() -> { if (isDestroyed()) return;
                    com.keplerslicer.app.glass.GlassBottomSheet.choose(this, root, false, title, labels.toArray(new String[0]), sel, index -> applyObjectTool(title, key, opt, en, values.get(index)));
                });
            } catch (Exception e) { String m = EngineText.clean(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); runOnUiThread(() -> { if (!isDestroyed()) dialog(title, m); }); }
        }, "object-tool").start();
    }
    private void applyObjectTool(String title, String key, com.keplerslicer.app.settings.SettingsSchema.Option option, com.keplerslicer.app.settings.SettingsSchema.Option enable, String value) {
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            try {
                ObjectSettingsStore.Snapshot scope = ObjectSettingsStore.current(app); org.json.JSONObject changes = scope.changes();
                if (enable != null) changes.put("enable_support", enable.parseInput(value == null ? "false" : "true", changes.opt("enable_support")));
                if (value != null) changes.put(key, option.parseInput(value, changes.opt(key)));
                ObjectSettingsStore.save(app, scope.modelFileName, scope.plateIndex, scope.nativeObjectCount, changes);
                runOnUiThread(() -> { if (isDestroyed()) return; ControlPreferences.scheduleAutoSlice(this); com.keplerslicer.app.glass.GlassBottomSheet.info(this, root, false, title, getString(R.string.kv3_tool_applied)); });
            } catch (Exception e) { String m = EngineText.clean(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); runOnUiThread(() -> { if (!isDestroyed()) dialog(title, m); }); }
        }, "object-tool-save").start();
    }

    /** Lay on face: orient the largest planar face of the mesh downwards (keeps Z rotation, scale and offset). */
    private void layOnFace() {
        if (mesh == null || mesh.vertices.length < 9 || !previewReady || !transformSupported()) { dialog(getString(R.string.kv3_tool_lay_on_face), getString(R.string.kv3_tools_unavailable)); return; }
        final float[] v = mesh.vertices; final ModelTransform t = modelTransform;
        new Thread(() -> {
            java.util.HashMap<Long, double[]> clusters = new java.util.HashMap<>(); double[] best = null; double bestArea = 0;
            for (int i = 0; i + 8 < v.length; i += 9) {
                double ax = (v[i+3]-v[i])*t.sx, ay = (v[i+4]-v[i+1])*t.sy, az = (v[i+5]-v[i+2])*t.sz, bx = (v[i+6]-v[i])*t.sx, by = (v[i+7]-v[i+1])*t.sy, bz = (v[i+8]-v[i+2])*t.sz;
                double nx = ay*bz-az*by, ny = az*bx-ax*bz, nz = ax*by-ay*bx, area = Math.sqrt(nx*nx+ny*ny+nz*nz); if (area < 1e-12) continue;
                nx /= area; ny /= area; nz /= area;
                long k = ((Math.round(nx*40)+64) << 16) | ((Math.round(ny*40)+64) << 8) | (Math.round(nz*40)+64);
                double[] c = clusters.get(k); if (c == null) clusters.put(k, c = new double[4]);
                c[0] += nx*area; c[1] += ny*area; c[2] += nz*area; c[3] += area;
                if (c[3] > bestArea) { bestArea = c[3]; best = c; }
            }
            if (best == null) return;
            double len = Math.sqrt(best[0]*best[0]+best[1]*best[1]+best[2]*best[2]), nx = best[0]/len, ny = best[1]/len, nz = best[2]/len;
            double a = Math.atan2(ny, nz), r = Math.sqrt(ny*ny+nz*nz), b = Math.atan2(nx, -r);
            final ModelTransform next = new ModelTransform(Math.toDegrees(a), Math.toDegrees(b), t.rz, t.sx, t.sy, t.sz, t.offsetX, t.offsetY);
            runOnUiThread(() -> { if (!isDestroyed() && modelTransform == t) applyTransform(next); });
        }, "lay-on-face").start();
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

    private void openTools() {
        boolean editable = previewReady && mesh != null && !loadingMesh && mesh.vertices.length > 0; // Move/Rotate/Scale check transformSupported() themselves
        if (!editable) { dialog(getString(R.string.kv3_mode_tools), getString(R.string.kv3_tools_unavailable)); return; }
        if (layersMode) showPreparation();
        setToolsMode(true);
    }
    private void setToolsMode(boolean on) {
        toolsMode = on;
        if (modeBar != null) modeBar.select(on ? 2 : layersMode ? 1 : 0);
        syncPanels();
        if (bedHint != null && on) bedHint.setVisibility(View.GONE); else if (bedHint != null && !layersMode) bedHint.setVisibility(View.VISIBLE);
        if (stlView != null) stlView.setMoveMode(on && activeTool == 0 && previewReady && moveMode, viewportMoveCallback);
        if (on) refreshToolFields();
    }
    private void selectTool(int index) {
        activeTool = index; if (index == 0) moveMode = true;
        int[] labels = {R.string.kv3_tool_move, R.string.kv3_tool_rotate, R.string.kv3_tool_scale};
        if (toolTitle != null) toolTitle.setText(labels[index]);
        if (toolTiles != null) for (int i = 0; i < toolTiles.length; i++) {
            boolean on = i == index; LinearLayout t = toolTiles[i];
            kui.pressable(t, on ? kui.shape(com.keplerslicer.app.glass.KeplerUi.WHITE, 0, 16) : kui.glass(16), 16); t.setSelected(on);
            ((ImageView) t.getChildAt(0)).setImageDrawable(kui.icon(new int[]{R.drawable.kv3_move, R.drawable.kv3_refresh, R.drawable.kv3_scale, R.drawable.kv3_lay_on_face, R.drawable.kv3_cut, R.drawable.kv3_support_paint, R.drawable.kv3_seam_paint, R.drawable.kv3_fuzzy_skin, R.drawable.kv3_text, R.drawable.kv3_measure, R.drawable.kv3_assembly, R.drawable.kv3_brim_ears}[i], on ? com.keplerslicer.app.glass.KeplerUi.ON_ACTIVE : com.keplerslicer.app.glass.KeplerUi.WHITE));
            ((TextView) t.getChildAt(1)).setTextColor(on ? com.keplerslicer.app.glass.KeplerUi.ON_ACTIVE : com.keplerslicer.app.glass.KeplerUi.WHITE);
        }
        if (stlView != null) stlView.setMoveMode(toolsMode && index == 0 && previewReady, viewportMoveCallback);
        refreshToolFields();
    }
    /** Values shown in the X/Y/Z fields for the active tool, from the stored model transform. */
    private double[] toolValues(ModelTransform t) {
        if (activeTool == 1) return new double[]{t.rx, t.ry, t.rz};
        if (activeTool == 2) return new double[]{t.sx * 100, t.sy * 100, t.sz * 100};
        return new double[]{t.offsetX, t.offsetY, 0};
    }
    private void refreshToolFields() {
        if (axisFields == null) return;
        double[] v = toolValues(modelTransform);
        String unit = getString(activeTool == 1 ? R.string.kv3_unit_deg : activeTool == 2 ? R.string.kv3_unit_percent : R.string.kv3_unit_mm);
        boolean editable = previewReady && mesh != null && mesh.vertices.length > 0 && transformSupported();
        for (int i = 0; i < 3; i++) {
            EditText field = axisFields[i].value; if (!field.hasFocus()) field.setText(String.format(java.util.Locale.ROOT, "%.2f", v[i]));
            boolean enabled = editable && !(activeTool == 0 && i == 2);
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
            if (activeTool == 1) next = new ModelTransform(v[0], v[1], v[2], t.sx, t.sy, t.sz, t.offsetX, t.offsetY);
            else if (activeTool == 2) next = new ModelTransform(t.rx, t.ry, t.rz, v[0] / 100, v[1] / 100, v[2] / 100, t.offsetX, t.offsetY);
            else next = new ModelTransform(t.rx, t.ry, t.rz, t.sx, t.sy, t.sz, v[0], v[1]);
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
        if(workspaceSlice!=null){boolean sliceable=ready&&canSliceModel();workspaceSlice.setEnabled(sliceable);workspaceSlice.setAlpha(sliceable?1:.42f);}
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
                catch(Exception rollback){view.discardPendingMove();dialog("Ошибка сохранения",rollback.getMessage());return;}
                view.setModelTransform(old,restoreError->{if(isDestroyed()||stlView!=view)return;dragReady(view,transform,move,restoreError==null);});
                dialog("Не удалось переместить модель",error.getMessage());
            });
        }catch(Exception error){view.discardPendingMove();dialog("Не удалось сохранить положение",error.getMessage());}
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
        if (key.isEmpty()) {if(pendingLayerPreview&&visibleWorkspace){pendingLayerPreview=false;dialog("Предпросмотр слоёв","Геометрия выбранного стола недоступна.");}return;}
        if (loadedBed != null) {
            view.setBuildVolume(loadedBed, error -> {
                if (isDestroyed() || stlView != view || !key.equals(bedKey)) return;
                hint.setText(error == null ? bedDescription(loadedBed) : "Условная сетка · область стола недоступна");
                if(error==null)maybeOpenRequestedLayers();else if(pendingLayerPreview){pendingLayerPreview=false;dialog("Предпросмотр слоёв","Не удалось загрузить геометрию стола.");}
            });
            return;
        }
        if (bedPending || SliceActivity.isSlicing()) return;
        bedPending = true; final int generation = bedGeneration;
        final android.content.Context app = getApplicationContext();
        bedWorker.execute(() -> {
            try {
                if(embeddedBed&&projectConfig==null)throw new java.io.IOException("Embedded project bed settings are unavailable");
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
                    loadedBed = result; bedPending = false; getSharedPreferences("kepler_v3_ui", MODE_PRIVATE).edit().putFloat("bed_center_x", (float) result.centerX).putFloat("bed_center_y", (float) result.centerY).apply();
                    if (stlView != null && bedHint != null) loadSelectedBed(printer, stlView, bedHint);
                });
            } catch (Exception | LinkageError error) {
                runOnUiThread(() -> { if (!isDestroyed() && generation == bedGeneration) { bedPending = false; if (stlView == view) {hint.setText("Условная сетка · область стола недоступна");if(pendingLayerPreview){pendingLayerPreview=false;dialog("Предпросмотр слоёв","Не удалось загрузить геометрию стола.");}} } });
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
    private void maybeOpenRequestedLayers(){if(!pendingLayerPreview||!visibleWorkspace||isDestroyed()||isFinishing())return;if(loadingMesh)return;if(mesh==null||mesh.vertices.length==0){pendingLayerPreview=false;dialog("Предпросмотр слоёв","Откройте непустую модель и выполните нарезку.");return;}if(!previewReady||loadedBed==null)return;pendingLayerPreview=false;openLayerPreview();}
    private void openLayerPreview(){
        if(layersMode)return;if(stlView==null||mesh==null||loadingMesh||!previewReady){dialog("Предпросмотр слоёв","Сначала откройте модель и дождитесь её отображения.");return;}
        if(loadedBed==null){dialog("Предпросмотр слоёв","Дождитесь загрузки геометрии выбранного стола принтера.");return;}
        cancelLayerWork();final int request=layerRequest;final StlView view=stlView;layersMode=true;setToolsMode(false);updatePreviewTabs();view.setMoveMode(false,null);
        if(viewportTransform!=null){viewportTransform.setEnabled(false);viewportTransform.setAlpha(.38f);}if(viewportMove!=null){viewportMove.setEnabled(false);viewportMove.setAlpha(.38f);}if(viewportMode!=null)viewportMode.setText("Читаем слои…");
        layerReader=new Thread(()->{try{
            java.io.File file=lastResult();if(file==null)throw new java.io.IOException("Сначала повторите нарезку для текущей модели и настроек.");long length=file.length(),modified=file.lastModified();
            GcodeAnalysis analysis=GcodeAnalysis.read(file);if(Thread.currentThread().isInterrupted())return;
            ToolpathGeometry geometry=ToolpathGeometry.build(analysis);if(Thread.currentThread().isInterrupted())return;geometry.colors=ToolpathGeometry.colors(analysis,colorMode,geometry.vertices.capacity()/3);
            java.io.File fresh=lastResult();if(fresh==null||!fresh.equals(file)||file.length()!=length||file.lastModified()!=modified)throw new java.io.IOException("Модель, настройки или результат изменились. Повторите нарезку.");
            if(geometry.layerCount==0)throw new java.io.IOException("Нет поддерживаемых слоёв для отображения.");
            runOnUiThread(()->{if(isDestroyed()||stlView!=view||request!=layerRequest||!layersMode)return;layerAnalysis=analysis;layerGeometry=geometry;updateLegend(analysis);int count=geometry.layerCount;
                view.setToolpaths(geometry,count,error->{if(isDestroyed()||stlView!=view||request!=layerRequest||!layersMode)return;if(error!=null){showPreparation();dialog("Предпросмотр слоёв",error.getMessage());return;}
                    if(viewportMode!=null)viewportMode.setText(plateCaption());if(bedHint!=null)bedHint.setVisibility(View.GONE);layerPanel.setVisibility(View.VISIBLE);layerSlider.setMax(count-1);if(layerRail!=null)layerRail.setMax(count-1);syncPanels();
                    layerSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){int visible=progress+1;view.setVisibleToolpathLayers(visible);updateLayerCaption(analysis,geometry,visible);}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});
                    layerSlider.setProgress(count-1);updateLayerCaption(analysis,geometry,count);
                });});
        }catch(Exception | OutOfMemoryError error){String message=error instanceof OutOfMemoryError?"Недостаточно памяти для просмотра траекторий.":error.getMessage()==null?"Не удалось прочитать G-code.":error.getMessage();runOnUiThread(()->{if(isDestroyed()||stlView!=view||request!=layerRequest||!layersMode)return;showPreparation();dialog("Предпросмотр слоёв",message);});}},"workspace-toolpaths");layerReader.start();
    }
    private void updateLayerCaption(GcodeAnalysis analysis,ToolpathGeometry geometry,int visible){int index=visible-1;double z=index<analysis.layers.size()?analysis.layers.get(index).z:Double.NaN;String value="Приблизительно · слой "+visible+" / "+geometry.layerCount+(Double.isFinite(z)?String.format(java.util.Locale.getDefault(),"  ·  Z %.3f мм",z):"  ·  Z неизвестен");if(geometry.skippedLayersWithoutZ>0||analysis.unsupportedMoves>0||analysis.skippedUnknown>0)value+="  ·  неполный просмотр";layerCaption.setText(getString(R.string.kv3_layer,visible));layerCaption.setContentDescription(value);if(layerRail!=null){layerRail.setProgress(visible-1);layerRail.setContentDescription(value);}}
    private void openExport() {
        java.io.File file=lastResult(); if(file==null) { dialog("Экспорт","Сначала повторите нарезку для текущей модели и настроек."); return; }
        String name=getSharedPreferences("last_slice_result",MODE_PRIVATE).getString("model","kepler-result");
        if(name==null || name.trim().isEmpty()) name="kepler-result";
        if(name.toLowerCase(java.util.Locale.ROOT).endsWith(".stl")) name=name.substring(0,name.length()-4);
        startActivity(new Intent(this,GcodeExportActivity.class).putExtra(GcodeExportActivity.EXTRA_RESULT_PATH,file.getAbsolutePath()).putExtra(GcodeExportActivity.EXTRA_FILE_NAME,name+".gcode"));
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
    private boolean canSliceModel(){if(mesh==null||mesh.vertices.length==0||modelMetadata!=null&&(modelMetadata.optBoolean("mapping_unresolved",false)||modelMetadata.has("printable_instances")&&modelMetadata.optInt("printable_instances",0)==0))return false;if(modelMetadata!=null&&"3mf".equals(modelMetadata.optString("format"))){if(modelProjectMode<0&&GeneralProjectPolicy.inspect(modelMetadata).settingsPresent)return false;if(modelProjectMode>0){org.json.JSONObject snapshot=modelMetadata.optJSONObject("project_settings_snapshot"),validation=snapshot==null?null:snapshot.optJSONObject("validation"),mode=validation==null?null:validation.optJSONObject(modelProjectMode==2?"embedded_printer":"selected_printer");if(mode==null||!mode.optBoolean("can_load",false))return false;}org.json.JSONObject project=modelMetadata.optJSONObject("project_data");if(project!=null&&(project.optBoolean("unknown_unit_requires_validation",false)||project.optBoolean("non_mm_transform_requires_validation",false)||project.optJSONArray("plates")!=null&&project.optJSONArray("plates").length()>1))return false;}return true;}
    private boolean needsProjectModeReview(){return modelMetadata!=null&&"3mf".equals(modelMetadata.optString("format"))&&modelProjectMode<0&&GeneralProjectPolicy.inspect(modelMetadata).settingsPresent;}
    private String bedDescription(BedGeometry bed){if(mesh!=null&&mesh.vertices.length==0)return "Пластина пуста";if(modelMetadata!=null&&modelMetadata.optBoolean("mapping_unresolved",false))return "Пластина не определена · все объекты проекта";if(modelMetadata!=null&&modelMetadata.has("printable_instances")&&modelMetadata.optInt("printable_instances",0)==0)return "Нет объектов для печати";
        double[] b=projectBounds(modelMetadata);if(b!=null&&b.length==6&&bed!=null){double eps=.001;if(b[0]<bed.centerX-bed.width/2-eps||b[3]>bed.centerX+bed.width/2+eps||b[1]<bed.centerY-bed.depth/2-eps||b[4]>bed.centerY+bed.depth/2+eps||b[2]<-eps||b[5]>bed.height+eps)return "Границы 3MF выходят за габариты стола";return "Положение 3MF · совместимость проверяется при нарезке";}return "Стол профиля · исключённые зоны не показаны";
    }
    private boolean transformSupported() {
        if(modelMetadata==null)return true;
        String format=modelMetadata.optString("format");if("3mf".equals(format))return false;
        if("step".equals(format)){org.json.JSONObject provenance=modelMetadata.optJSONObject("step_import");return provenance!=null&&provenance.optInt("object_count",0)==1&&provenance.optInt("instance_count",0)==1;}
        if("obj".equals(format)){org.json.JSONArray objects=modelMetadata.optJSONArray("objects");if(objects==null||objects.length()!=1)return false;org.json.JSONObject object=objects.optJSONObject(0);org.json.JSONArray instances=object==null?null:object.optJSONArray("instances");return instances!=null&&instances.length()==1;}
        return false;
    }
    private void openModelDetails() { startActivityForResult(new Intent().setClassName(this,getPackageName()+".ModelDetailsActivity"),REQUEST_MODEL_DETAILS); }

    private void openSlice() {
        if (mesh == null || loadingMesh) { dialog("Нарезка", "Сначала откройте модель."); return; }
        if(!canSliceModel()){dialog("Нарезка",needsProjectModeReview()?getString(R.string.native_project_legacy_mode):modelMetadata!=null&&modelMetadata.optBoolean("mapping_unresolved",false)?"Нельзя определить соответствие объектов выбранной пластине.":"Проверьте режим проекта, выбранную пластину и совместимость настроек.");return;}
        if (!previewReady) { dialog("Модель обновляется", "Дождитесь обновления предварительного просмотра."); return; }
        startActivity(new Intent(this, SliceActivity.class));
    }

    private void editModelTransform() {
        if (mesh == null || stlView == null || meshFileName == null || !previewReady || !transformSupported()) return;
        final String currentFile = meshFileName;
        final ModelTransform old = modelTransform;
        final StlView view = stlView;
        String[] labels = {"Поворот X, °", "Поворот Y, °", "Поворот Z, °", "Масштаб X (1 = 100%)", "Масштаб Y (1 = 100%)", "Масштаб Z (1 = 100%)", "Смещение X, мм", "Смещение Y, мм"};
        double[] values = {old.rx, old.ry, old.rz, old.sx, old.sy, old.sz, old.offsetX, old.offsetY};
        EditText[] fields = new EditText[8];
        LinearLayout inputs = vertical(); pad(inputs, 20, 4, 20, 8);
        TextView hint = text("Смещение задаётся относительно центра стола. Модель автоматически ставится на стол.", 13, MUTED, false);
        inputs.addView(hint); gap(inputs, 8);
        for (int i = 0; i < fields.length; i++) {
            inputs.addView(text(labels[i], 12, MUTED, true));
            EditText field = new EditText(this); fields[i] = field;
            field.setSingleLine(true); field.setText(Double.toString(values[i])); field.setTextColor(INK); field.setTextSize(16);
            field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
            field.setContentDescription(labels[i]); inputs.addView(field, lp(-1, 48)); gap(inputs, 8);
        }
        ScrollView scroller = new ScrollView(this); scroller.addView(inputs);
        AlertDialog editor = MdUi.alert(this).setTitle("Преобразование модели").setView(scroller)
                .setNegativeButton("Отмена", null).setNeutralButton("Сбросить", null).setPositiveButton("Применить", null).create();
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
                    ModelTransform next = new ModelTransform(n[0], n[1], n[2], n[3], n[4], n[5], n[6], n[7]);
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
                            dialog("Не удалось применить", e.getMessage());
                        }
                    });
                } catch (IllegalArgumentException e) {
                    dialog("Проверьте значения", e instanceof NumberFormatException ? "Введите число в каждом поле." : e.getMessage());
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
