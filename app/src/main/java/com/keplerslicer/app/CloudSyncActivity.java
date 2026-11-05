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
    private void render(){if(isFinishing()||isDestroyed())return;ui=new MdUi(this);page=ui.page(this,KeplerApplication.str(R.string.l10n_cloud_profiles),this::finish);ui.navigation(this,3);
        if(!notice.isEmpty()){TextView t=ui.text(notice,13,false);t.setTextColor(ui.muted);page.addView(t);ui.gap(page,16);}
        row(KeplerApplication.str(R.string.l10n_cs_about),KeplerApplication.str(R.string.l10n_cs_repo_note),null);
        try {JSONObject current=session();if(current==null){row(KeplerApplication.str(R.string.l10n_sign_in),KeplerApplication.str(R.string.l10n_cs_browser_note),busy?null:this::chooseLogin);
                if(login!=null)row(L.t("Браузер показал ошибку?"),L.t("Скопируйте адрес страницы (начинается с http://localhost) и вставьте его здесь."),this::pasteCallback);}
            else {String id=current.optJSONObject("user")==null?"":current.optJSONObject("user").optString("id");CloudProfileStore store=new CloudProfileStore(this,id);JSONArray profiles=store.profiles();int conflicts=store.conflictCount();
                row(KeplerApplication.str(R.string.l10n_cs_account),KeplerApplication.str(R.string.l10n_cs_profile_count)+profiles.length()+KeplerApplication.str(R.string.l10n_cs_conflicts_suffix)+conflicts,null);
                row(KeplerApplication.str(R.string.l10n_cs_sync_profiles),KeplerApplication.str(R.string.l10n_cs_sync_detail),busy?null:this::confirmSync);
                row(KeplerApplication.str(R.string.l10n_cs_profiles),KeplerApplication.str(R.string.l10n_cs_profiles_detail),busy?null:this::showProfiles);
                row(KeplerApplication.str(R.string.l10n_cs_conflicts_title),KeplerApplication.str(R.string.l10n_cs_compare_detail),busy?null:this::reviewRepositoryConflicts);
                if(conflicts>0)row(KeplerApplication.str(R.string.l10n_cs_conflicts),KeplerApplication.str(R.string.l10n_cs_local_saved_review),busy?null:this::showConflicts);
                row(KeplerApplication.str(R.string.l10n_cs_export),KeplerApplication.str(R.string.l10n_cs_export_detail),busy?null:this::export);
                row(KeplerApplication.str(R.string.l10n_sign_out),KeplerApplication.str(R.string.l10n_cs_sign_out_detail),busy?null:this::logout);
            }
        }catch(IOException e){row(KeplerApplication.str(R.string.l10n_error),message(e),null);}
    }
    private static String message(Throwable e){return EngineText.clean(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
    private void finishTask(String result){runOnUiThread(()->{if(!isDestroyed()&&!isFinishing()){busy=false;notice=result;render();}});}
    private void chooseLogin(){MdUi.alert(this).setTitle(KeplerApplication.str(R.string.l10n_cs_sign_in_title)).setItems(new String[]{"Google","GitHub","Apple"},(d,n)->startLogin(new String[]{"google","github","apple"}[n])).setNegativeButton(KeplerApplication.str(R.string.l10n_cancel),null).show();}
    private void startLogin(String provider){if(busy)return;if(!networkAllowed())return;busy=true;notice=KeplerApplication.str(R.string.l10n_cs_waiting_browser);new Thread(()->{
        CloudProfileOAuth auth=null;
        try{auth=new CloudProfileOAuth();login=auth;final CloudProfileOAuth a=auth;
            // Keep the process running while the browser is in front: some phones freeze background apps, and the
            // browser's return to http://localhost:41172 then fails.
            LoginKeepAlive.start(this);
            runOnUiThread(()->{if(isDestroyed())return;render();try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(a.authorizationUrl(provider))));}catch(Exception e){try{a.close();}catch(IOException ignored){}}});
            JSONObject data=auth.awaitAndExchange((path,json)->tokenRequest(path,json));OnlinePreferences.saveSession(this,data);finishTask(KeplerApplication.str(R.string.l10n_cs_signed_in));
        }catch(Exception e){if(!pasting)finishTask(KeplerApplication.str(R.string.l10n_cs_sign_in_failed)+message(e));} // the pasted address finishes the login itself
        finally{if(auth!=null)try{auth.close();}catch(IOException ignored){}LoginKeepAlive.stop(this);if(login==auth&&!pasting)login=null;}
    },"kepler-cloud-login").start();}
    private volatile boolean pasting;
    /** Manual finish: the address of the browser's error page carries the same one-time code. */
    private void pasteCallback(){
        final CloudProfileOAuth auth=login;if(auth==null||auth.isConsumed())return;
        android.widget.EditText field=new android.widget.EditText(this);field.setSingleLine(true);field.setHint("http://localhost:41172/callback?...");
        android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(cm!=null&&cm.hasPrimaryClip()&&cm.getPrimaryClip().getItemCount()>0){CharSequence t=cm.getPrimaryClip().getItemAt(0).coerceToText(this);if(t!=null&&t.toString().contains("localhost"))field.setText(t);}
        android.widget.LinearLayout box=new android.widget.LinearLayout(this);box.setOrientation(android.widget.LinearLayout.VERTICAL);box.setPadding(ui.dp(20),ui.dp(4),ui.dp(20),0);box.addView(field);
        MdUi.alert(this).setTitle(L.t("Адрес из браузера")).setMessage(L.t("Вставьте адрес страницы с ошибкой целиком.")).setView(box).setNegativeButton(KeplerApplication.str(R.string.l10n_cancel),null)
            .setPositiveButton(KeplerApplication.str(R.string.l10n_sign_in),(d,w)->{String address=field.getText().toString();busy=true;pasting=true;notice=KeplerApplication.str(R.string.l10n_cs_waiting_browser);render();new Thread(()->{
                try{JSONObject data=auth.exchangeFromUrl(address,(path,json)->tokenRequest(path,json));OnlinePreferences.saveSession(this,data);login=null;finishTask(KeplerApplication.str(R.string.l10n_cs_signed_in));}
                catch(Exception e){finishTask(KeplerApplication.str(R.string.l10n_cs_sign_in_failed)+message(e));}
                finally{pasting=false;if(auth.isConsumed())login=null;}
            },"kepler-cloud-login-paste").start();}).show();
    }
    private CloudProfileSync.Response tokenRequest(String path,String body)throws IOException {return OnlinePreferences.tokenRequest(this,path,body);}
    private JSONObject freshSession()throws IOException{return OnlinePreferences.freshSession(this);}
    private CloudProfileSync cloud(JSONObject session){return OnlinePreferences.client(this,session);}
    private CloudProfileStore store(JSONObject s)throws IOException {JSONObject user=s.optJSONObject("user");if(user==null||user.optString("id").isEmpty())throw new IOException(KeplerApplication.str(R.string.l10n_cs_no_account));return new CloudProfileStore(this,user.optString("id"));}
    private void confirmSync(){MdUi.alert(this).setTitle(KeplerApplication.str(R.string.l10n_cs_sync_q)).setMessage(KeplerApplication.str(R.string.l10n_cs_sync_note)).setPositiveButton(KeplerApplication.str(R.string.l10n_cs_sync),(d,w)->pull()).setNegativeButton(KeplerApplication.str(R.string.l10n_cancel),null).show();}
    private void pull(){if(busy)return;if(!networkAllowed())return;busy=true;notice=KeplerApplication.str(R.string.l10n_cs_syncing);render();new Thread(()->{try{finishTask(OnlinePreferences.syncNow(this));}catch(Exception e){finishTask(KeplerApplication.str(R.string.l10n_cs_sync_failed)+message(e));}},"kepler-cloud-pull").start();}
    private void reviewRepositoryConflicts(){if(busy||!networkAllowed())return;busy=true;notice=KeplerApplication.str(R.string.l10n_cs_fetching);render();new Thread(()->{try{JSONArray reviews=OnlinePreferences.conflictReviews(this);runOnUiThread(()->{if(isDestroyed())return;busy=false;if(reviews.length()==0){notice=KeplerApplication.str(R.string.l10n_cs_no_conflicts);render();return;}String[] labels=new String[reviews.length()];for(int i=0;i<reviews.length();i++){JSONObject row=reviews.optJSONObject(i);labels[i]=row.optJSONObject("local_source").optString("name")+(row.optBoolean("deleted",false)?KeplerApplication.str(R.string.l10n_cs_deleted_server):KeplerApplication.str(R.string.l10n_cs_changed_server));}MdUi.alert(this).setTitle(KeplerApplication.str(R.string.l10n_cs_choose_conflict)).setItems(labels,(d,n)->previewRepositoryConflict(reviews.optJSONObject(n))).setNegativeButton(KeplerApplication.str(R.string.l10n_close),null).show();});}catch(Exception e){finishTask(KeplerApplication.str(R.string.l10n_cs_versions_failed)+message(e));}},"kepler-cloud-review").start();}
    private void previewRepositoryConflict(JSONObject review){if(review==null)return;String local=review.optJSONObject("local_source").toString(),remote=review.optBoolean("deleted",false)?KeplerApplication.str(R.string.l10n_cs_deleted):review.optJSONObject("server_source").toString();String preview=KeplerApplication.str(R.string.l10n_cs_local_version)+shortPreview(local)+KeplerApplication.str(R.string.l10n_cs_server_version_nl)+shortPreview(remote)+KeplerApplication.str(R.string.l10n_cs_reject_note);MdUi.alert(this).setTitle(KeplerApplication.str(R.string.l10n_cs_compare)).setMessage(preview).setPositiveButton(KeplerApplication.str(R.string.l10n_cs_choose_action),(d,w)->MdUi.alert(this).setTitle(KeplerApplication.str(R.string.l10n_cs_only_this)).setItems(new String[]{KeplerApplication.str(R.string.l10n_cs_keep_local), KeplerApplication.str(R.string.l10n_cs_accept_server), KeplerApplication.str(R.string.l10n_cs_save_both), KeplerApplication.str(R.string.l10n_cs_export_both)},(dialog,index)->{if(index==3){exportReviewedVersions(review);return;}if(index==2){EditText name=new EditText(this);name.setSingleLine(true);name.setText(review.optJSONObject("local_source").optString("name")+" — local copy");MdUi.alert(this).setTitle(KeplerApplication.str(R.string.l10n_cs_copy_name)).setMessage(KeplerApplication.str(R.string.l10n_cs_save_both_note)).setView(name).setPositiveButton(KeplerApplication.str(R.string.l10n_cs_save_both_short),(a,b)->resolveRepositoryConflict(review,"duplicate",name.getText().toString())).setNegativeButton(KeplerApplication.str(R.string.l10n_cancel),null).show();return;}String choice=index==0?"local":"remote";String warning=index==0?KeplerApplication.str(R.string.l10n_cs_push_note):KeplerApplication.str(R.string.l10n_cs_accept_detail);MdUi.alert(this).setTitle(KeplerApplication.str(R.string.l10n_cs_confirm_q)).setMessage(warning).setPositiveButton(index==0?KeplerApplication.str(R.string.l10n_cs_push_local):KeplerApplication.str(R.string.l10n_cs_accept_server),(a,b)->resolveRepositoryConflict(review,choice,null)).setNegativeButton(KeplerApplication.str(R.string.l10n_cancel),null).show();}).setNegativeButton(KeplerApplication.str(R.string.l10n_cancel),null).show()).setNeutralButton(KeplerApplication.str(R.string.l10n_cs_export_versions),(d,w)->exportReviewedVersions(review)).setNegativeButton(KeplerApplication.str(R.string.l10n_cancel),null).show();}
    private static String shortPreview(String value){return value.length()<=5000?value:value.substring(0,5000)+KeplerApplication.str(R.string.l10n_cs_truncated);}
    private void resolveRepositoryConflict(JSONObject review,String choice,String name){if(busy)return;busy=true;notice=KeplerApplication.str(R.string.l10n_cs_applying);render();new Thread(()->{try{finishTask(OnlinePreferences.resolveConflict(this,review,choice,name));}catch(Exception e){finishTask(KeplerApplication.str(R.string.l10n_cs_not_applied)+message(e)+KeplerApplication.str(R.string.l10n_cs_both_kept));}},"kepler-cloud-resolve").start();}
    private void exportReviewedVersions(JSONObject review){try{exportData=new JSONObject().put("format","kepler-cloud-conflict-review-v1").put("review",new JSONObject(review.toString())).toString(2);Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"kepler-cloud-conflict.json");startActivityForResult(intent,EXPORT);}catch(Exception e){notice=KeplerApplication.str(R.string.l10n_cs_export_not_started)+message(e);render();}}
    private void showProfiles(){try {JSONArray p=store(session()).profiles();if(p.length()==0){notice=KeplerApplication.str(R.string.l10n_cs_no_profiles);render();return;}String[] names=new String[p.length()];for(int i=0;i<p.length();i++){JSONObject o=p.getJSONObject(i);names[i]=o.optString("name",o.optString("id"))+(o.optBoolean("dirty",false)?KeplerApplication.str(R.string.l10n_cs_changed):"");}MdUi.alert(this).setTitle(KeplerApplication.str(R.string.l10n_cs_cloud_profiles)).setItems(names,(d,index)->{try{showProfile(p.getJSONObject(index));}catch(Exception e){notice=message(e);render();}}).setNegativeButton(KeplerApplication.str(R.string.l10n_close),null).show();}catch(Exception e){notice=message(e);render();}}
    private void showProfile(JSONObject profile){String id=profile.optString("id"),name=profile.optString("name");MdUi.alert(this).setTitle(name).setMessage(KeplerApplication.str(R.string.l10n_cs_edit_note))
        .setPositiveButton(KeplerApplication.str(R.string.l10n_cs_edit),(d,w)->editProfile(id,profile.optJSONObject("content")))
        .setNeutralButton(KeplerApplication.str(R.string.l10n_send),(d,w)->{if(!profile.optBoolean("dirty",false)){notice=KeplerApplication.str(R.string.l10n_cs_no_local);render();}else push(profile);}).setNegativeButton(KeplerApplication.str(R.string.l10n_close),null).show();}
    private void editProfile(String id,JSONObject content){if(content==null)return;EditText editor=new EditText(this);editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);editor.setText(content.toString());editor.setMinLines(8);
        android.app.AlertDialog dialog=MdUi.alert(this).setTitle(KeplerApplication.str(R.string.l10n_cs_json)).setView(editor).setPositiveButton(KeplerApplication.str(R.string.l10n_save),null).setNegativeButton(KeplerApplication.str(R.string.l10n_cancel),null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{store(session()).edit(id,new JSONObject(editor.getText().toString()));notice=KeplerApplication.str(R.string.l10n_cs_local_saved);dialog.dismiss();render();}catch(Exception e){editor.setError(KeplerApplication.str(R.string.l10n_cs_save_failed)+message(e));}}));dialog.show();}
    private void showConflicts(){try {JSONObject conflicts=store(session()).conflicts();java.util.ArrayList<String> ids=new java.util.ArrayList<>();for(java.util.Iterator<String> i=conflicts.keys();i.hasNext();)ids.add(i.next());
        MdUi.alert(this).setTitle(KeplerApplication.str(R.string.l10n_cs_conflicts)).setItems(ids.toArray(new String[0]),(d,index)->{String id=ids.get(index);JSONObject c=conflicts.optJSONObject(id);String raw=c==null?"":c.toString(),value=raw.length()>4000?raw.substring(0,4000)+KeplerApplication.str(R.string.l10n_cs_truncated):raw;
            MdUi.alert(this).setTitle(id).setMessage(KeplerApplication.str(R.string.l10n_cs_server_version)+value+KeplerApplication.str(R.string.l10n_cs_accept_note))
                .setPositiveButton(KeplerApplication.str(R.string.l10n_cs_accept_server_short),(dialog,which)->{try{store(session()).acceptServer(id);notice=KeplerApplication.str(R.string.l10n_cs_server_accepted);}catch(Exception e){notice=message(e);}render();})
                .setNegativeButton(KeplerApplication.str(R.string.l10n_cs_save_local),null).show();}).setNegativeButton(KeplerApplication.str(R.string.l10n_close),null).show();
    }catch(Exception e){notice=message(e);render();}}
    private void push(JSONObject profile){if(busy||!networkAllowed())return;busy=true;notice=KeplerApplication.str(R.string.l10n_cs_pushing);render();new Thread(()->{try{finishTask(OnlinePreferences.pushMirroredProfile(this,profile));}catch(Exception e){finishTask(KeplerApplication.str(R.string.l10n_cs_push_failed)+message(e));}},"kepler-cloud-push").start();}
    private void export(){try{exportData=store(session()).exportJson();Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"kepler-cloud-profiles.json");startActivityForResult(i,EXPORT);}catch(Exception e){notice=KeplerApplication.str(R.string.l10n_cs_export_start_failed)+message(e);render();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==EXPORT&&result==RESULT_OK&&data!=null&&data.getData()!=null&&exportData!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new IOException(KeplerApplication.str(R.string.l10n_file_unavailable));out.write(exportData.getBytes(StandardCharsets.UTF_8));notice=KeplerApplication.str(R.string.l10n_cs_exported);}catch(IOException e){notice=KeplerApplication.str(R.string.l10n_cs_export_failed)+message(e);}finally{exportData=null;}render();}}
    private void logout(){MdUi.alert(this).setTitle(KeplerApplication.str(R.string.l10n_cs_sign_out_q)).setMessage(KeplerApplication.str(R.string.l10n_cs_sign_out_note)).setPositiveButton(KeplerApplication.str(R.string.l10n_sign_out),(d,w)->{try{OnlinePreferences.logout(this);notice=KeplerApplication.str(R.string.l10n_cs_signed_out);}catch(IOException e){notice=KeplerApplication.str(R.string.l10n_cs_sign_out_error)+message(e);}render();}).setNegativeButton(KeplerApplication.str(R.string.l10n_cancel),null).show();}
}
