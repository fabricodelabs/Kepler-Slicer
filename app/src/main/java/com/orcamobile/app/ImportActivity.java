package com.orcamobile.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.List;

/** Android document providers and real, persistently accessible model history. */
public final class ImportActivity extends Activity {
    private static final int PICK_FILE=401;
    private MdUi ui; private LinearLayout content,recent; private boolean pickerOpen;
    @Override public void onCreate(Bundle state) {
        MdUi.prepare(this); super.onCreate(state); pickerOpen=state!=null&&state.getBoolean("picker"); ui=new MdUi(this);
        content=ui.page(this,"Импорт модели",this::finish);ui.navigation(this,0);
        TextView intro=ui.text("Откройте модель на устройстве или через подключённое облачное хранилище.",14,false); intro.setTextColor(ui.muted); content.addView(intro); ui.gap(content,24);
        LinearLayout formats=ui.horizontal(); ui.pad(formats,4,4,4,4); formats.setBackground(ui.shape(ui.surface,100));
        for(String format:new String[]{"STL","3MF","OBJ"}) { TextView t=ui.text(format,13,true); t.setGravity(Gravity.CENTER); t.setMinHeight(ui.dp(42));
            t.setTextColor(ui.ink);t.setContentDescription(format+", импорт модели");t.setOnClickListener(v->chooseFile());
            formats.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        } content.addView(formats); ui.gap(content,24);
        TextView label=ui.text("Откуда загрузить",18,true); content.addView(label); ui.gap(content,12);
        LinearLayout sources=ui.horizontal(); source(sources,"▱","Файлы",this::chooseFile); source(sources,"☁","Облако",this::chooseFile); content.addView(sources); ui.gap(content,8);
        TextView note=ui.text("Облачные сервисы доступны в системном окне Android, если их приложения установлены.",12,false); note.setTextColor(ui.muted); content.addView(note); ui.gap(content,28);
        content.addView(ui.text("Недавние модели",18,true)); ui.gap(content,12); recent=ui.vertical(); content.addView(recent); loadRecent();
        ui.gap(content,24); content.addView(ui.button("Выбрать файл",true,this::chooseFile),ui.lp(-1,-2)); ui.gap(content,12);
        TextView limits=ui.text("STL · OBJ · 3MF · без лимита размера STL",12,false); limits.setGravity(Gravity.CENTER); limits.setTextColor(ui.muted); content.addView(limits);
    }
    private void source(LinearLayout row,String icon,String title,Runnable action) {
        LinearLayout tile=ui.vertical(); tile.setGravity(Gravity.CENTER); ui.pad(tile,16,22,16,22); TextView symbol=ui.text(icon,27,false); symbol.setGravity(Gravity.CENTER); tile.addView(symbol); ui.gap(tile,12); TextView caption=ui.text(title,14,true);caption.setGravity(Gravity.CENTER);tile.addView(caption,ui.lp(-1,-2)); ui.clickable(tile,ui.surface,24,action);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1); if(row.getChildCount()>0)p.leftMargin=ui.dp(12);row.addView(tile,p);
    }
    private void loadRecent() {
        recent.addView(ui.text("Загружаем историю…",13,false));
        new Thread(()-> { List<RecentModels.Entry> entries=RecentModels.load(this); CurrentModelStore current=null;
            try {current=CurrentModelStore.load(this);}catch(Exception ignored) { }
            final CurrentModelStore selected=current;
            runOnUiThread(()-> {if(isDestroyed())return;recent.removeAllViews();
                if(selected!=null) { LinearLayout card=ui.row(selected.name,"Открытая модель","◇",this::finish);recent.addView(card);ui.gap(recent,12); }
                LinearLayout row=null; int count=0;
                for(RecentModels.Entry e:entries) {if(count%2==0){row=ui.horizontal();row.setGravity(Gravity.TOP);recent.addView(row);ui.gap(recent,12);}addRecent(row,e);count++;}
                if(entries.isEmpty()&&selected==null){TextView empty=ui.text("Здесь появятся модели, которые вы откроете.",14,false);empty.setTextColor(ui.muted);recent.addView(empty);}
            });
        },"recent-models").start();
    }
    private void addRecent(LinearLayout row,RecentModels.Entry e) {
        LinearLayout tile=ui.vertical(); ui.pad(tile,8,8,8,14);ui.clickable(tile,ui.surface,20,()->returnUri(e.uri));
        File file=RecentModels.thumbnailFile(this,e); Bitmap bitmap=file==null?null:BitmapFactory.decodeFile(file.getAbsolutePath());
        if(bitmap!=null) {ImageView image=new ImageView(this); image.setImageBitmap(bitmap);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription("Превью "+e.name);tile.addView(image,ui.lp(-1,112));}
        else {TextView icon=ui.text("◇",40,false);icon.setGravity(Gravity.CENTER);icon.setTextColor(ui.muted);tile.addView(icon,ui.lp(-1,112));}
        ui.gap(tile,10); TextView name=ui.text(e.name,12,true);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);ui.pad(name,4,0,4,0);tile.addView(name);ui.gap(tile,5);TextView type=ui.text(e.name.toLowerCase(java.util.Locale.ROOT).endsWith(".3mf")?"3MF":e.name.toLowerCase(java.util.Locale.ROOT).endsWith(".obj")?"OBJ":"STL",10,false);type.setTextColor(ui.muted);ui.pad(type,4,0,4,0);tile.addView(type);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(row.getChildCount()>0)p.leftMargin=ui.dp(12);row.addView(tile,p);
    }
    private void chooseFile() {if(pickerOpen)return;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        Uri initial=GeneralPreferences.initialDocumentUri(this);if(initial!=null)i.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,initial);
        try {pickerOpen=true;startActivityForResult(i,PICK_FILE);}catch(android.content.ActivityNotFoundException e){pickerOpen=false;MdUi.alert(this).setTitle("Выбор файла").setMessage("На устройстве нет приложения для выбора документов.").setPositiveButton("Понятно",null).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=PICK_FILE)return;pickerOpen=false;if(result==RESULT_OK&&data!=null&&data.getData()!=null){
        if((data.getFlags()&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0&&(data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0)try{getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
        returnUri(data.getData());
    }}
    private void returnUri(Uri uri){setResult(RESULT_OK,new Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));finish();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("picker",pickerOpen);super.onSaveInstanceState(state);}
}
