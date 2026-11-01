package com.orcamobile.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

/** Saves an immutable, app-private slicing result through Android's document picker. */
public final class GcodeExportActivity extends Activity {
    public static final String EXTRA_RESULT_PATH = "result_path";
    public static final String EXTRA_FILE_NAME = "file_name";
    private static final int CREATE_DOCUMENT = 301;
    private TextView status, save;
    private MdUi ui;
    private File source;
    private ExportTask task;
    private boolean pickerOpen;

    @Override public void onCreate(Bundle state) {
        MdUi.prepare(this); super.onCreate(state);
        ui = new MdUi(this);
        Object retained = getLastNonConfigurationInstance();
        task = retained instanceof ExportTask ? (ExportTask)retained : null;
        pickerOpen = state != null && state.getBoolean("picker");
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); ui.bars(this,root);
        if (android.os.Build.VERSION.SDK_INT >= 35) root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets s = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            view.setPadding(s.left, s.top, s.right, s.bottom); return insets;
        });
        setContentView(root); ScrollView scroll = new ScrollView(this); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));ui.navigation(this,root,2);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(24),dp(24),dp(24),dp(32)); scroll.addView(content);
        TextView back = text("‹   Назад",15,true); back.setPadding(0,dp(8),0,dp(16)); back.setOnClickListener(v -> finish()); content.addView(back);
        content.addView(text("Экспорт G-code",28,true)); gap(content,16);
        content.addView(text("Сохраните результат нарезки в выбранную папку.",14,false)); gap(content,16);
        java.util.Map<String, ?> record=getSharedPreferences("last_slice_result",MODE_PRIVATE).getAll();
        String requested=getIntent().getStringExtra(EXTRA_RESULT_PATH);
        String details=requested!=null&&requested.equals(record.get("path"))?String.valueOf(record.get("details")):"";
        if(!details.isEmpty()){LinearLayout card=ui.row("Исходная модель и профили",details,null,null);content.addView(card);gap(content,16);}
        status = text("",14,false); status.setTextIsSelectable(true); content.addView(status); gap(content,24);
        save = text("Сохранить G-code",15,true); save.setGravity(Gravity.CENTER); save.setTextColor(ui.bg); save.setPadding(dp(20),dp(16),dp(20),dp(16));
        GradientDrawable background = new GradientDrawable(); background.setColor(ui.ink); background.setCornerRadius(dp(100)); save.setBackground(background); content.addView(save);gap(content,16);
        TextView note=text("Экспериментальная сборка: результат ещё не проверен для печати.",12,false);note.setTextColor(ui.muted);content.addView(note);
        save.setOnClickListener(v -> chooseDestination());
        try {
            String path = getIntent().getStringExtra(EXTRA_RESULT_PATH);
            if (path == null) throw new IOException("Не указан результат нарезки.");
            source = new File(path).getCanonicalFile();
            File base = new File(getFilesDir(), "slicer-engine").getCanonicalFile();
            if (!source.getPath().startsWith(base.getPath()+File.separator) || !source.getName().equals("result.gcode") || !source.isFile() || source.length() == 0)
                throw new IOException("Результат нарезки недоступен.");
            status.setText("Размер файла: " + source.length() + " байт.");
        } catch (IOException e) { status.setText(e.getMessage()); source = null; }
        if (task != null) { task.observer = new WeakReference<>(this); refreshTask(); }
        else if (state != null && state.getBoolean("copying")) status.setText("Экспорт был прерван. Проверьте файл в выбранной папке и повторите сохранение при необходимости.");
        refreshEnabled();
    }

    private void chooseDestination() {
        if (source == null || pickerOpen || task != null && task.running) return;
        String name = getIntent().getStringExtra(EXTRA_FILE_NAME);
        if (name == null || name.trim().isEmpty()) name = "kepler-result.gcode";
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (name.length() > 150) name = name.substring(0,150);
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".gcode")) name += ".gcode";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,name);
        try { pickerOpen = true; refreshEnabled(); startActivityForResult(intent,CREATE_DOCUMENT); }
        catch (android.content.ActivityNotFoundException e) { pickerOpen = false; status.setText("На устройстве нет приложения для сохранения документов."); refreshEnabled(); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request != CREATE_DOCUMENT) return;
        pickerOpen = false;
        if (result != RESULT_OK || data == null || data.getData() == null) { refreshEnabled(); return; }
        if (source == null) { status.setText("Исходный результат нарезки недоступен."); refreshEnabled(); return; }
        task = new ExportTask(getApplicationContext(),source,data.getData(),this); task.start(); refreshTask();
    }
    private void refreshEnabled() { boolean enabled = source != null && !pickerOpen && (task == null || !task.running); save.setEnabled(enabled); save.setAlpha(enabled?1f:.45f); }
    private void refreshTask() { if (task != null) status.setText(task.message); refreshEnabled(); }
    @Override public Object onRetainNonConfigurationInstance() { return task; }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("picker",pickerOpen); state.putBoolean("copying",task != null && task.running); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() { if (task != null && task.observer.get() == this) task.observer.clear(); super.onDestroy(); }
    private int dp(int value) { return (int)(value*getResources().getDisplayMetrics().density+.5f); }
    private TextView text(String value,int size,boolean bold) { return ui.text(value,size,bold); }
    private void gap(LinearLayout parent,int value) { parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(value))); }

    private static final class ExportTask extends Thread {
        final android.content.Context context; final File source; final Uri destination;
        volatile WeakReference<GcodeExportActivity> observer; volatile boolean running = true; volatile String message = "Сохраняем G-code…";
        ExportTask(android.content.Context context,File source,Uri destination,GcodeExportActivity activity) { super("gcode-export"); this.context=context; this.source=source; this.destination=destination; observer=new WeakReference<>(activity); }
        @Override public void run() {
            try {
                long length = source.length(), copied = 0;
                try (FileInputStream in = new FileInputStream(source); OutputStream out = context.getContentResolver().openOutputStream(destination,"wt")) {
                    if (out == null) throw new IOException("Не удалось открыть место сохранения.");
                    byte[] buffer = new byte[65536]; int n;
                    while ((n=in.read(buffer)) != -1) { out.write(buffer,0,n); copied+=n; }
                    out.flush();
                    if (copied != length || source.length() != length) throw new IOException("Размер исходного файла изменился.");
                }
                message = "G-code сохранён: " + copied + " байт.";
            } catch (Exception error) { message = "Не удалось сохранить файл полностью: " + (error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()) + " Повторите экспорт при необходимости."; }
            finally { running=false; new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> { GcodeExportActivity a=observer.get(); if (a!=null && !a.isDestroyed()) a.refreshTask(); }); }
        }
    }
}
