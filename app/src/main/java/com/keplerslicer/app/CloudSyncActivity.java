package com.keplerslicer.app;

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

/** Explicit Cloud profile sign-in and user-controlled profile synchronization. */
public final class CloudSyncActivity extends Activity {
    private static final int EXPORT=821;
    private MdUi ui;private LinearLayout page;private volatile CloudProfileOAuth login;private volatile boolean busy;private String notice="";private String exportData;
    @Override public void onCreate(Bundle state){MdUi.prepare(this);super.onCreate(state);render();}
    @Override protected void onDestroy(){CloudProfileOAuth active=login;if(active!=null)try{active.close();}catch(IOException ignored){}super.onDestroy();}
    private boolean networkAllowed(){try{NetworkPolicy.requireCloud(this);return true;}catch(IOException e){notice=e.getMessage();render();return false;}}
    private void row(String title,String description,Runnable action){page.addView(ui.row(title,description,null,action));ui.gap(page,10);}
    private JSONObject session() throws IOException {return OnlinePreferences.session(this);}
    private void render(){if(isFinishing()||isDestroyed())return;ui=new MdUi(this);page=ui.page(this,"Облако профилей",this::finish);ui.navigation(this,3);
        if(!notice.isEmpty()){TextView t=ui.text(notice,13,false);t.setTextColor(ui.muted);page.addView(t);ui.gap(page,16);}
        row("О синхронизации","Синхронизация применяет пользовательские Printer/Filament/Process к репозиторию Kepler. Конфликты и неизвестные поля сохраняются. Реальный аккаунт и серверная авторизация пока не проверены.",null);
        try {JSONObject current=session();if(current==null){row("Войти","Официальный вход через браузер; пароль в приложении не вводится.",busy?null:this::chooseLogin);}
            else {String id=current.optJSONObject("user")==null?"":current.optJSONObject("user").optString("id");CloudProfileStore store=new CloudProfileStore(this,id);JSONArray profiles=store.profiles();int conflicts=store.conflictCount();
                row("Облачный аккаунт профилей","Профилей: "+profiles.length()+" · конфликтов: "+conflicts,null);
                row("Синхронизировать профили","Получить изменения и отправить изменённые пользовательские Printer/Filament/Process. При конфликте обе версии сохраняются.",busy?null:this::confirmSync);
                row("Профили","Просмотр, правка JSON и явная отправка выбранного профиля.",busy?null:this::showProfiles);
                row("Конфликты профилей Kepler","Сравнить обе версии: оставить локальную, принять серверную или сохранить обе отдельными профилями.",busy?null:this::reviewRepositoryConflicts);
                if(conflicts>0)row("Конфликты","Локальные изменения сохранены; можно просмотреть версии.",busy?null:this::showConflicts);
                row("Экспорт профилей","Сохранить локальное зеркало и конфликты в JSON.",busy?null:this::export);
                row("Выйти","Удалить сохранённые токены с устройства.",busy?null:this::logout);
            }
        }catch(IOException e){row("Ошибка",message(e),null);}
    }
    private static String message(Throwable e){return EngineText.clean(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
    private void finishTask(String result){runOnUiThread(()->{if(!isDestroyed()&&!isFinishing()){busy=false;notice=result;render();}});}
    private void chooseLogin(){MdUi.alert(this).setTitle("Вход в облако профилей").setItems(new String[]{"Google","GitHub","Apple"},(d,n)->startLogin(new String[]{"google","github","apple"}[n])).setNegativeButton("Отмена",null).show();}
    private void startLogin(String provider){if(busy)return;if(!networkAllowed())return;busy=true;notice="Ожидаем входа в браузере…";render();new Thread(()->{
        try(CloudProfileOAuth auth=new CloudProfileOAuth()) {login=auth;runOnUiThread(()->{if(!isDestroyed())try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(auth.authorizationUrl(provider))));}catch(Exception e){try{auth.close();}catch(IOException ignored){}}});
            JSONObject data=auth.awaitAndExchange((path,json)->tokenRequest(path,json));OnlinePreferences.saveSession(this,data);finishTask("Вход завершён. Нажмите «Синхронизировать профили».");
        }catch(Exception e){finishTask("Не удалось войти: "+message(e));}finally{login=null;}
    },"kepler-cloud-login").start();}
    private CloudProfileSync.Response tokenRequest(String path,String body)throws IOException {return OnlinePreferences.tokenRequest(this,path,body);}
    private JSONObject freshSession()throws IOException{return OnlinePreferences.freshSession(this);}
    private CloudProfileSync cloud(JSONObject session){return OnlinePreferences.client(this,session);}
    private CloudProfileStore store(JSONObject s)throws IOException {JSONObject user=s.optJSONObject("user");if(user==null||user.optString("id").isEmpty())throw new IOException("Нет идентификатора аккаунта.");return new CloudProfileStore(this,user.optString("id"));}
    private void confirmSync(){MdUi.alert(this).setTitle("Синхронизировать пользовательские профили?").setMessage("Из облака будут получены изменения, а локальные пользовательские Printer/Filament/Process и ранее подтверждённые удаления будут отправлены в текущий аккаунт Cloud profile. Конфликты не перезаписываются автоматически.").setPositiveButton("Синхронизировать",(d,w)->pull()).setNegativeButton("Отмена",null).show();}
    private void pull(){if(busy)return;if(!networkAllowed())return;busy=true;notice="Синхронизация профилей…";render();new Thread(()->{try{finishTask(OnlinePreferences.syncNow(this));}catch(Exception e){finishTask("Синхронизация не завершена: "+message(e));}},"kepler-cloud-pull").start();}
    private void reviewRepositoryConflicts(){if(busy||!networkAllowed())return;busy=true;notice="Получаем версии для сравнения…";render();new Thread(()->{try{JSONArray reviews=OnlinePreferences.conflictReviews(this);runOnUiThread(()->{if(isDestroyed())return;busy=false;if(reviews.length()==0){notice="Конфликтов изменённых связанных профилей не найдено. При конфликте имён экспортируйте обе версии, переименуйте локальный профиль через Save As, затем повторите синхронизацию. Профили с отсутствующим родителем сначала требуют его импорта.";render();return;}String[] labels=new String[reviews.length()];for(int i=0;i<reviews.length();i++){JSONObject row=reviews.optJSONObject(i);labels[i]=row.optJSONObject("local_source").optString("name")+(row.optBoolean("deleted",false)?" · удалён на сервере":" · изменён на сервере");}MdUi.alert(this).setTitle("Выберите конфликт").setItems(labels,(d,n)->previewRepositoryConflict(reviews.optJSONObject(n))).setNegativeButton("Закрыть",null).show();});}catch(Exception e){finishTask("Версии не получены: "+message(e));}},"kepler-cloud-review").start();}
    private void previewRepositoryConflict(JSONObject review){if(review==null)return;String local=review.optJSONObject("local_source").toString(),remote=review.optBoolean("deleted",false)?"Удалено на сервере":review.optJSONObject("server_source").toString();String preview="Локальная версия:\n"+shortPreview(local)+"\n\nСерверная версия:\n"+shortPreview(remote)+"\n\nПри изменении локального профиля после этого просмотра выбор будет отклонён.";MdUi.alert(this).setTitle("Сравнение версий").setMessage(preview).setPositiveButton("Выбрать действие",(d,w)->MdUi.alert(this).setTitle("Только этот профиль").setItems(new String[]{"Оставить локальную", "Принять серверную", "Сохранить обе версии", "Экспортировать обе версии"},(dialog,index)->{if(index==3){exportReviewedVersions(review);return;}if(index==2){EditText name=new EditText(this);name.setSingleLine(true);name.setText(review.optJSONObject("local_source").optString("name")+" — local copy");MdUi.alert(this).setTitle("Имя отдельной локальной копии").setMessage("Полная локальная конфигурация сохранится отдельным профилем со стабильным системным предком; для исходного будет принята проверенная серверная версия.").setView(name).setPositiveButton("Сохранить обе",(a,b)->resolveRepositoryConflict(review,"duplicate",name.getText().toString())).setNegativeButton("Отмена",null).show();return;}String choice=index==0?"local":"remote";String warning=index==0?"Проверенная локальная версия будет отправлена в облако только если ревизия сервера ещё совпадает. Остальные профили не меняются.":"Только локальная версия этого профиля будет заменена проверенной серверной версией или удалена, если она удалена на сервере. Остальные профили не меняются.";MdUi.alert(this).setTitle("Подтвердить выбор?").setMessage(warning).setPositiveButton(index==0?"Отправить локальную":"Принять серверную",(a,b)->resolveRepositoryConflict(review,choice,null)).setNegativeButton("Отмена",null).show();}).setNegativeButton("Отмена",null).show()).setNeutralButton("Экспортировать версии",(d,w)->exportReviewedVersions(review)).setNegativeButton("Отмена",null).show();}
    private static String shortPreview(String value){return value.length()<=5000?value:value.substring(0,5000)+"… (полная версия доступна в экспорте)";}
    private void resolveRepositoryConflict(JSONObject review,String choice,String name){if(busy)return;busy=true;notice="Применяем проверенный выбор…";render();new Thread(()->{try{finishTask(OnlinePreferences.resolveConflict(this,review,choice,name));}catch(Exception e){finishTask("Выбор не применён: "+message(e)+". Обе версии сохранены; обновите сравнение или экспортируйте их.");}},"kepler-cloud-resolve").start();}
    private void exportReviewedVersions(JSONObject review){try{exportData=new JSONObject().put("format","kepler-cloud-conflict-review-v1").put("review",new JSONObject(review.toString())).toString(2);Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"kepler-cloud-conflict.json");startActivityForResult(intent,EXPORT);}catch(Exception e){notice="Экспорт не начат: "+message(e);render();}}
    private void showProfiles(){try {JSONArray p=store(session()).profiles();if(p.length()==0){notice="Профилей пока нет. Обновите данные из облака.";render();return;}String[] names=new String[p.length()];for(int i=0;i<p.length();i++){JSONObject o=p.getJSONObject(i);names[i]=o.optString("name",o.optString("id"))+(o.optBoolean("dirty",false)?" · изменён":"");}MdUi.alert(this).setTitle("Облачные профили").setItems(names,(d,index)->{try{showProfile(p.getJSONObject(index));}catch(Exception e){notice=message(e);render();}}).setNegativeButton("Закрыть",null).show();}catch(Exception e){notice=message(e);render();}}
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
    private void push(JSONObject profile){if(busy||!networkAllowed())return;busy=true;notice="Отправляем выбранный профиль…";render();new Thread(()->{try{finishTask(OnlinePreferences.pushMirroredProfile(this,profile));}catch(Exception e){finishTask("Не удалось отправить профиль: "+message(e));}},"kepler-cloud-push").start();}
    private void export(){try{exportData=store(session()).exportJson();Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"kepler-cloud-profiles.json");startActivityForResult(i,EXPORT);}catch(Exception e){notice="Не удалось начать экспорт: "+message(e);render();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==EXPORT&&result==RESULT_OK&&data!=null&&data.getData()!=null&&exportData!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new IOException("Файл недоступен.");out.write(exportData.getBytes(StandardCharsets.UTF_8));notice="Профили экспортированы.";}catch(IOException e){notice="Экспорт не завершён: "+message(e);}finally{exportData=null;}render();}}
    private void logout(){MdUi.alert(this).setTitle("Выйти из облака профилей?").setMessage("Сохранённые токены будут удалены; локальное зеркало профилей останется на устройстве.").setPositiveButton("Выйти",(d,w)->{try{OnlinePreferences.logout(this);notice="Выход выполнен.";}catch(IOException e){notice="Ошибка выхода: "+message(e);}render();}).setNegativeButton("Отмена",null).show();}
}
