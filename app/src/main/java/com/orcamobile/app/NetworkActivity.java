package com.orcamobile.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Connections use the phone's current Wi-Fi/LAN. Every transfer is explicitly initiated. */
public final class NetworkActivity extends Activity {
    private static final int CLOUD_LOGIN=751;
    private static final String UPLOAD_RECEIPT="last_uploaded_result";
    private MdUi ui;private LinearLayout page;private NetworkStore.State state;
    private TextView message;private String notice="";private boolean busy,editing;
    private NetworkHttp.Cancellation cancellation;private PrinterDiscovery discovery;
    private NetworkStore.Connection draft;private String pendingOauth="";private Spinner types,auths;private EditText name,url,user,secret,storage,extras;
    private final List<String[]> found=new ArrayList<>();
    @Override public void onCreate(Bundle saved){MdUi.prepare(this);super.onCreate(saved);load();}
    @Override protected void onStop(){stopDiscovery();super.onStop();}
    @Override protected void onDestroy(){if(cancellation!=null)cancellation.cancel();super.onDestroy();}
    private void load(){new Thread(()->{try{NetworkStore.State s=NetworkStore.load(this);runOnUiThread(()->{if(isDestroyed())return;state=s;render();});}catch(Exception e){runOnUiThread(()->{notice=error(e);state=new NetworkStore.State(new ArrayList<>(),"");render();});}},"printer-connections-load").start();}
    private void row(String title,String subtitle,String icon,Runnable action){page.addView(ui.row(title,subtitle,icon,action));ui.gap(page,10);}
    private void render(){if(isDestroyed()||isFinishing())return;editing=false;MdUi.prepare(this);ui=new MdUi(this);page=ui.page(this,"Сеть и принтеры",this::finish);ui.navigation(this,3);
        message=ui.text(notice,13,false);message.setTextColor(ui.muted);if(!notice.isEmpty()){page.addView(message);ui.gap(page,16);}
        row(NetworkPolicy.offline(this)?"Режим без сети":"Локальная сеть и облако",NetworkPolicy.offline(this)?"Импорт, нарезка и экспорт работают на устройстве":"Подключение использует текущую сеть телефона","⌁",this::mode);
        row("Найти в Wi-Fi","Обнаружить службы принтеров DNS-SD; адрес можно ввести вручную","⌕",busy?null:this::discover);
        row("Добавить подключение","Все типы серверов из Orca и отдельные облачные службы","＋",busy?null:()->form(null,null));
        row("Orca Cloud","Вход и синхронизация профилей","☁",busy?null:()->startActivity(new Intent(this,CloudSyncActivity.class)));
        if(state==null)return;
        ui.gap(page,12);page.addView(ui.text("Сохранённые принтеры",18,true));ui.gap(page,12);
        if(state.connections.isEmpty()){TextView empty=ui.text("Добавьте адрес принтера или выберите найденную службу.",14,false);empty.setTextColor(ui.muted);page.addView(empty);}
        for(NetworkStore.Connection c:state.connections){String marker=c.id.equals(state.active)?" · выбран":"";row(c.name+marker,label(c.endpoint.type)+" · "+c.endpoint.baseUri.toASCIIString(),"▣",busy?null:()->connectionMenu(c));}
        ui.gap(page,16);TextView note=ui.text("Проверка соединения и передача запускаются вручную. Ключи и пароли хранятся на устройстве в зашифрованном виде.",12,false);note.setTextColor(ui.muted);page.addView(note);
    }
    private void mode(){MdUi.alert(this).setTitle("Режим работы").setSingleChoiceItems(new String[]{"Локальная сеть и облачные службы","Без сети"},NetworkPolicy.offline(this)?1:0,(d,n)->{if(cancellation!=null)cancellation.cancel();stopDiscovery();getSharedPreferences("app_preferences",0).edit().putBoolean("network_offline",n==1).apply();d.dismiss();render();}).setNegativeButton("Отмена",null).show();}
    private void connectionMenu(NetworkStore.Connection c){List<String> actions=new ArrayList<>(Arrays.asList("Выбрать","Проверить соединение","Обновить статус","Передать текущий G-code","Открыть веб-интерфейс","Изменить"));if(c.endpoint.type==PrinterEndpoint.Type.ULTIMAKER){actions.add("Запросить сопряжение");actions.add("Проверить сопряжение");actions.add("Передать и начать печать");}if(c.endpoint.type==PrinterEndpoint.Type.OCTOPRINT||c.endpoint.type==PrinterEndpoint.Type.MOONRAKER)actions.add("Запустить переданный файл");actions.add("Удалить подключение");
        MdUi.alert(this).setTitle(c.name).setItems(actions.toArray(new String[0]),(d,index)->{String action=actions.get(index);switch(action){case "Выбрать":localTask("Сохраняем выбор…",cancel->{NetworkStore.select(this,c.id);return "Подключение выбрано.";});break;case "Проверить соединение":task("Проверяем соединение…",cancel->{PrinterHostClient.HostInfo info=new PrinterHostClient(fresh(c,cancel)).test(cancel);return "Связь подтверждена: "+info.name+"\nСостояние: "+info.state;});break;case "Обновить статус":task("Получаем состояние…",cancel->{PrinterHostClient.Status info=new PrinterHostClient(fresh(c,cancel)).status(cancel);return "Состояние: "+info.state+(info.state.equals("unknown")?"\nЭтот протокол подтверждает доступность, но не возвращает состояние печати.":"");});break;case "Передать текущий G-code":confirmUpload(c,false);break;case "Открыть веб-интерфейс":openWeb(c.endpoint.baseUri.toASCIIString());break;case "Изменить":form(c,null);break;case "Запросить сопряжение":task("Запрашиваем сопряжение…",cancel->{String[] credentials=LegacyPrinterHosts.requestUltiMakerPairing(c.endpoint,cancel);PrinterEndpoint e=c.endpoint;PrinterEndpoint paired=new PrinterEndpoint(e.type,e.baseUri.toASCIIString(),PrinterEndpoint.Auth.DIGEST,credentials[0],credentials[1],e.storage,e.extras);NetworkStore.save(this,new NetworkStore.Connection(c.id,c.name,c.printerIdentity,paired));return "Запрос сопряжения отправлен. Подтвердите его на панели принтера, затем проверьте сопряжение.";});break;case "Проверить сопряжение":task("Проверяем сопряжение…",cancel->LegacyPrinterHosts.ultiMakerPairingStatus(c.endpoint,cancel));break;case "Передать и начать печать":confirmUpload(c,true);break;case "Запустить переданный файл":confirmStart(c);break;default:MdUi.alert(this).setTitle("Удалить подключение?").setMessage("Будут удалены адрес и сохранённые учётные данные этого подключения.").setPositiveButton("Удалить",(a,b)->localTask("Удаляем подключение…",cancel->{NetworkStore.remove(this,c.id);return "Подключение удалено.";})).setNegativeButton("Отмена",null).show();}}).setNegativeButton("Закрыть",null).show();
    }
    private void openWeb(String value){try{NetworkPolicy.requireOnline(this);java.net.URI uri=java.net.URI.create(value);if(!Arrays.asList("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IOException("Некорректная ссылка.");new Thread(()->{try{NetworkPolicy.checkUri(this,uri);runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(value)));}catch(Exception e){notice=error(e);render();}});}catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){notice=error(e);render();}});}},"printer-web-policy").start();}catch(Exception e){notice=error(e);render();}}
    private interface Work{String run(NetworkHttp.Cancellation cancel)throws Exception;}
    private void localTask(String text,Work work){execute(text,work,false);}
    private void task(String text,Work work){execute(text,work,true);}
    private void execute(String text,Work work,boolean network){if(busy)return;try{if(network)NetworkPolicy.requireOnline(this);}catch(IOException e){notice=error(e);render();return;}busy=true;notice=text;render();
        final long[] lastUpdate={0};NetworkHttp.Cancellation token=new NetworkHttp.Cancellation((sent,total)->{long now=System.currentTimeMillis();if(now-lastUpdate[0]<250)return;lastUpdate[0]=now;runOnUiThread(()->{if(!isDestroyed()&&busy&&message!=null)message.setText(total>0?"Передано "+sent+" / "+total+" байт":"Передано "+sent+" байт");});});cancellation=token;
        page.addView(ui.button("Отменить операцию",false,token::cancel));new Thread(()->{String result;try{result=work.run(token);}catch(Throwable e){result=token.isCancelled()?"Операция отменена. Проверьте наличие файла или временных частей на сервере.":error(e);}final String end=result;runOnUiThread(()->{if(isDestroyed())return;busy=false;cancellation=null;notice=end;load();});},"printer-network-operation").start();
    }
    /** Refresh before a read or upload; never replay an upload after an ambiguous failure. */
    private PrinterEndpoint fresh(NetworkStore.Connection supplied,NetworkHttp.Cancellation cancel)throws Exception {
        NetworkPolicy.requireEndpoint(this,supplied.endpoint);
        if(supplied.endpoint.type!=PrinterEndpoint.Type.SIMPLYPRINT)return supplied.endpoint;
        synchronized(NetworkStore.class){
            NetworkStore.Connection current=null;for(NetworkStore.Connection item:NetworkStore.load(this).connections)if(item.id.equals(supplied.id)){current=item;break;}
            if(current==null||current.endpoint.type!=PrinterEndpoint.Type.SIMPLYPRINT)throw new IOException("Подключение изменилось. Откройте его снова.");
            PrinterEndpoint endpoint=current.endpoint;JSONObject session=null;
            if(!current.oauthSession.isEmpty()){session=new JSONObject(current.oauthSession);if(!endpoint.secret().equals(session.optString("access_token")))throw new IOException("Сессия SimplyPrint повреждена. Войдите снова.");}
            boolean expired=session!=null&&session.optLong("expires_at",Long.MAX_VALUE)<=System.currentTimeMillis()/1000+60;
            if(!expired){int status=tokenInfo(endpoint,cancel);if(status!=401)return endpoint;}
            if(session==null||session.optString("refresh_token").isEmpty())throw new IOException("Вход SimplyPrint истёк. Войдите снова.");
            JSONObject updated;
            cancel.check();
            try {updated=SimplyPrintOAuth.refresh(session.optString("refresh_token"),new NetworkHttp.Cancellation());}
            catch(InterruptedIOException cancelled){throw cancelled;}
            catch(IOException error){throw new IOException("Не удалось обновить вход SimplyPrint. Проверьте сеть или войдите снова.",error);}
            if(updated.optString("refresh_token").isEmpty())updated.put("refresh_token",session.optString("refresh_token"));
            updated.put("expires_at",System.currentTimeMillis()/1000+updated.optLong("expires_in",3600));
            try{endpoint=NetworkStore.updateOAuth(this,current.id,current.endpoint.secret(),updated.toString());}
            catch(IOException error){throw new IOException("Не удалось сохранить обновлённый вход SimplyPrint. Войдите снова.",error);}
            if(tokenInfo(endpoint,cancel)==401)throw new IOException("Обновлённый вход SimplyPrint отклонён. Войдите снова.");
            return endpoint;
        }
    }
    private static int tokenInfo(PrinterEndpoint endpoint,NetworkHttp.Cancellation cancel)throws IOException {
        NetworkHttp.Response response=NetworkHttp.request("GET",java.net.URI.create("https://api.simplyprint.io/oauth2/TokenInfo"),Collections.singletonMap("Authorization","Bearer "+endpoint.secret()),null,cancel);
        if(response.status!=401)response.requireSuccess();return response.status;
    }
    private static final class Result {File file;String name,details,fingerprint;}
    private Result currentResult()throws IOException{Map<String,?> record=getSharedPreferences("last_slice_result",0).getAll();String fingerprint=(String)record.get("job_fingerprint"),path=(String)record.get("path");if(fingerprint==null||path==null||!fingerprint.equals(ProcessOverridesStore.currentResultFingerprint(this)))throw new IOException("Сначала нарежьте текущую модель с выбранными параметрами.");File root=new File(getFilesDir(),"slicer-engine").getCanonicalFile(),file=new File(path).getCanonicalFile();if(!file.getPath().startsWith(root.getPath()+File.separator)||!file.getName().equals("result.gcode")||!file.isFile()||file.length()==0)throw new IOException("Файл результата недоступен.");Result result=new Result();result.file=file;result.fingerprint=fingerprint;result.details=String.valueOf(record.get("details"));String model=String.valueOf(record.get("model"));result.name=model.replaceAll("(?i)\\.(stl|obj|3mf)$","").replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_")+".gcode";return result;}
    private NetworkStore.Connection currentConnection(String id)throws IOException {for(NetworkStore.Connection item:NetworkStore.load(this).connections)if(item.id.equals(id))return item;throw new IOException("Подключение удалено.");}
    private static boolean sameEndpoint(NetworkStore.Connection a,NetworkStore.Connection b){return a.id.equals(b.id)&&a.endpoint.type==b.endpoint.type&&a.endpoint.baseUri.equals(b.endpoint.baseUri);}
    private static boolean validRemotePath(String path){if(path==null||path.isEmpty()||path.startsWith("/")||path.endsWith("/"))return false;try{for(String part:path.split("/",-1))PrinterEndpoint.encodeComponent(part);return true;}catch(IllegalArgumentException e){return false;}}
    private void confirmUpload(NetworkStore.Connection c,boolean start){try{
        NetworkPolicy.requireOnline(this);if(!c.printerIdentity.isEmpty()&&!c.printerIdentity.equals(NetworkStore.currentPrinterIdentity(this)))throw new IOException("Подключение сохранено для другой модели принтера. Выберите подходящий профиль или измените подключение.");
        if(c.endpoint.type==PrinterEndpoint.Type.SL1)throw new IOException("SLA-сервер требует SL1/SL1S. Текущая сборка нарезает FFF G-code.");Result result=currentResult();
        String warning=start?"Это действие передаёт файл и ЗАПУСКАЕТ ПЕЧАТЬ на UltiMaker.":"Будет передан только файл; команда запуска не отправляется.";
        if(c.endpoint.type==PrinterEndpoint.Type.ESP3D)warning+="\n\nESP3D сохранит файл как «"+LegacyHostFiles.short83(result.name)+"». Возможны совпадения с существующими именами и перезапись.";
        MdUi.alert(this).setTitle(start?"Начать печать?":"Передать G-code?").setMessage(c.name+"\n"+c.endpoint.baseUri+"\n\n"+result.details+"\nРазмер: "+result.file.length()+" байт\n\n"+warning+"\nРезультат экспериментальной нарезки ещё не проверен для физической печати.")
            .setPositiveButton(start?"Передать и начать":"Передать файл",(d,w)->task("Передаём файл…",cancel->{
                Result exact=currentResult();if(!exact.file.equals(result.file)||!exact.fingerprint.equals(result.fingerprint))throw new IOException("Результат изменился. Повторите выбор.");
                NetworkStore.Connection current=currentConnection(c.id);if(!sameEndpoint(c,current))throw new IOException("Адрес или тип подключения изменились. Повторите выбор.");
                NetworkSecrets.remove(this,UPLOAD_RECEIPT);PrinterEndpoint e=fresh(current,cancel);
                if(start){LegacyPrinterHosts.uploadAndStart(e,result.file,result.name,cancel);return "Задание передано с командой запуска.";}
                PrinterHostClient.UploadResult sent;try{sent=new PrinterHostClient(e).upload(result.file,result.name,cancel);}catch(IOException error){if(e.type==PrinterEndpoint.Type.SIMPLYPRINT&&"Cloud host HTTP 401".equals(error.getMessage()))throw new IOException("SimplyPrint отклонил вход. Проверьте подключение или войдите снова; повторите передачу вручную.",error);throw error;}
                cancel.check();String suffix=sent.remotePath==null||sent.remotePath.isEmpty()?"":"\nФайл: "+sent.remotePath;
                if((e.type==PrinterEndpoint.Type.OCTOPRINT||e.type==PrinterEndpoint.Type.MOONRAKER)&&validRemotePath(sent.remotePath)){
                    try{Result latest=currentResult();NetworkStore.Connection after=currentConnection(c.id);
                        if(latest.file.equals(result.file)&&latest.fingerprint.equals(result.fingerprint)&&sameEndpoint(current,after)){
                            JSONObject receipt=new JSONObject().put("connection",c.id).put("type",e.type.name()).put("url",e.baseUri.toASCIIString()).put("path",result.file.getCanonicalPath()).put("fingerprint",result.fingerprint).put("remote",sent.remotePath);
                            try{NetworkSecrets.put(this,UPLOAD_RECEIPT,receipt.toString());}catch(IOException error){suffix+="\nКвитанцию для запуска не удалось сохранить; файл уже передан.";}
                        }else suffix+="\nПараметры или подключение изменились; запуск из приложения недоступен.";
                    }catch(Exception changed){suffix+="\nПроверить актуальность для запуска не удалось; файл уже передан.";}
                }else if(e.type==PrinterEndpoint.Type.OCTOPRINT||e.type==PrinterEndpoint.Type.MOONRAKER)suffix+="\nСервер не подтвердил путь файла; запуск из приложения недоступен.";
                if(sent.actionUrl!=null&&!sent.actionUrl.isEmpty())runOnUiThread(()->{if(!isDestroyed())MdUi.alert(this).setTitle("Завершить импорт").setMessage("Открыть страницу импорта SimplyPrint?").setPositiveButton("Открыть",(a,b)->openWeb(sent.actionUrl)).setNegativeButton("Позже",null).show();});
                return sent.message+suffix;
            })).setNegativeButton("Отмена",null).show();
    }catch(Exception e){notice=error(e);render();}}
    private String validatedReceipt(NetworkStore.Connection connection,Result result,JSONObject receipt)throws IOException {
        if(!connection.id.equals(receipt.optString("connection"))||!connection.endpoint.type.name().equals(receipt.optString("type"))||!connection.endpoint.baseUri.toASCIIString().equals(receipt.optString("url"))||!result.file.getCanonicalPath().equals(receipt.optString("path"))||!result.fingerprint.equals(receipt.optString("fingerprint")))throw new IOException("Модель, настройки или получатель изменились. Сначала передайте файл заново.");
        String remote=receipt.optString("remote","");if(!validRemotePath(remote))throw new IOException("Сервер не подтвердил путь файла. Передайте файл заново.");return remote;
    }
    private void confirmStart(NetworkStore.Connection connection){try{
        NetworkPolicy.requireOnline(this);NetworkStore.Connection actual=currentConnection(connection.id);if(!sameEndpoint(connection,actual))throw new IOException("Адрес подключения изменился.");
        String raw=NetworkSecrets.get(this,UPLOAD_RECEIPT);if(raw.isEmpty())throw new IOException("Сначала передайте текущий файл на этот принтер.");Result result=currentResult();String remote=validatedReceipt(actual,result,new JSONObject(raw));
        MdUi.alert(this).setTitle("Запустить печать?").setMessage(actual.name+"\n"+remote+"\n\nПринтер получит команду запуска ранее переданного файла. G-code экспериментальной сборки не проверен физической печатью.")
            .setPositiveButton("Запустить печать",(d,w)->task("Отправляем команду запуска…",cancel->{
                Result latest=currentResult();NetworkStore.Connection again=currentConnection(connection.id);if(!sameEndpoint(actual,again))throw new IOException("Адрес подключения изменился.");String receipt=NetworkSecrets.get(this,UPLOAD_RECEIPT);if(!latest.file.equals(result.file)||!latest.fingerprint.equals(result.fingerprint)||!raw.equals(receipt)||receipt.isEmpty()||!remote.equals(validatedReceipt(again,latest,new JSONObject(receipt))))throw new IOException("Квитанция загрузки изменилась. Передайте файл заново.");
                try{new PrinterHostClient(fresh(again,cancel)).startUploaded(remote,cancel);}catch(IOException error){throw new IOException("Запуск не подтверждён. Проверьте состояние принтера перед повторной командой.",error);}return "Сервер подтвердил команду запуска.";
            })).setNegativeButton("Отмена",null).show();
    }catch(Exception error){notice=error(error);render();}}
    private void form(NetworkStore.Connection connection,String suggestedUrl){form(connection,suggestedUrl,null);}
    private static PrinterEndpoint.Type discoveredType(String service){String value=service==null?"":service.toLowerCase(Locale.ROOT);if(value.startsWith("_moonraker."))return PrinterEndpoint.Type.MOONRAKER;if(value.startsWith("_prusa.")||value.startsWith("_prusalink."))return PrinterEndpoint.Type.PRUSALINK;if(value.startsWith("_ultimaker."))return PrinterEndpoint.Type.ULTIMAKER;return PrinterEndpoint.Type.OCTOPRINT;}
    private void form(NetworkStore.Connection connection,String suggestedUrl,String service){stopDiscovery();editing=true;draft=connection;pendingOauth=connection==null?"":connection.oauthSession;PrinterEndpoint.Type suggested=connection==null?discoveredType(service):connection.endpoint.type;ui=new MdUi(this);page=ui.page(this,connection==null?"Новое подключение":"Подключение",this::render);ui.navigation(this,3);
        name=field("Название",connection==null?"Мой принтер":connection.name,false);String[] labels=new String[PrinterEndpoint.Type.values().length];for(int i=0;i<labels.length;i++)labels[i]=label(PrinterEndpoint.Type.values()[i]);types=spinner("Протокол",labels,suggested.ordinal());hostChips(types,labels);
        url=field("Адрес HTTP(S) или IP",connection==null?(suggestedUrl==null?"":suggestedUrl):connection.endpoint.baseUri.toASCIIString(),false);
        auths=spinner("Авторизация",new String[]{"Без авторизации","API key","Digest","Пароль / session","Bearer token","Basic"},connection==null?(service==null?0:defaultAuth(suggested).ordinal()):connection.endpoint.auth.ordinal());
        user=field("Пользователь",connection==null?"":connection.endpoint.username,false);secret=field("Ключ / пароль / session",connection==null?"":connection.endpoint.secret(),true);storage=field("Хранилище / printer slug",connection==null?(suggested==PrinterEndpoint.Type.MOONRAKER?"gcodes":suggested==PrinterEndpoint.Type.PRUSALINK?"usb":"local"):connection.endpoint.storage,false);
        extras=field("Дополнительные параметры JSON",connection==null?"{}":new JSONObject(connection.endpoint.extras).toString(),false);extras.setSingleLine(false);extras.setMinLines(3);
        TextView description=ui.text("Repetier: storage — slug принтера, group — группа. Flashforge: serial, flashforge_mode (http/tcp), firmware. Elegoo: model (Elegoo Centauri / Elegoo Centauri 2), X-Token — в поле ключа. TCP-порт: control_port; Flashforge HTTP: http_port. Obico: printer_id. 3DPrinterOS: project_id, project_name, printer_type, gcode_type.",12,false);description.setTextColor(ui.muted);page.addView(description);ui.gap(page,16);
        page.addView(ui.button("Вход SimplyPrint / 3DPrinterOS",false,()->{PrinterEndpoint.Type type=PrinterEndpoint.Type.values()[types.getSelectedItemPosition()];if(type!=PrinterEndpoint.Type.SIMPLYPRINT&&type!=PrinterEndpoint.Type.THREE_D_PRINTER_OS){MdUi.alert(this).setMessage("Выберите SimplyPrint или 3DPrinterOS в поле протокола.").setPositiveButton("Понятно",null).show();return;}try{NetworkPolicy.requireCloud(this);startActivityForResult(new Intent(this,CloudPrintLoginActivity.class).putExtra("type",type.name()).putExtra("base_url",url.getText().toString()),CLOUD_LOGIN);}catch(Exception e){MdUi.alert(this).setMessage(error(e)).setPositiveButton("Понятно",null).show();}}));ui.gap(page,16);
        page.addView(ui.button("Выбрать принтер Obico",false,()->chooseCloudItem(CloudSelectionHelper.Kind.OBICO_PRINTER)));ui.gap(page,8);
        page.addView(ui.button("Выбрать проект 3DPrinterOS",false,()->chooseCloudItem(CloudSelectionHelper.Kind.PRINTER_OS_PROJECT)));ui.gap(page,8);
        page.addView(ui.button("Выбрать тип принтера 3DPrinterOS",false,()->chooseCloudItem(CloudSelectionHelper.Kind.PRINTER_OS_TYPE)));ui.gap(page,16);
        page.addView(ui.button("Сохранить подключение",true,this::saveForm));ui.gap(page,12);TextView note=ui.text("Сохранение не соединяется с сервером. Bambu требует совместимой Android-библиотеки сетевого плагина; в этой сборке она недоступна. SL1 поддерживает соединение, но не FFF G-code.",12,false);note.setTextColor(ui.muted);page.addView(note);
        types.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){boolean initial=true;public void onNothingSelected(android.widget.AdapterView<?> parent){}public void onItemSelected(android.widget.AdapterView<?> parent,View view,int index,long id){if(initial){initial=false;return;}if(connection!=null)return;PrinterEndpoint.Type type=PrinterEndpoint.Type.values()[index];auths.setSelection(defaultAuth(type).ordinal());if(type==PrinterEndpoint.Type.MOONRAKER)storage.setText("gcodes");else if(type==PrinterEndpoint.Type.PRUSALINK)storage.setText("usb");else storage.setText("local");String host=defaultHost(type);if(!host.isEmpty())url.setText(host);}});
    }
    private EditText field(String label,String value,boolean password){TextView caption=ui.text(label,13,false);caption.setTextColor(ui.muted);ui.pad(caption,4,0,4,6);page.addView(caption);EditText e=new EditText(this);e.setTextColor(ui.ink);e.setTextSize(15);com.orcamobile.app.glass.GlassTypography.apply(e,600);e.setMinHeight(ui.dp(44));ui.pad(e,12,0,12,0);{android.graphics.drawable.GradientDrawable fb=new android.graphics.drawable.GradientDrawable();fb.setColor(com.orcamobile.app.glass.KeplerUi.FIELD);fb.setStroke(Math.max(1,ui.dp(1)),com.orcamobile.app.glass.KeplerUi.FIELD_STROKE);fb.setCornerRadius(ui.dp(12));e.setBackground(fb);}e.setText(value);e.setSingleLine(true);e.setContentDescription(label);e.setInputType(InputType.TYPE_CLASS_TEXT|(password?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));page.addView(e,ui.lp(-1,-2));ui.gap(page,14);return e;}
    private void chooseCloudItem(CloudSelectionHelper.Kind kind){try {
        if(!editing||busy)return;NetworkPolicy.requireOnline(this);
        PrinterEndpoint.Type type=PrinterEndpoint.Type.values()[types.getSelectedItemPosition()];
        PrinterEndpoint.Type expected=kind==CloudSelectionHelper.Kind.OBICO_PRINTER?PrinterEndpoint.Type.OBICO:PrinterEndpoint.Type.THREE_D_PRINTER_OS;
        if(type!=expected)throw new IOException("Сначала выберите протокол "+(expected==PrinterEndpoint.Type.OBICO?"Obico":"3DPrinterOS")+".");
        String host=url.getText().toString(),credential=secret.getText().toString(),login=user.getText().toString(),disk=storage.getText().toString();
        PrinterEndpoint.Auth auth=PrinterEndpoint.Auth.values()[auths.getSelectedItemPosition()];
        PrinterEndpoint endpoint=new PrinterEndpoint(type,host,auth,login,credential,disk);
        EditText target=extras;
        CloudSelectionHelper.choose(this,endpoint,kind,(id,label,create)->{try {
            if(!editing||extras!=target||types.getSelectedItemPosition()!=type.ordinal()||!host.equals(url.getText().toString())||!credential.equals(secret.getText().toString())||auths.getSelectedItemPosition()!=auth.ordinal())throw new IOException("Подключение изменилось. Получите список снова.");
            JSONObject value=new JSONObject(target.getText().toString());
            if(kind==CloudSelectionHelper.Kind.OBICO_PRINTER)value.put("printer_id",id);
            else if(kind==CloudSelectionHelper.Kind.PRINTER_OS_PROJECT){if(create){value.remove("project_id");value.put("project_name",label);}else{value.remove("project_name");value.put("project_id",id);}}
            else {value.put("printer_type",id);value.put("gcode_type","OrcaSlicer");}
            target.setText(value.toString());MdUi.alert(this).setMessage("Выбрано: "+label+". Сохраните подключение.").setPositiveButton("Понятно",null).show();
        }catch(Exception e){MdUi.alert(this).setMessage(error(e)).setPositiveButton("Понятно",null).show();}});
    }catch(Exception e){MdUi.alert(this).setMessage(error(e)).setPositiveButton("Понятно",null).show();}}
    /** Host type chips (Figma Connection 5:594) drive the existing protocol selector, which stays the single source of truth. */
    private void hostChips(Spinner spinner,String[] labels){com.orcamobile.app.glass.KeplerUi k=new com.orcamobile.app.glass.KeplerUi(this);int at=page.indexOfChild(spinner);com.orcamobile.app.glass.FlowRow row=new com.orcamobile.app.glass.FlowRow(this,ui.dp(8),ui.dp(8));TextView[] chips=new TextView[labels.length];
        for(int i=0;i<labels.length;i++){final int index=i;chips[i]=k.chip(labels[i],i==spinner.getSelectedItemPosition(),()->{spinner.setSelection(index);for(int j=0;j<chips.length;j++)k.setChipActive(chips[j],j==index);});row.addView(chips[i],new android.view.ViewGroup.LayoutParams(-2,ui.dp(36)));}
        spinner.setVisibility(View.GONE);page.addView(row,at,new LinearLayout.LayoutParams(-1,-2));}
    private Spinner spinner(String label,String[] values,int selected){TextView caption=ui.text(label,13,false);caption.setTextColor(ui.muted);ui.pad(caption,4,0,4,6);page.addView(caption);Spinner s=new Spinner(this);{android.graphics.drawable.GradientDrawable sb=new android.graphics.drawable.GradientDrawable();sb.setColor(com.orcamobile.app.glass.KeplerUi.FIELD);sb.setStroke(Math.max(1,ui.dp(1)),com.orcamobile.app.glass.KeplerUi.FIELD_STROKE);sb.setCornerRadius(ui.dp(12));s.setBackground(sb);s.setPopupBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF2E323A));}ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,values){@Override public View getView(int p,View v,android.view.ViewGroup parent){TextView t=(TextView)super.getView(p,v,parent);t.setTextColor(ui.ink);return t;}@Override public View getDropDownView(int p,View v,android.view.ViewGroup parent){TextView t=(TextView)super.getDropDownView(p,v,parent);t.setTextColor(ui.ink);t.setBackgroundColor(ui.surface);return t;}};s.setAdapter(a);s.setSelection(selected);s.setPopupBackgroundDrawable(ui.shape(ui.surface,16));page.addView(s,ui.lp(-1,44));ui.gap(page,16);return s;}
    private void saveForm(){if(busy)return;try{String title=name.getText().toString().trim();if(title.isEmpty()||title.length()>120)throw new IOException("Укажите название длиной до 120 символов.");JSONObject json=new JSONObject(extras.getText().toString());Map<String,String> x=new LinkedHashMap<>();for(Iterator<String> it=json.keys();it.hasNext();){String key=it.next();Object value=json.get(key);if(!(value instanceof String)&&!(value instanceof Number)&&!(value instanceof Boolean))throw new IOException("Дополнительные параметры должны быть строками, числами или флагами.");x.put(key,String.valueOf(value));}
        PrinterEndpoint endpoint=new PrinterEndpoint(PrinterEndpoint.Type.values()[types.getSelectedItemPosition()],url.getText().toString(),PrinterEndpoint.Auth.values()[auths.getSelectedItemPosition()],user.getText().toString(),secret.getText().toString(),storage.getText().toString(),x);String oauth="";if(endpoint.type==PrinterEndpoint.Type.SIMPLYPRINT&&!pendingOauth.isEmpty()){JSONObject session=new JSONObject(pendingOauth);if(endpoint.secret().equals(session.optString("access_token"))&&!session.optString("refresh_token").isEmpty())oauth=pendingOauth;}
        NetworkStore.Connection value=new NetworkStore.Connection(draft==null?UUID.randomUUID().toString():draft.id,title,NetworkStore.currentPrinterIdentity(this),endpoint,oauth);
        busy=true;new Thread(()->{try{NetworkStore.save(this,value);runOnUiThread(()->{busy=false;notice="Подключение сохранено.";load();});}catch(Exception e){runOnUiThread(()->{busy=false;if(!isDestroyed()&&!isFinishing())MdUi.alert(this).setMessage(error(e)).setPositiveButton("Понятно",null).show();});}},"printer-connection-save").start();
    }catch(Exception e){MdUi.alert(this).setMessage(error(e)).setPositiveButton("Понятно",null).show();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==CLOUD_LOGIN&&result==RESULT_OK&&data!=null&&editing){String token=data.getStringExtra("secret");if(token!=null)secret.setText(token);String session=data.getStringExtra("oauth_session");pendingOauth=session==null?"":session;String login=data.getStringExtra("username");if(login!=null)user.setText(login);auths.setSelection(PrinterEndpoint.Auth.BEARER.ordinal());}}
    private void discover(){try{NetworkPolicy.requireOnline(this);}catch(IOException e){notice=error(e);render();return;}stopDiscovery();found.clear();notice="Ищем объявленные принтерами службы Wi-Fi…";render();LinearLayout results=ui.vertical();page.addView(results);discovery=new PrinterDiscovery(this,new PrinterDiscovery.Listener(){public void found(String title,String address,String service){runOnUiThread(()->{if(isDestroyed()||discovery==null||found.size()>=100)return;found.add(new String[]{title,address,service});results.addView(ui.row(title,address+" · "+service,"▣",()->form(null,address,service)));ui.gap(results,8);});}public void message(String text){runOnUiThread(()->{if(message!=null)message.setText(text);});}});discovery.start();page.addView(ui.button("Остановить поиск",false,()->{stopDiscovery();notice=found.isEmpty()?"Службы не найдены. Введите адрес вручную; телефон и принтер должны быть в одной сети.":"Поиск остановлен.";if(message!=null)message.setText(notice);}));}
    private void stopDiscovery(){if(discovery!=null){discovery.stop();discovery=null;}}
    private static String error(Throwable e){String s=e.getMessage();return s==null?"Не удалось выполнить операцию: "+e.getClass().getSimpleName():s;}
    static String label(PrinterEndpoint.Type t){switch(t.name()){case "OCTOPRINT":return "OctoPrint";case "MOONRAKER":return "Klipper / Moonraker";case "PRUSALINK":return "PrusaLink";case "PRUSACONNECT":return "Prusa Connect";case "DUET":return "Duet / RepRapFirmware";case "ULTIMAKER":return "UltiMaker";case "FLASHAIR":return "FlashAir";case "ASTROBOX":return "AstroBox";case "REPETIER":return "Repetier Server";case "MKS":return "MKS";case "ESP3D":return "ESP3D";case "CREALITYPRINT":return "Creality Print";case "FLASHFORGE":return "Flashforge";case "ELEGOOLINK":return "ElegooLink";case "OBICO":return "Obico";case "SIMPLYPRINT":return "SimplyPrint";case "THREE_D_PRINTER_OS":return "3DPrinterOS";case "SL1":return "Prusa SL1 / SL1S";default:return "Bambu — требуется Android SDK";}}
    private static PrinterEndpoint.Auth defaultAuth(PrinterEndpoint.Type t){switch(t){case OCTOPRINT:case PRUSALINK:case PRUSACONNECT:case REPETIER:case ASTROBOX:return PrinterEndpoint.Auth.API_KEY;case ULTIMAKER:return PrinterEndpoint.Auth.NONE;case DUET:return PrinterEndpoint.Auth.PASSWORD;case OBICO:case SIMPLYPRINT:case CREALITYPRINT:return PrinterEndpoint.Auth.BEARER;default:return PrinterEndpoint.Auth.NONE;}}
    private static String defaultHost(PrinterEndpoint.Type t){switch(t){case PRUSACONNECT:return "https://connect.prusa3d.com";case OBICO:return "https://app.obico.io";case SIMPLYPRINT:return "https://simplyprint.io";case THREE_D_PRINTER_OS:return "https://cloud.3dprinteros.com";default:return "";}}
}
