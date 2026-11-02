package com.orcamobile.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

/** Read-only Orca project contents and explicit plate selection. */
public final class ModelDetailsActivity extends Activity {
    private Object backCallback;
    private MdUi ui;private LinearLayout page;private CurrentModelStore current;private boolean busy=true;private String error="";private volatile boolean alive=true;private boolean selecting,closeAfterSelection;
    @Override public void onCreate(Bundle state){MdUi.prepare(this);super.onCreate(state);if(android.os.Build.VERSION.SDK_INT>=33)backCallback=Api33.register(this);render();load();}
    @android.annotation.SuppressLint("GestureBackNavigation") @Override public void onBackPressed(){requestClose();}
    @android.annotation.TargetApi(33) private static final class Api33 {static Object register(ModelDetailsActivity a){android.window.OnBackInvokedCallback cb=a::requestClose;a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,cb);return cb;}static void unregister(ModelDetailsActivity a,Object cb){a.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback)cb);}}
    private void requestClose(){if(selecting){closeAfterSelection=true;return;}finish();}
    @Override protected void onDestroy(){alive=false;if(android.os.Build.VERSION.SDK_INT>=33&&backCallback!=null)Api33.unregister(this,backCallback);super.onDestroy();}
    private void load(){new Thread(()->{try{CurrentModelStore data=CurrentModelStore.load(getApplicationContext());runOnUiThread(()->{if(!alive)return;current=data;busy=false;error=data==null?"Откройте модель на столе.":"";render();});}catch(Exception e){fail(e);}},"model-details").start();}
    private void fail(Exception e){runOnUiThread(()->{if(!alive)return;busy=false;selecting=false;error=e.getMessage()==null?"Не удалось прочитать проект.":e.getMessage();if(closeAfterSelection){finish();return;}render();});}
    private void selectPlate(int index){if(busy||current==null)return;String expected=current.file.getName();busy=true;selecting=true;error="";render();new Thread(()->{try{CurrentModelStore result=CurrentModelStore.selectPlate(getApplicationContext(),expected,index);runOnUiThread(()->{if(!alive)return;current=result;busy=false;selecting=false;setResult(RESULT_OK);if(closeAfterSelection){finish();return;}render();});}catch(Exception e){fail(e);}},"select-project-plate").start();}
    private void section(String label){TextView t=ui.text(label,17,true);page.addView(t);ui.gap(page,10);}
    private void row(String title,String subtitle,Runnable action){page.addView(ui.row(title,subtitle,null,action));ui.gap(page,8);}
    private static int count(JSONArray array){return array==null?0:array.length();}
    private static int count(JSONObject object){return object==null?0:object.length();}
    private static String title(JSONObject value,String key,String fallback){String text=value==null?"":value.optString(key,"").trim();return text.isEmpty()?fallback:text;}
    private void render(){if(!alive||isFinishing())return;ui=new MdUi(this);page=ui.page(this,"Сведения о модели",this::requestClose);
        if(busy){row("Загрузка","Читаем данные модели…",null);return;}
        if(!error.isEmpty())row("Не удалось открыть",error,null);if(current==null)return;
        JSONObject metadata=current.metadata;String format=metadata==null?"stl":metadata.optString("format","");String label=format.toUpperCase(Locale.ROOT);
        section("Файл");row(current.name,label+" · "+current.mesh.triangleCount+" треугольников",null);
        if(format.equals("obj"))row("Материалы OBJ","Отдельные файлы MTL и текстуры не импортируются. Доступна геометрия модели.",null);
        if(format.equals("3mf"))renderProject(metadata);else if(metadata!=null)renderObjects(metadata.optJSONArray("objects"));
    }
    private void renderProject(JSONObject metadata){JSONObject project=metadata.optJSONObject("project_data");if(project==null){row("Данные проекта","Движок не вернул состав проекта.",null);return;}
        row("Исходный проект сохранён","Оригинальный файл 3MF сохранён целиком. Описание ниже доступно для просмотра.",null);
        row("Расположение и нарезка","При загруженном столе предпросмотр использует исходные координаты проекта. Совместимость со столом проверяется при нарезке; изменение положения проекта пока недоступно.",null);
        if(metadata.optBoolean("mapping_unresolved",false))row("Пластина не определена","Показаны все объекты проекта: не удалось определить их соответствие выбранной пластине. Нарезка недоступна.",null);
        if(metadata.optInt("non_printable_instances",0)>0)row("Непечатаемые экземпляры","Они видны в предпросмотре, но не попадут в нарезку.",null);
        if(metadata.optLong("preview_triangle_count",0)==0)row("Пластина пуста","На выбранной пластине нет геометрии для нарезки.",null);
        else if(metadata.has("printable_instances")&&metadata.optInt("printable_instances",0)==0)row("Нет объектов для печати","В проекте есть геометрия, но все экземпляры помечены непечатаемыми.",null);
        if(project.optBoolean("unknown_unit_requires_validation",false)||project.optBoolean("non_mm_transform_requires_validation",false))row("Единицы и преобразования","Единицы или преобразования проекта ещё не проверены. Геометрия предпросмотра может быть неточной; нарезка недоступна.",null);
        JSONArray plates=project.optJSONArray("plates");int selected=metadata.optInt("selected_plate",-1);ArrayList<String> blocked=new ArrayList<>();
        if(metadata.optBoolean("mapping_unresolved",false))blocked.add("не определено соответствие объектов пластине");if(project.optBoolean("unknown_unit_requires_validation",false)||project.optBoolean("non_mm_transform_requires_validation",false))blocked.add("непроверенные единицы или преобразования модели");if(count(plates)>1)blocked.add("несколько пластин");if(count(project.optJSONObject("project_config"))>0||count(project.optJSONArray("embedded_presets"))>0)blocked.add("встроенные профили проекта");
        if(count(project.optJSONArray("unrecognized_config_keys"))>0||project.optInt("substitutions_count",0)>0)blocked.add("неизвестные или заменённые параметры");
        if(plates!=null)for(int i=0;i<plates.length();i++){JSONObject p=plates.optJSONObject(i);if(p!=null&&count(p.optJSONObject("config"))>0){blocked.add("настройки пластины");break;}}
        JSONArray custom=project.optJSONArray("plate_custom_gcode");if(custom!=null)for(int i=0;i<custom.length();i++){JSONObject g=custom.optJSONObject(i);if(g!=null&&count(g.optJSONArray("items"))>0){blocked.add("пользовательский G-code пластин");break;}}
        if(!blocked.isEmpty())row("Нарезка пока недоступна","Проект содержит: "+android.text.TextUtils.join(", ",blocked)+". Исходный файл сохранён.",null);
        section("Пластины");if(count(plates)==0)row("Нет отдельных пластин","Показана геометрия проекта целиком.",null);
        else for(int i=0;i<plates.length();i++){JSONObject p=plates.optJSONObject(i);if(p==null||!p.has("index"))continue;int index=p.optInt("index",-1);if(index<0)continue;String name=title(p,"name","Пластина "+(index+1));String description=(index==selected?"Выбрана · ":"")+"экземпляров: "+count(p.optJSONArray("object_instances"));if(p.optBoolean("locked",false))description+=" · заблокирована в проекте";final int target=index;row(name,description,index==selected?null:()->selectPlate(target));}
        ui.gap(page,8);section("Настройки проекта");row("Встроенные профили",count(project.optJSONArray("embedded_presets"))+" · параметры проекта: "+count(project.optJSONObject("project_config")),null);
        renderObjects(project.optJSONArray("objects"));
    }
    private void renderObjects(JSONArray objects){ui.gap(page,8);section("Объекты и экземпляры");if(count(objects)==0){row("Нет сведений об объектах","Состав модели недоступен.",null);return;}
        for(int i=0;i<objects.length();i++){JSONObject object=objects.optJSONObject(i);if(object==null)continue;JSONArray instances=object.optJSONArray("instances"),volumes=object.optJSONArray("volumes");String name=title(object,"name","Объект "+(i+1));row(name,"Экземпляров: "+count(instances)+" · частей: "+count(volumes)+(object.optBoolean("printable",true)?"":" · не для печати"),null);
            if(instances!=null)for(int n=0;n<instances.length();n++){JSONObject instance=instances.optJSONObject(n);if(instance==null)continue;String subtitle=instance.optBoolean("printable",true)?"Для печати":"Не для печати";JSONArray matrix=instance.optJSONArray("matrix");final String details=matrix==null?"Преобразование недоступно.":matrix.toString();row("   Экземпляр "+(n+1),subtitle,()->MdUi.alert(this).setTitle("Преобразование экземпляра").setMessage(details).setPositiveButton("Закрыть",null).show());}
            if(volumes!=null)for(int n=0;n<volumes.length();n++){JSONObject volume=volumes.optJSONObject(n);if(volume==null)continue;String vname=title(volume,"name","Часть "+(n+1));row("   "+vname,"Тип: "+volume.optString("type","—")+" · треугольников: "+volume.optLong("triangles",0),null);}
        }
    }
}
