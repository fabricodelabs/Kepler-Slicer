package com.orcamobile.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import com.orcamobile.app.glass.GlassBackground;
import com.orcamobile.app.glass.GlassBottomSheet;
import com.orcamobile.app.glass.GlassIcon;
import com.orcamobile.app.glass.GlassSurface;
import com.orcamobile.app.glass.GlassTypography;
import com.orcamobile.app.glass.GlassUi;
import com.orcamobile.app.settings.PresetJson;
import com.orcamobile.app.settings.PresetRepository;
import com.orcamobile.app.settings.ProjectConfigArchive;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Configuration-only SAF transport. It neither reconstructs geometry nor selects slicing presets. */
public final class SettingsTransferActivity extends Activity {
    public static final String EXTRA_PRESET_ID="preset_id";
    private static final int PICK_PROFILES=521,PICK_PROJECT=522,SAVE_DOCUMENT=523;
    private static final int INIT=0,IMPORT=1,PREPARE_JSON=2,PREPARE_ZIP=3,OPEN_PROJECT=4,IMPORT_EMBEDDED=5,PREPARE_PROJECT=6,COPY_OUT=7;
    private Session session;private GlassUi ui;private FrameLayout root;private TextView status,presetLabel,projectLabel,embeddedLabel,cancel;
    private TextView importProfiles,exportJson,exportZip,openProject,previewEmbedded,importEmbedded,saveProject;
    private ProgressBar progress;private EditText editor;private View editorCard;private Object backCallback;
    private boolean settingEditor;

    @Override public void onCreate(Bundle state){super.onCreate(state);Object retained=getLastNonConfigurationInstance();
        try{session=retained instanceof Session?(Session)retained:Session.restore(this,state,getIntent().getStringExtra(EXTRA_PRESET_ID));}
        catch(Exception e){session=new Session(new File(getCacheDir(),"settings-transfer-"+UUID.randomUUID()));session.status=getString(R.string.transfer_error,message(e));}
        render();if(android.os.Build.VERSION.SDK_INT>=33)backCallback=Back33.register(this);
        if(session.task!=null&&session.task.running){session.task.observer=new WeakReference<>(this);refresh();}
        else if(!(retained instanceof Session))start(INIT,null,null);
        else refresh();
    }
    private void render(){root=new FrameLayout(this);root.setBackground(GlassBackground.create(this));setContentView(root);getWindow().setStatusBarColor(Color.rgb(74,79,89));getWindow().setNavigationBarColor(Color.rgb(74,79,89));getWindow().getDecorView().setSystemUiVisibility(0);
        if(android.os.Build.VERSION.SDK_INT>=35)root.setOnApplyWindowInsetsListener((v,in)->{android.graphics.Insets safe=in.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());v.setPadding(safe.left,safe.top,safe.right,safe.bottom);return in;});
        boolean reduced=getSharedPreferences("app_preferences",0).getBoolean("reduce_transparency",false);ui=new GlassUi(this,root,reduced);LinearLayout column=ui.vertical();root.addView(column,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=ui.horizontal();ui.pad(header,20,16,20,8);ImageView back=new ImageView(this);back.setImageDrawable(GlassIcon.create(this,"back",ui.tokens.textPrimary));back.setBackground(GlassSurface.of(this,32,reduced));back.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));back.setContentDescription(getString(R.string.transfer_back));back.setFocusable(true);back.setOnClickListener(v->back());header.addView(back,new LinearLayout.LayoutParams(ui.dp(44),ui.dp(44)));TextView title=ui.heading(getString(R.string.transfer_title));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=ui.dp(12);header.addView(title,hp);column.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);LinearLayout content=ui.vertical();ui.pad(content,20,8,20,28);scroll.addView(content);column.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content.addView(ui.text(getString(R.string.transfer_intro),14,400,ui.tokens.textSecondary));ui.space(content,12);
        LinearLayout stateCard=ui.vertical();ui.pad(stateCard,16,12,16,12);LinearLayout stateRow=ui.horizontal();progress=new ProgressBar(this);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ui.tokens.textPrimary));stateRow.addView(progress,new LinearLayout.LayoutParams(ui.dp(28),ui.dp(28)));status=ui.text("",14,400,ui.tokens.textPrimary);status.setTextIsSelectable(true);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1);sp.leftMargin=ui.dp(12);stateRow.addView(status,sp);stateCard.addView(stateRow);cancel=ui.action(getString(R.string.transfer_cancel),false,this::cancelTask);stateCard.addView(cancel);content.addView(ui.surface(stateCard,24));ui.space(content,18);
        content.addView(ui.heading(getString(R.string.transfer_presets)));ui.space(content,10);presetLabel=ui.text("",14,500,ui.tokens.textPrimary);content.addView(presetLabel);ui.space(content,10);
        importProfiles=button(content,R.string.transfer_import_profiles,()->pick(PICK_PROFILES));exportJson=button(content,R.string.transfer_export_json,()->start(PREPARE_JSON,null,null));exportZip=button(content,R.string.transfer_export_zip,()->start(PREPARE_ZIP,null,null));content.addView(ui.text(getString(R.string.transfer_preset_note),12,400,ui.tokens.textSecondary));ui.space(content,24);
        content.addView(ui.heading(getString(R.string.transfer_project)));ui.space(content,10);openProject=button(content,R.string.transfer_open_project,()->pick(PICK_PROJECT));projectLabel=ui.text("",14,500,ui.tokens.textPrimary);content.addView(projectLabel);ui.space(content,10);content.addView(ui.text(getString(R.string.transfer_project_note),12,400,ui.tokens.textSecondary));ui.space(content,12);
        LinearLayout editorBody=ui.vertical();ui.pad(editorBody,16,12,16,12);editorBody.addView(ui.text(getString(R.string.transfer_json_label),12,500,ui.tokens.textSecondary));editor=ui.edit("",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,true);editor.setGravity(Gravity.TOP|Gravity.START);editor.setMinLines(10);editor.setMaxLines(20);editor.setVerticalScrollBarEnabled(true);editor.setContentDescription(getString(R.string.transfer_json_description));GlassTypography.apply(editor,400);editor.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int before,int count){if(!settingEditor)session.projectText=s.toString();}});retainEditorScroll(editor);editorBody.addView(editor,new LinearLayout.LayoutParams(-1,-2));editorCard=ui.surface(editorBody,24);content.addView(editorCard);ui.space(content,12);
        embeddedLabel=ui.text("",14,500,ui.tokens.textPrimary);content.addView(embeddedLabel);previewEmbedded=button(content,R.string.transfer_preview_embedded,()->GlassBottomSheet.info(this,root,reduced,getString(R.string.transfer_preview_embedded),session.embeddedPreview));importEmbedded=button(content,R.string.transfer_import_embedded,()->start(IMPORT_EMBEDDED,null,null));saveProject=button(content,R.string.transfer_save_project,()->start(PREPARE_PROJECT,null,editor.getText().toString()));refresh();
    }
    private TextView button(LinearLayout content,int label,Runnable action){TextView button=ui.action(getString(label),false,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=ui.dp(8);content.addView(button,p);return button;}
    // This listener only controls nested scrolling and always returns false. Stock EditText
    // continues to handle selection, click and accessibility; there is no custom click gesture.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private static void retainEditorScroll(EditText editor){editor.setOnTouchListener((v,event)->{if(event.getAction()==android.view.MotionEvent.ACTION_DOWN)v.getParent().requestDisallowInterceptTouchEvent(true);else if(event.getAction()==android.view.MotionEvent.ACTION_UP||event.getAction()==android.view.MotionEvent.ACTION_CANCEL)v.getParent().requestDisallowInterceptTouchEvent(false);return false;});}
    private boolean busy(){return session.task!=null&&session.task.running;}
    private void enable(View view,boolean enabled){view.setEnabled(enabled);view.setAlpha(enabled?1f:.5f);}
    private void refresh(){if(isDestroyed()||isFinishing())return;boolean busy=busy(),enabled=!busy&&!session.pickerOpen;status.setText(session.status==null?getString(R.string.transfer_ready):session.status);progress.setVisibility(busy?View.VISIBLE:View.GONE);cancel.setVisibility(busy?View.VISIBLE:View.GONE);enable(cancel,busy&&session.task.canCancel);presetLabel.setText(session.presetName==null?getString(R.string.transfer_no_preset):getString(R.string.transfer_selected_preset,session.presetName,session.presetKind));enable(importProfiles,enabled);enable(exportJson,enabled&&session.presetId!=null&&session.presetName!=null);enable(exportZip,enabled&&session.presetId!=null&&session.presetName!=null);enable(openProject,enabled);boolean project=session.projectFile!=null&&session.projectFile.isFile()&&session.projectText!=null;projectLabel.setText(project?getString(R.string.transfer_project_file,session.projectName,android.text.format.Formatter.formatShortFileSize(this,session.projectFile.length())):getString(R.string.transfer_project_empty));editorCard.setVisibility(project?View.VISIBLE:View.GONE);enable(editor,enabled&&project);if(project&&!editor.getText().toString().equals(session.projectText)){settingEditor=true;editor.setText(session.projectText);settingEditor=false;}embeddedLabel.setVisibility(project?View.VISIBLE:View.GONE);embeddedLabel.setText(getString(R.string.transfer_embedded_count,session.embedded.size()));previewEmbedded.setVisibility(project?View.VISIBLE:View.GONE);importEmbedded.setVisibility(project?View.VISIBLE:View.GONE);saveProject.setVisibility(project?View.VISIBLE:View.GONE);enable(previewEmbedded,enabled&&!session.embedded.isEmpty());enable(importEmbedded,enabled&&!session.embedded.isEmpty());enable(saveProject,enabled&&project);if(session.changed)setResult(RESULT_OK);
        if(!busy&&session.queuedUri!=null){Uri uri=session.queuedUri;int kind=session.queuedKind;session.queuedUri=null;start(kind,uri,null);return;}
        if(!busy&&!session.pickerOpen&&session.preparedNeedsPicker){session.preparedNeedsPicker=false;createDocument();}
    }
    private void pick(int code){if(busy()||session.pickerOpen)return;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{session.pickerOpen=true;startActivityForResult(intent,code);refresh();}catch(android.content.ActivityNotFoundException e){session.pickerOpen=false;session.status=getString(R.string.transfer_picker_unavailable);refresh();}}
    private void createDocument(){if(session.preparedFile==null||!session.preparedFile.isFile()){session.status=getString(R.string.transfer_missing_export);refresh();return;}Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(session.preparedMime).putExtra(Intent.EXTRA_TITLE,session.preparedName);try{session.pickerOpen=true;startActivityForResult(intent,SAVE_DOCUMENT);refresh();}catch(android.content.ActivityNotFoundException e){session.pickerOpen=false;session.status=getString(R.string.transfer_picker_unavailable);refresh();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=PICK_PROFILES&&request!=PICK_PROJECT&&request!=SAVE_DOCUMENT)return;session.pickerOpen=false;if(result!=RESULT_OK||data==null||data.getData()==null){session.status=getString(R.string.transfer_canceled);refresh();return;}Uri uri=data.getData();if(request==SAVE_DOCUMENT&&session.projectExport&&sameDocument(uri,session.sourceUri)){session.status=getString(R.string.transfer_new_destination);refresh();return;}int kind=request==PICK_PROFILES?IMPORT:request==PICK_PROJECT?OPEN_PROJECT:COPY_OUT;if(busy()){session.queuedKind=kind;session.queuedUri=uri;refresh();}else start(kind,uri,null);}
    private static boolean sameDocument(Uri destination,String source){if(source==null)return false;Uri original=Uri.parse(source);if(original.equals(destination))return true;try{return java.util.Objects.equals(original.getAuthority(),destination.getAuthority())&&android.provider.DocumentsContract.getDocumentId(original).equals(android.provider.DocumentsContract.getDocumentId(destination));}catch(Exception ignored){return false;}}
    private void start(int kind,Uri uri,String payload){if(busy()||(session.pickerOpen&&kind!=INIT))return;session.task=new TransferTask(getApplicationContext(),session,kind,uri,payload,this);session.task.start();refresh();}
    private void cancelTask(){if(!busy())return;TransferTask task=session.task;if(!task.requestCancel()){session.status=getString(R.string.transfer_commit_wait);refresh();return;}session.status=getString(R.string.transfer_canceling);refresh();}
    private void back(){if(busy()){cancelTask();return;}finish();}
    @android.annotation.SuppressLint("GestureBackNavigation") @Override public void onBackPressed(){back();}
    @android.annotation.TargetApi(33) private static final class Back33 {static Object register(SettingsTransferActivity a){android.window.OnBackInvokedCallback c=a::back;a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,c);return c;}static void unregister(SettingsTransferActivity a,Object c){a.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback)c);}}
    @Override public Object onRetainNonConfigurationInstance(){return session;}
    @Override protected void onSaveInstanceState(Bundle out){try{session.save(out);}catch(Exception e){session.status=getString(R.string.transfer_draft_error,message(e));}super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){if(android.os.Build.VERSION.SDK_INT>=33&&backCallback!=null)Back33.unregister(this,backCallback);if(session.task!=null&&session.task.observer.get()==this)session.task.observer.clear();if(isFinishing()){session.closed=true;if(busy())session.task.requestCancel();else new Thread(()->cleanup(session.directory),"settings-transfer-cleanup").start();}super.onDestroy();}
    private static String message(Throwable e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}

    private static final class Session {
        final File directory;volatile TransferTask task;volatile String status;
        volatile String presetId,presetName,presetKind,projectName,projectText,sourceUri,embeddedPreview="";
        volatile File projectFile,preparedFile;volatile List<JSONObject> embedded=Collections.emptyList();
        volatile boolean pickerOpen,preparedNeedsPicker,projectExport,closed,changed,wasInterrupted;
        volatile String preparedName,preparedMime;volatile Uri queuedUri;volatile int queuedKind;
        File restoredDraft;
        Session(File directory){this.directory=directory;if(!directory.isDirectory())directory.mkdirs();}
        static Session restore(Context c,Bundle state,String requested)throws IOException {
            String name=state==null?null:state.getString("transfer_session");if(name==null||!name.matches("settings-transfer-[a-fA-F0-9-]{36}"))name="settings-transfer-"+UUID.randomUUID();Session s=new Session(new File(c.getCacheDir(),name));if(!s.directory.isDirectory())throw new IOException("Cannot prepare private transfer cache");s.presetId=state==null?requested:state.getString("transfer_preset",requested);if(state!=null){s.projectFile=child(s.directory,state.getString("transfer_source"));s.restoredDraft=child(s.directory,state.getString("transfer_draft"));s.projectName=state.getString("transfer_project_name");s.sourceUri=state.getString("transfer_source_uri");s.preparedFile=child(s.directory,state.getString("transfer_export"));s.preparedMime=state.getString("transfer_mime");s.preparedName=state.getString("transfer_name");s.projectExport=state.getBoolean("transfer_project_export");s.pickerOpen=state.getBoolean("transfer_picker");s.preparedNeedsPicker=state.getBoolean("transfer_export_pending");s.wasInterrupted=state.getBoolean("transfer_busy");String queued=state.getString("transfer_queued_uri");int kind=state.getInt("transfer_queued_kind");if(queued!=null&&(kind==IMPORT||kind==OPEN_PROJECT||kind==COPY_OUT)){s.queuedUri=Uri.parse(queued);s.queuedKind=kind;}}return s;
        }
        synchronized void save(Bundle out)throws IOException {out.putString("transfer_session",directory.getName());out.putString("transfer_preset",presetId);out.putBoolean("transfer_picker",pickerOpen);out.putBoolean("transfer_busy",task!=null&&task.running);out.putString("transfer_source",projectFile==null?null:projectFile.getName());out.putString("transfer_project_name",projectName);out.putString("transfer_source_uri",sourceUri);out.putString("transfer_export",preparedFile==null?null:preparedFile.getName());out.putString("transfer_mime",preparedMime);out.putString("transfer_name",preparedName);out.putBoolean("transfer_project_export",projectExport);out.putBoolean("transfer_export_pending",preparedNeedsPicker);out.putString("transfer_queued_uri",queuedUri==null?null:queuedUri.toString());out.putInt("transfer_queued_kind",queuedKind);if(projectText!=null&&projectFile!=null){File draft=File.createTempFile("draft-",".json",directory);try(FileOutputStream stream=new FileOutputStream(draft)){stream.write(projectText.getBytes(StandardCharsets.UTF_8));stream.getFD().sync();}out.putString("transfer_draft",draft.getName());}}
        private static File child(File root,String name)throws IOException {if(name==null||name.isEmpty())return null;if(name.indexOf('/')>=0||name.indexOf('\\')>=0||name.equals(".")||name.equals(".."))throw new IOException("Invalid private transfer filename");File child=new File(root,name);if(!child.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("Invalid private transfer path");return child.isFile()?child:null;}
    }
    private static final class TransferTask extends Thread {
        final Context context;final Session session;final int kind;final Uri uri;final String payload;
        volatile WeakReference<SettingsTransferActivity> observer;volatile boolean running=true,canCancel=true,canceled;
        TransferTask(Context c,Session s,int kind,Uri uri,String payload,SettingsTransferActivity a){super("kepler-settings-transfer");context=c;session=s;this.kind=kind;this.uri=uri;this.payload=payload;observer=new WeakReference<>(a);}
        void check()throws IOException {if(canceled||Thread.currentThread().isInterrupted()||session.closed)throw new IOException(context.getString(R.string.transfer_canceled));}
        void update(int message){session.status=context.getString(message);notifyActivity();}
        void notifyActivity(){SettingsTransferActivity a=observer.get();if(a!=null)a.runOnUiThread(()->{if(!a.isDestroyed()&&!a.isFinishing())a.refresh();});}
        synchronized boolean requestCancel(){if(!canCancel)return false;canceled=true;interrupt();return true;}
        void commit()throws IOException {synchronized(this){check();canCancel=false;}update(R.string.transfer_committing);}
        @Override public void run(){try{check();switch(kind){case INIT:initialize();break;case IMPORT:importProfiles();break;case PREPARE_JSON:prepareJson();break;case PREPARE_ZIP:prepareZip();break;case OPEN_PROJECT:openProject();break;case IMPORT_EMBEDDED:importEmbedded();break;case PREPARE_PROJECT:prepareProject();break;case COPY_OUT:copyOut();break;default:throw new IOException("Unknown transfer operation");}if(canCancel)check();}
            catch(Throwable e){session.preparedNeedsPicker=false;session.status=context.getString(canceled?kind==COPY_OUT?R.string.transfer_canceled_output:R.string.transfer_canceled:R.string.transfer_error,message(e));}
            finally{running=false;notifyActivity();if(session.closed)cleanup(session.directory);}}
        private PresetRepository repo()throws IOException {return SettingsState.repository(context);}
        private void initialize()throws Exception {update(R.string.transfer_loading);if(session.presetId!=null)loadPreset(session.presetId);if(session.projectFile!=null){JSONObject json=ProjectConfigArchive.readProjectConfig(session.projectFile);List<JSONObject> embedded=ProjectConfigArchive.readEmbeddedPresets(session.projectFile);String text;if(session.restoredDraft!=null){try(InputStream in=new FileInputStream(session.restoredDraft)){text=readText(in);}}else text=json.toString(2);check();synchronized(session){session.projectText=text;session.embedded=embedded;session.embeddedPreview=new JSONArray(embedded).toString(2);}}session.status=context.getString(session.wasInterrupted?R.string.transfer_previous_interrupted:R.string.transfer_ready);session.wasInterrupted=false;}
        private void loadPreset(String id)throws IOException {PresetRepository.Preset preset=repo().load(id);session.presetId=id;session.presetName=preset.name;session.presetKind=preset.kind;}
        private void imported(List<PresetRepository.Preset> presets)throws IOException {if(!presets.isEmpty())loadPreset(presets.get(0).id);session.changed=true;session.status=context.getResources().getQuantityString(R.plurals.transfer_imported,presets.size(),presets.size());}
        private void importProfiles()throws Exception {update(R.string.transfer_importing);File input=File.createTempFile("import-",".data",session.directory);try{try(InputStream in=openInput();FileOutputStream out=new FileOutputStream(input)){copy(in,out);}check();boolean zip;try(InputStream in=new FileInputStream(input)){zip=in.read()=='P'&&in.read()=='K';}JSONObject json=null;if(!zip){try(InputStream in=new FileInputStream(input)){json=PresetJson.read(in);}}check();commit();imported(zip?repo().importOrcaZip(input):repo().importBundle(json));}finally{input.delete();}}
        private void prepareJson()throws Exception {update(R.string.transfer_preparing_export);if(session.presetId==null)throw new IOException(context.getString(R.string.transfer_no_preset));JSONObject json=repo().exportPreset(session.presetId);check();File output=File.createTempFile("preset-",".json",session.directory);try(FileOutputStream out=new FileOutputStream(output)){out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));}prepared(output,safeName(session.presetName,"preset")+".json","application/json",false);}
        private void prepareZip()throws Exception {update(R.string.transfer_preparing_export);if(session.presetId==null)throw new IOException(context.getString(R.string.transfer_no_preset));File output=File.createTempFile("presets-",".zip",session.directory);try(OutputStream out=new FileOutputStream(output)){repo().exportOrcaZip(Collections.singletonList(session.presetId),out);}check();prepared(output,safeName(session.presetName,"presets")+".zip","application/zip",false);}
        private void openProject()throws Exception {update(R.string.transfer_opening_project);File source=File.createTempFile("source-",".3mf",session.directory);boolean loaded=false;try{try(InputStream in=openInput();OutputStream out=new FileOutputStream(source)){copy(in,out);}check();JSONObject config=ProjectConfigArchive.readProjectConfig(source);List<JSONObject> embedded=ProjectConfigArchive.readEmbeddedPresets(source);String text=config.toString(2),preview=new JSONArray(embedded).toString(2);check();synchronized(session){session.projectFile=source;session.projectName=displayName(context,uri);session.sourceUri=uri.toString();session.projectText=text;session.embedded=embedded;session.embeddedPreview=preview;}loaded=true;session.status=context.getString(R.string.transfer_project_loaded);}finally{if(!loaded)source.delete();}}
        private void importEmbedded()throws Exception {update(R.string.transfer_importing_embedded);if(session.embedded.isEmpty())throw new IOException(context.getString(R.string.transfer_no_embedded));JSONArray records=new JSONArray();for(JSONObject source:session.embedded){String type=source.optString("type");String kind=type.equals("machine")?"printer":type;if(!kind.equals("process")&&!kind.equals("filament")&&!kind.equals("printer"))throw new IOException(context.getString(R.string.transfer_unknown_embedded));records.put(new JSONObject().put("kind",kind).put("vendor","").put("source",PresetJson.copy(source)));}JSONObject bundle=new JSONObject().put("format","kepler-presets-v1").put("presets",records);check();commit();imported(repo().importBundle(bundle));}
        private void prepareProject()throws Exception {update(R.string.transfer_preparing_project);if(session.projectFile==null)throw new IOException(context.getString(R.string.transfer_missing_project));JSONObject json=new JSONObject(payload);check();File result=new File(session.directory,"modified-"+UUID.randomUUID()+".3mf");boolean prepared=false;try{ProjectConfigArchive.replaceProjectConfig(session.projectFile,result,json);check();String base=safeName(session.projectName,"project");if(base.toLowerCase(Locale.ROOT).endsWith(".3mf"))base=base.substring(0,base.length()-4);prepared(result,base+"-settings.3mf","application/vnd.ms-3mfdocument",true);prepared=true;}finally{if(!prepared)result.delete();}}
        private void prepared(File file,String name,String mime,boolean project)throws IOException {check();File previous=session.preparedFile;synchronized(session){session.preparedFile=file;session.preparedName=name;session.preparedMime=mime;session.projectExport=project;session.preparedNeedsPicker=true;}if(previous!=null&&!previous.equals(file))previous.delete();session.status=context.getString(R.string.transfer_ready);}
        private void copyOut()throws IOException {update(R.string.transfer_copying);File source=session.preparedFile;if(source==null||!source.isFile())throw new IOException(context.getString(R.string.transfer_missing_export));if(session.projectExport&&sameDocument(uri,session.sourceUri))throw new IOException(context.getString(R.string.transfer_new_destination));try(InputStream in=new FileInputStream(source);OutputStream out=context.getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException(context.getString(R.string.transfer_destination_unavailable));copy(in,out);out.flush();}check();source.delete();session.preparedFile=null;session.status=context.getString(R.string.transfer_saved);}
        private InputStream openInput()throws IOException {InputStream in=context.getContentResolver().openInputStream(uri);if(in==null)throw new IOException(context.getString(R.string.transfer_source_unavailable));return in;}
        private void copy(InputStream in,OutputStream out)throws IOException {byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){check();out.write(buffer,0,n);}check();}
    }
    private static String readText(InputStream input)throws IOException {java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] bytes=new byte[8192];int n;while((n=input.read(bytes))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Operation canceled");out.write(bytes,0,n);}return out.toString("UTF-8");}
    private static String displayName(Context context,Uri uri){try(Cursor cursor=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst()){String name=cursor.getString(0);if(name!=null&&!name.isEmpty())return name;}}catch(Exception ignored){}return "project.3mf";}
    private static String safeName(String name,String fallback){String safe=name==null?fallback:name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_");if(safe.trim().isEmpty())safe=fallback;if(safe.length()>150){int end=150;if(Character.isHighSurrogate(safe.charAt(end-1)))end--;safe=safe.substring(0,end);}return safe;}
    private static void cleanup(File dir){File[] entries=dir.listFiles();if(entries!=null)for(File entry:entries){if(entry.isDirectory())cleanup(entry);else entry.delete();}dir.delete();}
}
