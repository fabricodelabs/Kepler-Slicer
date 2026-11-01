package com.orcamobile.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit Orca Cloud sign-in and user-controlled profile synchronization. */
public final class CloudSyncActivity extends Activity {
    private static final String TOKEN_KEY="orca_cloud_token";
    private static final int EXPORT=821;
    private static final Object SESSION_LOCK=new Object();
    private MdUi ui;private LinearLayout page;private volatile OrcaCloudOAuth login;private volatile boolean busy;private String notice="";private String exportData;
    @Override public void onCreate(Bundle state){MdUi.prepare(this);super.onCreate(state);render();}
    @Override protected void onDestroy(){OrcaCloudOAuth active=login;if(active!=null)try{active.close();}catch(IOException ignored){}super.onDestroy();}
    private boolean networkAllowed(){try{NetworkPolicy.requireOnline(this);return true;}catch(IOException e){notice=e.getMessage();render();return false;}}
    private void row(String title,String description,Runnable action){page.addView(ui.row(title,description,null,action));ui.gap(page,10);}
    private JSONObject session() throws IOException {try {String json=NetworkSecrets.get(this,TOKEN_KEY);return json.isEmpty()?null:new JSONObject(json);}catch(JSONException e){throw new IOException("Сессия повреждена.",e);}}
    private void render(){if(isFinishing()||isDestroyed())return;ui=new MdUi(this);page=ui.page(this,"Orca Cloud",this::finish);ui.navigation(this,3);
        if(!notice.isEmpty()){TextView t=ui.text(notice,13,false);t.setTextColor(ui.muted);page.addView(t);ui.gap(page,16);}
        row("О синхронизации","Профили хранятся отдельно от встроенных профилей Kepler. Применение к нарезке пока недоступно. Вход в Orca Cloud и совместимость провайдеров на реальном аккаунте ещё не проверены.",null);
        try {JSONObject current=session();if(current==null){row("Войти","Официальный вход через браузер; пароль в приложении не вводится.",busy?null:this::chooseLogin);}
            else {String id=current.optJSONObject("user")==null?"":current.optJSONObject("user").optString("id");OrcaCloudPresetStore store=new OrcaCloudPresetStore(this,id);JSONArray profiles=store.profiles();int conflicts=store.conflictCount();
                row("Аккаунт Orca Cloud","Профилей: "+profiles.length()+" · конфликтов: "+conflicts,null);
                row("Обновить из облака","Получить изменения и удаления; локальные правки сохраняются.",busy?null:this::pull);
                row("Профили","Просмотр, правка JSON и явная отправка выбранного профиля.",busy?null:this::showProfiles);
                if(conflicts>0)row("Конфликты","Локальные изменения сохранены; можно просмотреть версии.",busy?null:this::showConflicts);
                row("Экспорт профилей","Сохранить локальное зеркало и конфликты в JSON.",busy?null:this::export);
                row("Выйти","Удалить сохранённые токены с устройства.",busy?null:this::logout);
            }
        }catch(IOException e){row("Ошибка",message(e),null);}
    }
    private static String message(Throwable e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private void finishTask(String result){runOnUiThread(()->{if(!isDestroyed()&&!isFinishing()){busy=false;notice=result;render();}});}
    private void chooseLogin(){MdUi.alert(this).setTitle("Вход в Orca Cloud").setItems(new String[]{"Google","GitHub","Apple"},(d,n)->startLogin(new String[]{"google","github","apple"}[n])).setNegativeButton("Отмена",null).show();}
    private void startLogin(String provider){if(busy)return;if(!networkAllowed())return;busy=true;notice="Ожидаем входа в браузере…";render();new Thread(()->{
        try(OrcaCloudOAuth auth=new OrcaCloudOAuth()) {login=auth;runOnUiThread(()->{if(!isDestroyed())try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(auth.authorizationUrl(provider))));}catch(Exception e){try{auth.close();}catch(IOException ignored){}}});
            JSONObject data=auth.awaitAndExchange((path,json)->tokenRequest(path,json));normalizeExpiry(data);synchronized(SESSION_LOCK){NetworkSecrets.put(this,TOKEN_KEY,data.toString());}finishTask("Вход завершён. Нажмите «Обновить из облака».");
        }catch(Exception e){finishTask("Не удалось войти: "+message(e));}finally{login=null;}
    },"orca-cloud-login").start();}
    private OrcaCloudSync.Response tokenRequest(String path,String body)throws IOException {Map<String,String> h=new LinkedHashMap<>();h.put("apikey",OrcaCloudOAuth.PUBLISHABLE_KEY);h.put("Content-Type","application/json");
        NetworkHttp.Response r=NetworkHttp.request("POST",URI.create(OrcaCloudOAuth.AUTH_BASE+"/"+path),h,NetworkHttp.bytes(body.getBytes(StandardCharsets.UTF_8)),new NetworkHttp.Cancellation());return new OrcaCloudSync.Response(r.status,r.text());}
    private static void normalizeExpiry(JSONObject s)throws JSONException {if(s.optLong("expires_at",0)==0)s.put("expires_at",System.currentTimeMillis()/1000+s.optLong("expires_in",0));}
    private JSONObject freshSession() throws IOException {synchronized(SESSION_LOCK){try {
        JSONObject current=session();if(current==null)throw new IOException("Сначала войдите в Orca Cloud.");
        if(current.optLong("expires_at",0)>System.currentTimeMillis()/1000+60)return current;
        String refresh=current.optString("refresh_token");if(refresh.isEmpty())throw new IOException("Нет токена обновления.");
        OrcaCloudSync.Response r=tokenRequest("auth/v1/token?grant_type=refresh_token",new JSONObject().put("refresh_token",refresh).toString());
        if(r.status<200||r.status>=300)throw new IOException("Обновление входа: HTTP "+r.status);
        JSONObject updated=new JSONObject(r.body);if(updated.optString("access_token").isEmpty()||updated.optString("refresh_token").isEmpty())throw new IOException("Неполный ответ входа.");
        if(updated.optJSONObject("user")==null)updated.put("user",current.getJSONObject("user"));normalizeExpiry(updated);NetworkSecrets.put(this,TOKEN_KEY,updated.toString());return updated;
    }catch(JSONException e){throw new IOException("Неверный ответ входа.",e);}}}
    private OrcaCloudSync cloud(JSONObject session){String token=session.optString("access_token");return new OrcaCloudSync((method,path,body)->{
        Map<String,String> headers=new LinkedHashMap<>();headers.put("Authorization","Bearer "+token);if(body!=null)headers.put("Content-Type","application/json");
        NetworkHttp.Response response=NetworkHttp.request(method,URI.create("https://api.orcaslicer.com/"+path),headers,body==null?null:NetworkHttp.bytes(body.getBytes(StandardCharsets.UTF_8)),new NetworkHttp.Cancellation(),32*1024*1024);return new OrcaCloudSync.Response(response.status,response.text());});}
    private OrcaCloudPresetStore store(JSONObject s)throws IOException {JSONObject user=s.optJSONObject("user");if(user==null||user.optString("id").isEmpty())throw new IOException("Нет идентификатора аккаунта.");return new OrcaCloudPresetStore(this,user.optString("id"));}
    private void pull(){if(busy)return;if(!networkAllowed())return;busy=true;notice="Получаем профили…";render();new Thread(()->{try{JSONObject s=freshSession();OrcaCloudPresetStore store=store(s);OrcaCloudSync.Pull batch=cloud(s).pull(store.cursor());store.apply(batch);finishTask("Синхронизация завершена. Конфликтов: "+store.conflictCount()+".");}catch(Exception e){finishTask("Синхронизация не завершена: "+message(e));}},"orca-cloud-pull").start();}
    private void showProfiles(){try {JSONArray p=store(session()).profiles();if(p.length()==0){notice="Профилей пока нет. Обновите данные из облака.";render();return;}String[] names=new String[p.length()];for(int i=0;i<p.length();i++){JSONObject o=p.getJSONObject(i);names[i]=o.optString("name",o.optString("id"))+(o.optBoolean("dirty",false)?" · изменён":"");}MdUi.alert(this).setTitle("Профили Orca Cloud").setItems(names,(d,index)->{try{showProfile(p.getJSONObject(index));}catch(Exception e){notice=message(e);render();}}).setNegativeButton("Закрыть",null).show();}catch(Exception e){notice=message(e);render();}}
    private void showProfile(JSONObject profile){String id=profile.optString("id"),name=profile.optString("name");MdUi.alert(this).setTitle(name).setMessage("Редактирование сохраняет исходный JSON локально. Отправка выполняется только по отдельной команде; при конфликте серверные и локальные данные сохраняются.")
        .setPositiveButton("Редактировать",(d,w)->editProfile(id,profile.optJSONObject("content")))
        .setNeutralButton("Отправить",(d,w)->{if(!profile.optBoolean("dirty",false)){notice="Локальных изменений нет.";render();}else push(profile);}).setNegativeButton("Закрыть",null).show();}
    private void editProfile(String id,JSONObject content){if(content==null)return;EditText editor=new EditText(this);editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);editor.setText(content.toString());editor.setMinLines(8);
        android.app.AlertDialog dialog=MdUi.alert(this).setTitle("JSON профиля").setView(editor).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{store(session()).edit(id,new JSONObject(editor.getText().toString()));notice="Локальные изменения сохранены.";dialog.dismiss();render();}catch(Exception e){editor.setError("Не удалось сохранить: "+message(e));}}));dialog.show();}
    private void showConflicts(){try {JSONObject conflicts=store(session()).conflicts();java.util.ArrayList<String> ids=new java.util.ArrayList<>();for(java.util.Iterator<String> i=conflicts.keys();i.hasNext();)ids.add(i.next());
        MdUi.alert(this).setTitle("Конфликты").setItems(ids.toArray(new String[0]),(d,index)->{String id=ids.get(index);JSONObject c=conflicts.optJSONObject(id);String raw=c==null?"":c.toString(),value=raw.length()>4000?raw.substring(0,4000)+"… (полная версия доступна в экспорте)":raw;
            MdUi.alert(this).setTitle(id).setMessage("Версия сервера:\n"+value+"\n\nПринятие версии сервера удалит локальные несохранённые изменения этого профиля.")
                .setPositiveButton("Принять сервер",(dialog,which)->{try{store(session()).acceptServer(id);notice="Принята версия сервера.";}catch(Exception e){notice=message(e);}render();})
                .setNegativeButton("Сохранить локальную",null).show();}).setNegativeButton("Закрыть",null).show();
    }catch(Exception e){notice=message(e);render();}}
    private void push(JSONObject profile){if(busy)return;if(!networkAllowed())return;busy=true;notice="Отправляем выбранный профиль…";render();new Thread(()->{try {JSONObject s=freshSession();OrcaCloudPresetStore store=store(s);String id=profile.getString("id"),name=profile.getString("name");JSONObject content=profile.getJSONObject("content");long previous=profile.optLong("updated_time",0);OrcaCloudSync.Push result=cloud(s).push(id,name,content,previous==0?"":Long.toString(previous),false);
        if(result.isConflict()){store.recordConflict(id,result.conflict);finishTask("Конфликт: локальная и серверная версии сохранены. Автоматическая перезапись отключена.");}
        else {store.markPushed(id,content.toString(),result.updatedTime);finishTask("Профиль отправлен.");}
    }catch(Exception e){finishTask("Не удалось отправить профиль: "+message(e));}},"orca-cloud-push").start();}
    private void export(){try{exportData=store(session()).exportJson();Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"orca-cloud-presets.json");startActivityForResult(i,EXPORT);}catch(Exception e){notice="Не удалось начать экспорт: "+message(e);render();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==EXPORT&&result==RESULT_OK&&data!=null&&data.getData()!=null&&exportData!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new IOException("Файл недоступен.");out.write(exportData.getBytes(StandardCharsets.UTF_8));notice="Профили экспортированы.";}catch(IOException e){notice="Экспорт не завершён: "+message(e);}finally{exportData=null;}render();}}
    private void logout(){MdUi.alert(this).setTitle("Выйти из Orca Cloud?").setMessage("Сохранённые токены будут удалены; локальное зеркало профилей останется на устройстве.").setPositiveButton("Выйти",(d,w)->{try{synchronized(SESSION_LOCK){NetworkSecrets.remove(this,TOKEN_KEY);}notice="Выход выполнен.";}catch(IOException e){notice="Ошибка выхода: "+message(e);}render();}).setNegativeButton("Отмена",null).show();}
}
