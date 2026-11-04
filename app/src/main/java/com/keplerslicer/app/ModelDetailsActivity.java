package com.keplerslicer.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;
import com.keplerslicer.app.glass.GlassBottomSheet;

/** Read-only 3MF project contents and explicit plate selection. */
public final class ModelDetailsActivity extends Activity {
    private Object backCallback;
    private MdUi ui;private LinearLayout page;private CurrentModelStore current;private boolean busy=true;private String error="";private volatile boolean alive=true;private boolean selecting,closeAfterSelection;
    private Thread cloneWorker;
    @Override public void onCreate(Bundle state){MdUi.prepare(this);super.onCreate(state);if(android.os.Build.VERSION.SDK_INT>=33)backCallback=Api33.register(this);render();load();}
    @android.annotation.SuppressLint("GestureBackNavigation") @Override public void onBackPressed(){requestClose();}
    @android.annotation.TargetApi(33) private static final class Api33 {static Object register(ModelDetailsActivity a){android.window.OnBackInvokedCallback cb=a::requestClose;a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,cb);return cb;}static void unregister(ModelDetailsActivity a,Object cb){a.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback)cb);}}
    private void requestClose(){if(selecting){closeAfterSelection=true;return;}finish();}
    @Override protected void onDestroy(){alive=false;if(cloneWorker!=null)cloneWorker.interrupt();if(android.os.Build.VERSION.SDK_INT>=33&&backCallback!=null)Api33.unregister(this,backCallback);super.onDestroy();}
    private void load(){new Thread(()->{try{CurrentModelStore data=CurrentModelStore.load(getApplicationContext());runOnUiThread(()->{if(!alive)return;current=data;busy=false;error=data==null?getString(R.string.l10n_md_open_model):"";render();});}catch(Exception e){fail(e);}},"model-details").start();}
    private void fail(Exception e){runOnUiThread(()->{if(!alive)return;busy=false;selecting=false;error=e.getMessage()==null?getString(R.string.l10n_md_read_failed):e.getMessage();if(closeAfterSelection){finish();return;}render();});}
    private void selectPlate(int index){if(busy||current==null)return;String expected=current.file.getName();busy=true;selecting=true;error="";render();new Thread(()->{try{CurrentModelStore result=CurrentModelStore.selectPlate(getApplicationContext(),expected,index);runOnUiThread(()->{if(!alive)return;current=result;busy=false;selecting=false;setResult(RESULT_OK);if(closeAfterSelection){finish();return;}render();});}catch(Exception e){fail(e);}},"select-project-plate").start();}
    private void chooseCloneCount(){
        if(busy||current==null)return;
        GlassBottomSheet.choose(this,page,false,getString(R.string.native_preferences_clone),new String[]{getString(R.string.native_preferences_one_copy),getString(R.string.native_preferences_two_copies),getString(R.string.native_preferences_three_copies)},-1,index->cloneModel(index+1));
    }
    private void cloneModel(int copies){
        if(busy||current==null)return;busy=true;selecting=true;error="";render();
        boolean arrange=getSharedPreferences("app_preferences",MODE_PRIVATE).getBoolean("arrange_on_clone",true);
        cloneWorker=new Thread(()->{
            try{
                NativePreferenceActions.clonePlainModel(getApplicationContext(),copies,arrange);
                CurrentModelStore result=CurrentModelStore.load(getApplicationContext());
                runOnUiThread(()->{if(!alive)return;current=result;busy=false;selecting=false;setResult(RESULT_OK);ControlPreferences.scheduleAutoSlice(this);if(closeAfterSelection){finish();return;}render();});
            }catch(Exception e){fail(e);}catch(LinkageError e){fail(new java.io.IOException(getString(R.string.native_actions_engine_unavailable),e));}
        },"native-clone-model");cloneWorker.start();
    }
    private void section(String label){TextView t=ui.text(label,17,true);page.addView(t);ui.gap(page,10);}
    private void row(String title,String subtitle,Runnable action){page.addView(ui.row(title,subtitle,null,action));ui.gap(page,8);}
    private static int count(JSONArray array){return array==null?0:array.length();}
    private static int count(JSONObject object){return object==null?0:object.length();}
    private static String title(JSONObject value,String key,String fallback){String text=value==null?"":value.optString(key,"").trim();return text.isEmpty()?fallback:text;}
    private void render(){if(!alive||isFinishing())return;ui=new MdUi(this);page=ui.page(this,getString(R.string.l10n_md_title),this::requestClose);
        if(busy){row(getString(R.string.l10n_loading),getString(R.string.l10n_md_reading),null);return;}
        if(!error.isEmpty())row(getString(R.string.l10n_md_open_failed),error,null);if(current==null)return;
        JSONObject metadata=current.metadata;String format=metadata==null?"stl":metadata.optString("format","");String label=format.toUpperCase(Locale.ROOT);
        section(getString(R.string.l10n_md_file));row(current.name,label+" · "+current.mesh.triangleCount+getString(R.string.l10n_md_triangles),null);
        if(format.equals("stl")||format.equals("obj"))row(getString(R.string.native_preferences_clone),getString(R.string.native_preferences_clone_detail),this::chooseCloneCount);
        if(format.equals("3mf")&&(current.projectMode==1||current.projectMode==2))row(getString(R.string.native_materials_title),getString(R.string.native_materials_detail),()->ProjectMaterialEditor.open(this,current,()->{setResult(RESULT_OK);ControlPreferences.scheduleAutoSlice(this);busy=true;error="";render();load();}));
        if(format.equals("obj"))row(getString(R.string.l10n_ws_obj_materials),getString(R.string.l10n_md_mtl_note),null);
        if(format.equals("3mf"))renderProject(metadata);else if(metadata!=null)renderObjects(metadata.optJSONArray("objects"));
    }
    private void renderProject(JSONObject metadata){JSONObject project=metadata.optJSONObject("project_data");if(project==null){row(getString(R.string.l10n_md_project_data),getString(R.string.l10n_md_no_contents),null);return;}
        row(getString(R.string.l10n_md_project_kept),getString(R.string.l10n_md_3mf_kept),null);
        row(getString(R.string.l10n_md_placement),getString(R.string.l10n_md_coords_note),null);
        if(metadata.optBoolean("mapping_unresolved",false))row(getString(R.string.l10n_ws_plate_unknown),getString(R.string.l10n_md_all_objects),null);
        if(metadata.optInt("non_printable_instances",0)>0)row(getString(R.string.l10n_md_unprintable_instances),getString(R.string.l10n_md_unprintable_note),null);
        if(metadata.optLong("preview_triangle_count",0)==0)row(getString(R.string.l10n_ws_plate_empty),getString(R.string.l10n_md_plate_no_geometry),null);
        else if(metadata.has("printable_instances")&&metadata.optInt("printable_instances",0)==0)row(getString(R.string.l10n_ws_no_objects),getString(R.string.l10n_md_all_unprintable),null);
        if(project.optBoolean("unknown_unit_requires_validation",false)||project.optBoolean("non_mm_transform_requires_validation",false))row(getString(R.string.l10n_md_units),getString(R.string.l10n_md_units_unchecked),null);
        JSONArray plates=project.optJSONArray("plates");int selected=metadata.optInt("selected_plate",-1);ArrayList<String> blocked=new ArrayList<>();
        if(metadata.optBoolean("mapping_unresolved",false))blocked.add(getString(R.string.l10n_md_reason_mapping));if(project.optBoolean("unknown_unit_requires_validation",false)||project.optBoolean("non_mm_transform_requires_validation",false))blocked.add(getString(R.string.l10n_md_reason_units));if(count(plates)>1)blocked.add(getString(R.string.l10n_md_reason_plates));if(selected>0)blocked.add(getString(R.string.l10n_md_reason_origin));
        if(current.projectMode<0&&GeneralProjectPolicy.inspect(metadata).settingsPresent)blocked.add(getString(R.string.native_project_legacy_mode));
        if(current.projectMode>0){JSONObject snapshot=metadata.optJSONObject("project_settings_snapshot"),validation=snapshot==null?null:snapshot.optJSONObject("validation"),mode=validation==null?null:validation.optJSONObject(current.projectMode==2?"embedded_printer":"selected_printer");if(mode==null||!mode.optBoolean("can_load",false))blocked.add(mode==null?getString(R.string.native_project_snapshot_unavailable):mode.optString("error",getString(R.string.native_project_snapshot_unavailable)));}
        if(current.projectMode==0)row(getString(R.string.native_project_mode_geometry),getString(R.string.native_project_loaded_geometry),null);
        else if(current.projectMode>0)row(getString(current.projectMode==2?R.string.native_project_mode_embedded:R.string.native_project_mode_current),getString(R.string.native_project_loaded_full),null);
        if(!blocked.isEmpty())row(getString(R.string.l10n_md_slicing_unavailable),getString(R.string.l10n_md_contains)+android.text.TextUtils.join(", ",blocked)+getString(R.string.l10n_md_source_kept),null);
        section(getString(R.string.l10n_md_plates));if(count(plates)==0)row(getString(R.string.l10n_md_no_plates),getString(R.string.l10n_md_whole_project),null);
        else for(int i=0;i<plates.length();i++){JSONObject p=plates.optJSONObject(i);if(p==null||!p.has("index"))continue;int index=p.optInt("index",-1);if(index<0)continue;String name=title(p,"name",getString(R.string.l10n_md_plate)+(index+1));String description=(index==selected?getString(R.string.l10n_md_selected):"")+getString(R.string.l10n_md_instances_lower)+count(p.optJSONArray("object_instances"));if(p.optBoolean("locked",false))description+=getString(R.string.l10n_md_locked);final int target=index;row(name,description,index==selected?null:()->selectPlate(target));}
        ui.gap(page,8);section(getString(R.string.l10n_md_project_settings));row(getString(R.string.l10n_md_embedded_profiles),count(project.optJSONArray("embedded_presets"))+getString(R.string.l10n_md_project_params)+count(project.optJSONObject("project_config")),null);
        renderObjects(project.optJSONArray("objects"));
    }
    private void renderObjects(JSONArray objects){ui.gap(page,8);section(getString(R.string.l10n_md_objects_instances));if(count(objects)==0){row(getString(R.string.l10n_md_no_objects_info),getString(R.string.l10n_md_contents_unavailable),null);return;}
        for(int i=0;i<objects.length();i++){JSONObject object=objects.optJSONObject(i);if(object==null)continue;JSONArray instances=object.optJSONArray("instances"),volumes=object.optJSONArray("volumes");String name=title(object,"name",getString(R.string.l10n_md_object)+(i+1));row(name,getString(R.string.l10n_md_instances)+count(instances)+getString(R.string.l10n_md_parts_suffix)+count(volumes)+(object.optBoolean("printable",true)?"":getString(R.string.l10n_md_not_printable_suffix)),null);
            if(instances!=null)for(int n=0;n<instances.length();n++){JSONObject instance=instances.optJSONObject(n);if(instance==null)continue;String subtitle=instance.optBoolean("printable",true)?getString(R.string.l10n_md_printable):getString(R.string.l10n_md_not_printable);JSONArray matrix=instance.optJSONArray("matrix");final String details=matrix==null?getString(R.string.l10n_md_transform_unavailable):matrix.toString();row(getString(R.string.l10n_md_instance_indent)+(n+1),subtitle,()->MdUi.alert(this).setTitle(getString(R.string.l10n_md_instance_transform)).setMessage(details).setPositiveButton(getString(R.string.l10n_close),null).show());}
            if(volumes!=null)for(int n=0;n<volumes.length();n++){JSONObject volume=volumes.optJSONObject(n);if(volume==null)continue;String vname=title(volume,"name",getString(R.string.l10n_md_part)+(n+1));row("   "+vname,getString(R.string.l10n_md_type)+volume.optString("type","—")+getString(R.string.l10n_md_triangles_suffix)+volume.optLong("triangles",0),null);}
        }
    }
}
