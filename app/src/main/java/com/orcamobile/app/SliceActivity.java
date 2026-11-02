package com.orcamobile.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.keplerslicer.engine.ExperimentalSlicer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Experimental integration of upstream libslic3r. No printer connection or export. */
public final class SliceActivity extends Activity {
    static final String EXTRA_AUTO_SLICE="kepler_auto_slice";
    static final String EXTRA_AUTO_SLICE_REQUEST="kepler_auto_slice_request";
    private static final Object LOCK = new Object();
    private static final String RESULTS = "last_slice_result", RESULT_PATH = "path", RESULT_DETAILS = "details", RESULT_MODEL = "model", RESULT_OVERRIDES = "overrides", RESULT_FINGERPRINT = "job_fingerprint";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<SliceActivity> observer = new WeakReference<>(null);
    private static boolean working;
    static boolean isSlicing() { synchronized (LOCK) { return working; } }
    private static String status = "Готово к пробной нарезке.";
    private TextView statusView, startButton, previewButton, previewDetails, exportButton;
    private MdUi ui;
    private boolean pendingAutoSlice;
    private long pendingAutoRequest;
    private File shownResult;
    private boolean checkingResult;
    private int resultQueryGeneration;
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    @Override public void onCreate(Bundle state) {
        MdUi.prepare(this); super.onCreate(state);
        pendingAutoSlice=state==null&&getIntent()!=null&&getIntent().getBooleanExtra(EXTRA_AUTO_SLICE,false);
        pendingAutoRequest=getIntent()==null?-1:getIntent().getLongExtra(EXTRA_AUTO_SLICE_REQUEST,-1);
        if(getIntent()!=null){getIntent().removeExtra(EXTRA_AUTO_SLICE);getIntent().removeExtra(EXTRA_AUTO_SLICE_REQUEST);}
        ui = new MdUi(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); ui.bars(this, root);
        setContentView(root);
        ScrollView scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this); content.setPadding(dp(24), dp(24), dp(24), dp(32)); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content);
        TextView back = label("‹   Назад", 15, true); back.setPadding(0, dp(8), 0, dp(12)); back.setOnClickListener(v -> finish()); content.addView(back);
        content.addView(label("Нарезка модели", 28, true)); space(content, 12);
        content.addView(label("Экспериментальный движок OrcaSlicer. Результат сохраняется только на устройстве; соответствие настольной версии и пригодность к печати ещё не подтверждены.", 14, false)); space(content, 24);
        OrcaProfileCatalog.Profile printer = OrcaProfileCatalog.loadSelection(this);
        OrcaProcessFilamentCatalog.Profile process = OrcaProcessFilamentCatalog.loadSelection(this, OrcaProcessFilamentCatalog.PROCESS);
        OrcaProcessFilamentCatalog.Profile filament = OrcaProcessFilamentCatalog.loadSelection(this, OrcaProcessFilamentCatalog.FILAMENT);
        content.addView(label("ПРИНТЕР", 11, true)); content.addView(label(printer == null ? "Не выбран" : printer.displayName(), 15, false)); space(content, 14);
        content.addView(label("ПРОЦЕСС", 11, true)); content.addView(label(process == null ? "Не выбран" : process.name, 15, false)); space(content, 14);
        content.addView(label("МАТЕРИАЛ", 11, true)); content.addView(label(filament == null ? "Не выбран" : filament.name, 15, false)); space(content, 24);
        statusView = label("", 14, false); statusView.setTextIsSelectable(true); content.addView(statusView); space(content, 24);
        startButton = label("Нарезать модель", 15, true); startButton.setTextColor(ui.bg); startButton.setGravity(Gravity.CENTER); startButton.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable button = new GradientDrawable(); button.setColor(ui.ink); button.setCornerRadius(dp(100)); startButton.setBackground(button); content.addView(startButton);
        startButton.setOnClickListener(v -> startSlice(-1));
        space(content, 16);
        previewButton = label("Посмотреть последнюю нарезку", 15, true); previewButton.setGravity(Gravity.CENTER); previewButton.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable outline = new GradientDrawable(); outline.setColor(ui.surface); outline.setStroke(dp(1), ui.line); outline.setCornerRadius(dp(100)); previewButton.setBackground(outline); content.addView(previewButton);
        previewButton.setOnClickListener(v -> withCurrentResult(result -> {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("kepler_show_layers",true));
        }));
        space(content, 12);
        exportButton = label("Сохранить G-code", 15, true); exportButton.setGravity(Gravity.CENTER); exportButton.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable exportOutline = new GradientDrawable(); exportOutline.setColor(ui.surface); exportOutline.setStroke(dp(1), ui.line); exportOutline.setCornerRadius(dp(100)); exportButton.setBackground(exportOutline); content.addView(exportButton);
        exportButton.setOnClickListener(v -> withCurrentResult(result -> {
            String modelName = getSharedPreferences(RESULTS, MODE_PRIVATE).getString(RESULT_MODEL, "");
            if (modelName == null || modelName.trim().isEmpty()) modelName = "kepler-result";
            String lowerName=modelName.toLowerCase(java.util.Locale.ROOT);
            if (lowerName.endsWith(".stl") || lowerName.endsWith(".obj") || lowerName.endsWith(".3mf")) modelName = modelName.substring(0, modelName.length() - 4);
            startActivity(new Intent(this, GcodeExportActivity.class).putExtra(GcodeExportActivity.EXTRA_RESULT_PATH, result.getAbsolutePath())
                    .putExtra(GcodeExportActivity.EXTRA_FILE_NAME, modelName + ".gcode"));
        }));
        space(content, 8); previewDetails = label("", 12, false); content.addView(previewDetails);
    }
    @Override protected void onStart() { super.onStart(); synchronized (LOCK) { observer = new WeakReference<>(this); } requestResultRefresh();if(pendingAutoSlice){pendingAutoSlice=false;if(ControlPreferences.currentAutoRequest(this,pendingAutoRequest))startSlice(pendingAutoRequest);} }
    @Override protected void onStop() { resultQueryGeneration++;shownResult=null;synchronized (LOCK) { if (observer.get() == this) observer.clear(); } super.onStop(); }
    private TextView label(String value, int size, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(ui.ink); t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); t.setLineSpacing(dp(2), 1); return t; }
    private void space(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private void refresh() {
        boolean available = shownResult != null;
        synchronized (LOCK) {
            String visibleStatus = !working && !available && status.contains("Нарезка завершена:") ? "Результат недоступен или относится к прежней модели и настройкам. Запустите нарезку заново." : status;
            statusView.setText(visibleStatus); startButton.setEnabled(!working); startButton.setAlpha(working ? .45f : 1f);
        }
        previewButton.setVisibility(available ? View.VISIBLE : View.GONE); exportButton.setVisibility(available ? View.VISIBLE : View.GONE); previewDetails.setVisibility(available ? View.VISIBLE : View.GONE);
        previewButton.setEnabled(!checkingResult);exportButton.setEnabled(!checkingResult);
        if (available) previewDetails.setText("Последний сохранённый результат:\n" + getSharedPreferences(RESULTS, MODE_PRIVATE).getString(RESULT_DETAILS, ""));
    }
    private void requestResultRefresh() {
        final int generation=++resultQueryGeneration;
        shownResult=null;refresh();
        final android.content.Context context=getApplicationContext();
        new Thread(()->{
            File current=lastResult(context);
            MAIN.post(()->{if(!isFinishing()&&!isDestroyed()&&generation==resultQueryGeneration){shownResult=current;refresh();}});
        },"orca-result-check").start();
    }
    private void withCurrentResult(java.util.function.Consumer<File> action) {
        if(checkingResult)return;
        checkingResult=true;refresh();
        final int generation=resultQueryGeneration;
        final android.content.Context context=getApplicationContext();
        new Thread(()->{
            File result=lastResult(context);
            MAIN.post(()->{
                if(isFinishing()||isDestroyed()||generation!=resultQueryGeneration){checkingResult=false;return;}
                checkingResult=false;shownResult=result;refresh();
                if(result==null)update("Сохранённый результат недоступен.");else action.accept(result);
            });
        },"orca-result-action").start();
    }
    private static void update(String text) { synchronized (LOCK) { status = text; } MAIN.post(() -> { SliceActivity a; synchronized (LOCK) { a = observer.get(); } if (a != null && !a.isFinishing()) a.refresh(); }); }

    private void startSlice(long autoRequest) {
        synchronized (LOCK) { if (working) return; working = true; }
        refresh();
        final android.content.Context context = getApplicationContext();
        new Thread(() -> {
            String stage = "снимок выбранных профилей";
            File data = null;
            boolean success = false;
            try {
                if(autoRequest>=0&&!ControlPreferences.currentAutoRequest(context,autoRequest))throw new IOException("Автонарезка отменена: появился более новый запрос.");
                final ProcessOverridesStore.Snapshot selection=ProcessOverridesStore.snapshotForSlice(context);
                final OrcaProfileCatalog.Profile printer=selection.identity.printer;
                final OrcaProcessFilamentCatalog.Profile process=selection.identity.process;
                final OrcaProcessFilamentCatalog.Profile filament=selection.identity.filament;
                stage="загрузка модели";
                update("Проверяем модель и подготавливаем ресурсы OrcaSlicer…");
                File root = new File(context.getFilesDir(), "slicer-engine"); ensureDirectory(root);
                data = new File(root, "run-" + UUID.randomUUID()); ensureDirectory(data);
                // Snapshot the bytes and transform together before the model can be changed.
                CurrentModelStore.Snapshot model = CurrentModelStore.snapshotToDirectory(context, data);
                ObjectSettingsStore.Snapshot objectSettings=selection.identity.objectSettings;
                String sourceFile=model.selectionKey.substring(0,model.selectionKey.indexOf('\n'));
                if(!objectSettings.modelFileName.equals(sourceFile)||objectSettings.plateIndex!=model.plateIndex)
                    throw new IOException("Модель или пластина изменились при подготовке параметров. Повторите нарезку.");
                File input = model.file;
                String jobFingerprint = ResultFingerprint.of(model.selectionKey, selection.identity.fingerprint, selection.overrides);
                if(autoRequest>=0&&hasSavedResult(context,jobFingerprint)&&ControlPreferences.currentAutoRequest(context,autoRequest)
                        &&jobFingerprint.equals(ProcessOverridesStore.currentResultFingerprint(context))){
                    update("Результат для текущей модели и настроек уже сохранён; повторная автонарезка не нужна.");return;
                }
                File resources = new File(root, "resources-6d2869c4"); prepareResources(context, resources);
                File system = new File(data, "system"); ensureDirectory(system);
                LinkedHashSet<String> vendors = new LinkedHashSet<>();
                vendors.add(printer.vendor); vendors.add(process.vendor); vendors.add(filament.vendor); vendors.add("OrcaFilamentLibrary");
                long needed = 128L * 1024 * 1024;
                for (String vendor : vendors) {
                    if (vendor == null || vendor.isEmpty() || vendor.contains("/") || vendor.contains("\\") || vendor.equals(".") || vendor.equals("..")) throw new IOException("Неверное имя производителя профиля.");
                    File from = new File(resources, "profiles");
                    needed += treeSize(new File(from, vendor + ".json")) + treeSize(new File(from, vendor));
                }
                if (root.getUsableSpace() < needed) throw new IOException("Недостаточно свободного места для выбранных профилей и результата.");
                for (String vendor : vendors) {
                    File from = new File(resources, "profiles");
                    copyTree(new File(from, vendor + ".json"), new File(system, vendor + ".json"));
                    copyTree(new File(from, vendor), new File(system, vendor));
                }
                objectSettings.applied(selection.identity.profileSettings).install(data);
                // Object values have higher priority than legacy global process overrides.
                org.json.JSONObject nativeOverrides=new org.json.JSONObject(selection.overrides);
                java.util.Iterator<String> objectKeys=objectSettings.changes().keys();
                while(objectKeys.hasNext())nativeOverrides.remove(objectKeys.next());
                if((autoRequest>=0&&!ControlPreferences.currentAutoRequest(context,autoRequest))||!jobFingerprint.equals(ProcessOverridesStore.currentResultFingerprint(context)))
                    throw new IOException("Модель или настройки изменились до начала нарезки. Повторите операцию.");
                stage = "загрузка движка и нарезка";
                update("Проверяем совместимость профилей и нарезаем модель…");
                File job = new File(data, "job-" + UUID.randomUUID());
                String result = ExperimentalSlicer.sliceModelWithSettings(printer.vendor, process.vendor, filament.vendor,
                        printer.name, process.name, filament.name, input.getAbsolutePath(), resources.getAbsolutePath(), data.getAbsolutePath(), job.getAbsolutePath(), model.transform.toNativeArguments(), nativeOverrides.toString(),model.plateIndex);
                File output = result == null ? null : new File(result);
                if (output == null || !output.isFile() || output.length() == 0 || !output.getCanonicalFile().getParentFile().equals(job.getCanonicalFile())) throw new IOException("G-code не создан или пуст.");
                if((autoRequest>=0&&!ControlPreferences.currentAutoRequest(context,autoRequest))||!jobFingerprint.equals(ProcessOverridesStore.currentResultFingerprint(context)))
                    throw new IOException("Модель или настройки изменились во время нарезки; устаревший результат не сохранён.");
                success = true;
                ModelTransform transform = model.transform;
                String printerDisplay=selection.identity.profileSettings.overlays.get("printer").values().optString("name",printer.name);
                String processDisplay=selection.identity.profileSettings.overlays.get("process").values().optString("name",process.name);
                String filamentDisplay=selection.identity.profileSettings.overlays.get("filament").values().optString("name",filament.name);
                String details = model.name + "\n" + printerDisplay + "\n" + processDisplay + "\n" + filamentDisplay +
                        String.format(java.util.Locale.getDefault(), "\nПоворот XYZ: %.2f°, %.2f°, %.2f°\nМасштаб XYZ: %.3f × %.3f × %.3f\nСмещение XY: %.2f, %.2f мм",
                                transform.rx, transform.ry, transform.rz, transform.sx, transform.sy, transform.sz, transform.offsetX, transform.offsetY);
                SharedPreferences prefs = context.getSharedPreferences(RESULTS, MODE_PRIVATE);
                String previousPath = prefs.getString(RESULT_PATH, ""), previousDetails = prefs.getString(RESULT_DETAILS, ""), previousModel = prefs.getString(RESULT_MODEL, ""), previousOverrides = prefs.getString(RESULT_OVERRIDES, "{}"), previousFingerprint = prefs.getString(RESULT_FINGERPRINT, "");
                boolean saved = prefs.edit().putString(RESULT_PATH, output.getCanonicalPath()).putString(RESULT_DETAILS, details).putString(RESULT_MODEL, model.name).putString(RESULT_OVERRIDES, selection.overrides).putString(RESULT_FINGERPRINT, jobFingerprint).commit();
                // A failed commit may still change the in-memory preference map.
                if (!saved) prefs.edit().putString(RESULT_PATH, previousPath).putString(RESULT_DETAILS, previousDetails).putString(RESULT_MODEL, previousModel).putString(RESULT_OVERRIDES, previousOverrides).putString(RESULT_FINGERPRINT, previousFingerprint).apply();
                update("Последний запуск: " + details + "\n\nНарезка завершена: " + output.length() + " байт. Результат сохранён во внутреннем хранилище и не проверен для печати." + (saved ? "" : "\nНе удалось сохранить ссылку на результат для просмотра и экспорта."));
            } catch (Throwable error) {
                String message = error.getMessage();
                String diagnostic = error instanceof LinkageError ? nativeDiagnostic(context) : "";
                update("Последний запуск остановился: " + stage + ".\n" + error.getClass().getSimpleName() + (message == null ? "" : ": " + message) + diagnostic);
            } finally {
                if (data != null) {
                    if (success) { deleteTree(new File(data, "system")); File[] files=data.listFiles(); if(files!=null) for (File source : files) if (source.isFile() && source.getName().startsWith("model.")) source.delete(); }
                    else deleteTree(data);
                }
                synchronized (LOCK) { working = false; }
                MAIN.post(() -> { SliceActivity a; synchronized (LOCK) { a = observer.get(); } if (a != null && !a.isFinishing()) a.requestResultRefresh(); });
            }
        }, "orca-slicing").start();
    }
    private static void ensureDirectory(File dir) throws IOException { if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Не удалось создать каталог: " + dir.getName()); }
    private static File lastResult(android.content.Context context) {
        java.util.Map<String, ?> record = context.getSharedPreferences(RESULTS, MODE_PRIVATE).getAll();
        Object rawPath=record.get(RESULT_PATH);String path=rawPath instanceof String?(String)rawPath:null;
        if (path == null || path.isEmpty()) return null;
        try {
            Object rawSaved=record.get(RESULT_FINGERPRINT);String saved=rawSaved instanceof String?(String)rawSaved:null;
            if (saved == null || saved.isEmpty() || !saved.equals(ProcessOverridesStore.currentResultFingerprint(context))) return null;
            File root = new File(context.getFilesDir(), "slicer-engine").getCanonicalFile();
            File result = new File(path).getCanonicalFile();
            return result.getName().equals("result.gcode") && result.getPath().startsWith(root.getPath() + File.separator) && result.isFile() && result.length() > 0 ? result : null;
        } catch (IOException | RuntimeException ignored) { return null; }
    }
    /** Called on the slicing worker to prevent an unchanged auto-slice loop. */
    private static boolean hasSavedResult(android.content.Context context,String fingerprint) {
        SharedPreferences prefs=context.getSharedPreferences(RESULTS,MODE_PRIVATE);
        if(!fingerprint.equals(prefs.getString(RESULT_FINGERPRINT,"")))return false;
        try {
            String path=prefs.getString(RESULT_PATH,"");if(path==null||path.isEmpty())return false;
            File root=new File(context.getFilesDir(),"slicer-engine").getCanonicalFile(),result=new File(path).getCanonicalFile();
            return result.getName().equals("result.gcode")&&result.getPath().startsWith(root.getPath()+File.separator)&&result.isFile()&&result.length()>0;
        } catch(IOException error){return false;}
    }
    private static void copy(InputStream in, FileOutputStream out) throws IOException { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n); }
    private static String nativeDiagnostic(android.content.Context context) {
        android.content.pm.ApplicationInfo info = context.getApplicationInfo();
        boolean found = containsNativeLibrary(info.sourceDir);
        if (info.splitSourceDirs != null) for (String split : info.splitSourceDirs) found |= containsNativeLibrary(split);
        return "\nABI устройства: " + String.join(", ", Build.SUPPORTED_ABIS) +
                "\nАрхитектура процесса: " + System.getProperty("os.arch", "неизвестна") +
                "\nARM64-библиотека в установленном APK: " + (found ? "есть" : "нет") +
                "\nКаталог библиотек: " + info.nativeLibraryDir;
    }
    private static boolean containsNativeLibrary(String path) {
        if (path == null) return false;
        try (java.util.zip.ZipFile apk = new java.util.zip.ZipFile(path)) {
            return apk.getEntry("lib/arm64-v8a/libkepler_slicer_experimental.so") != null;
        } catch (IOException ignored) { return false; }
    }
    private static long treeSize(File file) throws IOException {
        if (file.isFile()) return file.length();
        if (!file.isDirectory()) throw new IOException("Ресурс профиля не найден: " + file.getName());
        File[] files = file.listFiles(); if (files == null) throw new IOException("Не удалось прочитать профили.");
        long total = 0; for (File child : files) total += treeSize(child); return total;
    }
    private static void deleteTree(File file) {
        if (file.isDirectory()) { File[] files = file.listFiles(); if (files != null) for (File child : files) deleteTree(child); }
        file.delete();
    }
    private static void copyTree(File from, File to) throws IOException {
        if (from.isDirectory()) { ensureDirectory(to); File[] children = from.listFiles(); if (children == null) throw new IOException("Не удалось прочитать профили."); for (File child : children) copyTree(child, new File(to, child.getName())); }
        else if (from.isFile()) { try (InputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) { copy(in, out); } }
        else throw new IOException("Ресурс профиля не найден: " + from.getName());
    }
    static synchronized void prepareResources(android.content.Context context, File resources) throws IOException {
        File marker = new File(resources, ".complete"); if (marker.isFile()) return;
        ensureDirectory(resources);
        if (resources.getUsableSpace() < 350L * 1024 * 1024) throw new IOException("Для подготовки ресурсов требуется 350 МБ свободного места.");
        String base = resources.getCanonicalPath() + File.separator; long total = 0; int count = 0;
        try (ZipInputStream zip = new ZipInputStream(context.getAssets().open("upstream_resources.zip"))) {
            ZipEntry entry; byte[] bytes = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > 50000) throw new IOException("Слишком много файлов ресурсов.");
                File dest = new File(resources, entry.getName()); if (!dest.getCanonicalPath().startsWith(base)) throw new IOException("Недопустимый путь ресурса.");
                if (entry.isDirectory()) { ensureDirectory(dest); continue; }
                ensureDirectory(dest.getParentFile());
                try (FileOutputStream out = new FileOutputStream(dest)) { int n; while ((n = zip.read(bytes)) != -1) { total += n; if (total > 512L * 1024 * 1024) throw new IOException("Ресурсы слишком велики."); out.write(bytes, 0, n); } }
                if (count % 500 == 0) update("Подготавливаем ресурсы OrcaSlicer: " + count + " файлов…");
                zip.closeEntry();
            }
        }
        if (!marker.createNewFile() && !marker.isFile()) throw new IOException("Не удалось завершить подготовку ресурсов.");
    }
}
