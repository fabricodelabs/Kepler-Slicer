package com.keplerslicer.app.settings;

import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Configuration-only 3MF transport; never parses or rewrites model geometry. */
public final class ProjectConfigArchive {
    public static final String PROJECT_CONFIG = "Metadata/project_settings.config";

    private ProjectConfigArchive() { }

    /** The exact engine JSON object, including unrecognized keys and original value types. */
    public static JSONObject readProjectConfig(File archive) throws IOException {
        try (ZipFile zip = new ZipFile(archive)) {
            return readJson(zip, projectEntry(entries(zip)));
        }
    }

    /** Original embedded preset JSON; these are not implicitly selected/applied to slicing. */
    public static List<JSONObject> readEmbeddedPresets(File archive) throws IOException {
        List<JSONObject> out = new ArrayList<>();
        try (ZipFile zip = new ZipFile(archive)) {
            for (Map.Entry<String, ZipEntry> item : entries(zip).entrySet()) {
                String name = item.getKey();
                ZipEntry entry = item.getValue();
                if (!entry.isDirectory() && name.endsWith(".config")
                        && (name.startsWith("metadata/process_settings_")
                        || name.startsWith("metadata/filament_settings_")
                        || name.startsWith("metadata/machine_settings_"))) {
                    out.add(readJson(zip, entry));
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Writes a new archive. Every other entry's uncompressed payload is streamed unchanged.
     * ZIP compression bytes may change. A matching existing project_settings.config is required.
     */
    public static void replaceProjectConfig(File source, File destination, JSONObject config) throws IOException {
        if (config == null || (config.has("type") && !"project".equals(config.optString("type"))))
            throw new IOException("Expected project configuration JSON");
        if (source.getCanonicalFile().equals(destination.getCanonicalFile()))
            throw new IOException("Choose a new 3MF output file");
        Path output = destination.getAbsoluteFile().toPath();
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Output file already exists");
        File parent = output.toFile().getParentFile();
        if (parent == null) throw new IOException("Cannot create output directory");
        Files.createDirectories(parent.toPath());
        File staged = File.createTempFile(".project-config-", ".3mf", parent);
        try {
            writeArchive(source, staged, config);
            checkCanceled();
            // Claim the private cache name exclusively. The caller exposes the file only
            // after the completed archive atomically replaces this empty reservation.
            Files.createFile(output);
            try {
                Files.move(staged.toPath(), output, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException | RuntimeException failure) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        } finally {
            staged.delete();
        }
    }

    private static void writeArchive(File source, File staged, JSONObject config) throws IOException {
        try (ZipFile zip = new ZipFile(source);
             FileOutputStream file = new FileOutputStream(staged);
             ZipOutputStream out = new ZipOutputStream(file)) {
            Map<String, ZipEntry> entries = entries(zip);
            ZipEntry project = projectEntry(entries);
            byte[] buffer = new byte[32768];
            for (ZipEntry original : entries.values()) {
                boolean replace = original == project;
                out.putNextEntry(outputEntry(original, replace));
                try (VerifiedEntryInput in = new VerifiedEntryInput(zip, original)) {
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        if (!replace) out.write(buffer, 0, count);
                    }
                }
                if (replace) out.write(config.toString().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
            if (zip.getComment() != null) out.setComment(zip.getComment());
            out.finish();
            file.getFD().sync();
        }
    }

    private static ZipEntry outputEntry(ZipEntry original, boolean replace) throws IOException {
        ZipEntry next = new ZipEntry(original.getName());
        if (original.getTime() >= 0) next.setTime(original.getTime());
        if (original.getComment() != null) next.setComment(original.getComment());
        if (original.getExtra() != null) next.setExtra(withoutZip64Sizes(original.getExtra()));
        if (!replace && original.getMethod() == ZipEntry.STORED) {
            next.setMethod(ZipEntry.STORED);
            next.setSize(original.getSize());
            next.setCrc(original.getCrc());
        }
        return next;
    }

    private static Map<String, ZipEntry> entries(ZipFile zip) throws IOException {
        Map<String, ZipEntry> entries = new LinkedHashMap<>();
        Enumeration<? extends ZipEntry> items = zip.entries();
        while (items.hasMoreElements()) {
            ZipEntry entry = items.nextElement();
            String key = entry.getName().toLowerCase(Locale.ROOT);
            if (entries.putIfAbsent(key, entry) != null) throw new IOException("Duplicate 3MF ZIP entry: " + entry.getName());
        }
        return entries;
    }

    private static ZipEntry projectEntry(Map<String, ZipEntry> entries) throws IOException {
        ZipEntry entry = entries.get(PROJECT_CONFIG.toLowerCase(Locale.ROOT));
        if (entry == null || entry.isDirectory()) throw new IOException("This 3MF has no project_settings.config");
        return entry;
    }

    private static JSONObject readJson(ZipFile zip, ZipEntry entry) throws IOException {
        try (VerifiedEntryInput in = new VerifiedEntryInput(zip, entry)) {
            return PresetJson.read(in);
        }
    }

    private static void checkCanceled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("3MF transfer canceled");
    }

    private static final class VerifiedEntryInput extends FilterInputStream {
        private final ZipEntry entry;
        private final CRC32 crc = new CRC32();
        private long size;

        VerifiedEntryInput(ZipFile zip, ZipEntry entry) throws IOException {
            super(zip.getInputStream(entry));
            this.entry = entry;
        }

        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) == -1 ? -1 : one[0] & 0xff;
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            checkCanceled();
            int count = super.read(buffer, offset, length);
            if (count == -1) {
                if (size != entry.getSize() || crc.getValue() != entry.getCrc()) throw corruptEntry();
            } else {
                size += count;
                if (size > entry.getSize()) throw corruptEntry();
                crc.update(buffer, offset, count);
            }
            return count;
        }

        private IOException corruptEntry() {
            return new IOException("3MF ZIP entry size or checksum does not match: " + entry.getName());
        }
    }

    // ZIP64 size fields describe the old compression stream. The writer regenerates these
    // from the streamed payload; copying them into a recompressed entry can corrupt sizes.
    private static byte[] withoutZip64Sizes(byte[] extra) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int offset = 0;
        while (offset + 4 <= extra.length) {
            int id = (extra[offset] & 255) | ((extra[offset + 1] & 255) << 8);
            int length = (extra[offset + 2] & 255) | ((extra[offset + 3] & 255) << 8);
            if (offset + 4 + length > extra.length) throw new IOException("Malformed ZIP extra field");
            if (id != 1) out.write(extra, offset, length + 4);
            offset += length + 4;
        }
        if (offset != extra.length) throw new IOException("Malformed ZIP extra field");
        return out.toByteArray();
    }
}
