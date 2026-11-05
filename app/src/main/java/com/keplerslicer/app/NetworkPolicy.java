package com.keplerslicer.app;
import android.content.Context;
import java.io.IOException;
import java.net.URI;
/** Offline blocks every transport; stealth permits LAN while refusing cloud/public HTTP. */
final class NetworkPolicy {
    static boolean offline(Context c){return c.getSharedPreferences("app_preferences",0).getBoolean("network_offline",false);}
    static void requireOnline(Context c)throws IOException{if(offline(c))throw new IOException(L.t("Включён режим без сети. Измените режим на экране подключения."));}
    static void requireCloud(Context c)throws IOException{requireOnline(c);if(c.getSharedPreferences("app_preferences",0).getBoolean("stealth_mode",false))throw new IOException(L.t("Скрытый режим отключает облачные функции. Локальные принтеры остаются доступны."));}
    static void requireEndpoint(Context c,PrinterEndpoint endpoint)throws IOException{requireOnline(c);if((endpoint.type==PrinterEndpoint.Type.BAMBU||endpoint.type==PrinterEndpoint.Type.BAMBU_CLOUD)&&!c.getSharedPreferences("app_preferences",0).getBoolean("bambu_network_enabled",true))throw new IOException(L.t("Сеть Bambu Lab выключена в настройках «Общие»."));if(CloudPrinterHosts.supports(endpoint.type)||endpoint.type==PrinterEndpoint.Type.BAMBU_CLOUD)requireCloud(c);else checkUri(c,endpoint.baseUri);}
    static void install(Context context){final Context app=context.getApplicationContext();NetworkHttp.setRequestPolicy(uri->checkUri(app,uri));}
    static void checkUri(Context c,URI uri)throws IOException{requireOnline(c);if(c.getSharedPreferences("app_preferences",0).getBoolean("stealth_mode",false)){
        String host=uri.getHost();if(!OnlineRules.lanHost(host))throw new IOException(L.t("Скрытый режим блокирует облачный адрес. Используйте локальный адрес принтера."));
        if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())throw new IOException(L.t("Проверка локального адреса должна выполняться в фоне."));
        if(!OnlineRules.privateAddresses(java.net.InetAddress.getAllByName(host)))throw new IOException(L.t("Локальное имя разрешается в публичный адрес. Скрытый режим блокирует соединение."));
    }}
}
