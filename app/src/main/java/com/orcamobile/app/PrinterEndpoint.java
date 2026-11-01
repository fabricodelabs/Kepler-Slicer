package com.orcamobile.app;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharacterCodingException;
import java.nio.CharBuffer;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

/** Immutable in-memory connection details. Secrets must not be logged or serialized here. */
public final class PrinterEndpoint {
    public enum Type { OCTOPRINT, MOONRAKER, PRUSALINK, PRUSACONNECT, SL1, DUET, ULTIMAKER, FLASHAIR, ASTROBOX, REPETIER, MKS, ESP3D, CREALITYPRINT, FLASHFORGE, ELEGOOLINK, OBICO, SIMPLYPRINT, THREE_D_PRINTER_OS, BAMBU }
    public enum Auth { NONE, API_KEY, DIGEST, PASSWORD, BEARER, BASIC }
    public final Type type;
    public final Auth auth;
    public final URI baseUri;
    public final String username, storage;
    public final Map<String,String> extras;
    private final String secret;

    public PrinterEndpoint(Type type, String baseUrl, Auth auth, String username, String secret, String storage) {
        this(type, baseUrl, auth, username, secret, storage, Collections.emptyMap());
    }
    public PrinterEndpoint(Type type, String baseUrl, Auth auth, String username, String secret, String storage, Map<String,String> extras) {
        if (type == null || auth == null) throw new IllegalArgumentException("Не указан тип подключения.");
        this.type = type; this.auth = auth;
        this.username = checkCredential(username == null ? "" : username);
        this.secret = checkCredential(secret == null ? "" : secret);
        LinkedHashMap<String,String> copy=new LinkedHashMap<>();
        if(extras!=null) for(Map.Entry<String,String> entry:extras.entrySet()) {
            if(entry.getKey()==null || !entry.getKey().matches("[A-Za-z0-9_]{1,64}") || entry.getValue()==null || entry.getValue().length()>1024 || entry.getValue().indexOf('\r')>=0 || entry.getValue().indexOf('\n')>=0) throw new IllegalArgumentException("Недопустимый параметр подключения.");
            copy.put(entry.getKey(),entry.getValue());
        }
        this.extras=Collections.unmodifiableMap(copy);
        this.storage = storage == null || storage.trim().isEmpty() ? defaultStorage(type) : checkStorage(storage.trim());
        if (auth == Auth.DIGEST && (this.username.isEmpty() || this.secret.isEmpty()))
            throw new IllegalArgumentException("Для Digest нужны пользователь и пароль.");
        if ((auth == Auth.API_KEY || auth == Auth.BEARER) && this.secret.isEmpty()) throw new IllegalArgumentException("Введите ключ доступа.");
        if (auth == Auth.BASIC && (this.username.isEmpty() || this.secret.isEmpty())) throw new IllegalArgumentException("Для Basic нужны пользователь и пароль.");
        String input = baseUrl == null ? "" : baseUrl.trim();
        if (input.indexOf('\r') >= 0 || input.indexOf('\n') >= 0 || input.indexOf('\\') >= 0)
            throw new IllegalArgumentException("Недопустимый адрес принтера.");
        if (!input.contains("://")) input = "http://" + input;
        try {
            URI u = new URI(input);
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || u.getHost() == null || u.getHost().isEmpty() ||
                    u.getRawUserInfo() != null || u.getRawQuery() != null || u.getRawFragment() != null ||
                    u.getPort() < -1 || u.getPort() > 65535) throw new URISyntaxException(input, "Invalid host URL");
            String host=u.getHost();
            if(type==Type.PRUSACONNECT && !scheme.equals("https") && !host.equals("127.0.0.1") && !host.equalsIgnoreCase("localhost") && !host.equals("::1") && !host.equals("[::1]"))
                throw new IllegalArgumentException("Для Prusa Connect нужен HTTPS.");
            String rawPath = u.getRawPath() == null ? "" : u.getRawPath();
            String lower = rawPath.toLowerCase(Locale.ROOT);
            if (lower.contains("%2f") || lower.contains("%5c") || lower.contains("%2e") || lower.contains("%00") ||
                    lower.contains("%0d") || lower.contains("%0a")) throw new URISyntaxException(input, "Encoded path separator");
            for (String part : rawPath.split("/", -1)) if (part.equals(".") || part.equals("..")) throw new URISyntaxException(input, "Dot path");
            String clean = u.toASCIIString(); while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
            baseUri = new URI(clean);
        } catch (URISyntaxException e) { throw new IllegalArgumentException("Введите корректный HTTP(S)-адрес без логина и параметров.", e); }
    }
    private static String defaultStorage(Type type) { return type == Type.MOONRAKER ? "gcodes" : "local"; }
    private static String checkStorage(String value) {
        if (!value.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Недопустимое имя хранилища.");
        return value;
    }
    private static String checkCredential(String value) {
        if (value.length() > 4096 || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Недопустимые учётные данные.");
        return value;
    }
    String secret() { return secret; }
    public URI url(String relative) {
        if (relative == null || relative.isEmpty() || relative.startsWith("/") || relative.contains("\\") || relative.contains("#") || relative.contains("\r") || relative.contains("\n"))
            throw new IllegalArgumentException("Недопустимый путь API.");
        String path=relative.split("\\?",2)[0], lower=path.toLowerCase(Locale.ROOT);
        if(lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c") || lower.contains("%00") || lower.contains("%0d") || lower.contains("%0a")) throw new IllegalArgumentException("Недопустимый путь API.");
        for(String part:path.split("/",-1)) if(part.equals(".") || part.equals("..")) throw new IllegalArgumentException("Недопустимый путь API.");
        try { return new URI(baseUri.toASCIIString() + "/" + relative); }
        catch (URISyntaxException e) { throw new IllegalArgumentException("Недопустимый путь API.", e); }
    }
    /** Encode a single remote filename/storage path component, never a full URL. */
    public static String encodeComponent(String value) {
        if (value == null || value.isEmpty() || value.equals(".") || value.equals("..") || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0 || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Недопустимое имя файла.");
        byte[] bytes;
        try { ByteBuffer encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value)); bytes=new byte[encoded.remaining()];encoded.get(bytes); }
        catch(CharacterCodingException e) { throw new IllegalArgumentException("Имя файла содержит некорректный Unicode.",e); }
        if (bytes.length > 255) throw new IllegalArgumentException("Имя файла слишком длинное.");
        StringBuilder encoded = new StringBuilder(); final char[] hex = "0123456789ABCDEF".toCharArray();
        for (byte b : bytes) {
            int c = b & 255;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') encoded.append((char)c);
            else encoded.append('%').append(hex[c >>> 4]).append(hex[c & 15]);
        }
        return encoded.toString();
    }
}
