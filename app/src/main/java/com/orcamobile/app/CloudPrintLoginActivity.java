package com.orcamobile.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;
import java.net.URI;
import org.json.JSONObject;

/** User-initiated cloud sign-in in the system browser. */
public final class CloudPrintLoginActivity extends Activity {
    private PrinterEndpoint.Type type;private PrinterEndpoint endpoint;private MdUi ui;private LinearLayout page;
    private volatile NetworkHttp.Cancellation cancellation;private volatile SimplyPrintOAuth oauth;private volatile boolean alive=true,busy;private String notice="";
    @Override public void onCreate(Bundle state){MdUi.prepare(this);super.onCreate(state);try{type=PrinterEndpoint.Type.valueOf(getIntent().getStringExtra("type"));if(type!=PrinterEndpoint.Type.SIMPLYPRINT&&type!=PrinterEndpoint.Type.THREE_D_PRINTER_OS)throw new IllegalArgumentException();if(type==PrinterEndpoint.Type.THREE_D_PRINTER_OS){String url=getIntent().getStringExtra("base_url");endpoint=new PrinterEndpoint(type,url,PrinterEndpoint.Auth.NONE,"","",null);if(!"https".equalsIgnoreCase(endpoint.baseUri.getScheme()))throw new IllegalArgumentException("Для облачного входа требуется HTTPS.");}}catch(Exception e){notice=e.getMessage()==null?"Не указан поддерживаемый облачный сервис.":e.getMessage();type=null;}render();}
    private void render(){if(!alive||isFinishing())return;ui=new MdUi(this);page=ui.page(this,type==PrinterEndpoint.Type.SIMPLYPRINT?"Вход в SimplyPrint":type==PrinterEndpoint.Type.THREE_D_PRINTER_OS?"Вход в 3DPrinterOS":"Вход",this::finish);
        page.addView(ui.row("Безопасный вход","Откроется официальный вход в браузере. Подтвердите доступ к аккаунту и вернитесь в приложение.","↗",null));ui.gap(page,16);
        if(!notice.isEmpty()){TextView msg=ui.text(notice,14,false);msg.setTextColor(ui.muted);page.addView(msg);ui.gap(page,16);}
        if(type!=null&&!busy)page.addView(ui.button("Открыть вход в браузере",true,this::start));
        if(busy)page.addView(ui.button("Отменить",false,()->{NetworkHttp.Cancellation c=cancellation;if(c!=null)c.cancel();SimplyPrintOAuth a=oauth;if(a!=null)try{a.close();}catch(IOException ignored){}finish();}));
    }
    private void start(){if(busy)return;try{NetworkPolicy.requireOnline(this);}catch(IOException e){notice=e.getMessage();render();return;}busy=true;notice="Подготавливаем вход…";render();NetworkHttp.Cancellation cancel=new NetworkHttp.Cancellation();cancellation=cancel;
        new Thread(()->{try{if(type==PrinterEndpoint.Type.SIMPLYPRINT)loginSimply(cancel);else loginPrinterOs(cancel);}catch(Exception e){finishError(e);}finally{cancellation=null;oauth=null;}},"cloud-printer-login").start();}
    private void openBrowser(URI uri)throws IOException{if(!alive)throw new IOException("Вход отменён.");runOnUiThread(()->{if(!alive)return;try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(uri.toASCIIString())));notice="Завершите вход в браузере. Ожидаем подтверждение…";render();}catch(Exception e){NetworkHttp.Cancellation c=cancellation;if(c!=null)c.cancel();notice="Не удалось открыть браузер.";busy=false;render();}});}
    private void loginSimply(NetworkHttp.Cancellation cancel)throws IOException{try(SimplyPrintOAuth a=new SimplyPrintOAuth()){oauth=a;openBrowser(a.authorizationUri());JSONObject result=a.awaitAndExchange(cancel);cancel.check();if(!alive)return;try{result.put("expires_at",System.currentTimeMillis()/1000+result.optLong("expires_in",3600));}catch(org.json.JSONException e){throw new IOException("Неверный ответ входа.",e);}cancel.check();succeed(result.optString("access_token"),"",result.toString());}}
    private void loginPrinterOs(NetworkHttp.Cancellation cancel)throws IOException{ThreeDPrinterOSLogin.Pending pending=ThreeDPrinterOSLogin.generate(endpoint,cancel);openBrowser(pending.browserUri);ThreeDPrinterOSLogin.Session session=ThreeDPrinterOSLogin.poll(endpoint,pending.token,cancel);cancel.check();succeed(session.session,session.email,null);}
    private void succeed(String secret,String username,String oauthSession){runOnUiThread(()->{if(!alive||isFinishing())return;Intent result=new Intent().putExtra("secret",secret);if(username!=null&&!username.isEmpty())result.putExtra("username",username);if(oauthSession!=null)result.putExtra("oauth_session",oauthSession);setResult(RESULT_OK,result);finish();});}
    private void finishError(Exception e){runOnUiThread(()->{if(!alive||isFinishing())return;busy=false;notice="Вход не завершён: "+(e.getMessage()==null?"ошибка соединения":e.getMessage());render();});}
    @Override protected void onDestroy(){alive=false;NetworkHttp.Cancellation c=cancellation;if(c!=null)c.cancel();SimplyPrintOAuth a=oauth;if(a!=null)try{a.close();}catch(IOException ignored){}super.onDestroy();}
}
