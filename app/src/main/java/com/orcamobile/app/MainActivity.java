package com.orcamobile.app;

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
    private LinearLayout root, bottom;
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
    private TextView bedHint, workspaceSlice;
    private TextView prepareTab, layersTab, layerCaption, viewportMode;
    private LinearLayout layerPanel; private SeekBar layerSlider;
    private FrameLayout viewportMove, viewportTransform; private StlView.MoveCallback viewportMoveCallback;
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
                loadSelectedBed(OrcaProfileCatalog.loadSelection(this), stlView, bedHint);
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
    private void loadModelUri(Uri uri) {
        final int sequence = ++loadSequence;
        pendingUri = uri; loadingMesh = true; render();
        new Thread(() -> {
            try {
                String name = null, mime = null;
                try (android.database.Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0);
                } catch (RuntimeException ignored) { }
                try { mime=getContentResolver().getType(uri); } catch(RuntimeException ignored) { }
                if(name!=null&&(name.toLowerCase(java.util.Locale.ROOT).endsWith(".step")||name.toLowerCase(java.util.Locale.ROOT).endsWith(".stp")))throw new java.io.IOException(getString(R.string.general_step_blocked));
                name=ModelFilename.normalize(name,uri.getLastPathSegment(),mime);
                CurrentModelStore prepared = CurrentModelStore.prepare(this, uri, name);
                GeneralProjectPolicy policy=GeneralProjectPolicy.inspect(prepared.metadata);
                runOnUiThread(() -> {
                    if (isDestroyed() || sequence != loadSequence) { prepared.discard(); return; }
                    reviewPreparedImport(prepared,uri,sequence,policy);
                });
            } catch (Exception e) {
                final String message = e.getMessage() == null ? "Не удалось прочитать файл." : e.getMessage();
                runOnUiThread(() -> {
                    if (isDestroyed() || sequence != loadSequence) return;
                    pendingUri = null; loadingMesh = false; render();
                    if (mesh == null) restoreModel();
                    dialog("Не удалось открыть STL", message);
                });
            }
        }, "stl-loader").start();
    }

    private void reviewPreparedImport(CurrentModelStore prepared,Uri uri,int sequence,GeneralProjectPolicy policy){
        if(pendingPrepared!=null&&pendingPrepared!=prepared)pendingPrepared.discard();pendingPrepared=prepared;
        Runnable accept=()->publishPreparedImport(prepared,uri,sequence,policy);
        Runnable project=()->{String mode=GeneralPreferences.projectLoadBehaviour(this);boolean ask=policy.needsDecision(mode);
            if(!ask){accept.run();return;}
            int message=policy.settingsPresent?("load_all".equals(mode)?R.string.general_project_load_all_blocked:R.string.general_project_config_blocked):R.string.general_project_no_config;
            MdUi.alert(this).setTitle(R.string.general_project_title).setMessage(message).setPositiveButton(R.string.general_project_geometry,(d,w)->accept.run()).setNegativeButton(R.string.general_project_cancel,(d,w)->cancelPreparedImport(prepared,sequence)).setOnCancelListener(d->cancelPreparedImport(prepared,sequence)).show();};
        if(policy.customCodePresent&&!GeneralPreferences.suppressModifiedGcodeWarning(this)){
            android.widget.CheckBox suppress=new android.widget.CheckBox(this);suppress.setText(R.string.phase2_preferences_gcode_warning);
            MdUi.alert(this).setTitle(R.string.general_modified_gcode_title).setMessage(R.string.general_modified_gcode_detail).setView(suppress).setPositiveButton(R.string.general_continue,(d,w)->{if(suppress.isChecked())GeneralPreferences.prefs(this).edit().putBoolean("no_warn_when_modified_gcodes",true).apply();project.run();}).setNegativeButton(R.string.general_project_cancel,(d,w)->cancelPreparedImport(prepared,sequence)).setOnCancelListener(d->cancelPreparedImport(prepared,sequence)).show();
        }else project.run();
    }
    private void cancelPreparedImport(CurrentModelStore prepared,int sequence){prepared.discard();if(pendingPrepared==prepared)pendingPrepared=null;if(sequence==loadSequence){pendingUri=null;loadingMesh=false;render();if(mesh==null)restoreModel();}}
    private void publishPreparedImport(CurrentModelStore prepared,Uri uri,int sequence,GeneralProjectPolicy policy){
        if(isDestroyed()||sequence!=loadSequence){prepared.discard();return;}
        try{prepared.publish(this);if(pendingPrepared==prepared)pendingPrepared=null;RecentModels.remember(getApplicationContext(),uri,prepared.name,prepared.mesh);mesh=prepared.mesh;meshName=prepared.name;meshFileName=prepared.file.getName();modelTransform=prepared.transform;modelMetadata=prepared.metadata;pendingUri=null;loadingMesh=false;render();
            if(prepared.file.getName().endsWith(".obj"))dialog("Материалы OBJ","Загружена геометрия OBJ. Внешние файлы материалов .mtl и текстуры пока не импортируются; цвета и связанные настройки могут отсутствовать.");
            else if(policy.settingsPresent)dialog(getString(R.string.general_project_title),getString(R.string.general_project_config_blocked));
            else if(prepared.metadata!=null&&prepared.metadata.optBoolean("mapping_unresolved",false))dialog("Пластина не определена","Нельзя определить соответствие объектов пластине. Исходный проект сохранён; нарезка недоступна.");
            else if(prepared.metadata!=null&&prepared.metadata.optInt("non_printable_instances",0)>0)dialog("Объекты на столе","Непечатаемые экземпляры видны в предпросмотре, но не попадут в нарезку.");
            if(!policy.settingsPresent&&canSliceModel())ControlPreferences.scheduleAutoSlice(this);
        }catch(java.io.IOException e){prepared.discard();pendingUri=null;loadingMesh=false;render();if(mesh==null)restoreModel();dialog("Не удалось сохранить модель",e.getMessage());}
    }

    private void restoreModel() {
        final int sequence = ++loadSequence;
        loadingMesh = true; render();
        new Thread(() -> {
            try {
                CurrentModelStore saved = CurrentModelStore.load(this);
                runOnUiThread(() -> {
                    if (isDestroyed() || sequence != loadSequence) return;
                    if (saved != null) { mesh = saved.mesh; meshName = saved.name; meshFileName = saved.file.getName(); modelTransform = saved.transform; modelMetadata=saved.metadata; }
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
        MdUi.alert(this).setTitle(title).setMessage(message).setPositiveButton("Понятно", null).show();
    }

    private void updatePalette() {
        darkMode = !"light".equals(getSharedPreferences("app_preferences", MODE_PRIVATE).getString("theme", "dark"));
        BG = darkMode ? 0xff111311 : 0xfffafaf7; SURFACE = darkMode ? 0xff1c1e1c : 0xffeff0ec;
        LINE = darkMode ? 0xff3b3d39 : 0xffdadcd7; INK = darkMode ? 0xffe4e6df : 0xff1b1c1b; MUTED = darkMode ? 0xffacafa8 : 0xff696b68;
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(darkMode ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private void render() {
        refreshAfterResume = false; cancelLayerWork(); layersMode=false; layerAnalysis=null;
        if (stlView != null) { stlView.onPause(); stlView = null; }
        updatePalette(); selected = 0;
        android.content.res.Configuration config = getResources().getConfiguration();
        compactLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && config.screenWidthDp >= 500;
        root = vertical(); root.setBackgroundColor(BG);
        if (android.os.Build.VERSION.SDK_INT >= 35) root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom); return insets;
        });
        setContentView(root);
        workspace();
        FrameLayout navShell = new FrameLayout(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int available = Math.max(0, View.MeasureSpec.getSize(widthSpec) - dp(24));
                if (bottom != null && bottom.getLayoutParams() != null) bottom.getLayoutParams().width = Math.min(available, dp(600));
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        bottom = horizontal(); bottom.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL); bottom.setBackground(outline(SURFACE, 24, LINE));
        pad(bottom, 2, compactLandscape ? 2 : 4, 2, compactLandscape ? 2 : 4); bottom.setMinimumHeight(dp(compactLandscape ? 54 : 72));
        navShell.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        root.addView(navShell, lp(-1, -2)); gap(root, compactLandscape ? 2 : 8);
        navigation();
    }

    private void workspace() {
        OrcaProfileCatalog.Profile printer = OrcaProfileCatalog.loadSelection(this);
        OrcaProcessFilamentCatalog.Profile material = OrcaProcessFilamentCatalog.loadSelection(this, OrcaProcessFilamentCatalog.FILAMENT);
        OrcaProcessFilamentCatalog.Profile process = OrcaProcessFilamentCatalog.loadSelection(this, OrcaProcessFilamentCatalog.PROCESS);
        LinearLayout head = horizontal(); pad(head, compactLandscape ? 8 : 16, 8, compactLandscape ? 8 : 16, 4);
        FrameLayout cover = new FrameLayout(this); cover.setBackground(outline(SURFACE, 14, LINE));
        View coverFallback = new Icon("printer", MUTED); cover.addView(coverFallback, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.FIT_CENTER); pad(image, 3, 3, 3, 3);
        ColorMatrix grey = new ColorMatrix(); grey.setSaturation(0); image.setColorFilter(new ColorMatrixColorFilter(grey)); cover.addView(image, new FrameLayout.LayoutParams(-1, -1));
        if (printer != null && !printer.imported) loadPrinterCover(image, coverFallback, printer);
        head.addView(cover, lp(compactLandscape ? 42 : 56, compactLandscape ? 42 : 56)); click(cover, this::openPrinterProfiles);
        LinearLayout info = vertical(); pad(info, 12, 0, 0, 0);
        LinearLayout machine = horizontal(); TextView printerName = text(printer == null ? "Выберите принтер" : (printer.model == null || printer.model.isEmpty() ? printer.name : printer.model), 16, INK, true);
        printerName.setMaxLines(1); printerName.setEllipsize(android.text.TextUtils.TruncateAt.END); machine.addView(printerName,new LinearLayout.LayoutParams(0,-2,1));
        if (printer != null && printer.variant != null && !printer.variant.isEmpty()) {
            TextView nozzle = text(printer.variant, 10, INK, true); pad(nozzle, 7, 5, 7, 5); nozzle.setBackground(outline(SURFACE, 10, LINE));
            LinearLayout.LayoutParams n = lp(-2, -2); n.leftMargin=dp(6); machine.addView(nozzle,n);
        }
        TextView arrow = text("⌄", 20, INK, false); pad(arrow,6,0,0,0); machine.addView(arrow); click(machine, this::openPrinterProfiles); info.addView(machine);
        gap(info, 5);
        LinearLayout profiles = horizontal(); TextView materialLabel = text(material == null ? "Материал ›" : material.name + " ›", 12, MUTED, false);
        materialLabel.setMinHeight(dp(compactLandscape ? 36 : 40)); materialLabel.setGravity(Gravity.CENTER_VERTICAL);
        materialLabel.setMaxLines(1); materialLabel.setEllipsize(android.text.TextUtils.TruncateAt.END); profiles.addView(materialLabel,new LinearLayout.LayoutParams(0,-2,1));
        click(materialLabel, () -> openPrintProfile(OrcaProcessFilamentCatalog.FILAMENT, PICK_FILAMENT));
        TextView processLabel = text(process == null ? "Процесс ›" : process.name + " ›", 12, MUTED, false); pad(processLabel,8,0,0,0);
        processLabel.setMinHeight(dp(compactLandscape ? 36 : 40)); processLabel.setGravity(Gravity.CENTER_VERTICAL);
        processLabel.setMaxLines(1); processLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        click(processLabel, () -> openPrintProfile(OrcaProcessFilamentCatalog.PROCESS, PICK_PROCESS)); profiles.addView(processLabel,new LinearLayout.LayoutParams(0,-2,1)); info.addView(profiles);
        loadUserPresetNames(printerName, materialLabel, processLabel);
        head.addView(info, new LinearLayout.LayoutParams(0,-2,1));
        FrameLayout network=toolButton("wifi","Подключения принтеров",()->{Intent intent=new Intent();intent.setClassName(this,getPackageName()+".NetworkActivity");startActivity(intent);});head.addView(network,lp(44,44));
        LinearLayout wideBody = null, side = null;
        if (compactLandscape) {
            wideBody = horizontal(); wideBody.setGravity(Gravity.TOP); side = vertical();
            side.addView(head, lp(-1,-2)); wideBody.addView(side, lp(Math.min(220, getResources().getConfiguration().screenWidthDp / 3),-1));
            root.addView(wideBody, new LinearLayout.LayoutParams(-1,0,1));
        } else root.addView(head);

        FrameLayout viewport = new FrameLayout(this); viewport.setBackgroundColor(BG);
        StlView view = new StlView(this, mesh, projectBounds(modelMetadata)); stlView = view; view.setDarkMode(darkMode); viewport.addView(view, new FrameLayout.LayoutParams(-1,-1));
        LinearLayout stage=horizontal();stage.setBackground(outline(SURFACE,16,LINE));pad(stage,3,3,3,3);
        prepareTab=text("Подготовка",12,INK,true);layersTab=text("Слои",12,MUTED,true);for(TextView tab:new TextView[]{prepareTab,layersTab}){tab.setGravity(Gravity.CENTER);pad(tab,12,0,12,0);stage.addView(tab,lp(-2,38));}
        FrameLayout.LayoutParams stagePos=new FrameLayout.LayoutParams(-2,-2,Gravity.LEFT|Gravity.TOP);stagePos.setMargins(dp(16),dp(10),0,0);viewport.addView(stage,stagePos);
        TextView modeLabel=text(moveMode ? "Перемещение" : "Вращение вида",11,MUTED,true);viewportMode=modeLabel;pad(modeLabel,10,6,10,6);modeLabel.setBackground(outline(SURFACE,12,LINE));FrameLayout.LayoutParams modePosition=new FrameLayout.LayoutParams(-2,-2,Gravity.LEFT|Gravity.TOP);modePosition.setMargins(dp(16),dp(62),0,0);viewport.addView(modeLabel,modePosition);
        click(prepareTab,this::showPreparation);click(layersTab,this::openLayerPreview);updatePreviewTabs();
        LinearLayout tools = vertical(); tools.setBackground(outline(SURFACE, 18, LINE)); pad(tools,4,4,4,4);
        FrameLayout reset = toolButton("cube", "Сбросить ракурс", view::resetCamera); tools.addView(reset,lp(44,44));
        FrameLayout layers = toolButton("layers", "Предпросмотр слоёв", this::openLayerPreview); tools.addView(layers,lp(44,44));
        FrameLayout transform = toolButton("tune", "Преобразовать модель", this::editModelTransform); tools.addView(transform,lp(44,44));
        FrameLayout move=toolButton("move","Переместить модель",()->{});tools.addView(move,lp(44,44));
        move.setBackground(outline(moveMode ? LINE : SURFACE,12,LINE));
        move.setContentDescription(moveMode ? "Перемещение модели включено. Нажмите для вращения вида" : "Вращение вида включено. Нажмите для перемещения модели");
        FrameLayout.LayoutParams toolPosition = new FrameLayout.LayoutParams(-2,-2,Gravity.RIGHT|Gravity.TOP); toolPosition.setMargins(0,dp(8),dp(16),0); viewport.addView(tools,toolPosition);
        boolean hasModel = mesh != null && !loadingMesh; final boolean editable = hasModel && mesh.vertices.length>0 && transformSupported(); transform.setEnabled(false); transform.setAlpha(.38f); move.setEnabled(false);move.setAlpha(.38f);
        StlView.MoveCallback moved=next->commitDrag(view,next,transform,move); viewportMove=move;viewportTransform=transform;viewportMoveCallback=moved;
        click(move,()->{moveMode=!moveMode;modeLabel.setText(moveMode?"Перемещение":"Вращение вида");move.setBackground(outline(moveMode?LINE:SURFACE,12,LINE));move.setContentDescription(moveMode?"Перемещение модели включено. Нажмите для вращения вида":"Вращение вида включено. Нажмите для перемещения модели");view.setMoveMode(moveMode,moved);});
        FrameLayout add = new FrameLayout(this); add.setBackground(outline(SURFACE,100,LINE));
        add.addView(new Icon("add",INK),new FrameLayout.LayoutParams(dp(26),dp(26),Gravity.CENTER)); add.setContentDescription("Импортировать модель"); click(add,this::chooseStl);
        FrameLayout.LayoutParams plus = new FrameLayout.LayoutParams(dp(56),dp(56),Gravity.RIGHT|Gravity.BOTTOM); plus.setMargins(0,0,dp(16),dp(14)); viewport.addView(add,plus);
        TextView hint = text(loadingMesh ? "Читаем модель…" : mesh == null ? "Добавьте модель" : mesh.vertices.length==0 ? "Пластина пуста" : modelMetadata!=null && modelMetadata.optBoolean("mapping_unresolved",false) ? "Пластина не определена · все объекты проекта" : "Условная сетка · стол загружается",11,MUTED,false);
        bedHint = hint;
        FrameLayout.LayoutParams hintPosition = new FrameLayout.LayoutParams(-2,-2,Gravity.LEFT|Gravity.BOTTOM); hintPosition.setMargins(dp(18),0,0,dp(18)); viewport.addView(hint,hintPosition);
        layerPanel=vertical();pad(layerPanel,12,8,12,8);layerPanel.setBackground(outline(SURFACE,16,LINE));layerPanel.setVisibility(View.GONE);
        layerCaption=text("",12,INK,true);layerPanel.addView(layerCaption);click(layerCaption,()->{if(layerAnalysis!=null)dialog("О просмотре слоёв","Приблизительный просмотр линий экструзии и поддерживаемых дуг на высоте слоя. Холостые перемещения, толщина линий и изменение Z внутри сегмента не воспроизводятся. Пропущенных движений с неизвестной позицией: "+layerAnalysis.skippedUnknown+"; неподдерживаемых движений: "+layerAnalysis.unsupportedMoves+"; неотображённых дуг: "+layerAnalysis.arcMoves+".");});layerSlider=new SeekBar(this);layerSlider.setContentDescription("Количество видимых слоёв");layerPanel.addView(layerSlider,lp(-1,40));
        FrameLayout.LayoutParams sliderPos=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);sliderPos.setMargins(dp(16),0,dp(88),dp(12));viewport.addView(layerPanel,sliderPos);
        if (compactLandscape) wideBody.addView(viewport,new LinearLayout.LayoutParams(0,-1,1));
        else root.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));
        if (mesh != null && !loadingMesh) {
            LinearLayout card = horizontal(); card.setBackground(outline(SURFACE,18,LINE)); pad(card,12,7,12,7);
            FrameLayout modelIcon = new FrameLayout(this); modelIcon.setBackground(shape(darkMode ? 0xff353835 : 0xffe1e3df,12));
            modelIcon.addView(new Icon("cube",INK),new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER)); card.addView(modelIcon,lp(compactLandscape ? 32 : 44,compactLandscape ? 32 : 44));
            LinearLayout labels = vertical(); pad(labels,12,0,0,0); TextView file = text(meshName,14,INK,true); file.setSingleLine(true); file.setEllipsize(android.text.TextUtils.TruncateAt.END); labels.addView(file);
            if (!compactLandscape) { gap(labels,3); labels.addView(text(String.format(java.util.Locale.getDefault(),"Исходная модель  ·  %.1f × %.1f × %.1f мм",mesh.size[0],mesh.size[1],mesh.size[2]),11,MUTED,false)); }
            card.addView(labels,new LinearLayout.LayoutParams(0,-2,1)); card.addView(text("›",23,INK,false));
            LinearLayout.LayoutParams cardParams=lp(-1,-2); cardParams.setMargins(dp(compactLandscape ? 8 : 16),0,dp(compactLandscape ? 8 : 16),dp(8));
            if (compactLandscape) side.addView(card,cardParams); else root.addView(card,cardParams); click(card,modelMetadata==null?this::editModelTransform:this::openModelDetails);
        }
        TextView slice = text("▶   Нарезать",15,INK,true); workspaceSlice=slice; slice.setGravity(Gravity.CENTER); slice.setBackground(outline(SURFACE,20,LINE));
        LinearLayout.LayoutParams sliceParams=lp(-1,compactLandscape ? 40 : 48); sliceParams.setMargins(dp(16),0,dp(16),dp(compactLandscape ? 4 : 8)); root.addView(slice,sliceParams); click(slice,this::openSlice);
        previewReady = mesh == null; slice.setEnabled(false); slice.setAlpha(.42f);
        if (hasModel) view.setModelTransform(modelTransform, error -> {
            if (isDestroyed() || stlView != view) return;
            previewReady = error == null; transform.setEnabled(previewReady&&editable); transform.setAlpha(previewReady&&editable ? 1 : .38f);
            move.setEnabled(previewReady&&editable);move.setAlpha(previewReady&&editable?1:.38f);view.setMoveMode(previewReady&&editable&&moveMode,moved);
            if(!editable)modeLabel.setText("Расположение проекта");
            boolean sliceable=previewReady&&canSliceModel();slice.setEnabled(sliceable); slice.setAlpha(sliceable ? 1 : .42f);
            if (error != null) {pendingLayerPreview=false;dialog("Не удалось обновить модель",error.getMessage());} else maybeOpenRequestedLayers();
        });
        loadSelectedBed(printer, view, hint);
    }

    private void openSettingsPanel(){startActivity(new Intent(this, SettingsActivity.class));}

    private void dragReady(StlView view,FrameLayout transform,FrameLayout move,boolean ready) {
        previewReady=ready;transform.setEnabled(ready);move.setEnabled(ready);transform.setAlpha(ready?1:.38f);move.setAlpha(ready?1:.38f);
        if(workspaceSlice!=null){workspaceSlice.setEnabled(ready);workspaceSlice.setAlpha(ready?1:.42f);}
        view.setMoveMode(ready&&moveMode,value->commitDrag(view,value,transform,move));
    }
    private void commitDrag(StlView view,ModelTransform next,FrameLayout transform,FrameLayout move) {
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
                com.orcamobile.app.settings.PresetRepository.Preset preset =
                        SettingsState.repository(app).load(SettingsState.activeId(app, kinds[i]));
                if (!preset.system) names[i] = preset.name;
            } catch (java.io.IOException ignored) { /* Keep the already displayed system label. */ }
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (names[0] != null && printer.isAttachedToWindow()) printer.setText(names[0]);
                if (names[1] != null && filament.isAttachedToWindow()) filament.setText(names[1] + " ›");
                if (names[2] != null && process.isAttachedToWindow()) process.setText(names[2] + " ›");
            });
        });
    }

    private void loadSelectedBed(OrcaProfileCatalog.Profile printer, StlView view, TextView hint) {
        String key = printer == null || printer.imported ? "" : printer.vendor + "\\n" + printer.name + "\\n" + printer.path;
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
                double[] raw = com.keplerslicer.engine.ExperimentalSlicer.printerBuildVolume(printer.vendor, printer.name,
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
                    loadedBed = result; bedPending = false;
                    if (stlView != null && bedHint != null) loadSelectedBed(printer, stlView, bedHint);
                });
            } catch (Exception | LinkageError error) {
                runOnUiThread(() -> { if (!isDestroyed() && generation == bedGeneration) { bedPending = false; if (stlView == view) {hint.setText("Условная сетка · область стола недоступна");if(pendingLayerPreview){pendingLayerPreview=false;dialog("Предпросмотр слоёв","Не удалось загрузить геометрию стола.");}} } });
            }
        });
    }

    private void loadPrinterCover(ImageView target, View fallback, OrcaProfileCatalog.Profile printer) {
        final String identity = printer.vendor + "\u0000" + printer.model; target.setTag(identity);
        new Thread(() -> {
            try {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                try (java.io.InputStream in = getAssets().open("orca_machine_models.json")) {
                    byte[] buf=new byte[8192]; int n,total=0; while((n=in.read(buf))!=-1) { total+=n; if(total>2*1024*1024) return; out.write(buf,0,n); }
                }
                org.json.JSONObject index=new org.json.JSONObject(new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
                if (!OrcaProfileCatalog.UPSTREAM_REVISION.equals(index.optString("revision"))) return;
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
    private void updatePreviewTabs(){if(prepareTab==null||layersTab==null)return;prepareTab.setBackground(shape(layersMode?SURFACE:LINE,12));layersTab.setBackground(shape(layersMode?LINE:SURFACE,12));prepareTab.setTextColor(layersMode?MUTED:INK);layersTab.setTextColor(layersMode?INK:MUTED);prepareTab.setSelected(!layersMode);layersTab.setSelected(layersMode);}
    private void showPreparation(){cancelLayerWork();layersMode=false;layerAnalysis=null;StlView view=stlView;if(view!=null)view.clearToolpaths();if(layerPanel!=null)layerPanel.setVisibility(View.GONE);if(bedHint!=null)bedHint.setVisibility(View.VISIBLE);updatePreviewTabs();
        boolean editable=previewReady&&mesh!=null&&mesh.vertices.length>0&&transformSupported();if(viewportTransform!=null){viewportTransform.setEnabled(editable);viewportTransform.setAlpha(editable?1:.38f);}if(viewportMove!=null){viewportMove.setEnabled(editable);viewportMove.setAlpha(editable?1:.38f);}if(view!=null)view.setMoveMode(editable&&moveMode,viewportMoveCallback);
        if(viewportMode!=null)viewportMode.setText(editable?(moveMode?"Перемещение":"Вращение вида"):"Вращение вида");}
    private void maybeOpenRequestedLayers(){if(!pendingLayerPreview||!visibleWorkspace||isDestroyed()||isFinishing())return;if(loadingMesh)return;if(mesh==null||mesh.vertices.length==0){pendingLayerPreview=false;dialog("Предпросмотр слоёв","Откройте непустую модель и выполните нарезку.");return;}if(!previewReady||loadedBed==null)return;pendingLayerPreview=false;openLayerPreview();}
    private void openLayerPreview(){
        if(layersMode)return;if(stlView==null||mesh==null||loadingMesh||!previewReady){dialog("Предпросмотр слоёв","Сначала откройте модель и дождитесь её отображения.");return;}
        if(loadedBed==null){dialog("Предпросмотр слоёв","Дождитесь загрузки геометрии выбранного стола принтера.");return;}
        cancelLayerWork();final int request=layerRequest;final StlView view=stlView;layersMode=true;updatePreviewTabs();view.setMoveMode(false,null);
        if(viewportTransform!=null){viewportTransform.setEnabled(false);viewportTransform.setAlpha(.38f);}if(viewportMove!=null){viewportMove.setEnabled(false);viewportMove.setAlpha(.38f);}if(viewportMode!=null)viewportMode.setText("Читаем слои…");
        layerReader=new Thread(()->{try{
            java.io.File file=lastResult();if(file==null)throw new java.io.IOException("Сначала повторите нарезку для текущей модели и настроек.");long length=file.length(),modified=file.lastModified();
            GcodeAnalysis analysis=GcodeAnalysis.read(file);if(Thread.currentThread().isInterrupted())return;
            ToolpathGeometry geometry=ToolpathGeometry.build(analysis);if(Thread.currentThread().isInterrupted())return;
            java.io.File fresh=lastResult();if(fresh==null||!fresh.equals(file)||file.length()!=length||file.lastModified()!=modified)throw new java.io.IOException("Модель, настройки или результат изменились. Повторите нарезку.");
            if(geometry.layerCount==0)throw new java.io.IOException("Нет поддерживаемых слоёв для отображения.");
            runOnUiThread(()->{if(isDestroyed()||stlView!=view||request!=layerRequest||!layersMode)return;layerAnalysis=analysis;int count=geometry.layerCount;
                view.setToolpaths(geometry,count,error->{if(isDestroyed()||stlView!=view||request!=layerRequest||!layersMode)return;if(error!=null){showPreparation();dialog("Предпросмотр слоёв",error.getMessage());return;}
                    if(viewportMode!=null)viewportMode.setText("Просмотр слоёв");if(bedHint!=null)bedHint.setVisibility(View.GONE);layerPanel.setVisibility(View.VISIBLE);layerSlider.setMax(count-1);
                    layerSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){int visible=progress+1;view.setVisibleToolpathLayers(visible);updateLayerCaption(analysis,geometry,visible);}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});
                    layerSlider.setProgress(count-1);updateLayerCaption(analysis,geometry,count);
                });});
        }catch(Exception | OutOfMemoryError error){String message=error instanceof OutOfMemoryError?"Недостаточно памяти для просмотра траекторий.":error.getMessage()==null?"Не удалось прочитать G-code.":error.getMessage();runOnUiThread(()->{if(isDestroyed()||stlView!=view||request!=layerRequest||!layersMode)return;showPreparation();dialog("Предпросмотр слоёв",message);});}},"workspace-toolpaths");layerReader.start();
    }
    private void updateLayerCaption(GcodeAnalysis analysis,ToolpathGeometry geometry,int visible){int index=visible-1;double z=index<analysis.layers.size()?analysis.layers.get(index).z:Double.NaN;String value="Приблизительно · слой "+visible+" / "+geometry.layerCount+(Double.isFinite(z)?String.format(java.util.Locale.getDefault(),"  ·  Z %.3f мм",z):"  ·  Z неизвестен");if(geometry.skippedLayersWithoutZ>0||analysis.unsupportedMoves>0||analysis.skippedUnknown>0)value+="  ·  неполный просмотр";layerCaption.setText(value);}
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
    private boolean canSliceModel(){return mesh!=null&&mesh.vertices.length>0&&(modelMetadata==null||(!modelMetadata.optBoolean("mapping_unresolved",false)&&(!modelMetadata.has("printable_instances")||modelMetadata.optInt("printable_instances",0)>0)));}
    private String bedDescription(BedGeometry bed){if(mesh!=null&&mesh.vertices.length==0)return "Пластина пуста";if(modelMetadata!=null&&modelMetadata.optBoolean("mapping_unresolved",false))return "Пластина не определена · все объекты проекта";if(modelMetadata!=null&&modelMetadata.has("printable_instances")&&modelMetadata.optInt("printable_instances",0)==0)return "Нет объектов для печати";
        double[] b=projectBounds(modelMetadata);if(b!=null&&b.length==6&&bed!=null){double eps=.001;if(b[0]<bed.centerX-bed.width/2-eps||b[3]>bed.centerX+bed.width/2+eps||b[1]<bed.centerY-bed.depth/2-eps||b[4]>bed.centerY+bed.depth/2+eps||b[2]<-eps||b[5]>bed.height+eps)return "Границы 3MF выходят за габариты стола";return "Положение 3MF · совместимость проверяется при нарезке";}return "Стол профиля · исключённые зоны не показаны";
    }
    private boolean transformSupported() {
        if(modelMetadata==null)return true;
        String format=modelMetadata.optString("format");if("3mf".equals(format))return false;
        if("obj".equals(format)){org.json.JSONArray objects=modelMetadata.optJSONArray("objects");if(objects==null||objects.length()!=1)return false;org.json.JSONObject object=objects.optJSONObject(0);org.json.JSONArray instances=object==null?null:object.optJSONArray("instances");return instances!=null&&instances.length()==1;}
        return false;
    }
    private void openModelDetails() { startActivityForResult(new Intent().setClassName(this,getPackageName()+".ModelDetailsActivity"),REQUEST_MODEL_DETAILS); }

    private void openSlice() {
        if (mesh == null || loadingMesh) { dialog("Нарезка", "Сначала откройте модель."); return; }
        if(!canSliceModel()){dialog("Нарезка",modelMetadata!=null&&modelMetadata.optBoolean("mapping_unresolved",false)?"Нельзя определить соответствие объектов выбранной пластине.":"На выбранной пластине нет объектов для печати.");return;}
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
    private void navigation() {
        String[] names = {"Стол", "Параметры", "Экспорт", "Общие"}; String[] icons = {"cube", "tune", "export", "prefs"};
        for (int i = 0; i < names.length; i++) {
            final int index = i; LinearLayout item = vertical(); item.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            FrameLayout pill = new FrameLayout(this); if (i == 0) pill.setBackground(shape(darkMode ? 0xff454844 : 0xffe0e2dc, 100));
            int iconSize = compactLandscape ? 20 : 24;
            pill.addView(new Icon(icons[i], selected == i ? INK : MUTED), new FrameLayout.LayoutParams(dp(iconSize), dp(iconSize), Gravity.CENTER));
            item.addView(pill, lp(compactLandscape ? 50 : 56, compactLandscape ? 26 : 32)); gap(item, compactLandscape ? 2 : 4);
            TextView label = text(names[i], compactLandscape ? 10 : 12, selected == i ? INK : MUTED, selected == i);
            label.setGravity(Gravity.CENTER); label.setMaxLines(2); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(label, lp(-1, -2));
            item.setContentDescription(names[i] + (i == 0 ? ", выбран" : "")); item.setSelected(i == 0);
            click(item, () -> { if(index==1) openSettingsPanel(); else if(index==2) openExport(); else if(index==3) startActivity(new Intent(this,PreferencesActivity.class)); });
            bottom.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
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
