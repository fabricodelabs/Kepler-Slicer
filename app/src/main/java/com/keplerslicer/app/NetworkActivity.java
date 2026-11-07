package com.keplerslicer.app;

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
    private void render(){if(isDestroyed()||isFinishing())return;editing=false;MdUi.prepare(this);ui=new MdUi(this);page=ui.page(this,KeplerApplication.str(R.string.l10n_net_title),this::finish);ui.navigation(this,3);
        message=ui.text(notice,13,false);message.setTextColor(ui.muted);if(!notice.isEmpty()){page.addView(message);ui.gap(page,16);}
        row(NetworkPolicy.offline(this)?KeplerApplication.str(R.string.l10n_net_offline_mode):KeplerApplication.str(R.string.l10n_net_lan_cloud),NetworkPolicy.offline(this)?KeplerApplication.str(R.string.l10n_net_local_note):KeplerApplication.str(R.string.l10n_net_uses_network),"⌁",this::mode);
        row(KeplerApplication.str(R.string.l10n_net_find_wifi),KeplerApplication.str(R.string.l10n_net_discover_note),"⌕",busy?null:this::discover);
        row(KeplerApplication.str(R.string.l10n_net_add),KeplerApplication.str(R.string.l10n_net_all_types),"＋",busy?null:()->form(null,null));
        if(state==null)return;
        ui.gap(page,12);page.addView(ui.text(KeplerApplication.str(R.string.l10n_net_saved_printers),18,true));ui.gap(page,12);
        if(state.connections.isEmpty()){TextView empty=ui.text(KeplerApplication.str(R.string.l10n_net_add_hint),14,false);empty.setTextColor(ui.muted);page.addView(empty);}
        for(NetworkStore.Connection c:state.connections){String marker=c.id.equals(state.active)?KeplerApplication.str(R.string.l10n_net_selected_suffix):"";row(c.name+marker,label(c.endpoint.type)+" · "+c.endpoint.baseUri.toASCIIString(),"▣",busy?null:()->connectionMenu(c));}
        ui.gap(page,16);TextView note=ui.text(KeplerApplication.str(R.string.l10n_net_manual_note),12,false);note.setTextColor(ui.muted);page.addView(note);
    }
    private void mode(){MdUi.alert(this).setTitle(KeplerApplication.str(R.string.l10n_net_mode)).setSingleChoiceItems(new String[]{KeplerApplication.str(R.string.l10n_net_lan_cloud_services),KeplerApplication.str(R.string.l10n_net_offline)},NetworkPolicy.offline(this)?1:0,(d,n)->{if(cancellation!=null)cancellation.cancel();stopDiscovery();getSharedPreferences("app_preferences",0).edit().putBoolean("network_offline",n==1).apply();d.dismiss();render();}).setNegativeButton(KeplerApplication.str(R.string.l10n_cancel),null).show();}
    private void connectionMenu(NetworkStore.Connection c){List<String> actions=new ArrayList<>(Arrays.asList(KeplerApplication.str(R.string.l10n_choose),KeplerApplication.str(R.string.l10n_net_test),KeplerApplication.str(R.string.l10n_net_refresh_status),KeplerApplication.str(R.string.l10n_net_send_current),KeplerApplication.str(R.string.l10n_net_open_web),KeplerApplication.str(R.string.l10n_edit)));if(c.endpoint.type==PrinterEndpoint.Type.ULTIMAKER){actions.add(KeplerApplication.str(R.string.l10n_net_request_pairing));actions.add(KeplerApplication.str(R.string.l10n_net_check_pairing));actions.add(KeplerApplication.str(R.string.l10n_net_send_start_print));}if(c.endpoint.type==PrinterEndpoint.Type.OCTOPRINT||c.endpoint.type==PrinterEndpoint.Type.MOONRAKER||c.endpoint.type==PrinterEndpoint.Type.BAMBU||c.endpoint.type==PrinterEndpoint.Type.DUET)actions.add(KeplerApplication.str(R.string.l10n_net_start_sent));actions.add(KeplerApplication.str(R.string.l10n_net_delete));
        MdUi.alert(this).setTitle(c.name).setItems(actions.toArray(new String[0]),(d,index)->{String action=actions.get(index);if(action.equals(KeplerApplication.str(R.string.l10n_choose))){localTask(KeplerApplication.str(R.string.l10n_net_saving_choice),cancel->{NetworkStore.select(this,c.id);return KeplerApplication.str(R.string.l10n_net_chosen_done);});}else if(action.equals(KeplerApplication.str(R.string.l10n_net_test))){task(KeplerApplication.str(R.string.l10n_net_testing),cancel->{PrinterHostClient.HostInfo info=new PrinterHostClient(fresh(c,cancel)).test(cancel);return KeplerApplication.str(R.string.l10n_net_ok)+info.name+KeplerApplication.str(R.string.l10n_net_state_nl)+info.state;});}else if(action.equals(KeplerApplication.str(R.string.l10n_net_refresh_status))){task(KeplerApplication.str(R.string.l10n_net_getting_state),cancel->{PrinterHostClient.Status info=new PrinterHostClient(fresh(c,cancel)).status(cancel);return KeplerApplication.str(R.string.l10n_net_state)+info.state+(info.state.equals("unknown")?KeplerApplication.str(R.string.l10n_net_no_state):"");});}else if(action.equals(KeplerApplication.str(R.string.l10n_net_send_current))){confirmUpload(c,false);}else if(action.equals(KeplerApplication.str(R.string.l10n_net_open_web))){openWeb(c.endpoint.baseUri.toASCIIString());}else if(action.equals(KeplerApplication.str(R.string.l10n_edit))){form(c,null);}else if(action.equals(KeplerApplication.str(R.string.l10n_net_request_pairing))){task(KeplerApplication.str(R.string.l10n_net_pairing),cancel->{String[] credentials=LegacyPrinterHosts.requestUltiMakerPairing(c.endpoint,cancel);PrinterEndpoint e=c.endpoint;PrinterEndpoint paired=new PrinterEndpoint(e.type,e.baseUri.toASCIIString(),PrinterEndpoint.Auth.DIGEST,credentials[0],credentials[1],e.storage,e.extras);NetworkStore.save(this,new NetworkStore.Connection(c.id,c.name,c.printerIdentity,paired));return KeplerApplication.str(R.string.l10n_net_pairing_sent);});}else if(action.equals(KeplerApplication.str(R.string.l10n_net_check_pairing))){task(KeplerApplication.str(R.string.l10n_net_checking_pairing),cancel->LegacyPrinterHosts.ultiMakerPairingStatus(c.endpoint,cancel));}else if(action.equals(KeplerApplication.str(R.string.l10n_net_send_start_print))){confirmUpload(c,true);}else if(action.equals(KeplerApplication.str(R.string.l10n_net_start_sent))){confirmStart(c);}else{MdUi.alert(this).setTitle(KeplerApplication.str(R.string.l10n_net_delete_q)).setMessage(KeplerApplication.str(R.string.l10n_net_delete_note)).setPositiveButton(KeplerApplication.str(R.string.l10n_delete),(a,b)->localTask(KeplerApplication.str(R.string.l10n_net_deleting),cancel->{NetworkStore.remove(this,c.id);return KeplerApplication.str(R.string.l10n_net_deleted);})).setNegativeButton(KeplerApplication.str(R.string.l10n_cancel),null).show();}}).setNegativeButton(KeplerApplication.str(R.string.l10n_close),null).show();
    }
    private void openWeb(String value){try{NetworkPolicy.requireOnline(this);java.net.URI uri=java.net.URI.create(value);if(!Arrays.asList("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IOException(KeplerApplication.str(R.string.l10n_net_bad_link));new Thread(()->{try{NetworkPolicy.checkUri(this,uri);runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(value)));}catch(Exception e){notice=error(e);render();}});}catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){notice=error(e);render();}});}},"printer-web-policy").start();}catch(Exception e){notice=error(e);render();}}
    private interface Work{String run(NetworkHttp.Cancellation cancel)throws Exception;}
    private void localTask(String text,Work work){execute(text,work,false);}
    private void task(String text,Work work){execute(text,work,true);}
    private void execute(String text,Work work,boolean network){if(busy)return;try{if(network)NetworkPolicy.requireOnline(this);}catch(IOException e){notice=error(e);render();return;}busy=true;notice=text;render();
        final long[] lastUpdate={0};NetworkHttp.Cancellation token=new NetworkHttp.Cancellation((sent,total)->{long now=System.currentTimeMillis();if(now-lastUpdate[0]<250)return;lastUpdate[0]=now;runOnUiThread(()->{if(!isDestroyed()&&busy&&message!=null)message.setText(total>0?KeplerApplication.str(R.string.l10n_net_sent)+sent+" / "+total+KeplerApplication.str(R.string.l10n_bytes):KeplerApplication.str(R.string.l10n_net_sent)+sent+KeplerApplication.str(R.string.l10n_bytes));});});cancellation=token;
        page.addView(ui.button(KeplerApplication.str(R.string.l10n_net_cancel_op),false,token::cancel));new Thread(()->{String result;try{result=work.run(token);}catch(Throwable e){result=token.isCancelled()?KeplerApplication.str(R.string.l10n_net_cancelled):error(e);}final String end=result;runOnUiThread(()->{if(isDestroyed())return;busy=false;cancellation=null;notice=end;load();});},"printer-network-operation").start();
    }
    /** Refresh before a read or upload; never replay an upload after an ambiguous failure. */
    private PrinterEndpoint fresh(NetworkStore.Connection supplied,NetworkHttp.Cancellation cancel)throws Exception {
        NetworkPolicy.requireEndpoint(this,supplied.endpoint);
        if(supplied.endpoint.type==PrinterEndpoint.Type.BAMBU_CLOUD)return NetworkStore.fresh(this,supplied,cancel);
        if(supplied.endpoint.type!=PrinterEndpoint.Type.SIMPLYPRINT)return supplied.endpoint;
        synchronized(NetworkStore.class){
            NetworkStore.Connection current=null;for(NetworkStore.Connection item:NetworkStore.load(this).connections)if(item.id.equals(supplied.id)){current=item;break;}
            if(current==null||current.endpoint.type!=PrinterEndpoint.Type.SIMPLYPRINT)throw new IOException(KeplerApplication.str(R.string.l10n_net_changed_reopen));
            PrinterEndpoint endpoint=current.endpoint;JSONObject session=null;
            if(!current.oauthSession.isEmpty()){session=new JSONObject(current.oauthSession);if(!endpoint.secret().equals(session.optString("access_token")))throw new IOException(KeplerApplication.str(R.string.l10n_net_sp_broken));}
            boolean expired=session!=null&&session.optLong("expires_at",Long.MAX_VALUE)<=System.currentTimeMillis()/1000+60;
            if(!expired){int status=tokenInfo(endpoint,cancel);if(status!=401)return endpoint;}
            if(session==null||session.optString("refresh_token").isEmpty())throw new IOException(KeplerApplication.str(R.string.l10n_net_sp_expired));
            JSONObject updated;
            cancel.check();
            try {updated=SimplyPrintOAuth.refresh(session.optString("refresh_token"),new NetworkHttp.Cancellation());}
            catch(InterruptedIOException cancelled){throw cancelled;}
            catch(IOException error){throw new IOException(KeplerApplication.str(R.string.l10n_net_sp_refresh_failed),error);}
            if(updated.optString("refresh_token").isEmpty())updated.put("refresh_token",session.optString("refresh_token"));
            updated.put("expires_at",System.currentTimeMillis()/1000+updated.optLong("expires_in",3600));
            try{endpoint=NetworkStore.updateOAuth(this,current.id,current.endpoint.secret(),updated.toString());}
            catch(IOException error){throw new IOException(KeplerApplication.str(R.string.l10n_net_sp_save_failed),error);}
            if(tokenInfo(endpoint,cancel)==401)throw new IOException(KeplerApplication.str(R.string.l10n_net_sp_refresh_rejected));
            return endpoint;
        }
    }
    private static int tokenInfo(PrinterEndpoint endpoint,NetworkHttp.Cancellation cancel)throws IOException {
        NetworkHttp.Response response=NetworkHttp.request("GET",java.net.URI.create("https://api.simplyprint.io/oauth2/TokenInfo"),Collections.singletonMap("Authorization","Bearer "+endpoint.secret()),null,cancel);
        if(response.status!=401)response.requireSuccess();return response.status;
    }
    private static final class Result {File file;String name,details,fingerprint;}
    private Result currentResult()throws IOException{Map<String,?> record=getSharedPreferences("last_slice_result",0).getAll();String fingerprint=(String)record.get("job_fingerprint"),path=(String)record.get("path");if(fingerprint==null||path==null||!fingerprint.equals(ProcessOverridesStore.currentResultFingerprint(this)))throw new IOException(KeplerApplication.str(R.string.l10n_net_slice_first));File root=new File(getFilesDir(),"slicer-engine").getCanonicalFile(),file=new File(path).getCanonicalFile();if(!file.getPath().startsWith(root.getPath()+File.separator)||!file.getName().equals("result.gcode")||!file.isFile()||file.length()==0)throw new IOException(KeplerApplication.str(R.string.l10n_net_result_file_unavailable));Result result=new Result();result.file=file;result.fingerprint=fingerprint;result.details=String.valueOf(record.get("details"));String model=String.valueOf(record.get("model"));result.name=model.replaceAll("(?i)\\.(stl|obj|3mf)$","").replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_")+".gcode";return result;}
    private NetworkStore.Connection currentConnection(String id)throws IOException {for(NetworkStore.Connection item:NetworkStore.load(this).connections)if(item.id.equals(id))return item;throw new IOException(KeplerApplication.str(R.string.l10n_net_deleted));}
    private static boolean sameEndpoint(NetworkStore.Connection a,NetworkStore.Connection b){return a.id.equals(b.id)&&a.endpoint.type==b.endpoint.type&&a.endpoint.baseUri.equals(b.endpoint.baseUri);}
    private static boolean validRemotePath(String path){if(path==null||path.isEmpty()||path.startsWith("/")||path.endsWith("/"))return false;try{for(String part:path.split("/",-1))PrinterEndpoint.encodeComponent(part);return true;}catch(IllegalArgumentException e){return false;}}
    private void confirmUpload(NetworkStore.Connection c,boolean start){try{
        NetworkPolicy.requireOnline(this);if(!c.printerIdentity.isEmpty()&&!c.printerIdentity.equals(NetworkStore.currentPrinterIdentity(this)))throw new IOException(KeplerApplication.str(R.string.l10n_net_other_model));
        if(c.endpoint.type==PrinterEndpoint.Type.SL1)throw new IOException(KeplerApplication.str(R.string.l10n_net_sla));Result result=currentResult();
        String warning=start?KeplerApplication.str(R.string.l10n_net_ultimaker_warning):KeplerApplication.str(R.string.l10n_net_upload_only);
        if(c.endpoint.type==PrinterEndpoint.Type.ESP3D)warning+=KeplerApplication.str(R.string.l10n_net_esp3d_as)+LegacyHostFiles.short83(result.name)+KeplerApplication.str(R.string.l10n_net_esp3d_overwrite);
        MdUi.alert(this).setTitle(start?KeplerApplication.str(R.string.l10n_net_begin_print_q):KeplerApplication.str(R.string.l10n_net_send_gcode_q)).setMessage(c.name+"\n"+c.endpoint.baseUri+"\n\n"+result.details+KeplerApplication.str(R.string.l10n_net_size)+result.file.length()+KeplerApplication.str(R.string.l10n_net_bytes_nl)+warning+KeplerApplication.str(R.string.l10n_net_experimental))
            .setPositiveButton(start?KeplerApplication.str(R.string.l10n_net_send_start):KeplerApplication.str(R.string.l10n_net_send_file),(d,w)->task(KeplerApplication.str(R.string.l10n_net_sending),cancel->{
                Result exact=currentResult();if(!exact.file.equals(result.file)||!exact.fingerprint.equals(result.fingerprint))throw new IOException(KeplerApplication.str(R.string.l10n_net_result_changed));
                NetworkStore.Connection current=currentConnection(c.id);if(!sameEndpoint(c,current))throw new IOException(KeplerApplication.str(R.string.l10n_net_address_changed_retry));
                NetworkSecrets.remove(this,UPLOAD_RECEIPT);PrinterEndpoint e=fresh(current,cancel);
                if(start){LegacyPrinterHosts.uploadAndStart(e,result.file,result.name,cancel);return KeplerApplication.str(R.string.l10n_net_sent_started);}
                if(e.type==PrinterEndpoint.Type.BAMBU&&!getSharedPreferences("app_preferences",0).getBoolean("allow_abnormal_storage",false)){JSONObject report=BambuLan.report(e,cancel);if(report.has("sdcard")&&!report.optBoolean("sdcard",true))throw new IOException(KeplerApplication.str(R.string.kv3_bambu_storage_abnormal));}
                PrinterHostClient.UploadResult sent;try{sent=new PrinterHostClient(e).upload(result.file,result.name,cancel);}catch(IOException error){if(e.type==PrinterEndpoint.Type.SIMPLYPRINT&&"Cloud host HTTP 401".equals(error.getMessage()))throw new IOException(KeplerApplication.str(R.string.l10n_net_sp_rejected),error);throw error;}
                cancel.check();String suffix=sent.remotePath==null||sent.remotePath.isEmpty()?"":KeplerApplication.str(R.string.l10n_net_file_nl)+sent.remotePath;
                if((e.type==PrinterEndpoint.Type.OCTOPRINT||e.type==PrinterEndpoint.Type.MOONRAKER||e.type==PrinterEndpoint.Type.BAMBU||e.type==PrinterEndpoint.Type.DUET)&&validRemotePath(sent.remotePath)){
                    try{Result latest=currentResult();NetworkStore.Connection after=currentConnection(c.id);
                        if(latest.file.equals(result.file)&&latest.fingerprint.equals(result.fingerprint)&&sameEndpoint(current,after)){
                            JSONObject receipt=new JSONObject().put("connection",c.id).put("type",e.type.name()).put("url",e.baseUri.toASCIIString()).put("path",result.file.getCanonicalPath()).put("fingerprint",result.fingerprint).put("remote",sent.remotePath);
                            try{NetworkSecrets.put(this,UPLOAD_RECEIPT,receipt.toString());}catch(IOException error){suffix+=KeplerApplication.str(R.string.l10n_net_receipt_failed);}
                        }else suffix+=KeplerApplication.str(R.string.l10n_net_changed_no_start);
                    }catch(Exception changed){suffix+=KeplerApplication.str(R.string.l10n_net_check_failed);}
                }else if(e.type==PrinterEndpoint.Type.OCTOPRINT||e.type==PrinterEndpoint.Type.MOONRAKER||e.type==PrinterEndpoint.Type.BAMBU||e.type==PrinterEndpoint.Type.DUET)suffix+=KeplerApplication.str(R.string.l10n_net_no_path);
                if(sent.actionUrl!=null&&!sent.actionUrl.isEmpty())runOnUiThread(()->{if(!isDestroyed())MdUi.alert(this).setTitle(KeplerApplication.str(R.string.l10n_net_finish_import)).setMessage(KeplerApplication.str(R.string.l10n_net_sp_open_import)).setPositiveButton(KeplerApplication.str(R.string.l10n_open),(a,b)->openWeb(sent.actionUrl)).setNegativeButton(KeplerApplication.str(R.string.l10n_later),null).show();});
                return sent.message+suffix;
            })).setNegativeButton(KeplerApplication.str(R.string.l10n_cancel),null).show();
    }catch(Exception e){notice=error(e);render();}}
    private String validatedReceipt(NetworkStore.Connection connection,Result result,JSONObject receipt)throws IOException {
        if(!connection.id.equals(receipt.optString("connection"))||!connection.endpoint.type.name().equals(receipt.optString("type"))||!connection.endpoint.baseUri.toASCIIString().equals(receipt.optString("url"))||!result.file.getCanonicalPath().equals(receipt.optString("path"))||!result.fingerprint.equals(receipt.optString("fingerprint")))throw new IOException(KeplerApplication.str(R.string.l10n_net_changed_resend));
        String remote=receipt.optString("remote","");if(!validRemotePath(remote))throw new IOException(KeplerApplication.str(R.string.l10n_net_no_path_resend));return remote;
    }
    private void confirmStart(NetworkStore.Connection connection){try{
        NetworkPolicy.requireOnline(this);NetworkStore.Connection actual=currentConnection(connection.id);if(!sameEndpoint(connection,actual))throw new IOException(KeplerApplication.str(R.string.l10n_net_address_changed));
        String raw=NetworkSecrets.get(this,UPLOAD_RECEIPT);if(raw.isEmpty())throw new IOException(KeplerApplication.str(R.string.l10n_net_send_first));Result result=currentResult();String remote=validatedReceipt(actual,result,new JSONObject(raw));
        MdUi.alert(this).setTitle(KeplerApplication.str(R.string.l10n_net_start_print_q)).setMessage(actual.name+"\n"+remote+KeplerApplication.str(R.string.l10n_net_start_note))
            .setPositiveButton(KeplerApplication.str(R.string.l10n_net_start_print),(d,w)->task(KeplerApplication.str(R.string.l10n_net_sending_start),cancel->{
                Result latest=currentResult();NetworkStore.Connection again=currentConnection(connection.id);if(!sameEndpoint(actual,again))throw new IOException(KeplerApplication.str(R.string.l10n_net_address_changed));String receipt=NetworkSecrets.get(this,UPLOAD_RECEIPT);if(!latest.file.equals(result.file)||!latest.fingerprint.equals(result.fingerprint)||!raw.equals(receipt)||receipt.isEmpty()||!remote.equals(validatedReceipt(again,latest,new JSONObject(receipt))))throw new IOException(KeplerApplication.str(R.string.l10n_net_receipt_changed));
                if(again.endpoint.type==PrinterEndpoint.Type.BAMBU&&!getSharedPreferences("app_preferences",0).getBoolean("skip_ams_check",false)){String problem=amsMismatch();if(problem!=null)throw new IOException(problem);}
                try{new PrinterHostClient(fresh(again,cancel)).startUploaded(remote,cancel);}catch(IOException error){throw new IOException(KeplerApplication.str(R.string.l10n_net_start_unconfirmed),error);}return KeplerApplication.str(R.string.l10n_net_start_ok);
            })).setNegativeButton(KeplerApplication.str(R.string.l10n_cancel),null).show();
    }catch(Exception error){notice=error(error);render();}}
    // ---- Bambu Lab cloud: the user's own account; the token and account id fill the connection form ----
    /** AMS check before a Bambu start: every filled material slot needs a loaded tray of the same material type. */
    private String amsMismatch(){
        AmsTrays.Tray[] trays=AmsTrays.load(this);if(trays==null)return null;MaterialSlots.Slot[] slots=MaterialSlots.load(this);
        for(int i=0;i<slots.length&&i<trays.length;i++){if(!slots[i].filled())continue;String want=materialType(slots[i].name);
            if(!trays[i].loaded)return KeplerApplication.str(R.string.kv3_ams_slot_empty,i+1);
            if(!want.isEmpty()&&!trays[i].type.toUpperCase(Locale.ROOT).startsWith(want))return KeplerApplication.str(R.string.kv3_ams_slot_mismatch,i+1,trays[i].type,want);}
        return null;
    }
    static String materialType(String profile){String u=profile==null?"":profile.toUpperCase(Locale.ROOT);for(String t:new String[]{"PETG","PCTG","PLA","ABS","ASA","TPU","PA","PC","PVA","HIPS","PET"})if(u.matches(".*(^|[^A-Z])"+t+"([^A-Z]|$).*"))return t;return "";}
    private void bambuCloudLogin(){
        try{NetworkPolicy.requireCloud(this);}catch(IOException e){MdUi.alert(this).setMessage(error(e)).setPositiveButton(R.string.kv3_ok,null).show();return;}
        final String region=OnlinePreferences.region(this);
        android.widget.LinearLayout form=new android.widget.LinearLayout(this);form.setOrientation(android.widget.LinearLayout.VERTICAL);form.setPadding(ui.dp(20),ui.dp(4),ui.dp(20),0);
        TextView hint=ui.text(getString(R.string.kv3_bambu_cloud_hint,region),13,false);hint.setTextColor(ui.muted);form.addView(hint);
        final EditText mail=new EditText(this);mail.setHint(R.string.kv3_bambu_cloud_email);mail.setSingleLine(true);mail.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);mail.setTextColor(ui.ink);form.addView(mail);
        final EditText pass=new EditText(this);pass.setHint(R.string.kv3_bambu_cloud_password);pass.setSingleLine(true);pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);pass.setTextColor(ui.ink);form.addView(pass);
        MdUi.alert(this).setTitle(R.string.kv3_bambu_cloud).setView(form).setNegativeButton(R.string.l10n_cancel,null)
            .setPositiveButton(R.string.kv3_bambu_cloud_sign_in,(d,w)->bambuCloudStep(region,mail.getText().toString().trim(),pass.getText().toString(),"")).show();
    }
    /** The cloud button on a LAN form: pick the service, switch the connection type, then sign in. */
    private void chooseCloudService(){
        final PrinterEndpoint.Type[] services={PrinterEndpoint.Type.BAMBU_CLOUD,PrinterEndpoint.Type.SIMPLYPRINT,PrinterEndpoint.Type.THREE_D_PRINTER_OS};
        String[] labels={L.t("Облако Bambu Lab"),"SimplyPrint","3DPrinterOS"};
        com.keplerslicer.app.glass.GlassBottomSheet.choose(this,getWindow().getDecorView(),false,L.t("Вход в облако"),labels,-1,which->{
            types.setSelection(services[which].ordinal());
            if(services[which]==PrinterEndpoint.Type.BAMBU_CLOUD){bambuCloudLogin();return;}
            try{NetworkPolicy.requireCloud(this);startActivityForResult(new Intent(this,CloudPrintLoginActivity.class).putExtra("type",services[which].name()).putExtra("base_url",url.getText().toString()),CLOUD_LOGIN);}catch(Exception e){MdUi.alert(this).setMessage(error(e)).setPositiveButton(R.string.kv3_ok,null).show();}
        });
    }
    /** Code window: a one-time code from the e-mail (or the authenticator app for two-factor accounts). */
    private void bambuCodeWindow(String region,String email,String tfaKey,String notice){
        if(isDestroyed())return;
        boolean tfa=tfaKey!=null;
        android.widget.LinearLayout box=new android.widget.LinearLayout(this);box.setOrientation(android.widget.LinearLayout.VERTICAL);box.setPadding(ui.dp(20),ui.dp(4),ui.dp(20),0);
        TextView info=ui.text((notice==null||notice.isEmpty()?"":notice+"\n")+(tfa?L.t("Откройте приложение-аутентификатор и введите 6-значный код для аккаунта Bambu Lab."):getString(R.string.kv3_bambu_cloud_code_sent,email)),13,false);info.setTextColor(ui.muted);box.addView(info);
        final EditText field=new EditText(this);field.setInputType(InputType.TYPE_CLASS_NUMBER);field.setTextColor(ui.ink);field.setHint(tfa?L.t("Код из приложения"):getString(R.string.kv3_bambu_cloud_code));
        field.setTextSize(28);field.setGravity(android.view.Gravity.CENTER);field.setLetterSpacing(0.3f);field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});
        android.widget.LinearLayout.LayoutParams fp=new android.widget.LinearLayout.LayoutParams(-1,-2);fp.topMargin=ui.dp(12);box.addView(field,fp);
        android.app.AlertDialog.Builder b=MdUi.alert(this).setTitle(tfa?L.t("Двухфакторный вход"):L.t("Код подтверждения")).setView(box).setNegativeButton(R.string.l10n_cancel,null)
            .setPositiveButton(R.string.kv3_bambu_cloud_sign_in,(d,w)->{String code=field.getText().toString().trim();if(tfa)bambuTfaStep(region,email,tfaKey,code);else bambuCloudStep(region,email,"",code);});
        if(!tfa)b.setNeutralButton(L.t("Новый код"),(d,w)->bambuCloudStep(region,email,"",""));
        else b.setNeutralButton(L.t("Код на почту"),(d,w)->bambuCloudStep(region,email,"",""));
        android.app.AlertDialog dialog=b.show();
        field.requestFocus();if(dialog.getWindow()!=null)dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }
    private void bambuTfaStep(String region,String email,String tfaKey,String code){
        android.widget.Toast.makeText(this,R.string.pref_working,android.widget.Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{BambuCloud.Login login=BambuCloud.signInTfa(region,tfaKey,code,new NetworkHttp.Cancellation());runOnUiThread(()->bambuSignedIn(region,login));}
            catch(Exception e){runOnUiThread(()->{if(!isDestroyed())MdUi.alert(this).setTitle(R.string.kv3_bambu_cloud).setMessage(error(e)).setPositiveButton(R.string.kv3_ok,null).show();});}
        },"bambu-tfa").start();
    }
    private void bambuDevices(String region,BambuCloud.Login login,java.util.List<BambuCloud.Device> devices){if(isDestroyed())return;
                    if(devices.isEmpty()){MdUi.alert(this).setTitle(R.string.kv3_bambu_cloud).setMessage(R.string.kv3_bambu_cloud_no_printers).setPositiveButton(R.string.kv3_ok,null).show();return;}
                    String[] labels=new String[devices.size()];for(int i=0;i<labels.length;i++){BambuCloud.Device dv=devices.get(i);labels[i]=dv.name+(dv.model.isEmpty()?"":" · "+dv.model)+" · "+getString(dv.online?R.string.kv3_bambu_cloud_online:R.string.kv3_bambu_cloud_offline);}
                    com.keplerslicer.app.glass.GlassBottomSheet.choose(this,getWindow().getDecorView(),false,getString(R.string.kv3_bambu_cloud_choose),labels,-1,which->{
                        BambuCloud.Device dv=devices.get(which);
                        url.setText(BambuCloud.apiBase(region));user.setText(dv.serial);secret.setText(login.token);try{pendingOauth=login.session();}catch(org.json.JSONException ignored){}auths.setSelection(PrinterEndpoint.Auth.BEARER.ordinal());
                        try{extras.setText(new JSONObject().put("uid",login.uid).put("region",region).toString());}catch(org.json.JSONException ignored){}
                        if(name.getText().toString().trim().isEmpty()||name.getText().toString().equals(KeplerApplication.str(R.string.l10n_net_my_printer)))name.setText(dv.name);
                    });    }
    /** After the two-factor step: list the account's printers off the main thread. */
    private void bambuSignedIn(String region,BambuCloud.Login login){
        new Thread(()->{
            try{java.util.List<BambuCloud.Device> devices=BambuCloud.devices(region,login.token,new NetworkHttp.Cancellation());runOnUiThread(()->bambuDevices(region,login,devices));}
            catch(Exception e){final String m=error(e);runOnUiThread(()->{if(!isDestroyed())MdUi.alert(this).setTitle(R.string.kv3_bambu_cloud).setMessage(m).setPositiveButton(R.string.kv3_ok,null).show();});}
        },"bambu-cloud-devices").start();
    }
    private void bambuCloudStep(String region,String email,String password,String code){
        android.widget.Toast.makeText(this,R.string.pref_working,android.widget.Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                NetworkHttp.Cancellation cancel=new NetworkHttp.Cancellation();
                BambuCloud.Login login=BambuCloud.signIn(region,email,password,code,cancel);
                if(login.needsCode){runOnUiThread(()->bambuCodeWindow(region,email,null,login.notice));return;}
                if(login.needsTfa){runOnUiThread(()->bambuCodeWindow(region,email,login.tfaKey,""));return;}
                java.util.List<BambuCloud.Device> devices=BambuCloud.devices(region,login.token,cancel);
                runOnUiThread(()->bambuDevices(region,login,devices));
            }catch(Exception e){final String m=error(e);runOnUiThread(()->{if(!isDestroyed())MdUi.alert(this).setTitle(R.string.kv3_bambu_cloud).setMessage(m).setPositiveButton(R.string.kv3_ok,null).show();});}
        },"bambu-cloud-login").start();
    }
    private void form(NetworkStore.Connection connection,String suggestedUrl){form(connection,suggestedUrl,null);}
    private static PrinterEndpoint.Type discoveredType(String service){String value=service==null?"":service.toLowerCase(Locale.ROOT);if(value.startsWith("_bambu:"))return PrinterEndpoint.Type.BAMBU;if(value.startsWith("_moonraker."))return PrinterEndpoint.Type.MOONRAKER;if(value.startsWith("_prusa.")||value.startsWith("_prusalink."))return PrinterEndpoint.Type.PRUSALINK;if(value.startsWith("_ultimaker."))return PrinterEndpoint.Type.ULTIMAKER;return PrinterEndpoint.Type.OCTOPRINT;}
    private void form(NetworkStore.Connection connection,String suggestedUrl,String service){stopDiscovery();editing=true;draft=connection;pendingOauth=connection==null?"":connection.oauthSession;PrinterEndpoint.Type suggested=connection==null?discoveredType(service):connection.endpoint.type;ui=new MdUi(this);page=ui.page(this,connection==null?KeplerApplication.str(R.string.l10n_net_new):KeplerApplication.str(R.string.l10n_net_connection),this::render);ui.navigation(this,3);
        name=field(KeplerApplication.str(R.string.l10n_net_name),connection==null?KeplerApplication.str(R.string.l10n_net_my_printer):connection.name,false);String[] labels=new String[PrinterEndpoint.Type.values().length];for(int i=0;i<labels.length;i++)labels[i]=label(PrinterEndpoint.Type.values()[i]);types=spinner(KeplerApplication.str(R.string.l10n_net_protocol),labels,suggested.ordinal());hostChips(types,labels);
        guide=ui.text("",13,false);guide.setTextColor(ui.ink);guide.setLineSpacing(0,1.2f);ui.pad(guide,14,12,14,12);guide.setBackground(new com.keplerslicer.app.glass.KeplerUi(this).glass(16));guide.setVisibility(View.GONE);page.addView(guide);ui.gap(page,12);
        url=field(KeplerApplication.str(R.string.l10n_net_address),connection==null?(suggestedUrl==null?"":suggestedUrl):connection.endpoint.baseUri.toASCIIString(),false);
        auths=spinner(KeplerApplication.str(R.string.l10n_net_auth),new String[]{KeplerApplication.str(R.string.l10n_net_no_auth),"API key","Digest",KeplerApplication.str(R.string.l10n_net_password),"Bearer token","Basic"},connection==null?(service==null?0:defaultAuth(suggested).ordinal()):connection.endpoint.auth.ordinal());
        user=field(KeplerApplication.str(R.string.l10n_net_user),connection==null?(service!=null&&service.startsWith("_bambu:")?service.substring(7):""):connection.endpoint.username,false);secret=field(KeplerApplication.str(R.string.l10n_net_key),connection==null?"":connection.endpoint.secret(),true);storage=field(KeplerApplication.str(R.string.l10n_net_storage),connection==null?(suggested==PrinterEndpoint.Type.MOONRAKER?"gcodes":suggested==PrinterEndpoint.Type.PRUSALINK?"usb":"local"):connection.endpoint.storage,false);
        extras=field(KeplerApplication.str(R.string.l10n_net_extra_json),connection==null?"{}":new JSONObject(connection.endpoint.extras).toString(),false);extras.setSingleLine(false);extras.setMinLines(3);extras.getLayoutParams().height=-2;extras.setGravity(android.view.Gravity.TOP);extras.setPadding(ui.dp(12),ui.dp(10),ui.dp(12),ui.dp(10));
        TextView description=ui.text(KeplerApplication.str(R.string.l10n_net_extra_help),12,false);description.setTextColor(ui.muted);page.addView(description);extrasHelp=description;ui.gap(page,16);
        page.addView(cloudButton=ui.button(KeplerApplication.str(R.string.l10n_net_cloud_login),false,()->{PrinterEndpoint.Type type=PrinterEndpoint.Type.values()[types.getSelectedItemPosition()];if(type==PrinterEndpoint.Type.BAMBU_CLOUD){bambuCloudLogin();return;}if(type!=PrinterEndpoint.Type.SIMPLYPRINT&&type!=PrinterEndpoint.Type.THREE_D_PRINTER_OS){chooseCloudService();return;}try{NetworkPolicy.requireCloud(this);startActivityForResult(new Intent(this,CloudPrintLoginActivity.class).putExtra("type",type.name()).putExtra("base_url",url.getText().toString()),CLOUD_LOGIN);}catch(Exception e){MdUi.alert(this).setMessage(error(e)).setPositiveButton(R.string.kv3_ok,null).show();}}));ui.gap(page,16);
        page.addView(obicoButton=ui.button(KeplerApplication.str(R.string.l10n_net_obico_printer),false,()->chooseCloudItem(CloudSelectionHelper.Kind.OBICO_PRINTER)));ui.gap(page,8);
        page.addView(posProjectButton=ui.button(KeplerApplication.str(R.string.l10n_net_3dpos_project),false,()->chooseCloudItem(CloudSelectionHelper.Kind.PRINTER_OS_PROJECT)));ui.gap(page,8);
        page.addView(posTypeButton=ui.button(KeplerApplication.str(R.string.l10n_net_3dpos_type),false,()->chooseCloudItem(CloudSelectionHelper.Kind.PRINTER_OS_TYPE)));ui.gap(page,16);
        page.addView(ui.button(KeplerApplication.str(R.string.l10n_net_save),true,this::saveForm));ui.gap(page,12);TextView note=ui.text(KeplerApplication.str(R.string.l10n_net_save_note),12,false);note.setTextColor(ui.muted);page.addView(note);
        adaptForm(suggested);
        types.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){boolean initial=true;public void onNothingSelected(android.widget.AdapterView<?> parent){}public void onItemSelected(android.widget.AdapterView<?> parent,View view,int index,long id){adaptForm(PrinterEndpoint.Type.values()[index]);if(initial){initial=false;if(index==suggested.ordinal())return;}if(connection!=null)return;PrinterEndpoint.Type type=PrinterEndpoint.Type.values()[index];auths.setSelection(defaultAuth(type).ordinal());if(type==PrinterEndpoint.Type.MOONRAKER)storage.setText("gcodes");else if(type==PrinterEndpoint.Type.PRUSALINK)storage.setText("usb");else storage.setText("local");String host=defaultHost(type);if(!host.isEmpty())url.setText(host);}});
    }
    private TextView guide,extrasHelp;private View cloudButton,obicoButton,posProjectButton,posTypeButton;
    private static void showRow(View field,boolean visible){if(field==null)return;int v=visible?View.VISIBLE:View.GONE;field.setVisibility(v);if(field.getParent() instanceof LinearLayout){LinearLayout parent=(LinearLayout)field.getParent();int i=parent.indexOfChild(field);if(i>0)parent.getChildAt(i-1).setVisibility(v);}}
    private static void caption(View field,String text){if(field!=null&&field.getParent() instanceof LinearLayout){LinearLayout parent=(LinearLayout)field.getParent();int i=parent.indexOfChild(field);if(i>0&&parent.getChildAt(i-1) instanceof TextView)((TextView)parent.getChildAt(i-1)).setText(text);}}
    /** The form shows only what the chosen protocol needs, with its own names (Bambu LAN: IP, serial, access code). */
    private void adaptForm(PrinterEndpoint.Type type){
        if(url==null||guide==null)return;
        boolean lan=type==PrinterEndpoint.Type.BAMBU,cloud=type==PrinterEndpoint.Type.BAMBU_CLOUD,generic=!lan&&!cloud;
        caption(url,lan?L.t("IP-адрес принтера"):KeplerApplication.str(R.string.l10n_net_address));url.setHint(lan?"192.168.1.50":"");
        caption(user,lan?L.t("Серийный номер (SN)"):KeplerApplication.str(R.string.l10n_net_user));user.setHint(lan?"01P00A000000000":"");
        caption(secret,lan?L.t("Код доступа (Access Code)"):KeplerApplication.str(R.string.l10n_net_key));secret.setHint(lan?L.t("8 символов с экрана принтера"):"");
        if(lan){user.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);}
        showRow(url,!cloud);showRow(user,!cloud);showRow(secret,!cloud);
        showRow(auths,generic);showRow(storage,generic);showRow(extras,generic);
        if(extrasHelp!=null)extrasHelp.setVisibility(generic?View.VISIBLE:View.GONE);
        if(cloudButton!=null)cloudButton.setVisibility(lan?View.GONE:View.VISIBLE);
        for(View b:new View[]{obicoButton,posProjectButton,posTypeButton})if(b!=null)b.setVisibility(generic?View.VISIBLE:View.GONE);
        if(lan){guide.setText(L.t("На принтере Bambu Lab:\n• IP-адрес и код доступа: Настройки → WLAN (на A1/P1 — Настройки → Сеть).\n• Серийный номер: Настройки → Устройство (или наклейка на корпусе).\n• Включите «Только LAN» (LAN Only). На прошивках 2025 года для управления сторонними программами также включите «Режим разработчика».\nТелефон и принтер должны быть в одной сети Wi-Fi. Китайская и глобальная версии подключаются одинаково."));guide.setVisibility(View.VISIBLE);}
        else if(cloud){guide.setText(L.t("Войдите в аккаунт Bambu Lab кнопкой ниже и выберите принтер. Для китайской версии принтера сначала выберите регион «Китай» в Общие → Сеть → Регион входа."));guide.setVisibility(View.VISIBLE);}
        else guide.setVisibility(View.GONE);
    }
    private void checkBambuLan()throws IOException{
        String host=url.getText().toString().trim().replaceFirst("^[a-zA-Z]+://","").replaceAll("/.*$","");
        if(!host.matches("(\\d{1,3}\\.){3}\\d{1,3}")&&!host.matches("[A-Za-z0-9.-]{1,253}"))throw new IOException(L.t("Введите IP-адрес принтера, например 192.168.1.50."));
        String sn=user.getText().toString().trim();if(!sn.matches("[A-Za-z0-9]{8,32}"))throw new IOException(L.t("Введите серийный номер принтера (латинские буквы и цифры, без пробелов)."));
        String code=secret.getText().toString().trim();if(!code.matches("[A-Za-z0-9]{6,16}"))throw new IOException(L.t("Введите код доступа с экрана принтера (обычно 8 символов)."));
        user.setText(sn.toUpperCase(Locale.ROOT));secret.setText(code);
    }
    private EditText field(String label,String value,boolean password){TextView caption=ui.text(label,13,false);caption.setTextColor(ui.muted);ui.pad(caption,4,0,4,6);page.addView(caption);EditText e=new EditText(this);e.setTextColor(ui.ink);e.setTextSize(15);com.keplerslicer.app.glass.GlassTypography.apply(e,600);e.setMinHeight(ui.dp(44));ui.pad(e,12,0,12,0);{android.graphics.drawable.GradientDrawable fb=new android.graphics.drawable.GradientDrawable();fb.setColor(com.keplerslicer.app.glass.KeplerUi.FIELD);fb.setStroke(Math.max(1,ui.dp(1)),com.keplerslicer.app.glass.KeplerUi.FIELD_STROKE);fb.setCornerRadius(ui.dp(12));e.setBackground(fb);}e.setText(value);e.setSingleLine(true);e.setContentDescription(label);e.setInputType(InputType.TYPE_CLASS_TEXT|(password?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));e.setGravity(android.view.Gravity.CENTER_VERTICAL);page.addView(e,ui.lp(-1,48));ui.gap(page,14);return e;}
    private void chooseCloudItem(CloudSelectionHelper.Kind kind){try {
        if(!editing||busy)return;NetworkPolicy.requireOnline(this);
        PrinterEndpoint.Type type=PrinterEndpoint.Type.values()[types.getSelectedItemPosition()];
        PrinterEndpoint.Type expected=kind==CloudSelectionHelper.Kind.OBICO_PRINTER?PrinterEndpoint.Type.OBICO:PrinterEndpoint.Type.THREE_D_PRINTER_OS;
        if(type!=expected)throw new IOException(KeplerApplication.str(R.string.l10n_net_choose_protocol_first)+(expected==PrinterEndpoint.Type.OBICO?"Obico":"3DPrinterOS")+".");
        String host=url.getText().toString(),credential=secret.getText().toString(),login=user.getText().toString(),disk=storage.getText().toString();
        PrinterEndpoint.Auth auth=PrinterEndpoint.Auth.values()[auths.getSelectedItemPosition()];
        PrinterEndpoint endpoint=new PrinterEndpoint(type,host,auth,login,credential,disk);
        EditText target=extras;
        CloudSelectionHelper.choose(this,endpoint,kind,(id,label,create)->{try {
            if(!editing||extras!=target||types.getSelectedItemPosition()!=type.ordinal()||!host.equals(url.getText().toString())||!credential.equals(secret.getText().toString())||auths.getSelectedItemPosition()!=auth.ordinal())throw new IOException(KeplerApplication.str(R.string.l10n_net_changed_relist));
            JSONObject value=new JSONObject(target.getText().toString());
            if(kind==CloudSelectionHelper.Kind.OBICO_PRINTER)value.put("printer_id",id);
            else if(kind==CloudSelectionHelper.Kind.PRINTER_OS_PROJECT){if(create){value.remove("project_id");value.put("project_name",label);}else{value.remove("project_name");value.put("project_id",id);}}
            else {value.put("printer_type",id);value.put("gcode_type","OrcaSlicer");}
            target.setText(value.toString());MdUi.alert(this).setMessage(KeplerApplication.str(R.string.l10n_net_chosen)+label+KeplerApplication.str(R.string.l10n_net_save_it)).setPositiveButton(R.string.kv3_ok,null).show();
        }catch(Exception e){MdUi.alert(this).setMessage(error(e)).setPositiveButton(R.string.kv3_ok,null).show();}});
    }catch(Exception e){MdUi.alert(this).setMessage(error(e)).setPositiveButton(R.string.kv3_ok,null).show();}}
    /** Host type chips (Figma Connection 5:594) drive the existing protocol selector, which stays the single source of truth. */
    /** A hidden Spinner reports no selection, so the protocol chips apply the defaults of a new connection here. */
    private void applyTypeDefaults(PrinterEndpoint.Type type){auths.setSelection(defaultAuth(type).ordinal());if(type==PrinterEndpoint.Type.MOONRAKER)storage.setText("gcodes");else if(type==PrinterEndpoint.Type.PRUSALINK)storage.setText("usb");else storage.setText("local");String host=defaultHost(type);url.setText(host);}
    private void hostChips(Spinner spinner,String[] labels){com.keplerslicer.app.glass.KeplerUi k=new com.keplerslicer.app.glass.KeplerUi(this);int at=page.indexOfChild(spinner);com.keplerslicer.app.glass.FlowRow row=new com.keplerslicer.app.glass.FlowRow(this,ui.dp(8),ui.dp(8));TextView[] chips=new TextView[labels.length];
        for(int i=0;i<labels.length;i++){final int index=i;chips[i]=k.chip(labels[i],i==spinner.getSelectedItemPosition(),()->{spinner.setSelection(index);for(int j=0;j<chips.length;j++)k.setChipActive(chips[j],j==index);if(spinner==types&&draft==null)applyTypeDefaults(PrinterEndpoint.Type.values()[index]);if(spinner==types)adaptForm(PrinterEndpoint.Type.values()[index]);});row.addView(chips[i],new android.view.ViewGroup.LayoutParams(-2,ui.dp(36)));}
        spinner.setVisibility(View.GONE);page.addView(row,at,new LinearLayout.LayoutParams(-1,-2));}
    private Spinner spinner(String label,String[] values,int selected){TextView caption=ui.text(label,13,false);caption.setTextColor(ui.muted);ui.pad(caption,4,0,4,6);page.addView(caption);Spinner s=new Spinner(this);{android.graphics.drawable.GradientDrawable sb=new android.graphics.drawable.GradientDrawable();sb.setColor(com.keplerslicer.app.glass.KeplerUi.FIELD);sb.setStroke(Math.max(1,ui.dp(1)),com.keplerslicer.app.glass.KeplerUi.FIELD_STROKE);sb.setCornerRadius(ui.dp(12));s.setBackground(sb);s.setPopupBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF2E323A));}ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,values){@Override public View getView(int p,View v,android.view.ViewGroup parent){TextView t=(TextView)super.getView(p,v,parent);t.setTextColor(ui.ink);return t;}@Override public View getDropDownView(int p,View v,android.view.ViewGroup parent){TextView t=(TextView)super.getDropDownView(p,v,parent);t.setTextColor(ui.ink);t.setBackgroundColor(0xFF2E323A);return t;}};s.setAdapter(a);s.setSelection(selected);com.keplerslicer.app.glass.KeplerUi.sheetSpinner(s,label);page.addView(s,ui.lp(-1,44));ui.gap(page,16);return s;}
    private void saveForm(){if(busy)return;try{String title=name.getText().toString().trim();if(title.isEmpty()||title.length()>120)throw new IOException(KeplerApplication.str(R.string.l10n_net_name_length));JSONObject json=new JSONObject(extras.getText().toString());Map<String,String> x=new LinkedHashMap<>();for(Iterator<String> it=json.keys();it.hasNext();){String key=it.next();Object value=json.get(key);if(!(value instanceof String)&&!(value instanceof Number)&&!(value instanceof Boolean))throw new IOException(KeplerApplication.str(R.string.l10n_net_extra_types));x.put(key,String.valueOf(value));}
        if(PrinterEndpoint.Type.values()[types.getSelectedItemPosition()]==PrinterEndpoint.Type.BAMBU){checkBambuLan();}
        PrinterEndpoint endpoint=new PrinterEndpoint(PrinterEndpoint.Type.values()[types.getSelectedItemPosition()],url.getText().toString(),PrinterEndpoint.Auth.values()[auths.getSelectedItemPosition()],user.getText().toString(),secret.getText().toString(),storage.getText().toString(),x);String oauth="";if((endpoint.type==PrinterEndpoint.Type.SIMPLYPRINT||endpoint.type==PrinterEndpoint.Type.BAMBU_CLOUD)&&!pendingOauth.isEmpty()){JSONObject session=new JSONObject(pendingOauth);if(endpoint.secret().equals(session.optString("access_token"))&&!session.optString("refresh_token").isEmpty())oauth=pendingOauth;}
        NetworkStore.Connection value=new NetworkStore.Connection(draft==null?UUID.randomUUID().toString():draft.id,title,NetworkStore.currentPrinterIdentity(this),endpoint,oauth);
        busy=true;new Thread(()->{try{NetworkStore.save(this,value);runOnUiThread(()->{busy=false;notice=KeplerApplication.str(R.string.l10n_net_saved);load();});}catch(Exception e){runOnUiThread(()->{busy=false;if(!isDestroyed()&&!isFinishing())MdUi.alert(this).setMessage(error(e)).setPositiveButton(R.string.kv3_ok,null).show();});}},"printer-connection-save").start();
    }catch(Exception e){MdUi.alert(this).setMessage(error(e)).setPositiveButton(R.string.kv3_ok,null).show();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==CLOUD_LOGIN&&result==RESULT_OK&&data!=null&&editing){String token=data.getStringExtra("secret");if(token!=null)secret.setText(token);String session=data.getStringExtra("oauth_session");pendingOauth=session==null?"":session;String login=data.getStringExtra("username");if(login!=null)user.setText(login);auths.setSelection(PrinterEndpoint.Auth.BEARER.ordinal());}}
    private void discover(){try{NetworkPolicy.requireOnline(this);}catch(IOException e){notice=error(e);render();return;}stopDiscovery();found.clear();notice=KeplerApplication.str(R.string.l10n_net_searching);render();LinearLayout results=ui.vertical();page.addView(results);discovery=new PrinterDiscovery(this,new PrinterDiscovery.Listener(){public void found(String title,String address,String service){runOnUiThread(()->{if(isDestroyed()||discovery==null||found.size()>=100)return;found.add(new String[]{title,address,service});results.addView(ui.row(title,address+" · "+(service.startsWith("_bambu:")?"SN "+service.substring(7):service),"▣",()->{form(null,address,service);if(name!=null&&service.startsWith("_bambu:"))name.setText(title);}));ui.gap(results,8);});}public void message(String text){runOnUiThread(()->{if(message!=null)message.setText(text);});}});discovery.start();page.addView(ui.button(KeplerApplication.str(R.string.l10n_net_stop_search),false,()->{stopDiscovery();notice=found.isEmpty()?KeplerApplication.str(R.string.l10n_net_none_found):KeplerApplication.str(R.string.l10n_net_search_stopped);if(message!=null)message.setText(notice);}));}
    private void stopDiscovery(){if(discovery!=null){discovery.stop();discovery=null;}}
    private static String error(Throwable e){String s=EngineText.clean(e.getMessage());return s==null?KeplerApplication.str(R.string.l10n_net_op_failed)+e.getClass().getSimpleName():s;}
    static String label(PrinterEndpoint.Type t){switch(t.name()){case "OCTOPRINT":return "OctoPrint";case "MOONRAKER":return "Klipper / Moonraker";case "PRUSALINK":return "PrusaLink";case "PRUSACONNECT":return "Prusa Connect";case "DUET":return "Duet / RepRapFirmware";case "ULTIMAKER":return "UltiMaker";case "FLASHAIR":return "FlashAir";case "ASTROBOX":return "AstroBox";case "REPETIER":return "Repetier Server";case "MKS":return "MKS";case "ESP3D":return "ESP3D";case "CREALITYPRINT":return "Creality Print";case "FLASHFORGE":return "Flashforge";case "ELEGOOLINK":return "ElegooLink";case "OBICO":return "Obico";case "SIMPLYPRINT":return "SimplyPrint";case "THREE_D_PRINTER_OS":return "3DPrinterOS";case "SL1":return "Prusa SL1 / SL1S";case "BAMBU":return "Bambu Lab (LAN)";case "BAMBU_CLOUD":return KeplerApplication.str(R.string.kv3_bambu_cloud);default:return KeplerApplication.str(R.string.l10n_net_bambu_sdk);}}
    private static PrinterEndpoint.Auth defaultAuth(PrinterEndpoint.Type t){switch(t){case OCTOPRINT:case PRUSALINK:case PRUSACONNECT:case REPETIER:case ASTROBOX:return PrinterEndpoint.Auth.API_KEY;case ULTIMAKER:return PrinterEndpoint.Auth.NONE;case DUET:return PrinterEndpoint.Auth.PASSWORD;case OBICO:case SIMPLYPRINT:case CREALITYPRINT:case BAMBU_CLOUD:return PrinterEndpoint.Auth.BEARER;default:return PrinterEndpoint.Auth.NONE;}}
    private static String defaultHost(PrinterEndpoint.Type t){switch(t){case PRUSACONNECT:return "https://connect.prusa3d.com";case OBICO:return "https://app.obico.io";case SIMPLYPRINT:return "https://simplyprint.io";case THREE_D_PRINTER_OS:return "https://cloud.3dprinteros.com";case BAMBU_CLOUD:return BambuCloud.apiBase(OnlinePreferences.region(KeplerApplication.get()));default:return "";}}
}
