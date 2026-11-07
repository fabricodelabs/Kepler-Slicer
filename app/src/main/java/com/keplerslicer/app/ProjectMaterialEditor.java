package com.keplerslicer.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.keplerslicer.engine.ExperimentalSlicer;
import com.keplerslicer.app.glass.GlassBottomSheet;
import com.keplerslicer.app.glass.GlassUi;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native project-slot colours and real flushing recalculation; original 3MF remains immutable. */
final class ProjectMaterialEditor {
    private final Activity activity;
    private final Context app;
    private final String requestedFile;
    private final Runnable changed;
    private boolean busy;

    private ProjectMaterialEditor(Activity owner, CurrentModelStore current, Runnable afterChange) {
        activity=owner;app=owner.getApplicationContext();requestedFile=current.file.getName();changed=afterChange;
    }
    static void open(Activity activity, CurrentModelStore current, Runnable changed) {
        if(current==null)return;
        new ProjectMaterialEditor(activity,current,changed).load();
    }
    private boolean active(){return !activity.isFinishing()&&!activity.isDestroyed();}
    private void load(){
        if(busy)return;busy=true;Toast.makeText(activity,R.string.pref_working,Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{ReadResult result=read(-1,"",null);activity.runOnUiThread(()->{busy=false;if(active())showSlots(result);});}
            catch(Exception|LinkageError e){failed(e);}
        },"project-materials-read").start();
    }
    private void showSlots(ReadResult result){
        try{
            JSONArray slots=result.document.getJSONArray("slots");String[] labels=new String[slots.length()];
            for(int i=0;i<slots.length();i++){
                JSONObject slot=slots.getJSONObject(i);
                labels[i]=activity.getString(R.string.native_material_slot,i+1,slot.getString("type"),slot.getString("color"));
                if(slot.optBoolean("virtual_mixed",false))labels[i]+=" · "+activity.getString(R.string.native_material_virtual);
            }
            GlassBottomSheet.choose(activity,activity.getWindow().getDecorView(),false,activity.getString(R.string.native_materials_title),labels,-1,index->{
                try{JSONObject slot=slots.getJSONObject(index);if(slot.optBoolean("virtual_mixed",false)){info(activity.getString(R.string.native_material_virtual_detail));return;}editColour(result,index,slot.getString("color"));}
                catch(Exception e){failed(e);}
            });
        }catch(Exception e){failed(e);}
    }
    private void editColour(ReadResult expected,int slot,String current){
        com.keplerslicer.app.glass.SheetDialog dialog=new com.keplerslicer.app.glass.SheetDialog(activity);
        GlassUi ui=new GlassUi(activity,activity.getWindow().getDecorView(),false);LinearLayout body=ui.vertical();ui.pad(body,20,20,20,20);
        body.addView(ui.heading(activity.getString(R.string.native_material_colour_title,slot+1)));ui.space(body,10);
        body.addView(ui.text(activity.getString(R.string.native_material_colour_detail),14,400,ui.tokens.textPrimary));ui.space(body,10);
        EditText input=ui.edit(current,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS,false);input.setSingleLine(true);input.setContentDescription(activity.getString(R.string.native_material_colour_title,slot+1));body.addView(input);ui.space(body,14);
        body.addView(ui.text(activity.getString(R.string.native_material_monochrome_presets),12,400,ui.tokens.textSecondary));ui.space(body,6);
        String[] colours={"#000000","#404040","#808080","#C0C0C0","#FFFFFF"};
        int[] names={R.string.native_material_black,R.string.native_material_dark_grey,R.string.native_material_grey,R.string.native_material_light_grey,R.string.native_material_white};
        FrameLayout[] swatches=new FrameLayout[colours.length];LinearLayout palette=ui.horizontal();
        for(int i=0;i<colours.length;i++){
            final String value=colours[i];FrameLayout target=new FrameLayout(activity);target.setMinimumWidth(ui.dp(44));target.setMinimumHeight(ui.dp(44));target.setFocusable(true);target.setClickable(true);
            target.setContentDescription(activity.getString(R.string.native_material_colour_preset,activity.getString(names[i]),value));
            android.view.View colour=new android.view.View(activity);GradientDrawable fill=new GradientDrawable();fill.setShape(GradientDrawable.OVAL);fill.setColor(Color.parseColor(value));fill.setStroke(ui.dp(1),0xff171717);colour.setBackground(fill);colour.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            target.addView(colour,new FrameLayout.LayoutParams(ui.dp(32),ui.dp(32),Gravity.CENTER));
            target.setOnClickListener(v->{input.setText(value);input.setSelection(value.length());input.setError(null);input.clearFocus();android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(input.getWindowToken(),0);});
            LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48));if(i>0)position.leftMargin=ui.dp(8);palette.addView(target,position);swatches[i]=target;
        }
        HorizontalScrollView presetScroll=new HorizontalScrollView(activity);presetScroll.setHorizontalScrollBarEnabled(false);presetScroll.addView(palette);body.addView(presetScroll,new LinearLayout.LayoutParams(-1,ui.dp(48)));ui.space(body,14);
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence value,int start,int count,int after){}public void onTextChanged(CharSequence value,int start,int before,int count){updateSwatches(ui,swatches,colours,value.toString());}public void afterTextChanged(Editable value){}});
        updateSwatches(ui,swatches,colours,current);
        body.addView(ui.action(activity.getString(R.string.pref_save),true,()->{
            String colour=input.getText().toString().trim().toUpperCase(Locale.ROOT);
            if(!colour.matches("#[0-9A-F]{6}(?:[0-9A-F]{2})?")){input.setError(activity.getString(R.string.native_material_colour_invalid));return;}
            if(colour.equalsIgnoreCase(current.trim())){dialog.dismiss();return;}
            dialog.dismiss();save(expected,slot,colour);
        }));ui.space(body,6);body.addView(ui.action(activity.getString(R.string.pref_cancel),false,dialog::dismiss));
        dialog.setContentView(ui.surface(body,ui.tokens.cardRadius));dialog.show();
    }
    private static void updateSwatches(GlassUi ui,FrameLayout[] swatches,String[] colours,String value){
        for(int i=0;i<swatches.length;i++){boolean selected=colours[i].equalsIgnoreCase(value.trim());swatches[i].setSelected(selected);GradientDrawable outline=new GradientDrawable();outline.setColor(selected?0x22ffffff:Color.TRANSPARENT);outline.setCornerRadius(ui.dp(12));outline.setStroke(ui.dp(selected?2:1),selected?0xffffffff:0x44ffffff);swatches[i].setBackground(outline);}
    }
    private void save(ReadResult expected,int slot,String colour){
        if(busy)return;busy=true;Toast.makeText(activity,R.string.pref_working,Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                ReadResult result=read(slot,colour,expected);
                activity.runOnUiThread(()->{busy=false;if(!active())return;if(changed!=null)changed.run();info(activity.getString(result.recalculated?R.string.native_material_saved_recalculated:R.string.native_material_saved_manual));});
            }catch(Exception|LinkageError e){failed(e);}
        },"project-materials-save").start();
    }
    private ReadResult read(int changedSlot,String colour,ReadResult expected)throws Exception {
        if(!active()||Thread.currentThread().isInterrupted())throw new InterruptedIOException(app.getString(R.string.native_actions_canceled));
        EngineProfileSession session=EngineProfileSession.capture(app);
        File staging=Files.createTempDirectory(app.getCacheDir().toPath(),"project-materials-").toFile();
        try{
            CurrentModelStore.Snapshot source=CurrentModelStore.snapshotToDirectory(app,staging);
            if(!source.selectionKey.startsWith(requestedFile+"\n")||!source.file.getName().endsWith(".3mf")||(source.projectMode!=1&&source.projectMode!=2))throw new IOException(app.getString(R.string.native_material_full_project_required));
            String profileKey=session.id.fingerprint+"\n"+session.selection.overrides;
            if(expected!=null&&(!expected.selectionKey.equals(source.selectionKey)||!expected.profileKey.equals(profileKey)))throw new IOException(app.getString(R.string.native_material_changed));
            boolean autoFlush=app.getSharedPreferences("app_preferences",0).getBoolean("auto_calculate_flushing_volumes",true);
            boolean recalculate=changedSlot>=0&&autoFlush;
            ProcessOverridesStore.Identity id=session.id;
            byte[] response=ExperimentalSlicer.projectMaterialSettings(id.printer.vendor,id.process.vendor,id.filament.vendor,
                id.printerName,id.processName,id.filamentName,session.resources.getAbsolutePath(),session.data.getAbsolutePath(),
                session.selection.overrides,source.file.getAbsolutePath(),source.plateIndex,source.projectMode,source.materialEdits,changedSlot,colour,recalculate);
            if(response==null||response.length==0)throw new IOException(app.getString(R.string.native_actions_invalid_result));
            JSONObject document=new JSONObject(new String(response,StandardCharsets.UTF_8));
            if(document.getInt("version")!=1||document.getJSONArray("slots").length()==0)throw new IOException(app.getString(R.string.native_actions_invalid_result));
            if(!source.selectionKey.equals(CurrentModelStore.selectionKey(app))||!session.stillCurrent(app))throw new IOException(app.getString(R.string.native_material_changed));
            if(changedSlot>=0){
                String edits=document.getJSONObject("edits").toString();
                ProcessOverridesStore.changeSelection(app,false,()->{
                    if(!active()||Thread.currentThread().isInterrupted())throw new InterruptedIOException(app.getString(R.string.native_actions_canceled));
                    if(!session.stillCurrent(app)||autoFlush!=app.getSharedPreferences("app_preferences",0).getBoolean("auto_calculate_flushing_volumes",true))throw new IOException(app.getString(R.string.native_material_changed));
                    CurrentModelStore.saveMaterialEdits(app,source.selectionKey,edits);
                });
            }
            return new ReadResult(document,source.selectionKey,profileKey,recalculate);
        }finally{deleteStaging(staging);}
    }
    private void failed(Throwable failure){activity.runOnUiThread(()->{busy=false;if(active())info(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());});}
    private void info(String text){GlassBottomSheet.info(activity,activity.getWindow().getDecorView(),false,activity.getString(R.string.native_materials_title),text);}
    private static void deleteStaging(File file){if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)deleteStaging(child);}file.delete();}
    private static final class ReadResult{
        final JSONObject document;final String selectionKey,profileKey;final boolean recalculated;
        ReadResult(JSONObject value,String modelKey,String profiles,boolean didRecalculate){document=value;selectionKey=modelKey;profileKey=profiles;recalculated=didRecalculate;}
    }
}
