package com.orcamobile.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

/** Explicit, cancellable catalog lookup; never starts an upload or print. */
final class CloudSelectionHelper {
    enum Kind { OBICO_PRINTER, PRINTER_OS_PROJECT, PRINTER_OS_TYPE }
    interface Selection { void selected(String id,String name,boolean newProject); }
    private static final class Entry {final String id,name;Entry(String id,String name){this.id=id;this.name=name;}}
    static void choose(Activity activity,PrinterEndpoint endpoint,Kind kind,Selection selected) {
        if(activity==null||endpoint==null||kind==null||selected==null)throw new IllegalArgumentException();
        if(kind==Kind.PRINTER_OS_TYPE) {
            EditText search=new EditText(activity);search.setSingleLine(true);search.setHint("Например, название модели");
            MdUi.alert(activity).setTitle("Поиск типа принтера").setView(search).setPositiveButton("Найти",(d,w)->load(activity,endpoint,kind,search.getText().toString().trim(),selected)).setNegativeButton("Отмена",null).show();
        } else load(activity,endpoint,kind,"",selected);
    }
    private static void load(Activity activity,PrinterEndpoint endpoint,Kind kind,String query,Selection selected) {
        try {NetworkPolicy.requireOnline(activity);}catch(IOException e){error(activity,e);return;}
        NetworkHttp.Cancellation cancel=new NetworkHttp.Cancellation();
        AlertDialog waiting=MdUi.alert(activity).setTitle("Получаем список…").setMessage("Используется указанное подключение.").setNegativeButton("Отменить",(d,w)->cancel.cancel()).create();
        waiting.setOnCancelListener(d->cancel.cancel());waiting.show();
        new Thread(()->{try {
            ArrayList<Entry> entries=new ArrayList<>();
            if(kind==Kind.OBICO_PRINTER)collectArray(CloudPrinterHosts.listObicoPrinters(endpoint,cancel),"name",entries);
            else {JSONObject response=kind==Kind.PRINTER_OS_PROJECT?CloudPrinterHosts.listPrinterOsProjects(endpoint,cancel):CloudPrinterHosts.listPrinterOsTypes(endpoint,query,cancel);
                if(!response.optBoolean("result",false))throw new IOException("Сервис не подтвердил получение списка.");Object message=response.opt("message");String label=kind==Kind.PRINTER_OS_PROJECT?"name":"description";
                if(message instanceof JSONArray)collectArray((JSONArray)message,label,entries);
                else if(message instanceof JSONObject){JSONObject obj=(JSONObject)message;for(Iterator<String> it=obj.keys();it.hasNext();){Object v=obj.opt(it.next());if(v instanceof JSONObject)add((JSONObject)v,label,entries);}}
                else throw new IOException("Некорректный список сервиса.");}
            cancel.check();activity.runOnUiThread(()->{waiting.dismiss();if(activity.isDestroyed()||activity.isFinishing()||cancel.isCancelled())return;show(activity,kind,entries,selected);});
        }catch(Exception e){activity.runOnUiThread(()->{waiting.dismiss();if(!activity.isDestroyed()&&!activity.isFinishing()&&!cancel.isCancelled())error(activity,e);});}},"cloud-catalog").start();
    }
    private static void collectArray(JSONArray array,String label,ArrayList<Entry> out)throws IOException {
        for(int i=0;i<array.length();i++){JSONObject item=array.optJSONObject(i);if(item==null)throw new IOException("Некорректная запись сервиса.");add(item,label,out);}
    }
    private static void add(JSONObject item,String label,ArrayList<Entry> out)throws IOException {
        Object rawId=item.opt("id"),rawName=item.opt(label);
        if(!(rawId instanceof String)&&!(rawId instanceof Number) || !(rawName instanceof String))throw new IOException("Сервис вернул запись без имени или идентификатора.");
        String id=String.valueOf(rawId),name=((String)rawName).trim();
        if(id.isEmpty()||id.length()>1024||id.indexOf('\r')>=0||id.indexOf('\n')>=0||name.isEmpty())throw new IOException("Сервис вернул запись без имени или идентификатора.");
        if(out.size()>=10000)throw new IOException("Список слишком велик для выбора.");out.add(new Entry(id,name));
    }
    private static void show(Activity activity,Kind kind,ArrayList<Entry> entries,Selection selected) {
        boolean canCreate=kind==Kind.PRINTER_OS_PROJECT;
        if(entries.isEmpty()&&!canCreate){MdUi.alert(activity).setMessage("Подходящих записей не найдено.").setPositiveButton("Закрыть",null).show();return;}
        String[] labels=new String[entries.size()+(canCreate?1:0)];for(int i=0;i<entries.size();i++)labels[i]=entries.get(i).name+"  ["+entries.get(i).id+"]";
        if(canCreate)labels[entries.size()]="Создать проект…";
        String title=kind==Kind.OBICO_PRINTER?"Принтер Obico":kind==Kind.PRINTER_OS_PROJECT?"Проект 3DPrinterOS":"Тип принтера 3DPrinterOS";
        MdUi.alert(activity).setTitle(title).setItems(labels,(d,index)->{if(index<entries.size()){Entry e=entries.get(index);selected.selected(e.id,e.name,false);}else {
            EditText name=new EditText(activity);name.setSingleLine(true);name.setHint("Название проекта");
            AlertDialog dialog=MdUi.alert(activity).setTitle("Новый проект").setView(name).setPositiveButton("Выбрать",null).setNegativeButton("Отмена",null).create();
            dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String value=name.getText().toString().trim();if(value.isEmpty()){name.setError("Введите название.");return;}selected.selected("",value,true);dialog.dismiss();}));dialog.show();
        }}).setNegativeButton("Отмена",null).show();
    }
    private static void error(Activity activity,Exception e){if(!activity.isDestroyed()&&!activity.isFinishing())MdUi.alert(activity).setTitle("Не удалось получить список").setMessage(e.getMessage()==null?"Ошибка соединения.":e.getMessage()).setPositiveButton("Закрыть",null).show();}
}
