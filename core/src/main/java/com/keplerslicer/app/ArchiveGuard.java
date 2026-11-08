package com.keplerslicer.app;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Checks a ZIP container (3MF) before the native engine reads it. The engine's ZIP reader inflates entries without
 * an output limit, so a few megabytes of crafted 3MF could expand to gigabytes of XML (decompression bomb, CWE-409).
 * Every entry is inflated here once and counted, because the sizes written in the archive can lie.
 */
final class ArchiveGuard {
    static final int MAX_ENTRIES = 20000;
    static final long MAX_TOTAL = 4L * 1024 * 1024 * 1024;   // inflated bytes of the whole archive
    static final long MAX_RATIO = 1000;                       // inflated / compressed, for entries above RATIO_FLOOR
    static final long RATIO_FLOOR = 16L * 1024 * 1024;

    private ArchiveGuard() {}

    static void check(File archive) throws IOException { check(archive, MAX_TOTAL); }

    static void check(File archive, long maxTotal) throws IOException {
        try (ZipFile zip = new ZipFile(archive)) {
            int count = 0; long total = 0; byte[] buffer = new byte[65536];
            for (Enumeration<? extends ZipEntry> all = zip.entries(); all.hasMoreElements(); ) {
                ZipEntry entry = all.nextElement();
                if (++count > MAX_ENTRIES) throw new IOException(L.t("В архиве 3MF слишком много файлов."));
                String name = entry.getName();
                if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains("\0") || ("/" + name + "/").contains("/../"))
                    throw new IOException(L.t("Архив 3MF содержит недопустимый путь."));
                if (entry.isDirectory()) continue;
                long inflated = 0, compressed = Math.max(1, entry.getCompressedSize());
                try (InputStream in = zip.getInputStream(entry)) {
                    for (int n; (n = in.read(buffer)) != -1; ) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException(L.t("Импорт отменён."));
                        inflated += n; total += n;
                        if (total > maxTotal) throw new IOException(L.t("Архив 3MF распаковывается в слишком большой объём."));
                        if (inflated > RATIO_FLOOR && inflated / compressed > MAX_RATIO) throw new IOException(L.t("Архив 3MF похож на ZIP-бомбу и не будет открыт."));
                    }
                }
                if (entry.getSize() >= 0 && inflated != entry.getSize()) throw new IOException(L.t("Архив 3MF повреждён."));
            }
        } catch (ZipException e) {
            throw new IOException(L.t("Архив 3MF повреждён."), e);
        }
    }
}
