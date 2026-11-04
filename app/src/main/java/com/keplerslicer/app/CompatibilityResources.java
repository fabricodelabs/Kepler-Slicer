package com.keplerslicer.app;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Isolated, versioned read-only inputs for a partial upstream compatibility query. */
final class CompatibilityResources {
    final File resources, data;
    private CompatibilityResources(File root) { resources = new File(root, "resources"); data = new File(root, "data"); }
    private static void mkdir(File f) throws IOException { if (!f.isDirectory() && !f.mkdirs()) throw new IOException("Не удалось подготовить каталог профилей."); }
    private static void delete(File f) { if (f.isDirectory()) { File[] children=f.listFiles(); if(children!=null) for(File c:children) delete(c); } f.delete(); }
    private static void requireVendor(String vendor) throws IOException {
        if (vendor == null || vendor.isEmpty() || vendor.equals(".") || vendor.equals("..") || vendor.indexOf('/') >= 0 || vendor.indexOf('\\') >= 0)
            throw new IOException("Недопустимый производитель профиля.");
    }
    private static String key(Set<String> vendors) throws IOException {
        try {
            String[] ordered = vendors.toArray(new String[0]); Arrays.sort(ordered);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (String s : ordered) { sha.update(s.getBytes(StandardCharsets.UTF_8)); sha.update((byte)0); }
            StringBuilder hex = new StringBuilder(); for (byte b : sha.digest()) hex.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }
    static synchronized CompatibilityResources prepare(Context context, String vendor, String processVendor) throws IOException {
        return prepareTriple(context, vendor, processVendor, null);
    }
    static synchronized CompatibilityResources prepareTriple(Context context, String vendor, String processVendor, String filamentVendor) throws IOException {
        return prepareTriple(context,vendor,processVendor,filamentVendor,SettingsState.capture(context));
    }
    static synchronized CompatibilityResources prepareTriple(Context context, String vendor, String processVendor, String filamentVendor, SettingsState.Snapshot snapshot) throws IOException {
        requireVendor(vendor);
        Set<String> vendors = new LinkedHashSet<>(); vendors.add(vendor); vendors.add("OrcaFilamentLibrary");
        if (processVendor != null && !processVendor.isEmpty()) { requireVendor(processVendor); vendors.add(processVendor); }
        if (filamentVendor != null && !filamentVendor.isEmpty()) { requireVendor(filamentVendor); vendors.add(filamentVendor); }
        if(snapshot==null)throw new IOException("Missing immutable settings snapshot");
        for(String overlayVendor:snapshot.vendors()){requireVendor(overlayVendor);vendors.add(overlayVendor);}
        File parent = new File(context.getCacheDir(), "kepler-compat-6d2869c4"); mkdir(parent);
        File finalRoot = new File(parent, key(vendors)+"-"+snapshot.fingerprint);
        if (new File(finalRoot,".complete").isFile()) return new CompatibilityResources(finalRoot);
        File staging = new File(parent, "staging-" + UUID.randomUUID()); mkdir(staging);
        boolean complete = false;
        try {
            File system = new File(staging,"data/system"), resources = new File(staging,"resources"); mkdir(system); mkdir(resources);
            Set<String> manifests = new LinkedHashSet<>(); long total = 0; int count = 0;
            try (ZipInputStream zip = new ZipInputStream(context.getAssets().open("kepler_profiles.zip"))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Загрузка отменена.");
                    if (++count > 50000) throw new IOException("Слишком много файлов профилей.");
                    if (entry.isDirectory()) continue;
                    String name = entry.getName(); boolean include = false;
                    for (String v : vendors) if (name.equals(v + ".json") || name.startsWith(v + "/")) {
                        include = true; if (name.equals(v + ".json")) manifests.add(v); break;
                    }
                    if (!include) continue;
                    File out = new File(system,name);
                    if (!out.getCanonicalPath().startsWith(system.getCanonicalPath() + File.separator)) throw new IOException("Недопустимый путь в архиве профилей.");
                    mkdir(out.getParentFile());
                    try (FileOutputStream file = new FileOutputStream(out)) {
                        byte[] b = new byte[8192]; int n;
                        while ((n = zip.read(b)) != -1) { total += n; if (total > 256L*1024*1024) throw new IOException("Профили слишком велики."); file.write(b,0,n); }
                    }
                }
            }
            if (!manifests.containsAll(vendors)) throw new IOException("Не найден каталог выбранного производителя.");
            try (ZipInputStream zip = new ZipInputStream(context.getAssets().open("upstream_resources.zip"))) {
                ZipEntry entry; boolean found = false;
                while ((entry = zip.getNextEntry()) != null) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Загрузка отменена.");
                    if (!entry.getName().equals("info/legacy_compatible_printers.json")) continue;
                    File file = new File(resources, entry.getName()); mkdir(file.getParentFile());
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        byte[] b = new byte[8192]; int n, size = 0;
                        while ((n = zip.read(b)) != -1) { size += n; if (size > 8*1024*1024) throw new IOException("Файл совместимости слишком велик."); out.write(b,0,n); }
                    }
                    found = true; break;
                }
                if (!found) throw new IOException("Не найден файл совместимости профилей.");
            }
            snapshot.install(new File(staging,"data"));
            if (!new File(staging,".complete").createNewFile()) throw new IOException("Не удалось подготовить профили.");
            if (!staging.renameTo(finalRoot)) throw new IOException("Не удалось сохранить кэш профилей.");
            complete = true; return new CompatibilityResources(finalRoot);
        } finally { if (!complete) delete(staging); }
    }
}
